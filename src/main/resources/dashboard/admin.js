let catalog = null;
let playersCache = [];
let playerAdmin = null;
let economyCatalog = null;
let activePlayerTab = 'money';
let activeGlobalTab = 'macro';

function show(id) {
  document.getElementById('loginView').classList.toggle('hidden', id !== 'login');
  document.getElementById('adminView').classList.toggle('hidden', id !== 'admin');
}

async function resolveAppeal(id, accept) {
  const noteInput = document.getElementById('note-' + id);
  const note = noteInput ? noteInput.value.trim() : '';
  const path = accept ? '/admin/appeals/accept' : '/admin/appeals/reject';
  const data = await api(path, { method: 'POST', body: JSON.stringify({ id, note: note || undefined }) });
  if (data.success) {
    showToast(`İtiraz #${id} ${accept ? 'kabul edildi' : 'reddedildi'}`, true);
    loadAdmin();
  } else {
    showToast(data.message || 'İşlem başarısız', false);
  }
}

function formatRemaining(ms) {
  const min = Math.floor(ms / 60000);
  const sec = Math.floor((ms % 60000) / 1000);
  return min + ' dk ' + sec + ' sn';
}

async function justiceAction(action, id, extra = {}) {
  const data = await api('/admin/actions/justice/' + action, {
    method: 'POST',
    body: JSON.stringify({ id, ...extra })
  });
  if (data.success) {
    showToast(data.message || 'Tamam', true);
    loadAdmin();
  } else {
    showToast(data.message || 'İşlem başarısız', false);
  }
}

function renderJusticeReports(reports) {
  if (!reports.length) return '<p class="hint">Açık şikayet veya ihbar yok.</p>';
  return reports.map(r => `
    <div class="appeal-card">
      <div class="appeal-header">
        <strong>#${r.id}</strong>
        <span class="badge badge-warn">${r.typeLabel || r.type}</span>
        <span class="badge badge-muted">${r.status}</span>
      </div>
      <div class="appeal-subject">${r.subject || r.category} — ${r.reporterName} → ${r.targetName || '?'}</div>
      <div class="appeal-message">${r.message}</div>
      <textarea id="jnote-${r.id}" placeholder="Karar notu"></textarea>
      <label>Hapis (dakika, 0 = yok)</label>
      <input type="number" id="jmin-${r.id}" min="0" value="0" style="margin-bottom:8px">
      <div class="appeal-actions" style="grid-template-columns:1fr 1fr 1fr">
        <button class="btn btn-ghost" onclick="justiceAction('investigate', ${r.id})">Soruştur</button>
        <button class="btn btn-danger" onclick="justiceAction('dismiss', ${r.id}, { note: document.getElementById('jnote-${r.id}').value })">Reddet</button>
        <button class="btn btn-success" onclick="justiceAction('guilty', ${r.id}, { note: document.getElementById('jnote-${r.id}').value, prisonMinutes: +document.getElementById('jmin-${r.id}').value })">Suçlu + Hapis</button>
      </div>
    </div>`).join('');
}

function renderPrisoners(prisoners) {
  if (!prisoners.length) return '<p class="hint">Hapiste kimse yok.</p>';
  return prisoners.map(p => `
    <div class="list-item">
      <strong>${p.playerName}</strong> — ${p.reason}
      <span class="badge badge-warn" style="float:right">${formatRemaining(p.remainingMs)}</span>
    </div>`).join('');
}

