package com.mceconomy.exchange;

import com.mceconomy.config.EconomyConfig;

import java.util.UUID;

/** Kaldiracli (CFD tarzi) borsa pozisyonu. Token fiyatini oracle olarak kullanir. */
public final class LeveragePosition {
	private final int id;
	private final UUID owner;
	private final String symbol;
	private final boolean isLong;
	private final int leverage;
	private long marginMg;
	private final long entryPriceMg;
	private long sizeMilliTokens;
	private final long openedAt;
	private boolean open;

	public LeveragePosition(int id, UUID owner, String symbol, boolean isLong, int leverage,
			long marginMg, long entryPriceMg, long sizeMilliTokens, long openedAt, boolean open) {
		this.id = id;
		this.owner = owner;
		this.symbol = symbol;
		this.isLong = isLong;
		this.leverage = leverage;
		this.marginMg = marginMg;
		this.entryPriceMg = entryPriceMg;
		this.sizeMilliTokens = sizeMilliTokens;
		this.openedAt = openedAt;
		this.open = open;
	}

	public int id() {
		return id;
	}

	public UUID owner() {
		return owner;
	}

	public String symbol() {
		return symbol;
	}

	public boolean isLong() {
		return isLong;
	}

	public int leverage() {
		return leverage;
	}

	public long marginMg() {
		return marginMg;
	}

	public void addMargin(long amountMg) {
		if (amountMg > 0) {
			marginMg += amountMg;
		}
	}

	public long entryPriceMg() {
		return entryPriceMg;
	}

	public long sizeMilliTokens() {
		return sizeMilliTokens;
	}

	public void reduceSize(long closeMilli) {
		if (closeMilli > 0 && closeMilli < sizeMilliTokens) {
			long marginClose = (marginMg * closeMilli) / sizeMilliTokens;
			marginMg -= marginClose;
			sizeMilliTokens -= closeMilli;
		}
	}

	public long openedAt() {
		return openedAt;
	}

	public boolean isOpen() {
		return open;
	}

	public void close() {
		this.open = false;
	}

	public long pnlMg(long currentPriceMg) {
		long diff = isLong ? (currentPriceMg - entryPriceMg) : (entryPriceMg - currentPriceMg);
		return (sizeMilliTokens * diff) / 1000L;
	}

	public long pnlMg(long currentPriceMg, long sizeMilli) {
		long diff = isLong ? (currentPriceMg - entryPriceMg) : (entryPriceMg - currentPriceMg);
		return (sizeMilli * diff) / 1000L;
	}

	public long equityMg(long currentPriceMg) {
		return Math.max(0, marginMg + pnlMg(currentPriceMg));
	}

	public long notionalMg() {
		return (sizeMilliTokens * entryPriceMg) / 1000L;
	}

	public long maintenanceMarginMg() {
		return Math.max(1L, Math.round(marginMg * EconomyConfig.leverageMaintenanceMarginRatio()));
	}

	public boolean shouldLiquidate(long currentPriceMg) {
		return equityMg(currentPriceMg) <= maintenanceMarginMg();
	}

	public boolean isMarginCall(long currentPriceMg) {
		long equity = equityMg(currentPriceMg);
		long maintenance = maintenanceMarginMg();
		return equity <= maintenance * 1.1 && equity > maintenance;
	}

	/** Bakim marjina ulasilan fiyat (tahmini likidasyon fiyati). */
	public long liquidationPriceMg() {
		if (sizeMilliTokens <= 0) {
			return entryPriceMg;
		}
		long maintenance = maintenanceMarginMg();
		long pnlAtLiq = maintenance - marginMg;
		if (isLong) {
			long priceDelta = (pnlAtLiq * 1000L) / sizeMilliTokens;
			return Math.max(1, entryPriceMg + priceDelta);
		}
		long priceDelta = (pnlAtLiq * 1000L) / sizeMilliTokens;
		return Math.max(1, entryPriceMg - priceDelta);
	}
}
