package com.mceconomy.property;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.PropertyRepository;
import com.mceconomy.tax.TaxService;
import com.mceconomy.world.PropertyPlacer;
import com.mceconomy.world.StructureBuildQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PropertyService {
	private final PropertyRepository repository;
	private final CurrencyService currencyService;
	private final TaxService taxService;
	private final Map<Long, PlayerProperty> properties = new HashMap<>();
	private final Map<UUID, List<Long>> byOwner = new HashMap<>();

	public PropertyService(PropertyRepository repository, CurrencyService currencyService, TaxService taxService) {
		this.repository = repository;
		this.currencyService = currencyService;
		this.taxService = taxService;
	}

	public void load() throws SQLException {
		properties.clear();
		byOwner.clear();
		for (PlayerProperty p : repository.loadAll()) {
			properties.put(p.id(), p);
			byOwner.computeIfAbsent(p.ownerUuid(), k -> new ArrayList<>()).add(p.id());
		}
	}

	public int totalPlotCount() {
		return properties.size();
	}

	public List<PlayerProperty> forOwner(UUID owner) {
		List<Long> ids = byOwner.getOrDefault(owner, List.of());
		return ids.stream().map(properties::get).filter(p -> p != null).toList();
	}

	public String buy(ServerPlayer player, String tierId) throws SQLException {
		UUID owner = player.getUUID();
		if (repository.countForOwner(owner) >= EconomyConfig.maxPropertiesPerPlayer()) {
			return "Oyuncu basina en fazla " + EconomyConfig.maxPropertiesPerPlayer() + " ev.";
		}
		if (StructureBuildQueue.countBuiltPlots(((ServerLevel) player.level()).getServer())
				>= EconomyConfig.maxServerBuiltPlots()) {
			return "Sunucu yapı limiti doldu.";
		}
		if (StructureBuildQueue.get().progressFor(owner) != null) {
			return "Mevcut insaat tamamlanmadan yeni ev alinamaz.";
		}
		PropertyPlacer.Tier tier = PropertyPlacer.Tier.fromId(tierId);
		long price = tier.priceMg();
		if (!currencyService.withdraw(owner, price, TransactionType.MARKET_BUY)) {
			return "Yetersiz bakiye.";
		}
		long tax = taxService.calculateIncomeTax(price);
		if (tax > 0) {
			taxService.collectTax(tax);
		}
		ServerLevel level = (ServerLevel) player.level();
		int plotIndex = repository.nextPlotIndex();
		BlockPos origin = PropertyPlacer.findBuildOrigin(level, plotIndex);
		PlayerProperty prop = repository.insert(owner, tier.name(), origin, origin.getY(), plotIndex);
		String label = tier.name() + " evi";
		boolean queued = StructureBuildQueue.get().enqueue(owner, prop.id(),
				PropertyPlacer.placer(tier, origin, () -> {}), label);
		if (!queued) {
			repository.delete(prop.id());
			currencyService.deposit(owner, price, TransactionType.MARKET_SELL);
			return "Insaat kuyrugu dolu; odeme iade edildi.";
		}
		properties.put(prop.id(), prop);
		byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(prop.id());
		return "OK:" + prop.id();
	}

	public boolean teleport(ServerPlayer player, long propertyId) {
		PlayerProperty p = properties.get(propertyId);
		if (p == null || !p.ownerUuid().equals(player.getUUID())) {
			return false;
		}
		player.teleportTo((ServerLevel) player.level(), p.origin().getX() + 2.5, p.originY() + 1,
				p.origin().getZ() + 2.5, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		return true;
	}

	public String sell(ServerPlayer player, long propertyId) throws SQLException {
		PlayerProperty p = properties.get(propertyId);
		if (p == null || !p.ownerUuid().equals(player.getUUID())) {
			return "Ev bulunamadi.";
		}
		StructureBuildQueue.get().purgeForLinkedId(propertyId);
		PropertyPlacer.Tier tier = PropertyPlacer.Tier.valueOf(p.tier());
		long refund = (long) (tier.priceMg() * 0.5);
		currencyService.deposit(player.getUUID(), refund, TransactionType.MARKET_SELL);
		ServerLevel level = (ServerLevel) player.level();
		PropertyPlacer.demolish(level, p.origin(), tier);
		repository.delete(propertyId);
		properties.remove(propertyId);
		List<Long> owned = byOwner.get(player.getUUID());
		if (owned != null) {
			owned.remove(propertyId);
		}
		return "OK";
	}

	public void onRentTick(MinecraftServer server) {
		if (server.getTickCount() % 72000 != 0) {
			return;
		}
		for (PlayerProperty p : new ArrayList<>(properties.values())) {
			PropertyPlacer.Tier tier = PropertyPlacer.Tier.valueOf(p.tier());
			long tax = (long) (tier.priceMg() * EconomyConfig.propertyTaxRate() * 0.01);
			if (tax > 0 && currencyService.withdraw(p.ownerUuid(), tax, TransactionType.TAX)) {
				taxService.collectTax(tax);
			}
		}
	}

	public boolean isProtectedBlock(int x, int y, int z, ServerLevel level) {
		for (PlayerProperty p : properties.values()) {
			PropertyPlacer.Tier tier = PropertyPlacer.Tier.valueOf(p.tier());
			if (PropertyPlacer.isProtected(level, x, y, z, p.origin(), tier)) {
				return true;
			}
		}
		return false;
	}
}
