package com.mceconomy.company;

import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.McEconomyMod;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Sirket gelirlerini sahip borcu varken once cuzdana yonlendirir. */
public final class CompanyTreasuryHelper {
	private CompanyTreasuryHelper() {
	}

	public static void creditCompanyOrOwnerDebt(CurrencyService currencyService, Company company, long amount,
			TransactionType type) {
		if (company == null || amount <= 0 || currencyService == null) {
			return;
		}
		UUID owner = company.ownerUuid();
		long wallet = currencyService.getBalance(owner);
		if (wallet < 0) {
			long debt = -wallet;
			long toDebt = Math.min(amount, debt);
			if (toDebt > 0) {
				currencyService.deposit(owner, toDebt, type);
				amount -= toDebt;
				var server = McEconomyMod.getEconomyManager() != null
						? McEconomyMod.getEconomyManager().server() : null;
				if (server != null) {
					var ownerPlayer = server.getPlayerList().getPlayer(owner);
					if (ownerPlayer != null) {
						ownerPlayer.sendSystemMessage(Component.literal(
								"§e[Sirket] §f" + company.name() + " gelirinden §c"
										+ GoldStandard.formatMilligrams(toDebt)
										+ " §fborcunuza aktarildi."));
					}
				}
			}
		}
		if (amount > 0) {
			company.deposit(amount);
		}
	}
}
