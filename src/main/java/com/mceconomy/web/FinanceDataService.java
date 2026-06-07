package com.mceconomy.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mceconomy.McEconomyMod;
import com.mceconomy.company.Company;
import com.mceconomy.economy.EconomyEventCategory;
import com.mceconomy.economy.EconomyEventScope;
import com.mceconomy.economy.EconomyEventService;
import com.mceconomy.economy.EconomyManager;
import com.mceconomy.economy.GoldStandard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FinanceDataService {
	private static final int DEFAULT_LIMIT = 100;

	private FinanceDataService() {
	}

	public static JsonObject buildPersonalCategories(UUID playerUuid) {
		EconomyEventService events = service();
		JsonObject root = new JsonObject();
		JsonArray cats = new JsonArray();
		Map<String, Integer> counts = events != null ? events.countPersonalCategories(playerUuid) : Map.of();
		for (EconomyEventCategory cat : personalCategories()) {
			JsonObject row = new JsonObject();
			row.addProperty("category", cat.name());
			row.addProperty("label", categoryLabel(cat));
			row.addProperty("count", counts.getOrDefault(cat.name(), 0));
			cats.add(row);
		}
		root.add("categories", cats);
		return root;
	}

	public static JsonObject buildPersonalEvents(UUID playerUuid, String category, int limit) {
		EconomyEventService events = service();
		EconomyEventCategory cat = parseCategory(category);
		int lim = limit > 0 ? Math.min(limit, 100) : DEFAULT_LIMIT;
		List<Map<String, Object>> rows = events != null
				? events.loadPersonalEvents(playerUuid, cat, lim) : List.of();
		JsonObject root = new JsonObject();
		root.add("events", toEventArray(rows));
		root.addProperty("category", cat != null ? cat.name() : "ALL");
		root.addProperty("limit", lim);
		return root;
	}

	public static JsonObject buildPersonalCharts(UUID playerUuid, int days) {
		return buildCharts(EconomyEventScope.PERSONAL, playerUuid, null, days);
	}

	public static JsonObject buildCompanyList(UUID playerUuid) {
		JsonObject root = new JsonObject();
		JsonArray companies = new JsonArray();
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null) {
			for (Company company : manager.companyManager().allCompanies()) {
				if (company.ownerUuid().equals(playerUuid)) {
					JsonObject row = new JsonObject();
					row.addProperty("id", company.id());
					row.addProperty("name", company.name());
					row.addProperty("ticker", company.ticker());
					companies.add(row);
				}
			}
		}
		root.add("companies", companies);
		return root;
	}

	public static JsonObject buildCompanyEvents(UUID playerUuid, String companyName, String category, int limit) {
		var manager = McEconomyMod.getEconomyManager();
		JsonObject root = new JsonObject();
		if (manager == null) {
			root.addProperty("error", "economy_unavailable");
			return root;
		}
		Company company = manager.companyManager().find(companyName).orElse(null);
		if (company == null || !company.ownerUuid().equals(playerUuid)) {
			root.addProperty("error", "company_not_found");
			return root;
		}
		EconomyEventCategory cat = parseCompanyCategory(category);
		int lim = limit > 0 ? Math.min(limit, 100) : DEFAULT_LIMIT;
		List<Map<String, Object>> rows = manager.economyEventService()
				.loadCompanyEvents(company.id(), cat, lim);
		root.add("events", toEventArray(rows));
		root.addProperty("company", company.name());
		root.addProperty("category", cat != null ? cat.name() : "ALL");
		return root;
	}

	public static JsonObject buildCompanyCharts(UUID playerUuid, String companyName, int days) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null) {
			JsonObject err = new JsonObject();
			err.addProperty("error", "economy_unavailable");
			return err;
		}
		Company company = manager.companyManager().find(companyName).orElse(null);
		if (company == null || !company.ownerUuid().equals(playerUuid)) {
			JsonObject err = new JsonObject();
			err.addProperty("error", "company_not_found");
			return err;
		}
		return buildCharts(EconomyEventScope.COMPANY, company.ownerUuid(), company.id(), days);
	}

	public static JsonObject buildMunicipalEvents(String category, int limit) {
		EconomyEventCategory cat = parseMunicipalCategory(category);
		int lim = limit > 0 ? Math.min(limit, 100) : DEFAULT_LIMIT;
		List<Map<String, Object>> rows = service() != null ? service().loadMunicipalEvents(cat, lim) : List.of();
		JsonObject root = new JsonObject();
		root.add("events", toEventArray(rows));
		root.addProperty("category", cat != null ? cat.name() : "ALL");
		return root;
	}

	public static JsonObject buildMunicipalCharts(int days) {
		JsonObject root = buildCharts(EconomyEventScope.MUNICIPAL, null, null, days);
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.priceHistoryService() != null) {
			try {
				var history = manager.priceHistoryService().repository()
						.loadRecent("MACRO", "municipal_budget", 60);
				JsonArray budget = new JsonArray();
				for (var point : history) {
					JsonObject row = new JsonObject();
					row.addProperty("recordedAt", (Long) point.get("recordedAt"));
					row.addProperty("priceMg", (Long) point.get("priceMg"));
					budget.add(row);
				}
				root.add("budgetHistory", budget);
				root.addProperty("currentBudgetMg", manager.centralBank().getMunicipalBudgetMg());
				root.addProperty("currentBudget", GoldStandard.formatMilligrams(
						manager.centralBank().getMunicipalBudgetMg()));
			} catch (Exception ignored) {
			}
		}
		return root;
	}

	private static JsonObject buildCharts(EconomyEventScope scope, UUID ownerUuid, Integer companyId, int days) {
		int d = days > 0 ? Math.min(days, 90) : 30;
		EconomyEventService events = service();
		JsonObject root = new JsonObject();
		root.addProperty("days", d);
		if (events == null) {
			root.add("daily", new JsonArray());
			root.add("byCategory", new JsonArray());
			return root;
		}
		List<Map<String, Object>> daily = events.aggregateByDay(scope, ownerUuid, companyId, d);
		List<Map<String, Object>> byCat = events.aggregateByCategory(scope, ownerUuid, companyId, d);

		Map<Long, long[]> dayMap = new LinkedHashMap<>();
		for (Map<String, Object> row : daily) {
			long bucket = (Long) row.get("dayBucket");
			long[] totals = dayMap.computeIfAbsent(bucket, k -> new long[2]);
			String dir = (String) row.get("direction");
			long total = (Long) row.get("totalMg");
			if ("IN".equals(dir)) {
				totals[0] += total;
			} else {
				totals[1] += total;
			}
		}
		JsonArray dailyArr = new JsonArray();
		long totalIn = 0;
		long totalOut = 0;
		for (Map.Entry<Long, long[]> e : dayMap.entrySet()) {
			JsonObject row = new JsonObject();
			long dayMs = e.getKey() * 86_400_000L;
			row.addProperty("dayMs", dayMs);
			row.addProperty("inMg", e.getValue()[0]);
			row.addProperty("outMg", e.getValue()[1]);
			row.addProperty("in", GoldStandard.formatMilligrams(e.getValue()[0]));
			row.addProperty("out", GoldStandard.formatMilligrams(e.getValue()[1]));
			dailyArr.add(row);
			totalIn += e.getValue()[0];
			totalOut += e.getValue()[1];
		}
		root.add("daily", dailyArr);
		root.addProperty("totalInMg", totalIn);
		root.addProperty("totalOutMg", totalOut);
		root.addProperty("netMg", totalIn - totalOut);
		root.addProperty("totalIn", GoldStandard.formatMilligrams(totalIn));
		root.addProperty("totalOut", GoldStandard.formatMilligrams(totalOut));
		root.addProperty("net", GoldStandard.formatMilligrams(totalIn - totalOut));

		JsonArray catArr = new JsonArray();
		for (Map<String, Object> row : byCat) {
			JsonObject c = new JsonObject();
			c.addProperty("category", (String) row.get("category"));
			c.addProperty("direction", (String) row.get("direction"));
			c.addProperty("totalMg", (Long) row.get("totalMg"));
			c.addProperty("total", GoldStandard.formatMilligrams((Long) row.get("totalMg")));
			catArr.add(c);
		}
		root.add("byCategory", catArr);
		return root;
	}

	private static JsonArray toEventArray(List<Map<String, Object>> rows) {
		JsonArray arr = new JsonArray();
		for (Map<String, Object> row : rows) {
			JsonObject e = new JsonObject();
			Object id = row.get("id");
			if (id instanceof Long l) {
				e.addProperty("id", l);
			} else if (id != null) {
				e.addProperty("id", id.toString());
			}
			e.addProperty("timestamp", (Long) row.get("timestamp"));
			e.addProperty("category", (String) row.get("category"));
			e.addProperty("direction", (String) row.get("direction"));
			e.addProperty("amountMg", (Long) row.get("amountMg"));
			e.addProperty("amount", GoldStandard.formatMilligrams((Long) row.get("amountMg")));
			if (row.get("counterpartyName") != null) {
				e.addProperty("counterpartyName", (String) row.get("counterpartyName"));
			}
			if (row.get("counterpartyUuid") != null) {
				e.addProperty("counterpartyUuid", (String) row.get("counterpartyUuid"));
			}
			if (row.get("assetSymbol") != null) {
				e.addProperty("assetSymbol", (String) row.get("assetSymbol"));
			}
			e.addProperty("quantity", ((Number) row.get("quantity")).intValue());
			if (row.get("source") != null) {
				e.addProperty("source", (String) row.get("source"));
			}
			e.addProperty("description", (String) row.get("description"));
			if (Boolean.TRUE.equals(row.get("legacy"))) {
				e.addProperty("legacy", true);
			}
			arr.add(e);
		}
		return arr;
	}

	private static EconomyEventService service() {
		EconomyManager manager = McEconomyMod.getEconomyManager();
		return manager != null ? manager.economyEventService() : null;
	}

	private static EconomyEventCategory parseCategory(String raw) {
		if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
			return null;
		}
		try {
			return EconomyEventCategory.valueOf(raw.toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static EconomyEventCategory parseCompanyCategory(String raw) {
		EconomyEventCategory cat = parseCategory(raw);
		if (cat == null) {
			return null;
		}
		return switch (cat) {
			case TREASURY_IN, TREASURY_OUT, DIVIDEND, TAX_FEE, OTHER -> cat;
			default -> null;
		};
	}

	private static EconomyEventCategory parseMunicipalCategory(String raw) {
		EconomyEventCategory cat = parseCategory(raw);
		if (cat == null) {
			return null;
		}
		return switch (cat) {
			case TAX_IN, SPEND_OUT, SUBSIDY, OTHER -> cat;
			default -> null;
		};
	}

	private static List<EconomyEventCategory> personalCategories() {
		List<EconomyEventCategory> list = new ArrayList<>();
		for (EconomyEventCategory c : EconomyEventCategory.values()) {
			if (c == EconomyEventCategory.TREASURY_IN || c == EconomyEventCategory.TREASURY_OUT
					|| c == EconomyEventCategory.DIVIDEND || c == EconomyEventCategory.TAX_IN
					|| c == EconomyEventCategory.SPEND_OUT || c == EconomyEventCategory.SUBSIDY) {
				continue;
			}
			list.add(c);
		}
		return list;
	}

	public static String categoryLabel(EconomyEventCategory cat) {
		return switch (cat) {
			case WALLET -> "Cüzdan";
			case MARKET -> "Market";
			case EXCHANGE -> "Borsa (coin)";
			case COIN_CREATOR -> "Coin aktivitesi";
			case SHARES -> "Hisse işlemleri";
			case SHARE_OWNER -> "Şirketimden hisse";
			case LEVERAGE -> "Kaldıraç";
			case COLLATERAL -> "Teminat";
			case LOAN -> "Kredi";
			case PRIVATE_BANK -> "Özel banka";
			case EMPLOYMENT -> "Maaş / iş";
			case QUEST -> "Görev";
			case TAX_FEE -> "Vergi / komisyon";
			case BLACK_MARKET -> "Kara borsa";
			case MASAK -> "MASAK";
			case TRADE -> "Takas";
			case TREASURY_IN -> "Kasa girişi";
			case TREASURY_OUT -> "Kasa çıkışı";
			case DIVIDEND -> "Temettü";
			case TAX_IN -> "Vergi geliri";
			case SPEND_OUT -> "Harcama";
			case SUBSIDY -> "Piyasa desteği";
			case OTHER -> "Diğer";
		};
	}
}
