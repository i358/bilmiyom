package com.mceconomy.economy;

/**
 * Hibrit para: MC fiat (devlet guveni + yatirim + kismi altin destegi);
 * fiziksel altin fiyati goldFactor ile ayri ayarlanir.
 */
public final class GoldStandard {
	public static final long GRAMS_PER_INGOT = 1000;
	public static final long MILLIGRAMS_PER_GRAM = 1000;
	public static final long MILLIGRAMS_PER_INGOT = GRAMS_PER_INGOT * MILLIGRAMS_PER_GRAM;
	public static final int INGOTS_PER_BLOCK = 9;
	public static final long MILLIGRAMS_PER_BLOCK = MILLIGRAMS_PER_INGOT * INGOTS_PER_BLOCK;

	public static final int WHEAT_PER_100_GRAMS = 100;
	public static final long MILLIGRAMS_GOLD_PER_WHEAT = (100 * MILLIGRAMS_PER_GRAM) / WHEAT_PER_100_GRAMS;

	/** Gorunen para birimi. Dahili: 1000 mg = 1 $ (goldFactor ile carpilir). */
	public static final String CURRENCY_NAME = "$";
	public static final long MILLIGRAMS_PER_DISPLAY_UNIT = 1000L;

	/**
	 * Altinin MC cinsinden degeri (1.0 = baz). Enflasyon arttikca yukselir:
	 * 1 altin kulcesi = 1000 * goldFactor MC. Boylece fiziksel altin enflasyona karsi koruma saglar
	 * ve paranin (MC) altina karsi degeri duser.
	 */
	private static volatile double goldFactor = 1.0;
	/** Fiat gucu (1.0 = notr). Yuksek = MC satin alma gucu artar. */
	private static volatile double fiatStrength = 1.0;

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

	public static double fiatStrength() {
		return fiatStrength;
	}

	public static void setFiatStrength(double strength) {
		if (strength > 0 && !Double.isInfinite(strength) && !Double.isNaN(strength)) {
			fiatStrength = strength;
		}
	}

	/** 1 altin kulcesinin guncel $ degeri. */
	public static double ingotPriceMc() {
		return (MILLIGRAMS_PER_INGOT / (double) MILLIGRAMS_PER_DISPLAY_UNIT) * goldFactor;
	}

	public static double blockPriceMc() {
		return (MILLIGRAMS_PER_BLOCK / (double) MILLIGRAMS_PER_DISPLAY_UNIT) * goldFactor;
	}

	public static double gramPriceMc() {
		return (MILLIGRAMS_PER_GRAM / (double) MILLIGRAMS_PER_DISPLAY_UNIT) * goldFactor;
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
			return CURRENCY_NAME + "0";
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
		return CURRENCY_NAME + number;
	}

	public static double milligramsToMc(long milligrams) {
		return (milligrams / (double) MILLIGRAMS_PER_DISPLAY_UNIT) * goldFactor;
	}

	/** Ekranda gorunen $ tutarini dahili mg birimine cevirir. */
	public static long milligramsForDisplayMc(double displayDollars) {
		if (displayDollars <= 0) {
			return 0;
		}
		return Math.max(1L, Math.round(displayDollars * MILLIGRAMS_PER_DISPLAY_UNIT / Math.max(0.001, goldFactor)));
	}

	public static String formatWheatExchange() {
		return "1 gram altin = " + CURRENCY_NAME + String.format("%,.0f", gramPriceMc()) + " | 1 kulce = "
				+ CURRENCY_NAME + String.format("%,.0f", ingotPriceMc()) + " | 1 blok = "
				+ CURRENCY_NAME + String.format("%,.0f", blockPriceMc());
	}
}
