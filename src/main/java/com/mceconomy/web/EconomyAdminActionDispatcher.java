package com.mceconomy.web;

import com.google.gson.JsonObject;
import com.mceconomy.McEconomyMod;

/** Web admin panel ve oyun ici OP panel — ortak admin aksiyonlari. */
public final class EconomyAdminActionDispatcher {
	private EconomyAdminActionDispatcher() {
	}

	public static DashboardActionService.ActionResult dispatch(String action, JsonObject body, String adminName) {
		return dispatch(action, body, adminName, null);
	}

	public static DashboardActionService.ActionResult dispatch(String action, JsonObject body, String adminName,
			Runnable onFullReset) {
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
					intVal(body, "prisonMinutes", 0), adminName != null ? adminName : "OP");
			case "justice/prison/imprison" -> DashboardActionService.justiceImprison(text(body, "player"),
					intVal(body, "minutes", 5), text(body, "reason"), adminName != null ? adminName : "OP");
			case "justice/prison/release" -> DashboardActionService.justiceReleasePrison(text(body, "player"));
			case "trade/dispute/refund" -> DashboardActionService.tradeDisputeResolve(adminName,
					longVal(body, "id", -1), true, text(body, "note"));
			case "trade/dispute/dismiss" -> DashboardActionService.tradeDisputeResolve(adminName,
					longVal(body, "id", -1), false, text(body, "note"));
			case "appeals/accept" -> resolveAppeal(longVal(body, "id", -1), text(body, "note"), true);
			case "appeals/reject" -> resolveAppeal(longVal(body, "id", -1), text(body, "note"), false);
			case "player/wallet/set" -> AdminEconomyService.walletSet(text(body, "player"), text(body, "uuid"), displayMcVal(body, 0));
			case "player/wallet/adjust" -> AdminEconomyService.walletAdjust(text(body, "player"), text(body, "uuid"), displayMcVal(body, 0));
			case "player/dirty/set" -> AdminEconomyService.dirtySet(text(body, "player"), text(body, "uuid"), displayMcVal(body, 0));
			case "player/bank/set" -> AdminEconomyService.bankSet(text(body, "player"), text(body, "uuid"),
					text(body, "type"), displayMcVal(body, 0));
			case "player/bank/open-checking" -> AdminEconomyService.bankOpenChecking(text(body, "player"), text(body, "uuid"));
			case "player/bank/open-term" -> AdminEconomyService.bankOpenTerm(text(body, "player"), text(body, "uuid"));
			case "player/bank/delete" -> AdminEconomyService.bankDelete(text(body, "player"), text(body, "uuid"), text(body, "type"));
			case "player/profile/update" -> AdminEconomyService.profileUpdate(text(body, "player"), text(body, "uuid"), body);
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
			case "economy/full-reset" -> fullEconomyReset(onFullReset);
			default -> DashboardActionService.ActionResult.fail("Bilinmeyen admin islemi: " + action);
		};
	}

	private static DashboardActionService.ActionResult fullEconomyReset(Runnable onSessionsCleared) {
		try {
			var report = com.mceconomy.world.ModWorldResetService.fullEconomyReset(
					McEconomyMod.getEconomyManager().server(), null);
			if (onSessionsCleared != null) {
				onSessionsCleared.run();
			}
			return DashboardActionService.ActionResult.ok("Tam sifirlama: DB="
					+ (report.databaseWiped() ? "OK" : "HATA")
					+ ", MB yenilendi.");
		} catch (Exception e) {
			return DashboardActionService.ActionResult.fail(e.getMessage());
		}
	}

	private static DashboardActionService.ActionResult resolveAppeal(long id, String note, boolean accept) {
		if (id <= 0) {
			return DashboardActionService.ActionResult.fail("Itiraz id gerekli.");
		}
		String resolvedNote = note == null || note.isBlank()
				? (accept ? "Kabul edildi" : "Reddedildi") : note;
		try {
			boolean ok = accept
					? McEconomyMod.getEconomyManager().appealService().accept(id, resolvedNote)
					: McEconomyMod.getEconomyManager().appealService().reject(id, resolvedNote);
			return ok
					? DashboardActionService.ActionResult.ok("Itiraz #" + id + (accept ? " kabul." : " red."))
					: DashboardActionService.ActionResult.fail("Itiraz islenemedi.");
		} catch (java.sql.SQLException e) {
			return DashboardActionService.ActionResult.fail("Itiraz islenemedi.");
		}
	}

	private static String text(JsonObject body, String key) {
		return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : "";
	}

	private static int intVal(JsonObject body, String key, int def) {
		return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsInt() : def;
	}

	private static long longVal(JsonObject body, String key, long def) {
		return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsLong() : def;
	}

	private static long displayMcVal(JsonObject body, long def) {
		if (body.has("mc") && !body.get("mc").isJsonNull()) {
			return body.get("mc").getAsLong();
		}
		if (body.has("grams") && !body.get("grams").isJsonNull()) {
			return body.get("grams").getAsLong();
		}
		return def;
	}

	private static long displayMcVal(JsonObject body, String key, long def) {
		if (body.has(key) && !body.get(key).isJsonNull()) {
			return body.get(key).getAsLong();
		}
		return def;
	}
}
