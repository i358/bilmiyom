package com.mceconomy.exchange;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.LeverageRepository;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Kaldiracli (marjinli) borsa islemleri: long/short pozisyon ac/kapat ve otomatik likidasyon. */
public final class LeverageService {
	public static final int MAX_LEVERAGE = 10;

	private final LeverageRepository repository;
	private final CurrencyService currencyService;
	private final ExchangeService exchangeService;
	private MinecraftServer server;

	private final List<LeveragePosition> positions = new ArrayList<>();

	public LeverageService(LeverageRepository repository, CurrencyService currencyService,
			ExchangeService exchangeService) {
		this.repository = repository;
		this.currencyService = currencyService;
		this.exchangeService = exchangeService;
	}

	public void bindServer(MinecraftServer server) {
		this.server = server;
	}

	public void load() throws SQLException {
		positions.clear();
		positions.addAll(repository.loadOpen());
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
		long entry = tokenOpt.get().priceMg();
		if (entry <= 0) {
			return "Coin fiyati gecersiz.";
		}
		if (!currencyService.withdraw(owner, marginMg, TransactionType.EXCHANGE_TOKEN)) {
			return "Yetersiz bakiye (teminat icin).";
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
		} catch (SQLException e) {
			currencyService.deposit(owner, marginMg, TransactionType.EXCHANGE_TOKEN);
			McEconomyMod.LOGGER.error("Kaldirac pozisyonu kaydedilemedi", e);
			return "Pozisyon acilamadi.";
		}
		return "ACILDI: " + (isLong ? "LONG" : "SHORT") + " " + symbol.toUpperCase()
				+ " " + leverage + "x, teminat " + GoldStandard.formatMilligrams(marginMg);
	}

	public synchronized String closePosition(UUID owner, int positionId) {
		LeveragePosition pos = positions.stream()
				.filter(p -> p.id() == positionId && p.owner().equals(owner) && p.isOpen())
				.findFirst().orElse(null);
		if (pos == null) {
			return "Pozisyon bulunamadi.";
		}
		long price = currentPrice(pos.symbol());
		long equity = pos.equityMg(price);
		closeInternal(pos, equity);
		long pnl = pos.pnlMg(price);
		return "KAPANDI: " + pos.symbol() + " — iade " + GoldStandard.formatMilligrams(equity)
				+ " (K/Z: " + (pnl >= 0 ? "+" : "") + GoldStandard.formatMilligrams(pnl) + ")";
	}

	private void closeInternal(LeveragePosition pos, long equity) {
		pos.close();
		if (equity > 0) {
			currencyService.deposit(pos.owner(), equity, TransactionType.EXCHANGE_TOKEN);
		}
		try {
			repository.markClosed(pos.id());
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Pozisyon kapatma kaydi basarisiz", e);
		}
	}

	public synchronized void liquidationTick() {
		List<LeveragePosition> toClose = new ArrayList<>();
		for (LeveragePosition pos : positions) {
			if (!pos.isOpen()) {
				continue;
			}
			long price = currentPrice(pos.symbol());
			if (price > 0 && pos.shouldLiquidate(price)) {
				toClose.add(pos);
			}
		}
		for (LeveragePosition pos : toClose) {
			closeInternal(pos, 0);
			notifyOwner(pos.owner(), "§4[Likidasyon] §c" + pos.symbol() + " "
					+ pos.leverage() + "x pozisyonunuz zarar margini astigi icin kapatildi!");
		}
		positions.removeIf(p -> !p.isOpen());
	}

	public synchronized List<PositionView> positionsOf(UUID owner) {
		List<PositionView> views = new ArrayList<>();
		for (LeveragePosition pos : positions) {
			if (pos.owner().equals(owner) && pos.isOpen()) {
				long price = currentPrice(pos.symbol());
				views.add(new PositionView(pos.id(), pos.symbol(), pos.isLong(), pos.leverage(),
						pos.marginMg(), pos.entryPriceMg(), price, pos.pnlMg(price), pos.equityMg(price)));
			}
		}
		return views;
	}

	private long currentPrice(String symbol) {
		return exchangeService.findToken(symbol).map(ExchangeToken::priceMg).orElse(0L);
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

	public record PositionView(int id, String symbol, boolean isLong, int leverage, long marginMg,
			long entryPriceMg, long currentPriceMg, long pnlMg, long equityMg) {
	}
}
