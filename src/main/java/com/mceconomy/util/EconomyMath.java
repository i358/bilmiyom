package com.mceconomy.util;

public final class EconomyMath {
	private EconomyMath() {
	}

	public static long clampPrice(long price, long basePrice, double minMultiplier, double maxMultiplier) {
		long min = (long) (basePrice * minMultiplier);
		long max = (long) (basePrice * maxMultiplier);
		return Math.max(min, Math.min(max, price));
	}

	public static double applyMultiplier(double value, double multiplier) {
		return value * multiplier;
	}

	public static long applyTax(long amount, double taxRate) {
		return (long) Math.floor(amount * taxRate);
	}

	public static int clampCreditScore(int score) {
		return Math.max(300, Math.min(850, score));
	}
}
