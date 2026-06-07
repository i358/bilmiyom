package com.mceconomy.exchange;

import java.util.UUID;

/** Oyuncu coin pozisyonu ve ortalama maliyet bazı. */
public final class TokenHolding {
	private int amount;
	private long costBasisMg;

	public TokenHolding(int amount, long costBasisMg) {
		this.amount = amount;
		this.costBasisMg = Math.max(0, costBasisMg);
	}

	public int amount() {
		return amount;
	}

	public long costBasisMg() {
		return costBasisMg;
	}

	public long averageCostMg() {
		return amount <= 0 ? 0 : costBasisMg / amount;
	}

	public void add(int bought, long costMg) {
		amount += bought;
		costBasisMg += costMg;
	}

	public long remove(int sold) {
		if (sold <= 0 || amount <= 0) {
			return 0;
		}
		int actual = Math.min(sold, amount);
		long avg = averageCostMg();
		long costRemoved = avg * actual;
		amount -= actual;
		costBasisMg = Math.max(0, costBasisMg - costRemoved);
		return costRemoved;
	}
}
