package io.hmg.wallpaperapp;

/** Nearest alignment of a leading edge, center, or trailing edge. NaN means no guide. */
final class SnapMath {
 static float[] align(float position,float extent,float[] targets,int count,float radius){
  float best=radius+1,line=Float.NaN,delta=0;
  for(int i=0;i<count;i++)for(int anchor=0;anchor<3;anchor++){
   float adjustment=targets[i]-(position+extent*anchor*.5f);
   if(Math.abs(adjustment)<=radius&&Math.abs(adjustment)<best){best=Math.abs(adjustment);delta=adjustment;line=targets[i];}
  }
  return new float[]{position+delta,line};
 }
}
