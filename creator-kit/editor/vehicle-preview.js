'use strict';
// Virtual car panel and live preview of no-code data links. Formulas match the app (ThemeEffects / ThemeLinks) so the PC preview is what the car shows.
const VehiclePreview=(()=>{
 const state={level:72,charging:true,ambientColor:'#7FD6C8',ambientBrightness:40,song:'느린 오후의 플레이리스트',artist:'제작용 예시 곡',albumColor:'#B58BD6',playing:true,night:false,driving:false,ambientSync:false,volume:60};
 let frame=0,time=0,last=0;
 /** 0..1 value of a source, or null when it carries no value (e.g. a colour source). */
 function value(source){switch(source){case 'battery.level':return state.level/100;case 'battery.charging':return state.charging?1:0;case 'ambient.brightness':return state.ambientBrightness/100;case 'music.playing':return state.playing?1:0;case 'day.night':return state.night?1:0;case 'drive.paused':return state.driving?1:0;default:return null;}}
 function colorOf(link){return link.source==='ambient.color'?state.ambientColor:link.source==='music.color'?state.albumColor:link.color;}
 /** Overlay alphas and the effect speed factor for the current theme; shared formula with the app. */
 function compute(links){
  const k=link=>link?link.strength/100:0,out={glow:0,glowColor:'#BFE8D2',tint:0,tintColor:'#000000',dim:0,speed:1};
  if(links.glow){out.glow=k(links.glow)*.55*value(links.glow.source);out.glowColor=links.glow.color;}
  if(links.tint){const charging=links.tint.source==='battery.charging';out.tint=k(links.tint)*.35*(charging?value('battery.charging'):1);out.tintColor=colorOf(links.tint);}
  if(links.motion)out.speed=1+k(links.motion)*1.5*value(links.motion.source);
  if(links.dim)out.dim=k(links.dim)*.5*value(links.dim.source);
  return out;
 }
 function layer(id,tag){let el=document.getElementById(id);if(!el){el=document.createElement(tag||'div');el.id=id;el.className='linkLayer';document.getElementById('canvas').insertBefore(el,document.getElementById('widgets'));}return el;}
 function rgba(hex,a){return `rgba(${parseInt(hex.slice(1,3),16)},${parseInt(hex.slice(3,5),16)},${parseInt(hex.slice(5,7),16)},${a})`;}
 function apply(theme){
  const c=compute(theme.links||{});
  layer('linkTint').style.background=rgba(c.tintColor,c.tint);
  layer('linkGlow').style.background=`radial-gradient(ellipse 60% 55% at 50% 100%,${rgba(c.glowColor,c.glow)},transparent 70%)`;
  // Same as the car app: at night a theme without its own dim link is lowered a little (NIGHT_DIM in ThemeEffects.java).
  layer('linkDim').style.background=`rgba(0,0,0,${state.night&&!(theme.links&&theme.links.dim)?Math.max(c.dim,.15):c.dim})`;
  const badge=layer('driveBadge');badge.textContent=state.driving?'주행 중 · 실제 앱처럼 배경이 멈춰요':'';badge.style.display=state.driving?'block':'none';
  const video=document.getElementById('videoBg');if(video&&video.style.display==='block'){if(state.driving)video.pause();else video.play().catch(()=>{});}
  decorate();effects(theme,c.speed);if(typeof WebPreview!=='undefined')WebPreview.push();
 }
 /** Widgets show the virtual values instead of fixed samples. */
 function decorate(){
  const w=key=>document.querySelector('#widgets .widget.'+key);
  const battery=w('battery');if(battery){battery.querySelector('.large').textContent=state.level+'%';battery.querySelector('.bodyText').textContent=state.charging?'충전 중 · 가상 차량':'충전 중 아님 · 가상 차량';const bar=battery.querySelector('.batteryBar i');if(bar)bar.style.width=state.level+'%';}
  const music=w('music');if(music){const text=music.querySelectorAll('.musicText > div');if(text[0])text[0].textContent=state.song;if(text[1])text[1].textContent=state.artist+(state.playing?' · 재생 중':' · 일시정지');const album=music.querySelector('.album');if(album)album.style.background=state.albumColor;}
  const ambient=w('ambient');if(ambient){ambient.querySelector('.large').textContent=state.ambientColor.toUpperCase();ambient.querySelector('.bodyText').textContent='밝기 '+state.ambientBrightness+' · 가상 차량';}
 }
 // Same particle formula as ThemeEffects.java; paused while driving, like the app.
 function effects(theme,speed){
  const canvas=layer('effectLayer','canvas'),[w,h]=[parseInt(document.getElementById('canvas').style.width)||2560,parseInt(document.getElementById('canvas').style.height)||1439];if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
  cancelAnimationFrame(frame);const effect=theme.effect||{},type=effect.type||'none',g=canvas.getContext('2d');
  const draw=now=>{if(last&&!state.driving)time+=Math.min(100,now-last)/1000*speed;last=state.driving?0:now;g.clearRect(0,0,w,h);if(type!=='none'){const snow=type==='snow',count=effect.amount??24,t=time*(effect.speed??1);g.fillStyle=effect.color||'#D6F5B0';for(let i=0;i<count;i++){const phase=i*2.399963,x=((i*.61803398875+t*(snow?.003:.008))%1)*w+Math.sin(t*.4+phase)*w*.025,y=((i*.381966+t*(snow?.025:.005))%1)*h;g.globalAlpha=snow?120/255:(45+85*(.5+.5*Math.sin(t+phase)))/255;g.beginPath();g.arc(x,y,snow?1.5:2.2,0,Math.PI*2);g.fill();}g.globalAlpha=1;}if(!state.driving&&type!=='none')frame=requestAnimationFrame(draw);};
  last=0;frame=requestAnimationFrame(draw);
 }
 /** Virtual car panel controls; every change re-applies the preview. */
 function mountPanel(onChange){
  const root=document.getElementById('vehiclePanel');if(!root)return;
  const row=(label,input)=>{const l=document.createElement('label');l.textContent=label;l.append(input);root.append(l);return input;};
  const control=(key,type,attrs,label)=>{const input=document.createElement('input');input.type=type;Object.assign(input,attrs);if(type==='checkbox')input.checked=state[key];else input.value=state[key];input.addEventListener('input',()=>{state[key]=type==='checkbox'?input.checked:type==='range'?Number(input.value):input.value;onChange();});return row(label,input);};
  control('level','range',{min:0,max:100},'충전량');control('charging','checkbox',{},'충전 중');control('ambientColor','color',{},'무드등 색');control('ambientBrightness','range',{min:0,max:100},'무드등 밝기');
  control('song','text',{maxLength:40},'곡 제목');control('volume','range',{min:0,max:100},'음량');control('albumColor','color',{},'앨범 색');control('playing','checkbox',{},'음악 재생 중');control('night','checkbox',{},'야간');control('driving','checkbox',{},'주행 중');control('ambientSync','checkbox',{},'무드등 = 배경 색');
 }
 /** Inspector rows to connect each visual target to a car value — no code. */
 function mountLinks(getTheme,onChange){
  const root=document.getElementById('linkEditor');if(!root)return;root.replaceChildren();
  for(const [target,spec] of Object.entries(LINK_TARGETS)){
   const box=document.createElement('div');box.className='linkRow';const head=document.createElement('label');head.className='linkToggle';const on=document.createElement('input');on.type='checkbox';head.append(on,' '+spec.name);box.append(head);
   const source=document.createElement('select');for(const s of spec.sources){const o=document.createElement('option');o.value=s;o.textContent=LINK_SOURCES[s];source.append(o);}
   const strength=document.createElement('input');strength.type='range';strength.min=0;strength.max=100;const color=document.createElement('input');color.type='color';color.hidden=!spec.color;
   box.append(source,strength,color);root.append(box);
   const sync=()=>{const link=getTheme().links[target];on.checked=!!link;source.disabled=strength.disabled=color.disabled=!link;source.value=link?link.source:spec.sources[0];strength.value=link?link.strength:50;color.value=link&&link.color?link.color:'#BFE8D2';color.hidden=!spec.color||(target==='tint'&&source.value!=='battery.charging');};
   const write=()=>{const links=getTheme().links;if(on.checked){links[target]={source:source.value,strength:Number(strength.value)};if(spec.color)links[target].color=color.value;}else delete links[target];sync();onChange();};
   on.addEventListener('change',write);source.addEventListener('change',write);strength.addEventListener('input',write);color.addEventListener('input',write);box.sync=sync;
  }
 }
 /** Same rule as AmbientMatch.pick in the car app: hue buckets weighted by saturation² × brightness, then lifted (S ≥ 45%, V ≥ 75%). */
 function ambientFrom(pixels){
  const W=new Array(24).fill(0),S=W.slice(),V=W.slice(),X=W.slice(),Y=W.slice();let total=0,counted=0,r=0,g=0,b=0;
  for(let i=0;i<pixels.length;i+=4){const [h,sat,v]=hsv(pixels[i],pixels[i+1],pixels[i+2]);if(v<.12)continue;counted++;r+=pixels[i];g+=pixels[i+1];b+=pixels[i+2];const w=sat*sat*v;if(w<.02)continue;const bin=Math.min(23,Math.floor(h/15)),a=h*Math.PI/180;W[bin]+=w;S[bin]+=w*sat;V[bin]+=w*v;X[bin]+=w*Math.cos(a);Y[bin]+=w*Math.sin(a);total+=w;}
  if(!counted)return '#FFC58A';
  let best=0;for(let i=1;i<24;i++)if(W[i]>W[best])best=i;
  if(total<counted*.01||!W[best]){const [h,sat,v]=hsv(r/counted,g/counted,b/counted);return lift(h,Math.min(.25,sat),v);}
  return lift((Math.atan2(Y[best],X[best])*180/Math.PI+360)%360,S[best]/W[best],V[best]/W[best]);
 }
 function hsv(r,g,b){r/=255;g/=255;b/=255;const max=Math.max(r,g,b),min=Math.min(r,g,b),d=max-min;let h=0;if(d){h=max===r?((g-b)/d)%6:max===g?(b-r)/d+2:(r-g)/d+4;h*=60;if(h<0)h+=360;}return [h,max?d/max:0,max];}
 function lift(h,sat,v){sat=sat<.3?sat:Math.max(sat,.45);v=Math.max(v,.75);const f=n=>{const k=(n+h/60)%6;return Math.round(255*(v-v*sat*Math.max(0,Math.min(k,4-k,1))));};return '#'+[f(5),f(3),f(1)].map(x=>x.toString(16).padStart(2,'0')).join('').toUpperCase();}
 /** Editor preview of "무드등을 배경 색에 맞추기": sample the visible background and set the virtual mood light (not while driving, like the car). */
 function matchBackground(theme){
  if(!state.ambientSync||state.driving||typeof document==='undefined')return false;
  const source=['videoBg','imageBg'].map(id=>document.getElementById(id)).find(el=>el&&el.style.display!=='none'&&(el.videoWidth||el.naturalWidth));if(!source)return false;
  try{const c=document.createElement('canvas');c.width=64;c.height=36;const g=c.getContext('2d');g.drawImage(source,0,0,64,36);const color=ambientFrom(g.getImageData(0,0,64,36).data);if(color===state.ambientColor)return false;state.ambientColor=color;const input=[...document.querySelectorAll('#vehiclePanel input[type=color]')][0];if(input)input.value=color.toLowerCase();apply(theme);return true;}catch(e){return false;}
 }
 function syncLinks(){for(const row of document.querySelectorAll('#linkEditor .linkRow'))row.sync();}
 return {state,compute,apply,mountPanel,mountLinks,syncLinks,ambientFrom,matchBackground};
})();
if(typeof module!=='undefined')module.exports=VehiclePreview;
