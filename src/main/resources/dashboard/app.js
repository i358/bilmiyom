const charts = {};
let me = null;
let catalog = null;
let overview = null;
let portfolioLive = null;
let chartLiveTimer = null;

function show(id) {
  document.getElementById('loginView').classList.toggle('hidden', id !== 'login');
  document.getElementById('appView').classList.toggle('hidden', id !== 'app');
}

function destroyChart(name) {
  if (charts[name]) { charts[name].destroy(); charts[name] = null; }
}

function chartOptions() {
  return {
    plugins: { legend: { labels: { color: '#8b9cb3' } } },
    scales: {
      x: { ticks: { color: '#666', maxTicksLimit: 8 }, grid: { color: '#2a354433' } },
      y: { ticks: { color: '#666' }, grid: { color: '#2a354433' } }
    }
  };
}

function historyWithLive(history, currentPriceMg) {
  const sorted = (history || []).slice().sort((a, b) => a.recordedAt - b.recordedAt);
  if (currentPriceMg > 0) {
    sorted.push({ recordedAt: Date.now(), priceMg: currentPriceMg });
  }
  return sorted;
}

function historyToChart(history, scale = 1000, currentPriceMg = 0) {
  const sorted = historyWithLive(history, currentPriceMg);
  return {
    labels: sorted.map(p => new Date(p.recordedAt).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })),
    values: sorted.map(p => p.priceMg / scale)
  };
}

function formatChangeBps(bps) {
  if (bps == null || bps === 0) return '<span class="chg-up">0%</span>';
  const pct = (bps / 100).toFixed(2);
  return bps >= 0 ? `<span class="chg-up">+${pct}%</span>` : `<span class="chg-down">${pct}%</span>`;
}

function changeBpsFromHistory(history, currentPriceMg = 0) {
  if (!history?.length) return 0;
  const oldest = history[0].priceMg;
  const latest = currentPriceMg > 0 ? currentPriceMg : history[history.length - 1].priceMg;
  if (!oldest || !latest) return 0;
  return Math.round((latest - oldest) * 10000 / oldest);
}

function formatSupplyDemand(row) {
  if (row?.supplySharePct == null) return '';
  return `Arz %${row.supplySharePct} · Talep %${row.demandSharePct}`;
}

function setChartHeading(canvasId, title, bps, extra = '') {
  const h3 = document.getElementById(canvasId)?.closest('.chart-box')?.querySelector('h3');
  if (!h3) return;
  const parts = [title, formatChangeBps(bps)];
  if (extra) parts.push(`<span class="hint">${extra}</span>`);
  h3.innerHTML = parts.join(' ');
}

function renderLineChart(canvasId, name, labels, values, label, color, changeBps = null, subtitle = '') {
  destroyChart(name);
  const el = document.getElementById(canvasId);
  if (!el) return;
  if (changeBps != null) setChartHeading(canvasId, label, changeBps, subtitle);
  charts[name] = new Chart(el, {
    type: 'line',
    data: { labels, datasets: [{ label, data: values, borderColor: color, backgroundColor: color + '22', tension: 0.3, fill: true }] },
    options: chartOptions()
  });
}

function renderStats() {
  const masakBadge = me.blacklisted ? '<span class="badge badge-bad">Kara Liste</span>'
    : me.accountFrozen ? '<span class="badge badge-warn">Dondurulmuş</span>'
    : '<span class="badge badge-ok">Temiz</span>';
  document.getElementById('statGrid').innerHTML = `
    <div class="stat-card gold"><div class="label">Cüzdan</div><div class="value">${me.wallet}</div></div>
    <div class="stat-card accent"><div class="label">Vadesiz</div><div class="value">${me.checking || me.bank}</div></div>
    <div class="stat-card"><div class="label">Vadeli</div><div class="value">${me.hasTerm ? (me.termBalance || formatMg(me.termBalanceMg || 0)) : '—'}</div></div>
    <div class="stat-card"><div class="label">Kara Para</div><div class="value">${me.dirty}</div></div>
    <div class="stat-card"><div class="label">Kredi Skoru</div><div class="value">${me.creditScore}</div></div>
    <div class="stat-card"><div class="label">Meslek</div><div class="value">${me.job}</div></div>
    <div class="stat-card"><div class="label">MASAK</div><div class="value">${masakBadge}</div></div>
    <div class="stat-card gold"><div class="label">Ekonomi Endeksi</div><div class="value">${(overview?.economyIndex || me.economyIndex || 0).toFixed(2)}</div></div>
    ${me.fiatStrength != null ? `<div class="stat-card accent"><div class="label">Fiat Gücü ($)</div><div class="value">${me.fiatStrength.toFixed(2)}</div><div class="hint">Altın %${me.goldBackingPct ?? '—'} · Devlet %${me.stateCredibilityPct ?? '—'} · Yatırım %${me.investmentPct ?? '—'}</div></div>` : ''}
    <div class="stat-card accent"><div class="label">Altın Külçe Değeri</div><div class="value">${me.ingotPrice || '—'}</div></div>
    <div class="stat-card"><div class="label">Enflasyon</div><div class="value">${((me.inflationRate || 0) * 100).toFixed(2)}%</div></div>
    <div class="stat-card gold"><div class="label">MB Altın Rezervi</div><div class="value">${me.reserveGoldBlocks != null ? me.reserveGoldBlocks + ' blok' : '—'}</div></div>
    <div class="stat-card accent"><div class="label">Belediye Bütçesi</div><div class="value">${me.municipalBudget || overview?.municipalBudget || '—'}</div></div>`;

  const onlineBadge = document.getElementById('onlineBadge');
  onlineBadge.textContent = me.online ? 'Çevrimiçi' : 'Çevrimdışı';
  onlineBadge.className = 'badge ' + (me.online ? 'badge-ok' : 'badge-muted');
  document.getElementById('sidebarPlayer').textContent = me.name;
}

function renderHoldings() {
  const liveMap = {};
  (portfolioLive?.holdings || []).forEach(h => { liveMap[h.kind + ':' + h.symbol] = h; });
  let html = '';
  (me.shares || []).forEach(s => {
    const live = liveMap['SHARE:' + s.ticker];
    const chg = live ? ' ' + formatChangeBps(live.changeBps) : '';
    html += `<div class="list-item"><strong>${s.ticker}</strong> — ${s.amount} hisse (${formatMg(s.priceMg)}/adet)${chg}</div>`;
  });
  (me.tokens || []).forEach(t => {
    const live = liveMap['TOKEN:' + t.symbol];
    const chg = live ? ' ' + formatChangeBps(live.changeBps) : '';
    html += `<div class="list-item"><strong>${t.symbol}</strong> — ${t.amount} coin (${formatMg(t.priceMg)})${chg}</div>`;
  });
  (me.privateDeposits || []).forEach(p => html += `<div class="list-item"><strong>${p.bank}</strong> — ${p.balance || formatMg(p.balanceMg)}</div>`);
  document.getElementById('holdings').innerHTML = html || '<p class="hint">Henüz varlık yok.</p>';
}

function renderLoan() {
  const el = document.getElementById('loanStatus');
  if (!el) return;
  if (me.loan) {
    el.innerHTML = `<div class="list-item">Kalan: <strong>${me.loan.remaining}</strong></div>
      <div class="list-item">Taksit: <strong>${me.loan.installment}</strong></div>`;
  } else {
    el.innerHTML = '<p class="hint">Aktif kredi yok.</p>';
  }
}

function renderQuest() {
  const el = document.getElementById('questStatus');
  if (!el) return;
  if (me.quest) {
    const tag = me.quest.companyQuest ? ' <span class="badge badge-gold">Şirket görevi</span>' : '';
    el.innerHTML = `<div class="list-item"><strong>${me.quest.title}</strong>${tag}</div>
      <div class="list-item">İlerleme: ${me.quest.progress}/${me.quest.required}</div>
      <div class="list-item">Ödül: ${me.quest.reward}${me.quest.companyQuest ? ' (üretim şirkete)' : ''}</div>`;
  } else {
    el.innerHTML = '<p class="hint">Aktif görev yok.</p>';
  }
}

function renderAppeals() {
  const el = document.getElementById('myAppeals');
  if (!el) return;
  const appeals = me.appeals || [];
  el.innerHTML = appeals.length ? appeals.map(a =>
    `<div class="list-item"><span class="badge badge-muted">${a.status}</span> <strong>#${a.id}</strong> ${a.subject}</div>`
  ).join('') : '<p class="hint">İtiraz yok.</p>';
}

function formatRemaining(ms) {
  const min = Math.floor(ms / 60000);
  const sec = Math.floor((ms % 60000) / 1000);
  return min + ' dk ' + sec + ' sn';
}

function renderJustice() {
  const banner = document.getElementById('prisonBanner');
  const info = document.getElementById('prisonInfo');
  if (banner && info) {
    if (me.prison) {
      banner.classList.remove('hidden');
      info.innerHTML = `<div class="list-item">Kalan: <strong>${formatRemaining(me.prison.remainingMs)}</strong></div>
        <div class="list-item">Sebep: ${me.prison.reason || '—'}</div>`;
    } else {
      banner.classList.add('hidden');
    }
  }
  const reportsEl = document.getElementById('myReports');
  if (reportsEl) {
    const reports = me.myReports || [];
    reportsEl.innerHTML = reports.length ? reports.map(r =>
      `<div class="list-item"><span class="badge badge-muted">${r.status}</span>
        <strong>#${r.id}</strong> [${r.type}] ${r.targetName || '—'} — ${r.subject}</div>`
    ).join('') : '<p class="hint">Henüz başvuru yok.</p>';
  }
}

function renderCert() {
  const el = document.getElementById('certStatus');
  if (!el) return;
  if (me.bankCertified) {
    el.innerHTML = '<span class="badge badge-ok">Sertifikanız var</span>';
  } else {
    el.innerHTML = `<span class="badge badge-muted">Sertifika yok</span>
      <div class="hint" style="margin-top:8px">Gereken ücret: <strong>${me.certCost || '—'}</strong><br>
      Cüzdanınız: <strong>${me.wallet}</strong></div>`;
  }
}

function marketCommodityLabel(c) {
  const chg = c.changeBps != null ? ` · ${c.changeBps >= 0 ? '+' : ''}${(c.changeBps / 100).toFixed(1)}%` : '';
  const sd = c.supplySharePct != null ? ` · A%${c.supplySharePct} T%${c.demandSharePct}` : '';
  return `${c.name} ${formatMg(c.priceMg)}${chg}${sd}`;
}

