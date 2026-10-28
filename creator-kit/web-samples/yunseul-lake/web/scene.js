// 윤슬 호수: a painted night lake (made locally with Z-Image-Turbo, Apache-2.0; see ART.txt) with the clock in the sky.
// Moonlight glints (윤슬) shimmer on the water and flare on each estimated beat, a ripple spreads from the moon path,
// the charge level is the moon's halo and the mood-light colour is mixed into the light. Tap the water = play/pause, swipe = next/previous.
const {s, g} = Live;
// Painted plates: horizon = where the water starts, moon = centre of the painted moon, r = its radius (fractions of the image).
const ART = {
  wide: {src: 'lake-wide.webp', horizon: .583, moon: [.687, .238], r: .06},
  tall: {src: 'lake-tall.webp', horizon: .56, moon: [.697, .156], r: .097}
};
for (const a of Object.values(ART)) { a.img = new Image(); a.img.src = a.src; }

const rand = (i => () => (i = (i * 16807) % 2147483647) / 2147483647)(7);   // seeded so every car shows the same water
const gauss = () => (rand() + rand() + rand() - 1.5) / 1.5;
const glints = Array.from({length: 220}, () => ({d: Math.pow(rand(), .8), u: gauss(), p: rand() * 7, v: .6 + rand() * 1.6, j: rand() * 7}));
const bars = Array.from({length: 26}, (_, i) => ({d: .03 + Math.pow(i / 25, 1.35) * .85, o: rand() * .25, j: [gauss(), gauss(), gauss()]}));
const stars = Array.from({length: 60}, () => ({x: rand(), y: rand() * .8, p: rand() * 7}));
const Q0 = {kick: 0, snare: 0, hat: 0, energy: 0, drop: 0};
const ripples = []; let lastBeat = 0, phase = 0, prev = 0, glow = 0, kPrev = 0;

Live.onGesture((x, y) => y > place().hz / Live.h, {
  tap: () => Live.ask('media.playPause', s.playing ? '일시정지' : '재생'),
  left: () => Live.ask('media.next', '다음 곡'), right: () => Live.ask('media.prev', '이전 곡')});

/** Cover-fit the plate for this window and return where its horizon and moon land on the canvas. */
function place() {
  const {w, h} = Live, art = w < h ? ART.tall : ART.wide, iw = art.img.naturalWidth || 16, ih = art.img.naturalHeight || 9;
  const k = Math.max(w / iw, h / ih), dx = (w - iw * k) / 2, dy = (h - ih * k) / 2;
  return {art, k, dx, dy, iw, ih, hz: dy + art.horizon * ih * k, mx: dx + art.moon[0] * iw * k, my: dy + art.moon[1] * ih * k, mr: art.r * iw * k};
}

