// 방울: an original little round car made for this sample (drawn in code; not based on any existing character or IP).
// - Clock: a sign on 방울's roof shows the time; the date and the battery are on its second line.
// - Music: it bounces on its wheels to the ESTIMATED rhythm (from the song info, not the sound) and notes pop out; no music = it naps.
// - Charging: a charger post plugs into its side and fills up; at 100% it honks "빵빵!". Night: stars, moon, glowing headlights
//   (and curtains in the windshield while it naps). Day: sunshine.
// - Tap 방울 = play/pause (it blushes); tap elsewhere = a little hop and "빵!"; swipe = next / previous song.
// - Driving (info only): the pose freezes; only the clock, song and battery text refresh.
const {s, g} = Live, K = Sketch, INK = K.INK, MUTED = '#6B5570';
let prev = 0, t = 0, blush = 0, hop = 0, look = 0, lastIdx = -1, honk = 0;
const me = {x: 0, y: 0, r: 1};
Live.onGesture(() => true, {
  tap: (x, y) => {
    const dx = x * Live.w - me.x, dy = y * Live.h - me.y;
    if (Math.hypot(dx, dy) < me.r * 1.2) { blush = 1; if (s.title) Live.ask('media.playPause', s.playing ? '부릉… 잠깐 쉴게요' : '출발! 같이 들어요'); else Live.say('음악을 틀면 방울이가 신나요'); }
    else { hop = 1; honk = 1; look = Math.sign(dx); }
  },
  left: () => Live.ask('media.next', '다음 곡!'), right: () => Live.ask('media.prev', '이전 곡')});

function bubble(x, y, str, u, lw) {   // a little speech bubble
  const bw = u * .62, bh = u * .26;
  K.shape([...K.box(x - bw / 2, y - bh, bw, bh, bh * .4), [x - bw * .1, y], [x - bw * .2, y + bh * .35], [x - bw * .25, y]], '#FFFDF6', lw, 900);
  K.label(str, x, y - bh / 2, bh * .55, 800, '#D9534F', bw * .85);
}

