package com.mceconomy.persistence.repo;

import com.mceconomy.municipal.MayorService.MayorState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MunicipalRepository {
	private final Connection connection;

	public MunicipalRepository(Connection connection) {
		this.connection = connection;
	}

	public MayorState loadState() throws SQLException {
		try (Statement stmt = connection.createStatement();
			 ResultSet rs = stmt.executeQuery("SELECT * FROM municipal_state WHERE id = 1")) {
			if (rs.next()) {
				String mayorUuid = rs.getString("mayor_uuid");
				return new MayorState(
						mayorUuid != null ? UUID.fromString(mayorUuid) : null,
						rs.getString("mayor_name"),
						rs.getLong("term_end_ms"),
						rs.getLong("election_start_ms"));
			}
		}
		return new MayorState(null, null, 0, 0);
	}

	public void saveState(MayorState state) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO municipal_state(id, mayor_uuid, mayor_name, term_end_ms, election_start_ms)
				VALUES(1, ?, ?, ?, ?)
				ON CONFLICT(id) DO UPDATE SET
					mayor_uuid=excluded.mayor_uuid,
					mayor_name=excluded.mayor_name,
					term_end_ms=excluded.term_end_ms,
					election_start_ms=excluded.election_start_ms
				""")) {
			if (state.mayorUuid() != null) {
				ps.setString(1, state.mayorUuid().toString());
			} else {
				ps.setNull(1, java.sql.Types.VARCHAR);
			}
			ps.setString(2, state.mayorName());
			ps.setLong(3, state.termEndMs());
			ps.setLong(4, state.electionStartMs());
			ps.executeUpdate();
		}
	}

	public Map<UUID, UUID> loadVotes(long termId) throws SQLException {
		Map<UUID, UUID> map = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT voter_uuid, candidate_uuid FROM municipal_votes WHERE term_id = ?")) {
			ps.setLong(1, termId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					map.put(UUID.fromString(rs.getString("voter_uuid")),
							UUID.fromString(rs.getString("candidate_uuid")));
				}
			}
		}
		return map;
	}

	public Map<UUID, String> loadCandidates(long termId) throws SQLException {
		Map<UUID, String> map = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT candidate_uuid, candidate_name FROM municipal_candidates WHERE term_id = ?")) {
			ps.setLong(1, termId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					map.put(UUID.fromString(rs.getString("candidate_uuid")), rs.getString("candidate_name"));
				}
			}
		}
		return map;
	}

	public void saveVote(long termId, UUID voter, UUID candidate) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO municipal_votes(term_id, voter_uuid, candidate_uuid) VALUES(?, ?, ?)
				""")) {
			ps.setLong(1, termId);
			ps.setString(2, voter.toString());
			ps.setString(3, candidate.toString());
			ps.executeUpdate();
		}
	}

	public void saveCandidate(long termId, UUID candidate, String name) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO municipal_candidates(term_id, candidate_uuid, candidate_name) VALUES(?, ?, ?)
				ON CONFLICT(term_id, candidate_uuid) DO UPDATE SET candidate_name=excluded.candidate_name
				""")) {
			ps.setLong(1, termId);
			ps.setString(2, candidate.toString());
			ps.setString(3, name);
			ps.executeUpdate();
		}
	}

	public void clearElectionData() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("DELETE FROM municipal_votes");
			stmt.execute("DELETE FROM municipal_candidates");
		}
	}
}
