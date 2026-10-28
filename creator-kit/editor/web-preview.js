'use strict';
// Web (HTML/JS) theme preview: the server serves the files like the car app does (allowed types, CSP, runtime injected first);
// this side plays the app's role over the same MessagePort — virtual car data in, requests checked by the app's rules.
const WebPreview=(()=>{
 const TYPES=['html','js','css','json','png','jpg','jpeg','webp','gif','svg','mp4','woff2'],MAX_FILES=200,MAX_BYTES=40*1024*1024;
 let port=null,lastRequest=0,asking=false;
 /** Same rule as CreatorTheme.webEntry / server web_entry. */
 function webEntry(name){if(!name.startsWith('web/')||name.length>160||name.includes('..')||name.includes('//')||name.includes('\\')||!/^web\/[A-Za-z0-9._\-/]+$/.test(name))return false;const dot=name.lastIndexOf('.');return dot>name.lastIndexOf('/')&&TYPES.includes(name.slice(dot+1).toLowerCase());}
 function send(message){if(port)port.postMessage(JSON.stringify(message));}
 let trackSong=null,trackSince=0;
 /** Same estimate as MainActivity.estimatedBpm (Java String.hashCode), so the preview pulses exactly like the car. Not measured from sound. */
 function bpm(title,artist){if(!title)return 0;let h=0;const str=title+'\u0000'+artist;for(let i=0;i<str.length;i++)h=(Math.imul(31,h)+str.charCodeAt(i))|0;return 72+((h%64)+64)%64;}
 function data(){const s=VehiclePreview.state;if(s.song!==trackSong){trackSong=s.song;trackSince=Date.now();}return {battery:{level:s.level,charging:s.charging},ambient:{color:s.ambientColor.toUpperCase(),brightness:s.ambientBrightness},music:{title:s.song,artist:s.artist,playing:s.playing,color:s.albumColor.toUpperCase()},volume:s.volume/100,rhythm:{estimated:true,bpm:bpm(s.song,s.artist),playing:s.playing,since:trackSince},night:s.night,paused:s.driving};}
 function push(){if(!port)return;send({type:'fps',cap:mode==='split3'?24:30});const d=VehiclePreview.state.driving;send({type:'paused',value:d,reason:d?'driving':null,info:d&&(theme.replacesWidgets||[]).length>0});send({type:'data',data:data()});}
 async function load(){
  const frame=$('sceneBg');port=null;
  if(!webFiles.has('web/index.html')){message('웹 배경 폴더를 열거나 웹 샘플로 시작해주세요. index.html이 필요해요.');return;}
  try{
   const body=await ThemeZip.pack([...webFiles]);const response=await fetch('/v1/preview',{method:'POST',headers:{'Content-Type':'application/zip'},body});if(!response.ok)throw Error();
   // Theme code runs on the other loopback name, so it is cross-origin to this editor page and cannot touch it.
   const other=location.hostname==='localhost'?'127.0.0.1':'localhost',origin=`${location.protocol}//${other}:${location.port}`;frame.style.display='block';
   frame.onload=()=>{const channel=new MessageChannel();port=channel.port1;port.onmessage=event=>receive(event.data);frame.contentWindow.postMessage('livetheme:init',origin,[channel.port2]);push();};
   frame.src=origin+'/preview/index.html?r='+webRevision;
  }catch(e){message('웹 배경을 미리보기로 올리지 못했어요. 파일 종류와 용량을 확인해주세요.');}
 }
 /** The car app's request rules, applied to the virtual car so creators see the same answers. */
 function receive(raw){
  let msg;try{msg=JSON.parse(raw);}catch(e){return;}if(msg.type!=='request')return;
  const reply=(ok,reason)=>send({type:'response',id:msg.id,ok,reason:reason||null}),action=msg.action,state=VehiclePreview.state;
  if(!(theme.background.requests||[]).includes(action)||!WEB_REQUESTS.includes(action))return reply(false,'not-declared');
  // Music controls: like the car app, no question and two per second; while driving only play/pause.
  if(action.startsWith('media.')){if(state.driving&&action!=='media.playPause')return reply(false,'driving');const t=Date.now();if(t-(receive.media||0)<500)return reply(false,'rate-limited');receive.media=t;if(action==='media.playPause')state.playing=!state.playing;else{const songs=['밤바다 라디오 · 예시 곡','오로라 아래에서 · 예시 곡','반짝이는 입자 · 예시 곡'],i=songs.indexOf(state.song);state.song=songs[((i<0?0:i)+(action==='media.next'?1:songs.length-1))%songs.length];}VehiclePreview.apply(theme);push();return reply(true);}
  if(state.driving)return reply(false,'driving');
  if(asking)return reply(false,'busy');
  const now=Date.now();if(lastRequest&&now-lastRequest<10000)return reply(false,'rate-limited');lastRequest=now;
  const color=action==='ambient.color';if(color?!/^#[0-9a-f]{6}$/i.test(msg.value):!(msg.value>=0&&msg.value<=100))return reply(false,'invalid');
  asking=true;const ok=confirm(`‘${theme.name}’ 테마가 실내 무드등 ${color?'색을 '+String(msg.value).toUpperCase():'밝기를 '+Math.round(msg.value)}(으)로 바꾸려고 해요.\n적용할까요?\n\n(PC 미리보기: 가상 차량에 적용돼요. 실제 앱도 이렇게 확인창을 띄워요.)`);asking=false;
  if(!ok)return reply(false,'declined');
  if(color)state.ambientColor=msg.value.toUpperCase();else state.ambientBrightness=Math.round(msg.value);VehiclePreview.apply(theme);reply(true);
 }
 /** Taps on the preview background (not on widgets) reach the theme as 0..1 coordinates, as in the car. */
 function touch(event){if(!port||VehiclePreview.state.driving||event.target.closest('.widget'))return;const r=$('canvas').getBoundingClientRect();send({type:'touch',x:(event.clientX-r.left)/r.width,y:(event.clientY-r.top)/r.height});}
 /** A creator folder: either a web root with index.html, or a theme folder with live-theme.json and web/. */
 async function openFolder(list){
  const files=[...list];if(!files.length)return;const strip=f=>f.webkitRelativePath.split('/').slice(1).join('/');const themed=files.some(f=>strip(f)==='live-theme.json');
  const map=new Map();let total=0,config=null;
  for(const f of files){const rel=strip(f);if(themed&&rel==='live-theme.json'){config=JSON.parse(await f.text());continue;}const path=themed?rel:'web/'+rel;if(!webEntry(path))continue;total+=f.size;map.set(path,f);}
  if(!map.has('web/index.html'))return message('폴더에 index.html이 없어요. 웹 배경의 첫 화면 파일을 넣어주세요.');
  if(map.size>MAX_FILES||total>MAX_BYTES)return message('웹 배경은 파일 200개, 40 MiB 이하로 만들어주세요.');
  try{const next=validate(config||{...clone(theme),background:{type:'web',requests:theme.background.requests||[]}});if(next.background.type!=='web')throw Error('웹 배경 테마가 아니에요.');selectTheme(next,map);message(`웹 배경 파일 ${map.size}개를 열었어요. 허용되지 않는 파일 종류는 빼고 담았어요.`);}catch(e){message(e.message);}
 }
 async function samples(list){
  try{const response=await fetch('/v1/web-samples');if(!response.ok)throw Error();const {items}=await response.json();
   for(const item of items){const config=await (await fetch(`/web-samples/${item.id}/live-theme.json`)).json();const b=document.createElement('button');b.className='sample web';const label=document.createElement('span');label.textContent=config.name;const help=document.createElement('small');help.textContent=(config.replacesWidgets||[]).length?(config.name.includes('캐릭터')?'오리지널 캐릭터 · ':'위젯을 배경에 담은 예시 · ')+config.replacesWidgets.map(k=>({clock:'시계',music:'음악',battery:'배터리'})[k]||k).join('·')+' · 리듬은 추정':(config.background.requests||[]).length?'코드로 만든 웹 배경 · 무드등 색 요청 예시':'코드로 만든 웹 배경 · 차량 데이터 읽기 예시';label.append(help);b.append(label);
    b.addEventListener('click',async()=>{const map=new Map();for(const f of item.files.filter(f=>f!=='live-theme.json'))map.set(f,await (await fetch(`/web-samples/${item.id}/${f}`)).blob());selectTheme(validate(config),map);message('웹 샘플로 시작했어요. 가상 차량 패널을 바꾸면 웹 배경이 바로 반응해요.');});list.append(b);}
  }catch(e){}
 }
 function mount(){
  $('canvas').addEventListener('pointerdown',touch);
  $('webFolder').addEventListener('change',event=>openFolder(event.target.files));
  for(const box of document.querySelectorAll('#webRequests input'))box.addEventListener('change',()=>{theme.background.requests=[...document.querySelectorAll('#webRequests input:checked')].map(b=>b.value);message('웹 배경이 요청할 수 있는 항목을 바꿨어요. 설치할 때 사용자에게 보여줘요.');});
 }
 function sync(){const isWeb=theme.background.type==='web';$('webRequests').hidden=!isWeb;for(const box of document.querySelectorAll('#webRequests input'))box.checked=isWeb&&(theme.background.requests||[]).includes(box.value);}
 return {load,push,mount,sync,samples,webEntry};
})();
if(typeof module!=='undefined')module.exports=WebPreview;
