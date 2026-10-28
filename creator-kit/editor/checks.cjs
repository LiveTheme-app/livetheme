const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{pack}=require('./zip.js');
const code=fs.readFileSync(__dirname+'/editor.js','utf8'),scope={KEYS:['clock','calendar','music','memo','dday','weather','battery','ambient','trip','navigation'],RETIRED:['seat','climate'],clone:j=>JSON.parse(JSON.stringify(j))};
vm.createContext(scope);vm.runInContext(code.slice(code.indexOf('function validate('),code.indexOf('\nfunction rect(')),scope);
vm.runInContext(code.slice(code.indexOf('function blankTheme('),code.indexOf('function selectTheme(')),scope);assert.equal(scope.blankTheme().author,'');assert.equal(scope.blankTheme().background.type,'keep');assert.equal(scope.blankTheme().panel.clock,true);assert.ok(!code.includes("fetch('/v1/presets')"));
const presets=JSON.parse(fs.readFileSync(__dirname+'/../../community/presets.json','utf8'));
for(const theme of presets){assert.equal(Object.keys(scope.validate(theme).layouts).length,21);}
const theme=structuredClone(presets[0]);theme.memo='PRIVATE';theme.clock.secret='PRIVATE';theme.widgetSkins.clock={font:'serif',calendar:'PRIVATE'};
assert.ok(!JSON.stringify(scope.validate(theme)).includes('PRIVATE'));
theme.layouts['full/clock']='NaN,0,1';assert.throws(()=>scope.validate(theme));
vm.runInContext('let theme='+JSON.stringify(presets[0])+';'+code.slice(code.indexOf('function styleFor('),code.indexOf('function rgba(')),scope);
vm.runInContext("theme.clock.font='serif';theme.clock.color='#123456';theme.widgetSkins.clock={};",scope);assert.equal(scope.styleFor('clock').font,'serif');assert.equal(scope.styleFor('clock').textColor,'#123456');
vm.runInContext("theme.widgetSkins.clock={font:'mono',textColor:'#ABCDEF'};",scope);assert.equal(scope.styleFor('clock').font,'mono');assert.equal(scope.styleFor('clock').textColor,'#ABCDEF');
vm.runInContext("theme.widgetStyles.clock='classic';",scope);assert.equal(scope.styleFor('clock').font,'serif');assert.equal(scope.styleFor('clock').textColor,'#ABCDEF');
vm.runInContext("theme.widgetStyles.clock='theme';",scope);assert.equal(scope.styleFor('clock').font,'mono');
(async()=>{await assert.rejects(pack([['../escape','x']]));await assert.rejects(pack([['live-theme.json','a'],['live-theme.json','b']]));await assert.rejects(pack([['live-theme.json',new Uint8Array(65537)]]));
const zip=await pack([['live-theme.json',JSON.stringify(scope.validate(presets[0]))]]);fs.writeFileSync(require('os').tmpdir()+'/node-export.zip',Buffer.from(await zip.arrayBuffer()));console.log('PASS: presets, private-field stripping, invalid layout, ZIP allowlist, duplicates, size limits');})();
{const old=scope.blankTheme();old.panel.climate=true;old.panel.seat=true;old.widgetStyles={climate:'classic',clock:'minimal'};old.widgetSkins={climate:{color:'#112233'}};old.layouts={'full/climate':'0.1,0.1,1','split2/seat':'0.1,0.1,1','full/clock':'0.2,0.2,1'};const j=scope.validate(old);assert.ok(!('climate' in j.panel)&&!('seat' in j.panel)&&!('climate' in (j.widgetStyles||{}))&&!('climate' in (j.widgetSkins||{}))&&!('full/climate' in (j.layouts||{}))&&!('split2/seat' in (j.layouts||{}))&&j.layouts['full/clock']);console.log('PASS: old theme naming climate/seat opens, retired widgets dropped');}
