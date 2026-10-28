// 밤바다 라디오: a big clock above the horizon mirrored in the sea; waves rise with the music volume, ripples spread on each
// estimated beat, the song floats on the water and the album colour tints the sky. Tap the sea = play/pause, swipe = next/previous.
const {s, g} = Live;
const stars = Array.from({length: 70}, (_, i) => ({x: (i * 0.6180339) % 1, y: ((i * 0.3819660) % 1) * .9, r: .6 + (i % 3) * .5}));
const ripples = []; let lastBeat = 0, phase = 0, prev = 0, wave = .5;
const mirror = {c: document.createElement('canvas'), key: ''};
Live.onGesture((x, y) => y > horizon() / Live.h, {
  tap: () => Live.ask('media.playPause', s.playing ? '일시정지' : '재생'),
  left: () => Live.ask('media.next', '다음 곡'), right: () => Live.ask('media.prev', '이전 곡')});
function horizon() { const {w, h} = Live; return h * (w < h ? .46 : .56); }

function frame(now) {
  const {w, h} = Live, portrait = w < h, still = s.infoOnly, hz = horizon();
  const dt = prev ? Math.min(.1, (now - prev) / 1000) : 0; prev = now;
  if (!still) phase += dt * (s.playing ? 1.1 : .35);
  wave += ((s.playing ? .25 + s.volume * .9 : .2) - wave) * Math.min(1, dt * 2);   // eased so a volume jump rolls in, not snaps
  const tint = Live.hex(s.album || s.ambient, '#3a5f9a'), deep = [8, 14, 30];
  // sky
  const sky = g.createLinearGradient(0, 0, 0, hz);
  sky.addColorStop(0, Live.rgba(deep, 1)); sky.addColorStop(.7, Live.rgba(Live.mix(deep, tint, .35), 1)); sky.addColorStop(1, Live.rgba(Live.mix(deep, tint, .7), 1));
  g.fillStyle = sky; g.fillRect(0, 0, w, hz + 2);
  g.fillStyle = 'rgba(255,255,255,.7)'; for (const st of stars) { if (st.y * h > hz - 10) continue; g.globalAlpha = .35 + .35 * Math.sin(phase * 2 + st.x * 40); g.beginPath(); g.arc(st.x * w, st.y * hz, st.r * Live.dpr, 0, 7); g.fill(); } g.globalAlpha = 1;
  // sea
  const sea = g.createLinearGradient(0, hz, 0, h); sea.addColorStop(0, Live.rgba(Live.mix(deep, tint, .45), 1)); sea.addColorStop(1, Live.rgba([3, 6, 14], 1));
  g.fillStyle = sea; g.fillRect(0, hz, w, h - hz);
  // clock + date and their reflection
  const size = Math.min(w * (portrait ? .27 : .16), h * .2), clock = Live.time(), cy = hz - size * .75;
  Live.text(clock, w / 2, cy, size, 200, '#F4F7FF');
  Live.text(Live.date(), w / 2, cy + size * .62, size * .2, 400, 'rgba(235,242,255,.9)');
  // reflection: the clock is drawn once per minute into an offscreen canvas, then copied in thin wavy strips (cheap per frame)
  const key = clock + '|' + Math.round(size) + '|' + w;
  if (mirror.key !== key) { mirror.key = key; mirror.c.width = w; mirror.c.height = Math.ceil(size * 1.6); const m = mirror.c.getContext('2d'); m.clearRect(0, 0, w, mirror.c.height); m.font = `200 ${Math.round(size)}px sans-serif`; m.textAlign = 'center'; m.textBaseline = 'middle'; m.fillStyle = '#cfe3ff'; m.fillText(clock, w / 2, size * .7); }
  const strip = 6 * Live.dpr, mh = mirror.c.height;
  for (let y = 0; y < size * 1.3; y += strip) {
    const off = still ? 0 : Math.sin(phase * 3 + y * .05) * (3 + wave * 10) * Live.dpr, src = hz - y - cy + size * .7;   // the clock row mirrored at the horizon
    if (src < 0 || src > mh - strip) continue;
    g.globalAlpha = .28 * (1 - y / (size * 1.3)); g.drawImage(mirror.c, 0, src, w, strip, off, hz + y, w, strip);
  }
  g.globalAlpha = 1;
  // waves: amplitude follows the volume
  for (let layer = 0; layer < 4; layer++) {
    const base = hz + (h - hz) * (.12 + layer * .2), amp = (h - hz) * (.012 + wave * .035) * (1 + layer * .35);
    g.beginPath(); g.moveTo(0, h);
    for (let x = 0; x <= w; x += w / 60) g.lineTo(x, base + Math.sin(x / w * (5 + layer) + phase * (1.4 + layer * .3) + layer) * amp);
    g.lineTo(w, h); g.closePath(); g.fillStyle = Live.rgba(Live.mix([10, 20, 40], tint, .25 - layer * .05), .35 + layer * .12); g.fill();
  }
  // ripples on estimated beats
  const b = Live.beat(Date.now());
  if (!still && b > .9 && now - lastBeat > 250) { lastBeat = now; ripples.push({x: .25 + Math.random() * .5, t: now}); }
  for (let i = ripples.length - 1; i >= 0; i--) {
    const r = ripples[i], age = (now - r.t) / 1600; if (age > 1 || still) { ripples.splice(i, 1); continue; }
    g.strokeStyle = Live.rgba(Live.mix([200, 225, 255], tint, .3), (1 - age) * .5); g.lineWidth = 2 * Live.dpr;
    g.beginPath(); g.ellipse(r.x * w, hz + (h - hz) * .55, age * w * .22, age * (h - hz) * .06, 0, 0, 7); g.stroke();
  }
  // the song, floating
  const fy = hz + (h - hz) * (portrait ? .5 : .62) + (still ? 0 : Math.sin(phase * 1.6) * 6 * Live.dpr), ts = Math.max(26 * Live.dpr, size * .24);
  if (s.title) {
    Live.text(Live.fit(s.title, ts, 600, w * .8), w / 2, fy, ts, 600, '#FFFFFF');
    if (s.artist) Live.text(Live.fit(s.artist, ts * .62, 400, w * .7), w / 2, fy + ts * 1.05, ts * .62, 400, 'rgba(225,236,255,.85)');
  }
  Live.drawToast(now, hz + (h - hz) * .3);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
