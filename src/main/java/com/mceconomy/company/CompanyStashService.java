package com.mceconomy.company;

import com.mceconomy.McEconomyMod;
import com.mceconomy.market.Commodity;
import com.mceconomy.persistence.repo.CompanyStashRepository;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CompanyStashService {
	public record StashEntry(String commodityId, String displayName, int quantity) {
	}

	public record CollectResult(int itemStacks, int totalItems, List<String> lines) {
	}

	private final Map<Integer, Map<String, Integer>> stashByCompany = new HashMap<>();
	private final CompanyStashRepository repository;
	private final CompanyManager companyManager;

	public CompanyStashService(CompanyStashRepository repository, CompanyManager companyManager) {
		this.repository = repository;
		this.companyManager = companyManager;
	}

	public void load() throws SQLException {
		stashByCompany.clear();
		stashByCompany.putAll(repository.loadAll());
	}

	public void deposit(int companyId, Commodity commodity, int quantity) throws SQLException {
		if (quantity <= 0 || commodity == null) {
			return;
		}
		Map<String, Integer> stash = stashByCompany.computeIfAbsent(companyId, k -> new HashMap<>());
		int next = stash.getOrDefault(commodity.id(), 0) + quantity;
		stash.put(commodity.id(), next);
		repository.save(companyId, commodity.id(), next);
	}

	public List<StashEntry> entriesForCompany(int companyId) {
		Map<String, Integer> stash = stashByCompany.get(companyId);
		if (stash == null || stash.isEmpty()) {
			return List.of();
		}
		List<StashEntry> entries = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : stash.entrySet()) {
			Commodity commodity = Commodity.fromId(entry.getKey());
			if (commodity == null || entry.getValue() <= 0) {
				continue;
			}
			entries.add(new StashEntry(commodity.id(), commodity.displayName(), entry.getValue()));
		}
		return entries;
	}

	public List<StashEntry> entriesForOwner(UUID ownerUuid, String companyName) {
		return companyManager.find(companyName)
				.filter(c -> c.ownerUuid().equals(ownerUuid))
				.map(c -> entriesForCompany(c.id()))
				.orElse(List.of());
	}

	public CollectResult collectAll(UUID ownerUuid, String companyName, ServerPlayer player) throws SQLException {
		Company company = companyManager.find(companyName).orElse(null);
		if (company == null || !company.ownerUuid().equals(ownerUuid)) {
			return new CollectResult(0, 0, List.of());
		}
		Map<String, Integer> stash = stashByCompany.get(company.id());
		if (stash == null || stash.isEmpty()) {
			return new CollectResult(0, 0, List.of());
		}

		List<String> lines = new ArrayList<>();
		int totalItems = 0;
		int stacks = 0;
		Map<String, Integer> remaining = new HashMap<>();

		for (Map.Entry<String, Integer> entry : new HashMap<>(stash).entrySet()) {
			Commodity commodity = Commodity.fromId(entry.getKey());
			if (commodity == null || entry.getValue() <= 0) {
				continue;
			}
			int given = giveItems(player, commodity, entry.getValue());
			if (given > 0) {
				totalItems += given;
				stacks++;
				lines.add(given + "x " + commodity.displayName());
			}
			int left = entry.getValue() - given;
			if (left > 0) {
				remaining.put(entry.getKey(), left);
			}
			repository.save(company.id(), entry.getKey(), Math.max(0, left));
		}

		if (remaining.isEmpty()) {
			stashByCompany.remove(company.id());
		} else {
			stashByCompany.put(company.id(), remaining);
		}
		return new CollectResult(stacks, totalItems, lines);
	}

	private int giveItems(ServerPlayer player, Commodity commodity, int quantity) {
		int remaining = quantity;
		int delivered = 0;
		while (remaining > 0) {
			int batch = Math.min(remaining, commodity.item().getDefaultMaxStackSize());
			ItemStack stack = new ItemStack(commodity.item(), batch);
			if (!player.getInventory().add(stack)) {
				break;
			}
			int added = batch - stack.getCount();
			delivered += added;
			remaining -= added;
		}
		return delivered;
	}

	public void saveAll() {
		for (Map.Entry<Integer, Map<String, Integer>> companyEntry : stashByCompany.entrySet()) {
			for (Map.Entry<String, Integer> itemEntry : companyEntry.getValue().entrySet()) {
				try {
					repository.save(companyEntry.getKey(), itemEntry.getKey(), itemEntry.getValue());
				} catch (SQLException e) {
					McEconomyMod.LOGGER.error("Sirket deposu kaydedilemedi", e);
				}
			}
		}
	}
}
