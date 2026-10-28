// 무드 오로라: aurora bands in the mood-light (or album) colour that move faster while music plays.
// Tap a colour dot to ask the car to change the mood light. The app decides: it asks the driver, refuses while driving
// and allows one request per 10 seconds; this page only shows the answer.
const canvas = document.getElementById('scene'), g = canvas.getContext('2d'), note = document.getElementById('note');
const SWATCHES = ['#8F7CFF', '#4FD6C8', '#FFB36B'];
let color = '#8F7CFF', playing = false, phase = 0, last = 0, noteTimer = 0;

window.livetheme.on('data', data => {
  color = (data.ambient && data.ambient.color) || (data.music && data.music.color) || color;
  playing = !!(data.music && data.music.playing);
});
window.livetheme.on('touch', async ({x, y}) => {
  const hit = SWATCHES.findIndex((_, i) => Math.hypot(x - dot(i).x, (y - dot(i).y) * innerHeight / innerWidth) < 0.025);
  if (hit < 0) return;
  const answer = await window.livetheme.request('ambient.color', SWATCHES[hit]);
  show(answer.ok ? '무드등 색을 바꿨어요' : {driving: '주행 중에는 바꿀 수 없어요', 'rate-limited': '잠시 후 다시 눌러주세요', declined: '바꾸지 않았어요', unsupported: '이 차량에서는 지원하지 않아요'}[answer.reason] || '요청할 수 없어요');
});
function dot(i) { return {x: 0.5 + (i - 1) * 0.05, y: 0.07}; }   // top centre: usually clear of widgets (taps on widgets never reach the background)
function show(text) { note.textContent = text; note.style.opacity = 1; clearTimeout(noteTimer); noteTimer = setTimeout(() => note.style.opacity = 0, 2500); }
function rgb(hex) { return [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16)); }

function resize() { canvas.width = innerWidth * devicePixelRatio; canvas.height = innerHeight * devicePixelRatio; }
addEventListener('resize', resize); resize();

function frame(now) {
  const dt = last ? Math.min(0.1, (now - last) / 1000) : 0; last = now; phase += dt * (playing ? 0.9 : 0.3);
  const w = canvas.width, h = canvas.height, [r, gr, b] = rgb(color);
  g.fillStyle = '#0c0a1c'; g.fillRect(0, 0, w, h);
  for (let band = 0; band < 4; band++) {
    const y0 = h * (0.25 + band * 0.12), grad = g.createLinearGradient(0, y0 - h * 0.2, 0, y0 + h * 0.2);
    grad.addColorStop(0, `rgba(${r},${gr},${b},0)`); grad.addColorStop(0.5, `rgba(${r},${gr},${b},${0.22 - band * 0.04})`); grad.addColorStop(1, `rgba(${r},${gr},${b},0)`);
    g.fillStyle = grad; g.beginPath(); g.moveTo(0, h);
    for (let x = 0; x <= w; x += w / 48) g.lineTo(x, y0 + Math.sin(x / w * 6 + phase * (1 + band * 0.3) + band) * h * 0.06);
    g.lineTo(w, h); g.closePath(); g.fill();
  }
  SWATCHES.forEach((c, i) => { const p = dot(i); g.fillStyle = c; g.beginPath(); g.arc(p.x * w, p.y * h, w * 0.012, 0, Math.PI * 2); g.fill(); });
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
