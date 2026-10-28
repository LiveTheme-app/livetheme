// Hand-drawn look for the LiveTheme character samples, all drawn in code (no images, no existing character or IP):
// ink lines that "boil" 8 times a second like a flip-book, a watercolour paper background tinted by the mood light,
// doodled sun / moon / stars, paper signs and cards, a battery cushion, music notes and sparkles.
// The boil, notes and sparkles freeze while the car only allows info updates (driving).
const Sketch = (() => {
  const {g} = Live, TAU = Math.PI * 2, INK = '#4A3550';
  let seed = 1, lastBoil = 0;
  const hash = i => { const v = Math.sin(i * 12.9898 + 4.1) * 43758.5453; return v - Math.floor(v); };
  const rnd = i => hash(i + seed * 101);
  function tick(now, still) { if (!still && now - lastBoil > 125) { lastBoil = now; seed = seed % 7 + 1; } }

  // smooth curve through points (quadratic through the midpoints)
  function trace(pts, closed) {
    const n = pts.length; g.beginPath();
    if (closed) {
      g.moveTo((pts[n - 1][0] + pts[0][0]) / 2, (pts[n - 1][1] + pts[0][1]) / 2);
      for (let i = 0; i < n; i++) { const p = pts[i], q = pts[(i + 1) % n]; g.quadraticCurveTo(p[0], p[1], (p[0] + q[0]) / 2, (p[1] + q[1]) / 2); }
      g.closePath();
    } else {
      g.moveTo(pts[0][0], pts[0][1]);
      for (let i = 1; i < n - 1; i++) { const p = pts[i], q = pts[i + 1]; g.quadraticCurveTo(p[0], p[1], (p[0] + q[0]) / 2, (p[1] + q[1]) / 2); }
      g.lineTo(pts[n - 1][0], pts[n - 1][1]);
    }
  }
  const jit = (pts, amp, k) => pts.map(([x, y], i) => [x + (rnd(i + k) - .5) * amp, y + (rnd(i + k + 57) - .5) * amp]);
  /** Filled shape with a wobbly ink outline and a faint second pencil pass. `key` makes shapes wobble differently. */
  function shape(pts, fill, lw, key = 0, ink = INK) {
    trace(jit(pts, lw * .5, key), true);
    if (fill) { g.fillStyle = fill; g.fill(); }
    if (!lw) return;
    g.lineJoin = g.lineCap = 'round'; g.strokeStyle = ink; g.lineWidth = lw; g.stroke();
    g.save(); g.globalAlpha *= .3; trace(jit(pts, lw * 1.3, key + 13), true); g.lineWidth = lw * .6; g.stroke(); g.restore();
  }
  function line(pts, lw, key = 0, ink = INK) { trace(jit(pts, lw * .6, key), false); g.lineJoin = g.lineCap = 'round'; g.strokeStyle = ink; g.lineWidth = lw; g.stroke(); }
  function oval(cx, cy, rx, ry, rot = 0, n = 20) {
    const out = [], c = Math.cos(rot), s = Math.sin(rot);
    for (let i = 0; i < n; i++) { const a = i / n * TAU, x = Math.cos(a) * rx, y = Math.sin(a) * ry; out.push([cx + x * c - y * s, cy + x * s + y * c]); }
    return out;
  }
  function box(x, y, w, h, r, n = 4) {
    const out = [];
    for (const [cx, cy, a0] of [[x + w - r, y + r, -Math.PI / 2], [x + w - r, y + h - r, 0], [x + r, y + h - r, Math.PI / 2], [x + r, y + r, Math.PI]])
      for (let i = 0; i <= n; i++) { const a = a0 + i / n * Math.PI / 2; out.push([cx + Math.cos(a) * r, cy + Math.sin(a) * r]); }
    return out;
  }

  // watercolour paper: soft blots with darker edges + grain + vignette, rebuilt only when size, tint or day/night change
  const paper = document.createElement('canvas'); let paperKey = '';
  function background(w, h, tint, night) {
    const key = w + 'x' + h + tint.join() + night;
    if (key !== paperKey) { paperKey = key; paint(w, h, tint, night); }
    g.drawImage(paper, 0, 0);
  }
  function paint(w, h, tint, night) {
    paper.width = w; paper.height = h; const p = paper.getContext('2d'), m = Math.max(w, h);
    p.fillStyle = Live.rgba(night ? Live.mix([34, 34, 70], tint, .18) : Live.mix([252, 246, 236], tint, .1), 1); p.fillRect(0, 0, w, h);
    for (let i = 0; i < 34; i++) {
      const x = hash(i) * w, y = hash(i + 40) * h * .85, r = m * (.06 + hash(i + 80) * .16);
      const c = Live.mix(tint, night ? [70, 60, 150] : [255, 214, 170], hash(i + 120) * .7), a = (night ? .12 : .14) * (.5 + hash(i + 160));
      const blot = p.createRadialGradient(x, y, r * .1, x, y, r);
      blot.addColorStop(0, Live.rgba(c, a * .55)); blot.addColorStop(.86, Live.rgba(c, a)); blot.addColorStop(1, Live.rgba(c, 0));
      p.fillStyle = blot; p.beginPath(); p.arc(x, y, r, 0, TAU); p.fill();
    }
    const n = document.createElement('canvas'); n.width = n.height = 128; const q = n.getContext('2d'), img = q.createImageData(128, 128);
    for (let i = 0; i < img.data.length; i += 4) { img.data[i] = img.data[i + 1] = img.data[i + 2] = hash(i * .37) * 255; img.data[i + 3] = 20; }
    q.putImageData(img, 0, 0); p.fillStyle = p.createPattern(n, 'repeat'); p.fillRect(0, 0, w, h);
    const v = p.createRadialGradient(w / 2, h * .45, m * .2, w / 2, h * .45, m * .75);
    v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, night ? 'rgba(8,6,24,.45)' : 'rgba(120,90,70,.12)'); p.fillStyle = v; p.fillRect(0, 0, w, h);
  }

  function sun(x, y, r, t) {
    g.fillStyle = 'rgba(255,214,120,.22)'; g.beginPath(); g.arc(x, y, r * 2, 0, TAU); g.fill();
    for (let i = 0; i < 10; i++) {
      const a = i / 10 * TAU + t * .15, r0 = r * 1.3, r1 = r * (1.6 + (i % 2) * .22);
      line([[x + Math.cos(a) * r0, y + Math.sin(a) * r0], [x + Math.cos(a) * r1, y + Math.sin(a) * r1]], r * .1, i * 3, '#E39A35');
    }
    shape(oval(x, y, r, r), '#FFD27A', r * .08, 300, '#E39A35');
  }
  function moon(x, y, r) {
    g.fillStyle = 'rgba(255,240,200,.14)'; g.beginPath(); g.arc(x, y, r * 2.1, 0, TAU); g.fill();
    shape(oval(x, y, r, r), '#FFF1C9', r * .07, 310, '#C9A86A');
    g.fillStyle = 'rgba(201,168,106,.35)';
    for (const [dx, dy, k] of [[-.3, -.2, .18], [.28, .15, .12], [-.08, .38, .09]]) { g.beginPath(); g.arc(x + dx * r, y + dy * r, k * r, 0, TAU); g.fill(); }
  }
  function stars(w, top, t, u) {
    g.save(); g.strokeStyle = '#FFF4D6'; g.lineCap = 'round';
    for (let i = 0; i < 28; i++) {
      const x = hash(i + 500) * w, y = hash(i + 540) * top, r = u * (.012 + hash(i + 580) * .02);
      g.globalAlpha = .45 + .45 * Math.sin(t * 1.7 + i * 2.1); g.lineWidth = r * .45;
      g.beginPath(); g.moveTo(x - r, y); g.lineTo(x + r, y); g.moveTo(x, y - r); g.lineTo(x, y + r); g.stroke();
    }
    g.restore();
  }
  function ground(w, h, y, night, lw) {
    const pts = [[-30, y]]; for (let i = 1; i < 16; i++) pts.push([w * i / 15, y + Math.sin(i * 1.3) * h * .01]);
    pts.push([w + 30, y], [w + 30, h + 30], [-30, h + 30]);
    shape(pts, night ? '#3D4870' : '#CFE7C1', lw, 400);
    for (let i = 0; i < 9; i++) { const x = w * (.05 + hash(i + 700) * .9), yy = y + h * (.03 + hash(i + 720) * .1), k = lw * 3;
      const c = night ? 'rgba(190,200,240,.5)' : '#7DB57F';   // a grass tuft: three separate blades
      for (const [dx, tx, ty] of [[-.5, -.9, 1.3], [0, .1, 1.8], [.5, .9, 1.2]]) line([[x + dx * k, yy], [x + tx * k, yy - ty * k]], lw * .7, 410 + i, c); }
  }

  /** Text without a halo (dark ink on paper); shrinks to fit maxW. */
  function label(str, x, y, size, weight, color, maxW, align = 'center') {
    const font = px => `${weight} ${Math.round(px)}px "Noto Sans KR","Noto Sans CJK KR",sans-serif`;
    g.font = font(size); const mw = g.measureText(str).width;
    if (maxW && mw > maxW) { size *= maxW / mw; g.font = font(size); }
    g.textAlign = align; g.textBaseline = 'middle'; g.fillStyle = color; g.fillText(str, x, y);
  }
  /** A paper card; lines = [[text, px, weight, color]], centred, shrunk together when they do not fit. */
  function card(x, y, cw, ch, lines, lw, key = 600) {
    shape(box(x, y, cw, ch, Math.min(cw, ch) * .16), 'rgba(255,252,244,.95)', lw, key);
    const total = lines.reduce((a, l) => a + l[1] * 1.32, 0), k = Math.min(1, ch * .84 / total);
    let yy = y + (ch - total * k) / 2;
    for (const [str, px, weight, color] of lines) { yy += px * k * .66; label(str, x + cw / 2, yy, px * k, weight, color, cw * .88); yy += px * k * .66; }
  }
  /** Battery as a cushion, filled left to right (level 0..100); a soft wave rolls on its edge while charging. */
  function battery(x, y, bw, bh, level, charging, t, still, lw, key = 800) {
    const lv = Math.max(0, Math.min(100, level || 0)) / 100, body = box(x, y, bw, bh, bh * .3);
    shape(box(x + bw - lw, y + bh * .3, bw * .09, bh * .4, bw * .03, 2), '#F7E6C4', lw, key + 1);
    shape(body, '#FFF8EA', 0, key);
    g.save(); trace(body, true); g.clip();
    const fx = x + bw * lv; g.fillStyle = lv < .2 ? '#F49C8B' : '#8FD8A8'; g.beginPath(); g.moveTo(x - 2, y - 2);
    for (let i = 0; i <= 10; i++) g.lineTo(fx + (charging && !still ? Math.sin(t * 5 + i * .9) * bw * .025 : 0), y + bh * i / 10);
    g.lineTo(x - 2, y + bh + 2); g.closePath(); g.fill(); g.restore();
    shape(body, null, lw, key);
    label(Math.round(level || 0) + '%', x + bw / 2, y + bh * .53, bh * .46, 700, INK, bw * .8);
  }

  const notes = [];
  function note(x, y, u, color) {
    notes.push({x, y, at: performance.now(), glyph: notes.length % 2 ? '♪' : '♫', dx: (hash(performance.now() % 997) - .5) * u * .7, color});
    if (notes.length > 12) notes.shift();
  }
  function drawNotes(now, u, still) {
    for (let i = notes.length - 1; i >= 0; i--) {
      const n = notes[i], age = (now - n.at) / 2200; if (age > 1 || still) { notes.splice(i, 1); continue; }
      label(n.glyph, n.x + n.dx * age + Math.sin(age * 9) * u * .05, n.y - age * u * 1.1, u * .24, 700, Live.rgba(n.color, 1 - age * age));
    }
  }
  function zzz(x, y, u, t, color) {
    for (let i = 0; i < 3; i++) { const k = (t * .45 + i / 3) % 1; label('z', x + k * u * .35, y - k * u * .6, u * (.1 + k * .13), 700, Live.rgba(color, Math.sin(k * Math.PI))); }
  }
  function sparkle(x, y, r, color) {
    g.fillStyle = color; g.beginPath();
    for (let i = 0; i < 8; i++) { const a = i / 8 * TAU, rr = i % 2 ? r * .32 : r; g.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr); }
    g.closePath(); g.fill();
  }
  /** "10월 26일 월" — short enough for a small sign. */
  function shortDate(d = new Date()) { return `${d.getMonth() + 1}월 ${d.getDate()}일 ${'일월화수목금토'[d.getDay()]}`; }
  return {INK, TAU, hash, tick, trace, shape, line, oval, box, background, sun, moon, stars, ground, label, card, battery, note, drawNotes, zzz, sparkle, shortDate};
})();
