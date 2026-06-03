package com.mceconomy.persistence.repo;

import com.mceconomy.justice.CitizenReport;
import com.mceconomy.justice.ReportStatus;
import com.mceconomy.justice.ReportType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ReportRepository {
	private final Connection connection;

	public ReportRepository(Connection connection) {
		this.connection = connection;
	}

	public long insert(CitizenReport report) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO citizen_reports(type, reporter_uuid, reporter_name, target_uuid, target_name,
					category, subject, message, status, admin_note, prison_sentence_id, created_at, resolved_at)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			bind(ps, report);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					return keys.getLong(1);
				}
			}
		}
		return 0;
	}

	public void update(CitizenReport report) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE citizen_reports SET status=?, admin_note=?, prison_sentence_id=?, resolved_at=? WHERE id=?
				""")) {
			ps.setString(1, report.status().name());
			ps.setString(2, report.adminNote());
			if (report.prisonSentenceId() != null) {
				ps.setLong(3, report.prisonSentenceId());
			} else {
				ps.setNull(3, Types.INTEGER);
			}
			ps.setLong(4, report.resolvedAt());
			ps.setLong(5, report.id());
			ps.executeUpdate();
		}
	}

	public List<CitizenReport> loadOpen() throws SQLException {
		List<CitizenReport> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT * FROM citizen_reports WHERE status IN ('OPEN','INVESTIGATING') ORDER BY created_at ASC
				""");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(map(rs));
			}
		}
		return list;
	}

	public boolean hasTipReward(long reportId) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM tip_rewards WHERE report_id = ?")) {
			ps.setLong(1, reportId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	public void recordTipReward(long reportId, UUID reporterUuid, long amountMg) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO tip_rewards(report_id, reporter_uuid, amount_mg, paid_at) VALUES(?, ?, ?, ?)
				""")) {
			ps.setLong(1, reportId);
			ps.setString(2, reporterUuid.toString());
			ps.setLong(3, amountMg);
			ps.setLong(4, System.currentTimeMillis());
			ps.executeUpdate();
		}
	}

	public List<CitizenReport> loadForReporter(UUID uuid) throws SQLException {
		List<CitizenReport> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT * FROM citizen_reports WHERE reporter_uuid = ? ORDER BY created_at DESC LIMIT 15
				""")) {
			ps.setString(1, uuid.toString());
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(map(rs));
				}
			}
		}
		return list;
	}

	public List<CitizenReport> loadOpenForTarget(UUID targetUuid) throws SQLException {
		List<CitizenReport> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT * FROM citizen_reports
				WHERE target_uuid = ? AND status IN ('OPEN','INVESTIGATING')
				ORDER BY created_at ASC
				""")) {
			ps.setString(1, targetUuid.toString());
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(map(rs));
				}
			}
		}
		return list;
	}

	public Optional<CitizenReport> findById(long id) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM citizen_reports WHERE id = ?")) {
			ps.setLong(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Optional.of(map(rs));
				}
			}
		}
		return Optional.empty();
	}

	private void bind(PreparedStatement ps, CitizenReport report) throws SQLException {
		ps.setString(1, report.type().name());
		ps.setString(2, report.reporterUuid().toString());
		ps.setString(3, report.reporterName());
		if (report.targetUuid() != null) {
			ps.setString(4, report.targetUuid().toString());
		} else {
			ps.setNull(4, Types.VARCHAR);
		}
		ps.setString(5, report.targetName());
		ps.setString(6, report.category());
		ps.setString(7, report.subject());
		ps.setString(8, report.message());
		ps.setString(9, report.status().name());
		ps.setString(10, report.adminNote());
		if (report.prisonSentenceId() != null) {
			ps.setLong(11, report.prisonSentenceId());
		} else {
			ps.setNull(11, Types.INTEGER);
		}
		ps.setLong(12, report.createdAt());
		ps.setLong(13, report.resolvedAt());
	}

	private CitizenReport map(ResultSet rs) throws SQLException {
		String targetUuidStr = rs.getString("target_uuid");
		Long sentenceId = rs.getLong("prison_sentence_id");
		return new CitizenReport(
				rs.getLong("id"),
				ReportType.valueOf(rs.getString("type")),
				UUID.fromString(rs.getString("reporter_uuid")),
				rs.getString("reporter_name"),
				targetUuidStr != null ? UUID.fromString(targetUuidStr) : null,
				rs.getString("target_name"),
				rs.getString("category"),
				rs.getString("subject"),
				rs.getString("message"),
				ReportStatus.valueOf(rs.getString("status")),
				rs.getString("admin_note"),
				rs.wasNull() ? null : sentenceId,
				rs.getLong("created_at"),
				rs.getLong("resolved_at")
		);
	}
}
