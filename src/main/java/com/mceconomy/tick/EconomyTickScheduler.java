package com.mceconomy.tick;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.EconomyManager;

public final class EconomyTickScheduler {
	private int tickCounter;

	public void onServerTick(EconomyManager manager) {
		tickCounter++;

		if (tickCounter % EconomyConfig.marketDecayIntervalTicks() == 0) {
			manager.onMarketTick();
		}
		if (tickCounter % EconomyConfig.interestIntervalTicks() == 0) {
			manager.onInterestTick();
			manager.onLoanTick();
			manager.onInsuranceTick();
		}
		if (tickCounter % EconomyConfig.inflationIntervalTicks() == 0) {
			manager.onInflationTick();
			manager.onWealthTaxTick();
		}
		if (tickCounter % EconomyConfig.eventCheckIntervalTicks() == 0) {
			manager.onEventTick();
		}
		if (tickCounter % EconomyConfig.playerSaveIntervalTicks() == 0) {
			manager.savePlayers();
		}
		if (tickCounter % EconomyConfig.workforceApplicationIntervalTicks() == 0) {
			manager.onWorkforceApplicationTick();
		}
		if (tickCounter % EconomyConfig.workforcePayrollIntervalTicks() == 0) {
			manager.onWorkforcePayrollTick();
		}
		if (tickCounter % EconomyConfig.npcEconomyIntervalTicks() == 0) {
			manager.onNpcEconomyTick();
		}
		if (tickCounter % EconomyConfig.taxEvasionAuditIntervalTicks() == 0) {
			manager.onTaxEvasionTick();
		}
		if (tickCounter % 1200 == 0) {
			manager.onStorageTick();
		}
		if (tickCounter % 1200 == 0) {
			manager.onGuildTick();
			manager.onMayorTick();
		}
		if (tickCounter % 40 == 0) {
			manager.syncHudForOnlinePlayers();
			manager.onTrackedGoldTick();
		}
		if (tickCounter % 2 == 0) {
			manager.onVehicleDriveTick();
		}
		if (tickCounter % 1 == 0) {
			manager.onStructureBuildTick();
		}
		if (tickCounter % 1200 == 0) {
			manager.onPropertyRentTick();
		}
		if (tickCounter % 600 == 0) {
			manager.onPriceHistoryTick();
		}
	}
}
