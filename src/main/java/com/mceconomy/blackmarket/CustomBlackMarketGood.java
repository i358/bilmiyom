package com.mceconomy.blackmarket;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Admin tarafindan eklenen, istenen item ve istenen fiyatla karaborsa urunu. */
public final class CustomBlackMarketGood {
	private final String id;
	private final String displayName;
	private final String itemId;
	private final long priceMg;

	public CustomBlackMarketGood(String id, String displayName, String itemId, long priceMg) {
		this.id = id;
		this.displayName = displayName;
		this.itemId = itemId;
		this.priceMg = priceMg;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public String itemId() {
		return itemId;
	}

	public long priceMg() {
		return priceMg;
	}

	public Item resolveItem() {
		try {
			String full = itemId.contains(":") ? itemId : "minecraft:" + itemId;
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(full));
			return item != null ? item : Items.AIR;
		} catch (Exception e) {
			return Items.AIR;
		}
	}

	public boolean valid() {
		return resolveItem() != Items.AIR;
	}
}
