package com.mceconomy.persistence.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public final class NationalReserveRepository {
	private final Connection connection;

	public NationalReserveRepository(Connection connection) {
		this.connection = connection;
	}

	public Map<String, Integer> loadReserve() throws SQLException {
		Map<String, Integer> map = new HashMap<>();
		try (Statement stmt = connection.createStatement();
			 ResultSet rs = stmt.executeQuery("SELECT item_id, quantity FROM national_reserve")) {
			while (rs.next()) {
				map.put(rs.getString("item_id"), rs.getInt("quantity"));
			}
		}
		return map;
	}

	public void saveReserveItem(String itemId, int quantity) throws SQLException {
		if (quantity <= 0) {
			try (PreparedStatement ps = connection.prepareStatement(
					"DELETE FROM national_reserve WHERE item_id = ?")) {
				ps.setString(1, itemId);
				ps.executeUpdate();
			}
			return;
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO national_reserve(item_id, quantity) VALUES(?, ?)
				ON CONFLICT(item_id) DO UPDATE SET quantity = excluded.quantity
				""")) {
			ps.setString(1, itemId);
			ps.setInt(2, quantity);
			ps.executeUpdate();
		}
	}

	public int loadLedger(String key, int defaultValue) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT value_int FROM depot_ledger WHERE ledger_key = ?")) {
			ps.setString(1, key);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getInt("value_int") : defaultValue;
			}
		}
	}

	public void saveLedger(String key, int value) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO depot_ledger(ledger_key, value_int) VALUES(?, ?)
				ON CONFLICT(ledger_key) DO UPDATE SET value_int = excluded.value_int
				""")) {
			ps.setString(1, key);
			ps.setInt(2, value);
			ps.executeUpdate();
		}
	}
}
