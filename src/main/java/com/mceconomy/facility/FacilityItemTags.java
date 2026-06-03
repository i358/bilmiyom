package com.mceconomy.facility;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Fiziksel depo ve calinti esya isaretleme. */
public final class FacilityItemTags {
	private static final String KEY_DEPOT = "mceconomy_depot";
	private static final String KEY_STOLEN = "mceconomy_stolen_at";

	private FacilityItemTags() {
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

	public static void markStolen(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		CompoundTag tag = copyOrNew(stack);
		tag.putLong(KEY_STOLEN, System.currentTimeMillis());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static boolean isStolen(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(KEY_STOLEN);
	}

	public static void clearStolen(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return;
		}
		CompoundTag tag = data.copyTag();
		tag.remove(KEY_STOLEN);
		if (tag.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
	}

	private static CompoundTag copyOrNew(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null ? data.copyTag().copy() : new CompoundTag();
	}
}
