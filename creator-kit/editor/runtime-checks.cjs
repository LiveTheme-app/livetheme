// Runtime checks for the injected web-theme runtime (car app + PC preview): info-only driving mode, volume event, timer budget.
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const src=fs.readFileSync(__dirname+'/../../app/src/main/assets/engine/livetheme-runtime.js','utf8');
let rafQueue=[],listeners={},now=0;const timers=[];
const win={requestAnimationFrame:cb=>{rafQueue.push(cb);return rafQueue.length;},setInterval:(fn,ms)=>{timers.push({fn,ms});return timers.length;},setTimeout:(fn,ms)=>{timers.push({fn,ms,once:true});return timers.length;},addEventListener:(t,fn)=>{listeners[t]=fn;},console};
win.window=win;win.document={getAnimations:()=>[],querySelectorAll:()=>[]};
const ctx=vm.createContext(Object.assign(win,{Date:{now:()=>now},Map,Array,JSON,Promise,Object,Math,Number,String}));vm.runInContext(src,ctx);
const lt=ctx.livetheme;let port={postMessage:()=>{}};const send=m=>port.onmessage({data:JSON.stringify(m)});
listeners.message({data:'livetheme:init',ports:[port],stopImmediatePropagation(){}});
let frames=0;const loop=()=>{frames++;ctx.requestAnimationFrame(loop);};ctx.requestAnimationFrame(loop);
function run(ms){const vs=1000/60;for(let t=0;t<ms;t+=vs){now+=vs;const q=rafQueue;rafQueue=[];q.forEach(cb=>cb(now));}}
run(1000);const normal=frames;assert(normal>=25&&normal<=32,'30 fps cap when parked: '+normal);
send({type:'paused',value:true,reason:'driving',info:false});frames=0;run(2000);assert(frames===0,'frozen while driving without info mode');assert(lt.paused&&!lt.infoOnly,'paused flags');
send({type:'paused',value:false});send({type:'paused',value:true,reason:'driving',info:true});frames=0;run(3000);assert(frames>=2&&frames<=4,'info-only mode runs about 1 fps: '+frames);assert(lt.infoOnly&&lt.pauseReason==='driving','info flags');
let calls=0;ctx.setInterval(()=>calls++,16);const tick=timers[timers.length-1].fn;for(let i=0;i<10;i++){now+=100;tick();}assert(calls<=2,'timers at most once per second in info mode: '+calls);
send({type:'paused',value:false});assert(!lt.paused&&!lt.infoOnly,'resume when parked');calls=0;for(let i=0;i<5;i++){now+=16;tick();}assert(calls===5,'timers normal again');
let vols=[];lt.on('volume',v=>vols.push(v));send({type:'data',data:{volume:.4}});send({type:'data',data:{volume:.4}});send({type:'data',data:{volume:.8}});assert.deepStrictEqual(vols,[.4,.8],'volume event only on change');
assert(lt.state.volume===.8,'volume in state');
console.log('PASS runtime: 30 fps cap, driving freeze, info-only 1 fps + timer budget, resume, volume event');
