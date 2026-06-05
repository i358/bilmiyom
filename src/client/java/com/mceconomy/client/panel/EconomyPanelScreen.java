package com.mceconomy.client.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mceconomy.client.panel.components.FormFields;
import com.mceconomy.client.panel.components.PanelToast;
import com.mceconomy.client.panel.components.ScrollableList;
import com.mceconomy.client.panel.render.ChartRenderer;
import com.mceconomy.client.panel.render.DocsRenderer;
import com.mceconomy.client.panel.render.MapRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Oyun ici ekonomi paneli — dashboard ile tam sekme yapisi. */
public class EconomyPanelScreen extends Screen {
	private static final int SIDEBAR_W = 118;
	private static final int TAB_H = 16;
	private static final int GROUP_H = 10;

	private final List<Button> tabButtons = new ArrayList<>();
	private final ScrollableList contentList = new ScrollableList();
	private final ScrollableList docsList = new ScrollableList();
	private final ScrollableList bulletinList = new ScrollableList();

	private EditBox searchBox;
	private int contentTop = 28;
	private int contentBottom;

	public EconomyPanelScreen() {
		super(Component.literal("Ekonomi Paneli"));
	}

	@Override
	protected void init() {
		tabButtons.clear();
		clearWidgets();
		contentBottom = height - PanelToast.height() - 6;

		buildSidebar();
		int cx = SIDEBAR_W + 8;
		String tab = EconomyPanelClientState.tab();
		switch (tab) {
			case "market" -> buildMarketWidgets(cx);
			case "inventory" -> buildInventoryWidgets(cx);
			case "wallet", "bank", "loan", "casino", "illegal", "exchange", "company",
					"privatebank", "job", "vault", "appeals", "justice", "trade", "guild",
					"municipal", "government", "insurance", "map", "employees",
					"masak", "mbop", "events", "tools", "players", "blackmarket-admin",
					"justice-admin", "appeals-review", "economy-admin", "economy", "cameras", "config"
					-> buildFormTab(cx, tab);
			default -> { }
		}
		addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
				.bounds(width - 24, 6, 18, 18).build());
		if (EconomyPanelClientState.isOp()) {
			String toggleLabel = EconomyPanelClientState.adminMode() ? "Oyuncu" : "OP";
			addRenderableWidget(Button.builder(Component.literal(toggleLabel), b -> {
				EconomyPanelClientState.setAdminMode(!EconomyPanelClientState.adminMode());
				rebuildWidgets();
			}).bounds(width - 70, 6, 42, 18).build());
		}
		addRenderableWidget(Button.builder(Component.literal("Yenile"), b -> EconomyPanelNetworking.requestSync())
				.bounds(width - 118, 6, 44, 18).build());
	}

	private void buildSidebar() {
		List<PanelTabs.TabEntry> tabs = EconomyPanelClientState.adminMode()
				? tabsForMode(true)
				: tabsForMode(false);
		String lastGroup = null;
		int y = 24 - EconomyPanelClientState.sidebarScrollY();
		for (PanelTabs.TabEntry entry : tabs) {
			if (!entry.group().equals(lastGroup)) {
				lastGroup = entry.group();
				y += GROUP_H;
			}
			if (y > 20 && y < height - 24) {
				String tabId = entry.id();
				String label = truncate(entry.label(), 14);
				int ty = y;
				tabButtons.add(Button.builder(Component.literal(label), b -> switchTab(tabId))
						.bounds(6, ty, SIDEBAR_W - 10, TAB_H).build());
			}
			y += TAB_H + 2;
		}
		tabButtons.forEach(this::addRenderableWidget);
	}

	private List<PanelTabs.TabEntry> tabsForMode(boolean adminOnly) {
		List<PanelTabs.TabEntry> all = PanelTabs.visibleTabs();
		if (!adminOnly) {
			return all.stream().filter(t -> !t.admin()).toList();
		}
		return all.stream().filter(PanelTabs.TabEntry::admin).toList();
	}

	private void buildMarketWidgets(int cx) {
		searchBox = new EditBox(font, cx, contentTop, 140, 18, Component.literal("Ara"));
		searchBox.setValue(EconomyPanelClientState.search());
		searchBox.setResponder(EconomyPanelClientState::setSearch);
		addRenderableWidget(searchBox);
		addRenderableWidget(Button.builder(Component.literal("Ara"), b -> {
			EconomyPanelNetworking.changeMarketPage(0);
			rebuildWidgets();
		}).bounds(cx + 145, contentTop, 36, 18).build());
		int fy = contentTop + 22;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> EconomyPanelNetworking.changeMarketPage(EconomyPanelClientState.marketPage() - 1))
				.bounds(cx, fy, 18, 18).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> EconomyPanelNetworking.changeMarketPage(EconomyPanelClientState.marketPage() + 1))
				.bounds(cx + 22, fy, 18, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Tumu"), b -> setMarketFilter("all"))
				.bounds(cx + 46, fy, 34, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Sat"), b -> setMarketFilter("sellable"))
				.bounds(cx + 84, fy, 28, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Al"), b -> setMarketFilter("buyable"))
				.bounds(cx + 116, fy, 24, 18).build());

		FormFields ff = new FormFields(this::addRenderableWidget, font, cx, fy + 24);
		ff.label("Adet");
		ff.numberField("marketQty", "Adet", 50, "1");
		String sel = EconomyPanelClientState.selectedItemId();
		if (sel != null) {
			ff.button("Al", 44, () -> EconomyPanelNetworking.marketBuy(sel,
					FormFields.parseInt(EconomyPanelClientState.formField("marketQty", "1"), 1)));
			ff.button("Sat", 44, () -> EconomyPanelNetworking.marketSell(sel,
					FormFields.parseInt(EconomyPanelClientState.formField("marketQty", "1"), 1)));
			ff.button("Tumunu Sat", 80, () -> EconomyPanelNetworking.marketSellAll(sel));
		}
	}

	private void buildInventoryWidgets(int cx) {
		FormFields ff = new FormFields(this::addRenderableWidget, font, cx, contentTop);
		ff.numberField("invQty", "Adet", 50, "1");
		ff.numberField("invPrice", "Fiyat (MC)", 70, EconomyPanelClientState.bmPrice());
		String sel = EconomyPanelClientState.selectedItemId();
		if (sel != null) {
			ff.button("Markette Sat", 100, () -> EconomyPanelNetworking.inventorySell(sel,
					FormFields.parseInt(EconomyPanelClientState.formField("invQty", "1"), 1)));
			ff.button("Tumunu Sat", 90, () -> EconomyPanelNetworking.inventorySellAll(sel));
			ff.button("Karaborsa", 80, () -> EconomyPanelNetworking.blackMarketList(sel,
					FormFields.parseInt(EconomyPanelClientState.formField("invQty", "1"), 1),
					FormFields.parseLong(EconomyPanelClientState.formField("invPrice", "10"), 10)));
		}
	}

	private void buildFormTab(int cx, String tab) {
		FormFields ff = new FormFields(this::addRenderableWidget, font, cx, contentTop);
		switch (tab) {
			case "wallet" -> {
				ff.textField("payTarget", "Alici", 140, "");
				ff.numberField("payGrams", "Tutar ($)", 80, "10");
				ff.button("Gonder", 80, () -> EconomyPanelNetworking.pay(
						FormFields.parseLong(EconomyPanelClientState.formField("payGrams", "10"), 10)));
				ff.gap(8);
				ff.label("Hedef hesap");
				String acct = EconomyPanelClientState.formField("bankAccountType", "checking");
				ff.button(acct.equals("term") ? "[Vadeli]" : "Vadeli", 58,
						() -> EconomyPanelClientState.setFormField("bankAccountType", "term"));
				ff.button(acct.equals("checking") ? "[Vadesiz]" : "Vadesiz", 64,
						() -> EconomyPanelClientState.setFormField("bankAccountType", "checking"));
				ff.gap(4);
				ff.numberField("walletMoveGrams", "Tutar ($)", 80, "10");
				ff.button("Bankaya Yatir", 100, () -> EconomyPanelNetworking.walletDeposit(
						FormFields.parseLong(EconomyPanelClientState.formField("walletMoveGrams", "10"), 10)));
				ff.button("Bankadan Cek", 100, () -> EconomyPanelNetworking.walletWithdraw(
						FormFields.parseLong(EconomyPanelClientState.formField("walletMoveGrams", "10"), 10)));
			}
			case "bank" -> {
				ff.button("Vadesiz Hesap Ac", 110, () -> EconomyPanelNetworking.sendAction("bank/open-checking", new JsonObject()));
				ff.button("Vadeli Hesap Ac", 100, () -> EconomyPanelNetworking.sendAction("bank/open-term", new JsonObject()));
				ff.gap(8);
				ff.textField("bankTransferTarget", "Alici", 120, "");
				ff.numberField("bankTransferGrams", "Tutar ($)", 80, "10");
				ff.button("Transfer Et", 90, () -> EconomyPanelNetworking.bankTransfer(
						FormFields.parseLong(EconomyPanelClientState.formField("bankTransferGrams", "10"), 10)));
				ff.gap(8);
				ff.numberField("ingotCount", "Kulce", 50, "1");
				ff.button("Kulce Yatir", 90, () -> EconomyPanelNetworking.depositIngots(
						FormFields.parseInt(EconomyPanelClientState.formField("ingotCount", "1"), 1)));
				ff.button("Kulce Cek", 80, () -> EconomyPanelNetworking.withdrawIngots(
						FormFields.parseInt(EconomyPanelClientState.formField("ingotCount", "1"), 1)));
			}
			case "loan" -> {
				ff.numberField("loanGrams", "Tutar ($)", 80, "100");
				ff.button("Kredi Al", 80, () -> EconomyPanelNetworking.loanTake(
						FormFields.parseLong(EconomyPanelClientState.formField("loanGrams", "100"), 100)));
				ff.button("Taksit Ode", 90, () -> EconomyPanelNetworking.sendAction("loan/pay", new JsonObject()));
			}
			case "insurance" -> {
				ff.button("Kisisel Sigorta Al", 120, () -> EconomyPanelNetworking.sendAction("insurance/personal/subscribe", new JsonObject()));
				ff.button("Kisisel Iptal", 90, () -> EconomyPanelNetworking.sendAction("insurance/personal/cancel", new JsonObject()));
				ff.textField("insCompanyName", "Sirket", 120, "");
				ff.button("Sirket Policesi Al", 120, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("company", EconomyPanelClientState.formField("insCompanyName", ""));
					EconomyPanelNetworking.sendAction("insurance/company/subscribe", body);
				});
			}
			case "trade" -> {
				ff.textField("tradePartner", "Oyuncu", 120, "");
				ff.button("Davet Gonder", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("target", EconomyPanelClientState.formField("tradePartner", ""));
					EconomyPanelNetworking.sendAction("trade/invite", body);
				});
				ff.button("Daveti Kabul Et", 110, () -> EconomyPanelNetworking.sendAction("trade/accept", new JsonObject()));
				ff.numberField("tradeDisputeId", "Takas ID", 70, "0");
				ff.textField("tradeDisputeReason", "Sebep", 140, "");
				ff.button("Anlasmazlik", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("tradeId", FormFields.parseLong(
							EconomyPanelClientState.formField("tradeDisputeId", "0"), 0));
					body.addProperty("reason", EconomyPanelClientState.formField("tradeDisputeReason", ""));
					EconomyPanelNetworking.sendAction("trade/dispute", body);
				});
			}
			case "guild" -> {
				ff.textField("guildNameInput", "Lonca adi", 120, "");
				ff.button("Kur", 50, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("name", EconomyPanelClientState.formField("guildNameInput", ""));
					EconomyPanelNetworking.sendAction("guild/create", body);
				});
				ff.button("Katil", 50, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("name", EconomyPanelClientState.formField("guildNameInput", ""));
					EconomyPanelNetworking.sendAction("guild/join", body);
				});
				ff.button("Ayril", 50, () -> EconomyPanelNetworking.sendAction("guild/leave", new JsonObject()));
				ff.numberField("guildMc", "Tutar ($)", 80, "100");
				ff.button("Kasaya Yatir", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("guildMc", "100"), 100));
					EconomyPanelNetworking.sendAction("guild/deposit", body);
				});
				ff.button("Kasadan Cek", 90, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("guildMc", "100"), 100));
					EconomyPanelNetworking.sendAction("guild/withdraw", body);
				});
				ff.numberField("guildStrikeMin", "Grev (dk)", 60, "30");
				ff.button("Grev", 50, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("minutes", FormFields.parseInt(
							EconomyPanelClientState.formField("guildStrikeMin", "30"), 30));
					EconomyPanelNetworking.sendAction("guild/strike", body);
				});
				ff.textField("guildBargainMsg", "Pazarlik mesaji", 140, "");
				ff.button("Pazarlik", 70, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("message", EconomyPanelClientState.formField("guildBargainMsg", ""));
					EconomyPanelNetworking.sendAction("guild/bargain", body);
				});
			}
			case "exchange" -> {
				ff.textField("tokenSymbol", "Coin", 60, "");
				ff.numberField("tokenTradeQty", "Adet", 50, "1");
				ff.button("Coin Al", 60, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("symbol", EconomyPanelClientState.formField("tokenSymbol", ""));
					body.addProperty("amount", FormFields.parseInt(EconomyPanelClientState.formField("tokenTradeQty", "1"), 1));
					EconomyPanelNetworking.sendAction("exchange/token/buy", body);
				});
				ff.button("Coin Sat", 60, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("symbol", EconomyPanelClientState.formField("tokenSymbol", ""));
					body.addProperty("amount", FormFields.parseInt(EconomyPanelClientState.formField("tokenTradeQty", "1"), 1));
					EconomyPanelNetworking.sendAction("exchange/token/sell", body);
				});
				ff.button("Tum Coinleri Sat", 110, () -> EconomyPanelNetworking.sendAction("exchange/token/sell-all", new JsonObject()));
				ff.gap(8);
				ff.textField("coinSymbol", "Sembol", 60, "");
				ff.textField("coinName", "Isim", 100, "");
				ff.numberField("coinSupply", "Arz", 60, "1000");
				ff.numberField("coinPriceMg", "Fiyat ($)", 70, "10");
				ff.button("Coin Olustur", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("symbol", EconomyPanelClientState.formField("coinSymbol", ""));
					body.addProperty("name", EconomyPanelClientState.formField("coinName", ""));
					body.addProperty("supply", FormFields.parseInt(EconomyPanelClientState.formField("coinSupply", "1000"), 1000));
					body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("coinPriceMg", "10"), 10));
					EconomyPanelNetworking.sendAction("exchange/token/create", body);
				});
				ff.gap(8);
				ff.textField("levSymbol", "Coin", 60, "");
				ff.textField("levSide", "long/short", 60, "long");
				ff.numberField("levLeverage", "Kaldirac", 50, "2");
				ff.numberField("levMargin", "Teminat (MC)", 80, "10");
				ff.button("Pozisyon Ac", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("symbol", EconomyPanelClientState.formField("levSymbol", ""));
					body.addProperty("side", EconomyPanelClientState.formField("levSide", "long"));
					body.addProperty("leverage", FormFields.parseInt(EconomyPanelClientState.formField("levLeverage", "2"), 2));
					body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("levMargin", "10"), 10));
					EconomyPanelNetworking.sendAction("exchange/leverage/open", body);
				});
			}
			case "company" -> {
				ff.textField("companyName", "Sirket adi", 120, "");
				ff.button("Sirket Kur", 90, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("name", EconomyPanelClientState.formField("companyName", ""));
					EconomyPanelNetworking.sendAction("company/create", body);
				});
				ff.textField("shareCompany", "Sirket", 100, "");
				ff.numberField("shareQty", "Adet", 50, "1");
				ff.button("Hisse Al", 70, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("company", EconomyPanelClientState.formField("shareCompany", ""));
					body.addProperty("amount", FormFields.parseInt(EconomyPanelClientState.formField("shareQty", "1"), 1));
					EconomyPanelNetworking.sendAction("shares/buy", body);
				});
				ff.button("Hisse Sat", 70, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("company", EconomyPanelClientState.formField("shareCompany", ""));
					body.addProperty("amount", FormFields.parseInt(EconomyPanelClientState.formField("shareQty", "1"), 1));
					EconomyPanelNetworking.sendAction("shares/sell", body);
				});
				ff.button("Tum Hisseleri Sat", 110, () -> EconomyPanelNetworking.sendAction("shares/sell-all", new JsonObject()));
				ff.textField("companyStash", "Sirket", 100, "");
				ff.button("Sandiga Isinlan", 110, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("company", EconomyPanelClientState.formField("companyStash", ""));
					EconomyPanelNetworking.sendAction("company/vault/teleport", body);
				});
				ff.button("Depo Topla", 90, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("company", EconomyPanelClientState.formField("companyStash", ""));
					EconomyPanelNetworking.sendAction("company/stash/collect", body);
				});
				ff.button("Sandiktan Cik", 100, () -> EconomyPanelNetworking.sendAction("company/vault/exit", new JsonObject()));
			}
			case "employees" -> {
				ff.textField("empCompany", "Sirket", 100, "");
				ff.button("Ikramiye", 70, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("company", EconomyPanelClientState.formField("empCompany", ""));
					EconomyPanelNetworking.sendAction("company/employee/bonus", body);
				});
				ff.button("Sandiga Git", 90, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("company", EconomyPanelClientState.formField("empCompany", ""));
					EconomyPanelNetworking.sendAction("company/vault/teleport", body);
				});
				ff.button("Depo Topla", 90, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("company", EconomyPanelClientState.formField("empCompany", ""));
					EconomyPanelNetworking.sendAction("company/stash/collect", body);
				});
				ff.gap(6);
				ff.numberField("empId", "Calisan ID", 70, "0");
				ff.numberField("empRaiseMc", "Yeni maas ($)", 80, "100");
				ff.button("Zam", 50, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("employeeId", FormFields.parseLong(
							EconomyPanelClientState.formField("empId", "0"), 0));
					body.addProperty("mc", FormFields.parseLong(
							EconomyPanelClientState.formField("empRaiseMc", "100"), 100));
					EconomyPanelNetworking.sendAction("company/employee/raise", body);
				});
				ff.button("Kov", 50, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("employeeId", FormFields.parseLong(
							EconomyPanelClientState.formField("empId", "0"), 0));
					EconomyPanelNetworking.sendAction("company/employee/fire", body);
				});
				ff.gap(6);
				ff.numberField("appId", "Basvuru ID", 70, "0");
				ff.button("Kabul", 50, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("applicationId", FormFields.parseLong(
							EconomyPanelClientState.formField("appId", "0"), 0));
					EconomyPanelNetworking.sendAction("company/application/accept", body);
				});
				ff.button("Reddet", 60, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("applicationId", FormFields.parseLong(
							EconomyPanelClientState.formField("appId", "0"), 0));
					EconomyPanelNetworking.sendAction("company/application/reject", body);
				});
				ff.button("Yenile", 60, () -> EconomyPanelNetworking.requestSync());
			}
			case "privatebank" -> {
				ff.button("Sertifika Al", 90, () -> EconomyPanelNetworking.sendAction("private-bank/certify", new JsonObject()));
				ff.textField("pbankName", "Banka adi", 120, "");
				ff.button("Ozel Banka Ac", 110, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("name", EconomyPanelClientState.formField("pbankName", ""));
					EconomyPanelNetworking.sendAction("private-bank/open", body);
				});
				ff.textField("pbankSelect", "Banka", 100, "");
				ff.numberField("pbankGrams", "Tutar ($)", 80, "10");
				ff.button("Yatir", 60, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("bank", EconomyPanelClientState.formField("pbankSelect", ""));
					body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("pbankGrams", "10"), 10));
					EconomyPanelNetworking.sendAction("private-bank/deposit", body);
				});
				ff.button("Cek", 50, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("bank", EconomyPanelClientState.formField("pbankSelect", ""));
					body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("pbankGrams", "10"), 10));
					EconomyPanelNetworking.sendAction("private-bank/withdraw", body);
				});
			}
			case "job" -> {
				ff.textField("jobSelect", "Meslek id", 100, "");
				ff.button("Meslek Sec", 90, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("job", EconomyPanelClientState.formField("jobSelect", ""));
					EconomyPanelNetworking.sendAction("job/set", body);
				});
				ff.button("Gorev Al", 70, () -> EconomyPanelNetworking.sendAction("quest/assign", new JsonObject()));
				ff.button("Teslim Et", 70, () -> EconomyPanelNetworking.sendAction("quest/complete", new JsonObject()));
				ff.button("Gorev Iptal", 80, () -> EconomyPanelNetworking.sendAction("quest/cancel", new JsonObject()));
				ff.button("Istifa", 60, () -> EconomyPanelNetworking.sendAction("job/resign", new JsonObject()));
				ff.gap(8);
				ff.textField("employmentCompany", "Sirket", 100, "");
				ff.textField("employmentRole", "Rol", 80, "madenci");
				ff.numberField("employmentSalary", "Maas (mg)", 80, "50000");
				ff.button("Basvur", 70, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("company", EconomyPanelClientState.formField("employmentCompany", ""));
					body.addProperty("role", EconomyPanelClientState.formField("employmentRole", "madenci"));
					body.addProperty("salaryMg", FormFields.parseLong(EconomyPanelClientState.formField("employmentSalary", "50000"), 50000));
					EconomyPanelNetworking.sendAction("employment/apply", body);
				});
				ff.button("Isten Ayril", 90, () -> EconomyPanelNetworking.sendAction("employment/quit", new JsonObject()));
			}
			case "vault" -> {
				ff.button("Kasaya Isinlan", 110, () -> EconomyPanelNetworking.sendAction("vault/teleport", new JsonObject()));
				ff.button("Geri Don", 80, () -> EconomyPanelNetworking.sendAction("vault/back", new JsonObject()));
				ff.button("Soygun Baslat", 100, () -> EconomyPanelNetworking.sendAction("heist/start", new JsonObject()));
			}
			case "appeals" -> {
				ff.textField("appealSubject", "Konu", 140, "");
				ff.textField("appealMessage", "Mesaj", 180, "");
				ff.numberField("appealAlertId", "Uyari ID", 70, "");
				ff.button("Itiraz Gonder", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("subject", EconomyPanelClientState.formField("appealSubject", ""));
					body.addProperty("message", EconomyPanelClientState.formField("appealMessage", ""));
					String aid = EconomyPanelClientState.formField("appealAlertId", "");
					if (!aid.isBlank()) {
						body.addProperty("alertId", FormFields.parseLong(aid, 0));
					}
					EconomyPanelNetworking.sendAction("appeal/submit", body);
				});
			}
			case "justice" -> {
				ff.textField("complaintTarget", "Hedef", 100, "");
				ff.textField("complaintCategory", "Kategori", 100, "");
				ff.textField("complaintSubject", "Konu", 120, "");
				ff.textField("complaintMessage", "Mesaj", 160, "");
				ff.button("Sikayet Gonder", 110, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("target", EconomyPanelClientState.formField("complaintTarget", ""));
					body.addProperty("category", EconomyPanelClientState.formField("complaintCategory", ""));
					body.addProperty("subject", EconomyPanelClientState.formField("complaintSubject", ""));
					body.addProperty("message", EconomyPanelClientState.formField("complaintMessage", ""));
					EconomyPanelNetworking.sendAction("justice/complaint", body);
				});
				ff.gap(6);
				ff.textField("tipCategory", "Kategori", 100, "");
				ff.textField("tipTarget", "Hedef", 100, "");
				ff.textField("tipMessage", "Mesaj", 160, "");
				ff.button("Ihbar Gonder", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("category", EconomyPanelClientState.formField("tipCategory", ""));
					body.addProperty("target", EconomyPanelClientState.formField("tipTarget", ""));
					body.addProperty("message", EconomyPanelClientState.formField("tipMessage", ""));
					EconomyPanelNetworking.sendAction("justice/tipoff", body);
				});
			}
			case "illegal" -> {
				ff.textField("illegalGood", "Urun id", 100, "");
				ff.numberField("illegalQty", "Adet", 50, "1");
				ff.button("Al (Kara Para)", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("good", EconomyPanelClientState.formField("illegalGood", ""));
					body.addProperty("quantity", FormFields.parseInt(EconomyPanelClientState.formField("illegalQty", "1"), 1));
					EconomyPanelNetworking.sendAction("blackmarket/buy", body);
				});
				ff.button("Sat", 50, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("good", EconomyPanelClientState.formField("illegalGood", ""));
					body.addProperty("quantity", FormFields.parseInt(EconomyPanelClientState.formField("illegalQty", "1"), 1));
					EconomyPanelNetworking.sendAction("blackmarket/sell", body);
				});
				ff.numberField("launderGrams", "Tutar ($)", 80, "10");
				ff.button("Aklamayi Dene", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("launderGrams", "10"), 10));
					EconomyPanelNetworking.sendAction("launder", body);
				});
			}
			case "casino" -> {
				ff.numberField("cfBet", "Bahis (MC)", 70, "10");
				ff.textField("cfChoice", "yazi/tura", 60, "yazi");
				ff.button("Yazi/Tura", 80, () -> EconomyPanelNetworking.casinoPlay("coinflip",
						FormFields.parseLong(EconomyPanelClientState.formField("cfBet", "10"), 10),
						EconomyPanelClientState.formField("cfChoice", "yazi")));
				ff.numberField("diceBet", "Bahis (MC)", 70, "10");
				ff.textField("diceChoice", "1-6", 30, "1");
				ff.button("Zar At", 70, () -> EconomyPanelNetworking.casinoPlay("dice",
						FormFields.parseLong(EconomyPanelClientState.formField("diceBet", "10"), 10),
						EconomyPanelClientState.formField("diceChoice", "1")));
				ff.numberField("slotBet", "Bahis (MC)", 70, "10");
				ff.button("Slot Cevir", 80, () -> EconomyPanelNetworking.casinoPlay("slot",
						FormFields.parseLong(EconomyPanelClientState.formField("slotBet", "10"), 10), ""));
			}
			case "municipal" -> {
				ff.button("Aday Ol", 80, () -> EconomyPanelNetworking.sendAction("municipal/candidate", new JsonObject()));
				ff.textField("munVoteSelect", "Aday", 100, "");
				ff.button("Oy Ver", 70, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("candidate", EconomyPanelClientState.formField("munVoteSelect", ""));
					EconomyPanelNetworking.sendAction("municipal/vote", body);
				});
				ff.numberField("munSpendMc", "Tutar ($)", 80, "1000");
				ff.textField("munSpendPurpose", "Aciklama", 140, "");
				ff.button("Harcama Yap", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("munSpendMc", "1000"), 1000));
					body.addProperty("purpose", EconomyPanelClientState.formField("munSpendPurpose", ""));
					EconomyPanelNetworking.sendAction("municipal/spend", body);
				});
			}
			case "government" -> {
				ff.textField("decreeType", "Emir tipi", 120, "interest");
				ff.textField("decreePayload", "JSON", 180, "{}");
				ff.button("Emir Teklif Et", 110, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("type", EconomyPanelClientState.formField("decreeType", "interest"));
					body.addProperty("payloadJson", EconomyPanelClientState.formField("decreePayload", "{}"));
					EconomyPanelNetworking.sendAction("government/decree/propose", body);
				});
				ff.numberField("decreeId", "Emir ID", 60, "0");
				ff.button("Evet", 50, () -> voteDecree(true));
				ff.button("Hayir", 50, () -> voteDecree(false));
			}
			case "map" -> {
				ff.textField("mapTrackPlayer", "Takip", 120, "");
				ff.button("Harita Yenile", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("track", EconomyPanelClientState.formField("mapTrackPlayer", ""));
					EconomyPanelNetworking.sendAction("panel/map-sync", body);
				});
			}
			case "masak" -> {
				ff.textField("masakResolvePlayer", "Oyuncu", 100, "");
				ff.button("Hesap Coz", 90, () -> EconomyPanelNetworking.adminAction("masak/resolve", playerBody("masakResolvePlayer")));
				ff.textField("masakFinePlayer", "Oyuncu", 100, "");
				ff.numberField("masakFineGrams", "Gram", 60, "10");
				ff.button("Ceza Uygula", 100, () -> {
					JsonObject body = playerBody("masakFinePlayer");
					body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("masakFineGrams", "10"), 10));
					EconomyPanelNetworking.adminAction("masak/fine", body);
				});
			}
			case "mbop" -> {
				ff.textField("mbopPlayer", "Oyuncu", 100, "");
				ff.button("Yetki Ver", 80, () -> EconomyPanelNetworking.adminAction("mbop/grant", playerBody("mbopPlayer")));
				ff.button("Yetki Al", 70, () -> EconomyPanelNetworking.adminAction("mbop/revoke", playerBody("mbopPlayer")));
			}
			case "events" -> {
				ff.textField("eventType", "Olay tipi", 100, "");
				ff.numberField("eventDuration", "Sure (sn)", 70, "300");
				ff.button("Tetikle", 70, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("type", EconomyPanelClientState.formField("eventType", ""));
					body.addProperty("durationSeconds", FormFields.parseInt(EconomyPanelClientState.formField("eventDuration", "300"), 300));
					EconomyPanelNetworking.adminAction("event/trigger", body);
				});
			}
			case "players" -> {
				ff.textField("playerSearch", "Ara", 120, "");
				ff.button("Ara", 50, () -> EconomyPanelNetworking.requestSync());
				ff.button("Yonet (secili)", 100, () -> openPlayerAdminModal());
			}
			case "economy-admin", "economy" -> {
				ff.numberField("macroBaseRate", "Baz faiz", 60, "0.05");
				ff.numberField("macroInflation", "Enflasyon", 60, "0.02");
				ff.button("MB Guncelle", 90, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("baseRate", FormFields.parseDouble(
							EconomyPanelClientState.formField("macroBaseRate", "0.05"), 0.05));
					body.addProperty("inflationRate", FormFields.parseDouble(
							EconomyPanelClientState.formField("macroInflation", "0.02"), 0.02));
					EconomyPanelNetworking.adminAction("economy/central-bank/update", body);
				});
				ff.textField("newCompanyName", "Sirket", 100, "");
				ff.textField("newCompanyOwner", "Sahip", 80, "");
				ff.textField("newCompanyTicker", "Ticker", 60, "");
				ff.button("Sirket Olustur", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("name", EconomyPanelClientState.formField("newCompanyName", ""));
					body.addProperty("owner", EconomyPanelClientState.formField("newCompanyOwner", ""));
					body.addProperty("ticker", EconomyPanelClientState.formField("newCompanyTicker", ""));
					body.addProperty("listed", true);
					EconomyPanelNetworking.adminAction("economy/company/create", body);
				});
				ff.textField("newTokenSymbol", "Coin", 60, "");
				ff.textField("newTokenName", "Isim", 100, "");
				ff.numberField("newTokenSupply", "Arz", 60, "1000");
				ff.button("Coin Olustur", 90, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("symbol", EconomyPanelClientState.formField("newTokenSymbol", ""));
					body.addProperty("displayName", EconomyPanelClientState.formField("newTokenName", ""));
					body.addProperty("supply", FormFields.parseInt(
							EconomyPanelClientState.formField("newTokenSupply", "1000"), 1000));
					EconomyPanelNetworking.adminAction("economy/token/create", body);
				});
			}
			case "cameras" -> {
				ff.numberField("cameraNight", "Gece indeksi", 60, "0");
				ff.button("Kameralari Yukle", 110, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("tab", "cameras");
					body.addProperty("nightIndex", FormFields.parseInt(
							EconomyPanelClientState.formField("cameraNight", "0"), 0));
					EconomyPanelNetworking.sendAction("panel/sync", body);
				});
			}
			case "blackmarket-admin" -> {
				ff.textField("bmAdminName", "Isim", 100, "");
				ff.textField("bmAdminItemId", "Item id", 100, "");
				ff.numberField("bmAdminPrice", "Fiyat", 60, "10");
				ff.button("Urun Ekle", 80, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("name", EconomyPanelClientState.formField("bmAdminName", ""));
					body.addProperty("itemId", EconomyPanelClientState.formField("bmAdminItemId", ""));
					body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("bmAdminPrice", "10"), 10));
					EconomyPanelNetworking.adminAction("blackmarket/add", body);
				});
			}
			case "justice-admin" -> {
				ff.numberField("justiceReportId", "Rapor ID", 70, "0");
				ff.textField("justiceNote", "Not", 120, "");
				ff.numberField("justicePrisonMin", "Hapis (dk)", 70, "5");
				ff.button("Sorustur", 80, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("id", FormFields.parseLong(EconomyPanelClientState.formField("justiceReportId", "0"), 0));
					EconomyPanelNetworking.adminAction("justice/investigate", body);
				});
				ff.button("Reddet", 60, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("id", FormFields.parseLong(EconomyPanelClientState.formField("justiceReportId", "0"), 0));
					body.addProperty("note", EconomyPanelClientState.formField("justiceNote", ""));
					EconomyPanelNetworking.adminAction("justice/dismiss", body);
				});
				ff.button("Mahkum Et", 80, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("id", FormFields.parseLong(EconomyPanelClientState.formField("justiceReportId", "0"), 0));
					body.addProperty("note", EconomyPanelClientState.formField("justiceNote", ""));
					body.addProperty("prisonMinutes", FormFields.parseInt(
							EconomyPanelClientState.formField("justicePrisonMin", "5"), 5));
					EconomyPanelNetworking.adminAction("justice/guilty", body);
				});
				ff.textField("prisonPlayer", "Oyuncu", 100, "");
				ff.button("Hapse At", 80, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("player", EconomyPanelClientState.formField("prisonPlayer", ""));
					body.addProperty("minutes", FormFields.parseInt(
							EconomyPanelClientState.formField("justicePrisonMin", "5"), 5));
					body.addProperty("reason", EconomyPanelClientState.formField("justiceNote", ""));
					EconomyPanelNetworking.adminAction("justice/prison/imprison", body);
				});
				ff.button("Serbest Birak", 100, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("player", EconomyPanelClientState.formField("prisonPlayer", ""));
					EconomyPanelNetworking.adminAction("justice/prison/release", body);
				});
			}
			case "appeals-review" -> {
				ff.numberField("appealReviewId", "Itiraz ID", 70, "0");
				ff.textField("appealReviewNote", "Not", 120, "");
				ff.button("Kabul", 60, () -> resolveAppeal(true));
				ff.button("Reddet", 60, () -> resolveAppeal(false));
			}
			case "tools" -> {
				ff.button("MB Yeniden Kur", 110, () -> EconomyPanelNetworking.adminAction("central-bank/rebuild", new JsonObject()));
				ff.button("Tam Ekonomi Sifirla", 120, () -> EconomyPanelNetworking.adminAction("economy/full-reset", new JsonObject()));
			}
			case "config" -> {
				String cfgDefault = EconomyPanelClientState.data().has("json")
						? EconomyPanelClientState.data().get("json").getAsString() : "{}";
				ff.textField("configJson", "Config JSON", Math.min(280, width - cx - 20), cfgDefault);
				ff.button("Yenile", 60, () -> EconomyPanelNetworking.requestSync());
				ff.button("Kaydet", 70, () -> {
					JsonObject body = new JsonObject();
					body.addProperty("json", EconomyPanelClientState.formField("configJson", "{}"));
					EconomyPanelNetworking.adminAction("config/save", body);
				});
			}
			default -> { }
		}
	}

	private JsonObject playerBody(String fieldKey) {
		JsonObject body = new JsonObject();
		body.addProperty("player", EconomyPanelClientState.formField(fieldKey, ""));
		return body;
	}

	private void voteDecree(boolean yes) {
		JsonObject body = new JsonObject();
		body.addProperty("decreeId", FormFields.parseLong(EconomyPanelClientState.formField("decreeId", "0"), 0));
		body.addProperty("yes", yes);
		EconomyPanelNetworking.sendAction("government/decree/vote", body);
	}

	private void resolveAppeal(boolean accept) {
		JsonObject body = new JsonObject();
		body.addProperty("id", FormFields.parseLong(EconomyPanelClientState.formField("appealReviewId", "0"), 0));
		body.addProperty("note", EconomyPanelClientState.formField("appealReviewNote", ""));
		EconomyPanelNetworking.adminAction(accept ? "appeals/accept" : "appeals/reject", body);
	}

	private void openPlayerAdminModal() {
		String uuid = EconomyPanelClientState.selectedId("adminPlayer");
		String name = EconomyPanelClientState.selectedId("adminPlayerName");
		if (uuid == null || name == null) {
			return;
		}
		EconomyPanelNetworking.loadAdminPlayer(uuid, EconomyPanelClientState.formField("playerSearch", ""));
		if (minecraft != null) {
			minecraft.setScreen(new PlayerAdminModal(this, name, uuid));
		}
	}

	private void switchTab(String tab) {
		EconomyPanelClientState.setTab(tab);
		EconomyPanelNetworking.switchTab(tab);
		rebuildWidgets();
	}

	private void setMarketFilter(String filter) {
		EconomyPanelClientState.setFilter(filter);
		EconomyPanelNetworking.changeMarketPage(0);
		rebuildWidgets();
	}

	public void refreshAfterSync() {
		rebuildWidgets();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		extractTransparentBackground(graphics);
		int cx = SIDEBAR_W + 8;
		int toastY = height - PanelToast.height() - 4;
		contentBottom = toastY - 4;

		graphics.fill(0, 0, SIDEBAR_W, height, 0xCC101820);
		graphics.fill(SIDEBAR_W, 0, width, height, 0xCC1A2430);
		graphics.text(font, "Ekonomi Paneli", 8, 8, 0xFFE8C547, false);

		JsonObject data = EconomyPanelClientState.data();
		graphics.text(font, PanelTabs.titleFor(EconomyPanelClientState.tab()), cx, 8, 0xFFFFFFFF, false);
		graphics.text(font, "Cuzdan: " + text(data, "wallet", "-"), cx, 20, 0xFFE8C547, false);
		graphics.text(font, "Vadesiz: " + text(data, "checking", text(data, "bank", "-")), cx + 120, 20, 0xFF88CCFF, false);
		if (data.has("hasTerm") && data.get("hasTerm").getAsBoolean()) {
			graphics.text(font, "Vadeli: " + text(data, "termBalance", "-"), cx + 250, 20, 0xFFAA88FF, false);
		}
		if (EconomyPanelClientState.isOp()) {
			graphics.text(font, "OP", cx + 260, 20, 0xFF7BED9F, false);
		}

		renderSidebarGroups(graphics);
		String tab = EconomyPanelClientState.tab();
		switch (tab) {
			case "overview" -> renderOverview(graphics, cx);
			case "macro" -> renderMacro(graphics, cx);
			case "map" -> renderMap(graphics, cx);
			case "bulletins" -> renderBulletins(graphics, cx);
			case "charts" -> renderCharts(graphics, cx);
			case "inventory" -> renderInventory(graphics, cx, mouseX, mouseY);
			case "docs" -> renderDocs(graphics, cx);
			case "market" -> renderMarket(graphics, cx, mouseX, mouseY);
			case "bank" -> renderBank(graphics, cx);
			case "wallet" -> renderWallet(graphics, cx);
			case "loan" -> renderLoan(graphics, cx);
			case "job" -> renderJob(graphics, cx);
			case "exchange" -> renderExchange(graphics, cx);
			case "property" -> renderPropertyList(graphics, cx);
			case "vehicle" -> renderVehicleList(graphics, cx);
			case "employees" -> renderEmployees(graphics, cx);
			case "dashboard" -> renderAdminDashboard(graphics, cx);
			case "players" -> renderAdminPlayers(graphics, cx, mouseX, mouseY);
			case "insurance" -> renderJsonList(graphics, cx, "insurance", "policies", "Sigorta");
			case "trade" -> renderJsonList(graphics, cx, "trades", null, "Takas Gecmisi");
			case "guild" -> renderGuild(graphics, cx);
			case "municipal" -> renderMunicipal(graphics, cx);
			case "government" -> renderGovernment(graphics, cx);
			case "economy", "economy-admin" -> renderEconomyAdmin(graphics, cx);
			case "cameras" -> renderCameras(graphics, cx);
			case "masak" -> renderJsonList(graphics, cx, "masakAlerts", null, "MASAK Uyarilari");
			case "appeals-review" -> renderJsonList(graphics, cx, "openAppeals", null, "Acik Itirazlar");
			case "justice-admin" -> renderJusticeAdmin(graphics, cx);
			case "blackmarket-admin" -> renderJsonList(graphics, cx, "adminIllegalGoods", null, "Karaborsa Urunleri");
			case "config" -> renderConfig(graphics, cx);
			default -> renderHint(graphics, cx, "Sekme: " + PanelTabs.titleFor(tab));
		}

		PanelToast.render(graphics, font, cx, toastY, width - cx - 8);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void renderSidebarGroups(GuiGraphicsExtractor graphics) {
		List<PanelTabs.TabEntry> tabs = EconomyPanelClientState.adminMode()
				? tabsForMode(true) : tabsForMode(false);
		String lastGroup = null;
		int y = 24 - EconomyPanelClientState.sidebarScrollY();
		for (PanelTabs.TabEntry entry : tabs) {
			if (!entry.group().equals(lastGroup)) {
				lastGroup = entry.group();
				if (y > 14 && y < height - 10) {
					graphics.text(font, entry.group(), 8, y, 0xFF88AACC, false);
				}
				y += GROUP_H;
			}
			if (entry.id().equals(EconomyPanelClientState.tab()) && y > 18 && y < height - 8) {
				graphics.fill(4, y - 1, SIDEBAR_W - 4, y + TAB_H + 1, 0x44E8C547);
			}
			y += TAB_H + 2;
		}
	}

	private void renderOverview(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		int y = contentTop + 8;
		graphics.text(font, "Cuzdan: " + text(data, "wallet", "-"), cx, y, 0xFFFFFFFF, false);
		y += 12;
		graphics.text(font, "Vadesiz: " + text(data, "checking", text(data, "bank", "-")), cx, y, 0xFF88CCFF, false);
		y += 12;
		if (data.has("hasTerm") && data.get("hasTerm").getAsBoolean()) {
			graphics.text(font, "Vadeli: " + text(data, "termBalance", "-"), cx, y, 0xFFAA88FF, false);
			y += 12;
		}
		y += 0;
		graphics.text(font, "Meslek: " + text(data, "job", "-"), cx, y, 0xFFCCCCCC, false);
		y += 16;
		renderMarketPreview(graphics, cx, y, 6);
	}

	private void renderMacro(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		int y = contentTop + 8;
		graphics.text(font, "Makro & Fiat", cx, y, 0xFFE8C547, false);
		y += 14;
		if (data.has("inflationRate")) {
			graphics.text(font, "Enflasyon: " + String.format("%.2f%%", data.get("inflationRate").getAsDouble() * 100), cx, y, 0xFFDDDDDD, false);
			y += 12;
		}
		if (data.has("fiatStrength")) {
			graphics.text(font, "Fiat gucu: " + data.get("fiatStrength").getAsString(), cx, y, 0xFFDDDDDD, false);
			y += 12;
		}
		if (data.has("municipalBudget")) {
			graphics.text(font, "Belediye butcesi: " + data.get("municipalBudget").getAsString(), cx, y, 0xFFDDDDDD, false);
		}
	}

	private void renderMap(GuiGraphicsExtractor graphics, int cx) {
		int mapY = contentTop + 50;
		int mapH = contentBottom - mapY;
		JsonObject map = EconomyPanelClientState.data().has("worldMap")
				? EconomyPanelClientState.data().getAsJsonObject("worldMap") : null;
		MapRenderer.render(graphics, font, cx, mapY, width - cx - 12, mapH, map);
	}

	private void renderBulletins(GuiGraphicsExtractor graphics, int cx) {
		int listY = contentTop + 8;
		int listH = contentBottom - listY;
		JsonArray rows = EconomyPanelClientState.data().has("bulletins")
				? EconomyPanelClientState.data().getAsJsonArray("bulletins") : new JsonArray();
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			JsonObject b = rows.get(i).getAsJsonObject();
			lines.add((b.has("headline") ? b.get("headline").getAsString() : "?") + " — "
					+ (b.has("categoryLabel") ? b.get("categoryLabel").getAsString() : ""));
		}
		bulletinList.setRows(lines);
		bulletinList.setScrollY(EconomyPanelClientState.scrollY());
		bulletinList.rowHeight(14).visibleRows(listH / 14);
		bulletinList.render(graphics, font, cx, listY, width - cx - 12, listH, 0xFFDDDDDD, null);
	}

	private void renderCharts(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		int y = contentTop + 60;
		int halfW = (width - cx - 16) / 2;
		JsonArray indexHist = data.has("indexHistory") ? data.getAsJsonArray("indexHistory") : new JsonArray();
		ChartRenderer.renderLine(graphics, font, cx, y, halfW, 70, indexHist, "priceMg", 0xFF4DA6FF, "Ekonomi Endeksi");
		JsonArray infHist = data.has("inflationHistory") ? data.getAsJsonArray("inflationHistory") : new JsonArray();
		ChartRenderer.renderLine(graphics, font, cx + halfW + 8, y, halfW, 70, infHist, "priceMg", 0xFFFF6B6B, "Enflasyon");
		y += 78;
		JsonArray commodities = data.has("commodities") ? data.getAsJsonArray("commodities")
				: EconomyPanelClientState.marketItems();
		ChartRenderer.renderBar(graphics, font, cx, y, width - cx - 12, 80, commodities, "name", "priceMg", 0x66D4A843, "Market");
		y += 88;
		if (data.has("hasTerm") && data.get("hasTerm").getAsBoolean()) {
			JsonArray termHist = data.has("termHistory") ? data.getAsJsonArray("termHistory") : new JsonArray();
			ChartRenderer.renderLine(graphics, font, cx, y, width - cx - 12, 70, termHist, "priceMg", 0xFFAA88FF, "Vadeli Bakiye");
		}
	}

	private void renderDocs(GuiGraphicsExtractor graphics, int cx) {
		int listY = contentTop + 8;
		int listH = contentBottom - listY;
		String content = EconomyPanelClientState.data().has("docs")
				? EconomyPanelClientState.data().get("docs").getAsString() : null;
		docsList.setScrollY(EconomyPanelClientState.scrollY());
		DocsRenderer.render(graphics, font, docsList, cx, listY, width - cx - 12, listH, content);
	}

	private void renderMarket(GuiGraphicsExtractor graphics, int cx, int mouseX, int mouseY) {
		int cols = 8;
		int cellW = 36;
		int cellH = 42;
		int startY = contentTop + 90;
		int viewH = contentBottom - startY;
		JsonArray items = EconomyPanelClientState.marketItems();
		int scroll = EconomyPanelClientState.scrollY();
		int totalRows = (items.size() + cols - 1) / cols;
		int visibleRows = Math.max(1, viewH / cellH);
		int firstRow = scroll / cellH;

		for (int i = firstRow * cols; i < items.size(); i++) {
			int rowIdx = i / cols;
			int relRow = rowIdx - firstRow;
			int iy = startY + relRow * cellH - (scroll % cellH);
			if (iy + cellH < startY || iy > contentBottom) {
				continue;
			}
			JsonObject row = items.get(i).getAsJsonObject();
			String itemId = row.get("itemId").getAsString();
			int col = i % cols;
			int ix = cx + col * cellW;
			boolean hover = mouseX >= ix && mouseX < ix + cellW - 2 && mouseY >= iy && mouseY < iy + cellH;
			boolean sel = itemId.equals(EconomyPanelClientState.selectedItemId());
			int bg = sel ? 0x884488FF : (hover ? 0x44FFFFFF : 0x22000000);
			graphics.fill(ix, iy, ix + cellW - 2, iy + cellH - 2, bg);
			ItemStack stack = EconomyPanelClientState.iconFor(itemId);
			if (!stack.isEmpty()) {
				graphics.item(stack, ix + 10, iy + 4);
			}
			String price = row.has("price") ? row.get("price").getAsString() : "?";
			graphics.text(font, truncate(price, 7), ix + 2, iy + 26, 0xFF88FF88, false);
			graphics.text(font, truncate(row.get("name").getAsString(), 6), ix + 2, iy + 34, 0xFFCCCCCC, false);
		}
		if (totalRows > visibleRows) {
			int barH = Math.max(12, viewH * visibleRows / totalRows);
			int maxScroll = Math.max(0, totalRows * cellH - viewH);
			int barY = startY + (maxScroll == 0 ? 0 : scroll * (viewH - barH) / maxScroll);
			graphics.fill(width - 14, barY, width - 10, barY + barH, 0xAAE8C547);
		}
		graphics.text(font, "Sayfa " + (EconomyPanelClientState.marketPage() + 1)
				+ "/" + EconomyPanelClientState.marketPageCount(), cx + 50, contentTop + 48, 0xFFAAAAAA, false);
	}

	private void renderInventory(GuiGraphicsExtractor graphics, int cx, int mouseX, int mouseY) {
		JsonArray items = EconomyPanelClientState.inventoryItems();
		int listY = contentTop + 70;
		int listH = contentBottom - listY;
		contentList.setRowCount(items.size());
		contentList.rowHeight(22).visibleRows(Math.max(1, listH / 22));
		contentList.setScrollY(EconomyPanelClientState.scrollY());
		String selected = EconomyPanelClientState.selectedItemId();
		if (items.isEmpty()) {
			graphics.text(font, "Envanter bos veya sync bekleniyor — Yenile.", cx, listY, 0xFFAAAAAA, false);
			return;
		}
		contentList.renderRows(graphics, font, cx, listY, width - cx - 12, listH, (g, row) -> {
			JsonObject item = items.get(row.index()).getAsJsonObject();
			String itemId = item.get("itemId").getAsString();
			ItemStack stack = EconomyPanelClientState.iconFor(itemId);
			if (!stack.isEmpty()) {
				g.item(stack, row.x() + 2, row.y() + 2);
			}
			String line = item.get("count").getAsInt() + "x " + item.get("name").getAsString();
			if (item.has("price")) {
				line += " [" + item.get("price").getAsString() + "]";
			}
			g.text(font, truncate(line, 42), row.x() + 22, row.y() + 6, 0xFFFFFFFF, false);
		}, selected != null ? indexOfItem(items, selected) : null);
	}

	private void renderBank(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		int y = contentTop + 8;
		graphics.text(font, "Vadesiz Hesap", cx, y, 0xFF88CCFF, false);
		y += 12;
		if (data.has("hasChecking") && data.get("hasChecking").getAsBoolean()) {
			graphics.text(font, "Bakiye: " + text(data, "checking", text(data, "bank", "-")), cx, y, 0xFFFFFFFF, false);
		} else {
			graphics.text(font, "Hesap yok — Vadesiz Hesap Ac", cx, y, 0xFFAAAAAA, false);
		}
		y += 18;
		graphics.text(font, "Vadeli Hesap", cx, y, 0xFFAA88FF, false);
		y += 12;
		if (data.has("hasTerm") && data.get("hasTerm").getAsBoolean()) {
			graphics.text(font, "Bakiye: " + text(data, "termBalance", "-"), cx, y, 0xFFFFFFFF, false);
			y += 12;
			if (data.has("termInterestTotalPct") || data.has("termInterestRate")) {
				int pct = data.has("termInterestTotalPct")
						? data.get("termInterestTotalPct").getAsInt()
						: (int) Math.round(data.get("termInterestRate").getAsDouble() * 100);
				int sec = data.has("termInterestIntervalSec")
						? data.get("termInterestIntervalSec").getAsInt() : 60;
				graphics.text(font, "7 gun getiri: %" + pct + " / " + sec + " sn", cx, y, 0xFFCCCCCC, false);
				y += 12;
			}
			if (data.has("termMatured") && data.get("termMatured").getAsBoolean()) {
				graphics.text(font, "Vade doldu — cekim yapilabilir", cx, y, 0xFF7BED9F, false);
			} else if (data.has("termMaturityDaysLeft")) {
				graphics.text(font, "Kalan vade: " + data.get("termMaturityDaysLeft").getAsLong() + " gun", cx, y, 0xFFE8C547, false);
			} else {
				graphics.text(font, "Vade dolana kadar cekim yok", cx, y, 0xFFE8C547, false);
			}
		} else {
			graphics.text(font, "Hesap yok — Vadeli Hesap Ac", cx, y, 0xFFAAAAAA, false);
		}
		y += 16;
		graphics.text(font, "Kulce islemleri vadesiz hesaba yatar.", cx, y, 0xFF888888, false);
	}

	private void renderWallet(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		int y = contentTop + 8;
		graphics.text(font, "Cuzdan: " + text(data, "wallet", "-"), cx, y, 0xFFE8C547, false);
		y += 14;
		String acct = EconomyPanelClientState.formField("bankAccountType", "checking");
		graphics.text(font, "Secili hesap: " + ("term".equals(acct) ? "Vadeli" : "Vadesiz"), cx, y, 0xFFDDDDDD, false);
		y += 12;
		if ("term".equals(acct)) {
			if (data.has("hasTerm") && data.get("hasTerm").getAsBoolean()) {
				graphics.text(font, "Vadeli bakiye: " + text(data, "termBalance", "-"), cx, y, 0xFFAA88FF, false);
			} else {
				graphics.text(font, "Once Banka sekmesinden vadeli hesap acin.", cx, y, 0xFFFF8888, false);
			}
		} else {
			graphics.text(font, "Vadesiz bakiye: " + text(data, "checking", text(data, "bank", "-")), cx, y, 0xFF88CCFF, false);
		}
	}

	private void renderLoan(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		String line = data.has("hasLoan") && data.get("hasLoan").getAsBoolean()
				? "Borc: " + text(data, "loanRemaining", "?") : "Aktif kredi yok";
		graphics.text(font, line, cx, contentTop + 80, 0xFFDDDDDD, false);
	}

	private void renderJob(GuiGraphicsExtractor graphics, int cx) {
		graphics.text(font, "Meslek: " + text(EconomyPanelClientState.data(), "job", "-"), cx, contentTop + 80, 0xFFDDDDDD, false);
	}

	private void renderExchange(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		JsonArray tokens = data.has("tokens") ? data.getAsJsonArray("tokens") : new JsonArray();
		JsonArray companies = data.has("companies") ? data.getAsJsonArray("companies") : new JsonArray();
		int y = contentTop + 200;
		graphics.text(font, "Tokenler:", cx, y, 0xFFE8C547, false);
		y += 12;
		for (int i = 0; i < Math.min(6, tokens.size()); i++) {
			JsonObject t = tokens.get(i).getAsJsonObject();
			graphics.text(font, t.get("symbol").getAsString(), cx, y, 0xFFDDDDDD, false);
			y += 11;
		}
		y += 6;
		graphics.text(font, "Hisseler:", cx, y, 0xFFE8C547, false);
		y += 12;
		for (int i = 0; i < Math.min(6, companies.size()); i++) {
			JsonObject c = companies.get(i).getAsJsonObject();
			graphics.text(font, c.get("ticker").getAsString() + " " + c.get("name").getAsString(), cx, y, 0xFFDDDDDD, false);
			y += 11;
		}
	}

	private void renderEmployees(GuiGraphicsExtractor graphics, int cx) {
		int listY = contentTop + 200;
		int listH = contentBottom - listY;
		List<String> lines = buildEmployeeLines();
		bulletinList.setRows(lines);
		bulletinList.setScrollY(EconomyPanelClientState.scrollY());
		bulletinList.rowHeight(12).visibleRows(Math.max(1, listH / 12));
		if (lines.isEmpty() || lines.size() == 1 && lines.get(0).contains("Sirket yok")) {
			graphics.text(font, lines.isEmpty() ? "Veri yok — Yenile." : lines.get(0), cx, listY, 0xFFAAAAAA, false);
			return;
		}
		bulletinList.render(graphics, font, cx, listY, width - cx - 12, listH, 0xFFDDDDDD, null);
	}

	private List<String> buildEmployeeLines() {
		List<String> lines = new ArrayList<>();
		JsonObject data = EconomyPanelClientState.data();
		if (!data.has("workforce") || !data.get("workforce").isJsonObject()) {
			return lines;
		}
		JsonObject wf = data.getAsJsonObject("workforce");
		if (!wf.has("companies") || !wf.get("companies").isJsonArray()) {
			return lines;
		}
		JsonArray companies = wf.getAsJsonArray("companies");
		if (companies.isEmpty()) {
			lines.add("Sahibi oldugunuz sirket yok.");
			return lines;
		}
		for (int ci = 0; ci < companies.size(); ci++) {
			JsonObject c = companies.get(ci).getAsJsonObject();
			String cname = c.has("name") ? c.get("name").getAsString() : "?";
			String treasury = c.has("treasury") ? c.get("treasury").getAsString() : "?";
			lines.add("--- " + cname + " | Kasa: " + treasury + " ---");
			if (c.has("stash") && c.get("stash").isJsonArray()) {
				JsonArray stash = c.getAsJsonArray("stash");
				if (stash.isEmpty()) {
					lines.add("  Depo: (bos)");
				} else {
					for (int si = 0; si < stash.size(); si++) {
						JsonObject s = stash.get(si).getAsJsonObject();
						lines.add("  Depo: " + s.get("quantity").getAsInt() + "x "
								+ (s.has("name") ? s.get("name").getAsString() : "?"));
					}
				}
			}
			lines.add("  Calisanlar:");
			appendPeopleLines(lines, c, "employees", "    ", false);
			lines.add("  Basvurular:");
			appendPeopleLines(lines, c, "applications", "    [BASVURU] ", true);
		}
		return lines;
	}

	private void appendPeopleLines(List<String> lines, JsonObject company, String key, String prefix, boolean application) {
		if (!company.has(key) || !company.get(key).isJsonArray()) {
			lines.add(prefix + "(yok)");
			return;
		}
		JsonArray arr = company.getAsJsonArray(key);
		if (arr.isEmpty()) {
			lines.add(prefix + "(yok)");
			return;
		}
		for (int i = 0; i < arr.size(); i++) {
			JsonObject e = arr.get(i).getAsJsonObject();
			long id = e.has("id") ? e.get("id").getAsLong() : 0;
			String name = e.has("name") ? e.get("name").getAsString() : "?";
			String role = e.has("role") ? e.get("role").getAsString() : "?";
			String salary = e.has("salary") ? e.get("salary").getAsString() : "?";
			if (application) {
				String msg = e.has("message") ? e.get("message").getAsString() : "";
				lines.add(prefix + "#" + id + " " + name + " | " + role + " | " + salary + " — " + truncate(msg, 28));
			} else {
				String produced = e.has("produced") ? e.get("produced").getAsString() : "—";
				lines.add(prefix + "#" + id + " " + name + " | " + role + " | Maas: " + salary + " | Uretim: " + produced);
			}
		}
	}

	private void renderPropertyList(GuiGraphicsExtractor graphics, int cx) {
		int y = contentTop + 60;
		graphics.text(font, "Gayrimenkul", cx, y, 0xFFE8C547, false);
		y += 14;
		if (!EconomyPanelClientState.data().has("properties")
				|| !EconomyPanelClientState.data().get("properties").isJsonArray()) {
			graphics.text(font, "Gayrimenkul yok.", cx, y, 0xFFAAAAAA, false);
			return;
		}
		JsonArray arr = EconomyPanelClientState.data().getAsJsonArray("properties");
		for (int i = 0; i < arr.size(); i++) {
			JsonObject p = arr.get(i).getAsJsonObject();
			String line = "#" + p.get("id").getAsLong() + " " + p.get("tier").getAsString()
					+ " @ " + p.get("x").getAsInt() + "," + p.get("z").getAsInt();
			graphics.text(font, line, cx, y, 0xFFCCCCCC, false);
			y += 11;
		}
	}

	private void renderVehicleList(GuiGraphicsExtractor graphics, int cx) {
		int y = contentTop + 60;
		graphics.text(font, "Garaj", cx, y, 0xFFE8C547, false);
		y += 14;
		if (!EconomyPanelClientState.data().has("vehicles")
				|| !EconomyPanelClientState.data().get("vehicles").isJsonArray()) {
			graphics.text(font, "Arac yok.", cx, y, 0xFFAAAAAA, false);
			return;
		}
		JsonArray arr = EconomyPanelClientState.data().getAsJsonArray("vehicles");
		for (int i = 0; i < arr.size(); i++) {
			JsonObject v = arr.get(i).getAsJsonObject();
			String line = "#" + v.get("id").getAsLong() + " " + v.get("model").getAsString()
					+ " | Yakit: " + v.get("fuel").getAsInt()
					+ (v.has("spawned") && v.get("spawned").getAsBoolean() ? " [SPAWN]" : "");
			graphics.text(font, line, cx, y, 0xFFCCCCCC, false);
			y += 11;
		}
	}

	private void renderListPanel(GuiGraphicsExtractor graphics, int cx, String key, String title) {
		int y = contentTop + 60;
		graphics.text(font, title, cx, y, 0xFFE8C547, false);
		y += 14;
		if (!EconomyPanelClientState.data().has(key)) {
			graphics.text(font, "Veri yok — Yenile.", cx, y, 0xFFAAAAAA, false);
			return;
		}
		var el = EconomyPanelClientState.data().get(key);
		if (el.isJsonArray()) {
			JsonArray arr = el.getAsJsonArray();
			for (int i = 0; i < Math.min(12, arr.size()); i++) {
				graphics.text(font, arr.get(i).toString(), cx, y, 0xFFCCCCCC, false);
				y += 11;
			}
		} else {
			graphics.text(font, el.toString(), cx, y, 0xFFCCCCCC, false);
		}
	}

	private void renderAdminDashboard(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		int y = contentTop + 8;
		String[] keys = {"playerCount", "appealCount", "reportCount", "prisonerCount", "alertCount"};
		String[] labels = {"Kayitli Oyuncu", "Acik Itiraz", "Sikayet", "Hapiste", "MASAK Uyari"};
		for (int i = 0; i < keys.length; i++) {
			graphics.text(font, labels[i] + ": " + text(data, keys[i], "—"), cx, y, 0xFFDDDDDD, false);
			y += 12;
		}
	}

	private void renderHint(GuiGraphicsExtractor graphics, int cx, String hint) {
		graphics.text(font, hint, cx, contentTop + 60, 0xFFAAAAAA, false);
	}

	private void renderAdminPlayers(GuiGraphicsExtractor graphics, int cx, int mouseX, int mouseY) {
		int listY = contentTop + 70;
		int listH = contentBottom - listY;
		JsonArray players = EconomyPanelClientState.data().has("adminPlayers")
				? EconomyPanelClientState.data().getAsJsonArray("adminPlayers") : new JsonArray();
		contentList.setRowCount(players.size());
		contentList.rowHeight(18).visibleRows(Math.max(1, listH / 18));
		contentList.setScrollY(EconomyPanelClientState.scrollY());
		if (players.isEmpty()) {
			graphics.text(font, "Oyuncu yok veya OP degilsiniz — Ara / Yenile.", cx, listY, 0xFFAAAAAA, false);
			return;
		}
		String selected = EconomyPanelClientState.selectedId("adminPlayer");
		contentList.renderRows(graphics, font, cx, listY, width - cx - 12, listH, (g, row) -> {
			JsonObject p = players.get(row.index()).getAsJsonObject();
			String name = p.get("name").getAsString();
			String line = name + (p.has("online") && p.get("online").getAsBoolean() ? " [ON]" : "");
			if (p.has("walletMg")) {
				line += " $" + p.get("walletMg").getAsLong();
			}
			g.text(font, truncate(line, 48), row.x() + 2, row.y() + 5, 0xFFFFFFFF, false);
		}, selected != null ? indexOfAdminPlayer(players, selected) : null);
	}

	private void renderJsonList(GuiGraphicsExtractor graphics, int cx, String key, String nestedKey, String title) {
		int y = contentTop + 120;
		graphics.text(font, title, cx, y, 0xFFE8C547, false);
		y += 14;
		JsonArray arr = new JsonArray();
		if (EconomyPanelClientState.data().has(key)) {
			var el = EconomyPanelClientState.data().get(key);
			if (nestedKey != null && el.isJsonObject() && el.getAsJsonObject().has(nestedKey)) {
				arr = el.getAsJsonObject().getAsJsonArray(nestedKey);
			} else if (el.isJsonArray()) {
				arr = el.getAsJsonArray();
			}
		}
		for (int i = 0; i < Math.min(16, arr.size()); i++) {
			graphics.text(font, truncate(arr.get(i).toString(), 56), cx, y, 0xFFCCCCCC, false);
			y += 11;
		}
		if (arr.isEmpty()) {
			graphics.text(font, "Kayit yok — Yenile.", cx, y, 0xFFAAAAAA, false);
		}
	}

	private void renderGuild(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		int y = contentTop + 120;
		if (data.has("name")) {
			graphics.text(font, "Lonca: " + data.get("name").getAsString(), cx, y, 0xFFE8C547, false);
			y += 12;
			if (data.has("treasury")) {
				graphics.text(font, "Kasa: " + data.get("treasury").getAsString(), cx, y, 0xFFDDDDDD, false);
			}
		} else {
			graphics.text(font, "Lonca uyeligi yok.", cx, y, 0xFFAAAAAA, false);
		}
	}

	private void renderMunicipal(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		int y = contentTop + 120;
		graphics.text(font, "Baskan: " + text(data, "mayorName", "—"), cx, y, 0xFFDDDDDD, false);
		y += 12;
		graphics.text(font, "Butce: " + text(data, "budget", "—"), cx, y, 0xFFDDDDDD, false);
	}

	private void renderGovernment(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		int y = contentTop + 120;
		graphics.text(font, data.has("isMinister") && data.get("isMinister").getAsBoolean()
				? "Ekonomi Bakani yetkisi var" : "Bakan degilsiniz", cx, y, 0xFFDDDDDD, false);
		y += 14;
		if (data.has("pendingDecrees")) {
			JsonArray pending = data.getAsJsonArray("pendingDecrees");
			for (int i = 0; i < Math.min(8, pending.size()); i++) {
				JsonObject d = pending.get(i).getAsJsonObject();
				graphics.text(font, "#" + d.get("id").getAsLong() + " " + d.get("type").getAsString(),
						cx, y, 0xFFCCCCCC, false);
				y += 11;
			}
		}
	}

	private void renderEconomyAdmin(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		int y = contentTop + 200;
		if (data.has("inflationRate")) {
			graphics.text(font, "Enflasyon: " + String.format("%.2f%%",
					data.get("inflationRate").getAsDouble() * 100), cx, y, 0xFFDDDDDD, false);
			y += 12;
		}
		if (data.has("economyIndex")) {
			graphics.text(font, "Endeks: " + data.get("economyIndex").getAsDouble(), cx, y, 0xFFDDDDDD, false);
			y += 12;
		}
		if (data.has("companies")) {
			graphics.text(font, "Sirketler: " + data.getAsJsonArray("companies").size(), cx, y, 0xFFDDDDDD, false);
			y += 12;
		}
		if (data.has("tokens")) {
			graphics.text(font, "Coinler: " + data.getAsJsonArray("tokens").size(), cx, y, 0xFFDDDDDD, false);
		}
	}

	private void renderCameras(GuiGraphicsExtractor graphics, int cx) {
		int y = contentTop + 120;
		if (!EconomyPanelClientState.data().has("securityCameras")) {
			graphics.text(font, "Gece indeksi secip Yukle.", cx, y, 0xFFAAAAAA, false);
			return;
		}
		JsonObject cams = EconomyPanelClientState.data().getAsJsonObject("securityCameras");
		if (cams.has("logs") && cams.get("logs").isJsonArray()) {
			JsonArray logs = cams.getAsJsonArray("logs");
			for (int i = 0; i < Math.min(20, logs.size()); i++) {
				graphics.text(font, truncate(logs.get(i).toString(), 56), cx, y, 0xFFCCCCCC, false);
				y += 11;
			}
		}
	}

	private void renderConfig(GuiGraphicsExtractor graphics, int cx) {
		JsonObject data = EconomyPanelClientState.data();
		int y = contentTop + 120;
		if (data.has("path")) {
			graphics.text(font, "Dosya: " + data.get("path").getAsString(), cx, y, 0xFFE8C547, false);
			y += 14;
		}
		if (data.has("error")) {
			graphics.text(font, data.get("error").getAsString(), cx, y, 0xFFFF6B6B, false);
			return;
		}
		if (!data.has("json")) {
			graphics.text(font, "Config yuklenmedi — Yenile.", cx, y, 0xFFAAAAAA, false);
			return;
		}
		String json = data.get("json").getAsString();
		int listY = y;
		int listH = contentBottom - listY;
		List<String> lines = new ArrayList<>();
		for (String line : json.split("\n", -1)) {
			lines.add(line);
		}
		docsList.setRows(lines);
		docsList.setScrollY(EconomyPanelClientState.scrollY());
		docsList.rowHeight(12).visibleRows(Math.max(1, listH / 12));
		docsList.render(graphics, font, cx, listY, width - cx - 12, listH, 0xFFCCCCCC, null);
	}

	private void renderJusticeAdmin(GuiGraphicsExtractor graphics, int cx) {
		renderJsonList(graphics, cx, "openReports", null, "Acik Raporlar");
		int y = contentTop + 280;
		if (EconomyPanelClientState.data().has("activePrisoners")) {
			JsonArray prisoners = EconomyPanelClientState.data().getAsJsonArray("activePrisoners");
			graphics.text(font, "Hapistekiler: " + prisoners.size(), cx, y, 0xFFE8C547, false);
		}
	}

	private static int indexOfAdminPlayer(JsonArray players, String uuid) {
		for (int i = 0; i < players.size(); i++) {
			if (players.get(i).getAsJsonObject().get("uuid").getAsString().equals(uuid)) {
				return i;
			}
		}
		return -1;
	}

	private void renderMarketPreview(GuiGraphicsExtractor graphics, int cx, int y, int max) {
		JsonArray items = EconomyPanelClientState.marketItems();
		for (int i = 0; i < Math.min(max, items.size()); i++) {
			JsonObject row = items.get(i).getAsJsonObject();
			graphics.text(font, row.get("name").getAsString() + " " + row.get("price").getAsString(),
					cx, y + i * 12, 0xFFDDDDDD, false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();
		int cx = SIDEBAR_W + 8;
		String tab = EconomyPanelClientState.tab();
		if ("market".equals(tab)) {
			String hit = hitMarketItem(cx, mouseX, mouseY);
			if (hit != null) {
				EconomyPanelClientState.selectItem(hit);
				rebuildWidgets();
				return true;
			}
		}
		if ("inventory".equals(tab)) {
			int idx = contentList.hitRow(mouseX, mouseY, cx, contentTop + 70, width - cx - 12, contentBottom - contentTop - 70);
			if (idx >= 0) {
				JsonArray items = EconomyPanelClientState.inventoryItems();
				if (idx < items.size()) {
					EconomyPanelClientState.selectItem(items.get(idx).getAsJsonObject().get("itemId").getAsString());
					rebuildWidgets();
					return true;
				}
			}
		}
		if ("players".equals(tab)) {
			int idx = contentList.hitRow(mouseX, mouseY, cx, contentTop + 70, width - cx - 12, contentBottom - contentTop - 70);
			if (idx >= 0 && EconomyPanelClientState.data().has("adminPlayers")) {
				JsonArray players = EconomyPanelClientState.data().getAsJsonArray("adminPlayers");
				if (idx < players.size()) {
					JsonObject p = players.get(idx).getAsJsonObject();
					EconomyPanelClientState.selectId("adminPlayer", p.get("uuid").getAsString());
					EconomyPanelClientState.selectId("adminPlayerName", p.get("name").getAsString());
					rebuildWidgets();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < SIDEBAR_W) {
			EconomyPanelClientState.scrollSidebarBy((int) scrollY);
			rebuildWidgets();
			return true;
		}
		int cx = SIDEBAR_W + 8;
		String tab = EconomyPanelClientState.tab();
		int delta = (int) (scrollY * 14);
		if ("market".equals(tab)) {
			EconomyPanelClientState.scrollBy(delta);
			return true;
		}
		if ("inventory".equals(tab) || "docs".equals(tab) || "bulletins".equals(tab) || "config".equals(tab)
				|| "players".equals(tab) || "employees".equals(tab)) {
			EconomyPanelClientState.scrollBy(delta);
			if ("inventory".equals(tab)) {
				contentList.scrollBy((int) scrollY);
			} else if ("players".equals(tab)) {
				contentList.scrollBy((int) scrollY);
			} else if ("config".equals(tab)) {
				docsList.scrollBy((int) scrollY);
			} else if ("employees".equals(tab)) {
				bulletinList.scrollBy((int) scrollY);
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private String hitMarketItem(int cx, int mouseX, int mouseY) {
		int cols = 8;
		int cellW = 36;
		int cellH = 42;
		int startY = contentTop + 90;
		int scroll = EconomyPanelClientState.scrollY();
		JsonArray items = EconomyPanelClientState.marketItems();
		for (int i = 0; i < items.size(); i++) {
			int rowIdx = i / cols;
			int relRow = rowIdx - scroll / cellH;
			int iy = startY + relRow * cellH - (scroll % cellH);
			int col = i % cols;
			int ix = cx + col * cellW;
			if (mouseX >= ix && mouseX < ix + cellW - 2 && mouseY >= iy && mouseY < iy + cellH) {
				return items.get(i).getAsJsonObject().get("itemId").getAsString();
			}
		}
		return null;
	}

	private static int indexOfItem(JsonArray items, String itemId) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).getAsJsonObject().get("itemId").getAsString().equals(itemId)) {
				return i;
			}
		}
		return -1;
	}

	private static String text(JsonObject data, String key, String def) {
		return data.has(key) ? data.get(key).getAsString() : def;
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
