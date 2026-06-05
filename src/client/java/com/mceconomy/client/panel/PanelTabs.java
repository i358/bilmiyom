package com.mceconomy.client.panel;

import java.util.ArrayList;
import java.util.List;

/** Dashboard index.html ile eslesen sekme tanimlari. */
public final class PanelTabs {
	public record TabEntry(String id, String label, String group, boolean admin) {
	}

	private static final TabEntry[] PLAYER = {
			// Genel
			new TabEntry("overview", "Genel Bakis", "Genel", false),
			new TabEntry("macro", "Makro & Fiat", "Genel", false),
			new TabEntry("map", "Canli Harita", "Genel", false),
			new TabEntry("bulletins", "Bulten Arsivi", "Genel", false),
			new TabEntry("charts", "Grafikler", "Genel", false),
			new TabEntry("inventory", "Envanter", "Genel", false),
			new TabEntry("docs", "Rehber", "Genel", false),
			// Islemler
			new TabEntry("wallet", "Cuzdan & Odeme", "Islemler", false),
			new TabEntry("bank", "Banka", "Islemler", false),
			new TabEntry("market", "Market", "Islemler", false),
			new TabEntry("loan", "Kredi", "Islemler", false),
			new TabEntry("insurance", "Sigorta", "Islemler", false),
			new TabEntry("trade", "Takas", "Islemler", false),
			// Sosyal
			new TabEntry("guild", "Lonca", "Sosyal", false),
			// Yatirim
			new TabEntry("exchange", "Borsa & Coin", "Yatirim", false),
			new TabEntry("company", "Sirket & Hisse", "Yatirim", false),
			new TabEntry("employees", "Calisanlar", "Yatirim", false),
			new TabEntry("privatebank", "Ozel Banka", "Yatirim", false),
			new TabEntry("property", "Gayrimenkul", "Yatirim", false),
			new TabEntry("vehicle", "Arac", "Yatirim", false),
			// Devlet
			new TabEntry("municipal", "Belediye", "Devlet", false),
			new TabEntry("government", "Bakanlik", "Devlet", false),
			// Guvenlik
			new TabEntry("vault", "Kasa & Soygun", "Guvenlik", false),
			new TabEntry("appeals", "Itirazlar", "Guvenlik", false),
			new TabEntry("justice", "Adalet", "Guvenlik", false),
			// Yasadisi
			new TabEntry("illegal", "Kara Borsa", "Yasadisi", false),
			new TabEntry("casino", "Casino", "Yasadisi", false),
			new TabEntry("job", "Meslek & Gorev", "Yasadisi", false),
	};

	private static final TabEntry[] ADMIN = {
			new TabEntry("dashboard", "Ozet", "OP Genel", true),
			new TabEntry("players", "Oyuncular", "OP Genel", true),
			new TabEntry("economy-admin", "Ekonomi Yonetimi", "OP Genel", true),
			new TabEntry("economy", "Merkez Bankasi", "OP Genel", true),
			new TabEntry("masak", "MASAK", "OP Denetim", true),
			new TabEntry("appeals-review", "Itirazlar", "OP Denetim", true),
			new TabEntry("justice-admin", "Adalet", "OP Denetim", true),
			new TabEntry("cameras", "Guvenlik Kameralari", "OP Denetim", true),
			new TabEntry("events", "Ekonomi Olaylari", "OP Yonetim", true),
			new TabEntry("mbop", "MB Yetkileri", "OP Yonetim", true),
			new TabEntry("blackmarket-admin", "Karaborsa", "OP Yonetim", true),
			new TabEntry("tools", "Araclar", "OP Yonetim", true),
			new TabEntry("config", "Config", "OP Yonetim", true),
	};

	private PanelTabs() {
	}

	public static List<TabEntry> visibleTabs() {
		List<TabEntry> out = new ArrayList<>();
		for (TabEntry t : PLAYER) {
			out.add(t);
		}
		if (EconomyPanelClientState.isOp() || EconomyPanelClientState.adminMode()) {
			for (TabEntry t : ADMIN) {
				out.add(t);
			}
		}
		return out;
	}

	public static String titleFor(String tabId) {
		for (TabEntry t : PLAYER) {
			if (t.id().equals(tabId)) {
				return t.label();
			}
		}
		for (TabEntry t : ADMIN) {
			if (t.id().equals(tabId)) {
				return t.label();
			}
		}
		return tabId;
	}

	public static int playerTabCount() {
		return PLAYER.length;
	}

	public static int adminTabCount() {
		return ADMIN.length;
	}
}
