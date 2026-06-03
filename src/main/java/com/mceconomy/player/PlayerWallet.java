package com.mceconomy.player;

public final class PlayerWallet {
	private long balance;

	public PlayerWallet(long balance) {
		this.balance = balance;
	}

	public long balance() {
		return balance;
	}

	public boolean deposit(long amount) {
		if (amount <= 0) {
			return false;
		}
		if (Long.MAX_VALUE - balance < amount) {
			return false;
		}
		balance += amount;
		return true;
	}

	public boolean withdraw(long amount) {
		if (amount <= 0 || balance < amount) {
			return false;
		}
		balance -= amount;
		return true;
	}

	public void setBalance(long newBalance) {
		balance = newBalance;
	}

	public long drainAll() {
		long drained = balance;
		balance = 0;
		return drained;
	}
}
