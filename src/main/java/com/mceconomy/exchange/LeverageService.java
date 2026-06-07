package com.mceconomy.exchange;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.EconomyEventCategory;
import com.mceconomy.economy.EconomyEventDirection;
import com.mceconomy.economy.EconomyEventService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.persistence.repo.LeverageRepository;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Kaldiracli (marjinli) borsa islemleri: long/short pozisyon ac/kapat ve otomatik likidasyon. */
public final class LeverageService {
	public static final int MAX_LEVERAGE = 10;

	private final LeverageRepository repository;
	private final ExchangeService exchangeService;
	private final ExchangeCollateralService collateralService;
	private final ExchangeTaxService exchangeTaxService;
	private final LeveragePool pool = new LeveragePool();
	private MinecraftServer server;
	private EconomyEventService economyEventService;

	private final List<LeveragePosition> positions = new ArrayList<>();
	private final Set<String> marginCallNotified = new HashSet<>();
	private final Map<Integer, Long> marginCallSince = new HashMap<>();

	public LeverageService(LeverageRepository repository, ExchangeService exchangeService,
			ExchangeCollateralService collateralService, ExchangeTaxService exchangeTaxService) {
		this.repository = repository;
		this.exchangeService = exchangeService;
		this.collateralService = collateralService;
		this.exchangeTaxService = exchangeTaxService;
	}

	public void bindServer(MinecraftServer server) {
		this.server = server;
	}

	public void bindEconomyEventService(EconomyEventService economyEventService) {
		this.economyEventService = economyEventService;
	}

	public void load() throws SQLException {
		positions.clear();
		marginCallNotified.clear();
		marginCallSince.clear();
		positions.addAll(repository.loadOpen());
		pool.setBalanceMg(repository.loadPoolBalanceMg());
		if (pool.balanceMg() == 0 && EconomyConfig.leveragePoolSeedMg() > 0) {
			pool.credit(EconomyConfig.leveragePoolSeedMg());
			savePool();
		}
		reconcilePoolWithOpenMargins();
	}

	public void savePool() {
		try {
			repository.savePoolBalanceMg(pool.balanceMg());
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Kaldirac havuzu kaydedilemedi", e);
		}
	}

	private void reconcilePoolWithOpenMargins() {
		long marginSum = 0;
		for (LeveragePosition pos : positions) {
			if (pos.isOpen()) {
				marginSum += pos.marginMg();
			}
		}
		if (pool.balanceMg() < marginSum) {
			pool.setBalanceMg(marginSum);
			savePool();
		}
	}

	public synchronized long lockedMarginMg(UUID owner) {
		long sum = 0;
		for (LeveragePosition pos : positions) {
			if (pos.isOpen() && pos.owner().equals(owner)) {
				sum += pos.marginMg();
			}
		}
		return sum;
	}

	public synchronized ExchangeService.OpenInterest openInterestFor(String symbol) {
		String key = symbol.toUpperCase();
		long longN = 0;
		long shortN = 0;
		for (LeveragePosition pos : positions) {
			if (!pos.isOpen() || !pos.symbol().equals(key)) {
				continue;
			}
			if (pos.isLong()) {
				longN += pos.notionalMg();
			} else {
				shortN += pos.notionalMg();
			}
		}
		return new ExchangeService.OpenInterest(longN, shortN);
	}

