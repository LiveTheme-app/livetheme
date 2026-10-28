// 비 오는 골목: a rainy Seoul alley at night (Z-Image-Turbo, Apache-2.0) seen through the windshield. The wet road ripples, the neon
// signs take the cabin mood-light colour and pulse on each estimated beat, rings open in the puddles on the beat, raindrops gather
// on the glass and a wiper clears them when the song changes. The clock is a neon sign; the charge level is a neon tube.
// Tap the road = play/pause, swipe = next/previous.
const {s, g} = Live;
const ART = Plate.load({
  wide: {src: 'wide.webp', mask: 'wide-mask.png', vp: [.5, .58]},
  tall: {src: 'tall.webp', mask: 'tall-mask.png', vp: [.5, .58]}
});
const rand = (i => () => (i = (i * 16807) % 2147483647) / 2147483647)(5);
const rain = Array.from({length: 240}, () => ({x: rand(), y: rand(), z: .3 + rand() * .7}));
const drops = Array.from({length: 34}, () => drop({}));
const Q0 = {kick: 0, snare: 0, hat: 0, energy: 0, drop: 0};
const rings = []; let phase = 0, prev = 0, lastBeat = 0, lastRing = 0, song = null, wipe = -1, kPrev = 0, sPrev = 0;

function drop(d) { return Object.assign(d, {x: rand(), y: rand() * .9, r: 3 + rand() * 10, slide: rand() < .25 ? .01 + rand() * .03 : 0, a: 0}); }
Live.onGesture((x, y) => y > .5, {
  tap: () => Live.ask('media.playPause', s.playing ? '일시정지' : '재생'),
  left: () => Live.ask('media.next', '다음 곡'), right: () => Live.ask('media.prev', '이전 곡')});

/** Text lit like a neon sign: dark halo, coloured glow, pale core. */
function neon(str, x, y, size, color, flicker) {
  g.save(); g.font = `300 ${Math.round(size)}px "Noto Sans KR","Noto Sans CJK KR",sans-serif`; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.shadowColor = 'rgba(0,0,0,.6)'; g.shadowBlur = size * .25; g.fillStyle = 'rgba(0,0,0,.01)'; g.fillText(str, x, y);
  g.shadowColor = Live.rgba(color, .9); g.shadowBlur = size * (.18 + flicker * .2); g.fillStyle = Live.rgba(Live.mix(color, [255, 255, 255], .65), .95 - flicker * .1);
  g.fillText(str, x, y); g.shadowBlur = size * .05; g.fillText(str, x, y); g.restore();
}

