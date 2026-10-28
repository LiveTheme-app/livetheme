// 몽글: an original cloud-mochi friend made for this sample (drawn in code; not based on any existing character or IP).
// - Clock: 몽글 holds up a garden sign with the time; the date and the battery are on its second line.
// - Music: it bobs its head to the ESTIMATED rhythm (from the song info, not the sound) and notes float up; no music = it dozes.
// - Charging: it hugs a battery cushion that fills up; at 100% it cheers. Night: nightcap, moon, stars. Day: sunshine and a sprout.
// - Tap 몽글 = play/pause (it blushes); tap elsewhere = it hops and looks over; swipe = next / previous song.
// - Driving (info only): the pose freezes; only the clock, song and battery text refresh.
const {s, g} = Live, K = Sketch, INK = K.INK, MUTED = '#6B5570';
let prev = 0, t = 0, blush = 0, hop = 0, look = 0, lastIdx = -1;
const me = {x: 0, y: 0, r: 1};
Live.onGesture(() => true, {
  tap: (x, y) => {
    const dx = x * Live.w - me.x, dy = y * Live.h - me.y;
    if (Math.hypot(dx, dy) < me.r * 1.2) { blush = 1; if (s.title) Live.ask('media.playPause', s.playing ? '쉿, 잠깐 멈출게요' : '같이 들어요!'); else Live.say('음악을 틀면 몽글이가 춤춰요'); }
    else { hop = 1; look = Math.sign(dx); }
  },
  left: () => Live.ask('media.next', '다음 곡!'), right: () => Live.ask('media.prev', '이전 곡')});

function bodyPts(rx, ry) {   // a cloud: scalloped on top, a little flatter at the bottom
  const out = [];
  for (let i = 0; i < 36; i++) {
    const a = i / 36 * K.TAU, sy = Math.sin(a), bump = 1 + .08 * Math.max(0, -sy) * Math.abs(Math.sin(a * 3));
    out.push([Math.cos(a) * rx * bump, sy * ry * (sy > 0 ? .9 : bump)]);
  }
  return out;
}

