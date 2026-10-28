// Painted plate with masked motion, the way scene wallpapers animate one still picture: a mask image says where it moves.
// mask R = water (ripple), G = sway (blossoms, fog), B = light (sparkle on bright pixels, neon follows the mood light and the beat).
// WebGL when available; otherwise the plate is drawn still with 2D canvas. Each theme folder ships its own copy.
const Plate = (() => {
  const c = document.getElementById('plate');
  let gl = null, prog = null, u = {}, tex = [], art = null, k2d = null;
  try { gl = c.getContext('webgl', {alpha: false, antialias: false, preserveDrawingBuffer: true}); } catch (e) { gl = null; }
  const VS = 'attribute vec2 p;varying vec2 v;void main(){v=vec2(p.x*.5+.5,.5-p.y*.5);gl_Position=vec4(p,0.,1.);}';
  const FS = `precision mediump float;varying vec2 v;
uniform sampler2D img,msk;uniform vec2 sc,of;uniform vec3 tint;
uniform float t,beat,ripple,sway,shimmer,neon,tintAmt,still;
void main(){
 vec2 uv=v*sc+of;vec4 m=texture2D(msk,uv);
 float tt=t*(1.-still);
 uv.x+=m.r*ripple*(sin(uv.y*230.-tt*2.3)*.0016+sin(uv.y*71.+tt*1.2+uv.x*19.)*.0011);
 uv.y+=m.r*ripple*sin(uv.x*48.+tt*1.7)*.0010;
 uv+=m.g*sway*vec2(sin(tt*1.1+uv.y*13.+uv.x*5.)*.0024,cos(tt*.8+uv.x*10.)*.0015);
 vec3 col=texture2D(img,uv).rgb;
 float mx=max(col.r,max(col.g,col.b)),mn=min(col.r,min(col.g,col.b)),sat=(mx-mn)/(mx+.001);
 float n=m.b*smoothstep(.35,.7,sat)*smoothstep(.3,.75,mx);
 col=mix(col,tint*mx*1.1,n*neon);col+=col*n*neon*beat*.5;
 float l=dot(col,vec3(.299,.587,.114));
 vec2 cell=floor(uv*vec2(260.,520.));float hs=fract(sin(dot(cell,vec2(127.1,311.7)))*43758.5453);
 float sp=m.b*smoothstep(.5,.9,l)*step(.96,hs)*pow(max(0.,sin(tt*(1.5+hs*4.)+hs*60.)),6.);
 col+=sp*shimmer*(.45+beat)*vec3(1.,.96,.9);
 col=mix(col,(1.-2.*tint)*col*col+2.*tint*col,tintAmt);
 gl_FragColor=vec4(col,1.);}`;
  function shader(type, src) { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw Error(gl.getShaderInfoLog(s)); return s; }
  if (gl) try {
    prog = gl.createProgram(); gl.attachShader(prog, shader(gl.VERTEX_SHADER, VS)); gl.attachShader(prog, shader(gl.FRAGMENT_SHADER, FS)); gl.linkProgram(prog); gl.useProgram(prog);
    gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer()); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    const a = gl.getAttribLocation(prog, 'p'); gl.enableVertexAttribArray(a); gl.vertexAttribPointer(a, 2, gl.FLOAT, false, 0, 0);
    for (const n of ['img', 'msk', 'sc', 'of', 'tint', 't', 'beat', 'ripple', 'sway', 'shimmer', 'neon', 'tintAmt', 'still']) u[n] = gl.getUniformLocation(prog, n);
    gl.uniform1i(u.img, 0); gl.uniform1i(u.msk, 1);
  } catch (e) { console.error(e); gl = null; }
  if (!gl) k2d = c.getContext('2d');

  function upload(unit, image, nearest) {
    gl.activeTexture(gl.TEXTURE0 + unit); const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGB, gl.RGB, gl.UNSIGNED_BYTE, image);
    const f = nearest ? gl.NEAREST : gl.LINEAR;
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, f); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, f);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    return t;
  }
  /** arts: {wide: {src, mask, ...}, tall: {...}}; images load once, the plate for the current window shape is picked per frame. */
  function load(arts) {
    for (const a of Object.values(arts)) { a.img = new Image(); a.img.src = a.src; a.msk = new Image(); a.msk.src = a.mask; }
    return arts;
  }
  /** Cover-fit placement in canvas pixels; map(x, y) turns image fractions into canvas pixels. */
  function place(arts, w, h) {
    const a = w < h ? arts.tall : arts.wide, iw = a.img.naturalWidth || a.w || 16, ih = a.img.naturalHeight || a.h || 9;
    const k = Math.max(w / iw, h / ih), dx = (w - iw * k) / 2, dy = (h - ih * k) / 2;
    return {art: a, k, dx, dy, iw, ih, x: f => dx + f * iw * k, y: f => dy + f * ih * k, size: f => f * iw * k};
  }
  /** Draw the plate for this frame. o: {t, beat, ripple, sway, shimmer, neon, tint [r,g,b] 0..255, tintAmt, still}. */
  function draw(P, w, h, o) {
    if (c.width !== w || c.height !== h) { c.width = w; c.height = h; }
    const a = P.art; if (!a.img.complete || !a.img.naturalWidth) return;
    if (!gl) { k2d.drawImage(a.img, P.dx, P.dy, P.iw * P.k, P.ih * P.k); return; }
    if (art !== a) { if (!a.msk.complete || !a.msk.naturalWidth) return; tex.forEach(t => gl.deleteTexture(t)); tex = [upload(0, a.img, a.pixel), upload(1, a.msk, a.pixel)]; art = a; }
    gl.viewport(0, 0, w, h);
    gl.uniform2f(u.sc, w / (P.iw * P.k), h / (P.ih * P.k)); gl.uniform2f(u.of, -P.dx / (P.iw * P.k), -P.dy / (P.ih * P.k));
    gl.uniform3f(u.tint, o.tint[0] / 255, o.tint[1] / 255, o.tint[2] / 255);
    gl.uniform1f(u.t, o.t); gl.uniform1f(u.beat, o.beat || 0); gl.uniform1f(u.ripple, o.ripple || 0); gl.uniform1f(u.sway, o.sway || 0);
    gl.uniform1f(u.shimmer, o.shimmer || 0); gl.uniform1f(u.neon, o.neon || 0); gl.uniform1f(u.tintAmt, o.tintAmt || 0); gl.uniform1f(u.still, o.still ? 1 : 0);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }
  // Frosted cards (CSS backdrop blur over the plate) so text stays readable on a busy painting, like glass widgets.
  const cards = {};
  /** key names the card; rect [x, y, w, h] in canvas pixels or null to hide; look {bg, border, radius, shadow} in CSS. */
  function card(key, rect, dpr, look) {
    let el = cards[key];
    if (!el) { el = cards[key] = document.createElement('div'); el.style.cssText = 'position:absolute;display:none;pointer-events:none;-webkit-backdrop-filter:blur(18px) saturate(1.3);backdrop-filter:blur(18px) saturate(1.3)'; document.body.insertBefore(el, document.getElementById('scene')); }
    const st = el.style; if (!rect) { st.display = 'none'; return; }
    st.display = 'block'; st.left = rect[0] / dpr + 'px'; st.top = rect[1] / dpr + 'px'; st.width = rect[2] / dpr + 'px'; st.height = rect[3] / dpr + 'px';
    st.background = look.bg; st.border = look.border || 'none'; st.borderRadius = (look.radius ?? 28) + 'px'; st.boxShadow = look.shadow || 'none';
  }
  return {load, place, draw, card, webgl: !!gl};
})();
