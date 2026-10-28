// 픽셀 노을: a 16-bit sunset sea (Z-Image-Turbo, Apache-2.0) kept crisp with nearest-pixel sampling. Everything on top is drawn
// into a low-resolution buffer and scaled up, so it stays pixel art: a bitmap-font clock, a pixel battery, twinkling stars,
// the lighthouse lamp blinking and its beam sweeping in time with the estimated beat, sparkles on the water on each beat,
// and a few gulls flying across when the song changes. Tap the sea = play/pause, swipe = next/previous.
const {s, g} = Live;
const ART = Plate.load({
  wide: {src: 'wide.webp', mask: 'wide-mask.png', pixel: true, horizon: .65, sun: .525, lamp: [.792, .525]},
  tall: {src: 'tall.webp', mask: 'tall-mask.png', pixel: true, horizon: .646, sun: .5, lamp: [.787, .562]}
});
// 5x7 glyphs, one string per row ('#' = pixel)
const FONT = {
  0: ['.###.', '#...#', '#..##', '#.#.#', '##..#', '#...#', '.###.'], 1: ['..#..', '.##..', '..#..', '..#..', '..#..', '..#..', '.###.'],
  2: ['.###.', '#...#', '....#', '...#.', '..#..', '.#...', '#####'], 3: ['####.', '....#', '....#', '.###.', '....#', '....#', '####.'],
  4: ['...#.', '..##.', '.#.#.', '#..#.', '#####', '...#.', '...#.'], 5: ['#####', '#....', '####.', '....#', '....#', '#...#', '.###.'],
  6: ['..##.', '.#...', '#....', '####.', '#...#', '#...#', '.###.'], 7: ['#####', '....#', '...#.', '..#..', '.#...', '.#...', '.#...'],
  8: ['.###.', '#...#', '#...#', '.###.', '#...#', '#...#', '.###.'], 9: ['.###.', '#...#', '#...#', '.####', '....#', '...#.', '.##..'],
  ':': ['.', '#', '#', '.', '#', '#', '.'], '.': ['.', '.', '.', '.', '.', '.', '#'], ' ': ['..', '..', '..', '..', '..', '..', '..'], '%': ['##..#', '##.#.', '...#.', '..#..', '.#...', '.#.##', '#..##'],
  A: ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'], D: ['####.', '#...#', '#...#', '#...#', '#...#', '#...#', '####.'],
  E: ['#####', '#....', '#....', '####.', '#....', '#....', '#####'], F: ['#####', '#....', '#....', '####.', '#....', '#....', '#....'],
  H: ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'], I: ['###', '.#.', '.#.', '.#.', '.#.', '.#.', '###'],
  M: ['#...#', '##.##', '#.#.#', '#.#.#', '#...#', '#...#', '#...#'], N: ['#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#', '#...#'],
  O: ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'], R: ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
  S: ['.####', '#....', '#....', '.###.', '....#', '....#', '####.'], T: ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '..#..'],
  U: ['#...#', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'], W: ['#...#', '#...#', '#...#', '#.#.#', '#.#.#', '##.##', '#...#']
};
const DAYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const buf = document.createElement('canvas'), q = buf.getContext('2d');
const rand = (i => () => (i = (i * 16807) % 2147483647) / 2147483647)(9);
const stars = Array.from({length: 40}, () => ({x: rand(), y: rand() * .3, p: rand() * 7}));
const Q0 = {kick: 0, snare: 0, hat: 0, energy: 0, drop: 0};
const sparks = []; let phase = 0, prev = 0, lastBeat = 0, song = null, gulls = null, beam = 0, kPrev = 0, hPrev = 0, dPrev = 0, meteor = null;
Live.onGesture((x, y) => { const P = Plate.place(ART, Live.w, Live.h); return y > P.y(P.art.horizon) / Live.h; }, {
  tap: () => Live.ask('media.playPause', s.playing ? '일시정지' : '재생'),
  left: () => Live.ask('media.next', '다음 곡'), right: () => Live.ask('media.prev', '이전 곡')});

