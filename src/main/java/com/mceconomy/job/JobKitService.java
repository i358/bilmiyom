package com.mceconomy.job;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Meslege gore gecici alet/ malzeme odunc verir; gorev bitince geri alir. */
public final class JobKitService {
	private record KitEntry(Item item, int count) {
	}

	private JobKitService() {
	}

	public static boolean hasLoanItems(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (JobItemTags.isJobLoan(player.getInventory().getItem(slot))) {
				return true;
			}
		}
		return false;
	}

	public static void giveKit(ServerPlayer player, JobType job) {
		if (player == null || job == null) {
			return;
		}
		reclaimKit(player);
		List<ItemStack> stacks = buildKit(job);
		int given = 0;
		for (ItemStack stack : stacks) {
			if (player.getInventory().add(stack)) {
				given++;
			} else {
				player.drop(stack, false);
				given++;
			}
		}
		if (given > 0) {
			player.sendSystemMessage(Component.literal(
					"§a[Meslek] §f" + job.displayName() + " ekipmani gecici olarak verildi (" + given + " parca). "
							+ "Gorev tamamlaninca geri alinir."));
		}
	}

	public static int reclaimKit(ServerPlayer player) {
		if (player == null) {
			return 0;
		}
		int removed = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (JobItemTags.isJobLoan(stack)) {
				removed += stack.getCount();
				player.getInventory().setItem(slot, ItemStack.EMPTY);
			}
		}
		if (removed > 0) {
			player.sendSystemMessage(Component.literal(
					"§e[Meslek] §fGecici meslek ekipmanlari geri alindi (" + removed + " adet)."));
		}
		return removed;
	}

	private static List<ItemStack> buildKit(JobType job) {
		List<KitEntry> entries = switch (job) {
			case MINER, SMITH -> List.of(
					entry(Items.IRON_PICKAXE, 1),
					entry(Items.IRON_SHOVEL, 1),
					entry(Items.TORCH, 32));
			case FARMER, RANCHER -> List.of(
					entry(Items.IRON_HOE, 1),
					entry(Items.WHEAT_SEEDS, 32),
					entry(Items.BUCKET, 1));
			case LUMBERJACK -> List.of(entry(Items.IRON_AXE, 1));
			case FISHER -> List.of(entry(Items.FISHING_ROD, 1));
			case HUNTER -> List.of(
					entry(Items.IRON_SWORD, 1),
					entry(Items.BOW, 1),
					entry(Items.ARROW, 32));
			case TRADER -> List.of(
					entry(Items.COMPASS, 1),
					entry(Items.EMERALD, 5),
					entry(Items.PAPER, 16));
			case BUILDER -> List.of(
					entry(Items.IRON_PICKAXE, 1),
					entry(Items.IRON_AXE, 1),
					entry(Items.COBBLESTONE, 64));
		};
		List<ItemStack> stacks = new ArrayList<>();
		for (KitEntry e : entries) {
			stacks.add(loanStack(e.item(), e.count(), job));
		}
		return stacks;
	}

	private static KitEntry entry(Item item, int count) {
		return new KitEntry(item, count);
	}

	private static ItemStack loanStack(Item item, int count, JobType job) {
		ItemStack stack = new ItemStack(item, count);
		JobItemTags.markLoan(stack, job);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("§7[Meslek] §r")
				.append(stack.getHoverName()));
		return stack;
	}
}
