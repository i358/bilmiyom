package com.mceconomy.client.panel.components;

import com.mceconomy.client.panel.EconomyPanelClientState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Sync lastMessage / success durum cubugu. */
public final class PanelToast {
	private static final int BAR_H = 16;

	private PanelToast() {
	}

	public static void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int width) {
		String msg = EconomyPanelClientState.message();
		if (msg == null || msg.isBlank()) {
			return;
		}
		int bg = EconomyPanelClientState.messageSuccess() ? 0xCC1A3D2A : 0xCC3D1A1A;
		int fg = EconomyPanelClientState.messageSuccess() ? 0xFF7BED9F : 0xFFFF6B6B;
		graphics.fill(x, y, x + width, y + BAR_H, bg);
		String prefix = EconomyPanelClientState.messageSuccess() ? "✓ " : "✗ ";
		graphics.text(font, prefix + truncate(msg, width - 12, font), x + 6, y + 4, fg, false);
	}

	public static int height() {
		return EconomyPanelClientState.message().isBlank() ? 0 : BAR_H;
	}

	private static String truncate(String s, int maxW, Font font) {
		String t = s;
		while (t.length() > 1 && font.width(t) > maxW) {
			t = t.substring(0, t.length() - 2) + "…";
		}
		return t;
	}
}
