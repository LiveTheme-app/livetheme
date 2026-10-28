// 봄날 연못: a painted spring pond (Z-Image-Turbo, Apache-2.0). The plate moves only where its mask says: the pond ripples and the
// blossoms sway. On top: sun rays, falling petals that gust on each estimated beat and float when they reach the water,
// a row of ten blossoms under the clock that open with the charge level, and the mood-light colour in the petals.
// Tap the pond = play/pause, swipe = next/previous.
const {s, g} = Live;
const ART = Plate.load({
  wide: {src: 'wide.webp', mask: 'wide-mask.png', horizon: .625, sun: [.645, .04]},
  tall: {src: 'tall.webp', mask: 'tall-mask.png', horizon: .662, sun: [.88, .03]}
});
const rand = (i => () => (i = (i * 16807) % 2147483647) / 2147483647)(11);
const petals = Array.from({length: 90}, () => fresh({y: rand()}));
const Q0 = {kick: 0, snare: 0, hat: 0, energy: 0, drop: 0};
let phase = 0, prev = 0, wind = 0, lastBeat = 0, kPrev = 0, dPrev = 0;

function fresh(p = {}) {
  return Object.assign(p, {x: rand() * 1.2 - .1, y: p.y ?? -.05 - rand() * .1, z: .4 + rand() * .9, spin: rand() * 7, vs: .5 + rand() * 1.5,
    fall: .025 + rand() * .03, sway: rand() * 7, float: 0});
}
Live.onGesture((x, y) => { const P = Plate.place(ART, Live.w, Live.h); return y > P.y(P.art.horizon) / Live.h; }, {
  tap: () => Live.ask('media.playPause', s.playing ? '일시정지' : '재생'),
  left: () => Live.ask('media.next', '다음 곡'), right: () => Live.ask('media.prev', '이전 곡')});

function petal(x, y, r, a, color, alpha) {
  g.save(); g.translate(x, y); g.rotate(a); g.globalAlpha = alpha; g.fillStyle = color;
  g.beginPath(); g.moveTo(0, -r); g.bezierCurveTo(r * .9, -r * .6, r * .7, r * .7, 0, r); g.bezierCurveTo(-r * .7, r * .7, -r * .9, -r * .6, 0, -r); g.fill(); g.restore();
}
function blossom(x, y, r, open, color) {
  for (let i = 0; i < 5; i++) { const a = i / 5 * Math.PI * 2 - Math.PI / 2; petal(x + Math.cos(a) * r * .55 * open, y + Math.sin(a) * r * .55 * open, r * .5 * (.4 + .6 * open), a + Math.PI / 2, color, .95); }
  g.fillStyle = '#F7D36B'; g.beginPath(); g.arc(x, y, r * .16, 0, 7); g.fill();
}

