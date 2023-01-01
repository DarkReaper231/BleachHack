/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.Items;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.state.property.Properties;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.gen.blockpredicate.SolidBlockPredicate;
import net.minecraft.world.gen.feature.TreeFeature;
import org.apache.commons.lang3.tuple.Pair;
import org.bleachhack.event.events.EventTick;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.SettingBlockList;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.InventoryUtils;
import org.bleachhack.util.world.WorldUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class AutoFarm extends Module {

    private final Map<BlockPos, Integer> mossMap = new HashMap<>();

    public AutoFarm() {
        super("AutoFarm", KEY_UNBOUND, ModuleCategory.PLAYER, "Automatically does farming activities for you.",
                new SettingSlider("Range", 1, 6, 4.5, 1).withDesc("Farming reach."),
                new SettingToggle("Till", true).withDesc("Tills dirt around you.").withChildren(
                        new SettingToggle("WateredOnly", true).withDesc("Only tills watered dirt.")),
                new SettingToggle("Harvest", true).withDesc("Harvests grown crops.").withChildren(
                        new SettingToggle("Crops", true).withDesc("Harvests wheat, carrots, potato & beetroot."),
                        new SettingToggle("StemCrops", true).withDesc("Harvests melons/pumpkins."),
                        new SettingToggle("NetherWart", true).withDesc("Harvests nether wart."),
                        new SettingToggle("Cocoa", true).withDesc("Harvests cocoa beans."),
                        new SettingToggle("SweetBerries", false).withDesc("Harvests sweet berries."),
                        new SettingToggle("SugarCane", false).withDesc("Harvests sugar canes."),
                        new SettingToggle("Cactus", false).withDesc("Harvests cactuses."),
                        new SettingToggle("GlowBerries", false).withDesc("Harvests glow berries."),
                        new SettingToggle("Kelp", false).withDesc("Harvests kelp.")),
                new SettingToggle("Plant", true).withDesc("Plants crops around you.").withChildren(
                        new SettingToggle("Crops", true).withDesc("Plants wheat, carrots, potato & beetroot."),
                        new SettingToggle("StemCrops", true).withDesc("Plants melon/pumpkin stems."),
                        new SettingToggle("NetherWart", true).withDesc("Plants nether wart."),
                        new SettingToggle("Cocoa", true).withDesc("Plants cocoa beans."),
                        new SettingToggle("SweetBerries", false).withDesc("Plants sweet berries."),
                        new SettingToggle("SugarCane", false).withDesc("Plants sugar canes."),
                        new SettingToggle("Cactus", false).withDesc("Plants cactuses."),
                        new SettingToggle("GlowBerries", false).withDesc("Plants glow berries.").withChildren(
                                new SettingBlockList("Edit Blocks", "Edit Blocks To Plant Glow Berries On",
                                        Blocks.STONE,
                                        Blocks.DIRT,
                                        Blocks.GRASS_BLOCK,
                                        Blocks.COBBLESTONE,
                                        Blocks.GRANITE,
                                        Blocks.ANDESITE,
                                        Blocks.DIORITE,
                                        Blocks.CALCITE).withDesc("Edit the blocks to plant glow berries on."))),
                new SettingToggle("Bonemeal", true).withDesc("Bonemeals ungrown crop.").withChildren(
                        new SettingToggle("Crops", true).withDesc("Bonemeals wheat, carrots, potato & beetroot."),
                        new SettingToggle("StemCrops", true).withDesc("Bonemeals melon/pumpkin stems."),
                        new SettingToggle("Cocoa", true).withDesc("Bonemeals cocoa beans."),
                        new SettingToggle("SweetBerries", false).withDesc("Bonemeals sweet berries."),
                        new SettingToggle("Mushrooms", false).withDesc("Bonemeals mushrooms."),
                        new SettingToggle("Saplings", false).withDesc("Bonemeals saplings."),
                        new SettingToggle("Moss", false).withDesc("Bonemeals moss."),
                        new SettingToggle("GlowBerries", false).withDesc("Bonemeals glow berries.")));
    }

    @Override
    public void onDisable(boolean inWorld) {
        mossMap.clear();

        super.onDisable(inWorld);
    }

    @BleachSubscribe
    public void onTick(EventTick event) {
        mossMap.entrySet().removeIf(e -> e.setValue(e.getValue() - 1) == 0);

        double range = getSetting(0).asSlider().getValue();
        int ceilRange = MathHelper.ceil(range);
        SettingToggle tillSetting = getSetting(1).asToggle();
        SettingToggle harvestSetting = getSetting(2).asToggle();
        SettingToggle plantSetting = getSetting(3).asToggle();
        SettingToggle bonemealSetting = getSetting(4).asToggle();

        // Special case for moss to maximize efficiency
        if (bonemealSetting.getState() && bonemealSetting.getChild(6).asToggle().getState()) {
            int slot = InventoryUtils.getSlot(true, i -> mc.player.getInventory().getStack(i).getItem() == Items.BONE_MEAL);
            if (slot != -1) {
                BlockPos bestBlock = BlockPos.streamOutwards(new BlockPos(mc.player.getEyePos()), ceilRange, ceilRange, ceilRange)
                        .filter(b -> mc.player.getEyePos().distanceTo(Vec3d.ofCenter(b)) <= range && !mossMap.containsKey(b))
                        .map(b -> Pair.of(b.toImmutable(), getMossSpots(b)))
                        .filter(p -> p.getRight() > 10)
                        .map(Pair::getLeft)
                        .min(Comparator.reverseOrder()).orElse(null);

                if (bestBlock != null) {
                    if (!mc.world.isAir(bestBlock.up())) {
                        mc.interactionManager.updateBlockBreakingProgress(bestBlock.up(), Direction.UP);
                    }

                    Hand hand = InventoryUtils.selectSlot(slot);
                    mc.interactionManager.interactBlock(mc.player, hand,
                            new BlockHitResult(Vec3d.ofCenter(bestBlock, 1), Direction.UP, bestBlock, false));
                    mossMap.put(bestBlock, 100);
                    return;
                }
            }
        }

        for (BlockPos pos : BlockPos.iterateOutwards(new BlockPos(mc.player.getEyePos()), ceilRange, ceilRange, ceilRange)) {
            if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) > range)
                continue;

            BlockState state = mc.world.getBlockState(pos);
            Block block = state.getBlock();
            if (tillSetting.getState() && canTill(block) && mc.world.isAir(pos.up())) {
                if (!tillSetting.getChild(0).asToggle().getState()
                        || BlockPos.stream(pos.getX() - 4, pos.getY(), pos.getZ() - 4, pos.getX() + 4, pos.getY(), pos.getZ() + 4).anyMatch(
                        b -> mc.world.getFluidState(b).isIn(FluidTags.WATER))) {
                    Hand hand = InventoryUtils.selectSlot(true, i -> mc.player.getInventory().getStack(i).getItem() instanceof HoeItem);

                    if (hand != null) {
                        mc.interactionManager.interactBlock(mc.player, hand,
                                new BlockHitResult(Vec3d.ofCenter(pos, 1), Direction.UP, pos, false));
                        return;
                    }
                }
            }

            if (harvestSetting.getState()) {
                if ((harvestSetting.getChild(0).asToggle().getState() && block instanceof CropBlock && ((CropBlock) block).isMature(state))
                        || (harvestSetting.getChild(1).asToggle().getState() && block instanceof GourdBlock)
                        || (harvestSetting.getChild(2).asToggle().getState() && block instanceof NetherWartBlock && state.get(NetherWartBlock.AGE) >= 3)
                        || (harvestSetting.getChild(3).asToggle().getState() && block instanceof CocoaBlock && state.get(CocoaBlock.AGE) >= 2)
                        || (harvestSetting.getChild(5).asToggle().getState() && shouldHarvestTallCrop(pos, block, SugarCaneBlock.class))
                        || (harvestSetting.getChild(6).asToggle().getState() && shouldHarvestTallCrop(pos, block, CactusBlock.class))
                        || (harvestSetting.getChild(8).asToggle().getState() && shouldHarvestTallCrop(pos, block, KelpPlantBlock.class))) {
                    mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
                    return;
                }

                int slot = InventoryUtils.getSlot(true, i -> mc.player.getInventory().getStack(i).getItem() != Items.BONE_MEAL);

                if ((harvestSetting.getChild(4).asToggle().getState() && block instanceof SweetBerryBushBlock && state.get(SweetBerryBushBlock.AGE) >= 3)
                        || (harvestSetting.getChild(7).asToggle().getState() && CaveVines.hasBerries(state))) {
                    Hand hand = InventoryUtils.selectSlot(slot);
                    mc.interactionManager.interactBlock(mc.player, hand,
                            new BlockHitResult(Vec3d.ofCenter(pos, 1), Direction.UP, pos, false));
                    return;
                }
            }

            if (plantSetting.getState() && mc.world.getOtherEntities(null, new Box(pos.up()), EntityPredicates.VALID_LIVING_ENTITY).isEmpty()) {
                if (block instanceof FarmlandBlock && mc.world.isAir(pos.up())) {
                    int slot = InventoryUtils.getSlot(true, i -> {
                        Item item = mc.player.getInventory().getStack(i).getItem();

                        if (plantSetting.getChild(0).asToggle().getState() && (item == Items.WHEAT_SEEDS || item == Items.CARROT || item == Items.POTATO || item == Items.BEETROOT_SEEDS)) {
                            return true;
                        }

                        return plantSetting.getChild(1).asToggle().getState() && (item == Items.PUMPKIN_SEEDS || item == Items.MELON_SEEDS);
                    });

                    if (slot != -1) {
                        WorldUtils.placeBlock(pos.up(), slot, 0, false, false, true);
                        return;
                    }
                }

                if (block instanceof SoulSandBlock && mc.world.isAir(pos.up()) && plantSetting.getChild(2).asToggle().getState()) {
                    int slot = InventoryUtils.getSlot(true, i -> mc.player.getInventory().getStack(i).getItem() == Items.NETHER_WART);

                    if (slot != -1) {
                        WorldUtils.placeBlock(pos.up(), slot, 0, false, false, true);
                        return;
                    }
                }

                if (block.equals(Blocks.JUNGLE_LOG)
                        && (mc.world.isAir(pos.west()) || mc.world.isAir(pos.south()) || mc.world.isAir(pos.east()) || mc.world.isAir(pos.north()))
                        && plantSetting.getChild(3).asToggle().getState()) {
                    int slot = InventoryUtils.getSlot(true, i -> mc.player.getInventory().getStack(i).getItem() == Items.COCOA_BEANS);

                    if (slot != -1) {
                        if (mc.world.isAir(pos.west())) {
                            WorldUtils.placeBlock(pos.west(), slot, 0, false, false, true);
                        } else if (mc.world.isAir(pos.north())) {
                            WorldUtils.placeBlock(pos.north(), slot, 0, false, false, true);
                        } else if (mc.world.isAir(pos.east())) {
                            WorldUtils.placeBlock(pos.east(), slot, 0, false, false, true);
                        } else if (mc.world.isAir(pos.south())) {
                            WorldUtils.placeBlock(pos.south(), slot, 0, false, false, true);
                        }
                        return;
                    }
                }

                if ((block instanceof GrassBlock
                        || block instanceof RootedDirtBlock
                        || block instanceof FarmlandBlock
                        || block.equals(Blocks.DIRT)
                        || block.equals(Blocks.COARSE_DIRT)
                        || block.equals(Blocks.PODZOL)
                        || block.equals(Blocks.MOSS_BLOCK)
                        || block.equals(Blocks.MYCELIUM)
                        || block.equals(Blocks.MUD)) && mc.world.isAir(pos.up()) && plantSetting.getChild(4).asToggle().getState()) {
                    int slot = InventoryUtils.getSlot(true, i -> mc.player.getInventory().getStack(i).getItem() == Items.SWEET_BERRIES);

                    if (slot != -1) {
                        WorldUtils.placeBlock(pos.up(), slot, 0, false, false, true);
                        return;
                    }
                }

				if ((block instanceof GrassBlock
                        || block instanceof RootedDirtBlock
                        || block instanceof SandBlock
                        || block.equals(Blocks.DIRT)
                        || block.equals(Blocks.COARSE_DIRT)
                        || block.equals(Blocks.PODZOL)
                        || block.equals(Blocks.MOSS_BLOCK)
                        || block.equals(Blocks.MYCELIUM)
                        || block.equals(Blocks.MUD))
                        && mc.world.isAir(pos.up())
                        && ((mc.world.isWater(pos.east())
                        || mc.world.isWater(pos.west())
                        || mc.world.isWater(pos.north())
                        || mc.world.isWater(pos.south()))
                        || (mc.world.getBlockState(pos.east()).get(Properties.WATERLOGGED)
                        || mc.world.getBlockState(pos.west()).get(Properties.WATERLOGGED)
                        || mc.world.getBlockState(pos.north()).get(Properties.WATERLOGGED)
                        || mc.world.getBlockState(pos.south()).get(Properties.WATERLOGGED)))
						&& plantSetting.getChild(5).asToggle().getState()) {
					int slot = InventoryUtils.getSlot(true, i -> mc.player.getInventory().getStack(i).getItem() == Items.SUGAR_CANE);

					if (slot != -1) {
						WorldUtils.placeBlock(pos.up(), slot, 0, false, false, true);
						return;
					}
				}

                if (block instanceof SandBlock && mc.world.isAir(pos.up())
                        && mc.world.isAir(pos.up().north())
                        && mc.world.isAir(pos.up().east())
                        && mc.world.isAir(pos.up().west())
                        && mc.world.isAir(pos.up().south())
                        && plantSetting.getChild(6).asToggle().getState()) {
                    int slot = InventoryUtils.getSlot(true, i -> mc.player.getInventory().getStack(i).getItem() == Items.CACTUS);

                    if (slot != -1) {
                        WorldUtils.placeBlock(pos.up(), slot, 0, false, false, true);
                        return;
                    }
                }

                if ((plantSetting.getChild(7).asToggle().getChild(0).asList(Block.class).contains(block))
                        && mc.world.isAir(pos.down())
                        && plantSetting.getChild(7).asToggle().getState()) {
                    int slot = InventoryUtils.getSlot(true, i -> mc.player.getInventory().getStack(i).getItem() == Items.GLOW_BERRIES);

                    if (slot != -1) {
                        WorldUtils.placeBlock(pos.down(), slot, 0, false, false, true);
                        return;
                    }
                }
            }

            if (bonemealSetting.getState()) {
                int slot = InventoryUtils.getSlot(true, i -> mc.player.getInventory().getStack(i).getItem() == Items.BONE_MEAL);

                if (slot != -1) {
                    if ((bonemealSetting.getChild(0).asToggle().getState() && block instanceof CropBlock && !((CropBlock) block).isMature(state))
                            || (bonemealSetting.getChild(1).asToggle().getState() && block instanceof StemBlock && state.get(StemBlock.AGE) < StemBlock.MAX_AGE)
                            || (bonemealSetting.getChild(2).asToggle().getState() && block instanceof CocoaBlock && state.get(CocoaBlock.AGE) < 2)
                            || (bonemealSetting.getChild(3).asToggle().getState() && block instanceof SweetBerryBushBlock && state.get(SweetBerryBushBlock.AGE) < 3)
                            || (bonemealSetting.getChild(4).asToggle().getState() && block instanceof MushroomPlantBlock)
                            || (bonemealSetting.getChild(5).asToggle().getState() && (block instanceof SaplingBlock || block instanceof AzaleaBlock  || block instanceof BambooSaplingBlock) && canPlaceSapling(pos))
                            || (bonemealSetting.getChild(7).asToggle().getState() && block instanceof CaveVines && !CaveVines.hasBerries(state))) {
                        Hand hand = InventoryUtils.selectSlot(slot);
                        mc.interactionManager.interactBlock(mc.player, hand,
                                new BlockHitResult(Vec3d.ofCenter(pos, 1), Direction.UP, pos, false));
                        return;
                    }
                }
            }
        }
    }

    private boolean shouldHarvestTallCrop(BlockPos pos, Block posBlock, Class<? extends Block> blockClass) {
        return posBlock.getClass().equals(blockClass)
                && mc.world.getBlockState(pos.down()).getBlock().getClass().equals(blockClass)
                && !mc.world.getBlockState(pos.down(2)).getBlock().getClass().equals(blockClass);
    }

    private int getMossSpots(BlockPos pos) {
        if (mc.world.getBlockState(pos).getBlock() != Blocks.MOSS_BLOCK
                || mc.world.getBlockState(pos.up()).getHardness(mc.world, pos) != 0f) {
            return 0;
        }

        return (int) BlockPos.streamOutwards(pos, 3, 4, 3)
                .filter(b -> isMossGrowableOn(mc.world.getBlockState(b).getBlock()) && mc.world.isAir(b.up()))
                .count();
    }

    private boolean isMossGrowableOn(Block block) {
        return block == Blocks.STONE || block == Blocks.GRANITE || block == Blocks.ANDESITE || block == Blocks.DIORITE
                || block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.MYCELIUM || block == Blocks.GRASS_BLOCK
                || block == Blocks.PODZOL || block == Blocks.ROOTED_DIRT;
    }

    private boolean canPlaceSapling(BlockPos pos) {
        return BlockPos.stream(pos.getX() - 1, pos.getY() + 1, pos.getZ() - 1, pos.getX() + 1, pos.getY() + 5, pos.getZ() + 1)
                .allMatch(b -> TreeFeature.canReplace(mc.world, b));
    }

    private boolean canTill(Block block) {
        return block == Blocks.DIRT || block == Blocks.GRASS_BLOCK || block == Blocks.COARSE_DIRT
                || block == Blocks.ROOTED_DIRT || block == Blocks.DIRT_PATH;
    }
}
