package com.mceconomy.market;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Ham maden eritme ve yemek pisirme kurallari. */
public final class CommodityProcessing {
	private static final Map<Commodity, Commodity> SMELT = new EnumMap<>(Commodity.class);
	private static final Map<Commodity, Item> COOK = new EnumMap<>(Commodity.class);

	static {
		SMELT.put(Commodity.RAW_COPPER, Commodity.COPPER_INGOT);
		SMELT.put(Commodity.RAW_IRON, Commodity.IRON);
		SMELT.put(Commodity.RAW_GOLD, Commodity.GOLD);
		COOK.put(Commodity.BEEF, Items.COOKED_BEEF);
		COOK.put(Commodity.PORKCHOP, Items.COOKED_PORKCHOP);
		COOK.put(Commodity.CHICKEN, Items.COOKED_CHICKEN);
		COOK.put(Commodity.MUTTON, Items.COOKED_MUTTON);
		COOK.put(Commodity.RABBIT, Items.COOKED_RABBIT);
		COOK.put(Commodity.POTATO, Items.BAKED_POTATO);
		COOK.put(Commodity.COD, Items.COOKED_COD);
		COOK.put(Commodity.SALMON, Items.COOKED_SALMON);
	}

	private CommodityProcessing() {
	}

	public static boolean isSmeltable(Commodity commodity) {
		return SMELT.containsKey(commodity);
	}

	public static boolean isCookable(Commodity commodity) {
		return COOK.containsKey(commodity);
	}

	public static boolean isOre(Commodity commodity) {
		return commodity != null && commodity.jobCategory() == com.mceconomy.job.JobCategory.MINING;
	}

	public static Commodity smelt(Commodity raw) {
		return SMELT.getOrDefault(raw, raw);
	}

	public static Optional<Item> cookedItem(Commodity raw) {
		return Optional.ofNullable(COOK.get(raw));
	}

	/** Pazar satisi icin nihai emtia (ham maden ise kulce). */
	public static Commodity forMarket(Commodity produced) {
		if (produced == null) {
			return null;
		}
		return SMELT.getOrDefault(produced, produced);
	}
}
