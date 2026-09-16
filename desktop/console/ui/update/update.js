/* Update panel — thin client over the console server.
   GET /ui/update every 2s: the two slots, the pack a stick offers, and the job in flight
   (the console runs ujimactl itself, so the job IS the install). A click is POST
   /update/<op> — a refusal answers that request, everything after it rides the poll. */

const POLL_MS = 2000;

const I = {
  cpu:'<rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><path d="M9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M1 15h3M20 9h3M20 15h3"/>',
  up:'<circle cx="12" cy="12" r="10"/><path d="m16 12-4-4-4 4"/><path d="M12 16V8"/>',
  back:'<path d="M3 12a9 9 0 1 0 9-9c-2.5 0-4.9 1-6.6 2.6L3 8"/><path d="M3 3v5h5"/>',
  usb:'<circle cx="10" cy="7" r="1"/><circle cx="4" cy="20" r="1"/><path d="M4.7 19.3 19 5"/><path d="m21 3-3 1 2 2Z"/><path d="M9.26 7.68 5 12l2 5"/><path d="m10 14 5 2 3.5-3.5"/><path d="m18 12 1-1 1 1-1 1Z"/>',
  alert:'<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
};
const ic  = k => `<span class="ic"><svg viewBox="0 0 24 24">${I[k]}</svg></span>`;
const $   = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const ver = s => s || '—';
const day = iso => (iso || '').slice(0, 10);
const gb  = b => b ? (b / 1e9).toFixed(1) + ' GB' : '';
const up  = s => String(s || '?').toUpperCase();

let S    = null;   // last /ui/update
let asking = null; // a click the server has not answered yet, or its refusal
let key  = '';     // what the page was built from — rebuild only when it moves

async function poll(){
  let s;
  try { s = await (await fetch('/ui/update')).json(); }
  catch (e) { return; }
  if (!s || s.schema !== 1) return;
  S = s;
  render();
}

const running = () => S.action && !S.action.finished;
const busy    = () => !!(asking && !asking.err) || running();

function slotHtml(k, s, now){
  if (!s) return '';
  return `<div class="slot ${now ? 'now' : ''}">
    <span class="k">${k} · slot ${esc(up(s.slot))}</span>
    ${s.empty ? `<span class="v dim">empty</span>`
              : `<span class="v">${esc(ver(s.version))}</span>
                 <span class="s">installed ${esc(day(s.installedAt))}</span>`}
    ${s.failedTrial ? `<span class="chip warn">did not boot last time</span>` : ''}
  </div>`;
}

function packHtml(){
  const p = S.pack;
  if (!p) return `<p class="hint">No update on a stick. Plug in a labeled stick that carries a pack —
    it appears here on its own.</p>`;
  const same = p.version && S.running && p.version === S.running.version;
  return `<div class="packrow">
      <span class="name">${esc(p.label || p.version)}</span>
      <span class="meta">${esc(ver(p.version))} · built ${esc(day(p.packedAt))} · ${esc(gb(p.bytes))}${p.stick ? ' · ' + esc(p.stick) : ''}</span>
      <span class="grow"></span>
      <button class="btn primary" onclick="confirmUpdate()" ${same || S.trial || busy() ? 'disabled' : ''}>${ic('up')}Update to ${esc(ver(p.version))}</button>
    </div>
    ${same ? `<p class="hint">This version is already running.</p>` : ''}`;
}

function taskHtml(){
  if (asking && asking.err)
    return `<div class="scard"><span class="sc-title">${ic('alert')}Could not start</span>
      <p class="jobmsg fail">${esc(asking.err)}</p></div>`;
  if (asking)
    return `<div class="scard"><div class="frow"><span class="spin"></span>
      <span class="jobmsg">starting…</span></div></div>`;
  const t = S.action;
  if (!t || !['install','revert'].includes(t.verb)) return '';
  const fail = t.error != null, ok = t.finished && !fail;
  const pct  = ok ? 100 : (t.progress || 0);
  return `<div class="scard">
    <span class="sc-title">${ic(t.verb === 'revert' ? 'back' : 'up')}${t.verb === 'revert' ? 'Going back' : 'Updating'}</span>
    <div class="bar ${fail ? 'fail' : ok ? 'ok' : ''}"><i style="width:${pct}%"></i></div>
    <div class="frow">
      <span class="jobmsg ${fail ? 'fail' : ok ? 'ok' : ''}">${esc(fail ? t.error : (t.message || (ok ? 'done' : 'starting…')))}</span>
      <span class="grow"></span>
      <span class="pct">${pct}%</span>
    </div>
    ${fail ? `<p class="hint">Nothing was activated — the other slot is simply not finished. Fix the cause and try again.</p>` : ''}
  </div>`;
}

