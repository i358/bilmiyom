package com.mceconomy.persistence.repo;

import com.mceconomy.economy.EconomyEventCategory;
import com.mceconomy.economy.EconomyEventDirection;
import com.mceconomy.economy.EconomyEventScope;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EconomyEventRepository {
	private final Connection connection;

	public EconomyEventRepository(Connection connection) {
		this.connection = connection;
	}

	public void record(EconomyEventScope scope, UUID ownerUuid, Integer companyId, EconomyEventCategory category,
			EconomyEventDirection direction, long amountMg, UUID counterpartyUuid, String counterpartyName,
			String assetSymbol, int quantity, String source, String description, String metadataJson, long timestamp)
			throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO economy_events(scope, owner_uuid, company_id, category, direction, amount_mg,
					counterparty_uuid, counterparty_name, asset_symbol, quantity, source, description,
					metadata_json, timestamp)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")) {
			ps.setString(1, scope.name());
			ps.setString(2, ownerUuid != null ? ownerUuid.toString() : null);
			if (companyId != null) {
				ps.setInt(3, companyId);
			} else {
				ps.setNull(3, java.sql.Types.INTEGER);
			}
			ps.setString(4, category.name());
			ps.setString(5, direction.name());
			ps.setLong(6, amountMg);
			ps.setString(7, counterpartyUuid != null ? counterpartyUuid.toString() : null);
			ps.setString(8, counterpartyName);
			ps.setString(9, assetSymbol);
			ps.setInt(10, quantity);
			ps.setString(11, source);
			ps.setString(12, description);
			ps.setString(13, metadataJson);
			ps.setLong(14, timestamp);
			ps.executeUpdate();
		}
	}

	public List<Map<String, Object>> loadPersonal(UUID ownerUuid, EconomyEventCategory category, int limit)
			throws SQLException {
		return loadScoped(EconomyEventScope.PERSONAL, ownerUuid, null, category, limit);
	}

	public List<Map<String, Object>> loadCompany(int companyId, EconomyEventCategory category, int limit)
			throws SQLException {
		return loadScoped(EconomyEventScope.COMPANY, null, companyId, category, limit);
	}

	public List<Map<String, Object>> loadMunicipal(EconomyEventCategory category, int limit) throws SQLException {
		return loadScoped(EconomyEventScope.MUNICIPAL, null, null, category, limit);
	}

	private List<Map<String, Object>> loadScoped(EconomyEventScope scope, UUID ownerUuid, Integer companyId,
			EconomyEventCategory category, int limit) throws SQLException {
		StringBuilder sql = new StringBuilder("""
				SELECT id, scope, owner_uuid, company_id, category, direction, amount_mg,
					counterparty_uuid, counterparty_name, asset_symbol, quantity, source,
					description, metadata_json, timestamp
				FROM economy_events WHERE scope = ?
				""");
		List<Object> params = new ArrayList<>();
		params.add(scope.name());
		if (ownerUuid != null) {
			sql.append(" AND owner_uuid = ?");
			params.add(ownerUuid.toString());
		}
		if (companyId != null) {
			sql.append(" AND company_id = ?");
			params.add(companyId);
		}
		if (category != null) {
			sql.append(" AND category = ?");
			params.add(category.name());
		}
		sql.append(" ORDER BY timestamp DESC LIMIT ?");
		params.add(Math.max(1, Math.min(limit, 500)));

		List<Map<String, Object>> rows = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
			for (int i = 0; i < params.size(); i++) {
				Object p = params.get(i);
				if (p instanceof String s) {
					ps.setString(i + 1, s);
				} else if (p instanceof Integer n) {
					ps.setInt(i + 1, n);
				}
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					rows.add(mapRow(rs));
				}
			}
		}
		return rows;
	}

	public Map<String, Integer> countByCategory(EconomyEventScope scope, UUID ownerUuid, Integer companyId)
			throws SQLException {
		StringBuilder sql = new StringBuilder("""
				SELECT category, COUNT(*) AS cnt FROM economy_events WHERE scope = ?
				""");
		List<Object> params = new ArrayList<>();
		params.add(scope.name());
		if (ownerUuid != null) {
			sql.append(" AND owner_uuid = ?");
			params.add(ownerUuid.toString());
		}
		if (companyId != null) {
			sql.append(" AND company_id = ?");
			params.add(companyId);
		}
		sql.append(" GROUP BY category");

		Map<String, Integer> counts = new LinkedHashMap<>();
		try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
			for (int i = 0; i < params.size(); i++) {
				Object p = params.get(i);
				if (p instanceof String s) {
					ps.setString(i + 1, s);
				} else if (p instanceof Integer n) {
					ps.setInt(i + 1, n);
				}
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					counts.put(rs.getString("category"), rs.getInt("cnt"));
				}
			}
		}
		return counts;
	}

	public List<Map<String, Object>> aggregateByDay(EconomyEventScope scope, UUID ownerUuid, Integer companyId,
			long sinceMs) throws SQLException {
		StringBuilder sql = new StringBuilder("""
				SELECT (timestamp / 86400000) AS day_bucket, direction, SUM(amount_mg) AS total
				FROM economy_events WHERE scope = ? AND timestamp >= ?
				""");
		List<Object> params = new ArrayList<>();
		params.add(scope.name());
		params.add(sinceMs);
		if (ownerUuid != null) {
			sql.append(" AND owner_uuid = ?");
			params.add(ownerUuid.toString());
		}
		if (companyId != null) {
			sql.append(" AND company_id = ?");
			params.add(companyId);
		}
		sql.append(" GROUP BY day_bucket, direction ORDER BY day_bucket ASC");

		List<Map<String, Object>> rows = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
			for (int i = 0; i < params.size(); i++) {
				Object p = params.get(i);
				if (p instanceof String s) {
					ps.setString(i + 1, s);
				} else if (p instanceof Long l) {
					ps.setLong(i + 1, l);
				} else if (p instanceof Integer n) {
					ps.setInt(i + 1, n);
				}
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("dayBucket", rs.getLong("day_bucket"));
					row.put("direction", rs.getString("direction"));
					row.put("totalMg", rs.getLong("total"));
					rows.add(row);
				}
			}
		}
		return rows;
	}

	public List<Map<String, Object>> aggregateByCategory(EconomyEventScope scope, UUID ownerUuid, Integer companyId,
			long sinceMs) throws SQLException {
		StringBuilder sql = new StringBuilder("""
				SELECT category, direction, SUM(amount_mg) AS total
				FROM economy_events WHERE scope = ? AND timestamp >= ?
				""");
		List<Object> params = new ArrayList<>();
		params.add(scope.name());
		params.add(sinceMs);
		if (ownerUuid != null) {
			sql.append(" AND owner_uuid = ?");
			params.add(ownerUuid.toString());
		}
		if (companyId != null) {
			sql.append(" AND company_id = ?");
			params.add(companyId);
		}
		sql.append(" GROUP BY category, direction");

		List<Map<String, Object>> rows = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
			for (int i = 0; i < params.size(); i++) {
				Object p = params.get(i);
				if (p instanceof String s) {
					ps.setString(i + 1, s);
				} else if (p instanceof Long l) {
					ps.setLong(i + 1, l);
				} else if (p instanceof Integer n) {
					ps.setInt(i + 1, n);
				}
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("category", rs.getString("category"));
					row.put("direction", rs.getString("direction"));
					row.put("totalMg", rs.getLong("total"));
					rows.add(row);
				}
			}
		}
		return rows;
	}

	private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", rs.getLong("id"));
		row.put("scope", rs.getString("scope"));
		row.put("ownerUuid", rs.getString("owner_uuid"));
		row.put("companyId", rs.getObject("company_id"));
		row.put("category", rs.getString("category"));
		row.put("direction", rs.getString("direction"));
		row.put("amountMg", rs.getLong("amount_mg"));
		row.put("counterpartyUuid", rs.getString("counterparty_uuid"));
		row.put("counterpartyName", rs.getString("counterparty_name"));
		row.put("assetSymbol", rs.getString("asset_symbol"));
		row.put("quantity", rs.getInt("quantity"));
		row.put("source", rs.getString("source"));
		row.put("description", rs.getString("description"));
		row.put("metadataJson", rs.getString("metadata_json"));
		row.put("timestamp", rs.getLong("timestamp"));
		return row;
	}
}
