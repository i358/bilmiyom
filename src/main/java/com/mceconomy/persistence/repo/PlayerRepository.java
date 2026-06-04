package com.mceconomy.persistence.repo;



import com.mceconomy.config.EconomyConfig;

import com.mceconomy.job.JobType;

import com.mceconomy.player.PlayerEconomyProfile;



import java.sql.Connection;

import java.sql.PreparedStatement;

import java.sql.ResultSet;

import java.sql.SQLException;

import java.util.HashMap;

import java.util.Map;

import java.util.Optional;

import java.util.UUID;



public final class PlayerRepository {

	private final Connection connection;



	public PlayerRepository(Connection connection) {

		this.connection = connection;

	}



	public Map<UUID, PlayerEconomyProfile> loadAll() throws SQLException {

		Map<UUID, PlayerEconomyProfile> profiles = new HashMap<>();

		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM players");

			 ResultSet rs = ps.executeQuery()) {

			while (rs.next()) {

				profiles.put(UUID.fromString(rs.getString("uuid")), mapProfile(rs));

			}

		}

		return profiles;

	}



	public void save(PlayerEconomyProfile profile) throws SQLException {

		try (PreparedStatement ps = connection.prepareStatement("""

				INSERT INTO players(uuid, name, coin_balance, credit_score, job_type, last_tax_at,

					dirty_balance, account_frozen, blacklisted, bank_certified,

					central_bank_official, economy_minister, dashboard_password_hash, dashboard_password_salt)

				VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)

				ON CONFLICT(uuid) DO UPDATE SET

					name=excluded.name,

					coin_balance=excluded.coin_balance,

					credit_score=excluded.credit_score,

					job_type=excluded.job_type,

					last_tax_at=excluded.last_tax_at,

					dirty_balance=excluded.dirty_balance,

					account_frozen=excluded.account_frozen,

					blacklisted=excluded.blacklisted,

					bank_certified=excluded.bank_certified,

					central_bank_official=excluded.central_bank_official,

					economy_minister=excluded.economy_minister,

					dashboard_password_hash=excluded.dashboard_password_hash,

					dashboard_password_salt=excluded.dashboard_password_salt

				""")) {

			ps.setString(1, profile.uuid().toString());

			ps.setString(2, profile.name());

			ps.setLong(3, profile.wallet().balance());

			ps.setInt(4, profile.creditScore().score());

			ps.setString(5, profile.jobType() != null ? profile.jobType().name() : null);

			ps.setLong(6, profile.lastTaxAt());

			ps.setLong(7, profile.dirtyWallet().balance());

			ps.setInt(8, profile.accountFrozen() ? 1 : 0);

			ps.setInt(9, profile.blacklisted() ? 1 : 0);

			ps.setInt(10, profile.bankCertified() ? 1 : 0);

			ps.setInt(11, profile.centralBankOfficial() ? 1 : 0);

			ps.setInt(12, profile.economyMinister() ? 1 : 0);

			ps.setString(13, profile.dashboardPasswordHash());

			ps.setString(14, profile.dashboardPasswordSalt());

			ps.executeUpdate();

		}

	}



	public PlayerEconomyProfile createIfAbsent(UUID uuid, String name) throws SQLException {

		Optional<PlayerEconomyProfile> existing = find(uuid);

		if (existing.isPresent()) {

			return existing.get();

		}

		PlayerEconomyProfile profile = PlayerEconomyProfile.createNew(uuid, name, EconomyConfig.startingBalance());

		save(profile);

		return profile;

	}



	public Optional<PlayerEconomyProfile> find(UUID uuid) throws SQLException {

		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {

			ps.setString(1, uuid.toString());

			try (ResultSet rs = ps.executeQuery()) {

				if (rs.next()) {

					return Optional.of(mapProfile(rs));

				}

			}

		}

		return Optional.empty();

	}



	public Optional<PlayerEconomyProfile> findByNameIgnoreCase(String name) throws SQLException {

		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM players WHERE LOWER(name) = LOWER(?)")) {

			ps.setString(1, name);

			try (ResultSet rs = ps.executeQuery()) {

				if (rs.next()) {

					return Optional.of(mapProfile(rs));

				}

			}

		}

		return Optional.empty();

	}



	private PlayerEconomyProfile mapProfile(ResultSet rs) throws SQLException {

		UUID uuid = UUID.fromString(rs.getString("uuid"));

		return new PlayerEconomyProfile(

				uuid,

				rs.getString("name"),

				rs.getLong("coin_balance"),

				getLongColumn(rs, "dirty_balance", 0),

				rs.getInt("credit_score"),

				JobType.fromString(rs.getString("job_type")),

				rs.getLong("last_tax_at"),

				getIntColumn(rs, "account_frozen", 0) == 1,

				getIntColumn(rs, "blacklisted", 0) == 1,

				getIntColumn(rs, "bank_certified", 0) == 1,

				getIntColumn(rs, "central_bank_official", 0) == 1,

				getIntColumn(rs, "economy_minister", 0) == 1,

				getStringColumn(rs, "dashboard_password_hash", null),

				getStringColumn(rs, "dashboard_password_salt", null)

		);

	}



	private static long getLongColumn(ResultSet rs, String column, long defaultValue) {

		try {

			return rs.getLong(column);

		} catch (SQLException e) {

			return defaultValue;

		}

	}



	private static int getIntColumn(ResultSet rs, String column, int defaultValue) {

		try {

			return rs.getInt(column);

		} catch (SQLException e) {

			return defaultValue;

		}

	}



	private static String getStringColumn(ResultSet rs, String column, String defaultValue) {

		try {

			return rs.getString(column);

		} catch (SQLException e) {

			return defaultValue;

		}

	}

}