function populateSelects() {
  const commodities = catalog?.commodities || overview?.commodities || me.market || [];
  const buyable = commodities.filter(c => c.buyable !== false);
  const sellable = commodities.filter(c => c.sellable !== false);
  fillSelect('marketBuySelect', buyable, 'id', marketCommodityLabel);
  fillSelect('marketSellSelect', sellable, 'id', marketCommodityLabel);
  fillSelect('chartSymbol', sellable.length ? sellable : commodities, 'id', c => c.name);
  fillSelect('jobSelect', catalog?.jobs || [], 'id', j => j.name);
  const ownedTokens = me.tokens || [];
  fillSelect('tokenTradeSelect',
    ownedTokens.length ? ownedTokens : (catalog?.tokens || []),
    'symbol',
    t => `${t.symbol} — elinizde: ${t.amount ?? '?'} (${formatMg(t.priceMg)})`);
  const ownedShares = me.shares || [];
  const shareChartItems = ownedShares.length
    ? ownedShares.map(s => ({ symbol: s.ticker, name: s.ticker, priceMg: s.priceMg }))
    : (catalog?.companies || []).filter(c => c.listed && c.ticker).map(c => ({ symbol: c.ticker, name: c.name, priceMg: c.sharePriceMg }));
  fillSelect('shareChartSelect', shareChartItems, 'symbol', s => `${s.symbol} — ${formatMg(s.priceMg || 0)}`);
  fillSelect('shareCompanySelect', catalog?.companies || [], 'name', c => `${c.name} (${c.ticker || '—'}) ${formatMg(c.sharePriceMg)}/hisse`);
  fillSelect('listCompanySelect', (catalog?.companies || []).filter(c => !c.listed), 'name', c => c.name);
  fillSelect('delistCompanySelect', (catalog?.companies || []).filter(c => c.listed), 'name', c => `${c.name} (${c.ticker || '—'})`);
  fillSelect('pbankSelect', catalog?.privateBanks || [], 'name', b => b.name);
  fillSelect('illegalSelect', catalog?.illegalGoods || [], 'id', g => `${g.name} (al: ${formatMg(g.buyPriceMg)})`);
  fillSelect('tokenChartSelect', catalog?.tokens || overview?.tokens || [], 'symbol', t => t.symbol || t.name);
  fillSelect('levSymbol', catalog?.tokens || [], 'symbol', t => `${t.symbol} (${formatMg(t.priceMg)})`);
  const levPos = me.leveragePositions || [];
  fillSelect('levChartSelect', levPos, 'id',
    p => `#${p.id} ${p.symbol} ${p.side} ${p.leverage}x`,
    levPos.length ? null : 'Açık pozisyon yok');
}

function kindLabel(kind) {
  if (kind === 'TOKEN') return 'Coin';
  if (kind === 'SHARE') return 'Hisse';
  if (kind === 'LEVERAGE') return 'Kaldıraç';
  return kind;
}

function renderLeverageLineChart(canvasEl, chartKey, h) {
  if (!canvasEl || !h) return;
  const chart = historyToChart(h.history, 1000, h.priceMg);
  const entryVal = (h.entryPriceMg || 0) / 1000;
  const entryLine = chart.values.map(() => entryVal);
  const sideColor = h.side === 'LONG' ? '#5fd68a' : '#f87171';
  destroyChart(chartKey);
  charts[chartKey] = new Chart(canvasEl, {
    type: 'line',
    data: {
      labels: chart.labels,
      datasets: [
        { label: 'Fiyat', data: chart.values, borderColor: '#e6b422', backgroundColor: '#e6b42222', tension: 0.25, fill: true, pointRadius: 0 },
        { label: 'Giriş', data: entryLine, borderColor: sideColor, borderDash: [6, 4], tension: 0, fill: false, pointRadius: 0 }
      ]
    },
    options: { ...chartOptions(), plugins: { legend: { display: true, labels: { color: '#8b9cb3', boxWidth: 12 } } } }
  });
}

async function renderLeveragePositions() {
  const el = document.getElementById('leveragePositions');
  if (!el) return;
  const positions = me.leveragePositions || [];
  if (!positions.length) { el.innerHTML = '<p class="hint">Açık pozisyon yok.</p>'; return; }
  const live = await api('/charts/portfolio');
  const levItems = (live?.holdings || []).filter(h => h.kind === 'LEVERAGE');
  el.innerHTML = positions.map(p => {
    const up = p.pnl.startsWith('+');
    return `<div class="list-item position-row" style="margin-bottom:14px">
      <div><strong>#${p.id} ${p.symbol}</strong> <span class="badge ${p.side === 'LONG' ? 'badge-ok' : 'badge-bad'}">${p.side} ${p.leverage}x</span></div>
      <div class="hint">Giriş: ${formatMg(p.entryPriceMg)} · Anlık: ${formatMg(p.currentPriceMg)}</div>
      <div class="hint">Teminat: ${p.margin} · Özsermaye: ${p.equity} · <span class="${up ? 'pnl-up' : 'pnl-down'}">K/Z ${p.pnl}</span></div>
      <canvas id="levPosChart-${p.id}" class="lev-pos-mini"></canvas>
      <button class="btn btn-ghost btn-sm" data-close="${p.id}" style="margin-top:8px">Kapat</button>
    </div>`;
  }).join('');
  positions.forEach(p => {
    const h = levItems.find(l => l.positionId === p.id)
      || levItems.find(l => l.symbol === p.symbol && l.side === p.side);
    const canvas = document.getElementById('levPosChart-' + p.id);
    if (h) renderLeverageLineChart(canvas, 'levpos-' + p.id, h);
  });
  el.querySelectorAll('button[data-close]').forEach(btn => {
    btn.onclick = () => doAction('/actions/exchange/leverage/close', { positionId: +btn.dataset.close }, refresh);
  });
}

function renderTermBalanceChart(history, currentMg) {
  const hint = document.getElementById('termChartHint');
  const el = document.getElementById('termBalanceChart');
  if (!el) return;
  if (!me.hasTerm) {
    destroyChart('termBalance');
    if (hint) {
      hint.textContent = 'Vadeli hesap açın ve bakiye yatırın; grafik ~30 sn aralıkla güncellenir.';
      hint.classList.remove('hidden');
    }
    return;
  }
  const chart = historyToChart(history || [], 1000, currentMg);
  const bps = overview?.termHistoryChangeBps ?? 0;
  const pct = me.termInterestTotalPct != null ? me.termInterestTotalPct : Math.round((me.termInterestRate || 0) * 100);
  const interval = me.termInterestIntervalSec || 60;
  const subtitle = `7 gün toplam getiri %${pct} · ${interval} sn'de bir faiz`;
  renderLineChart('termBalanceChart', 'termBalance', chart.labels, chart.values, 'Vadeli Bakiye', '#aa88ff', bps, subtitle);
  if (hint) hint.classList.add('hidden');
}

async function renderCharts() {
  renderPortfolioChart();
  renderMarketBar(catalog?.commodities || me.market || []);
  renderTermBalanceChart(overview?.termHistory || [], overview?.termBalanceMg || me.termBalanceMg || 0);
  renderIndexChart(overview?.indexHistory || []);
  renderMacroCharts();
  renderShareBar(catalog?.companies || []);
  renderTokenBar(catalog?.tokens || overview?.tokens || []);
  renderAssetBar();
  await loadPortfolioLive();
  const symbol = document.getElementById('chartSymbol')?.value || 'bugday';
  loadPriceChart(symbol);
  const tokenSel = document.getElementById('tokenChartSelect');
  if (tokenSel && tokenSel.options.length) loadTokenChart(tokenSel.value);
  const shareSel = document.getElementById('shareChartSelect');
  if (shareSel && shareSel.options.length) loadShareChart(shareSel.value);
}

function findLeverageHolding(positionId) {
  return (portfolioLive?.holdings || []).find(h => h.kind === 'LEVERAGE' && h.positionId === positionId);
}

async function loadLeverageChart(positionId) {
  if (!positionId) return;
  if (!portfolioLive) portfolioLive = await api('/charts/portfolio');
  let h = findLeverageHolding(positionId);
  if (!h) {
    const p = (me.leveragePositions || []).find(x => x.id === positionId);
    if (!p) return;
    const data = await api('/prices?symbol=' + encodeURIComponent(p.symbol) + '&type=TOKEN');
    h = {
      kind: 'LEVERAGE', positionId: p.id, symbol: p.symbol, side: p.side, leverage: p.leverage,
      entryPriceMg: p.entryPriceMg, priceMg: p.currentPriceMg, pnlMg: 0,
      history: data.history || []
    };
  }
  const el = document.getElementById('leverageChart');
  renderLeverageLineChart(el, 'leverage', h);
}

async function loadPortfolioLive() {
  const box = document.getElementById('portfolioLiveCharts');
  const updated = document.getElementById('portfolioLiveUpdated');
  if (!box) return;
  portfolioLive = await api('/charts/portfolio');
  const items = portfolioLive?.holdings || [];
  if (updated && portfolioLive?.updatedAt) {
    updated.textContent = 'Güncellendi: ' + new Date(portfolioLive.updatedAt).toLocaleTimeString('tr-TR');
  }
  if (!items.length) {
    box.innerHTML = '<p class="hint">Portföyünüzde coin, hisse veya kaldıraç pozisyonu yok.</p>';
    renderHoldings();
    populateSelects();
    return;
  }
  box.innerHTML = items.map((h, i) => {
    const badge = h.kind === 'LEVERAGE'
      ? `<span class="badge ${h.side === 'LONG' ? 'badge-ok' : 'badge-bad'}">${h.side} ${h.leverage}x</span>`
      : `<span class="badge badge-muted">${kindLabel(h.kind)}</span>`;
    const meta = h.kind === 'LEVERAGE'
      ? `Giriş ${formatMg(h.entryPriceMg)} · Anlık ${formatMg(h.priceMg)} · K/Z ${formatMg(h.pnlMg)}`
      : `${h.amount} adet · ${formatMg(h.priceMg)} · Toplam ${formatMg(h.valueMg)}`;
    const chg = h.kind === 'LEVERAGE' && h.pnlMg != null
      ? (h.pnlMg >= 0 ? `<span class="chg-up">+${formatMg(h.pnlMg)}</span>` : `<span class="chg-down">${formatMg(h.pnlMg)}</span>`)
      : formatChangeBps(h.changeBps);
    return `<div class="portfolio-live-card">
      <div class="portfolio-live-meta">
        <div><strong>${h.kind === 'LEVERAGE' ? '#' + h.positionId + ' ' : ''}${h.symbol}</strong> ${badge}</div>
        <div>${chg}</div>
      </div>
      <div class="hint" style="margin-bottom:6px">${meta}</div>
      <canvas id="liveChart-${i}"></canvas>
    </div>`;
  }).join('');
  items.forEach((h, i) => {
    const el = document.getElementById('liveChart-' + i);
    if (!el) return;
    const key = h.kind === 'LEVERAGE' ? 'live-lev-' + h.positionId : 'live-' + h.kind + '-' + h.symbol;
    if (h.kind === 'LEVERAGE') {
      renderLeverageLineChart(el, key, h);
      return;
    }
    const chart = historyToChart(h.history, 1000, h.priceMg);
    const color = h.kind === 'TOKEN' ? '#e6b422' : '#4da6ff';
    destroyChart(key);
    charts[key] = new Chart(el, {
      type: 'line',
      data: { labels: chart.labels, datasets: [{ label: h.symbol, data: chart.values, borderColor: color, backgroundColor: color + '22', tension: 0.25, fill: true, pointRadius: 0 }] },
      options: { ...chartOptions(), plugins: { legend: { display: false } } }
    });
  });
  renderHoldings();
  populateSelects();
  const levSel = document.getElementById('levChartSelect');
  if (levSel && levSel.value) loadLeverageChart(+levSel.value);
}

