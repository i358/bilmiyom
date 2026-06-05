package com.mceconomy;

import com.mceconomy.network.EconomyNetworking;
import net.fabricmc.api.ModInitializer;

/** Ortak baslatma — payload codec kayitlari client ve sunucuda bir kez calisir. */
public class McEconomyModInitializer implements ModInitializer {
	@Override
	public void onInitialize() {
		EconomyNetworking.registerPlayPayloads();
	}
}
