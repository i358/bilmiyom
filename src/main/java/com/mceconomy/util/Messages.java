package com.mceconomy.util;

import com.mceconomy.economy.GoldStandard;
import net.minecraft.network.chat.Component;

public final class Messages {
	private Messages() {
	}

	public static Component tr(String key, Object... args) {
		return Component.literal(format(key, args));
	}

	private static String format(String key, Object... args) {
		return switch (key) {
			case "command.mceconomy.balance.self" ->
					"Cüzdan: " + gold(args[0]);
			case "command.mceconomy.balance.other" ->
					args[0] + " cüzdanı: " + gold(args[1]);
			case "command.mceconomy.balance.bank" ->
					"Banka: " + gold(args[0]);
			case "command.mceconomy.balance.total" ->
					"Toplam servet: " + gold(args[0]);
			case "command.mceconomy.pay.success" ->
					args[0] + " oyuncusuna " + gold(args[1]) + " gönderildi.";
			case "command.mceconomy.pay.received" ->
					args[0] + " size " + gold(args[1]) + " gönderdi.";
			case "command.mceconomy.pay.insufficient" ->
					"Yetersiz altın bakiyesi.";
			case "command.mceconomy.pay.invalid_amount" ->
					"Geçersiz miktar.";
			case "command.mceconomy.bank.created" ->
					"Banka hesabınız oluşturuldu.";
			case "command.mceconomy.bank.already_exists" ->
					"Zaten bir banka hesabınız var.";
			case "command.mceconomy.bank.no_account" ->
					"Banka hesabınız yok. Menüden veya /banka ac ile oluşturun.";
			case "command.mceconomy.bank.deposit" ->
					gold(args[0]) + " bankaya yatırıldı.";
			case "command.mceconomy.bank.withdraw" ->
					gold(args[0]) + " bankadan çekildi.";
			case "command.mceconomy.bank.transfer" ->
					args[0] + " oyuncusuna " + gold(args[1]) + " transfer edildi.";
			case "command.mceconomy.bank.term_created" ->
					"Vadeli hesap oluşturuldu. Faiz oranı: %" + args[0];
			case "command.mceconomy.bank.physical_deposit" ->
					args[0] + " altın külçesi bankaya yatırıldı (" + gold(args[1]) + ").";
			case "command.mceconomy.bank.physical_withdraw" ->
					args[0] + " altın külçesi bankadan çekildi (" + gold(args[1]) + ").";
			case "command.mceconomy.bank.no_gold" ->
					"Envanterinizde yeterli altın külçesi yok.";
			case "command.mceconomy.bank.wanted_gold_deposit" ->
					"Kayıp MB seri numaralı altın bankaya yatırılamaz. Yalnızca karaborsada eriterek aklayabilirsiniz.";
			case "command.mceconomy.bank.inventory_full" ->
					"Envanteriniz dolu, altın külçesi verilemedi.";
			case "command.mceconomy.gui.opened" ->
					"Merkez Bankası menüsü açıldı.";
			case "command.mceconomy.gui.standard" ->
					GoldStandard.formatWheatExchange();
			case "command.mceconomy.market.price_header" ->
					"=== Market Fiyatları (altın karşılığı) ===";
			case "command.mceconomy.market.price_line" ->
					args[0] + ": " + gold(args[1]) + " (Endeks: " + args[2] + ")";
			case "command.mceconomy.market.buy" ->
					args[0] + " adet " + args[1] + " satın alındı. Toplam: " + gold(args[2]);
			case "command.mceconomy.market.sell" ->
					args[0] + " adet " + args[1] + " satıldı. Kazanç: " + gold(args[2]);
			case "command.mceconomy.market.insufficient_items" ->
					"Yeterli eşyanız yok.";
			case "command.mceconomy.market.insufficient_coins" ->
					"Yeterli altın bakiyeniz yok.";
			case "command.mceconomy.market.invalid_commodity" ->
					"Geçersiz emtia: " + args[0];
			case "command.mceconomy.market.not_buyable" ->
					args[0] + " marketten alınamaz. Altın külçesi sadece ürün satarak kazanılır.";
			case "command.mceconomy.market.not_sellable" ->
					args[0] + " markette satılamaz.";
			case "command.mceconomy.loan.taken" ->
					"Kredi alındı: " + gold(args[0]) + ". Taksit: " + gold(args[1]);
			case "command.mceconomy.loan.paid" ->
					"Taksit ödendi: " + gold(args[0]) + ". Kalan borç: " + gold(args[1]);
			case "command.mceconomy.loan.no_loan" ->
					"Aktif krediniz yok.";
			case "command.mceconomy.loan.denied" ->
					"Kredi skorunuz yetersiz.";
			case "command.mceconomy.centralbank.report" ->
					"=== Merkez Bankası Raporu ===";
			case "command.mceconomy.event.triggered" ->
					"Ekonomi olayı başlatıldı: " + args[0];
			case "command.mceconomy.company.created" ->
					"Şirket kuruldu: " + args[0];
			case "command.mceconomy.company.shares_bought" ->
					args[0] + " hisse satın alındı.";
			case "command.mceconomy.company.shares_sold" ->
					args[0] + " hisse satıldı.";
			case "command.mceconomy.job.set" ->
					"Mesleğiniz: " + args[0];
			case "command.mceconomy.quest.completed" ->
					"Görev tamamlandı! Ödül: " + gold(args[0]);
			case "command.mceconomy.quest.assigned" ->
					args[0] + " (" + args[1] + "/" + args[2] + ") — Ödül: " + gold(args[3]);
			case "command.mceconomy.quest.already_active" ->
					"Zaten aktif bir göreviniz var. /gorev durum";
			case "command.mceconomy.quest.need_job" ->
					"Görev almak için önce /meslek sec ile meslek seçin.";
			case "command.mceconomy.quest.none" ->
					"Aktif göreviniz yok.";
			case "command.mceconomy.quest.missing_items" ->
					"Görev için yeterli eşya yok.";
			case "command.mceconomy.quest.not_complete" ->
					"Görev henüz tamamlanmadı.";
			case "command.mceconomy.error.generic" ->
					"İşlem başarısız.";
			case "command.mceconomy.dirty.balance" ->
					"Kara para bakiyesi: " + gold(args[0]);
			case "command.mceconomy.dirty.insufficient" ->
					"Yeterli kara paranız yok.";
			case "command.mceconomy.blackmarket.sell" ->
					args[0] + " adet " + args[1] + " karaborsada satıldı. Kazanç: " + gold(args[2]) + " (kara para)";
			case "command.mceconomy.blackmarket.buy" ->
					args[0] + " adet " + args[1] + " karaborsadan alındı. Ödeme: " + gold(args[2]) + " (kara para)";
			case "command.mceconomy.launder.success" ->
					"Aklama başarılı! Temiz altın: " + gold(args[0]) + " (risk: %" + args[1] + ")";
			case "command.mceconomy.launder.caught" ->
					"MASAK yakaladı! Ceza: " + gold(args[0]) + " (risk: %" + args[1] + "). Hesap donduruldu.";
			case "command.mceconomy.masak.frozen" ->
					"Hesabınız MASAK tarafından donduruldu. Yeraltı menüsünden aklama deneyebilirsiniz.";
			case "command.mceconomy.masak.blacklisted" ->
					"Kara listedesiniz. Yasal banka işlemleri kapalı.";
			case "command.mceconomy.masak.restricted" ->
					"Bu işlem için hesabınız kısıtlı. MASAK ile görüşün.";
			case "command.mceconomy.exchange.coin_created" ->
					"Coin oluşturuldu: " + args[0] + " (" + args[1] + ")";
			case "command.mceconomy.exchange.coin_failed" ->
					"Coin oluşturulamadı. Sembol benzersiz olmalı (2-6 harf) ve yeterli bakiyeniz olmalı.";
			case "command.mceconomy.exchange.listed" ->
					args[0] + " borsaya listelendi. Ticker: " + args[1];
			case "command.mceconomy.exchange.list_failed" ->
					"Listeleme başarısız. Şirket sahibi misiniz? Ticker benzersiz mi? Ücret yeterli mi?";
			case "command.mceconomy.exchange.token_bought" ->
					args[0] + " adet " + args[1] + " coin satın alındı.";
			case "command.mceconomy.exchange.token_sold" ->
					args[0] + " adet " + args[1] + " coin satıldı.";
			case "command.mceconomy.exchange.list_hint" ->
					"Şirket listele: /borsa listele <sirket> <ticker>";
			case "command.mceconomy.exchange.coin_hint" ->
					"Coin oluştur: /borsa coin <sembol> <isim> <adet> <fiyatMg>";
			case "command.mceconomy.pbank.certified" ->
					"Bankacılık sertifikası alındı! Artık özel banka açabilirsiniz.";
			case "command.mceconomy.pbank.already_certified" ->
					"Zaten bankacılık sertifikanız var.";
			case "command.mceconomy.pbank.need_cert" ->
					"Önce bankacılık sertifikası almalısınız.";
			case "command.mceconomy.pbank.opened" ->
					"Özel banka açıldı: " + args[0];
			case "command.mceconomy.pbank.open_failed" ->
					"Banka açılamadı. İsim benzersiz olmalı ve sertifikanız olmalı.";
			case "command.mceconomy.pbank.open_hint" ->
					"Banka aç: /ozelbanka ac <isim>";
			case "command.mceconomy.pbank.deposited" ->
					gold(args[0]) + " → " + args[1] + " bankasına yatırıldı.";
			case "command.mceconomy.pbank.withdrawn" ->
					gold(args[0]) + " ← " + args[1] + " bankasından çekildi.";
			case "command.mceconomy.appeal.submitted" ->
					"İtirazınız operatörlere iletildi. /itiraz durum ile takip edin.";
			case "command.mceconomy.appeal.accepted" ->
					"İtiraz #" + args[0] + " kabul edildi. Hesap kısıtları kaldırıldı.";
			case "command.mceconomy.appeal.rejected" ->
					"İtiraz #" + args[0] + " reddedildi.";
			case "command.mceconomy.appeal.hint" ->
					"İtiraz: /itiraz ac <konu> <mesaj>  veya  /itiraz uyari <id> <konu> <mesaj>";
			case "command.mceconomy.dashboard.password_hint" ->
					"Dashboard şifresi: /panel sifre <sifre> (en az 4 karakter)";
			case "command.mceconomy.dashboard.password_set" ->
					"Dashboard şifreniz ayarlı.";
			case "command.mceconomy.dashboard.password_saved" ->
					"Dashboard şifreniz kaydedildi.";
			case "command.mceconomy.dashboard.password_short" ->
					"Şifre en az 4 karakter olmalı.";
			case "command.mceconomy.dashboard.password_save_failed" ->
					"Şifre kaydedilemedi. Sunucuyu yeniden başlatın veya OP ile iletişime geçin.";
			case "command.mceconomy.mbop.op_only" ->
					"Bu komutu yalnızca sunucu OP kullanabilir.";
			case "command.mceconomy.mbop.granted" ->
					args[0] + " Merkez Bankası yetkilisi yapıldı.";
			case "command.mceconomy.mbop.revoked" ->
					args[0] + " MB yetkisi alındı.";
			case "command.mceconomy.help.staff_only" ->
					"Bu bölüm yalnızca OP / MB yetkilileri içindir.";
			default -> key;
		};
	}

	private static String gold(Object amount) {
		return GoldStandard.formatMilligrams(((Number) amount).longValue());
	}
}