function startChartLiveRefresh() {
  stopChartLiveRefresh();
  chartLiveTimer = setInterval(async () => {
    try {
      if (!document.getElementById('page-charts')?.classList.contains('active')) return;
      await loadPortfolioLive();
      const sym = document.getElementById('chartSymbol')?.value;
      if (sym) loadPriceChart(sym);
      const tok = document.getElementById('tokenChartSelect')?.value;
      if (tok) loadTokenChart(tok);
      const shr = document.getElementById('shareChartSelect')?.value;
      if (shr) loadShareChart(shr);
      const levId = document.getElementById('levChartSelect')?.value;
      if (levId) loadLeverageChart(+levId);
      overview = await api('/charts/overview');
      renderTermBalanceChart(overview?.termHistory || [], overview?.termBalanceMg || me.termBalanceMg || 0);
      renderIndexChart(overview?.indexHistory || []);
      renderMacroCharts();
      renderTokenBar(overview?.tokens || []);
    } catch (e) { /* sessiz — sonraki tur */ }
  }, 20000);
}

function stopChartLiveRefresh() {
  if (chartLiveTimer) { clearInterval(chartLiveTimer); chartLiveTimer = null; }
}

function renderBarChart(canvasId, name, labels, values, label, color) {
  destroyChart(name);
  const el = document.getElementById(canvasId);
  if (!el) return;
  charts[name] = new Chart(el, {
    type: 'bar',
    data: { labels, datasets: [{ label, data: values, backgroundColor: color }] },
    options: chartOptions()
  });
}

function renderShareBar(companies) {
  const list = companies.slice(0, 10);
  renderBarChart('shareBarChart', 'shareBar', list.map(c => c.ticker || c.name),
    list.map(c => (c.sharePriceMg || 0) / 1000), 'Hisse ($)', '#4da6ff66');
}

function renderTokenBar(tokens) {
  const list = tokens.slice(0, 10);
  renderBarChart('tokenBarChart', 'tokenBar', list.map(t => {
    const bps = t.changeBps ?? 0;
    return `${t.symbol} (${bps >= 0 ? '+' : ''}${(bps / 100).toFixed(1)}%)`;
  }), list.map(t => (t.priceMg || 0) / 1000), 'Fiyat ($)', '#e6b42266');
}

function renderAssetBar() {
  const shares = (me.shares || []).reduce((s, x) => s + (x.amount * (x.priceMg || 0)), 0);
  renderBarChart('assetBarChart', 'assetBar',
    ['Cüzdan', 'Banka', 'Kara Para', 'Hisse'],
    [(me.walletMg || 0) / 1000, (me.bankMg || 0) / 1000, (me.dirtyMg || 0) / 1000, shares / 1000],
    'MC', '#d4a84366');
}

function renderPortfolioChart() {
  destroyChart('portfolio');
  const el = document.getElementById('portfolioChart');
  if (!el) return;
  charts.portfolio = new Chart(el, {
    type: 'doughnut',
    data: {
      labels: ['Cüzdan', 'Vadesiz', 'Vadeli', 'Kara Para'],
      datasets: [{ data: [me.walletMg || 0, me.checkingMg || me.bankMg || 0, me.termBalanceMg || 0, me.dirtyMg || 0],
        backgroundColor: ['#d4a843', '#4da6ff', '#aa88ff', '#555'] }]
    },
    options: { plugins: { legend: { labels: { color: '#8b9cb3' } } } }
  });
}

function renderMarketBar(commodities) {
  const top = commodities.slice(0, 8);
  destroyChart('marketBar');
  const el = document.getElementById('marketBarChart');
  if (!el) return;
  charts.marketBar = new Chart(el, {
    type: 'bar',
    data: {
      labels: top.map(c => {
        const bps = c.changeBps ?? 0;
        const pct = (bps / 100).toFixed(1);
        const sign = bps >= 0 ? '+' : '';
        return `${c.name} (${sign}${pct}%)`;
      }),
      datasets: [{ label: 'Fiyat ($)', data: top.map(c => c.priceMg / 1000), backgroundColor: '#d4a84366' }]
    },
    options: chartOptions()
  });
}

let selectedInvItem = null;

async function loadInventory() {
  const box = document.getElementById('inventoryBox');
  const panel = document.getElementById('invActionPanel');
  if (!box) return;
  if (panel) panel.classList.add('hidden');
  selectedInvItem = null;
  box.innerHTML = '<p class="hint">Yükleniyor...</p>';
  const data = await api('/inventory');
  if (!data.online) {
    box.innerHTML = '<p class="hint warn-text">Çevrimdışısınız. Envanteri görmek için oyuna girin.</p>';
    return;
  }
  const items = data.items || [];
  if (!items.length) { box.innerHTML = '<p class="hint">Envanteriniz boş.</p>'; return; }
  box.innerHTML = '<div class="inv-grid">' + items.map(i => {
    const sellable = i.marketable ? 'badge-ok' : 'badge-muted';
    const tag = i.marketable ? 'Market' : 'Karaborsa';
    return `<div class="inv-item inv-selectable" data-item-id="${i.itemId}" data-marketable="${i.marketable ? '1' : '0'}" data-count="${i.count}" data-name="${i.name.replace(/"/g, '&quot;')}">
      <div><span class="inv-name">${i.name}</span> <span class="badge ${sellable}">${tag}</span></div>
      <span class="inv-count">x${i.count}</span>
    </div>`;
  }).join('') + '</div>';
  box.querySelectorAll('.inv-selectable').forEach(el => {
    el.onclick = () => selectInventoryItem(el);
  });
}

function selectInventoryItem(el) {
  selectedInvItem = {
    itemId: el.dataset.itemId,
    marketable: el.dataset.marketable === '1',
    count: +el.dataset.count,
    name: el.dataset.name
  };
  const panel = document.getElementById('invActionPanel');
  const sellBtn = document.getElementById('invMarketSellBtn');
  const sellAllBtn = document.getElementById('invMarketSellAllBtn');
  if (!panel) return;
  panel.classList.remove('hidden');
  document.getElementById('invActionTitle').textContent = selectedInvItem.name;
  document.getElementById('invActionMeta').textContent = 'Envanterde: x' + selectedInvItem.count
    + (selectedInvItem.marketable ? ' · Market fiyatindan satilabilir' : ' · Sadece karaborsa ilani');
  document.getElementById('invQty').max = selectedInvItem.count;
  document.getElementById('invQty').value = 1;
  const priceLabel = document.getElementById('invPriceLabel');
  const priceInput = document.getElementById('invPrice');
  if (sellBtn) sellBtn.classList.toggle('hidden', !selectedInvItem.marketable);
  if (sellAllBtn) sellAllBtn.classList.toggle('hidden', !selectedInvItem.marketable);
  if (priceLabel) priceLabel.classList.toggle('hidden', selectedInvItem.marketable);
  if (priceInput) priceInput.classList.toggle('hidden', selectedInvItem.marketable);
}

document.getElementById('invMarketSellBtn').onclick = () => {
  if (!selectedInvItem?.marketable) { showToast('Bu item markette satilamaz', false); return; }
  doAction('/actions/inventory/market-sell', {
    itemId: selectedInvItem.itemId,
    quantity: +document.getElementById('invQty').value
  }, () => { loadInventory(); refresh(); });
};

document.getElementById('invMarketSellAllBtn').onclick = () => {
  if (!selectedInvItem?.marketable) { showToast('Bu item markette satilamaz', false); return; }
  doAction('/actions/inventory/market-sell-all', {
    itemId: selectedInvItem.itemId
  }, () => { loadInventory(); refresh(); });
};

document.getElementById('invBmListBtn').onclick = () => {
  if (!selectedInvItem) return;
  doAction('/actions/inventory/blackmarket-list', {
    itemId: selectedInvItem.itemId,
    quantity: +document.getElementById('invQty').value,
    mc: +document.getElementById('invPrice').value
  }, () => { loadInventory(); refresh(); });
};

async function loadEmployees() {
  const box = document.getElementById('employeesBox');
  if (!box) return;
  box.innerHTML = '<p class="hint">Yükleniyor...</p>';
  const data = await api('/workforce');
  const companies = data.companies || [];
  if (!companies.length) { box.innerHTML = '<p class="hint">Sahibi olduğunuz bir şirket yok.</p>'; return; }
  box.innerHTML = companies.map(c => {
    const emps = (c.employees || []).map(e => `
      <div class="list-item emp-row">
        <div><strong>${e.name}</strong> <span class="badge badge-muted">${e.role}</span></div>
        <div class="hint">Maaş: ${e.salary} · Üretim: ${e.produced ?? '—'}</div>
        <div class="form-row">
          <input type="number" min="1" placeholder="Yeni maaş ($)" data-raise-input="${e.id}" style="max-width:140px">
          <button class="btn btn-accent btn-sm" data-raise="${e.id}">Zam</button>
          <button class="btn btn-danger btn-sm" data-fire="${e.id}">Kov</button>
        </div>
      </div>`).join('') || '<p class="hint">Çalışan yok.</p>';
    const apps = (c.applications || []).map(a => `
      <div class="list-item app-row">
        <div><strong>${a.name}</strong> <span class="badge badge-warn">Başvuru</span> ${a.role}</div>
        <div class="hint">İstenen maaş: ${a.salary} — "${a.message}"</div>
        <div class="form-row">
          <button class="btn btn-gold btn-sm" data-accept="${a.id}">Kabul</button>
          <button class="btn btn-ghost btn-sm" data-reject="${a.id}">Reddet</button>
        </div>
      </div>`).join('') || '<p class="hint">Bekleyen başvuru yok.</p>';
    const stash = (c.stash || []).map(s =>
      `<span class="badge badge-muted">${s.quantity}x ${s.name}</span>`).join(' ') || '<span class="hint">Depo boş</span>';
    return `<div class="panel-card" style="margin-bottom:14px">
      <h3>${c.name} <span class="hint">Kasa: ${c.treasury}</span></h3>
      <button class="btn btn-accent btn-sm" data-bonus="${c.name}">Tüm Çalışanlara İkramiye Öde</button>
      <button class="btn btn-gold btn-sm" data-vault-tp="${c.name}">Sandığa Git</button>
      <p class="hint" style="margin-top:8px"><strong>Gizli sandik:</strong> ${stash}</p>
      <p class="hint"><code>/sirket depo ${c.name}</code> · <code>/sirket sandik ${c.name}</code></p>
      <h4 style="margin:12px 0 6px">Çalışanlar</h4>${emps}
      <h4 style="margin:12px 0 6px">İş Başvuruları</h4>${apps}
    </div>`;
  }).join('');
  box.querySelectorAll('button[data-fire]').forEach(b => b.onclick = () =>
    doAction('/actions/company/employee/fire', { employeeId: +b.dataset.fire }, loadEmployees));
  box.querySelectorAll('button[data-raise]').forEach(b => b.onclick = () => {
    const val = +document.querySelector(`[data-raise-input="${b.dataset.raise}"]`).value;
    if (!val) { showToast('Yeni maaş girin', false); return; }
    doAction('/actions/company/employee/raise', { employeeId: +b.dataset.raise, mc: val }, loadEmployees);
  });
  box.querySelectorAll('button[data-bonus]').forEach(b => b.onclick = () =>
    doAction('/actions/company/employee/bonus', { company: b.dataset.bonus }, loadEmployees));
  box.querySelectorAll('button[data-vault-tp]').forEach(b => b.onclick = () =>
    doAction('/actions/company/vault/teleport', { company: b.dataset.vaultTp }, () => {
      showToast('Sandiga isinlandiniz (oyunda)', true);
      loadEmployees();
    }));
  box.querySelectorAll('button[data-accept]').forEach(b => b.onclick = () =>
    doAction('/actions/company/application/accept', { applicationId: +b.dataset.accept }, loadEmployees));
  box.querySelectorAll('button[data-reject]').forEach(b => b.onclick = () =>
    doAction('/actions/company/application/reject', { applicationId: +b.dataset.reject }, loadEmployees));
}

