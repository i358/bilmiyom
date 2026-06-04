package com.mceconomy.economy;

import com.mceconomy.bank.BankService;
import com.mceconomy.company.CompanyManager;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.exchange.ExchangeService;
import com.mceconomy.exchange.ForeignInvestorMarketService;
import com.mceconomy.market.MarketPriceEngine;
import com.mceconomy.reserve.GoldReserveService;
import com.mceconomy.tax.CentralBank;

/**
 * Fiat (itibari) para: MC degeri yalnizca fiziksel altin karsiligina bagli degil;
 * altin destegi, devlet guvenilirligi ve yatirim derinligi ile belirlenir.
 */
public final class FiatMonetarySystem {

	private FiatMonetarySystem() {
	}

	public static void update(CentralBank centralBank, BankService bankService, long walletTotal,
			double inflationRate, MarketPriceEngine priceEngine, GoldReserveService goldReserve,
			ExchangeService exchangeService, CompanyManager companyManager,
			ForeignInvestorMarketService foreignInvestors) {
		long moneySupply = Math.max(1L, walletTotal + bankService.totalBankBalance());
		centralBank.updateMoneySupply(moneySupply);

		double goldBackingScore = computeGoldBackingScore(goldReserve, moneySupply);
		double stateCredibility = computeStateCredibility(centralBank, moneySupply, inflationRate);
		double investmentScore = computeInvestmentScore(exchangeService, companyManager,
				foreignInvestors, moneySupply);

		centralBank.setGoldBackingScore(goldBackingScore);
		centralBank.setStateCredibilityScore(stateCredibility);
		centralBank.setInvestmentScore(investmentScore);

		double fiatStrength = computeFiatStrength(goldBackingScore, stateCredibility, investmentScore);
		centralBank.setFiatStrength(fiatStrength);
		GoldStandard.setFiatStrength(fiatStrength);

		applyGoldFactor(centralBank, goldBackingScore, fiatStrength, inflationRate);
		applyGlobalMultiplier(priceEngine, fiatStrength);
	}

	public static void applyMacroShock(CentralBank centralBank, double penalty) {
		if (penalty <= 0) {
			return;
		}
		double next = Math.min(1.0, centralBank.getFiatShockPenalty() + penalty);
		centralBank.setFiatShockPenalty(next);
	}

	private static double computeGoldBackingScore(GoldReserveService goldReserve, long moneySupplyMg) {
		if (goldReserve == null) {
			return 0.5;
		}
		double coverage = goldReserve.coverageRatio(moneySupplyMg);
		double target = EconomyConfig.targetGoldReserveCoverage();
		if (target <= 0) {
			return 0.5;
		}
		return clamp01(coverage / target);
	}

	private static double computeStateCredibility(CentralBank centralBank, long moneySupplyMg,
			double inflationRate) {
		double budgetSignal = clamp01(centralBank.getMunicipalBudgetMg()
				/ (double) Math.max(1L, Math.round(moneySupplyMg * EconomyConfig.fiatBudgetSupplyRatio())));
		double inflTarget = EconomyConfig.targetInflationRate();
		double inflStable = clamp01(1.0 - Math.abs(inflationRate - inflTarget)
				* EconomyConfig.fiatInflationStabilityFactor());
		double shock = centralBank.getFiatShockPenalty();
		double shockDecay = EconomyConfig.fiatShockDecayPerTick();
		centralBank.setFiatShockPenalty(shock * shockDecay);

		return clamp01(budgetSignal * 0.35 + inflStable * 0.40 + (1.0 - shock) * 0.25);
	}

	private static double computeInvestmentScore(ExchangeService exchangeService,
			CompanyManager companyManager, ForeignInvestorMarketService foreignInvestors,
			long moneySupplyMg) {
		long exchangeCap = exchangeService != null ? exchangeService.totalCirculatingMarketCapMg() : 0L;
		long companyCap = companyManager != null ? companyManager.totalTreasuryMg() : 0L;
		long investorCap = foreignInvestors != null ? foreignInvestors.totalInvestorCapitalMg() : 0L;
		long total = exchangeCap + companyCap + investorCap;
		double ref = moneySupplyMg * EconomyConfig.fiatInvestmentSupplyRatio();
		return clamp01(total / Math.max(1.0, ref));
	}

	private static double computeFiatStrength(double goldBacking, double stateCredibility,
			double investment) {
		double wGold = EconomyConfig.fiatWeightGold();
		double wState = EconomyConfig.fiatWeightState();
		double wInv = EconomyConfig.fiatWeightInvestment();
		double legGold = 0.35 + 0.65 * goldBacking;
		double legState = 0.35 + 0.65 * stateCredibility;
		double legInv = 0.35 + 0.65 * investment;
		double composite = wGold * legGold + wState * legState + wInv * legInv;
		return clamp(composite, EconomyConfig.fiatStrengthMin(), EconomyConfig.fiatStrengthMax());
	}

	private static void applyGoldFactor(CentralBank centralBank, double goldBackingScore,
			double fiatStrength, double inflationRate) {
		double inflDrift = inflationRate > 0
				? 1.0 + inflationRate * EconomyConfig.fiatInflationGoldDriftUp()
				: 1.0 + inflationRate * EconomyConfig.fiatInflationGoldDriftDown();
		double goldLeg = 1.0 + (1.0 - goldBackingScore) * EconomyConfig.fiatGoldDeficitBump();
		double fiatLeg = 1.0 / Math.max(0.5, fiatStrength);
		double wPhysical = EconomyConfig.fiatPhysicalGoldWeight();
		double target = wPhysical * goldLeg * inflDrift + (1.0 - wPhysical) * fiatLeg;
		target = clamp(target, EconomyConfig.fiatGoldFactorMin(), EconomyConfig.fiatGoldFactorMax());
		double blended = centralBank.getGoldFactor() * (1.0 - EconomyConfig.fiatGoldFactorSmoothing())
				+ target * EconomyConfig.fiatGoldFactorSmoothing();
		blended = clamp(blended, EconomyConfig.fiatGoldFactorMin(), EconomyConfig.fiatGoldFactorMax());
		centralBank.setGoldFactor(blended);
		GoldStandard.setGoldFactor(blended);
	}

	private static void applyGlobalMultiplier(MarketPriceEngine priceEngine, double fiatStrength) {
		if (priceEngine == null) {
			return;
		}
		double target = EconomyConfig.fiatBaseGlobalMultiplier() / Math.max(0.5, fiatStrength);
		double smooth = EconomyConfig.fiatGlobalMultiplierSmoothing();
		double next = priceEngine.globalMultiplier() * (1.0 - smooth) + target * smooth;
		priceEngine.setGlobalMultiplier(clamp(next,
				EconomyConfig.fiatGlobalMultiplierMin(),
				EconomyConfig.fiatGlobalMultiplierMax()));
	}

	private static double clamp01(double v) {
		return clamp(v, 0.0, 1.0);
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}
}
