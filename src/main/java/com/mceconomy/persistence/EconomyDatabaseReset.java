package com.mceconomy.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Mod ekonomi veritabanini sifirlar; schema_version korunur. */
public final class EconomyDatabaseReset {
	private static final String[] TABLES = {
			"security_camera_logs",
			"leverage_positions",
			"custom_blackmarket",
			"player_blackmarket",
			"prison_sentences",
			"citizen_reports",
			"player_employments",
			"player_job_applications",
			"company_vaults",
			"economy_bulletins",
			"depot_ledger",
			"national_reserve",
			"municipal_votes",
			"municipal_candidates",
			"municipal_state",
			"insurance_policies",
			"company_stash",
			"tip_rewards",
			"trade_disputes",
			"player_trades",
			"guild_members",
			"guilds",
			"salary_payments",
			"player_vaults",
			"company_employees",
			"job_applications",
			"price_history",
			"appeals",
			"masak_alerts",
			"private_bank_deposits",
			"private_banks",
			"token_holdings",
			"exchange_tokens",
			"active_quests",
			"shares",
			"companies",
			"loans",
			"transactions",
			"bank_accounts",
			"market_state",
			"players",
			"central_bank",
			"player_properties",
			"player_vehicles",
			"company_buildings",
			"economy_minister_applications",
			"economy_decrees",
	};

	private EconomyDatabaseReset() {
	}

	public static void wipeAllEconomyData(Connection connection) throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("PRAGMA foreign_keys=OFF");
			for (String table : TABLES) {
				stmt.execute("DELETE FROM " + table);
			}
			stmt.execute("INSERT OR IGNORE INTO central_bank(id) VALUES(1)");
			stmt.execute("PRAGMA foreign_keys=ON");
		}
	}
}
