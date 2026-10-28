package io.hmg.wallpaperapp;

import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Portable declarative themes. No executable code, personal notes or calendar data. */
final class CreatorTheme {
 static JSONObject defaults(){try{return validate(new JSONObject("{\"format\":\"live-theme\",\"version\":1}"));}catch(Exception e){throw new IllegalStateException(e);}}
 static JSONObject validate(JSONObject input)throws Exception{
  if(!"live-theme".equals(input.optString("format"))||input.optInt("version")!=1)throw new IOException("지원하지 않는 라이브테마 버전입니다");
  JSONObject out=new JSONObject();out.put("format","live-theme").put("version",1);
  out.put("name",text(input,"name","나의 테마",60)).put("author",text(input,"author","",40));
  JSONObject bg=input.optJSONObject("background");if(bg==null)bg=new JSONObject();JSONObject background=new JSONObject();background.put("type",choice(bg,"type","keep","keep","builtin","video","workshop","web")).put("index",(int)number(bg,"index",0,0,2));if("workshop".equals(background.optString("type"))){String id=bg.optString("id");if(!id.matches("[0-9]{6,14}"))throw new IOException("창작마당 작품 번호가 올바르지 않습니다");background.put("id",id);}if("web".equals(background.optString("type")))background.put("requests",requests(bg.optJSONArray("requests")));out.put("background",background);
  JSONObject panel=input.optJSONObject("panel");if(panel==null)panel=new JSONObject();JSONObject widget=new JSONObject();for(String key:new String[]{"clock","date","calendar","music","memo"})widget.put(key,panel.optBoolean(key,true));for(String key:WidgetShelf.KEYS)if(!widget.has(key))widget.put(key,panel.optBoolean(key,false));widget.put("dday",panel.optBoolean("dday",false)).put("weather",panel.optBoolean("weather",false));widget.put("color",color(panel,"color","#142129")).put("opacity",number(panel,"opacity",60,0,100));widget.put("textColor",color(panel,"textColor","#E0EEE5")).put("mutedColor",color(panel,"mutedColor","#AAC8C4")).put("accentColor",color(panel,"accentColor","#A8DCD0")).put("font",choice(panel,"font","sans","sans","serif","mono")).put("corner",number(panel,"corner",24,8,36));widget.put("trim",choice(panel,"trim","none","none","leaf","frost","ripple","neon","line","paper"));out.put("panel",widget);
  JSONObject styles=input.optJSONObject("widgetStyles"),safeStyles=new JSONObject();for(String key:WidgetShelf.KEYS){String style=styles==null?"theme":choice(styles,key,"theme","theme","minimal","focus","classic","glass","clear","solid");safeStyles.put(key,java.util.Arrays.asList("glass","clear","solid").contains(style)?"theme":style);}out.put("widgetStyles",safeStyles);
  JSONObject rawSkins=input.optJSONObject("widgetSkins"),skins=new JSONObject();if(rawSkins!=null){if(rawSkins.length()>WidgetShelf.KEYS.length+WidgetShelf.RETIRED.size())throw new IOException("위젯 스킨이 너무 많습니다");Iterator<String> names=rawSkins.keys();while(names.hasNext()){String key=names.next();if(WidgetShelf.RETIRED.contains(key))continue;if(!Arrays.asList(WidgetShelf.KEYS).contains(key))throw new IOException("지원하지 않는 위젯 스킨입니다");JSONObject raw=rawSkins.getJSONObject(key),skin=new JSONObject();for(String colorKey:new String[]{"color","textColor","mutedColor","accentColor"})if(raw.has(colorKey))skin.put(colorKey,color(raw,colorKey,"#E0EEE5"));if(raw.has("font"))skin.put("font",choice(raw,"font","sans","sans","serif","mono"));if(raw.has("corner"))skin.put("corner",number(raw,"corner",24,8,36));if(raw.has("opacity"))skin.put("opacity",number(raw,"opacity",60,0,100));if(raw.has("trim"))skin.put("trim",choice(raw,"trim","none","none","leaf","frost","ripple","neon","line","paper"));skins.put(key,skin);}}out.put("widgetSkins",skins);
  JSONObject c=input.optJSONObject("clock");if(c==null)c=new JSONObject();JSONObject clock=new JSONObject();
  clock.put("design",choice(c,"design","digital","digital","stacked","analog"));
  clock.put("font",choice(c,"font","light","light","serif","mono","bold","custom"));
  clock.put("color",color(c,"color","#F3F7EF")).put("caption",text(c,"caption","",60));
  clock.put("size",number(c,"size",1,.6,1.3)).put("shadow",number(c,"shadow",12,0,24)).put("plate",number(c,"plate",0,0,80));
  clock.put("seconds",c.optBoolean("seconds",false)).put("hour24",c.optBoolean("hour24",true));out.put("clock",clock);
  JSONObject e=input.optJSONObject("effect");if(e==null)e=new JSONObject();JSONObject effect=new JSONObject();
  effect.put("type",choice(e,"type","none","none","fireflies","snow"));effect.put("color",color(e,"color","#D6F5B0"));
  effect.put("amount",number(e,"amount",24,0,60)).put("speed",number(e,"speed",1,.1,2));out.put("effect",effect);
  JSONObject layouts=input.optJSONObject("layouts"),safe=new JSONObject();
  if(layouts!=null){if(layouts.length()>(WidgetShelf.KEYS.length+WidgetShelf.RETIRED.size())*3)throw new IOException("배치 항목이 너무 많습니다");Iterator<String> keys=layouts.keys();while(keys.hasNext()){
   String key=keys.next();if(key.matches("(split2|split3|full)/("+String.join("|",WidgetShelf.RETIRED)+")"))continue;if(!key.matches("(split2|split3|full)/("+String.join("|",WidgetShelf.KEYS)+")"))throw new IOException("지원하지 않는 위젯 배치입니다");
   String raw=layouts.getString(key);String[] parts=raw.split(",",-1);if(parts.length!=3&&parts.length!=4)throw new IOException("위젯 크기 형식이 잘못됐습니다");
   for(int i=0;i<parts.length;i++){double v=Double.parseDouble(parts[i]);if(!Double.isFinite(v)||v<(i<2?0:.1)||v>(i<2?1:8))throw new IOException("위젯 배치 범위를 벗어났습니다");}safe.put(key,raw);
  }}out.put("layouts",safe);out.put("links",ThemeLinks.validate(input.optJSONObject("links")));
  JSONArray replaces=input.optJSONArray("replacesWidgets"),keptReplaces=new JSONArray();if(replaces!=null&&"web".equals(background.optString("type"))){Set<String> seen=new HashSet<>();for(int i=0;i<replaces.length();i++){String key=replaces.optString(i);if(!REPLACEABLE.contains(key)||!seen.add(key))throw new IOException("배경이 대신하는 위젯 목록을 확인해주세요");keptReplaces.put(key);}}if(keptReplaces.length()>0)out.put("replacesWidgets",keptReplaces);return out;
 }
 static JSONObject forApplication(JSONObject incoming,JSONObject current)throws Exception{
  JSONObject next=validate(incoming);next.put("layouts",new JSONObject());JSONObject existing=current.optJSONObject("panel");
  if(existing!=null)for(String key:panelKeys())next.getJSONObject("panel").put(key,existing.optBoolean(key));
  return next;
 }
 /** Changes a web theme may ask the app for; each still needs the user's confirmation. Fixed list. */
 static final List<String> REQUESTS=Arrays.asList("ambient.color","ambient.brightness","media.playPause","media.next","media.prev");
 /** Media requests are low risk: no confirmation, only a rate limit (MainActivity.onThemeRequest). */
 static boolean mediaRequest(String action){return action.startsWith("media.");}
 /** Estimated tempo for web themes: 72..135 bpm, stable per track. Derived from the title, not from the sound (no audio capture). Same formula as the editor preview. */
 static int estimatedBpm(String title,String artist){if(title==null||title.isEmpty())return 0;return 72+Math.floorMod((title+"\u0000"+artist).hashCode(),64);}
 /** Widgets a web theme draws itself; the app offers to hide them while that theme is on. */
 static final List<String> REPLACEABLE=Arrays.asList("clock","music","battery","calendar","weather","dday");
 private static JSONArray requests(JSONArray raw)throws IOException{JSONArray out=new JSONArray();if(raw==null)return out;Set<String> seen=new HashSet<>();for(int i=0;i<raw.length();i++){String r=raw.optString(i);if(!REQUESTS.contains(r)||!seen.add(r))throw new IOException("지원하지 않는 테마 요청입니다: "+r);out.put(r);}return out;}
 private static final int WEB_FILES=200;private static final long WEB_BYTES=40L*1024*1024;
 /** web/ entries: safe relative names with an allowed extension only. */
 static boolean webEntry(String name){if(!name.startsWith("web/")||name.length()>160||name.contains("..")||name.contains("//")||name.contains("\\")||!name.matches("web/[A-Za-z0-9._\\-/]+"))return false;String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);return name.lastIndexOf('.')>name.lastIndexOf('/')&&SceneBackground.WEB_TYPES.contains(ext);}
 static String[] panelKeys(){String[] out=java.util.Arrays.copyOf(WidgetShelf.KEYS,WidgetShelf.KEYS.length+1);out[out.length-1]="date";return out;}
 static JSONObject withoutLayouts(JSONObject theme)throws Exception{JSONObject next=validate(theme);next.put("layouts",new JSONObject());return next;}
 private static String text(JSONObject j,String key,String fallback,int max)throws Exception{String v=j.optString(key,fallback);if(v.length()>max||v.indexOf('\0')>=0)throw new IOException("텍스트 길이를 확인해주세요");return v;}
 private static String choice(JSONObject j,String key,String fallback,String... allowed)throws Exception{String v=j.optString(key,fallback);if(!Arrays.asList(allowed).contains(v))throw new IOException("지원하지 않는 디자인 옵션입니다: "+key);return v;}
 private static String color(JSONObject j,String key,String fallback)throws Exception{String v=j.optString(key,fallback);if(!v.matches("#[0-9a-fA-F]{6}"))throw new IOException("색상은 #RRGGBB 형식으로 입력해주세요");return v;}
 private static double number(JSONObject j,String key,double fallback,double min,double max)throws Exception{double v=j.has(key)?j.getDouble(key):fallback;if(!Double.isFinite(v)||v<min||v>max)throw new IOException("설정 범위를 벗어났습니다: "+key);return v;}
 static File importUri(Context context,Uri uri)throws Exception{
  if(!"content".equals(uri.getScheme()))throw new IOException("공유된 파일을 읽을 수 없습니다");
  try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("파일을 열 수 없습니다");return importStream(context,in);}
 }
 /** Same checks as a shared ZIP; also used for themes bundled in the APK (BundledThemes). */
 static File importStream(Context context,InputStream source)throws Exception{
  File staging=File.createTempFile("creator-",".zip",context.getCacheDir());File folder=null;boolean success=false;
  try{
   try(OutputStream out=new FileOutputStream(staging)){copy(source,out,200L*1024*1024);}
   try(ZipFile zip=new ZipFile(staging)){
    ZipEntry manifest=zip.getEntry("live-theme.json");if(manifest==null)return null;
    if(staging.length()>80L*1024*1024)throw new IOException("라이브테마 ZIP은 80 MiB 이하로 선택해주세요");
    if(zip.size()>3+WEB_FILES)throw new IOException("테마 파일이 너무 많습니다");
    Set<String> names=new HashSet<>();List<ZipEntry> web=new ArrayList<>();Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){
     ZipEntry e=entries.nextElement();if(e.isDirectory()&&e.getName().startsWith("web/")&&!e.getName().contains(".."))continue;
     boolean fixed=Arrays.asList("live-theme.json","font.ttf","font.otf","background.mp4").contains(e.getName());if(!fixed&&!webEntry(e.getName())||!names.add(e.getName())||e.isDirectory())throw new IOException("허용되지 않는 테마 파일입니다: "+e.getName());if(!fixed)web.add(e);
    }
    if(web.size()>WEB_FILES)throw new IOException("웹 배경 파일은 "+WEB_FILES+"개 이하로 넣어주세요");
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=zip.getInputStream(manifest)){copy(in,bytes,65536);}
    JSONObject config=validate(new JSONObject(bytes.toString("UTF-8")));
    File library=new File(context.getFilesDir(),"creator-themes");if(!library.isDirectory()&&!library.mkdirs())throw new IOException("저장 공간을 만들 수 없습니다");
    folder=new File(library,UUID.randomUUID().toString());if(!folder.mkdir())throw new IOException("테마 저장 실패");
    ZipEntry font=zip.getEntry("font.ttf");if(font==null)font=zip.getEntry("font.otf");
    if(font!=null){File target=new File(folder,font.getName());try(InputStream in=zip.getInputStream(font);OutputStream out=new FileOutputStream(target)){copy(in,out,4L*1024*1024);}checkFont(target);}
    if("custom".equals(config.getJSONObject("clock").getString("font"))&&font==null)throw new IOException("사용자 글꼴 파일이 없습니다");
    ZipEntry video=zip.getEntry("background.mp4");if(video!=null){File target=new File(folder,"background.mp4");try(InputStream in=zip.getInputStream(video);OutputStream output=new FileOutputStream(target)){copy(in,output,64L*1024*1024);}checkVideo(target);}
    if("video".equals(config.getJSONObject("background").getString("type"))&&video==null)throw new IOException("배경 영상 파일이 없습니다");
    boolean isWeb="web".equals(config.getJSONObject("background").getString("type"));if(isWeb&&!names.contains("web/index.html"))throw new IOException("웹 배경에는 web/index.html이 필요합니다");
    if(isWeb){long total=0;File root=new File(folder,"web").getCanonicalFile();for(ZipEntry e:web){File target=new File(folder,e.getName()).getCanonicalFile();if(!target.getPath().startsWith(root.getPath()+File.separator))throw new IOException("허용되지 않는 웹 배경 경로입니다");File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("저장 공간을 만들 수 없습니다");ByteArrayOutputStream counted=new ByteArrayOutputStream();try(InputStream in=zip.getInputStream(e)){copy(in,counted,WEB_BYTES-total);}total+=counted.size();Files.write(target.toPath(),counted.toByteArray());}}
    else if(!web.isEmpty())throw new IOException("웹 배경 파일은 웹 배경 테마에만 넣을 수 있습니다");
    Files.write(new File(folder,"live-theme.json").toPath(),config.toString(2).getBytes(StandardCharsets.UTF_8));success=true;return folder;
   }
  }finally{staging.delete();if(!success&&folder!=null)deleteTree(folder);}
 }
 /** In-app save/share only carries JSON, a font and one video; web themes are edited and shared from the PC editor. */
 private static void webOnPc(JSONObject valid)throws IOException{if("web".equals(valid.optJSONObject("background").optString("type")))throw new IOException("웹 배경 테마는 PC 편집기에서 수정·공유해주세요");}
 /** Cleans up only a theme folder this import just created. */
 private static void deleteTree(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);file.delete();}
 static void checkFont(File file)throws IOException{
  try(DataInputStream in=new DataInputStream(new FileInputStream(file))){int magic=in.readInt();if(magic!=0x00010000&&magic!=0x4f54544f)throw new IOException("TTF 또는 OTF 글꼴 파일이 아닙니다");}
  try{Typeface.createFromFile(file);}catch(RuntimeException e){throw new IOException("글꼴을 읽을 수 없습니다",e);}
 }
 static void copy(InputStream in,OutputStream out,long limit)throws IOException{long size=0;byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){size+=n;if(size>limit)throw new IOException("파일 용량 한도를 초과했습니다");out.write(b,0,n);}}
 static File font(File folder){File f=new File(folder,"font.ttf");return f.isFile()?f:new File(folder,"font.otf");}
 static void checkVideo(File file)throws IOException{android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();try{reader.setDataSource(file.getAbsolutePath());String duration=reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION),width=reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),height=reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);if(duration==null||width==null||height==null||Long.parseLong(duration)<=0||Integer.parseInt(width)>4096||Integer.parseInt(height)>4096)throw new IOException("4096 이하 해상도의 재생 가능한 영상을 선택해주세요");}catch(RuntimeException e){throw new IOException("배경 영상을 읽을 수 없습니다",e);}finally{try{reader.release();}catch(IOException ignored){}}}
 static File save(Context context,JSONObject config,File font)throws Exception{return save(context,config,font,null);}
 static File save(Context context,JSONObject config,File font,File video)throws Exception{
  JSONObject valid=validate(config);webOnPc(valid);if("video".equals(valid.getJSONObject("background").getString("type"))&&(video==null||!video.isFile()))throw new IOException("배경 영상 파일이 없습니다");if("custom".equals(valid.getJSONObject("clock").getString("font"))&&(font==null||!font.isFile()))throw new IOException("사용자 글꼴을 먼저 가져와주세요");
  File library=new File(context.getFilesDir(),"creator-themes");if(!library.isDirectory()&&!library.mkdirs())throw new IOException("테마 저장 공간이 없습니다");File folder=new File(library,UUID.randomUUID().toString());if(!folder.mkdir())throw new IOException("테마 저장 실패");
  try{if(video!=null&&video.isFile()&&"video".equals(valid.getJSONObject("background").getString("type"))){if(video.length()>64L*1024*1024)throw new IOException("배경 영상은 64 MiB 이하로 선택해주세요");checkVideo(video);Files.copy(video.toPath(),new File(folder,"background.mp4").toPath());}if(font!=null&&font.isFile()){checkFont(font);Files.copy(font.toPath(),new File(folder,font.getName().endsWith("otf")?"font.otf":"font.ttf").toPath());}Files.write(new File(folder,"live-theme.json").toPath(),valid.toString(2).getBytes(StandardCharsets.UTF_8));return folder;}
  catch(Exception e){File[] files=folder.listFiles();if(files!=null)for(File f:files)f.delete();folder.delete();throw e;}
 }
 static void export(File destination,JSONObject config,File font)throws Exception{export(destination,config,font,null);}
 static void export(File destination,JSONObject config,File font,File video)throws Exception{
  JSONObject valid=validate(config);webOnPc(valid);if("video".equals(valid.getJSONObject("background").getString("type"))&&(video==null||!video.isFile()))throw new IOException("배경 영상 파일이 없습니다");if("custom".equals(valid.getJSONObject("clock").getString("font"))&&(font==null||!font.isFile()))throw new IOException("글꼴 파일이 없습니다");
  File temp=new File(destination.getParentFile(),destination.getName()+".tmp");
  try{try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(temp))){zip.putNextEntry(new ZipEntry("live-theme.json"));zip.write(valid.toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();if(video!=null&&video.isFile()&&"video".equals(valid.getJSONObject("background").getString("type"))){checkVideo(video);zip.putNextEntry(new ZipEntry("background.mp4"));try(InputStream in=new FileInputStream(video)){copy(in,zip,64L*1024*1024);}zip.closeEntry();}if(font!=null&&font.isFile()){checkFont(font);zip.putNextEntry(new ZipEntry(font.getName().endsWith("otf")?"font.otf":"font.ttf"));try(InputStream in=new FileInputStream(font)){copy(in,zip,4L*1024*1024);}zip.closeEntry();}}Files.move(temp.toPath(),destination.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}finally{temp.delete();}
 }
}
