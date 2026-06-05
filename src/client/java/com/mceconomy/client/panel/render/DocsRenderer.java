package com.mceconomy.client.panel.render;

import com.mceconomy.client.panel.components.ScrollableList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Arrays;
import java.util.List;

/** Rehber metnini kaydirilabilir gosterim. */
public final class DocsRenderer {
	private static final List<String> DOCS_LINES = Arrays.asList(
			"MC Economy Rehberi",
			"Para birimi: $ (dolar). Yeni oyuncular 100.000 $ ile baslar.",
			"",
			"Cuzdan & Banka",
			"- Cuzdan <-> Banka transferi panel veya komutlarla.",
			"- Vadesiz / vadeli hesap acilabilir.",
			"- Kulce altin yatirma / cekme envanter islemidir.",
			"",
			"Market",
			"- Emtia al/sat; fiyatlar arz-talebe gore degisir.",
			"- Envanterden satis icin cevrimici olmalisiniz.",
			"",
			"Borsa & Coin",
			"- Coin al/sat, sirket listele, kaldiracli islem (CFD).",
			"- Yuksek risk: likidasyon mumkun.",
			"",
			"Sirket & Calisanlar",
			"- Sirket kur, hisse al/sat, gizli sandik / uretim deposu.",
			"- NPC calisanlar ve maas yonetimi.",
			"",
			"Meslek & Gorev",
			"- Meslek sec, gorev al/teslim et, sirket gorevleri.",
			"",
			"Kara Borsa & Adalet",
			"- Kara borsa al/sat, para aklama.",
			"- Sikayet, ihbar, itiraz formlari.",
			"",
			"Kasa & Soygun",
			"- Kisisel kasa isinlanma, MB soygun protokolu.",
			"",
			"Detay: /yardim ve web dashboard Rehber sekmesi."
	);

	private DocsRenderer() {
	}

	public static List<String> lines(String fromSync) {
		if (fromSync != null && !fromSync.isBlank()) {
			return Arrays.asList(fromSync.split("\n"));
		}
		return DOCS_LINES;
	}

	public static void render(GuiGraphicsExtractor graphics, Font font, ScrollableList list,
			int x, int y, int w, int h, String syncContent) {
		list.setRows(lines(syncContent));
		list.rowHeight(12).visibleRows(h / 12);
		list.render(graphics, font, x, y, w, h, 0xFFDDDDDD, null);
	}
}
