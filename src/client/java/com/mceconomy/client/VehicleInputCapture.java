package com.mceconomy.client;

import com.mceconomy.network.VehicleInputPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.vehicle.boat.Boat;

public final class VehicleInputCapture {
	private VehicleInputCapture() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			LocalPlayer player = client.player;
			if (player == null || !(player.getVehicle() instanceof Boat boat)
					|| !VehicleHudOverlay.isMcVehicle(boat)) {
				if (player != null) {
					VehicleHudState.clear();
				}
				return;
			}
			var opts = client.options;
			boolean forward = opts.keyUp.isDown();
			boolean backward = opts.keyDown.isDown();
			boolean left = opts.keyLeft.isDown();
			boolean right = opts.keyRight.isDown();
			boolean brake = opts.keyJump.isDown();
			boolean handbrake = opts.keyShift.isDown();
			String modelLabel = boat.hasCustomName()
					? boat.getCustomName().getString().replaceAll("§[0-9a-fk-or]", "") : "arac";
			VehicleHudState.update(VehicleHudState.speed(), VehicleHudState.fuel(), modelLabel);
			ClientPlayNetworking.send(new VehicleInputPayload(forward, backward, left, right, brake, handbrake));
		});
	}
}
