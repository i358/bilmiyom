package com.mceconomy.company;

import com.mceconomy.config.EconomyConfig;

import java.util.UUID;

public final class Company {
	private int id;
	private final String name;
	private final UUID ownerUuid;
	private long treasury;
	private int outstandingShares;
	private final long createdAt;
	private boolean listedOnExchange;
	private String ticker;
	private long lifetimeRevenueMg;
	private long lastDividendAt;
	private boolean insolvent;

	public Company(int id, String name, UUID ownerUuid, long treasury, int outstandingShares, long createdAt,
			boolean listedOnExchange, String ticker, long lifetimeRevenueMg, long lastDividendAt, boolean insolvent) {
		this.id = id;
		this.name = name;
		this.ownerUuid = ownerUuid;
		this.treasury = treasury;
		this.outstandingShares = outstandingShares;
		this.createdAt = createdAt;
		this.listedOnExchange = listedOnExchange;
		this.ticker = ticker;
		this.lifetimeRevenueMg = Math.max(0, lifetimeRevenueMg);
		this.lastDividendAt = lastDividendAt;
		this.insolvent = insolvent;
	}

	public static Company create(String name, UUID owner) {
		return new Company(0, name, owner, 0, 100, System.currentTimeMillis(), false, null, 0, 0, false);
	}

	public int id() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String name() {
		return name;
	}

	public UUID ownerUuid() {
		return ownerUuid;
	}

	public long treasury() {
		return treasury;
	}

	public void deposit(long amount) {
		if (amount > 0) {
			treasury += amount;
			lifetimeRevenueMg += amount;
		}
	}

	public void withdraw(long amount) {
		treasury = Math.max(0, treasury - amount);
	}

	public void setTreasury(long treasury) {
		this.treasury = Math.max(0, treasury);
	}

	public int outstandingShares() {
		return outstandingShares;
	}

	public long createdAt() {
		return createdAt;
	}

	public boolean listedOnExchange() {
		return listedOnExchange;
	}

	public String ticker() {
		return ticker;
	}

	public long lifetimeRevenueMg() {
		return lifetimeRevenueMg;
	}

	public long lastDividendAt() {
		return lastDividendAt;
	}

	public void setLastDividendAt(long lastDividendAt) {
		this.lastDividendAt = lastDividendAt;
	}

	public boolean insolvent() {
		return insolvent;
	}

	public void markInsolvent() {
		insolvent = true;
		listedOnExchange = false;
		ticker = null;
	}

	public void listOnExchange(String ticker) {
		if (!insolvent) {
			this.listedOnExchange = true;
			this.ticker = ticker;
		}
	}

	public void delistFromExchange() {
		this.listedOnExchange = false;
		this.ticker = null;
	}

	/** Defter degeri + gelir carpani (fundamental pricing). */
	public long sharePrice(double economyIndex) {
		long bookValue = treasury + (long) (economyIndex * 10);
		long earningsPremium = Math.round(lifetimeRevenueMg * EconomyConfig.companyFundamentalRevenueWeight());
		long value = bookValue + earningsPremium;
		return Math.max(1, value / outstandingShares);
	}
}
