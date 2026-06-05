package com.mceconomy.market;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.util.EconomyMath;

import java.util.Map;

public final class MarketPriceEngine {
	private final Map<String, MarketItemState> states;
	private final MarketCatalogService catalog;
	private double globalMultiplier = 1.0;

	public MarketPriceEngine(Map<String, MarketItemState> states, MarketCatalogService catalog) {
		this.states = states;
		this.catalog = catalog;
	}

	public void onBuy(String itemId, int quantity) {
		MarketItemState state = states.get(itemId);
		if (state == null) {
			return;
		}
		state.addDemand(quantity, EconomyConfig.demandFactor());
		clampPrice(state, itemId);
	}

	public void onBuy(Commodity commodity, int quantity) {
		if (commodity != null) {
			onBuy(ItemPriceHeuristic.itemId(commodity.item()), quantity);
		}
	}

	public void onSell(String itemId, int quantity) {
		MarketItemState state = states.get(itemId);
		if (state == null) {
			return;
		}
		state.addSupply(quantity, EconomyConfig.supplyFactor());
		clampPrice(state, itemId);
	}

	public void onSell(Commodity commodity, int quantity) {
		if (commodity != null) {
			onSell(ItemPriceHeuristic.itemId(commodity.item()), quantity);
		}
	}

	public long getUnitPrice(String itemId) {
		MarketItemEntry entry = catalog.resolve(itemId);
		if (entry == null) {
			return 0;
		}
		MarketItemState state = states.get(itemId);
		long base = entry.basePriceMg();
		if (state == null) {
			return base;
		}
		long raw = (long) Math.ceil(state.price() * globalMultiplier);
		return EconomyMath.clampPrice(raw, base, EconomyConfig.minPriceMultiplier(), EconomyConfig.maxPriceMultiplier());
	}

	public long getUnitPrice(Commodity commodity) {
		if (commodity == null) {
			return 0;
		}
		return getUnitPrice(ItemPriceHeuristic.itemId(commodity.item()));
	}

	public void decayAll() {
		for (MarketItemState state : states.values()) {
			state.decay(0.5);
			clampPrice(state, state.itemId());
		}
	}

	public void setGlobalMultiplier(double multiplier) {
		this.globalMultiplier = multiplier;
	}

	public double globalMultiplier() {
		return globalMultiplier;
	}

	public Map<String, MarketItemState> states() {
		return states;
	}

	public MarketItemState stateFor(String itemId) {
		return states.get(itemId);
	}

	private void clampPrice(MarketItemState state, String itemId) {
		MarketItemEntry entry = catalog.resolve(itemId);
		long base = entry != null ? entry.basePriceMg() : (long) state.basePrice();
		long clamped = EconomyMath.clampPrice(
				(long) state.price(),
				base,
				EconomyConfig.minPriceMultiplier(),
				EconomyConfig.maxPriceMultiplier()
		);
		state.setPrice(clamped);
	}
}
