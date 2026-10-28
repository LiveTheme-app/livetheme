// Data-link validation and the shared preview formula (must match ThemeLinks.java).
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const code=fs.readFileSync(__dirname+'/editor.js','utf8'),scope={KEYS:['clock','calendar','music','memo','dday','weather','battery','ambient','trip','navigation'],RETIRED:['seat','climate'],clone:j=>JSON.parse(JSON.stringify(j))};
vm.createContext(scope);vm.runInContext(code.slice(code.indexOf('function validate('),code.indexOf('\nfunction rect('))+';this.LINK_TARGETS=LINK_TARGETS;this.LINK_SOURCES=LINK_SOURCES;',scope);
const base={format:'live-theme',version:1,background:{type:'keep'}};
assert.equal(JSON.stringify(scope.validate(base).links),'{}');
const ok=scope.validate({...base,links:{glow:{source:'battery.level',strength:80,color:'#112233'},tint:{source:'ambient.color'},motion:{source:'music.playing',strength:0},dim:{source:'drive.paused',strength:100}}}).links;
assert.equal(ok.tint.strength,50);assert.equal(ok.tint.color,'#BFE8D2');assert.equal(ok.motion.color,undefined);
for(const bad of [{glow:{source:'music.color'}},{sparkle:{source:'battery.level'}},{dim:{source:'day.night',strength:101}},{glow:{source:'battery.level',color:'red'}},[]])assert.throws(()=>scope.validate({...base,links:bad}),JSON.stringify(bad));
global.LINK_TARGETS=scope.LINK_TARGETS;global.LINK_SOURCES=scope.LINK_SOURCES;const preview=require('./vehicle-preview.js');Object.assign(preview.state,{level:50,charging:false,playing:true,night:true,driving:false,ambientColor:'#FF0000'});
const c=preview.compute(ok);
assert.ok(Math.abs(c.glow-.8*.55*.5)<1e-9,'glow follows charge');assert.equal(c.tintColor,'#FF0000');assert.ok(Math.abs(c.tint-.5*.35)<1e-9);assert.equal(c.speed,1,'zero strength keeps speed');assert.equal(c.dim,0,'not driving');
preview.state.driving=true;assert.equal(preview.compute(ok).dim,.5,'driving dims fully at 100');
const samples=JSON.parse(fs.readFileSync(__dirname+'/samples.json','utf8'));assert.equal(samples.length,2);
for(const s of samples){const v=scope.validate(s.theme);assert.equal(v.background.type,'builtin','samples use project-made built-in backgrounds only');assert.ok(Object.keys(v.links).length>0,'sample has a data link');assert.ok(s.summary.length>10);}
// Web themes: request list, file names (same rule as the app and server), and the two web samples.
const web={...base,background:{type:'web',requests:['ambient.color']}};assert.equal(scope.validate(web).background.requests.length,1);
for(const bad of [['seat.heat'],['ambient.color','ambient.color'],'ambient.color'])assert.throws(()=>scope.validate({...base,background:{type:'web',requests:bad}}),String(bad));
assert.equal(scope.validate({...base,background:{type:'builtin',index:0,requests:['ambient.color']}}).background.requests,undefined,'requests only on web themes');
const {webEntry}=require('./web-preview.js');assert.ok(webEntry('web/index.html')&&webEntry('web/img/a-1.png'));for(const n of ['web/../x.js','web/a.exe','web/noext','/web/a.js','web\\a.js','web//a.js'])assert.ok(!webEntry(n),n);
for(const id of ['charge-ring','mood-aurora']){const dir=__dirname+'/../web-samples/'+id;const v=scope.validate(JSON.parse(fs.readFileSync(dir+'/live-theme.json','utf8')));assert.equal(v.background.type,'web');assert.ok(fs.existsSync(dir+'/web/index.html'));for(const f of fs.readdirSync(dir+'/web',{recursive:true}))if(fs.statSync(dir+'/web/'+f).isFile())assert.ok(webEntry('web/'+f.replace(/\\/g,'/')),f);}
console.log('PASS links: validation, defaults, rejection, shared formula, 2 samples, web themes');

// Mood light = background colour (same rule as AmbientMatch.pick in the car app).
{
 const VP=require('./vehicle-preview.js');const fill=(rgb,n=64*36)=>{const a=new Uint8ClampedArray(n*4);for(let i=0;i<n;i++)a.set([...rgb,255],i*4);return a;};
 const hue=hex=>{const r=parseInt(hex.slice(1,3),16),g=parseInt(hex.slice(3,5),16),b=parseInt(hex.slice(5,7),16),max=Math.max(r,g,b),min=Math.min(r,g,b),d=max-min;let h=0;if(d){h=max===r?((g-b)/d)%6:max===g?(b-r)/d+2:(r-g)/d+4;h*=60;if(h<0)h+=360;}return {h,s:max?d/max:0,v:max/255};};
 const assert=require('assert');
 let c=hue(VP.ambientFrom(fill([208,32,32])));assert(c.h<8||c.h>352,'red scene');assert(c.v>=.74,'bright');
 c=hue(VP.ambientFrom(fill([16,26,68])));assert(c.h>220&&c.h<240&&c.v>=.74&&c.s>=.44,'dark navy lifted');
 const mix=fill([128,128,128]);for(let i=0;i<64*36*3/10;i++)mix.set([32,96,224,255],i*4);c=hue(VP.ambientFrom(mix));assert(c.h>210&&c.h<230,'30% blue beats grey');
 c=hue(VP.ambientFrom(fill([154,154,154])));assert(c.s<.3&&c.v>=.74,'grey soft');
 assert.strictEqual(VP.ambientFrom(fill([0,0,0])),'#FFC58A','dark default');
 assert.strictEqual(VP.matchBackground({}),false,'off by default');
 console.log('PASS ambient match: red, navy lift, vibrant over grey, grey soft, dark default, off by default');
}
