package com.mceconomy.persistence.repo;

import com.mceconomy.news.EconomyBulletin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class EconomyBulletinRepository {
	private final Connection connection;

	public EconomyBulletinRepository(Connection connection) {
		this.connection = connection;
	}

	public long insert(EconomyBulletin bulletin) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO economy_bulletins(category, headline, body, value_mg, created_at)
				VALUES(?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, bulletin.category());
			ps.setString(2, bulletin.headline());
			ps.setString(3, bulletin.body());
			ps.setLong(4, bulletin.valueMg());
			ps.setLong(5, bulletin.createdAt());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				return keys.next() ? keys.getLong(1) : 0;
			}
		}
	}

	public List<EconomyBulletin> loadRecent(int limit) throws SQLException {
		List<EconomyBulletin> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT id, category, headline, body, value_mg, created_at
				FROM economy_bulletins ORDER BY created_at DESC LIMIT ?
				""")) {
			ps.setInt(1, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(map(rs));
				}
			}
		}
		return list;
	}

	public List<EconomyBulletin> loadRecentByCategory(String category, int limit) throws SQLException {
		List<EconomyBulletin> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT id, category, headline, body, value_mg, created_at
				FROM economy_bulletins WHERE category = ? ORDER BY created_at DESC LIMIT ?
				""")) {
			ps.setString(1, category);
			ps.setInt(2, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(map(rs));
				}
			}
		}
		return list;
	}

	private EconomyBulletin map(ResultSet rs) throws SQLException {
		return new EconomyBulletin(
				rs.getLong("id"),
				rs.getString("category"),
				rs.getString("headline"),
				rs.getString("body"),
				rs.getLong("value_mg"),
				rs.getLong("created_at"));
	}
}
