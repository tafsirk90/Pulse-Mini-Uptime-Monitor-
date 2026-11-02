'use strict';

const TOKEN_KEY = 'pulseToken';
const USER_KEY = 'pulseUser';

let monitors = [];
let channels = [];
let selectedId = null;
let range = '24h';
let editingId = null;

const $ = selector => document.querySelector(selector);
const isLoggedIn = () => !!localStorage.getItem(TOKEN_KEY);

/* ---------------- api ---------------- */
async function api(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) headers.Authorization = 'Bearer ' + token;

  const res = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  if (res.status === 401 && token) {
    logout();
    throw new Error('Your session has ended. Please log in again.');
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.fields ? Object.values(data.fields).join('. ') : (data.error || 'Something went wrong'));
  }
  return data;
}

let toastTimer;
function toast(message, isError) {
  const t = $('#toast');
  t.textContent = message;
  t.className = 'toast show' + (isError ? ' error' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.className = 'toast'; }, 4500);
}

/* ---------------- sign in / out ---------------- */
function currentUser() {
  try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch (e) { return null; }
}

function showAuth() {
  $('#auth').hidden = false;
  $('#app').hidden = true;
}

function showApp() {
  const user = currentUser() || {};
  $('#auth').hidden = true;
  $('#app').hidden = false;
  $('#who').textContent = user.name || '';
  $('#statusLink').href = '/status/' + (user.slug || '');
  refresh();
}

function logout() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  selectedId = null;
  showAuth();
}

function switchAuthTab(login) {
  $('#loginForm').hidden = !login;
  $('#registerForm').hidden = login;
  $('#showLogin').setAttribute('aria-pressed', String(login));
  $('#showRegister').setAttribute('aria-pressed', String(!login));
  $('#authError').hidden = true;
}

async function authSubmit(path, body) {
  $('#authError').hidden = true;
  localStorage.removeItem(TOKEN_KEY);
  try {
    const data = await api('POST', path, body);
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, JSON.stringify({ name: data.name, slug: data.slug }));
    showApp();
  } catch (e) {
    $('#authError').textContent = e.message;
    $('#authError').hidden = false;
  }
}

$('#showLogin').onclick = () => switchAuthTab(true);
$('#showRegister').onclick = () => switchAuthTab(false);
$('#logoutBtn').onclick = logout;

$('#loginForm').addEventListener('submit', e => {
  e.preventDefault();
  authSubmit('/api/auth/login', { email: $('#loginEmail').value, password: $('#loginPassword').value });
});
$('#registerForm').addEventListener('submit', e => {
  e.preventDefault();
  authSubmit('/api/auth/register', {
    name: $('#regName').value, email: $('#regEmail').value, password: $('#regPassword').value
  });
});

/* ---------------- loading ---------------- */
async function refresh() {
  try {
    [monitors, channels] = await Promise.all([api('GET', '/api/monitors'), api('GET', '/api/channels')]);
  } catch (e) {
    if (isLoggedIn()) toast(e.message, true);
    return;
  }
  if (selectedId && !monitors.some(m => m.id === selectedId)) selectedId = null;
  renderSummary();
  renderMonitors();
  renderChannels();
  await loadDetail();
}

/* ---------------- summary + monitor list ---------------- */
function renderSummary() {
  const count = status => monitors.filter(m => m.status === status).length;
  const host = $('#summary');
  host.textContent = '';
  [['Up', count('UP'), 'up'], ['Down', count('DOWN'), 'down'],
   ['Pending', count('PENDING'), 'pending'], ['Paused', count('PAUSED'), 'paused']]
    .forEach(([label, n, cls]) => host.append(
      el('div', { class: 'sum ' + cls },
        el('span', { class: 'sum-n', text: String(n) }),
        el('span', { class: 'sum-l', text: label }))));
}

function renderMonitors() {
  const host = $('#monitorList');
  host.textContent = '';
  if (!monitors.length) {
    host.append(el('div', { class: 'empty' },
      el('p', { text: 'No monitors yet.' }),
      el('p', { class: 'muted', text: 'Add a website to start checking it, or add the demo monitor to watch an outage happen live.' })));
    return;
  }
  monitors.forEach(m => host.append(monitorCard(m)));
}

