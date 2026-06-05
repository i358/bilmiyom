package com.mceconomy.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mceconomy.McEconomyMod;
import com.mceconomy.appeal.Appeal;
import com.mceconomy.blackmarket.BlackMarketService;
import com.mceconomy.blackmarket.IllegalGood;
import com.mceconomy.company.Company;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.EconomyManager;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.event.EconomyEventType;
import com.mceconomy.exchange.ExchangeToken;
import com.mceconomy.job.JobType;
import com.mceconomy.job.QuestManager;
import com.mceconomy.justice.CitizenReport;
import com.mceconomy.justice.PrisonSentence;
import com.mceconomy.market.Commodity;
import com.mceconomy.market.CommodityState;
import com.mceconomy.market.MarketItemEntry;
import com.mceconomy.market.MarketService;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.privatebank.PrivateBank;
import com.mceconomy.regulation.MasakAlert;
import com.mceconomy.util.Permissions;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DashboardDataService {
	public static final int MARKET_PAGE_SIZE = 48;

	private DashboardDataService() {
	}

	public static JsonObject buildTabData(ServerPlayer player, String tab, JsonObject params) {
		var manager = McEconomyMod.getEconomyManager();
		JsonObject root = new JsonObject();
		root.addProperty("tab", tab == null ? "overview" : tab);
		long wallet = manager.currencyService().getBalance(player.getUUID());
		long bank = manager.bankService().getBankBalanceMg(player.getUUID());
		root.addProperty("wallet", GoldStandard.formatMilligrams(wallet));
		root.addProperty("walletMg", wallet);
		root.addProperty("bank", GoldStandard.formatMilligrams(bank));
		root.addProperty("bankMg", bank);
		root.addProperty("isOp", Permissions.isServerOp(player));

		int marketPage = params != null && params.has("marketPage") ? params.get("marketPage").getAsInt() : 0;
		String search = params != null && params.has("search") ? params.get("search").getAsString() : "";
		String filter = params != null && params.has("filter") ? params.get("filter").getAsString() : "all";
		root.addProperty("marketPage", marketPage);
		root.addProperty("search", search == null ? "" : search);
		root.addProperty("filter", filter == null ? "all" : filter);
		boolean adminMode = params != null && params.has("adminMode") && params.get("adminMode").getAsBoolean();
		root.addProperty("adminMode", adminMode);

		String effectiveTab = tab == null ? "overview" : tab;
		if ("market".equals(effectiveTab) || "overview".equals(effectiveTab)) {
			root.add("market", buildMarketPage(manager.marketService(), marketPage, search, filter));
		}
		if ("inventory".equals(effectiveTab) || "overview".equals(effectiveTab)) {
			JsonObject inv = buildInventory(player);
			root.add("inventory", inv.getAsJsonArray("items"));
			root.addProperty("inventoryCount", inv.getAsJsonArray("items").size());
		}
		if ("illegal".equals(effectiveTab)) {
			root.add("illegalGoods", buildIllegalGoods(manager));
		}
		if ("exchange".equals(effectiveTab)) {
			root.add("tokens", buildTokenList(manager));
			root.add("companies", buildCompanyList(manager));
		}
		if ("job".equals(effectiveTab)) {
			var profile = manager.profiles().get(player.getUUID());
			root.addProperty("job", profile != null && profile.jobType() != null ? profile.jobType().name() : "-");
		}
		if ("loan".equals(effectiveTab)) {
			var loan = manager.loanManager().getLoan(player.getUUID());
			loan.ifPresentOrElse(l -> {
				root.addProperty("loanRemaining", GoldStandard.formatMilligrams(l.remaining()));
				root.addProperty("hasLoan", true);
			}, () -> root.addProperty("hasLoan", false));
		}
		UUID uuid = player.getUUID();
		boolean op = Permissions.isServerOp(player);
		switch (effectiveTab) {
			case "macro" -> {
				JsonObject me = buildMe(uuid, player);
				if (me.has("inflationRate")) {
					root.addProperty("inflationRate", me.get("inflationRate").getAsDouble());
				}
				if (me.has("fiatStrength")) {
					root.addProperty("fiatStrength", me.get("fiatStrength").getAsDouble());
				}
				if (me.has("municipalBudget")) {
					root.addProperty("municipalBudget", me.get("municipalBudget").getAsString());
				}
			}
			case "map" -> {
				String track = params != null && params.has("track") ? params.get("track").getAsString() : null;
				root.add("worldMap", buildWorldMap(uuid, op, track));
			}
			case "bulletins" -> root.add("bulletins", buildBulletins(null, 12).getAsJsonArray("bulletins"));
			case "overview" -> {
				root.add("bulletins", buildBulletins(null, 12).getAsJsonArray("bulletins"));
				mergeMeSummary(root, buildMe(uuid, player));
			}
			case "charts" -> {
				JsonObject charts = buildChartsOverview(uuid);
				if (charts.has("indexHistory")) {
					root.add("indexHistory", charts.get("indexHistory"));
				}
				if (charts.has("inflationHistory")) {
					root.add("inflationHistory", charts.get("inflationHistory"));
				}
				if (charts.has("commodities")) {
					root.add("commodities", charts.get("commodities"));
				}
			}
			case "employees" -> {
				JsonObject workforce = buildWorkforce(uuid);
				root.add("workforce", workforce);
				int companyCount = workforce.has("companies") ? workforce.getAsJsonArray("companies").size() : 0;
				root.addProperty("workforceCompanyCount", companyCount);
			}
			case "property", "vehicle" -> {
				JsonObject me = buildMe(uuid, player);
				if (me.has("properties")) {
					root.add("properties", me.get("properties"));
				}
				if (me.has("vehicles")) {
					root.add("vehicles", me.get("vehicles"));
				}
			}
			case "docs" -> root.addProperty("docs", buildDocsText());
			case "wallet", "bank", "privatebank" -> mergeMeSummary(root, buildMe(uuid, player));
			case "company" -> {
				JsonObject me = buildMe(uuid, player);
				mergeMeSummary(root, me);
				if (me.has("companies")) {
					root.add("myCompanies", me.get("companies"));
				}
				if (me.has("shares")) {
					root.add("shares", me.get("shares"));
				}
			}
			case "insurance" -> root.add("insurance", buildInsurance(uuid));
			case "trade" -> root.add("trades", buildTrades(uuid).getAsJsonArray("trades"));
			case "guild" -> mergeGuild(root, buildGuild(uuid));
			case "municipal" -> mergeMunicipal(root, buildMunicipal(uuid));
			case "government" -> mergeGovernment(root, buildGovernment(uuid));
			case "dashboard" -> {
				if (op) {
					JsonObject admin = buildAdminOverview();
					for (var entry : admin.entrySet()) {
						root.add(entry.getKey(), entry.getValue());
					}
					if (admin.has("openAppeals")) {
						root.addProperty("appealCount", admin.getAsJsonArray("openAppeals").size());
					}
					if (admin.has("masakAlerts")) {
						root.addProperty("alertCount", admin.getAsJsonArray("masakAlerts").size());
					}
					if (admin.has("openReportCount")) {
						root.addProperty("reportCount", admin.get("openReportCount").getAsInt());
					}
				}
			}
			case "players" -> {
				if (op) {
					String ps = params != null && params.has("playerSearch")
							? params.get("playerSearch").getAsString() : "";
					var playersResult = buildAdminPlayers(ps);
					root.add("adminPlayers", playersResult.getAsJsonArray("players"));
					root.addProperty("playerCount", playersResult.getAsJsonArray("players").size());
					String targetUuid = params != null && params.has("adminPlayerUuid")
							? params.get("adminPlayerUuid").getAsString() : "";
					if (!targetUuid.isBlank()) {
						try {
							root.add("adminPlayerDetail", buildAdminPlayerDetail(UUID.fromString(targetUuid)));
						} catch (IllegalArgumentException ignored) {
						}
					}
				}
			}
			case "economy-admin", "economy" -> {
				if (op) {
					JsonObject catalog = buildAdminEconomyCatalog();
					for (var entry : catalog.entrySet()) {
						root.add(entry.getKey(), entry.getValue());
					}
					var cb = manager.centralBank();
					root.addProperty("moneySupply", cb.getMoneySupply());
					root.addProperty("economyIndex", cb.getEconomyIndex());
					root.addProperty("inflationRate", cb.getInflationRate());
					root.addProperty("baseRate", cb.getBaseRate());
					root.addProperty("municipalBudget", GoldStandard.formatMilligrams(cb.getMunicipalBudgetMg()));
				}
			}
			case "masak" -> {
				if (op) {
					root.add("masakAlerts", buildAdminOverview().getAsJsonArray("masakAlerts"));
				}
			}
			case "appeals-review" -> {
				if (op) {
					root.add("openAppeals", buildAdminAppeals().getAsJsonArray("appeals"));
				}
			}
			case "justice-admin" -> {
				if (op) {
					root.add("openReports", buildAdminJusticeReports().getAsJsonArray("reports"));
					root.add("activePrisoners", buildAdminJusticePrison().getAsJsonArray("prisoners"));
				}
			}
			case "cameras" -> {
				if (op) {
					int night = params != null && params.has("nightIndex") ? params.get("nightIndex").getAsInt() : 0;
					root.add("securityCameras", buildAdminSecurityCameras(night));
				}
			}
			case "events", "tools" -> {
				if (op && buildCatalog().has("events")) {
					root.add("events", buildCatalog().getAsJsonArray("events"));
				}
			}
			case "blackmarket-admin" -> {
				if (op && buildCatalog().has("illegalGoods")) {
					root.add("adminIllegalGoods", buildCatalog().getAsJsonArray("illegalGoods"));
				}
			}
			case "config" -> {
				if (op) {
					JsonObject cfg = buildAdminConfig();
					for (var entry : cfg.entrySet()) {
						root.add(entry.getKey(), entry.getValue());
					}
				}
			}
			default -> { }
		}
		return root;
	}

	private static void mergeMeSummary(JsonObject root, JsonObject me) {
		for (String key : new String[]{"totalMg", "total", "dirty", "dirtyMg", "creditScore", "job", "jobId",
				"hasChecking", "hasTerm", "bankCertified", "canUseLegal", "isEconomyMinister", "centralBankOfficial"}) {
			if (me.has(key)) {
				root.add(key, me.get(key));
			}
		}
		if (me.has("privateBanks")) {
			root.add("privateBanks", me.get("privateBanks"));
		}
		if (me.has("privateDeposits")) {
			root.add("privateDeposits", me.get("privateDeposits"));
		}
	}

	private static void mergeGuild(JsonObject root, JsonObject guild) {
		for (var entry : guild.entrySet()) {
			root.add(entry.getKey(), entry.getValue());
		}
	}

	private static void mergeMunicipal(JsonObject root, JsonObject municipal) {
		for (var entry : municipal.entrySet()) {
			root.add(entry.getKey(), entry.getValue());
		}
	}

	private static void mergeGovernment(JsonObject root, JsonObject government) {
		for (var entry : government.entrySet()) {
			root.add(entry.getKey(), entry.getValue());
		}
	}

	public static String buildDocsText() {
		try (var in = McEconomyMod.class.getClassLoader().getResourceAsStream("dashboard/docs.js")) {
			if (in != null) {
				String raw = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				int start = raw.indexOf("const DOCS_HTML = `");
				if (start >= 0) {
					start = raw.indexOf('`', start) + 1;
					int end = raw.indexOf("`;", start);
					if (end > start) {
						return stripHtml(raw.substring(start, end));
					}
				}
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.warn("Docs yuklenemedi", e);
		}
		return "MC Economy Rehberi\n\nDetay icin web dashboard Rehber sekmesine bakin.";
	}

	private static String stripHtml(String html) {
		return html.replaceAll("<[^>]+>", "\n")
				.replaceAll("&nbsp;", " ")
				.replaceAll("&amp;", "&")
				.replaceAll("&lt;", "<")
				.replaceAll("&gt;", ">")
				.replaceAll("\n{3,}", "\n\n")
				.trim();
	}

	public static JsonObject buildMe(UUID uuid, ServerPlayer player) {
		JsonObject me = buildPortfolio(uuid);
		var em = McEconomyMod.getEconomyManager();
		var minister = em.economyMinisterService();
		me.addProperty("isEconomyMinister", minister != null && minister.isMinister(uuid));
		PlayerEconomyProfile prof = em.profiles().get(uuid);
		me.addProperty("centralBankOfficial", prof != null && prof.centralBankOfficial());
		if (player != null) {
			me.addProperty("online", true);
		}
		return me;
	}

	public static JsonObject buildInventory(ServerPlayer player) {
		JsonObject data = new JsonObject();
		JsonArray items = new JsonArray();
		if (player == null) {
			data.addProperty("online", false);
			data.add("items", items);
			return data;
		}
		data.addProperty("online", true);
		EconomyManager manager = McEconomyMod.getEconomyManager();
		Map<String, JsonObject> aggregated = new LinkedHashMap<>();
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var stack = inv.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
					.getKey(stack.getItem()).toString();
			JsonObject row = aggregated.get(itemId);
			if (row == null) {
				row = new JsonObject();
				row.addProperty("itemId", itemId);
				row.addProperty("name", stack.getHoverName().getString());
				row.addProperty("count", 0);
				var catalog = manager.marketService().catalog();
				var entry = catalog.resolve(stack.getItem());
				if (entry != null && entry.sellable()) {
					row.addProperty("marketable", true);
					long price = manager.marketService().priceEngine().getUnitPrice(entry.itemId());
					row.addProperty("price", GoldStandard.formatMilligrams(price));
					row.addProperty("priceMg", price);
					Commodity commodity = Commodity.fromItem(stack.getItem());
					if (commodity != null) {
						row.addProperty("commodityId", commodity.id());
					}
				} else {
					row.addProperty("marketable", false);
				}
				aggregated.put(itemId, row);
			}
			row.addProperty("count", row.get("count").getAsInt() + stack.getCount());
		}
		aggregated.values().forEach(items::add);
		data.add("items", items);
		return data;
	}

	public static JsonObject buildWorkforce(UUID uuid) {
		EconomyManager manager = McEconomyMod.getEconomyManager();
		JsonObject data = new JsonObject();
		JsonArray companies = new JsonArray();
		for (Company company : manager.companyManager().allCompanies()) {
			if (!company.ownerUuid().equals(uuid)) {
				continue;
			}
			JsonObject c = new JsonObject();
			c.addProperty("name", company.name());
			c.addProperty("treasury", GoldStandard.formatMilligrams(company.treasury()));
			JsonArray emps = new JsonArray();
			for (var e : manager.workforceService().employeesForOwner(uuid, company.name())) {
				JsonObject row = new JsonObject();
				row.addProperty("id", e.id());
				row.addProperty("kind", "npc");
				row.addProperty("name", e.npcName());
				row.addProperty("role", e.roleId());
				row.addProperty("salary", GoldStandard.formatMilligrams(e.salaryMg()));
				row.addProperty("salaryMg", e.salaryMg());
				row.addProperty("produced", GoldStandard.formatMilligrams(e.totalProducedMg()));
				emps.add(row);
			}
			for (var e : manager.playerEmploymentService().employeesForOwner(uuid, company.name())) {
				JsonObject row = new JsonObject();
				row.addProperty("id", e.id());
				row.addProperty("kind", "player");
				row.addProperty("name", e.playerName());
				row.addProperty("role", e.roleId());
				row.addProperty("salary", GoldStandard.formatMilligrams(e.salaryMg()));
				row.addProperty("salaryMg", e.salaryMg());
				row.addProperty("produced", "—");
				emps.add(row);
			}
			c.add("employees", emps);
			JsonArray apps = new JsonArray();
			for (var a : manager.workforceService().pendingForOwner(uuid, company.name())) {
				JsonObject row = new JsonObject();
				row.addProperty("id", a.id());
				row.addProperty("kind", "npc");
				row.addProperty("name", a.npcName());
				row.addProperty("role", a.roleId());
				row.addProperty("salary", GoldStandard.formatMilligrams(a.requestedSalaryMg()));
				row.addProperty("message", a.message());
				apps.add(row);
			}
			for (var a : manager.playerEmploymentService().pendingForOwner(uuid, company.name())) {
				JsonObject row = new JsonObject();
				row.addProperty("id", a.id());
				row.addProperty("kind", "player");
				row.addProperty("name", a.playerName());
				row.addProperty("role", a.roleId());
				row.addProperty("salary", GoldStandard.formatMilligrams(a.requestedSalaryMg()));
				row.addProperty("message", a.message());
				apps.add(row);
			}
			c.add("applications", apps);
			JsonArray stash = new JsonArray();
			for (var entry : manager.companyVaultService().listContents(company.id())) {
				JsonObject row = new JsonObject();
				row.addProperty("itemId", entry.itemId());
				row.addProperty("name", entry.displayName());
				row.addProperty("quantity", entry.quantity());
				stash.add(row);
			}
			c.add("stash", stash);
			c.addProperty("vaultReady", manager.companyVaultService().getVault(company.id()) != null);
			companies.add(c);
		}
		data.add("companies", companies);
		return data;
	}

	public static JsonObject buildInsurance(UUID uuid) {
		var ins = McEconomyMod.getEconomyManager().insuranceService();
		JsonObject data = new JsonObject();
		JsonArray policies = new JsonArray();
		for (var p : ins.policiesFor(uuid)) {
			JsonObject row = new JsonObject();
			row.addProperty("type", p.type().name());
			row.addProperty("companyId", p.companyId());
			row.addProperty("premiumMg", p.monthlyPremiumMg());
			row.addProperty("premium", GoldStandard.formatMilligrams(p.monthlyPremiumMg()));
			row.addProperty("active", p.active());
			policies.add(row);
		}
		data.add("policies", policies);
		return data;
	}

	public static JsonObject buildGuild(UUID uuid) {
		var guild = McEconomyMod.getEconomyManager().guildService().guildForPlayer(uuid);
		JsonObject data = new JsonObject();
		if (guild.isPresent()) {
			var g = guild.get();
			data.addProperty("name", g.name());
			data.addProperty("treasuryMg", g.treasuryMg());
			data.addProperty("treasury", GoldStandard.formatMilligrams(g.treasuryMg()));
			data.addProperty("strikeActive", g.strikeActive());
		}
		return data;
	}

	public static JsonObject buildGovernment(UUID uuid) {
		var minister = McEconomyMod.getEconomyManager().economyMinisterService();
		JsonObject data = new JsonObject();
		data.addProperty("isMinister", minister != null && minister.isMinister(uuid));
		data.addProperty("ministerCount", minister != null ? minister.ministerCount() : 0);
		data.addProperty("requiredYesVotes", minister != null ? minister.requiredYesVotes() : 1);
		JsonArray pending = new JsonArray();
		JsonArray recent = new JsonArray();
		if (minister != null) {
			try {
				for (var d : minister.pendingDecrees()) {
					JsonObject row = new JsonObject();
					row.addProperty("id", d.id());
					row.addProperty("type", d.type());
					row.addProperty("payloadJson", d.payloadJson());
					row.addProperty("createdAt", d.createdAt());
					row.addProperty("issuedBy", d.issuedBy());
					int yes = 0;
					JsonArray votes = new JsonArray();
					for (var v : minister.votesForDecree(d.id())) {
						if (v.yes()) {
							yes++;
						}
						JsonObject vr = new JsonObject();
						vr.addProperty("ministerUuid", v.ministerUuid().toString());
						vr.addProperty("yes", v.yes());
						votes.add(vr);
					}
					row.addProperty("yesVotes", yes);
					row.add("votes", votes);
					pending.add(row);
				}
				for (var d : minister.recentDecrees(12)) {
					JsonObject row = new JsonObject();
					row.addProperty("id", d.id());
					row.addProperty("type", d.type());
					row.addProperty("status", d.status());
					row.addProperty("createdAt", d.createdAt());
					recent.add(row);
				}
			} catch (Exception e) {
				McEconomyMod.LOGGER.error("Bakanlik API", e);
				data.addProperty("error", "Bakanlik verisi yuklenemedi: " + e.getMessage());
			}
		}
		data.add("pendingDecrees", pending);
		data.add("recentDecrees", recent);
		return data;
	}

	public static JsonObject buildMunicipal(UUID uuid) {
		var mayor = McEconomyMod.getEconomyManager().mayorService();
		JsonObject data = new JsonObject();
		var mstate = mayor.state();
		data.addProperty("mayorName", mstate.hasMayor() ? mstate.mayorName() : "—");
		data.addProperty("budgetMg", McEconomyMod.getEconomyManager().centralBank().getMunicipalBudgetMg());
		data.addProperty("budget", GoldStandard.formatMilligrams(
				McEconomyMod.getEconomyManager().centralBank().getMunicipalBudgetMg()));
		JsonArray candidates = new JsonArray();
		for (var entry : mayor.electionCandidates().entrySet()) {
			JsonObject row = new JsonObject();
			row.addProperty("name", entry.getValue());
			candidates.add(row);
		}
		data.add("candidates", candidates);
		return data;
	}

	public static JsonObject buildTrades(UUID uuid) {
		JsonArray trades = new JsonArray();
		for (var trade : McEconomyMod.getEconomyManager().playerTradeService().history(uuid)) {
			JsonObject row = new JsonObject();
			row.addProperty("id", trade.id());
			row.addProperty("initiator", trade.initiatorName());
			row.addProperty("partner", trade.partnerName());
			row.addProperty("status", trade.status().name());
			row.addProperty("initiatorGoldMg", trade.initiatorGoldMg());
			row.addProperty("partnerGoldMg", trade.partnerGoldMg());
			row.addProperty("completedAt", trade.completedAt());
			trades.add(row);
		}
		JsonObject data = new JsonObject();
		data.add("trades", trades);
		return data;
	}

	public static JsonObject buildBulletins() {
		return buildBulletins(null, 50);
	}

	public static JsonObject buildBulletins(String category, int limit) {
		var manager = McEconomyMod.getEconomyManager();
		JsonArray items = new JsonArray();
		if (manager != null && manager.bulletinService() != null) {
			var bulletins = category != null && !category.isBlank()
					? manager.bulletinService().recentByCategory(category, limit)
					: manager.bulletinService().recent(limit);
			for (var bulletin : bulletins) {
				JsonObject row = new JsonObject();
				row.addProperty("id", bulletin.id());
				row.addProperty("category", bulletin.category());
				row.addProperty("categoryLabel", categoryLabel(bulletin.category()));
				row.addProperty("headline", bulletin.headline());
				row.addProperty("body", bulletin.body());
				row.addProperty("valueMg", bulletin.valueMg());
				row.addProperty("value", GoldStandard.formatMilligrams(bulletin.valueMg()));
				row.addProperty("createdAt", bulletin.createdAt());
				items.add(row);
			}
		}
		JsonObject data = new JsonObject();
		data.add("bulletins", items);
		return data;
	}

	public static JsonObject buildWorldMap(UUID uuid, boolean op) {
		return buildWorldMap(uuid, op, null);
	}

	public static JsonObject buildWorldMap(UUID uuid, boolean op, String track) {
		return WorldMapService.buildMapData(uuid, op, track);
	}

	public static JsonObject buildChartsOverview(UUID uuid) {
		var manager = McEconomyMod.getEconomyManager();
		var market = manager.marketService();
		JsonObject data = new JsonObject();
		double economyIndex = market.economyIndex().calculate();
		long indexScaledMg = (long) (economyIndex * 1000);
		JsonArray indexHistory = loadHistory("INDEX", "economy", 48);
		data.addProperty("economyIndex", economyIndex);
		data.addProperty("economyIndexChangeBps", priceChangeBps(indexHistory, indexScaledMg));
		data.add("indexHistory", indexHistory);

		JsonArray commodities = new JsonArray();
		for (Commodity commodity : Commodity.values()) {
			if (!commodity.sellable()) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("id", commodity.id());
			row.addProperty("name", commodity.displayName());
			long priceMg = market.priceEngine().getUnitPrice(commodity);
			row.addProperty("priceMg", priceMg);
			row.addProperty("category", commodity.jobCategory().name());
			enrichCommodityRow(row, market, commodity, priceMg);
			commodities.add(row);
		}
		data.add("commodities", commodities);

		JsonArray tokens = new JsonArray();
		for (ExchangeToken token : manager.exchangeService().allTokens()) {
			JsonObject row = new JsonObject();
			row.addProperty("symbol", token.symbol());
			row.addProperty("name", token.displayName());
			long priceMg = token.priceMg();
			row.addProperty("priceMg", priceMg);
			JsonArray tokenHist = loadHistory("TOKEN", token.symbol(), 24);
			row.add("history", tokenHist);
			row.addProperty("changeBps", priceChangeBps(tokenHist, priceMg));
			tokens.add(row);
		}
		data.add("tokens", tokens);

		JsonArray topCommodityHistories = new JsonArray();
		int count = 0;
		for (Commodity commodity : Commodity.values()) {
			if (!commodity.sellable() || count >= 5) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("id", commodity.id());
			row.addProperty("name", commodity.displayName());
			row.add("history", loadHistory("COMMODITY", commodity.id(), 24));
			topCommodityHistories.add(row);
			count++;
		}
		data.add("topHistories", topCommodityHistories);
		data.addProperty("municipalBudgetMg", manager.centralBank().getMunicipalBudgetMg());
		data.addProperty("municipalBudget", GoldStandard.formatMilligrams(manager.centralBank().getMunicipalBudgetMg()));
		data.add("inflationHistory", loadHistory("MACRO", "inflation", 48));
		data.add("goldReserveHistory", loadHistory("MACRO", "gold_reserve", 48));
		data.add("municipalHistory", loadHistory("MACRO", "municipal_budget", 48));
		data.add("fiatStrengthHistory", loadHistory("MACRO", "fiat_strength", 48));
		if (manager.centralBank() != null) {
			addFiatMacro(data, manager.centralBank());
		}
		return data;
	}

	public static JsonObject buildChartsPortfolio(UUID uuid) {
		var manager = McEconomyMod.getEconomyManager();
		double index = manager.marketService().economyIndex().calculate();
		JsonArray holdings = new JsonArray();

		for (ExchangeToken token : manager.exchangeService().allTokens()) {
			int amount = manager.exchangeService().tokenBalance(uuid, token);
			if (amount <= 0) {
				continue;
			}
			JsonObject row = portfolioHoldingRow("TOKEN", token.symbol(), token.displayName(),
					amount, token.priceMg(), loadHistory("TOKEN", token.symbol(), 60));
			holdings.add(row);
		}
		for (Company company : manager.companyManager().allCompanies()) {
			int amount = manager.companyManager().getShareCount(uuid, company);
			if (amount <= 0) {
				continue;
			}
			String symbol = company.ticker() != null ? company.ticker() : company.name();
			long priceMg = company.sharePrice(index);
			JsonArray history = company.listedOnExchange() && company.ticker() != null
					? loadHistory("SHARE", company.ticker(), 60)
					: new JsonArray();
			JsonObject row = portfolioHoldingRow("SHARE", symbol, company.name(), amount, priceMg, history);
			holdings.add(row);
		}
		for (var pos : manager.leverageService().positionsOf(uuid)) {
			holdings.add(portfolioLeverageRow(pos, loadHistory("TOKEN", pos.symbol(), 60)));
		}
		JsonObject data = new JsonObject();
		data.add("holdings", holdings);
		data.addProperty("economyIndex", index);
		data.addProperty("updatedAt", System.currentTimeMillis());
		return data;
	}

	public static JsonObject buildCatalog() {
		var manager = McEconomyMod.getEconomyManager();
		double index = manager.marketService().economyIndex().calculate();
		JsonObject catalog = new JsonObject();

		JsonArray jobs = new JsonArray();
		for (JobType job : JobType.values()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", job.id());
			row.addProperty("name", job.displayName());
			jobs.add(row);
		}
		catalog.add("jobs", jobs);

		JsonArray commodities = new JsonArray();
		for (Commodity commodity : Commodity.values()) {
			if (!commodity.sellable() && !commodity.buyable()) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("id", commodity.id());
			row.addProperty("name", commodity.displayName());
			long priceMg = manager.marketService().priceEngine().getUnitPrice(commodity);
			row.addProperty("priceMg", priceMg);
			row.addProperty("buyable", commodity.buyable());
			row.addProperty("sellable", commodity.sellable());
			enrichCommodityRow(row, manager.marketService(), commodity, priceMg);
			commodities.add(row);
		}
		catalog.add("commodities", commodities);

		JsonArray companies = new JsonArray();
		for (Company company : manager.companyManager().allCompanies()) {
			JsonObject row = new JsonObject();
			row.addProperty("name", company.name());
			row.addProperty("ticker", company.ticker() != null ? company.ticker() : "");
			row.addProperty("listed", company.listedOnExchange());
			row.addProperty("sharePriceMg", company.sharePrice(index));
			row.addProperty("treasury", company.treasury());
			companies.add(row);
		}
		catalog.add("companies", companies);

		JsonArray tokens = new JsonArray();
		for (ExchangeToken token : manager.exchangeService().allTokens()) {
			JsonObject row = new JsonObject();
			row.addProperty("symbol", token.symbol());
			row.addProperty("name", token.displayName());
			row.addProperty("priceMg", token.priceMg());
			row.addProperty("circulating", token.circulating());
			row.addProperty("supply", token.totalSupply());
			tokens.add(row);
		}
		catalog.add("tokens", tokens);

		JsonArray privateBanks = new JsonArray();
		for (PrivateBank bank : manager.privateBankService().allBanks()) {
			JsonObject row = new JsonObject();
			row.addProperty("name", bank.name());
			privateBanks.add(row);
		}
		catalog.add("privateBanks", privateBanks);

		JsonArray illegalGoods = new JsonArray();
		BlackMarketService bm = manager.blackMarketService();
		for (IllegalGood good : IllegalGood.tradable()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", good.id());
			row.addProperty("name", good.displayName());
			row.addProperty("sellPriceMg", bm.getSellPrice(good));
			row.addProperty("buyPriceMg", bm.getBuyPrice(good));
			illegalGoods.add(row);
		}
		if (manager.customBlackMarket() != null) {
			for (var good : manager.customBlackMarket().all()) {
				JsonObject row = new JsonObject();
				row.addProperty("id", good.id());
				row.addProperty("name", "★ " + good.displayName());
				row.addProperty("sellPriceMg", (long) (good.priceMg() * EconomyConfig.blackMarketSellMultiplier()));
				row.addProperty("buyPriceMg", (long) (good.priceMg() * EconomyConfig.blackMarketBuyPremium()));
				illegalGoods.add(row);
			}
		}
		if (manager.playerBlackMarket() != null) {
			for (var listing : manager.playerBlackMarket().all()) {
				JsonObject row = new JsonObject();
				row.addProperty("id", listing.catalogId());
				row.addProperty("name", "🛒 " + listing.displayName() + " (" + listing.sellerName() + " x" + listing.stock() + ")");
				row.addProperty("sellPriceMg", (long) (listing.priceMg() * EconomyConfig.blackMarketSellMultiplier()));
				row.addProperty("buyPriceMg", (long) (listing.priceMg() * EconomyConfig.blackMarketBuyPremium()));
				row.addProperty("playerListing", true);
				illegalGoods.add(row);
			}
		}
		catalog.add("illegalGoods", illegalGoods);

		JsonArray events = new JsonArray();
		for (EconomyEventType type : EconomyEventType.values()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", type.id());
			row.addProperty("name", type.name());
			events.add(row);
		}
		catalog.add("events", events);
		return catalog;
	}

	public static JsonObject buildAdminOverview() {
		var manager = McEconomyMod.getEconomyManager();
		JsonObject data = new JsonObject();
		data.addProperty("playerCount", manager.profiles().size());
		JsonArray alerts = new JsonArray();
		for (MasakAlert alert : manager.masakService().openAlerts()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", alert.id());
			row.addProperty("playerUuid", alert.playerUuid().toString());
			row.addProperty("reason", alert.reason());
			row.addProperty("riskScore", alert.riskScore());
			row.addProperty("amountMg", alert.amount());
			String playerName = manager.profiles().containsKey(alert.playerUuid())
					? manager.profiles().get(alert.playerUuid()).name() : alert.playerUuid().toString().substring(0, 8);
			row.addProperty("playerName", playerName);
			alerts.add(row);
		}
		data.add("masakAlerts", alerts);
		JsonArray appeals = new JsonArray();
		for (Appeal appeal : manager.appealService().openAppeals()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", appeal.id());
			row.addProperty("playerName", appeal.playerName());
			row.addProperty("subject", appeal.subject());
			row.addProperty("message", appeal.message());
			if (appeal.relatedAlertId() != null) {
				row.addProperty("relatedAlertId", appeal.relatedAlertId());
			}
			appeals.add(row);
		}
		data.add("openAppeals", appeals);

		JsonArray citizenReports = new JsonArray();
		for (CitizenReport report : manager.reportService().openReports()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", report.id());
			row.addProperty("type", report.type().name());
			row.addProperty("typeLabel", report.type().displayName());
			row.addProperty("status", report.status().name());
			row.addProperty("reporterName", report.reporterName());
			row.addProperty("targetName", report.targetName());
			row.addProperty("category", report.category());
			row.addProperty("subject", report.subject());
			row.addProperty("message", report.message());
			citizenReports.add(row);
		}
		data.add("openReports", citizenReports);
		data.addProperty("openReportCount", citizenReports.size());

		JsonArray prisoners = new JsonArray();
		for (PrisonSentence s : manager.prisonService().activeSentences()) {
			JsonObject row = new JsonObject();
			row.addProperty("playerName", s.playerName());
			row.addProperty("reason", s.reason());
			row.addProperty("remainingMs", s.remainingMs());
			row.addProperty("sentencedBy", s.sentencedBy());
			prisoners.add(row);
		}
		data.add("activePrisoners", prisoners);
		data.addProperty("prisonerCount", prisoners.size());

		JsonArray mbOfficials = new JsonArray();
		for (PlayerEconomyProfile profile : manager.profiles().values()) {
			if (profile.centralBankOfficial()) {
				mbOfficials.add(profile.name());
			}
		}
		data.add("mbOfficials", mbOfficials);
		return data;
	}

	public static JsonObject buildAdminPlayers(String search) {
		var manager = McEconomyMod.getEconomyManager();
		JsonArray players = new JsonArray();
		String q = search != null ? search.trim().toLowerCase() : "";
		for (PlayerEconomyProfile profile : manager.profiles().values()) {
			if (!q.isEmpty() && !profile.name().toLowerCase().contains(q)) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("name", profile.name());
			row.addProperty("uuid", profile.uuid().toString());
			row.addProperty("walletMg", manager.currencyService().getBalance(profile.uuid()));
			row.addProperty("bankMg", manager.bankService().getBankBalanceMg(profile.uuid()));
			row.addProperty("dirtyMg", manager.currencyService().getDirtyBalance(profile.uuid()));
			row.addProperty("creditScore", profile.creditScore().score());
			row.addProperty("frozen", profile.accountFrozen());
			row.addProperty("blacklisted", profile.blacklisted());
			row.addProperty("mbOfficial", profile.centralBankOfficial());
			row.addProperty("online", DashboardActionService.onlinePlayer(profile.uuid()) != null);
			players.add(row);
		}
		JsonObject wrapper = new JsonObject();
		wrapper.add("players", players);
		return wrapper;
	}

	public static JsonObject buildAdminPlayerDetail(UUID uuid) {
		JsonObject data = buildPortfolio(uuid);
		if (!data.has("name")) {
			return data;
		}
		var manager = McEconomyMod.getEconomyManager();
		data.addProperty("uuid", uuid.toString());

		manager.bankService().getTerm(uuid).ifPresent(term -> {
			data.addProperty("hasTerm", true);
			data.addProperty("termBalanceMg", term.balance());
			data.addProperty("termBalance", GoldStandard.formatMilligrams(term.balance()));
			data.addProperty("termInterestRate", term.interestRate());
			data.addProperty("termMaturesAt", term.maturesAt());
		});
		if (!data.has("hasTerm")) {
			data.addProperty("hasTerm", false);
			data.addProperty("termBalanceMg", 0);
		}

		manager.loanManager().getLoan(uuid).ifPresent(loan -> {
			if (data.has("loan")) {
				data.getAsJsonObject("loan").addProperty("dueAt", loan.dueAt());
				data.getAsJsonObject("loan").addProperty("interestRate", loan.interestRate());
			}
		});

		JsonArray allPrivateDeposits = new JsonArray();
		for (PrivateBank bank : manager.privateBankService().allBanks()) {
			long dep = manager.privateBankService().customerBalance(uuid, bank);
			JsonObject row = new JsonObject();
			row.addProperty("bank", bank.name());
			row.addProperty("balanceMg", dep);
			row.addProperty("balance", GoldStandard.formatMilligrams(dep));
			allPrivateDeposits.add(row);
		}
		data.add("allPrivateDeposits", allPrivateDeposits);

		JsonArray allShares = new JsonArray();
		double index = manager.marketService().economyIndex().calculate();
		for (Company company : manager.companyManager().allCompanies()) {
			int amount = manager.companyManager().getShareCount(uuid, company);
			if (amount <= 0) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("ticker", company.ticker() != null ? company.ticker() : company.name());
			row.addProperty("name", company.name());
			row.addProperty("amount", amount);
			row.addProperty("priceMg", company.sharePrice(index));
			allShares.add(row);
		}
		data.add("allShares", allShares);

		JsonArray allTokens = new JsonArray();
		for (ExchangeToken token : manager.exchangeService().allTokens()) {
			int amount = manager.exchangeService().tokenBalance(uuid, token);
			if (amount <= 0) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("symbol", token.symbol());
			row.addProperty("displayName", token.displayName());
			row.addProperty("amount", amount);
			row.addProperty("priceMg", token.priceMg());
			allTokens.add(row);
		}
		data.add("allTokens", allTokens);

		PlayerEconomyProfile adminProfile = manager.profiles().get(uuid);
		if (adminProfile != null) {
			data.addProperty("mbOfficial", adminProfile.centralBankOfficial());
		}
		return data;
	}

	public static JsonObject buildAdminEconomyCatalog() {
		var manager = McEconomyMod.getEconomyManager();
		double index = manager.marketService().economyIndex().calculate();
		JsonObject data = new JsonObject();

		JsonArray companies = new JsonArray();
		for (Company company : manager.companyManager().allCompanies()) {
			JsonObject row = new JsonObject();
			row.addProperty("name", company.name());
			row.addProperty("ticker", company.ticker());
			row.addProperty("listed", company.listedOnExchange());
			row.addProperty("treasuryMg", company.treasury());
			row.addProperty("treasury", GoldStandard.formatMilligrams(company.treasury()));
			row.addProperty("sharePriceMg", company.sharePrice(index));
			row.addProperty("ownerUuid", company.ownerUuid().toString());
			companies.add(row);
		}
		data.add("companies", companies);

		JsonArray tokens = new JsonArray();
		for (ExchangeToken token : manager.exchangeService().allTokens()) {
			JsonObject row = new JsonObject();
			row.addProperty("symbol", token.symbol());
			row.addProperty("displayName", token.displayName());
			row.addProperty("priceMg", token.priceMg());
			row.addProperty("circulating", token.circulating());
			row.addProperty("totalSupply", token.totalSupply());
			tokens.add(row);
		}
		data.add("tokens", tokens);

		JsonArray privateBanks = new JsonArray();
		for (PrivateBank bank : manager.privateBankService().allBanks()) {
			JsonObject row = new JsonObject();
			row.addProperty("name", bank.name());
			privateBanks.add(row);
		}
		data.add("privateBanks", privateBanks);

		var cb = manager.centralBank();
		if (cb != null) {
			JsonObject macro = new JsonObject();
			macro.addProperty("baseRate", cb.getBaseRate());
			macro.addProperty("inflationRate", cb.getInflationRate());
			macro.addProperty("economyIndex", cb.getEconomyIndex());
			macro.addProperty("goldFactor", cb.getGoldFactor());
			macro.addProperty("moneySupply", cb.getMoneySupply());
			macro.addProperty("municipalBudgetMg", cb.getMunicipalBudgetMg());
			macro.addProperty("municipalBudgetMc", cb.getMunicipalBudgetMg() / 1000.0);
			addFiatMacro(macro, cb);
			data.add("centralBank", macro);
		}
		return data;
	}

	public static JsonObject buildAdminConfig() {
		DashboardActionService.ActionResult result = AdminEconomyService.configRead();
		if (result.success() && result.data() != null) {
			return result.data();
		}
		JsonObject err = new JsonObject();
		err.addProperty("error", result.message() != null ? result.message() : "Config okunamadı.");
		return err;
	}

	public static JsonObject buildAdminAppeals() {
		JsonArray appeals = new JsonArray();
		for (Appeal appeal : McEconomyMod.getEconomyManager().appealService().openAppeals()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", appeal.id());
			row.addProperty("playerName", appeal.playerName());
			row.addProperty("subject", appeal.subject());
			row.addProperty("message", appeal.message());
			row.addProperty("relatedAlertId", appeal.relatedAlertId());
			appeals.add(row);
		}
		JsonObject wrapper = new JsonObject();
		wrapper.add("appeals", appeals);
		return wrapper;
	}

	public static JsonObject buildAdminJusticeReports() {
		JsonArray reports = new JsonArray();
		for (CitizenReport report : McEconomyMod.getEconomyManager().reportService().openReports()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", report.id());
			row.addProperty("type", report.type().name());
			row.addProperty("typeLabel", report.type().displayName());
			row.addProperty("status", report.status().name());
			row.addProperty("reporterName", report.reporterName());
			row.addProperty("targetName", report.targetName());
			row.addProperty("category", report.category());
			row.addProperty("subject", report.subject());
			row.addProperty("message", report.message());
			reports.add(row);
		}
		JsonObject wrapper = new JsonObject();
		wrapper.add("reports", reports);
		return wrapper;
	}

	public static JsonObject buildAdminJusticePrison() {
		JsonArray prisoners = new JsonArray();
		for (PrisonSentence s : McEconomyMod.getEconomyManager().prisonService().activeSentences()) {
			JsonObject row = new JsonObject();
			row.addProperty("playerName", s.playerName());
			row.addProperty("reason", s.reason());
			row.addProperty("remainingMs", s.remainingMs());
			row.addProperty("sentencedBy", s.sentencedBy());
			prisoners.add(row);
		}
		JsonObject wrapper = new JsonObject();
		wrapper.add("prisoners", prisoners);
		return wrapper;
	}

	public static JsonObject buildAdminSecurityCameras(int nightIndex) {
		return buildAdminSecurityCameras(nightIndex, 500);
	}

	public static JsonObject buildAdminSecurityCameras(int nightIndex, int limit) {
		var manager = McEconomyMod.getEconomyManager();
		JsonObject data = new JsonObject();
		if (manager == null || manager.securityCameraService() == null) {
			data.add("logs", new JsonArray());
			data.add("nights", new JsonArray());
			return data;
		}
		try {
			List<String> nightKeys = manager.securityCameraService().listNights();
			JsonArray nights = new JsonArray();
			for (String key : nightKeys) {
				nights.add(key);
			}
			data.add("nights", nights);
			data.addProperty("currentNight", manager.securityCameraService().currentNightKey());

			String night = null;
			if (nightIndex >= 0 && nightIndex < nightKeys.size()) {
				night = nightKeys.get(nightIndex);
			}

			JsonArray logs = new JsonArray();
			for (var log : manager.securityCameraService().loadLogs(night, limit)) {
				JsonObject row = new JsonObject();
				row.addProperty("id", log.id());
				row.addProperty("nightKey", log.nightKey());
				row.addProperty("playerUuid", log.playerUuid().toString());
				row.addProperty("playerName", log.playerName());
				row.addProperty("x", log.x());
				row.addProperty("y", log.y());
				row.addProperty("z", log.z());
				row.addProperty("recordedAt", log.recordedAt());
				logs.add(row);
			}
			data.add("logs", logs);

			JsonArray replay = new JsonArray();
			String replayNight = night != null && !night.isBlank() ? night : manager.securityCameraService().currentNightKey();
			if (replayNight == null || replayNight.isBlank()) {
				if (!nightKeys.isEmpty()) {
					replayNight = nightKeys.get(0);
				}
			}
			if (replayNight != null && !replayNight.isBlank()) {
				for (var log : manager.securityCameraService().loadReplayForNight(replayNight, 4000)) {
					JsonObject row = new JsonObject();
					row.addProperty("playerUuid", log.playerUuid().toString());
					row.addProperty("playerName", log.playerName());
					row.addProperty("x", log.x());
					row.addProperty("y", log.y());
					row.addProperty("z", log.z());
					row.addProperty("recordedAt", log.recordedAt());
					replay.add(row);
				}
			}
			data.add("replay", replay);
		} catch (java.sql.SQLException e) {
			McEconomyMod.LOGGER.error("Kamera kayitlari", e);
			data.addProperty("error", "Kamera kayitlari yuklenemedi");
		}
		return data;
	}

	public static JsonObject buildMarketPage(MarketService market, int page, String search, String filter) {
		JsonObject obj = new JsonObject();
		var catalog = market.catalog();
		obj.addProperty("page", page);
		obj.addProperty("pageCount", catalog.pageCount(MARKET_PAGE_SIZE, search, filter));
		JsonArray items = new JsonArray();
		for (MarketItemEntry entry : catalog.page(page, MARKET_PAGE_SIZE, search, filter)) {
			JsonObject row = new JsonObject();
			row.addProperty("itemId", entry.itemId());
			row.addProperty("name", entry.displayName());
			row.addProperty("priceMg", market.priceEngine().getUnitPrice(entry.itemId()));
			row.addProperty("price", GoldStandard.formatMilligrams(market.priceEngine().getUnitPrice(entry.itemId())));
			row.addProperty("sellable", entry.sellable());
			row.addProperty("buyable", entry.buyable());
			row.addProperty("tier", entry.valueTier().name());
			items.add(row);
		}
		obj.add("items", items);
		return obj;
	}

	public static JsonArray loadHistory(String type, String symbol, int limit) {
		JsonArray history = new JsonArray();
		try {
			for (Map<String, Object> row : McEconomyMod.getEconomyManager().priceHistoryRepository().loadRecent(type, symbol, limit)) {
				JsonObject point = new JsonObject();
				point.addProperty("priceMg", ((Number) row.get("priceMg")).longValue());
				point.addProperty("recordedAt", ((Number) row.get("recordedAt")).longValue());
				history.add(point);
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("Fiyat geçmişi okunamadı: {} {}", type, symbol, e);
		}
		return history;
	}

	public static void enrichCommodityRow(JsonObject row, MarketService market, Commodity commodity, long priceMg) {
		CommodityState state = market.commodityState(commodity);
		if (state == null) {
			return;
		}
		row.addProperty("supplyIndex", state.supplyIndex());
		row.addProperty("demandIndex", state.demandIndex());
		double flow = state.supplyIndex() + state.demandIndex();
		if (flow > 0) {
			row.addProperty("supplySharePct", Math.round(state.supplyIndex() / flow * 1000) / 10.0);
			row.addProperty("demandSharePct", Math.round(state.demandIndex() / flow * 1000) / 10.0);
		} else {
			row.addProperty("supplySharePct", 50.0);
			row.addProperty("demandSharePct", 50.0);
		}
		JsonArray hist = loadHistory("COMMODITY", commodity.id(), 24);
		row.addProperty("changeBps", priceChangeBps(hist, priceMg));
	}

	public static long priceChangeBps(JsonArray history, long currentPriceMg) {
		if (history == null || history.isEmpty() || currentPriceMg <= 0) {
			return 0;
		}
		long oldestPrice = history.get(0).getAsJsonObject().get("priceMg").getAsLong();
		if (oldestPrice <= 0) {
			return 0;
		}
		return Math.round((currentPriceMg - oldestPrice) * 10000.0 / oldestPrice);
	}

	private static JsonObject buildPortfolio(UUID uuid) {
		var manager = McEconomyMod.getEconomyManager();
		PlayerEconomyProfile profile = manager.profiles().get(uuid);
		if (profile == null) {
			manager.ensurePlayer(uuid, "Oyuncu");
			profile = manager.profiles().get(uuid);
		}
		JsonObject data = new JsonObject();
		if (profile == null) {
			data.addProperty("error", "Profil olusturulamadi. Once oyuna girin.");
			return data;
		}
		long wallet = manager.currencyService().getBalance(uuid);
		long bank = manager.bankService().getBankBalanceMg(uuid);
		long dirty = manager.currencyService().getDirtyBalance(uuid);
		data.addProperty("name", profile.name());
		data.addProperty("walletMg", wallet);
		data.addProperty("wallet", GoldStandard.formatMilligrams(wallet));
		data.addProperty("bankMg", bank);
		data.addProperty("bank", GoldStandard.formatMilligrams(bank));
		data.addProperty("dirtyMg", dirty);
		data.addProperty("dirty", GoldStandard.formatMilligrams(dirty));
		data.addProperty("totalMg", wallet + bank);
		data.addProperty("creditScore", profile.creditScore().score());
		data.addProperty("job", profile.jobType() != null ? profile.jobType().displayName() : "Yok");
		data.addProperty("jobId", profile.jobType() != null ? profile.jobType().id() : "");
		data.addProperty("accountFrozen", profile.accountFrozen());
		data.addProperty("blacklisted", profile.blacklisted());
		data.addProperty("bankCertified", profile.bankCertified());
		data.addProperty("hasChecking", manager.bankService().getChecking(uuid).isPresent());
		data.addProperty("canUseLegal", profile.canUseLegalEconomy());
		data.addProperty("online", DashboardActionService.onlinePlayer(uuid) != null);
		data.addProperty("economyIndex", manager.marketService().economyIndex().calculate());
		data.addProperty("goldFactor", GoldStandard.goldFactor());
		data.addProperty("ingotPrice", GoldStandard.formatMilligrams(Math.round(GoldStandard.ingotPriceMc() * 1000)));
		data.addProperty("inflationRate", manager.centralBank() != null ? manager.centralBank().getInflationRate() : 0);
		if (manager.centralBank() != null) {
			var cb = manager.centralBank();
			data.addProperty("municipalBudgetMg", cb.getMunicipalBudgetMg());
			data.addProperty("municipalBudget", GoldStandard.formatMilligrams(cb.getMunicipalBudgetMg()));
			addFiatMacro(data, cb);
		}
		data.addProperty("certCostMg", EconomyConfig.bankCertificateCostMg());
		data.addProperty("certCost", GoldStandard.formatMilligrams(EconomyConfig.bankCertificateCostMg()));
		if (manager.goldReserveService() != null) {
			data.addProperty("reserveGoldBlocks", manager.goldReserveService().cachedGoldBlocks());
			data.addProperty("reserveBacking", GoldStandard.formatMilligrams(manager.goldReserveService().backingMilligrams()));
		}

		manager.loanManager().getLoan(uuid).ifPresent(loan -> {
			JsonObject loanObj = new JsonObject();
			loanObj.addProperty("remainingMg", loan.remaining());
			loanObj.addProperty("remaining", GoldStandard.formatMilligrams(loan.remaining()));
			loanObj.addProperty("installmentMg", loan.installment());
			loanObj.addProperty("installment", GoldStandard.formatMilligrams(loan.installment()));
			data.add("loan", loanObj);
		});

		QuestManager.ActiveQuest quest = manager.questManager().getQuest(uuid);
		if (quest != null) {
			JsonObject questObj = new JsonObject();
			questObj.addProperty("title", quest.title());
			questObj.addProperty("progress", quest.progress());
			questObj.addProperty("required", quest.required());
			questObj.addProperty("reward", GoldStandard.formatMilligrams(quest.reward()));
			questObj.addProperty("companyQuest", quest.isCompanyQuest());
			data.add("quest", questObj);
		}

		JsonArray shares = new JsonArray();
		double index = manager.marketService().economyIndex().calculate();
		for (Company company : manager.companyManager().allCompanies()) {
			int amount = manager.companyManager().getShareCount(uuid, company);
			if (amount > 0) {
				JsonObject row = new JsonObject();
				row.addProperty("ticker", company.ticker() != null ? company.ticker() : company.name());
				row.addProperty("name", company.name());
				row.addProperty("amount", amount);
				row.addProperty("priceMg", company.sharePrice(index));
				shares.add(row);
			}
		}
		data.add("shares", shares);

		JsonArray tokens = new JsonArray();
		for (ExchangeToken token : manager.exchangeService().allTokens()) {
			int amount = manager.exchangeService().tokenBalance(uuid, token);
			if (amount > 0) {
				JsonObject row = new JsonObject();
				row.addProperty("symbol", token.symbol());
				row.addProperty("amount", amount);
				row.addProperty("priceMg", token.priceMg());
				tokens.add(row);
			}
		}
		data.add("tokens", tokens);

		manager.playerEmploymentService().employmentForPlayer(uuid).ifPresent(emp -> {
			Company company = manager.companyManager().allCompanies().stream()
					.filter(c -> c.id() == emp.companyId()).findFirst().orElse(null);
			JsonObject job = new JsonObject();
			job.addProperty("company", company != null ? company.name() : "?");
			job.addProperty("role", emp.roleId());
			job.addProperty("salary", GoldStandard.formatMilligrams(emp.salaryMg()));
			job.addProperty("salaryMg", emp.salaryMg());
			job.addProperty("nextPayMs", emp.lastPaidAt() + EconomyConfig.playerDailySalaryIntervalMs());
			data.add("employment", job);
		});

		JsonArray leveragePositions = new JsonArray();
		for (var pos : manager.leverageService().positionsOf(uuid)) {
			JsonObject row = new JsonObject();
			row.addProperty("id", pos.id());
			row.addProperty("symbol", pos.symbol());
			row.addProperty("side", pos.isLong() ? "LONG" : "SHORT");
			row.addProperty("leverage", pos.leverage());
			row.addProperty("margin", GoldStandard.formatMilligrams(pos.marginMg()));
			row.addProperty("entryPriceMg", pos.entryPriceMg());
			row.addProperty("currentPriceMg", pos.currentPriceMg());
			row.addProperty("pnl", (pos.pnlMg() >= 0 ? "+" : "") + GoldStandard.formatMilligrams(pos.pnlMg()));
			row.addProperty("equity", GoldStandard.formatMilligrams(pos.equityMg()));
			leveragePositions.add(row);
		}
		data.add("leveragePositions", leveragePositions);

		JsonArray privateDeposits = new JsonArray();
		for (PrivateBank privateBank : manager.privateBankService().allBanks()) {
			long dep = manager.privateBankService().customerBalance(uuid, privateBank);
			if (dep > 0) {
				JsonObject row = new JsonObject();
				row.addProperty("bank", privateBank.name());
				row.addProperty("balanceMg", dep);
				row.addProperty("balance", GoldStandard.formatMilligrams(dep));
				privateDeposits.add(row);
			}
		}
		data.add("privateDeposits", privateDeposits);

		JsonArray appeals = new JsonArray();
		for (Appeal appeal : manager.appealService().playerAppeals(uuid)) {
			JsonObject row = new JsonObject();
			row.addProperty("id", appeal.id());
			row.addProperty("status", appeal.status().name());
			row.addProperty("subject", appeal.subject());
			row.addProperty("message", appeal.message());
			appeals.add(row);
		}
		data.add("appeals", appeals);

		if (manager.prisonService().isJailed(uuid)) {
			manager.prisonService().sentenceFor(uuid).ifPresent(s -> {
				JsonObject prison = new JsonObject();
				prison.addProperty("reason", s.reason());
				prison.addProperty("remainingMs", s.remainingMs());
				prison.addProperty("releaseAt", s.releaseAt());
				data.add("prison", prison);
			});
		}

		JsonArray myReports = new JsonArray();
		for (CitizenReport report : manager.reportService().reporterHistory(uuid)) {
			JsonObject row = new JsonObject();
			row.addProperty("id", report.id());
			row.addProperty("type", report.type().displayName());
			row.addProperty("status", report.status().name());
			row.addProperty("targetName", report.targetName());
			row.addProperty("subject", report.subject());
			myReports.add(row);
		}
		data.add("myReports", myReports);

		JsonArray market = new JsonArray();
		for (Commodity commodity : Commodity.values()) {
			if (!commodity.sellable()) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("id", commodity.id());
			row.addProperty("name", commodity.displayName());
			long priceMg = manager.marketService().priceEngine().getUnitPrice(commodity);
			row.addProperty("priceMg", priceMg);
			enrichCommodityRow(row, manager.marketService(), commodity, priceMg);
			market.add(row);
		}
		data.add("market", market);

		if (manager.propertyService() != null) {
			JsonArray props = new JsonArray();
			for (var p : manager.propertyService().forOwner(uuid)) {
				JsonObject row = new JsonObject();
				row.addProperty("id", p.id());
				row.addProperty("tier", p.tier());
				row.addProperty("x", p.origin().getX());
				row.addProperty("z", p.origin().getZ());
				row.addProperty("plotIndex", p.plotIndex());
				props.add(row);
			}
			data.add("properties", props);
		}
		if (manager.vehicleService() != null) {
			JsonArray cars = new JsonArray();
			for (var v : manager.vehicleService().forOwner(uuid)) {
				JsonObject row = new JsonObject();
				row.addProperty("id", v.id());
				row.addProperty("model", v.model());
				row.addProperty("fuel", v.fuel());
				row.addProperty("spawned", v.spawned());
				cars.add(row);
			}
			data.add("vehicles", cars);
		}
		return data;
	}

	private static JsonObject portfolioHoldingRow(String kind, String symbol, String name, int amount,
			long priceMg, JsonArray history) {
		JsonObject row = new JsonObject();
		row.addProperty("kind", kind);
		row.addProperty("symbol", symbol);
		row.addProperty("name", name);
		row.addProperty("amount", amount);
		row.addProperty("priceMg", priceMg);
		row.addProperty("valueMg", priceMg * amount);
		row.add("history", history);
		row.addProperty("changeBps", priceChangeBps(history, priceMg));
		return row;
	}

	private static JsonObject portfolioLeverageRow(com.mceconomy.exchange.LeverageService.PositionView pos,
			JsonArray history) {
		JsonObject row = new JsonObject();
		row.addProperty("kind", "LEVERAGE");
		row.addProperty("positionId", pos.id());
		row.addProperty("symbol", pos.symbol());
		row.addProperty("name", pos.symbol());
		row.addProperty("side", pos.isLong() ? "LONG" : "SHORT");
		row.addProperty("leverage", pos.leverage());
		row.addProperty("amount", 1);
		row.addProperty("entryPriceMg", pos.entryPriceMg());
		row.addProperty("priceMg", pos.currentPriceMg());
		row.addProperty("marginMg", pos.marginMg());
		row.addProperty("pnlMg", pos.pnlMg());
		row.addProperty("equityMg", pos.equityMg());
		row.addProperty("valueMg", pos.equityMg());
		row.add("history", history);
		row.addProperty("changeBps", priceChangeBps(history, pos.currentPriceMg()));
		return row;
	}

	private static void addFiatMacro(JsonObject data, com.mceconomy.tax.CentralBank cb) {
		data.addProperty("fiatStrength", cb.getFiatStrength());
		data.addProperty("goldBackingPct", Math.round(cb.getGoldBackingScore() * 1000) / 10.0);
		data.addProperty("stateCredibilityPct", Math.round(cb.getStateCredibilityScore() * 1000) / 10.0);
		data.addProperty("investmentPct", Math.round(cb.getInvestmentScore() * 1000) / 10.0);
		data.addProperty("fiatShockPenalty", cb.getFiatShockPenalty());
	}

	private static String categoryLabel(String category) {
		return switch (category) {
			case "ROBBERY" -> "SOYGUN";
			case "STORAGE" -> "DEPO";
			case "MACRO" -> "MAKRO";
			default -> category;
		};
	}

	private static JsonArray buildIllegalGoods(EconomyManager manager) {
		JsonArray arr = new JsonArray();
		for (IllegalGood good : IllegalGood.values()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", good.id());
			row.addProperty("name", good.displayName());
			arr.add(row);
		}
		for (var custom : manager.customBlackMarket().all()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", "custom:" + custom.id());
			row.addProperty("name", custom.displayName());
			arr.add(row);
		}
		return arr;
	}

	private static JsonArray buildTokenList(EconomyManager manager) {
		JsonArray arr = new JsonArray();
		for (var token : manager.exchangeService().allTokens()) {
			JsonObject row = new JsonObject();
			row.addProperty("symbol", token.symbol());
			row.addProperty("priceMg", token.priceMg());
			arr.add(row);
		}
		return arr;
	}

	private static JsonArray buildCompanyList(EconomyManager manager) {
		JsonArray arr = new JsonArray();
		double index = manager.marketService().economyIndex().calculate();
		for (var company : manager.companyManager().allCompanies()) {
			if (!company.listedOnExchange()) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("name", company.name());
			row.addProperty("ticker", company.ticker());
			row.addProperty("priceMg", company.sharePrice(index));
			arr.add(row);
		}
		return arr;
	}
}
