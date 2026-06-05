package com.mceconomy.client.panel;

import com.google.gson.JsonObject;
import com.mceconomy.client.panel.components.FormFields;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** OP oyuncu yonetim alt ekrani — admin.html modal paritesi. */
public class PlayerAdminModal extends Screen {
	private final Screen parent;
	private final String playerName;
	private final String playerUuid;

	public PlayerAdminModal(Screen parent, String playerName, String playerUuid) {
		super(Component.literal("Oyuncu: " + playerName));
		this.parent = parent;
		this.playerName = playerName;
		this.playerUuid = playerUuid;
	}

	@Override
	protected void init() {
		clearWidgets();
		int cx = 20;
		int y = 28;
		FormFields ff = new FormFields(this::addRenderableWidget, font, cx, y);
		ff.label("Oyuncu: " + playerName);
		ff.gap(6);
		ff.numberField("paWalletSet", "Cuzdan ($)", 80, "0");
		ff.button("Cuzdan Ayarla", 100, () -> {
			JsonObject body = playerBody();
			body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("paWalletSet", "0"), 0));
			EconomyPanelNetworking.adminAction("player/wallet/set", body);
		});
		ff.numberField("paWalletAdj", "Cuzdan +/- ($)", 80, "0");
		ff.button("Cuzdan Artir/Azalt", 120, () -> {
			JsonObject body = playerBody();
			body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("paWalletAdj", "0"), 0));
			EconomyPanelNetworking.adminAction("player/wallet/adjust", body);
		});
		ff.gap(8);
		ff.numberField("paBankChecking", "Vadesiz ($)", 80, "0");
		ff.button("Vadesiz Ayarla", 100, () -> {
			JsonObject body = playerBody();
			body.addProperty("type", "checking");
			body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("paBankChecking", "0"), 0));
			EconomyPanelNetworking.adminAction("player/bank/set", body);
		});
		ff.numberField("paDirtySet", "Kara para ($)", 80, "0");
		ff.button("Kara Para Ayarla", 110, () -> {
			JsonObject body = playerBody();
			body.addProperty("mc", FormFields.parseLong(EconomyPanelClientState.formField("paDirtySet", "0"), 0));
			EconomyPanelNetworking.adminAction("player/dirty/set", body);
		});
		ff.gap(8);
		ff.textField("paShareTicker", "Hisse ticker", 80, "");
		ff.numberField("paShareAmount", "Adet", 60, "0");
		ff.button("Hisse Ayarla", 90, () -> {
			JsonObject body = playerBody();
			body.addProperty("ticker", EconomyPanelClientState.formField("paShareTicker", ""));
			body.addProperty("amount", FormFields.parseInt(EconomyPanelClientState.formField("paShareAmount", "0"), 0));
			EconomyPanelNetworking.adminAction("player/shares/set", body);
		});
		ff.textField("paTokenSymbol", "Coin", 60, "");
		ff.numberField("paTokenAmount", "Adet", 60, "0");
		ff.button("Coin Ayarla", 90, () -> {
			JsonObject body = playerBody();
			body.addProperty("symbol", EconomyPanelClientState.formField("paTokenSymbol", ""));
			body.addProperty("amount", FormFields.parseInt(EconomyPanelClientState.formField("paTokenAmount", "0"), 0));
			EconomyPanelNetworking.adminAction("player/tokens/set", body);
		});
		ff.gap(8);
		ff.button("Vadesiz Ac", 80, () -> EconomyPanelNetworking.adminAction("player/bank/open-checking", playerBody()));
		ff.button("Vadeli Ac", 70, () -> EconomyPanelNetworking.adminAction("player/bank/open-term", playerBody()));
		ff.button("Kapat", 60, () -> minecraft.setScreen(parent));
	}

	private JsonObject playerBody() {
		JsonObject body = new JsonObject();
		body.addProperty("player", playerName);
		body.addProperty("uuid", playerUuid);
		body.addProperty("tab", "players");
		return body;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		extractTransparentBackground(graphics);
		graphics.fill(10, 10, width - 10, height - 10, 0xEE1A2430);
		graphics.text(font, "Oyuncu Yonetimi — " + playerName, 16, 14, 0xFFE8C547, false);
		JsonObject detail = EconomyPanelClientState.data().has("adminPlayerDetail")
				? EconomyPanelClientState.data().getAsJsonObject("adminPlayerDetail") : null;
		if (detail != null) {
			int y = 14;
			if (detail.has("wallet")) {
				graphics.text(font, "Cuzdan: " + detail.get("wallet").getAsString(), width / 2, y, 0xFFDDDDDD, false);
				y += 12;
			}
			if (detail.has("bank")) {
				graphics.text(font, "Banka: " + detail.get("bank").getAsString(), width / 2, y, 0xFFDDDDDD, false);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
