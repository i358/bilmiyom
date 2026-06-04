package com.mceconomy.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.vehicle.boat.Boat;

/** Arac surus HUD — hiz, yakit, model. */
public final class VehicleHudOverlay {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("mceconomy", "vehicle_hud");

	private VehicleHudOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ID, VehicleHudOverlay::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		if (!(client.player.getVehicle() instanceof Boat boat) || !isMcVehicle(boat)) {
			return;
		}
		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		int x = sw / 2 + 8;
		int y = sh - 72;
		String model = VehicleHudState.model();
		if (model.isEmpty()) {
			model = "arac";
		}
		String line1 = "Arac: " + model + "  Hiz: " + String.format("%.1f", VehicleHudState.speed()) + " m/s";
		String line2 = "Yakit: " + String.format("%.0f", VehicleHudState.fuel()) + "%";
		graphics.fill(x - 4, y - 4, x + 160, y + 22, 0x99000000);
		int fuelColor = VehicleHudState.fuel() > 25 ? 0x7BED9F : 0xE74C3C;
		graphics.text(client.font, line1, x, y, 0xFFFFFF);
		graphics.text(client.font, line2, x, y + 10, fuelColor);
	}

	static boolean isMcVehicle(Boat boat) {
		return boat.hasCustomName() && boat.getCustomName().getString().contains("[Arac]");
	}
}
