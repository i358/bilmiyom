package com.mceconomy.economy;

import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.regulation.MasakService;

import java.util.Map;
import java.util.UUID;

public final class CurrencyService {
	private final Map<UUID, PlayerEconomyProfile> profiles;
	private final TransactionLedger ledger;
	private MasakService masakService;

	public CurrencyService(Map<UUID, PlayerEconomyProfile> profiles, TransactionLedger ledger) {
		this.profiles = profiles;
		this.ledger = ledger;
	}

	public void bindMasak(MasakService masakService) {
		this.masakService = masakService;
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
		if (!profile.wallet().deposit(amount)) {
			return false;
		}
		ledger.record(null, uuid, amount, type, null);
		return true;
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
}
