package com.mceconomy.exchange;

import java.util.UUID;

/** Kaldiracli (CFD tarzi) borsa pozisyonu. Token fiyatini orakl olarak kullanir. */
public final class LeveragePosition {
	private final int id;
	private final UUID owner;
	private final String symbol;
	private final boolean isLong;
	private final int leverage;
	private final long marginMg;
	private final long entryPriceMg;
	private final long sizeMilliTokens;
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

	public long entryPriceMg() {
		return entryPriceMg;
	}

	public long sizeMilliTokens() {
		return sizeMilliTokens;
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

	/** Mevcut fiyata gore kar/zarar (mg). */
	public long pnlMg(long currentPriceMg) {
		long diff = isLong ? (currentPriceMg - entryPriceMg) : (entryPriceMg - currentPriceMg);
		return (sizeMilliTokens * diff) / 1000L;
	}

	/** Pozisyon ozsermayesi (margin + pnl), 0 altina dusmez. */
	public long equityMg(long currentPriceMg) {
		return Math.max(0, marginMg + pnlMg(currentPriceMg));
	}

	/** Zarar margin'i tukettiyse pozisyon likide edilir. */
	public boolean shouldLiquidate(long currentPriceMg) {
		return marginMg + pnlMg(currentPriceMg) <= 0;
	}
}
