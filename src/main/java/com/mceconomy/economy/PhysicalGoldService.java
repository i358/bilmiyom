package com.mceconomy.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			remaining -= stackSize;
		}
		return true;
	}
}
