package com.mceconomy.exchange;

import com.mceconomy.company.Company;
import com.mceconomy.company.CompanyManager;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.ExchangeRepository;
import com.mceconomy.regulation.MasakService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ExchangeService {
	private final Map<String, ExchangeToken> tokensBySymbol = new HashMap<>();
	private final Map<Integer, Map<UUID, Integer>> tokenHoldings = new HashMap<>();
	private final ExchangeRepository repository;
	private final CurrencyService currencyService;
	private final CompanyManager companyManager;
	private final MasakService masakService;

	public ExchangeService(ExchangeRepository repository, CurrencyService currencyService,
			CompanyManager companyManager, MasakService masakService) {
		this.repository = repository;
		this.currencyService = currencyService;
		this.companyManager = companyManager;
		this.masakService = masakService;
	}

	public void load() throws SQLException {
		tokensBySymbol.clear();
		tokenHoldings.clear();
		for (ExchangeToken token : repository.loadAllTokens()) {
			tokensBySymbol.put(token.symbol(), token);
		}
		Map<Integer, Map<UUID, Integer>> loaded = repository.loadAllHoldings();
		for (Map.Entry<Integer, Map<UUID, Integer>> entry : loaded.entrySet()) {
			tokenHoldings.put(entry.getKey(), new HashMap<>(entry.getValue()));
		}
	}

	public void saveAll() throws SQLException {
		for (ExchangeToken token : tokensBySymbol.values()) {
			repository.saveToken(token);
		}
		for (Map.Entry<Integer, Map<UUID, Integer>> entry : tokenHoldings.entrySet()) {
			for (Map.Entry<UUID, Integer> holding : entry.getValue().entrySet()) {
				repository.saveHolding(entry.getKey(), holding.getKey(), holding.getValue());
			}
		}
	}

	/** Kamu / bootstrap coin — ucret alinmaz. */
	public boolean createPublicToken(String symbol, String displayName, long priceMg, int totalSupply)
			throws SQLException {
		String normalized = symbol.toUpperCase();
		if (normalized.length() < 2 || normalized.length() > 6 || tokensBySymbol.containsKey(normalized)) {
			return false;
		}
		if (totalSupply <= 0 || priceMg <= 0) {
			return false;
		}
		ExchangeToken token = ExchangeToken.create(normalized, displayName,
				com.mceconomy.bootstrap.EconomyBootstrap.SYSTEM_OWNER, totalSupply, priceMg);
		repository.saveToken(token);
		tokensBySymbol.put(normalized, token);
		int marketFloat = Math.max(1, totalSupply / 4);
		int treasuryHold = totalSupply - marketFloat;
		token.addCirculating(marketFloat);
		tokenHoldings.computeIfAbsent(token.id(), k -> new HashMap<>())
				.put(com.mceconomy.bootstrap.EconomyBootstrap.SYSTEM_OWNER, treasuryHold);
		repository.saveToken(token);
		repository.saveHolding(token.id(), com.mceconomy.bootstrap.EconomyBootstrap.SYSTEM_OWNER, treasuryHold);
		return true;
	}

	public boolean createToken(UUID creator, String symbol, String displayName, int totalSupply, long priceMg)
			throws SQLException {
		String normalized = symbol.toUpperCase();
		if (normalized.length() < 2 || normalized.length() > 6 || tokensBySymbol.containsKey(normalized)) {
			return false;
		}
		if (totalSupply <= 0 || priceMg <= 0) {
			return false;
		}
		long fee = EconomyConfig.tokenCreationFeeMg();
		if (!currencyService.withdraw(creator, fee, TransactionType.EXCHANGE_TOKEN)) {
			return false;
		}
		ExchangeToken token = ExchangeToken.create(normalized, displayName, creator, totalSupply, priceMg);
		token.depositTreasury(fee / 2);
		repository.saveToken(token);
		tokensBySymbol.put(normalized, token);
		tokenHoldings.computeIfAbsent(token.id(), k -> new HashMap<>())
				.put(creator, totalSupply);
		repository.saveHolding(token.id(), creator, totalSupply);
		return true;
	}

	public boolean listCompany(UUID owner, String companyName, String ticker) throws SQLException {
		return companyManager.listOnExchange(companyName, ticker, owner, EconomyConfig.exchangeListingFeeMg());
	}

	public boolean buyToken(UUID buyer, String symbol, int amount) throws SQLException {
		if (masakService.isRestricted(buyer)) {
			return false;
		}
		ExchangeToken token = tokensBySymbol.get(symbol.toUpperCase());
		if (token == null || amount <= 0) {
			return false;
		}
		int available = token.totalSupply() - token.circulating();
		if (amount > available) {
			return false;
		}
		long cost = token.priceMg() * amount;
		if (!currencyService.withdraw(buyer, cost, TransactionType.EXCHANGE_TOKEN)) {
			return false;
		}
		token.depositTreasury(cost);
		token.addCirculating(amount);
		token.setPriceMg((long) (token.priceMg() * (1 + EconomyConfig.exchangePriceImpact())));
		int owned = tokenHoldings.computeIfAbsent(token.id(), k -> new HashMap<>())
				.merge(buyer, amount, Integer::sum);
		repository.saveToken(token);
		repository.saveHolding(token.id(), buyer, owned);
		return true;
	}

	public boolean sellToken(UUID seller, String symbol, int amount) throws SQLException {
		if (masakService.isRestricted(seller)) {
			return false;
		}
		ExchangeToken token = tokensBySymbol.get(symbol.toUpperCase());
		if (token == null || amount <= 0) {
			return false;
		}
		Map<UUID, Integer> holdings = tokenHoldings.get(token.id());
		if (holdings == null) {
			return false;
		}
		int owned = holdings.getOrDefault(seller, 0);
		if (owned < amount) {
			return false;
		}
		long payout = token.priceMg() * amount;
		token.removeCirculating(amount);
		token.setPriceMg(Math.max(1, (long) (token.priceMg() * (1 - EconomyConfig.exchangePriceImpact()))));
		int remaining = owned - amount;
		if (remaining <= 0) {
			holdings.remove(seller);
		} else {
			holdings.put(seller, remaining);
		}
		long fromTreasury = Math.min(token.treasuryMg(), payout);
		if (fromTreasury > 0) {
			token.withdrawTreasury(fromTreasury);
		}
		if (!currencyService.deposit(seller, payout, TransactionType.EXCHANGE_TOKEN)) {
			return false;
		}
		repository.saveToken(token);
		repository.saveHolding(token.id(), seller, Math.max(0, remaining));
		return true;
	}

	public List<ExchangeToken> allTokens() {
		return new ArrayList<>(tokensBySymbol.values());
	}

	public List<Company> listedCompanies() {
		return companyManager.listedCompanies();
	}

	public int tokenBalance(UUID player, ExchangeToken token) {
		Map<UUID, Integer> holdings = tokenHoldings.get(token.id());
		return holdings != null ? holdings.getOrDefault(player, 0) : 0;
	}

	public long seizeAllTokens(UUID owner, com.mceconomy.tax.CentralBank centralBank) throws SQLException {
		long total = 0;
		for (ExchangeToken token : allTokens()) {
			Map<UUID, Integer> holdings = tokenHoldings.get(token.id());
			if (holdings == null) {
				continue;
			}
			int amount = holdings.getOrDefault(owner, 0);
			if (amount <= 0) {
				continue;
			}
			total += token.priceMg() * (long) amount;
			holdings.remove(owner);
			token.removeCirculating(amount);
			repository.saveToken(token);
			repository.saveHolding(token.id(), owner, 0);
		}
		if (total > 0) {
			centralBank.addMunicipalBudget(total);
		}
		return total;
	}

	public Optional<ExchangeToken> findToken(String symbol) {
		return Optional.ofNullable(tokensBySymbol.get(symbol.toUpperCase()));
	}

	public long sharePrice(Company company, double economyIndex) {
		return company.sharePrice(economyIndex);
	}

	public String formatPrice(long mg) {
		return GoldStandard.formatMilligrams(mg);
	}
}
