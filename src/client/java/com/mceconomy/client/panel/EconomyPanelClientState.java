package com.mceconomy.client.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mceconomy.market.ItemPriceHeuristic;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class EconomyPanelClientState {
	private static JsonObject data = new JsonObject();
	private static String tab = "overview";
	private static int scrollY;
	private static int sidebarScrollY;
	private static boolean adminMode;
	private static boolean isOp;
	private static boolean lastSuccess = true;
	private static String lastMessage = "";

	private static int marketPage;
	private static String search = "";
	private static String filter = "all";
	private static int quantity = 1;
	private static String bmPrice = "10";

	private static final Map<String, String> selectedIds = new HashMap<>();
	private static final Map<String, String> formFields = new HashMap<>();

	private EconomyPanelClientState() {
	}

	public static void applySync(String syncTab, String json) {
		tab = syncTab;
		data = json == null || json.isBlank() ? new JsonObject() : JsonParser.parseString(json).getAsJsonObject();
		if (data.has("marketPage")) {
			marketPage = data.get("marketPage").getAsInt();
		}
		if (data.has("search")) {
			search = data.get("search").getAsString();
		}
		if (data.has("filter")) {
			filter = data.get("filter").getAsString();
		}
		if (data.has("scrollY")) {
			scrollY = data.get("scrollY").getAsInt();
		}
		if (data.has("isOp")) {
			isOp = data.get("isOp").getAsBoolean();
		}
		if (data.has("adminMode")) {
			adminMode = data.get("adminMode").getAsBoolean();
		}
		if (data.has("lastMessage")) {
			lastMessage = data.get("lastMessage").getAsString();
		} else if (data.has("message")) {
			lastMessage = data.get("message").getAsString();
		}
		if (data.has("success")) {
			lastSuccess = data.get("success").getAsBoolean();
		}
		hydrateFormFieldsFromSync(syncTab);
	}

	private static void hydrateFormFieldsFromSync(String syncTab) {
		if ("config".equals(syncTab) && data.has("json")) {
			setFormField("configJson", data.get("json").getAsString());
		}
		if ("players".equals(syncTab) && data.has("playerSearch")) {
			setFormField("playerSearch", data.get("playerSearch").getAsString());
		}
	}

	public static void setTab(String newTab) {
		tab = newTab;
		scrollY = 0;
	}

	public static String tab() {
		return tab;
	}

	public static JsonObject data() {
		return data;
	}

	public static int scrollY() {
		return scrollY;
	}

	public static void setScrollY(int y) {
		scrollY = Math.max(0, y);
	}

	public static void scrollBy(int delta) {
		setScrollY(scrollY + delta);
	}

	public static int sidebarScrollY() {
		return sidebarScrollY;
	}

	public static void setSidebarScrollY(int y) {
		sidebarScrollY = Math.max(0, y);
	}

	public static void scrollSidebarBy(int delta) {
		setSidebarScrollY(sidebarScrollY + delta);
	}

	public static boolean adminMode() {
		return adminMode;
	}

	public static void setAdminMode(boolean mode) {
		adminMode = mode;
		if (mode) {
			tab = "dashboard";
		} else if (tab.startsWith("admin:") || isAdminTab(tab)) {
			tab = "overview";
		}
	}

	public static boolean isOp() {
		return isOp;
	}

	public static void setMessage(String msg, boolean success) {
		lastMessage = msg == null ? "" : msg;
		lastSuccess = success;
	}

	public static void setMessage(String msg) {
		setMessage(msg, true);
	}

	public static String message() {
		return lastMessage;
	}

	public static boolean messageSuccess() {
		return lastSuccess;
	}

	public static String selectedItemId() {
		return selectedIds.get("item");
	}

	public static void selectItem(String itemId) {
		if (itemId == null) {
			selectedIds.remove("item");
		} else {
			selectedIds.put("item", itemId);
		}
	}

	public static String selectedId(String key) {
		return selectedIds.get(key);
	}

	public static void selectId(String key, String id) {
		if (id == null) {
			selectedIds.remove(key);
		} else {
			selectedIds.put(key, id);
		}
	}

	public static String formField(String key, String defaultValue) {
		return formFields.getOrDefault(key, defaultValue == null ? "" : defaultValue);
	}

	public static void setFormField(String key, String value) {
		formFields.put(key, value == null ? "" : value);
	}

	public static int marketPage() {
		return marketPage;
	}

	public static void setMarketPage(int page) {
		marketPage = Math.max(0, page);
	}

	public static String search() {
		return search;
	}

	public static void setSearch(String value) {
		search = value == null ? "" : value;
	}

	public static String filter() {
		return filter;
	}

	public static void setFilter(String value) {
		filter = value == null ? "all" : value;
	}

	public static int quantity() {
		return Math.max(1, quantity);
	}

	public static void setQuantity(int q) {
		quantity = Math.max(1, q);
	}

	public static String bmPrice() {
		return bmPrice;
	}

	public static void setBmPrice(String p) {
		bmPrice = p == null ? "10" : p;
	}

	public static ItemStack iconFor(String itemId) {
		var item = ItemPriceHeuristic.resolveItem(itemId);
		return item != null && item != net.minecraft.world.item.Items.AIR ? new ItemStack(item) : ItemStack.EMPTY;
	}

	public static JsonArray inventoryItems() {
		return data.has("inventory") && data.get("inventory").isJsonArray()
				? data.getAsJsonArray("inventory") : new JsonArray();
	}

	public static JsonArray marketItems() {
		if (!data.has("market") || !data.get("market").isJsonObject()) {
			return new JsonArray();
		}
		JsonObject market = data.getAsJsonObject("market");
		return market.has("items") && market.get("items").isJsonArray()
				? market.getAsJsonArray("items") : new JsonArray();
	}

	public static int marketPageCount() {
		if (!data.has("market") || !data.get("market").isJsonObject()) {
			return 1;
		}
		return Math.max(1, data.getAsJsonObject("market").get("pageCount").getAsInt());
	}

	public static boolean isAdminTab(String tabId) {
		return switch (tabId) {
			case "dashboard", "players", "economy-admin", "economy", "masak", "appeals-review",
					"justice-admin", "cameras", "events", "mbop", "blackmarket-admin", "tools", "config" -> true;
			default -> false;
		};
	}
}
