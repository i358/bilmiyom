package com.mceconomy.persistence.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SecurityCameraRepository {
	public record CameraLog(long id, String nightKey, UUID playerUuid, String playerName,
			int x, int y, int z, long recordedAt) {
	}

	private final Connection connection;

	public SecurityCameraRepository(Connection connection) {
		this.connection = connection;
	}

	public void purgeAll() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("DELETE FROM security_camera_logs");
		}
	}

	public void insert(String nightKey, UUID playerUuid, String playerName, int x, int y, int z, long recordedAt)
			throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO security_camera_logs(night_key, player_uuid, player_name, x, y, z, recorded_at)
				VALUES(?, ?, ?, ?, ?, ?, ?)
				""")) {
			ps.setString(1, nightKey);
			ps.setString(2, playerUuid.toString());
			ps.setString(3, playerName);
			ps.setInt(4, x);
			ps.setInt(5, y);
			ps.setInt(6, z);
			ps.setLong(7, recordedAt);
			ps.executeUpdate();
		}
	}

	public List<CameraLog> loadByNight(String nightKey, int limit) throws SQLException {
		List<CameraLog> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT id, night_key, player_uuid, player_name, x, y, z, recorded_at
				FROM security_camera_logs WHERE night_key = ? ORDER BY recorded_at DESC LIMIT ?
				""")) {
			ps.setString(1, nightKey);
			ps.setInt(2, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(readRow(rs));
				}
			}
		}
		return list;
	}

	/** Radar: her oyuncunun son gorulen konumu (gece kaydi). */
	/** Radar video oynatimi: kronolojik kareler. */
	public List<CameraLog> loadReplayChronological(String nightKey, int limit) throws SQLException {
		List<CameraLog> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT id, night_key, player_uuid, player_name, x, y, z, recorded_at
				FROM security_camera_logs WHERE night_key = ? ORDER BY recorded_at ASC LIMIT ?
				""")) {
			ps.setString(1, nightKey);
			ps.setInt(2, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(readRow(rs));
				}
			}
		}
		return list;
	}

	public List<CameraLog> loadLatestPerPlayer(String nightKey, int maxPlayers) throws SQLException {
		Map<String, CameraLog> latest = new LinkedHashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT id, night_key, player_uuid, player_name, x, y, z, recorded_at
				FROM security_camera_logs WHERE night_key = ? ORDER BY recorded_at DESC LIMIT 2000
				""")) {
			ps.setString(1, nightKey);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					String uuid = rs.getString("player_uuid");
					if (!latest.containsKey(uuid)) {
						latest.put(uuid, readRow(rs));
						if (latest.size() >= maxPlayers) {
							break;
						}
					}
				}
			}
		}
		return List.copyOf(latest.values());
	}

	private static CameraLog readRow(ResultSet rs) throws SQLException {
		return new CameraLog(
				rs.getLong("id"),
				rs.getString("night_key"),
				UUID.fromString(rs.getString("player_uuid")),
				rs.getString("player_name"),
				rs.getInt("x"),
				rs.getInt("y"),
				rs.getInt("z"),
				rs.getLong("recorded_at"));
	}

	public List<String> listNightKeys() throws SQLException {
		List<String> keys = new ArrayList<>();
		try (Statement stmt = connection.createStatement();
			 ResultSet rs = stmt.executeQuery(
					 "SELECT DISTINCT night_key FROM security_camera_logs ORDER BY night_key DESC LIMIT 14")) {
			while (rs.next()) {
				keys.add(rs.getString(1));
			}
		}
		return keys;
	}

	public Map<String, Integer> countByPlayer(String nightKey) throws SQLException {
		java.util.HashMap<String, Integer> map = new java.util.HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT player_name, COUNT(*) AS c FROM security_camera_logs
				WHERE night_key = ? GROUP BY player_name ORDER BY c DESC
				""")) {
			ps.setString(1, nightKey);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					map.put(rs.getString("player_name"), rs.getInt("c"));
				}
			}
		}
		return map;
	}
}
