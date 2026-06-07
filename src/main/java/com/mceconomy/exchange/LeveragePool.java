package com.mceconomy.exchange;

/** Kaldirac teminat ve ucretlerinin toplandigi sifir-toplamli odeme havuzu. */
public final class LeveragePool {
	private long balanceMg;

	public long balanceMg() {
		return balanceMg;
	}

	public void setBalanceMg(long balanceMg) {
		this.balanceMg = Math.max(0, balanceMg);
	}

	public void credit(long amountMg) {
		if (amountMg > 0) {
			balanceMg += amountMg;
		}
	}

	/** Havuzdaki bakiyeyi asmayacak sekilde odeme yapar; odenen tutari dondurur. */
	public long debitUpTo(long requestedMg) {
		if (requestedMg <= 0) {
			return 0;
		}
		long paid = Math.min(requestedMg, balanceMg);
		balanceMg -= paid;
		return paid;
	}
}
