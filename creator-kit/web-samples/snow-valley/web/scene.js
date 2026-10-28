// 눈 내린 숲: a quiet snowy valley at blue hour (Z-Image-Turbo, Apache-2.0) with a small, spaced-out clock in the empty sky.
// The fog between the ridges drifts and the snow glitters (plate mask); snow falls in three depths and gusts on each estimated
// beat. An aurora in the mood-light colour appears while music plays and grows bright and full while the car is charging.
// Tap the valley = play/pause, swipe = next/previous.
const {s, g} = Live;
const ART = Plate.load({
  wide: {src: 'wide.webp', mask: 'wide-mask.png', sky: .3},
  tall: {src: 'tall.webp', mask: 'tall-mask.png', sky: .26}
});
const rand = (i => () => (i = (i * 16807) % 2147483647) / 2147483647)(3);
const flakes = Array.from({length: 230}, (_, i) => ({x: rand(), y: rand(), z: i < 150 ? .25 + rand() * .3 : i < 215 ? .55 + rand() * .3 : .9 + rand() * .4, p: rand() * 7}));
const Q0 = {kick: 0, snare: 0, hat: 0, energy: 0, drop: 0};
let phase = 0, prev = 0, wind = 0, lastBeat = 0, aurora = 0, kPrev = 0;
Live.onGesture((x, y) => y > .45, {
  tap: () => Live.ask('media.playPause', s.playing ? '일시정지' : '재생'),
  left: () => Live.ask('media.next', '다음 곡'), right: () => Live.ask('media.prev', '이전 곡')});

/** Thin, widely spaced text (minimal wallpaper typography). letterSpacing is skipped where the WebView lacks it. */
function spaced(str, x, y, size, weight, color, spacing) {
  g.save(); g.font = `${weight} ${Math.round(size)}px "Noto Sans KR","Noto Sans CJK KR",sans-serif`; g.textAlign = 'center'; g.textBaseline = 'middle';
  if ('letterSpacing' in g) g.letterSpacing = spacing + 'px';
  g.shadowColor = 'rgba(5,10,25,.45)'; g.shadowBlur = size * .3; g.fillStyle = color; g.fillText(str, x + (('letterSpacing' in g) ? spacing / 2 : 0), y); g.restore();
}

function frame(now) {
  const {w, h, dpr} = Live, portrait = w < h, still = s.infoOnly, P = Plate.place(ART, w, h);
  const dt = prev ? Math.min(.1, (now - prev) / 1000) : 0; prev = now; if (!still) phase += dt;
  const G = still ? Q0 : Live.groove(Date.now()), b = G.kick, tint = Live.hex(s.ambient, '#7FE0C0');
  if (!still && b > .5 && b > kPrev + .25 && now - lastBeat > 200) { lastBeat = now; wind = Math.min(1.6, wind + .25 + G.energy * .4); }
  kPrev = b;
  wind *= Math.exp(-dt * 1.4);
  aurora += ((s.charging ? 1 : s.playing ? .2 + G.energy * .5 : 0) - aurora) * Math.min(1, dt * 1.5);
  Plate.draw(P, w, h, {t: phase, beat: G.hat, sway: .7, shimmer: 1, tint, tintAmt: .05, still});
  g.clearRect(0, 0, w, h);

  // aurora curtains: columns of light hanging from a slowly waving line, in the mood-light colour
  if (aurora > .02) {
    g.save(); g.globalCompositeOperation = 'lighter';
    const base = P.y(P.art.sky), step = 5 * dpr;
    for (let r = 0; r < 3; r++) {
      const hgt = h * (.16 + r * .05), col = Live.mix(tint, [140, 255, 200], .25 + r * .15);
      const grad = g.createLinearGradient(0, 0, 0, -hgt); grad.addColorStop(0, Live.rgba(col, .0)); grad.addColorStop(.08, Live.rgba(col, .55)); grad.addColorStop(.5, Live.rgba(col, .18)); grad.addColorStop(1, Live.rgba(col, 0));
      g.fillStyle = grad;
      for (let x = 0; x < w; x += step) {
        const u = x / w, y = base - r * h * .05 + Math.sin(u * 5 + phase * .25 + r) * h * .04 + Math.sin(u * 13 - phase * .18) * h * .015;
        const a = (.5 + .5 * Math.sin(u * 40 + phase * (1.2 + r * .3) + r * 2)) * Math.sin(Math.PI * Math.min(1, Math.max(0, u * 1.2 - .1))) * aurora * (.3 + b * .25 + G.drop * .6);
        if (a < .01) continue;
        g.globalAlpha = a; g.save(); g.translate(0, y); g.fillRect(x, -hgt, step + 1, hgt * 1.05); g.restore();
      }
    }
    g.restore();
  }

  // snow in three depths; the nearest flakes are large and soft
  for (const f of flakes) {
    if (!still) {
      f.y += dt * (.02 + f.z * .05); f.x += dt * (Math.sin(phase * .6 + f.p) * .01 + wind * .08 * f.z);
      if (f.y > 1.03) { f.y = -.03; f.x = rand(); } if (f.x > 1.03) f.x -= 1.06;
    }
    const r = f.z * 2.4 * dpr, x = f.x * w, y = f.y * h;
    if (f.z > .9) { const s2 = g.createRadialGradient(x, y, 0, x, y, r * 2.2); s2.addColorStop(0, 'rgba(255,255,255,.55)'); s2.addColorStop(1, 'rgba(255,255,255,0)'); g.fillStyle = s2; g.fillRect(x - r * 2.2, y - r * 2.2, r * 4.4, r * 4.4); }
    else { g.globalAlpha = .45 + f.z * .4; g.fillStyle = '#fff'; g.beginPath(); g.arc(x, y, r, 0, 7); g.fill(); g.globalAlpha = 1; }
  }

  // minimal clock: small, thin and spaced, with the charge as a hairline under it
  const size = Math.min(w * (portrait ? .13 : .065), h * .09), cx = w / 2, cy = h * (portrait ? .17 : .19);
  spaced(Live.time(), cx, cy, size, 200, 'rgba(245,248,255,.97)', size * .22);
  spaced(Live.date(), cx, cy + size * .78, size * .2, 400, 'rgba(225,232,248,.9)', size * .06);
  if (s.level != null) {
    const lw = size * 2.4, y0 = cy + size * 1.2;
    g.fillStyle = 'rgba(255,255,255,.22)'; g.fillRect(cx - lw / 2, y0, lw, Math.max(1, dpr));
    g.fillStyle = Live.rgba(Live.mix(tint, [255, 255, 255], .5), .95); g.fillRect(cx - lw / 2, y0, lw * s.level / 100, Math.max(1, dpr) * 2);
    spaced((s.charging ? '충전  ' : '') + s.level + '%', cx, y0 + size * .32, size * .17, 400, 'rgba(225,232,248,.85)', size * .05);
  }
  if (s.title) {
    const ts = Math.max(18 * dpr, size * .24), fy = h * .9;
    spaced(Live.fit(s.title + (s.artist ? '  ·  ' + s.artist : ''), ts, 400, w * .8), w / 2, fy, ts, 400, s.playing ? 'rgba(245,248,255,.95)' : 'rgba(245,248,255,.65)', ts * .08);
  }
  Live.drawToast(now, h * .78);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
