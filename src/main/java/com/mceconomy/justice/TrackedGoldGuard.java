package com.mceconomy.justice;

import com.mceconomy.McEconomyMod;
import com.mceconomy.facility.FacilityItemTags;
import com.mceconomy.facility.FacilityType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Seri numarali / izlenen altin yalnizca kulce veya karaborsa parcacigi olabilir. */
public final class TrackedGoldGuard {
	private TrackedGoldGuard() {
	}

	public static void enforce(ServerPlayer player) {
		List<String> serialPool = collectSerials(player);
		boolean hasTracked = !serialPool.isEmpty();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(Items.GOLD_BLOCK) && (FacilityItemTags.isBankTrackedGold(stack) || hasTracked)
					&& !FacilityItemTags.isGoldParticle(stack)) {
				int ingots = stack.getCount() * 9;
				player.getInventory().setItem(slot, ItemStack.EMPTY);
				giveIngotsWithSerials(player, ingots, serialPool);
				player.sendSystemMessage(Component.literal(
						"§c[Adalet] §fCalinti altin blok haline getirilemez — kulcelere geri alindi."));
			} else if (stack.is(Items.GOLD_NUGGET) && !FacilityItemTags.isGoldParticle(stack) && hasTracked) {
				int ingots = Math.max(1, (stack.getCount() + 8) / 9);
				player.getInventory().setItem(slot, ItemStack.EMPTY);
				giveIngotsWithSerials(player, ingots, serialPool);
				player.sendSystemMessage(Component.literal(
						"§c[Adalet] §fCalinti altin parcaciga cevirlemez — yalnizca karaborsada eritme."));
			}
		}
		combineLaunderedParticles(player);
	}

	/** Yalnizca karaborsa eritmesinden gelen parcaciklar evde temiz kulceye donusur. */
	private static void combineLaunderedParticles(ServerPlayer player) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.GOLD_NUGGET) && FacilityItemTags.isGoldParticle(stack)) {
				total += stack.getCount();
			}
		}
		while (total >= 9) {
			if (!removeParticles(player, 9)) {
				break;
			}
			ItemStack ingot = new ItemStack(Items.GOLD_INGOT, 1);
			FacilityItemTags.markCleanIngot(ingot);
			if (!player.getInventory().add(ingot)) {
				player.drop(ingot, false);
			}
			player.sendSystemMessage(Component.literal(
					"§7[Karaborsa] §f9 aklanmis parcacik → 1 temiz altin kulcesi."));
			total -= 9;
		}
	}

	private static boolean removeParticles(ServerPlayer player, int amount) {
		int remaining = amount;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.is(Items.GOLD_NUGGET) || !FacilityItemTags.isGoldParticle(stack)) {
				continue;
			}
			int take = Math.min(stack.getCount(), remaining);
			stack.shrink(take);
			remaining -= take;
		}
		return remaining == 0;
	}

	private static List<String> collectSerials(ServerPlayer player) {
		List<String> serials = new ArrayList<>();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.is(Items.GOLD_INGOT)) {
				continue;
			}
			String serial = FacilityItemTags.getSerial(stack);
			if (serial == null) {
				continue;
			}
			for (int i = 0; i < stack.getCount(); i++) {
				serials.add(serial);
			}
		}
		return serials;
	}

	private static void giveIngotsWithSerials(ServerPlayer player, int ingots, List<String> serialPool) {
		var registry = McEconomyMod.getEconomyManager() != null
				? McEconomyMod.getEconomyManager().bankAssetSerialRegistry() : null;
		int serialIdx = 0;
		for (int i = 0; i < ingots; i++) {
			ItemStack one = new ItemStack(Items.GOLD_INGOT, 1);
			String serial = serialPool.isEmpty() ? null : serialPool.get(serialIdx++ % serialPool.size());
			if (serial != null) {
				FacilityItemTags.markDepotWithSerial(one, FacilityType.PHYSICAL_GOLD, serial);
				FacilityItemTags.applySerialDisplayName(one);
			} else if (registry != null) {
				registry.assignSerial(one, FacilityType.PHYSICAL_GOLD);
				FacilityItemTags.applySerialDisplayName(one);
			} else {
				FacilityItemTags.markDepot(one, FacilityType.PHYSICAL_GOLD);
			}
			if (!player.getInventory().add(one)) {
				player.drop(one, false);
			}
		}
	}
}
