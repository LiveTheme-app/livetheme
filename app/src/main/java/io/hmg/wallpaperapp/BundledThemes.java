package io.hmg.wallpaperapp;
import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Themes shipped in the APK (contest build only: src/release/assets/bundled-themes). They replace the Workshop presets there and install once into the normal theme library. Debug has none, so nothing changes. */
final class BundledThemes {
 private static final String DIR="bundled-themes";
 static String[] names(Context c){try{String[] n=c.getAssets().list(DIR);if(n==null)return new String[0];Arrays.sort(n);return n;}catch(IOException e){return new String[0];}}
 static boolean present(Context c){return names(c).length>0;}
 /** Installed bundled themes in asset order; installs any that are missing (worker thread: unzips). */
 static List<InstalledThemes.Entry> list(Context c){
  SharedPreferences prefs=c.getSharedPreferences("bundled_themes",Context.MODE_PRIVATE);List<InstalledThemes.Entry> out=new ArrayList<>();
  for(String name:names(c))try{
   String id=prefs.getString(name,"");File folder=id.isEmpty()?null:new File(c.getFilesDir(),"creator-themes/"+id);
   if(folder==null||!new File(folder,"live-theme.json").isFile()){try(InputStream in=c.getAssets().open(DIR+"/"+name)){folder=CreatorTheme.importStream(c,in);}if(folder==null)continue;prefs.edit().putString(name,folder.getName()).apply();}
   JSONObject theme=CreatorTheme.validate(new JSONObject(new String(Files.readAllBytes(new File(folder,"live-theme.json").toPath()),StandardCharsets.UTF_8)));
   prefs.edit().putString("name:"+theme.optString("name"),folder.getName()).apply();out.add(new InstalledThemes.Entry(theme,folder));
  }catch(Exception e){android.util.Log.w("LiveBundled",name+": "+e);}
  return out;
 }
 /** Very first launch of the contest build: start on the first bundled theme with every default widget off (the theme draws its own clock, music and battery). Runs before ThemeStudio reads its draft. */
 static void firstRun(Context c){
  SharedPreferences store=c.getSharedPreferences("theme_creator",Context.MODE_PRIVATE);if(store.contains("draft")||!present(c))return;
  List<InstalledThemes.Entry> all=list(c);if(all.isEmpty())return;InstalledThemes.Entry first=all.get(0);
  store.edit().putString("draft",first.theme.toString()).putString("font","").putString("video",new File(first.folder,"web/index.html").getAbsolutePath()).apply();
  // Widgets are already off, so skip the "hide the same widgets?" question for this theme (key = MainActivity.themeSignature()).
  c.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).edit().putString("themeHidesAsked",first.theme.optString("name")+"/"+first.theme.optString("author")+"/web").apply();
 }
 /** Card thumbnail for a bundled web theme: its own wide background, scaled down. null for anything else (debug has no bundled themes). */
 static android.graphics.Bitmap preview(Context c,JSONObject theme){
  if(!"web".equals(theme.optJSONObject("background").optString("type")))return null;
  String id=c.getSharedPreferences("bundled_themes",Context.MODE_PRIVATE).getString("name:"+theme.optString("name"),"");if(id.isEmpty())return null;
  File[] wide=new File(c.getFilesDir(),"creator-themes/"+id+"/web").listFiles((dir,n)->n.endsWith("wide.webp"));if(wide==null||wide.length==0)return null;// wide.webp, lake-wide.webp
  android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inSampleSize=4;
  return android.graphics.BitmapFactory.decodeFile(wide[0].getAbsolutePath(),o);
 }
}
