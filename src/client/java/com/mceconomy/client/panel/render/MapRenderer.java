package com.mceconomy.client.panel.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/** Dunya haritasi JSON'undan 2D grid harita. */
public final class MapRenderer {
	private static final Map<String, Integer> COLORS = Map.of(
			"bank", 0xFFD4A843,
			"reserve", 0xFFFFD700,
			"depot", 0xFF6B8CCE,
			"prison", 0xFFCC4444,
			"company_vault", 0xFF4DA6FF,
			"personal_vault", 0xFF7EC850,
			"black_depot", 0xFF8844AA,
			"camera", 0xFFB366FF,
			"player", 0xFFFF4444
	);

	private MapRenderer() {
	}

	public static void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h, JsonObject mapData) {
		graphics.fill(x, y, x + w, y + h, 0xCC0A1520);
		if (mapData == null || mapData.has("error")) {
			String msg = mapData != null && mapData.has("message")
					? mapData.get("message").getAsString() : "Harita yukleniyor...";
			graphics.text(font, msg, x + 6, y + h / 2 - 4, 0xFFAAAAAA, false);
			return;
		}
		JsonObject focus = mapData.has("focus") ? mapData.getAsJsonObject("focus") : new JsonObject();
		double cx = focus.has("x") ? focus.get("x").getAsDouble() : 0;
		double cz = focus.has("z") ? focus.get("z").getAsDouble() : 0;
		double radius = focus.has("radius") ? focus.get("radius").getAsDouble() : 96;
		double minX = cx - radius;
		double maxX = cx + radius;
		double minZ = cz - radius;
		double maxZ = cz + radius;
		double scale = Math.min(w / (maxX - minX), h / (maxZ - minZ));

		int rcx = x + (int) ((cx - minX) * scale);
		int rcz = y + (int) ((cz - minZ) * scale);
		int rr = (int) (radius * scale);
		graphics.fill(rcx - rr, rcz - rr, rcx + rr, rcz + rr, 0x33143C5A);
		drawCircleOutline(graphics, rcx, rcz, rr, 0xFF3A5F8A);

		drawBlips(graphics, mapData.getAsJsonArray("pois"), minX, minZ, scale, x, y);
		drawBlips(graphics, mapData.getAsJsonArray("radar"), minX, minZ, scale, x, y);
		drawBlips(graphics, mapData.getAsJsonArray("players"), minX, minZ, scale, x, y);

		graphics.text(font, "MB X:" + (int) cx + " Z:" + (int) cz, x + 4, y + h - 12, 0xFF88AACC, false);
	}

	private static void drawBlips(GuiGraphicsExtractor graphics, JsonArray arr, double minX, double minZ,
			double scale, int ox, int oy) {
		if (arr == null) {
			return;
		}
		for (int i = 0; i < arr.size(); i++) {
			JsonObject p = arr.get(i).getAsJsonObject();
			if (!p.has("x") || !p.has("z")) {
				continue;
			}
			String type = p.has("type") ? p.get("type").getAsString() : "player";
			int color = COLORS.getOrDefault(type, 0xFF888888);
			int px = ox + (int) ((p.get("x").getAsDouble() - minX) * scale);
			int py = oy + (int) ((p.get("z").getAsDouble() - minZ) * scale);
			graphics.fill(px - 3, py - 3, px + 3, py + 3, color);
		}
	}

	private static void drawCircleOutline(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
		for (int deg = 0; deg < 360; deg += 6) {
			double rad = Math.toRadians(deg);
			int px = cx + (int) (Math.cos(rad) * r);
			int py = cy + (int) (Math.sin(rad) * r);
			graphics.fill(px, py, px + 1, py + 1, color);
		}
	}
}
