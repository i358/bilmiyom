package com.mceconomy.client.panel.components;

import com.mceconomy.client.panel.EconomyPanelClientState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** EditBox + Button satirlari eklemek icin yardimci. */
public final class FormFields {
	@FunctionalInterface
	public interface WidgetHost {
		<T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget);
	}

	private final WidgetHost host;
	private final Font font;
	private final int startX;
	private int y;
	private final List<EditBox> editBoxes = new ArrayList<>();

	public FormFields(WidgetHost host, Font font, int startX, int startY) {
		this.host = host;
		this.font = font;
		this.startX = startX;
		this.y = startY;
	}

	public List<EditBox> editBoxes() {
		return editBoxes;
	}

	public int cursorY() {
		return y;
	}

	public FormFields gap(int px) {
		y += px;
		return this;
	}

	public FormFields label(String text) {
		y += 12;
		return this;
	}

	public EditBox textField(String fieldKey, String placeholder, int width, String defaultValue) {
		String val = EconomyPanelClientState.formField(fieldKey, defaultValue);
		EditBox box = new EditBox(font, startX, y, width, 18, Component.literal(placeholder));
		box.setValue(val);
		box.setResponder(v -> EconomyPanelClientState.setFormField(fieldKey, v));
		host.add(box);
		editBoxes.add(box);
		y += 22;
		return box;
	}

	public EditBox numberField(String fieldKey, String placeholder, int width, String defaultValue) {
		return textField(fieldKey, placeholder, width, defaultValue);
	}

	public Button button(String label, int width, Runnable action) {
		Button btn = Button.builder(Component.literal(label), b -> action.run())
				.bounds(startX, y, width, 18).build();
		host.add(btn);
		y += 22;
		return btn;
	}

	public void row(Button... buttons) {
		int x = startX;
		for (Button b : buttons) {
			host.add(b);
			x += b.getWidth() + 4;
		}
		y += 22;
	}

	public static long parseLong(String s, long def) {
		if (s == null || s.isBlank()) {
			return def;
		}
		try {
			return Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	public static int parseInt(String s, int def) {
		if (s == null || s.isBlank()) {
			return def;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	public static double parseDouble(String s, double def) {
		if (s == null || s.isBlank()) {
			return def;
		}
		try {
			return Double.parseDouble(s.trim().replace(',', '.'));
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
