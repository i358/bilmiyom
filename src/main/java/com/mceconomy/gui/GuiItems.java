package com.mceconomy.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class GuiItems {
	private GuiItems() {
	}

	public static ItemStack button(Item item, String title, String... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6" + title));
		if (lore.length > 0) {
			List<Component> lines = new java.util.ArrayList<>();
			for (String line : lore) {
				lines.add(Component.literal("§7" + line));
			}
			stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
		}
		return stack;
	}

	public static ItemStack filler() {
		return button(Items.GRAY_STAINED_GLASS_PANE, " ");
	}

	public static ItemStack backButton() {
		return button(Items.ARROW, "Geri", "Ana menüye dön");
	}

	public static ItemStack closeButton() {
		return button(Items.BARRIER, "Kapat", "Menüyü kapat");
	}
}