function renderCompanyStashDetail(company) {
  const box = document.getElementById('companyStashBox');
  if (!box) return;
  if (!company) {
    box.innerHTML = '<p class="hint">Şirket seçin.</p>';
    return;
  }
  const stash = company.stash || [];
  if (!stash.length) {
    box.innerHTML = `<p class="hint"><strong>${company.name}</strong> sandigi bos. Maden %2 + pisirilmis yemek buraya; diger urunler pazara satilir.</p>`;
    return;
  }
  box.innerHTML = `
    <p class="hint" style="margin-bottom:8px">Kasa: ${company.treasury}</p>
    ${stash.map(s => `<div class="list-item"><strong>${s.quantity}x</strong> ${s.name}</div>`).join('')}`;
}

async function loadCompanyStash() {
  const box = document.getElementById('companyStashBox');
  const select = document.getElementById('companyStashSelect');
  if (!box || !select) return;
  box.innerHTML = '<p class="hint">Yükleniyor...</p>';
  const data = await api('/workforce');
  const companies = data.companies || [];
  if (!companies.length) {
    select.innerHTML = '';
    box.innerHTML = '<p class="hint">Sahibi olduğunuz şirket yok. Önce <strong>Şirket Kur</strong> bölümünden şirket açın.</p>';
    return;
  }
  const prev = select.value;
  fillSelect('companyStashSelect', companies, 'name', c => c.name);
  if (prev && companies.some(c => c.name === prev)) {
    select.value = prev;
  }
  const company = companies.find(c => c.name === select.value) || companies[0];
  renderCompanyStashDetail(company);
}

document.getElementById('companyStashSelect')?.addEventListener('change', async () => {
  const data = await api('/workforce');
  const company = (data.companies || []).find(c => c.name === document.getElementById('companyStashSelect')?.value);
  renderCompanyStashDetail(company);
});

document.getElementById('companyVaultTpBtn')?.addEventListener('click', () => {
  const company = document.getElementById('companyStashSelect')?.value;
  if (!company) { showToast('Şirket seçin', false); return; }
  doAction('/actions/company/vault/teleport', { company }, () => showToast('Sandiga isinlandiniz', true));
});

document.getElementById('companyVaultExitBtn')?.addEventListener('click', () =>
  doAction('/actions/company/vault/exit', {}, () => showToast('Konumunuza dondunuz', true)));

document.getElementById('companyStashRefreshBtn')?.addEventListener('click', loadCompanyStash);

function renderDocs() {
  const box = document.getElementById('docsBox');
  if (!box || box.dataset.loaded) return;
  box.dataset.loaded = '1';
  box.innerHTML = DOCS_HTML;
}

function renderMacroCharts() {
  if (!overview) return;
  const inf = historyToChart(overview.inflationHistory || [], 10000);
  renderLineChart('inflationChart', 'inflation', inf.labels, inf.values, 'Enflasyon %', '#ff6b6b',
    changeBpsFromHistory(overview.inflationHistory || []));
  const gold = historyToChart(overview.goldReserveHistory || [], 1000);
  renderLineChart('goldReserveChart', 'goldReserve', gold.labels, gold.values, 'Altın Rezervi', '#ffd700',
    changeBpsFromHistory(overview.goldReserveHistory || []));
  const mun = historyToChart(overview.municipalHistory || [], 1000);
  const munLive = overview.municipalBudgetMg || 0;
  renderLineChart('municipalChart', 'municipal', mun.labels, mun.values, 'Belediye ($)', '#7bed9f',
    changeBpsFromHistory(overview.municipalHistory || [], munLive));
  if (document.getElementById('fiatStrengthChart')) {
    const fiat = historyToChart(overview.fiatStrengthHistory || [], 10000);
    const live = (overview?.fiatStrength || me?.fiatStrength || 1) * 10000;
    renderLineChart('fiatStrengthChart', 'fiatStrength', fiat.labels, fiat.values, 'Fiat Gücü', '#a78bfa',
      changeBpsFromHistory(overview.fiatStrengthHistory || [], live));
  }
}

function renderIndexChart(history) {
  const chart = historyToChart(history, 1000);
  const live = (overview?.economyIndex || me.economyIndex || 0) * 1000;
  const bps = overview?.economyIndexChangeBps ?? changeBpsFromHistory(history, live);
  renderLineChart('indexChart', 'index', chart.labels, chart.values, 'Endeks', '#4da6ff', bps);
}

async function loadPriceChart(symbol) {
  const data = await api('/prices?symbol=' + encodeURIComponent(symbol) + '&type=COMMODITY');
  const commodities = catalog?.commodities || overview?.commodities || me.market || [];
  const row = commodities.find(c => c.id === symbol);
  const live = data.priceMg || row?.priceMg || 0;
  const chart = historyToChart(data.history, 1000, live);
  const bps = data.changeBps ?? row?.changeBps ?? changeBpsFromHistory(data.history, live);
  const sub = formatSupplyDemand(data.supplySharePct != null ? data : row);
  const label = row?.name || symbol;
  renderLineChart('priceChart', 'price', chart.labels, chart.values, label, '#d4a843', bps, sub);
}

async function loadTokenChart(symbol) {
  const tokens = overview?.tokens || catalog?.tokens || [];
  const token = tokens.find(t => t.symbol === symbol);
  let hist = token?.history;
  let livePrice = token?.priceMg || 0;
  let bps = token?.changeBps;
  if (!hist) {
    const data = await api('/prices?symbol=' + encodeURIComponent(symbol) + '&type=TOKEN');
    hist = data.history;
    livePrice = data.priceMg || livePrice;
    bps = data.changeBps;
  }
  const owned = (me.tokens || []).find(t => t.symbol === symbol);
  if (owned) livePrice = owned.priceMg;
  const chart = historyToChart(hist, 1000, livePrice);
  renderLineChart('tokenChart', 'token', chart.labels, chart.values, symbol, '#e6b422',
    bps ?? changeBpsFromHistory(hist, livePrice));
}

async function loadShareChart(symbol) {
  const data = await api('/prices?symbol=' + encodeURIComponent(symbol) + '&type=SHARE');
  const companies = catalog?.companies || [];
  const row = companies.find(c => c.ticker === symbol) || (me.shares || []).find(s => s.ticker === symbol);
  const livePrice = data.priceMg || row?.sharePriceMg || row?.priceMg || 0;
  const chart = historyToChart(data.history, 1000, livePrice);
  renderLineChart('shareChart', 'share', chart.labels, chart.values, symbol, '#4da6ff',
    data.changeBps ?? changeBpsFromHistory(data.history, livePrice));
}

async function loadEmployment() {
  const status = document.getElementById('employmentStatus');
  const histBox = document.getElementById('salaryHistory');
  const sel = document.getElementById('employmentCompany');
  if (!status) return;
  const data = await api('/employment');
  if (sel) {
    sel.innerHTML = (data.companies || []).map(c => `<option value="${c.name}">${c.name}</option>`).join('')
      || '<option value="">Şirket yok</option>';
  }
  const cancelBtn = document.getElementById('employmentCancelAppBtn');
  const applyBtn = document.getElementById('employmentApplyBtn');
  const quitBtn = document.getElementById('employmentQuitBtn');
  if (data.employment) {
    const isCeo = data.employment.role === 'ceo';
    status.innerHTML = isCeo
      ? `<strong>${data.employment.company}</strong> — <span class="warn-text">CEO Ortak</span><br><span class="hint">Kazancın yarısı sizde, yarısı şirket kasasında. Kişisel mesleğinizle Meslek → Görev Al.</span>`
      : `<strong>${data.employment.company}</strong> — ${data.employment.role} · Maaş: ${data.employment.salary}<br><span class="hint">Şirket görevi: Meslek → Görev Al. Teslim edilen ürünler şirkete gider.</span>`;
    if (cancelBtn) cancelBtn.style.display = 'none';
    if (applyBtn) applyBtn.style.display = '';
    if (quitBtn) quitBtn.style.display = '';
  } else if (data.pendingApplication) {
    const p = data.pendingApplication;
    status.innerHTML = `<span class="warn-text">Bekleyen başvuru:</span> <strong>${p.company}</strong> — ${p.role} · ${p.salary}<br><span class="hint">Geri çekmek için aşağıdaki düğme veya <code>/is basvuru-iptal</code></span>`;
    if (cancelBtn) cancelBtn.style.display = '';
    if (applyBtn) applyBtn.style.display = 'none';
    if (quitBtn) quitBtn.style.display = 'none';
  } else {
    status.textContent = 'Şu an bir şirkette çalışmıyorsunuz.';
    if (cancelBtn) cancelBtn.style.display = 'none';
    if (applyBtn) applyBtn.style.display = '';
    if (quitBtn) quitBtn.style.display = '';
  }
  const rows = data.salaryHistory || [];
  if (histBox) {
    histBox.innerHTML = rows.length
      ? '<ul>' + rows.map(r => `<li>${new Date(r.paidAt).toLocaleString('tr')} — ${r.amount}${r.bonusMg ? ' (+' + (r.bonusMg/1000).toFixed(1) + ' bonus)' : ''}</li>`).join('') + '</ul>'
      : '<p class="hint">Henüz maaş yatmadı.</p>';
  }
}

