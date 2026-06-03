package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.EconomyManager;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.util.Messages;
import com.mceconomy.web.DashboardPasswordService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class DashboardCommand {
	private DashboardCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("panel")
				.then(literal("sifre").then(argument("sifre", StringArgumentType.greedyString())
						.executes(ctx -> setPassword(ctx.getSource(), StringArgumentType.getString(ctx, "sifre")))))
				.then(literal("şifre").then(argument("sifre", StringArgumentType.greedyString())
						.executes(ctx -> setPassword(ctx.getSource(), StringArgumentType.getString(ctx, "sifre")))))
				.executes(ctx -> info(ctx.getSource())));

		dispatcher.register(literal("dashboard")
				.then(literal("sifre").then(argument("sifre", StringArgumentType.greedyString())
						.executes(ctx -> setPassword(ctx.getSource(), StringArgumentType.getString(ctx, "sifre")))))
				.then(literal("şifre").then(argument("sifre", StringArgumentType.greedyString())
						.executes(ctx -> setPassword(ctx.getSource(), StringArgumentType.getString(ctx, "sifre")))))
				.executes(ctx -> info(ctx.getSource())));
	}

	private static int info(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		EconomyManager manager = McEconomyMod.getEconomyManager();
		if (manager == null || !manager.isLoaded()) {
			source.sendFailure(Component.literal("§cEkonomi sistemi henüz hazır değil."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("§6=== Ekonomi Dashboard ==="), false);
		source.sendSuccess(() -> Component.literal("Adres: http://" + EconomyConfig.webBindAddress()
				+ ":" + EconomyConfig.webPort() + "/  |  OP paneli: /admin"), false);
		source.sendSuccess(() -> Messages.tr("command.mceconomy.dashboard.password_hint"), false);
		PlayerEconomyProfile profile = resolveProfile(manager, player);
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
		EconomyManager manager = McEconomyMod.getEconomyManager();
		if (manager == null || !manager.isLoaded()) {
			source.sendFailure(Component.literal("§cEkonomi sistemi henüz hazır değil."));
			return 0;
		}
		if (password == null) {
			source.sendFailure(Messages.tr("command.mceconomy.dashboard.password_short"));
			return 0;
		}
		password = password.trim();
		if (password.length() < 4) {
			source.sendFailure(Messages.tr("command.mceconomy.dashboard.password_short"));
			return 0;
		}
		PlayerEconomyProfile profile = resolveProfile(manager, player);
		if (profile == null) {
			source.sendFailure(Component.literal("§cProfiliniz oluşturulamadı. Sunucudan çıkıp tekrar girin."));
			return 0;
		}
		try {
			DashboardPasswordService.setPassword(profile, password);
			manager.playerRepository().save(profile);
			source.sendSuccess(() -> Messages.tr("command.mceconomy.dashboard.password_saved"), true);
			return 1;
		} catch (IllegalArgumentException e) {
			source.sendFailure(Messages.tr("command.mceconomy.dashboard.password_short"));
			return 0;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Dashboard sifre kaydi basarisiz: {}", player.getUUID(), e);
			source.sendFailure(Messages.tr("command.mceconomy.dashboard.password_save_failed"));
			return 0;
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("Dashboard sifre hatasi: {}", player.getUUID(), e);
			source.sendFailure(Messages.tr("command.mceconomy.dashboard.password_save_failed"));
			return 0;
		}
	}

	static PlayerEconomyProfile resolveProfile(EconomyManager manager, ServerPlayer player) {
		UUID uuid = player.getUUID();
		PlayerEconomyProfile profile = manager.profiles().get(uuid);
		if (profile != null) {
			profile.setName(player.getName().getString());
			return profile;
		}
		manager.ensurePlayer(uuid, player.getName().getString());
		profile = manager.profiles().get(uuid);
		if (profile != null) {
			return profile;
		}
		try {
			profile = manager.playerRepository().find(uuid).orElse(null);
			if (profile != null) {
				profile.setName(player.getName().getString());
				manager.profiles().put(uuid, profile);
			}
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Profil yuklenemedi: {}", uuid, e);
		}
		return profile;
	}
}
