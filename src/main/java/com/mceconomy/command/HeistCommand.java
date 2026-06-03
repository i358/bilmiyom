package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.util.Permissions;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public final class HeistCommand {
	private HeistCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("soygun")
				.executes(ctx -> help(ctx.getSource()))
				.then(literal("baslat").executes(ctx -> start(ctx.getSource())))
				.then(literal("durum").executes(ctx -> status(ctx.getSource())))
				.then(literal("bitir").executes(ctx -> stop(ctx.getSource()))));
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("§e/soygun baslat §7— Altin rezerv RP soygunu"), false);
		source.sendSuccess(() -> Component.literal("§5Gece: §7muhafizlar uyur, ates yok; §edepo sandiklari §7acik (fiziksel soygun)"), false);
		source.sendSuccess(() -> Component.literal("§7Gunduz: kasa bolgesine girince muhafizlar ates edebilir"), false);
		return 1;
	}

	private static int start(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		String name = player != null ? player.getName().getString() : "Konsol";
		if (McEconomyMod.getEconomyManager().heistService().start(name, player != null ? player.getUUID() : null)) {
			if (player != null) {
				var sec = McEconomyMod.getEconomyManager().bankSecurityService();
				if (sec != null && sec.guardsSleeping()) {
					player.sendSystemMessage(Component.literal(
							"§5[Gece] §dMuhafizlar uyuyor — depo sandiklarindan da calabilirsiniz; sabah ust arama riski var!"));
				}
			}
			return 1;
		}
		source.sendFailure(Component.literal("§cZaten aktif bir soygun protokolu var."));
		return 0;
	}

	private static int status(CommandSourceStack source) {
		boolean active = McEconomyMod.getEconomyManager().heistService().isActive();
		source.sendSuccess(() -> Component.literal(active
				? "§c[Soygun] §fSu an aktif bir soygun protokolu devam ediyor!"
				: "§a[Soygun] §fSu an aktif soygun yok."), false);
		return 1;
	}

	private static int stop(CommandSourceStack source) {
		if (!Permissions.isServerOp(source)) {
			source.sendFailure(Component.literal("§cBu islem sadece OP yetkililer icindir."));
			return 0;
		}
		McEconomyMod.getEconomyManager().heistService().forceStop();
		source.sendSuccess(() -> Component.literal("§e[Soygun] §fProtokol sonlandirildi."), false);
		return 1;
	}
}
