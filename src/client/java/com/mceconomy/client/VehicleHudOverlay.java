package com.mceconomy.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

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
		Entity vehicle = client.player.getVehicle();
		if (vehicle == null || !VehicleInputCapture.isMcVehicle(vehicle)) {
			return;
		}
		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		int x = sw / 2 + 8;
		int y = sh - 72;
		String model = VehicleHudState.model();
		if (model.isEmpty()) {
			model = "sedan";
		}
		double speedKmh = VehicleHudState.speed() * 20 * 3.6;
		String line1 = "Arac: " + model + "  Hiz: " + String.format("%.0f", speedKmh) + " km/h";
		String line2 = "Yakit: " + String.format("%.0f", VehicleHudState.fuel()) + "%";
		graphics.fill(x - 4, y - 4, x + 180, y + 24, 0x99000000);
		int fuelColor = VehicleHudState.fuel() > 25 ? 0xFF7BED9F : 0xFFE74C3C;
		graphics.text(client.font, line1, x, y, 0xFFFFFFFF, false);
		graphics.text(client.font, line2, x, y + 11, fuelColor, false);
	}
}