function render(){
  const k = [S.error, S.trial, JSON.stringify(S.running), JSON.stringify(S.other),
             JSON.stringify(S.pack), JSON.stringify(S.action), JSON.stringify(asking)].join('|');
  if (k === key) return;
  key = k;
  if (S.error){
    $('detail').innerHTML = `<div class="scard"><span class="sc-title">${ic('alert')}Not available</span>
      <p class="hint">${esc(S.error)}</p></div>`;
    return;
  }
  const other = S.other || {};
  $('detail').innerHTML = `
  <div class="scard">
    <span class="sc-title">${ic('cpu')}This computer</span>
    <div class="slots">
      ${slotHtml('Running', S.running, true)}
      ${slotHtml('Other', other, false)}
    </div>
    ${S.trial ? `<div class="warnline"><span class="wd"></span>A trial boot is running — keep it or go back in New version first.</div>` : ''}
  </div>

  <div class="scard">
    <span class="sc-title">${ic('usb')}From a stick</span>
    ${packHtml()}
  </div>

  <div class="scard">
    <span class="sc-title">${ic('back')}Go back</span>
    <div class="frow">
      <p class="hint grow">${other.empty ? 'The other slot holds nothing to go back to.'
        : `Restart on ${esc(ver(other.version))}, the version in slot ${esc(up(other.slot))}.`}</p>
      <button class="btn dangerf" onclick="confirmRevert()" ${other.empty || S.trial || busy() ? 'disabled' : ''}>${ic('back')}Revert to ${esc(ver(other.version))}</button>
    </div>
  </div>

  ${taskHtml()}`;
}

/* ── the click ───────────────────────────────────────────────────────────── */
function openSheet(html){ $('sheet').innerHTML = html; $('ovl').classList.add('show'); }
function closeSheet(){ $('ovl').classList.remove('show'); }
$('ovl').addEventListener('click', e => { if (e.target === $('ovl')) closeSheet(); });

function skipRow(){
  return `<label class="check"><input type="checkbox" id="skip"> Skip the trial boot</label>
  <p class="warn" id="skipwarn" hidden>Without a trial there is no fallback: if the other slot does not boot, this computer needs its card rewritten.</p>`;
}
function wireSkip(){ $('skip').onchange = () => { $('skipwarn').hidden = !$('skip').checked; }; }

function confirmUpdate(){
  const p = S.pack; if (!p) return;
  openSheet(`
    <h2>Update to ${esc(ver(p.version))}?</h2>
    <p>The new version is written into the other slot and this computer's settings are carried over.
    It takes about ten minutes; keep the stick in and this window open. Then it restarts to try the
    new version — keep it or go back from there.</p>
    ${skipRow()}
    <div class="sfoot">
      <button class="btn quiet" onclick="closeSheet()">Cancel</button>
      <button class="btn primary" onclick="go('install')">${ic('up')}Update</button>
    </div>`);
  wireSkip();
}
function confirmRevert(){
  const o = S.other || {};
  openSheet(`
    <h2>Go back to ${esc(ver(o.version))}?</h2>
    <p>This computer restarts to try the version in slot ${esc(up(o.slot))}. Anything open closes.
    Keep it or come back from there.</p>
    ${skipRow()}
    <div class="sfoot">
      <button class="btn quiet" onclick="closeSheet()">Cancel</button>
      <button class="btn dangerf" onclick="go('revert')">${ic('back')}Revert</button>
    </div>`);
  wireSkip();
}

async function go(op){
  const skip = !!($('skip') && $('skip').checked);
  closeSheet();
  asking = {op}; render();
  let err = null;
  try {
    const r = await fetch('/update/' + op, {method: 'POST',
      headers: {'content-type': 'application/json'}, body: JSON.stringify({skipTrial: skip})});
    const d = await r.json();
    if (!r.ok) err = d.error || 'the request failed';
  } catch (e) { err = 'no reply from the server'; }
  asking = err ? {op, err} : null;
  render();
  poll();
  if (err) setTimeout(() => { asking = null; render(); }, 8000);
}

setInterval(poll, POLL_MS);
poll();
