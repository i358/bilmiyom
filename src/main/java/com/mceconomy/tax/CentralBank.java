package com.mceconomy.tax;

import com.mceconomy.persistence.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class CentralBank {
	private final DatabaseManager database;
	private double baseRate;
	private long moneySupply;
	private double inflationRate;
	private double economyIndex;
	private double goldFactor = 1.0;
	private long municipalBudgetMg;

	public CentralBank(DatabaseManager database) {
		this.database = database;
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
			} else {
				save();
			}
		}
	}

	public void save() throws SQLException {
		try (PreparedStatement ps = database.connection().prepareStatement("""
				INSERT INTO central_bank(id, base_rate, money_supply, inflation_rate, economy_index, gold_factor, municipal_budget_mg)
				VALUES(1, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(id) DO UPDATE SET
					base_rate=excluded.base_rate,
					money_supply=excluded.money_supply,
					inflation_rate=excluded.inflation_rate,
					economy_index=excluded.economy_index,
					gold_factor=excluded.gold_factor,
					municipal_budget_mg=excluded.municipal_budget_mg
				""")) {
			ps.setDouble(1, baseRate);
			ps.setLong(2, moneySupply);
			ps.setDouble(3, inflationRate);
			ps.setDouble(4, economyIndex);
			ps.setDouble(5, goldFactor);
			ps.setLong(6, municipalBudgetMg);
			ps.executeUpdate();
		}
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
		return municipalBudgetMg;
	}

	public void addMunicipalBudget(long amount) {
		if (amount > 0) {
			municipalBudgetMg += amount;
		}
	}

	public boolean spendMunicipalBudget(long amount) {
		if (amount <= 0 || municipalBudgetMg < amount) {
			return false;
		}
		municipalBudgetMg -= amount;
		return true;
	}

	public void adjustBaseRate(double targetInflation, double currentInflation) {
		if (currentInflation > targetInflation + 0.01) {
			baseRate = Math.min(0.2, baseRate + 0.005);
		} else if (currentInflation < targetInflation - 0.01) {
			baseRate = Math.max(0.01, baseRate - 0.005);
		}
	}
}
