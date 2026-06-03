package com.mceconomy.persistence.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class CompanyStashRepository {
	private final Connection connection;

	public CompanyStashRepository(Connection connection) {
		this.connection = connection;
	}

	public Map<Integer, Map<String, Integer>> loadAll() throws SQLException {
		Map<Integer, Map<String, Integer>> result = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM company_stash WHERE quantity > 0");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				int companyId = rs.getInt("company_id");
				result.computeIfAbsent(companyId, k -> new HashMap<>())
						.put(rs.getString("commodity_id"), rs.getInt("quantity"));
			}
		}
		return result;
	}

	public void save(int companyId, String commodityId, int quantity) throws SQLException {
		if (quantity <= 0) {
			try (PreparedStatement ps = connection.prepareStatement(
					"DELETE FROM company_stash WHERE company_id = ? AND commodity_id = ?")) {
				ps.setInt(1, companyId);
				ps.setString(2, commodityId);
				ps.executeUpdate();
			}
			return;
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO company_stash(company_id, commodity_id, quantity)
				VALUES(?, ?, ?)
				ON CONFLICT(company_id, commodity_id) DO UPDATE SET quantity = excluded.quantity
				""")) {
			ps.setInt(1, companyId);
			ps.setString(2, commodityId);
			ps.setInt(3, quantity);
			ps.executeUpdate();
		}
	}
}
