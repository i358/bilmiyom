let token = localStorage.getItem('mceconomy_token');

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (token) headers['Authorization'] = 'Bearer ' + token;
  const res = await fetch('/api' + path, { ...options, headers });
  return res.json();
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
  const data = await api(path, { method: 'POST', body: JSON.stringify(body || {}) });
  if (data.success) {
    showToast(data.message, true);
    if (onSuccess) onSuccess(data);
    return true;
  }
  showToast(data.message || 'İşlem başarısız', false);
  return false;
}

function fillSelect(id, items, valueKey, labelFn, emptyLabel) {
  const sel = document.getElementById(id);
  if (!sel) return;
  sel.innerHTML = (emptyLabel ? `<option value="">${emptyLabel}</option>` : '') +
    items.map(i => `<option value="${i[valueKey]}">${labelFn(i)}</option>`).join('');
}

function formatMg(mg) {
  const mc = (mg || 0) / 1000;
  return mc.toLocaleString('tr-TR', { maximumFractionDigits: 2 }) + ' MC';
}

async function logout() {
  try { await api('/logout', { method: 'POST' }); } catch (e) { /* yine de cik */ }
  token = null;
  localStorage.removeItem('mceconomy_token');
  location.reload();
}

function setupNav(containerId, pagePrefix) {
  document.querySelectorAll(`#${containerId} .nav-item[data-page]`).forEach(btn => {
    btn.onclick = () => {
      document.querySelectorAll(`#${containerId} .nav-item`).forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const page = btn.dataset.page;
      document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
      const target = document.getElementById(pagePrefix + page);
      if (target) target.classList.add('active');
      const title = document.getElementById('pageTitle');
      if (title) title.textContent = btn.textContent.trim();
    };
  });
}
