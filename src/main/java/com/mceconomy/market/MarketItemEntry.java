package com.mceconomy.market;

import net.minecraft.world.item.Item;

public record MarketItemEntry(
		String itemId,
		Item item,
		long basePriceMg,
		ValueTier valueTier,
		boolean sellable,
		boolean buyable,
		String displayName
) {
}
