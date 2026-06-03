package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.insurance.InsurancePolicy;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public final class InsuranceCommand {
	private InsuranceCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("sigorta")
				.executes(ctx -> help(ctx.getSource()))
				.then(literal("kisisel")
						.then(literal("al").executes(ctx -> personal(ctx.getSource(), true)))
						.then(literal("iptal").executes(ctx -> personal(ctx.getSource(), false))))
				.then(literal("sirket")
						.then(literal("al").then(literal("isim")
								.then(net.minecraft.commands.Commands.argument("sirket", StringArgumentType.greedyString())
										.executes(ctx -> company(ctx.getSource(),
												StringArgumentType.getString(ctx, "sirket"), true)))))
						.then(literal("iptal").then(literal("isim")
								.then(net.minecraft.commands.Commands.argument("sirket", StringArgumentType.greedyString())
										.executes(ctx -> company(ctx.getSource(),
												StringArgumentType.getString(ctx, "sirket"), false)))))));
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("§b=== Sigorta ==="), false);
		source.sendSuccess(() -> Component.literal("§e/sigorta kisisel al|iptal"), false);
		source.sendSuccess(() -> Component.literal("§e/sigorta sirket al <isim> §7| §e/sigorta sirket iptal <isim>"), false);
		source.sendSuccess(() -> Component.literal("§7Soygun bultenlerinde zararin bir kismi tazmin edilir (aylik prim)."), false);
		return 1;
	}

	private static int personal(CommandSourceStack source, boolean subscribe) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			var svc = McEconomyMod.getEconomyManager().insuranceService();
			if (subscribe) {
				if (svc.subscribePersonal(player.getUUID())) {
					source.sendSuccess(() -> Component.literal("§a[Sigorta] §fKisisel poliçe aktif."), false);
					return 1;
				}
				source.sendFailure(Component.literal("§cPrim odenemedi veya zaten aktif."));
			} else {
				if (svc.cancel(player.getUUID(), InsurancePolicy.PolicyType.PERSONAL, 0)) {
					source.sendSuccess(() -> Component.literal("§a[Sigorta] §fKisisel poliçe iptal."), false);
					return 1;
				}
				source.sendFailure(Component.literal("§cAktif poliçe yok."));
			}
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cIslem basarisiz."));
		}
		return 0;
	}

	private static int company(CommandSourceStack source, String name, boolean subscribe) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			var svc = McEconomyMod.getEconomyManager().insuranceService();
			var company = McEconomyMod.getEconomyManager().companyManager().find(name).orElse(null);
			if (company == null) {
				source.sendFailure(Component.literal("§cSirket bulunamadi."));
				return 0;
			}
			if (subscribe) {
				if (svc.subscribeCompany(player.getUUID(), name)) {
					source.sendSuccess(() -> Component.literal("§a[Sigorta] §f" + name + " sirket poliçesi aktif."), false);
					return 1;
				}
				source.sendFailure(Component.literal("§cSirket kasasinda prim yok veya zaten aktif."));
			} else {
				if (svc.cancel(player.getUUID(), InsurancePolicy.PolicyType.COMPANY, company.id())) {
					source.sendSuccess(() -> Component.literal("§a[Sigorta] §fSirket poliçesi iptal."), false);
					return 1;
				}
				source.sendFailure(Component.literal("§cAktif sirket poliçesi yok."));
			}
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cIslem basarisiz."));
		}
		return 0;
	}
}
