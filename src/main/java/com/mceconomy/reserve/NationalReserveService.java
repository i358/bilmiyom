package com.mceconomy.reserve;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.facility.FacilityDepotService;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.market.Commodity;
import com.mceconomy.market.MarketPriceEngine;
import com.mceconomy.persistence.repo.NationalReserveRepository;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Merkez bankasi depo sandiklari dolunca esyalari ulusal rezerve tasir. */
public final class NationalReserveService {
	public record ReserveEntry(String itemId, String displayName, int quantity) {
	}

	private final NationalReserveRepository repository;
	private final Map<String, Integer> reserveByItem = new HashMap<>();

	public NationalReserveService(NationalReserveRepository repository) {
		this.repository = repository;
	}

	public void load() throws SQLException {
		reserveByItem.clear();
		reserveByItem.putAll(repository.loadReserve());
	}

	public void deposit(String itemId, int quantity) throws SQLException {
		if (quantity <= 0 || itemId == null) {
			return;
		}
		int next = reserveByItem.getOrDefault(itemId, 0) + quantity;
		reserveByItem.put(itemId, next);
		repository.saveReserveItem(itemId, next);
	}

	public Map<String, Integer> snapshot() {
		return Map.copyOf(reserveByItem);
	}

	public long estimateValueMg(MarketPriceEngine priceEngine) {
		long total = 0;
		for (Map.Entry<String, Integer> entry : reserveByItem.entrySet()) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(entry.getKey()));
			if (item == null) {
				continue;
			}
			if (item == Items.GOLD_INGOT) {
				total += GoldStandard.ingotsToMilligrams(entry.getValue());
				continue;
			}
			Commodity commodity = Commodity.fromItem(item);
			if (commodity != null && priceEngine != null) {
				total += priceEngine.getUnitPrice(commodity) * entry.getValue();
			}
		}
		return total;
	}

	public int consolidateDepot(ServerLevel level, FacilityDepotService depot, FacilityType type) {
		if (depot.isNearFull(level, type, EconomyConfig.depotReserveFreeStacks())) {
			return moveExcessToReserve(level, depot, type);
		}
		return 0;
	}

	private int moveExcessToReserve(ServerLevel level, FacilityDepotService depot, FacilityType type) {
		int maxKeep = FacilityDepotService.maxCapacity(type) - EconomyConfig.depotReserveFreeStacks() * 64;
		int total = depot.totalItemCount(level, type);
		if (total <= maxKeep) {
			return 0;
		}
		int toMove = total - maxKeep;
		int moved = 0;
		for (ItemStack stack : depot.snapshot(level, type)) {
			if (moved >= toMove || stack.isEmpty()) {
				break;
			}
			int take = Math.min(stack.getCount(), toMove - moved);
			int withdrawn = depot.withdrawItem(level, type, stack.getItem(), take);
			if (withdrawn <= 0) {
				continue;
			}
			String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			try {
				deposit(itemId, withdrawn);
				moved += withdrawn;
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Ulusal rezerv kaydi basarisiz: {}", itemId, e);
				depot.depositItem(level, type, stack.getItem(), withdrawn);
				break;
			}
		}
		if (moved > 0) {
			McEconomyMod.LOGGER.info("[Ulusal Rezerv] {} deposundan {} esya arsivlendi", type.displayName(), moved);
		}
		return moved;
	}

	public long estimateItemValueMg(Item item, int quantity, MarketPriceEngine priceEngine) {
		if (quantity <= 0 || item == null) {
			return 0;
		}
		if (item == Items.GOLD_INGOT) {
			return GoldStandard.ingotsToMilligrams(quantity);
		}
		if (item == Items.GOLD_BLOCK) {
			return GoldStandard.ingotsToMilligrams(quantity * GoldReserveService.INGOTS_PER_GOLD_BLOCK);
		}
		Commodity commodity = Commodity.fromItem(item);
		if (commodity != null && priceEngine != null) {
			return priceEngine.getUnitPrice(commodity) * quantity;
		}
		return quantity * 1000L;
	}
}
