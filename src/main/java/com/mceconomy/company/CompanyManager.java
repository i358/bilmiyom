package com.mceconomy.company;

import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.CompanyRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CompanyManager {
	private final Map<String, Company> companiesByName = new HashMap<>();
	private final Map<String, Company> companiesByTicker = new HashMap<>();
	private final Map<Integer, Map<UUID, ShareHolding>> shares = new HashMap<>();
	private final CompanyRepository repository;
	private final CurrencyService currencyService;

	public CompanyManager(CompanyRepository repository, CurrencyService currencyService) {
		this.repository = repository;
		this.currencyService = currencyService;
	}

	public void load() throws SQLException {
		companiesByName.clear();
		companiesByTicker.clear();
		shares.clear();
		for (Company company : repository.loadAll()) {
			companiesByName.put(company.name().toLowerCase(), company);
			if (company.ticker() != null) {
				companiesByTicker.put(company.ticker().toUpperCase(), company);
			}
		}
		Map<Integer, Map<UUID, Integer>> loadedShares = repository.loadAllShares();
		for (Map.Entry<Integer, Map<UUID, Integer>> entry : loadedShares.entrySet()) {
			Map<UUID, ShareHolding> companyShares = new HashMap<>();
			for (Map.Entry<UUID, Integer> share : entry.getValue().entrySet()) {
				companyShares.put(share.getKey(), new ShareHolding(entry.getKey(), share.getKey(), share.getValue()));
			}
			shares.put(entry.getKey(), companyShares);
		}
	}

	public void saveAll() throws SQLException {
		for (Company company : companiesByName.values()) {
			repository.save(company);
		}
		for (Map.Entry<Integer, Map<UUID, ShareHolding>> entry : shares.entrySet()) {
			for (ShareHolding holding : entry.getValue().values()) {
				repository.saveShare(holding);
			}
		}
	}

	public void saveCompany(Company company) throws SQLException {
		repository.save(company);
	}

	public boolean createCompany(String name, UUID owner) throws SQLException {
		if (companiesByName.containsKey(name.toLowerCase())) {
			return false;
		}
		Company company = Company.create(name, owner);
		repository.save(company);
		companiesByName.put(name.toLowerCase(), company);
		shares.computeIfAbsent(company.id(), k -> new HashMap<>())
				.put(owner, new ShareHolding(company.id(), owner, company.outstandingShares()));
		repository.saveShare(new ShareHolding(company.id(), owner, company.outstandingShares()));
		return true;
	}

	/** Kamu / varsayilan borsa sirketi — listeli olarak kurulur. */
	public boolean createPublicListedCompany(String name, String ticker, UUID owner, long treasury) throws SQLException {
		if (companiesByName.containsKey(name.toLowerCase())
				|| companiesByTicker.containsKey(ticker.toUpperCase())) {
			return false;
		}
		Company company = Company.create(name, owner);
		company.deposit(treasury);
		company.listOnExchange(ticker.toUpperCase());
		repository.save(company);
		companiesByName.put(name.toLowerCase(), company);
		companiesByTicker.put(ticker.toUpperCase(), company);
		shares.computeIfAbsent(company.id(), k -> new HashMap<>())
				.put(owner, new ShareHolding(company.id(), owner, company.outstandingShares()));
		repository.saveShare(new ShareHolding(company.id(), owner, company.outstandingShares()));
		return true;
	}

	public boolean listOnExchange(String companyName, String ticker, UUID owner, long listingFeeMg) throws SQLException {
		Company company = companiesByName.get(companyName.toLowerCase());
		if (company == null || !company.ownerUuid().equals(owner) || company.listedOnExchange()) {
			return false;
		}
		if (companiesByTicker.containsKey(ticker.toUpperCase())) {
			return false;
		}
		if (!currencyService.withdraw(owner, listingFeeMg, TransactionType.EXCHANGE_LISTING)) {
			return false;
		}
		company.listOnExchange(ticker.toUpperCase());
		companiesByTicker.put(ticker.toUpperCase(), company);
		repository.save(company);
		return true;
	}

	public boolean delistCompany(String companyName, UUID owner) throws SQLException {
		Company company = companiesByName.get(companyName.toLowerCase());
		if (company == null || !company.ownerUuid().equals(owner) || !company.listedOnExchange()) {
			return false;
		}
		if (company.ticker() != null) {
			companiesByTicker.remove(company.ticker().toUpperCase());
		}
		company.delistFromExchange();
		repository.save(company);
		return true;
	}

	public Optional<Company> find(String name) {
		return Optional.ofNullable(companiesByName.get(name.toLowerCase()));
	}

	public Optional<Company> findByTicker(String ticker) {
		return Optional.ofNullable(companiesByTicker.get(ticker.toUpperCase()));
	}

	public Collection<Company> allCompanies() {
		return companiesByName.values();
	}

	public List<Company> listedCompanies() {
		List<Company> listed = new ArrayList<>();
		for (Company company : companiesByName.values()) {
			if (company.listedOnExchange()) {
				listed.add(company);
			}
		}
		return listed;
	}

	public boolean buyShares(UUID buyer, String companyNameOrTicker, int amount, double economyIndex) throws SQLException {
		Company company = resolveCompany(companyNameOrTicker);
		if (company == null || amount <= 0 || !company.listedOnExchange()) {
			return false;
		}
		long cost = company.sharePrice(economyIndex) * amount;
		if (!currencyService.withdraw(buyer, cost, TransactionType.COMPANY)) {
			return false;
		}
		company.deposit(cost);
		ShareHolding holding = shares.computeIfAbsent(company.id(), k -> new HashMap<>())
				.computeIfAbsent(buyer, u -> new ShareHolding(company.id(), u, 0));
		holding.add(amount);
		repository.save(company);
		repository.saveShare(holding);
		return true;
	}

	public boolean sellShares(UUID seller, String companyNameOrTicker, int amount, double economyIndex) throws SQLException {
		Company company = resolveCompany(companyNameOrTicker);
		if (company == null || amount <= 0 || !company.listedOnExchange()) {
			return false;
		}
		ShareHolding holding = shares.getOrDefault(company.id(), Map.of()).get(seller);
		if (holding == null || !holding.remove(amount)) {
			return false;
		}
		long payout = company.sharePrice(economyIndex) * amount;
		company.withdraw(payout);
		currencyService.deposit(seller, payout, TransactionType.COMPANY);
		repository.save(company);
		repository.saveShare(holding);
		return true;
	}

	public int getShareCount(UUID player, Company company) {
		ShareHolding holding = shares.getOrDefault(company.id(), Map.of()).get(player);
		return holding != null ? holding.amount() : 0;
	}

	public long seizeAllShares(UUID owner, double economyIndex, com.mceconomy.tax.CentralBank centralBank)
			throws SQLException {
		long total = 0;
		for (Company company : allCompanies()) {
			Map<UUID, ShareHolding> companyShares = shares.get(company.id());
			if (companyShares == null) {
				continue;
			}
			ShareHolding holding = companyShares.get(owner);
			if (holding == null || holding.amount() <= 0) {
				continue;
			}
			int amount = holding.amount();
			total += company.sharePrice(economyIndex) * (long) amount;
			holding.remove(amount);
			repository.saveShare(holding);
			if (holding.amount() <= 0) {
				companyShares.remove(owner);
			}
		}
		if (total > 0) {
			centralBank.addMunicipalBudget(total);
		}
		return total;
	}

	private Company resolveCompany(String nameOrTicker) {
		Company company = companiesByName.get(nameOrTicker.toLowerCase());
		if (company != null) {
			return company;
		}
		return companiesByTicker.get(nameOrTicker.toUpperCase());
	}

	public boolean adminSetShareCount(UUID player, String companyNameOrTicker, int amount) throws SQLException {
		Company company = resolveCompany(companyNameOrTicker);
		if (company == null || amount < 0) {
			return false;
		}
		Map<UUID, ShareHolding> companyShares = shares.computeIfAbsent(company.id(), k -> new HashMap<>());
		if (amount == 0) {
			companyShares.remove(player);
			repository.saveShare(new ShareHolding(company.id(), player, 0));
			return true;
		}
		ShareHolding holding = companyShares.computeIfAbsent(player,
				u -> new ShareHolding(company.id(), u, 0));
		holding.setAmount(amount);
		repository.saveShare(holding);
		return true;
	}

	public boolean adminDelist(String companyName) throws SQLException {
		Company company = companiesByName.get(companyName.toLowerCase());
		if (company == null || !company.listedOnExchange()) {
			return false;
		}
		if (company.ticker() != null) {
			companiesByTicker.remove(company.ticker().toUpperCase());
		}
		company.delistFromExchange();
		repository.save(company);
		return true;
	}

	public boolean adminUpdateCompany(String companyName, Long treasuryMg, String ticker, Boolean listed)
			throws SQLException {
		Company company = companiesByName.get(companyName.toLowerCase());
		if (company == null) {
			return false;
		}
		if (treasuryMg != null) {
			company.setTreasury(treasuryMg);
		}
		if (ticker != null && !ticker.isBlank()) {
			String normalized = ticker.toUpperCase();
			companiesByTicker.remove(company.ticker() != null ? company.ticker().toUpperCase() : "");
			if (Boolean.TRUE.equals(listed) || company.listedOnExchange()) {
				company.listOnExchange(normalized);
				companiesByTicker.put(normalized, company);
			}
		}
		if (listed != null) {
			if (listed && company.ticker() != null) {
				company.listOnExchange(company.ticker());
				companiesByTicker.put(company.ticker().toUpperCase(), company);
			} else if (!listed) {
				if (company.ticker() != null) {
					companiesByTicker.remove(company.ticker().toUpperCase());
				}
				company.delistFromExchange();
			}
		}
		repository.save(company);
		return true;
	}
}