function renderBulletins(data) {
  const box = document.getElementById('bulletinFeed');
  if (!box) return;
  const rows = data?.bulletins || [];
  if (!rows.length) {
    box.innerHTML = '<p class="hint">Henüz bülten yok. Soygun veya depo olayları burada yayınlanır.</p>';
    return;
  }
  box.innerHTML = rows.slice(0, 5).map(b => renderBulletinItem(b)).join('');
}

function renderBulletinItem(b) {
  const when = new Date(b.createdAt).toLocaleString('tr');
  const val = b.valueMg > 0 ? `<span class="tag warn">${b.value || ''}</span>` : '';
  const label = b.categoryLabel || b.category;
  return `<article class="list-item bulletin-item">
    <div class="row" style="justify-content:space-between;align-items:center">
      <strong>${label}</strong><span class="hint">${when}</span>
    </div>
    <div style="margin:6px 0;font-weight:600">${b.headline} ${val}</div>
    <div class="hint">${b.body}</div>
  </article>`;
}

let bulletinFilter = '';

async function loadBulletins() {
  try {
    const data = await api('/bulletins?limit=12');
    renderBulletins(data);
  } catch (_) {
    const box = document.getElementById('bulletinFeed');
    if (box) box.innerHTML = '<p class="hint">Bülten yüklenemedi.</p>';
  }
}

async function loadBulletinArchive() {
  const box = document.getElementById('bulletinArchive');
  if (!box) return;
  try {
    const q = bulletinFilter ? `?category=${bulletinFilter}&limit=80` : '?limit=80';
    const data = await api('/bulletins' + q);
    const rows = data?.bulletins || [];
    box.innerHTML = rows.length
      ? rows.map(renderBulletinItem).join('')
      : '<p class="hint">Bu kategoride bülten yok.</p>';
  } catch (_) {
    box.innerHTML = '<p class="hint">Bülten arşivi yüklenemedi.</p>';
  }
}

const MAP_COLORS = {
  bank: '#d4a843', reserve: '#ffd700', depot: '#6b8cce', prison: '#c44',
  company_vault: '#4da6ff', personal_vault: '#7ec850', black_depot: '#8844aa',
  camera: '#b366ff', player: '#ff4444'
};

let mapRefreshTimer;
let mapStaticData = null;
const mapReplay = { frames: [], index: 0, playing: false, timer: null, speed: 2 };

function stopRadarReplay() {
  mapReplay.playing = false;
  if (mapReplay.timer) {
    clearTimeout(mapReplay.timer);
    mapReplay.timer = null;
  }
  updateMapReplayUi();
}

function updateMapReplayUi() {
  const ctrl = document.getElementById('mapReplayControls');
  const status = document.getElementById('mapReplayStatus');
  if (ctrl) ctrl.style.display = mapReplay.frames.length ? 'flex' : 'none';
  if (status && mapReplay.frames.length) {
    const f = mapReplay.frames[mapReplay.index];
    const t = f ? new Date(f.recordedAt).toLocaleTimeString('tr') : '—';
    status.textContent = mapReplay.playing
      ? `Oynatılıyor ${mapReplay.index + 1}/${mapReplay.frames.length} · ${t}`
      : `${mapReplay.frames.length} kare · ${t}`;
  }
}

function blipsForReplayFrame(frames, frameIndex, trailMs = 50000) {
  if (!frames.length || frameIndex < 0) return [];
  const t = frames[Math.min(frameIndex, frames.length - 1)].recordedAt;
  const byPlayer = new Map();
  for (let i = 0; i <= frameIndex; i++) {
    const f = frames[i];
    if (t - f.recordedAt <= trailMs) {
      byPlayer.set(f.playerUuid || f.name, f);
    }
  }
  return [...byPlayer.values()];
}

function renderRadarTrails(frames, frameIndex, toX, toY, trailMs) {
  if (frameIndex < 1) return '';
  const t = frames[frameIndex].recordedAt;
  const byPlayer = new Map();
  for (let i = 0; i < frameIndex; i++) {
    const f = frames[i];
    if (t - f.recordedAt > trailMs) continue;
    const key = f.playerUuid || f.name;
    if (!byPlayer.has(key)) byPlayer.set(key, []);
    byPlayer.get(key).push(f);
  }
  let svg = '';
  byPlayer.forEach(points => {
    if (points.length < 2) return;
    const path = points.map((p, idx) => {
      const px = toX(p.x), py = toY(p.z);
      return `${idx === 0 ? 'M' : 'L'}${px},${py}`;
    }).join(' ');
    svg += `<path d="${path}" fill="none" stroke="${MAP_COLORS.camera}" stroke-width="1.2" opacity="0.35"/>`;
  });
  return svg;
}

function renderWorldMapCanvas(data, replayOpts) {
  const canvas = document.getElementById('worldMapCanvas');
  const legend = document.getElementById('worldMapLegend');
  if (!canvas) return;
  const pois = data?.pois || [];
  const players = data?.players || [];
  const remote = data?.remotePois || [];
  const focus = data?.focus || { x: 0, z: 0, radius: 96 };
  const replaying = replayOpts && replayOpts.frameIndex >= 0 && mapReplay.frames.length;
  const radar = replaying
    ? blipsForReplayFrame(mapReplay.frames, replayOpts.frameIndex)
    : (data?.radar || []);
  const hubPoints = [...pois, ...players, ...radar];
  const w = 520, h = 360;
  const minX = focus.x - focus.radius;
  const maxX = focus.x + focus.radius;
  const minZ = focus.z - focus.radius;
  const maxZ = focus.z + focus.radius;
  const scale = Math.min(w / (maxX - minX || 1), h / (maxZ - minZ || 1));
  const toX = x => (x - minX) * scale;
  const toY = z => (z - minZ) * scale;
  const cx = toX(focus.x);
  const cy = toY(focus.z);
  const radarR = focus.radius * scale;
  const sweep = replaying
    ? ((replayOpts.frameIndex * 4) % 360)
    : ((Date.now() / 40) % 360);
  const radarZone = `
    <g class="map-radar-zone">
      <circle cx="${cx}" cy="${cy}" r="${radarR}" fill="rgba(20,40,60,0.25)" stroke="#3a5f8a" stroke-width="1"/>
      <circle cx="${cx}" cy="${cy}" r="${radarR * 0.66}" fill="none" stroke="#3a5f8a" stroke-width="0.8" opacity="0.35"/>
      <circle cx="${cx}" cy="${cy}" r="${radarR * 0.33}" fill="none" stroke="#3a5f8a" stroke-width="0.6" opacity="0.25"/>
      <line x1="${cx}" y1="${cy}" x2="${cx + radarR * Math.cos(sweep * Math.PI / 180)}"
        y2="${cy + radarR * Math.sin(sweep * Math.PI / 180)}" stroke="#5ecfff" stroke-width="2" opacity="0.9"/>
    </g>`;
  const trailSvg = replaying
    ? renderRadarTrails(mapReplay.frames, replayOpts.frameIndex, toX, toY, 55000)
    : '';
  const poiSvg = pois.map(p => {
    const color = MAP_COLORS[p.type] || '#888';
    const px = toX(p.x), py = toY(p.z);
    return `<g class="map-poi"><circle cx="${px}" cy="${py}" r="7" fill="${color}" stroke="#111" stroke-width="1.5"/><title>${p.name}</title></g>`;
  }).join('');
  const playerSvg = players.map(p => {
    const px = toX(p.x), py = toY(p.z);
    return `<g class="map-player"><circle cx="${px}" cy="${py}" r="5" fill="${MAP_COLORS.player}" stroke="#fff" stroke-width="1"/><title>${p.name} canlı</title></g>`;
  }).join('');
  const radarSvg = radar.map(p => {
    const px = toX(p.x), py = toY(p.z);
    const cls = replaying ? 'map-radar-blip' : (Date.now() - (p.recordedAt || 0) < 120000 ? 'map-radar-blip' : 'map-radar-blip dim');
    return `<g class="${cls}"><circle cx="${px}" cy="${py}" r="6" fill="${MAP_COLORS.camera}" stroke="#e8d4ff" stroke-width="1.2"/><title>📹 ${p.name}</title></g>`;
  }).join('');
  if (!hubPoints.length && !remote.length) {
    canvas.innerHTML = '<p class="hint">Merkez Bankası kurulmamış. Oyunda /merkezbanka kur sonra yenileyin.</p>';
    return;
  }
  canvas.innerHTML = `<svg viewBox="0 0 ${w} ${h}" class="map-svg">${radarZone}${trailSvg}${poiSvg}${radarSvg}${playerSvg}</svg>`;
  let camHint = document.getElementById('mapCameraHint');
  if (!camHint) {
    camHint = document.createElement('p');
    camHint.id = 'mapCameraHint';
    camHint.className = 'hint';
    canvas.parentElement.insertBefore(camHint, canvas);
  }
  if (replaying) {
    camHint.textContent = '📹 Radar kaydı oynatılıyor — mor iz = hareket yolu, tarama çizgisi gece kaydını simüle eder.';
  } else {
    const radarNote = radar.length ? ` · ${radar.length} tespit` : '';
    camHint.textContent = data.cameraRecording
      ? `📹 Canlı gece kaydı${radarNote}. ▶ ile gece radar videosunu oynatabilirsiniz.`
      : (mapReplay.frames.length
        ? `📹 Gece radar kaydı hazır (${mapReplay.frames.length} kare) — ▶ Oynat.`
        : 'Gündüz — radar gece vardiyasında kayıt yapar.');
  }
  camHint.style.color = (data.cameraRecording || radar.length || replaying) ? '#c9a0ff' : '';
  if (legend) {
    legend.innerHTML =
      `<li class="hint" style="list-style:none">Merkez: X:${focus.x} Z:${focus.z} (±${focus.radius} blok)</li>` +
      pois.map(p =>
        `<li><span class="map-dot" style="background:${MAP_COLORS[p.type] || '#888'}"></span>${p.name} — X:${p.x} Z:${p.z}</li>`
      ).join('') +
      radar.map(p =>
        `<li><span class="map-dot" style="background:${MAP_COLORS.camera}"></span>📹 ${p.name} — X:${p.x} Z:${p.z}</li>`
      ).join('') +
      players.map(p =>
        `<li><span class="map-dot" style="background:${MAP_COLORS.player}"></span>${p.name} (canlı) — X:${Math.round(p.x)} Z:${Math.round(p.z)}</li>`
      ).join('') +
      remote.map(p =>
        `<li><span class="map-dot" style="background:${MAP_COLORS.prison}"></span>${p.name} — X:${p.x} (harita dışı)</li>`
      ).join('');
  }
  updateMapReplayUi();
}

