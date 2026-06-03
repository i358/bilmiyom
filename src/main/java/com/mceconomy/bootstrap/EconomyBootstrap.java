package com.mceconomy.bootstrap;

import com.mceconomy.McEconomyMod;
import com.mceconomy.company.CompanyManager;
import com.mceconomy.economy.EconomyManager;
import com.mceconomy.exchange.ExchangeService;
import com.mceconomy.exchange.ForeignInvestorMarketService;
import com.mceconomy.privatebank.PrivateBankService;

import java.sql.SQLException;
import java.util.UUID;

/** Ilk kurulumda varsayilan borsa hisseleri ve kamu ozel bankalari. */
public final class EconomyBootstrap {
	public static final UUID SYSTEM_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private EconomyBootstrap() {
	}

	public static void seed(EconomyManager manager) {
		try {
			seedCompanies(manager.companyManager());
			seedPrivateBanks(manager.privateBankService());
			seedDefaultTokens(manager.exchangeService());
			manager.foreignInvestorMarket().bootstrapInvestors(manager);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Ekonomi bootstrap basarisiz", e);
		}
	}

	private static void seedDefaultTokens(ExchangeService exchange) throws SQLException {
		if (!exchange.allTokens().isEmpty()) {
			return;
		}
		createToken(exchange, "MCCOIN", "McEconomy Coin", 120_000, 10_000);
		createToken(exchange, "ALTGC", "Altin Gram Coin", 80_000, 25_000);
		createToken(exchange, "TECX", "Teknoloji Endeksi", 200_000, 15_000);
		createToken(exchange, "AGRO", "Tarim Token", 60_000, 8_000);
		McEconomyMod.LOGGER.info("Varsayilan borsa coinleri olusturuldu.");
	}

	private static void createToken(ExchangeService exchange, String symbol, String name, int supply, long priceMg)
			throws SQLException {
		if (!exchange.createPublicToken(symbol, name, priceMg, supply)) {
			McEconomyMod.LOGGER.warn("Coin olusturulamadi: {}", symbol);
		}
	}

	private static void seedCompanies(CompanyManager companies) throws SQLException {
		if (!companies.allCompanies().isEmpty()) {
			return;
		}
		createListed(companies, "McEconomy Holding", "MCE", 500_000);
		createListed(companies, "Altin Maden A.S.", "ALTIN", 250_000);
		createListed(companies, "Tarim Kooperatifi", "TARIM", 180_000);
		createListed(companies, "Teknoloji Yatirim", "TEKNO", 320_000);
		McEconomyMod.LOGGER.info("Varsayilan borsa sirketleri olusturuldu.");
	}

	private static void createListed(CompanyManager companies, String name, String ticker, long treasury)
			throws SQLException {
		if (companies.createPublicListedCompany(name, ticker, SYSTEM_OWNER, treasury)) {
			return;
		}
		McEconomyMod.LOGGER.warn("Sirket olusturulamadi: {}", name);
	}

	private static void seedPrivateBanks(PrivateBankService privateBanks) throws SQLException {
		if (!privateBanks.allBanks().isEmpty()) {
			return;
		}
		privateBanks.createPublicBank("Vakif Ozel Banka", 0.028, 2_000_000);
		privateBanks.createPublicBank("Ziraat Finans", 0.032, 1_500_000);
		privateBanks.createPublicBank("Kara El Finans", 0.045, 800_000);
		McEconomyMod.LOGGER.info("Varsayilan ozel bankalar olusturuldu.");
	}
}
