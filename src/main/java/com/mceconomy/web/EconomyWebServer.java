package com.mceconomy.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mceconomy.McEconomyMod;
import com.mceconomy.company.Company;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.EconomyManager;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.exchange.ExchangeToken;
import com.mceconomy.market.Commodity;
import com.mceconomy.player.PlayerEconomyProfile;
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
			server.createContext("/api/insurance", this::handleInsurance);
			server.createContext("/api/guild", this::handleGuild);
			server.createContext("/api/municipal", this::handleMunicipal);
			server.createContext("/api/government", this::handleGovernment);
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
		UUID uuid = session.get().playerUuid();
		var manager = McEconomyMod.getEconomyManager();
		ServerPlayer player = manager.server() != null ? manager.server().getPlayerList().getPlayer(uuid) : null;
		JsonObject me = DashboardDataService.buildMe(uuid, player);
		me.addProperty("op", session.get().op());
		sendJson(exchange, 200, me);
	}

	public void clearSessions() {
		sessionManager.clearAll();
	}

	private void handleCatalog(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		sendJson(exchange, 200, DashboardDataService.buildCatalog());
	}

	private void handleWorkforce(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		sendJson(exchange, 200, DashboardDataService.buildWorkforce(session.get().playerUuid()));
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
		sendJson(exchange, 200, DashboardDataService.buildTrades(session.get().playerUuid()));
	}

	private void handleInsurance(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		sendJson(exchange, 200, DashboardDataService.buildInsurance(session.get().playerUuid()));
	}

	private void handleGuild(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		sendJson(exchange, 200, DashboardDataService.buildGuild(session.get().playerUuid()));
	}

	private void handleGovernment(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		sendJson(exchange, 200, DashboardDataService.buildGovernment(session.get().playerUuid()));
	}

	private void handleMunicipal(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		sendJson(exchange, 200, DashboardDataService.buildMunicipal(session.get().playerUuid()));
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
			return DashboardActionService.ActionResult.ok("ok", DashboardDataService.buildInventory(player));
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
		return EconomyPlayerActionDispatcher.dispatch(uuid, DashboardActionService.onlinePlayer(uuid), action,
				body == null ? "{}" : body.toString());
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
		return EconomyAdminActionDispatcher.dispatch(action, body, adminName, sessionManager::clearAll);
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
		String search = queryParam(exchange.getRequestURI().getQuery(), "search");
		sendJson(exchange, 200, DashboardDataService.buildAdminPlayers(search));
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
		JsonObject detail = DashboardDataService.buildAdminPlayerDetail(uuid);
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
		JsonObject data = DashboardDataService.buildAdminConfig();
		if (data.has("error")) {
			sendJson(exchange, 400, data);
			return;
		}
		sendJson(exchange, 200, data);
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
		sendJson(exchange, 200, DashboardDataService.buildAdminEconomyCatalog());
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
		var manager = McEconomyMod.getEconomyManager();
		JsonArray history = DashboardDataService.loadHistory(type, symbol, 48);
		JsonObject response = new JsonObject();
		response.addProperty("symbol", symbol);
		response.addProperty("type", type);
		response.add("history", history);
		long currentPriceMg = 0;
		if ("COMMODITY".equals(type)) {
			Commodity commodity = Commodity.fromId(symbol);
			if (commodity != null) {
				currentPriceMg = manager.marketService().priceEngine().getUnitPrice(commodity);
				DashboardDataService.enrichCommodityRow(response, manager.marketService(), commodity, currentPriceMg);
			}
		} else if ("TOKEN".equals(type)) {
			for (ExchangeToken token : manager.exchangeService().allTokens()) {
				if (token.symbol().equalsIgnoreCase(symbol)) {
					currentPriceMg = token.priceMg();
					break;
				}
			}
		} else if ("SHARE".equals(type)) {
			double index = manager.marketService().economyIndex().calculate();
			for (Company company : manager.companyManager().allCompanies()) {
				if (company.ticker() != null && company.ticker().equalsIgnoreCase(symbol)) {
					currentPriceMg = company.sharePrice(index);
					break;
				}
			}
		}
		if (currentPriceMg > 0) {
			response.addProperty("priceMg", currentPriceMg);
			response.addProperty("changeBps", DashboardDataService.priceChangeBps(history, currentPriceMg));
		}
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
		sendJson(exchange, 200, DashboardDataService.buildBulletins(category, limit));
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
		sendJson(exchange, 200, DashboardDataService.buildWorldMap(s.playerUuid(), s.op(), track));
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
		int nightIndex = -1;
		if (night != null && !night.isBlank()) {
			try {
				nightIndex = Integer.parseInt(night);
			} catch (NumberFormatException ignored) {
				var manager = McEconomyMod.getEconomyManager();
				if (manager != null && manager.securityCameraService() != null) {
					try {
						nightIndex = manager.securityCameraService().listNights().indexOf(night);
					} catch (java.sql.SQLException ignoredSql) {
						nightIndex = -1;
					}
				}
			}
		}
		JsonObject data = DashboardDataService.buildAdminSecurityCameras(nightIndex, limit);
		if (data.has("error")) {
			sendJson(exchange, 500, error("db", data.get("error").getAsString()));
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
		sendJson(exchange, 200, DashboardDataService.buildChartsOverview(session.get().playerUuid()));
	}

	private void handleChartsPortfolio(HttpExchange exchange) throws IOException {
		Optional<WebSession> session = requireSession(exchange);
		if (session.isEmpty()) {
			sendJson(exchange, 401, error("auth", "Oturum gerekli"));
			return;
		}
		sendJson(exchange, 200, DashboardDataService.buildChartsPortfolio(session.get().playerUuid()));
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
		sendJson(exchange, 200, DashboardDataService.buildAdminOverview());
	}

	private void handleAdminAppeals(HttpExchange exchange) throws IOException {
		if (requireOp(exchange).isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
		sendJson(exchange, 200, DashboardDataService.buildAdminAppeals());
	}

	private void handleAdminJusticeReports(HttpExchange exchange) throws IOException {
		if (requireOp(exchange).isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
		sendJson(exchange, 200, DashboardDataService.buildAdminJusticeReports());
	}

	private void handleAdminJusticePrison(HttpExchange exchange) throws IOException {
		if (requireOp(exchange).isEmpty()) {
			sendJson(exchange, 403, error("forbidden", "Yalnızca sunucu OP erişebilir"));
			return;
		}
		sendJson(exchange, 200, DashboardDataService.buildAdminJusticePrison());
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
