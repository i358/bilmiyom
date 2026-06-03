package com.mceconomy.security;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Ruhsatli / ruhsatsiz guvenlik silahlari. */
public enum SecurityWeapon {
	GUARD_SIDEARM("muhafiz_tabancasi", Items.CROSSBOW, 5.0f, true),
	GUARD_RIFLE("muhafiz_tufegi", Items.BOW, 7.0f, true),
	RIOT_SHOTGUN("gocuk_tufegi", Items.IRON_AXE, 9.0f, true),
	BLACK_PISTOL("ruhsatsiz_tabanca", Items.GOLDEN_SWORD, 6.0f, false),
	BLACK_SMG("ruhsatsiz_smg", Items.IRON_SWORD, 4.5f, false);

	private final String id;
	private final Item item;
	private final float bonusDamage;
	private final boolean licensed;

	SecurityWeapon(String id, Item item, float bonusDamage, boolean licensed) {
		this.id = id;
		this.item = item;
		this.bonusDamage = bonusDamage;
		this.licensed = licensed;
	}

	public String id() {
		return id;
	}

	public Item item() {
		return item;
	}

	public float bonusDamage() {
		return bonusDamage;
	}

	public boolean licensed() {
		return licensed;
	}

	public String displayName() {
		return switch (this) {
			case GUARD_SIDEARM -> "§bMuhafiz Tabancasi";
			case GUARD_RIFLE -> "§bMuhafiz Tufegi";
			case RIOT_SHOTGUN -> "§bGocuk Tufegi";
			case BLACK_PISTOL -> "§4Ruhsatsiz Tabanca";
			case BLACK_SMG -> "§4Ruhsatsiz SMG";
		};
	}

	public static SecurityWeapon fromItem(Item item) {
		for (SecurityWeapon weapon : values()) {
			if (weapon.item == item) {
				return weapon;
			}
		}
		return null;
	}

	public static SecurityWeapon[] blackMarketWeapons() {
		return new SecurityWeapon[] { BLACK_PISTOL, BLACK_SMG, RIOT_SHOTGUN };
	}

	public static SecurityWeapon[] guardLoadout() {
		return new SecurityWeapon[] { GUARD_SIDEARM, GUARD_RIFLE, RIOT_SHOTGUN };
	}
}
