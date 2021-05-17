/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDrinker420/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package bleach.hack.module.mods;

import java.util.Random;

import com.google.common.eventbus.Subscribe;

import bleach.hack.event.events.EventSendPacket;
import bleach.hack.module.ModuleCategory;
import bleach.hack.module.Module;
import bleach.hack.util.PlayerInteractEntityC2SUtils;
import bleach.hack.util.PlayerInteractEntityC2SUtils.InteractType;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.particle.ParticleTypes;

/**
 * @author sl, Bleach
 */
public class Criticals extends Module {

	public Criticals() {
		super("Criticals", KEY_UNBOUND, ModuleCategory.COMBAT, "Attempts to force Critical hits on entities you hit.");
	}

	@Subscribe
	public void sendPacket(EventSendPacket event) {
		if (event.getPacket() instanceof PlayerInteractEntityC2SPacket) {
			PlayerInteractEntityC2SPacket packet = (PlayerInteractEntityC2SPacket) event.getPacket();
			if (PlayerInteractEntityC2SUtils.getInteractType(packet) == InteractType.INTERACT_AT) {
				this.doCritical();

				/* Lets fake some extra paricles why not */
				Entity e = PlayerInteractEntityC2SUtils.getEntity(packet);

				if (e != null) {
					Random r = new Random();
					for (int i = 0; i < 10; i++) {
						mc.particleManager.addParticle(ParticleTypes.CRIT, e.getX(), e.getY() + e.getHeight() / 2, e.getZ(),
								r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5);
					}
				}
			}
		}
	}

	private void doCritical() {
		if (!mc.player.isOnGround() || mc.player.isInLava() || mc.player.isTouchingWater()) {
			return;
		}

		double posX = mc.player.getX();
		double posY = mc.player.getY();
		double posZ = mc.player.getZ();
		mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(posX, posY + 0.0625, posZ, true));
		mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(posX, posY, posZ, false));
	}
}
