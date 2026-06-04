package com.mceconomy.economy;

import com.mceconomy.bank.BankService;
import com.mceconomy.company.CompanyManager;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.exchange.ExchangeService;
import com.mceconomy.exchange.ForeignInvestorMarketService;
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
			MarketPriceEngine priceEngine, GoldReserveService goldReserve,
			ExchangeService exchangeService, CompanyManager companyManager,
			ForeignInvestorMarketService foreignInvestors) {
		long walletTotal = profiles.values().stream()
				.mapToLong(p -> p.wallet().balance())
				.sum();

		double currentIndex = economyIndex.calculate();
		centralBank.setEconomyIndex(currentIndex);

		double inflation = 0;
		if (previousIndex > 0) {
			inflation = (currentIndex - previousIndex) / previousIndex;
			centralBank.setInflationRate(inflation);
			centralBank.adjustBaseRate(EconomyConfig.targetInflationRate(), inflation);
		}

		FiatMonetarySystem.update(centralBank, bankService, walletTotal, inflation, priceEngine,
				goldReserve, exchangeService, companyManager, foreignInvestors);

		if (goldReserve != null) {
			applyReserveBonus(centralBank, goldReserve, centralBank.getMoneySupply());
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
