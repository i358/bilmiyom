package com.mceconomy.market;

public final class MarketItemState {
	private final String itemId;
	private double price;
	private final double basePrice;
	private double supplyIndex;
	private double demandIndex;

	public MarketItemState(String itemId, double price, double basePrice, double supplyIndex, double demandIndex) {
		this.itemId = itemId;
		this.price = price;
		this.basePrice = basePrice;
		this.supplyIndex = supplyIndex;
		this.demandIndex = demandIndex;
	}

	public static MarketItemState createDefault(MarketItemEntry entry) {
		return new MarketItemState(entry.itemId(), entry.basePriceMg(), entry.basePriceMg(), 0, 0);
	}

	public String itemId() {
		return itemId;
	}

	public double price() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public double basePrice() {
		return basePrice;
	}

	public double supplyIndex() {
		return supplyIndex;
	}

	public double demandIndex() {
		return demandIndex;
	}

	public void addDemand(int quantity, double factor) {
		demandIndex += quantity;
		price *= (1 + factor * quantity);
	}

	public void addSupply(int quantity, double factor) {
		supplyIndex += quantity;
		price *= Math.max(0.1, 1 - factor * quantity);
	}

	public void decay(double decayRate) {
		supplyIndex = Math.max(0, supplyIndex - decayRate);
		demandIndex = Math.max(0, demandIndex - decayRate);
		double equilibrium = basePrice * (1 + (demandIndex - supplyIndex) * 0.01);
		price = price * 0.95 + equilibrium * 0.05;
	}
}
