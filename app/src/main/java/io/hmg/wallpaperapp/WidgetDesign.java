package io.hmg.wallpaperapp;
import android.graphics.*;
import android.view.*;
import android.widget.TextView;
import org.json.JSONObject;

/** Borderless widget designs; choosing a basic look never overwrites the creator's skin. */
final class WidgetDesign {
 static final String[] IDS={"theme","minimal","focus","classic"};
 static final String[] NAMES={"테마 커스텀","기본 심플","기본 포커스","기본 클래식"};
 static String design(JSONObject theme,String key){String value=theme.optJSONObject("widgetStyles").optString(key,"theme");return java.util.Arrays.asList(IDS).contains(value)?value:"theme";}
 static JSONObject panelFor(JSONObject theme,String key){try{
  JSONObject panel=new JSONObject(theme.getJSONObject("panel").toString()),skins=theme.optJSONObject("widgetSkins"),skin=skins==null?null:skins.optJSONObject(key);
  if(skin!=null){java.util.Iterator<String> names=skin.keys();while(names.hasNext()){String field=names.next();panel.put(field,skin.get(field));}}
  String design=design(theme,key);panel.put("_design",design);
  if(!design.equals("theme"))panel.put("font",design.equals("classic")?"serif":"sans").put("trim",design.equals("focus")?"line":design.equals("classic")?"frost":"none");
  return panel;
 }catch(org.json.JSONException e){throw new IllegalArgumentException(e);}}
 static JSONObject clockTheme(JSONObject theme){try{
  String design=design(theme,"clock");JSONObject drawing=new JSONObject(theme.toString()),clock=drawing.getJSONObject("clock"),skin=theme.getJSONObject("widgetSkins").optJSONObject("clock");clock.put("plate",0);
  if(skin!=null&&skin.has("textColor"))clock.put("color",skin.getString("textColor"));
  if(design.equals("theme")){if(skin!=null&&skin.has("font")){String font=skin.getString("font");clock.put("font",font.equals("sans")?"light":font);}}
  else clock.put("design",design.equals("classic")?"analog":"digital").put("font",design.equals("classic")?"serif":design.equals("focus")?"bold":"light").put("caption","");
  return drawing;
 }catch(org.json.JSONException e){throw new IllegalArgumentException(e);}}
 static Typeface clockFont(JSONObject theme,Typeface fallback){String font=theme.optJSONObject("clock").optString("font");return font.equals("custom")?fallback:Typeface.create(font.equals("serif")?"serif":font.equals("mono")?"monospace":font.equals("light")?"sans-serif-light":"sans-serif",font.equals("bold")?Typeface.BOLD:Typeface.NORMAL);}
 static void applySkin(View view,String key,JSONObject theme){apply(view,design(theme,key),panelFor(theme,key));}
 static void apply(View view,String style,JSONObject panel){
  float density=view.getResources().getDisplayMetrics().density;view.setBackground(null);view.setForeground(null);
  if(view instanceof CalendarWidget)((CalendarWidget)view).setPalette(panel);
  palette(view,panel);shadow(view,Color.luminance(Color.parseColor(panel.optString("textColor","#E0EEE5")))>.5,density);
 }
 static void textTheme(TextView text,JSONObject panel){
  if(panel==null||"calendar-day".equals(text.getTag()))return;
  float sp=text.getTextSize()/text.getResources().getDisplayMetrics().scaledDensity;String color=sp<=12?"mutedColor":sp<=14?"accentColor":"textColor",design=panel.optString("_design","theme"),font=panel.optString("font","sans");
  int ink=Color.parseColor(panel.optString(color,"#E0EEE5"));if(color.equals("mutedColor")){// Captions over bright skies vanish in a muted tone: lift them halfway to the text colour, keeping the hierarchy by size.
   ink=blend(ink,Color.parseColor(panel.optString("textColor","#E0EEE5")),.5f);}text.setTextColor(ink);Typeface family=Typeface.create(font.equals("serif")?"serif":font.equals("mono")?"monospace":"sans-serif",(design.equals("focus")||strongText)&&sp>=20?Typeface.BOLD:Typeface.NORMAL);
  // Large values read heavier (medium 500) than their labels, iOS-widget style; focus keeps its bold.
  text.setTypeface(!design.equals("focus")&&!strongText&&sp>=28?Typeface.create(family,500,false):family);
  text.setTextAlignment(design.equals("classic")&&!(text instanceof android.widget.Button)?View.TEXT_ALIGNMENT_CENTER:View.TEXT_ALIGNMENT_GRAVITY);
 }
 private static void palette(View view,JSONObject panel){
  if(view instanceof TextView)textTheme((TextView)view,panel);int accent=Color.parseColor(panel.optString("accentColor","#A8DCD0"));
  if(view instanceof android.widget.Button)view.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(alpha(accent,48)),null,null));
  if(view instanceof android.widget.ProgressBar){android.widget.ProgressBar bar=(android.widget.ProgressBar)view;bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(alpha(accent,42)));}
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)palette(group.getChildAt(i),panel);}
 }
 /** 분위기 option: large widget values (20sp+) bold in every design, not only in focus. */
 static boolean strongText;
 static int blend(int a,int b,float t){return Color.argb(Math.round(Color.alpha(a)+(Color.alpha(b)-Color.alpha(a))*t),Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*t),Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*t),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
 static int alpha(int color,int opacity){return (color&0xffffff)|(opacity<<24);}
 static final class Preview extends View {
  final JSONObject theme;final String key;final Paint paint=new Paint(3);final ThemeClock clock=new ThemeClock();final Typeface customFont;
  Preview(android.content.Context context,String key,String design,JSONObject source){this(context,key,design,source,null);}
  Preview(android.content.Context context,String key,String design,JSONObject source,Typeface customFont){super(context);this.key=key;this.customFont=customFont==null?Typeface.DEFAULT:customFont;try{theme=new JSONObject(source.toString());theme.getJSONObject("widgetStyles").put(key,design);}catch(org.json.JSONException e){throw new IllegalArgumentException(e);}}
  protected void onDraw(Canvas canvas){
   super.onDraw(canvas);JSONObject ink=panelFor(theme,key);canvas.drawColor(Color.luminance(Color.parseColor(ink.optString("textColor","#FFFFFF")))>.5?0xff214239:0xffE6E7E5);int save=canvas.save();float scale=Math.min(getWidth()/480f,getHeight()/260f);canvas.translate((getWidth()-480*scale)/2,(getHeight()-260*scale)/2);canvas.scale(scale,scale);JSONObject panel=panelFor(theme,key);
   if(key.equals("clock")){JSONObject drawing=clockTheme(theme);clock.draw(canvas,480,260,drawing,clockFont(drawing,customFont),true,true,System.currentTimeMillis());}
   else{String design=design(theme,key),font=panel.optString("font"),value=key.equals("calendar")?"23":key.equals("music")?"My playlist":key.equals("battery")?"72%":key.equals("weather")?"18°":key.equals("dday")?"D−7":WidgetShelf.vehicleKey(key)?WidgetShelf.sample(key):"A quiet moment";
    paint.setTypeface(Typeface.create(font.equals("serif")?"serif":font.equals("mono")?"monospace":"sans-serif",design.equals("focus")?Typeface.BOLD:Typeface.NORMAL));paint.setTextAlign(design.equals("classic")?Paint.Align.CENTER:Paint.Align.LEFT);float x=design.equals("classic")?240:30;
    paint.setColor(Color.parseColor(panel.optString("accentColor")));paint.setTextSize(22);canvas.drawText(key.toUpperCase(java.util.Locale.ROOT),x,50,paint);paint.setColor(Color.parseColor(panel.optString("textColor")));paint.setTextSize(key.equals("battery")||key.equals("calendar")?86:48);canvas.drawText(value,x,145,paint);
    paint.setTextSize(23);paint.setColor(Color.parseColor(panel.optString("mutedColor")));canvas.drawText("디자인 미리보기",x,200,paint);
   }canvas.restoreToCount(save);
  }
 }
 private static void shadow(View view,boolean light,float density){if(view instanceof TextView){TextView t=(TextView)view;// Light text sits on photos and bright skies: a halo that grows with the glyphs keeps small captions readable on white clouds.
  if(light)t.setShadowLayer(Math.min(12*density,Math.max(4*density,t.getTextSize()*.4f)),0,density,0xf0091716);else t.setShadowLayer(density,0,density,0x22ffffff);}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)shadow(group.getChildAt(i),light,density);}}
}