function tickRadarReplay() {
  if (!mapReplay.playing || !mapStaticData || !mapReplay.frames.length) return;
  renderWorldMapCanvas(mapStaticData, { frameIndex: mapReplay.index });
  if (mapReplay.index >= mapReplay.frames.length - 1) {
    stopRadarReplay();
    return;
  }
  const cur = mapReplay.frames[mapReplay.index];
  const next = mapReplay.frames[mapReplay.index + 1];
  const speed = +(document.getElementById('mapReplaySpeed')?.value || mapReplay.speed) || 2;
  const delay = Math.max(35, Math.min(600, (next.recordedAt - cur.recordedAt) / speed));
  mapReplay.index++;
  mapReplay.timer = setTimeout(tickRadarReplay, delay);
}

function startRadarReplay() {
  if (!mapReplay.frames.length || !mapStaticData) return;
  stopRadarReplay();
  mapReplay.playing = true;
  mapReplay.index = 0;
  tickRadarReplay();
}

async function loadWorldMap() {
  const canvas = document.getElementById('worldMapCanvas');
  if (!canvas) return;
  const track = document.getElementById('mapTrackPlayer')?.value?.trim() || '';
  const q = track ? `?track=${encodeURIComponent(track)}` : '';
  try {
    const data = await api('/world/map' + q);
    if (data.error) {
      canvas.innerHTML = `<p class="hint">Harita: ${data.message || data.error}</p>`;
      return;
    }
    mapStaticData = data;
    mapReplay.frames = data.radarReplay || [];
    if (!mapReplay.playing) {
      renderWorldMapCanvas(data, null);
    }
  } catch (e) {
    canvas.innerHTML = '<p class="hint">Harita yüklenemedi (oturum veya sunucu bağlantısı).</p>';
  }
}

function startMapLiveRefresh() {
  stopMapLiveRefresh();
  mapRefreshTimer = setInterval(() => {
    if (!mapReplay.playing) loadWorldMap();
  }, 3000);
}

function stopMapLiveRefresh() {
  if (mapRefreshTimer) {
    clearInterval(mapRefreshTimer);
    mapRefreshTimer = null;
  }
}

document.getElementById('mapRefreshBtn')?.addEventListener('click', () => {
  stopRadarReplay();
  loadWorldMap();
});
document.getElementById('mapReplayPlayBtn')?.addEventListener('click', startRadarReplay);
document.getElementById('mapReplayStopBtn')?.addEventListener('click', () => {
  stopRadarReplay();
  if (mapStaticData) renderWorldMapCanvas(mapStaticData, null);
});
document.getElementById('mapReplaySpeed')?.addEventListener('input', e => { mapReplay.speed = +e.target.value; });

async function refresh() {
  [me, catalog, overview] = await Promise.all([api('/me'), api('/catalog'), api('/charts/overview')]);
  if (me.error || !me.name) {
    showToast(me.message || me.error || 'Giris gerekli', false);
    show('login');
    return;
  }
  setGoldFactor(me.goldFactor);
  show('app');
  const adminLink = document.getElementById('adminLink');
  if (adminLink) adminLink.classList.toggle('hidden', !me.op);
  renderStats();
  renderBankAccounts();
  renderHoldings();
  renderLoan();
  renderQuest();
  renderAppeals();
  renderJustice();
  renderCert();
  populateSelects();
  await renderLeveragePositions();
  await renderCharts();
  await loadEmployment();
  await loadBulletins();
  startChartLiveRefresh();
}

document.getElementById('setupPasswordBtn')?.addEventListener('click', async () => {
  const err = document.getElementById('loginError');
  const username = document.getElementById('username')?.value?.trim();
  const password = document.getElementById('password')?.value || '';
  if (!username || password.length < 4) {
    if (err) err.textContent = 'Kullanıcı adı ve en az 4 karakterlik şifre girin.';
    return;
  }
  const data = await api('/setup-password', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  });
  if (data.error || !data.success) {
    if (err) err.textContent = data.message || 'Şifre kaydedilemedi.';
    showToast(data.message || 'Şifre kaydedilemedi.', false);
    return;
  }
  if (err) err.textContent = '';
  showToast(data.message || 'Şifre kaydedildi.', true);
});

document.getElementById('loginBtn').onclick = async () => {
  const err = document.getElementById('loginError');
  const data = await api('/login', {
    method: 'POST',
    body: JSON.stringify({ username: document.getElementById('username').value, password: document.getElementById('password').value })
  });
  if (data.error) {
    if (err) err.textContent = data.message || 'Giriş başarısız';
    return;
  }
  if (err) err.textContent = '';
  token = data.token;
  localStorage.setItem('mceconomy_token', token);
  if (data.op) document.getElementById('adminLink').classList.remove('hidden');
  await refresh();
};

document.getElementById('logoutBtn').onclick = logout;
document.getElementById('refreshBtn').onclick = refresh;
document.getElementById('chartSymbol')?.addEventListener('change', e => loadPriceChart(e.target.value));
document.getElementById('tokenChartSelect')?.addEventListener('change', e => loadTokenChart(e.target.value));
document.getElementById('shareChartSelect')?.addEventListener('change', e => loadShareChart(e.target.value));
document.getElementById('levChartSelect')?.addEventListener('change', e => {
  if (e.target.value) loadLeverageChart(+e.target.value);
});

document.getElementById('tokenTradeSelect')?.addEventListener('change', e => {
  const sym = e.target.value;
  const owned = (me?.tokens || []).find(t => t.symbol === sym);
  const qty = document.getElementById('tokenTradeQty');
  if (qty && owned) { qty.max = owned.amount; qty.value = Math.min(+qty.value || 1, owned.amount); }
});

setupNav('sidebarNav', 'page-');

document.getElementById('payBtn').onclick = () => doAction('/actions/pay', {
  target: document.getElementById('payTarget').value, mc: +document.getElementById('payGrams').value
}, refresh);

function walletAccountType() {
  return document.getElementById('walletAccountType')?.value || 'checking';
}

function renderBankAccounts() {
  const checkingLine = document.getElementById('checkingBalanceLine');
  const termLine = document.getElementById('termBalanceLine');
  const interestLine = document.getElementById('termInterestLine');
  const maturityLine = document.getElementById('termMaturityLine');
  const openCheckingBtn = document.getElementById('openCheckingBtn');
  const openTermBtn = document.getElementById('openTermBtn');
  if (checkingLine) {
    checkingLine.textContent = me.hasChecking
      ? `Bakiye: ${me.checking || me.bank}`
      : 'Hesap yok — vadesiz hesap açın';
  }
  if (openCheckingBtn) openCheckingBtn.classList.toggle('hidden', !!me.hasChecking);
  if (termLine) {
    if (me.hasTerm) {
      termLine.textContent = `Bakiye: ${me.termBalance || formatMg(me.termBalanceMg || 0)}`;
      if (interestLine) {
        const pct = me.termInterestTotalPct != null ? me.termInterestTotalPct
          : (me.termInterestRate != null ? Math.round(me.termInterestRate * 100) : '—');
        const sec = me.termInterestIntervalSec || 60;
        interestLine.textContent = `7 gün toplam getiri: %${pct} · her ${sec} sn faiz işlenir`;
      }
      if (maturityLine) {
        maturityLine.textContent = me.termMatured
          ? 'Vade doldu — çekim yapılabilir'
          : (me.termMaturityDaysLeft != null
            ? `Kalan vade: ${me.termMaturityDaysLeft} gün`
            : 'Vade dolana kadar çekim yok');
      }
    } else {
      termLine.textContent = 'Hesap yok — vadeli hesap açın';
      if (interestLine) interestLine.textContent = '';
      if (maturityLine) maturityLine.textContent = '';
    }
  }
  if (openTermBtn) openTermBtn.classList.toggle('hidden', !!me.hasTerm);
}

document.getElementById('walletToBankBtn').onclick = () => doAction('/actions/bank/wallet-deposit', {
  mc: +document.getElementById('walletMoveGrams').value,
  account: walletAccountType()
}, refresh);

document.getElementById('bankToWalletBtn').onclick = () => doAction('/actions/bank/wallet-withdraw', {
  mc: +document.getElementById('walletMoveGrams').value,
  account: walletAccountType()
}, refresh);

document.getElementById('openCheckingBtn').onclick = () => doAction('/actions/bank/open-checking', {}, refresh);
document.getElementById('openTermBtn').onclick = () => doAction('/actions/bank/open-term', {}, refresh);

document.getElementById('bankTransferBtn').onclick = () => doAction('/actions/bank/transfer', {
  target: document.getElementById('bankTransferTarget').value, mc: +document.getElementById('bankTransferGrams').value
}, refresh);

document.getElementById('depositIngotsBtn').onclick = () => doAction('/actions/bank/deposit-ingots', {
  ingots: +document.getElementById('ingotCount').value
}, refresh);

document.getElementById('withdrawIngotsBtn').onclick = () => doAction('/actions/bank/withdraw-ingots', {
  ingots: +document.getElementById('ingotCount').value
}, refresh);

document.getElementById('marketBuyBtn').onclick = () => doAction('/actions/market/buy', {
  commodity: document.getElementById('marketBuySelect').value, quantity: +document.getElementById('marketBuyQty').value
}, refresh);

document.getElementById('marketSellBtn').onclick = () => doAction('/actions/market/sell', {
  commodity: document.getElementById('marketSellSelect').value, quantity: +document.getElementById('marketSellQty').value
}, refresh);

document.getElementById('marketSellAllBtn').onclick = () => {
  const commodity = document.getElementById('marketSellSelect').value;
  if (!commodity) { showToast('Emtia secin', false); return; }
  doAction('/actions/market/sell-all', { commodity }, refresh);
};

document.getElementById('loanTakeBtn').onclick = () => doAction('/actions/loan/take', {
  mc: +document.getElementById('loanGrams').value
}, refresh);

document.getElementById('loanPayBtn').onclick = () => doAction('/actions/loan/pay', {}, refresh);

document.getElementById('jobSetBtn').onclick = () => doAction('/actions/job/set', {
  job: document.getElementById('jobSelect').value
}, refresh);

document.getElementById('questAssignBtn').onclick = () => doAction('/actions/quest/assign', {}, refresh);
document.getElementById('questCompleteBtn').onclick = () => doAction('/actions/quest/complete', {}, refresh);
document.getElementById('questCancelBtn').onclick = () => doAction('/actions/quest/cancel', {}, refresh);
document.getElementById('jobResignBtn').onclick = () => doAction('/actions/job/resign', {}, refresh);

function syncEmploymentRoleUi() {
  const role = document.getElementById('employmentRole')?.value;
  const salaryInput = document.getElementById('employmentSalary');
  if (!salaryInput) return;
  if (role === 'ceo') {
    salaryInput.value = '0';
    salaryInput.disabled = true;
  } else {
    salaryInput.disabled = false;
    if (+salaryInput.value === 0) salaryInput.value = '50000';
  }
}
document.getElementById('employmentRole')?.addEventListener('change', syncEmploymentRoleUi);
syncEmploymentRoleUi();