function renderAppeals(appeals) {
  if (!appeals.length) return '<p class="hint">İtiraz yok.</p>';
  return appeals.map(a => `
    <div class="appeal-card">
      <div class="appeal-header">
        <strong>#${a.id}</strong> — ${a.playerName}
        ${a.relatedAlertId ? `<span class="badge badge-warn">Uyarı #${a.relatedAlertId}</span>` : ''}
      </div>
      <div class="appeal-subject">${a.subject}</div>
      <div class="appeal-message">${a.message}</div>
      <textarea id="note-${a.id}" placeholder="Operatör notu"></textarea>
      <div class="appeal-actions">
        <button class="btn btn-success" onclick="resolveAppeal(${a.id}, true)">Kabul Et</button>
        <button class="btn btn-danger" onclick="resolveAppeal(${a.id}, false)">Reddet</button>
      </div>
    </div>`).join('');
}

function renderAlerts(alerts, withActions) {
  if (!alerts.length) return '<p class="hint">Uyarı yok.</p>';
  return alerts.map(a => `
    <div class="list-item">
      <strong>#${a.id}</strong> ${a.playerName || (a.playerUuid ? a.playerUuid.slice(0, 8) : '?')}
      — ${a.reason} | risk: ${a.riskScore} | ${formatMg(a.amountMg)}
      ${withActions ? `<button class="btn btn-sm btn-success" style="float:right" onclick="quickResolve('${a.playerName || ''}')">Çöz</button>` : ''}
    </div>`).join('');
}

function quickResolve(name) {
  if (name) document.getElementById('masakResolvePlayer').value = name;
  document.querySelector('[data-page="masak"]').click();
}

async function loadPlayers() {
  const data = await api('/admin/players');
  if (data.error) return;
  playersCache = data.players || [];
  renderPlayersTable();
}

function renderPlayersTable() {
  const q = (document.getElementById('playerSearch')?.value || '').trim().toLowerCase();
  const list = q ? playersCache.filter(p => p.name.toLowerCase().includes(q)) : playersCache;
  document.getElementById('playersTable').innerHTML = list.map(p => {
    const status = p.blacklisted ? '<span class="badge badge-bad">Kara Liste</span>'
      : p.frozen ? '<span class="badge badge-warn">Dondurulmuş</span>'
      : '<span class="badge badge-ok">Normal</span>';
    const dot = p.online ? 'on' : 'off';
    const safeName = p.name.replace(/'/g, "\\'");
    return `<tr>
      <td><span class="online-dot ${dot}"></span>${p.name}${p.mbOfficial ? ' 🎖' : ''}</td>
      <td>${formatMg(p.walletMg)}</td>
      <td>${formatMg(p.bankMg)}</td>
      <td>${formatMg(p.dirtyMg)}</td>
      <td>${p.creditScore}</td>
      <td>${status}</td>
      <td>
        <button class="btn btn-sm btn-gold" onclick="openPlayerAdmin('${safeName}')">Yönet</button>
        <button class="btn btn-sm btn-ghost" onclick="fillMasak('${safeName}')">MASAK</button>
      </td>
    </tr>`;
  }).join('');
}

function playerBody(extra = {}) {
  return { player: playerAdmin.name, uuid: playerAdmin.uuid, ...extra };
}

async function reloadPlayerAdmin() {
  if (!playerAdmin?.name) return;
  const data = await api('/admin/player?name=' + encodeURIComponent(playerAdmin.name));
  if (data.error) return;
  playerAdmin = data;
  renderPlayerAdminModal();
  await loadPlayers();
}

async function openPlayerAdmin(name) {
  if (!economyCatalog) economyCatalog = await api('/admin/economy/catalog');
  const data = await api('/admin/player?name=' + encodeURIComponent(name));
  if (data.error) {
    showToast(data.message || 'Oyuncu yüklenemedi', false);
    return;
  }
  playerAdmin = data;
  activePlayerTab = 'money';
  document.getElementById('playerAdminModal').classList.remove('hidden');
  document.getElementById('playerAdminTitle').textContent = 'Oyuncu: ' + data.name;
  document.querySelectorAll('#playerTabBar .tab-btn').forEach(b => {
    b.classList.toggle('active', b.dataset.playerTab === activePlayerTab);
  });
  renderPlayerAdminModal();
}

function closePlayerAdmin() {
  document.getElementById('playerAdminModal').classList.add('hidden');
  playerAdmin = null;
}

function mcInput(id, label, valueMc) {
  const mc = valueMc != null ? (valueMc / 1000).toFixed(2) : '';
  return `<div><label>${label}</label><input id="${id}" type="number" step="0.01" value="${mc}"></div>`;
}

function renderPlayerAdminModal() {
  const p = playerAdmin;
  if (!p) return;
  const el = document.getElementById('playerTabContent');
  if (activePlayerTab === 'money') {
    el.innerHTML = `
      <div class="stat-grid" style="margin-bottom:16px">
        <div class="stat-card"><div class="label">Cüzdan</div><div class="value">${formatMg(p.walletMg)}</div></div>
        <div class="stat-card"><div class="label">Vadesiz</div><div class="value">${formatMg(p.bankMg)}</div></div>
        <div class="stat-card"><div class="label">Vadeli</div><div class="value">${formatMg(p.termBalanceMg || 0)}</div></div>
        <div class="stat-card warn"><div class="label">Kara</div><div class="value">${formatMg(p.dirtyMg)}</div></div>
      </div>
      <h4>Cüzdan</h4>
      <div class="admin-form-grid">${mcInput('paWalletSet', 'Mutlak ($)', p.walletMg)}${mcInput('paWalletAdj', 'Delta (+/- MC)', 0)}</div>
      <div class="inline-actions">
        <button class="btn btn-gold btn-sm" id="paWalletSetBtn">Cüzdan Ayarla</button>
        <button class="btn btn-ghost btn-sm" id="paWalletAdjBtn">Delta Uygula</button>
      </div>
      <h4 style="margin-top:20px">Banka — Vadesiz ${p.hasChecking ? '' : '(hesap yok)'}</h4>
      <div class="admin-form-grid">${mcInput('paBankChecking', 'Bakiye ($)', p.bankMg)}</div>
      <div class="inline-actions">
        <button class="btn btn-gold btn-sm" id="paBankCheckingBtn">Vadesiz Kaydet</button>
        ${p.hasChecking ? '<button class="btn btn-danger btn-sm" id="paBankCheckingDelBtn">Vadesiz Sil</button>'
          : '<button class="btn btn-success btn-sm" id="paBankOpenCheckingBtn">Vadesiz Aç</button>'}
      </div>
      <h4 style="margin-top:20px">Banka — Vadeli ${p.hasTerm ? '' : '(hesap yok)'}</h4>
      <div class="admin-form-grid">${mcInput('paBankTerm', 'Bakiye ($)', p.termBalanceMg || 0)}</div>
      <div class="inline-actions">
        <button class="btn btn-gold btn-sm" id="paBankTermBtn">Vadeli Kaydet</button>
        ${p.hasTerm ? '<button class="btn btn-danger btn-sm" id="paBankTermDelBtn">Vadeli Sil</button>'
          : '<button class="btn btn-success btn-sm" id="paBankOpenTermBtn">Vadeli Aç</button>'}
      </div>
      <h4 style="margin-top:20px">Kara Para</h4>
      <div class="admin-form-grid">${mcInput('paDirtySet', 'Bakiye ($)', p.dirtyMg)}</div>
      <button class="btn btn-gold btn-sm" id="paDirtySetBtn" style="margin-top:12px">Kara Para Kaydet</button>`;
    bindMoneyTab();
  } else if (activePlayerTab === 'profile') {
    const jobs = (catalog?.jobs || []).map(j =>
      `<option value="${j.id}"${p.jobId === j.id ? ' selected' : ''}>${j.name}</option>`).join('');
    el.innerHTML = `
      <div class="admin-form-grid">
        <div><label>Kredi Skoru</label><input id="paCredit" type="number" min="0" max="850" value="${p.creditScore}"></div>
        <div><label>Meslek</label><select id="paJob"><option value="">Yok</option>${jobs}</select></div>
        <div><label><input type="checkbox" id="paFrozen" ${p.accountFrozen ? 'checked' : ''}> Hesap Dondurulmuş</label></div>
        <div><label><input type="checkbox" id="paBlacklisted" ${p.blacklisted ? 'checked' : ''}> Kara Liste</label></div>
        <div><label><input type="checkbox" id="paCertified" ${p.bankCertified ? 'checked' : ''}> Bankacılık Sertifikası</label></div>
      </div>
      <button class="btn btn-gold" id="paProfileSaveBtn" style="margin-top:12px">Profili Kaydet</button>`;
    document.getElementById('paProfileSaveBtn').onclick = () => doAction('/admin/actions/player/profile/update', playerBody({
      creditScore: +document.getElementById('paCredit').value,
      jobId: document.getElementById('paJob').value,
      accountFrozen: document.getElementById('paFrozen').checked,
      blacklisted: document.getElementById('paBlacklisted').checked,
      bankCertified: document.getElementById('paCertified').checked
    }), reloadPlayerAdmin);
  } else if (activePlayerTab === 'portfolio') {
    const companies = economyCatalog?.companies || [];
    const tokens = economyCatalog?.tokens || [];
    const shareRows = (p.allShares || p.shares || []).map(s => `
      <tr><td>${s.ticker}</td><td>${s.name || s.ticker}</td><td>${s.amount}</td>
      <td><input type="number" min="0" value="${s.amount}" data-share-ticker="${s.ticker}" style="width:80px"></td>
      <td><button class="btn btn-sm btn-gold" data-share-save="${s.ticker}">Kaydet</button>
      <button class="btn btn-sm btn-danger" data-share-del="${s.ticker}">Sil</button></td></tr>`).join('');
    const tokenRows = (p.allTokens || p.tokens || []).map(t => `
      <tr><td>${t.symbol}</td><td>${t.displayName || t.symbol}</td><td>${t.amount}</td>
      <td><input type="number" min="0" value="${t.amount}" data-token-symbol="${t.symbol}" style="width:80px"></td>
      <td><button class="btn btn-sm btn-gold" data-token-save="${t.symbol}">Kaydet</button>
      <button class="btn btn-sm btn-danger" data-token-del="${t.symbol}">Sil</button></td></tr>`).join('');
    el.innerHTML = `
      <h4>Hisseler</h4>
      <table class="data-table"><thead><tr><th>Ticker</th><th>Şirket</th><th>Mevcut</th><th>Yeni</th><th></th></tr></thead>
      <tbody>${shareRows || '<tr><td colspan="5" class="hint">Hisse yok</td></tr>'}</tbody></table>
      <div class="form-row" style="margin-top:12px">
        <select id="paNewShareTicker">${companies.map(c => `<option value="${c.ticker || c.name}">${c.name}</option>`).join('')}</select>
        <input id="paNewShareAmt" type="number" min="1" value="1" style="width:100px">
        <button class="btn btn-gold btn-sm" id="paNewShareBtn">Hisse Ekle</button>
      </div>
      <h4 style="margin-top:24px">Coinler</h4>
      <table class="data-table"><thead><tr><th>Sembol</th><th>Ad</th><th>Mevcut</th><th>Yeni</th><th></th></tr></thead>
      <tbody>${tokenRows || '<tr><td colspan="5" class="hint">Coin yok</td></tr>'}</tbody></table>
      <div class="form-row" style="margin-top:12px">
        <select id="paNewTokenSymbol">${tokens.map(t => `<option value="${t.symbol}">${t.symbol}</option>`).join('')}</select>
        <input id="paNewTokenAmt" type="number" min="1" value="1" style="width:100px">
        <button class="btn btn-gold btn-sm" id="paNewTokenBtn">Coin Ekle</button>
      </div>`;
    el.querySelectorAll('[data-share-save]').forEach(btn => btn.onclick = () => {
      const ticker = btn.dataset.shareSave;
      const inp = el.querySelector(`[data-share-ticker="${ticker}"]`);
      doAction('/admin/actions/player/shares/set', playerBody({ ticker, amount: +inp.value }), reloadPlayerAdmin);
    });
    el.querySelectorAll('[data-share-del]').forEach(btn => btn.onclick = () =>
      doAction('/admin/actions/player/shares/set', playerBody({ ticker: btn.dataset.shareDel, amount: 0 }), reloadPlayerAdmin));
    el.querySelectorAll('[data-token-save]').forEach(btn => btn.onclick = () => {
      const sym = btn.dataset.tokenSave;
      const inp = el.querySelector(`[data-token-symbol="${sym}"]`);
      doAction('/admin/actions/player/tokens/set', playerBody({ symbol: sym, amount: +inp.value }), reloadPlayerAdmin);
    });
    el.querySelectorAll('[data-token-del]').forEach(btn => btn.onclick = () =>
      doAction('/admin/actions/player/tokens/set', playerBody({ symbol: btn.dataset.tokenDel, amount: 0 }), reloadPlayerAdmin));
    document.getElementById('paNewShareBtn')?.addEventListener('click', () =>
      doAction('/admin/actions/player/shares/set', playerBody({
        ticker: document.getElementById('paNewShareTicker').value,
        amount: +document.getElementById('paNewShareAmt').value
      }), reloadPlayerAdmin));
    document.getElementById('paNewTokenBtn')?.addEventListener('click', () =>
      doAction('/admin/actions/player/tokens/set', playerBody({
        symbol: document.getElementById('paNewTokenSymbol').value,
        amount: +document.getElementById('paNewTokenAmt').value
      }), reloadPlayerAdmin));
  } else if (activePlayerTab === 'loan') {
    const loan = p.loan;
    el.innerHTML = loan ? `
      <p>Kalan: <strong>${loan.remaining || formatMg(loan.remainingMg)}</strong> — Taksit: ${loan.installment || formatMg(loan.installmentMg)}</p>
      <div class="admin-form-grid">
        ${mcInput('paLoanRemaining', 'Kalan ($)', loan.remainingMg)}
        ${mcInput('paLoanInstallment', 'Taksit ($)', loan.installmentMg)}
        <div><label>Vade (ms epoch)</label><input id="paLoanDue" type="number" value="${loan.dueAt || Date.now() + 86400000}"></div>
      </div>
      <div class="inline-actions">
        <button class="btn btn-gold btn-sm" id="paLoanSaveBtn">Kredi Güncelle</button>
        <button class="btn btn-danger btn-sm" id="paLoanDelBtn">Kredi Sil</button>
      </div>` : `
      <p class="hint">Aktif kredi yok.</p>
      <div class="admin-form-grid">
        ${mcInput('paLoanRemaining', 'Anapara/Kalan ($)', 0)}
        ${mcInput('paLoanInstallment', 'Taksit ($)', 0)}
        <div><label>Vade (ms epoch)</label><input id="paLoanDue" type="number" value="${Date.now() + 86400000}"></div>
      </div>
      <button class="btn btn-gold btn-sm" id="paLoanSaveBtn">Kredi Oluştur</button>`;
    document.getElementById('paLoanSaveBtn').onclick = () => doAction('/admin/actions/player/loan/upsert', playerBody({
      mc: +document.getElementById('paLoanRemaining').value,
      installmentMc: +document.getElementById('paLoanInstallment').value,
      dueAt: +document.getElementById('paLoanDue').value
    }), reloadPlayerAdmin);
    document.getElementById('paLoanDelBtn')?.addEventListener('click', () =>
      doAction('/admin/actions/player/loan/delete', playerBody(), reloadPlayerAdmin));
  } else if (activePlayerTab === 'extra') {
    const lev = (p.leveragePositions || []).map(pos => `
      <div class="list-item">${pos.symbol} ${pos.side} ${pos.leverage}x — teminat ${pos.margin}
        <button class="btn btn-sm btn-danger" style="float:right" data-lev-close="${pos.id}">Kapat</button></div>`).join('');
    const priv = (p.allPrivateDeposits || p.privateDeposits || []).map(d => `
      <div class="list-item"><strong>${d.bank}</strong> — ${d.balance || formatMg(d.balanceMg)}
        <input type="number" step="0.01" data-priv-bank="${d.bank}" value="${(d.balanceMg / 1000).toFixed(2)}" style="width:100px;margin-left:8px">
        <button class="btn btn-sm btn-gold" data-priv-save="${d.bank}">Kaydet</button></div>`).join('');
    const banks = economyCatalog?.privateBanks || [];
    el.innerHTML = `
      <h4>Kaldıraç Pozisyonları</h4>
      ${lev || '<p class="hint">Açık pozisyon yok.</p>'}
      <h4 style="margin-top:20px">Özel Banka Mevduatları</h4>
      ${priv || '<p class="hint">Mevduat yok.</p>'}
      <div class="form-row" style="margin-top:12px">
        <select id="paNewPrivBank">${banks.map(b => `<option value="${b.name}">${b.name}</option>`).join('')}</select>
        <input id="paNewPrivAmt" type="number" step="0.01" min="0" value="0" style="width:100px">
        <button class="btn btn-gold btn-sm" id="paNewPrivBtn">Mevduat Ayarla</button>
      </div>`;
    el.querySelectorAll('[data-lev-close]').forEach(btn => btn.onclick = () =>
      doAction('/admin/actions/player/leverage/close', { positionId: +btn.dataset.levClose }, reloadPlayerAdmin));
    el.querySelectorAll('[data-priv-save]').forEach(btn => btn.onclick = () => {
      const bank = btn.dataset.privSave;
      const val = el.querySelector(`[data-priv-bank="${bank}"]`).value;
      doAction('/admin/actions/player/private-deposit/set', playerBody({ bankName: bank, mc: +val }), reloadPlayerAdmin);
    });
    document.getElementById('paNewPrivBtn')?.addEventListener('click', () =>
      doAction('/admin/actions/player/private-deposit/set', playerBody({
        bankName: document.getElementById('paNewPrivBank').value,
        mc: +document.getElementById('paNewPrivAmt').value
      }), reloadPlayerAdmin));
  }
}

function bindMoneyTab() {
  const mc = id => +document.getElementById(id).value;
  document.getElementById('paWalletSetBtn').onclick = () =>
    doAction('/admin/actions/player/wallet/set', playerBody({ mc: mc('paWalletSet') }), reloadPlayerAdmin);
  document.getElementById('paWalletAdjBtn').onclick = () =>
    doAction('/admin/actions/player/wallet/adjust', playerBody({ mc: mc('paWalletAdj') }), reloadPlayerAdmin);
  document.getElementById('paBankCheckingBtn').onclick = () =>
    doAction('/admin/actions/player/bank/set', playerBody({ type: 'checking', mc: mc('paBankChecking') }), reloadPlayerAdmin);
  document.getElementById('paBankTermBtn')?.addEventListener('click', () =>
    doAction('/admin/actions/player/bank/set', playerBody({ type: 'term', mc: mc('paBankTerm') }), reloadPlayerAdmin));
  document.getElementById('paBankCheckingDelBtn')?.addEventListener('click', () =>
    doAction('/admin/actions/player/bank/delete', playerBody({ type: 'checking' }), reloadPlayerAdmin));
  document.getElementById('paBankTermDelBtn')?.addEventListener('click', () =>
    doAction('/admin/actions/player/bank/delete', playerBody({ type: 'term' }), reloadPlayerAdmin));
  document.getElementById('paBankOpenCheckingBtn')?.addEventListener('click', () =>
    doAction('/admin/actions/player/bank/open-checking', playerBody(), reloadPlayerAdmin));
  document.getElementById('paBankOpenTermBtn')?.addEventListener('click', () =>
    doAction('/admin/actions/player/bank/open-term', playerBody(), reloadPlayerAdmin));
  document.getElementById('paDirtySetBtn').onclick = () =>
    doAction('/admin/actions/player/dirty/set', playerBody({ mc: mc('paDirtySet') }), reloadPlayerAdmin);
}

async function loadEconomyAdmin() {
  economyCatalog = await api('/admin/economy/catalog');
  if (economyCatalog.error) return;
  renderGlobalEconomy();
}

function renderGlobalEconomy() {
  const cb = economyCatalog.centralBank || {};
  document.getElementById('macroForm').innerHTML = `
    <div><label>Faiz Oranı</label><input id="gBaseRate" type="number" step="0.001" value="${cb.baseRate ?? 0}"></div>
    <div><label>Enflasyon</label><input id="gInflation" type="number" step="0.001" value="${cb.inflationRate ?? 0}"></div>
    <div><label>Ekonomi Endeksi</label><input id="gEconIndex" type="number" step="0.01" value="${cb.economyIndex ?? 0}"></div>
    <div><label>Altın Faktörü</label><input id="gGoldFactor" type="number" step="0.01" value="${cb.goldFactor ?? 1}"></div>
    <div><label>Para Arzı</label><input id="gMoneySupply" type="number" value="${cb.moneySupply ?? 0}"></div>
    <div><label>Belediye Bütçesi ($)</label><input id="gMunicipal" type="number" step="0.01" value="${cb.municipalBudgetMc ?? 0}"></div>
    <p class="hint">Fiat gücü: <strong>${(cb.fiatStrength ?? 1).toFixed(2)}</strong> · Altın destek %${cb.goldBackingPct ?? '—'} · Devlet %${cb.stateCredibilityPct ?? '—'} · Yatırım %${cb.investmentPct ?? '—'}</p>`;

  const companies = economyCatalog.companies || [];
  document.getElementById('globalCompaniesTable').innerHTML = `
    <table class="data-table"><thead><tr><th>Ad</th><th>Ticker</th><th>Hazine</th><th>Listeli</th><th>İşlem</th></tr></thead><tbody>
    ${companies.map(c => `<tr>
      <td>${c.name}</td>
      <td><input value="${c.ticker || ''}" data-co-ticker="${c.name}" style="width:70px"></td>
      <td><input type="number" step="0.01" value="${(c.treasuryMg / 1000).toFixed(2)}" data-co-treas="${c.name}" style="width:100px"></td>
      <td>${c.listed ? 'Evet' : 'Hayır'}</td>
      <td>
        <button class="btn btn-sm btn-gold" data-co-save="${c.name}">Kaydet</button>
        ${c.listed ? `<button class="btn btn-sm btn-ghost" data-co-delist="${c.name}">Delist</button>` : ''}
      </td></tr>`).join('')}</tbody></table>`;

  document.getElementById('newCompanyForm').innerHTML = `
    <div><label>Ad</label><input id="gCoName"></div>
    <div><label>Sahip</label><input id="gCoOwner" placeholder="Oyuncu adı"></div>
    <div><label>Ticker</label><input id="gCoTicker"></div>
    <div><label>Hazine ($)</label><input id="gCoTreasury" type="number" value="0"></div>
    <div><label><input type="checkbox" id="gCoListed"> Borsada listeli</label></div>`;

  document.getElementById('globalCompaniesTable').querySelectorAll('[data-co-save]').forEach(btn => {
    btn.onclick = () => {
      const name = btn.dataset.coSave;
      doAction('/admin/actions/economy/company/update', {
        name,
        ticker: document.querySelector(`[data-co-ticker="${name}"]`).value,
        treasuryMc: +document.querySelector(`[data-co-treas="${name}"]`).value
      }, loadEconomyAdmin);
    };
  });
  document.getElementById('globalCompaniesTable').querySelectorAll('[data-co-delist]').forEach(btn =>
    btn.onclick = () => doAction('/admin/actions/economy/company/delist', { name: btn.dataset.coDelist }, loadEconomyAdmin));

  const tokens = economyCatalog.tokens || [];
  document.getElementById('globalTokensTable').innerHTML = `
    <table class="data-table"><thead><tr><th>Sembol</th><th>Ad</th><th>Fiyat</th><th>Dolaşım</th><th>İşlem</th></tr></thead><tbody>
    ${tokens.map(t => `<tr>
      <td>${t.symbol}</td><td>${t.displayName}</td>
      <td><input type="number" step="0.01" value="${(t.priceMg / 1000).toFixed(2)}" data-tk-price="${t.symbol}" style="width:90px"></td>
      <td><input type="number" value="${t.circulating}" data-tk-circ="${t.symbol}" style="width:70px"></td>
      <td>
        <button class="btn btn-sm btn-gold" data-tk-save="${t.symbol}">Kaydet</button>
        <button class="btn btn-sm btn-danger" data-tk-del="${t.symbol}">Sil</button>
      </td></tr>`).join('')}</tbody></table>`;

  document.getElementById('newTokenForm').innerHTML = `
    <div><label>Sembol</label><input id="gTkSymbol" maxlength="6"></div>
    <div><label>Ad</label><input id="gTkName"></div>
    <div><label>Arz</label><input id="gTkSupply" type="number" value="1000"></div>
    <div><label>Fiyat ($)</label><input id="gTkPrice" type="number" value="1"></div>`;

  document.getElementById('globalTokensTable').querySelectorAll('[data-tk-save]').forEach(btn => {
    btn.onclick = () => {
      const sym = btn.dataset.tkSave;
      doAction('/admin/actions/economy/token/update', {
        symbol: sym,
        priceMc: +document.querySelector(`[data-tk-price="${sym}"]`).value,
        circulating: +document.querySelector(`[data-tk-circ="${sym}"]`).value
      }, loadEconomyAdmin);
    };
  });
  document.getElementById('globalTokensTable').querySelectorAll('[data-tk-del]').forEach(btn =>
    btn.onclick = () => {
      if (confirm('Coin silinsin mi?')) doAction('/admin/actions/economy/token/delete', { symbol: btn.dataset.tkDel }, loadEconomyAdmin);
    });
}

function fillMasak(name) {
  document.getElementById('masakResolvePlayer').value = name;
  document.getElementById('masakFinePlayer').value = name;
  document.getElementById('masakBlPlayer').value = name;
  document.querySelector('[data-page="masak"]').click();
}

async function loadReport() {
  const data = await api('/admin/report');
  if (data.error) return;
  document.getElementById('cbReport').innerHTML = `
    <div class="stat-grid">
      <div class="stat-card"><div class="label">Para Arzı</div><div class="value">${data.moneySupply}</div></div>
      <div class="stat-card gold"><div class="label">Endeks</div><div class="value">${data.economyIndex?.toFixed(2)}</div></div>
      <div class="stat-card warn"><div class="label">Enflasyon</div><div class="value">${(data.inflationRate * 100).toFixed(2)}%</div></div>
      <div class="stat-card accent"><div class="label">Faiz</div><div class="value">${(data.baseRate * 100).toFixed(2)}%</div></div>
      <div class="stat-card"><div class="label">Market Endeksi</div><div class="value">${data.marketIndex?.toFixed(2)}</div></div>
    </div>`;
}

const adminCameraReplay = { frames: [], index: 0, playing: false, timer: null, mapFocus: null };

function stopAdminCameraReplay() {
  adminCameraReplay.playing = false;
  if (adminCameraReplay.timer) {
    clearTimeout(adminCameraReplay.timer);
    adminCameraReplay.timer = null;
  }
}

function renderAdminRadarFrame(frameIndex) {
  const canvas = document.getElementById('cameraRadarCanvas');
  const status = document.getElementById('cameraReplayStatus');
  const focus = adminCameraReplay.mapFocus || { x: 0, z: 0, radius: 96 };
  const frames = adminCameraReplay.frames;
  if (!canvas || !frames.length) {
    if (canvas) canvas.innerHTML = '<p class="hint">Bu gece için radar karesi yok.</p>';
    return;
  }
  const w = 520, h = 280;
  const minX = focus.x - focus.radius, maxX = focus.x + focus.radius;
  const minZ = focus.z - focus.radius, maxZ = focus.z + focus.radius;
  const scale = Math.min(w / (maxX - minX || 1), h / (maxZ - minZ || 1));
  const toX = x => (x - minX) * scale;
  const toY = z => (z - minZ) * scale;
  const cx = toX(focus.x), cy = toY(focus.z);
  const radarR = focus.radius * scale;
  const sweep = (frameIndex * 4) % 360;
  const t = frames[Math.min(frameIndex, frames.length - 1)].recordedAt;
  const trailMs = 55000;
  const byPlayer = new Map();
  for (let i = 0; i <= frameIndex; i++) {
    const f = frames[i];
    if (t - f.recordedAt <= trailMs) byPlayer.set(f.playerUuid || f.playerName, f);
  }
  const blips = [...byPlayer.values()];
  const blipSvg = blips.map(p => {
    const px = toX(p.x), py = toY(p.z);
    return `<g class="map-radar-blip"><circle cx="${px}" cy="${py}" r="6" fill="#b366ff" stroke="#e8d4ff" stroke-width="1.2"/><title>${p.playerName}</title></g>`;
  }).join('');
  canvas.innerHTML = `<svg viewBox="0 0 ${w} ${h}" class="map-svg">
    <g class="map-radar-zone">
      <circle cx="${cx}" cy="${cy}" r="${radarR}" fill="rgba(20,40,60,0.3)" stroke="#3a5f8a"/>
      <line x1="${cx}" y1="${cy}" x2="${cx + radarR * Math.cos(sweep * Math.PI / 180)}"
        y2="${cy + radarR * Math.sin(sweep * Math.PI / 180)}" stroke="#5ecfff" stroke-width="2"/>
    </g>${blipSvg}</svg>`;
  if (status) {
    const cur = frames[frameIndex];
    status.textContent = adminCameraReplay.playing
      ? `Radar video: ${frameIndex + 1}/${frames.length} — ${new Date(cur.recordedAt).toLocaleTimeString('tr')}`
      : `${frames.length} kare yüklendi`;
  }
}

function tickAdminCameraReplay() {
  if (!adminCameraReplay.playing || !adminCameraReplay.frames.length) return;
  renderAdminRadarFrame(adminCameraReplay.index);
  if (adminCameraReplay.index >= adminCameraReplay.frames.length - 1) {
    stopAdminCameraReplay();
    return;
  }
  const cur = adminCameraReplay.frames[adminCameraReplay.index];
  const next = adminCameraReplay.frames[adminCameraReplay.index + 1];
  const delay = Math.max(35, Math.min(500, next.recordedAt - cur.recordedAt));
  adminCameraReplay.index++;
  adminCameraReplay.timer = setTimeout(tickAdminCameraReplay, delay);
}

function startAdminCameraReplay() {
  if (!adminCameraReplay.frames.length) return;
  stopAdminCameraReplay();
  adminCameraReplay.playing = true;
  adminCameraReplay.index = 0;
  tickAdminCameraReplay();
}

async function loadCameraLogs() {
  const box = document.getElementById('cameraLogsTable');
  const sel = document.getElementById('cameraNightSelect');
  if (!box) return;
  const night = sel?.value || '';
  const q = night ? `?night=${encodeURIComponent(night)}&limit=800` : '?limit=800';
  const [data, mapData] = await Promise.all([
    api('/admin/security/cameras' + q),
    api('/world/map').catch(() => ({}))
  ]);
  if (data.error) {
    box.innerHTML = '<p class="hint">Kayıt yüklenemedi.</p>';
    return;
  }
  adminCameraReplay.mapFocus = mapData.focus || { x: 0, z: 0, radius: 96 };
  adminCameraReplay.frames = (data.replay || []).map(f => ({
    playerUuid: f.playerUuid,
    playerName: f.playerName || f.name,
    x: f.x, y: f.y, z: f.z,
    recordedAt: f.recordedAt
  }));
  if (sel && data.nights) {
    const prev = sel.value;
    sel.innerHTML = (data.nights || []).map(n =>
      `<option value="${n}"${n === data.currentNight ? ' selected' : ''}>${n}</option>`
    ).join('') || `<option value="">—</option>`;
    if (prev) sel.value = prev;
  }
  if (!adminCameraReplay.playing) {
    renderAdminRadarFrame(adminCameraReplay.frames.length ? adminCameraReplay.frames.length - 1 : 0);
  }
  const rows = data.logs || [];
  if (!rows.length) {
    box.innerHTML = '<p class="hint">Bu gece için kamera kaydı yok (gece vardiyası bekleniyor).</p>';
    return;
  }
  box.innerHTML = `<table class="data-table"><thead><tr><th>Zaman</th><th>Oyuncu</th><th>X</th><th>Y</th><th>Z</th></tr></thead><tbody>${
    rows.map(r => `<tr><td>${new Date(r.recordedAt).toLocaleString('tr')}</td><td>${r.playerName}</td><td>${r.x}</td><td>${r.y}</td><td>${r.z}</td></tr>`).join('')
  }</tbody></table>`;
}

async function loadAdmin() {
  const [overview, cat] = await Promise.all([api('/admin/overview'), api('/catalog')]);
  if (overview.error) { show('login'); return; }
  show('admin');
  catalog = cat;

  document.getElementById('playerCount').textContent = overview.playerCount;
  document.getElementById('appealCount').textContent = (overview.openAppeals || []).length;
  document.getElementById('reportCount').textContent = overview.openReportCount ?? (overview.openReports || []).length;
  document.getElementById('prisonerCount').textContent = overview.prisonerCount ?? (overview.activePrisoners || []).length;
  document.getElementById('alertCount').textContent = (overview.masakAlerts || []).length;
  document.getElementById('mbCount').textContent = (overview.mbOfficials || []).length;

  document.getElementById('alertPreview').innerHTML = renderAlerts((overview.masakAlerts || []).slice(0, 5), false);
  document.getElementById('alerts').innerHTML = renderAlerts(overview.masakAlerts || [], true);
  document.getElementById('appeals').innerHTML = renderAppeals(overview.openAppeals || []);
  document.getElementById('justiceReports').innerHTML = renderJusticeReports(overview.openReports || []);
  document.getElementById('justicePrisoners').innerHTML = renderPrisoners(overview.activePrisoners || []);
  document.getElementById('mbOfficials').innerHTML = (overview.mbOfficials || []).length
    ? overview.mbOfficials.map(n => `<div class="list-item"><strong>${n}</strong></div>`).join('')
    : '<p class="hint">MB yetkilisi yok.</p>';

  fillSelect('eventSelect', catalog?.events || [], 'id', e => e.id);
  renderBlackMarketList();
  await loadPlayers();
  await loadReport();
}

document.getElementById('loginBtn').onclick = async () => {
  const data = await api('/login', {
    method: 'POST',
    body: JSON.stringify({ username: document.getElementById('username').value, password: document.getElementById('password').value })
  });
  if (data.error || !data.op) {
    document.getElementById('loginError').textContent = data.message || 'Erişim reddedildi — sunucu OP olmalı ve çevrimiçi olmalısınız.';
    return;
  }
  token = data.token;
  localStorage.setItem('mceconomy_token', token);
  loadAdmin();
};

document.getElementById('logoutBtn').onclick = logout;
document.getElementById('refreshBtn').onclick = loadAdmin;
setupNav('adminNav', 'page-');

document.getElementById('masakResolveBtn').onclick = () => doAction('/admin/actions/masak/resolve', {
  player: document.getElementById('masakResolvePlayer').value
}, loadAdmin);

document.getElementById('masakFineBtn').onclick = () => doAction('/admin/actions/masak/fine', {
  player: document.getElementById('masakFinePlayer').value,
  grams: +document.getElementById('masakFineGrams').value
}, loadAdmin);

document.getElementById('masakBlBtn').onclick = () => doAction('/admin/actions/masak/blacklist', {
  player: document.getElementById('masakBlPlayer').value
}, loadAdmin);

document.getElementById('eventTriggerBtn').onclick = () => doAction('/admin/actions/event/trigger', {
  type: document.getElementById('eventSelect').value,
  durationSeconds: +document.getElementById('eventDuration').value
}, loadAdmin);

document.getElementById('mbopGrantBtn').onclick = () => doAction('/admin/actions/mbop/grant', {
  player: document.getElementById('mbopGrantPlayer').value
}, loadAdmin);

document.getElementById('mbopRevokeBtn').onclick = () => doAction('/admin/actions/mbop/revoke', {
  player: document.getElementById('mbopRevokePlayer').value
}, loadAdmin);

function renderBlackMarketList() {
  const el = document.getElementById('bmList');
  if (!el) return;
  const custom = (catalog?.illegalGoods || []).filter(g => g.name && g.name.startsWith('★'));
  if (!custom.length) { el.innerHTML = '<p class="hint">Henüz özel ürün yok.</p>'; return; }
  el.innerHTML = custom.map(g => `
    <div class="list-item">
      <strong>${g.name}</strong> — al: ${formatMg(g.buyPriceMg)} / sat: ${formatMg(g.sellPriceMg)}
      <button class="btn btn-sm btn-danger" style="float:right" data-bmremove="${g.id}">Sil</button>
    </div>`).join('');
  el.querySelectorAll('button[data-bmremove]').forEach(b => b.onclick = () =>
    doAction('/admin/actions/blackmarket/remove', { id: b.dataset.bmremove }, loadAdmin));
}

document.getElementById('bmAddBtn').onclick = () => doAction('/admin/actions/blackmarket/add', {
  name: document.getElementById('bmName').value,
  itemId: document.getElementById('bmItemId').value,
  grams: +document.getElementById('bmPrice').value
}, loadAdmin);

document.getElementById('fullResetBtn')?.addEventListener('click', () => {
  const typed = prompt('Tam sifirlama GERI ALINAMAZ. Onaylamak icin SIFIRLA yazin:');
  if (typed === 'SIFIRLA') {
    doAction('/admin/actions/economy/full-reset', {}, () => {
      alert('Ekonomi sifirlandi. Oturumunuz kapandi; yeniden giris yapin.');
      logout();
    });
  }
});

document.getElementById('rebuildCbBtn').onclick = () => {
  if (confirm('Merkez Bankası yapısı yeniden kurulacak. Emin misiniz?')) {
    doAction('/admin/actions/central-bank/rebuild', {}, loadAdmin);
  }
};

document.getElementById('prisonImprisonBtn')?.addEventListener('click', () => {
  doAction('/admin/actions/justice/prison/imprison', {
    player: document.getElementById('prisonImprisonPlayer').value,
    minutes: +document.getElementById('prisonImprisonMinutes').value,
    reason: document.getElementById('prisonImprisonReason').value
  }, loadAdmin);
});

document.getElementById('prisonReleaseBtn')?.addEventListener('click', () => {
  doAction('/admin/actions/justice/prison/release', {
    player: document.getElementById('prisonReleasePlayer').value
  }, loadAdmin);
});

window.resolveAppeal = resolveAppeal;
window.justiceAction = justiceAction;
window.quickResolve = quickResolve;
window.fillMasak = fillMasak;
window.openPlayerAdmin = openPlayerAdmin;

document.getElementById('playerSearch')?.addEventListener('input', renderPlayersTable);
document.getElementById('playerAdminClose')?.addEventListener('click', closePlayerAdmin);
document.getElementById('playerAdminModal')?.addEventListener('click', e => {
  if (e.target.id === 'playerAdminModal') closePlayerAdmin();
});
document.querySelectorAll('#playerTabBar .tab-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    activePlayerTab = btn.dataset.playerTab;
    document.querySelectorAll('#playerTabBar .tab-btn').forEach(b => b.classList.toggle('active', b === btn));
    renderPlayerAdminModal();
  });
});
document.querySelectorAll('#globalTabBar .tab-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    activeGlobalTab = btn.dataset.globalTab;
    document.querySelectorAll('#globalTabBar .tab-btn').forEach(b => b.classList.toggle('active', b === btn));
    ['macro', 'companies', 'tokens'].forEach(t => {
      document.getElementById('globalTab-' + t)?.classList.toggle('hidden', t !== activeGlobalTab);
    });
  });
});
document.getElementById('macroSaveBtn')?.addEventListener('click', () => doAction('/admin/actions/economy/central-bank/update', {
  baseRate: +document.getElementById('gBaseRate').value,
  inflationRate: +document.getElementById('gInflation').value,
  economyIndex: +document.getElementById('gEconIndex').value,
  goldFactor: +document.getElementById('gGoldFactor').value,
  moneySupply: +document.getElementById('gMoneySupply').value,
  municipalBudgetMc: +document.getElementById('gMunicipal').value
}, () => { loadEconomyAdmin(); loadReport(); }));
document.getElementById('newCompanyBtn')?.addEventListener('click', () => doAction('/admin/actions/economy/company/create', {
  name: document.getElementById('gCoName').value,
  owner: document.getElementById('gCoOwner').value,
  ticker: document.getElementById('gCoTicker').value,
  treasuryMc: +document.getElementById('gCoTreasury').value,
  listed: document.getElementById('gCoListed').checked
}, loadEconomyAdmin));
document.getElementById('newTokenBtn')?.addEventListener('click', () => doAction('/admin/actions/economy/token/create', {
  symbol: document.getElementById('gTkSymbol').value,
  displayName: document.getElementById('gTkName').value,
  supply: +document.getElementById('gTkSupply').value,
  priceMc: +document.getElementById('gTkPrice').value
}, loadEconomyAdmin));

document.getElementById('cameraReloadBtn')?.addEventListener('click', () => {
  stopAdminCameraReplay();
  loadCameraLogs();
});
document.getElementById('cameraNightSelect')?.addEventListener('change', () => {
  stopAdminCameraReplay();
  loadCameraLogs();
});
document.getElementById('cameraReplayPlayBtn')?.addEventListener('click', startAdminCameraReplay);
document.getElementById('cameraReplayStopBtn')?.addEventListener('click', stopAdminCameraReplay);
async function loadConfigEditor() {
  const data = await api('/admin/config');
  if (data.error) {
    showToast(data.message || 'Config yüklenemedi', false);
    return;
  }
  if (data.path) document.getElementById('configPathLabel').textContent = data.path;
  const el = document.getElementById('configEditor');
  if (el) el.value = data.json || '';
}

document.getElementById('configReloadBtn')?.addEventListener('click', loadConfigEditor);
document.getElementById('configSaveBtn')?.addEventListener('click', async () => {
  const json = document.getElementById('configEditor')?.value || '';
  if (!json.trim()) {
    showToast('Config boş olamaz', false);
    return;
  }
  try {
    JSON.parse(json);
  } catch (e) {
    showToast('Geçersiz JSON: ' + e.message, false);
    return;
  }
  await doAction('/admin/actions/config/save', { json }, loadConfigEditor);
});

document.querySelectorAll('#adminNav .nav-item[data-page]').forEach(btn => {
  btn.addEventListener('click', () => {
    if (btn.dataset.page === 'cameras') loadCameraLogs();
    if (btn.dataset.page === 'economy-admin') loadEconomyAdmin();
    if (btn.dataset.page === 'config') loadConfigEditor();
  });
});

if (token) loadAdmin(); else show('login');
