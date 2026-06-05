let token = localStorage.getItem('mceconomy_token');
let goldFactor = 1;

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (token) headers['Authorization'] = 'Bearer ' + token;
  const res = await fetch('/api' + path, { ...options, headers });
  let data;
  try {
    data = await res.json();
  } catch {
    return { success: false, error: 'invalid_json', message: 'Sunucu yaniti okunamadi (' + res.status + ')' };
  }
  if (!res.ok && data.error == null && data.message == null) {
    data.error = 'http_' + res.status;
    data.message = res.status === 401 ? 'Oturum suresi doldu — yeniden giris yapin.' : ('HTTP ' + res.status);
    data.success = false;
  }
  return data;
}

function setGoldFactor(f) {
  goldFactor = f && f > 0 ? f : 1;
}

function showToast(message, ok = true) {
  const existing = document.querySelector('.toast');
  if (existing) existing.remove();
  const el = document.createElement('div');
  el.className = 'toast ' + (ok ? 'ok' : 'bad');
  el.textContent = message;
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 4000);
}

async function doAction(path, body, onSuccess) {
  try {
    const data = await api(path, { method: 'POST', body: JSON.stringify(body || {}) });
    if (data.success) {
      showToast(data.message, true);
      if (onSuccess) onSuccess(data);
      return true;
    }
    showToast(data.message || data.error || 'Islem basarisiz', false);
    return false;
  } catch (e) {
    showToast('Baglanti hatasi: ' + e.message, false);
    return false;
  }
}

function fillSelect(id, items, valueKey, labelFn, emptyLabel) {
  const sel = document.getElementById(id);
  if (!sel) return;
  sel.innerHTML = (emptyLabel ? `<option value="">${emptyLabel}</option>` : '') +
    items.map(i => `<option value="${i[valueKey]}">${labelFn(i)}</option>`).join('');
}

function formatMg(mg) {
  const usd = (mg || 0) / 1000 * goldFactor;
  return '$' + usd.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
}

async function logout() {
  try { await api('/logout', { method: 'POST' }); } catch (e) { /* yine de cik */ }
  token = null;
  localStorage.removeItem('mceconomy_token');
  location.reload();
}

function setupNav(containerId, pagePrefix) {
  document.querySelectorAll('#' + containerId + ' .nav-item').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('#' + containerId + ' .nav-item').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
      const page = document.getElementById(pagePrefix + btn.dataset.page);
      if (page) page.classList.add('active');
    });
  });
}
