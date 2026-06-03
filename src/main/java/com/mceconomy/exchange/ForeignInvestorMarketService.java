package com.mceconomy.exchange;

import com.mceconomy.McEconomyMod;
import com.mceconomy.bootstrap.EconomyBootstrap;
import com.mceconomy.company.Company;
import com.mceconomy.company.CompanyManager;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.EconomyManager;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Yabanci ve yerli yatirimci NPC'leri — coin/hisse alim-satim, oyuncu sirketlerine yatirim. */
public final class ForeignInvestorMarketService {
	private record Investor(UUID uuid, String name) {
	}

	private static final String[] INVESTOR_NAMES = {
			"Zurich Capital", "Dubai Holdings", "Nordic Fund", "Silicon Valley LP",
			"Tokyo Meridian", "London Bridge VC", "Sao Paulo Invest", "Seoul Alpha",
			"Anadolu Yatirim", "Istanbul Portfoy", "Kapadokya Fon", "Ege Sermaye",
			"Karadeniz Holding", "Bogazici VC", "Ankara Emeklilik", "Marmara Capital"
	};

	private final List<Investor> investors = new ArrayList<>();
	private int tickCounter;
	private int actionCounter;

	public void bootstrapInvestors(EconomyManager manager) {
		investors.clear();
		CurrencyService currency = manager.currencyService();
		long seedCapital = EconomyConfig.foreignInvestorCapitalMg();
		for (int i = 0; i < INVESTOR_NAMES.length; i++) {
			UUID uuid = UUID.nameUUIDFromBytes(("mceconomy-investor-" + i).getBytes());
			manager.ensurePlayer(uuid, INVESTOR_NAMES[i]);
			long balance = currency.getBalance(uuid);
			if (balance < seedCapital) {
				currency.deposit(uuid, seedCapital - balance, TransactionType.DEPOSIT);
			}
			investors.add(new Investor(uuid, INVESTOR_NAMES[i]));
		}
		McEconomyMod.LOGGER.info("[Borsa] {} yatirimci NPC aktif", investors.size());
	}

	public void tick(EconomyManager manager) {
		if (!EconomyConfig.foreignInvestorEnabled() || investors.isEmpty()) {
			return;
		}
		tickCounter++;
		if (tickCounter % EconomyConfig.foreignInvestorIntervalTicks() != 0) {
			return;
		}
		int trades = 1 + (ThreadLocalRandom.current().nextInt(100) < 20 ? 1 : 0);
		for (int i = 0; i < trades; i++) {
			Investor investor = investors.get(ThreadLocalRandom.current().nextInt(investors.size()));
			try {
				ensureCapital(manager.currencyService(), investor);
				performRandomTrade(manager, investor);
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Yatirimci NPC islemi basarisiz", e);
			}
		}
	}

	private void ensureCapital(CurrencyService currency, Investor investor) {
		long target = EconomyConfig.foreignInvestorCapitalMg();
		long min = target / 5;
		long balance = currency.getBalance(investor.uuid());
		if (balance < min) {
			currency.deposit(investor.uuid(), target - balance, TransactionType.DEPOSIT);
		}
	}

	private void performRandomTrade(EconomyManager manager, Investor investor) throws SQLException {
		ExchangeService exchange = manager.exchangeService();
		CompanyManager companyManager = manager.companyManager();
		int roll = ThreadLocalRandom.current().nextInt(100);

		if (roll < 8 && exchange.allTokens().size() < EconomyConfig.foreignInvestorMaxTokens()) {
			tryLaunchCoin(exchange, investor);
			return;
		}
		if (roll < 45 && !exchange.allTokens().isEmpty()) {
			tryBuyToken(manager, exchange, investor, roll < 25);
			return;
		}
		if (roll < 58 && !exchange.allTokens().isEmpty()) {
			trySellToken(exchange, investor);
			return;
		}
		if (roll < 88) {
			tryBuyShares(manager, companyManager, investor);
			return;
		}
		trySellShares(manager, companyManager, investor);
	}

