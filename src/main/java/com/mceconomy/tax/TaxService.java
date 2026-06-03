package com.mceconomy.tax;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.util.EconomyMath;

public final class TaxService {
	private long collectedTaxes;
	private CentralBank centralBank;

	public void bindCentralBank(CentralBank centralBank) {
		this.centralBank = centralBank;
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
		if (amount <= 0) {
			return;
		}
		collectedTaxes += amount;
		if (centralBank != null) {
			centralBank.addMunicipalBudget(amount);
		}
	}

	public long collectedTaxes() {
		return collectedTaxes;
	}
}
