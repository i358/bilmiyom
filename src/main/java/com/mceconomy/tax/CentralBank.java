package com.mceconomy.tax;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.EconomyEventCategory;
import com.mceconomy.economy.EconomyEventDirection;
import com.mceconomy.economy.EconomyEventService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.persistence.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class CentralBank {
	/** long tasmasini ve dashboard grafik bozulmasini onlemek icin ust sinir. */
	public static final long MAX_MUNICIPAL_BUDGET_MG = 10_000_000_000_000_000L;

	private final DatabaseManager database;
	private EconomyEventService economyEventService;
	private double baseRate;
	private long moneySupply;
	private double inflationRate;
	private double economyIndex;
	private double goldFactor = 1.0;
	private long municipalBudgetMg;
	/** Fiat gucu: 1.0 = notr; yuksek = MC guclu (ucuz emtia). */
	private double fiatStrength = 1.0;
	private double goldBackingScore = 0.5;
	private double stateCredibilityScore = 0.5;
	private double investmentScore = 0.5;
	private double fiatShockPenalty;

	public CentralBank(DatabaseManager database) {
		this.database = database;
	}

	public void bindEconomyEventService(EconomyEventService economyEventService) {
		this.economyEventService = economyEventService;
	}

	public void load() throws SQLException {
		try (PreparedStatement ps = database.connection().prepareStatement("SELECT * FROM central_bank WHERE id = 1");
			 ResultSet rs = ps.executeQuery()) {
			if (rs.next()) {
				baseRate = rs.getDouble("base_rate");
				moneySupply = rs.getLong("money_supply");
				inflationRate = rs.getDouble("inflation_rate");
				economyIndex = rs.getDouble("economy_index");
				double gf = rs.getDouble("gold_factor");
				goldFactor = gf > 0 ? gf : 1.0;
				try {
					municipalBudgetMg = rs.getLong("municipal_budget_mg");
				} catch (SQLException ignored) {
					municipalBudgetMg = 0;
				}
				loadFiatColumns(rs);
				if (normalizeMunicipalBudget()) {
					save();
				}
			} else {
				save();
			}
		}
	}

	public void save() throws SQLException {
		try (PreparedStatement ps = database.connection().prepareStatement("""
				INSERT INTO central_bank(id, base_rate, money_supply, inflation_rate, economy_index, gold_factor,
					municipal_budget_mg, fiat_strength, gold_backing_score, state_credibility_score,
					investment_score, fiat_shock_penalty)
				VALUES(1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(id) DO UPDATE SET
					base_rate=excluded.base_rate,
					money_supply=excluded.money_supply,
					inflation_rate=excluded.inflation_rate,
					economy_index=excluded.economy_index,
					gold_factor=excluded.gold_factor,
					municipal_budget_mg=excluded.municipal_budget_mg,
					fiat_strength=excluded.fiat_strength,
					gold_backing_score=excluded.gold_backing_score,
					state_credibility_score=excluded.state_credibility_score,
					investment_score=excluded.investment_score,
					fiat_shock_penalty=excluded.fiat_shock_penalty
				""")) {
			ps.setDouble(1, baseRate);
			ps.setLong(2, moneySupply);
			ps.setDouble(3, inflationRate);
			ps.setDouble(4, economyIndex);
			ps.setDouble(5, goldFactor);
			ps.setLong(6, municipalBudgetMg);
			ps.setDouble(7, fiatStrength);
			ps.setDouble(8, goldBackingScore);
			ps.setDouble(9, stateCredibilityScore);
			ps.setDouble(10, investmentScore);
			ps.setDouble(11, fiatShockPenalty);
			ps.executeUpdate();
		}
	}

	private void loadFiatColumns(ResultSet rs) {
		try {
			fiatStrength = positiveOrDefault(rs.getDouble("fiat_strength"), 1.0);
			goldBackingScore = clamp01(rs.getDouble("gold_backing_score"));
			stateCredibilityScore = clamp01(rs.getDouble("state_credibility_score"));
			investmentScore = clamp01(rs.getDouble("investment_score"));
			fiatShockPenalty = Math.max(0, rs.getDouble("fiat_shock_penalty"));
		} catch (SQLException ignored) {
			fiatStrength = 1.0;
			goldBackingScore = 0.5;
			stateCredibilityScore = 0.5;
			investmentScore = 0.5;
			fiatShockPenalty = 0;
		}
	}

	private static double positiveOrDefault(double value, double fallback) {
		return value > 0 && !Double.isNaN(value) && !Double.isInfinite(value) ? value : fallback;
	}

	private static double clamp01(double v) {
		return Math.max(0, Math.min(1, v));
	}

	public double getBaseRate() {
		return baseRate;
	}

	public void setBaseRate(double baseRate) {
		this.baseRate = baseRate;
	}

	public long getMoneySupply() {
		return moneySupply;
	}

	public void updateMoneySupply(long supply) {
		this.moneySupply = supply;
	}

	public double getInflationRate() {
		return inflationRate;
	}

	public void setInflationRate(double inflationRate) {
		this.inflationRate = inflationRate;
	}

	public double getEconomyIndex() {
		return economyIndex;
	}

	public void setEconomyIndex(double economyIndex) {
		this.economyIndex = economyIndex;
	}

	public double getGoldFactor() {
		return goldFactor;
	}

	public void setGoldFactor(double goldFactor) {
		this.goldFactor = goldFactor;
	}

	public long getMunicipalBudgetMg() {
		return Math.max(0, municipalBudgetMg);
	}

	public void addMunicipalBudget(long amount) {
		addMunicipalBudget(amount, "BUDGET", "Belediye butcesi artisi");
	}

	public void addMunicipalBudget(long amount, String source, String description) {
		if (amount <= 0) {
			return;
		}
		long current = getMunicipalBudgetMg();
		if (current >= MAX_MUNICIPAL_BUDGET_MG) {
			return;
		}
		long next = current + amount;
		if (next < current || next > MAX_MUNICIPAL_BUDGET_MG) {
			municipalBudgetMg = MAX_MUNICIPAL_BUDGET_MG;
		} else {
			municipalBudgetMg = next;
		}
		if (economyEventService != null) {
			economyEventService.recordMunicipal(EconomyEventCategory.TAX_IN, EconomyEventDirection.IN, amount,
					source, description + ": " + GoldStandard.formatMilligrams(amount));
		}
	}

	public void setMunicipalBudgetMg(long municipalBudgetMg) {
		this.municipalBudgetMg = Math.min(MAX_MUNICIPAL_BUDGET_MG, Math.max(0, municipalBudgetMg));
	}

	public boolean spendMunicipalBudget(long amount) {
		return spendMunicipalBudget(amount, "SPEND", "Belediye harcamasi");
	}

	public boolean spendMunicipalBudget(long amount, String source, String description) {
		long current = getMunicipalBudgetMg();
		if (amount <= 0 || current < amount) {
			return false;
		}
		municipalBudgetMg = current - amount;
		if (economyEventService != null) {
			economyEventService.recordMunicipal(EconomyEventCategory.SPEND_OUT, EconomyEventDirection.OUT, amount,
					source, description + ": " + GoldStandard.formatMilligrams(amount));
		}
		return true;
	}

	private boolean normalizeMunicipalBudget() {
		long before = municipalBudgetMg;
		if (municipalBudgetMg < 0) {
			McEconomyMod.LOGGER.warn("Belediye butcesi negatifti (muhtemel long tasmasi), sifirlandi: {}", before);
			municipalBudgetMg = 0;
			return true;
		}
		if (municipalBudgetMg > MAX_MUNICIPAL_BUDGET_MG) {
			McEconomyMod.LOGGER.warn("Belediye butcesi ust sinira cekildi: {} -> {}", before, MAX_MUNICIPAL_BUDGET_MG);
			municipalBudgetMg = MAX_MUNICIPAL_BUDGET_MG;
			return true;
		}
		return false;
	}

	public void adjustBaseRate(double targetInflation, double currentInflation) {
		if (currentInflation > targetInflation + 0.01) {
			baseRate = Math.min(0.2, baseRate + 0.005);
		} else if (currentInflation < targetInflation - 0.01) {
			baseRate = Math.max(0.01, baseRate - 0.005);
		}
	}

	public double getFiatStrength() {
		return fiatStrength;
	}

	public void setFiatStrength(double fiatStrength) {
		this.fiatStrength = fiatStrength;
	}

	public double getGoldBackingScore() {
		return goldBackingScore;
	}

	public void setGoldBackingScore(double goldBackingScore) {
		this.goldBackingScore = clamp01(goldBackingScore);
	}

	public double getStateCredibilityScore() {
		return stateCredibilityScore;
	}

	public void setStateCredibilityScore(double stateCredibilityScore) {
		this.stateCredibilityScore = clamp01(stateCredibilityScore);
	}

	public double getInvestmentScore() {
		return investmentScore;
	}

	public void setInvestmentScore(double investmentScore) {
		this.investmentScore = clamp01(investmentScore);
	}

	public double getFiatShockPenalty() {
		return fiatShockPenalty;
	}

	public void setFiatShockPenalty(double fiatShockPenalty) {
		this.fiatShockPenalty = Math.max(0, Math.min(1, fiatShockPenalty));
	}
}
