package com.mceconomy.economy;

import com.mceconomy.McEconomyMod;
import com.mceconomy.bank.BankService;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.regulation.MasakService;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.UUID;

public final class CurrencyService {
	private final Map<UUID, PlayerEconomyProfile> profiles;
	private final TransactionLedger ledger;
	private MasakService masakService;
	private BankService bankService;

	public CurrencyService(Map<UUID, PlayerEconomyProfile> profiles, TransactionLedger ledger) {
		this.profiles = profiles;
		this.ledger = ledger;
	}

	public void bindMasak(MasakService masakService) {
		this.masakService = masakService;
	}

	public void bindBank(BankService bankService) {
		this.bankService = bankService;
	}

	public long getBalance(UUID uuid) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		return profile != null ? profile.wallet().balance() : 0;
	}

	public long getDirtyBalance(UUID uuid) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		return profile != null ? profile.dirtyWallet().balance() : 0;
	}

	public boolean deposit(UUID uuid, long amount, TransactionType type) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile == null || amount <= 0) {
			return false;
		}
		long before = profile.wallet().balance();
		if (!profile.wallet().deposit(amount)) {
			return false;
		}
		ledger.record(null, uuid, amount, type, null);
		if (bankService != null) {
			bankService.sweepCheckingTowardDebt(uuid);
		}
		long after = profile.wallet().balance();
		if (before < 0) {
			notifyDebtRepayment(uuid, before, after);
		}
		return true;
	}

	private void notifyDebtRepayment(UUID uuid, long before, long after) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || manager.server() == null) {
			return;
		}
		var player = manager.server().getPlayerList().getPlayer(uuid);
		if (player == null) {
			return;
		}
		long paid = Math.min(-before, after - before);
		if (after >= 0) {
			player.sendSystemMessage(Component.literal(
					"§a[Borc] Borcunuz kapandi. Bakiye: " + GoldStandard.formatMilligrams(after)));
		} else if (paid > 0) {
			player.sendSystemMessage(Component.literal(
					"§e[Borc] Gelirin " + GoldStandard.formatMilligrams(paid)
							+ " borca yazildi. Kalan: " + GoldStandard.formatMilligrams(-after)));
		}
	}

	public boolean depositDirty(UUID uuid, long amount, TransactionType type) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile == null || amount <= 0) {
			return false;
		}
		if (!profile.dirtyWallet().deposit(amount)) {
			return false;
		}
		ledger.record(null, uuid, amount, type, "dirty");
		return true;
	}

	public boolean withdraw(UUID uuid, long amount, TransactionType type) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile == null || amount <= 0) {
			return false;
		}
		if (!profile.wallet().withdraw(amount)) {
			return false;
		}
		ledger.record(uuid, null, amount, type, null);
		return true;
	}

	public boolean withdrawDirty(UUID uuid, long amount, TransactionType type) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile == null || amount <= 0) {
			return false;
		}
		if (!profile.dirtyWallet().withdraw(amount)) {
			return false;
		}
		ledger.record(uuid, null, amount, type, "dirty");
		return true;
	}

	public boolean transfer(UUID from, UUID to, long amount) {
		if (from.equals(to) || amount <= 0) {
			return false;
		}
		PlayerEconomyProfile fromProfile = profiles.get(from);
		PlayerEconomyProfile toProfile = profiles.get(to);
		if (fromProfile == null || toProfile == null) {
			return false;
		}
		if (!fromProfile.canUseLegalEconomy() || !toProfile.canUseLegalEconomy()) {
			return false;
		}
		if (!fromProfile.wallet().withdraw(amount)) {
			return false;
		}
		if (!toProfile.wallet().deposit(amount)) {
			fromProfile.wallet().deposit(amount);
			return false;
		}
		ledger.record(from, to, amount, TransactionType.TRANSFER, null);
		if (masakService != null) {
			masakService.onTransfer(from, amount);
		}
		return true;
	}

	public boolean adminSetWallet(UUID uuid, long balanceMg) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile == null) {
			return false;
		}
		profile.wallet().setBalance(balanceMg);
		ledger.record(null, uuid, Math.abs(balanceMg), TransactionType.ADMIN_OP, "wallet-set");
		return true;
	}

	public boolean adminAdjustWallet(UUID uuid, long deltaMg) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile == null) {
			return false;
		}
		profile.wallet().setBalance(profile.wallet().balance() + deltaMg);
		ledger.record(null, uuid, Math.abs(deltaMg), TransactionType.ADMIN_OP, "wallet-adjust");
		return true;
	}

	public boolean adminSetDirty(UUID uuid, long balanceMg) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile == null) {
			return false;
		}
		profile.dirtyWallet().setBalance(balanceMg);
		ledger.record(null, uuid, balanceMg, TransactionType.ADMIN_OP, "dirty-set");
		return true;
	}
}
