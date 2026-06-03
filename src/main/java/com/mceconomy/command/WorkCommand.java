package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.company.Company;
import com.mceconomy.company.EmploymentRole;
import com.mceconomy.company.PlayerEmployment;
import com.mceconomy.company.PlayerEmploymentService;
import com.mceconomy.company.PlayerJobApplication;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.job.JobType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class WorkCommand {
	private static final SuggestionProvider<CommandSourceStack> ROLE_SUGGESTIONS = (ctx, builder) -> {
		for (JobType type : JobType.values()) {
			builder.suggest(type.id());
		}
		return builder.buildFuture();
	};

	private WorkCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("is")
				.then(literal("basvur")
						.then(argument("sirket", StringArgumentType.string())
								.then(argument("rol", StringArgumentType.string()).suggests(ROLE_SUGGESTIONS)
										.then(argument("maas", LongArgumentType.longArg(1))
												.executes(ctx -> apply(ctx.getSource(),
														StringArgumentType.getString(ctx, "sirket"),
														StringArgumentType.getString(ctx, "rol"),
														LongArgumentType.getLong(ctx, "maas"),
														null))
												.then(argument("mesaj", StringArgumentType.greedyString())
														.executes(ctx -> apply(ctx.getSource(),
																StringArgumentType.getString(ctx, "sirket"),
																StringArgumentType.getString(ctx, "rol"),
																LongArgumentType.getLong(ctx, "maas"),
																StringArgumentType.getString(ctx, "mesaj"))))))))
				.then(literal("durum").executes(ctx -> status(ctx.getSource())))
				.then(literal("basvuru-iptal").executes(ctx -> cancelApplication(ctx.getSource())))
				.then(literal("basvur-ceo")
						.then(argument("sirket", StringArgumentType.string())
								.executes(ctx -> applyCeo(ctx.getSource(),
										StringArgumentType.getString(ctx, "sirket"), null))
								.then(argument("mesaj", StringArgumentType.greedyString())
										.executes(ctx -> applyCeo(ctx.getSource(),
												StringArgumentType.getString(ctx, "sirket"),
												StringArgumentType.getString(ctx, "mesaj"))))))
				.then(literal("ayril").executes(ctx -> quit(ctx.getSource())))
				.then(literal("sirketler").executes(ctx -> listCompanies(ctx.getSource()))));
	}

	private static int apply(CommandSourceStack source, String companyName, String roleId, long salaryMg,
			String message) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		PlayerEmploymentService service = McEconomyMod.getEconomyManager().playerEmploymentService();
		if (service.apply(player, source.getServer(), companyName, roleId, salaryMg, message)) {
			return 1;
		}
		return 0;
	}

	private static int status(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		PlayerEmploymentService service = McEconomyMod.getEconomyManager().playerEmploymentService();
		var employment = service.employmentForPlayer(player.getUUID());
		if (employment.isPresent()) {
			PlayerEmployment emp = employment.get();
			Company company = McEconomyMod.getEconomyManager().companyManager().allCompanies().stream()
					.filter(c -> c.id() == emp.companyId()).findFirst().orElse(null);
			String companyName = company != null ? company.name() : "?";
			boolean ceo = EmploymentRole.isCeo(emp.roleId());
			JobType role = JobType.fromString(emp.roleId());
			if (ceo) {
				source.sendSuccess(() -> Component.literal(
						"§e=== Is Durumu ===\n§fSirket: §a" + companyName
								+ "\n§fRol: §6CEO Ortak"
								+ "\n§fKazanc payi: §a%" + (int) (EmploymentRole.playerProfitShare() * 100)
								+ " size, %" + (int) (EmploymentRole.companyProfitShare() * 100)
								+ " sirket\n§7Kisisel mesleginizle §e/gorev al"), false);
			} else {
				long nextPayMs = emp.lastPaidAt() + EconomyConfig.playerDailySalaryIntervalMs();
				long waitMin = Math.max(0, (nextPayMs - System.currentTimeMillis()) / 60_000);
				source.sendSuccess(() -> Component.literal(
						"§e=== Is Durumu ===\n§fSirket: §a" + companyName
								+ "\n§fRol: §7" + (role != null ? role.displayName() : emp.roleId())
								+ "\n§fMaas: §a" + GoldStandard.formatMilligrams(emp.salaryMg())
								+ " §7(gunluk)\n§fSonraki odeme: §7~" + waitMin + " dk"), false);
			}
			return 1;
		}
		var pending = service.pendingApplicationForPlayer(player.getUUID());
		if (pending.isPresent()) {
			PlayerJobApplication app = pending.get();
			Company company = McEconomyMod.getEconomyManager().companyManager().allCompanies().stream()
					.filter(c -> c.id() == app.companyId()).findFirst().orElse(null);
			source.sendSuccess(() -> Component.literal(
					"§eBekleyen basvuru: §f" + (company != null ? company.name() : "?")
							+ " §7(" + app.roleId() + ", "
							+ GoldStandard.formatMilligrams(app.requestedSalaryMg()) + ")"
							+ "\n§e/is basvuru-iptal §7— basvuruyu geri cek"), false);
			return 1;
		}
		source.sendSuccess(() -> Component.literal(
				"§7Bir sirkette calismiyorsunuz.\n§e/is sirketler §7— sirket listesi\n"
						+ "§e/is basvur <sirket> <rol> <maas> §7— maasli basvuru\n"
						+ "§e/is basvur-ceo <sirket> §7— CEO ortak (kazanc yarisi)"), false);
		return 1;
	}

	private static int applyCeo(CommandSourceStack source, String companyName, String message) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		PlayerEmploymentService service = McEconomyMod.getEconomyManager().playerEmploymentService();
		if (service.apply(player, source.getServer(), companyName, EmploymentRole.CEO_ID, 0, message)) {
			return 1;
		}
		return 0;
	}

	private static int cancelApplication(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		PlayerEmploymentService service = McEconomyMod.getEconomyManager().playerEmploymentService();
		if (service.cancelPendingApplication(player.getUUID(), source.getServer())) {
			source.sendSuccess(() -> Component.literal("§aIs basvurunuz geri cekildi."), false);
			return 1;
		}
		source.sendFailure(Component.literal("§cBekleyen is basvurunuz yok."));
		return 0;
	}

	private static int quit(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		PlayerEmploymentService service = McEconomyMod.getEconomyManager().playerEmploymentService();
		if (service.quit(player.getUUID(), source.getServer())) {
			source.sendSuccess(() -> Component.literal("§eSirketten ayrildiniz."), false);
			return 1;
		}
		source.sendFailure(Component.literal("§cBir sirkette calismiyorsunuz."));
		return 0;
	}

	private static int listCompanies(CommandSourceStack source) {
		var companies = McEconomyMod.getEconomyManager().companyManager().allCompanies();
		if (companies.isEmpty()) {
			source.sendSuccess(() -> Component.literal("§7Henuz sirket yok."), false);
			return 1;
		}
		source.sendSuccess(() -> Component.literal("§e=== Sirketler ==="), false);
		for (Company company : companies) {
			source.sendSuccess(() -> Component.literal("§f" + company.name()
					+ " §7— /is basvur " + company.name() + " <rol> <maas> | /is basvur-ceo "
					+ company.name()), false);
		}
		long minSalary = EconomyConfig.baseNpcSalaryMg();
		long maxSalary = minSalary + EconomyConfig.maxNpcSalaryBonusMg();
		source.sendSuccess(() -> Component.literal(
				"§7Maas araligi: " + GoldStandard.formatMilligrams(minSalary) + " — "
						+ GoldStandard.formatMilligrams(maxSalary)), false);
		return 1;
	}
}
