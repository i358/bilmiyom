package com.mceconomy.client;

import com.mceconomy.economy.GoldStandard;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class EconomyHudOverlay {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("mceconomy", "economy_hud");

	private EconomyHudOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, ID, EconomyHudOverlay::extractRenderState);
	}

	private static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		int y = sh - 62;
		int x = sw / 2 - 90;

		String wallet = GoldStandard.formatMilligrams(EconomyHudState.walletMg());
		String bank = GoldStandard.formatMilligrams(EconomyHudState.bankMg());
		String status = EconomyHudState.frozen() ? "MASAK" :
				(EconomyHudState.blacklisted() ? "KARA" : "OK");
		String walletLine = "Cuzdan: " + wallet;
		String bankLine = "Banka: " + bank;
		String line2 = "Meslek: " + EconomyHudState.jobLabel() + "  Durum: " + status;
		if (EconomyHudState.dirtyMg() > 0) {
			line2 += "  Kara: " + GoldStandard.formatMilligrams(EconomyHudState.dirtyMg());
		}
		int boxW = Math.max(220, client.font.width(walletLine) + client.font.width(bankLine) + 24);
		graphics.fill(x - 4, y - 4, x + boxW, y + 22, 0x88000000);
		graphics.text(client.font, walletLine, x, y, 0xFFE8C547, false);
		graphics.text(client.font, bankLine, x + client.font.width(walletLine) + 8, y, 0xFF88CCFF, false);
		graphics.text(client.font, line2, x, y + 10, 0xFFCCCCCC, false);
	}
}
