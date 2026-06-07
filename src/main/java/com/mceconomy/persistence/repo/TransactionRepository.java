package com.mceconomy.persistence.repo;

import com.mceconomy.economy.EconomyEventCategory;
import com.mceconomy.economy.EconomyEventDirection;
import com.mceconomy.economy.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Gecmis cuzdan islemleri icin transactions tablosu okuma (backfill). */
public final class TransactionRepository {
	private final Connection connection;

	public TransactionRepository(Connection connection) {
		this.connection = connection;
	}

	public List<Map<String, Object>> loadForPlayer(UUID playerUuid, EconomyEventCategory categoryFilter, int limit)
			throws SQLException {
		List<Map<String, Object>> rows = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT id, from_uuid, to_uuid, amount, type, timestamp, metadata_json
				FROM transactions
				WHERE from_uuid = ? OR to_uuid = ?
				ORDER BY timestamp DESC LIMIT ?
				""")) {
			String uuid = playerUuid.toString();
			ps.setString(1, uuid);
			ps.setString(2, uuid);
			ps.setInt(3, Math.max(1, Math.min(limit, 500)));
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> mapped = mapTransaction(playerUuid, rs);
					if (categoryFilter == null || categoryFilter.name().equals(mapped.get("category"))) {
						rows.add(mapped);
					}
				}
			}
		}
		return rows;
	}

	private Map<String, Object> mapTransaction(UUID playerUuid, ResultSet rs) throws SQLException {
		String from = rs.getString("from_uuid");
		String to = rs.getString("to_uuid");
		String typeStr = rs.getString("type");
		long amount = rs.getLong("amount");
		String player = playerUuid.toString();
		boolean incoming = player.equals(to);
		TransactionType type;
		try {
			type = TransactionType.valueOf(typeStr);
		} catch (IllegalArgumentException e) {
			type = TransactionType.TRANSFER;
		}
		EconomyEventCategory category = categoryForType(type);
		EconomyEventDirection direction = incoming ? EconomyEventDirection.IN : EconomyEventDirection.OUT;
		String counterpartyUuid = incoming ? from : to;
		String description = buildDescription(type, incoming, amount, rs.getString("metadata_json"));

		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", "tx-" + rs.getLong("id"));
		row.put("scope", "PERSONAL");
		row.put("ownerUuid", player);
		row.put("companyId", null);
		row.put("category", category.name());
		row.put("direction", direction.name());
		row.put("amountMg", amount);
		row.put("counterpartyUuid", counterpartyUuid);
		row.put("counterpartyName", null);
		row.put("assetSymbol", null);
		row.put("quantity", 0);
		row.put("source", type.name());
		row.put("description", description);
		row.put("metadataJson", rs.getString("metadata_json"));
		row.put("timestamp", rs.getLong("timestamp"));
		row.put("legacy", true);
		return row;
	}

	private EconomyEventCategory categoryForType(TransactionType type) {
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
			case TRANSFER, DEPOSIT, WITHDRAW, ADMIN_OP -> EconomyEventCategory.WALLET;
		};
	}

	private String buildDescription(TransactionType type, boolean incoming, long amountMg, String metadata) {
		String dir = incoming ? "Gelir" : "Gider";
		String meta = metadata != null && !metadata.isBlank() ? " (" + metadata + ")" : "";
		return dir + " — " + type.name() + ": " + amountMg + " mg" + meta;
	}
}
