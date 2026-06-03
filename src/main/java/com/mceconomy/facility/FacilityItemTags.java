package com.mceconomy.facility;

import com.mceconomy.McEconomyMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Fiziksel depo, seri numarasi ve calinti isaretleme. */
public final class FacilityItemTags {
	private static final String KEY_DEPOT = "mceconomy_depot";
	private static final String KEY_SERIAL = "mceconomy_serial";
	/** Eski kayitlar; yeni adalet yalnizca seri no ile calisir. */
	private static final String KEY_STOLEN = "mceconomy_stolen_at";
	/** Karaborsada eritilmis altin parcacigi — evde 9 adet = 1 temiz kulce. */
	private static final String KEY_GOLD_PARTICLE = "mceconomy_gold_particle";

	private FacilityItemTags() {
	}

	/** MB seri no / depo izi tasiyan altin (kulce). */
	public static boolean isBankTrackedGold(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (getSerial(stack) != null) {
			return true;
		}
		if (matchesWantedSerial(stack)) {
			return true;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		String depot = data.copyTag().getString(KEY_DEPOT).orElse("");
		return FacilityType.PHYSICAL_GOLD.id().equals(depot);
	}

	public static boolean isGoldParticle(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBoolean(KEY_GOLD_PARTICLE).orElse(false);
	}

	public static void markGoldParticle(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		CompoundTag tag = copyOrNew(stack);
		tag.putBoolean(KEY_GOLD_PARTICLE, true);
		tag.remove(KEY_SERIAL);
		tag.remove(KEY_DEPOT);
		tag.remove(KEY_STOLEN);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("§eAltin parcacigi"));
	}

	public static void markCleanIngot(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		stack.remove(DataComponents.CUSTOM_DATA);
		stack.remove(DataComponents.CUSTOM_NAME);
	}

	public static void markDepot(ItemStack stack, FacilityType type) {
		if (stack.isEmpty()) {
			return;
		}
		CompoundTag tag = copyOrNew(stack);
		tag.putString(KEY_DEPOT, type.id());
		tag.remove(KEY_STOLEN);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static void markDepotWithSerial(ItemStack stack, FacilityType type, String serial) {
		if (stack.isEmpty() || serial == null || serial.isBlank()) {
			return;
		}
		CompoundTag tag = copyOrNew(stack);
		tag.putString(KEY_DEPOT, type.id());
		tag.putString(KEY_SERIAL, serial);
		tag.remove(KEY_STOLEN);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		applySerialDisplayName(stack);
	}

	public static FacilityType getDepotType(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		String depot = data.copyTag().getString(KEY_DEPOT).orElse("");
		if (depot.isBlank()) {
			return null;
		}
		for (FacilityType type : FacilityType.values()) {
			if (type.id().equals(depot)) {
				return type;
			}
		}
		return null;
	}

	/** MB depo zimmeti (seri no veya depo etiketi). */
	public static boolean isDepotAsset(ItemStack stack) {
		return getSerial(stack) != null || getDepotType(stack) != null;
	}

	/** Kayip esya iade edilirken hedef depo. */
	public static FacilityType resolveRecoveryDepot(ItemStack stack) {
		FacilityType tagged = getDepotType(stack);
		if (tagged != null) {
			return tagged;
		}
		if (stack.is(net.minecraft.world.item.Items.GOLD_INGOT)) {
			return FacilityType.PHYSICAL_GOLD;
		}
		return FacilityType.MARKET;
	}

	public static String getSerial(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		String serial = data.copyTag().getString(KEY_SERIAL).orElse("");
		return serial.isBlank() ? null : serial;
	}

	public static boolean matchesWantedSerial(ItemStack stack) {
		var manager = McEconomyMod.getEconomyManager();
		return manager != null && manager.bankAssetSerialRegistry() != null
				&& manager.bankAssetSerialRegistry().isWanted(stack);
	}

	/** Gecis: eski stolen etiketi (seri nosuz) — sabah taramasinda yok sayilir. */
	public static boolean isStolen(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(KEY_STOLEN);
	}

	public static void clearTheftMarks(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return;
		}
		CompoundTag tag = data.copyTag();
		tag.remove(KEY_STOLEN);
		tag.remove(KEY_SERIAL);
		if (tag.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
	}

	public static void clearStolen(ItemStack stack) {
		clearTheftMarks(stack);
	}

	/** Seri no — meslek odunc esyasi gibi gri italik (whisper) etiket. */
	public static void applySerialDisplayName(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		String serial = getSerial(stack);
		if (serial != null) {
			Style whisper = Style.EMPTY.withItalic(true).withColor(ChatFormatting.GRAY);
			stack.set(DataComponents.CUSTOM_NAME, Component.literal("§7")
					.append(Component.literal(serial).withStyle(whisper)));
		}
	}

	private static CompoundTag copyOrNew(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null ? data.copyTag().copy() : new CompoundTag();
	}
}
