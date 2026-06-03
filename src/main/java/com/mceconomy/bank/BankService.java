package com.mceconomy.bank;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.facility.FacilityItemTags;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.PhysicalGoldService;
import com.mceconomy.facility.FacilityDepotService;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.reserve.DepotLedgerService;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.BankRepository;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
		if (currencyService.getBalance(owner) < 0) {
			return false;
		}
		if (!currencyService.withdraw(owner, amount, TransactionType.WITHDRAW)) {
			return false;
		}
		return account.deposit(amount);
	}

	/** Vadesiz hesaptaki para borc varken cuzdana aktarilir. */
	public void sweepCheckingTowardDebt(UUID owner) {
		long wallet = currencyService.getBalance(owner);
		if (wallet >= 0) {
			return;
		}
		BankAccount account = checkingAccounts.get(owner);
		if (account == null) {
			return;
		}
		long debt = -wallet;
		long sweep = Math.min(debt, account.balance());
		if (sweep <= 0) {
			return;
		}
		if (!account.withdraw(sweep)) {
			return;
		}
		currencyService.deposit(owner, sweep, TransactionType.DEPOSIT);
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
		if (PhysicalGoldService.countDepositEligibleGoldIngots(player) < ingots) {
			return false;
		}
		List<ItemStack> removed = new ArrayList<>();
		if (!PhysicalGoldService.removeDepositEligibleGoldIngots(player, ingots, removed)) {
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		if (depotService != null) {
			depositStacksToPhysicalVault(level, removed);
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
			rollbackPhysicalDeposit(level, player, ingots, removed);
			return false;
		}
		try {
			repository.save(account);
		} catch (SQLException e) {
			com.mceconomy.McEconomyMod.LOGGER.error("Banka hesabi kaydedilemedi", e);
		}
		return true;
	}

	private void depositStacksToPhysicalVault(ServerLevel level, List<ItemStack> stacks) {
		if (depotService == null || stacks == null) {
			return;
		}
		var manager = McEconomyMod.getEconomyManager();
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack toDeposit = stack.copy();
			if (FacilityItemTags.getSerial(toDeposit) == null) {
				if (manager != null && manager.bankAssetSerialRegistry() != null) {
					manager.bankAssetSerialRegistry().assignSerial(toDeposit, FacilityType.PHYSICAL_GOLD);
				} else {
					FacilityItemTags.markDepot(toDeposit, FacilityType.PHYSICAL_GOLD);
					FacilityItemTags.applySerialDisplayName(toDeposit);
				}
			} else {
				FacilityItemTags.applySerialDisplayName(toDeposit);
			}
			if (!depotService.deposit(level, FacilityType.PHYSICAL_GOLD, toDeposit)) {
				int placed = depotService.depositItem(level, FacilityType.PHYSICAL_GOLD,
						Items.GOLD_INGOT, toDeposit.getCount());
				if (placed < toDeposit.getCount()) {
					com.mceconomy.McEconomyMod.LOGGER.warn(
							"Fiziksel altin kasasina tam yatirilamadi: {} / {}", placed, toDeposit.getCount());
				}
			}
		}
	}

	private void rollbackPhysicalDeposit(ServerLevel level, ServerPlayer player, int ingots,
			List<ItemStack> removedStacks) {
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
		if (removedStacks != null && !removedStacks.isEmpty()) {
			PhysicalGoldService.giveGoldStacks(player, removedStacks);
		} else {
			PhysicalGoldService.giveGoldIngots(player, ingots);
		}
	}

	/** 1 gram = 1000 MC — bankadan 1 altin parcacigi (nugget) olarak cekim. */
	public boolean withdrawPhysicalGoldGrams(UUID owner, ServerPlayer player, int grams) {
		BankAccount account = checkingAccounts.get(owner);
		if (account == null || grams <= 0) {
			return false;
		}
		long milligrams = GoldStandard.gramsToMilligrams(grams);
		if (!account.withdraw(milligrams)) {
			return false;
		}
		if (!PhysicalGoldService.giveGoldNuggets(player, grams)) {
			account.deposit(milligrams);
			return false;
		}
		return true;
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
		java.util.List<net.minecraft.world.item.ItemStack> depotStacks = java.util.List.of();
		if (depotService != null) {
			depotStacks = depotService.withdrawGoldIngots((ServerLevel) player.level(), ingots);
			for (net.minecraft.world.item.ItemStack stack : depotStacks) {
				fromDepot += stack.getCount();
			}
		}
		int remaining = ingots - fromDepot;
		if (!depotStacks.isEmpty() && !PhysicalGoldService.giveGoldStacks(player, depotStacks)) {
			account.deposit(milligrams);
			for (net.minecraft.world.item.ItemStack stack : depotStacks) {
				depotService.deposit((ServerLevel) player.level(), FacilityType.PHYSICAL_GOLD, stack);
			}
			return false;
		}
		if (remaining > 0 && !PhysicalGoldService.giveGoldIngots(player, remaining)) {
			account.deposit(milligrams);
			if (!depotStacks.isEmpty() && depotService != null) {
				for (net.minecraft.world.item.ItemStack stack : depotStacks) {
					depotService.deposit((ServerLevel) player.level(), FacilityType.PHYSICAL_GOLD, stack);
				}
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
