package com.mceconomy.client.panel.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** JSON fiyat serilerinden basit cizgi / cubuk grafik (GuiGraphicsExtractor). */
public final class ChartRenderer {
	private ChartRenderer() {
	}

	public static void renderLine(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
			JsonArray history, String valueKey, int color, String title) {
		graphics.fill(x, y, x + w, y + h, 0x66000000);
		if (title != null && !title.isBlank()) {
			graphics.text(font, title, x + 4, y + 2, 0xFFE8C547, false);
		}
		List<Double> values = extractValues(history, valueKey);
		if (values.size() < 2) {
			graphics.text(font, "Veri yok", x + 4, y + h / 2, 0xFFAAAAAA, false);
			return;
		}
		double min = values.stream().min(Double::compare).orElse(0d);
		double max = values.stream().max(Double::compare).orElse(1d);
		double range = Math.max(0.001, max - min);
		int plotY = y + (title != null ? 14 : 4);
		int plotH = h - (title != null ? 18 : 8);
		int plotX = x + 4;
		int plotW = w - 8;
		int prevX = -1, prevY = -1;
		for (int i = 0; i < values.size(); i++) {
			double v = values.get(i);
			int px = plotX + (plotW * i / Math.max(1, values.size() - 1));
			int py = plotY + plotH - (int) ((v - min) / range * plotH);
			if (prevX >= 0) {
				drawLine(graphics, prevX, prevY, px, py, color);
			}
			graphics.fill(px - 1, py - 1, px + 1, py + 1, color | 0xFF000000);
			prevX = px;
			prevY = py;
		}
	}

	public static void renderBar(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
			JsonArray items, String labelKey, String valueKey, int color, String title) {
		graphics.fill(x, y, x + w, y + h, 0x66000000);
		if (title != null && !title.isBlank()) {
			graphics.text(font, title, x + 4, y + 2, 0xFFE8C547, false);
		}
		if (items == null || items.isEmpty()) {
			graphics.text(font, "Veri yok", x + 4, y + h / 2, 0xFFAAAAAA, false);
			return;
		}
		int count = Math.min(8, items.size());
		double max = 1;
		for (int i = 0; i < count; i++) {
			max = Math.max(max, itemValue(items.get(i).getAsJsonObject(), valueKey));
		}
		int barW = Math.max(8, (w - 16) / count - 4);
		int baseY = y + h - 4;
		int plotH = h - 20;
		for (int i = 0; i < count; i++) {
			JsonObject row = items.get(i).getAsJsonObject();
			double v = itemValue(row, valueKey);
			int barH = (int) (v / max * plotH);
			int bx = x + 8 + i * (barW + 4);
			int by = baseY - barH;
			graphics.fill(bx, by, bx + barW, baseY, color);
			String lbl = row.has(labelKey) ? row.get(labelKey).getAsString() : "?";
			graphics.text(font, truncate(lbl, 6), bx, y + 12, 0xFFCCCCCC, false);
		}
	}

	private static List<Double> extractValues(JsonArray history, String valueKey) {
		List<Double> out = new ArrayList<>();
		if (history == null) {
			return out;
		}
		List<JsonElement> sorted = new ArrayList<>();
		history.forEach(sorted::add);
		sorted.sort(Comparator.comparingLong(el -> {
			if (el.isJsonObject() && el.getAsJsonObject().has("recordedAt")) {
				return el.getAsJsonObject().get("recordedAt").getAsLong();
			}
			return 0L;
		}));
		for (JsonElement el : sorted) {
			if (el.isJsonObject()) {
				out.add(itemValue(el.getAsJsonObject(), valueKey));
			}
		}
		return out;
	}

	private static double itemValue(JsonObject row, String key) {
		if (row.has(key)) {
			return row.get(key).getAsDouble();
		}
		if ("priceMg".equals(key) && row.has("price")) {
			return row.get("price").getAsDouble();
		}
		return 0;
	}

	private static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
		int dx = Math.abs(x1 - x0);
		int dy = Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx - dy;
		int x = x0;
		int y = y0;
		while (true) {
			graphics.fill(x, y, x + 1, y + 1, color);
			if (x == x1 && y == y1) {
				break;
			}
			int e2 = 2 * err;
			if (e2 > -dy) {
				err -= dy;
				x += sx;
			}
			if (e2 < dx) {
				err += dx;
				y += sy;
			}
		}
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}
}
