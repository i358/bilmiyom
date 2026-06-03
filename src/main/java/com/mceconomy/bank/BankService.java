package com.mceconomy.bank;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.PhysicalGoldService;
import com.mceconomy.facility.FacilityDepotService;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.reserve.DepotLedgerService;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.BankRepository;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BankService {
	private final Map<UUID, BankAccount> checkingAccounts = new HashMap<>();
	private final Map<UUID, BankAccount> termAccounts = new HashMap<>();
	private final BankRepository repository;
	private final CurrencyService currencyService;
	private FacilityDepotService depotService;
	private DepotLedgerService depotLedger;

	public BankService(BankRepository repository, CurrencyService currencyService) {
		this.repository = repository;
		this.currencyService = currencyService;
	}

	public void load() throws SQLException {
		for (BankAccount account : repository.loadAll()) {
			if (account.type() == BankAccountType.CHECKING) {
				checkingAccounts.put(account.ownerUuid(), account);
			} else {
				termAccounts.put(account.ownerUuid(), account);
			}
		}
	}

	public void saveAll() throws SQLException {
		for (BankAccount account : checkingAccounts.values()) {
			repository.save(account);
		}
		for (BankAccount account : termAccounts.values()) {
			repository.save(account);
		}
	}

	public boolean createCheckingAccount(UUID owner) throws SQLException {
		if (checkingAccounts.containsKey(owner)) {
			return false;
		}
		BankAccount account = BankAccount.createChecking(owner);
		repository.save(account);
		checkingAccounts.put(owner, account);
		return true;
	}

	public boolean createTermAccount(UUID owner, double baseRate) throws SQLException {
		if (termAccounts.containsKey(owner)) {
			return false;
		}
		long maturesAt = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000;
		BankAccount account = BankAccount.createTerm(owner, baseRate, maturesAt);
		repository.save(account);
		termAccounts.put(owner, account);
		return true;
	}

	public Optional<BankAccount> getChecking(UUID owner) {
		return Optional.ofNullable(checkingAccounts.get(owner));
	}

	public Optional<BankAccount> getTerm(UUID owner) {
		return Optional.ofNullable(termAccounts.get(owner));
	}

	public boolean depositToBank(UUID owner, long amount) {
		BankAccount account = checkingAccounts.get(owner);
		if (account == null || amount <= 0) {
			return false;
		}
		if (!currencyService.withdraw(owner, amount, TransactionType.WITHDRAW)) {
			return false;
		}
		return account.deposit(amount);
	}

	public boolean withdrawFromBank(UUID owner, long amount) {
		BankAccount account = checkingAccounts.get(owner);
		if (account == null || amount <= 0) {
			return false;
		}
		if (!account.withdraw(amount)) {
			return false;
		}
		return currencyService.deposit(owner, amount, TransactionType.DEPOSIT);
	}

	public boolean transferFromBank(UUID from, UUID to, long amount) {
		BankAccount fromAccount = checkingAccounts.get(from);
		if (fromAccount == null || amount <= 0) {
			return false;
		}
		if (!fromAccount.withdraw(amount)) {
			return false;
		}
		return currencyService.deposit(to, amount, TransactionType.TRANSFER);
	}

	public void bindDepot(FacilityDepotService depotService) {
		this.depotService = depotService;
	}

	public void bindDepotLedger(DepotLedgerService depotLedger) {
		this.depotLedger = depotLedger;
	}

	public boolean depositPhysicalGold(UUID owner, ServerPlayer player, int ingots) {
		BankAccount account = checkingAccounts.get(owner);
		if (account == null || ingots <= 0) {
			return false;
		}
		if (PhysicalGoldService.countGoldIngots(player) < ingots) {
			return false;
		}
		if (!PhysicalGoldService.removeGoldIngots(player, ingots)) {
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		if (depotService != null) {
			depotService.depositItem(level, FacilityType.PHYSICAL_GOLD,
					net.minecraft.world.item.Items.GOLD_INGOT, ingots);
		}
		if (depotLedger != null) {
			try {
				depotLedger.onPhysicalGoldDeposited(ingots);
			} catch (SQLException e) {
				com.mceconomy.McEconomyMod.LOGGER.error("Altin kasasi defteri guncellenemedi", e);
			}
		}
		long mg = GoldStandard.ingotsToMilligrams(ingots);
		boolean credited;
		if (currencyService.getBalance(owner) < 0) {
			credited = currencyService.deposit(owner, mg, TransactionType.DEPOSIT);
		} else {
			credited = account.deposit(mg);
		}
		if (!credited) {
			rollbackPhysicalDeposit(level, player, ingots);
			return false;
		}
		try {
			repository.save(account);
		} catch (SQLException e) {
			com.mceconomy.McEconomyMod.LOGGER.error("Banka hesabi kaydedilemedi", e);
		}
		return true;
	}

	private void rollbackPhysicalDeposit(ServerLevel level, ServerPlayer player, int ingots) {
		if (depotService != null) {
			depotService.withdrawItem(level, FacilityType.PHYSICAL_GOLD,
					net.minecraft.world.item.Items.GOLD_INGOT, ingots);
		}
		if (depotLedger != null) {
			try {
				depotLedger.onPhysicalGoldWithdrawn(ingots);
			} catch (SQLException e) {
				com.mceconomy.McEconomyMod.LOGGER.error("Altin kasasi defteri geri alinamadi", e);
			}
		}
		PhysicalGoldService.giveGoldIngots(player, ingots);
	}

	public boolean withdrawPhysicalGold(UUID owner, ServerPlayer player, int ingots) {
		BankAccount account = checkingAccounts.get(owner);
		if (account == null || ingots <= 0) {
			return false;
		}
		long milligrams = GoldStandard.ingotsToMilligrams(ingots);
		if (!account.withdraw(milligrams)) {
			return false;
		}
		int fromDepot = 0;
		if (depotService != null) {
			fromDepot = depotService.withdrawItem((ServerLevel) player.level(), FacilityType.PHYSICAL_GOLD,
					net.minecraft.world.item.Items.GOLD_INGOT, ingots);
		}
		int remaining = ingots - fromDepot;
		if (remaining > 0 && !PhysicalGoldService.giveGoldIngots(player, remaining)) {
			account.deposit(milligrams);
			if (fromDepot > 0 && depotService != null) {
				depotService.depositItem((ServerLevel) player.level(), FacilityType.PHYSICAL_GOLD,
						net.minecraft.world.item.Items.GOLD_INGOT, fromDepot);
			}
			return false;
		}
		if (depotLedger != null) {
			try {
				depotLedger.onPhysicalGoldWithdrawn(ingots);
			} catch (SQLException e) {
				com.mceconomy.McEconomyMod.LOGGER.error("Altin kasasi defteri guncellenemedi", e);
			}
		}
		return true;
	}

	public long getBankBalanceMg(UUID owner) {
		BankAccount account = checkingAccounts.get(owner);
		return account != null ? account.balance() : 0;
	}

	public long seizeAllAccounts(UUID owner, com.mceconomy.tax.CentralBank centralBank) throws SQLException {
		long total = 0;
		BankAccount checking = checkingAccounts.get(owner);
		if (checking != null) {
			long drained = checking.drainAll();
			if (drained > 0) {
				centralBank.addMunicipalBudget(drained);
				total += drained;
			}
		}
		BankAccount term = termAccounts.get(owner);
		if (term != null) {
			long drained = term.drainAll();
			if (drained > 0) {
				centralBank.addMunicipalBudget(drained);
				total += drained;
			}
		}
		saveAll();
		return total;
	}

	public long totalBankBalance() {
		long total = 0;
		for (BankAccount account : checkingAccounts.values()) {
			total += account.balance();
		}
		for (BankAccount account : termAccounts.values()) {
			total += account.balance();
		}
		return total;
	}

	public void applyTermInterest(double baseRate) {
		for (BankAccount account : termAccounts.values()) {
			double rate = account.interestRate() > 0 ? account.interestRate() : baseRate;
			account.applyInterest(rate / EconomyConfig.interestIntervalTicks() * 20);
		}
	}
}
