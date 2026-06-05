package com.mceconomy.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mceconomy.McEconomyMod;
import com.mceconomy.player.PlayerEconomyProfile;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Panel ve oyun ici menu aksiyonlari — web ile ayni is mantigi. */
public final class EconomyPlayerActionDispatcher {
	private EconomyPlayerActionDispatcher() {
	}

	public static DashboardActionService.ActionResult dispatch(UUID uuid, ServerPlayer player, String action, String bodyJson) {
		JsonObject body = bodyJson == null || bodyJson.isBlank()
				? new JsonObject()
				: JsonParser.parseString(bodyJson).getAsJsonObject();
		return switch (action) {
			case "pay" -> DashboardActionService.pay(uuid, text(body, "target"), displayMcVal(body, 0));
			case "bank/open-checking" -> DashboardActionService.bankOpenChecking(uuid);
			case "bank/open-term" -> DashboardActionService.bankOpenTerm(uuid);
			case "bank/transfer" -> DashboardActionService.bankTransfer(uuid, text(body, "target"), displayMcVal(body, 0));
			case "bank/wallet-deposit" -> DashboardActionService.bankWalletDeposit(uuid, displayMcVal(body, 0), text(body, "account"));
			case "bank/wallet-withdraw" -> DashboardActionService.bankWalletWithdraw(uuid, displayMcVal(body, 0), text(body, "account"));
			case "bank/deposit-ingots" -> requireOnline(player,
					p -> DashboardActionService.bankDepositIngots(p, intVal(body, "ingots", 0)));
			case "bank/withdraw-ingots" -> requireOnline(player,
					p -> DashboardActionService.bankWithdrawIngots(p, intVal(body, "ingots", 0)));
			case "market/buy" -> requireOnline(player,
					p -> DashboardActionService.marketBuyByItem(p, text(body, "itemId"), text(body, "commodity"), intVal(body, "quantity", 0)));
			case "market/sell" -> requireOnline(player,
					p -> DashboardActionService.marketSellByItem(p, text(body, "itemId"), text(body, "commodity"), intVal(body, "quantity", 0)));
			case "market/sell-all" -> requireOnline(player,
					p -> DashboardActionService.marketSellAllByItem(p, text(body, "itemId"), text(body, "commodity")));
			case "loan/take" -> DashboardActionService.loanTake(uuid, displayMcVal(body, 0));
			case "loan/pay" -> DashboardActionService.loanPay(uuid);
			case "job/set" -> DashboardActionService.setJob(uuid, text(body, "job"));
			case "job/resign" -> DashboardActionService.resignJob(uuid);
			case "quest/assign" -> requireOnline(player, DashboardActionService::assignQuest);
			case "quest/complete" -> requireOnline(player, DashboardActionService::completeQuest);
			case "quest/cancel" -> DashboardActionService.cancelQuest(uuid);
			case "company/create" -> DashboardActionService.createCompany(uuid, text(body, "name"));
			case "shares/buy" -> DashboardActionService.buyShares(uuid, text(body, "company"), intVal(body, "amount", 0));
			case "shares/sell" -> DashboardActionService.sellShares(uuid, text(body, "company"), intVal(body, "amount", 0));
			case "shares/sell-all" -> DashboardActionService.sellAllShares(uuid);
			case "exchange/token/sell-all" -> DashboardActionService.sellAllTokens(uuid);
			case "exchange/token/buy" -> DashboardActionService.buyToken(uuid, text(body, "symbol"), intVal(body, "amount", 0));
			case "exchange/token/sell" -> DashboardActionService.sellToken(uuid, text(body, "symbol"), intVal(body, "amount", 0));
			case "exchange/token/create" -> DashboardActionService.createToken(uuid, text(body, "symbol"), text(body, "name"),
					intVal(body, "supply", 0), displayMcVal(body, 0));
			case "exchange/list" -> DashboardActionService.listCompany(uuid, text(body, "company"), text(body, "ticker"));
			case "exchange/delist" -> DashboardActionService.delistCompany(uuid, text(body, "company"));
			case "exchange/leverage/open" -> DashboardActionService.openLeverage(uuid, text(body, "symbol"),
					"long".equalsIgnoreCase(text(body, "side")), intVal(body, "leverage", 2), displayMcVal(body, 0));
			case "exchange/leverage/close" -> DashboardActionService.closeLeverage(uuid, intVal(body, "positionId", -1));
			case "casino/play" -> DashboardActionService.casinoPlay(uuid, text(body, "game"), displayMcVal(body, 0), text(body, "choice"));
			case "company/employee/fire" -> DashboardActionService.fireEmployee(uuid, longVal(body, "employeeId", -1));
			case "company/employee/raise" -> DashboardActionService.raiseSalary(uuid, longVal(body, "employeeId", -1), displayMcVal(body, 0));
			case "company/employee/bonus" -> DashboardActionService.payBonus(uuid, text(body, "company"));
			case "company/stash/collect" -> DashboardActionService.collectCompanyStash(uuid, text(body, "company"));
			case "company/vault/teleport" -> DashboardActionService.teleportCompanyVault(uuid, text(body, "company"));
			case "company/vault/exit" -> DashboardActionService.exitCompanyVault(uuid);
			case "company/application/accept" -> DashboardActionService.acceptApplication(uuid, longVal(body, "applicationId", -1));
			case "company/application/reject" -> DashboardActionService.rejectApplication(uuid, longVal(body, "applicationId", -1));
			case "vault/teleport" -> requireOnline(player, DashboardActionService::teleportVault);
			case "vault/back" -> requireOnline(player, DashboardActionService::vaultBack);
			case "inventory/market-sell" -> requireOnline(player,
					p -> DashboardActionService.inventoryMarketSell(p, text(body, "itemId"), intVal(body, "quantity", 0)));
			case "inventory/market-sell-all" -> requireOnline(player,
					p -> DashboardActionService.inventoryMarketSellAll(p, text(body, "itemId")));
			case "inventory/blackmarket-list" -> requireOnline(player,
					p -> DashboardActionService.inventoryBlackMarketList(p, text(body, "itemId"), intVal(body, "quantity", 0), displayMcVal(body, 0)));
			case "heist/start" -> requireOnline(player, DashboardActionService::startHeist);
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
			case "blackmarket/buy" -> requireOnline(player,
					p -> DashboardActionService.blackMarketBuy(p, text(body, "good"), intVal(body, "quantity", 0)));
			case "blackmarket/sell" -> requireOnline(player,
					p -> DashboardActionService.blackMarketSell(p, text(body, "good"), intVal(body, "quantity", 0)));
			case "launder" -> requireOnline(player, p -> DashboardActionService.launder(p, displayMcVal(body, 0)));
			case "employment/apply" -> DashboardActionService.employmentApply(uuid, text(body, "company"),
					text(body, "role"), longVal(body, "salaryMg", 0));
			case "employment/cancel-application" -> DashboardActionService.employmentCancelApplication(uuid);
			case "employment/quit" -> DashboardActionService.employmentQuit(uuid);
			case "trade/invite" -> requireOnline(player, p -> DashboardActionService.tradeInvite(uuid, text(body, "target")));
			case "trade/accept" -> DashboardActionService.tradeAccept(uuid);
			case "trade/dispute" -> DashboardActionService.tradeDispute(uuid, longVal(body, "tradeId", 0), text(body, "reason"));
			case "insurance/personal/subscribe" -> DashboardActionService.insurancePersonal(uuid, true);
			case "insurance/personal/cancel" -> DashboardActionService.insurancePersonal(uuid, false);
			case "insurance/company/subscribe" -> DashboardActionService.insuranceCompany(uuid, text(body, "company"), true);
			case "insurance/company/cancel" -> DashboardActionService.insuranceCompany(uuid, text(body, "company"), false);
			case "guild/create" -> DashboardActionService.guildCreate(uuid, text(body, "name"));
			case "guild/join" -> DashboardActionService.guildJoin(uuid, text(body, "name"));
			case "guild/leave" -> DashboardActionService.guildLeave(uuid);
			case "guild/deposit" -> DashboardActionService.guildDeposit(uuid, displayMcVal(body, 0));
			case "guild/withdraw" -> DashboardActionService.guildWithdraw(uuid, displayMcVal(body, 0));
			case "guild/strike" -> DashboardActionService.guildStrike(uuid, intVal(body, "minutes", 30));
			case "guild/bargain" -> DashboardActionService.guildBargain(uuid, text(body, "message"));
			case "municipal/candidate" -> DashboardActionService.municipalCandidate(uuid);
			case "municipal/vote" -> DashboardActionService.municipalVote(uuid, text(body, "candidate"));
			case "municipal/spend" -> DashboardActionService.municipalSpend(uuid, displayMcVal(body, 0), text(body, "purpose"));
			case "government/decree/propose" -> DashboardActionService.proposeDecree(uuid, text(body, "type"), text(body, "payloadJson"));
			case "government/decree/vote" -> DashboardActionService.voteDecree(uuid, longVal(body, "decreeId", 0),
					body.has("yes") && body.get("yes").getAsBoolean());
			default -> DashboardActionService.ActionResult.fail("Bilinmeyen islem: " + action);
		};
	}

	private static DashboardActionService.ActionResult requireOnline(ServerPlayer player,
			java.util.function.Function<ServerPlayer, DashboardActionService.ActionResult> fn) {
		if (player == null) {
			return DashboardActionService.ActionResult.fail("Bu islem icin oyunda olmalisiniz.");
		}
		return fn.apply(player);
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
		if (body.has("displayMc") && !body.get("displayMc").isJsonNull()) {
			return body.get("displayMc").getAsLong();
		}
		return def;
	}
}