	public synchronized String openPosition(UUID owner, String symbol, boolean isLong, int leverage, long marginMg) {
		if (leverage < 2 || leverage > MAX_LEVERAGE) {
			return "Kaldirac 2x ile " + MAX_LEVERAGE + "x arasinda olmali.";
		}
		if (marginMg <= 0) {
			return "Gecersiz teminat miktari.";
		}
		var tokenOpt = exchangeService.findToken(symbol);
		if (tokenOpt.isEmpty()) {
			return "Coin bulunamadi: " + symbol;
		}
		ExchangeToken token = tokenOpt.get();
		if (token.creatorUuid().equals(owner)) {
			return "Kendi olusturdugun coinde kaldirac acilamaz.";
		}
		long entry = exchangeService.markPriceMg(symbol);
		if (entry <= 0) {
			return "Coin fiyati gecersiz.";
		}
		long openFee = feeFromBps(marginMg, EconomyConfig.leverageOpenFeeBps());
		long totalCost = marginMg + openFee;
		long locked = lockedMarginMg(owner);
		if (!collateralService.debit(owner, totalCost, locked)) {
			return "Yetersiz borsa teminat bakiyesi. Once cuzdandan teminat yatirin.";
		}
		long notional = marginMg * leverage;
		long sizeMilli = (notional * 1000L) / entry;
		LeveragePosition pos = new LeveragePosition(-1, owner, symbol.toUpperCase(), isLong, leverage,
				marginMg, entry, sizeMilli, System.currentTimeMillis(), true);
		try {
			int id = repository.insert(pos);
			pos = new LeveragePosition(id, owner, symbol.toUpperCase(), isLong, leverage,
					marginMg, entry, sizeMilli, pos.openedAt(), true);
			positions.add(pos);
			pool.credit(totalCost);
			savePool();
		} catch (SQLException e) {
			collateralService.credit(owner, totalCost);
			McEconomyMod.LOGGER.error("Kaldirac pozisyonu kaydedilemedi", e);
			return "Pozisyon acilamadi.";
		}
		logLeverage(owner, symbol, totalCost, "OPEN",
				(isLong ? "LONG" : "SHORT") + " " + symbol.toUpperCase() + " " + leverage + "x acildi");
		String feeNote = openFee > 0 ? " (acilis ucreti " + GoldStandard.formatMilligrams(openFee) + ")" : "";
		return "ACILDI: " + (isLong ? "LONG" : "SHORT") + " " + symbol.toUpperCase()
				+ " " + leverage + "x, teminat " + GoldStandard.formatMilligrams(marginMg) + feeNote;
	}

	public synchronized String addMargin(UUID owner, int positionId, long amountMg) {
		if (amountMg <= 0) {
			return "Gecersiz tutar.";
		}
		LeveragePosition pos = findOpen(owner, positionId);
		if (pos == null) {
			return "Pozisyon bulunamadi.";
		}
		long locked = lockedMarginMg(owner);
		if (!collateralService.debit(owner, amountMg, locked)) {
			return "Yetersiz kullanilabilir teminat.";
		}
		pos.addMargin(amountMg);
		pool.credit(amountMg);
		savePool();
		marginCallSince.remove(positionId);
		try {
			repository.updateOpen(pos.id(), pos.marginMg(), pos.sizeMilliTokens());
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Teminat guncelleme", e);
			return "Teminat kaydedilemedi.";
		}
		logLeverage(owner, pos.symbol(), amountMg, "ADD_MARGIN",
				pos.symbol() + " pozisyonuna teminat eklendi");
		return "Teminat eklendi: " + GoldStandard.formatMilligrams(amountMg);
	}

	public synchronized String closePosition(UUID owner, int positionId) {
		return closePositionPartial(owner, positionId, 10_000);
	}

	public synchronized String closePositionPartial(UUID owner, int positionId, int closeBps) {
		if (closeBps <= 0 || closeBps > 10_000) {
			return "Gecersiz kapanis orani.";
		}
		LeveragePosition pos = findOpen(owner, positionId);
		if (pos == null) {
			return "Pozisyon bulunamadi.";
		}
		long price = currentPrice(pos.symbol());
		if (closeBps >= 10_000) {
			long equity = pos.equityMg(price);
			long pnl = pos.pnlMg(price);
			long payout = settleClose(pos, price, pos.sizeMilliTokens(), pos.marginMg());
			closeInternal(pos, payout);
			logLeverageClose(owner, pos.symbol(), payout, pnl);
			String capped = payout < equity ? " (havuz limiti)" : "";
			String stopajNote = pnl > 0 ? " stopaj " + GoldStandard.formatMilligrams(
					exchangeTaxService.leverageProfitStopajMg(pnl)) : "";
			return "KAPANDI: " + pos.symbol() + " — iade " + GoldStandard.formatMilligrams(payout)
					+ capped + stopajNote + " (K/Z: " + (pnl >= 0 ? "+" : "")
					+ GoldStandard.formatMilligrams(pnl) + ")";
		}
		long closeMilli = Math.max(1, pos.sizeMilliTokens() * closeBps / 10_000L);
		if (closeMilli >= pos.sizeMilliTokens()) {
			return closePosition(owner, positionId);
		}
		long marginClose = (pos.marginMg() * closeBps) / 10_000L;
		long pnl = pos.pnlMg(price, closeMilli);
		long payout = settleClose(pos, price, closeMilli, marginClose);
		if (payout > 0) {
			collateralService.credit(owner, payout);
		}
		pos.reduceSize(closeMilli);
		pool.credit(marginClose);
		savePool();
		try {
			repository.updateOpen(pos.id(), pos.marginMg(), pos.sizeMilliTokens());
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Kismi kapanis", e);
		}
		return "KISMI KAPANDI: %" + (closeBps / 100) + " " + pos.symbol()
				+ " — iade " + GoldStandard.formatMilligrams(payout)
				+ " (K/Z: " + (pnl >= 0 ? "+" : "") + GoldStandard.formatMilligrams(pnl) + ")";
	}

