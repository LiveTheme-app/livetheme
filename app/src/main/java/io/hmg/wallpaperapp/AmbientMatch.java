package io.hmg.wallpaperapp;

import android.graphics.Color;

/** Mood light from the background: the most vibrant hue in a small screen sample, lifted so a dark scene still gives a visible light. Same rule as ambientFrom() in the PC editor. */
final class AmbientMatch {
 static final long MIN_INTERVAL_MS=15_000;
 /** Hue buckets weighted by saturation² × brightness (vibrant colours win over large grey or dark areas); a grey picture falls back to its average hue at low saturation. */
 static int pick(int[] argb){
  float[] hsv=new float[3];double[] weight=new double[24],sumS=new double[24],sumV=new double[24],x=new double[24],y=new double[24];double total=0,r=0,g=0,b=0;int counted=0;
  for(int p:argb){
   Color.colorToHSV(p,hsv);if(hsv[2]<.12f)continue;counted++;r+=Color.red(p);g+=Color.green(p);b+=Color.blue(p);
   double w=hsv[1]*hsv[1]*hsv[2];if(w<.02)continue;int bin=Math.min(23,(int)(hsv[0]/15));double angle=Math.toRadians(hsv[0]);
   weight[bin]+=w;sumS[bin]+=w*hsv[1];sumV[bin]+=w*hsv[2];x[bin]+=w*Math.cos(angle);y[bin]+=w*Math.sin(angle);total+=w;
  }
  if(counted==0)return 0xffffc58a;// all dark: a warm default instead of "off"
  int best=0;for(int i=1;i<24;i++)if(weight[i]>weight[best])best=i;
  if(total<counted*.01||weight[best]==0){Color.RGBToHSV((int)(r/counted),(int)(g/counted),(int)(b/counted),hsv);return lift(hsv[0],Math.min(.25f,hsv[1]),hsv[2]);}
  float hue=(float)((Math.toDegrees(Math.atan2(y[best],x[best]))+360)%360);
  return lift(hue,(float)(sumS[best]/weight[best]),(float)(sumV[best]/weight[best]));
 }
 /** Mood lights look washed out below ~45% saturation and invisible when dark: a coloured pick gets at least 45% saturation, and every pick at least 75% brightness. A grey pick (under 30%) stays soft. */
 static int lift(float hue,float saturation,float value){return Color.HSVToColor(new float[]{hue,saturation<.3f?saturation:Math.max(saturation,.45f),Math.max(value,.75f)});}
}
