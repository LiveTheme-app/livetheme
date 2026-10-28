package io.hmg.engineprobe;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.util.Log;
import java.io.*;
import java.util.*;

/** Local compatibility experiment. No network permission, Java bridge or user-file access. */
public final class MainActivity extends Activity {
 private WebView web;
 /** Silent test media session (no audio): lets the car app read a song and its controls on the emulator. Logs every control as ProbeMedia. */
 static android.media.session.MediaSession session;static int track;static boolean playing=true;
 static final String[][] TRACKS={{"바다를 건너는 밤","시험용 무음 세션"},{"오로라 아래에서","시험용 무음 세션"},{"반짝이는 입자","시험용 무음 세션"}};
 void startTestSession(){if(session!=null)return;session=new android.media.session.MediaSession(getApplicationContext(),"probe");session.setCallback(new android.media.session.MediaSession.Callback(){
   public void onPlay(){playing=true;publish("play");}public void onPause(){playing=false;publish("pause");}
   public void onSkipToNext(){track=(track+1)%TRACKS.length;publish("next");}public void onSkipToPrevious(){track=(track+TRACKS.length-1)%TRACKS.length;publish("prev");}});
  session.setActive(true);publish("start");}
 static void publish(String why){session.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,TRACKS[track][0]).putString(android.media.MediaMetadata.METADATA_KEY_ARTIST,TRACKS[track][1]).build());
  session.setPlaybackState(new android.media.session.PlaybackState.Builder().setActions(android.media.session.PlaybackState.ACTION_PLAY|android.media.session.PlaybackState.ACTION_PAUSE|android.media.session.PlaybackState.ACTION_PLAY_PAUSE|android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT|android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS).setState(playing?android.media.session.PlaybackState.STATE_PLAYING:android.media.session.PlaybackState.STATE_PAUSED,0,1).build());
  android.util.Log.i("ProbeMedia",why+" track="+TRACKS[track][0]+" playing="+playing);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);web=new WebView(this);setContentView(web);
  if(getIntent().getBooleanExtra("mediaSession",false)){startTestSession();moveTaskToBack(true);return;}
  if(getIntent().getBooleanExtra("motionChecks",false)){io.hmg.wallpaperapp.UiMotionChecks.run(this);return;}
  if(getIntent().getBooleanExtra("shapeChecks",false)){io.hmg.wallpaperapp.WidgetShapeChecks.run(this);finish();return;}
  if(getIntent().getBooleanExtra("catalogChecks",false)){io.hmg.wallpaperapp.WidgetCatalogChecks.run(this);finish();return;}
  if(getIntent().getBooleanExtra("widgetChecks",false)){io.hmg.wallpaperapp.WidgetBoardChecks.run(this);finish();return;}
  if(getIntent().getBooleanExtra("weatherChecks",false)){io.hmg.wallpaperapp.WeatherChecks.run(this);finish();return;}
  if(getIntent().getBooleanExtra("creatorChecks",false)){io.hmg.wallpaperapp.CreatorThemeChecks.run(this);finish();return;}
  if(getIntent().getBooleanExtra("themeChecks",false)){io.hmg.wallpaperapp.ThemeHubChecks.run(this);finish();return;}
  String share=getIntent().getStringExtra("share");
  if(share!=null){
   try{
    File target=new File(getCacheDir(),"theme.zip");
    if("cached".equals(share)){
     if(!target.isFile()||target.length()==0)throw new IOException("Missing cached theme");
    }else try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(target))){
     if("bad".equals(share)){zip.putNextEntry(new java.util.zip.ZipEntry("../escape.txt"));zip.write(new byte[]{1});zip.closeEntry();}
     else pack(zip,"wallpaper","");
    }
    android.net.Uri uri=android.net.Uri.parse("content://io.hmg.engineprobe.theme/theme.zip");
    android.content.Intent send=new android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/zip").setPackage("io.hmg.wallpaperapp").putExtra(android.content.Intent.EXTRA_STREAM,uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
    send.setClipData(android.content.ClipData.newRawUri("테마 ZIP",uri));startActivity(send);finish();return;
   }catch(IOException e){throw new IllegalStateException(e);}
  }
  web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);
  web.getSettings().setAllowContentAccess(false);web.getSettings().setMediaPlaybackRequiresUserGesture(true);
  web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){Log.i("EngineProbe",m.message());return true;}});
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
    String path=r.getUrl().getPath();
    if(!"https".equals(r.getUrl().getScheme())||!"appassets.androidplatform.net".equals(r.getUrl().getHost())||path==null||path.contains("..")||!path.startsWith("/local/"))return missing();
    try {
     InputStream data=getAssets().open(path.substring(7));
     String mime=path.endsWith(".js")?"text/javascript":path.endsWith(".html")?"text/html":path.endsWith(".json")?"application/json":"application/octet-stream";
     return new WebResourceResponse(mime,"UTF-8",200,"OK",Collections.singletonMap("Access-Control-Allow-Origin","https://appassets.androidplatform.net"),data);
    }catch(IOException e){return missing();}
   }
  });web.loadUrl("https://appassets.androidplatform.net/local/index.html");
 }
 private void pack(java.util.zip.ZipOutputStream zip,String asset,String name)throws IOException{
  String[] children=getAssets().list(asset);
  if(children!=null&&children.length>0){for(String child:children)pack(zip,asset+"/"+child,name.isEmpty()?child:name+"/"+child);}
  else{zip.putNextEntry(new java.util.zip.ZipEntry(name));try(InputStream input=getAssets().open(asset)){byte[] b=new byte[32768];int n;while((n=input.read(b))!=-1)zip.write(b,0,n);}zip.closeEntry();}
 }
 private WebResourceResponse missing(){return new WebResourceResponse("text/plain","UTF-8",404,"Not found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
 @Override protected void onPause(){web.onPause();super.onPause();}
 @Override protected void onResume(){super.onResume();web.onResume();}
 @Override protected void onDestroy(){web.destroy();super.onDestroy();}
}
