package com.mceconomy.util;

import com.mceconomy.McEconomyMod;
import com.mceconomy.player.PlayerEconomyProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class Permissions {
	private Permissions() {
	}

	public static boolean isServerOp(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player != null) {
			return source.getServer().getPlayerList().isOp(player.nameAndId());
		}
		return true;
	}

	public static boolean isCentralBankOfficial(UUID uuid) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || !manager.isLoaded()) {
			return false;
		}
		PlayerEconomyProfile profile = manager.profiles().get(uuid);
		return profile != null && profile.centralBankOfficial();
	}

	public static boolean isMbStaff(CommandSourceStack source) {
		if (isServerOp(source)) {
			return true;
		}
		ServerPlayer player = source.getPlayer();
		return player != null && isCentralBankOfficial(player.getUUID());
	}

	public static boolean isMbStaff(UUID uuid) {
		return isCentralBankOfficial(uuid);
	}
}
