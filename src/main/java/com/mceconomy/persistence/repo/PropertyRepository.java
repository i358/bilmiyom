package com.mceconomy.persistence.repo;

import com.mceconomy.property.PlayerProperty;
import net.minecraft.core.BlockPos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PropertyRepository {
	private final Connection connection;

	public PropertyRepository(Connection connection) {
		this.connection = connection;
	}

	public List<PlayerProperty> loadAll() throws SQLException {
		List<PlayerProperty> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_properties");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(map(rs));
			}
		}
		return list;
	}

	public int countForOwner(UUID owner) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT COUNT(*) FROM player_properties WHERE owner_uuid = ?")) {
			ps.setString(1, owner.toString());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}

	public int totalCount() throws SQLException {
		try (Statement stmt = connection.createStatement();
			 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_properties")) {
			return rs.next() ? rs.getInt(1) : 0;
		}
	}

	/** Monoton artan arsa slotu — satis sonrasi tekrar kullanilmaz. */
	public int nextPlotIndex() throws SQLException {
		try (Statement stmt = connection.createStatement();
			 ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(plot_index), -1) + 1 FROM player_properties")) {
			return rs.next() ? rs.getInt(1) : 0;
		}
	}

	public PlayerProperty insert(UUID owner, String tier, BlockPos origin, int y, int plotIndex) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO player_properties(owner_uuid, tier, origin_x, origin_y, origin_z, purchased_at, plot_index)
				VALUES(?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, owner.toString());
			ps.setString(2, tier);
			ps.setInt(3, origin.getX());
			ps.setInt(4, y);
			ps.setInt(5, origin.getZ());
			ps.setLong(6, System.currentTimeMillis());
			ps.setInt(7, plotIndex);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				long id = keys.next() ? keys.getLong(1) : 0;
				return new PlayerProperty(id, owner, tier, origin, y, System.currentTimeMillis(), plotIndex);
			}
		}
	}

	public void delete(long id) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_properties WHERE id = ?")) {
			ps.setLong(1, id);
			ps.executeUpdate();
		}
	}

	public void updateTier(long id, String tier) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"UPDATE player_properties SET tier = ? WHERE id = ?")) {
			ps.setString(1, tier);
			ps.setLong(2, id);
			ps.executeUpdate();
		}
	}

	private static PlayerProperty map(ResultSet rs) throws SQLException {
		int plotIndex = rs.getInt("plot_index");
		return new PlayerProperty(
				rs.getLong("id"),
				UUID.fromString(rs.getString("owner_uuid")),
				rs.getString("tier"),
				new BlockPos(rs.getInt("origin_x"), rs.getInt("origin_y"), rs.getInt("origin_z")),
				rs.getInt("origin_y"),
				rs.getLong("purchased_at"),
				plotIndex);
	}
}
