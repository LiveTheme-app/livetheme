// Self-check for the virtual groove: varied parts, kick/snare/hat differ, silent when paused, songs differ.
const fs = require('fs'), assert = require('assert');
global.window = {devicePixelRatio: 1}; global.innerWidth = 100; global.innerHeight = 100; global.addEventListener = () => {};
global.document = {getElementById: () => ({getContext: () => ({})})};
const Live = new Function(fs.readFileSync(process.argv[2] || __dirname + '/../web-samples/yunseul-lake/web/common.js', 'utf8') + '; return Live;')();
const s = Live.s; s.playing = true; s.bpm = 120; s.since = 0; s.title = '느린 오후의 플레이리스트'; s.artist = 'x';
const parts = new Set(), sample = []; let kicks = 0, snares = 0, hats = 0;
for (let t = 0; t < 120000; t += 1000 / 30) { const g = Live.groove(t); parts.add(g.part); sample.push(g); if (g.kick > .7) kicks++; if (g.snare > .7) snares++; if (g.hat > .5) hats++; }
assert.deepStrictEqual([...parts].sort(), ['break', 'build', 'drop', 'intro']);
assert(kicks > 0 && snares > 0 && hats > 0);
const corr = sample.reduce((a, g) => a + Math.abs(g.kick - g.snare), 0) / sample.length; assert(corr > .05, 'kick and snare move differently');
assert(sample.some(g => g.drop > .9), 'a drop happens');
const e = sample.map(g => g.energy); assert(Math.max(...e) - Math.min(...e) > .5, 'energy rises and falls');
const a = JSON.stringify(Array.from({length: 60}, (_, i) => Live.groove(i * 125).kick.toFixed(2)));
s.title = '밤 드라이브'; const b = JSON.stringify(Array.from({length: 60}, (_, i) => Live.groove(i * 125).kick.toFixed(2)));
assert.notStrictEqual(a, b, 'songs get different patterns');
s.playing = false; assert.strictEqual(Live.groove(5000).kick, 0);
// Every title must play without throwing, including seeds >= 2^31 (signed-shift index bug fixed 2026-11-13): 304 songs.
s.playing = true;
const hash = str => { let x = 7; for (const c of str) x = (Math.imul(x, 31) + c.charCodeAt(0)) | 0; return x >>> 0; };
const songs = [['느린 오후의 플레이리스트', '제작용 예시 곡'], ['느린 오후의 플레이리스트', 'x'], ['밤 드라이브', 'x'], ['Dynamite', '']];
for (let i = 0; i < 300; i++) songs.push(['곡 ' + i, i % 2 ? 'artist ' + i : '']);
assert(songs.some(([t, a]) => hash(t + '\u0000' + a) >= 2 ** 31), 'covers seeds >= 2^31');
for (const [title, artist] of songs) {
  s.title = title; s.artist = artist;
  for (let t = 0; t < 60000; t += 250) assert.doesNotThrow(() => Live.groove(t), `${title} / ${artist} @${t}ms`);
}
console.log('groove ok', {parts: [...parts], kicks, snares, hats, songs: songs.length});
