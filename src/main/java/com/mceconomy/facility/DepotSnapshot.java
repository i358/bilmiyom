package com.mceconomy.facility;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Depo sandigi icerik ozeti — sabah soygun tespiti icin. */
public final class DepotSnapshot {
	public record ItemFingerprint(String itemId, int count) {
	}

	private final Map<String, Integer> counts = new HashMap<>();

	public static DepotSnapshot fromStacks(List<ItemStack> stacks) {
		DepotSnapshot snapshot = new DepotSnapshot();
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			snapshot.counts.merge(id, stack.getCount(), Integer::sum);
		}
		return snapshot;
	}

	public List<ItemFingerprint> fingerprints() {
		List<ItemFingerprint> list = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			list.add(new ItemFingerprint(entry.getKey(), entry.getValue()));
		}
		return list;
	}

	public List<ItemFingerprint> missingFrom(DepotSnapshot current) {
		List<ItemFingerprint> missing = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			int now = current.counts.getOrDefault(entry.getKey(), 0);
			if (now < entry.getValue()) {
				missing.add(new ItemFingerprint(entry.getKey(), entry.getValue() - now));
			}
		}
		return missing;
	}

	public boolean hasLossComparedTo(DepotSnapshot current) {
		return !missingFrom(current).isEmpty();
	}
}
