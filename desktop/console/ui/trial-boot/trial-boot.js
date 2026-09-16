/* New version — the trial-boot page. GET /ui/trial-boot every 2s: the slots and the
   decision in flight. Keep commits the running slot; Go back is a plain restart, and the
   firmware falls back on its own. */

const POLL_MS = 2000;
const I = {
  up:'<circle cx="12" cy="12" r="10"/><path d="m16 12-4-4-4 4"/><path d="M12 16V8"/>',
  check:'<polyline points="20 6 9 17 4 12"/>',
  back:'<path d="M3 12a9 9 0 1 0 9-9c-2.5 0-4.9 1-6.6 2.6L3 8"/><path d="M3 3v5h5"/>',
};
const ic  = k => `<span class="ic"><svg viewBox="0 0 24 24">${I[k]}</svg></span>`;
const $   = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const ver = s => s || '—';
const up  = s => String(s || '?').toUpperCase();

let S = null, asking = null, key = '';

async function poll(){
  let s;
  try { s = await (await fetch('/ui/trial-boot')).json(); } catch (e) { return; }
  if (!s || s.schema !== 1) return;
  S = s; render();
}

function render(){
  const t = S.action;
  const k = [S.error, S.trial, JSON.stringify(S.running), JSON.stringify(S.previous),
             JSON.stringify(t), JSON.stringify(asking)].join('|');
  if (k === key) return;
  key = k;
  const r = S.running || {}, p = S.previous || {};
  let body;
  if (S.error){
    body = `<h1>Not available</h1><p>${esc(S.error)}</p>`;
  } else if (asking && asking.err){
    body = head(r, p) + `<p class="fail">${esc(asking.err)}</p>` + acts();
  } else if (asking || (t && !t.finished)){
    const msg = asking ? (asking.op === 'keep' ? 'keeping this version…' : 'restarting…')
                       : (t.message || '…');
    body = head(r, p) + `<div class="frow"><span class="spin"></span><p>${esc(msg)}</p></div>`;
  } else if (t && t.error != null){
    body = head(r, p) + `<p class="fail">${esc(t.error)}</p>` + acts();
  } else if (!S.trial){
    body = `<h1>Running <b>${esc(ver(r.version))}</b></h1>
      <p>This computer stays on it from now on. You can close this window.</p>
      <div class="done">${ic('check')}Done</div>`;
  } else {
    body = head(r, p) + acts();
  }
  $('card').innerHTML = `<div class="glyph"><svg viewBox="0 0 24 24">${I.up}</svg></div>` + body;
}

function head(r, p){
  return `<h1>You are trying <b>${esc(ver(r.version))}</b></h1>
    <p>This computer restarted into a new version to see that it works. Keep it, or go back to the
    version it ran before — if nothing is decided, the next restart goes back on its own.</p>
    <div class="vers">
      <div class="ver now"><span class="k">Trying · slot ${esc(up(r.slot))}</span><span class="v">${esc(ver(r.version))}</span></div>
      <div class="ver"><span class="k">Before · slot ${esc(up(p.slot))}</span><span class="v">${esc(ver(p.version))}</span></div>
    </div>`;
}
function acts(){
  return `<div class="acts">
    <button class="btn quiet" onclick="go('back')">${ic('back')}Go back</button>
    <button class="btn primary" onclick="go('keep')">${ic('check')}Keep this version</button>
  </div>`;
}

async function go(op){
  asking = {op}; render();
  let err = null;
  try {
    const r = await fetch('/trial-boot/' + op, {method: 'POST',
      headers: {'content-type': 'application/json'}, body: '{}'});
    const d = await r.json();
    if (!r.ok) err = d.error || 'the request failed';
  } catch (e) { err = 'no reply from the server'; }
  asking = err ? {op, err} : null;
  render();
  poll();
}

setInterval(poll, POLL_MS);
poll();
