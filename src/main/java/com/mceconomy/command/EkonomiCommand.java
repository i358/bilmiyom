package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.government.EconomyMinisterService;
import com.mceconomy.util.Permissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class EkonomiCommand {
	private EkonomiCommand() {
	}

	public static void registerBakan(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("ekonomi")
				.then(literal("bakan")
						.then(literal("ol").executes(ctx -> {
							if (!Permissions.isServerOp(ctx.getSource())) {
								return 0;
							}
							return appointSelf(ctx.getSource());
						}))
						.then(literal("basvur").then(argument("sebep", StringArgumentType.greedyString())
								.executes(ctx -> apply(ctx.getSource(),
										StringArgumentType.getString(ctx, "sebep")))))
						.then(literal("onay").then(argument("oyuncu", StringArgumentType.string())
								.executes(ctx -> approve(ctx.getSource(),
										StringArgumentType.getString(ctx, "oyuncu")))))
						.then(literal("red").then(argument("oyuncu", StringArgumentType.string())
								.executes(ctx -> reject(ctx.getSource(),
										StringArgumentType.getString(ctx, "oyuncu")))))
						.then(literal("ver").then(argument("oyuncu", StringArgumentType.string())
								.executes(ctx -> grant(ctx.getSource(),
										StringArgumentType.getString(ctx, "oyuncu"), true))))
						.then(literal("al").then(argument("oyuncu", StringArgumentType.string())
								.executes(ctx -> grant(ctx.getSource(),
										StringArgumentType.getString(ctx, "oyuncu"), false))))));
	}

	private static int appointSelf(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			McEconomyMod.getEconomyManager().economyMinisterService().appoint(player, true);
			source.sendSuccess(() -> Component.literal("§6[Devlet] §aEkonomi Bakani atandiniz."), true);
			return 1;
		} catch (Exception e) {
			source.sendFailure(Component.literal("§c" + e.getMessage()));
			return 0;
		}
	}

	private static int apply(CommandSourceStack source, String reason) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			String msg = McEconomyMod.getEconomyManager().economyMinisterService().apply(player, reason);
			source.sendSuccess(() -> Component.literal("§7" + msg), false);
			return 1;
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cBasvuru alinamadi."));
			return 0;
		}
	}

	private static int approve(CommandSourceStack source, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		EconomyMinisterService svc = McEconomyMod.getEconomyManager().economyMinisterService();
		if (!svc.isMinister(player.getUUID()) && !Permissions.isServerOp(source)) {
			source.sendFailure(Component.literal("§cYetkisiz."));
			return 0;
		}
		try {
			String msg = svc.approveApplication(name, player.getUUID());
			source.sendSuccess(() -> Component.literal("§a" + msg), false);
			return msg.contains("Onay") ? 1 : 0;
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cHata."));
			return 0;
		}
	}

	private static int reject(CommandSourceStack source, String name) {
		try {
			String msg = McEconomyMod.getEconomyManager().economyMinisterService().rejectApplication(name);
			source.sendSuccess(() -> Component.literal("§7" + msg), false);
			return 1;
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cHata."));
			return 0;
		}
	}

	private static int grant(CommandSourceStack source, String name, boolean value) {
		if (!Permissions.isServerOp(source)) {
			return 0;
		}
		ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
		if (target == null) {
			source.sendFailure(Component.literal("§cOyuncu cevrimici degil."));
			return 0;
		}
		try {
			McEconomyMod.getEconomyManager().economyMinisterService().appoint(target, value);
			source.sendSuccess(() -> Component.literal(
					"§6" + name + " bakanlik: " + (value ? "verildi" : "alindi")), true);
			return 1;
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cHata."));
			return 0;
		}
	}
}
