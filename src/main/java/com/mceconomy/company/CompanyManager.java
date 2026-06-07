package com.mceconomy.company;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.EconomyEventCategory;
import com.mceconomy.economy.EconomyEventDirection;
import com.mceconomy.economy.EconomyEventService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.exchange.ExchangeTaxService;
import com.mceconomy.persistence.repo.CompanyRepository;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

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
	private ExchangeTaxService exchangeTaxService;
	private EconomyEventService economyEventService;
	private int economyTickCounter;

	public CompanyManager(CompanyRepository repository, CurrencyService currencyService) {
		this.repository = repository;
		this.currencyService = currencyService;
	}

	public void bindExchangeTaxService(ExchangeTaxService exchangeTaxService) {
		this.exchangeTaxService = exchangeTaxService;
	}

	public void bindEconomyEventService(EconomyEventService economyEventService) {
		this.economyEventService = economyEventService;
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

	public long totalTreasuryMg() {
		long total = 0;
		for (Company company : companiesByName.values()) {
			total += company.treasury();
		}
		return total;
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
		if (company == null || amount <= 0 || !company.listedOnExchange() || company.insolvent()) {
			return false;
		}
		long cost = company.sharePrice(economyIndex) * amount;
		long commission = exchangeTaxService != null ? exchangeTaxService.shareCommissionMg(cost) : 0;
		if (!currencyService.withdraw(buyer, cost + commission, TransactionType.COMPANY)) {
			return false;
		}
		company.deposit(cost);
		ShareHolding holding = shares.computeIfAbsent(company.id(), k -> new HashMap<>())
				.computeIfAbsent(buyer, u -> new ShareHolding(company.id(), u, 0));
		holding.add(amount);
		repository.save(company);
		repository.saveShare(holding);
		logShareTrade(company, buyer, amount, cost, commission, true);
		return true;
	}

	public boolean sellShares(UUID seller, String companyNameOrTicker, int amount, double economyIndex) throws SQLException {
		Company company = resolveCompany(companyNameOrTicker);
		if (company == null || amount <= 0 || !company.listedOnExchange() || company.insolvent()) {
			return false;
		}
		ShareHolding holding = shares.getOrDefault(company.id(), Map.of()).get(seller);
		if (holding == null || !holding.remove(amount)) {
			return false;
		}
		long payout = company.sharePrice(economyIndex) * amount;
		long commission = exchangeTaxService != null ? exchangeTaxService.shareCommissionMg(payout) : 0;
		if (company.treasury() < payout) {
			holding.add(amount);
			return false;
		}
		company.withdraw(payout);
		currencyService.deposit(seller, payout - commission, TransactionType.COMPANY);
		repository.save(company);
		repository.saveShare(holding);
		logShareTrade(company, seller, amount, payout, commission, false);
		return true;
	}

	private void logShareTrade(Company company, UUID trader, int amount, long tradeMg, long commission, boolean buy) {
		if (economyEventService == null) {
			return;
		}
		String traderName = economyEventService.resolveName(trader);
		String label = company.ticker() != null ? company.ticker() : company.name();
		String action = buy ? "aldi" : "satti";
		economyEventService.recordPersonal(trader, EconomyEventCategory.SHARES,
				buy ? EconomyEventDirection.OUT : EconomyEventDirection.IN, tradeMg + commission,
				company.ownerUuid(), label, amount, buy ? "SHARE_BUY" : "SHARE_SELL",
				traderName + " " + company.name() + " hissesinden " + amount + " adet " + action);
		if (commission > 0) {
			economyEventService.recordPersonal(trader, EconomyEventCategory.TAX_FEE, EconomyEventDirection.OUT,
					commission, "SHARE_FEE", "Hisse komisyonu: " + GoldStandard.formatMilligrams(commission));
		}
		if (!company.ownerUuid().equals(trader)) {
			economyEventService.recordPersonal(company.ownerUuid(), EconomyEventCategory.SHARE_OWNER,
					buy ? EconomyEventDirection.IN : EconomyEventDirection.OUT, tradeMg,
					trader, label, amount, buy ? "SHARE_BUY" : "SHARE_SELL",
					traderName + " sizin " + company.name() + " sirketinizden " + amount + " hisse " + action);
		}
		economyEventService.recordCompany(company.id(), company.ownerUuid(),
				buy ? EconomyEventCategory.TREASURY_IN : EconomyEventCategory.TREASURY_OUT,
				buy ? EconomyEventDirection.IN : EconomyEventDirection.OUT, tradeMg,
				trader, label, amount, buy ? "SHARE_BUY" : "SHARE_SELL",
				(buy ? "Hisse satisi geliri: " : "Hisse geri alim odemesi: ")
						+ traderName + " — " + amount + " adet");
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

	public void economyTick(double economyIndex, MinecraftServer server) {
		economyTickCounter++;
		if (economyTickCounter % EconomyConfig.companyDividendIntervalTicks() == 0) {
			try {
				payDividends(economyIndex, server);
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Temettu odemesi", e);
			}
		}
		if (economyTickCounter % 1200 == 0) {
			try {
				checkBankruptcy(server);
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Sirket iflas kontrolu", e);
			}
		}
	}

	private void payDividends(double economyIndex, MinecraftServer server) throws SQLException {
		long now = System.currentTimeMillis();
		for (Company company : listedCompanies()) {
			if (company.insolvent() || company.treasury() < company.outstandingShares() * 10L) {
				continue;
			}
			if (now - company.lastDividendAt() < EconomyConfig.companyDividendIntervalTicks() * 50L) {
				continue;
			}
			long pool = company.treasury() / 20;
			if (pool <= 0) {
				continue;
			}
			Map<UUID, ShareHolding> holders = shares.get(company.id());
			if (holders == null || holders.isEmpty()) {
				continue;
			}
			int totalHeld = holders.values().stream().mapToInt(ShareHolding::amount).sum();
			if (totalHeld <= 0) {
				continue;
			}
			company.withdraw(pool);
			for (ShareHolding holding : holders.values()) {
				if (holding.amount() <= 0) {
					continue;
				}
				long share = (pool * holding.amount()) / totalHeld;
				if (share > 0) {
					currencyService.deposit(holding.ownerUuid(), share, TransactionType.COMPANY);
				}
			}
			company.setLastDividendAt(now);
			repository.save(company);
			if (server != null) {
				server.getPlayerList().broadcastSystemMessage(Component.literal(
						"§6[Temettü] §f" + company.name() + " — "
								+ GoldStandard.formatMilligrams(pool) + " dagitildi."), false);
			}
		}
	}

	private void checkBankruptcy(MinecraftServer server) throws SQLException {
		long threshold = EconomyConfig.companyBankruptcyTreasuryMg();
		for (Company company : listedCompanies()) {
			if (company.treasury() > threshold) {
				continue;
			}
			if (company.ticker() != null) {
				companiesByTicker.remove(company.ticker().toUpperCase());
			}
			company.markInsolvent();
			repository.save(company);
			if (server != null) {
				server.getPlayerList().broadcastSystemMessage(Component.literal(
						"§4[İflas] §c" + company.name() + " borsadan cikarildi (yetersiz kasa)."), false);
			}
		}
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
