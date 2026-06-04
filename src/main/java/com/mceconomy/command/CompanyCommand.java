package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.company.Company;
import com.mceconomy.company.EmploymentRole;
import com.mceconomy.company.JobApplication;
import com.mceconomy.company.NpcEmployee;
import com.mceconomy.company.PlayerEmployment;
import com.mceconomy.company.PlayerJobApplication;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class CompanyCommand {
	private CompanyCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("sirket")
				.then(literal("kur").then(argument("isim", StringArgumentType.string())
						.executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "isim")))))
				.then(literal("kasa").then(argument("isim", StringArgumentType.string())
						.executes(ctx -> treasury(ctx.getSource(), StringArgumentType.getString(ctx, "isim")))))
				.then(literal("depo").then(argument("isim", StringArgumentType.string())
						.executes(ctx -> stashView(ctx.getSource(), StringArgumentType.getString(ctx, "isim")))))
				.then(literal("sandik")
						.then(literal("cik").executes(ctx -> vaultExit(ctx.getSource())))
						.then(argument("isim", StringArgumentType.string())
								.executes(ctx -> vaultTeleport(ctx.getSource(),
										StringArgumentType.getString(ctx, "isim")))))
				.then(literal("topla").then(argument("isim", StringArgumentType.string())
						.executes(ctx -> vaultTeleport(ctx.getSource(), StringArgumentType.getString(ctx, "isim")))))
				.then(literal("basvurular").executes(ctx -> listApplications(ctx.getSource(), null))
						.then(argument("sirket", StringArgumentType.string())
								.executes(ctx -> listApplications(ctx.getSource(),
										StringArgumentType.getString(ctx, "sirket")))))
				.then(literal("kabul").then(argument("id", LongArgumentType.longArg(1))
						.executes(ctx -> accept(ctx.getSource(), LongArgumentType.getLong(ctx, "id")))))
				.then(literal("red").then(argument("id", LongArgumentType.longArg(1))
						.executes(ctx -> reject(ctx.getSource(), LongArgumentType.getLong(ctx, "id")))))
				.then(literal("calisanlar").executes(ctx -> listEmployees(ctx.getSource(), null))
						.then(argument("sirket", StringArgumentType.string())
								.executes(ctx -> listEmployees(ctx.getSource(),
										StringArgumentType.getString(ctx, "sirket")))))
				.then(literal("kov").then(argument("id", LongArgumentType.longArg(1))
						.executes(ctx -> fire(ctx.getSource(), LongArgumentType.getLong(ctx, "id")))))
				.then(literal("maas").executes(ctx -> payrollInfo(ctx.getSource())))
				.then(literal("borsacikar").then(argument("isim", StringArgumentType.string())
						.executes(ctx -> delist(ctx.getSource(), StringArgumentType.getString(ctx, "isim"))))));

		dispatcher.register(literal("hisse")
				.then(literal("al").then(argument("sirket", StringArgumentType.string())
						.then(argument("adet", IntegerArgumentType.integer(1))
								.executes(ctx -> buy(ctx.getSource(),
										StringArgumentType.getString(ctx, "sirket"),
										IntegerArgumentType.getInteger(ctx, "adet"))))))
				.then(literal("sat").then(argument("sirket", StringArgumentType.string())
						.then(argument("adet", IntegerArgumentType.integer(1))
								.executes(ctx -> sell(ctx.getSource(),
										StringArgumentType.getString(ctx, "sirket"),
										IntegerArgumentType.getInteger(ctx, "adet")))))));
	}

	private static int create(CommandSourceStack source, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var manager = McEconomyMod.getEconomyManager();
		long bank = manager.bankService().getBankBalanceMg(player.getUUID());
		long wealth = manager.workforceService().totalWealth(player.getUUID(), bank);
		if (wealth < EconomyConfig.minCompanyWealthMg()) {
			source.sendFailure(Component.literal("Sirket kurmak icin en az "
					+ GoldStandard.formatMilligrams(EconomyConfig.minCompanyWealthMg()) + " varlik gerekli."));
			return 0;
		}
		long fee = EconomyConfig.companyCreationFeeMg();
		if (fee > 0 && !manager.currencyService().withdraw(player.getUUID(), fee, TransactionType.COMPANY)) {
			source.sendFailure(Messages.tr("command.mceconomy.pay.insufficient"));
			return 0;
		}
		try {
			if (manager.companyManager().createCompany(name, player.getUUID())) {
				manager.companyManager().find(name).ifPresent(company -> {
					manager.onCompanyCreated(company);
					source.sendSuccess(() -> Component.literal(
							"§7Sirket binasi insa edildi (spawn yakininda). Sandik: §e/sirket sandik"), false);
				});
				source.sendSuccess(() -> Messages.tr("command.mceconomy.company.created", name), false);
				source.sendSuccess(() -> Component.literal(
						"§7NPC'ler ve oyuncular is basvurusu yapabilir. §e/sirket basvurular | §e/is basvur"), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		}
		return 0;
	}

	private static int listApplications(CommandSourceStack source, String companyName) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var manager = McEconomyMod.getEconomyManager();
		var npcApps = manager.workforceService().pendingForOwner(player.getUUID(), companyName);
		var playerApps = manager.playerEmploymentService().pendingForOwner(player.getUUID(), companyName);
		if (npcApps.isEmpty() && playerApps.isEmpty()) {
			source.sendSuccess(() -> Component.literal("§7Bekleyen basvuru yok."), false);
			return 1;
		}
		source.sendSuccess(() -> Component.literal("§e=== Is Basvurulari ==="), false);
		for (JobApplication app : npcApps) {
			source.sendSuccess(() -> Component.literal(
					"§6#" + app.id() + " §7[NPC] §f" + app.npcName() + " §7(" + app.roleId() + ") maas: "
							+ GoldStandard.formatMilligrams(app.requestedSalaryMg())
							+ "\n  §7" + app.message()), false);
		}
		for (PlayerJobApplication app : playerApps) {
			String roleLabel = EmploymentRole.displayName(app.roleId());
			String payLabel = EmploymentRole.isCeo(app.roleId())
					? "kazanc payi %50/%50"
					: "maas: " + GoldStandard.formatMilligrams(app.requestedSalaryMg());
			source.sendSuccess(() -> Component.literal(
					"§6#" + app.id() + " §7[OYUNCU] §f" + app.playerName() + " §7(" + roleLabel + ") " + payLabel
							+ "\n  §7" + app.message()), false);
		}
		source.sendSuccess(() -> Component.literal("§7Kabul: /sirket kabul <id> | Red: /sirket red <id>"), false);
		return 1;
	}

	private static int accept(CommandSourceStack source, long id) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var manager = McEconomyMod.getEconomyManager();
		if (manager.workforceService().acceptApplication(player.getUUID(), id, source.getServer())
				|| manager.playerEmploymentService().acceptApplication(player.getUUID(), id, source.getServer())) {
			source.sendSuccess(() -> Component.literal("Basvuru #" + id + " kabul edildi."), false);
			return 1;
		}
		source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		return 0;
	}

	private static int reject(CommandSourceStack source, long id) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var manager = McEconomyMod.getEconomyManager();
		if (manager.workforceService().rejectApplication(player.getUUID(), id, source.getServer())
				|| manager.playerEmploymentService().rejectApplication(player.getUUID(), id, source.getServer())) {
			source.sendSuccess(() -> Component.literal("Basvuru #" + id + " reddedildi."), false);
			return 1;
		}
		source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		return 0;
	}

	private static int listEmployees(CommandSourceStack source, String companyName) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var manager = McEconomyMod.getEconomyManager();
		var npcList = manager.workforceService().employeesForOwner(player.getUUID(), companyName);
		var playerList = manager.playerEmploymentService().employeesForOwner(player.getUUID(), companyName);
		if (npcList.isEmpty() && playerList.isEmpty()) {
			source.sendSuccess(() -> Component.literal("§7Calisan yok."), false);
			return 1;
		}
		source.sendSuccess(() -> Component.literal("§e=== Calisanlar ==="), false);
		for (NpcEmployee emp : npcList) {
			source.sendSuccess(() -> Component.literal(
					"§6#" + emp.id() + " §7[NPC] §f" + emp.npcName() + " §7(" + emp.roleId() + ")"
							+ " maas: " + GoldStandard.formatMilligrams(emp.salaryMg())
							+ " uretim: " + GoldStandard.formatMilligrams(emp.totalProducedMg())), false);
		}
		for (PlayerEmployment emp : playerList) {
			String roleLabel = EmploymentRole.displayName(emp.roleId());
			String payLabel = EmploymentRole.isCeo(emp.roleId())
					? "CEO ortak (kazanc yarisi)"
					: "maas: " + GoldStandard.formatMilligrams(emp.salaryMg()) + " (gunluk)";
			source.sendSuccess(() -> Component.literal(
					"§6#" + emp.id() + " §7[OYUNCU] §f" + emp.playerName() + " §7(" + roleLabel + ") " + payLabel), false);
		}
		return 1;
	}

	private static int fire(CommandSourceStack source, long id) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var manager = McEconomyMod.getEconomyManager();
		if (manager.workforceService().fireEmployee(player.getUUID(), id)
				|| manager.playerEmploymentService().fireEmployee(player.getUUID(), id, source.getServer())) {
			source.sendSuccess(() -> Component.literal("Calisan #" + id + " isten cikarildi."), false);
			return 1;
		}
		source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		return 0;
	}

	private static int payrollInfo(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		source.sendSuccess(() -> Component.literal(
				"§7Ham maden eritilip pazara satilir; %%2 sandiga. Yemekler pisirilip sandiga. "
						+ "§e/sirket depo <isim> §7| §e/sirket sandik <isim> §7| §e/sirket sandik cik"), false);
		return 1;
	}

	private static int delist(CommandSourceStack source, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			if (McEconomyMod.getEconomyManager().companyManager().delistCompany(name, player.getUUID())) {
				source.sendSuccess(() -> Component.literal("§e" + name + " borsadan cikarildi."), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		source.sendFailure(Component.literal("§cIslem basarisiz (sahiplik veya listede degil)."));
		return 0;
	}

	private static int treasury(CommandSourceStack source, String name) {
		var company = McEconomyMod.getEconomyManager().companyManager().find(name);
		if (company.isEmpty()) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		double index = McEconomyMod.getEconomyManager().marketService().economyIndex().calculate();
		int npcWorkers = McEconomyMod.getEconomyManager().workforceService()
				.employeesForOwner(company.get().ownerUuid(), name).size();
		int playerWorkers = McEconomyMod.getEconomyManager().playerEmploymentService()
				.employeesForOwner(company.get().ownerUuid(), name).size();
		source.sendSuccess(() -> Component.literal(
				name + " kasasi: " + GoldStandard.formatMilligrams(company.get().treasury())
						+ " | Hisse: " + company.get().sharePrice(index)
						+ " | Calisan: " + (npcWorkers + playerWorkers)), false);
		return 1;
	}

	private static int stashView(CommandSourceStack source, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var company = McEconomyMod.getEconomyManager().companyManager().find(name);
		if (company.isEmpty() || !company.get().ownerUuid().equals(player.getUUID())) {
			source.sendFailure(Component.literal("§cSirket bulunamadi veya sahibi degilsiniz."));
			return 0;
		}
		var vault = McEconomyMod.getEconomyManager().companyVaultService();
		var entries = vault.listContents(company.get().id());
		if (entries.isEmpty()) {
			source.sendSuccess(() -> Component.literal(
					"§7" + name + " sandigi bos. Madenlerin %2'si ve pisirilmis yemekler buraya gelir; geri kalan pazara satilir."), false);
			return 1;
		}
		source.sendSuccess(() -> Component.literal("§e=== " + name + " Gizli Sandik (Celik Oda) ==="), false);
		for (var entry : entries) {
			source.sendSuccess(() -> Component.literal("§f" + entry.quantity() + "x " + entry.displayName()), false);
		}
		source.sendSuccess(() -> Component.literal("§7Gitmek icin: §e/sirket sandik " + name), false);
		return 1;
	}

	private static int vaultTeleport(CommandSourceStack source, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		if (McEconomyMod.getEconomyManager().companyVaultService().teleportToVault(player, name)) {
			source.sendSuccess(() -> Component.literal(
					"§6[Sirket Sandigi] §fGizli celik odaya isinlandiniz. Sandiktan esya alin. §e/sirket sandik cik"), false);
			return 1;
		}
		source.sendFailure(Component.literal("§cSandiga gidilemedi (sahiplik veya sirket adi)."));
		return 0;
	}

	private static int vaultExit(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		if (McEconomyMod.getEconomyManager().companyVaultService().teleportBack(player)) {
			source.sendSuccess(() -> Component.literal("§6[Sirket Sandigi] §fOnceki konumunuza dondunuz."), false);
			return 1;
		}
		source.sendFailure(Component.literal("§cDonus konumu yok. Once /sirket sandik <isim> ile gidin."));
		return 0;
	}

	private static int buy(CommandSourceStack source, String companyName, int amount) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		double index = McEconomyMod.getEconomyManager().marketService().economyIndex().calculate();
		try {
			if (McEconomyMod.getEconomyManager().companyManager().buyShares(player.getUUID(), companyName, amount, index)) {
				source.sendSuccess(() -> Messages.tr("command.mceconomy.company.shares_bought", amount), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		}
		source.sendFailure(Messages.tr("command.mceconomy.pay.insufficient"));
		return 0;
	}

	private static int sell(CommandSourceStack source, String companyName, int amount) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		double index = McEconomyMod.getEconomyManager().marketService().economyIndex().calculate();
		try {
			if (McEconomyMod.getEconomyManager().companyManager().sellShares(player.getUUID(), companyName, amount, index)) {
				source.sendSuccess(() -> Messages.tr("command.mceconomy.company.shares_sold", amount), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		}
		return 0;
	}
}
