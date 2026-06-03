package com.mceconomy.market;

public final class EconomyIndex {
	private final MarketPriceEngine priceEngine;

	public EconomyIndex(MarketPriceEngine priceEngine) {
		this.priceEngine = priceEngine;
	}

	public double calculate() {
		double total = 0;
		int count = 0;
		for (Commodity commodity : Commodity.values()) {
			long price = priceEngine.getUnitPrice(commodity);
			total += (double) price / commodity.basePrice();
			count++;
		}
		if (count == 0) {
			return 100.0;
		}
		return (total / count) * 100.0;
	}
}
