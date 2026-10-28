package io.hmg.wallpaperapp;
import android.graphics.*;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

final class ThemeClock {
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 void draw(Canvas c,int w,int h,JSONObject theme,Typeface font,boolean showClock,boolean showDate,long now){
  JSONObject s=theme.optJSONObject("clock");float scale=(float)s.optDouble("size",1);int color=Color.parseColor(s.optString("color","#F3F7EF"));
  p.setStyle(Paint.Style.FILL);p.clearShadowLayer();p.setColor(Color.argb(Math.round((float)s.optDouble("plate")*2.55f),7,25,30));c.drawRoundRect(6,6,w-6,h-6,24,24,p);
  p.setColor(color);p.setTypeface(font);p.setTextAlign(Paint.Align.CENTER);p.setShadowLayer((float)s.optDouble("shadow",12),0,2,0xbb071b22);
  String design=s.optString("design","digital");boolean seconds=s.optBoolean("seconds");String pattern=(s.optBoolean("hour24",true)?"HH":"hh")+":mm"+(seconds?":ss":"");
  String time=new SimpleDateFormat(pattern,Locale.getDefault()).format(new Date(now));float bottom=h*.65f;float size=Math.min(w*(seconds?.22f:.30f),h*.68f)*scale;
  if(showClock){
   if("analog".equals(design)){
    float radius=Math.min(h*.35f,w*.22f)*scale,cx=w*.5f,cy=h*.43f;p.clearShadowLayer();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(cx,cy,radius,p);p.setStrokeWidth(3);
    for(int i=0;i<12;i++){double a=i*Math.PI/6;c.drawLine(cx+(float)Math.sin(a)*radius*.88f,cy-(float)Math.cos(a)*radius*.88f,cx+(float)Math.sin(a)*radius*.96f,cy-(float)Math.cos(a)*radius*.96f,p);}
    Calendar cal=Calendar.getInstance();cal.setTimeInMillis(now);hand(c,cx,cy,radius*.52f,(cal.get(Calendar.HOUR)+cal.get(Calendar.MINUTE)/60f)*30,5);hand(c,cx,cy,radius*.77f,(cal.get(Calendar.MINUTE)+cal.get(Calendar.SECOND)/60f)*6,3);if(seconds)hand(c,cx,cy,radius*.83f,cal.get(Calendar.SECOND)*6,1);p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,4,p);bottom=h*.89f;
   }else if("stacked".equals(design)){
    String[] parts=time.split(":");p.setTextSize(h*.39f*scale);float widest=Math.max(p.measureText(parts[0]),p.measureText(parts[1]));if(widest>w*.9f)p.setTextSize(p.getTextSize()*w*.9f/widest);c.drawText(parts[0],w*.5f,h*.40f,p);c.drawText(parts[1],w*.5f,h*.79f,p);if(seconds){p.setTextSize(Math.min(h*.12f,w*.12f));c.drawText(parts[2],w*.76f,h*.76f,p);}bottom=h*.92f;
   }else{
    p.setTextSize(size);float max=w*.92f;if(p.measureText(time)>max)p.setTextSize(size*max/p.measureText(time));c.drawText(time,w*.5f,bottom,p);bottom+=size*.34f;
   }
  }else bottom=h*.5f;
  p.clearShadowLayer();if(s.optDouble("shadow",12)>0)p.setShadowLayer((float)s.optDouble("shadow",12)*.75f,0,1,0xbb071b22);p.setStyle(Paint.Style.FILL);p.setTypeface(font);p.setTextSize(Math.min(h*.095f,w*.045f));p.setColor(color);
  String date=new SimpleDateFormat("M월 d일 EEEE",Locale.KOREA).format(new Date(now));String caption=s.optString("caption","");
  if(showDate)c.drawText(date,w*.5f,bottom,p);
  if(!caption.isEmpty()){p.setTextSize(Math.min(h*.075f,w*.035f));while(p.measureText(caption)>w*.9f&&caption.length()>1)caption=caption.substring(0,caption.length()-1);c.drawText(caption,w*.5f,Math.min(h-4,bottom+h*.105f),p);}
 }
 private void hand(Canvas c,float x,float y,float length,float degrees,float width){double a=Math.toRadians(degrees);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,x+(float)Math.sin(a)*length,y-(float)Math.cos(a)*length,p);}
}
