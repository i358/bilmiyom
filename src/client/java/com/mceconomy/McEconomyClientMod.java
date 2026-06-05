package com.mceconomy;

import com.mceconomy.client.EconomyHudOverlay;
import com.mceconomy.client.EconomyHudState;
import com.mceconomy.client.VehicleHudOverlay;
import com.mceconomy.client.VehicleHudState;
import com.mceconomy.client.VehicleInputCapture;
import com.mceconomy.client.panel.EconomyPanelClientState;
import com.mceconomy.client.panel.EconomyPanelScreen;
import com.mceconomy.network.EconomyHudPayload;
import com.mceconomy.network.EconomyNetworking;
import com.mceconomy.network.EconomyPanelOpenPayload;
import com.mceconomy.network.EconomyPanelSyncPayload;
import com.mceconomy.network.VehicleStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class McEconomyClientMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EconomyNetworking.registerPlayPayloads();

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

		try {
			VehicleInputCapture.register();
			ClientPlayNetworking.registerGlobalReceiver(VehicleStatePayload.TYPE, (payload, context) ->
					context.client().execute(() -> VehicleHudState.update(
							payload.speed(), payload.fuel(),
							payload.model() != null && !payload.model().isBlank() ? payload.model() : "sedan")));
			VehicleHudOverlay.register();
		} catch (Throwable t) {
			McEconomyMod.LOGGER.warn("Arac client networking: {}", t.getMessage());
		}

		try {
			ClientPlayNetworking.registerGlobalReceiver(EconomyPanelOpenPayload.TYPE, (payload, context) ->
					context.client().execute(() -> {
						EconomyPanelClientState.setTab(payload.initialTab());
						context.client().setScreen(new EconomyPanelScreen());
					}));
			ClientPlayNetworking.registerGlobalReceiver(EconomyPanelSyncPayload.TYPE, (payload, context) ->
					context.client().execute(() -> {
						EconomyPanelClientState.applySync(payload.tab(), payload.json());
						if (context.client().screen instanceof EconomyPanelScreen screen) {
							screen.refreshAfterSync();
						}
					}));
		} catch (Throwable t) {
			McEconomyMod.LOGGER.warn("Ekonomi panel client networking: {}", t.getMessage());
		}
	}
}
