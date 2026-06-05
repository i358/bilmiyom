package com.mceconomy.command;

import com.mceconomy.panel.EconomyPanelService;
import com.mceconomy.util.Permissions;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public final class EconomyPanelCommand {
	private EconomyPanelCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("ekonomi")
				.executes(ctx -> open(ctx.getSource(), "overview", false))
				.then(literal("admin").executes(ctx -> openAdmin(ctx.getSource()))));
	}

	private static int open(CommandSourceStack source, String tab, boolean adminMode) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		EconomyPanelService.openPanel(player, tab, adminMode);
		source.sendSuccess(() -> Component.literal(
				adminMode ? "§a[Ekonomi] OP panel acildi." : "§a[Ekonomi] Panel acildi."), false);
		return 1;
	}

	private static int openAdmin(CommandSourceStack source) {
		if (!Permissions.isServerOp(source)) {
			source.sendFailure(Component.literal("§c[Ekonomi] OP gerekli."));
			return 0;
		}
		return open(source, "dashboard", true);
	}
}