function frame(now) {
  const {w, h} = Live, portrait = w < h, still = s.infoOnly, night = s.night;
  const dt = prev ? Math.min(.1, (now - prev) / 1000) : 0; prev = now;
  if (!still) { t += dt; blush = Math.max(0, blush - dt * .45); hop = Math.max(0, hop - dt * 2.2); honk = Math.max(0, honk - dt * .9); look *= Math.max(0, 1 - dt * .6); }
  K.tick(now, still);
  const tint = Live.hex(s.ambient || s.album, night ? '#6F8FE0' : '#9FD4E8');
  K.background(w, h, tint, night);
  const u = portrait ? Math.min(w * .42, h * .26) : Math.min(w * .2, h * .31), lw = Math.max(2, u * .028);
  const charging = s.charging && s.level != null, full = charging && s.level >= 99.5, asleep = !s.playing && !charging;
  // 1.37: no song card any more; 방울 and its roof clock sit in the middle (a little right while the charger stands on the left)
  const cx = w * (charging ? .54 : .5), gy = Math.min(h * .86, h * .5 + u * 1.05);
  if (night) { K.stars(w, gy * .8, t, u); K.moon(w * (portrait ? .82 : .87), h * .15, u * .2); } else K.sun(w * (portrait ? .82 : .87), h * .15, u * .19, t);
  K.ground(w, h, gy, night, lw);
  g.fillStyle = night ? 'rgba(40,46,80,.95)' : 'rgba(196,186,200,.95)'; g.fillRect(0, gy + (h - gy) * .16, w, (h - gy) * .28);   // the road
  for (let i = 0; i < 7; i++) K.line([[w * (i / 6.5) + u * .1, gy + (h - gy) * .3], [w * (i / 6.5) + u * .45, gy + (h - gy) * .3]], lw * 1.6, 450 + i, night ? 'rgba(255,250,220,.45)' : 'rgba(255,255,255,.9)');

  const b = still ? 0 : Live.beat(Date.now()), period = s.bpm ? 60000 / s.bpm : 0;
  const idx = s.playing && period ? Math.floor((Date.now() - s.since) / period) : -1, side = idx % 2 ? 1 : -1;
  const bounce = s.playing ? b * u * .05 : 0, breathe = still ? 0 : Math.sin(t * (asleep ? 1.3 : 2.4)) * u * .01;
  const lift = still ? 0 : Math.sin(hop * Math.PI) * u * .25 + (full ? Math.abs(Math.sin(t * 5)) * u * .1 : 0);
  const wheelY = gy - u * .11, by = gy - u * .45 + bounce - breathe - lift, tilt = s.playing ? side * b * .03 : 0;
  me.x = cx; me.y = by - u * .1; me.r = u * .7;

  // charger post on the left, its cable into 방울's side
  if (charging) {
    const px = cx - u * 1.05, pw = u * .34, ph = u * 1.0;
    K.shape(K.box(px, gy - ph, pw, ph, pw * .25), '#BFE6D2', lw, 501);
    const sx = px + pw * .14, sy = gy - ph * .86, swd = pw * .72, sht = ph * .5;
    K.shape(K.box(sx, sy, swd, sht, swd * .2), '#FFFDF6', lw * .8, 502);
    g.save(); K.trace(K.box(sx, sy, swd, sht, swd * .2), true); g.clip(); g.fillStyle = s.level < 20 ? '#F49C8B' : '#8FD8A8';
    const top = sy + sht * (1 - s.level / 100) + (s.charging && !still ? Math.sin(t * 4) * sht * .02 : 0); g.fillRect(sx, top, swd, sy + sht - top); g.restore();
    K.label(Math.round(s.level) + '%', sx + swd / 2, sy + sht * .5, swd * .36, 700, INK, swd * .9);
    K.label('⚡', px + pw / 2, gy - ph * .22, pw * .4, 700, '#E39A35');
    K.line([[px + pw, gy - ph * .3], [px + pw * 1.25, gy - u * .05], [cx - u * .7, gy - u * .1], [cx - u * .6, by + u * .05]], lw * 1.4, 500, '#5B4A60');
  }

  // shadow and wheels stay on the road; the body bounces
  g.fillStyle = 'rgba(60,40,70,.18)'; g.beginPath(); g.ellipse(cx, gy + u * .02, u * .7 * (1 - lift / u * .4), u * .07, 0, 0, K.TAU); g.fill();
  for (const k of [-1, 1]) K.shape(K.oval(cx + k * u * .43, wheelY - lift, u * .14, u * .12), '#5B4A60', lw, 510 + k);
  g.save(); g.translate(cx, by); g.rotate(tilt);
  const paint = night ? '#F0A39A' : '#FFB4A2';
  K.shape(K.box(-u * .43, -u * .58, u * .86, u * .42, u * .2), paint, lw * 1.2, 520);                 // cabin
  const glass = K.box(-u * .35, -u * .52, u * .7, u * .28, u * .12);
  K.shape(glass, night ? '#9DB7E8' : '#CFEAF7', lw, 521);
  if (asleep && night) { for (const k of [-1, 1]) K.shape([[k * u * .33, -u * .5], [k * u * .05, -u * .5], [k * u * .2, -u * .38], [k * u * .3, -u * .26]], '#C7B4F0', lw * .7, 522 + k); }
  else K.line([[-u * .22, -u * .44], [-u * .1, -u * .34]], lw * 1.4, 524, 'rgba(255,255,255,.85)');
  for (const k of [-1, 1]) K.shape(K.oval(k * u * .5, -u * .25, u * .08, u * .05, k * .3), paint, lw, 526 + k);   // mirrors
  K.shape(K.box(-u * .64, -u * .24, u * 1.28, u * .56, u * .24), paint, lw * 1.2, 530);                // body
  K.line([[-u * .55, u * .26], [u * .55, u * .26]], lw * 2.6, 531, '#FFE3D8');                         // bumper
  // headlight eyes (glow at night), grille smile, cheeks
  const ex = u * .3, ey = -u * .04, er = u * .12, lo = look * u * .03, happy = full || blush > .5 || honk > .5;
  for (const k of [-1, 1]) {
    const x = k * ex;
    if (night && !asleep) { const glow = g.createRadialGradient(x, ey, er * .5, x, ey, er * 2.6); glow.addColorStop(0, 'rgba(255,244,180,.5)'); glow.addColorStop(1, 'rgba(255,244,180,0)'); g.fillStyle = glow; g.beginPath(); g.arc(x, ey, er * 2.6, 0, K.TAU); g.fill(); }
    if (asleep) K.line([[x - er * .7, ey], [x, ey + er * .45], [x + er * .7, ey]], lw * 1.1, 540 + k);
    else if (happy) K.line([[x - er * .7, ey + er * .3], [x, ey - er * .45], [x + er * .7, ey + er * .3]], lw * 1.1, 542 + k);
    else {
      K.shape(K.oval(x, ey, er, er), night ? '#FFF6CF' : '#FFFFFF', lw, 544 + k);
      g.fillStyle = INK; g.beginPath(); g.arc(x + lo, ey + er * .1, er * .5, 0, K.TAU); g.fill();
      g.fillStyle = '#fff'; g.beginPath(); g.arc(x + lo + er * .18, ey - er * .12, er * .17, 0, K.TAU); g.fill();
    }
  }
  if ((s.playing && b > .4) || honk > .5) K.shape([[-u * .09, u * .1], [u * .09, u * .1], [u * .06, u * .19], [-u * .06, u * .19]], '#C95C6E', lw * .8, 550);
  else K.line([[-u * .09, u * .12], [0, u * .17], [u * .09, u * .12]], lw, 551);
  g.fillStyle = `rgba(255,120,120,${.25 + blush * .55})`;
  for (const k of [-1, 1]) { g.beginPath(); g.ellipse(k * u * .5, u * .1, u * (.06 + .025 * blush), u * (.035 + .015 * blush), 0, 0, K.TAU); g.fill(); }
  // roof sign with the clock
  const sw = u * (portrait ? 1.75 : 1.5), sh = u * .62, sTop = -u * .58 - u * .1 - sh;
  for (const k of [-1, 1]) K.line([[k * sw * .25, -u * .58], [k * sw * .25, sTop + sh]], lw * 1.6, 560 + k, '#8B6F86');
  K.shape(K.box(-sw / 2, sTop, sw, sh, sh * .22), night ? '#F3E8D4' : '#FFF4DC', lw * 1.2, 562);
  K.label(Live.time(), 0, sTop + sh * .42, sh * .54, 700, INK, sw * .86);
  K.label(K.shortDate() + (s.level != null && !charging ? ` · 배터리 ${Math.round(s.level)}%` : ''), 0, sTop + sh * .81, sh * .16, 500, MUTED, sw * .86);
  g.restore();
  const signTop = by + sTop;

  if (asleep) K.zzz(cx + u * .7, by - u * .45, u, t, night ? [235, 228, 255] : [110, 85, 120]);
  if (honk > 0 || full) bubble(cx + u * .98, by - u * .55, full ? '빵빵! 가득!' : '빵!', u, lw);
  if (full && !still) for (let i = 0; i < 6; i++) { const a = t * 1.4 + i / 6 * K.TAU; K.sparkle(cx + Math.cos(a) * u * .95, by - u * .2 + Math.sin(a) * u * .5, u * .05, i % 2 ? '#FFD56B' : '#9FD8F0'); }
  if (idx !== lastIdx) { lastIdx = idx; if (idx >= 0 && !still) K.note(cx + side * u * .95, by - u * .35, u, Live.mix(tint, [50, 40, 90], .55)); }
  K.drawNotes(now, u, still);

  Live.drawToast(now, signTop - u * .18);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
