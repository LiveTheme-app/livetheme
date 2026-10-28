package io.hmg.wallpaperapp;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/** Runs real Android JSON, Typeface and content-URI checks in the isolated fixture. */
public final class CreatorThemeChecks {
 private static int passed;
 private static void check(boolean value,String name){if(!value)throw new AssertionError(name);passed++;}
 interface Work{void run()throws Exception;}
 private static void rejects(Work work,String name)throws Exception{try{work.run();}catch(Exception expected){passed++;return;}throw new AssertionError(name);}
 private static void zip(File file,String[] names,byte[][] data)throws Exception{try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))){for(int i=0;i<names.length;i++){out.putNextEntry(new ZipEntry(names[i]));out.write(data[i]);out.closeEntry();}}}
 public static void run(Context c){try{
  passed=0;JSONObject defaults=CreatorTheme.defaults();check("digital".equals(defaults.getJSONObject("clock").getString("design")),"defaults");
  rejects(()->CreatorTheme.validate(new JSONObject("{\"format\":\"live-theme\",\"version\":99}")),"version");
  JSONObject invalid=new JSONObject(defaults.toString());invalid.getJSONObject("clock").put("color","red");rejects(()->CreatorTheme.validate(invalid),"color");
  invalid.getJSONObject("clock").put("color","#FFFFFF").put("design","script");rejects(()->CreatorTheme.validate(invalid),"unknown design");
  invalid.getJSONObject("clock").put("design","digital");invalid.getJSONObject("effect").put("amount",10000);rejects(()->CreatorTheme.validate(invalid),"resource bounds");
  JSONObject privateFields=new JSONObject(defaults.toString()).put("memo","SECRET").put("calendar","SECRET").put("script","SECRET");check(!CreatorTheme.validate(privateFields).toString().contains("SECRET"),"personal and code fields excluded");
  JSONObject layout=new JSONObject(defaults.toString());layout.getJSONObject("layouts").put("full/clock","0.2,0.3,1.2").put("split2/calendar","0.1,0.2,1.1,2.2").put("split3/music","0,1,1");check(CreatorTheme.validate(layout).getJSONObject("layouts").length()==3,"independent profiles");
  layout.getJSONObject("layouts").put("full/clock","NaN,0.2,1");rejects(()->CreatorTheme.validate(layout),"nonfinite layout");layout.getJSONObject("layouts").remove("full/clock");layout.getJSONObject("layouts").put("memo","SECRET");rejects(()->CreatorTheme.validate(layout),"layout whitelist");
  check("theme".equals(defaults.getJSONObject("widgetStyles").getString("clock")),"creator clock default");
  JSONObject styled=new JSONObject(defaults.toString());styled.getJSONObject("widgetStyles").put("music","minimal").put("memo","classic");check("classic".equals(CreatorTheme.validate(styled).getJSONObject("widgetStyles").getString("memo")),"per widget styles");
  JSONObject badStyle=new JSONObject(styled.toString());badStyle.getJSONObject("widgetStyles").put("music","javascript");rejects(()->CreatorTheme.validate(badStyle),"style whitelist");
  android.widget.TextView sample=new android.widget.TextView(c);sample.setPadding(12,13,14,15);for(String style:WidgetDesign.IDS){styled.getJSONObject("widgetStyles").put("battery",style);WidgetDesign.applySkin(sample,"battery",styled);check(sample.getBackground()==null,"all designs have no background");check(sample.getPaddingLeft()==12&&sample.getPaddingBottom()==15,"design preserves content padding");}
  File share=new File(c.getCacheDir(),"theme.zip");Uri uri=Uri.parse("content://io.hmg.engineprobe.theme/theme.zip");
  CreatorTheme.export(share,defaults,null);File imported=CreatorTheme.importUri(c,uri);check(imported!=null&&new File(imported,"live-theme.json").isFile(),"export import roundtrip");
  CreatorTheme.export(share,styled,null);File styledFolder=CreatorTheme.importUri(c,uri);JSONObject restored=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(styledFolder,"live-theme.json").toPath()),StandardCharsets.UTF_8));check("minimal".equals(restored.getJSONObject("widgetStyles").getString("music"))&&"classic".equals(restored.getJSONObject("widgetStyles").getString("memo")),"ZIP preserves independent designs");
  byte[] good=defaults.toString().getBytes(StandardCharsets.UTF_8);
  zip(share,new String[]{"live-theme.json","../escape"},new byte[][]{good,new byte[]{1}});rejects(()->CreatorTheme.importUri(c,uri),"zip traversal");
  zip(share,new String[]{"live-theme.json","effect.js"},new byte[][]{good,new byte[]{1}});rejects(()->CreatorTheme.importUri(c,uri),"executable rejected");
  zip(share,new String[]{"live-theme.json","font.ttf"},new byte[][]{good,new byte[]{1,2,3,4}});rejects(()->CreatorTheme.importUri(c,uri),"bad font");
  zip(share,new String[]{"live-theme.json"},new byte[][]{new byte[65537]});rejects(()->CreatorTheme.importUri(c,uri),"oversized manifest");
  JSONObject custom=new JSONObject(defaults.toString());custom.getJSONObject("clock").put("font","custom");zip(share,new String[]{"live-theme.json"},new byte[][]{custom.toString().getBytes(StandardCharsets.UTF_8)});rejects(()->CreatorTheme.importUri(c,uri),"missing font");
  File font=new File("/system/fonts/Roboto-Regular.ttf");check(font.isFile(),"system test font exists");CreatorTheme.export(share,custom,font);File fontTheme=CreatorTheme.importUri(c,uri);check(CreatorTheme.font(fontTheme).isFile(),"real ttf roundtrip");
  JSONObject movie=new JSONObject(custom.toString());movie.getJSONObject("background").put("type","video");
  zip(share,new String[]{"live-theme.json"},new byte[][]{movie.toString().getBytes(StandardCharsets.UTF_8)});rejects(()->CreatorTheme.importUri(c,uri),"missing theme assets");
  JSONObject videoOnly=new JSONObject(defaults.toString());videoOnly.getJSONObject("background").put("type","video");
  zip(share,new String[]{"live-theme.json","background.mp4"},new byte[][]{videoOnly.toString().getBytes(StandardCharsets.UTF_8),new byte[]{1,2,3,4}});rejects(()->CreatorTheme.importUri(c,uri),"invalid video");
  File video=new File(c.getCacheDir(),"creator-video.mp4");check(video.isFile(),"test video exists");CreatorTheme.export(share,movie,font,video);File complete=CreatorTheme.importUri(c,uri);check(CreatorTheme.font(complete).isFile()&&new File(complete,"background.mp4").isFile(),"video font widgets package roundtrip");
  File saved=CreatorTheme.save(c,movie,font,video);check(new File(saved,"background.mp4").length()==video.length(),"library saves video");
  CreatorTheme.export(share,defaults,null);byte[] before=java.nio.file.Files.readAllBytes(share.toPath());rejects(()->CreatorTheme.export(share,invalid,null),"bad export");check(java.util.Arrays.equals(before,java.nio.file.Files.readAllBytes(share.toPath())),"failed export preserves file");
  // Data links: same catalogue and formula as creator-kit/editor/links-checks.cjs.
  JSONObject linked=CreatorTheme.validate(new JSONObject("{\"format\":\"live-theme\",\"version\":1,\"links\":{\"glow\":{\"source\":\"battery.level\",\"strength\":80,\"color\":\"#112233\"},\"tint\":{\"source\":\"ambient.color\"},\"motion\":{\"source\":\"music.playing\",\"strength\":0},\"dim\":{\"source\":\"drive.paused\",\"strength\":100}}}"));
  JSONObject links=linked.getJSONObject("links");check(links.getJSONObject("tint").getDouble("strength")==50&&"#BFE8D2".equals(links.getJSONObject("tint").getString("color"))&&!links.getJSONObject("motion").has("color"),"link defaults");
  for(String bad:new String[]{"{\"glow\":{\"source\":\"music.color\"}}","{\"sparkle\":{\"source\":\"battery.level\"}}","{\"dim\":{\"source\":\"day.night\",\"strength\":101}}","{\"glow\":{\"source\":\"battery.level\",\"color\":\"red\"}}"})rejects(()->CreatorTheme.validate(new JSONObject("{\"format\":\"live-theme\",\"version\":1,\"links\":"+bad+"}")),"bad link "+bad);
  check(CreatorTheme.validate(new JSONObject("{\"format\":\"live-theme\",\"version\":1}")).getJSONObject("links").length()==0,"old themes have no links");
  ThemeLinks values=new ThemeLinks();ThemeLinks.Overlay none=values.compute(links);check(none.glow==0&&none.tint==0&&none.speed==1,"unknown car values keep links off");
  values.level=50;values.ambientColor=0xffff0000;values.playing=true;values.paused=false;ThemeLinks.Overlay o=values.compute(links);
  check(Math.abs(o.glow-.8f*.55f*.5f)<1e-6&&o.tintColor==0xffff0000&&Math.abs(o.tint-.5f*.35f)<1e-6&&o.speed==1&&o.dim==0,"link formula matches editor");values.paused=true;check(Math.abs(values.compute(links).dim-.5f)<1e-6,"driving dims");
  check(ThemeLinks.uses(links,"battery.")&&ThemeLinks.uses(links,"ambient.")&&!ThemeLinks.uses(links,"day."),"link sources detected");
  // Web themes: declared requests from a fixed list, and a web/ folder with safe names and allowed file types only.
  String web="{\"format\":\"live-theme\",\"version\":1,\"name\":\"웹\",\"background\":{\"type\":\"web\",\"requests\":[\"ambient.color\"]}}";byte[] page="<!doctype html><p>hi".getBytes(StandardCharsets.UTF_8);
  check(CreatorTheme.validate(new JSONObject(web)).getJSONObject("background").getJSONArray("requests").length()==1,"web requests kept");
  for(String bad:new String[]{"[\"seat.heat\"]","[\"ambient.color\",\"ambient.color\"]"})rejects(()->CreatorTheme.validate(new JSONObject(web.replace("[\"ambient.color\"]",bad))),"bad request "+bad);
  check(!CreatorTheme.validate(new JSONObject("{\"format\":\"live-theme\",\"version\":1,\"background\":{\"type\":\"builtin\",\"requests\":[\"ambient.color\"]}}")).getJSONObject("background").has("requests"),"requests only on web themes");
  check(CreatorTheme.webEntry("web/index.html")&&CreatorTheme.webEntry("web/img/a-1.png")&&!CreatorTheme.webEntry("web/../x.js")&&!CreatorTheme.webEntry("web/a.exe")&&!CreatorTheme.webEntry("web/noext")&&!CreatorTheme.webEntry("/web/a.js")&&!CreatorTheme.webEntry("web\\a.js"),"web entry names");
  zip(share,new String[]{"live-theme.json","web/index.html","web/js/app.js"},new byte[][]{web.getBytes(StandardCharsets.UTF_8),page,"livetheme.on('data',()=>{})".getBytes(StandardCharsets.UTF_8)});File webImported=CreatorTheme.importUri(c,uri);
  check(webImported!=null&&new File(webImported,"web/index.html").isFile()&&new File(webImported,"web/js/app.js").isFile(),"web theme imported");
  zip(share,new String[]{"live-theme.json","web/index.html","web/../escape.js"},new byte[][]{web.getBytes(StandardCharsets.UTF_8),page,page});rejects(()->CreatorTheme.importUri(c,uri),"path escape rejected");
  zip(share,new String[]{"live-theme.json","web/index.html","web/run.exe"},new byte[][]{web.getBytes(StandardCharsets.UTF_8),page,page});rejects(()->CreatorTheme.importUri(c,uri),"file type rejected");
  zip(share,new String[]{"live-theme.json","web/app.js"},new byte[][]{web.getBytes(StandardCharsets.UTF_8),page});rejects(()->CreatorTheme.importUri(c,uri),"index required");
  zip(share,new String[]{"live-theme.json","web/index.html"},new byte[][]{"{\"format\":\"live-theme\",\"version\":1,\"background\":{\"type\":\"builtin\"}}".getBytes(StandardCharsets.UTF_8),page});rejects(()->CreatorTheme.importUri(c,uri),"web files only in web themes");
  rejects(()->CreatorTheme.export(share,CreatorTheme.validate(new JSONObject(web)),null),"web themes are shared from the PC editor");
  Log.i("CreatorChecks","PASS "+passed);
 }catch(Throwable error){Log.e("CreatorChecks","FAIL",error);throw new IllegalStateException(error);}}
}
