package com.mceconomy;

import com.mceconomy.client.EconomyHudOverlay;
import com.mceconomy.client.EconomyHudState;
import com.mceconomy.network.EconomyHudPayload;
import com.mceconomy.McEconomyMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class McEconomyClientMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		try {
			PayloadTypeRegistry.clientboundPlay().register(EconomyHudPayload.TYPE, EconomyHudPayload.STREAM_CODEC);
		} catch (Throwable t) {
			// Sunucu tarafi ayni JVM'de zaten kaydetmis olabilir; yok say.
			McEconomyMod.LOGGER.warn("HUD payload kaydi atlandi: {}", t.getMessage());
		}
		try {
			ClientPlayNetworking.registerGlobalReceiver(EconomyHudPayload.TYPE, (payload, context) ->
					context.client().execute(() -> EconomyHudState.update(
							payload.walletMg(),
							payload.bankMg(),
							payload.dirtyMg(),
							payload.frozen(),
							payload.blacklisted(),
							payload.jobLabel())));
			EconomyHudOverlay.register();
		} catch (Throwable t) {
			McEconomyMod.LOGGER.error("HUD overlay baslatilamadi, oyun yine de calisacak", t);
		}
	}
}
