package com.mceconomy.client.panel;

import com.google.gson.JsonObject;
import com.mceconomy.network.EconomyPanelActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class EconomyPanelNetworking {
	private EconomyPanelNetworking() {
	}

	public static void sendAction(String action, JsonObject body) {
		if (body == null) {
			body = new JsonObject();
		}
		body.addProperty("tab", EconomyPanelClientState.tab());
		body.addProperty("marketPage", EconomyPanelClientState.marketPage());
		if ("market".equals(EconomyPanelClientState.tab())) {
			body.addProperty("search", EconomyPanelClientState.search());
			body.addProperty("filter", EconomyPanelClientState.filter());
		}
		if ("players".equals(EconomyPanelClientState.tab())) {
			body.addProperty("playerSearch", EconomyPanelClientState.formField("playerSearch", ""));
		}
		body.addProperty("scrollY", EconomyPanelClientState.scrollY());
		body.addProperty("adminMode", EconomyPanelClientState.adminMode());
		ClientPlayNetworking.send(new EconomyPanelActionPayload(action, body.toString()));
	}

	public static void requestSync() {
		JsonObject body = new JsonObject();
		body.addProperty("tab", EconomyPanelClientState.tab());
		sendAction("panel/sync", body);
	}

	public static void switchTab(String tab) {
		EconomyPanelClientState.setTab(tab);
		JsonObject body = new JsonObject();
		body.addProperty("tab", tab);
		sendAction("panel/sync", body);
	}

	public static void marketBuy(String itemId, int qty) {
		JsonObject body = new JsonObject();
		body.addProperty("itemId", itemId);
		body.addProperty("quantity", qty);
		sendAction("market/buy", body);
	}

	public static void marketSell(String itemId, int qty) {
		JsonObject body = new JsonObject();
		body.addProperty("itemId", itemId);
		body.addProperty("quantity", qty);
		sendAction("market/sell", body);
	}

	public static void marketSellAll(String itemId) {
		JsonObject body = new JsonObject();
		body.addProperty("itemId", itemId);
		sendAction("market/sell-all", body);
	}

	public static void inventorySell(String itemId, int qty) {
		JsonObject body = new JsonObject();
		body.addProperty("itemId", itemId);
		body.addProperty("quantity", qty);
		sendAction("inventory/market-sell", body);
	}

	public static void inventorySellAll(String itemId) {
		JsonObject body = new JsonObject();
		body.addProperty("itemId", itemId);
		sendAction("inventory/market-sell-all", body);
	}

	public static void blackMarketList(String itemId, int qty, long mc) {
		JsonObject body = new JsonObject();
		body.addProperty("itemId", itemId);
		body.addProperty("quantity", qty);
		body.addProperty("mc", mc);
		sendAction("inventory/blackmarket-list", body);
	}

	public static void changeMarketPage(int page) {
		EconomyPanelClientState.setMarketPage(page);
		JsonObject body = new JsonObject();
		body.addProperty("tab", "market");
		body.addProperty("marketPage", page);
		body.addProperty("search", EconomyPanelClientState.search());
		body.addProperty("filter", EconomyPanelClientState.filter());
		sendAction("panel/sync", body);
	}

	public static void pay(long mc) {
		JsonObject body = new JsonObject();
		body.addProperty("target", EconomyPanelClientState.formField("payTarget", ""));
		body.addProperty("mc", mc);
		sendAction("pay", body);
	}

	public static void walletDeposit(long mc) {
		JsonObject body = new JsonObject();
		body.addProperty("mc", mc);
		sendAction("bank/wallet-deposit", body);
	}

	public static void walletWithdraw(long mc) {
		JsonObject body = new JsonObject();
		body.addProperty("mc", mc);
		sendAction("bank/wallet-withdraw", body);
	}

	public static void bankTransfer(long mc) {
		JsonObject body = new JsonObject();
		body.addProperty("target", EconomyPanelClientState.formField("bankTransferTarget", ""));
		body.addProperty("mc", mc);
		sendAction("bank/transfer", body);
	}

	public static void depositIngots(int ingots) {
		JsonObject body = new JsonObject();
		body.addProperty("ingots", ingots);
		sendAction("bank/deposit-ingots", body);
	}

	public static void withdrawIngots(int ingots) {
		JsonObject body = new JsonObject();
		body.addProperty("ingots", ingots);
		sendAction("bank/withdraw-ingots", body);
	}

	public static void loanTake(long mc) {
		JsonObject body = new JsonObject();
		body.addProperty("mc", mc);
		sendAction("loan/take", body);
	}

	public static void casinoPlay(String game, long mc, String choice) {
		JsonObject body = new JsonObject();
		body.addProperty("game", game);
		body.addProperty("mc", mc);
		if (choice != null && !choice.isBlank()) {
			body.addProperty("choice", choice);
		}
		sendAction("casino/play", body);
	}

	public static void adminAction(String action, JsonObject body) {
		sendAction("admin/" + action, body == null ? new JsonObject() : body);
	}

	public static void loadAdminPlayer(String uuid, String search) {
		JsonObject body = new JsonObject();
		body.addProperty("tab", "players");
		body.addProperty("adminPlayerUuid", uuid == null ? "" : uuid);
		body.addProperty("search", search == null ? "" : search);
		sendAction("panel/sync", body);
	}
}
