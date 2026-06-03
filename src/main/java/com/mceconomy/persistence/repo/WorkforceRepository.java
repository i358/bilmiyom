package com.mceconomy.persistence.repo;

import com.mceconomy.company.ApplicationStatus;
import com.mceconomy.company.JobApplication;
import com.mceconomy.company.NpcEmployee;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class WorkforceRepository {
	private final Connection connection;

	public WorkforceRepository(Connection connection) {
		this.connection = connection;
	}

	public List<JobApplication> loadPendingApplications() throws SQLException {
		List<JobApplication> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM job_applications WHERE status = 'PENDING' ORDER BY applied_at")) {
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(mapApplication(rs));
				}
			}
		}
		return list;
	}

	public List<JobApplication> loadPendingForCompany(int companyId) throws SQLException {
		List<JobApplication> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM job_applications WHERE company_id = ? AND status = 'PENDING' ORDER BY applied_at")) {
			ps.setInt(1, companyId);
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
				"SELECT COUNT(*) FROM job_applications WHERE company_id = ? AND status = 'PENDING'")) {
			ps.setInt(1, companyId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}

	public void saveApplication(JobApplication app) throws SQLException {
		if (app.id() <= 0) {
			insertApplication(app);
		} else {
			updateApplication(app);
		}
	}

	public List<NpcEmployee> loadAllEmployees() throws SQLException {
		List<NpcEmployee> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM company_employees")) {
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(mapEmployee(rs));
				}
			}
		}
		return list;
	}

	public List<NpcEmployee> loadEmployeesForCompany(int companyId) throws SQLException {
		List<NpcEmployee> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM company_employees WHERE company_id = ?")) {
			ps.setInt(1, companyId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(mapEmployee(rs));
				}
			}
		}
		return list;
	}

	public void saveEmployee(NpcEmployee employee) throws SQLException {
		if (employee.id() <= 0) {
			insertEmployee(employee);
		} else {
			updateEmployee(employee);
		}
	}

	public void deleteEmployee(long id) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("DELETE FROM company_employees WHERE id = ?")) {
			ps.setLong(1, id);
			ps.executeUpdate();
		}
	}

	private void insertApplication(JobApplication app) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO job_applications(company_id, npc_name, role_id, requested_salary_mg, message, status, applied_at, entity_uuid)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, app.companyId());
			ps.setString(2, app.npcName());
			ps.setString(3, app.roleId());
			ps.setLong(4, app.requestedSalaryMg());
			ps.setString(5, app.message());
			ps.setString(6, app.status().name());
			ps.setLong(7, app.appliedAt());
			ps.setString(8, app.entityUuid());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					app.setId(keys.getLong(1));
				}
			}
		}
	}

	private void updateApplication(JobApplication app) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE job_applications SET status = ?, entity_uuid = ? WHERE id = ?
				""")) {
			ps.setString(1, app.status().name());
			ps.setString(2, app.entityUuid());
			ps.setLong(3, app.id());
			ps.executeUpdate();
		}
	}

	private void insertEmployee(NpcEmployee employee) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO company_employees(company_id, npc_name, role_id, salary_mg, hired_at, last_paid_at, total_produced_mg)
				VALUES(?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, employee.companyId());
			ps.setString(2, employee.npcName());
			ps.setString(3, employee.roleId());
			ps.setLong(4, employee.salaryMg());
			ps.setLong(5, employee.hiredAt());
			ps.setLong(6, employee.lastPaidAt());
			ps.setLong(7, employee.totalProducedMg());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					employee.setId(keys.getLong(1));
				}
			}
		}
	}

	private void updateEmployee(NpcEmployee employee) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE company_employees SET salary_mg = ?, last_paid_at = ?, total_produced_mg = ? WHERE id = ?
				""")) {
			ps.setLong(1, employee.salaryMg());
			ps.setLong(2, employee.lastPaidAt());
			ps.setLong(3, employee.totalProducedMg());
			ps.setLong(4, employee.id());
			ps.executeUpdate();
		}
	}

	private JobApplication mapApplication(ResultSet rs) throws SQLException {
		return new JobApplication(
				rs.getLong("id"),
				rs.getInt("company_id"),
				rs.getString("npc_name"),
				rs.getString("role_id"),
				rs.getLong("requested_salary_mg"),
				rs.getString("message"),
				ApplicationStatus.valueOf(rs.getString("status")),
				rs.getLong("applied_at"),
				rs.getString("entity_uuid")
		);
	}

	private NpcEmployee mapEmployee(ResultSet rs) throws SQLException {
		return new NpcEmployee(
				rs.getLong("id"),
				rs.getInt("company_id"),
				rs.getString("npc_name"),
				rs.getString("role_id"),
				rs.getLong("salary_mg"),
				rs.getLong("hired_at"),
				rs.getLong("last_paid_at"),
				rs.getLong("total_produced_mg")
		);
	}
}
