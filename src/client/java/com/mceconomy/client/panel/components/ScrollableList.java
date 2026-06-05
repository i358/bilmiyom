package com.mceconomy.client.panel.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Kaydirilabilir satir listesi — scroll offset, tekerlek ve satir cizimi. */
public final class ScrollableList {
	private final List<String> rows = new ArrayList<>();
	private int scrollY;
	private int rowHeight = 14;
	private int visibleRows = 10;

	public ScrollableList rowHeight(int h) {
		this.rowHeight = Math.max(10, h);
		return this;
	}

	public ScrollableList visibleRows(int n) {
		this.visibleRows = Math.max(1, n);
		return this;
	}

	public void setRows(List<String> lines) {
		rows.clear();
		if (lines != null) {
			rows.addAll(lines);
		}
		clampScroll();
	}

	/** renderRows icin satir sayisi — metin drawer tarafindan cizilir. */
	public void setRowCount(int count) {
		rows.clear();
		for (int i = 0; i < Math.max(0, count); i++) {
			rows.add("");
		}
		clampScroll();
	}

	public void clear() {
		rows.clear();
		scrollY = 0;
	}

	public int scrollY() {
		return scrollY;
	}

	public void setScrollY(int y) {
		scrollY = Math.max(0, y);
		clampScroll();
	}

	public void scrollBy(int delta) {
		setScrollY(scrollY - delta * rowHeight);
	}

	public int maxScroll() {
		return Math.max(0, rows.size() * rowHeight - visibleRows * rowHeight);
	}

	public int rowCount() {
		return rows.size();
	}

	public String rowAt(int index) {
		return index >= 0 && index < rows.size() ? rows.get(index) : "";
	}

	public int hitRow(int x, int y, int listX, int listY, int listW, int listH) {
		if (x < listX || x >= listX + listW || y < listY || y >= listY + listH) {
			return -1;
		}
		int rel = y - listY + scrollY;
		int idx = rel / rowHeight;
		return idx >= 0 && idx < rows.size() ? idx : -1;
	}

	public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
			int textColor, Integer selectedIndex) {
		graphics.fill(x, y, x + w, y + h, 0x44000000);
		int start = scrollY / rowHeight;
		int yOff = -(scrollY % rowHeight);
		for (int i = start; i < rows.size() && yOff < h; i++) {
			int ry = y + yOff;
			if (ry + rowHeight > y && ry < y + h) {
				if (selectedIndex != null && selectedIndex == i) {
					graphics.fill(x, ry, x + w, ry + rowHeight, 0x664488FF);
				}
				graphics.text(font, truncate(rows.get(i), w, font), x + 4, ry + 3, textColor, false);
			}
			yOff += rowHeight;
		}
		if (rows.size() > visibleRows) {
			int barH = Math.max(8, h * visibleRows / rows.size());
			int barY = y + (maxScroll() == 0 ? 0 : scrollY * (h - barH) / maxScroll());
			graphics.fill(x + w - 3, barY, x + w - 1, barY + barH, 0xAAE8C547);
		}
	}

	public void renderRows(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
			BiConsumer<GuiGraphicsExtractor, RowContext> drawer, Integer selectedIndex) {
		graphics.fill(x, y, x + w, y + h, 0x44000000);
		int start = scrollY / rowHeight;
		int yOff = -(scrollY % rowHeight);
		for (int i = start; i < rows.size() && yOff < h; i++) {
			int ry = y + yOff;
			if (ry + rowHeight > y && ry < y + h) {
				boolean sel = selectedIndex != null && selectedIndex == i;
				if (sel) {
					graphics.fill(x, ry, x + w, ry + rowHeight, 0x664488FF);
				}
				drawer.accept(graphics, new RowContext(i, x, ry, w, rowHeight, rows.get(i), sel));
			}
			yOff += rowHeight;
		}
	}

	private void clampScroll() {
		scrollY = Math.min(scrollY, maxScroll());
	}

	private static String truncate(String s, int width, Font font) {
		if (s == null) {
			return "";
		}
		String t = s;
		while (t.length() > 1 && font.width(t) > width - 8) {
			t = t.substring(0, t.length() - 2) + "…";
		}
		return t;
	}

	public record RowContext(int index, int x, int y, int width, int height, String text, boolean selected) {
	}
}
