package com.mceconomy.panel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mceconomy.web.DashboardActionService;
import com.mceconomy.web.EconomyAdminActionDispatcher;

/** OP panel aksiyonlari — EconomyAdminActionDispatcher delegasyonu. */
public final class EconomyPanelAdminDispatcher {
	private EconomyPanelAdminDispatcher() {
	}

	public static DashboardActionService.ActionResult dispatch(String action, String bodyJson, String adminName) {
		JsonObject body = bodyJson == null || bodyJson.isBlank()
				? new JsonObject()
				: JsonParser.parseString(bodyJson).getAsJsonObject();
		return EconomyAdminActionDispatcher.dispatch(action, body, adminName);
	}
}
