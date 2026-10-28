package io.hmg.wallpaperapp;

import android.graphics.Color;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;

/** No-code data links: fixed visual targets driven by read-only car/app values. Catalogue and formula mirror the PC editor (editor.js / vehicle-preview.js). */
final class ThemeLinks {
 private static final Map<String,String[]> SOURCES=new LinkedHashMap<>();
 private static final Set<String> COLORED=new HashSet<>(Arrays.asList("glow","tint"));
 static{
  SOURCES.put("glow",new String[]{"battery.level","ambient.brightness","music.playing","battery.charging"});
  SOURCES.put("tint",new String[]{"ambient.color","music.color","battery.charging"});
  SOURCES.put("motion",new String[]{"music.playing","battery.charging"});
  SOURCES.put("dim",new String[]{"day.night","drive.paused"});
 }
 /** Latest values; null means the car or app has not provided one, and links using it stay off. */
 Integer level,ambientColor,albumColor;Boolean charging,playing;Double ambientBrightness;boolean night,paused;

 static JSONObject validate(JSONObject raw)throws Exception{
  JSONObject out=new JSONObject();if(raw==null)return out;Iterator<String> targets=raw.keys();
  while(targets.hasNext()){
   String target=targets.next();String[] allowed=SOURCES.get(target);JSONObject link=raw.optJSONObject(target);
   if(allowed==null||link==null)throw new IOException("지원하지 않는 데이터 연결입니다");
   String source=link.optString("source");if(!Arrays.asList(allowed).contains(source))throw new IOException("연결할 수 없는 데이터입니다: "+target);
   double strength=link.has("strength")?link.getDouble("strength"):50;if(!Double.isFinite(strength)||strength<0||strength>100)throw new IOException("연결 세기는 0~100이어야 합니다");
   JSONObject entry=new JSONObject().put("source",source).put("strength",strength);
   if(COLORED.contains(target)){String color=link.optString("color","#BFE8D2");if(!color.matches("#[0-9a-fA-F]{6}"))throw new IOException("색상은 #RRGGBB 형식으로 입력해주세요");entry.put("color",color);}
   out.put(target,entry);
  }
  return out;
 }
 static boolean uses(JSONObject links,String prefix){if(links==null)return false;Iterator<String> keys=links.keys();while(keys.hasNext()){JSONObject link=links.optJSONObject(keys.next());if(link!=null&&link.optString("source").startsWith(prefix))return true;}return false;}

 /** 0..1, or null when unknown. */
 Double value(String source){
  switch(source){
   case "battery.level":return level==null?null:level/100.0;
   case "battery.charging":return charging==null?null:charging?1.0:0.0;
   case "ambient.brightness":return ambientBrightness==null?null:Math.max(0,Math.min(1,ambientBrightness/100));
   case "music.playing":return playing==null?null:playing?1.0:0.0;
   case "day.night":return night?1.0:0.0;
   case "drive.paused":return paused?1.0:0.0;
   default:return null;
  }
 }
 private Integer colorOf(JSONObject link){String source=link.optString("source");return "ambient.color".equals(source)?ambientColor:"music.color".equals(source)?albumColor:Integer.valueOf(Color.parseColor(link.optString("color","#BFE8D2")));}

 static final class Overlay{float glow,tint,dim,speed=1;int glowColor=0xffbfe8d2,tintColor=0xff000000;}
 Overlay compute(JSONObject links){
  Overlay out=new Overlay();if(links==null)return out;
  JSONObject glow=links.optJSONObject("glow"),tint=links.optJSONObject("tint"),motion=links.optJSONObject("motion"),dim=links.optJSONObject("dim");
  if(glow!=null){Double v=value(glow.optString("source"));if(v!=null){out.glow=(float)(strength(glow)*.55*v);out.glowColor=Color.parseColor(glow.optString("color","#BFE8D2"));}}
  if(tint!=null){Integer color=colorOf(tint);Double v="battery.charging".equals(tint.optString("source"))?value("battery.charging"):Double.valueOf(1);if(color!=null&&v!=null){out.tint=(float)(strength(tint)*.35*v);out.tintColor=color;}}
  if(motion!=null){Double v=value(motion.optString("source"));if(v!=null)out.speed=(float)(1+strength(motion)*1.5*v);}
  if(dim!=null){Double v=value(dim.optString("source"));if(v!=null)out.dim=(float)(strength(dim)*.5*v);}
  return out;
 }
 private static double strength(JSONObject link){return link.optDouble("strength",50)/100;}
 /** Album colour for "music.color": the cover averaged to one pixel. */
 static Integer averageColor(android.graphics.Bitmap art){return art==null?null:android.graphics.Bitmap.createScaledBitmap(art,1,1,true).getPixel(0,0)|0xff000000;}
}