function frame(now) {
  const {w, h, dpr} = Live, portrait = w < h, still = s.infoOnly, P = Plate.place(ART, w, h), hz = P.y(P.art.horizon);
  const dt = prev ? Math.min(.1, (now - prev) / 1000) : 0; prev = now; if (!still) phase += dt;
  const G = still ? Q0 : Live.groove(Date.now()), b = G.kick, tint = Live.hex(s.ambient, '#FFC6D9');
  if (!still && b > .5 && b > kPrev + .25 && now - lastBeat > 200) { lastBeat = now; wind = Math.min(2.2, wind + .4 + G.energy * .6); }
  if (!still && G.drop > .9 && dPrev <= .9) for (let i = 0; i < 30; i++) Object.assign(petals[Math.floor(rand() * petals.length)], {x: rand() * .5, y: rand() * .3, float: 0});   // drop: a burst from the tree
  kPrev = b; dPrev = G.drop;
  wind *= Math.exp(-dt * 1.6);
  Plate.draw(P, w, h, {t: phase, beat: G.snare, ripple: 1, sway: .6 + wind * .5 + G.energy * .3, shimmer: .7, tint, tintAmt: .08 + .12 * ((s.ambientBrightness ?? 50) / 100), still});
  g.clearRect(0, 0, w, h);

  // sun rays: soft wedges from the sun, swinging slowly and brightening on the beat
  const sx = P.x(P.art.sun[0]), sy = P.y(P.art.sun[1]);
  g.save(); g.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 6; i++) {
    const a = Math.PI * (.55 + i * .09) + Math.sin(phase * .15 + i) * .03, len = h * 1.3, spread = .025 + (i % 3) * .012;
    const grad = g.createLinearGradient(sx, sy, sx + Math.cos(a) * len, sy + Math.sin(a) * len);
    grad.addColorStop(0, `rgba(255,248,225,${.09 + G.energy * .07 + G.hat * .04 + G.drop * .1})`); grad.addColorStop(1, 'rgba(255,248,225,0)');
    g.fillStyle = grad; g.beginPath(); g.moveTo(sx, sy); g.lineTo(sx + Math.cos(a - spread) * len, sy + Math.sin(a - spread) * len); g.lineTo(sx + Math.cos(a + spread) * len, sy + Math.sin(a + spread) * len); g.fill();
  }
  g.restore();

  // petals: fall with a breeze, gust on the beat, then float and fade on the pond
  const pink = Live.mix([255, 205, 222], tint, .3), pinkCss = Live.rgba(pink, 1);
  for (const p of petals) {
    if (!still) {
      if (p.float) { p.float += dt * .12; p.x += dt * .006; if (p.float > 1) fresh(p); }
      else {
        p.y += dt * p.fall * p.z; p.x += dt * (.012 + wind * .07) * p.z + Math.sin(phase * p.vs + p.sway) * dt * .01; p.spin += dt * p.vs * (1 + wind);
        if (p.x > 1.15) { p.x = -.1; }
        if (p.y * h > hz + (h - hz) * (p.z - .4) / .9 * .9) p.float = .001;   // nearer petals land lower on the pond
      }
    }
    const r = (5 + p.z * 7) * dpr, alpha = p.float ? (1 - p.float) * .85 : .9;
    petal(p.x * w, p.y * h, r, p.spin, pinkCss, alpha);
  }

  // clock on a frosted card in the sky; under it ten blossoms open with the charge level, the next one breathes while charging
  const size = Math.min(w * (portrait ? .17 : .1), h * .13), cx = portrait ? w * .5 : w * .36, cy = portrait ? h * .16 : h * .19;
  const rows = s.level != null ? 1.95 : 1.55;
  Plate.card('clock', [cx - size * 1.75, cy - size * .72, size * 3.5, size * rows], dpr, {bg: 'rgba(40,52,90,.18)', border: '1px solid rgba(255,255,255,.4)', radius: size / dpr * .28, shadow: '0 10px 40px rgba(30,40,80,.18)'});
  Live.text(Live.time(), cx, cy, size, 300, '#FFFFFF');
  Live.text(Live.date(), cx, cy + size * .6, size * .19, 500, 'rgba(255,255,255,.96)');
  if (s.level != null) {
    const n = Math.round(s.level / 10), r = size * .1, gap = r * 2.5, x0 = cx - gap * 4.5, y0 = cy + size * .95;
    for (let i = 0; i < 10; i++) {
      const lit = i < n, pulse = s.charging && i === n && !still ? .5 + .5 * Math.sin(now / 300) : 0;
      if (lit || pulse) blossom(x0 + i * gap, y0, r, lit ? 1 : pulse * .8, pinkCss);
      else { g.strokeStyle = 'rgba(255,255,255,.7)'; g.lineWidth = Math.max(1, dpr); g.beginPath(); g.arc(x0 + i * gap, y0, r * .3, 0, 7); g.stroke(); }
    }
  }

  // the song on the pond
  const glass = {bg: 'rgba(40,52,90,.18)', border: '1px solid rgba(255,255,255,.4)', radius: size / dpr * .2};
  Plate.card('song', null, dpr);
  if (s.title) {
    const ts = Math.max(22 * dpr, size * .2), fy = hz + (h - hz) * .55;
    g.font = `700 ${Math.round(ts)}px sans-serif`; const tw = Math.min(w * .8, Math.max(g.measureText(s.title).width, ts * 6)) + ts * 2;
    Plate.card('song', [w / 2 - tw / 2, fy - ts * 1.1, tw, ts * (s.artist ? 3.1 : 2.2)], dpr, glass);
    Live.text(Live.fit(s.title, ts, 700, w * .8), w / 2, fy, ts, 700, '#FFFFFF', 'center', s.playing ? 1 : .75);
    if (s.artist) Live.text(Live.fit(s.artist, ts * .66, 500, w * .7), w / 2, fy + ts * 1.1, ts * .66, 500, 'rgba(255,255,255,.9)');
  }
  Live.drawToast(now, hz + (h - hz) * .3);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
