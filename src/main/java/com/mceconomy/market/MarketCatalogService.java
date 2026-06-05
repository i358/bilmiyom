package com.mceconomy.market;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MarketCatalogService {
	private final Map<String, MarketItemEntry> entries = new HashMap<>();
	private List<MarketItemEntry> sorted = List.of();

	public void bootstrap() {
		entries.clear();
		for (Item item : BuiltInRegistries.ITEM) {
			String itemId = ItemPriceHeuristic.itemId(item);
			if (ItemPriceHeuristic.isExcluded(item, itemId)) {
				continue;
			}
			Commodity commodity = Commodity.fromItem(item);
			long basePrice = commodity != null ? commodity.basePrice() : ItemPriceHeuristic.computeBasePriceMg(item, itemId);
			ValueTier tier = commodity != null
					? ValueTier.fromScore(Math.max(6, commodity.basePrice() / (double) GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT))
					: ItemPriceHeuristic.tierFor(item, itemId);
			boolean sellable = commodity != null ? commodity.sellable() : true;
			boolean buyable = commodity != null ? commodity.buyable() : ItemPriceHeuristic.defaultBuyable(tier, itemId);
			if (commodity == Commodity.GOLD) {
				sellable = false;
				buyable = false;
			}
			if (commodity == Commodity.NETHERITE) {
				buyable = false;
			}
			entries.put(itemId, new MarketItemEntry(
					itemId, item, basePrice, tier, sellable, buyable,
					commodity != null ? commodity.displayName() : ItemPriceHeuristic.displayName(item)));
		}
		sorted = entries.values().stream()
				.sorted(Comparator.comparing(MarketItemEntry::displayName, String.CASE_INSENSITIVE_ORDER))
				.toList();
		McEconomyMod.LOGGER.info("Pazar katalogu: {} item", entries.size());
	}

	public MarketItemEntry resolve(Item item) {
		if (item == null) {
			return null;
		}
		return entries.get(ItemPriceHeuristic.itemId(item));
	}

	public MarketItemEntry resolve(String itemId) {
		if (itemId == null) {
			return null;
		}
		return entries.get(itemId);
	}

	public Map<String, MarketItemEntry> entries() {
		return entries;
	}

	public List<MarketItemEntry> allSorted() {
		return sorted;
	}

	public List<MarketItemEntry> page(int page, int pageSize, String search, String filter) {
		List<MarketItemEntry> filtered = new ArrayList<>();
		String q = search == null ? "" : search.toLowerCase().trim();
		for (MarketItemEntry e : sorted) {
			if (!q.isEmpty() && !e.displayName().toLowerCase().contains(q) && !e.itemId().toLowerCase().contains(q)) {
				continue;
			}
			if ("sell".equals(filter) && !e.sellable()) {
				continue;
			}
			if ("buy".equals(filter) && !e.buyable()) {
				continue;
			}
			filtered.add(e);
		}
		int from = Math.max(0, page * pageSize);
		if (from >= filtered.size()) {
			return List.of();
		}
		int to = Math.min(filtered.size(), from + pageSize);
		return filtered.subList(from, to);
	}

	public int pageCount(int pageSize, String search, String filter) {
		String q = search == null ? "" : search.toLowerCase().trim();
		int count = 0;
		for (MarketItemEntry e : sorted) {
			if (!q.isEmpty() && !e.displayName().toLowerCase().contains(q) && !e.itemId().toLowerCase().contains(q)) {
				continue;
			}
			if ("sell".equals(filter) && !e.sellable()) {
				continue;
			}
			if ("buy".equals(filter) && !e.buyable()) {
				continue;
			}
			count++;
		}
		return Math.max(1, (count + pageSize - 1) / pageSize);
	}
}
