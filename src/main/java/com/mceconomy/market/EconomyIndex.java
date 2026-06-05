package com.mceconomy.market;

public final class EconomyIndex {
	private final MarketPriceEngine priceEngine;
	private final MarketCatalogService catalog;

	public EconomyIndex(MarketPriceEngine priceEngine, MarketCatalogService catalog) {
		this.priceEngine = priceEngine;
		this.catalog = catalog;
	}

	public double calculate() {
		double total = 0;
		int count = 0;
		for (MarketItemEntry entry : catalog.allSorted()) {
			if (!entry.sellable() && !entry.buyable()) {
				continue;
			}
			long price = priceEngine.getUnitPrice(entry.itemId());
			if (entry.basePriceMg() <= 0) {
				continue;
			}
			total += (double) price / entry.basePriceMg();
			count++;
		}
		if (count == 0) {
			return 100.0;
		}
		return (total / count) * 100.0;
	}
}
