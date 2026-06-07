package com.mceconomy.exchange;

import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.ExchangeCollateralRepository;

import java.sql.SQLException;
import java.util.UUID;

/** Borsa teminat hesabi: kaldıraç ve borsa islemleri icin cuzdandan ayrilan bakiye. */
public final class ExchangeCollateralService {
	private final ExchangeCollateralRepository repository;
	private final CurrencyService currencyService;

	public ExchangeCollateralService(ExchangeCollateralRepository repository, CurrencyService currencyService) {
		this.repository = repository;
		this.currencyService = currencyService;
	}

	public long balanceMg(UUID player) {
		try {
			return repository.getBalanceMg(player);
		} catch (SQLException e) {
			return 0L;
		}
	}

	public long availableMg(UUID player, long lockedMarginMg) {
		return Math.max(0, balanceMg(player) - Math.max(0, lockedMarginMg));
	}

	public boolean depositFromWallet(UUID player, long amountMg) {
		if (amountMg <= 0) {
			return false;
		}
		if (!currencyService.withdraw(player, amountMg, TransactionType.EXCHANGE_TOKEN)) {
			return false;
		}
		try {
			long next = balanceMg(player) + amountMg;
			repository.setBalanceMg(player, next);
			return true;
		} catch (SQLException e) {
			currencyService.deposit(player, amountMg, TransactionType.EXCHANGE_TOKEN);
			return false;
		}
	}

	public boolean withdrawToWallet(UUID player, long amountMg, long lockedMarginMg) {
		if (amountMg <= 0) {
			return false;
		}
		if (availableMg(player, lockedMarginMg) < amountMg) {
			return false;
		}
		try {
			repository.setBalanceMg(player, balanceMg(player) - amountMg);
			return currencyService.deposit(player, amountMg, TransactionType.EXCHANGE_TOKEN);
		} catch (SQLException e) {
			return false;
		}
	}

	public boolean debit(UUID player, long amountMg, long lockedMarginMg) {
		if (amountMg <= 0) {
			return false;
		}
		if (availableMg(player, lockedMarginMg) < amountMg) {
			return false;
		}
		try {
			repository.setBalanceMg(player, balanceMg(player) - amountMg);
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public void credit(UUID player, long amountMg) {
		if (amountMg <= 0) {
			return;
		}
		try {
			repository.setBalanceMg(player, balanceMg(player) + amountMg);
		} catch (SQLException ignored) {
		}
	}

	public String formatBalance(UUID player, long lockedMarginMg) {
		return GoldStandard.formatMilligrams(balanceMg(player))
				+ " (kullanilabilir: " + GoldStandard.formatMilligrams(availableMg(player, lockedMarginMg)) + ")";
	}
}
