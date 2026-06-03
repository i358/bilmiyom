package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.guild.Guild;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class GuildCommand {
	private GuildCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("lonca")
				.then(literal("kur").then(argument("isim", StringArgumentType.string())
						.executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "isim")))))
				.then(literal("katil").then(argument("isim", StringArgumentType.string())
						.executes(ctx -> join(ctx.getSource(), StringArgumentType.getString(ctx, "isim")))))
				.then(literal("ayril").executes(ctx -> leave(ctx.getSource())))
				.then(literal("durum").executes(ctx -> status(ctx.getSource())))
				.then(literal("kasa")
						.then(literal("yatir").then(argument("miktar", LongArgumentType.longArg(1))
								.executes(ctx -> deposit(ctx.getSource(), LongArgumentType.getLong(ctx, "miktar")))))
						.then(literal("cek").then(argument("miktar", LongArgumentType.longArg(1))
								.executes(ctx -> withdraw(ctx.getSource(), LongArgumentType.getLong(ctx, "miktar"))))))
				.then(literal("grev").then(argument("dakika", IntegerArgumentType.integer(1, 120))
						.executes(ctx -> strike(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "dakika")))))
				.then(literal("talep").then(argument("mesaj", StringArgumentType.greedyString())
						.executes(ctx -> bargain(ctx.getSource(), StringArgumentType.getString(ctx, "mesaj"))))));
	}

	private static int create(CommandSourceStack source, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().guildService().create(player, name) ? 1 : 0;
	}

	private static int join(CommandSourceStack source, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().guildService().join(player, name) ? 1 : 0;
	}

	private static int leave(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().guildService().leave(player) ? 1 : 0;
	}

	private static int deposit(CommandSourceStack source, long amount) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().guildService().deposit(player, amount) ? 1 : 0;
	}

	private static int withdraw(CommandSourceStack source, long amount) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().guildService().withdraw(player, amount) ? 1 : 0;
	}

	private static int strike(CommandSourceStack source, int minutes) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().guildService().startStrike(player, minutes) ? 1 : 0;
	}

	private static int bargain(CommandSourceStack source, String message) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		return McEconomyMod.getEconomyManager().guildService().setBargain(player, message) ? 1 : 0;
	}

	private static int status(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var guildOpt = McEconomyMod.getEconomyManager().guildService().guildForPlayer(player.getUUID());
		if (guildOpt.isEmpty()) {
			source.sendSuccess(() -> Component.literal("§7Bir loncada degilsiniz. §e/lonca kur|katil"), false);
			return 1;
		}
		Guild guild = guildOpt.get();
		source.sendSuccess(() -> Component.literal(
				"§e=== Lonca ===\n§f" + guild.name()
						+ "\n§7Kasa: " + GoldStandard.formatMilligrams(guild.treasuryMg())
						+ (guild.strikeActive() ? "\n§4GREV AKTIF" : "")), false);
		return 1;
	}
}
