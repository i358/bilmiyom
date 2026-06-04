package com.mceconomy.persistence.repo;

import com.mceconomy.vehicle.PlayerVehicle;
import net.minecraft.core.BlockPos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VehicleRepository {
	private final Connection connection;

	public VehicleRepository(Connection connection) {
		this.connection = connection;
	}

	public List<PlayerVehicle> loadAll() throws SQLException {
		List<PlayerVehicle> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_vehicles");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(map(rs));
			}
		}
		return list;
	}

	public int countForOwner(UUID owner) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT COUNT(*) FROM player_vehicles WHERE owner_uuid = ?")) {
			ps.setString(1, owner.toString());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}

	public int spawnedCount() throws SQLException {
		try (Statement stmt = connection.createStatement();
			 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_vehicles WHERE spawned = 1")) {
			return rs.next() ? rs.getInt(1) : 0;
		}
	}

	public PlayerVehicle insert(UUID owner, String model, BlockPos garage) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO player_vehicles(owner_uuid, model, garage_x, garage_y, garage_z, fuel, spawned)
				VALUES(?, ?, ?, ?, ?, 100.0, 0)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, owner.toString());
			ps.setString(2, model);
			ps.setInt(3, garage.getX());
			ps.setInt(4, garage.getY());
			ps.setInt(5, garage.getZ());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				long id = keys.next() ? keys.getLong(1) : 0;
				return new PlayerVehicle(id, owner, model, garage, 100.0, null, false);
			}
		}
	}

	public void update(PlayerVehicle v) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE player_vehicles SET fuel=?, entity_uuid=?, spawned=? WHERE id=?
				""")) {
			ps.setDouble(1, v.fuel());
			ps.setString(2, v.entityUuid() != null ? v.entityUuid().toString() : null);
			ps.setInt(3, v.spawned() ? 1 : 0);
			ps.setLong(4, v.id());
			ps.executeUpdate();
		}
	}

	private static PlayerVehicle map(ResultSet rs) throws SQLException {
		String entityStr = rs.getString("entity_uuid");
		return new PlayerVehicle(
				rs.getLong("id"),
				UUID.fromString(rs.getString("owner_uuid")),
				rs.getString("model"),
				new BlockPos(rs.getInt("garage_x"), rs.getInt("garage_y"), rs.getInt("garage_z")),
				rs.getDouble("fuel"),
				entityStr != null ? UUID.fromString(entityStr) : null,
				rs.getInt("spawned") == 1);
	}
}
