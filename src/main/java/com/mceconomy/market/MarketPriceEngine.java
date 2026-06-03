package com.mceconomy.market;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.util.EconomyMath;

import java.util.EnumMap;
import java.util.Map;

public final class MarketPriceEngine {
	private final Map<Commodity, CommodityState> states;
	private double globalMultiplier = 1.0;

	public MarketPriceEngine(Map<Commodity, CommodityState> states) {
		this.states = states;
	}

	public void onBuy(Commodity commodity, int quantity) {
		CommodityState state = states.get(commodity);
		if (state == null) {
			return;
		}
		state.addDemand(quantity, EconomyConfig.demandFactor());
		clampPrice(state);
	}

	public void onSell(Commodity commodity, int quantity) {
		CommodityState state = states.get(commodity);
		if (state == null) {
			return;
		}
		state.addSupply(quantity, EconomyConfig.supplyFactor());
		clampPrice(state);
	}

	public long getUnitPrice(Commodity commodity) {
		CommodityState state = states.get(commodity);
		if (state == null) {
			return commodity.basePrice();
		}
		long raw = (long) Math.ceil(state.price() * globalMultiplier);
		return EconomyMath.clampPrice(
				raw,
				commodity.basePrice(),
				EconomyConfig.minPriceMultiplier(),
				EconomyConfig.maxPriceMultiplier()
		);
	}

	public void decayAll() {
		for (CommodityState state : states.values()) {
			state.decay(0.5);
			clampPrice(state);
		}
	}

	public void setGlobalMultiplier(double multiplier) {
		this.globalMultiplier = multiplier;
	}

	public double globalMultiplier() {
		return globalMultiplier;
	}

	private void clampPrice(CommodityState state) {
		long clamped = EconomyMath.clampPrice(
				(long) state.price(),
				(long) state.basePrice(),
				EconomyConfig.minPriceMultiplier(),
				EconomyConfig.maxPriceMultiplier()
		);
		state.setPrice(clamped);
	}
}
