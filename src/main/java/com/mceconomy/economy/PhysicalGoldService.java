package com.mceconomy.economy;

import com.mceconomy.facility.FacilityItemTags;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.McEconomyMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class PhysicalGoldService {
	private PhysicalGoldService() {
	}

	public static int countGoldIngots(ServerPlayer player) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.GOLD_INGOT)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** Bankaya yatirilabilir (kayip seri no olmayan) kulce sayisi. */
	public static int countDepositEligibleGoldIngots(ServerPlayer player) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.GOLD_INGOT) && !FacilityItemTags.matchesWantedSerial(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public static boolean hasWantedGoldIngots(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.GOLD_INGOT) && FacilityItemTags.matchesWantedSerial(stack)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Yalnizca kayip seri numarasi tasimayan kulceleri alir.
	 * {@code removedOut} orijinal etiketleri korur (seri no dahil).
	 */
	public static boolean removeDepositEligibleGoldIngots(ServerPlayer player, int ingots, List<ItemStack> removedOut) {
		if (ingots <= 0 || countDepositEligibleGoldIngots(player) < ingots) {
			return false;
		}
		int remaining = ingots;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.is(Items.GOLD_INGOT) || FacilityItemTags.matchesWantedSerial(stack)) {
				continue;
			}
			int take = Math.min(stack.getCount(), remaining);
			ItemStack taken = stack.split(take);
			removedOut.add(taken);
			remaining -= take;
		}
		return remaining == 0;
	}

	public static boolean removeGoldIngots(ServerPlayer player, int ingots) {
		if (ingots <= 0 || countGoldIngots(player) < ingots) {
			return false;
		}
		int remaining = ingots;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.GOLD_INGOT)) {
				int take = Math.min(stack.getCount(), remaining);
				stack.shrink(take);
				remaining -= take;
			}
		}
		return remaining == 0;
	}

	public static boolean giveGoldIngots(ServerPlayer player, int ingots) {
		if (ingots <= 0) {
			return false;
		}
		int remaining = ingots;
		while (remaining > 0) {
			int stackSize = Math.min(remaining, Items.GOLD_INGOT.getDefaultMaxStackSize());
			ItemStack stack = new ItemStack(Items.GOLD_INGOT, stackSize);
			assignFreshSerial(stack);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			remaining -= stackSize;
		}
		return true;
	}

	public static boolean giveGoldNuggets(ServerPlayer player, int nuggets) {
		if (nuggets <= 0) {
			return false;
		}
		int remaining = nuggets;
		while (remaining > 0) {
			int stackSize = Math.min(remaining, Items.GOLD_NUGGET.getDefaultMaxStackSize());
			ItemStack stack = new ItemStack(Items.GOLD_NUGGET, stackSize);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			remaining -= stackSize;
		}
		return true;
	}

	public static boolean giveGoldStacks(ServerPlayer player, List<ItemStack> stacks) {
		if (stacks == null || stacks.isEmpty()) {
			return true;
		}
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack copy = stack.copy();
			if (!player.getInventory().add(copy)) {
				player.drop(copy, false);
			}
		}
		return true;
	}

	private static void assignFreshSerial(ItemStack stack) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.bankAssetSerialRegistry() != null) {
			manager.bankAssetSerialRegistry().assignSerial(stack, FacilityType.PHYSICAL_GOLD);
		} else {
			FacilityItemTags.markDepot(stack, FacilityType.PHYSICAL_GOLD);
		}
		FacilityItemTags.applySerialDisplayName(stack);
	}
}
