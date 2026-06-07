package com.mceconomy.exchange;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.tax.TaxService;

/** Borsa komisyonu ve kâr stopaji. */
public final class ExchangeTaxService {
	private final TaxService taxService;

	public ExchangeTaxService(TaxService taxService) {
		this.taxService = taxService;
	}

	public long spotCommissionMg(long tradeAmountMg) {
		if (tradeAmountMg <= 0) {
			return 0;
		}
		long commission = (tradeAmountMg * EconomyConfig.exchangeSpotCommissionBps()) / 10_000L;
		if (commission > 0) {
			taxService.collectTax(commission);
		}
		return commission;
	}

	public long leverageProfitStopajMg(long pnlMg) {
		return profitStopajMg(pnlMg);
	}

	public long spotProfitStopajMg(long profitMg) {
		return profitStopajMg(profitMg);
	}

	public long shareCommissionMg(long tradeAmountMg) {
		if (tradeAmountMg <= 0) {
			return 0;
		}
		long commission = (tradeAmountMg * EconomyConfig.exchangeShareCommissionBps()) / 10_000L;
		if (commission > 0) {
			taxService.collectTax(commission);
		}
		return commission;
	}

	private long profitStopajMg(long profitMg) {
		if (profitMg <= 0) {
			return 0;
		}
		double rate = EconomyConfig.leverageProfitStopajRate();
		if (rate <= 0) {
			return 0;
		}
		long stopaj = Math.max(0, Math.round(profitMg * rate));
		if (stopaj > 0) {
			taxService.collectTax(stopaj);
		}
		return stopaj;
	}
}