function metric(value, label) {
  return el('div', { class: 'metric' },
    el('div', { class: 'metric-v', text: value }),
    el('div', { class: 'metric-l', text: label }));
}

function monitorCard(m) {
  return el('article', { class: 'monitor' + (m.id === selectedId ? ' selected' : '') },
    el('div', { class: 'm-head' },
      el('div', { class: 'm-title' },
        el('div', { class: 'm-name' }, el('span', { text: m.name }), statusBadge(m.status)),
        el('div', { class: 'm-url', text: m.url })),
      el('div', { class: 'm-metrics' },
        metric(pct(m.uptime24h), 'Uptime 24h'),
        metric(m.lastResponseMs != null ? m.lastResponseMs + ' ms' : '–', 'Last response'),
        metric(m.lastCheckedAt ? timeAgo(m.lastCheckedAt) : 'not yet', 'Last check'))),
    m.active && m.lastError ? el('p', { class: 'm-error', text: m.lastError }) : null,
    el('div', { class: 'm-foot' },
      heartbeat(m.recent),
      el('div', { class: 'm-actions' },
        el('button', { class: 'link', type: 'button', text: 'Details', onclick: () => selectMonitor(m.id) }),
        el('button', { class: 'link', type: 'button', text: m.active ? 'Pause' : 'Resume', onclick: () => toggleActive(m) }),
        el('button', { class: 'link', type: 'button', text: 'Edit', onclick: () => openForm(m) }),
        el('button', { class: 'link danger', type: 'button', text: 'Delete', onclick: () => removeMonitor(m) }))));
}

async function toggleActive(m) {
  try {
    await api('PUT', '/api/monitors/' + m.id + '/active?value=' + (!m.active));
    toast(m.active ? 'Paused ' + m.name : 'Resumed ' + m.name);
    refresh();
  } catch (e) { toast(e.message, true); }
}

async function removeMonitor(m) {
  if (!confirm('Delete "' + m.name + '"? Its history will be removed.')) return;
  try {
    await api('DELETE', '/api/monitors/' + m.id);
    if (selectedId === m.id) selectedId = null;
    toast('Deleted ' + m.name);
    refresh();
  } catch (e) { toast(e.message, true); }
}

/* ---------------- add / edit form ---------------- */
function selectValue(select, value) {
  const wanted = String(value);
  if (![...select.options].some(o => o.value === wanted)) {
    select.append(el('option', { value: wanted, text: wanted }));
  }
  select.value = wanted;
}

function openForm(m) {
  editingId = m ? m.id : null;
  $('#formTitle').textContent = m ? 'Edit monitor' : 'Add a monitor';
  $('#saveBtn').textContent = m ? 'Save changes' : 'Add monitor';
  $('#mName').value = m ? m.name : '';
  $('#mUrl').value = m ? m.url : '';
  selectValue($('#mInterval'), m ? m.intervalSec : 60);
  selectValue($('#mTimeout'), m ? m.timeoutMs : 10000);
  $('#mStatus').value = m && m.expectedStatus ? m.expectedStatus : '';
  $('#mKeyword').value = m && m.keyword ? m.keyword : '';
  $('#formError').hidden = true;
  $('#monitorForm').hidden = false;
  $('#mName').focus();
}

function closeForm() {
  editingId = null;
  $('#monitorForm').hidden = true;
}

$('#addBtn').onclick = () => openForm(null);
$('#cancelBtn').onclick = closeForm;

