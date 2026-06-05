package com.mceconomy.market;

public enum ValueTier {
	TRIVIAL(0.5),
	COMMON(2.0),
	UNCOMMON(10.0),
	RARE(80.0),
	EPIC(600.0),
	LEGENDARY(2000.0);

	private final double wheatMultiplier;

	ValueTier(double wheatMultiplier) {
		this.wheatMultiplier = wheatMultiplier;
	}

	public double wheatMultiplier() {
		return wheatMultiplier;
	}

	public static ValueTier fromScore(double score) {
		if (score >= 200) {
			return LEGENDARY;
		}
		if (score >= 81) {
			return EPIC;
		}
		if (score >= 36) {
			return RARE;
		}
		if (score >= 16) {
			return UNCOMMON;
		}
		if (score >= 6) {
			return COMMON;
		}
		return TRIVIAL;
	}
}
