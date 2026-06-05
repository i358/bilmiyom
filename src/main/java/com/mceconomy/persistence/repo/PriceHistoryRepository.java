package com.mceconomy.persistence.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PriceHistoryRepository {
	private final Connection connection;

	public PriceHistoryRepository(Connection connection) {
		this.connection = connection;
	}

	public void record(String symbolType, String symbol, long priceMg, long recordedAt) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO price_history(symbol_type, symbol, price_mg, recorded_at)
				VALUES(?, ?, ?, ?)
				""")) {
			ps.setString(1, symbolType);
			ps.setString(2, symbol);
			ps.setLong(3, priceMg);
			ps.setLong(4, recordedAt);
			ps.executeUpdate();
		}
	}

	/** Son {@code limit} kayit, zaman sirasina gore eskiden yeniye (grafik ve degisim yuzdesi icin). */
	public List<Map<String, Object>> loadRecent(String symbolType, String symbol, int limit) throws SQLException {
		List<Map<String, Object>> rows = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT price_mg, recorded_at FROM (
					SELECT price_mg, recorded_at FROM price_history
					WHERE symbol_type = ? AND symbol = ?
					ORDER BY recorded_at DESC LIMIT ?
				) ORDER BY recorded_at ASC
				""")) {
			ps.setString(1, symbolType);
			ps.setString(2, symbol);
			ps.setInt(3, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("priceMg", rs.getLong("price_mg"));
					row.put("recordedAt", rs.getLong("recorded_at"));
					rows.add(row);
				}
			}
		}
		return rows;
	}

	/** Son {@code windowLimit} kayit icindeki en eski fiyat (sembol -> priceMg). */
	public Map<String, Long> loadOldestPriceInWindow(String symbolType, int windowLimit) throws SQLException {
		Map<String, Long> oldest = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				WITH recent AS (
					SELECT symbol, price_mg, recorded_at,
						ROW_NUMBER() OVER (PARTITION BY symbol ORDER BY recorded_at DESC) AS rn
					FROM price_history
					WHERE symbol_type = ?
				),
				windowed AS (
					SELECT symbol, price_mg,
						ROW_NUMBER() OVER (PARTITION BY symbol ORDER BY recorded_at ASC) AS rn_oldest
					FROM recent
					WHERE rn <= ?
				)
				SELECT symbol, price_mg
				FROM windowed
				WHERE rn_oldest = 1
				""")) {
			ps.setString(1, symbolType);
			ps.setInt(2, windowLimit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					oldest.put(rs.getString("symbol"), rs.getLong("price_mg"));
				}
			}
		}
		return oldest;
	}

	public void pruneOlderThan(long cutoffMs) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("DELETE FROM price_history WHERE recorded_at < ?")) {
			ps.setLong(1, cutoffMs);
			ps.executeUpdate();
		}
	}
}
