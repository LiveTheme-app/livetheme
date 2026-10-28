const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{pack}=require('./zip.js');
const source=fs.readFileSync(__dirname+'/editor.js','utf8'),handlers={},pending=[],faces=new Set(),revoked=[];
const scope={KEYS:['clock','calendar','music','memo','dday','weather','battery','ambient','trip','navigation'],RETIRED:['seat','climate'],MODES:{full:[2560,1439],split2:[1682,1210],split3:[841,1210]},mode:'full',clone:v=>JSON.parse(JSON.stringify(v)),clamp:(n,a,b)=>Math.max(a,Math.min(b,n)),fontRequest:0,importRequest:0,webFiles:new Map(),webRevision:0,customFace:null,fontFile:null,fontUrl:'',videoFile:null,videoUrl:'',bgSignature:'',baseline:null,selected:'clock',render:()=>{},message:()=>{},
 $:id=>({addEventListener:(event,fn)=>{handlers[id+':'+event]=fn;},click:()=>{}}),
 URL:{createObjectURL:file=>'blob:'+file.name+':'+pending.length,revokeObjectURL:url=>revoked.push(url)},
 FontFace:class{constructor(name,url){this.url=url;}load(){return new Promise((resolve,reject)=>pending.push({face:this,resolve,reject}));}},
 document:{fonts:{add:f=>faces.add(f),delete:f=>faces.delete(f)}}};
vm.createContext(scope);
vm.runInContext(source.slice(source.indexOf('function validate('),source.indexOf('\nfunction setRect(')),scope);
vm.runInContext(source.slice(source.indexOf('function blankTheme('),source.indexOf('\nasync function loadCommunity(')),scope);
vm.runInContext(source.slice(source.indexOf("$('fontFile').addEventListener"),source.indexOf("$('publish').addEventListener")),scope);
vm.runInContext(source.slice(source.indexOf("$('import').addEventListener"),source.indexOf("$('setWorkshop').addEventListener")),scope);
const template=JSON.parse(fs.readFileSync(__dirname+'/../../community/presets.json','utf8'))[0];
let passed=0;function check(value,name){assert.ok(value,name);passed++;console.log('PASS',name);}
for(const [field,value] of [['index',999],['index',1.5],['index','1']]){const bad=structuredClone(template);bad.background[field]=value;assert.throws(()=>scope.validate(bad));check(true,'invalid background '+String(value));}
for(const mutate of [j=>j.panel.font='script',j=>j.clock.caption='x'.repeat(61),j=>j.panel.clock='false',j=>j.layouts['full/clock']='0,,1']){const bad=structuredClone(template);mutate(bad);assert.throws(()=>scope.validate(bad));check(true,'invalid imported field rejected');}
scope.selectTheme(scope.blankTheme());
for(const mode of ['full','split2','split3']){scope.mode=mode;const boxes=scope.KEYS.map(k=>scope.rect(k)),[w,h]=scope.MODES[mode];for(let i=0;i<boxes.length;i++){const r=boxes[i];check(r.x>=16&&r.y>=96&&r.x+r.w<=w-16+.01&&r.y+r.h<=h-48+.01,'default preview in bounds '+mode+' '+i);for(let j=0;j<i;j++){const a=boxes[j];assert.ok(!(r.x<a.x+a.w&&r.x+r.w>a.x&&r.y<a.y+a.h&&r.y+r.h>a.y),'preview overlap '+mode);}}check(true,'all default widgets separate '+mode);}
(async()=>{
 const font=handlers['fontFile:change'];
 const old=font({target:{files:[{name:'old.ttf',size:128}]}});const current=scope.blankTheme();current.name='new project';scope.selectTheme(current);pending[0].resolve();await old;
 check(scope.theme.name==='new project'&&scope.theme.clock.font==='light'&&scope.fontFile===null&&faces.size===0,'stale font cannot mutate new project');
 const first=font({target:{files:[{name:'first.ttf',size:128}]}}),last=font({target:{files:[{name:'last.ttf',size:128}]}});pending[2].resolve();await last;pending[1].resolve();await first;
 check(scope.fontFile.name==='last.ttf'&&faces.size===1&&faces.has(pending[2].face),'latest font wins out of order');
 const failed=font({target:{files:[{name:'broken.ttf',size:128}]}});pending[3].reject(new Error('invalid font'));await failed;
 check(scope.fontFile.name==='last.ttf'&&faces.size===1,'failed replacement preserves current font');
 scope.selectTheme(scope.blankTheme());check(faces.size===0&&scope.fontFile===null,'changing project removes previous font face');
 let done;const opening=handlers['importFile:change']({target:{files:[{size:100,text:()=>new Promise(resolve=>done=resolve)}]}});const next=scope.blankTheme();next.name='keep new';scope.selectTheme(next);done(JSON.stringify(template));await opening;
 check(scope.theme.name==='keep new','stale JSON import cannot replace new project');
 const config=scope.validate(template),zip=await pack([['live-theme.json',JSON.stringify(config)]]);fs.writeFileSync(require('os').tmpdir()+'/editor-roundtrip.zip',Buffer.from(await zip.arrayBuffer()));check(true,'validated package exported for server roundtrip');
 console.log('DONE',passed);
})().catch(e=>{console.error(e);process.exitCode=1;});
