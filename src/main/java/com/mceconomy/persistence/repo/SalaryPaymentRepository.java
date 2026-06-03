package com.mceconomy.persistence.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SalaryPaymentRepository {
	private final Connection connection;

	public SalaryPaymentRepository(Connection connection) {
		this.connection = connection;
	}

	public void record(UUID playerUuid, String playerName, int companyId, long amountMg, long bonusMg)
			throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO salary_payments(player_uuid, player_name, company_id, amount_mg, bonus_mg, paid_at)
				VALUES(?, ?, ?, ?, ?, ?)
				""")) {
			ps.setString(1, playerUuid.toString());
			ps.setString(2, playerName);
			ps.setInt(3, companyId);
			ps.setLong(4, amountMg);
			ps.setLong(5, bonusMg);
			ps.setLong(6, System.currentTimeMillis());
			ps.executeUpdate();
		}
	}

	public List<SalaryPaymentRow> loadForPlayer(UUID playerUuid, int limit) throws SQLException {
		List<SalaryPaymentRow> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT id, player_uuid, player_name, company_id, amount_mg, bonus_mg, paid_at
				FROM salary_payments WHERE player_uuid = ? ORDER BY paid_at DESC LIMIT ?
				""")) {
			ps.setString(1, playerUuid.toString());
			ps.setInt(2, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(map(rs));
				}
			}
		}
		return list;
	}

	private SalaryPaymentRow map(ResultSet rs) throws SQLException {
		return new SalaryPaymentRow(
				rs.getLong("id"),
				UUID.fromString(rs.getString("player_uuid")),
				rs.getString("player_name"),
				rs.getInt("company_id"),
				rs.getLong("amount_mg"),
				rs.getLong("bonus_mg"),
				rs.getLong("paid_at")
		);
	}

	public record SalaryPaymentRow(long id, UUID playerUuid, String playerName, int companyId,
			long amountMg, long bonusMg, long paidAt) {
	}
}
