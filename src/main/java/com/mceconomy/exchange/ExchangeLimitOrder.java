package com.mceconomy.exchange;

import java.util.UUID;

public final class ExchangeLimitOrder {
	private final int id;
	private final UUID owner;
	private final String symbol;
	private final boolean isBuy;
	private final int amount;
	private final long limitPriceMg;
	private final long createdAt;
	private boolean open;

	public ExchangeLimitOrder(int id, UUID owner, String symbol, boolean isBuy, int amount,
			long limitPriceMg, long createdAt, boolean open) {
		this.id = id;
		this.owner = owner;
		this.symbol = symbol.toUpperCase();
		this.isBuy = isBuy;
		this.amount = amount;
		this.limitPriceMg = limitPriceMg;
		this.createdAt = createdAt;
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

	public boolean isBuy() {
		return isBuy;
	}

	public int amount() {
		return amount;
	}

	public long limitPriceMg() {
		return limitPriceMg;
	}

	public long createdAt() {
		return createdAt;
	}

	public boolean isOpen() {
		return open;
	}

	public void cancel() {
		open = false;
	}
}
