package com.mceconomy.market;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.util.EconomyMath;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/** Nadirlik ve deger skoruna gore otomatik taban fiyat. */
public final class ItemPriceHeuristic {
	private ItemPriceHeuristic() {
	}

	public static long computeBasePriceMg(Item item, String itemId) {
		if (item == null || item == Items.AIR) {
			return 0;
		}
		double score = scoreItem(item, itemId);
		ValueTier tier = ValueTier.fromScore(score);
		double category = categoryFactor(item, itemId);
		double stackBonus = stackBonus(item.getDefaultMaxStackSize());
		long raw = Math.round(GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT * tier.wheatMultiplier() * category * stackBonus);
		return EconomyMath.clampPrice(raw, raw, EconomyConfig.minPriceMultiplier(), EconomyConfig.maxPriceMultiplier());
	}

	public static ValueTier tierFor(Item item, String itemId) {
		return ValueTier.fromScore(scoreItem(item, itemId));
	}

	private static double scoreItem(Item item, String itemId) {
		double score = 0;
		Rarity rarity = item.components().get(net.minecraft.core.component.DataComponents.RARITY);
		if (rarity != null) {
			score += switch (rarity) {
				case COMMON -> 4;
				case UNCOMMON -> 12;
				case RARE -> 40;
				case EPIC -> 120;
			};
		} else {
			score += 4;
		}
		score += equipmentTierScore(itemId);
		score += idPatternScore(itemId);
		var food = item.components().get(net.minecraft.core.component.DataComponents.FOOD);
		if (food != null) {
			score += food.nutrition() * 1.5 + food.saturation() * 8;
		}
		if (item.getDefaultMaxStackSize() == 1) {
			score += 25;
		} else if (item.getDefaultMaxStackSize() <= 16) {
			score += 8;
		}
		return score;
	}

	private static double equipmentTierScore(String itemId) {
		String path = path(itemId);
		if (path.contains("netherite")) {
			return 120;
		}
		if (path.contains("diamond")) {
			return 55;
		}
		if (path.contains("golden_") || path.contains("gold_")) {
			return 28;
		}
		if (path.contains("iron_")) {
			return 22;
		}
		if (path.contains("stone_") || path.contains("cobblestone")) {
			return 10;
		}
		if (path.contains("wooden_") || path.contains("_wood")) {
			return 6;
		}
		if (path.contains("helmet") || path.contains("chestplate") || path.contains("leggings") || path.contains("boots")) {
			return 18;
		}
		if (path.contains("sword") || path.contains("pickaxe") || path.contains("axe") || path.contains("shovel") || path.contains("hoe")) {
			return 14;
		}
		return 0;
	}

	private static double idPatternScore(String itemId) {
		String path = path(itemId);
		if (path.contains("spawn_egg")) {
			return 220;
		}
		if (path.contains("dragon_egg") || path.equals("enchanted_golden_apple")) {
			return 180;
		}
		if (path.contains("elytra") || path.contains("totem")) {
			return 100;
		}
		if (path.contains("disc") || path.contains("music_disc")) {
			return 35;
		}
		if (path.contains("shulker")) {
			return 70;
		}
		if (path.contains("beacon") || path.contains("conduit")) {
			return 85;
		}
		if (path.contains("raw_") || path.contains("ingot") || path.contains("gem")) {
			return 15;
		}
		return 0;
	}

	private static String path(String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		return id != null ? id.getPath() : itemId;
	}

	private static double categoryFactor(Item item, String itemId) {
		if (itemId.contains("spawn_egg")) {
			return 1.8;
		}
		if (item instanceof BlockItem) {
			return 0.6;
		}
		if (item.components().has(net.minecraft.core.component.DataComponents.FOOD)) {
			return 1.2;
		}
		String p = path(itemId);
		if (p.contains("sword") || p.contains("pickaxe") || p.contains("helmet") || p.contains("chestplate")) {
			return 1.5;
		}
		if (p.contains("banner") || p.contains("pottery") || p.contains("music_disc")) {
			return 0.4;
		}
		return 1.0;
	}

	private static double stackBonus(int maxStack) {
		if (maxStack <= 1) {
			return 1.5;
		}
		if (maxStack <= 16) {
			return 1.1;
		}
		return 1.0;
	}

	public static boolean isExcluded(Item item, String itemId) {
		if (item == null || item == Items.AIR) {
			return true;
		}
		String path = path(itemId);
		return path.equals("gold_ingot")
				|| path.equals("barrier")
				|| path.equals("structure_void")
				|| path.equals("command_block")
				|| path.equals("chain_command_block")
				|| path.equals("repeating_command_block")
				|| path.equals("jigsaw")
				|| path.equals("debug_stick")
				|| path.equals("knowledge_book")
				|| path.equals("light");
	}

	public static boolean defaultBuyable(ValueTier tier, String itemId) {
		return !(tier == ValueTier.LEGENDARY && itemId.contains("spawn_egg"));
	}

	public static String itemId(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	public static Item resolveItem(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return Items.AIR;
		}
		String full = itemId.contains(":") ? itemId : "minecraft:" + itemId;
		try {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(full));
			return item != null ? item : Items.AIR;
		} catch (Exception e) {
			return Items.AIR;
		}
	}

	public static String displayName(Item item) {
		return new ItemStack(item).getHoverName().getString();
	}
}
