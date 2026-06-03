package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.trade.PlayerTrade;
import com.mceconomy.trade.TradeDispute;
import com.mceconomy.util.Permissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class TradeCommand {
	private TradeCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("takas")
				.then(literal("davet").then(argument("oyuncu", StringArgumentType.string())
						.executes(ctx -> invite(ctx.getSource(), StringArgumentType.getString(ctx, "oyuncu")))))
				.then(literal("kabul").executes(ctx -> accept(ctx.getSource())))
				.then(literal("para").then(argument("miktar", LongArgumentType.longArg(1))
						.executes(ctx -> gold(ctx.getSource(), LongArgumentType.getLong(ctx, "miktar")))))
				.then(literal("el").executes(ctx -> hand(ctx.getSource())))
				.then(literal("hazir").executes(ctx -> ready(ctx.getSource())))
				.then(literal("iptal").executes(ctx -> cancel(ctx.getSource())))
				.then(literal("gecmis").executes(ctx -> history(ctx.getSource())))
				.then(literal("sikayet").then(argument("id", LongArgumentType.longArg(1))
						.then(argument("sebep", StringArgumentType.greedyString())
								.executes(ctx -> dispute(ctx.getSource(), LongArgumentType.getLong(ctx, "id"),
										StringArgumentType.getString(ctx, "sebep"))))))
				.then(literal("incele").requires(Permissions::isServerOp)
						.then(argument("id", LongArgumentType.longArg(1))
								.executes(ctx -> inspect(ctx.getSource(), LongArgumentType.getLong(ctx, "id")))))
				.then(literal("sikayetler").requires(Permissions::isServerOp)
						.executes(ctx -> listDisputes(ctx.getSource())))
				.then(literal("karar").requires(Permissions::isServerOp)
						.then(argument("id", LongArgumentType.longArg(1))
								.then(literal("iade").executes(ctx -> resolve(ctx.getSource(),
										LongArgumentType.getLong(ctx, "id"), true, "")))
								.then(literal("reddet").then(argument("not", StringArgumentType.greedyString())
										.executes(ctx -> resolve(ctx.getSource(),
												LongArgumentType.getLong(ctx, "id"), false,
												StringArgumentType.getString(ctx, "not"))))))));
	}

	private static int invite(CommandSourceStack source, String partner) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().playerTradeService().invite(player, partner) ? 1 : 0;
	}

	private static int accept(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().playerTradeService().accept(player) ? 1 : 0;
	}

	private static int gold(CommandSourceStack source, long amount) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().playerTradeService().addGold(player, amount) ? 1 : 0;
	}

	private static int hand(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().playerTradeService().addHandItem(player) ? 1 : 0;
	}

	private static int ready(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().playerTradeService().setReady(player) ? 1 : 0;
	}

	private static int cancel(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().playerTradeService().cancel(player) ? 1 : 0;
	}

	private static int history(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var list = McEconomyMod.getEconomyManager().playerTradeService().history(player.getUUID());
		if (list.isEmpty()) {
			source.sendSuccess(() -> Component.literal("§7Takas gecmisi yok."), false);
			return 1;
		}
		source.sendSuccess(() -> Component.literal("§e=== Takas Gecmisi ==="), false);
		for (PlayerTrade trade : list) {
			source.sendSuccess(() -> Component.literal(
					"§6#" + trade.id() + " §f" + trade.initiatorName() + " ↔ " + trade.partnerName()
							+ " §7[" + trade.status().name() + "] altin: "
							+ GoldStandard.formatMilligrams(trade.initiatorGoldMg()) + " / "
							+ GoldStandard.formatMilligrams(trade.partnerGoldMg())), false);
		}
		return 1;
	}

	private static int dispute(CommandSourceStack source, long tradeId, String reason) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().playerTradeService().dispute(player, tradeId, reason) ? 1 : 0;
	}

	private static int inspect(CommandSourceStack source, long tradeId) {
		var opt = McEconomyMod.getEconomyManager().playerTradeService().findTrade(tradeId);
		if (opt.isEmpty()) {
			source.sendFailure(Component.literal("§cTakas bulunamadi."));
			return 0;
		}
		PlayerTrade trade = opt.get();
		source.sendSuccess(() -> Component.literal(
				"§eTakas #" + trade.id() + " §7[" + trade.status().name() + "]\n"
						+ trade.initiatorName() + ": " + GoldStandard.formatMilligrams(trade.initiatorGoldMg())
						+ " + " + trade.initiatorItemsJson() + "\n"
						+ trade.partnerName() + ": " + GoldStandard.formatMilligrams(trade.partnerGoldMg())
						+ " + " + trade.partnerItemsJson()), false);
		return 1;
	}

	private static int listDisputes(CommandSourceStack source) {
		var list = McEconomyMod.getEconomyManager().playerTradeService().openDisputes();
		if (list.isEmpty()) {
			source.sendSuccess(() -> Component.literal("§7Acik takas sikayeti yok."), false);
			return 1;
		}
		for (TradeDispute d : list) {
			source.sendSuccess(() -> Component.literal(
					"§6#" + d.id() + " §ftakas #" + d.tradeId() + " — " + d.reporterName()
							+ " → " + d.targetName() + ": §7" + d.reason()), false);
		}
		source.sendSuccess(() -> Component.literal("§7Karar: /takas karar <id> iade|reddet <not>"), false);
		return 1;
	}

	private static int resolve(CommandSourceStack source, long disputeId, boolean refund, String note) {
		String admin = source.getPlayer() != null ? source.getPlayer().getName().getString() : "Konsol";
		if (McEconomyMod.getEconomyManager().playerTradeService().resolveDispute(admin, disputeId, refund, note)) {
			source.sendSuccess(() -> Component.literal("§aSikayet #" + disputeId + " cozuldu."), true);
			return 1;
		}
		source.sendFailure(Component.literal("§cIslem basarisiz."));
		return 0;
	}
}
