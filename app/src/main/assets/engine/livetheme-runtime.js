/* LiveTheme runtime, injected before a web theme's own code (car app and PC preview).
   - Frame budget and driving pause are enforced here, not left to the theme: requestAnimationFrame is throttled to the app's cap
     and stops while paused; CSS animations and media pause too.
   - The data channel is a MessagePort handed over once as "livetheme:init"; theme code never sees the port, only window.livetheme.
   - Data is read-only. Requests go to the app, which checks the theme's declared list, refuses while driving, rate-limits and asks the user
     (music controls skip the question and are only rate-limited).
   - Info-only mode (driving, for themes that replace the clock/music widgets): motion is held, frames drop to 1 per second and timers
     run at most once per second, so only numbers and titles refresh. Enforced here even if the theme ignores it. */
(function(){
 'use strict';
 var port=null,cap=30,paused=false,info=false,reason=null,last=0,pumping=false,ids=0,seq=0;
 var frames=new Map(),listeners={},pending={},state={},held=[];
 var raf=window.requestAnimationFrame.bind(window);
 function pump(now){
  if(paused&&!info){pumping=false;return;}raf(pump);
  if(now-last<1000/(info?1:cap)-1)return;last=now;var run=Array.from(frames);frames.clear();
  for(var i=0;i<run.length;i++){try{run[i][1](now);}catch(e){console.error(e);}}
 }
 function wake(){if(!pumping&&(!paused||info)){pumping=true;raf(pump);}}
 /* Timers follow the same budget: in info-only mode a callback runs at most once per second. */
 var nativeInterval=window.setInterval.bind(window),nativeTimeout=window.setTimeout.bind(window);
 function budget(fn){var lastRun=0;return function(){if(info){var t=Date.now();if(t-lastRun<1000)return;lastRun=t;}if(typeof fn==='function')fn.apply(this,arguments);};}
 window.setInterval=function(fn,ms){return nativeInterval(budget(fn),ms);};
 window.setTimeout=function(fn,ms){return nativeTimeout(fn,info?Math.max(ms||0,1000):ms);};
 window.requestAnimationFrame=function(callback){var id=++ids;frames.set(id,callback);wake();return id;};
 window.cancelAnimationFrame=function(id){frames.delete(id);};
 function hold(){
  if(document.getAnimations)document.getAnimations().forEach(function(a){if(a.playState==='running'){a.pause();held.push(a);}});
  document.querySelectorAll('video,audio').forEach(function(m){if(!m.paused){m.pause();held.push(m);}});
 }
 function release(){held.splice(0).forEach(function(item){var p=item.play();if(p&&p.catch)p.catch(function(){});});}
 function emit(name,value){(listeners[name]||[]).forEach(function(fn){try{fn(value);}catch(e){console.error(e);}});}
 function receive(raw){
  var msg;try{msg=JSON.parse(raw);}catch(e){return;}
  if(msg.type==='data'){var before=state.volume;state=msg.data||{};emit('data',state);if(state.volume!==before&&state.volume!=null)emit('volume',state.volume);}
  else if(msg.type==='touch')emit('touch',{x:msg.x,y:msg.y});
  else if(msg.type==='fps'&&msg.cap>0){cap=msg.cap;}
  else if(msg.type==='paused'){var next=!!msg.value,nextInfo=next&&!!msg.info;reason=next?(msg.reason||null):null;if(next===paused&&nextInfo===info)return;paused=next;info=nextInfo;if(paused)hold();else release();wake();emit('paused',paused);}
  else if(msg.type==='response'&&pending[msg.id]){pending[msg.id](msg);delete pending[msg.id];}
 }
 window.addEventListener('message',function(e){
  if(e.data!=='livetheme:init'||!e.ports||!e.ports[0]||port)return;
  e.stopImmediatePropagation();port=e.ports[0];port.onmessage=function(m){receive(m.data);};
 },true);
 var api={
  /** 'data' (battery, ambient, music, rhythm, volume, night, paused), 'volume' (0..1), 'touch' ({x,y} 0..1), 'paused' (boolean).
      rhythm = {estimated:true, bpm, playing, since}: a tempo guessed from the track, NOT measured from the sound. */
  on:function(name,fn){(listeners[name]=listeners[name]||[]).push(fn);if(name==='data'&&state.battery)fn(state);},
  get state(){return state;},
  get paused(){return paused;},
  /** True while driving for themes that replace widgets: stop motion, keep updating text (1 fps). */
  get infoOnly(){return info;},
  /** Why the theme is paused: 'driving', 'user' or null. */
  get pauseReason(){return reason;},
  /** Ask the app for a change it allows: 'ambient.color' '#RRGGBB', 'ambient.brightness' 0..100 (confirmed by the driver),
      'media.playPause' / 'media.next' / 'media.prev' (no confirmation, 2 per second; only play/pause while driving). Resolves {ok, reason}. */
  request:function(action,value){return new Promise(function(resolve){if(!port){resolve({ok:false,reason:'unavailable'});return;}var id=++seq;pending[id]=resolve;port.postMessage(JSON.stringify({type:'request',id:id,action:String(action),value:value}));});}
 };
 Object.defineProperty(window,'livetheme',{value:Object.freeze(api),writable:false,configurable:false});
 /* Wallpaper Engine–style alias for creators used to web wallpapers. */
 window.wallpaperRegisterCarListener=function(fn){api.on('data',fn);};
})();
