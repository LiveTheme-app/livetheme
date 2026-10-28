package io.hmg.wallpaperapp;
import org.json.*;
import java.util.Locale;

/** Backgrounds with matching widget palettes and typography. */
final class ThemePresets {
 static final String[] NAMES={"Windy · 그린 쉼표","Winter · 고요한 겨울","Lazy River · 느린 오후","Wave · 모던 웨이브","Magnetofluid · 실버 미니멀"};
 static final String[] NOTES={"차분한 초록 · 시계와 차량 배터리","겨울빛과 은색 · 일정과 나의 메모","청록과 크림 · 음악과 날씨","Hope麻匪 · 청록과 자홍의 추상적인 물결","Hope麻匪 · 은빛 여백과 움직이는 금속 유체"};
 static JSONObject create(int index)throws Exception{
  if(index<0||index>=NAMES.length)throw new IllegalArgumentException("template index");JSONObject theme=CreatorTheme.defaults();theme.put("name",NAMES[index]).put("author",index>=3?"Hope麻匪 · 라이브테마 위젯 스타일":"라이브테마 기본 템플릿");theme.getJSONObject("background").put("type","workshop").put("id",WorkshopLibrary.IDS[index]).put("index",Math.min(index,2));
  JSONObject panel=theme.getJSONObject("panel");
  for(String key:WidgetShelf.KEYS)panel.put(key,false);panel.put("clock",true).put("date",true).put("music",true).put("battery",true);if(index<2)panel.put("calendar",true);if(index==1)panel.put("memo",true);if(index==2)panel.put("weather",true);
  JSONObject layouts=theme.getJSONObject("layouts");
  for(String profile:new String[]{"full","split2","split3"}){
   int w=profile.equals("full")?2560:profile.equals("split2")?1682:841,h=profile.equals("full")?1439:1210;
   int[][] full={{96,144,720,320},{96,610,480,480},{1984,1040,480,220},{720,740,360,300},{720,1080,360,220},{2040,500,360,300},{2040,160,360,240}};
   int[][] split2={{80,144,600,260},{1090,440,480,480},{1090,960,480,200},{80,790,360,300},{480,790,360,220},{480,470,360,280},{80,470,360,240}};
   int[][] split3={{120,96,600,260},{16,400,400,480},{440,400,360,220},{440,920,360,220},{440,920,360,220},{16,900,400,240},{440,660,360,230}};
   int[][] boxes=profile.equals("full")?full:profile.equals("split2")?split2:split3;
   for(int i=0;i<boxes.length;i++){int[] r=boxes[i];double x=Math.max(0,Math.min(1,(r[0]-16.0)/Math.max(1,w-32-r[2]))),y=Math.max(0,Math.min(1,(r[1]-96.0)/Math.max(1,h-144-r[3])));layouts.put(profile+"/"+WidgetShelf.KEYS[i],String.format(Locale.ROOT,"%.6f,%.6f,%.6f,%.6f",x,y,r[2]/(i==0?600.0:480.0),r[3]/218.0));}
  }
  return CreatorTheme.validate(widgetLook(theme,index));
 }
 static JSONObject widgetLook(JSONObject theme,int index)throws Exception{
  if(index<0||index>5)throw new IllegalArgumentException("widget look index");
  String[][] colors={{"#13372D","#F4F5DD","#BED0BC","#D4E6AF"},{"#1B3040","#F4F7FC","#B7CCD9","#BEDCF0"},{"#F5E8CA","#FFF5D9","#D7E5D0","#DDE8BA"},{"#11182E","#F4F6FF","#B6C9DC","#83E9E7"},{"#F2F1ED","#292D36","#646A76","#75728C"},{"#F8EEE1","#FFF5E4","#E9DECE","#F6D7B0"}};
  String[] trims={"leaf","frost","ripple","neon","line","paper"},fonts={"sans","serif","serif","mono","sans","serif"};int[] corners={32,12,30,12,18,20},opacities={76,76,91,84,94,93};String[] c=colors[index];
  JSONObject panel=theme.getJSONObject("panel");panel.put("color",c[0]).put("textColor",c[1]).put("mutedColor",c[2]).put("accentColor",c[3]).put("font",fonts[index]).put("corner",corners[index]).put("opacity",opacities[index]).put("trim",trims[index]);
  JSONObject skins=new JSONObject(),styles=theme.getJSONObject("widgetStyles");for(String key:WidgetShelf.KEYS){styles.put(key,"theme");JSONObject skin=new JSONObject();if(key.equals("calendar"))skin.put("opacity",Math.min(98,opacities[index]+6));if(key.equals("memo"))skin.put("trim",index==5?"paper":trims[index]);if(key.equals("music"))skin.put("corner",Math.min(36,corners[index]+4));skins.put(key,skin);}theme.put("widgetSkins",skins);
  
  JSONObject clock=theme.getJSONObject("clock");clock.put("design",index==2?"analog":"digital").put("font",index==1?"serif":index==3?"mono":"light").put("color",index==4?c[1]:index==2?"#FFF3D8":index==5?"#FFF6E7":c[1]).put("shadow",index==4?0:8).put("caption",new String[]{"SLOW DOWN · BREATHE","WINTER STILLNESS","A LITTLE RIVER TIME","MAKE ROOM FOR YOURSELF","A MOMENT, SIMPLY","COLLECT LITTLE MOMENTS"}[index]);
  return theme;
 }
}
