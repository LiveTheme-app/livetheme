package io.hmg.wallpaperapp;
import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

/** HTTPS in production; the emulator's loopback bridge is available in debug builds only. */
final class ThemeCommunity {
 private final Context context;private volatile HttpURLConnection current;
 ThemeCommunity(Context c){context=c;}
 String endpoint(){return context.getSharedPreferences("community",0).getString("endpoint",BuildConfig.DEBUG?"http://10.0.2.2:8787":"");}
 static String validEndpoint(String text)throws Exception{URI uri=new URI(text.trim());String host=uri.getHost();boolean local=BuildConfig.DEBUG&&"http".equals(uri.getScheme())&&Arrays.asList("10.0.2.2","127.0.0.1","localhost").contains(host);if(host==null||!("https".equals(uri.getScheme())||local)||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath()==null||uri.getPath().isEmpty()||uri.getPath().equals("/")))throw new IOException("HTTPS 서버 주소를 입력해주세요");return text.trim().replaceAll("/+$","");}
 void setEndpoint(String text)throws Exception{context.getSharedPreferences("community",0).edit().putString("endpoint",validEndpoint(text)).apply();}
 private HttpURLConnection open(String path)throws Exception{String base=endpoint();if(base.isEmpty())throw new IOException("커뮤니티 서버를 먼저 연결해주세요");HttpURLConnection c=(HttpURLConnection)new URL(validEndpoint(base)+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(6000);c.setReadTimeout(20000);current=c;return c;}
 private JSONObject json(HttpURLConnection c,int expected)throws Exception{if(c.getResponseCode()!=expected)throw new IOException("서버 응답을 확인해주세요 ("+c.getResponseCode()+")");ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){CreatorTheme.copy(in,bytes,256*1024);}return new JSONObject(bytes.toString("UTF-8"));}
 JSONArray list()throws Exception{HttpURLConnection c=open("/v1/themes");try{JSONObject data=json(c,200);JSONArray items=data.getJSONArray("items");if(items.length()>50)throw new IOException("테마 목록이 너무 큽니다");for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);validateItem(item);item.put("preview",CreatorTheme.validate(item.getJSONObject("preview")));}return items;}finally{c.disconnect();current=null;}}
 private static void validateItem(JSONObject item)throws Exception{if(!item.getString("id").matches("[a-f0-9-]{36}")||!item.getString("sha256").matches("[a-f0-9]{64}")||item.getLong("bytes")<=0||item.getLong("bytes")>80L*1024*1024)throw new IOException("테마 목록 형식이 올바르지 않습니다");}
 File download(JSONObject item)throws Exception{validateItem(item);File file=new File(context.getCacheDir(),"live-theme-"+UUID.randomUUID()+".zip");HttpURLConnection c=open("/v1/themes/"+item.getString("id")+".zip");try{if(c.getResponseCode()!=200)throw new IOException("테마를 다운로드할 수 없습니다");try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){CreatorTheme.copy(in,out,80L*1024*1024);}if(file.length()!=item.getLong("bytes"))throw new IOException("다운로드가 완료되지 않았습니다");MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));if(!hash.toString().equals(item.getString("sha256")))throw new IOException("테마 파일 검증에 실패했습니다");File folder=CreatorTheme.importUri(context,Uri.parse("content://io.hmg.wallpaperapp.themes/"+file.getName()));if(folder==null)throw new IOException("라이브테마 ZIP이 아닙니다");context.getSharedPreferences("community-downloads",0).edit().putString(item.getString("id"),folder.getName()).apply();return folder;}finally{file.delete();c.disconnect();current=null;}}
 File downloaded(String id){String name=context.getSharedPreferences("community-downloads",0).getString(id,"");if(!name.matches("[a-f0-9-]{36}"))return null;File folder=new File(context.getFilesDir(),"creator-themes/"+name);return new File(folder,"live-theme.json").isFile()?folder:null;}
 void upload(File file)throws Exception{if(file.length()>80L*1024*1024)throw new IOException("테마는 80 MiB 이하여야 합니다");HttpURLConnection c=open("/v1/themes");try{c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/zip");c.setFixedLengthStreamingMode(file.length());try(InputStream in=new FileInputStream(file);OutputStream out=c.getOutputStream()){CreatorTheme.copy(in,out,80L*1024*1024);}json(c,201);}finally{c.disconnect();current=null;}}
 void close(){HttpURLConnection c=current;if(c!=null)c.disconnect();}
}
