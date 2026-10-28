// 오로라 시계: aurora curtains in the mood-light colour sway with the music volume and the estimated beat; a thin, very large clock;
// the battery is the light band on the horizon (its length = charge, it breathes while charging). Tap the sky = play/pause.
const {s, g} = Live;
let phase = 0, prev = 0, sway = .3;
Live.onGesture(() => true, {tap: () => Live.ask('media.playPause', s.playing ? '일시정지' : '재생'), left: () => {}, right: () => {}});

function frame(now) {
  const {w, h} = Live, portrait = w < h, still = s.infoOnly;
  const dt = prev ? Math.min(.1, (now - prev) / 1000) : 0; prev = now;
  if (!still) phase += dt * (s.playing ? .8 : .3);
  sway += ((s.playing ? .35 + s.volume * .8 : .25) - sway) * Math.min(1, dt * 1.5);
  const tint = Live.hex(s.ambient || s.album, '#6EF2C5'), second = Live.mix(tint, [150, 110, 255], .55), b = still ? 0 : Live.beat(Date.now());
  g.fillStyle = '#05070f'; g.fillRect(0, 0, w, h);
  const sky = g.createLinearGradient(0, 0, 0, h); sky.addColorStop(0, '#070b1a'); sky.addColorStop(1, Live.rgba(Live.mix([5, 8, 18], tint, .12), 1)); g.fillStyle = sky; g.fillRect(0, 0, w, h);
  // aurora: vertical streaks along a wandering curve, brighter on each estimated beat
  g.globalCompositeOperation = 'lighter';
  for (let band = 0; band < 3; band++) {
    const color = band === 1 ? second : tint, top = h * (portrait ? .12 : .08) + band * h * .06;
    for (let i = 0; i <= 90; i++) {
      const u = i / 90, x = u * w;
      const y = top + Math.sin(u * 4 + phase * (1 + band * .4) + band) * h * .05 * (1 + sway) + Math.sin(u * 11 - phase * 2) * h * .015 * sway;
      const len = h * (.18 + .12 * Math.sin(u * 7 + phase + band)) * (1 + b * .25);
      const grad = g.createLinearGradient(0, y, 0, y + len);
      const a = (.2 + .12 * Math.sin(u * 13 + phase * 3)) * (1 + b * .6) * (band === 2 ? .6 : 1);
      grad.addColorStop(0, Live.rgba(color, 0)); grad.addColorStop(.25, Live.rgba(color, a)); grad.addColorStop(1, Live.rgba(color, 0));
      g.fillStyle = grad; g.fillRect(x - w / 180, y, w / 90 + 1, len);
    }
  }
  g.globalCompositeOperation = 'source-over';
  // horizon and the battery light band
  const hz = h * (portrait ? .82 : .78);
  g.fillStyle = '#03050b'; g.beginPath(); g.moveTo(0, h);
  for (let x = 0; x <= w; x += w / 40) g.lineTo(x, hz + Math.sin(x / w * 9) * h * .012 + Math.sin(x / w * 23) * h * .005);
  g.lineTo(w, h); g.closePath(); g.fill();
  if (s.level != null) {
    const len = w * .8 * Math.max(.02, s.level / 100), x0 = (w - w * .8) / 2, glow = s.charging && !still ? .6 + .4 * Math.sin(now / 500) : .8;
    const band = g.createLinearGradient(x0, 0, x0 + len, 0); band.addColorStop(0, Live.rgba(tint, 0)); band.addColorStop(.15, Live.rgba(tint, .9 * glow)); band.addColorStop(1, Live.rgba([255, 255, 255], glow));
    g.fillStyle = band; g.fillRect(x0, hz - 3 * Live.dpr, len, 4 * Live.dpr);
    g.fillStyle = Live.rgba(tint, .14 * glow); g.fillRect(x0, hz - 30 * Live.dpr, len, 30 * Live.dpr);
    Live.text(`배터리 ${Math.round(s.level)}%${s.charging ? ' · 충전 중' : ''}`, w / 2, hz + Math.max(28, h * .04), Math.max(24 * Live.dpr, h * .026), 500, 'rgba(235,245,255,.92)');
  } else Live.text('배터리 정보를 기다리는 중', w / 2, hz + Math.max(28, h * .04), Math.max(22 * Live.dpr, h * .024), 400, 'rgba(220,230,250,.7)');
  // the clock: thin, large; the song as a quiet line under the date
  const size = Math.min(w * (portrait ? .3 : .2), h * (portrait ? .2 : .28)), cy = h * (portrait ? .42 : .46);
  Live.text(Live.time(), w / 2, cy, size, 100, '#F7FBFF');
  Live.text(Live.date(), w / 2, cy + size * .6, size * .17, 400, 'rgba(230,240,255,.9)');
  if (s.title) Live.text(Live.fit((s.playing ? '♪ ' : '‖ ') + s.title + (s.artist ? ' · ' + s.artist : ''), size * .15, 400, w * .8), w / 2, cy + size * .9, size * .15, 400, 'rgba(225,238,255,.8)');
  Live.drawToast(now, cy + size * 1.2);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
