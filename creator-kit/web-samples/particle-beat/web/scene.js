// 파티클 비트: a round clock (hour/minute arcs + digital time) with particles circling it; on each estimated beat they burst
// outward, how far depends on the volume. The album colour paints the particles. Tap the ring = play/pause, swipe = next/previous.
const {s, g} = Live;
const N = 220, parts = Array.from({length: N}, (_, i) => ({a: i / N * Math.PI * 2, r: 1 + (i % 7) * .025, v: .15 + (i % 5) * .05, k: 0}));
let prev = 0, spin = 0, burst = 0, lastBeat = 0;
Live.onGesture(() => true, {
  tap: () => Live.ask('media.playPause', s.playing ? '일시정지' : '재생'),
  left: () => Live.ask('media.next', '다음 곡'), right: () => Live.ask('media.prev', '이전 곡')});

function frame(now) {
  const {w, h} = Live, portrait = w < h, still = s.infoOnly;
  const dt = prev ? Math.min(.1, (now - prev) / 1000) : 0; prev = now;
  const tint = Live.hex(s.album || s.ambient, '#FF8AD8'), cool = Live.mix(tint, [120, 200, 255], .5);
  const cx = w / 2, cy = h * (portrait ? .4 : .47), R = Math.min(w, h) * (portrait ? .3 : .27);
  g.fillStyle = 'rgba(9,7,18,1)'; g.fillRect(0, 0, w, h);
  const glow = g.createRadialGradient(cx, cy, R * .2, cx, cy, R * 2); glow.addColorStop(0, Live.rgba(Live.mix([12, 9, 24], tint, .22), 1)); glow.addColorStop(1, 'rgba(9,7,18,1)'); g.fillStyle = glow; g.fillRect(0, 0, w, h);
  const b = still ? 0 : Live.beat(Date.now());
  if (b > .9 && now - lastBeat > 200) { lastBeat = now; burst = 1; }
  burst = Math.max(0, burst - dt * 2.2);
  if (!still) spin += dt * (s.playing ? .35 + s.volume * .4 : .1);
  // particles
  g.globalCompositeOperation = 'lighter';
  for (const p of parts) {
    if (!still) p.a += dt * p.v * (s.playing ? 1 : .3);
    const out = 1 + burst * (.15 + s.volume * .45) * (0.5 + (p.r - 1) * 8), rr = R * p.r * out;
    const x = cx + Math.cos(p.a + spin) * rr, y = cy + Math.sin(p.a + spin) * rr, size = (1.4 + (p.r - 1) * 20) * Live.dpr * (1 + burst * .6);
    g.fillStyle = Live.rgba(p.r > 1.08 ? cool : tint, .55 + burst * .35); g.beginPath(); g.arc(x, y, size, 0, 7); g.fill();
  }
  g.globalCompositeOperation = 'source-over';
  // round clock: minute arc (outer) and hour arc (inner) over faint tracks
  const d = new Date(), min = (d.getMinutes() + d.getSeconds() / 60) / 60, hr = ((d.getHours() % 12) + d.getMinutes() / 60) / 12;
  g.lineCap = 'round';
  for (const [frac, rad, col, width] of [[min, R * .86, tint, 6], [hr, R * .74, cool, 10]]) {
    g.strokeStyle = 'rgba(255,255,255,.08)'; g.lineWidth = width * Live.dpr; g.beginPath(); g.arc(cx, cy, rad, 0, 7); g.stroke();
    g.strokeStyle = Live.rgba(col, .95); g.beginPath(); g.arc(cx, cy, rad, -Math.PI / 2, -Math.PI / 2 + frac * Math.PI * 2); g.stroke();
  }
  const size = R * .52;
  Live.text(Live.time(d), cx, cy - size * .05, size, 200, '#FFFFFF');
  Live.text(Live.date(d), cx, cy + size * .62, size * .2, 400, 'rgba(240,235,255,.9)');
  // the song under the ring (or a calm waiting line)
  const ty = cy + R * 1.35, ts = Math.max(26 * Live.dpr, R * .13);
  if (s.title) {
    Live.text(Live.fit(s.title, ts, 600, w * .8), cx, ty, ts, 600, '#FFFFFF');
    if (s.artist) Live.text(Live.fit(s.artist, ts * .65, 400, w * .7), cx, ty + ts * 1.05, ts * .65, 400, 'rgba(235,228,255,.85)');
  }
  Live.drawToast(now, cy - R * 1.15);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
