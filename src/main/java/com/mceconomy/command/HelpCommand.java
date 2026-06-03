package com.mceconomy.command;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.util.Permissions;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.job.JobType;
import com.mceconomy.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class HelpCommand {
	private HelpCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("yardim")
				.executes(ctx -> show(ctx.getSource(), "genel"))
				.then(argument("konu", StringArgumentType.word())
						.executes(ctx -> show(ctx.getSource(), StringArgumentType.getString(ctx, "konu")))));

		dispatcher.register(literal("help")
				.executes(ctx -> show(ctx.getSource(), "genel"))
				.then(argument("konu", StringArgumentType.word())
						.executes(ctx -> show(ctx.getSource(), StringArgumentType.getString(ctx, "konu")))));
	}

	private static int show(CommandSourceStack source, String topic) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		String key = topic.toLowerCase();
		List<Component> lines = switch (key) {
			case "banka" -> bankHelp();
			case "market" -> marketHelp();
			case "borsa" -> exchangeHelp();
			case "masak", "karaborsa" -> masakHelp();
			case "meslek", "gorev" -> jobHelp();
			case "sirket", "hisse", "is" -> companyHelp();
			case "ozelbanka", "panel", "dashboard" -> extraHelp();
			case "mbop", "itiraz", "admin" -> staffHelp(source);
			default -> generalHelp();
		};
		source.sendSuccess(() -> Component.literal("§6=== MC Economy /yardim " + key + " ==="), false);
		for (Component line : lines) {
			source.sendSuccess(() -> line, false);
		}
		source.sendSuccess(() -> Component.literal(
				"§7Konular: genel, banka, market, borsa, masak, meslek, sirket, panel, admin"), false);
		return 1;
	}

	private static List<Component> generalHelp() {
		return List.of(
				line("§e/bakiye [oyuncu] §7— Cüzdan, banka ve toplam servet"),
				line("§e/ode <oyuncu> <miktar> §7— Oyuncuya altın gönder (mg birimi)"),
				line("§e/banka §7— Merkez Bankası GUI (hesap, külçe, market)"),
				line("§e/market fiyat §7— Canlı emtia fiyatları"),
				line("§e/kredi al|ode|durum §7— Banka kredisi"),
				line("§e/panel sifre <sifre> §7— Web dashboard şifresi"),
				line("§e/panel §7— Dashboard adresi: http://" + EconomyConfig.webBindAddress()
						+ ":" + EconomyConfig.webPort() + "/"),
				line("§eAltın standardı: §7" + GoldStandard.formatWheatExchange())
		);
	}

	private static List<Component> bankHelp() {
		return List.of(
				line("§e/banka §7— Ana menü (NPC ile de açılır)"),
				line("§e/banka ac §7— Vadesiz hesap aç"),
				line("§e/banka yatir <mg> §7— Cüzdandan bankaya"),
				line("§e/banka cek <mg> §7— Bankadan cüzdana"),
				line("§e/banka transfer <oyuncu> <mg> §7— Banka transferi"),
				line("§e/banka vadeli §7— Vadeli hesap"),
				line("§7GUI: külçe yatır/çek, ürün sat, mal al, yeraltı menüsü"),
				line("§5Gece: §7muhafizlar uyur — §edepo sandiklari §7acik, ates yok"),
				line("§e/soygun baslat §7— Altin rezerv soygunu (gece hasarsiz RP)"),
				line("§e/bulten §7— Resmi ekonomi bulteni (soygun, depo, makro)"),
				line("§7Sabah depoda eksiklik varsa sehir geneli ust arama")
		);
	}

	private static List<Component> marketHelp() {
		return List.of(
				line("§e/market fiyat §7— Tüm emtia fiyatları"),
				line("§e/market al <emtia> <adet> §7— Mal al (cüzdan)"),
				line("§e/market sat <emtia> <adet> §7— Envanterden sat"),
				line("§7Mesleğinize uygun emtia satışında %" + (int) ((EconomyConfig.jobBonusMultiplier() - 1) * 100)
						+ " bonus"),
				line("§7Altın külçesi marketten alınamaz; ürün satarak kazanılır")
		);
	}

	private static List<Component> exchangeHelp() {
		return List.of(
				line("§e/borsa §7— Borsa GUI (hisse + coin)"),
				line("§e/borsa coin <sembol> <isim> <adet> <fiyatMg> §7— Token oluştur"),
				line("§e/borsa listele <sirket> <ticker> §7— Şirketi borsaya çıkar"),
				line("§e/hisse al|sat <sirket> <adet> §7— Hisse işlemleri"),
				line("§e/sirket kur <isim> §7— Şirket kur")
		);
	}

	private static List<Component> masakHelp() {
		return List.of(
				line("§e/masak §7— Kara para bakiyesi ve hesap durumu"),
				line("§e/karaborsa §7— Yeraltı menüsü (kaçak mal, aklama)"),
				line("§e/itiraz ac <konu> <mesaj> §7— MASAK kararına itiraz"),
				line("§e/itiraz durum §7— İtirazlarınızı görün"),
				line("§e/sikayet <oyuncu> <konu> <mesaj> §7— Oyuncu şikayeti"),
				line("§e/ihbar <kategori> <mesaj> [hedef] §7— Anonim ihbar"),
				line("§e/ihbar oyuncu <ad> <kategori> <mesaj> §7— Supheli ihbari"),
				line("§7Banka calintisi kisisel kasada + acik ihbar → borc ve el koyma"),
				line("§7Ihbar yoksa karaborsa/kasa calintisina dokunulmaz"),
				line("§e/hapishane durum §7— Hapis cezanız"),
				line("§7Donmuş hesap: yasal banka kapalı, itiraz açılabilir")
		);
	}

	private static List<Component> jobHelp() {
		StringBuilder jobs = new StringBuilder("§7Meslekler: ");
		for (JobType type : JobType.values()) {
			jobs.append(type.id()).append(" ");
		}
		return List.of(
				line("§e/meslek sec <meslek> §7— Meslek seç (geçici ekipman verilir)"),
				line("§e/gorev al §7— Görev al (ekipman yenilenir)"),
				line("§e/gorev teslim §7— Görev bitir (ekipman geri alınır)"),
				line(jobs.toString()),
				line("§e/gorev al §7— Mesleğe özel görev al"),
				line("§e/gorev durum §7— Aktif görevi gör"),
				line("§e/gorev teslim §7— Görevi teslim et / tamamla"),
				line("§7Madenci/çiftçi/balıkçı vb. kendi emtiasında satış bonusu alır")
		);
	}

	private static List<Component> companyHelp() {
		return List.of(
				line("§e/sirket kur <isim> §7— Şirket kur"),
				line("§e/sirket basvurular §7— NPC ve oyuncu iş başvuruları"),
				line("§e/sirket kabul|red <id> §7— Başvuru kabul/red"),
				line("§e/sirket calisanlar §7— Çalışan listesi"),
				line("§e/is sirketler §7— Başvurulabilecek şirketler"),
				line("§e/is basvur <sirket> <rol> <maas> §7— Şirkete iş başvurusu"),
				line("§e/is durum §7— İş durumu ve sonraki maaş"),
				line("§e/is ayril §7— Şirketten ayrıl"),
				line("§e/sirket kasa <isim> §7— Kasa ve hisse fiyatı"),
				line("§e/sirket depo <isim> §7— Gizli sandık içeriği (maden %2 + pişmiş yemek)"),
				line("§e/sirket sandik <isim> §7— Gizli çelik odaya ışınlan"),
				line("§e/sirket sandik cik §7— Sandıktan geri dön"),
				line("§e/hisse al|sat <sirket> <adet> §7— Hisse al/sat"),
				line("§e/borsa listele <sirket> <ticker> §7— Borsaya listele")
		);
	}

	private static List<Component> extraHelp() {
		return List.of(
				line("§e/ozelbanka §7— Özel banka menüsü"),
				line("§e/ozelbanka sertifika §7— Bankacılık sertifikası al"),
				line("§e/ozelbanka ac <isim> §7— Özel banka kur"),
				line("§e/panel sifre <sifre> §7— Dashboard giriş şifresi (min 4 karakter)"),
				line("§7Dashboard: portföy, MASAK durumu, borsa grafikleri")
		);
	}

	private static List<Component> staffHelp(CommandSourceStack source) {
		if (!Permissions.isMbStaff(source) && !Permissions.isServerOp(source)) {
			return List.of(Messages.tr("command.mceconomy.help.staff_only"));
		}
		boolean op = Permissions.isServerOp(source);
		return List.of(
				line(op ? "§c[OP] /mbop ver|al|liste <oyuncu> §7— MB yetkilisi" : "§e[MB] MASAK/itiraz yetkileriniz aktif"),
				line("§c[OP/MB] /masak liste|coz|ceza|karaliste"),
				line("§c[OP/MB] /itiraz liste|kabul|red"),
				line("§c[OP] /adalet raporlar §7— Şikayet ve ihbarlar"),
				line("§c[OP] /hapishane yatir|serbest|liste <oyuncu>"),
				line("§c[OP/MB] /merkezbanka rapor"),
				line("§c[OP] /merkezbanka kur §7— Spawn banka binası"),
				line("§c[OP] /merkezbanka muhafiz-temizle §7— Fazla muhafiz NPC sil"),
				line("§c[OP] /ekonomi olay <tip> §7— Ekonomi olayı"),
				line("§7Dashboard /admin — denetim paneli (staff girişi)")
		);
	}

	private static Component line(String text) {
		return Component.literal(text);
	}
}
