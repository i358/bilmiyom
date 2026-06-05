package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.vehicle.PlayerVehicle;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class VehicleCommand {
	private VehicleCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("araba")
				.then(literal("al").then(argument("model", StringArgumentType.word())
						.executes(ctx -> buy(ctx.getSource(), StringArgumentType.getString(ctx, "model")))))
				.then(literal("cikar").then(argument("id", LongArgumentType.longArg(1))
						.executes(ctx -> spawn(ctx.getSource(), LongArgumentType.getLong(ctx, "id")))))
				.then(literal("liste").executes(ctx -> list(ctx.getSource()))));
	}

	private static int buy(CommandSourceStack source, String model) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var svc = McEconomyMod.getEconomyManager().vehicleService();
		if (svc == null) {
			source.sendFailure(Component.literal("§cArac servisi hazir degil."));
			return 0;
		}
		try {
			String r = svc.purchase(player, model);
			if (r.startsWith("OK")) {
				source.sendSuccess(() -> Component.literal("§a[Arac] §fGaraja eklendi."), false);
				return 1;
			}
			source.sendFailure(Component.literal("§c" + r));
		} catch (Exception e) {
			source.sendFailure(Component.literal("§c" + e.getMessage()));
		}
		return 0;
	}

	private static int spawn(CommandSourceStack source, long id) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var svc = McEconomyMod.getEconomyManager().vehicleService();
		if (svc == null) {
			source.sendFailure(Component.literal("§cArac servisi hazir degil."));
			return 0;
		}
		try {
			String r = svc.spawn(player, id);
			if (r.startsWith("OK")) {
				source.sendSuccess(() -> Component.literal("§a[Arac] §fSuruse hazir (client mod onerilir)."), false);
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
		for (PlayerVehicle v : McEconomyMod.getEconomyManager().vehicleService().forOwner(player.getUUID())) {
			source.sendSuccess(() -> Component.literal(
					"§7#" + v.id() + " §f" + v.model() + " §8yakit=" + String.format("%.0f", v.fuel())
							+ (v.spawned() ? " §a(yolda)" : " §7(garaj)")), false);
		}
		return 1;
	}
}
