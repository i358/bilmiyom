package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.market.Commodity;
import com.mceconomy.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class MarketCommand {
	private static final SuggestionProvider<CommandSourceStack> COMMODITY_SUGGESTIONS = (ctx, builder) -> {
		for (Commodity commodity : Commodity.values()) {
			builder.suggest(commodity.id());
		}
		return builder.buildFuture();
	};

	private MarketCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("market")
				.then(literal("fiyat").executes(ctx -> listPrices(ctx.getSource())))
				.then(literal("al").then(argument("emtia", StringArgumentType.string())
						.suggests(COMMODITY_SUGGESTIONS)
						.then(argument("adet", IntegerArgumentType.integer(1))
								.executes(ctx -> buy(ctx.getSource(),
										StringArgumentType.getString(ctx, "emtia"),
										IntegerArgumentType.getInteger(ctx, "adet"))))))
				.then(literal("sat").then(argument("emtia", StringArgumentType.string())
						.suggests(COMMODITY_SUGGESTIONS)
						.then(argument("adet", IntegerArgumentType.integer(1))
								.executes(ctx -> sell(ctx.getSource(),
										StringArgumentType.getString(ctx, "emtia"),
										IntegerArgumentType.getInteger(ctx, "adet")))))));
	}

	private static int listPrices(CommandSourceStack source) {
		source.sendSuccess(() -> Messages.tr("command.mceconomy.market.price_header"), false);
		var market = McEconomyMod.getEconomyManager().marketService();
		double index = market.economyIndex().calculate();
		for (Commodity commodity : Commodity.values()) {
			if (!commodity.sellable() && !commodity.buyable()) {
				continue;
			}
			long price = market.priceEngine().getUnitPrice(commodity);
			source.sendSuccess(() -> Messages.tr("command.mceconomy.market.price_line",
					commodity.displayName(), price, String.format("%.1f", index)), false);
		}
		return 1;
	}

	private static int buy(CommandSourceStack source, String commodityId, int quantity) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		Commodity commodity = Commodity.fromId(commodityId);
		if (commodity == null) {
			source.sendFailure(Messages.tr("command.mceconomy.market.invalid_commodity", commodityId));
			return 0;
		}
		if (!commodity.buyable()) {
			source.sendFailure(Messages.tr("command.mceconomy.market.not_buyable", commodity.displayName()));
			return 0;
		}
		long unitPrice = McEconomyMod.getEconomyManager().marketService().priceEngine().getUnitPrice(commodity);
		if (McEconomyMod.getEconomyManager().marketService().buy(player, commodity, quantity)) {
			long total = unitPrice * quantity;
			source.sendSuccess(() -> Messages.tr("command.mceconomy.market.buy", quantity, commodity.displayName(), total), false);
			return 1;
		}
		source.sendFailure(Messages.tr("command.mceconomy.market.insufficient_coins"));
		return 0;
	}

	private static int sell(CommandSourceStack source, String commodityId, int quantity) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		Commodity commodity = Commodity.fromId(commodityId);
		if (commodity == null) {
			source.sendFailure(Messages.tr("command.mceconomy.market.invalid_commodity", commodityId));
			return 0;
		}
		if (!commodity.sellable()) {
			source.sendFailure(Messages.tr("command.mceconomy.market.not_sellable", commodity.displayName()));
			return 0;
		}
		long unitPrice = McEconomyMod.getEconomyManager().marketService().priceEngine().getUnitPrice(commodity);
		if (McEconomyMod.getEconomyManager().marketService().sell(player, commodity, quantity)) {
			long total = unitPrice * quantity;
			source.sendSuccess(() -> Messages.tr("command.mceconomy.market.sell", quantity, commodity.displayName(), total), false);
			return 1;
		}
		source.sendFailure(Messages.tr("command.mceconomy.market.insufficient_items"));
		return 0;
	}
}
