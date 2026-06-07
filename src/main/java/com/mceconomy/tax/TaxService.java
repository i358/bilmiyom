package com.mceconomy.tax;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.EconomyEventCategory;
import com.mceconomy.economy.EconomyEventDirection;
import com.mceconomy.economy.EconomyEventService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.util.EconomyMath;

public final class TaxService {
	private long collectedTaxes;
	private CentralBank centralBank;
	private EconomyEventService economyEventService;

	public void bindCentralBank(CentralBank centralBank) {
		this.centralBank = centralBank;
	}

	public void bindEconomyEventService(EconomyEventService economyEventService) {
		this.economyEventService = economyEventService;
	}

	public long calculateIncomeTax(long amount) {
		return EconomyMath.applyTax(amount, EconomyConfig.incomeTaxRate());
	}

	public long calculateTradeTax(long amount) {
		return EconomyMath.applyTax(amount, EconomyConfig.tradeTaxRate());
	}

	public long calculateCityTax(long amount) {
		return EconomyMath.applyTax(amount, EconomyConfig.cityTaxRate());
	}

	public long calculateWealthTax(long totalWealth) {
		if (!EconomyConfig.wealthTaxEnabled()) {
			return 0;
		}
		return EconomyMath.applyTax(totalWealth, EconomyConfig.wealthTaxRate());
	}

	public void collectTax(long amount) {
		collectTax(amount, "TAX", "Vergi / komisyon tahsilati");
	}

	public void collectTax(long amount, String source, String description) {
		if (amount <= 0) {
			return;
		}
		collectedTaxes += amount;
		if (centralBank != null) {
			centralBank.addMunicipalBudget(amount, source, description);
		} else if (economyEventService != null) {
			economyEventService.recordMunicipal(EconomyEventCategory.TAX_IN, EconomyEventDirection.IN, amount,
					source, description + ": " + GoldStandard.formatMilligrams(amount));
		}
	}

	public long collectedTaxes() {
		return collectedTaxes;
	}
}
