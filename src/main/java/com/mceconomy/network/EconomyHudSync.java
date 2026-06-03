package com.mceconomy.network;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.job.JobType;
import com.mceconomy.player.PlayerEconomyProfile;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class EconomyHudSync {
	private EconomyHudSync() {
	}

	public static void syncPlayer(ServerPlayer player) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || !manager.isLoaded()) {
			return;
		}
		PlayerEconomyProfile profile = manager.profiles().get(player.getUUID());
		long wallet = manager.currencyService().getBalance(player.getUUID());
		long bank = manager.bankService().getBankBalanceMg(player.getUUID());
		long dirty = manager.currencyService().getDirtyBalance(player.getUUID());
		boolean frozen = profile != null && profile.accountFrozen();
		boolean blacklisted = profile != null && profile.blacklisted();
		String job = profile != null && profile.jobType() != null ? profile.jobType().displayName() : "-";
		if (ServerPlayNetworking.canSend(player, EconomyHudPayload.TYPE)) {
			ServerPlayNetworking.send(player, new EconomyHudPayload(wallet, bank, dirty, frozen, blacklisted, job));
		}
	}

	public static String formatCompact(long walletMg, long bankMg) {
		return GoldStandard.formatMilligrams(walletMg) + " | B:" + GoldStandard.formatMilligrams(bankMg);
	}
}
