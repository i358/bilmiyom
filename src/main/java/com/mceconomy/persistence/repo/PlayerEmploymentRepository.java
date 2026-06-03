package com.mceconomy.persistence.repo;

import com.mceconomy.company.ApplicationStatus;
import com.mceconomy.company.PlayerEmployment;
import com.mceconomy.company.PlayerJobApplication;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerEmploymentRepository {
	private final Connection connection;

	public PlayerEmploymentRepository(Connection connection) {
		this.connection = connection;
	}

	public List<PlayerJobApplication> loadPendingApplications() throws SQLException {
		List<PlayerJobApplication> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM player_job_applications WHERE status = 'PENDING' ORDER BY applied_at")) {
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(mapApplication(rs));
				}
			}
		}
		return list;
	}

	public int countPendingForCompany(int companyId) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT COUNT(*) FROM player_job_applications WHERE company_id = ? AND status = 'PENDING'")) {
			ps.setInt(1, companyId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}

	public Optional<PlayerJobApplication> findPendingForPlayer(UUID playerUuid) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM player_job_applications WHERE player_uuid = ? AND status = 'PENDING' LIMIT 1")) {
			ps.setString(1, playerUuid.toString());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? Optional.of(mapApplication(rs)) : Optional.empty();
			}
		}
	}

	public void saveApplication(PlayerJobApplication app) throws SQLException {
		if (app.id() <= 0) {
			insertApplication(app);
		} else {
			updateApplication(app);
		}
	}

	public List<PlayerEmployment> loadAllEmployments() throws SQLException {
		List<PlayerEmployment> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_employments")) {
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(mapEmployment(rs));
				}
			}
		}
		return list;
	}

	public Optional<PlayerEmployment> findEmploymentForPlayer(UUID playerUuid) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM player_employments WHERE player_uuid = ? LIMIT 1")) {
			ps.setString(1, playerUuid.toString());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? Optional.of(mapEmployment(rs)) : Optional.empty();
			}
		}
	}

	public void saveEmployment(PlayerEmployment employment) throws SQLException {
		if (employment.id() <= 0) {
			insertEmployment(employment);
		} else {
			updateEmployment(employment);
		}
	}

	public void deleteEmployment(long id) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_employments WHERE id = ?")) {
			ps.setLong(1, id);
			ps.executeUpdate();
		}
	}

	private void insertApplication(PlayerJobApplication app) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO player_job_applications(company_id, player_uuid, player_name, role_id,
					requested_salary_mg, message, status, applied_at)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, app.companyId());
			ps.setString(2, app.playerUuid().toString());
			ps.setString(3, app.playerName());
			ps.setString(4, app.roleId());
			ps.setLong(5, app.requestedSalaryMg());
			ps.setString(6, app.message());
			ps.setString(7, app.status().name());
			ps.setLong(8, app.appliedAt());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					app.setId(keys.getLong(1));
				}
			}
		}
	}

	private void updateApplication(PlayerJobApplication app) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"UPDATE player_job_applications SET status = ? WHERE id = ?")) {
			ps.setString(1, app.status().name());
			ps.setLong(2, app.id());
			ps.executeUpdate();
		}
	}

	private void insertEmployment(PlayerEmployment employment) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO player_employments(player_uuid, player_name, company_id, role_id, salary_mg,
					hired_at, last_paid_at)
				VALUES(?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, employment.playerUuid().toString());
			ps.setString(2, employment.playerName());
			ps.setInt(3, employment.companyId());
			ps.setString(4, employment.roleId());
			ps.setLong(5, employment.salaryMg());
			ps.setLong(6, employment.hiredAt());
			ps.setLong(7, employment.lastPaidAt());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					employment.setId(keys.getLong(1));
				}
			}
		}
	}

	private void updateEmployment(PlayerEmployment employment) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"UPDATE player_employments SET salary_mg = ?, last_paid_at = ?, player_name = ? WHERE id = ?")) {
			ps.setLong(1, employment.salaryMg());
			ps.setLong(2, employment.lastPaidAt());
			ps.setString(3, employment.playerName());
			ps.setLong(4, employment.id());
			ps.executeUpdate();
		}
	}

	private PlayerJobApplication mapApplication(ResultSet rs) throws SQLException {
		return new PlayerJobApplication(
				rs.getLong("id"),
				rs.getInt("company_id"),
				UUID.fromString(rs.getString("player_uuid")),
				rs.getString("player_name"),
				rs.getString("role_id"),
				rs.getLong("requested_salary_mg"),
				rs.getString("message"),
				ApplicationStatus.valueOf(rs.getString("status")),
				rs.getLong("applied_at")
		);
	}

	private PlayerEmployment mapEmployment(ResultSet rs) throws SQLException {
		return new PlayerEmployment(
				rs.getLong("id"),
				UUID.fromString(rs.getString("player_uuid")),
				rs.getString("player_name"),
				rs.getInt("company_id"),
				rs.getString("role_id"),
				rs.getLong("salary_mg"),
				rs.getLong("hired_at"),
				rs.getLong("last_paid_at")
		);
	}
}
