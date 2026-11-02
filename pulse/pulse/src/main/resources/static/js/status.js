'use strict';

const slug = location.pathname.split('/').filter(Boolean).pop();

async function load() {
  const banner = document.getElementById('banner');
  try {
    const res = await fetch('/api/public/status/' + encodeURIComponent(slug));
    if (!res.ok) throw new Error('not found');
    render(await res.json());
  } catch (e) {
    banner.className = 'banner bad';
    banner.textContent = 'This status page does not exist.';
  }
}

function render(data) {
  document.title = data.owner + ' status';
  document.getElementById('title').textContent = data.owner + '\u2019s status';

  const banner = document.getElementById('banner');
  if (data.total === 0) {
    banner.className = 'banner';
    banner.textContent = 'No monitors yet';
  } else if (data.down > 0) {
    banner.className = 'banner bad';
    banner.textContent = data.down + (data.down === 1 ? ' system is down' : ' systems are down');
  } else if (data.up === data.total) {
    banner.className = 'banner ok';
    banner.textContent = 'All systems operational';
  } else {
    banner.className = 'banner';
    banner.textContent = 'Checking systems\u2026';
  }

  const list = document.getElementById('list');
  list.textContent = '';
  data.monitors.forEach(m => list.append(
    el('div', { class: 's-item' },
      el('div', { class: 's-top' }, el('strong', { text: m.name }), statusBadge(m.status)),
      heartbeat(m.recent),
      el('div', { class: 's-nums' },
        el('span', {}, 'Last 24h ', el('b', { text: pct(m.uptime24h) })),
        el('span', {}, 'Last 30 days ', el('b', { text: pct(m.uptime30d) }))))));

  const incTitle = document.getElementById('incTitle');
  const incidents = document.getElementById('incidents');
  incidents.textContent = '';
  incTitle.hidden = data.incidents.length === 0;
  data.incidents.forEach(i => incidents.append(
    el('div', { class: 'incident' },
      el('span', { class: 'tag ' + (i.ongoing ? 'ongoing' : 'resolved'), text: i.ongoing ? 'Ongoing' : 'Resolved' }),
      el('span', { class: 'when', text: i.monitorName }),
      el('span', { class: 'dur', text: fmtDate(i.startedAt) }),
      i.reason ? el('span', { class: 'why', text: i.reason }) : null)));
}

load();
setInterval(load, 30000);
