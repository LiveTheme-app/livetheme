"""Loopback-only theme community for PC/emulator testing; no public account service."""
import argparse, hashlib, io, json, os, re, stat, threading, time, uuid, zipfile, math, mimetypes
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MAX_ZIP = 80 * 1024 * 1024
LOCK = threading.Lock()
KEYS = ('clock', 'calendar', 'music', 'memo', 'dday', 'weather', 'battery', 'ambient', 'trip', 'navigation')
RETIRED = ('seat', 'climate')  # removed widgets (1.35, 1.37): older packages may still name them; they are ignored, not rejected

# Web themes: same limits and allowed file types as the car app (CreatorTheme.webEntry / SceneBackground.WEB_TYPES).
WEB_FILES, WEB_BYTES = 200, 40 * 1024 * 1024
WEB_TYPES = ('html','js','css','json','png','jpg','jpeg','webp','gif','svg','mp4','woff2')
WEB_REQUESTS = ('ambient.color','ambient.brightness','media.playPause','media.next','media.prev')
REPLACEABLE = ('clock','music','battery','calendar','weather','dday')
def web_entry(name):
    if not name.startswith('web/') or len(name) > 160 or '..' in name or '//' in name or '\\' in name or not re.fullmatch(r'web/[A-Za-z0-9._\-/]+', name): return False
    dot, slash = name.rfind('.'), name.rfind('/')
    return dot > slash and name[dot+1:].lower() in WEB_TYPES

