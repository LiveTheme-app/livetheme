package io.hmg.wallpaperapp;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import org.json.JSONObject;

final class ThemeEffects extends View {
 private final ThemeStudio studio;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG),glowPaint=new Paint(Paint.ANTI_ALIAS_FLAG);private long previous;private double time;boolean active,playing=true;
 /** Car/app values for the theme's data links; drawn under the widgets with the same formula as the PC preview. */
 final ThemeLinks links=new ThemeLinks();
 static final float NIGHT_DIM=.15f;
 /** Viewer preferences from the 분위기 preset (Mood): night dimming, effect amount/speed scale, and an effect colour taken from the background. */
 boolean nightDim=true;float amountScale=1,speedScale=1;Integer autoColor;
 private int glowShaderColor,glowShaderSize;
 /** Rebuilt only when the glow colour or width changes, not every frame. */
 private static Shader glowShader(float cx,float cy,float radius,int color){return new RadialGradient(cx,cy,radius,new int[]{color,color&0x00ffffff},new float[]{0,.7f},Shader.TileMode.CLAMP);}
 ThemeEffects(Context c,ThemeStudio studio){super(c);this.studio=studio;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 @Override public boolean dispatchTouchEvent(MotionEvent event){return false;}
 @Override protected void onDraw(Canvas canvas){
  ThemeLinks.Overlay overlay=links.compute(studio.config.optJSONObject("links"));
  if(overlay.tint>0)canvas.drawColor(Color.argb(Math.round(overlay.tint*255),Color.red(overlay.tintColor),Color.green(overlay.tintColor),Color.blue(overlay.tintColor)));
  if(overlay.glow>0){float rx=getWidth()*.6f,ry=getHeight()*.55f,cx=getWidth()/2f,cy=getHeight();int c=Color.argb(Math.round(overlay.glow*255),Color.red(overlay.glowColor),Color.green(overlay.glowColor),Color.blue(overlay.glowColor));int size=getWidth()*65536+getHeight();if(c!=glowShaderColor||size!=glowShaderSize){glowShaderColor=c;glowShaderSize=size;glowPaint.setShader(glowShader(cx,cy,rx,c));}int save=canvas.save();canvas.scale(1,ry/rx,cx,cy);canvas.drawCircle(cx,cy,rx,glowPaint);canvas.restoreToCount(save);}
  // Night: a theme without its own dim link is lowered a little so a bright sky does not glare in a dark cabin.
  JSONObject linkConfig=studio.config.optJSONObject("links");float dim=nightDim&&links.night&&(linkConfig==null||linkConfig.optJSONObject("dim")==null)?Math.max(overlay.dim,NIGHT_DIM):overlay.dim;
  if(dim>0)canvas.drawColor(Color.argb(Math.round(dim*255),0,0,0));
  long now=SystemClock.elapsedRealtime();if(active&&playing&&previous!=0)time+=Math.min(100,now-previous)/1000.0*overlay.speed;previous=active&&playing?now:0;
  JSONObject effect=studio.config.optJSONObject("effect");String type=effect.optString("type","none");if("none".equals(type))return;
  int count=Math.round(effect.optInt("amount",24)*amountScale),color=autoColor!=null?autoColor:Color.parseColor(effect.optString("color","#D6F5B0"));if(count<=0)return;double t=time*effect.optDouble("speed",1)*speedScale;boolean snow="snow".equals(type);
  for(int i=0;i<count;i++){double phase=i*2.399963;float x=(float)(((i*.61803398875+t*(snow?.003:.008))%1)*getWidth()+Math.sin(t*.4+phase)*getWidth()*.025),y=(float)(((i*.381966+t*(snow?.025:.005))%1)*getHeight());paint.setColor(color);paint.setAlpha(snow?120:(int)(45+85*(.5+.5*Math.sin(t+phase))));canvas.drawCircle(x,y,getResources().getDisplayMetrics().density*(snow?1.5f:2.2f),paint);}
  if(active&&playing)postInvalidateDelayed(50);
 }
}
