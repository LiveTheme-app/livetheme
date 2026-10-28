package io.hmg.wallpaperapp;
import android.content.Context;
import android.car.hardware.property.EvChargeState;
import android.net.Uri;
import android.util.Log;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipFile;

public final class ThemeHubChecks {
 private static int passed;
 private static void check(boolean b,String label){if(!b)throw new AssertionError(label);passed++;}
 interface Work{void run()throws Exception;}
 private static void rejects(Work work)throws Exception{try{work.run();}catch(Exception expected){passed++;return;}throw new AssertionError("invalid theme accepted");}
 public static void run(Context c){try{
  passed=0;JSONArray presets=new JSONArray();
  check(ThemePresets.NAMES.length==WorkshopLibrary.IDS.length&&ThemePresets.NOTES.length==WorkshopLibrary.IDS.length,"preset metadata counts agree");
  for(int i=0;i<ThemePresets.NAMES.length;i++){
   JSONObject theme=ThemePresets.create(i);presets.put(new JSONObject(theme.toString()));
   check(theme.getJSONObject("layouts").length()==21,"three complete window profiles");
   check(theme.getJSONObject("widgetStyles").length()==WidgetShelf.KEYS.length,"all catalog widget styles");for(String key:WidgetShelf.KEYS)check(theme.getJSONObject("widgetStyles").has(key),"style for "+key);
   check(WorkshopLibrary.IDS[i].equals(theme.getJSONObject("background").getString("id")),"workshop reference");
   check(theme.getJSONObject("panel").getBoolean("battery"),"battery enabled");
   check(!theme.getJSONObject("panel").getString("trim").equals("none"),"theme ornament configured");
   CalendarWidget calendar=new CalendarWidget(c,()->{});calendar.prepareSize(480,480);WidgetDesign.applySkin(calendar,"calendar",theme);calendar.measure(android.view.View.MeasureSpec.makeMeasureSpec(480,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(480,1073741824));calendar.layout(0,0,480,480);check(calendar.isMonthView(),"themed calendar month view");
   android.widget.TextView day=calendar.findViewWithTag("calendar-day");check(day!=null&&day.getCurrentTextColor()==android.graphics.Color.parseColor(WidgetDesign.panelFor(theme,"calendar").getString("textColor")),"calendar day color survives palette refresh");
   android.graphics.Bitmap calendarImage=android.graphics.Bitmap.createBitmap(480,480,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas calendarCanvas=new android.graphics.Canvas(calendarImage);calendarCanvas.drawColor(android.graphics.Color.luminance(android.graphics.Color.parseColor(theme.getJSONObject("panel").getString("textColor")))>.5?0xff214239:0xffE6E7E5);calendar.draw(calendarCanvas);try(FileOutputStream image=new FileOutputStream(new File(c.getCacheDir(),"calendar-style-"+i+".png"))){calendarImage.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image);}calendarImage.recycle();

   theme.getJSONObject("widgetSkins").put("battery",new JSONObject().put("font","mono").put("textColor","#ABCDEF").put("opacity",35));
   theme.put("memo","PRIVATE").put("vehicle","PRIVATE");
   File zip=new File(c.getCacheDir(),"theme.zip");CreatorTheme.export(zip,theme,null);
   try(ZipFile archive=new ZipFile(zip)){check(archive.size()==1,"workshop artwork excluded");}
   File folder=CreatorTheme.importUri(c,Uri.parse("content://io.hmg.engineprobe.theme/theme.zip"));
   JSONObject restored=new JSONObject(new String(Files.readAllBytes(new File(folder,"live-theme.json").toPath()),StandardCharsets.UTF_8));
   check(restored.getJSONObject("widgetSkins").getJSONObject("battery").getString("textColor").equals("#ABCDEF"),"skin ZIP roundtrip");
   check(restored.getJSONObject("panel").getString("trim").equals(theme.getJSONObject("panel").getString("trim")),"theme ornament ZIP roundtrip");
   check(!restored.toString().contains("PRIVATE"),"private data excluded");
  }
  Files.write(new File(c.getCacheDir(),"presets.json").toPath(),presets.toString(2).getBytes(StandardCharsets.UTF_8));
  Files.write(new File(c.getCacheDir(),"coast-widget-look.json").toPath(),CreatorTheme.validate(ThemePresets.widgetLook(CreatorTheme.defaults(),5)).toString(2).getBytes(StandardCharsets.UTF_8));
  JSONObject coast=CreatorTheme.validate(ThemePresets.widgetLook(CreatorTheme.defaults(),5));check(coast.getJSONObject("panel").getString("trim").equals("paper"),"watercolor paper theme");
  for(String trim:new String[]{"none","leaf","frost","ripple","neon","line","paper"}){
   JSONObject theme=ThemePresets.create(0);theme.getJSONObject("panel").put("trim",trim);theme.getJSONObject("widgetSkins").put("music",new JSONObject().put("trim",trim));JSONObject valid=CreatorTheme.validate(theme);check(valid.getJSONObject("widgetSkins").getJSONObject("music").getString("trim").equals(trim),"per-widget trim preserved");
   android.widget.TextView widget=new android.widget.TextView(c);widget.setText("72%");WidgetDesign.applySkin(widget,"music",valid);check(widget.getBackground()==null,"widget has no backplate "+trim);
   check(widget.getForeground()==null,"no corner ornament even for older themes "+trim);

  }
  JSONObject authored=ThemePresets.create(0);authored.getJSONObject("widgetSkins").put("music",new JSONObject().put("font","serif").put("trim","ripple"));String custom=authored.getJSONObject("widgetSkins").toString();
  for(String design:WidgetDesign.IDS){
   JSONObject selected=new JSONObject(authored.toString());selected.getJSONObject("widgetStyles").put("music",design);selected=CreatorTheme.validate(selected);check(selected.getJSONObject("widgetSkins").toString().equals(custom),"custom skin retained for "+design);check(selected.getJSONObject("widgetStyles").getString("battery").equals("theme"),"other widget design untouched");
   android.widget.TextView sample=new android.widget.TextView(c);sample.setTextSize(36);sample.setText("72%");WidgetDesign.applySkin(sample,"music",selected);check(sample.getBackground()==null,"basic designs have no plate");check(sample.getTypeface().isBold()==design.equals("focus"),"focus weight only");
   File designZip=new File(c.getCacheDir(),"theme.zip");CreatorTheme.export(designZip,selected,null);File designFolder=CreatorTheme.importUri(c,Uri.parse("content://io.hmg.engineprobe.theme/theme.zip"));JSONObject imported=new JSONObject(new String(Files.readAllBytes(new File(designFolder,"live-theme.json").toPath()),StandardCharsets.UTF_8));check(imported.getJSONObject("widgetStyles").getString("music").equals(design),"design choice ZIP roundtrip");
  }
  JSONObject back=new JSONObject(authored.toString());back.getJSONObject("widgetStyles").put("music","focus");back.getJSONObject("widgetStyles").put("music","theme");check(WidgetDesign.panelFor(back,"music").getString("trim").equals("ripple"),"custom design restored");
  for(String legacy:new String[]{"glass","clear","solid"}){JSONObject old=new JSONObject(authored.toString());old.getJSONObject("widgetStyles").put("music",legacy);check(CreatorTheme.validate(old).getJSONObject("widgetStyles").getString("music").equals("theme"),"old card choice migrates to borderless custom");}
  final JSONObject badDesign=new JSONObject(authored.toString());badDesign.getJSONObject("widgetStyles").put("music","script");rejects(()->CreatorTheme.validate(badDesign));
  final JSONObject badTrim=ThemePresets.create(0);badTrim.getJSONObject("panel").put("trim","<script>");rejects(()->CreatorTheme.validate(badTrim));final JSONObject badSkinTrim=ThemePresets.create(0);badSkinTrim.getJSONObject("widgetSkins").put("music",new JSONObject().put("trim","unknown"));rejects(()->CreatorTheme.validate(badSkinTrim));
  JSONObject invalid=ThemePresets.create(0);invalid.getJSONObject("background").put("id","../escape");rejects(()->CreatorTheme.validate(invalid));
  JSONObject bad=ThemePresets.create(0);bad.getJSONObject("widgetSkins").put("battery",new JSONObject().put("color","red"));rejects(()->CreatorTheme.validate(bad));
  bad.getJSONObject("widgetSkins").remove("battery");bad.getJSONObject("widgetSkins").put("script",new JSONObject());rejects(()->CreatorTheme.validate(bad));
  check(BatteryWidget.validPercent(null)==null,"unknown stays unknown");
  check(BatteryWidget.validPercent(-1)==null&&BatteryWidget.validPercent(101)==null,"invalid SOC");
  check(BatteryWidget.validPercent(0)==0&&BatteryWidget.validPercent(100)==100,"SOC boundaries");
  check(BatteryWidget.chargeLabel(null).contains("확인 불가"),"unknown charge");
  check(BatteryWidget.chargeLabel(EvChargeState.STATE_CHARGING).equals("충전 중"),"actual charging");
  check(BatteryWidget.chargeLabel(EvChargeState.STATE_NOT_CHARGING).equals("충전 중 아님"),"not charging");
  check(BatteryWidget.chargeLabel(EvChargeState.STATE_FULLY_CHARGED).equals("목표 충전량 도달"),"target reached");
  java.io.File libraryTest=new java.io.File(c.getCacheDir(),"installed-test-"+java.util.UUID.randomUUID());check(libraryTest.mkdir(),"isolated installed library");
  android.content.Context isolated=new android.content.ContextWrapper(c){public java.io.File getFilesDir(){return libraryTest;}};
  JSONObject original=ThemePresets.create(0);CreatorTheme.save(isolated,original,null);CreatorTheme.save(isolated,original,null);
  JSONObject backup=new JSONObject(original.toString()).put("name","이전 구성 · Old");CreatorTheme.save(isolated,backup,null);
  JSONObject variant=new JSONObject(original.toString());variant.getJSONObject("widgetStyles").put("music","clear");CreatorTheme.save(isolated,variant,null);
  java.util.List<InstalledThemes.Entry> visible=InstalledThemes.list(isolated);check(visible.size()==1,"one background regardless of widget style");
  check(visible.stream().noneMatch(e->InstalledThemes.legacyBackup(e.theme)),"no backup in installed list");
  check(new java.io.File(libraryTest,"creator-themes").listFiles().length==4,"hidden packages preserved");
  JSONObject changedName=new JSONObject(original.toString()).put("name","Another name").put("author","Another author");changedName.getJSONObject("layouts").put("full/clock","1,1,1,1");
  check(InstalledThemes.backgroundKey(original,null).equals(InstalledThemes.backgroundKey(changedName,null)),"name author and layout do not create another theme");
  check(!InstalledThemes.backgroundKey(original,null).equals(InstalledThemes.backgroundKey(ThemePresets.create(1),null)),"different backgrounds remain separate");
  check(InstalledThemes.backgroundKey(CreatorTheme.defaults(),null)==null,"layout-only snapshots are not themes");
  JSONObject current=ThemePresets.create(2);current.getJSONObject("panel").put("calendar",true).put("music",false);JSONObject applied=CreatorTheme.forApplication(original,current);
  check(applied.getJSONObject("layouts").length()==0,"old package layouts ignored on application");
  check(applied.getJSONObject("panel").getBoolean("calendar")&&!applied.getJSONObject("panel").getBoolean("music"),"enabled widgets retained");
  check(applied.getJSONObject("background").getString("id").equals(original.getJSONObject("background").getString("id")),"selected theme background applied");
  check(CreatorTheme.withoutLayouts(original).getJSONObject("layouts").length()==0&&original.getJSONObject("layouts").length()==21,"export excludes layouts without changing package");
  java.io.File mediaA=new java.io.File(libraryTest,"mediaA"),mediaB=new java.io.File(libraryTest,"mediaB");mediaA.mkdir();mediaB.mkdir();Files.write(new File(mediaA,"background.mp4").toPath(),new byte[]{1,2,3});Files.write(new File(mediaB,"background.mp4").toPath(),new byte[]{1,2,3});JSONObject movie=CreatorTheme.defaults();movie.getJSONObject("background").put("type","video");
  check(InstalledThemes.backgroundKey(movie,mediaA).equals(InstalledThemes.backgroundKey(movie,mediaB)),"identical videos grouped across folders");
  Files.write(new File(mediaB,"background.mp4").toPath(),new byte[]{1,2,4});check(!InstalledThemes.backgroundKey(movie,mediaA).equals(InstalledThemes.backgroundKey(movie,mediaB)),"different videos not merged");
  JSONObject styledClock=ThemePresets.create(0);styledClock.getJSONObject("widgetSkins").put("clock",new JSONObject().put("textColor","#ABCDEF").put("font","mono"));styledClock.getJSONObject("clock").put("plate",60);String rawClock=styledClock.toString();JSONObject drawing=WidgetDesign.clockTheme(styledClock);
  check(drawing.getJSONObject("clock").getString("color").equals("#ABCDEF"),"preview uses actual clock skin color");check(drawing.getJSONObject("clock").getString("font").equals("mono"),"preview uses actual clock skin font");check(drawing.getJSONObject("clock").getInt("plate")==0,"old clock plate not rendered");check(styledClock.toString().equals(rawClock),"drawing never mutates saved custom design");
  styledClock.getJSONObject("widgetStyles").put("clock","focus");drawing=WidgetDesign.clockTheme(styledClock);check(drawing.getJSONObject("clock").getString("font").equals("bold"),"basic clock ignores custom font");check(drawing.getJSONObject("clock").getString("color").equals("#ABCDEF"),"basic clock keeps theme ink");
  final int[] changes={0};ThemeStudio studio=new ThemeStudio((android.app.Activity)c,()->changes[0]++,()->{});studio.config=CreatorTheme.defaults();studio.setPanels(true,true,false,true,false,false,false,true);check(studio.config.getJSONObject("panel").getBoolean("battery"),"settings enables battery");studio.setPanels(true,true,false,true,false,false,false,false);check(!studio.config.getJSONObject("panel").getBoolean("battery"),"settings disables battery");check(!new ThemeStudio((android.app.Activity)c,()->{},()->{}).config.getJSONObject("panel").getBoolean("battery"),"battery toggle survives recreation");check(changes[0]==2,"one update per settings change");
  // Mood light from the background (AmbientMatch): vibrant hue wins, dark scenes are lifted, grey/dark fall back to soft colours.
  java.util.function.Function<int[],float[]> hsv=px->{float[] o=new float[3];android.graphics.Color.colorToHSV(AmbientMatch.pick(px),o);return o;};
  int[] red=new int[64*36];java.util.Arrays.fill(red,0xffd02020);float[] h=hsv.apply(red);check(h[0]<8||h[0]>352,"red scene gives red light");check(h[2]>=.74f,"light is bright");
  int[] navy=new int[64*36];java.util.Arrays.fill(navy,0xff101a44);h=hsv.apply(navy);check(h[0]>220&&h[0]<240,"dark navy keeps its hue");check(h[2]>=.74f,"dark scene lifted to a visible light");check(h[1]>=.44f,"coloured pick keeps saturation");
  int[] mostlyGrey=new int[64*36];java.util.Arrays.fill(mostlyGrey,0xff808080);for(int i=0;i<mostlyGrey.length*3/10;i++)mostlyGrey[i]=0xff2060e0;h=hsv.apply(mostlyGrey);check(h[0]>210&&h[0]<230,"30% blue beats 70% grey");
  int[] grey=new int[64*36];java.util.Arrays.fill(grey,0xff9a9a9a);h=hsv.apply(grey);check(h[1]<.3f&&h[2]>=.74f,"grey scene gives a soft light");
  int[] black=new int[64*36];check(AmbientMatch.pick(black)==0xffffc58a,"all-dark scene gives the warm default");
  int[] wrap=new int[64*36];for(int i=0;i<wrap.length;i++)wrap[i]=i%2==0?0xffff1030:0xffff3010;h=hsv.apply(wrap);check(h[0]<15||h[0]>345,"hue average wraps around red");
  check(AmbientMatch.MIN_INTERVAL_MS>=10_000,"mood light changes at most every 10 s or slower");
  // 1.32 분위기 migration: untouched 1.31 settings become 기본 with identical values; a moved dim slider becomes 사용자 지정 and keeps its value; nothing else is written.
  android.content.SharedPreferences mp=c.getSharedPreferences("mood-check",0);mp.edit().clear().putInt("dim",15).putBoolean("playing",true).putString("widget_layouts_marker","keep").commit();
  check(Mood.DEFAULT.equals(Mood.migrate(mp)),"untouched 1.31 settings migrate to default");android.content.SharedPreferences.Editor me=mp.edit();Mood.apply(me,Mood.migrate(mp));me.commit();
  check(mp.getInt("dim",-1)==15&&mp.getBoolean("nightDim",false)&&!mp.getBoolean("autoColor",true)&&!mp.getBoolean("strongText",true)&&mp.getFloat("effectAmount",0)==1f&&mp.getFloat("effectSpeed",0)==1f&&mp.getFloat("glass",0)==1f,"default preset equals the 1.31 look");
  check("keep".equals(mp.getString("widget_layouts_marker",null))&&mp.getBoolean("playing",false),"migration leaves unrelated settings alone");
  mp.edit().clear().putInt("dim",42).commit();check(Mood.CUSTOM.equals(Mood.migrate(mp)),"moved dim slider migrates to custom");me=mp.edit();Mood.apply(me,Mood.migrate(mp));me.commit();check(mp.getInt("dim",-1)==42&&Mood.CUSTOM.equals(mp.getString("mood",null)),"custom keeps the user's dim value");
  me=mp.edit();Mood.apply(me,Mood.CALM);me.commit();check(Mood.migrate(mp).equals(Mood.CALM),"a saved mood is never migrated again");
  check(mp.getInt("dim",-1)==30&&mp.getFloat("effectAmount",9)==0f&&mp.getFloat("glass",0)>1f,"차분하게 sets dim, effect and glass together");
  me=mp.edit();Mood.apply(me,Mood.VIVID);me.commit();check(mp.getBoolean("autoColor",false)&&mp.getBoolean("strongText",false)&&mp.getFloat("effectAmount",0)>1f,"화려하게 sets auto colour, bold values and more effect");
  check(Mood.IDS.length==3&&Mood.index(Mood.CUSTOM)==-1,"three presets plus custom");mp.edit().clear().commit();
  // 1.34 driving view: only glanceable widgets stay; everything else (and every dialog) is locked.
  // 1.35: every widget stays visible while driving (the clock/music/battery-only rule was dropped at the owner's request); the seat widget is gone.
  check(WidgetShelf.KEYS.length==10&&!java.util.Arrays.asList(WidgetShelf.KEYS).contains("seat")&&!java.util.Arrays.asList(VehicleWidgets.KEYS).contains("seat"),"seat widget removed");
  check(!java.util.Arrays.asList(WidgetShelf.KEYS).contains("climate")&&!java.util.Arrays.asList(VehicleWidgets.KEYS).contains("climate")&&WidgetShelf.RETIRED.contains("climate"),"climate widget removed: 10 widgets");
  try{org.json.JSONObject old=CreatorTheme.defaults();old.getJSONObject("panel").put("climate",true).put("seat",true);old.put("widgetSkins",new org.json.JSONObject().put("climate",new org.json.JSONObject().put("color","#112233")).put("clock",new org.json.JSONObject().put("color","#445566")));old.put("layouts",new org.json.JSONObject().put("full/climate","0.1,0.1,1").put("split2/seat","0.1,0.1,1").put("full/clock","0.2,0.2,1"));org.json.JSONObject ok=CreatorTheme.validate(old);check(!ok.getJSONObject("widgetSkins").has("climate")&&ok.getJSONObject("widgetSkins").has("clock")&&!ok.getJSONObject("layouts").has("full/climate")&&!ok.getJSONObject("layouts").has("split2/seat")&&ok.getJSONObject("layouts").has("full/clock")&&!ok.getJSONObject("panel").has("climate"),"old theme naming climate/seat loads, retired parts dropped");}catch(Exception e){check(false,"old theme naming climate/seat loads: "+e);}
  {android.content.SharedPreferences lp=c.getSharedPreferences("widget_layouts",0);java.util.Map<String,?> keep=new java.util.HashMap<>(lp.getAll());android.content.SharedPreferences.Editor le=lp.edit().clear();String[] profiles={"split3/","split2/","full/"};for(String pr:profiles){le.putString(pr+"seat","0.1,0.1,1,1");le.putString(pr+"climate","0.3,0.1,1,1");le.putString(pr+"clock","0.2,0.2,1,1");le.putString(pr+"music","0.6,0.6,1,1");}le.commit();java.util.Map<String,?> before=new java.util.HashMap<>(lp.getAll());
   WidgetBoard board=new WidgetBoard(c,()->{});for(String key:new String[]{"clock","music"}){board.addWidget(key,key,new android.widget.TextView(c),key.equals("clock"));board.showWidget(key,true);}for(int[] size:new int[][]{{841,1210},{1682,1210},{2560,1439}}){board.measure(android.view.View.MeasureSpec.makeMeasureSpec(size[0],android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(size[1],android.view.View.MeasureSpec.EXACTLY));board.layout(0,0,size[0],size[1]);}
   for(String pr:profiles){check(before.get(pr+"seat").equals(lp.getString(pr+"seat",null)),"old seat layout kept on disk, unread "+pr);check(before.get(pr+"climate").equals(lp.getString(pr+"climate",null)),"old climate layout kept on disk, unread "+pr);}
   board.setDriving(true);check(board.isDriving()&&!board.isEditing(),"driving closes editing");boolean allShown=true;for(int i=0;i<board.getChildCount();i++)allShown&=board.getChildAt(i).getAlpha()==1f;check(allShown,"every widget stays visible while driving");board.setDriving(false);
   le=lp.edit().clear();for(java.util.Map.Entry<String,?> e:keep.entrySet())if(e.getValue() instanceof String)le.putString(e.getKey(),(String)e.getValue());le.commit();}
  boolean was=AppUi.driving;AppUi.driving=true;android.app.Dialog probe=new android.app.Dialog(c);check(AppUi.refuse(probe)&&!probe.isShowing(),"dialogs are refused while driving");AppUi.driving=false;check(!AppUi.refuse(new android.app.Dialog(c)),"dialogs open when parked");AppUi.driving=was;
  // Theme market mockup: own built-in backgrounds only, every value marked as an example, tabs filter correctly.
  for(MarketPreview.Item item:MarketPreview.ITEMS){check(item.background>=0&&item.background<=2,"market uses built-in backgrounds only "+item.name);JSONObject mt=MarketPreview.theme(item);check("builtin".equals(mt.getJSONObject("background").getString("type")),"market never uses Workshop originals "+item.name);check(item.creator.startsWith("예시"),"market creators are marked as examples "+item.name);}
  for(MarketPreview.Item item:MarketPreview.filter(2))check(item.price==0,"free tab only free");for(MarketPreview.Item item:MarketPreview.filter(3))check(item.price>0,"premium tab only paid");
  java.util.List<MarketPreview.Item> popular=MarketPreview.filter(0);for(int i=1;i<popular.size();i++)check(popular.get(i-1).downloads>=popular.get(i).downloads,"popular sorted by downloads");check(MarketPreview.MOCK.contains("시안")&&MarketPreview.MOCK.contains("예시"),"mock label shown");check(MarketPreview.price(MarketPreview.ITEMS[1]).equals("₩2,900")&&MarketPreview.price(MarketPreview.ITEMS[0]).equals("무료"),"price labels");
  // 1.36 widgets-in-background: music requests, replacesWidgets manifest, estimated tempo (not measured from sound).
  check(CreatorTheme.REQUESTS.containsAll(java.util.Arrays.asList("media.playPause","media.next","media.prev"))&&CreatorTheme.mediaRequest("media.next")&&!CreatorTheme.mediaRequest("ambient.color"),"music requests allowed without confirmation, ambient still confirmed");
  {JSONObject web=CreatorTheme.defaults();web.getJSONObject("background").put("type","web").put("requests",new org.json.JSONArray().put("media.playPause"));web.put("replacesWidgets",new org.json.JSONArray().put("clock").put("music"));JSONObject kept=CreatorTheme.validate(web);check(kept.getJSONArray("replacesWidgets").length()==2,"web theme keeps replacesWidgets");
   for(String badKey:new String[]{"seat","memo","navigation"}){JSONObject b=new JSONObject(web.toString());b.put("replacesWidgets",new org.json.JSONArray().put(badKey));boolean refused;try{CreatorTheme.validate(b);refused=false;}catch(Exception e){refused=true;}check(refused,"replacesWidgets refuses "+badKey);}
   JSONObject dup=new JSONObject(web.toString());dup.put("replacesWidgets",new org.json.JSONArray().put("clock").put("clock"));boolean refused;try{CreatorTheme.validate(dup);refused=false;}catch(Exception e){refused=true;}check(refused,"replacesWidgets refuses duplicates");
   JSONObject notWeb=CreatorTheme.defaults();notWeb.put("replacesWidgets",new org.json.JSONArray().put("clock"));check(!CreatorTheme.validate(notWeb).has("replacesWidgets"),"only web themes can replace widgets");}
  check(CreatorTheme.estimatedBpm("",null)==0,"no song, no tempo");int bpm1=CreatorTheme.estimatedBpm("바다를 건너는 밤","제작용 예시 곡"),bpm2=CreatorTheme.estimatedBpm("바다를 건너는 밤","제작용 예시 곡");check(bpm1==bpm2&&bpm1>=72&&bpm1<=135,"estimated tempo stable and in 72..135");
  {int lo=999,hi=0;for(int i=0;i<200;i++){int b=CreatorTheme.estimatedBpm("곡 "+i,"가수");lo=Math.min(lo,b);hi=Math.max(hi,b);}check(lo>=72&&hi<=135&&hi-lo>40,"tempo spread across tracks");}
  // Pull-down quick settings: the start band stays inside the app window's top 40dp, and release follows distance unless flung.
  float density=c.getResources().getDisplayMetrics().density;check(PullDownPanel.startsInBand(0,density)&&PullDownPanel.startsInBand(39*density,density),"top band starts the pull");check(!PullDownPanel.startsInBand(41*density,density)&&!PullDownPanel.startsInBand(-1,density),"below the band or above the window never starts it");
  check(PullDownPanel.shouldOpen(400,1000,0)&&!PullDownPanel.shouldOpen(300,1000,0),"opens past 35% of the sheet");check(PullDownPanel.shouldOpen(50,1000,1200),"a downward fling opens");check(!PullDownPanel.shouldOpen(900,1000,-1200),"an upward fling closes");check(!PullDownPanel.shouldOpen(0,0,0),"an unmeasured sheet stays closed");
  Log.i("ThemeHubChecks","PASS "+passed);
 }catch(Throwable error){Log.e("ThemeHubChecks","FAIL",error);throw new IllegalStateException(error);}}
}

