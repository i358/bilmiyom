package com.mceconomy.blackmarket;

import com.mceconomy.economy.GoldStandard;
import com.mceconomy.market.Commodity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Arrays;

/** Karaborsada işlem gören kaçak eşyalar — fiyatlar serbest piyasa (legal market) referansına göre. */
public enum IllegalGood {
	DIAMOND_HELMET("elmas_miğfer", Items.DIAMOND_HELMET, Commodity.DIAMOND, 8),
	DIAMOND_CHESTPLATE("elmas_gogusluk", Items.DIAMOND_CHESTPLATE, Commodity.DIAMOND, 12),
	DIAMOND_SWORD("elmas_kilic", Items.DIAMOND_SWORD, Commodity.DIAMOND, 6),
	NETHERITE_HELMET("netherite_miğfer", Items.NETHERITE_HELMET, Commodity.NETHERITE, 10),
	NETHERITE_CHESTPLATE("netherite_gogusluk", Items.NETHERITE_CHESTPLATE, Commodity.NETHERITE, 16),
	BOW("yay", Items.BOW, Commodity.IRON, 4),
	CROSSBOW("tatar_yayi", Items.CROSSBOW, Commodity.IRON, 5),
	TNT("tnt", Items.TNT, Commodity.COAL, 20),
	GOLDEN_APPLE("altin_elma", Items.GOLDEN_APPLE, Commodity.GOLD, 3);

	private final String id;
	private final Item item;
	private final Commodity priceReference;
	private final int unitMultiplier;

	IllegalGood(String id, Item item, Commodity priceReference, int unitMultiplier) {
		this.id = id;
		this.item = item;
		this.priceReference = priceReference;
		this.unitMultiplier = unitMultiplier;
	}

	public String id() {
		return id;
	}

	public Item item() {
		return item;
	}

	public long basePriceMg() {
		return priceReference.basePrice() * unitMultiplier;
	}

	public Commodity priceReference() {
		return priceReference;
	}

	public int unitMultiplier() {
		return unitMultiplier;
	}

	public String displayName() {
		return switch (this) {
			case DIAMOND_HELMET -> "Kaçak Elmas Miğfer";
			case DIAMOND_CHESTPLATE -> "Kaçak Elmas Göğüslük";
			case DIAMOND_SWORD -> "Kaçak Elmas Kılıç";
			case NETHERITE_HELMET -> "Kaçak Netherite Miğfer";
			case NETHERITE_CHESTPLATE -> "Kaçak Netherite Göğüslük";
			case BOW -> "Kaçak Yay";
			case CROSSBOW -> "Kaçak Tatar Yayı";
			case TNT -> "Kaçak TNT";
			case GOLDEN_APPLE -> "Kaçak Altın Elma";
		};
	}

	public static IllegalGood[] tradable() {
		return values();
	}

	public static IllegalGood fromId(String id) {
		for (IllegalGood good : values()) {
			if (good.id.equalsIgnoreCase(id) || good.name().equalsIgnoreCase(id)) {
				return good;
			}
		}
		return null;
	}

	public static IllegalGood fromItem(Item item) {
		return Arrays.stream(values()).filter(g -> g.item == item).findFirst().orElse(null);
	}
}
