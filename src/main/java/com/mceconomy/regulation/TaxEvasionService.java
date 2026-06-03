package com.mceconomy.regulation;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.player.PlayerEconomyProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Vergi kacakciligi suphesi — MASAK denetimi. */
public final class TaxEvasionService {
	private final MasakService masakService;
	private final Map<UUID, PlayerEconomyProfile> profiles;

	public TaxEvasionService(MasakService masakService, Map<UUID, PlayerEconomyProfile> profiles) {
		this.masakService = masakService;
		this.profiles = profiles;
	}

	public void tick(MinecraftServer server) {
		if (!EconomyConfig.masakEnabled()) {
			return;
		}
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ThreadLocalRandom.current().nextInt(100) > 8) {
				continue;
			}
			auditPlayer(player.getUUID());
		}
	}

	public void auditPlayer(UUID uuid) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile == null || profile.accountFrozen()) {
			return;
		}
		long wallet = profile.wallet().balance();
		long bank = McEconomyMod.getEconomyManager().bankService().getBankBalanceMg(uuid);
		long dirty = McEconomyMod.getEconomyManager().currencyService().getDirtyBalance(uuid);
		long total = wallet + bank + dirty;
		if (total <= 0) {
			return;
		}
		double dirtyRatio = (double) dirty / total;
		if (dirtyRatio >= EconomyConfig.taxEvasionDirtyRatioThreshold()) {
			masakService.onTaxEvasionSuspect(uuid, dirty, dirtyRatio);
			ServerPlayer online = McEconomyMod.getEconomyManager().server().getPlayerList().getPlayer(uuid);
			if (online != null) {
				online.sendSystemMessage(Component.literal(
						"§c[MASAK] §fVergi kacakciligi suphesi — hesabiniz denetimde."));
			}
		}
	}
}
