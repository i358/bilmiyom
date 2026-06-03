package com.mceconomy.economy;

import com.mceconomy.bank.BankService;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.market.EconomyIndex;
import com.mceconomy.market.MarketPriceEngine;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.reserve.GoldReserveService;
import com.mceconomy.tax.CentralBank;

import java.util.Map;
import java.util.UUID;

public final class InflationSystem {
	private double previousIndex = 100.0;

	public void update(CentralBank centralBank, BankService bankService,
			Map<UUID, PlayerEconomyProfile> profiles, EconomyIndex economyIndex,
			MarketPriceEngine priceEngine, GoldReserveService goldReserve) {
		long walletTotal = profiles.values().stream()
				.mapToLong(p -> p.wallet().balance())
				.sum();
		long moneySupply = walletTotal + bankService.totalBankBalance();
		centralBank.updateMoneySupply(moneySupply);

		double currentIndex = economyIndex.calculate();
		centralBank.setEconomyIndex(currentIndex);

		if (previousIndex > 0) {
			double inflation = (currentIndex - previousIndex) / previousIndex;
			centralBank.setInflationRate(inflation);
			centralBank.adjustBaseRate(EconomyConfig.targetInflationRate(), inflation);

			if (inflation > EconomyConfig.targetInflationRate() + 0.05) {
				priceEngine.setGlobalMultiplier(priceEngine.globalMultiplier() * 1.02);
			} else if (inflation < EconomyConfig.targetInflationRate() - 0.05) {
				priceEngine.setGlobalMultiplier(priceEngine.globalMultiplier() * 0.98);
			}

			// Enflasyon pozitifken altinin MC degeri yukselir (para altina karsi deger kaybeder).
			// Deflasyonda sinirli olcude geri gelir. Faktor 1.0'in altina dusmez.
			double drift = inflation > 0 ? inflation * 0.5 : inflation * 0.2;
			double newFactor = Math.max(1.0, centralBank.getGoldFactor() * (1 + drift));
			centralBank.setGoldFactor(newFactor);
			GoldStandard.setGoldFactor(newFactor);
		}
		if (goldReserve != null) {
			goldReserve.applyReservePressure(centralBank, moneySupply);
			applyReserveBonus(centralBank, goldReserve, moneySupply);
		}
		previousIndex = currentIndex;
	}

	private void applyReserveBonus(CentralBank centralBank, GoldReserveService goldReserve, long moneySupplyMg) {
		double coverage = goldReserve.coverageRatio(moneySupplyMg);
		double target = EconomyConfig.targetGoldReserveCoverage();
		double strong = target * EconomyConfig.reserveBonusStrongCoverageMultiplier();
		if (coverage >= strong) {
			double reduced = Math.max(0.01, centralBank.getBaseRate() - EconomyConfig.reserveBonusRateReduction());
			centralBank.setBaseRate(reduced);
		}
	}
}
