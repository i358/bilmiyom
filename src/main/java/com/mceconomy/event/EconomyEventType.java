package com.mceconomy.event;

public enum EconomyEventType {
	CRISIS("ekonomik_kriz", 0.7, 1.3),
	GOLD_RUSH("altin_patlamasi", 1.8, 1.0),
	CROP_BOOM("tarim_bollugu", 0.6, 1.0),
	INFLATION_SPIKE("enflasyon_spike", 1.0, 1.5),
	DEFLATION("deflasyon", 0.8, 0.7),
	MARKET_CRASH("market_cokusu", 0.5, 1.0);

	private final String id;
	private final double priceMultiplier;
	private final double inflationMultiplier;

	EconomyEventType(String id, double priceMultiplier, double inflationMultiplier) {
		this.id = id;
		this.priceMultiplier = priceMultiplier;
		this.inflationMultiplier = inflationMultiplier;
	}

	public String id() {
		return id;
	}

	public double priceMultiplier() {
		return priceMultiplier;
	}

	public double inflationMultiplier() {
		return inflationMultiplier;
	}

	public static EconomyEventType fromId(String id) {
		for (EconomyEventType type : values()) {
			if (type.id.equalsIgnoreCase(id) || type.name().equalsIgnoreCase(id)) {
				return type;
			}
		}
		return null;
	}
}
