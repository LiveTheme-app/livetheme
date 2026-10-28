package io.hmg.wallpaperapp;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Installed packages and available built-ins, excluding old automatic snapshots. */
final class InstalledThemes {
 static final class Entry {
  final JSONObject theme;final File folder;
  Entry(JSONObject theme,File folder){this.theme=theme;this.folder=folder;}
 }
 static boolean legacyBackup(JSONObject theme){return theme.optString("name").startsWith("이전 구성 · ");}
 static String backgroundKey(JSONObject theme,File folder)throws Exception{
  JSONObject bg=theme.getJSONObject("background");String type=bg.getString("type");
  if("workshop".equals(type))return "workshop:"+bg.getString("id");
  if("builtin".equals(type))return "builtin:"+bg.getInt("index");
  if(!"video".equals(type)||folder==null)return null;
  File video=new File(folder,"background.mp4");if(!video.isFile())return null;java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
  try(InputStream stream=new FileInputStream(video)){byte[] data=new byte[65536];int count;while((count=stream.read(data))!=-1)digest.update(data,0,count);}return "video:"+android.util.Base64.encodeToString(digest.digest(),android.util.Base64.NO_WRAP);
 }
 static List<Entry> list(Context context){
  LinkedHashMap<String,Entry> result=new LinkedHashMap<>();HashSet<String> seen=new HashSet<>();
  for(int i=0;i<ThemePresets.NAMES.length;i++)try{JSONObject theme=ThemePresets.create(i);String id=theme.getJSONObject("background").getString("id");if(WorkshopLibrary.scene(context,id)==null&&WorkshopLibrary.video(context,id)==null)continue;result.put(backgroundKey(theme,null),new Entry(theme,null));}catch(Exception ignored){}
  File[] folders=new File(context.getFilesDir(),"creator-themes").listFiles(File::isDirectory);if(folders==null)return new ArrayList<>(result.values());Arrays.sort(folders,Comparator.comparingLong(File::lastModified).reversed());
  for(File folder:folders){if(!folder.getName().matches("[a-f0-9-]{36}"))continue;try{
   JSONObject theme=CreatorTheme.validate(new JSONObject(new String(Files.readAllBytes(new File(folder,"live-theme.json").toPath()),StandardCharsets.UTF_8)));if(legacyBackup(theme))continue;
   String key=backgroundKey(theme,folder);if(key==null||!seen.add(key))continue;
   result.put(key,new Entry(theme,folder));
  }catch(Exception ignored){}}
  return new ArrayList<>(result.values());
 }
}