$('#monitorForm').addEventListener('submit', async e => {
  e.preventDefault();
  let url = $('#mUrl').value.trim();
  if (!/^https?:\/\//i.test(url)) url = 'https://' + url;
  const body = {
    name: $('#mName').value.trim(),
    url,
    intervalSec: Number($('#mInterval').value),
    timeoutMs: Number($('#mTimeout').value),
    expectedStatus: $('#mStatus').value ? Number($('#mStatus').value) : null,
    keyword: $('#mKeyword').value.trim() || null
  };
  $('#formError').hidden = true;
  try {
    if (editingId) await api('PUT', '/api/monitors/' + editingId, body);
    else await api('POST', '/api/monitors', body);
    closeForm();
    toast('Saved. The first check runs within a few seconds.');
    refresh();
  } catch (err) {
    $('#formError').textContent = err.message;
    $('#formError').hidden = false;
  }
});

/* The demo monitor points at this app's own /demo/flaky page: up for 60s, down for 60s, repeating. */
$('#demoBtn').onclick = async () => {
  try {
    await api('POST', '/api/monitors', {
      name: 'Demo: flaky site', url: location.origin + '/demo/flaky',
      intervalSec: 10, timeoutMs: 5000, expectedStatus: null, keyword: null
    });
    toast('Demo monitor added. Within about 2 minutes you will see it go down and recover.');
    refresh();
  } catch (e) { toast(e.message, true); }
};

/* ---------------- details panel ---------------- */
function selectMonitor(id) {
  selectedId = id;
  renderMonitors();
  loadDetail().then(() => $('#detail').scrollIntoView({ behavior: 'smooth', block: 'start' }));
}

async function loadDetail() {
  const panel = $('#detail');
  if (!selectedId) { panel.hidden = true; return; }
  try {
    const [stats, incidents] = await Promise.all([
      api('GET', '/api/monitors/' + selectedId + '/stats?range=' + range),
      api('GET', '/api/monitors/' + selectedId + '/incidents')
    ]);
    renderDetail(panel, stats, incidents);
  } catch (e) {
    if (isLoggedIn()) toast(e.message, true);
  }
}

function stat(value, label) {
  return el('div', { class: 'stat' },
    el('div', { class: 'stat-v', text: value }),
    el('div', { class: 'stat-l', text: label }));
}

function renderDetail(panel, stats, incidents) {
  const m = monitors.find(x => x.id === selectedId);
  panel.hidden = false;
  panel.textContent = '';

  const seg = el('div', { class: 'seg', role: 'group', 'aria-label': 'Time range' },
    ['24h', '7d', '30d'].map(r => el('button', {
      type: 'button', text: r, 'aria-pressed': String(r === range),
      onclick: () => { range = r; loadDetail(); }
    })));

  panel.append(
    el('div', { class: 'detail-head' }, el('h2', { text: m ? m.name : 'Details' }), seg),
    el('div', { class: 'stats' },
      stat(pct(stats.uptimePercent), 'Uptime'),
      stat(stats.avgMs != null ? stats.avgMs + ' ms' : '–', 'Average response'),
      stat(stats.p95Ms != null ? stats.p95Ms + ' ms' : '–', 'Slowest 5% (p95)'),
      stat(stats.totalChecks + ' (' + stats.failedChecks + ' failed)', 'Checks'),
      stat(fmtDuration(stats.downtimeSeconds), 'Downtime')),
    buildChart(stats.points, range),
    el('p', { class: 'caption', text: 'Response time in milliseconds. Red bands mark periods with failed checks.' }),
    incidentList(incidents));
}

function incidentList(incidents) {
  const wrap = el('div', { class: 'incidents' }, el('h3', { text: 'Incidents' }));
  if (!incidents.length) {
    wrap.append(el('p', { class: 'muted', text: 'No incidents recorded. Nice.' }));
    return wrap;
  }
  incidents.forEach(i => wrap.append(
    el('div', { class: 'incident' },
      el('span', { class: 'tag ' + (i.ongoing ? 'ongoing' : 'resolved'), text: i.ongoing ? 'Ongoing' : 'Resolved' }),
      el('span', { class: 'when', text: fmtDate(i.startedAt) }),
      el('span', { class: 'dur', text: 'lasted ' + fmtDuration(i.durationSeconds) }),
      i.reason ? el('span', { class: 'why', text: i.reason }) : null)));
  return wrap;
}

function fmtTick(ms, rangeKey) {
  const d = new Date(ms);
  return rangeKey === '24h'
    ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString([], { month: 'short', day: 'numeric' });
}

function buildChart(points, rangeKey) {
  if (!points.length) return el('p', { class: 'empty small muted', text: 'No checks in this range yet.' });

  const W = 800, H = 230, L = 52, R = 14, T = 14, B = 30;
  const innerW = W - L - R, innerH = H - T - B;
  const t0 = points[0].t, t1 = points[points.length - 1].t, span = Math.max(1, t1 - t0);
  const maxMs = Math.max(100, ...points.map(p => p.ms || 0));
  const top = Math.ceil(maxMs * 1.15 / 100) * 100;
  const x = t => L + (points.length === 1 ? innerW / 2 : (t - t0) / span * innerW);
  const y = v => T + (1 - v / top) * innerH;

  const svg = svgEl('svg', { viewBox: '0 0 ' + W + ' ' + H, class: 'chart', role: 'img',
    'aria-label': 'Response time chart for the last ' + rangeKey });

  [0, top / 2, top].forEach(v => {
    svg.append(svgEl('line', { x1: L, x2: W - R, y1: y(v), y2: y(v), class: 'grid' }));
    const label = svgEl('text', { x: L - 8, y: y(v) + 4, class: 'axis', 'text-anchor': 'end' });
    label.textContent = Math.round(v);
    svg.append(label);
  });

  points.filter(p => p.failed).forEach(p =>
    svg.append(svgEl('rect', { x: x(p.t) - 4, y: T, width: 8, height: innerH, class: 'fail-band' })));

  let path = '', penDown = false;
  points.forEach(p => {
    if (p.ms == null) { penDown = false; return; }
    path += (penDown ? 'L' : 'M') + x(p.t).toFixed(1) + ' ' + y(p.ms).toFixed(1) + ' ';
    penDown = true;
  });
  if (path) svg.append(svgEl('path', { d: path, class: 'line' }));
  if (points.length <= 45) {
    points.filter(p => p.ms != null).forEach(p =>
      svg.append(svgEl('circle', { cx: x(p.t), cy: y(p.ms), r: 2.5, class: 'dot' })));
  }

  const start = svgEl('text', { x: L, y: H - 8, class: 'axis' });
  start.textContent = fmtTick(t0, rangeKey);
  const end = svgEl('text', { x: W - R, y: H - 8, class: 'axis', 'text-anchor': 'end' });
  end.textContent = fmtTick(t1, rangeKey);
  svg.append(start, end);
  return svg;
}

/* ---------------- alert channels ---------------- */
function maskTarget(c) {
  if (c.type === 'EMAIL') return c.target;
  try { return new URL(c.target).host + '/…'; } catch (e) { return 'webhook'; }   // hide the secret token
}

function renderChannels() {
  const host = $('#channelList');
  host.textContent = '';
  if (!channels.length) {
    host.append(el('li', { class: 'muted', text: 'No alert channels yet.' }));
    return;
  }
  channels.forEach(c => host.append(
    el('li', { class: 'channel' },
      el('span', { class: 'tag', text: c.type === 'EMAIL' ? 'Email' : 'Webhook' }),
      el('span', { class: 'target', text: maskTarget(c) }),
      el('button', { class: 'link', type: 'button', text: 'Send test', onclick: () => testChannel(c) }),
      el('button', { class: 'link danger', type: 'button', text: 'Remove', onclick: () => removeChannel(c) }))));
}

async function testChannel(c) {
  try {
    const res = await api('POST', '/api/channels/' + c.id + '/test');
    toast(res.message);
  } catch (e) { toast(e.message, true); }
}

async function removeChannel(c) {
  try {
    await api('DELETE', '/api/channels/' + c.id);
    refresh();
  } catch (e) { toast(e.message, true); }
}

$('#cType').addEventListener('change', () => {
  $('#cTarget').placeholder = $('#cType').value === 'EMAIL' ? 'you@example.com' : 'https://discord.com/api/webhooks/…';
});

$('#channelForm').addEventListener('submit', async e => {
  e.preventDefault();
  try {
    await api('POST', '/api/channels', { type: $('#cType').value, target: $('#cTarget').value.trim() });
    $('#cTarget').value = '';
    toast('Alert channel added');
    refresh();
  } catch (err) { toast(err.message, true); }
});

/* ---------------- start ---------------- */
// Auto-refresh every 10s, but not while a button in the list has keyboard focus (re-rendering would steal it).
setInterval(() => {
  const busy = $('#monitorList').contains(document.activeElement);
  if (isLoggedIn() && !document.hidden && !busy) refresh();
}, 10000);

if (isLoggedIn() && currentUser()) showApp(); else showAuth();
