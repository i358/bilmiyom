package com.mceconomy.bank;

import java.util.UUID;

public final class LoanRecord {
	private int id;
	private final UUID borrowerUuid;
	private final long principal;
	private long remaining;
	private long installment;
	private long dueAt;
	private double lateInterest;
	private double interestRate;

	public LoanRecord(int id, UUID borrowerUuid, long principal, long remaining, long installment,
			long dueAt, double lateInterest, double interestRate) {
		this.id = id;
		this.borrowerUuid = borrowerUuid;
		this.principal = principal;
		this.remaining = remaining;
		this.installment = installment;
		this.dueAt = dueAt;
		this.lateInterest = lateInterest;
		this.interestRate = interestRate;
	}

	public static LoanRecord create(UUID borrower, long principal, long installment, long dueAt, double interestRate) {
		return new LoanRecord(0, borrower, principal, principal, installment, dueAt, 0, interestRate);
	}

	public int id() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public UUID borrowerUuid() {
		return borrowerUuid;
	}

	public long principal() {
		return principal;
	}

	public long remaining() {
		return remaining;
	}

	public long installment() {
		return installment;
	}

	public long dueAt() {
		return dueAt;
	}

	public double lateInterest() {
		return lateInterest;
	}

	public double interestRate() {
		return interestRate;
	}

	public void setDueAt(long dueAt) {
		this.dueAt = dueAt;
	}

	public void addLateInterest(double rate) {
		lateInterest += rate;
	}

	public boolean payInstallment(long amount) {
		if (amount <= 0) {
			return false;
		}
		long totalDue = installment + (long) Math.floor(remaining * lateInterest);
		if (amount < totalDue) {
			return false;
		}
		remaining = Math.max(0, remaining - installment);
		lateInterest = 0;
		return true;
	}

	public boolean isPaidOff() {
		return remaining <= 0;
	}

	public void setRemaining(long remaining) {
		this.remaining = Math.max(0, remaining);
	}

	public void setInstallment(long installment) {
		this.installment = Math.max(0, installment);
	}

	public void resetLateInterest() {
		lateInterest = 0;
	}
}