function frame(now) {
  const {w, h, dpr} = Live, portrait = w < h, still = s.infoOnly, P = Plate.place(ART, w, h);
  const dt = prev ? Math.min(.1, (now - prev) / 1000) : 0; prev = now; if (!still) phase += dt;
  const G = still ? Q0 : Live.groove(Date.now()), b = G.kick, tint = Live.hex(s.ambient, '#59F0E0');
  Plate.draw(P, w, h, {t: phase, beat: Math.min(1, b * .8 + G.drop), ripple: .9, shimmer: .5, neon: .85, tint, tintAmt: .05, still});
  g.clearRect(0, 0, w, h);
  // bottom shade so the song reads over bright puddles
  const shade = g.createLinearGradient(0, h * .7, 0, h); shade.addColorStop(0, 'rgba(3,6,12,0)'); shade.addColorStop(1, 'rgba(3,6,12,.55)');
  g.fillStyle = shade; g.fillRect(0, h * .7, w, h * .3);

  // puddle rings: one on each beat, a few more from the rain itself
  const vx = P.x(P.art.vp[0]), vy = P.y(P.art.vp[1]);
  const spawn = () => { const d = .15 + rand() * .85, y = vy + (h - vy) * d; rings.push({x: vx + (rand() - .5) * w * .9 * d, y, d, t: now}); };
  if (!still && b > .5 && b > kPrev + .25 && now - lastBeat > 200) { lastBeat = now; spawn(); spawn(); }
  if (!still && G.snare > .5 && G.snare > sPrev + .25) spawn();
  kPrev = b; sPrev = G.snare;
  if (!still && now - lastRing > 180) { lastRing = now; spawn(); }
  g.save(); g.globalCompositeOperation = 'lighter';
  for (let i = rings.length - 1; i >= 0; i--) {
    const r = rings[i], age = (now - r.t) / 900; if (age > 1 || still) { rings.splice(i, 1); continue; }
    g.globalAlpha = (1 - age) * .35; g.strokeStyle = Live.rgba(Live.mix([200, 220, 255], tint, .3), 1); g.lineWidth = dpr;
    g.beginPath(); g.ellipse(r.x, r.y, age * 34 * r.d * dpr + 2, age * 8 * r.d * dpr + 1, 0, 0, 7); g.stroke();
  }
  // rain streaks
  g.strokeStyle = 'rgba(190,210,240,1)'; g.lineCap = 'round';
  for (const d of rain) {
    if (!still) { d.y += dt * (1.2 + d.z * 1.4) * (1 + G.energy * .4); d.x += dt * .05; if (d.y > 1.05) { d.y = -.05; d.x = rand(); } if (d.x > 1) d.x -= 1; }
    const len = (12 + d.z * 26) * dpr; g.globalAlpha = .1 + d.z * .22; g.lineWidth = (.5 + d.z) * dpr;
    g.beginPath(); g.moveTo(d.x * w, d.y * h); g.lineTo(d.x * w - len * .08, d.y * h - len); g.stroke();
  }
  g.restore();
  // the drop of a song: lightning over the rooftops
  if (G.drop > .05) { const fl = g.createLinearGradient(0, 0, 0, h * .75); fl.addColorStop(0, `rgba(210,225,255,${G.drop * .38 * (.6 + .4 * Math.sin(now * .05))})`); fl.addColorStop(1, 'rgba(210,225,255,0)'); g.fillStyle = fl; g.fillRect(0, 0, w, h * .75); }

  // drops on the windshield; a new song sends the wiper across
  if (s.title !== song) { if (song && !still) wipe = 0; song = s.title; }
  const pivot = [w * .5, h * 1.08], reach = Math.hypot(w * .62, h * 1.1);
  let blade = null;
  if (wipe >= 0) { wipe += dt / 1.1; if (wipe >= 1) wipe = -1; else blade = Math.PI * (1.15 + .7 * Math.sin(wipe * Math.PI)); }
  for (const d of drops) {
    if (!still) {
      d.a = Math.min(1, d.a + dt * .25);
      if (d.slide) { d.y += dt * d.slide; if (d.y > 1.05) drop(d); }
      if (blade !== null) { const ang = Math.atan2(d.y * h - pivot[1], d.x * w - pivot[0]) + Math.PI * 2; if (Math.abs(((ang - blade) % (Math.PI * 2) + Math.PI * 3) % (Math.PI * 2) - Math.PI) < .06) { drop(d); d.a = 0; } }
    }
    const x = d.x * w, y = d.y * h, r = d.r * dpr; if (d.a < .05) continue;
    const lens = g.createRadialGradient(x - r * .25, y + r * .3, r * .1, x, y, r);
    lens.addColorStop(0, `rgba(255,255,255,${.28 * d.a})`); lens.addColorStop(.7, `rgba(150,180,210,${.08 * d.a})`); lens.addColorStop(1, `rgba(10,15,25,${.35 * d.a})`);
    g.fillStyle = lens; g.beginPath(); g.ellipse(x, y, r, r * 1.1, 0, 0, 7); g.fill();
  }
  if (blade !== null) {
    g.save(); g.strokeStyle = 'rgba(8,10,14,.85)'; g.lineWidth = 7 * dpr; g.lineCap = 'round';
    g.beginPath(); g.moveTo(pivot[0], pivot[1]); g.lineTo(pivot[0] + Math.cos(blade) * reach, pivot[1] + Math.sin(blade) * reach); g.stroke(); g.restore();
  }

  // neon clock over the rooftops, a neon tube for the charge level
  const size = Math.min(w * (portrait ? .2 : .11), h * .14), cx = w / 2, cy = h * (portrait ? .13 : .16), flick = G.snare * .8;
  neon(Live.time(), cx, cy, size, tint, flick);
  Live.text(Live.date(), cx, cy + size * .62, size * .18, 500, 'rgba(235,242,255,.9)');
  if (s.level != null) {
    const tw = size * 2.2, th = size * .11, x0 = cx - tw / 2, y0 = cy + size * .9, fill = tw * s.level / 100;
    g.save(); g.lineWidth = Math.max(1.5, dpr * 1.5); g.strokeStyle = 'rgba(220,235,255,.35)';
    g.beginPath(); g.roundRect(x0, y0, tw, th, th / 2); g.stroke();
    g.shadowColor = Live.rgba(tint, .9); g.shadowBlur = th * 2.2; g.fillStyle = Live.rgba(Live.mix(tint, [255, 255, 255], .35), .95);
    g.beginPath(); g.roundRect(x0, y0, Math.max(th, fill), th, th / 2); g.fill();
    if (s.charging && !still) {   // a soft light flows through the filled part, fading in at the start and out at the end
      const u = (now / 1800) % 1, fade = Math.sin(Math.PI * u) ** 1.5, px = x0 + th + (Math.max(th, fill) - th * 2) * u * u * (3 - 2 * u);
      const glow = g.createRadialGradient(px, y0 + th / 2, 0, px, y0 + th / 2, th * 3); glow.addColorStop(0, `rgba(255,255,255,${.95 * fade})`); glow.addColorStop(1, 'rgba(255,255,255,0)');
      g.save(); g.beginPath(); g.roundRect(x0, y0, Math.max(th, fill), th, th / 2); g.clip(); g.fillStyle = glow; g.fillRect(px - th * 3, y0, th * 6, th); g.restore();
    }
    g.restore();
    Live.text((s.charging ? '충전 ' : '') + s.level + '%', cx, y0 + th + size * .2, size * .15, 500, 'rgba(235,242,255,.85)');
  }
  if (s.title) {
    const ts = Math.max(22 * dpr, size * .2), fy = h * (portrait ? .86 : .84);
    Live.text(Live.fit(s.title, ts, 600, w * .8), w / 2, fy, ts, 600, '#FFFFFF', 'center', s.playing ? 1 : .7);
    if (s.artist) Live.text(Live.fit(s.artist, ts * .66, 400, w * .7), w / 2, fy + ts * 1.1, ts * .66, 400, 'rgba(225,235,250,.85)');
  }
  Live.drawToast(now, h * .7);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
