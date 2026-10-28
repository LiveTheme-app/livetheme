// 충전 링: the ring fills with the car's charge level; while charging, light runs around it.
// Read-only sample — it only listens to livetheme data and never asks the car for anything.
const canvas = document.getElementById('scene'), g = canvas.getContext('2d');
let level = 0, shown = 0, charging = false, night = false, accent = '#7FE3C4', t = 0;

window.livetheme.on('data', data => {
  if (data.battery && typeof data.battery.level === 'number') level = data.battery.level;
  charging = !!(data.battery && data.battery.charging);
  night = !!data.night;
  if (data.ambient && data.ambient.color) accent = data.ambient.color;   // follow the mood light when the car reports it
});

function resize() { canvas.width = innerWidth * devicePixelRatio; canvas.height = innerHeight * devicePixelRatio; }
addEventListener('resize', resize); resize();

function frame(now) {
  t = now / 1000; shown += (level - shown) * 0.06;
  const w = canvas.width, h = canvas.height, cx = w * 0.62, cy = h * 0.52, r = Math.min(w, h) * 0.28;
  const sky = g.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, night ? '#05080f' : '#0d1a2c'); sky.addColorStop(1, night ? '#0a1020' : '#123047');
  g.fillStyle = sky; g.fillRect(0, 0, w, h);

  g.lineCap = 'round'; g.lineWidth = r * 0.09;
  g.strokeStyle = 'rgba(255,255,255,0.08)'; g.beginPath(); g.arc(cx, cy, r, 0, Math.PI * 2); g.stroke();
  const end = -Math.PI / 2 + Math.PI * 2 * Math.max(0, Math.min(1, shown / 100));
  g.strokeStyle = accent; g.shadowColor = accent; g.shadowBlur = r * 0.25;
  g.beginPath(); g.arc(cx, cy, r, -Math.PI / 2, end); g.stroke(); g.shadowBlur = 0;

  if (charging) {                       // running light around the ring
    for (let i = 0; i < 3; i++) {
      const a = -Math.PI / 2 + ((t * 0.35 + i / 3) % 1) * Math.PI * 2;
      g.fillStyle = 'rgba(255,255,255,0.8)'; g.beginPath(); g.arc(cx + Math.cos(a) * r, cy + Math.sin(a) * r, r * 0.035, 0, Math.PI * 2); g.fill();
    }
  }
  g.fillStyle = '#EAF2FF'; g.font = `300 ${r * 0.42}px sans-serif`; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.fillText(Math.round(shown) + '%', cx, cy);
  g.fillStyle = 'rgba(234,242,255,0.6)'; g.font = `${r * 0.11}px sans-serif`;
  g.fillText(charging ? '충전 중' : 'EV BATTERY', cx, cy + r * 0.38);
  requestAnimationFrame(frame);           // the app caps and pauses this loop; no extra timing needed here
}
requestAnimationFrame(frame);
