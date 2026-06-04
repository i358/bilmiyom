package com.mceconomy.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mceconomy.McEconomyMod;
import com.mceconomy.appeal.Appeal;
import com.mceconomy.justice.CitizenReport;
import com.mceconomy.justice.PrisonSentence;
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
import com.mceconomy.market.Commodity;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.privatebank.PrivateBank;
import com.mceconomy.regulation.MasakAlert;
import com.mceconomy.web.DashboardActionService.ActionResult;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class EconomyWebServer {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private final WebSessionManager sessionManager = new WebSessionManager();
	private HttpServer server;

	public void start() {
		if (!EconomyConfig.webDashboardEnabled()) {
			return;
		}
		try {
			server = HttpServer.create(new InetSocketAddress(EconomyConfig.webBindAddress(), EconomyConfig.webPort()), 0);
			server.createContext("/", this::handleStatic);
			server.createContext("/admin", exchange -> serveResource(exchange, "/dashboard/admin.html"));
			server.createContext("/api/login", this::handleLogin);
			server.createContext("/api/setup-password", this::handleSetupPassword);
			server.createContext("/api/logout", this::handleLogout);
			server.createContext("/api/me", this::handleMe);
			server.createContext("/api/catalog", this::handleCatalog);
			server.createContext("/api/workforce", this::handleWorkforce);
			server.createContext("/api/inventory", this::handleInventory);
			server.createContext("/api/prices", this::handlePrices);
			server.createContext("/api/charts/overview", this::handleChartsOverview);
			server.createContext("/api/bulletins", this::handleBulletins);
			server.createContext("/api/world/map", this::handleWorldMap);
			server.createContext("/api/admin/security/cameras", this::handleAdminSecurityCameras);
			server.createContext("/api/charts/portfolio", this::handleChartsPortfolio);
			server.createContext("/api/employment", this::handleEmployment);
			server.createContext("/api/trades", this::handleTrades);
			server.createContext("/api/admin/trades/disputes", this::handleAdminTradeDisputes);
			server.createContext("/api/actions/", this::handlePlayerAction);
			server.createContext("/api/admin/overview", this::handleAdminOverview);
			server.createContext("/api/admin/report", this::handleAdminReport);
			server.createContext("/api/admin/players", this::handleAdminPlayers);
			server.createContext("/api/admin/player", this::handleAdminPlayer);
			server.createContext("/api/admin/economy/catalog", this::handleAdminEconomyCatalog);
			server.createContext("/api/admin/config", this::handleAdminConfig);
			server.createContext("/api/admin/appeals", this::handleAdminAppeals);
			server.createContext("/api/admin/appeals/accept", this::handleAppealAccept);
			server.createContext("/api/admin/appeals/reject", this::handleAppealReject);
			server.createContext("/api/admin/justice/reports", this::handleAdminJusticeReports);
			server.createContext("/api/admin/justice/prison", this::handleAdminJusticePrison);
			server.createContext("/api/admin/actions/", this::handleAdminAction);
			server.setExecutor(Executors.newFixedThreadPool(6));
			server.start();
			McEconomyMod.LOGGER.info("Ekonomi dashboard: http://{}:{}/",
					EconomyConfig.webBindAddress(), EconomyConfig.webPort());
		} catch (IOException e) {
			McEconomyMod.LOGGER.error("Web dashboard başlatılamadı", e);
		}
	}

	public void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
	}

	private void handleStatic(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		if (path.equals("/") || path.equals("/index.html")) {
			serveResource(exchange, "/dashboard/index.html");
			return;
		}
		if (path.startsWith("/dashboard/")) {
			serveResource(exchange, path);
			return;
		}
		sendJson(exchange, 404, error("not_found", "Sayfa bulunamadı"));
	}

	private void handleLogin(HttpExchange exchange) throws IOException {
		if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
			sendJson(exchange, 405, error("method", "POST gerekli"));
			return;
		}
		JsonObject body = readJson(exchange);
		String username = text(body, "username");
		String password = text(body, "password");
		if (username == null || password == null) {
			sendJson(exchange, 400, error("invalid", "Kullanıcı adı ve şifre gerekli"));
			return;
		}
		PlayerEconomyProfile profile = findProfileByName(username);
		if (profile == null) {
			sendJson(exchange, 401, error("not_found",
					"Oyuncu bulunamadı. Önce Minecraft sunucusuna giriş yapın."));
			return;
		}
		if (!DashboardPasswordService.hasPassword(profile)) {
			sendJson(exchange, 401, error("no_password",
					"Henüz şifre yok. Oyunda /panel sifre <şifre> veya sitede «İlk şifre belirle» kullanın (çevrimiçi olmalısınız)."));
			return;
		}
		if (!DashboardPasswordService.verify(profile, password)) {
			sendJson(exchange, 401, error("auth", "Şifre hatalı."));
			return;
		}
		boolean op = isOnlineOp(profile.uuid());
		String token = sessionManager.create(profile.uuid(), profile.name(), op);
		JsonObject ok = new JsonObject();
		ok.addProperty("token", token);
		ok.addProperty("playerName", profile.name());
		ok.addProperty("op", op);
		sendJson(exchange, 200, ok);
	}

	private void handleSetupPassword(HttpExchange exchange) throws IOException {
		if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
			sendJson(exchange, 405, error("method", "POST gerekli"));
			return;
		}
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || !manager.isLoaded()) {
			sendJson(exchange, 503, error("unavailable", "Ekonomi sistemi hazır değil"));
			return;
		}
		JsonObject body = readJson(exchange);
		String username = text(body, "username");
		String password = text(body, "password");
		if (username == null || password == null) {
			sendJson(exchange, 400, error("invalid", "Kullanıcı adı ve şifre gerekli"));
			return;
		}
		password = password.trim();
		if (password.length() < 4) {
			sendJson(exchange, 400, error("invalid", "Şifre en az 4 karakter olmalı"));
			return;
		}
		PlayerEconomyProfile profile = findProfileByName(username);
		if (profile == null) {
			sendJson(exchange, 404, error("not_found", "Kayıt yok. Önce sunucuya giriş yapın."));
			return;
		}
		if (DashboardPasswordService.hasPassword(profile)) {
			sendJson(exchange, 409, error("exists", "Şifre zaten ayarlı. Giriş yapın."));
			return;
		}
		if (!isOnline(profile.uuid())) {
			sendJson(exchange, 403, error("offline",
					"İlk şifre için oyunda çevrimiçi olmalısınız."));
			return;
		}
		try {
			DashboardPasswordService.setPassword(profile, password);
			manager.playerRepository().save(profile);
			JsonObject ok = new JsonObject();
			ok.addProperty("success", true);
			ok.addProperty("message", "Dashboard şifreniz kaydedildi. Şimdi giriş yapabilirsiniz.");
			sendJson(exchange, 200, ok);
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("Web dashboard sifre kaydi", e);
			sendJson(exchange, 500, error("save", "Şifre kaydedilemedi: " + e.getMessage()));
		}
	}

	private void handleLogout(HttpExchange exchange) throws IOException {
		sessionManager.revoke(bearer(exchange));
		sendJson(exchange, 200, ok("logged_out", true));
	}

	private void handleMe(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		JsonObject me = buildPortfolio(session.get().playerUuid());
		me.addProperty("op", session.get().op());
		sendJson(exchange, 200, me);
	}

	private void handleCatalog(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		sendJson(exchange, 200, buildCatalog());
	}

	private void handleWorkforce(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		UUID uuid = session.get().playerUuid();
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
		sendJson(exchange, 200, data);
	}

	private void handleEmployment(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		UUID uuid = session.get().playerUuid();
		var manager = McEconomyMod.getEconomyManager();
		JsonObject data = new JsonObject();
		JsonArray companies = new JsonArray();
		for (Company company : manager.companyManager().allCompanies()) {
			JsonObject row = new JsonObject();
			row.addProperty("name", company.name());
			companies.add(row);
		}
		data.add("companies", companies);
		JsonArray salaryHistory = new JsonArray();
		for (var pay : manager.playerEmploymentService().salaryHistory(uuid)) {
			JsonObject row = new JsonObject();
			row.addProperty("amountMg", pay.amountMg());
			row.addProperty("bonusMg", pay.bonusMg());
			row.addProperty("amount", GoldStandard.formatMilligrams(pay.amountMg() + pay.bonusMg()));
			row.addProperty("paidAt", pay.paidAt());
			salaryHistory.add(row);
		}
		data.add("salaryHistory", salaryHistory);
		manager.playerEmploymentService().employmentForPlayer(uuid).ifPresent(emp -> {
			Company company = manager.companyManager().allCompanies().stream()
					.filter(c -> c.id() == emp.companyId()).findFirst().orElse(null);
			JsonObject job = new JsonObject();
			job.addProperty("company", company != null ? company.name() : "?");
			job.addProperty("role", emp.roleId());
			job.addProperty("salaryMg", emp.salaryMg());
			job.addProperty("salary", GoldStandard.formatMilligrams(emp.salaryMg()));
			data.add("employment", job);
		});
		manager.playerEmploymentService().pendingApplicationForPlayer(uuid).ifPresent(app -> {
			Company company = manager.companyManager().allCompanies().stream()
					.filter(c -> c.id() == app.companyId()).findFirst().orElse(null);
			JsonObject pending = new JsonObject();
			pending.addProperty("company", company != null ? company.name() : "?");
			pending.addProperty("role", app.roleId());
			pending.addProperty("salaryMg", app.requestedSalaryMg());
			pending.addProperty("salary", GoldStandard.formatMilligrams(app.requestedSalaryMg()));
			data.add("pendingApplication", pending);
		});
		sendJson(exchange, 200, data);
	}

	private void handleTrades(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		UUID uuid = session.get().playerUuid();
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
		sendJson(exchange, 200, data);
	}

	private void handleAdminTradeDisputes(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireOp(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
		JsonArray list = new JsonArray();
		for (var d : McEconomyMod.getEconomyManager().playerTradeService().openDisputes()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", d.id());
			row.addProperty("tradeId", d.tradeId());
			row.addProperty("reporter", d.reporterName());
			row.addProperty("target", d.targetName());
			row.addProperty("reason", d.reason());
			row.addProperty("createdAt", d.createdAt());
			list.add(row);
		}
		JsonObject data = new JsonObject();
		data.add("disputes", list);
		sendJson(exchange, 200, data);
	}

	private void handleInventory(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		UUID uuid = session.get().playerUuid();
		DashboardActionService.ActionResult result = runOnServer(() -> {
			EconomyManager manager = McEconomyMod.getEconomyManager();
			ServerPlayer player = manager.server() != null ? manager.server().getPlayerList().getPlayer(uuid) : null;
			JsonObject data = new JsonObject();
			JsonArray items = new JsonArray();
			if (player == null) {
				data.addProperty("online", false);
				data.add("items", items);
				return DashboardActionService.ActionResult.ok("offline", data);
			}
			data.addProperty("online", true);
			java.util.Map<String, JsonObject> aggregated = new java.util.LinkedHashMap<>();
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
					com.mceconomy.market.Commodity commodity = com.mceconomy.market.Commodity.fromItem(stack.getItem());
					if (commodity != null && commodity.sellable()) {
						row.addProperty("commodityId", commodity.id());
					}
					aggregated.put(itemId, row);
				}
				row.addProperty("count", row.get("count").getAsInt() + stack.getCount());
			}
			aggregated.values().forEach(items::add);
			data.add("items", items);
			return DashboardActionService.ActionResult.ok("ok", data);
		});
		sendJson(exchange, 200, result.data() != null ? result.data() : new JsonObject());
	}

	private void handlePlayerAction(HttpExchange exchange) throws IOException {
		if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
			sendJson(exchange, 405, error("method", "POST gerekli"));
			return;
		}
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		String path = exchange.getRequestURI().getPath();
		String action = path.substring("/api/actions/".length());
		UUID uuid = session.get().playerUuid();
		JsonObject body = readJson(exchange);
		ActionResult result = runOnServer(() -> dispatchPlayerAction(uuid, action, body));
		sendJson(exchange, result.success() ? 200 : 400, result.toJson());
	}

	private ActionResult dispatchPlayerAction(UUID uuid, String action, JsonObject body) {
		return switch (action) {
			case "pay" -> DashboardActionService.pay(uuid, text(body, "target"), displayMcVal(body, 0));
			case "bank/open-checking" -> DashboardActionService.bankOpenChecking(uuid);
			case "bank/open-term" -> DashboardActionService.bankOpenTerm(uuid);
			case "bank/transfer" -> DashboardActionService.bankTransfer(uuid, text(body, "target"), displayMcVal(body, 0));
			case "bank/wallet-deposit" -> DashboardActionService.bankWalletDeposit(uuid, displayMcVal(body, 0));
			case "bank/wallet-withdraw" -> DashboardActionService.bankWalletWithdraw(uuid, displayMcVal(body, 0));
			case "bank/deposit-ingots" -> withOnline(uuid, p -> DashboardActionService.bankDepositIngots(p, intVal(body, "ingots", 0)));
			case "bank/withdraw-ingots" -> withOnline(uuid, p -> DashboardActionService.bankWithdrawIngots(p, intVal(body, "ingots", 0)));
			case "market/buy" -> withOnline(uuid, p -> DashboardActionService.marketBuy(p, text(body, "commodity"), intVal(body, "quantity", 0)));
			case "market/sell" -> withOnline(uuid, p -> DashboardActionService.marketSell(p, text(body, "commodity"), intVal(body, "quantity", 0)));
			case "loan/take" -> DashboardActionService.loanTake(uuid, displayMcVal(body, 0));
			case "loan/pay" -> DashboardActionService.loanPay(uuid);
			case "job/set" -> DashboardActionService.setJob(uuid, text(body, "job"));
			case "job/resign" -> DashboardActionService.resignJob(uuid);
			case "quest/assign" -> withOnline(uuid, DashboardActionService::assignQuest);
			case "quest/complete" -> withOnline(uuid, DashboardActionService::completeQuest);
			case "quest/cancel" -> DashboardActionService.cancelQuest(uuid);
			case "company/create" -> DashboardActionService.createCompany(uuid, text(body, "name"));
			case "shares/buy" -> DashboardActionService.buyShares(uuid, text(body, "company"), intVal(body, "amount", 0));
			case "shares/sell" -> DashboardActionService.sellShares(uuid, text(body, "company"), intVal(body, "amount", 0));
			case "shares/sell-all" -> DashboardActionService.sellAllShares(uuid);
			case "exchange/token/sell-all" -> DashboardActionService.sellAllTokens(uuid);
			case "exchange/token/buy" -> DashboardActionService.buyToken(uuid, text(body, "symbol"), intVal(body, "amount", 0));
			case "exchange/token/sell" -> DashboardActionService.sellToken(uuid, text(body, "symbol"), intVal(body, "amount", 0));
			case "exchange/token/create" -> DashboardActionService.createToken(uuid, text(body, "symbol"), text(body, "name"),
					intVal(body, "supply", 0), longVal(body, "priceMg", 0));
			case "exchange/list" -> DashboardActionService.listCompany(uuid, text(body, "company"), text(body, "ticker"));
			case "exchange/delist" -> DashboardActionService.delistCompany(uuid, text(body, "company"));
			case "exchange/leverage/open" -> DashboardActionService.openLeverage(uuid, text(body, "symbol"),
					"long".equalsIgnoreCase(text(body, "side")), intVal(body, "leverage", 2), displayMcVal(body, 0));
			case "exchange/leverage/close" -> DashboardActionService.closeLeverage(uuid, intVal(body, "positionId", -1));
			case "casino/play" -> DashboardActionService.casinoPlay(uuid, text(body, "game"), displayMcVal(body, 0), text(body, "choice"));
			case "company/employee/fire" -> DashboardActionService.fireEmployee(uuid, longVal(body, "employeeId", -1));
			case "company/employee/raise" -> DashboardActionService.raiseSalary(uuid, longVal(body, "employeeId", -1), displayMcVal(body, 0));
			case "company/employee/bonus" -> DashboardActionService.payBonus(uuid, text(body, "company"));
			case "company/stash/collect" -> DashboardActionService.teleportCompanyVault(uuid, text(body, "company"));
			case "company/vault/teleport" -> DashboardActionService.teleportCompanyVault(uuid, text(body, "company"));
			case "company/vault/exit" -> DashboardActionService.exitCompanyVault(uuid);
			case "company/application/accept" -> DashboardActionService.acceptApplication(uuid, longVal(body, "applicationId", -1));
			case "company/application/reject" -> DashboardActionService.rejectApplication(uuid, longVal(body, "applicationId", -1));
			case "vault/teleport" -> withOnline(uuid, DashboardActionService::teleportVault);
			case "vault/back" -> withOnline(uuid, DashboardActionService::vaultBack);
			case "inventory/market-sell" -> withOnline(uuid, p -> DashboardActionService.inventoryMarketSell(
					p, text(body, "itemId"), intVal(body, "quantity", 0)));
			case "inventory/blackmarket-list" -> withOnline(uuid, p -> DashboardActionService.inventoryBlackMarketList(
					p, text(body, "itemId"), intVal(body, "quantity", 0), displayMcVal(body, 0)));
			case "heist/start" -> withOnline(uuid, DashboardActionService::startHeist);
			case "private-bank/certify" -> DashboardActionService.purchaseCert(uuid);
			case "private-bank/open" -> DashboardActionService.openPrivateBank(uuid, text(body, "name"));
			case "private-bank/deposit" -> DashboardActionService.privateDeposit(uuid, text(body, "bank"), displayMcVal(body, 0));
			case "private-bank/withdraw" -> DashboardActionService.privateWithdraw(uuid, text(body, "bank"), displayMcVal(body, 0));
			case "appeal/submit" -> {
				Long alertId = body.has("alertId") && !body.get("alertId").isJsonNull()
						? body.get("alertId").getAsLong() : null;
				PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(uuid);
				yield DashboardActionService.submitAppeal(uuid, profile != null ? profile.name() : "?", text(body, "subject"), text(body, "message"), alertId);
			}
			case "justice/complaint" -> {
				PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(uuid);
				yield DashboardActionService.submitComplaint(uuid, profile != null ? profile.name() : "?",
						text(body, "target"), text(body, "category"), text(body, "subject"), text(body, "message"));
			}
			case "justice/tipoff" -> {
				PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(uuid);
				yield DashboardActionService.submitTipOff(uuid, profile != null ? profile.name() : "?",
						text(body, "target"), text(body, "category"), text(body, "message"));
			}
			case "blackmarket/buy" -> withOnline(uuid, p -> DashboardActionService.blackMarketBuy(p, text(body, "good"), intVal(body, "quantity", 0)));
			case "blackmarket/sell" -> withOnline(uuid, p -> DashboardActionService.blackMarketSell(p, text(body, "good"), intVal(body, "quantity", 0)));
			case "launder" -> withOnline(uuid, p -> DashboardActionService.launder(p, displayMcVal(body, 0)));
			case "employment/apply" -> DashboardActionService.employmentApply(uuid, text(body, "company"),
					text(body, "role"), longVal(body, "salaryMg", 0));
			case "employment/cancel-application" -> DashboardActionService.employmentCancelApplication(uuid);
			case "employment/quit" -> DashboardActionService.employmentQuit(uuid);
			default -> ActionResult.fail("Bilinmeyen işlem: " + action);
		};
	}

	private void handleAdminAction(HttpExchange exchange) throws IOException {
		if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
			sendJson(exchange, 405, error("method", "POST gerekli"));
			return;
		}
		Optional<WebSession> session = requireOp(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
		String path = exchange.getRequestURI().getPath();
		String action = path.substring("/api/admin/actions/".length());
		JsonObject body = readJson(exchange);
		String adminName = session.get().playerName();
		ActionResult result = runOnServer(() -> dispatchAdminAction(action, body, adminName));
		sendJson(exchange, result.success() ? 200 : 400, result.toJson());
	}

	private ActionResult dispatchAdminAction(String action, JsonObject body, String adminName) {
		return switch (action) {
			case "blackmarket/add" -> DashboardActionService.addCustomBlackMarket(text(body, "name"), text(body, "itemId"), displayMcVal(body, 0));
			case "blackmarket/remove" -> DashboardActionService.removeCustomBlackMarket(text(body, "id"));
			case "masak/resolve" -> DashboardActionService.masakResolve(text(body, "player"));
			case "masak/fine" -> DashboardActionService.masakFine(text(body, "player"), displayMcVal(body, 0));
			case "masak/blacklist" -> DashboardActionService.masakBlacklist(text(body, "player"));
			case "event/trigger" -> DashboardActionService.triggerEvent(text(body, "type"),
					intVal(body, "durationSeconds", 300) * 1000L);
			case "mbop/grant" -> DashboardActionService.mbopGrant(text(body, "player"));
			case "mbop/revoke" -> DashboardActionService.mbopRevoke(text(body, "player"));
			case "central-bank/rebuild" -> DashboardActionService.rebuildCentralBank();
			case "justice/investigate" -> DashboardActionService.justiceInvestigate(longVal(body, "id", -1));
			case "justice/dismiss" -> DashboardActionService.justiceDismiss(longVal(body, "id", -1), text(body, "note"));
			case "justice/guilty" -> DashboardActionService.justiceGuilty(longVal(body, "id", -1), text(body, "note"),
					intVal(body, "prisonMinutes", 0), adminName != null ? adminName : "Dashboard OP");
			case "justice/prison/imprison" -> DashboardActionService.justiceImprison(text(body, "player"),
					intVal(body, "minutes", 5), text(body, "reason"), adminName != null ? adminName : "Dashboard OP");
			case "justice/prison/release" -> DashboardActionService.justiceReleasePrison(text(body, "player"));
			case "trade/dispute/refund" -> DashboardActionService.tradeDisputeResolve(adminName,
					longVal(body, "id", -1), true, text(body, "note"));
			case "trade/dispute/dismiss" -> DashboardActionService.tradeDisputeResolve(adminName,
					longVal(body, "id", -1), false, text(body, "note"));
			case "player/wallet/set" -> AdminEconomyService.walletSet(text(body, "player"), text(body, "uuid"),
					displayMcVal(body, 0));
			case "player/wallet/adjust" -> AdminEconomyService.walletAdjust(text(body, "player"), text(body, "uuid"),
					displayMcVal(body, 0));
			case "player/dirty/set" -> AdminEconomyService.dirtySet(text(body, "player"), text(body, "uuid"),
					displayMcVal(body, 0));
			case "player/bank/set" -> AdminEconomyService.bankSet(text(body, "player"), text(body, "uuid"),
					text(body, "type"), displayMcVal(body, 0));
			case "player/bank/open-checking" -> AdminEconomyService.bankOpenChecking(text(body, "player"),
					text(body, "uuid"));
			case "player/bank/open-term" -> AdminEconomyService.bankOpenTerm(text(body, "player"), text(body, "uuid"));
			case "player/bank/delete" -> AdminEconomyService.bankDelete(text(body, "player"), text(body, "uuid"),
					text(body, "type"));
			case "player/profile/update" -> AdminEconomyService.profileUpdate(text(body, "player"), text(body, "uuid"),
					body);
			case "player/loan/upsert" -> AdminEconomyService.loanUpsert(text(body, "player"), text(body, "uuid"),
					displayMcVal(body, 0), displayMcVal(body, "installmentMc", 0), longVal(body, "dueAt", 0));
			case "player/loan/delete" -> AdminEconomyService.loanDelete(text(body, "player"), text(body, "uuid"));
			case "player/shares/set" -> AdminEconomyService.sharesSet(text(body, "player"), text(body, "uuid"),
					text(body, "ticker"), intVal(body, "amount", 0));
			case "player/tokens/set" -> AdminEconomyService.tokensSet(text(body, "player"), text(body, "uuid"),
					text(body, "symbol"), intVal(body, "amount", 0));
			case "player/leverage/close" -> AdminEconomyService.leverageClose(intVal(body, "positionId", -1));
			case "player/private-deposit/set" -> AdminEconomyService.privateDepositSet(text(body, "player"),
					text(body, "uuid"), text(body, "bankName"), displayMcVal(body, 0));
			case "economy/central-bank/update" -> AdminEconomyService.centralBankUpdate(body);
			case "economy/company/create" -> AdminEconomyService.companyCreate(text(body, "name"),
					text(body, "owner"), text(body, "ticker"), displayMcVal(body, "treasuryMc", 0),
					body.has("listed") && body.get("listed").getAsBoolean());
			case "economy/company/update" -> AdminEconomyService.companyUpdate(text(body, "name"),
					body.has("treasuryMc") ? displayMcVal(body, "treasuryMc", 0) : null,
					text(body, "ticker"), body.has("listed") ? body.get("listed").getAsBoolean() : null);
			case "economy/company/delist" -> AdminEconomyService.companyDelist(text(body, "name"));
			case "economy/token/create" -> AdminEconomyService.tokenCreate(text(body, "symbol"),
					text(body, "displayName"), intVal(body, "supply", 0), displayMcVal(body, "priceMc", 0));
			case "economy/token/update" -> AdminEconomyService.tokenUpdate(text(body, "symbol"),
					body.has("priceMc") ? displayMcVal(body, "priceMc", 0) : null,
					body.has("circulating") ? intVal(body, "circulating", 0) : null);
			case "economy/token/delete" -> AdminEconomyService.tokenDelete(text(body, "symbol"));
			case "config/save" -> AdminEconomyService.configSave(text(body, "json"));
			default -> ActionResult.fail("Bilinmeyen admin işlemi: " + action);
		};
	}

	private ActionResult withOnline(UUID uuid, java.util.function.Function<ServerPlayer, ActionResult> action) {
		ServerPlayer player = DashboardActionService.onlinePlayer(uuid);
		if (player == null) {
			return ActionResult.fail("Bu işlem için oyunda çevrimiçi olmalısınız.");
		}
		return action.apply(player);
	}

	private void handleAdminReport(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireOp(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
		var cb = McEconomyMod.getEconomyManager().centralBank();
		var market = McEconomyMod.getEconomyManager().marketService();
		JsonObject data = new JsonObject();
		data.addProperty("moneySupply", cb.getMoneySupply());
		data.addProperty("economyIndex", cb.getEconomyIndex());
		data.addProperty("inflationRate", cb.getInflationRate());
		data.addProperty("baseRate", cb.getBaseRate());
		data.addProperty("marketIndex", market.economyIndex().calculate());
		data.addProperty("municipalBudgetMg", cb.getMunicipalBudgetMg());
		data.addProperty("municipalBudget", GoldStandard.formatMilligrams(cb.getMunicipalBudgetMg()));
		sendJson(exchange, 200, data);
	}

	private void handleAdminPlayers(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireOp(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
		var manager = McEconomyMod.getEconomyManager();
		JsonArray players = new JsonArray();
		for (PlayerEconomyProfile profile : manager.profiles().values()) {
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
		sendJson(exchange, 200, wrapper);
	}

	private void handleAdminPlayer(HttpExchange exchange) throws IOException {
		if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
			sendJson(exchange, 405, error("method", "GET gerekli"));
			return;
		}
		Optional<WebSession> session = requireOp(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
		String query = exchange.getRequestURI().getQuery();
		String name = queryParam(query, "name");
		String uuidStr = queryParam(query, "uuid");
		UUID uuid = null;
		if (uuidStr != null && !uuidStr.isBlank()) {
			try {
				uuid = UUID.fromString(uuidStr.trim());
			} catch (IllegalArgumentException ignored) {
				sendJson(exchange, 400, error("invalid", "Geçersiz UUID"));
				return;
			}
		} else if (name != null && !name.isBlank()) {
			PlayerEconomyProfile profile = findProfileByName(name);
			if (profile == null) {
				sendJson(exchange, 404, error("not_found", "Oyuncu bulunamadı"));
				return;
			}
			uuid = profile.uuid();
		} else {
			sendJson(exchange, 400, error("invalid", "name veya uuid gerekli"));
			return;
		}
		JsonObject detail = buildAdminPlayerDetail(uuid);
		if (!detail.has("name")) {
			sendJson(exchange, 404, error("not_found", "Oyuncu bulunamadı"));
			return;
		}
		sendJson(exchange, 200, detail);
	}

	private void handleAdminConfig(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireOp(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
		if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
			sendJson(exchange, 405, error("method", "GET gerekli"));
			return;
		}
		ActionResult result = AdminEconomyService.configRead();
		if (!result.success()) {
			sendJson(exchange, 400, result.toJson());
			return;
		}
		sendJson(exchange, 200, result.data());
	}

	private void handleAdminEconomyCatalog(HttpExchange exchange) throws IOException {
		if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
			sendJson(exchange, 405, error("method", "GET gerekli"));
			return;
		}
		Optional<WebSession> session = requireOp(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
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
			data.add("centralBank", macro);
		}
		sendJson(exchange, 200, data);
	}

	private static String queryParam(String query, String key) {
		if (query == null || query.isBlank()) {
			return null;
		}
		for (String part : query.split("&")) {
			int eq = part.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			if (part.substring(0, eq).equals(key)) {
				return java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
			}
		}
		return null;
	}

	private JsonObject buildCatalog() {
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
			row.addProperty("priceMg", manager.marketService().priceEngine().getUnitPrice(commodity));
			row.addProperty("buyable", commodity.buyable());
			row.addProperty("sellable", commodity.sellable());
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

	private ActionResult runOnServer(Supplier<ActionResult> action) {
		MinecraftServer mcServer = McEconomyMod.getEconomyManager().server();
		if (mcServer == null) {
			return ActionResult.fail("Sunucu hazır değil.");
		}
		if (mcServer.isSameThread()) {
			return action.get();
		}
		CompletableFuture<ActionResult> future = new CompletableFuture<>();
		mcServer.execute(() -> {
			try {
				future.complete(action.get());
			} catch (Exception e) {
				McEconomyMod.LOGGER.error("Dashboard işlemi hatası", e);
				future.complete(ActionResult.fail("İşlem hatası: " + e.getMessage()));
			}
		});
		try {
			return future.get(15, TimeUnit.SECONDS);
		} catch (Exception e) {
			return ActionResult.fail("İşlem zaman aşımına uğradı.");
		}
	}

	private void handlePrices(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		String query = exchange.getRequestURI().getQuery();
		String symbol = "bugday";
		String type = "COMMODITY";
		if (query != null) {
			for (String part : query.split("&")) {
				String[] kv = part.split("=", 2);
				if (kv.length == 2 && kv[0].equals("symbol")) {
					symbol = kv[1];
				}
				if (kv.length == 2 && kv[0].equals("type")) {
					type = kv[1];
				}
			}
		}
		JsonObject response = new JsonObject();
		response.addProperty("symbol", symbol);
		response.addProperty("type", type);
		response.add("history", loadHistory(type, symbol, 48));
		sendJson(exchange, 200, response);
	}

	private void handleBulletins(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		String category = null;
		int limit = 50;
		String query = exchange.getRequestURI().getQuery();
		if (query != null) {
			for (String part : query.split("&")) {
				String[] kv = part.split("=", 2);
				if (kv.length == 2 && kv[0].equals("category")) {
					category = kv[1];
				}
				if (kv.length == 2 && kv[0].equals("limit")) {
					try {
						limit = Integer.parseInt(kv[1]);
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
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
		sendJson(exchange, 200, data);
	}

	private static String categoryLabel(String category) {
		return switch (category) {
			case "ROBBERY" -> "SOYGUN";
			case "STORAGE" -> "DEPO";
			case "MACRO" -> "MAKRO";
			default -> category;
		};
	}

	private void handleWorldMap(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		String track = null;
		String query = exchange.getRequestURI().getQuery();
		if (query != null) {
			for (String part : query.split("&")) {
				String[] kv = part.split("=", 2);
				if (kv.length == 2 && kv[0].equals("track")) {
					track = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
				}
			}
		}
		WebSession s = session.get();
		sendJson(exchange, 200, WorldMapService.buildMapData(s.playerUuid(), s.op(), track));
	}

	private void handleAdminSecurityCameras(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		if (!session.get().op()) {
			sendJson(exchange, 403, error("forbidden", "OP gerekli"));
			return;
		}
		String night = null;
		int limit = 500;
		String query = exchange.getRequestURI().getQuery();
		if (query != null) {
			for (String part : query.split("&")) {
				String[] kv = part.split("=", 2);
				if (kv.length == 2 && kv[0].equals("night")) {
					night = kv[1];
				}
				if (kv.length == 2 && kv[0].equals("limit")) {
					try {
						limit = Integer.parseInt(kv[1]);
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		var manager = McEconomyMod.getEconomyManager();
		JsonObject data = new JsonObject();
		if (manager == null || manager.securityCameraService() == null) {
			data.add("logs", new JsonArray());
			data.add("nights", new JsonArray());
			sendJson(exchange, 200, data);
			return;
		}
		try {
			JsonArray nights = new JsonArray();
			for (String key : manager.securityCameraService().listNights()) {
				nights.add(key);
			}
			data.add("nights", nights);
			data.addProperty("currentNight", manager.securityCameraService().currentNightKey());
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
				var nightKeys = manager.securityCameraService().listNights();
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
			sendJson(exchange, 500, error("db", "Kamera kayitlari yuklenemedi"));
			return;
		}
		sendJson(exchange, 200, data);
	}

	private void handleChartsOverview(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		var manager = McEconomyMod.getEconomyManager();
		JsonObject data = new JsonObject();
		data.addProperty("economyIndex", manager.marketService().economyIndex().calculate());
		data.add("indexHistory", loadHistory("INDEX", "economy", 48));

		JsonArray commodities = new JsonArray();
		for (Commodity commodity : Commodity.values()) {
			if (!commodity.sellable()) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("id", commodity.id());
			row.addProperty("name", commodity.displayName());
			row.addProperty("priceMg", manager.marketService().priceEngine().getUnitPrice(commodity));
			row.addProperty("category", commodity.jobCategory().name());
			commodities.add(row);
		}
		data.add("commodities", commodities);

		JsonArray tokens = new JsonArray();
		for (ExchangeToken token : manager.exchangeService().allTokens()) {
			JsonObject row = new JsonObject();
			row.addProperty("symbol", token.symbol());
			row.addProperty("name", token.displayName());
			row.addProperty("priceMg", token.priceMg());
			row.add("history", loadHistory("TOKEN", token.symbol(), 24));
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
		sendJson(exchange, 200, data);
	}

	private void handleChartsPortfolio(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		UUID uuid = session.get().playerUuid();
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
		sendJson(exchange, 200, data);
	}

	private JsonObject portfolioHoldingRow(String kind, String symbol, String name, int amount,
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

	private JsonObject portfolioLeverageRow(com.mceconomy.exchange.LeverageService.PositionView pos,
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

	/** Gecmis penceresinde en eski fiyat -> guncel fiyat (basis points). */
	private static long priceChangeBps(JsonArray history, long currentPriceMg) {
		if (history == null || history.isEmpty() || currentPriceMg <= 0) {
			return 0;
		}
		long oldestPrice = history.get(0).getAsJsonObject().get("priceMg").getAsLong();
		if (oldestPrice <= 0) {
			return 0;
		}
		return Math.round((currentPriceMg - oldestPrice) * 10000.0 / oldestPrice);
	}

	private JsonArray loadHistory(String type, String symbol, int limit) {
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

	private void handleAppealAccept(HttpExchange exchange) throws IOException {
		if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
			sendJson(exchange, 405, error("method", "POST gerekli"));
			return;
		}
		if (requireOp(exchange).isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
		resolveAppeal(exchange, true);
	}

	private void handleAppealReject(HttpExchange exchange) throws IOException {
		if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
			sendJson(exchange, 405, error("method", "POST gerekli"));
			return;
		}
		if (requireOp(exchange).isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
		resolveAppeal(exchange, false);
	}

	private void resolveAppeal(HttpExchange exchange, boolean accept) throws IOException {
		JsonObject body = readJson(exchange);
		if (!body.has("id")) {
			sendJson(exchange, 400, error("invalid", "İtiraz id gerekli"));
			return;
		}
		long id = body.get("id").getAsLong();
		String note = text(body, "note");
		if (note == null || note.isBlank()) {
			note = accept ? "Dashboard uzerinden kabul edildi" : "Dashboard uzerinden reddedildi";
		}
		try {
			boolean ok = accept
					? McEconomyMod.getEconomyManager().appealService().accept(id, note)
					: McEconomyMod.getEconomyManager().appealService().reject(id, note);
			if (ok) {
				JsonObject result = new JsonObject();
				result.addProperty("success", true);
				result.addProperty("id", id);
				result.addProperty("status", accept ? "ACCEPTED" : "REJECTED");
				sendJson(exchange, 200, result);
				return;
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("İtiraz işlenemedi", e);
		}
		sendJson(exchange, 400, error("failed", "İtiraz işlenemedi"));
	}

	private void handleAdminOverview(HttpExchange exchange) throws IOException {
		if (requireOp(exchange).isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
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
		sendJson(exchange, 200, data);
	}

	private void handleAdminAppeals(HttpExchange exchange) throws IOException {
		if (requireOp(exchange).isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
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
		sendJson(exchange, 200, wrapper);
	}

	private void handleAdminJusticeReports(HttpExchange exchange) throws IOException {
		if (requireOp(exchange).isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
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
		sendJson(exchange, 200, wrapper);
	}

	private void handleAdminJusticePrison(HttpExchange exchange) throws IOException {
		if (requireOp(exchange).isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
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
		sendJson(exchange, 200, wrapper);
	}

	private JsonObject buildPortfolio(UUID uuid) {
		var manager = McEconomyMod.getEconomyManager();
		PlayerEconomyProfile profile = manager.profiles().get(uuid);
		JsonObject data = new JsonObject();
		if (profile == null) {
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
			data.addProperty("municipalBudgetMg", manager.centralBank().getMunicipalBudgetMg());
			data.addProperty("municipalBudget", GoldStandard.formatMilligrams(manager.centralBank().getMunicipalBudgetMg()));
		}
		data.addProperty("certCostMg", com.mceconomy.config.EconomyConfig.bankCertificateCostMg());
		data.addProperty("certCost", GoldStandard.formatMilligrams(com.mceconomy.config.EconomyConfig.bankCertificateCostMg()));
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
			job.addProperty("nextPayMs", emp.lastPaidAt() + com.mceconomy.config.EconomyConfig.playerDailySalaryIntervalMs());
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
			row.addProperty("priceMg", manager.marketService().priceEngine().getUnitPrice(commodity));
			market.add(row);
		}
		data.add("market", market);
		return data;
	}

	private JsonObject buildAdminPlayerDetail(UUID uuid) {
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

	private Optional<WebSession> requireSession(HttpExchange exchange) {
		return sessionManager.get(bearer(exchange));
	}

	private Optional<WebSession> requireOp(HttpExchange exchange) {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			return Optional.empty();
		}
		WebSession s = session.get();
		if (!s.op() || !isOnlineOp(s.playerUuid())) {
			return Optional.empty();
		}
		return session;
	}

	private boolean isOnlineOp(UUID uuid) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager.server() == null) {
			return false;
		}
		var player = manager.server().getPlayerList().getPlayer(uuid);
		return player != null && manager.server().getPlayerList().isOp(player.nameAndId());
	}

	private PlayerEconomyProfile findProfileByName(String username) {
		if (username == null || username.isBlank()) {
			return null;
		}
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null) {
			return null;
		}
		for (PlayerEconomyProfile profile : manager.profiles().values()) {
			if (profile.name().equalsIgnoreCase(username.trim())) {
				return profile;
			}
		}
		try {
			return manager.playerRepository().findByNameIgnoreCase(username.trim()).orElse(null);
		} catch (java.sql.SQLException e) {
			McEconomyMod.LOGGER.error("Profil adi ile arama", e);
			return null;
		}
	}

	private boolean isOnline(UUID uuid) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || manager.server() == null) {
			return false;
		}
		return manager.server().getPlayerList().getPlayer(uuid) != null;
	}

	private static int intVal(JsonObject obj, String key, int defaultVal) {
		if (obj.has(key) && !obj.get(key).isJsonNull()) {
			return obj.get(key).getAsInt();
		}
		return defaultVal;
	}

	private static long longVal(JsonObject obj, String key, long defaultVal) {
		if (obj.has(key) && !obj.get(key).isJsonNull()) {
			return obj.get(key).getAsLong();
		}
		return defaultVal;
	}

	/** Web panelde "Tutar (MC)" — mc veya eski grams anahtari (ikisi de gorunen MC). */
	private static long displayMcVal(JsonObject obj, long defaultVal) {
		if (obj.has("mc") && !obj.get("mc").isJsonNull()) {
			return obj.get("mc").getAsLong();
		}
		if (obj.has("grams") && !obj.get("grams").isJsonNull()) {
			return obj.get("grams").getAsLong();
		}
		return defaultVal;
	}

	private static long displayMcVal(JsonObject obj, String key, long defaultVal) {
		if (obj.has(key) && !obj.get(key).isJsonNull()) {
			return obj.get(key).getAsLong();
		}
		return defaultVal;
	}

	private static String bearer(HttpExchange exchange) {
		String auth = exchange.getRequestHeaders().getFirst("Authorization");
		if (auth != null && auth.startsWith("Bearer ")) {
			return auth.substring(7);
		}
		return exchange.getRequestHeaders().getFirst("X-Session-Token");
	}

	private static JsonObject readJson(HttpExchange exchange) throws IOException {
		try (InputStream in = exchange.getRequestBody()) {
			String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			if (raw.isBlank()) {
				return new JsonObject();
			}
			return GSON.fromJson(raw, JsonObject.class);
		}
	}

	private static String text(JsonObject obj, String key) {
		return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
	}

	private static JsonObject error(String code, String message) {
		JsonObject obj = new JsonObject();
		obj.addProperty("error", code);
		obj.addProperty("message", message);
		return obj;
	}

	private static JsonObject ok(String key, boolean value) {
		JsonObject obj = new JsonObject();
		obj.addProperty(key, value);
		return obj;
	}

	private void serveResource(HttpExchange exchange, String resourcePath) throws IOException {
		String contentType = contentType(resourcePath);
		try (InputStream in = EconomyWebServer.class.getResourceAsStream(resourcePath)) {
			if (in == null) {
				sendJson(exchange, 404, error("not_found", resourcePath));
				return;
			}
			byte[] bytes = in.readAllBytes();
			Headers headers = exchange.getResponseHeaders();
			headers.set("Content-Type", contentType);
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}
	}

	private static String contentType(String path) {
		if (path.endsWith(".html")) {
			return "text/html; charset=utf-8";
		}
		if (path.endsWith(".js")) {
			return "application/javascript; charset=utf-8";
		}
		if (path.endsWith(".css")) {
			return "text/css; charset=utf-8";
		}
		return "application/octet-stream";
	}

	private static void sendJson(HttpExchange exchange, int code, JsonObject body) throws IOException {
		byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
		Headers headers = exchange.getResponseHeaders();
		headers.set("Content-Type", "application/json; charset=utf-8");
		headers.set("Access-Control-Allow-Origin", "*");
		exchange.sendResponseHeaders(code, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}
}
