package com.mceconomy.economy;

import com.mceconomy.McEconomyMod;
import com.mceconomy.persistence.repo.EconomyEventRepository;
import com.mceconomy.persistence.repo.TransactionRepository;
import com.mceconomy.player.PlayerEconomyProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EconomyEventService {
	private final EconomyEventRepository repository;
	private final TransactionRepository transactionRepository;
	private final Map<UUID, PlayerEconomyProfile> profiles;

	public EconomyEventService(EconomyEventRepository repository, TransactionRepository transactionRepository,
			Map<UUID, PlayerEconomyProfile> profiles) {
		this.repository = repository;
		this.transactionRepository = transactionRepository;
		this.profiles = profiles;
	}

	public void recordPersonal(UUID owner, EconomyEventCategory category, EconomyEventDirection direction, long amountMg,
			String source, String description) {
		record(EconomyEventScope.PERSONAL, owner, null, category, direction, amountMg, null, null, null, 0, source,
				description, null);
	}

	public void recordPersonal(UUID owner, EconomyEventCategory category, EconomyEventDirection direction, long amountMg,
			UUID counterparty, String assetSymbol, int quantity, String source, String description) {
		record(EconomyEventScope.PERSONAL, owner, null, category, direction, amountMg, counterparty,
				resolveName(counterparty), assetSymbol, quantity, source, description, null);
	}

	public void recordCompany(int companyId, UUID ownerUuid, EconomyEventCategory category,
			EconomyEventDirection direction, long amountMg, UUID counterparty, String assetSymbol, int quantity,
			String source, String description) {
		record(EconomyEventScope.COMPANY, ownerUuid, companyId, category, direction, amountMg, counterparty,
				resolveName(counterparty), assetSymbol, quantity, source, description, null);
	}

	public void recordMunicipal(EconomyEventCategory category, EconomyEventDirection direction, long amountMg,
			String source, String description) {
		record(EconomyEventScope.MUNICIPAL, null, null, category, direction, amountMg, null, null, null, 0, source,
				description, null);
	}

	public void recordMunicipal(EconomyEventCategory category, EconomyEventDirection direction, long amountMg,
			UUID counterparty, String source, String description) {
		record(EconomyEventScope.MUNICIPAL, null, null, category, direction, amountMg, counterparty,
				resolveName(counterparty), null, 0, source, description, null);
	}

	private void record(EconomyEventScope scope, UUID ownerUuid, Integer companyId, EconomyEventCategory category,
			EconomyEventDirection direction, long amountMg, UUID counterpartyUuid, String counterpartyName,
			String assetSymbol, int quantity, String source, String description, String metadataJson) {
		if (amountMg <= 0 || description == null || description.isBlank()) {
			return;
		}
		try {
			repository.record(scope, ownerUuid, companyId, category, direction, amountMg, counterpartyUuid,
					counterpartyName, assetSymbol, quantity, source, description, metadataJson,
					System.currentTimeMillis());
		} catch (SQLException e) {
			McEconomyMod.LOGGER.warn("Finans event kaydedilemedi: {}", description, e);
		}
	}

	public String resolveName(UUID uuid) {
		if (uuid == null) {
			return null;
		}
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile != null && profile.name() != null && !profile.name().isBlank()) {
			return profile.name();
		}
		MinecraftServer server = McEconomyMod.getEconomyManager() != null
				? McEconomyMod.getEconomyManager().server() : null;
		if (server != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				return player.getName().getString();
			}
		}
		return uuid.toString().substring(0, 8);
	}

	public List<Map<String, Object>> loadPersonalEvents(UUID owner, EconomyEventCategory category, int limit) {
		try {
			List<Map<String, Object>> events = new ArrayList<>(repository.loadPersonal(owner, category, limit));
			if (category == null || category == EconomyEventCategory.WALLET) {
				List<Map<String, Object>> legacy = transactionRepository.loadForPlayer(owner,
						category == EconomyEventCategory.WALLET ? EconomyEventCategory.WALLET : null, limit);
				events.addAll(legacy);
				events.sort(Comparator.comparingLong((Map<String, Object> e) -> (Long) e.get("timestamp")).reversed());
				if (events.size() > limit) {
					return events.subList(0, limit);
				}
			}
			return events;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Kisisel finans eventleri yuklenemedi", e);
			return List.of();
		}
	}

	public List<Map<String, Object>> loadCompanyEvents(int companyId, EconomyEventCategory category, int limit) {
		try {
			return repository.loadCompany(companyId, category, limit);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Sirket finans eventleri yuklenemedi", e);
			return List.of();
		}
	}

	public List<Map<String, Object>> loadMunicipalEvents(EconomyEventCategory category, int limit) {
		try {
			return repository.loadMunicipal(category, limit);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Belediye finans eventleri yuklenemedi", e);
			return List.of();
		}
	}

	public Map<String, Integer> countPersonalCategories(UUID owner) {
		try {
			return repository.countByCategory(EconomyEventScope.PERSONAL, owner, null);
		} catch (SQLException e) {
			return Map.of();
		}
	}

	public Map<String, Integer> countCompanyCategories(int companyId) {
		try {
			return repository.countByCategory(EconomyEventScope.COMPANY, null, companyId);
		} catch (SQLException e) {
			return Map.of();
		}
	}

	public Map<String, Integer> countMunicipalCategories() {
		try {
			return repository.countByCategory(EconomyEventScope.MUNICIPAL, null, null);
		} catch (SQLException e) {
			return Map.of();
		}
	}

	public List<Map<String, Object>> aggregateByDay(EconomyEventScope scope, UUID ownerUuid, Integer companyId,
			int days) {
		long since = System.currentTimeMillis() - (long) days * 86_400_000L;
		try {
			return repository.aggregateByDay(scope, ownerUuid, companyId, since);
		} catch (SQLException e) {
			return List.of();
		}
	}

	public List<Map<String, Object>> aggregateByCategory(EconomyEventScope scope, UUID ownerUuid, Integer companyId,
			int days) {
		long since = System.currentTimeMillis() - (long) days * 86_400_000L;
		try {
			return repository.aggregateByCategory(scope, ownerUuid, companyId, since);
		} catch (SQLException e) {
			return List.of();
		}
	}

	public EconomyEventCategory categoryForTransactionType(TransactionType type) {
		return switch (type) {
			case MARKET_BUY, MARKET_SELL -> EconomyEventCategory.MARKET;
			case LOAN, LOAN_PAYMENT -> EconomyEventCategory.LOAN;
			case TAX -> EconomyEventCategory.TAX_FEE;
			case QUEST_REWARD -> EconomyEventCategory.QUEST;
			case COMPANY -> EconomyEventCategory.SHARES;
			case BLACK_MARKET_BUY, BLACK_MARKET_SELL, LAUNDERING, LAUNDERING_CAUGHT -> EconomyEventCategory.BLACK_MARKET;
			case MASAK_FINE -> EconomyEventCategory.MASAK;
			case EXCHANGE_TOKEN, EXCHANGE_LISTING -> EconomyEventCategory.EXCHANGE;
			case PRIVATE_BANK -> EconomyEventCategory.PRIVATE_BANK;
			case TRANSFER -> EconomyEventCategory.WALLET;
			case DEPOSIT, WITHDRAW, ADMIN_OP -> EconomyEventCategory.WALLET;
		};
	}
}
