// Shared helpers for the LiveTheme widget-in-background samples (each theme folder ships its own copy; no external files).
// - Live values from the car app (window.livetheme): music, volume, battery, mood light, estimated rhythm.
// - The rhythm is ESTIMATED from the track (title → tempo), not measured from the sound: use it for decoration only.
// - Gestures: a tap or a horizontal swipe, decided 260 ms after the finger lands (the app sends touch points, no "up").
const Live = (() => {
  const canvas = document.getElementById('scene'), g = canvas.getContext('2d');
  const s = {title: '', artist: '', playing: false, album: null, ambient: null, volume: 0.5, level: null, charging: false,
    bpm: 0, since: 0, night: true, infoOnly: false, connected: false};
  let w = 0, h = 0, dpr = 1;
  function resize() { dpr = Math.min(2, window.devicePixelRatio || 1); w = canvas.width = Math.round(innerWidth * dpr); h = canvas.height = Math.round(innerHeight * dpr); }
  addEventListener('resize', resize); resize();
  const lt = window.livetheme;
  if (lt) {
    lt.on('data', d => {
      s.connected = true;
      const m = d.music || {}; s.title = m.title || ''; s.artist = m.artist || ''; s.playing = !!m.playing; s.album = m.color || null;
      s.ambient = d.ambient && d.ambient.color || null; s.ambientBrightness = d.ambient && typeof d.ambient.brightness === "number" ? d.ambient.brightness : null; s.night = d.night !== false;
      if (typeof d.volume === 'number') s.volume = d.volume;
      const b = d.battery || {}; s.level = typeof b.level === 'number' ? b.level : null; s.charging = !!b.charging;
      const r = d.rhythm || {}; s.bpm = r.bpm || 0; s.since = r.since || 0;
    });
    lt.on('paused', () => { s.infoOnly = lt.infoOnly; });
  }
  // Virtual groove. The car never gives us the sound, only an estimated tempo, so a steady pulse would look like a metronome.
  // Instead each song gets its own drum pattern (picked from the title), phrased in 8-bar sections — intro, build-up with a snare
  // roll, drop, breakdown — so the bass, snare and hi-hat parts of a theme move differently and the scene rises and falls.
  // It is still decoration, not the real music. Demo recordings can feed measured values through window.__audio instead.
  const KICK = ['1000100010001000', '1000001010000010', '1000000010100000', '1000100000101000'];
  const SNARE = ['0000100000001000', '0000100000001001', '0000100001001000'];
  const HAT = ['1010101010101010', '0010001000100010', '1111101111111011'];
  const PARTS = [{name: 'intro', level: .35, kick: false, hat: .5}, {name: 'build', level: .6, kick: true, hat: .8},
    {name: 'drop', level: 1, kick: true, hat: 1}, {name: 'break', level: .3, kick: false, hat: .35}];
  const ORDER = [0, 1, 2, 3, 1, 2, 2, 3];
  const hash = str => { let x = 7; for (const c of str) x = (Math.imul(x, 31) + c.charCodeAt(0)) | 0; return x >>> 0; };
  const rnd = n => ((Math.imul(n ^ 0x9e3779b9, 0x85ebca6b) >>> 0) % 1000) / 1000;
  const QUIET = {kick: 0, snare: 0, hat: 0, energy: 0, drop: 0, part: 'none'};
  function groove(now) {
    if (window.__audio) return window.__audio;
    if (!s.playing || !s.bpm) return QUIET;
    const seed = hash(s.title + '\u0000' + s.artist), spb = 60 / s.bpm, pos = Math.max(0, (now - (s.since || 0)) / 1000 / spb * 4), step = Math.floor(pos);
    const partOf = st => PARTS[ORDER[(Math.floor(st / 128) + (seed % 3)) % ORDER.length]];
    const k = KICK[seed % KICK.length], sn = SNARE[(seed >> 3) % SNARE.length], hh = HAT[(seed >> 6) % HAT.length];
    function hit(inst, st) {
      const p = partOf(st), i = st % 16, barIn = Math.floor(st / 16) % 8, v = .75 + .25 * rnd(st * 3 + seed);
      if (inst === 'kick') return (p.kick || barIn % 2 === 0 && i === 0) && k[i] === '1' ? v : 0;
      if (inst === 'snare') {
        if (p.name === 'build' && barIn >= 6) return (barIn === 7 || i % 2 === 0) ? .45 + .55 * ((barIn - 6) * 16 + i) / 32 : 0;   // roll into the drop
        if (barIn === 7 && i >= 13 && rnd(st + seed) > .4) return v * .8;                                                     // small fill
        return p.name !== 'intro' && sn[i] === '1' ? v : 0;
      }
      return hh[i] === '1' ? v * p.hat * (i % 4 === 2 ? 1 : .7) : 0;
    }
    function env(inst, decay) {   // most recent hit within a bar, decayed by the seconds since it
      for (let back = 0; back < 16 && step - back >= 0; back++) { const v = hit(inst, step - back); if (v) return v * Math.exp(-(pos - (step - back)) * spb / 4 * decay); }
      return 0;
    }
    const p = partOf(step), barIn = Math.floor(step / 16) % 8, inBar = (pos % 16) / 16;
    const energy = p.name === 'build' ? p.level + .35 * (barIn + inBar) / 8 : p.level * (.9 + .1 * Math.cos(inBar * Math.PI * 2));
    const drop = p.name === 'drop' && barIn === 0 ? Math.exp(-(pos % 16) * spb / 4 * 1.2) : 0;
    return {kick: env('kick', 9), snare: env('snare', 7), hat: env('hat', 22), energy: Math.min(1, energy), drop, part: p.name};
  }
  /** 0..1 pulse on the kick of the virtual groove (kept for older theme code); 0 when nothing plays. */
  function beat(now) { return groove(now).kick; }
  function hex(c, fallback) { const v = /^#[0-9a-f]{6}$/i.test(c || '') ? c : fallback; return [1, 3, 5].map(i => parseInt(v.slice(i, i + 2), 16)); }
  function rgba(rgb, a) { return `rgba(${rgb[0]},${rgb[1]},${rgb[2]},${a})`; }
  function mix(a, b, t) { return a.map((v, i) => Math.round(v + (b[i] - v) * t)); }
  const two = n => String(n).padStart(2, '0');
  function time(d = new Date()) { return two(d.getHours()) + ':' + two(d.getMinutes()); }
  function date(d = new Date()) { return `${d.getMonth() + 1}월 ${d.getDate()}일 ${'일월화수목금토'[d.getDay()]}요일`; }
  /** Text with a soft dark halo so it stays readable on any frame (car glance rule). */
  function text(str, x, y, size, weight, color, align = 'center', alpha = 1) {
    g.save(); g.globalAlpha = alpha; g.font = `${weight} ${Math.round(size)}px "Noto Sans KR","Noto Sans CJK KR",sans-serif`; g.textAlign = align; g.textBaseline = 'middle';
    g.shadowColor = 'rgba(0,0,0,.55)'; g.shadowBlur = size * .18; g.fillStyle = color; g.fillText(str, x, y); g.restore();
  }
  function fit(str, size, weight, max) { g.font = `${weight} ${Math.round(size)}px sans-serif`; let out = str; while (out.length > 1 && g.measureText(out).width > max) out = out.slice(0, -2) + '…'; return out; }
  // Gestures → music requests. The whole area given to onGesture counts as the target (far above 76 dp).
  let gesture = null;
  function onGesture(inside, handlers) {
    if (!lt) return;
    lt.on('touch', ({x, y}) => {
      const now = performance.now();
      if (!gesture || now - gesture.last > 250) {
        if (!inside(x, y)) { gesture = null; return; }
        gesture = {x0: x, x, last: now}; const mine = gesture;
        setTimeout(() => { if (gesture !== mine) return; const dx = mine.x - mine.x0; gesture = null; if (dx > .12) handlers.right(); else if (dx < -.12) handlers.left(); else handlers.tap(); }, 260);
      } else { gesture.x = x; gesture.last = now; }
    });
  }
  let toast = '', toastUntil = 0;
  function say(str) { toast = str; toastUntil = performance.now() + 1800; }
  function ask(action, ok) { if (!lt) return; lt.request(action).then(r => say(r.ok ? ok : r.reason === 'driving' ? '주행 중에는 재생·일시정지만 돼요' : r.reason === 'unsupported' ? '음악 앱이 이 조작을 지원하지 않아요' : r.reason === 'rate-limited' ? '' : '지금은 조작할 수 없어요')); }
  function drawToast(now, y) { if (toast && now < toastUntil) text(toast, w / 2, y, Math.max(22, h * .028), 500, '#fff', 'center', Math.min(1, (toastUntil - now) / 400)); }
  return {s, g, get w() { return w; }, get h() { return h; }, get dpr() { return dpr; }, beat, groove, hex, rgba, mix, time, date, text, fit, onGesture, ask, say, drawToast};
})();
