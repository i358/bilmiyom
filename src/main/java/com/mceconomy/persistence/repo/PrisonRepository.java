package com.mceconomy.persistence.repo;

import com.mceconomy.justice.PrisonSentence;

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

public final class PrisonRepository {
	private final Connection connection;

	public PrisonRepository(Connection connection) {
		this.connection = connection;
	}

	public long insert(PrisonSentence sentence) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO prison_sentences(player_uuid, player_name, reason, sentenced_by,
					jailed_at, release_at, active, return_x, return_y, return_z, return_dimension, cell_index)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			bindInsert(ps, sentence);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					return keys.getLong(1);
				}
			}
		}
		return 0;
	}

	public void update(PrisonSentence sentence) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE prison_sentences SET active=?, release_at=? WHERE id=?
				""")) {
			ps.setInt(1, sentence.active() ? 1 : 0);
			ps.setLong(2, sentence.releaseAt());
			ps.setLong(3, sentence.id());
			ps.executeUpdate();
		}
	}

	public List<PrisonSentence> loadActive() throws SQLException {
		List<PrisonSentence> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM prison_sentences WHERE active = 1");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(map(rs));
			}
		}
		return list;
	}

	public Optional<PrisonSentence> findActiveForPlayer(UUID uuid) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM prison_sentences WHERE player_uuid = ? AND active = 1 ORDER BY id DESC LIMIT 1")) {
			ps.setString(1, uuid.toString());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Optional.of(map(rs));
				}
			}
		}
		return Optional.empty();
	}

	private void bindInsert(PreparedStatement ps, PrisonSentence s) throws SQLException {
		ps.setString(1, s.playerUuid().toString());
		ps.setString(2, s.playerName());
		ps.setString(3, s.reason());
		ps.setString(4, s.sentencedBy());
		ps.setLong(5, s.jailedAt());
		ps.setLong(6, s.releaseAt());
		ps.setInt(7, s.active() ? 1 : 0);
		if (s.returnX() != null) {
			ps.setDouble(8, s.returnX());
			ps.setDouble(9, s.returnY());
			ps.setDouble(10, s.returnZ());
			ps.setString(11, s.returnDimension());
		} else {
			ps.setNull(8, Types.REAL);
			ps.setNull(9, Types.REAL);
			ps.setNull(10, Types.REAL);
			ps.setNull(11, Types.VARCHAR);
		}
		ps.setInt(12, s.cellIndex());
	}

	private PrisonSentence map(ResultSet rs) throws SQLException {
		double rx = rs.getDouble("return_x");
		boolean hasReturn = !rs.wasNull();
		return new PrisonSentence(
				rs.getLong("id"),
				UUID.fromString(rs.getString("player_uuid")),
				rs.getString("player_name"),
				rs.getString("reason"),
				rs.getString("sentenced_by"),
				rs.getLong("jailed_at"),
				rs.getLong("release_at"),
				rs.getInt("active") == 1,
				hasReturn ? rx : null,
				hasReturn ? rs.getDouble("return_y") : null,
				hasReturn ? rs.getDouble("return_z") : null,
				hasReturn ? rs.getString("return_dimension") : null,
				rs.getInt("cell_index")
		);
	}
}
