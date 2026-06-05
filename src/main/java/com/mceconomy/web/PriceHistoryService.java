package com.mceconomy.web;

import com.mceconomy.McEconomyMod;
import com.mceconomy.company.Company;
import com.mceconomy.exchange.ExchangeToken;
import com.mceconomy.market.MarketItemEntry;
import com.mceconomy.persistence.repo.PriceHistoryRepository;

import java.sql.SQLException;

public final class PriceHistoryService {
	private final PriceHistoryRepository repository;
	private long lastRecordedAt;

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
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Fiyat geçmişi kaydedilemedi", e);
		}
	}

	public PriceHistoryRepository repository() {
		return repository;
	}
}
