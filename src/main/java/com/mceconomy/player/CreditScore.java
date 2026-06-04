package com.mceconomy.player;

import com.mceconomy.util.EconomyMath;

public final class CreditScore {
	private int score;

	public CreditScore(int score) {
		this.score = EconomyMath.clampCreditScore(score);
	}

	public int score() {
		return score;
	}

	public void onTimePayment() {
		score = EconomyMath.clampCreditScore(score + 10);
	}

	public void latePayment() {
		score = EconomyMath.clampCreditScore(score - 25);
	}

	public void defaultPayment() {
		score = EconomyMath.clampCreditScore(score - 50);
	}

	public void loanTaken() {
		score = EconomyMath.clampCreditScore(score - 5);
	}

	public void loanRepaid() {
		score = EconomyMath.clampCreditScore(score + 30);
	}

	public void adjust(int delta) {
		score = EconomyMath.clampCreditScore(score + delta);
	}

	public void setScore(int newScore) {
		score = EconomyMath.clampCreditScore(newScore);
	}
}
