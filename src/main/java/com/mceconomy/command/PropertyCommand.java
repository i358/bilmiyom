package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.property.PlayerProperty;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class PropertyCommand {
	private PropertyCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("ev")
				.then(literal("al").then(argument("tip", StringArgumentType.word())
						.executes(ctx -> buy(ctx.getSource(), StringArgumentType.getString(ctx, "tip")))))
				.then(literal("tp").then(argument("id", LongArgumentType.longArg(1))
						.executes(ctx -> tp(ctx.getSource(), LongArgumentType.getLong(ctx, "id")))))
				.then(literal("sat").then(argument("id", LongArgumentType.longArg(1))
						.executes(ctx -> sell(ctx.getSource(), LongArgumentType.getLong(ctx, "id")))))
				.then(literal("liste").executes(ctx -> list(ctx.getSource()))));
	}

	private static int buy(CommandSourceStack source, String tier) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var svc = McEconomyMod.getEconomyManager().propertyService();
		if (svc == null) {
			source.sendFailure(Component.literal("§cKonut servisi hazir degil."));
			return 0;
		}
		try {
			String r = svc.buy(player, tier);
			if (r.startsWith("OK")) {
				source.sendSuccess(() -> Component.literal("§a[Ev] §fSatin alindi, insaat kuyrugunda."), false);
				return 1;
			}
			source.sendFailure(Component.literal("§c" + r));
		} catch (Exception e) {
			source.sendFailure(Component.literal("§c" + e.getMessage()));
		}
		return 0;
	}

	private static int tp(CommandSourceStack source, long id) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var svc = McEconomyMod.getEconomyManager().propertyService();
		if (svc != null && svc.teleport(player, id)) {
			source.sendSuccess(() -> Component.literal("§a[Ev] §fIsinlandi."), false);
			return 1;
		}
		source.sendFailure(Component.literal("§cEv bulunamadi."));
		return 0;
	}

	private static int sell(CommandSourceStack source, long id) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var svc = McEconomyMod.getEconomyManager().propertyService();
		if (svc == null) {
			source.sendFailure(Component.literal("§cKonut servisi hazir degil."));
			return 0;
		}
		try {
			String r = svc.sell(player, id);
			if (r.startsWith("OK")) {
				source.sendSuccess(() -> Component.literal("§a[Ev] §fSatildi."), false);
				return 1;
			}
			source.sendFailure(Component.literal("§c" + r));
		} catch (Exception e) {
			source.sendFailure(Component.literal("§c" + e.getMessage()));
		}
		return 0;
	}

	private static int list(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var svc = McEconomyMod.getEconomyManager().propertyService();
		if (svc == null) {
			return 0;
		}
		for (PlayerProperty p : svc.forOwner(player.getUUID())) {
			source.sendSuccess(() -> Component.literal(
					"§7#" + p.id() + " §f" + p.tier() + " §8@ " + p.origin().getX() + "," + p.origin().getZ()), false);
		}
		return 1;
	}
}
