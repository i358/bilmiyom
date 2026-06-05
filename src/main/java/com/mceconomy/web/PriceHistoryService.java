package com.mceconomy.web;

import com.mceconomy.McEconomyMod;
import com.mceconomy.company.Company;
import com.mceconomy.exchange.ExchangeToken;
import com.mceconomy.market.Commodity;
import com.mceconomy.market.MarketItemEntry;
import com.mceconomy.market.MarketItemState;
import com.mceconomy.persistence.repo.PriceHistoryRepository;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PriceHistoryService {
	private static final int MOVEMENT_WINDOW = 48;

	private final PriceHistoryRepository repository;
	private long lastRecordedAt;
	private volatile Map<String, Long> itemAbsChangeBps = Map.of();

	public PriceHistoryService(PriceHistoryRepository repository) {
		this.repository = repository;
	}

	public void recordSnapshot() {
		long now = System.currentTimeMillis();
		if (now - lastRecordedAt < 30_000) {
			return;
		}
		lastRecordedAt = now;
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || !manager.isLoaded()) {
			return;
		}
		try {
			for (MarketItemEntry entry : manager.marketService().catalog().allSorted()) {
				long price = manager.marketService().priceEngine().getUnitPrice(entry.itemId());
				repository.record("ITEM", entry.itemId(), price, now);
			}
			for (ExchangeToken token : manager.exchangeService().allTokens()) {
				repository.record("TOKEN", token.symbol(), token.priceMg(), now);
			}
			double economyIndex = manager.marketService().economyIndex().calculate();
			for (Company company : manager.companyManager().allCompanies()) {
				if (company.listedOnExchange() && company.ticker() != null && !company.ticker().isBlank()) {
					repository.record("SHARE", company.ticker(), company.sharePrice(economyIndex), now);
				}
			}
			long indexScaled = (long) (economyIndex * 1000);
			repository.record("INDEX", "economy", indexScaled, now);
			long inflationScaled = (long) (manager.centralBank().getInflationRate() * 10000);
			repository.record("MACRO", "inflation", inflationScaled, now);
			long reserveBlocks = manager.goldReserveService() != null
					? manager.goldReserveService().cachedGoldBlocks() : 0;
			repository.record("MACRO", "gold_reserve", reserveBlocks * 1000L, now);
			repository.record("MACRO", "municipal_budget", manager.centralBank().getMunicipalBudgetMg(), now);
			long fiatScaled = (long) (manager.centralBank().getFiatStrength() * 10_000);
			repository.record("MACRO", "fiat_strength", fiatScaled, now);
			for (var profile : manager.profiles().values()) {
				long termBal = manager.bankService().getTermBalanceMg(profile.uuid());
				if (termBal > 0) {
					repository.record("TERM", profile.uuid().toString(), termBal, now);
				}
			}
			repository.pruneOlderThan(now - 7L * 24 * 60 * 60 * 1000);
			refreshItemMovementCache(manager);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Fiyat geçmişi kaydedilemedi", e);
		}
	}

	public Map<String, Long> itemAbsChangeBps() {
		if (itemAbsChangeBps.isEmpty()) {
			var manager = McEconomyMod.getEconomyManager();
			if (manager != null && manager.isLoaded()) {
				try {
					refreshItemMovementCache(manager);
				} catch (SQLException e) {
					McEconomyMod.LOGGER.error("Item hareketlilik önbelleği güncellenemedi", e);
				}
			}
		}
		return itemAbsChangeBps;
	}

	public List<MarketItemEntry> chartItemsSortedByMovement(String search) {
		var market = McEconomyMod.getEconomyManager().marketService();
		Map<String, Long> movement = itemAbsChangeBps();
		String q = search == null ? "" : search.toLowerCase().trim();
		return market.catalog().allSorted().stream()
				.filter(entry -> q.isEmpty()
						|| entry.displayName().toLowerCase().contains(q)
						|| entry.itemId().toLowerCase().contains(q))
				.sorted(Comparator
						.comparingLong((MarketItemEntry entry) -> movement.getOrDefault(entry.itemId(), 0L)).reversed()
						.thenComparingDouble(entry -> marketActivity(market, entry.itemId())).reversed()
						.thenComparing(MarketItemEntry::displayName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private void refreshItemMovementCache(com.mceconomy.economy.EconomyManager manager) throws SQLException {
		Map<String, Long> itemOldest = repository.loadOldestPriceInWindow("ITEM", MOVEMENT_WINDOW);
		Map<String, Long> commodityOldest = repository.loadOldestPriceInWindow("COMMODITY", MOVEMENT_WINDOW);
		Map<String, Long> movement = new HashMap<>();
		var priceEngine = manager.marketService().priceEngine();
		for (MarketItemEntry entry : manager.marketService().catalog().allSorted()) {
			long current = priceEngine.getUnitPrice(entry.itemId());
			long oldest = itemOldest.getOrDefault(entry.itemId(), 0L);
			if (oldest <= 0) {
				Commodity commodity = Commodity.fromItem(entry.item());
				if (commodity != null) {
					oldest = commodityOldest.getOrDefault(commodity.id(), 0L);
				}
			}
			long absBps = oldest > 0 && current > 0
					? Math.abs(Math.round((current - oldest) * 10000.0 / oldest))
					: 0L;
			movement.put(entry.itemId(), absBps);
		}
		itemAbsChangeBps = Map.copyOf(movement);
	}

	private static double marketActivity(com.mceconomy.market.MarketService market, String itemId) {
		MarketItemState state = market.priceEngine().stateFor(itemId);
		return state != null ? state.supplyIndex() + state.demandIndex() : 0;
	}

	public PriceHistoryRepository repository() {
		return repository;
	}
}
