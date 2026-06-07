package com.mceconomy.exchange;

import com.mceconomy.McEconomyMod;
import com.mceconomy.company.Company;
import com.mceconomy.company.CompanyManager;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.bootstrap.EconomyBootstrap;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.EconomyEventCategory;
import com.mceconomy.economy.EconomyEventDirection;
import com.mceconomy.economy.EconomyEventService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.ExchangeRepository;
import com.mceconomy.regulation.MasakService;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ExchangeService {
	private record TradeTick(long priceMg, long atMs) {
	}

	private final Map<String, ExchangeToken> tokensBySymbol = new HashMap<>();
	private final Map<Integer, Map<UUID, TokenHolding>> tokenHoldings = new HashMap<>();
	private final Map<String, Deque<Long>> recentTrades = new HashMap<>();
	private final Map<String, Deque<TradeTick>> symbolTradeHistory = new HashMap<>();
	private final Map<String, Long> circuitBreakerUntil = new HashMap<>();
	private final List<ExchangeLimitOrder> limitOrders = new ArrayList<>();
	private final ExchangeRepository repository;
	private final CurrencyService currencyService;
	private final CompanyManager companyManager;
	private final MasakService masakService;
	private final ExchangeTaxService exchangeTaxService;
	private EconomyEventService economyEventService;

	public ExchangeService(ExchangeRepository repository, CurrencyService currencyService,
			CompanyManager companyManager, MasakService masakService, ExchangeTaxService exchangeTaxService) {
		this.repository = repository;
		this.currencyService = currencyService;
		this.companyManager = companyManager;
		this.masakService = masakService;
		this.exchangeTaxService = exchangeTaxService;
	}

	public void bindEconomyEventService(EconomyEventService economyEventService) {
		this.economyEventService = economyEventService;
	}

	public void load() throws SQLException {
		tokensBySymbol.clear();
		tokenHoldings.clear();
		recentTrades.clear();
		symbolTradeHistory.clear();
		circuitBreakerUntil.clear();
		limitOrders.clear();
		for (ExchangeToken token : repository.loadAllTokens()) {
			tokensBySymbol.put(token.symbol(), token);
		}
		Map<Integer, Map<UUID, Integer>> loaded = repository.loadAllHoldings();
		Map<Integer, Map<UUID, Long>> costBasis = repository.loadAllCostBasis();
		for (Map.Entry<Integer, Map<UUID, Integer>> entry : loaded.entrySet()) {
			Map<UUID, TokenHolding> map = new HashMap<>();
			for (Map.Entry<UUID, Integer> holding : entry.getValue().entrySet()) {
				long basis = costBasis.getOrDefault(entry.getKey(), Map.of())
						.getOrDefault(holding.getKey(), 0L);
				map.put(holding.getKey(), new TokenHolding(holding.getValue(), basis));
			}
			tokenHoldings.put(entry.getKey(), map);
		}
		limitOrders.addAll(repository.loadOpenLimitOrders());
		for (ExchangeToken token : tokensBySymbol.values()) {
			recordPriceTick(token.symbol(), token.priceMg());
		}
	}

	public void saveAll() throws SQLException {
		for (ExchangeToken token : tokensBySymbol.values()) {
			repository.saveToken(token);
		}
		for (Map.Entry<Integer, Map<UUID, TokenHolding>> entry : tokenHoldings.entrySet()) {
			for (Map.Entry<UUID, TokenHolding> holding : entry.getValue().entrySet()) {
				TokenHolding h = holding.getValue();
				repository.saveHolding(entry.getKey(), holding.getKey(), h.amount(), h.costBasisMg());
			}
		}
	}

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
				.put(com.mceconomy.bootstrap.EconomyBootstrap.SYSTEM_OWNER,
						new TokenHolding(treasuryHold, 0));
		repository.saveToken(token);
		repository.saveHolding(token.id(), com.mceconomy.bootstrap.EconomyBootstrap.SYSTEM_OWNER, treasuryHold, 0);
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
				.put(creator, new TokenHolding(totalSupply, 0));
		repository.saveHolding(token.id(), creator, totalSupply, 0);
		return true;
	}

	public boolean listCompany(UUID owner, String companyName, String ticker) throws SQLException {
		return companyManager.listOnExchange(companyName, ticker, owner, EconomyConfig.exchangeListingFeeMg());
	}

	public boolean buyToken(UUID buyer, String symbol, int amount) throws SQLException {
		return executeBuy(buyer, symbol, amount, false);
	}

	public boolean sellToken(UUID seller, String symbol, int amount) throws SQLException {
		return executeSell(seller, symbol, amount, false);
	}

	public String placeLimitOrder(UUID owner, String symbol, boolean isBuy, int amount, long limitPriceMg)
			throws SQLException {
		if (amount <= 0 || limitPriceMg <= 0) {
			return "Gecersiz emir.";
		}
		if (tokensBySymbol.get(symbol.toUpperCase()) == null) {
			return "Coin bulunamadi.";
		}
		if (isBuy) {
			long est = limitPriceMg * amount;
			long commission = exchangeTaxService.spotCommissionMg(est);
			if (!currencyService.withdraw(owner, est + commission, TransactionType.EXCHANGE_TOKEN)) {
				return "Yetersiz bakiye (emir + komisyon).";
			}
		} else {
			ExchangeToken token = tokensBySymbol.get(symbol.toUpperCase());
			TokenHolding holding = holdingOf(token, owner);
			if (holding == null || holding.amount() < amount) {
				return "Yetersiz coin.";
			}
			holding.remove(amount);
			persistHolding(token, owner, holding);
		}
		try {
			ExchangeLimitOrder order = new ExchangeLimitOrder(-1, owner, symbol.toUpperCase(), isBuy, amount,
					limitPriceMg, System.currentTimeMillis(), true);
			int id = repository.insertLimitOrder(order);
			limitOrders.add(new ExchangeLimitOrder(id, owner, symbol.toUpperCase(), isBuy, amount,
					limitPriceMg, order.createdAt(), true));
			return "LIMIT EMIR: " + (isBuy ? "AL" : "SAT") + " " + amount + " " + symbol.toUpperCase()
					+ " @ " + GoldStandard.formatMilligrams(limitPriceMg);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Limit emir", e);
			return "Emir kaydedilemedi.";
		}
	}

	public boolean cancelLimitOrder(UUID owner, int orderId) throws SQLException {
		ExchangeLimitOrder order = limitOrders.stream()
				.filter(o -> o.id() == orderId && o.owner().equals(owner) && o.isOpen())
				.findFirst().orElse(null);
		if (order == null) {
			return false;
		}
		order.cancel();
		repository.cancelLimitOrder(orderId);
		if (order.isBuy()) {
			long refund = order.limitPriceMg() * order.amount();
			long commission = exchangeTaxService.spotCommissionMg(refund);
			currencyService.deposit(owner, refund + commission, TransactionType.EXCHANGE_TOKEN);
		} else {
			ExchangeToken token = tokensBySymbol.get(order.symbol());
			if (token != null) {
				TokenHolding holding = holdingOf(token, owner);
				if (holding == null) {
					holding = new TokenHolding(0, 0);
				}
				holding.add(order.amount(), order.limitPriceMg() * order.amount());
				persistHolding(token, owner, holding);
			}
		}
		limitOrders.removeIf(o -> o.id() == orderId);
		return true;
	}

	public void processLimitOrders() throws SQLException {
		List<ExchangeLimitOrder> toFill = new ArrayList<>();
		for (ExchangeLimitOrder order : limitOrders) {
			if (!order.isOpen()) {
				continue;
			}
			long mark = markPriceMg(order.symbol());
			if (mark <= 0) {
				continue;
			}
			boolean fill = order.isBuy() ? mark <= order.limitPriceMg() : mark >= order.limitPriceMg();
			if (fill) {
				toFill.add(order);
			}
		}
		for (ExchangeLimitOrder order : toFill) {
			boolean ok = order.isBuy()
					? executeBuy(order.owner(), order.symbol(), order.amount(), true)
					: executeSell(order.owner(), order.symbol(), order.amount(), true);
			if (ok) {
				order.cancel();
				repository.cancelLimitOrder(order.id());
				limitOrders.removeIf(o -> o.id() == order.id());
			}
		}
	}

	public long markPriceMg(String symbol) {
		String key = symbol.toUpperCase();
		Deque<TradeTick> history = symbolTradeHistory.get(key);
		ExchangeToken token = tokensBySymbol.get(key);
		if (history == null || history.isEmpty()) {
			return token != null ? token.priceMg() : 0L;
		}
		int count = EconomyConfig.exchangeMarkPriceTradeCount();
		long sum = 0;
		int n = 0;
		for (TradeTick tick : history) {
			sum += tick.priceMg();
			n++;
			if (n >= count) {
				break;
			}
		}
		return n > 0 ? sum / n : (token != null ? token.priceMg() : 0L);
	}

	public OpenInterest openInterest(String symbol) {
		LeverageService leverage = leverageService();
		if (leverage == null) {
			return new OpenInterest(0, 0);
		}
		return leverage.openInterestFor(symbol);
	}

	private boolean executeBuy(UUID buyer, String symbol, int amount, boolean fromLimit) throws SQLException {
		if (masakService.isRestricted(buyer)) {
			return false;
		}
		if (isCircuitBroken(symbol)) {
			return false;
		}
		LeverageService leverage = leverageService();
		if (leverage != null && leverage.hasOpenLong(buyer, symbol)) {
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
		long unitPrice = fromLimit ? token.priceMg() : token.priceMg();
		long cost = unitPrice * amount;
		long commission = exchangeTaxService.spotCommissionMg(cost);
		if (!fromLimit && !currencyService.withdraw(buyer, cost + commission, TransactionType.EXCHANGE_TOKEN)) {
			return false;
		}
		token.depositTreasury(cost);
		token.addCirculating(amount);
		double impact = slippageImpact(token, amount, effectivePriceImpact(token, buyer, true));
		if (impact > 0) {
			token.setPriceMg((long) (token.priceMg() * (1 + impact)));
		}
		recordPriceTick(token.symbol(), token.priceMg());
		checkCircuitBreaker(token.symbol(), token.priceMg());
		recordTrade(buyer, token.symbol(), true);
		TokenHolding holding = holdingOf(token, buyer);
		if (holding == null) {
			holding = new TokenHolding(0, 0);
		}
		holding.add(amount, cost);
		persistHolding(token, buyer, holding);
		repository.saveToken(token);
		logTokenTrade(buyer, token, amount, cost, commission, true);
		return true;
	}

	private boolean executeSell(UUID seller, String symbol, int amount, boolean fromLimit) throws SQLException {
		if (masakService.isRestricted(seller)) {
			return false;
		}
		if (isCircuitBroken(symbol)) {
			return false;
		}
		LeverageService leverage = leverageService();
		if (leverage != null && leverage.hasOpenShort(seller, symbol)) {
			return false;
		}
		ExchangeToken token = tokensBySymbol.get(symbol.toUpperCase());
		if (token == null || amount <= 0) {
			return false;
		}
		TokenHolding holding = holdingOf(token, seller);
		long costRemoved;
		if (fromLimit) {
			costRemoved = amount * token.priceMg();
		} else {
			if (holding == null || holding.amount() < amount) {
				return false;
			}
			costRemoved = holding.remove(amount);
		}
		long payout = token.priceMg() * amount;
		long profit = Math.max(0, payout - costRemoved);
		long profitStopaj = exchangeTaxService.spotProfitStopajMg(profit);
		long commission = exchangeTaxService.spotCommissionMg(payout);
		long netPayout = payout - commission - profitStopaj;
		if (token.treasuryMg() < netPayout) {
			if (!fromLimit && holding != null) {
				holding.add(amount, costRemoved);
				persistHolding(token, seller, holding);
			}
			return false;
		}
		token.removeCirculating(amount);
		double impact = slippageImpact(token, amount, effectivePriceImpact(token, seller, false));
		if (impact > 0) {
			token.setPriceMg(Math.max(1, (long) (token.priceMg() * (1 - impact))));
		}
		recordPriceTick(token.symbol(), token.priceMg());
		checkCircuitBreaker(token.symbol(), token.priceMg());
		recordTrade(seller, token.symbol(), false);
		token.withdrawTreasury(netPayout);
		if (!currencyService.deposit(seller, netPayout, TransactionType.EXCHANGE_TOKEN)) {
			return false;
		}
		if (!fromLimit && holding != null) {
			persistHolding(token, seller, holding);
		}
		repository.saveToken(token);
		logTokenTrade(seller, token, amount, netPayout, commission + profitStopaj, false);
		return true;
	}

	private void logTokenTrade(UUID trader, ExchangeToken token, int amount, long tradeAmountMg, long feesMg,
			boolean buy) {
		if (economyEventService == null) {
			return;
		}
		String traderName = economyEventService.resolveName(trader);
		String action = buy ? "aldi" : "satti";
		economyEventService.recordPersonal(trader, EconomyEventCategory.EXCHANGE,
				buy ? EconomyEventDirection.OUT : EconomyEventDirection.IN, tradeAmountMg + feesMg,
				null, token.symbol(), amount, buy ? "SPOT_BUY" : "SPOT_SELL",
				traderName + " " + amount + "x " + token.symbol() + " " + action
						+ " (" + GoldStandard.formatMilligrams(tradeAmountMg) + ")");
		if (feesMg > 0) {
			economyEventService.recordPersonal(trader, EconomyEventCategory.TAX_FEE, EconomyEventDirection.OUT,
					feesMg, "EXCHANGE_FEE", "Borsa komisyon/stopaj: " + GoldStandard.formatMilligrams(feesMg));
		}
		UUID creator = token.creatorUuid();
		if (creator != null && !creator.equals(trader) && !creator.equals(EconomyBootstrap.SYSTEM_OWNER)) {
			economyEventService.recordPersonal(creator, EconomyEventCategory.COIN_CREATOR,
					buy ? EconomyEventDirection.IN : EconomyEventDirection.OUT, tradeAmountMg,
					trader, token.symbol(), amount, buy ? "SPOT_BUY" : "SPOT_SELL",
					traderName + " sizin " + token.symbol() + " coininizden " + amount + " adet " + action);
		}
	}

	private double slippageImpact(ExchangeToken token, int amount, double baseImpact) {
		if (baseImpact <= 0 || token.circulating() <= 0) {
			return baseImpact;
		}
		double sizeRatio = (double) amount / token.circulating();
		double mult = 1.0 + sizeRatio * EconomyConfig.exchangeSlippageImpactMultiplier();
		return baseImpact * mult;
	}

	private void recordPriceTick(String symbol, long priceMg) {
		String key = symbol.toUpperCase();
		Deque<TradeTick> history = symbolTradeHistory.computeIfAbsent(key, k -> new ArrayDeque<>());
		long now = System.currentTimeMillis();
		history.addFirst(new TradeTick(priceMg, now));
		while (history.size() > EconomyConfig.exchangeMarkPriceTradeCount() * 2) {
			history.removeLast();
		}
	}

	private void checkCircuitBreaker(String symbol, long newPriceMg) {
		String key = symbol.toUpperCase();
		Deque<TradeTick> history = symbolTradeHistory.get(key);
		if (history == null || history.size() < 2) {
			return;
		}
		long window = EconomyConfig.exchangeCircuitBreakerWindowMs();
		long now = System.currentTimeMillis();
		TradeTick oldest = null;
		for (TradeTick tick : history) {
			if (now - tick.atMs() <= window) {
				oldest = tick;
				break;
			}
		}
		if (oldest == null || oldest.priceMg() <= 0) {
			return;
		}
		long changeBps = Math.abs((newPriceMg - oldest.priceMg()) * 10_000L / oldest.priceMg());
		if (changeBps >= EconomyConfig.exchangeCircuitBreakerBps()) {
			circuitBreakerUntil.put(key, now + 60_000L);
			McEconomyMod.LOGGER.info("Circuit breaker: {} — {} bps hareket", key, changeBps);
		}
	}

	private boolean isCircuitBroken(String symbol) {
		Long until = circuitBreakerUntil.get(symbol.toUpperCase());
		return until != null && System.currentTimeMillis() < until;
	}

	private double effectivePriceImpact(ExchangeToken token, UUID trader, boolean isBuy) {
		double base = EconomyConfig.exchangePriceImpact();
		if (trader.equals(token.creatorUuid())) {
			return base * EconomyConfig.exchangeSelfTradeImpactMultiplier();
		}
		int held = tokenBalance(trader, token);
		int circulating = token.circulating();
		if (circulating > 0) {
			double sharePct = held * 100.0 / circulating;
			if (sharePct >= EconomyConfig.exchangeLargeHolderThresholdPct()) {
				return base * EconomyConfig.exchangeLargeHolderImpactMultiplier();
			}
		}
		if (exceedsWashLimit(trader, token.symbol(), isBuy)) {
			return 0;
		}
		return base;
	}

	private void recordTrade(UUID trader, String symbol, boolean isBuy) {
		String key = tradeKey(trader, symbol, isBuy);
		long now = System.currentTimeMillis();
		Deque<Long> moves = recentTrades.computeIfAbsent(key, k -> new ArrayDeque<>());
		moves.addLast(now);
		long window = EconomyConfig.exchangeWashTradeWindowMs();
		while (!moves.isEmpty() && now - moves.peekFirst() > window) {
			moves.removeFirst();
		}
	}

	private boolean exceedsWashLimit(UUID trader, String symbol, boolean isBuy) {
		String key = tradeKey(trader, symbol, isBuy);
		Deque<Long> moves = recentTrades.get(key);
		if (moves == null || moves.isEmpty()) {
			return false;
		}
		long now = System.currentTimeMillis();
		long window = EconomyConfig.exchangeWashTradeWindowMs();
		int count = 0;
		for (Long timestamp : moves) {
			if (now - timestamp <= window) {
				count++;
			}
		}
		return count >= EconomyConfig.exchangeWashTradeMaxMoves();
	}

	private static String tradeKey(UUID trader, String symbol, boolean isBuy) {
		return trader + ":" + symbol.toUpperCase() + ":" + (isBuy ? "buy" : "sell");
	}

	private TokenHolding holdingOf(ExchangeToken token, UUID owner) {
		Map<UUID, TokenHolding> map = tokenHoldings.get(token.id());
		return map != null ? map.get(owner) : null;
	}

	private void persistHolding(ExchangeToken token, UUID owner, TokenHolding holding) throws SQLException {
		if (holding.amount() <= 0) {
			tokenHoldings.computeIfAbsent(token.id(), k -> new HashMap<>()).remove(owner);
			repository.saveHolding(token.id(), owner, 0, 0);
		} else {
			tokenHoldings.computeIfAbsent(token.id(), k -> new HashMap<>()).put(owner, holding);
			repository.saveHolding(token.id(), owner, holding.amount(), holding.costBasisMg());
		}
	}

	private static LeverageService leverageService() {
		var manager = McEconomyMod.getEconomyManager();
		return manager != null ? manager.leverageService() : null;
	}

	public List<ExchangeToken> allTokens() {
		return new ArrayList<>(tokensBySymbol.values());
	}

	public List<ExchangeLimitOrder> openLimitOrders(UUID owner) {
		return limitOrders.stream().filter(o -> o.isOpen() && o.owner().equals(owner)).toList();
	}

	public long totalCirculatingMarketCapMg() {
		long total = 0;
		for (ExchangeToken token : tokensBySymbol.values()) {
			total += (long) token.circulating() * token.priceMg();
		}
		return total;
	}

	public List<Company> listedCompanies() {
		return companyManager.listedCompanies();
	}

	public int tokenBalance(UUID player, ExchangeToken token) {
		TokenHolding holding = holdingOf(token, player);
		return holding != null ? holding.amount() : 0;
	}

	public long seizeAllTokens(UUID owner, com.mceconomy.tax.CentralBank centralBank) throws SQLException {
		long total = 0;
		for (ExchangeToken token : allTokens()) {
			TokenHolding holding = holdingOf(token, owner);
			if (holding == null || holding.amount() <= 0) {
				continue;
			}
			int amount = holding.amount();
			total += token.priceMg() * (long) amount;
			persistHolding(token, owner, new TokenHolding(0, 0));
			token.removeCirculating(amount);
			repository.saveToken(token);
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

	public boolean adminSetTokenHolding(UUID player, String symbol, int amount) throws SQLException {
		ExchangeToken token = tokensBySymbol.get(symbol.toUpperCase());
		if (token == null || amount < 0) {
			return false;
		}
		if (amount == 0) {
			tokenHoldings.computeIfAbsent(token.id(), k -> new HashMap<>()).remove(player);
		} else {
			tokenHoldings.computeIfAbsent(token.id(), k -> new HashMap<>())
					.put(player, new TokenHolding(amount, token.priceMg() * amount));
		}
		repository.saveHolding(token.id(), player, Math.max(0, amount), token.priceMg() * Math.max(0, amount));
		return true;
	}

	public boolean adminUpdateToken(String symbol, Long priceMg, Integer circulating) throws SQLException {
		ExchangeToken token = tokensBySymbol.get(symbol.toUpperCase());
		if (token == null) {
			return false;
		}
		if (priceMg != null) {
			token.setPriceMg(priceMg);
		}
		if (circulating != null) {
			token.setCirculating(circulating);
		}
		repository.saveToken(token);
		return true;
	}

	public boolean adminDeleteToken(String symbol) throws SQLException {
		ExchangeToken token = tokensBySymbol.get(symbol.toUpperCase());
		if (token == null) {
			return false;
		}
		Map<UUID, TokenHolding> holdings = tokenHoldings.get(token.id());
		if (holdings != null) {
			for (TokenHolding h : holdings.values()) {
				if (h.amount() > 0) {
					return false;
				}
			}
		}
		tokensBySymbol.remove(symbol.toUpperCase());
		tokenHoldings.remove(token.id());
		repository.deleteToken(token.id());
		return true;
	}

	public record OpenInterest(long longNotionalMg, long shortNotionalMg) {
	}
}