	public synchronized boolean adminForceClose(int positionId) {
		LeveragePosition pos = positions.stream()
				.filter(p -> p.id() == positionId && p.isOpen())
				.findFirst().orElse(null);
		if (pos == null) {
			return false;
		}
		long price = currentPrice(pos.symbol());
		long payout = settleClose(pos, price, pos.sizeMilliTokens(), pos.marginMg());
		closeInternal(pos, payout);
		positions.removeIf(p -> p.id() == positionId);
		return true;
	}

	private long settleClose(LeveragePosition pos, long currentPriceMg, long closeMilli, long marginClose) {
		long pnl = pos.pnlMg(currentPriceMg, closeMilli);
		long equity = marginClose + pnl;
		long closeFee = feeFromBps(Math.max(0, equity), EconomyConfig.leverageCloseFeeBps());
		long stopaj = exchangeTaxService.leverageProfitStopajMg(pnl);
		long afterFees = Math.max(0, equity - closeFee - stopaj);
		long payout = pool.debitUpTo(afterFees);
		savePool();
		return payout;
	}

	private void closeInternal(LeveragePosition pos, long payout) {
		pos.close();
		marginCallNotified.remove(marginCallKey(pos));
		marginCallSince.remove(pos.id());
		if (payout > 0) {
			collateralService.credit(pos.owner(), payout);
		}
		try {
			repository.markClosed(pos.id());
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Pozisyon kapatma kaydi basarisiz", e);
		}
	}

	public synchronized void liquidationTick() {
		long now = System.currentTimeMillis();
		long grace = EconomyConfig.leverageMarginCallGraceMs();
		List<LeveragePosition> toClose = new ArrayList<>();
		for (LeveragePosition pos : positions) {
			if (!pos.isOpen()) {
				continue;
			}
			long price = currentPrice(pos.symbol());
			if (price <= 0) {
				continue;
			}
			if (pos.isMarginCall(price)) {
				String key = marginCallKey(pos);
				marginCallSince.putIfAbsent(pos.id(), now);
				if (!marginCallNotified.contains(key)) {
					marginCallNotified.add(key);
					notifyOwner(pos.owner(), "§e[Margin Call] §f" + pos.symbol() + " "
							+ pos.leverage() + "x — teminat ekleyin veya pozisyonu kapatın!");
				}
			} else {
				marginCallSince.remove(pos.id());
			}
			if (pos.shouldLiquidate(price)) {
				Long since = marginCallSince.get(pos.id());
				if (since == null || now - since >= grace) {
					toClose.add(pos);
				}
			}
		}
		for (LeveragePosition pos : toClose) {
			closeInternal(pos, 0);
			notifyOwner(pos.owner(), "§4[Likidasyon] §c" + pos.symbol() + " "
					+ pos.leverage() + "x pozisyonunuz bakim marjinin altina dustu!");
		}
		positions.removeIf(p -> !p.isOpen());
	}

	public synchronized void fundingTick() {
		int baseBps = EconomyConfig.leverageFundingRateBpsPerInterval();
		if (baseBps <= 0) {
			return;
		}
		List<LeveragePosition> underfunded = new ArrayList<>();
		for (LeveragePosition pos : positions) {
			if (!pos.isOpen()) {
				continue;
			}
			int bps = dynamicFundingBps(pos.symbol(), pos.isLong(), baseBps);
			long fee = (pos.notionalMg() * bps) / 10_000L;
			if (fee <= 0) {
				continue;
			}
			long locked = lockedMarginMg(pos.owner());
			if (!collateralService.debit(pos.owner(), fee, locked)) {
				underfunded.add(pos);
				continue;
			}
			pool.credit(fee);
			logLeverage(pos.owner(), pos.symbol(), fee, "FUNDING",
					pos.symbol() + " funding ucreti");
		}
		savePool();
		long now = System.currentTimeMillis();
		long grace = EconomyConfig.leverageMarginCallGraceMs();
		for (LeveragePosition pos : underfunded) {
			long price = currentPrice(pos.symbol());
			if (price > 0 && pos.shouldLiquidate(price)) {
				Long since = marginCallSince.get(pos.id());
				if (since == null) {
					marginCallSince.put(pos.id(), now);
				} else if (now - since >= grace) {
					closeInternal(pos, 0);
					notifyOwner(pos.owner(), "§4[Likidasyon] §c" + pos.symbol()
							+ " — funding ucreti odenemedi.");
				}
			}
		}
		positions.removeIf(p -> !p.isOpen());
	}

