package com.mceconomy.trade;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class TradeItemCodec {
	private static final Gson GSON = new Gson();
	private static final Type LIST_TYPE = new TypeToken<List<StackEntry>>() {}.getType();

	private TradeItemCodec() {
	}

	public record StackEntry(String itemId, int count) {
	}

	public static List<StackEntry> parse(String json) {
		if (json == null || json.isBlank()) {
			return new ArrayList<>();
		}
		List<StackEntry> list = GSON.fromJson(json, LIST_TYPE);
		return list != null ? list : new ArrayList<>();
	}

	public static String encode(List<StackEntry> stacks) {
		return GSON.toJson(stacks != null ? stacks : List.of());
	}

	public static Item resolve(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return Items.AIR;
		}
		Identifier id = Identifier.tryParse(itemId.contains(":") ? itemId : "minecraft:" + itemId);
		if (id == null) {
			return Items.AIR;
		}
		return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
	}

	public static String normalizeItemId(String itemId) {
		Identifier id = Identifier.tryParse(itemId.contains(":") ? itemId : "minecraft:" + itemId);
		return id != null ? id.toString() : itemId;
	}

	public static int countItem(net.minecraft.server.level.ServerPlayer player, Item item) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public static boolean removeItem(net.minecraft.server.level.ServerPlayer player, Item item, int count) {
		if (countItem(player, item) < count) {
			return false;
		}
		int remaining = count;
		for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.is(item)) {
				continue;
			}
			int take = Math.min(stack.getCount(), remaining);
			stack.shrink(take);
			remaining -= take;
		}
		return remaining == 0;
	}

	public static void giveItems(net.minecraft.server.level.ServerPlayer player, Item item, int count) {
		ItemStack stack = new ItemStack(item, count);
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	public static void giveEncoded(net.minecraft.server.level.ServerPlayer player, String json) {
		for (StackEntry entry : parse(json)) {
			Item item = resolve(entry.itemId());
			if (item != Items.AIR && entry.count() > 0) {
				giveItems(player, item, entry.count());
			}
		}
	}
}
