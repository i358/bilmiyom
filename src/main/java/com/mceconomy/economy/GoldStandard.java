package com.mceconomy.economy;

/**
 * Altın standardı: 1 gram = 1000 MC, 1000 gram = 1 külçe, 9 külçe = 1 blok.
 * Dahili birim: mgAu (1 mgAu = 1 MC, goldFactor ile carpilir).
 */
public final class GoldStandard {
	public static final long GRAMS_PER_INGOT = 1000;
	public static final long MILLIGRAMS_PER_GRAM = 1000;
	public static final long MILLIGRAMS_PER_INGOT = GRAMS_PER_INGOT * MILLIGRAMS_PER_GRAM;
	public static final int INGOTS_PER_BLOCK = 9;
	public static final long MILLIGRAMS_PER_BLOCK = MILLIGRAMS_PER_INGOT * INGOTS_PER_BLOCK;

	public static final int WHEAT_PER_100_GRAMS = 100;
	public static final long MILLIGRAMS_GOLD_PER_WHEAT = (100 * MILLIGRAMS_PER_GRAM) / WHEAT_PER_100_GRAMS;

	/** Para birimi adi: Minecraft Coins. Dahili birim: 1 MC = 1000 dahili (mg). */
	public static final String CURRENCY_NAME = "MC";

	/**
	 * Altinin MC cinsinden degeri (1.0 = baz). Enflasyon arttikca yukselir:
	 * 1 altin kulcesi = 1000 * goldFactor MC. Boylece fiziksel altin enflasyona karsi koruma saglar
	 * ve paranin (MC) altina karsi degeri duser.
	 */
	private static volatile double goldFactor = 1.0;

	private GoldStandard() {
	}

	public static double goldFactor() {
		return goldFactor;
	}

	public static void setGoldFactor(double factor) {
		if (factor > 0 && !Double.isInfinite(factor) && !Double.isNaN(factor)) {
			goldFactor = factor;
		}
	}

	/** 1 altin kulcesinin guncel MC degeri (1000 gram x 1000 MC). */
	public static double ingotPriceMc() {
		return (MILLIGRAMS_PER_INGOT / (double) MILLIGRAMS_PER_GRAM) * goldFactor;
	}

	public static double blockPriceMc() {
		return (MILLIGRAMS_PER_BLOCK / (double) MILLIGRAMS_PER_GRAM) * goldFactor;
	}

	public static double gramPriceMc() {
		return 1000.0 * goldFactor;
	}

	public static long gramsToMilligrams(long grams) {
		return grams * MILLIGRAMS_PER_GRAM;
	}

	public static long ingotsToMilligrams(int ingots) {
		return Math.round((double) ingots * MILLIGRAMS_PER_INGOT * goldFactor);
	}

	public static int milligramsToIngots(long milligrams) {
		return (int) (milligrams / Math.max(1.0, MILLIGRAMS_PER_INGOT * goldFactor));
	}

	public static long milligramsRemainder(long milligrams) {
		return milligrams % Math.max(1L, Math.round(MILLIGRAMS_PER_INGOT * goldFactor));
	}

	public static String formatMilligrams(long milligrams) {
		if (milligrams == 0) {
			return "0 " + CURRENCY_NAME;
		}
		if (milligrams < 0) {
			return "-" + formatMilligrams(-milligrams);
		}
		double mc = milligramsToMc(milligrams);
		String number;
		if (mc == Math.floor(mc)) {
			number = String.format("%,d", (long) mc);
		} else {
			number = String.format("%,.2f", mc);
		}
		return number + " " + CURRENCY_NAME;
	}

	public static double milligramsToMc(long milligrams) {
		return milligrams * goldFactor;
	}

	/** Ekranda gorunen MC tutarini cuzdan/banka dahili mg birimine cevirir. */
	public static long milligramsForDisplayMc(double displayMc) {
		if (displayMc <= 0) {
			return 0;
		}
		return Math.max(1L, Math.round(displayMc / Math.max(0.001, goldFactor)));
	}

	public static String formatWheatExchange() {
		return "1 gram altin = " + String.format("%,.0f", gramPriceMc()) + " MC | 1 kulce = "
				+ String.format("%,.0f", ingotPriceMc()) + " MC | 1 blok = "
				+ String.format("%,.0f", blockPriceMc()) + " MC";
	}
}