document.getElementById('employmentApplyBtn')?.addEventListener('click', () => {
  const role = document.getElementById('employmentRole')?.value;
  doAction('/actions/employment/apply', {
    company: document.getElementById('employmentCompany')?.value,
    role,
    salaryMg: role === 'ceo' ? 0 : +document.getElementById('employmentSalary')?.value
  }, () => { loadEmployment(); refresh(); });
});

document.getElementById('employmentCancelAppBtn')?.addEventListener('click', () => doAction('/actions/employment/cancel-application', {}, () => { loadEmployment(); refresh(); }));

document.getElementById('employmentQuitBtn')?.addEventListener('click', () => doAction('/actions/employment/quit', {}, () => { loadEmployment(); refresh(); }));

document.getElementById('companyCreateBtn').onclick = () => doAction('/actions/company/create', {
  name: document.getElementById('companyName').value
}, refresh);

document.getElementById('shareBuyBtn').onclick = () => doAction('/actions/shares/buy', {
  company: document.getElementById('shareCompanySelect').value, amount: +document.getElementById('shareQty').value
}, refresh);

document.getElementById('shareSellBtn').onclick = () => doAction('/actions/shares/sell', {
  company: document.getElementById('shareCompanySelect').value, amount: +document.getElementById('shareQty').value
}, refresh);

document.getElementById('tokenBuyBtn').onclick = () => doAction('/actions/exchange/token/buy', {
  symbol: document.getElementById('tokenTradeSelect').value, amount: +document.getElementById('tokenTradeQty').value
}, refresh);

document.getElementById('tokenSellBtn').onclick = () => doAction('/actions/exchange/token/sell', {
  symbol: document.getElementById('tokenTradeSelect').value, amount: +document.getElementById('tokenTradeQty').value
}, refresh);

document.getElementById('coinCreateBtn').onclick = () => doAction('/actions/exchange/token/create', {
  symbol: document.getElementById('coinSymbol').value,
  name: document.getElementById('coinName').value,
  supply: +document.getElementById('coinSupply').value,
  mc: +document.getElementById('coinPriceMg').value
}, refresh);

document.getElementById('listCompanyBtn').onclick = () => doAction('/actions/exchange/list', {
  company: document.getElementById('listCompanySelect').value,
  ticker: document.getElementById('listTicker').value
}, refresh);

document.getElementById('delistCompanyBtn').onclick = () => doAction('/actions/exchange/delist', {
  company: document.getElementById('delistCompanySelect').value
}, refresh);

document.getElementById('levOpenBtn').onclick = () => doAction('/actions/exchange/leverage/open', {
  symbol: document.getElementById('levSymbol').value,
  side: document.getElementById('levSide').value,
  leverage: +document.getElementById('levLeverage').value,
  mc: +document.getElementById('levMargin').value
}, refresh);

document.getElementById('vaultGoBtn').onclick = () => doAction('/actions/vault/teleport', {}, refresh);
document.getElementById('vaultBackBtn').onclick = () => doAction('/actions/vault/back', {}, refresh);
document.getElementById('heistStartBtn').onclick = () => doAction('/actions/heist/start', {}, refresh);

document.getElementById('certBuyBtn').onclick = () => doAction('/actions/private-bank/certify', {}, refresh);
document.getElementById('pbankOpenBtn').onclick = () => doAction('/actions/private-bank/open', {
  name: document.getElementById('pbankName').value
}, refresh);

document.getElementById('pbankDepositBtn').onclick = () => doAction('/actions/private-bank/deposit', {
  bank: document.getElementById('pbankSelect').value, mc: +document.getElementById('pbankGrams').value
}, refresh);

document.getElementById('pbankWithdrawBtn').onclick = () => doAction('/actions/private-bank/withdraw', {
  bank: document.getElementById('pbankSelect').value, mc: +document.getElementById('pbankGrams').value
}, refresh);

document.getElementById('illegalBuyBtn').onclick = () => doAction('/actions/blackmarket/buy', {
  good: document.getElementById('illegalSelect').value, quantity: +document.getElementById('illegalQty').value
}, refresh);

document.getElementById('illegalSellBtn').onclick = () => doAction('/actions/blackmarket/sell', {
  good: document.getElementById('illegalSelect').value, quantity: +document.getElementById('illegalQty').value
}, refresh);

document.getElementById('launderBtn').onclick = () => doAction('/actions/launder', {
  mc: +document.getElementById('launderGrams').value
}, refresh);

document.getElementById('tokenSellAllBtn').onclick = () => doAction('/actions/exchange/token/sell-all', {}, refresh);
document.getElementById('shareSellAllBtn').onclick = () => doAction('/actions/shares/sell-all', {}, refresh);

function casinoResult(data) {
  const el = document.getElementById('casinoResult');
  if (el) el.innerHTML = `<div class="${data.success ? 'pnl-up' : 'pnl-down'}">${data.message}</div>`;
  refresh();
}
document.getElementById('cfBtn').onclick = () => doAction('/actions/casino/play', {
  game: 'coinflip', mc: +document.getElementById('cfBet').value, choice: document.getElementById('cfChoice').value
}, casinoResult);
document.getElementById('diceBtn').onclick = () => doAction('/actions/casino/play', {
  game: 'dice', mc: +document.getElementById('diceBet').value, choice: document.getElementById('diceChoice').value
}, casinoResult);
document.getElementById('slotBtn').onclick = () => doAction('/actions/casino/play', {
  game: 'slot', mc: +document.getElementById('slotBet').value, choice: ''
}, casinoResult);

