package com.mceconomy.persistence.repo;

import com.mceconomy.regulation.MasakAlert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MasakRepository {
	private final Connection connection;

	public MasakRepository(Connection connection) {
		this.connection = connection;
	}

	public void save(MasakAlert alert) throws SQLException {
		if (alert.id() > 0) {
			try (PreparedStatement ps = connection.prepareStatement(
					"UPDATE masak_alerts SET resolved = ? WHERE id = ?")) {
				ps.setInt(1, alert.resolved() ? 1 : 0);
				ps.setLong(2, alert.id());
				ps.executeUpdate();
			}
			return;
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO masak_alerts(player_uuid, reason, risk_score, amount, resolved, created_at)
				VALUES(?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, alert.playerUuid().toString());
			ps.setString(2, alert.reason());
			ps.setInt(3, alert.riskScore());
			ps.setLong(4, alert.amount());
			ps.setInt(5, alert.resolved() ? 1 : 0);
			ps.setLong(6, alert.createdAt());
			ps.executeUpdate();
		}
	}

	public List<MasakAlert> loadOpenAlerts() throws SQLException {
		List<MasakAlert> alerts = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM masak_alerts WHERE resolved = 0 ORDER BY created_at DESC LIMIT 50");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				alerts.add(map(rs));
			}
		}
		return alerts;
	}

	public List<MasakAlert> loadAlertsForPlayer(UUID uuid) throws SQLException {
		List<MasakAlert> alerts = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM masak_alerts WHERE player_uuid = ? ORDER BY created_at DESC LIMIT 20")) {
			ps.setString(1, uuid.toString());
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					alerts.add(map(rs));
				}
			}
		}
		return alerts;
	}

	private static MasakAlert map(ResultSet rs) throws SQLException {
		return new MasakAlert(
				rs.getLong("id"),
				UUID.fromString(rs.getString("player_uuid")),
				rs.getString("reason"),
				rs.getInt("risk_score"),
				rs.getLong("amount"),
				rs.getInt("resolved") == 1,
				rs.getLong("created_at")
		);
	}
}
