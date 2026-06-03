package com.mceconomy.regulation;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class LaunderingService {
	public enum LaunderOutcome {
		SUCCESS,
		CAUGHT,
		INSUFFICIENT
	}

	public record LaunderResult(LaunderOutcome outcome, long amountMg, int riskPercent, long fineMg, long cleanedMg) {
	}

	private final CurrencyService currencyService;
	private final MasakService masakService;

	public LaunderingService(CurrencyService currencyService, MasakService masakService) {
		this.currencyService = currencyService;
		this.masakService = masakService;
	}

	public int previewRisk(UUID player, long amountMg) {
		return masakService.calculateLaunderRiskPercent(player, amountMg);
	}

	public LaunderResult attempt(ServerPlayer player, long amountMg) {
		UUID uuid = player.getUUID();
		if (amountMg <= 0 || currencyService.getDirtyBalance(uuid) < amountMg) {
			return new LaunderResult(LaunderOutcome.INSUFFICIENT, amountMg, 0, 0, 0);
		}
		int riskPercent = masakService.calculateLaunderRiskPercent(uuid, amountMg);
		double roll = ThreadLocalRandom.current().nextDouble();
		boolean caught = roll < riskPercent / 100.0;

		if (caught) {
			long fineMg = (long) (amountMg * EconomyConfig.launderFinePercent());
			long seizedMg = Math.min(amountMg, fineMg + amountMg / 2);
			currencyService.withdrawDirty(uuid, seizedMg, TransactionType.LAUNDERING_CAUGHT);
			masakService.applyFine(uuid, fineMg);
			masakService.onLaunderingAttempt(uuid, amountMg, true);
			masakService.notifyPlayer(player, "Aklama girişiminiz tespit edildi! Ceza: "
					+ GoldStandard.formatMilligrams(fineMg));
			return new LaunderResult(LaunderOutcome.CAUGHT, amountMg, riskPercent, fineMg, 0);
		}

		long feeMg = (long) (amountMg * EconomyConfig.launderServiceFeePercent());
		long cleanedMg = amountMg - feeMg;
		if (!currencyService.withdrawDirty(uuid, amountMg, TransactionType.LAUNDERING)) {
			return new LaunderResult(LaunderOutcome.INSUFFICIENT, amountMg, riskPercent, 0, 0);
		}
		currencyService.deposit(uuid, cleanedMg, TransactionType.LAUNDERING);
		masakService.onLaunderingAttempt(uuid, amountMg, false);
		return new LaunderResult(LaunderOutcome.SUCCESS, amountMg, riskPercent, 0, cleanedMg);
	}
}