	private void tryBuyToken(EconomyManager manager, ExchangeService exchange, Investor investor,
			boolean preferPlayerCoin) throws SQLException {
		ExchangeToken token = pickToken(exchange, preferPlayerCoin);
		if (token == null) {
			return;
		}
		int amount = investmentAmount(manager.currencyService(), investor, token.priceMg());
		if (amount <= 0) {
			return;
		}
		if (!exchange.buyToken(investor.uuid(), token.symbol(), amount)) {
			return;
		}
		String kind = isPlayerToken(token) ? "oyuncu coini" : "coin";
		broadcastTrade(investor.name() + " §a" + amount + "x " + token.symbol() + " §7(" + kind + ") aldi");
		if (isPlayerToken(token)) {
			notifyTokenCreator(manager, token, investor, amount, true);
		}
	}

	private void trySellToken(ExchangeService exchange, Investor investor) throws SQLException {
		List<ExchangeToken> held = new ArrayList<>();
		for (ExchangeToken token : exchange.allTokens()) {
			if (exchange.tokenBalance(investor.uuid(), token) > 0) {
				held.add(token);
			}
		}
		if (held.isEmpty()) {
			return;
		}
		ExchangeToken token = held.get(ThreadLocalRandom.current().nextInt(held.size()));
		int owned = exchange.tokenBalance(investor.uuid(), token);
		int amount = Math.min(owned, 1 + ThreadLocalRandom.current().nextInt(Math.min(owned, 12)));
		if (exchange.sellToken(investor.uuid(), token.symbol(), amount)) {
			broadcastTrade(investor.name() + " §c" + amount + "x " + token.symbol() + " satti (kar realizasyonu)");
		}
	}

	private void tryBuyShares(EconomyManager manager, CompanyManager companyManager, Investor investor)
			throws SQLException {
		Company company = pickShareTarget(companyManager);
		if (company == null) {
			return;
		}
		double index = manager.marketService().economyIndex().calculate();
		long price = company.sharePrice(index);
		int amount = shareInvestmentAmount(manager.currencyService(), investor, price);
		if (amount <= 0) {
			return;
		}
		String ticker = company.ticker() != null ? company.ticker() : company.name();
		if (!companyManager.buyShares(investor.uuid(), ticker, amount, index)) {
			return;
		}
		boolean playerCompany = isPlayerCompany(company);
		broadcastTrade(investor.name() + " §6" + amount + "x " + ticker + " hisse aldi"
				+ (playerCompany ? " §7(oyuncu sirketi)" : ""));
		if (playerCompany) {
			notifyCompanyOwner(manager, company, investor, amount, price);
		}
	}

	private void trySellShares(EconomyManager manager, CompanyManager companyManager, Investor investor)
			throws SQLException {
		List<Company> held = new ArrayList<>();
		for (Company company : companyManager.listedCompanies()) {
			if (companyManager.getShareCount(investor.uuid(), company) > 0) {
				held.add(company);
			}
		}
		if (held.isEmpty()) {
			return;
		}
		Company company = held.get(ThreadLocalRandom.current().nextInt(held.size()));
		int owned = companyManager.getShareCount(investor.uuid(), company);
		int amount = Math.min(owned, 1 + ThreadLocalRandom.current().nextInt(Math.min(owned, 4)));
		double index = manager.marketService().economyIndex().calculate();
		String ticker = company.ticker() != null ? company.ticker() : company.name();
		if (companyManager.sellShares(investor.uuid(), ticker, amount, index)) {
			broadcastTrade(investor.name() + " §e" + amount + "x " + ticker + " hisse satti");
		}
	}

	private Company pickShareTarget(CompanyManager companyManager) {
		List<Company> listed = companyManager.listedCompanies();
		if (listed.isEmpty()) {
			return null;
		}
		List<Company> playerListed = listed.stream().filter(this::isPlayerCompany).toList();
		int bias = (int) (EconomyConfig.foreignInvestorPlayerCompanyBias() * 100);
		if (!playerListed.isEmpty() && ThreadLocalRandom.current().nextInt(100) < bias) {
			return playerListed.get(ThreadLocalRandom.current().nextInt(playerListed.size()));
		}
		return listed.get(ThreadLocalRandom.current().nextInt(listed.size()));
	}