function frame(now) {
  const {w, h, dpr} = Live, portrait = w < h, still = s.infoOnly, P = place(), hz = P.hz;
  const dt = prev ? Math.min(.1, (now - prev) / 1000) : 0; prev = now;
  if (!still) phase += dt * (s.playing ? 1 : .45);
  const moon = [255, 240, 214], tint = Live.hex(s.ambient, '#9DB8E8'), light = Live.mix(moon, tint, .35);
  const G = still ? Q0 : Live.groove(Date.now()), b = G.kick, kickHit = b > .5 && b > kPrev + .25; kPrev = b;
  glow += ((s.playing ? .4 + G.energy * .3 + b * .4 : .4) - glow) * Math.min(1, dt * 6);

  // plate
  if (P.art.img.complete && P.art.img.naturalWidth) g.drawImage(P.art.img, P.dx, P.dy, P.iw * P.k, P.ih * P.k);
  else { const f = g.createLinearGradient(0, 0, 0, h); f.addColorStop(0, '#0b1426'); f.addColorStop(1, '#03060c'); g.fillStyle = f; g.fillRect(0, 0, w, h); }
  // mood light washes the whole scene, stronger when the cabin light is brighter
  g.save(); g.globalCompositeOperation = 'soft-light'; g.globalAlpha = .12 + .22 * ((s.ambientBrightness ?? 60) / 100);
  g.fillStyle = Live.rgba(tint, 1); g.fillRect(0, 0, w, h); g.restore();

  g.save(); g.globalCompositeOperation = 'lighter';
  // twinkling stars (sky only)
  for (const st of stars) { const y = st.y * hz * .85; g.globalAlpha = .12 + .18 * Math.sin(phase * 1.7 + st.p) ** 2; g.fillStyle = '#fff'; g.beginPath(); g.arc(st.x * w, y, .9 * dpr, 0, 7); g.fill(); }
  // drifting mist on the horizon
  for (let i = 0; i < 3; i++) {
    const x = ((i * .37 + phase * (.006 + i * .003)) % 1.4 - .2) * w, y = hz - (h - hz) * (.05 + i * .04), r = w * (.35 + i * .1);
    const m = g.createRadialGradient(x, y, 0, x, y, r); m.addColorStop(0, Live.rgba(Live.mix([150, 170, 200], tint, .3), .07)); m.addColorStop(1, 'rgba(0,0,0,0)');
    g.globalAlpha = 1; g.fillStyle = m; g.save(); g.translate(x, y); g.scale(1, .18); g.translate(-x, -y); g.fillRect(x - r, y - r, r * 2, r * 2); g.restore();
  }
  // glints: a column under the moon that widens towards the viewer, each one twinkling on its own clock
  for (const p of glints) {
    const y = hz + (h - hz) * p.d, spread = w * (.03 + p.d * .2), x = P.mx + p.u * spread + (still ? 0 : Math.sin(phase * .8 + p.j) * 3 * dpr);
    const tw = still ? .45 : Math.max(0, Math.sin(phase * p.v * 2.2 + p.p)) ** 3, a = tw * (1 - Math.min(1, Math.abs(p.u))) * glow;
    if (a < .02) continue;
    const len = (2 + p.d * 16) * dpr * (1 + b * .5), thick = (.6 + p.d * 1.8) * dpr;
    g.globalAlpha = Math.min(1, a); g.fillStyle = Live.rgba(light, 1); g.fillRect(x - len / 2, y - thick / 2, len, thick);
  }
  // moonlight equalizer: the reflection is cut into brush-stroke rows. Near rows follow the kick, middle rows the snare,
  // far rows the hi-hat of the virtual groove; with no music they just breathe with the water.
  for (let i = 0; i < bars.length; i++) {
    const r = bars[i], y = hz + (h - hz) * r.d;
    const env = (r.d > .55 ? G.kick : r.d > .25 ? G.snare * .9 : G.hat * .8) * (.55 + .45 * Math.sin(i * 2.1 + r.o * 20 + phase * .7) ** 2);
    const calm = .75 + .25 * Math.sin(phase * 1.3 + i * .9), half = w * (.015 + r.d * .07) * calm * (1 + env * .9);
    const thick = (1 + r.d * 3.2) * dpr, cx0 = P.mx + (still ? 0 : Math.sin(phase * .6 + i) * 4 * dpr);
    for (let k = 0; k < 3; k++) {   // three offset strokes per row read as one hand-painted dash
      g.globalAlpha = (.16 + env * .45) * glow * (1 - k * .25); g.fillStyle = Live.rgba(light, 1);
      g.fillRect(cx0 - half * (1 - k * .18) + r.j[k] * half * .3, y + (k - 1) * thick * .7, half * 2 * (1 - k * .18), thick * (1 - k * .3));
    }
  }
  // a ripple on each estimated beat
  if (!still && kickHit && now - lastBeat > 200) { lastBeat = now; ripples.push(now); }
  for (let i = ripples.length - 1; i >= 0; i--) {
    const age = (now - ripples[i]) / 2200; if (age > 1 || still) { ripples.splice(i, 1); continue; }
    g.globalAlpha = (1 - age) ** 2 * .35; g.strokeStyle = Live.rgba(light, 1); g.lineWidth = 1.5 * dpr;
    g.beginPath(); g.ellipse(P.mx, hz + (h - hz) * .4, age * w * .28, age * (h - hz) * .07, 0, 0, 7); g.stroke();
  }
  // moon halo breathes with the music
  // (transparent inside the disc so the painted moon keeps its texture)
  const halo = g.createRadialGradient(P.mx, P.my, P.mr * .98, P.mx, P.my, P.mr * (3 + glow * 1.5));
  halo.addColorStop(0, 'rgba(0,0,0,0)'); halo.addColorStop(.04, Live.rgba(light, .16 + b * .1 + G.drop * .25)); halo.addColorStop(1, 'rgba(0,0,0,0)');
  g.globalAlpha = 1; g.fillStyle = halo; g.fillRect(P.mx - P.mr * 7, P.my - P.mr * 7, P.mr * 14, P.mr * 14);
  g.restore();

  // charge level: a thin ring around the moon, drawn from the top; a light runs along it while charging
  if (s.level != null) {
    const R = P.mr * 1.4, end = -Math.PI / 2 + Math.PI * 2 * s.level / 100;
    g.save(); g.lineCap = 'round'; g.lineWidth = Math.max(2, P.mr * .07);
    g.strokeStyle = 'rgba(255,255,255,.14)'; g.beginPath(); g.arc(P.mx, P.my, R, 0, 7); g.stroke();
    g.strokeStyle = Live.rgba(light, .85); g.shadowColor = Live.rgba(light, .8); g.shadowBlur = P.mr * .4;
    g.beginPath(); g.arc(P.mx, P.my, R, -Math.PI / 2, end); g.stroke();
    if (s.charging && !still) {   // a light with a short tail runs along the ring; it fades in at the start and out at the end
      const u = (now / 2600) % 1, fade = Math.sin(Math.PI * u) ** 1.5, ease = u * u * (3 - 2 * u), span = end + Math.PI / 2;
      for (let i = 0; i < 8; i++) { const t = -Math.PI / 2 + span * Math.max(0, ease - i * .018); g.globalAlpha = fade * (1 - i / 8); g.fillStyle = '#fff'; g.beginPath(); g.arc(P.mx + Math.cos(t) * R, P.my + Math.sin(t) * R, g.lineWidth * (1.3 - i * .1), 0, 7); g.fill(); }
      g.globalAlpha = 1;
    }
    g.restore();
    const ls = Math.max(16 * dpr, P.mr * .4); Live.text((s.charging ? '충전 ' : '') + s.level + '%', P.mx, P.my + R + ls * 1.1, ls, 500, 'rgba(244,241,234,.92)');
  }

  // clock in the sky, with a soft shade behind it so it reads on any frame
  const size = Math.min(w * (portrait ? .17 : .14), hz * .34), cx = w * (portrait ? .3 : .34), cy = portrait ? P.my : hz * .36;   // portrait: beside the moon
  const shade = g.createRadialGradient(cx, cy, 0, cx, cy, size * 2.2); shade.addColorStop(0, 'rgba(4,8,18,.35)'); shade.addColorStop(1, 'rgba(4,8,18,0)');
  g.fillStyle = shade; g.fillRect(cx - size * 2.2, cy - size * 2.2, size * 4.4, size * 4.4);
  Live.text(Live.time(), cx, cy, size, 200, '#F4F1EA');
  Live.text(Live.date(), cx, cy + size * .66, size * .2, 400, 'rgba(236,232,222,.9)');

  // the song rests on the water, its album colour as a faint underline
  if (s.title) {
    const ts = Math.max(24 * dpr, size * .22), fy = hz + (h - hz) * (portrait ? .62 : .66);
    Live.text(Live.fit(s.title, ts, 600, w * .8), w / 2, fy, ts, 600, '#FFFFFF', 'center', s.playing ? 1 : .7);
    if (s.artist) Live.text(Live.fit(s.artist, ts * .64, 400, w * .7), w / 2, fy + ts * 1.05, ts * .64, 400, 'rgba(232,236,246,.82)');
    const album = Live.hex(s.album, '#F2D9A6'), lw = Math.min(w * .3, ts * 6) * (s.playing ? .6 + b * .4 : .4);
    g.fillStyle = Live.rgba(album, .7); g.fillRect(w / 2 - lw / 2, fy + ts * 1.7, lw, Math.max(2, 2 * dpr));
  }
  Live.drawToast(now, hz + (h - hz) * .3);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
