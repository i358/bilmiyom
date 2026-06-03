package com.mceconomy.event;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.market.MarketPriceEngine;
import com.mceconomy.tax.CentralBank;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Random;

public final class EconomyEventManager {
	private static final Random RANDOM = new Random();

	private EconomyEventType activeEvent;
	private long eventEndTime;
	private double previousPriceMultiplier = 1.0;

	public void tick(MinecraftServer server, MarketPriceEngine priceEngine, CentralBank centralBank) {
		if (activeEvent != null && System.currentTimeMillis() >= eventEndTime) {
			endEvent(priceEngine, centralBank);
		}

		if (activeEvent == null && RANDOM.nextDouble() < EconomyConfig.randomEventChance()) {
			EconomyEventType[] values = EconomyEventType.values();
			triggerEvent(values[RANDOM.nextInt(values.length)], 5 * 60 * 1000, priceEngine, centralBank, server);
		}
	}

	public boolean triggerEvent(EconomyEventType type, long durationMs, MarketPriceEngine priceEngine,
			CentralBank centralBank, MinecraftServer server) {
		if (type == null) {
			return false;
		}
		if (activeEvent != null) {
			endEvent(priceEngine, centralBank);
		}
		activeEvent = type;
		eventEndTime = System.currentTimeMillis() + durationMs;
		previousPriceMultiplier = priceEngine.globalMultiplier();
		priceEngine.setGlobalMultiplier(previousPriceMultiplier * type.priceMultiplier());
		centralBank.setInflationRate(centralBank.getInflationRate() * type.inflationMultiplier());

		server.getPlayerList().broadcastSystemMessage(
				Component.literal("§6[Ekonomi] §e" + type.id() + " olayı başladı!"), false);
		return true;
	}

	public void endEvent(MarketPriceEngine priceEngine, CentralBank centralBank) {
		if (activeEvent == null) {
			return;
		}
		priceEngine.setGlobalMultiplier(previousPriceMultiplier);
		activeEvent = null;
		eventEndTime = 0;
	}

	public EconomyEventType activeEvent() {
		return activeEvent;
	}
}