	private int dynamicFundingBps(String symbol, boolean isLong, int baseBps) {
		ExchangeService.OpenInterest oi = openInterestFor(symbol);
		long total = oi.longNotionalMg() + oi.shortNotionalMg();
		if (total <= 0) {
			return baseBps;
		}
		double longShare = oi.longNotionalMg() / (double) total;
		double imbalance = Math.abs(longShare - 0.5) * 2.0;
		int extra = (int) Math.round(baseBps * imbalance);
		if (longShare > 0.5) {
			return isLong ? baseBps + extra : Math.max(0, baseBps - extra / 2);
		}
		if (longShare < 0.5) {
			return isLong ? Math.max(0, baseBps - extra / 2) : baseBps + extra;
		}
		return baseBps;
	}

	public synchronized List<PositionView> positionsOf(UUID owner) {
		List<PositionView> views = new ArrayList<>();
		for (LeveragePosition pos : positions) {
			if (pos.owner().equals(owner) && pos.isOpen()) {
				long price = currentPrice(pos.symbol());
				views.add(new PositionView(pos.id(), pos.symbol(), pos.isLong(), pos.leverage(),
						pos.marginMg(), pos.entryPriceMg(), price, pos.pnlMg(price), pos.equityMg(price),
						pos.maintenanceMarginMg(), pos.notionalMg(), pos.liquidationPriceMg()));
			}
		}
		return views;
	}

	public synchronized boolean hasOpenLong(UUID owner, String symbol) {
		String normalized = symbol.toUpperCase();
		return positions.stream()
				.anyMatch(p -> p.isOpen() && p.owner().equals(owner)
						&& p.symbol().equals(normalized) && p.isLong());
	}

	public synchronized boolean hasOpenShort(UUID owner, String symbol) {
		String normalized = symbol.toUpperCase();
		return positions.stream()
				.anyMatch(p -> p.isOpen() && p.owner().equals(owner)
						&& p.symbol().equals(normalized) && !p.isLong());
	}

	private LeveragePosition findOpen(UUID owner, int positionId) {
		return positions.stream()
				.filter(p -> p.id() == positionId && p.owner().equals(owner) && p.isOpen())
				.findFirst().orElse(null);
	}

	private long currentPrice(String symbol) {
		return exchangeService.markPriceMg(symbol);
	}

	private static long feeFromBps(long amountMg, int bps) {
		if (amountMg <= 0 || bps <= 0) {
			return 0;
		}
		return Math.max(0, (amountMg * bps) / 10_000L);
	}

	private static String marginCallKey(LeveragePosition pos) {
		return pos.id() + ":" + pos.owner();
	}

	private void notifyOwner(UUID owner, String message) {
		if (server == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			player.sendSystemMessage(Component.literal(message));
		}
	}

	private void logLeverage(UUID owner, String symbol, long amountMg, String source, String description) {
		if (economyEventService == null || amountMg <= 0) {
			return;
		}
		economyEventService.recordPersonal(owner, EconomyEventCategory.LEVERAGE, EconomyEventDirection.OUT,
				amountMg, null, symbol, 0, source, description + ": " + GoldStandard.formatMilligrams(amountMg));
	}

	private void logLeverageClose(UUID owner, String symbol, long payoutMg, long pnlMg) {
		if (economyEventService == null) {
			return;
		}
		if (payoutMg > 0) {
			economyEventService.recordPersonal(owner, EconomyEventCategory.LEVERAGE, EconomyEventDirection.IN,
					payoutMg, null, symbol, 0, "CLOSE",
					symbol + " pozisyon kapanisi — iade: " + GoldStandard.formatMilligrams(payoutMg));
		}
		if (pnlMg != 0) {
			economyEventService.recordPersonal(owner, EconomyEventCategory.LEVERAGE,
					pnlMg > 0 ? EconomyEventDirection.IN : EconomyEventDirection.OUT, Math.abs(pnlMg),
					null, symbol, 0, "PNL",
					symbol + " K/Z: " + (pnlMg >= 0 ? "+" : "") + GoldStandard.formatMilligrams(pnlMg));
		}
	}

	public record PositionView(int id, String symbol, boolean isLong, int leverage, long marginMg,
			long entryPriceMg, long currentPriceMg, long pnlMg, long equityMg,
			long maintenanceMarginMg, long notionalMg, long liquidationPriceMg) {
	}
}