function textWidth(str, k) { let n = 0; for (const c of str) n += ((FONT[c] || FONT[' '])[0].length + 1) * k; return n - k; }
/** Bitmap text into the low-res buffer, with a one-pixel drop shadow. */
function pix(str, x, y, k, color, shadow = '#2A1840') {
  for (const [col, off] of [[shadow, Math.max(1, Math.round(k / 2))], [color, 0]]) {
    q.fillStyle = col; let cx = Math.round(x);
    for (const c of str) { const gl = FONT[c] || FONT[' ']; gl.forEach((row, ry) => { for (let rx = 0; rx < row.length; rx++) if (row[rx] === '#') q.fillRect(cx + rx * k + off, Math.round(y) + ry * k + off, k, k); }); cx += (gl[0].length + 1) * k; }
  }
}

function frame(now) {
  const {w, h, dpr} = Live, portrait = w < h, still = s.infoOnly, P = Plate.place(ART, w, h);
  const dt = prev ? Math.min(.1, (now - prev) / 1000) : 0; prev = now; if (!still) phase += dt;
  const G = still ? Q0 : Live.groove(Date.now()), b = G.kick, tint = Live.hex(s.ambient, '#FFB36B');
  Plate.draw(P, w, h, {t: phase, beat: G.snare, ripple: .6, shimmer: .9, tint, tintAmt: .06, still});
  // low-res buffer: about 320 pixels across, one buffer pixel = one art pixel-ish
  const ps = Math.max(2, Math.round((portrait ? w / 200 : w / 320))), bw = Math.ceil(w / ps), bh = Math.ceil(h / ps);
  if (buf.width !== bw || buf.height !== bh) { buf.width = bw; buf.height = bh; }
  q.clearRect(0, 0, bw, bh);
  const hz = P.y(P.art.horizon) / ps, lx = P.x(P.art.lamp[0]) / ps, ly = P.y(P.art.lamp[1]) / ps, sx = P.x(P.art.sun) / ps;

  // stars
  for (const st of stars) { if (Math.sin(phase * 2 + st.p * 3) > .3) { q.fillStyle = '#FFF4E0'; q.fillRect(Math.floor(st.x * bw), Math.floor(st.y * bh), 1, 1); } }
  // lighthouse: the beam sweeps one turn per four beats (slow when nothing plays), the lamp flashes on the beat
  if (!still) beam += dt * (s.playing && s.bpm ? Math.PI * 2 * s.bpm / 60 / 4 : .6);
  const reach = bw * .45, dir = Math.cos(beam), lampColor = Live.mix([255, 236, 170], tint, .3);
  if (Math.abs(dir) > .05) {
    q.fillStyle = Live.rgba(lampColor, 1); const len = reach * Math.abs(dir);
    for (let i = 2; i < len; i++) { const half = Math.floor(i * .05); q.globalAlpha = .28 * (1 - i / len); q.fillRect(Math.round(lx + Math.sign(dir) * i), Math.round(ly - half), 1, half * 2 + 1); }
    q.globalAlpha = 1;
  }
  q.fillStyle = Live.rgba(Live.mix(lampColor, [255, 255, 255], b), 1); q.fillRect(Math.round(lx) - 1, Math.round(ly) - 1, 3, 2 + (b > .5 ? 1 : 0));
  // sparkles on the water, near the sun's reflection, on each beat
  const spark = () => sparks.push({x: sx + (rand() - .5) * bw * .25, y: hz + 2 + rand() * (bh - hz) * .6, t: now});
  if (!still && b > .5 && b > kPrev + .25 && now - lastBeat > 200) { lastBeat = now; spark(); spark(); spark(); }
  if (!still && G.hat > .4 && G.hat > hPrev + .2) spark();
  if (!still && G.drop > .9 && dPrev <= .9) meteor = {t: now, x: bw * (.15 + rand() * .4), y: bh * (.04 + rand() * .1)};   // drop: a shooting star
  kPrev = b; hPrev = G.hat; dPrev = G.drop;
  if (meteor) { const age = (now - meteor.t) / 900; if (age > 1) meteor = null; else for (let i = 0; i < 14; i++) { q.globalAlpha = (1 - i / 14) * (1 - age); q.fillStyle = '#FFF6D8'; q.fillRect(Math.round(meteor.x + age * 60 - i), Math.round(meteor.y + (age * 60 - i) * .4), 1, 1); } q.globalAlpha = 1; }
  for (let i = sparks.length - 1; i >= 0; i--) {
    const sp = sparks[i], age = (now - sp.t) / 500; if (age > 1 || still) { sparks.splice(i, 1); continue; }
    const r = age < .5 ? 1 : 2, x = Math.round(sp.x), y = Math.round(sp.y); q.fillStyle = '#FFF6D8';
    q.fillRect(x, y, 1, 1); if (age < .8) { q.fillRect(x - r, y, 1, 1); q.fillRect(x + r, y, 1, 1); q.fillRect(x, y - r, 1, 1); q.fillRect(x, y + r, 1, 1); }
  }
  // gulls cross the sky when a new song starts
  if (s.title !== song) { if (song && !still) gulls = {t: now, y: bh * (.18 + rand() * .15)}; song = s.title; }
  if (gulls) {
    const age = (now - gulls.t) / 7000; if (age > 1) gulls = null;
    else for (let i = 0; i < 3; i++) {
      const x = Math.round(-10 + age * (bw + 20) - i * 7), y = Math.round(gulls.y + i * 4 + Math.sin(age * 20 + i) * 1.5), up = Math.floor(now / 180 + i) % 2;
      q.fillStyle = '#2A1840'; q.fillRect(x - 2, y + (up ? -1 : 1), 2, 1); q.fillRect(x, y, 1, 1); q.fillRect(x + 1, y + (up ? -1 : 1), 2, 1);
    }
  }

  // bitmap clock and date
  const d = new Date(), time = Live.time(d), k = portrait ? 3 : 4, tx = Math.round(bw / 2 - textWidth(time, k) / 2), ty = Math.round(bh * (portrait ? .1 : .12));
  pix(time, tx, ty, k, '#FFF2D6');
  const date = `${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')} ${DAYS[d.getDay()]}`;
  pix(date, Math.round(bw / 2 - textWidth(date, 1) / 2), ty + 7 * k + 4, 1, '#FFE2C2');
  // pixel battery under the date: five cells, the next one blinks while charging
  if (s.level != null) {
    const cells = Math.ceil(s.level / 20), bx = Math.round(bw / 2 - 16), by = ty + 7 * k + 15;
    q.fillStyle = '#2A1840'; q.fillRect(bx + 1, by + 1, 26, 9); q.fillStyle = '#FFF2D6'; q.fillRect(bx, by, 26, 9); q.fillRect(bx + 26, by + 3, 2, 3);
    q.fillStyle = '#3A2558'; q.fillRect(bx + 1, by + 1, 24, 7);
    for (let i = 0; i < 5; i++) { const on = i < cells || (s.charging && i === cells && Math.floor(now / 400) % 2 === 0); if (on) { q.fillStyle = s.level <= 20 ? '#FF6B6B' : Live.rgba(Live.mix([255, 214, 120], tint, .25), 1); q.fillRect(bx + 2 + i * 5, by + 2, 4, 5); } }
    pix(s.level + '%', bx + 32, by + 1, 1, '#FFE2C2');
  }
  g.clearRect(0, 0, w, h); g.imageSmoothingEnabled = false; g.drawImage(buf, 0, 0, bw * ps, bh * ps);
  if (s.title) {
    const ts = Math.max(20 * dpr, ps * 6), fy = h * .88;
    g.save(); g.font = `700 ${Math.round(ts)}px "Noto Sans KR",sans-serif`; g.textAlign = 'center'; g.textBaseline = 'middle';
    const line = Live.fit(s.title + (s.artist ? ' - ' + s.artist : ''), ts, 700, w * .8);
    g.fillStyle = '#2A1840'; g.fillText(line, w / 2 + ps, fy + ps); g.fillStyle = s.playing ? '#FFF2D6' : '#D9C6B8'; g.fillText(line, w / 2, fy); g.restore();
  }
  Live.drawToast(now, h * .78);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
