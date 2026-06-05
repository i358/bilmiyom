package com.mceconomy.panel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mceconomy.network.EconomyHudSync;
import com.mceconomy.network.EconomyPanelOpenPayload;
import com.mceconomy.network.EconomyPanelSyncPayload;
import com.mceconomy.util.Permissions;
import com.mceconomy.web.DashboardActionService;
import com.mceconomy.web.DashboardDataService;
import com.mceconomy.web.EconomyPlayerActionDispatcher;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EconomyPanelService {
	public static final int MARKET_PAGE_SIZE = DashboardDataService.MARKET_PAGE_SIZE;
	private static final Gson GSON = new Gson();
	private static final Map<UUID, DashboardActionService.ActionResult> LAST_RESULTS = new ConcurrentHashMap<>();

	private EconomyPanelService() {
	}

	public static void openPanel(ServerPlayer player, String tab) {
		openPanel(player, tab, false);
	}

	public static void openPanel(ServerPlayer player, String tab, boolean adminMode) {
		if (!ServerPlayNetworking.canSend(player, EconomyPanelOpenPayload.TYPE)) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"§c[Ekonomi] Client mod gerekli. /market komutlarini kullanabilirsiniz."));
			return;
		}
		String resolvedTab = tab == null ? "overview" : tab;
		ServerPlayNetworking.send(player, new EconomyPanelOpenPayload(resolvedTab));
		sync(player, resolvedTab, 0, "", "all", adminMode);
	}

	public static void sync(ServerPlayer player, String tab, int marketPage, String search, String filter) {
		sync(player, tab, marketPage, search, filter, false);
	}

	public static void sync(ServerPlayer player, String tab, int marketPage, String search, String filter,
			boolean adminMode) {
		sync(player, tab, marketPage, search, filter, adminMode, new JsonObject());
	}

	public static void sync(ServerPlayer player, String tab, int marketPage, String search, String filter,
			boolean adminMode, JsonObject extraParams) {
		if (!ServerPlayNetworking.canSend(player, EconomyPanelSyncPayload.TYPE)) {
			return;
		}
		JsonObject params = new JsonObject();
		params.addProperty("marketPage", marketPage);
		params.addProperty("search", search == null ? "" : search);
		params.addProperty("filter", filter == null ? "all" : filter);
		params.addProperty("adminMode", adminMode);
		if (extraParams != null) {
			for (var entry : extraParams.entrySet()) {
				params.add(entry.getKey(), entry.getValue());
			}
		}
		JsonObject data = DashboardDataService.buildTabData(player, tab, params);
		// #region agent log
		{
			JsonObject dbg = new JsonObject();
			dbg.addProperty("tab", tab);
			dbg.addProperty("adminMode", adminMode);
			dbg.addProperty("isOp", com.mceconomy.util.Permissions.isServerOp(player));
			if (data.has("inventoryCount")) {
				dbg.addProperty("inventoryCount", data.get("inventoryCount").getAsInt());
			}
			if (data.has("playerCount")) {
				dbg.addProperty("playerCount", data.get("playerCount").getAsInt());
			}
			if (data.has("json")) {
				dbg.addProperty("hasConfigJson", true);
			}
			if (data.has("workforceCompanyCount")) {
				dbg.addProperty("workforceCompanyCount", data.get("workforceCompanyCount").getAsInt());
			}
			PanelDebugLog.log("EconomyPanelService.sync", "tab sync built", "H1-H3", dbg);
		}
		// #endregion
		DashboardActionService.ActionResult last = LAST_RESULTS.get(player.getUUID());
		if (last != null) {
			data.addProperty("lastMessage", last.message());
			data.addProperty("success", last.success());
			data.addProperty("lastAction", last.message());
		}
		data.addProperty("adminMode", adminMode);
		ServerPlayNetworking.send(player, new EconomyPanelSyncPayload(tab, GSON.toJson(data)));
	}

	public static DashboardActionService.ActionResult handleAction(ServerPlayer player, String action, String bodyJson) {
		com.google.gson.JsonObject body = bodyJson == null || bodyJson.isBlank()
				? new com.google.gson.JsonObject()
				: com.google.gson.JsonParser.parseString(bodyJson).getAsJsonObject();
		if ("panel/sync".equals(action) || "panel/map-sync".equals(action)) {
			String tab = body.has("tab") ? body.get("tab").getAsString() : "overview";
			int page = body.has("marketPage") ? body.get("marketPage").getAsInt() : 0;
			String search = body.has("search") ? body.get("search").getAsString() : "";
			String filter = body.has("filter") ? body.get("filter").getAsString() : "all";
			if ("panel/map-sync".equals(action)) {
				tab = "map";
			}
			boolean adminMode = body.has("adminMode") && body.get("adminMode").getAsBoolean();
			sync(player, tab, page, search, filter, adminMode, extraSyncParams(body));
			return DashboardActionService.ActionResult.ok("Senkronize edildi.");
		}

		DashboardActionService.ActionResult result;
		if (action.startsWith("admin/")) {
			if (!Permissions.isServerOp(player)) {
				result = DashboardActionService.ActionResult.fail("OP gerekli.");
			} else {
				result = EconomyPanelAdminDispatcher.dispatch(
						action.substring("admin/".length()), bodyJson, player.getName().getString());
			}
		} else {
			result = EconomyPlayerActionDispatcher.dispatch(player.getUUID(), player, action, bodyJson);
		}

		LAST_RESULTS.put(player.getUUID(), result);
		String tab = body.has("tab") ? body.get("tab").getAsString() : "overview";
		int page = body.has("marketPage") ? body.get("marketPage").getAsInt() : 0;
		String search = body.has("search") ? body.get("search").getAsString() : "";
		String filter = body.has("filter") ? body.get("filter").getAsString() : "all";
		boolean adminMode = body.has("adminMode") && body.get("adminMode").getAsBoolean();
		sync(player, tab, page, search, filter, adminMode, extraSyncParams(body));
		if (result.success()) {
			EconomyHudSync.syncPlayer(player);
		}
		return result;
	}

	private static JsonObject extraSyncParams(JsonObject body) {
		JsonObject extra = new JsonObject();
		if (body.has("adminPlayerUuid")) {
			extra.addProperty("adminPlayerUuid", body.get("adminPlayerUuid").getAsString());
		}
		if (body.has("nightIndex")) {
			extra.addProperty("nightIndex", body.get("nightIndex").getAsInt());
		}
		if (body.has("track")) {
			extra.addProperty("track", body.get("track").getAsString());
		}
		if (body.has("playerSearch")) {
			extra.addProperty("playerSearch", body.get("playerSearch").getAsString());
		}
		return extra;
	}
}
