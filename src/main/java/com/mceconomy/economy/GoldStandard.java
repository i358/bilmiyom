package com.mceconomy.economy;

/**
 * Altın standardı: 1000 gram = 1 Minecraft altın külçesi (1 kg).
 * 100 buğday = 100 gram altın (1 buğday = 1 gram).
 * Dahili birim: altın miligramı (mgAu).
 */
public final class GoldStandard {
	public static final long GRAMS_PER_INGOT = 1000;
	public static final long MILLIGRAMS_PER_GRAM = 1000;
	public static final long MILLIGRAMS_PER_INGOT = GRAMS_PER_INGOT * MILLIGRAMS_PER_GRAM;

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

	/** 1 altin kulcesinin guncel MC degeri. */
	public static double ingotPriceMc() {
		return GRAMS_PER_INGOT * goldFactor;
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
		double mc = milligrams / (double) MILLIGRAMS_PER_GRAM;
		String number;
		if (mc == Math.floor(mc)) {
			number = String.format("%,d", (long) mc);
		} else {
			number = String.format("%,.2f", mc);
		}
		return number + " " + CURRENCY_NAME;
	}

	public static String formatWheatExchange() {
		return "1 bugday ≈ 1 MC | 1 altin kulcesi = " + String.format("%,.0f", ingotPriceMc()) + " MC";
	}
}
