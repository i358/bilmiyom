package com.mceconomy.client;

import com.mceconomy.network.VehicleInputPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

public final class VehicleInputCapture {
	private VehicleInputCapture() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			LocalPlayer player = client.player;
			Entity vehicle = player != null ? player.getVehicle() : null;
			if (player == null || vehicle == null || !isMcVehicle(vehicle)) {
				if (player != null) {
					VehicleHudState.clear();
				}
				return;
			}
			var opts = client.options;
			ClientPlayNetworking.send(new VehicleInputPayload(
					opts.keyUp.isDown(), opts.keyDown.isDown(),
					opts.keyLeft.isDown(), opts.keyRight.isDown(),
					opts.keyJump.isDown(), opts.keyShift.isDown()));
		});
	}

	static boolean isMcVehicle(Entity entity) {
		if (entity == null) {
			return false;
		}
		if (entity instanceof ArmorStand) {
			if (entity.hasCustomName()) {
				return entity.getCustomName().getString().contains("[Arac]");
			}
			return VehicleHudState.recentlyDriving();
		}
		return false;
	}
}
