// Shared helpers for the LiveTheme widget-in-background samples (each theme folder ships its own copy; no external files).
// - Live values from the car app (window.livetheme): music, volume, battery, mood light, estimated rhythm.
// - The rhythm is ESTIMATED from the track (title → tempo), not measured from the sound: use it for decoration only.
// - Gestures: a tap (with where it landed, 0..1) or a horizontal swipe, decided 260 ms after the finger lands (the app sends touch points, no "up").
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
      s.ambient = d.ambient && d.ambient.color || null; s.night = d.night !== false;
      if (typeof d.volume === 'number') s.volume = d.volume;
      const b = d.battery || {}; s.level = typeof b.level === 'number' ? b.level : null; s.charging = !!b.charging;
      const r = d.rhythm || {}; s.bpm = r.bpm || 0; s.since = r.since || 0;
    });
    lt.on('paused', () => { s.infoOnly = lt.infoOnly; });
  }
  /** 0..1 pulse on each estimated beat (sharp attack, soft decay); 0 when nothing plays. */
  function beat(now) {
    if (!s.playing || !s.bpm) return 0;
    const period = 60000 / s.bpm, t = ((now - (s.since || 0)) % period + period) % period / period;
    return Math.exp(-t * 6);
  }
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
        gesture = {x0: x, y0: y, x, last: now}; const mine = gesture;
        setTimeout(() => { if (gesture !== mine) return; const dx = mine.x - mine.x0; gesture = null; if (dx > .12) handlers.right(); else if (dx < -.12) handlers.left(); else handlers.tap(mine.x0, mine.y0); }, 260);
      } else { gesture.x = x; gesture.last = now; }
    });
  }
  let toast = '', toastUntil = 0;
  function say(str) { toast = str; toastUntil = performance.now() + 1800; }
  function ask(action, ok) { if (!lt) return; lt.request(action).then(r => say(r.ok ? ok : r.reason === 'driving' ? '주행 중에는 재생·일시정지만 돼요' : r.reason === 'unsupported' ? '음악 앱이 이 조작을 지원하지 않아요' : r.reason === 'rate-limited' ? '' : '지금은 조작할 수 없어요')); }
  function drawToast(now, y) { if (toast && now < toastUntil) text(toast, w / 2, y, Math.max(22, h * .028), 500, '#fff', 'center', Math.min(1, (toastUntil - now) / 400)); }
  return {s, g, get w() { return w; }, get h() { return h; }, get dpr() { return dpr; }, beat, hex, rgba, mix, time, date, text, fit, onGesture, ask, say, drawToast};
})();
