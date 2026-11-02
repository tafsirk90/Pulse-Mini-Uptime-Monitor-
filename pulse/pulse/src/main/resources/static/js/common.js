'use strict';
/* Helpers shared by the dashboard and the public status page.
   All text is added with textContent / text nodes, so user-supplied values can never inject HTML. */

function el(tag, props, ...kids) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props || {})) {
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (value !== false && value != null) node.setAttribute(key, value);
  }
  kids.flat().forEach(kid => { if (kid != null) node.append(kid); });
  return node;
}

function svgEl(tag, attrs) {
  const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [key, value] of Object.entries(attrs || {})) node.setAttribute(key, value);
  return node;
}

function pct(value) {
  return value == null ? '–' : (Math.round(value * 100) / 100) + '%';
}

function fmtDuration(sec) {
  if (sec == null) return '–';
  if (sec < 60) return sec + 's';
  const m = Math.floor(sec / 60), s = sec % 60;
  if (m < 60) return s ? m + 'm ' + s + 's' : m + 'm';
  const h = Math.floor(m / 60);
  if (h < 24) return h + 'h ' + (m % 60) + 'm';
  return Math.floor(h / 24) + 'd ' + (h % 24) + 'h';
}

function timeAgo(iso) {
  const sec = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (sec < 5) return 'just now';
  if (sec < 60) return sec + 's ago';
  if (sec < 3600) return Math.floor(sec / 60) + 'm ago';
  if (sec < 86400) return Math.floor(sec / 3600) + 'h ago';
  return Math.floor(sec / 86400) + 'd ago';
}

function fmtDate(iso) {
  return new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
}

const STATUS_LABELS = { UP: 'Up', DOWN: 'Down', PENDING: 'Pending', PAUSED: 'Paused' };

function statusBadge(status) {
  return el('span', { class: 'badge ' + status.toLowerCase(), text: STATUS_LABELS[status] || status });
}

/* The heartbeat strip: one bar per recent check (green = ok, red = failed), oldest on the left. */
function heartbeat(recent) {
  const failed = recent.filter(ok => !ok).length;
  const label = recent.length
    ? 'Last ' + recent.length + ' checks: ' + (recent.length - failed) + ' succeeded, ' + failed + ' failed'
    : 'No checks yet';
  const wrap = el('div', { class: 'beats', role: 'img', 'aria-label': label });
  for (let i = recent.length; i < 30; i++) wrap.append(el('span', { class: 'beat' }));
  recent.forEach(ok => wrap.append(el('span', { class: 'beat ' + (ok ? 'ok' : 'fail') })));
  return wrap;
}
