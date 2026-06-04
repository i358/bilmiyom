package com.mceconomy.bank;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.LoanRepository;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.tax.CentralBank;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class LoanManager {
	private final Map<UUID, LoanRecord> loans = new HashMap<>();
	private final LoanRepository repository;
	private final CurrencyService currencyService;
	private final InterestEngine interestEngine;

	public LoanManager(LoanRepository repository, CurrencyService currencyService, InterestEngine interestEngine) {
		this.repository = repository;
		this.currencyService = currencyService;
		this.interestEngine = interestEngine;
	}

	public void load() throws SQLException {
		loans.clear();
		loans.putAll(repository.loadAll());
	}

	public void saveAll() throws SQLException {
		for (LoanRecord loan : loans.values()) {
			if (loan.isPaidOff()) {
				repository.delete(loan.borrowerUuid());
			} else {
				repository.save(loan);
			}
		}
	}

	public Optional<LoanRecord> getLoan(UUID borrower) {
		return Optional.ofNullable(loans.get(borrower));
	}

	public boolean takeLoan(PlayerEconomyProfile profile, long amount, CentralBank centralBank) throws SQLException {
		if (amount <= 0 || loans.containsKey(profile.uuid())) {
			return false;
		}
		if (profile.creditScore().score() < EconomyConfig.minCreditScoreForLoan()) {
			return false;
		}
		long max = interestEngine.maxLoanForScore(profile.creditScore().score());
		if (amount > max) {
			return false;
		}

		double rate = interestEngine.effectiveLoanRate(centralBank.getBaseRate(), profile.creditScore().score());
		long installment = (long) Math.ceil((double) amount / EconomyConfig.loanInstallmentCount());
		long dueAt = System.currentTimeMillis() + 24L * 60 * 60 * 1000;

		LoanRecord loan = LoanRecord.create(profile.uuid(), amount, installment, dueAt, rate);
		repository.save(loan);
		loans.put(profile.uuid(), loan);
		profile.creditScore().loanTaken();
		currencyService.deposit(profile.uuid(), amount, TransactionType.LOAN);
		return true;
	}

	public boolean payInstallment(PlayerEconomyProfile profile) throws SQLException {
		LoanRecord loan = loans.get(profile.uuid());
		if (loan == null) {
			return false;
		}
		long fee = interestEngine.calculateLateFee(loan.remaining(), EconomyConfig.lateInterestRate(), loan.lateInterest());
		long total = loan.installment() + fee;
		if (!currencyService.withdraw(profile.uuid(), total, TransactionType.LOAN_PAYMENT)) {
			return false;
		}
		loan.payInstallment(total);
		if (System.currentTimeMillis() <= loan.dueAt()) {
			profile.creditScore().onTimePayment();
		} else {
			profile.creditScore().latePayment();
		}
		if (loan.isPaidOff()) {
			profile.creditScore().loanRepaid();
			loans.remove(profile.uuid());
			repository.delete(profile.uuid());
		} else {
			loan.setDueAt(System.currentTimeMillis() + 24L * 60 * 60 * 1000);
			repository.save(loan);
		}
		return true;
	}

	public boolean adminUpsertLoan(UUID borrower, long remainingMg, long installmentMg, long dueAt, double interestRate)
			throws SQLException {
		if (remainingMg <= 0) {
			return adminClearLoan(borrower);
		}
		LoanRecord loan = loans.get(borrower);
		if (loan == null) {
			loan = LoanRecord.create(borrower, remainingMg, installmentMg, dueAt, interestRate);
			loan.setRemaining(remainingMg);
			repository.save(loan);
			loans.put(borrower, loan);
		} else {
			loan.setRemaining(remainingMg);
			loan.setInstallment(installmentMg);
			loan.setDueAt(dueAt);
			loan.resetLateInterest();
			repository.save(loan);
		}
		return true;
	}

	public boolean adminClearLoan(UUID borrower) throws SQLException {
		if (!loans.containsKey(borrower)) {
			return false;
		}
		loans.remove(borrower);
		repository.delete(borrower);
		return true;
	}

	public void processOverdueLoans(Map<UUID, PlayerEconomyProfile> profiles, ServerLevel level) throws SQLException {
		long now = System.currentTimeMillis();
		for (LoanRecord loan : loans.values()) {
			if (now <= loan.dueAt()) {
				continue;
			}
			loan.addLateInterest(EconomyConfig.lateInterestRate());
			PlayerEconomyProfile profile = profiles.get(loan.borrowerUuid());
			if (profile != null) {
				profile.creditScore().latePayment();
			}
			repository.save(loan);

			if (EconomyConfig.loanConfiscationEnabled() && profile != null) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(loan.borrowerUuid());
				if (player != null && loan.lateInterest() > EconomyConfig.lateInterestRate() * 3) {
					player.getInventory().clearContent();
					profile.creditScore().defaultPayment();
				}
			}
		}
	}
}
