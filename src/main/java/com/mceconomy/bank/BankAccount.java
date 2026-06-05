package com.mceconomy.bank;

import java.util.UUID;

public final class BankAccount {
	private int id;
	private final UUID ownerUuid;
	private final BankAccountType type;
	private long balance;
	private double interestRate;
	private long maturesAt;

	public BankAccount(int id, UUID ownerUuid, BankAccountType type, long balance, double interestRate, long maturesAt) {
		this.id = id;
		this.ownerUuid = ownerUuid;
		this.type = type;
		this.balance = balance;
		this.interestRate = interestRate;
		this.maturesAt = maturesAt;
	}

	public static BankAccount createChecking(UUID owner) {
		return new BankAccount(0, owner, BankAccountType.CHECKING, 0, 0, 0);
	}

	public static BankAccount createTerm(UUID owner, double interestRate, long maturesAt) {
		return new BankAccount(0, owner, BankAccountType.TERM, 0, interestRate, maturesAt);
	}

	public int id() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public UUID ownerUuid() {
		return ownerUuid;
	}

	public BankAccountType type() {
		return type;
	}

	public long balance() {
		return balance;
	}

	public double interestRate() {
		return interestRate;
	}

	public long maturesAt() {
		return maturesAt;
	}

	public boolean deposit(long amount) {
		if (amount <= 0 || Long.MAX_VALUE - balance < amount) {
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

	public long drainAll() {
		long drained = balance;
		balance = 0;
		return drained;
	}

	public boolean applyInterest(double rate) {
		if (rate <= 0 || balance <= 0) {
			return false;
		}
		long interest = Math.round(balance * rate);
		if (interest <= 0) {
			interest = 1;
		}
		return deposit(interest);
	}

	public void setBalance(long newBalance) {
		balance = Math.max(0, newBalance);
	}
}
