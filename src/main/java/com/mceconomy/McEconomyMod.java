package com.mceconomy;

import com.mceconomy.economy.EconomyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ortak mod sabitleri — entrypoint degil, client bu sinifi yuklemez. */
public final class McEconomyMod {
	public static final String MOD_ID = "mceconomy";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static EconomyManager economyManager;

	private McEconomyMod() {
	}

	static void bindEconomyManager(EconomyManager manager) {
		economyManager = manager;
	}

	public static EconomyManager getEconomyManager() {
		return economyManager;
	}
}
