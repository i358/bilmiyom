package com.mceconomy.privatebank;

import com.mceconomy.bootstrap.EconomyBootstrap;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.PrivateBankRepository;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.regulation.MasakService;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PrivateBankService {
	private final Map<String, PrivateBank> banksByName = new HashMap<>();
	private final Map<Integer, Map<UUID, Long>> deposits = new HashMap<>();
	private final PrivateBankRepository repository;
	private final CurrencyService currencyService;
	private final Map<UUID, PlayerEconomyProfile> profiles;
	private final MasakService masakService;

	public PrivateBankService(PrivateBankRepository repository, CurrencyService currencyService,
			Map<UUID, PlayerEconomyProfile> profiles, MasakService masakService) {
		this.repository = repository;
		this.currencyService = currencyService;
		this.profiles = profiles;
		this.masakService = masakService;
	}

	public void load() throws SQLException {
		banksByName.clear();
		deposits.clear();
		for (PrivateBank bank : repository.loadAllBanks()) {
			banksByName.put(bank.name().toLowerCase(), bank);
		}
		deposits.putAll(repository.loadAllDeposits());
	}

	public void saveAll() throws SQLException {
		for (PrivateBank bank : banksByName.values()) {
			repository.saveBank(bank);
		}
		for (Map.Entry<Integer, Map<UUID, Long>> entry : deposits.entrySet()) {
			for (Map.Entry<UUID, Long> deposit : entry.getValue().entrySet()) {
				repository.saveDeposit(entry.getKey(), deposit.getKey(), deposit.getValue());
			}
		}
	}

	public boolean hasCertificate(UUID player) {
		PlayerEconomyProfile profile = profiles.get(player);
		return profile != null && profile.bankCertified();
	}

	public boolean purchaseCertificate(UUID player) {
		PlayerEconomyProfile profile = profiles.get(player);
		if (profile == null || profile.bankCertified()) {
			return false;
		}
		long cost = EconomyConfig.bankCertificateCostMg();
		if (!currencyService.withdraw(player, cost, TransactionType.PRIVATE_BANK)) {
			return false;
		}
		profile.setBankCertified(true);
		return true;
	}

	public void grantCertificate(UUID player) {
		PlayerEconomyProfile profile = profiles.get(player);
		if (profile != null) {
			profile.setBankCertified(true);
		}
	}

	public boolean createPublicBank(String name, double interestRate, long treasuryMg) throws SQLException {
		if (banksByName.containsKey(name.toLowerCase())) {
			return false;
		}
		PrivateBank bank = PrivateBank.create(name, EconomyBootstrap.SYSTEM_OWNER);
		bank.setInterestRate(interestRate);
		bank.depositTreasury(treasuryMg);
		repository.saveBank(bank);
		banksByName.put(name.toLowerCase(), bank);
		deposits.put(bank.id(), new HashMap<>());
		return true;
	}

	public boolean openBank(UUID owner, String name) throws SQLException {
		if (!hasCertificate(owner) || banksByName.containsKey(name.toLowerCase())) {
			return false;
		}
		if (masakService.isRestricted(owner)) {
			return false;
		}
		PrivateBank bank = PrivateBank.create(name, owner);
		repository.saveBank(bank);
		banksByName.put(name.toLowerCase(), bank);
		deposits.put(bank.id(), new HashMap<>());
		return true;
	}

	public boolean deposit(UUID customer, String bankName, long amountMg) throws SQLException {
		if (amountMg <= 0 || masakService.isRestricted(customer)) {
			return false;
		}
		PrivateBank bank = banksByName.get(bankName.toLowerCase());
		if (bank == null) {
			return false;
		}
		if (!currencyService.withdraw(customer, amountMg, TransactionType.PRIVATE_BANK)) {
			return false;
		}
		bank.depositTreasury(amountMg);
		long balance = deposits.computeIfAbsent(bank.id(), k -> new HashMap<>())
				.merge(customer, amountMg, Long::sum);
		repository.saveBank(bank);
		repository.saveDeposit(bank.id(), customer, balance);
		return true;
	}

	public boolean withdraw(UUID customer, String bankName, long amountMg) throws SQLException {
		if (amountMg <= 0) {
			return false;
		}
		PrivateBank bank = banksByName.get(bankName.toLowerCase());
		if (bank == null) {
			return false;
		}
		Map<UUID, Long> bankDeposits = deposits.get(bank.id());
		if (bankDeposits == null) {
			return false;
		}
		long balance = bankDeposits.getOrDefault(customer, 0L);
		if (balance < amountMg || bank.treasuryMg() < amountMg) {
			return false;
		}
		bank.withdrawTreasury(amountMg);
		bankDeposits.put(customer, balance - amountMg);
		currencyService.deposit(customer, amountMg, TransactionType.PRIVATE_BANK);
		repository.saveBank(bank);
		repository.saveDeposit(bank.id(), customer, balance - amountMg);
		return true;
	}

	public List<PrivateBank> allBanks() {
		return List.copyOf(banksByName.values());
	}

	public Optional<PrivateBank> find(String name) {
		return Optional.ofNullable(banksByName.get(name.toLowerCase()));
	}

	public long customerBalance(UUID customer, PrivateBank bank) {
		Map<UUID, Long> bankDeposits = deposits.get(bank.id());
		return bankDeposits != null ? bankDeposits.getOrDefault(customer, 0L) : 0;
	}

	public boolean adminSetDeposit(UUID customer, String bankName, long balanceMg) throws SQLException {
		PrivateBank bank = banksByName.get(bankName.toLowerCase());
		if (bank == null || balanceMg < 0) {
			return false;
		}
		Map<UUID, Long> bankDeposits = deposits.computeIfAbsent(bank.id(), k -> new HashMap<>());
		if (balanceMg == 0) {
			bankDeposits.remove(customer);
		} else {
			bankDeposits.put(customer, balanceMg);
		}
		repository.saveDeposit(bank.id(), customer, balanceMg);
		return true;
	}
}