async function loadMacroPanel() {
  const m = me || await api('/me');
  const el = document.getElementById('macroPanel');
  if (!el) return;
  const goldFactor = m?.goldFactor ?? '—';
  const inflationPct = ((m?.inflationRate ?? 0) * 100).toFixed(2);
  const budget = m?.municipalBudget ?? '—';
  const fiat = m?.fiatStrength != null ? m.fiatStrength.toFixed(2) : null;
  el.innerHTML = `
    ${fiat != null ? `<p>Fiat gücü ($): <strong>${fiat}</strong></p>` : ''}
    <p>Altın faktörü: <strong>${goldFactor}</strong></p>
    <p>Enflasyon: <strong>${inflationPct}%</strong></p>
    <p>Belediye bütçesi: <strong>${budget}</strong></p>
    <p class="hint">Detay: oyunda <code>/para durum</code></p>`;
}
async function loadInsurancePanel() {
  const d = await api('/insurance');
  const el = document.getElementById('insurancePanel');
  if (!el) return;
  const rows = (d?.policies || []).map(p =>
    `<div class="list-row">${p.type ?? '—'} — ${p.premium ?? '—'} ${p.active ? '✓' : '✗'}</div>`).join('');
  el.innerHTML = `
    ${rows || '<p class="hint">Aktif poliçe yok.</p>'}
    <div class="form-row" style="margin-top:12px">
      <button class="btn btn-gold btn-sm" id="insPersonalSub">Kişisel al</button>
      <button class="btn btn-ghost btn-sm" id="insPersonalCancel">Kişisel iptal</button>
    </div>
    <label>Şirket adı (sahip olduğunuz)</label>
    <input id="insCompanyName" placeholder="Şirket">
    <div class="form-row">
      <button class="btn btn-gold btn-sm" id="insCompanySub">Şirket poliçesi al</button>
      <button class="btn btn-ghost btn-sm" id="insCompanyCancel">Şirket iptal</button>
    </div>`;
  document.getElementById('insPersonalSub')?.addEventListener('click', () =>
    doAction('/actions/insurance/personal/subscribe', {}, loadInsurancePanel));
  document.getElementById('insPersonalCancel')?.addEventListener('click', () =>
    doAction('/actions/insurance/personal/cancel', {}, loadInsurancePanel));
  document.getElementById('insCompanySub')?.addEventListener('click', () =>
    doAction('/actions/insurance/company/subscribe', { company: document.getElementById('insCompanyName').value }, loadInsurancePanel));
  document.getElementById('insCompanyCancel')?.addEventListener('click', () =>
    doAction('/actions/insurance/company/cancel', { company: document.getElementById('insCompanyName').value }, loadInsurancePanel));
}
async function loadTradePanel() {
  const d = await api('/trades');
  const el = document.getElementById('tradePanel');
  if (!el) return;
  const hist = (d?.trades || []).map(t =>
    `<div class="list-row">#${t.id ?? '?'} ${t.initiator ?? '—'} ↔ ${t.partner ?? '—'} — ${t.status ?? '—'}
      <button class="btn btn-ghost btn-sm trade-dispute-btn" data-id="${t.id}">Şikayet</button></div>`).join('');
  el.innerHTML = `
    <p class="hint">Aktif takas için oyunda olmalısınız.</p>
    <label>Oyuncu adı</label><input id="tradePartner" placeholder="Hedef">
    <button class="btn btn-gold btn-sm" id="tradeInviteBtn">Davet gönder</button>
    <button class="btn btn-accent btn-sm" id="tradeAcceptBtn">Daveti kabul et</button>
    <hr style="border-color:var(--border);margin:16px 0">
    <h4 style="margin:0 0 8px;color:var(--gold)">Geçmiş</h4>
    ${hist || '<p class="hint">Takas yok</p>'}`;
  document.getElementById('tradeInviteBtn')?.addEventListener('click', () =>
    doAction('/actions/trade/invite', { target: document.getElementById('tradePartner').value }, loadTradePanel));
  document.getElementById('tradeAcceptBtn')?.addEventListener('click', () =>
    doAction('/actions/trade/accept', {}, loadTradePanel));
  el.querySelectorAll('.trade-dispute-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const reason = prompt('Şikayet sebebi:') || '';
      doAction('/actions/trade/dispute', { tradeId: +btn.dataset.id, reason }, loadTradePanel);
    });
  });
}
async function loadGuildPanel() {
  const d = await api('/guild');
  const el = document.getElementById('guildPanel');
  if (!el) return;
  const status = d?.name
    ? `<p><strong>${d.name}</strong> — Kasa: ${d.treasury ?? '—'}${d.strikeActive ? ' <span class="badge badge-warn">Grev</span>' : ''}</p>`
    : '<p class="hint">Loncada değilsiniz.</p>';
  el.innerHTML = `
    ${status}
    <label>Lonca adı</label><input id="guildNameInput" placeholder="Lonca">
    <div class="form-row">
      <button class="btn btn-gold btn-sm" id="guildCreateBtn">Kur</button>
      <button class="btn btn-accent btn-sm" id="guildJoinBtn">Katıl</button>
      <button class="btn btn-ghost btn-sm" id="guildLeaveBtn">Ayrıl</button>
    </div>
    <label>Tutar ($)</label><input id="guildMc" type="number" min="1" value="100">
    <div class="form-row">
      <button class="btn btn-gold btn-sm" id="guildDepositBtn">Kasaya yatır</button>
      <button class="btn btn-ghost btn-sm" id="guildWithdrawBtn">Kasadan çek (lider)</button>
    </div>
    <label>Grev süresi (dk)</label><input id="guildStrikeMin" type="number" min="1" max="120" value="30">
    <button class="btn btn-danger btn-sm" id="guildStrikeBtn">Grev başlat</button>
    <label>Pazarlık mesajı</label><textarea id="guildBargainMsg" placeholder="Talep..."></textarea>
    <button class="btn btn-ghost btn-sm" id="guildBargainBtn">Mesaj gönder</button>`;
  document.getElementById('guildCreateBtn')?.addEventListener('click', () =>
    doAction('/actions/guild/create', { name: document.getElementById('guildNameInput').value }, loadGuildPanel));
  document.getElementById('guildJoinBtn')?.addEventListener('click', () =>
    doAction('/actions/guild/join', { name: document.getElementById('guildNameInput').value }, loadGuildPanel));
  document.getElementById('guildLeaveBtn')?.addEventListener('click', () =>
    doAction('/actions/guild/leave', {}, loadGuildPanel));
  document.getElementById('guildDepositBtn')?.addEventListener('click', () =>
    doAction('/actions/guild/deposit', { mc: +document.getElementById('guildMc').value }, loadGuildPanel));
  document.getElementById('guildWithdrawBtn')?.addEventListener('click', () =>
    doAction('/actions/guild/withdraw', { mc: +document.getElementById('guildMc').value }, loadGuildPanel));
  document.getElementById('guildStrikeBtn')?.addEventListener('click', () =>
    doAction('/actions/guild/strike', { minutes: +document.getElementById('guildStrikeMin').value }, loadGuildPanel));
  document.getElementById('guildBargainBtn')?.addEventListener('click', () =>
    doAction('/actions/guild/bargain', { message: document.getElementById('guildBargainMsg').value }, loadGuildPanel));
}
async function loadMunicipalPanel() {
  const d = await api('/municipal');
  const el = document.getElementById('municipalPanel');
  if (!el) return;
  const cands = (d?.candidates || []).map(c =>
    `<option value="${c.name ?? ''}">${c.name ?? '—'}</option>`).join('');
  el.innerHTML = `
    <p>Başkan: <strong>${d?.mayorName ?? '—'}</strong></p>
    <p>Bütçe: ${d?.budget ?? '—'}</p>
    <button class="btn btn-gold btn-sm" id="munCandidateBtn">Aday ol</button>
    <label>Aday seç</label>
    <select id="munVoteSelect"><option value="">—</option>${cands}</select>
    <button class="btn btn-accent btn-sm" id="munVoteBtn">Oy ver</button>
    <hr style="border-color:var(--border);margin:16px 0">
    <p class="hint">Bütçe harcaması yalnızca başkan (oyunda).</p>
    <label>Tutar ($)</label><input id="munSpendMc" type="number" min="1" value="1000">
    <label>Açıklama</label><input id="munSpendPurpose" placeholder="Proje / etkinlik">
    <button class="btn btn-gold btn-sm" id="munSpendBtn">Harcama yap</button>`;
  document.getElementById('munCandidateBtn')?.addEventListener('click', () =>
    doAction('/actions/municipal/candidate', {}, loadMunicipalPanel));
  document.getElementById('munVoteBtn')?.addEventListener('click', () =>
    doAction('/actions/municipal/vote', { candidate: document.getElementById('munVoteSelect').value }, loadMunicipalPanel));
  document.getElementById('munSpendBtn')?.addEventListener('click', () =>
    doAction('/actions/municipal/spend', {
      mc: +document.getElementById('munSpendMc').value,
      purpose: document.getElementById('munSpendPurpose').value
    }, loadMunicipalPanel));
}
async function loadPropertyPanel() {
  const el = document.getElementById('propertyPanel');
  if (!el) return;
  const m = me?.properties ? me : await api('/me');
  const props = m?.properties || [];
  if (!props.length) {
    el.innerHTML = '<p class="hint">Ev yok. Oyunda: <code>/ev al cottage|house|villa</code></p>';
    return;
  }
  el.innerHTML = props.map(p =>
    `<div class="list-row"><strong>#${p.id}</strong> ${p.tier} — X:${p.x} Z:${p.z} · TP: <code>/ev tp ${p.id}</code></div>`
  ).join('');
}
async function loadVehiclePanel() {
  const el = document.getElementById('vehiclePanel');
  if (!el) return;
  const m = me?.vehicles ? me : await api('/me');
  const cars = m?.vehicles || [];
  if (!cars.length) {
    el.innerHTML = '<p class="hint">Garaj bos. <code>/araba al sedan|suv</code> → <code>/araba cikar &lt;id&gt;</code></p>';
    return;
  }
  el.innerHTML = cars.map(v =>
    `<div class="list-row"><strong>#${v.id}</strong> ${v.model} — yakit ${Math.round(v.fuel)}% ${v.spawned ? '(yolda)' : '(garaj)'} · <code>/araba cikar ${v.id}</code></div>`
  ).join('');
}
async function loadGovernmentPanel() {
  const m = me || await api('/me');
  const gov = await api('/government');
  const el = document.getElementById('governmentPanel');
  if (!el) return;
  const isMinister = !!(gov?.isMinister || m?.isEconomyMinister);
  if (!isMinister) {
    el.innerHTML = '<p class="hint">Başvuru: <code>/ekonomi bakan basvur &lt;sebep&gt;</code></p>';
    return;
  }
  const requiredVotes = gov.requiredYesVotes ?? 1;
  const pending = (gov.pendingDecrees || []).map(d =>
    `<div class="list-row">#${d.id ?? '?'} <strong>${d.type ?? '—'}</strong> — ${d.yesVotes ?? 0}/${requiredVotes} onay
      <button class="btn btn-gold btn-sm decree-yes" data-id="${d.id}">Evet</button>
      <button class="btn btn-ghost btn-sm decree-no" data-id="${d.id}">Hayır</button></div>`).join('');
  const recent = (gov.recentDecrees || []).map(d =>
    `<div class="list-row">#${d.id ?? '?'} ${d.type ?? '—'} — ${d.status ?? '—'}</div>`).join('');
  el.innerHTML = `
    <p class="pnl-up">Kabine oylaması: ${gov.ministerCount ?? 0} bakan, ${requiredVotes} evet gerekir.</p>
    <label>Emir tipi</label>
    <select id="decreeType">
      <option value="interest">interest (faiz)</option>
      <option value="tax">tax (vergi)</option>
      <option value="bulletin">bulletin (bülten)</option>
      <option value="market_multiplier">market_multiplier</option>
    </select>
    <label>JSON payload</label>
    <textarea id="decreePayload" placeholder='{"baseRate":0.05}'></textarea>
    <button class="btn btn-gold btn-sm" id="decreeProposeBtn">Emir teklif et</button>
    <h4 style="margin:16px 0 8px;color:var(--gold)">Bekleyen</h4>
    ${pending || '<p class="hint">Bekleyen emir yok</p>'}
    <h4 style="margin:16px 0 8px;color:var(--gold)">Geçmiş</h4>
    ${recent || '<p class="hint">—</p>'}`;
  document.getElementById('decreeProposeBtn')?.addEventListener('click', () =>
    doAction('/actions/government/decree/propose', {
      type: document.getElementById('decreeType').value,
      payloadJson: document.getElementById('decreePayload').value
    }, loadGovernmentPanel));
  el.querySelectorAll('.decree-yes').forEach(btn => btn.addEventListener('click', () =>
    doAction('/actions/government/decree/vote', { decreeId: +btn.dataset.id, yes: true }, loadGovernmentPanel)));
  el.querySelectorAll('.decree-no').forEach(btn => btn.addEventListener('click', () =>
    doAction('/actions/government/decree/vote', { decreeId: +btn.dataset.id, yes: false }, loadGovernmentPanel)));
}

function setupMobileNav() {
  const sidebar = document.getElementById('sidebarNav');
  const backdrop = document.getElementById('sidebarBackdrop');
  const burger = document.getElementById('navHamburger');
  const close = () => {
    sidebar?.classList.remove('open');
    backdrop?.classList.remove('visible');
    backdrop?.classList.add('hidden');
  };
  burger?.addEventListener('click', () => {
    sidebar?.classList.toggle('open');
    const open = sidebar?.classList.contains('open');
    backdrop?.classList.toggle('hidden', !open);
    backdrop?.classList.toggle('visible', !!open);
  });
  backdrop?.addEventListener('click', close);
  document.querySelectorAll('#sidebarNav .nav-item[data-page]').forEach(btn => {
    btn.addEventListener('click', () => { if (window.innerWidth <= 900) close(); });
  });
}
setupMobileNav();

document.querySelectorAll('#sidebarNav .nav-item[data-page]').forEach(btn => {
  btn.addEventListener('click', () => {
    const page = btn.dataset.page;
    if (page === 'inventory') loadInventory();
    else if (page === 'employees') loadEmployees();
    else if (page === 'company') loadCompanyStash();
    else if (page === 'docs') renderDocs();
    else if (page === 'charts') { loadPortfolioLive(); startChartLiveRefresh(); }
    else if (page === 'exchange') { renderLeveragePositions(); }
    else if (page === 'map') { loadWorldMap(); startMapLiveRefresh(); }
    else if (page === 'bulletins') loadBulletinArchive();
    else if (page === 'macro') loadMacroPanel();
    else if (page === 'insurance') loadInsurancePanel();
    else if (page === 'trade') loadTradePanel();
    else if (page === 'guild') loadGuildPanel();
    else if (page === 'municipal') loadMunicipalPanel();
    else if (page === 'property') loadPropertyPanel();
    else if (page === 'vehicle') loadVehiclePanel();
    else if (page === 'government') loadGovernmentPanel();
    else { stopChartLiveRefresh(); stopMapLiveRefresh(); }
  });
});

document.querySelectorAll('.bulletin-filter').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.bulletin-filter').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    bulletinFilter = btn.dataset.category || '';
    loadBulletinArchive();
  });
});

document.getElementById('appealSubmitBtn').onclick = () => {
  const alertId = document.getElementById('appealAlertId').value;
  doAction('/actions/appeal/submit', {
    subject: document.getElementById('appealSubject').value,
    message: document.getElementById('appealMessage').value,
    alertId: alertId ? +alertId : undefined
  }, refresh);
};

document.getElementById('complaintSubmitBtn')?.addEventListener('click', () => {
  doAction('/actions/justice/complaint', {
    target: document.getElementById('complaintTarget').value,
    category: document.getElementById('complaintCategory').value,
    subject: document.getElementById('complaintSubject').value,
    message: document.getElementById('complaintMessage').value
  }, refresh);
});

document.getElementById('tipSubmitBtn')?.addEventListener('click', () => {
  doAction('/actions/justice/tipoff', {
    target: document.getElementById('tipTarget').value,
    category: document.getElementById('tipCategory').value,
    message: document.getElementById('tipMessage').value
  }, refresh);
});

if (token) refresh(); else show('login');
