package com.mceconomy.persistence.repo;

import com.mceconomy.appeal.Appeal;
import com.mceconomy.appeal.AppealStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AppealRepository {
	private final Connection connection;

	public AppealRepository(Connection connection) {
		this.connection = connection;
	}

	public void save(Appeal appeal) throws SQLException {
		if (appeal.id() <= 0) {
			insert(appeal);
		} else {
			update(appeal);
		}
	}

	public List<Appeal> loadOpen() throws SQLException {
		List<Appeal> appeals = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM appeals WHERE status = 'OPEN' ORDER BY created_at ASC");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				appeals.add(map(rs));
			}
		}
		return appeals;
	}

	public Optional<Appeal> findById(long id) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM appeals WHERE id = ?")) {
			ps.setLong(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Optional.of(map(rs));
				}
			}
		}
		return Optional.empty();
	}

	public List<Appeal> loadForPlayer(UUID uuid) throws SQLException {
		List<Appeal> appeals = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM appeals WHERE player_uuid = ? ORDER BY created_at DESC LIMIT 10")) {
			ps.setString(1, uuid.toString());
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					appeals.add(map(rs));
				}
			}
		}
		return appeals;
	}

	private void insert(Appeal appeal) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO appeals(player_uuid, player_name, subject, message, related_alert_id,
					status, admin_note, created_at, resolved_at)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, appeal.playerUuid().toString());
			ps.setString(2, appeal.playerName());
			ps.setString(3, appeal.subject());
			ps.setString(4, appeal.message());
			if (appeal.relatedAlertId() != null) {
				ps.setLong(5, appeal.relatedAlertId());
			} else {
				ps.setNull(5, java.sql.Types.INTEGER);
			}
			ps.setString(6, appeal.status().name());
			ps.setString(7, appeal.adminNote());
			ps.setLong(8, appeal.createdAt());
			ps.setLong(9, appeal.resolvedAt());
			ps.executeUpdate();
		}
	}

	private void update(Appeal appeal) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE appeals SET status=?, admin_note=?, resolved_at=? WHERE id=?
				""")) {
			ps.setString(1, appeal.status().name());
			ps.setString(2, appeal.adminNote());
			ps.setLong(3, appeal.resolvedAt());
			ps.setLong(4, appeal.id());
			ps.executeUpdate();
		}
	}

	private Appeal map(ResultSet rs) throws SQLException {
		long alertId = rs.getLong("related_alert_id");
		return new Appeal(
				rs.getLong("id"),
				UUID.fromString(rs.getString("player_uuid")),
				rs.getString("player_name"),
				rs.getString("subject"),
				rs.getString("message"),
				rs.wasNull() ? null : alertId,
				AppealStatus.valueOf(rs.getString("status")),
				rs.getString("admin_note"),
				rs.getLong("created_at"),
				rs.getLong("resolved_at")
		);
	}
}