	private ExchangeToken pickToken(ExchangeService exchange, boolean preferPlayerCoin) {
		List<ExchangeToken> all = exchange.allTokens();
		if (all.isEmpty()) {
			return null;
		}
		List<ExchangeToken> playerTokens = all.stream().filter(this::isPlayerToken).toList();
		if (preferPlayerCoin && !playerTokens.isEmpty()
				&& ThreadLocalRandom.current().nextInt(100) < 70) {
			return playerTokens.get(ThreadLocalRandom.current().nextInt(playerTokens.size()));
		}
		if (!playerTokens.isEmpty() && ThreadLocalRandom.current().nextInt(100) < 35) {
			return playerTokens.get(ThreadLocalRandom.current().nextInt(playerTokens.size()));
		}
		return all.get(ThreadLocalRandom.current().nextInt(all.size()));
	}

	private int investmentAmount(CurrencyService currency, Investor investor, long unitPriceMg) {
		if (unitPriceMg <= 0) {
			return 0;
		}
		long balance = currency.getBalance(investor.uuid());
		long maxAfford = balance / unitPriceMg;
		if (maxAfford <= 0) {
			return 0;
		}
		int cap = (int) Math.min(maxAfford, EconomyConfig.foreignInvestorMaxTokenBuy());
		return 1 + ThreadLocalRandom.current().nextInt(Math.max(1, cap));
	}

	private int shareInvestmentAmount(CurrencyService currency, Investor investor, long sharePriceMg) {
		if (sharePriceMg <= 0) {
			return 0;
		}
		long balance = currency.getBalance(investor.uuid());
		long maxAfford = balance / sharePriceMg;
		if (maxAfford <= 0) {
			return 0;
		}
		int cap = (int) Math.min(maxAfford, EconomyConfig.foreignInvestorMaxShareBuy());
		return 1 + ThreadLocalRandom.current().nextInt(Math.max(1, cap));
	}

	private boolean isPlayerCompany(Company company) {
		return company.ownerUuid() != null && !company.ownerUuid().equals(EconomyBootstrap.SYSTEM_OWNER);
	}

	private boolean isPlayerToken(ExchangeToken token) {
		return token.creatorUuid() != null && !token.creatorUuid().equals(EconomyBootstrap.SYSTEM_OWNER);
	}

	private void notifyCompanyOwner(EconomyManager manager, Company company, Investor investor, int amount, long price) {
		if (manager.server() == null) {
			return;
		}
		ServerPlayer owner = manager.server().getPlayerList().getPlayer(company.ownerUuid());
		if (owner == null) {
			return;
		}
		long total = price * amount;
		owner.sendSystemMessage(Component.literal(
				"§6[Borsa Yatirim] §f" + investor.name() + " sirketinizden §a" + amount + "x "
						+ (company.ticker() != null ? company.ticker() : company.name())
						+ " §7hisse aldi — kasaya +" + GoldStandard.formatMilligrams(total)));
	}

	private void notifyTokenCreator(EconomyManager manager, ExchangeToken token, Investor investor, int amount,
			boolean buy) {
		if (manager.server() == null || !isPlayerToken(token)) {
			return;
		}
		ServerPlayer creator = manager.server().getPlayerList().getPlayer(token.creatorUuid());
		if (creator == null) {
			return;
		}
		if (buy) {
			creator.sendSystemMessage(Component.literal(
					"§6[Borsa Yatirim] §f" + investor.name() + " coininizden §a" + amount + "x "
							+ token.symbol() + " §7aldi!"));
		}
	}

	private void tryLaunchCoin(ExchangeService exchange, Investor investor) throws SQLException {
		String symbol = randomSymbol();
		if (exchange.findToken(symbol).isPresent()) {
			return;
		}
		long price = 50_000 + ThreadLocalRandom.current().nextLong(200_000);
		int supply = 500 + ThreadLocalRandom.current().nextInt(1500);
		if (exchange.createPublicToken(symbol, investor.name() + " Coin", price, supply)) {
			broadcastTrade("§e" + investor.name() + " yeni coin acti: §6" + symbol);
		}
	}

	private String randomSymbol() {
		String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ";
		StringBuilder sb = new StringBuilder();
		int len = 3 + ThreadLocalRandom.current().nextInt(3);
		for (int i = 0; i < len; i++) {
			sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
		}
		return sb.toString();
	}

	private void broadcastTrade(String message) {
		actionCounter++;
		if (actionCounter % 2 != 0) {
			return;
		}
		var server = McEconomyMod.getEconomyManager().server();
		if (server != null) {
			server.getPlayerList().broadcastSystemMessage(
					Component.literal("§8[Borsa NPC] §7" + message), false);
		}
	}
}