def inspect_package(raw):
    if not 0 < len(raw) <= MAX_ZIP:
        raise ValueError('ZIP size')
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = archive.infolist()
        allowed = {'live-theme.json': 65536, 'background.mp4': 64*1024*1024, 'font.ttf': 4*1024*1024, 'font.otf': 4*1024*1024}
        names = [e.filename for e in entries]
        if len(entries) > 3 + WEB_FILES or len(set(names)) != len(names) or 'live-theme.json' not in names:
            raise ValueError('ZIP entries')
        web_bytes = 0
        for e in entries:
            if stat.S_ISLNK(e.external_attr >> 16) or e.is_dir():
                raise ValueError('ZIP content')
            if web_entry(e.filename):
                web_bytes += e.file_size
                if web_bytes > WEB_BYTES: raise ValueError('Web theme size')
                continue
            if e.filename not in allowed or e.file_size > allowed[e.filename]:
                raise ValueError('ZIP content')
        config = json.loads(archive.read('live-theme.json'))
        if config.get('format') != 'live-theme' or config.get('version') != 1:
            raise ValueError('Theme format')
        name, author = config.get('name', ''), config.get('author', '')
        if not isinstance(name, str) or not name.strip() or len(name) > 60 or not isinstance(author, str) or len(author) > 40 or '\0' in name+author:
            raise ValueError('Theme name/author')
        background = config.get('background', {})
        if background.get('type') not in ('builtin', 'video', 'workshop', 'web'):
            raise ValueError('Choose a portable background before sharing')
        has_web = any(web_entry(n) for n in names)
        if background['type'] == 'web':
            if 'web/index.html' not in names: raise ValueError('Web theme index')
            requests = background.get('requests', [])
            if not isinstance(requests, list) or len(set(requests)) != len(requests) or any(r not in WEB_REQUESTS for r in requests): raise ValueError('Web requests')
            replaces = config.get('replacesWidgets', [])
            if not isinstance(replaces, list) or len(set(replaces)) != len(replaces) or any(r not in REPLACEABLE for r in replaces): raise ValueError('Replaced widgets')
        elif has_web: raise ValueError('Web files only in web themes')
        index = background.get('index', 0)
        if type(index) is not int or not 0 <= index <= 2:
            raise ValueError('Background index')
        if background['type'] == 'video' and 'background.mp4' not in names:
            raise ValueError('Missing video')
        if background['type'] == 'workshop':
            if not re.fullmatch(r'[0-9]{6,14}',str(background.get('id',''))) or 'background.mp4' in names:
                raise ValueError('Workshop references must not redistribute artwork')
        for n in names:
            if n != 'live-theme.json':
                with archive.open(n) as stream:
                    header = stream.read(32)
                    if n.endswith('.mp4') and (len(header) < 12 or header[4:8] != b'ftyp'):
                        raise ValueError('Video header')
                    if n.startswith('font.') and header[:4] not in (b'\x00\x01\x00\x00', b'OTTO'):
                        raise ValueError('Font header')
        for group in ('panel', 'widgetStyles', 'widgetSkins'):
            if isinstance(config.get(group), dict):
                config[group] = {k: v for k, v in config[group].items() if k not in RETIRED}
        if isinstance(config.get('layouts'), dict):
            config['layouts'] = {k: v for k, v in config['layouts'].items() if k.split('/')[-1] not in RETIRED}
        panel = config.get('panel', {})
        for k in (*KEYS, 'date'):
            if k in panel and type(panel[k]) is not bool: raise ValueError('Panel visibility')
        safe_panel = {k: panel.get(k, k in ('clock','calendar','music','memo')) for k in KEYS}
        safe_panel['date'] = bool(panel.get('date', True))
        for k, fallback in [('color','#142129'),('textColor','#E0EEE5'),('mutedColor','#AAC8C4'),('accentColor','#A8DCD0')]:
            value = panel.get(k, fallback)
            if not isinstance(value,str) or not re.fullmatch(r'#[0-9a-fA-F]{6}', value):
                raise ValueError('Palette')
            safe_panel[k] = value
        for k, low, high, fallback in [('opacity',0,100,60),('corner',8,36,24)]:
            v = panel.get(k, fallback)
            if type(v) not in (int,float) or not low <= v <= high:
                raise ValueError('Panel bounds')
            safe_panel[k] = v
        font = panel.get('font','sans')
        if font not in ('sans','serif','mono'):
            raise ValueError('Panel font')
        safe_panel['font'] = font
        trims=('none','leaf','frost','ripple','neon','line','paper')
        if panel.get('trim','none') not in trims: raise ValueError('Panel trim')
        safe_panel['trim']=panel.get('trim','none')
        # Previews contain declarative styling only; personal contents and arbitrary fields are excluded.
        preview = {'format':'live-theme','version':1,'name':name.strip(),'author':author,'background':{k:background[k] for k in ('type','id','index','requests') if k in background},'panel':safe_panel}
        clock = config.get('clock',{})
        for field, choices, fallback in [('design',('digital','stacked','analog'),'digital'),('font',('light','serif','mono','bold','custom'),'light')]:
            if clock.get(field,fallback) not in choices: raise ValueError('Clock option')
        if clock.get('font') == 'custom' and not any(n.startswith('font.') for n in names): raise ValueError('Missing font')
        for block in [clock,config.get('effect',{})]:
            if 'color' in block and not re.fullmatch(r'#[0-9a-fA-F]{6}',str(block['color'])): raise ValueError('Clock/effect color')
        for field,low,high in [('size',.6,1.3),('shadow',0,24),('plate',0,80)]:
            if field in clock and (type(clock[field]) not in (int,float) or not math.isfinite(clock[field]) or not low <= clock[field] <= high): raise ValueError('Clock bounds')
        if not isinstance(clock.get('caption',''),str) or len(clock.get('caption','')) > 60 or '\0' in clock.get('caption',''): raise ValueError('Caption')
        for key in ('seconds','hour24'):
            if key in clock and type(clock[key]) is not bool: raise ValueError('Clock visibility')
        effect = config.get('effect',{})
        if effect.get('type','none') not in ('none','fireflies','snow'): raise ValueError('Effect')
        for field,low,high in [('amount',0,60),('speed',.1,2)]:
            if field in effect and (type(effect[field]) not in (int,float) or not math.isfinite(effect[field]) or not low <= effect[field] <= high): raise ValueError('Effect bounds')
        styles = config.get('widgetStyles',{})
        if any(k not in KEYS or v not in ('theme','minimal','focus','classic','clear','glass','solid') for k,v in styles.items()): raise ValueError('Widget style')
        styles={k:('theme' if v in ('clear','glass','solid') else v) for k,v in styles.items()}
        skins = config.get('widgetSkins',{})
        if len(skins) > len(KEYS): raise ValueError('Skin count')
        for key,skin in skins.items():
            if key not in KEYS or not isinstance(skin,dict): raise ValueError('Skin key')
            for field in ('color','textColor','mutedColor','accentColor'):
                if field in skin and not re.fullmatch(r'#[0-9a-fA-F]{6}',str(skin[field])): raise ValueError('Skin color')
            if skin.get('font','sans') not in ('sans','serif','mono'): raise ValueError('Skin font')
            if skin.get('trim','none') not in trims: raise ValueError('Skin trim')
            for field,low,high in [('corner',8,36),('opacity',0,100)]:
                if field in skin and (type(skin[field]) not in (int,float) or not math.isfinite(skin[field]) or not low <= skin[field] <= high): raise ValueError('Skin bounds')
        layouts = config.get('layouts',{})
        if len(layouts)>len(KEYS)*3: raise ValueError('Layout count')
        for key,raw in layouts.items():
            if not re.fullmatch(r'(full|split2|split3)/('+'|'.join(KEYS)+r')',key): raise ValueError('Layout key')
            values = [float(v) for v in raw.split(',')]
            if len(values) not in (3,4) or any(not math.isfinite(v) or v < (0 if i<2 else .1) or v > (1 if i<2 else 8) for i,v in enumerate(values)): raise ValueError('Layout range')
        preview['clock']={k:clock[k] for k in ('design','font','color','size','shadow','plate','caption','seconds','hour24') if k in clock}
        preview['effect']={k:effect[k] for k in ('type','color','amount','speed') if k in effect}
        preview['widgetStyles']=styles
        preview['widgetSkins']={key:{k:v for k,v in skin.items() if k in ('color','textColor','mutedColor','accentColor','font','corner','opacity','trim')} for key,skin in skins.items()}
        preview['layouts']=layouts
        preview['links']=safe_links(config.get('links',{}))
        return preview

