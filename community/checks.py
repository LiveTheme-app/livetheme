"""Real ZIP and HTTP checks, using isolated temporary storage."""
import io, json, zipfile, tempfile, threading, urllib.request, urllib.error, hashlib
from pathlib import Path
from server import inspect_package, make_server

presets=json.loads(Path(__file__).with_name('presets.json').read_text(encoding='utf-8'))
def package(config,extra=()):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:
        z.writestr('live-theme.json',json.dumps(config))
        for key,value in extra:z.writestr(key,value)
    return out.getvalue()

def rejects(config,extra=()):
    try:inspect_package(package(config,extra))
    except (ValueError,KeyError,TypeError):return
    raise AssertionError('Invalid package accepted')

for preset in presets:
    assert len(inspect_package(package(preset))['layouts'])==21
bad=json.loads(json.dumps(presets[0]));bad['layouts']['full/clock']='NaN,0,1';rejects(bad)
bad=json.loads(json.dumps(presets[0]));bad['widgetSkins']['battery']={'opacity':101};rejects(bad)
for trim in ('none','leaf','frost','ripple','neon','line','paper'):
    themed=json.loads(json.dumps(presets[0]));themed['panel']['trim']=trim;themed['widgetSkins']['music']={'trim':trim};clean=inspect_package(package(themed));assert clean['panel']['trim']==trim and clean['widgetSkins']['music']['trim']==trim
bad=json.loads(json.dumps(presets[0]));bad['panel']['trim']='script';rejects(bad)
bad=json.loads(json.dumps(presets[0]));bad['widgetSkins']['music']={'trim':'script'};rejects(bad)
for design in ('theme','minimal','focus','classic'):
    themed=json.loads(json.dumps(presets[0]));themed['widgetStyles']['battery']=design;clean=inspect_package(package(themed));assert clean['widgetStyles']['battery']==design and clean['widgetSkins']==themed['widgetSkins']
bad=json.loads(json.dumps(presets[0]));bad['widgetStyles']['music']='script';rejects(bad)
rejects(presets[0],[('../escape','x')]);rejects(presets[0],[('effect.js','alert(1)')])
ref=json.loads(json.dumps(presets[0]));ref['background']={'type':'workshop','id':'1234567890','index':0};rejects(ref,[('background.mp4',b'0000ftyp0000')])  # reference-only backgrounds may not carry artwork
bad=json.loads(json.dumps(presets[0]));bad['background']['private']='SECRET';bad['clock']['secret']='SECRET';bad['memo']='SECRET'
assert 'SECRET' not in json.dumps(inspect_package(package(bad)))
with tempfile.TemporaryDirectory() as root:
    server=make_server(root,0);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    base='http://127.0.0.1:'+str(server.server_port)
    def get(path):return urllib.request.urlopen(base+path,timeout=10)
    try:
        assert json.load(get('/health'))['mode']=='local-test'
        raw=package(presets[0]);req=urllib.request.Request(base+'/v1/themes',data=raw,headers={'Content-Type':'application/zip'})
        item=json.load(urllib.request.urlopen(req,timeout=10));received=get('/v1/themes/'+item['id']+'.zip').read()
        assert received==raw and hashlib.sha256(received).hexdigest()==item['sha256']
        assert len(json.load(get('/v1/themes'))['items'])==1
        again=json.load(urllib.request.urlopen(urllib.request.Request(base+'/v1/themes',data=raw,headers={'Content-Type':'application/zip'}),timeout=10));listed=json.load(get('/v1/themes'))['items']
        assert len(listed)==1 and listed[0]['id']==again['id'] and again['version']==2,'re-share replaces as a new version'
        item=again
        assert b'editor.js' in get('/creator/').read()
        req=urllib.request.Request(base+'/web-samples/pixel-sunset/web/wide.webp',headers={'Range':'bytes=0-15'})
        response=urllib.request.urlopen(req,timeout=10);assert response.status==206 and len(response.read())==16
        for path in ['/creator-source/3519888361/../../server.py','/creator/editor.py']:
            try:get(path)
            except urllib.error.HTTPError as e:assert e.code==404
            else:raise AssertionError('Traversal/static allowlist')
        req=urllib.request.Request(base+'/v1/themes',data=raw,headers={'Content-Type':'application/zip','Origin':'https://other.example'})
        try:urllib.request.urlopen(req,timeout=10)
        except urllib.error.HTTPError as e:assert e.code==403
        else:raise AssertionError('Cross-site write accepted')
        # Web themes: accepted with web/ files, preview serves the runtime first with a CSP, unsafe files refused.
        web=dict(presets[0]);web['background']={'type':'web','requests':['ambient.color']};page=b'<!doctype html><p>hi'
        assert inspect_package(package(web,[('web/index.html',page),('web/a.js',b'1')]))['background']['requests']==['ambient.color']
        for extra,config in [([('web/index.html',page),('web/../x.js',b'1')],web),([('web/index.html',page),('web/a.exe',b'1')],web),([('web/a.js',b'1')],web),([('web/index.html',page)],presets[0])]:
            rejects(config,extra)
        bad=dict(web);bad['background']={'type':'web','requests':['seat.heat']};rejects(bad,[('web/index.html',page)])
        preview=io.BytesIO()
        with zipfile.ZipFile(preview,'w') as z:z.writestr('web/index.html',page);z.writestr('web/a.js',b'console.log(1)')
        urllib.request.urlopen(urllib.request.Request(base+'/v1/preview',data=preview.getvalue(),headers={'Content-Type':'application/zip'}),timeout=10)
        response=get('/preview/index.html');body=response.read()
        assert body.startswith(b'<script>') and b'livetheme:init' in body and body.endswith(page) and "connect-src 'self'" in response.headers['Content-Security-Policy']
        assert get('/preview/a.js').read()==b'console.log(1)'
        assert {i['id'] for i in json.load(get('/v1/web-samples'))['items']}>={'charge-ring','mood-aurora'}
    finally:server.shutdown();server.server_close()
print('PASS: package validation, private preview filtering, upload/download SHA256, static allowlist, video range, Origin restriction')
