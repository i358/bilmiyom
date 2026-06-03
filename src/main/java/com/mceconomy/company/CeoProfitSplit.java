package com.mceconomy.company;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** CEO ortaginin urettigi nakit kazancin sirket / oyuncu arasinda bolunmesi. */
public final class CeoProfitSplit {
	private CeoProfitSplit() {
	}

	public static void distribute(CurrencyService currencyService, Company company, UUID workerUuid,
			long grossMg, TransactionType type) {
		if (company == null || currencyService == null || workerUuid == null || grossMg <= 0) {
			return;
		}
		long companyPart = (long) (grossMg * EmploymentRole.companyProfitShare());
		long playerPart = grossMg - companyPart;
		if (companyPart > 0) {
			CompanyTreasuryHelper.creditCompanyOrOwnerDebt(currencyService, company, companyPart, type);
		}
		if (playerPart > 0) {
			currencyService.deposit(workerUuid, playerPart, type);
		}
		MinecraftServer server = McEconomyMod.getEconomyManager() != null
				? McEconomyMod.getEconomyManager().server() : null;
		if (server == null) {
			return;
		}
		ServerPlayer worker = server.getPlayerList().getPlayer(workerUuid);
		if (worker != null) {
			worker.sendSystemMessage(Component.literal(
					"§a[CEO Ortak] §fKazanc payiniz: §e" + GoldStandard.formatMilligrams(playerPart)
							+ " §7| Sirket: " + GoldStandard.formatMilligrams(companyPart)));
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(company.ownerUuid());
		if (owner != null) {
			owner.sendSystemMessage(Component.literal(
					"§e[Sirket/CEO] §f" + (worker != null ? worker.getName().getString() : "CEO")
							+ " uretimden kasaya +" + GoldStandard.formatMilligrams(companyPart)));
		}
	}
}