function frame(now) {
  const {w, h} = Live, portrait = w < h, still = s.infoOnly, night = s.night;
  const dt = prev ? Math.min(.1, (now - prev) / 1000) : 0; prev = now;
  if (!still) { t += dt; blush = Math.max(0, blush - dt * .45); hop = Math.max(0, hop - dt * 2.2); look *= Math.max(0, 1 - dt * .6); }
  K.tick(now, still);
  const tint = Live.hex(s.ambient || s.album, night ? '#8E7CE0' : '#F4A7B9');
  K.background(w, h, tint, night);
  // 1.37: no song card any more; 몽글 and the clock sign stand in the middle (music shows only through the dance and the notes)
  const u = portrait ? Math.min(w * .42, h * .26) : Math.min(w * .2, h * .31), lw = Math.max(2, u * .028);
  const cx = w * .5, gy = Math.min(h * .86, h * .5 + u * 1.05);
  if (night) { K.stars(w, gy * .8, t, u); K.moon(w * (portrait ? .82 : .87), h * .15, u * .2); } else K.sun(w * (portrait ? .82 : .87), h * .15, u * .19, t);
  K.ground(w, h, gy, night, lw);

  const charging = s.charging && s.level != null, full = charging && s.level >= 99.5, asleep = !s.playing && !charging;
  const b = still ? 0 : Live.beat(Date.now()), period = s.bpm ? 60000 / s.bpm : 0;
  const idx = s.playing && period ? Math.floor((Date.now() - s.since) / period) : -1, side = idx % 2 ? 1 : -1;
  const tilt = s.playing ? side * b * .1 : asleep ? -.06 : 0;
  const lift = still ? 0 : Math.sin(hop * Math.PI) * u * .3 + (full ? Math.abs(Math.sin(t * 5)) * u * .07 : 0);
  const breathe = still ? 0 : Math.sin(t * (asleep ? 1.4 : 2.6)) * .025, squash = s.playing ? b * .07 : 0;
  const rx = u * .56 * (1 + squash + breathe * .5), ry = u * .44 * (1 - squash + breathe), by = gy - ry * .9 - lift;
  me.x = cx; me.y = by; me.r = u * .6;

  // the sign: two posts planted behind 몽글, the board above its head
  const sw = u * (portrait ? 2.1 : 1.8), sh = u * .8, sTop = gy - u * 1.95, wob = still ? 0 : Math.sin(t * 1.2) * .015 + (s.playing ? side * b * .02 : 0);
  g.save(); g.translate(cx, gy); g.rotate(wob); g.translate(-cx, -gy);
  for (const k of [-1, 1]) K.shape(K.box(cx + k * sw * .3 - u * .04, sTop + sh * .8, u * .08, gy - sTop - sh * .8, u * .03, 2), '#D9B48A', lw, 20 + k);
  K.shape(K.box(cx - sw / 2, sTop, sw, sh, sh * .2), night ? '#F3E8D4' : '#FFF4DC', lw * 1.2, 30);
  K.label(Live.time(), cx, sTop + sh * .43, sh * .52, 700, INK, sw * .86);
  K.label(K.shortDate() + (s.level != null && !charging ? ` · 배터리 ${Math.round(s.level)}%` : ''), cx, sTop + sh * .81, sh * .15, 500, MUTED, sw * .86);
  g.restore();

  // shadow, then 몽글 (rotated around its feet for the head bob)
  g.fillStyle = 'rgba(60,40,70,.16)'; g.beginPath(); g.ellipse(cx, gy + u * .02, rx * (1 - lift / u * .5), u * .07, 0, 0, K.TAU); g.fill();
  g.save(); g.translate(cx, gy - lift); g.rotate(tilt); g.translate(0, by - gy + lift);
  for (const k of [-1, 1]) K.shape(K.oval(k * rx * .42, ry * .86, u * .13, u * .07), '#F4E6EE', lw, 40 + k);
  const fur = g.createRadialGradient(-rx * .3, -ry * .45, u * .05, 0, 0, rx * 1.1); fur.addColorStop(0, '#FFFEFA'); fur.addColorStop(1, '#ECE1F4');
  K.shape(bodyPts(rx, ry), fur, lw * 1.2, 50);
  // top: a sprout by day, a floppy nightcap at night
  if (night) {
    K.shape([[-rx * .55, -ry * .8], [-rx * .3, -ry * 1.3], [rx * .05, -ry * 1.5], [rx * .45, -ry * 1.38], [rx * .78, -ry * 1.08], [rx * .82, -ry * .9], [rx * .52, -ry * 1.0], [rx * .45, -ry * .82]], '#B9A6EE', lw, 60);
    K.line([[-rx * .55, -ry * .84], [0, -ry * .95], [rx * .47, -ry * .86]], lw * 3.2, 61, '#FFFDF6');
    K.shape(K.oval(rx * .84, -ry * .86, u * .065, u * .065), '#FFFDF6', lw, 62);
    for (const [px, py] of [[-.15, -1.25], [.3, -1.25], [.55, -1.05]]) K.sparkle(rx * px, ry * py, u * .035, '#FFF4C2');
  } else {
    K.line([[0, -ry * 1.02], [u * .015, -ry * 1.02 - u * .1], [u * .03, -ry * 1.02 - u * .15]], lw, 63, '#5E9E6C');
    K.shape(K.oval(-u * .05, -ry * 1.02 - u * .16, u * .07, u * .035, -.5), '#9ED9A8', lw, 64);
    K.shape(K.oval(u * .1, -ry * 1.02 - u * .19, u * .07, u * .035, .4), '#9ED9A8', lw, 65);
  }
  // face
  const ex = rx * .32, ey = -ry * .08, er = u * .05, lo = look * u * .025, happy = full || blush > .5;
  for (const k of [-1, 1]) {
    const x = k * ex + lo;
    if (asleep) K.line([[x - er, ey], [x, ey + er * .7], [x + er, ey]], lw * .9, 70 + k);
    else if (happy) K.line([[x - er, ey + er * .4], [x, ey - er * .6], [x + er, ey + er * .4]], lw * .9, 72 + k);
    else { g.fillStyle = INK; g.beginPath(); g.ellipse(x, ey, er * .85, er * 1.25, 0, 0, K.TAU); g.fill(); g.fillStyle = '#fff'; g.beginPath(); g.arc(x + er * .3, ey - er * .45, er * .32, 0, K.TAU); g.fill(); }
  }
  if (s.playing && b > .4) K.shape(K.oval(lo, ry * .22, u * .035, u * .03), '#C95C6E', lw * .7, 75);
  else K.line([[lo - u * .04, ry * .18], [lo, ry * .23], [lo + u * .04, ry * .18]], lw * .8, 76);
  g.fillStyle = `rgba(255,140,140,${.26 + blush * .55})`;
  for (const k of [-1, 1]) { g.beginPath(); g.ellipse(k * rx * .56 + lo, ry * .15, u * (.07 + .025 * blush), u * (.04 + .015 * blush), 0, 0, K.TAU); g.fill(); }
  // arms: holding the sign posts, pumping on the beat, resting while asleep, hugging the cushion while charging
  if (charging) {
    const bw = u * .8, bh = u * .38;
    K.battery(-bw / 2, ry * .08, bw, bh, s.level, s.charging, t, still, lw, 80);
    for (const k of [-1, 1]) K.shape(K.oval(k * bw * .5, ry * .3, u * .09, u * .12, k * .4), '#FBF5FB', lw, 85 + k);
  } else for (const k of [-1, 1]) {
    const up = s.playing ? (k === side ? b : 0) * u * .08 : 0, y = asleep ? ry * .3 : -ry * .1 - up;
    K.shape(K.oval(k * rx * .98, y, u * .09, u * .12, k * .3), '#FBF5FB', lw, 87 + k);
  }
  g.restore();

  if (asleep) K.zzz(cx + rx * .75, by - ry * 1.1, u, t, night ? [235, 228, 255] : [110, 85, 120]);
  if (full && !still) for (let i = 0; i < 5; i++) { const a = t * 1.5 + i / 5 * K.TAU; K.sparkle(cx + Math.cos(a) * u * .85, by + Math.sin(a) * u * .55, u * .05, '#FFD56B'); }
  if (idx !== lastIdx) { lastIdx = idx; if (idx >= 0 && !still) K.note(cx + side * u * 1.1, by - ry * .2, u, Live.mix(tint, [70, 40, 90], .45)); }
  K.drawNotes(now, u, still);

  Live.drawToast(now, sTop - u * .18);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