# Same fixed catalogue as editor.js LINK_TARGETS: declarative only, read-only car values.
LINK_TARGETS = {'glow':(('battery.level','ambient.brightness','music.playing','battery.charging'),True),'tint':(('ambient.color','music.color','battery.charging'),True),'motion':(('music.playing','battery.charging'),False),'dim':(('day.night','drive.paused'),False)}
def safe_links(links):
    if not isinstance(links,dict): raise ValueError('Links')
    out = {}
    for target,link in links.items():
        if target not in LINK_TARGETS or not isinstance(link,dict): raise ValueError('Link target')
        sources,colored = LINK_TARGETS[target]
        if link.get('source') not in sources: raise ValueError('Link source')
        strength = link.get('strength',50)
        if type(strength) not in (int,float) or not math.isfinite(strength) or not 0 <= strength <= 100: raise ValueError('Link strength')
        entry = {'source':link['source'],'strength':strength}
        if colored:
            color = link.get('color','#BFE8D2')
            if not isinstance(color,str) or not re.fullmatch(r'#[0-9a-fA-F]{6}',color): raise ValueError('Link color')
            entry['color'] = color
        out[target] = entry
    return out

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(fmt % args, flush=True)
    def json(self, status, value):
        data = json.dumps(value, ensure_ascii=False).encode('utf-8')
        self.send_response(status); self.send_header('Content-Type','application/json; charset=utf-8'); self.send_header('Content-Length',str(len(data))); self.send_header('X-Content-Type-Options','nosniff'); self.end_headers(); self.wfile.write(data)
    def send_file(self, file, mime=None):
        if not file.is_file(): return self.json(404, {'error':'Not found'})
        size=file.stat().st_size; start=0; end=size-1; status=200
        requested=self.headers.get('Range')
        if requested:
            match=re.fullmatch(r'bytes=(\d+)-(\d*)',requested)
            if not match or int(match[1])>=size: return self.json(416,{'error':'Invalid range'})
            start=int(match[1]); end=min(size-1,int(match[2]) if match[2] else size-1); status=206
            if end<start:return self.json(416,{'error':'Invalid range'})
        self.send_response(status); self.send_header('Content-Type',mime or mimetypes.guess_type(file.name)[0] or 'application/octet-stream');self.send_header('Content-Length',str(end-start+1));self.send_header('Accept-Ranges','bytes');self.send_header('X-Content-Type-Options','nosniff')
        if status==206:self.send_header('Content-Range',f'bytes {start}-{end}/{size}')
        if file.suffix=='.html':self.send_header('Content-Security-Policy',"default-src 'self' blob: data:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' blob: data:; media-src 'self' blob:; font-src 'self' blob: data:; frame-src 'self' http://localhost:"+str(self.server.server_port)+" http://127.0.0.1:"+str(self.server.server_port))
        self.end_headers()
        try:
            with file.open('rb') as stream:
                stream.seek(start);remaining=end-start+1
                while remaining:
                    chunk=stream.read(min(65536,remaining))
                    if not chunk:break
                    self.wfile.write(chunk);remaining-=len(chunk)
        except (BrokenPipeError,ConnectionResetError):pass
    def do_GET(self):
        workspace=Path(__file__).resolve().parent.parent
        if self.path in ('/creator','/creator/'):
            return self.send_file(workspace/'creator-kit/editor/index.html')
        if self.path.startswith('/creator/'):
            name=self.path[len('/creator/'):]
            if name not in ('editor.js','editor.css','zip.js','vehicle-preview.js','web-preview.js','guide.js','samples.json'):return self.json(404,{'error':'Not found'})
            return self.send_file(workspace/'creator-kit/editor'/name)
        if self.path=='/v1/presets':
            file=workspace/'community/presets.json'
            return self.json(200,{'items':json.loads(file.read_text(encoding='utf-8')) if file.is_file() else []})
        match=re.fullmatch(r'/creator-scene/([0-9]{6,14})',self.path)
        if match:
            identity=match[1]
            data=("<!doctype html><style>html,body,#scene{margin:0;width:100%;height:100%;overflow:hidden;background:#10232b}</style><div id='scene'></div><script src='/engine/webwallgl.global.min.js'></script><script>WebWallGL.mount(document.querySelector('#scene'),{source:WebWallGL.httpSource('/creator-source/"+identity+"'),fps:24,volume:0,fit:'cover',scripts:false,audio:null,media:null}).catch(e=>document.querySelector('#scene').textContent='이 장면의 일부 효과는 미리보기에서 지원하지 않아요.');</script>").encode('utf-8')
            self.send_response(200);self.send_header('Content-Type','text/html; charset=utf-8');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data);return
        # The app's built-in video backgrounds, when present (this repository leaves them out; see README).
        match=re.fullmatch(r'/builtin/([0-2])\.(mp4|jpg)',self.path)
        if match:
            assets=workspace/'app/src/main/assets'
            clip=('background.mp4','themes/cafe.mp4','themes/coast.mp4')[int(match[1])]
            return self.send_file(assets/clip if match[2]=='mp4' else assets/'themes'/('preview-'+match[1]+'.jpg'))
        if self.path=='/engine/webwallgl.global.min.js':return self.send_file(workspace/'app/src/main/assets/engine/webwallgl.global.min.js','application/javascript')
        match=re.fullmatch(r'/creator-source/([0-9]{6,14})/(.+)',self.path)
        if match:
            base=(workspace/'evidence-theme-hub/workshop'/match[1]).resolve();file=(base/match[2]).resolve()
            if not file.is_relative_to(base) or file.suffix.lower() in ('.html','.js','.py','.exe') or '..' in Path(match[2]).parts:return self.json(404,{'error':'Not found'})
            return self.send_file(file)
        if self.path.startswith('/preview/'):
            return self.web_preview(self.path[len('/preview/'):].split('?')[0])
        samples = workspace/'creator-kit/web-samples'
        if self.path == '/v1/web-samples':
            # Read-only starters shipped with the creator kit: each folder is a theme (live-theme.json + web/).
            items = []
            for folder in sorted(p for p in samples.iterdir() if p.is_dir()) if samples.is_dir() else []:
                files = sorted(str(f.relative_to(folder)).replace('\\','/') for f in folder.rglob('*') if f.is_file())
                if 'live-theme.json' in files and all(f == 'live-theme.json' or web_entry(f) for f in files): items.append({'id':folder.name,'files':files})
            return self.json(200, {'items':items})
        match = re.fullmatch(r'/web-samples/([a-z0-9-]{1,40})/(.+)', self.path)
        if match:
            folder = (samples/match[1]).resolve(); file = (folder/match[2]).resolve()
            if not file.is_relative_to(folder) or (match[2] != 'live-theme.json' and not web_entry(match[2])): return self.json(404, {'error':'Not found'})
            return self.send_file(file)
        if self.path == '/health':
            return self.json(200, {'mode':'local-test'})
        if self.path == '/v1/themes':
            items = []
            for f in self.server.root.glob('*.json'):
                try:
                    item = json.loads(f.read_text(encoding='utf-8'))
                    if (self.server.root/(item['id']+'.zip')).is_file(): items.append(item)
                except (OSError, ValueError, KeyError): pass
            return self.json(200, {'mode':'local-test','items':sorted(items,key=lambda x:x['created'],reverse=True)[:50]})
        match = re.fullmatch(r'/v1/themes/([0-9a-f-]{36})\.zip', self.path)
        if not match: return self.json(404, {'error':'Not found'})
        f = self.server.root/(match[1]+'.zip')
        if not f.is_file(): return self.json(404, {'error':'Not found'})
        self.send_response(200); self.send_header('Content-Type','application/zip'); self.send_header('Content-Length',str(f.stat().st_size)); self.send_header('Content-Disposition','attachment; filename="theme-'+match[1]+'.zip"'); self.send_header('X-Content-Type-Options','nosniff'); self.end_headers()
        try:
            with f.open('rb') as stream:
                while chunk := stream.read(65536): self.wfile.write(chunk)
        except (BrokenPipeError, ConnectionResetError): pass
    def web_preview(self, name):
        """The editor's current web theme, served like the car app serves it: allowed types only, CSP, runtime injected first."""
        files = getattr(self.server, 'preview', {})
        path = 'web/' + name
        if not web_entry(path) or path not in files: return self.json(404, {'error':'Not found'})
        data = files[path]
        if name == 'index.html':
            runtime = (Path(__file__).resolve().parent.parent/'app/src/main/assets/engine/livetheme-runtime.js').read_bytes()
            data = b'<script>' + runtime + b'</script>\n' + data
        self.send_response(200); self.send_header('Content-Type', mimetypes.guess_type(name)[0] or 'application/octet-stream'); self.send_header('Content-Length', str(len(data)))
        # The editor loads this from its other loopback name (localhost vs 127.0.0.1), so theme code runs cross-origin and cannot reach the editor page.
        self.send_header('Content-Security-Policy', "default-src 'self' blob: data:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; media-src 'self' blob:; font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'")
        self.send_header('X-Content-Type-Options', 'nosniff'); self.end_headers(); self.wfile.write(data)
    def do_POST(self):
        origin = self.headers.get('Origin')
        if origin is not None and origin not in (f'http://127.0.0.1:{self.server.server_port}',f'http://localhost:{self.server.server_port}'): return self.json(403, {'error':'Origin not allowed'})
        if self.path == '/v1/preview':
            # One in-memory slot for the editor's live web preview; nothing is written to disk or shared.
            try:
                length = int(self.headers.get('Content-Length','0'))
                if not 0 < length <= WEB_BYTES + 65536: raise ValueError('Preview size')
                with zipfile.ZipFile(io.BytesIO(self.rfile.read(length))) as archive:
                    entries = [e for e in archive.infolist() if not e.is_dir()]
                    if len(entries) > WEB_FILES or any(not web_entry(e.filename) or stat.S_ISLNK(e.external_attr >> 16) for e in entries) or sum(e.file_size for e in entries) > WEB_BYTES: raise ValueError('Preview files')
                    self.server.preview = {e.filename: archive.read(e) for e in entries}
                return self.json(200, {'files': len(self.server.preview)})
            except (ValueError, zipfile.BadZipFile, OSError):
                return self.json(400, {'error':'Invalid web preview'})
        if self.path != '/v1/themes': return self.json(404, {'error':'Not found'})
        try:
            length = int(self.headers.get('Content-Length','0'))
            if not 0 < length <= MAX_ZIP or self.headers.get('Content-Type','').split(';')[0] != 'application/zip':
                raise ValueError('ZIP request')
            self.connection.settimeout(20)
            raw = self.rfile.read(length)
            if len(raw) != length: raise ValueError('Incomplete ZIP')
            preview = inspect_package(raw)
            with LOCK:
                files = list(self.server.root.glob('*.zip'))
                if len(files) >= 50 or sum(f.stat().st_size for f in files)+length > 500*1024*1024:
                    return self.json(507, {'error':'Local test storage is full'})
                # Sharing the same theme again (same name and author) replaces it as a new version instead of piling up look-alike cards.
                older = []
                for f in self.server.root.glob('*.json'):
                    try:
                        known = json.loads(f.read_text(encoding='utf-8'))
                        if known['name'] == preview['name'] and known.get('author','') == preview['author']: older.append(known)
                    except (OSError, ValueError, KeyError): pass
                identity = str(uuid.uuid4())
                item = {'id':identity,'name':preview['name'],'author':preview['author'],'bytes':length,'sha256':hashlib.sha256(raw).hexdigest(),'created':time.time(),'version':1+max([k.get('version',1) for k in older] or [0]),'preview':preview}
                temporary = self.server.root/(identity+'.tmp')
                temporary.write_bytes(raw); os.replace(temporary,self.server.root/(identity+'.zip'))
                temporary.write_text(json.dumps(item,ensure_ascii=False),encoding='utf-8'); os.replace(temporary,self.server.root/(identity+'.json'))
                for known in older:
                    for suffix in ('.json','.zip'): (self.server.root/(known['id']+suffix)).unlink(missing_ok=True)
            return self.json(201, item)
        except (ValueError, OSError, zipfile.BadZipFile, KeyError, TypeError, RuntimeError, AttributeError, OverflowError):
            return self.json(400, {'error':'Invalid or unsupported theme ZIP'})

def make_server(root, port=8787):
    root = Path(root).resolve(); root.mkdir(parents=True,exist_ok=True)
    server = ThreadingHTTPServer(('127.0.0.1',port),Handler); server.root = root
    return server

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--port',type=int,default=8787); parser.add_argument('--data',default=str(Path(__file__).parent/'data')); args = parser.parse_args()
    server = make_server(args.data,args.port); print('Local test community: http://127.0.0.1:'+str(server.server_port),flush=True)
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close()
