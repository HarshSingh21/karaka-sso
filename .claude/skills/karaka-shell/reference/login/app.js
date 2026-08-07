(function(){
"use strict";

const state = { view: 'signin', email: '', password: '', error: null, shake: false };

/* Generic suite-relevant floating icons (not movie icons) — document,
   bar-chart, shield/badge, grid, spark, clock. Plain inline SVG paths. */
const ICONS = {
  doc: 'M14 4h20l10 10v26H14z M34 4v10h10',
  chart: 'M8 40h32 M14 40V26 M24 40V16 M34 40V22',
  shield: 'M24 6l16 6v12c0 11-7 18-16 22-9-4-16-11-16-22V12z',
  grid: 'M8 8h12v12H8z M28 8h12v12H28z M8 28h12v12H8z M28 28h12v12H28z',
  spark: 'M24 4l4 16 16 4-16 4-4 16-4-16-16-4 16-4z',
  clock: 'M24 6a18 18 0 100 36 18 18 0 000-36z M24 14v10l8 5',
};
const FLOAT_ICONS = [
  { icon:'doc', x:'10%', y:'16%', size:44, delay:0 },
  { icon:'chart', x:'70%', y:'12%', size:50, delay:1.4 },
  { icon:'grid', x:'20%', y:'60%', size:52, delay:2.6 },
  { icon:'shield', x:'78%', y:'50%', size:42, delay:0.7 },
  { icon:'spark', x:'46%', y:'28%', size:34, delay:3.3 },
  { icon:'clock', x:'58%', y:'72%', size:40, delay:1.9 },
];

function iconSvg(key, size){
  return `<svg width="${size}" height="${size}" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="${ICONS[key]}"/></svg>`;
}

/* Karaka's product mark — three nodes converging on one point, echoing the
   grammatical meaning of the name ("kāraka": the agent that brings the
   action about — the thing everything else relates back to). */
function suiteMarkSvg(size){
  return `<svg width="${size}" height="${size}" viewBox="0 0 48 48"><path d="M14 32 L24 14 L34 32" stroke="#fff" stroke-width="2.6" fill="none" stroke-linecap="round" stroke-linejoin="round"/><circle cx="14" cy="32" r="5" fill="#fff"/><circle cx="34" cy="32" r="5" fill="#fff"/><circle cx="24" cy="14" r="5" fill="#fff"/></svg>`;
}
function orbitMarkSvg(){
  return `<svg width="26" height="26" viewBox="0 0 64 64"><defs><linearGradient id="gO" x1="4" y1="6" x2="60" y2="58" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#2451FF"/><stop offset="1" stop-color="#0F8B8D"/></linearGradient></defs><g transform="rotate(-28 32 32)"><ellipse cx="32" cy="32" rx="26" ry="12" fill="none" stroke="url(#gO)" stroke-width="3"/><circle cx="58" cy="32" r="4.6" fill="url(#gO)"/></g><circle cx="32" cy="32" r="7" fill="url(#gO)"/></svg>`;
}
function auraMarkSvg(){
  return `<svg width="26" height="26" viewBox="0 0 64 64"><defs><linearGradient id="gA" x1="4" y1="6" x2="60" y2="58" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#2451FF"/><stop offset="1" stop-color="#0F8B8D"/></linearGradient></defs><path d="M32 8 L37 27 L56 32 L37 37 L32 56 L27 37 L8 32 L27 27 Z" fill="url(#gA)"/></svg>`;
}
function pulseMarkSvg(){
  return `<svg width="26" height="26" viewBox="0 0 64 64"><defs><linearGradient id="gP" x1="4" y1="6" x2="60" y2="58" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#2451FF"/><stop offset="1" stop-color="#0F8B8D"/></linearGradient></defs><path d="M6 34 H20 L26 20 L34 46 L40 34 H58" stroke="url(#gP)" stroke-width="3.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
}

function esc(s){ return String(s==null?'':s).replace(/[&<>"']/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }

function heroPanel(){
  return `<div class="hero">
    <div class="hero-blob b1"></div>
    <div class="hero-blob b2"></div>
    ${FLOAT_ICONS.map(f=>`<div class="float-ic" style="left:${f.x};top:${f.y};animation-delay:${f.delay}s">${iconSvg(f.icon, f.size)}</div>`).join('')}
    <div class="hero-copy">
      <h1 class="display">ONE LOGIN.<br>EVERY TOOL,<br><span class="hl">ONE KARAKA.</span></h1>
      <p>Sign in once to reach ORBIT, AURA, and PULSE — your directory, your assistant, and your attendance, all under Karaka.</p>
    </div>
  </div>`;
}

function signinView(){
  return `${heroPanel()}
  <div class="form-side">
    <div class="form-wrap form-wrap-signin">
      <div class="brand-row">
        <div class="brand-lockup">
          <div class="brand-mark">${suiteMarkSvg(20)}</div>
          <div class="brand-name">KARAKA</div>
        </div>
        <div class="org-tag">Opal</div>
      </div>
      <div class="glass${state.shake ? ' shake' : ''}">
        <h2>Sign in</h2>
        <p class="sub">Use your work email to continue.</p>
        <form class="form" onsubmit="app.submit(event)" novalidate>
          <div class="field-float">
            <input class="input-float" id="f-email" type="email" placeholder=" " autocomplete="username" value="${esc(state.email)}" oninput="app.setEmail(event)">
            <label for="f-email">Work email</label>
            ${state.error==='email' ? `<div class="field-error">Enter a valid work email.</div>` : ''}
          </div>
          <div class="field-float">
            <input class="input-float" id="f-password" type="password" placeholder=" " autocomplete="current-password" value="${esc(state.password)}" oninput="app.setPassword(event)">
            <label for="f-password">Password</label>
            ${state.error==='password' ? `<div class="field-error">Enter your password.</div>` : ''}
          </div>
          <button class="btn-gradient" type="submit">Sign in</button>
        </form>
      </div>
      <div class="foot-note">Trouble signing in? Contact your workspace admin.</div>
    </div>
  </div>`;
}

function pickerView(){
  const tiles = [
    { name:'ORBIT', desc:'Employee register — directory, branches, and the audit trail.', mark:orbitMarkSvg(), href:'../../orbit-redesign/index.html', soon:false },
    { name:'AURA', desc:'Assistant for day-to-day questions across your books.', mark:auraMarkSvg(), soon:true },
    { name:'PULSE', desc:'Leave and attendance for every branch.', mark:pulseMarkSvg(), soon:true },
  ];
  return `<div class="form-side" style="width:100%">
    <div class="form-wrap" style="max-width:620px">
      <div class="brand-row">
        <div class="brand-lockup">
          <div class="brand-mark">${suiteMarkSvg(20)}</div>
          <div class="brand-name">KARAKA</div>
        </div>
        <div class="org-tag">Opal</div>
      </div>
      <div class="glass">
        <div class="picker-head">
          <h2>Choose where to go</h2>
          <p class="sub">Signed in as ${esc(state.email || 'you@company.com')}</p>
        </div>
        <div class="tiles">
          ${tiles.map(t => t.soon
            ? `<div class="tile soon" title="Not available yet">${t.mark}<div class="t-name" style="margin-top:10px">${t.name}</div><div class="t-desc">${esc(t.desc)}</div><span class="soon-tag">SOON</span></div>`
            : `<a class="tile" href="${t.href}">${t.mark}<div class="t-name" style="margin-top:10px">${t.name}</div><div class="t-desc">${esc(t.desc)}</div></a>`
          ).join('')}
        </div>
      </div>
    </div>
  </div>`;
}

const app = {
  setEmail(e){ state.email = e.target.value; },
  setPassword(e){ state.password = e.target.value; },
  submit(e){
    e.preventDefault();
    const validEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(state.email.trim());
    if(!validEmail){ state.error = 'email'; app.triggerShake(); return; }
    if(!state.password){ state.error = 'password'; app.triggerShake(); return; }
    state.error = null;
    state.view = 'picker';
    render();
  },
  triggerShake(){
    state.shake = true; render();
    setTimeout(()=>{ state.shake = false; render(); }, 500);
  },
};
window.app = app;

function render(){
  const root = document.getElementById('app');
  root.innerHTML = state.view === 'signin' ? signinView() : pickerView();
}
render();
})();
