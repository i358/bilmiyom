package com.mceconomy.bank;

import com.mceconomy.config.EconomyConfig;

public final class InterestEngine {
	public long calculateTermInterest(long balance, double rate) {
		return (long) Math.floor(balance * rate);
	}

	public long calculateLateFee(long remaining, double lateRate, double accumulatedLateInterest) {
		return (long) Math.floor(remaining * (lateRate + accumulatedLateInterest));
	}

	public double effectiveLoanRate(double baseRate, int creditScore) {
		double adjustment = (650 - creditScore) / 10000.0;
		return Math.max(0.01, baseRate + adjustment);
	}

	public long maxLoanForScore(int creditScore) {
		if (creditScore < EconomyConfig.minCreditScoreForLoan()) {
			return 0;
		}
		double factor = (creditScore - 300) / 550.0;
		return (long) (EconomyConfig.maxLoanAmount() * factor);
	}
}
