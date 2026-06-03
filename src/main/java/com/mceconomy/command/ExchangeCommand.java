package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.gui.ExchangeGuiManager;
import com.mceconomy.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ExchangeCommand {
	private ExchangeCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("borsa")
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayer();
					if (player == null) {
						return 0;
					}
					ExchangeGuiManager.openHub(player);
					return 1;
				})
				.then(literal("coin").then(argument("sembol", StringArgumentType.word())
						.then(argument("isim", StringArgumentType.string())
								.then(argument("adet", IntegerArgumentType.integer(1, 1_000_000))
										.then(argument("fiyatMg", LongArgumentType.longArg(1))
												.executes(ctx -> createCoin(ctx.getSource(),
														StringArgumentType.getString(ctx, "sembol"),
														StringArgumentType.getString(ctx, "isim"),
														IntegerArgumentType.getInteger(ctx, "adet"),
														LongArgumentType.getLong(ctx, "fiyatMg"))))))))
				.then(literal("listele").then(argument("sirket", StringArgumentType.string())
						.then(argument("ticker", StringArgumentType.word())
								.executes(ctx -> listCompany(ctx.getSource(),
										StringArgumentType.getString(ctx, "sirket"),
										StringArgumentType.getString(ctx, "ticker"))))))
				.then(literal("kaldirac")
						.then(literal("long").then(argument("sembol", StringArgumentType.word())
								.then(argument("kaldirac", IntegerArgumentType.integer(2, 10))
										.then(argument("gram", LongArgumentType.longArg(1))
												.executes(ctx -> openLeverage(ctx.getSource(), true,
														StringArgumentType.getString(ctx, "sembol"),
														IntegerArgumentType.getInteger(ctx, "kaldirac"),
														LongArgumentType.getLong(ctx, "gram")))))))
						.then(literal("short").then(argument("sembol", StringArgumentType.word())
								.then(argument("kaldirac", IntegerArgumentType.integer(2, 10))
										.then(argument("gram", LongArgumentType.longArg(1))
												.executes(ctx -> openLeverage(ctx.getSource(), false,
														StringArgumentType.getString(ctx, "sembol"),
														IntegerArgumentType.getInteger(ctx, "kaldirac"),
														LongArgumentType.getLong(ctx, "gram"))))))))
				.then(literal("pozisyonlar").executes(ctx -> listPositions(ctx.getSource())))
				.then(literal("kapat").then(argument("id", IntegerArgumentType.integer(1))
						.executes(ctx -> closePosition(ctx.getSource(),
								IntegerArgumentType.getInteger(ctx, "id"))))));
	}

	private static int openLeverage(CommandSourceStack source, boolean isLong, String symbol, int leverage, long grams) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		String result = McEconomyMod.getEconomyManager().leverageService()
				.openPosition(player.getUUID(), symbol, isLong, leverage, grams * 1000L);
		if (result.startsWith("ACILDI")) {
			source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("§a[Kaldirac] §f" + result), false);
			return 1;
		}
		source.sendFailure(net.minecraft.network.chat.Component.literal("§c" + result));
		return 0;
	}

	private static int closePosition(CommandSourceStack source, int id) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		String result = McEconomyMod.getEconomyManager().leverageService().closePosition(player.getUUID(), id);
		if (result.startsWith("KAPANDI")) {
			source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("§a[Kaldirac] §f" + result), false);
			return 1;
		}
		source.sendFailure(net.minecraft.network.chat.Component.literal("§c" + result));
		return 0;
	}

	private static int listPositions(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var positions = McEconomyMod.getEconomyManager().leverageService().positionsOf(player.getUUID());
		if (positions.isEmpty()) {
			source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("§7Acik kaldiracli pozisyonunuz yok."), false);
			return 1;
		}
		source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("§6=== Kaldiracli Pozisyonlar ==="), false);
		for (var pos : positions) {
			String pnl = (pos.pnlMg() >= 0 ? "§a+" : "§c")
					+ com.mceconomy.economy.GoldStandard.formatMilligrams(pos.pnlMg());
			source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
					"§f#" + pos.id() + " " + pos.symbol() + " " + (pos.isLong() ? "LONG" : "SHORT") + " "
							+ pos.leverage() + "x — K/Z: " + pnl), false);
		}
		return 1;
	}

	private static int createCoin(CommandSourceStack source, String symbol, String name, int supply, long priceMg) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			if (McEconomyMod.getEconomyManager().exchangeService()
					.createToken(player.getUUID(), symbol, name, supply, priceMg)) {
				source.sendSuccess(() -> Messages.tr("command.mceconomy.exchange.coin_created", symbol, name), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		source.sendFailure(Messages.tr("command.mceconomy.exchange.coin_failed"));
		return 0;
	}

	private static int listCompany(CommandSourceStack source, String companyName, String ticker) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			if (McEconomyMod.getEconomyManager().exchangeService()
					.listCompany(player.getUUID(), companyName, ticker)) {
				source.sendSuccess(() -> Messages.tr("command.mceconomy.exchange.listed", companyName, ticker), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		source.sendFailure(Messages.tr("command.mceconomy.exchange.list_failed"));
		return 0;
	}
}
