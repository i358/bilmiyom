package com.mceconomy.persistence.repo;

import net.minecraft.core.BlockPos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public final class CompanyBuildingRepository {
	public record CompanyBuilding(int companyId, BlockPos origin, int originY) {
	}

	private final Connection connection;

	public CompanyBuildingRepository(Connection connection) {
		this.connection = connection;
	}

	public Map<Integer, CompanyBuilding> loadAll() throws SQLException {
		Map<Integer, CompanyBuilding> map = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM company_buildings");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				map.put(rs.getInt("company_id"), new CompanyBuilding(
						rs.getInt("company_id"),
						new BlockPos(rs.getInt("origin_x"), rs.getInt("origin_y"), rs.getInt("origin_z")),
						rs.getInt("origin_y")));
			}
		}
		return map;
	}

	public int totalCount() throws SQLException {
		try (Statement stmt = connection.createStatement();
			 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM company_buildings")) {
			return rs.next() ? rs.getInt(1) : 0;
		}
	}

	public int countBuildingsForCompanies(java.util.Collection<Integer> companyIds) throws SQLException {
		if (companyIds.isEmpty()) {
			return 0;
		}
		int n = 0;
		for (int id : companyIds) {
			if (loadAll().containsKey(id)) {
				n++;
			}
		}
		return n;
	}

	public void save(int companyId, BlockPos origin, int y) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO company_buildings(company_id, origin_x, origin_y, origin_z)
				VALUES(?, ?, ?, ?)
				ON CONFLICT(company_id) DO UPDATE SET
					origin_x=excluded.origin_x, origin_y=excluded.origin_y, origin_z=excluded.origin_z
				""")) {
			ps.setInt(1, companyId);
			ps.setInt(2, origin.getX());
			ps.setInt(3, y);
			ps.setInt(4, origin.getZ());
			ps.executeUpdate();
		}
	}
}
