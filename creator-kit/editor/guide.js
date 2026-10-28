'use strict';
// "3분 만에 내 차 테마": five no-code steps, sample starters and built-in backgrounds. Steps tick themselves from the theme state.
const Guide=(()=>{
 const BUILTIN=[];// This repository does not ship the app's three built-in video backgrounds (see README), so the picker offers web samples and your own video only.
 const touched={vehicle:false,shared:false};let started=0,timer=0;
 const STEPS=[
  ['배경 고르기',()=>theme.background.type!=='keep','backgroundPicker'],
  ['효과 고르기',()=>(theme.effect.type||'none')!=='none','effectType'],
  ['차량 데이터 연결',()=>Object.keys(theme.links||{}).length>0,'linkEditor'],
  ['가상 차량으로 확인',()=>touched.vehicle,'vehiclePanel'],
  ['내보내기 · 공유',()=>touched.shared,'export']
 ];
 function mount(){
  const bar=document.getElementById('guide');if(!bar)return;
  STEPS.forEach(([name,,target],i)=>{const step=document.createElement('button');step.className='guideStep';step.innerHTML=`<i>${i+1}</i>`;step.append(name);step.addEventListener('click',()=>{const el=document.getElementById(target);el.scrollIntoView({behavior:'smooth',block:'center'});if(el.focus)el.focus({preventScroll:true});});bar.append(step);});
  const clock=document.createElement('span');clock.id='guideTimer';bar.append(clock);
  const picker=document.getElementById('backgroundPicker');
  for(const [name,index] of BUILTIN){const b=document.createElement('button');b.className='builtinBg';const img=document.createElement('img');img.src='/builtin/'+index+'.jpg';img.alt='';b.append(img,name);b.addEventListener('click',()=>{theme.background={type:'builtin',index};render();message(name+' 배경을 골랐어요. 앱에 들어 있는 자체 제작 배경이라 공유해도 원작 연결이 필요 없어요.');});picker.append(b);}
  const own=document.createElement('button');own.className='builtinBg own';own.textContent='내 영상 올리기 (MP4)';own.addEventListener('click',()=>document.getElementById('videoFile').click());picker.append(own);
  document.getElementById('vehiclePanel').addEventListener('input',()=>{touched.vehicle=true;update();});
  for(const id of ['export','confirmShare','saveJson'])document.getElementById(id).addEventListener('click',()=>{touched.shared=true;update();});
  document.getElementById('newTheme').addEventListener('click',restart);
  loadSamples();
 }
 async function loadSamples(){
  const list=document.getElementById('samples');
  try{const response=await fetch('samples.json');if(!response.ok)throw Error();const samples=await response.json();
   for(const sample of samples){const b=document.createElement('button');b.className='sample';const img=document.createElement('img');img.src='/builtin/'+sample.theme.background.index+'.jpg';img.alt='';const label=document.createElement('span');label.textContent=sample.theme.name;const help=document.createElement('small');help.textContent=sample.summary;label.append(help);b.append(img,label);
    b.addEventListener('click',()=>{selectTheme(validate(sample.theme));restart();message('샘플로 시작했어요. 이름·색·숫자만 바꿔도 내 테마가 돼요. 가상 차량 패널로 반응을 확인해보세요.');});list.append(b);}
  }catch(e){list.textContent='샘플을 불러오지 못했어요.';}
  WebPreview.samples(list);
 }
 function restart(){touched.vehicle=false;touched.shared=false;started=Date.now();clearInterval(timer);timer=setInterval(update,1000);update();}
 function update(){
  const bar=document.getElementById('guide');if(!bar||!theme)return;let done=0;
  [...bar.querySelectorAll('.guideStep')].forEach((el,i)=>{const ok=STEPS[i][1]();el.classList.toggle('done',ok);if(ok)done++;});
  const clock=document.getElementById('guideTimer');if(started){const s=Math.floor((Date.now()-started)/1000);clock.textContent=(done===STEPS.length?'완성! ':'')+Math.floor(s/60)+':'+String(s%60).padStart(2,'0');if(done===STEPS.length)clearInterval(timer);}else clock.textContent=done+' / '+STEPS.length;
 }
 return {mount,update};
})();
Guide.mount();Guide.update();
