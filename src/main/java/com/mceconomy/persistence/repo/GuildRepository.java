package com.mceconomy.persistence.repo;

import com.mceconomy.guild.Guild;
import com.mceconomy.guild.GuildRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class GuildRepository {
	private final Connection connection;

	public GuildRepository(Connection connection) {
		this.connection = connection;
	}

	public void saveGuild(Guild guild) throws SQLException {
		if (guild.id() <= 0) {
			insertGuild(guild);
		} else {
			updateGuild(guild);
		}
	}

	public Optional<Guild> findByName(String name) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM guilds WHERE lower(name) = lower(?)")) {
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? Optional.of(mapGuild(rs)) : Optional.empty();
			}
		}
	}

	public Optional<Guild> findByMember(UUID uuid) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT g.* FROM guilds g
				JOIN guild_members m ON m.guild_id = g.id
				WHERE m.player_uuid = ?
				""")) {
			ps.setString(1, uuid.toString());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? Optional.of(mapGuild(rs)) : Optional.empty();
			}
		}
	}

	public List<Guild> loadAll() throws SQLException {
		List<Guild> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM guilds ORDER BY name")) {
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(mapGuild(rs));
				}
			}
		}
		return list;
	}

	public int memberCount(int guildId) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM guild_members WHERE guild_id = ?")) {
			ps.setInt(1, guildId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}

	public void addMember(int guildId, UUID uuid, String name, GuildRole role) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO guild_members(guild_id, player_uuid, player_name, role, joined_at)
				VALUES(?, ?, ?, ?, ?)
				""")) {
			ps.setInt(1, guildId);
			ps.setString(2, uuid.toString());
			ps.setString(3, name);
			ps.setString(4, role.name());
			ps.setLong(5, System.currentTimeMillis());
			ps.executeUpdate();
		}
	}

	public void removeMember(UUID uuid) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("DELETE FROM guild_members WHERE player_uuid = ?")) {
			ps.setString(1, uuid.toString());
			ps.executeUpdate();
		}
	}

	public List<MemberRow> members(int guildId) throws SQLException {
		List<MemberRow> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT player_uuid, player_name, role FROM guild_members WHERE guild_id = ?")) {
			ps.setInt(1, guildId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(new MemberRow(
							UUID.fromString(rs.getString("player_uuid")),
							rs.getString("player_name"),
							GuildRole.valueOf(rs.getString("role"))));
				}
			}
		}
		return list;
	}

	private void insertGuild(Guild guild) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO guilds(name, leader_uuid, treasury_mg, strike_active, strike_until, bargain_message, created_at)
				VALUES(?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, guild.name());
			ps.setString(2, guild.leaderUuid().toString());
			ps.setLong(3, guild.treasuryMg());
			ps.setInt(4, guild.strikeActive() ? 1 : 0);
			ps.setLong(5, guild.strikeUntil());
			ps.setString(6, guild.bargainMessage());
			ps.setLong(7, guild.createdAt());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					guild.setId(keys.getInt(1));
				}
			}
		}
	}

	private void updateGuild(Guild guild) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE guilds SET treasury_mg=?, strike_active=?, strike_until=?, bargain_message=? WHERE id=?
				""")) {
			ps.setLong(1, guild.treasuryMg());
			ps.setInt(2, guild.strikeActive() ? 1 : 0);
			ps.setLong(3, guild.strikeUntil());
			ps.setString(4, guild.bargainMessage());
			ps.setInt(5, guild.id());
			ps.executeUpdate();
		}
	}

	private Guild mapGuild(ResultSet rs) throws SQLException {
		return new Guild(
				rs.getInt("id"),
				rs.getString("name"),
				UUID.fromString(rs.getString("leader_uuid")),
				rs.getLong("treasury_mg"),
				rs.getInt("strike_active") == 1,
				rs.getLong("strike_until"),
				rs.getString("bargain_message"),
				rs.getLong("created_at")
		);
	}

	public record MemberRow(UUID playerUuid, String playerName, GuildRole role) {
	}
}
