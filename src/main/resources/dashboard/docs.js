const DOCS_HTML = `
<h2>📖 MC Economy Rehberi</h2>
<p class="hint">Bu panel ve oyun içi komutlarla işleyen, fiat + kısmi altın destekli bir ekonomi simülasyonudur. Para birimi <strong>$ (dolar)</strong>dır. Yeni oyuncular <strong>100.000 $</strong> ile başlar. MC değeri yalnızca altına değil; devlet güveni ve yatırımlara da bağlıdır (<code>/para durum</code>).</p>

<div class="doc-section">
  <h3>💰 Para Birimi</h3>
  <ul>
    <li><strong>$ (Dolar):</strong> Tüm bakiyeler, fiyatlar ve maaşlar dolar cinsindendir.</li>
    <li><strong>Altın Külçesi:</strong> Fiziksel değer saklama aracıdır. Bankaya külçe yatırıp $ bakiyesine çevirebilirsiniz.</li>
    <li><strong>Fiat gücü:</strong> Belediye bütçesi, enflasyon istikrarı, borsa ve şirket yatırımları paranın gücünü etkiler.</li>
  </ul>
</div>

<div class="doc-section">
  <h3>🏦 Bankacılık</h3>
  <ul>
    <li><strong>Cüzdan ↔ Banka:</strong> Cüzdanınızdaki $ bakiyesini bankaya yatırıp faiz kazanın, dilediğinizde çekin.</li>
    <li><strong>Vadesiz Hesap:</strong> Anında erişim, düşük faiz.</li>
    <li><strong>Vadeli Hesap:</strong> Daha yüksek faiz, vade boyunca kilitli.</li>
    <li><strong>Fiziksel Altın:</strong> "Külçe Yatır" ile envanterinizdeki altın külçelerini güncel kur üzerinden $'a çevirin. "Külçe Çek" ile geri alın.</li>
    <li><strong>Banka Transferi:</strong> Başka oyuncuya banka üzerinden $ gönderin.</li>
  </ul>
</div>

<div class="doc-section">
  <h3>🔐 Özel Banka & Sertifika</h3>
  <ul>
    <li><strong>Bankacılık Sertifikası:</strong> Kendi bankanızı açmak için gereklidir. Panelde gereken ücret ve mevcut bakiyeniz gösterilir. Yetersiz bakiyede ne kadar eksiğiniz olduğu yazar.</li>
    <li><strong>Özel Banka Aç:</strong> Sertifika aldıktan sonra isim verip bankanızı kurun; diğer oyuncular mevduat yatırabilir.</li>
  </ul>
</div>

<div class="doc-section">
  <h3>🌾 Market (Ticarete Giriş)</h3>
  <ul>
    <li><strong>Market Al:</strong> Buğday, maden vb. emtiaları $ ile satın alın.</li>
    <li><strong>Market Sat:</strong> Envanterinizdeki emtiaları satın (oyunda çevrimiçi olmalısınız). Elinizde yeterli ürün yoksa işlem iptal edilir ve <em>hiçbir ürün alınmaz</em>.</li>
    <li><strong>Fiyatlar:</strong> Arz-talep ve enflasyona göre dinamik olarak değişir. Grafikler sekmesinden takip edin.</li>
    <li><strong>Kaldıraç grafikleri:</strong> Açık LONG/SHORT pozisyonlarınızın altında canlı coin fiyatı ve giriş fiyatı (kesikli çizgi) görünür; Grafikler → Kaldıraçlı Pozisyon.</li>
  </ul>
</div>

<div class="doc-section">
  <h3>📉 Borsa & Coin (Borsaya Giriş)</h3>
  <ul>
    <li><strong>Coin Al/Sat:</strong> Borsada listelenen coinleri alıp satın. "Tüm Coinlerimi Sat" ile elinizdeki tüm coinleri tek tıkla satın.</li>
    <li><strong>Coin Oluştur:</strong> Kendi coininizi sembol, isim, arz ve başlangıç fiyatıyla çıkarın.</li>
    <li><strong>Şirketi Listele/Çıkar:</strong> Şirketinizi bir ticker ile borsaya açın veya borsadan çıkarın.</li>
    <li><strong>⚡ Kaldıraçlı İşlem (CFD):</strong> 2x-10x kaldıraçla LONG (yükselişe) veya SHORT (düşüşe) pozisyon açın. <span class="warn-text">Zarar teminatınızı aşarsa pozisyon otomatik likide edilir.</span> Yüksek risk!</li>
  </ul>
</div>

<div class="doc-section">
  <h3>🏢 Şirket, Hisse & Çalışanlar</h3>
  <ul>
    <li><strong>Şirket Kur:</strong> Yeterli servete sahipseniz şirket kurun.</li>
    <li><strong>Hisse Al/Sat:</strong> Şirket hisselerini alıp satın. "Tüm Hisselerimi Sat" ile tüm hisseleri toplu satın.</li>
    <li><strong>Çalışanlar:</strong> NPC'ler şirketinize iş başvurusu yapar. Çalışanlar sekmesinden başvuruları kabul/reddedin, çalışanlara zam verin, ikramiye ödeyin veya işten çıkarın. Maaş karşılığı av eti ve ürün teslim ederler.</li>
    <li><strong>Gizli sandık:</strong> Ham maden eritilip pazara satılır; madenlerin %2'si ve pişmiş yemekler çelik korumalı gizli sandığa konur. Panel: <em>Şirket &amp; Hisse → Gizli Şirket Sandığı</em> — <em>Sandığa Işınlan</em> (oyunda çevrimiçi). Komutlar: <code>/sirket depo</code>, <code>/sirket sandik &lt;isim&gt;</code>, <code>/sirket sandik cik</code>.</li>
  </ul>
</div>

<div class="doc-section">
  <h3>⛏ Meslek & Görev</h3>
  <ul>
    <li><strong>Meslek Seç:</strong> Bir meslek seçin; ilgili işlerde bonus kazanın. Meslek ekipmanı (kazma, olta vb.) geçici olarak verilir — adında <em>[Meslek]</em> yazar.</li>
    <li><strong>Görev:</strong> Görev tamamlanınca veya iptal/istifa edince geçici ekipman geri alınır. Ödünç eşyalar markette satılamaz.</li>
    <li><strong>İstifa:</strong> Meslekten istifa edebilirsiniz; aktif göreviniz de iptal olur.</li>
    <li><strong>Görevler:</strong> Görev alın, ilerletin, teslim edin (çevrimiçi olun) veya iptal edin.</li>
  </ul>
</div>

<div class="doc-section">
  <h3>🔒 Kasa & 🚨 Soygun</h3>
  <ul>
    <li><strong>Kişisel Kasa:</strong> Yer altında, bedrock ile çevrili, yalnızca size ait kilitli kasaya <code>/kasa</code> ile ışınlanın, <code>/kasa cik</code> ile geri dönün.</li>
    <li><strong>Gece soygunu:</strong> Muhafızlar uyur — otomatik ateş yok. Merkez bankasındaki <strong>depo sandıkları</strong> (piyasa, kara, altın) gece açılır; eşya alırsanız sabah şehir geneli üst arama riski vardır.</li>
    <li><strong>Altın rezerv:</strong> <code>/soygun baslat</code> ile RP protokolü (gece de hasarsız). Gündüz kasa içine girerseniz muhafızlar ateş edebilir.</li>
  </ul>
</div>

<div class="doc-section">
  <h3>☠ Kara Borsa & Para Aklama</h3>
  <ul>
    <li><strong>Kara Borsa:</strong> Yasadışı ürünleri kara para ile alıp satın. Riskli — MASAK sizi izliyor olabilir.</li>
    <li><strong>Para Aklama:</strong> Kara parayı temize çıkarmayı deneyin; başarısız olursa yakalanabilirsiniz.</li>
  </ul>
</div>

<div class="doc-section">
  <h3>🎰 Casino</h3>
  <ul>
    <li><strong>Yazı/Tura:</strong> Doğru tahminde 1.95x.</li>
    <li><strong>Zar:</strong> 1-6 arası sayıyı bilirsen 5.5x.</li>
    <li><strong>Slot:</strong> 2'li eşleşme 1.8x, 3'lü eşleşme 8x-25x.</li>
    <li class="warn-text">Uzun vadede ev kazanır; sorumlu oynayın.</li>
  </ul>
</div>

<div class="doc-section">
  <h3>⚖ İtirazlar & ⛓ MASAK</h3>
  <ul>
    <li>Hesabınız dondurulduysa veya kara listeye alındıysanız İtirazlar sekmesinden itiraz gönderin; OP'ler değerlendirir.</li>
    <li><strong>Adalet</strong> sekmesinden oyuncu şikayeti veya ihbar gönderebilirsiniz. Hapis cezası aldıysanız yasal ekonomi kapanır; süre bitince veya OP tahliye edince serbest kalırsınız.</li>
    <li>Oyun içi: <code>/sikayet</code>, <code>/ihbar</code>, <code>/hapishane durum</code></li>
  </ul>
</div>

<div class="doc-section">
  <h3>🖥 Panel ↔ Komut</h3>
  <p class="hint">8 GB sunucu: <code>maxPropertiesPerPlayer=1</code>, <code>maxVehiclesPerPlayer=1</code>, yapı kuyruğu tick başına 400 blok.</p>
  <ul>
    <li><code>/ev al|tp|sat|liste</code> — Gayrimenkul (panel: Yatırım)</li>
    <li><code>/araba al|cikar|liste</code> — Garaj (client+server mod, minecart yok)</li>
    <li><code>/ekonomi bakan …</code> — Devlet / Bakanlık paneli</li>
    <li><code>/sigorta</code>, <code>/takas</code>, <code>/lonca</code>, <code>/belediye</code> — Finans / Sosyal / Devlet sekmeleri</li>
    <li><code>/ekonomi sifirla</code> — Yalnızca OP; panel: Araçlar → Tam sıfırlama (SIFIRLA)</li>
  </ul>
</div>
`;
