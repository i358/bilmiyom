let catalog = null;

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
      <strong>#${a.id}</strong> ${a.playerName || a.playerUuid.slice(0,8)}
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
  document.getElementById('playersTable').innerHTML = (data.players || []).map(p => {
    const status = p.blacklisted ? '<span class="badge badge-bad">Kara Liste</span>'
      : p.frozen ? '<span class="badge badge-warn">Dondurulmuş</span>'
      : '<span class="badge badge-ok">Normal</span>';
    const dot = p.online ? 'on' : 'off';
    return `<tr>
      <td><span class="online-dot ${dot}"></span>${p.name}${p.mbOfficial ? ' 🎖' : ''}</td>
      <td>${formatMg(p.walletMg)}</td>
      <td>${formatMg(p.bankMg)}</td>
      <td>${formatMg(p.dirtyMg)}</td>
      <td>${p.creditScore}</td>
      <td>${status}</td>
      <td>
        <button class="btn btn-sm btn-ghost" onclick="fillMasak('${p.name}')">MASAK</button>
      </td>
    </tr>`;
  }).join('');
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
document.querySelectorAll('#adminNav .nav-item[data-page]').forEach(btn => {
  btn.addEventListener('click', () => {
    if (btn.dataset.page === 'cameras') loadCameraLogs();
  });
});

if (token) loadAdmin(); else show('login');
