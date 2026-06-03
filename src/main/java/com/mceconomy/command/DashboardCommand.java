package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.persistence.repo.PlayerRepository;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.util.Messages;
import com.mceconomy.util.Permissions;
import com.mceconomy.web.DashboardPasswordService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class DashboardCommand {
	private DashboardCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("panel")
				.then(literal("sifre").then(argument("sifre", StringArgumentType.greedyString())
						.executes(ctx -> setPassword(ctx.getSource(), StringArgumentType.getString(ctx, "sifre")))))
				.executes(ctx -> info(ctx.getSource())));

		dispatcher.register(literal("dashboard")
				.then(literal("sifre").then(argument("sifre", StringArgumentType.greedyString())
						.executes(ctx -> setPassword(ctx.getSource(), StringArgumentType.getString(ctx, "sifre")))))
				.executes(ctx -> info(ctx.getSource())));
	}

	private static int info(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		source.sendSuccess(() -> Component.literal("§6=== Ekonomi Dashboard ==="), false);
		source.sendSuccess(() -> Component.literal("Adres: http://" + EconomyConfig.webBindAddress()
				+ ":" + EconomyConfig.webPort() + "/  |  OP paneli: /admin"), false);
		source.sendSuccess(() -> Messages.tr("command.mceconomy.dashboard.password_hint"), false);
		var profile = McEconomyMod.getEconomyManager().profiles().get(player.getUUID());
		if (profile != null && DashboardPasswordService.hasPassword(profile)) {
			source.sendSuccess(() -> Messages.tr("command.mceconomy.dashboard.password_set"), false);
		}
		return 1;
	}

	private static int setPassword(CommandSourceStack source, String password) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		if (password.length() < 4) {
			source.sendFailure(Messages.tr("command.mceconomy.dashboard.password_short"));
			return 0;
		}
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(player.getUUID());
		if (profile == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		try {
			DashboardPasswordService.setPassword(profile, password);
			McEconomyMod.getEconomyManager().playerRepository().save(profile);
			source.sendSuccess(() -> Messages.tr("command.mceconomy.dashboard.password_saved"), true);
			return 1;
		} catch (IllegalArgumentException e) {
			source.sendFailure(Messages.tr("command.mceconomy.dashboard.password_short"));
			return 0;
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
	}
}
