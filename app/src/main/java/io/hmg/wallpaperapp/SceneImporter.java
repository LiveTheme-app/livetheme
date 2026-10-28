package io.hmg.wallpaperapp;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;
import org.json.JSONObject;

final class SceneImporter {
 private final Context context;
 private long bytes;private int count;
 SceneImporter(Context c){context=c;}
 File load(Uri tree)throws Exception{
  File out=new File(context.getFilesDir(),"scene-"+UUID.randomUUID());if(!out.mkdir())throw new IOException("장면 저장 공간을 만들 수 없습니다");
  try{
   copy(tree,DocumentsContract.getTreeDocumentId(tree),out,0);
   validate(out);return out;
  }catch(Exception e){discard(out);throw e;}
 }
 File loadZip(Uri uri)throws Exception{
  if(!"content".equals(uri.getScheme()))throw new IOException("파일 앱에서 테마 ZIP을 공유해주세요");
  File out=new File(context.getFilesDir(),"scene-"+UUID.randomUUID());if(!out.mkdir())throw new IOException("저장 공간을 만들 수 없습니다");
  try(InputStream input=context.getContentResolver().openInputStream(uri)){
   if(input==null)throw new IOException("공유된 파일을 읽을 수 없습니다");
   try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(input)){
    java.util.zip.ZipEntry entry;Set<String> names=new HashSet<>();
    while((entry=zip.getNextEntry())!=null){
     String name=entry.getName().replace('\\','/');String[] parts=name.split("/",-1);
     if(++count>512||name.length()>1024||parts.length>9||name.startsWith("/")||name.contains(":")||name.indexOf('\0')>=0||Arrays.asList(parts).contains("..")||Arrays.asList(parts).contains(".")||!names.add(name))throw new IOException("지원하지 않는 ZIP 경로입니다");
     File target=new File(out,name);if(!target.getCanonicalPath().startsWith(out.getCanonicalPath()+File.separator))throw new IOException("잘못된 ZIP 경로입니다");
     if(entry.isDirectory()){if(!target.isDirectory()&&!target.mkdirs())throw new IOException("폴더 저장 실패");}
     else{
      File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("폴더 저장 실패");
      long size=0;try(OutputStream output=new FileOutputStream(target)){byte[] buffer=new byte[32768];int n;
       while((n=zip.read(buffer))!=-1){size+=n;bytes+=n;if(size>64L*1024*1024||bytes>200L*1024*1024)throw new IOException("테마 용량 한도를 초과했습니다");output.write(buffer,0,n);}
      }
     }zip.closeEntry();
    }
   }
   validate(out);return out;
  }catch(Exception e){discard(out);throw e;}
 }
 private void validate(File out)throws Exception{
  File project=new File(out,"project.json"),pkg=new File(out,"scene.pkg");
  if(!project.isFile()||project.length()>1024*1024||!pkg.isFile())throw new IOException("최상위에 project.json과 scene.pkg가 있어야 합니다");
  JSONObject info=new JSONObject(new String(java.nio.file.Files.readAllBytes(project.toPath()),java.nio.charset.StandardCharsets.UTF_8));
  if(!"scene".equalsIgnoreCase(info.optString("type")))throw new IOException("현재는 Scene 유형만 가져올 수 있습니다");PkgValidator.validate(pkg);
  prepareBackground(out,pkg,info);
 }
 private void prepareBackground(File out,File pkg,JSONObject project)throws Exception{
  // Keep the original package; render a private loose copy without stale scripted clock text.
  try(RandomAccessFile file=new RandomAccessFile(pkg,"r")){
   long n=PkgValidator.u32(file);file.seek(file.getFilePointer()+n);int entries=(int)PkgValidator.u32(file);
   String[] names=new String[entries];long[] offsets=new long[entries],sizes=new long[entries];long total=0;
   for(int i=0;i<entries;i++){
    int length=(int)PkgValidator.u32(file);byte[] name=new byte[length];file.readFully(name);names[i]=new String(name,java.nio.charset.StandardCharsets.UTF_8).replace('\\','/');
    offsets[i]=PkgValidator.u32(file);sizes[i]=PkgValidator.u32(file);total+=sizes[i];
   }
   if(total>128L*1024*1024)throw new IOException("장면 리소스 용량이 너무 큽니다");long start=file.getFilePointer();
   for(int i=0;i<entries;i++){
    if(names[i].equals("project.json")||names[i].equals("scene.pkg"))continue;
    File target=new File(out,names[i]);if(names[i].split("/").length>9||names[i].contains(":")||!target.getCanonicalPath().startsWith(out.getCanonicalPath()+File.separator))throw new IOException("잘못된 장면 경로입니다");
    if(names[i].endsWith("/"))continue;
    File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("장면 폴더를 만들 수 없습니다");
    file.seek(start+offsets[i]);long remaining=sizes[i];try(OutputStream output=new FileOutputStream(target)){byte[] buffer=new byte[32768];while(remaining>0){int count=file.read(buffer,0,(int)Math.min(buffer.length,remaining));if(count<0)throw new EOFException();output.write(buffer,0,count);remaining-=count;}}
   }
  }
  File sceneFile=new File(out,"scene.json");if(sceneFile.length()>4L*1024*1024)throw new IOException("장면 설명이 너무 큽니다");
  JSONObject scene=new JSONObject(new String(java.nio.file.Files.readAllBytes(sceneFile.toPath()),java.nio.charset.StandardCharsets.UTF_8));
  org.json.JSONArray objects=scene.optJSONArray("objects");int hidden=0;
  if(objects!=null)for(int i=0;i<objects.length();i++){
   JSONObject object=objects.optJSONObject(i);if(object==null)continue;
   JSONObject alpha=object.optJSONObject("alpha");String binding=alpha==null?"":alpha.optString("user").toLowerCase(java.util.Locale.ROOT);
   boolean audioOnly=false;org.json.JSONArray effects=object.optJSONArray("effects");
   if(object.optString("image").equals("models/util/composelayer.json")&&effects!=null)for(int j=0;j<effects.length();j++){
    JSONObject effect=effects.optJSONObject(j);if(effect!=null&&effect.optString("file").toLowerCase(java.util.Locale.ROOT).contains("audio"))audioOnly=true;
   }
   if(object.has("text")||binding.matches("clockopacity[0-9]*")||binding.equals("opclock")||audioOnly){object.put("visible",false);hidden++;}
  }
  java.nio.file.Files.write(sceneFile.toPath(),scene.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  project.put("file","scene.json");java.nio.file.Files.write(new File(out,"project.json").toPath(),project.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  android.util.Log.i("LiveThemeScene","IMPORT_READY hiddenTextLayers="+hidden+" resources="+totalResourceCount(out));
 }
 private int totalResourceCount(File folder){File[] all=folder.listFiles();if(all==null)return 0;int total=0;for(File f:all)total+=f.isDirectory()?totalResourceCount(f):1;return total;}
 private void copy(Uri tree,String id,File out,int depth)throws Exception{
  if(depth>8)throw new IOException("폴더 구조가 너무 깊습니다");
  Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
  String[] fields={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
  try(Cursor cursor=context.getContentResolver().query(children,fields,null,null,null)){
   if(cursor==null)throw new IOException("선택한 폴더를 읽을 수 없습니다");
   while(cursor.moveToNext()){
    if(++count>512)throw new IOException("파일 수가 너무 많습니다 (최대 512개)");
    String childId=cursor.getString(0),name=cursor.getString(1),mime=cursor.getString(2);
    if(name==null||name.isEmpty()||name.equals(".")||name.equals("..")||name.contains("/")||name.contains("\\")||name.indexOf('\0')>=0)throw new IOException("잘못된 파일 이름입니다");
    File target=new File(out,name);
    if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){if(!target.mkdir())throw new IOException("폴더 저장 실패");copy(tree,childId,target,depth+1);}
    else{
     long fileBytes=0;Uri document=DocumentsContract.buildDocumentUriUsingTree(tree,childId);
     try(InputStream input=context.getContentResolver().openInputStream(document);OutputStream output=new FileOutputStream(target)){
      if(input==null)throw new IOException("파일을 열 수 없습니다");
      byte[] buffer=new byte[32768];int n;
      while((n=input.read(buffer))!=-1){fileBytes+=n;bytes+=n;if(fileBytes>64L*1024*1024||bytes>200L*1024*1024)throw new IOException("가져오기 용량 한도를 초과했습니다");output.write(buffer,0,n);}
     }
    }
   }
  }
 }
 void discard(File f){
  // Only UUID import folders created inside this app's private storage may be removed.
  try{if(!f.getCanonicalPath().startsWith(context.getFilesDir().getCanonicalPath()+File.separator+"scene-"))return;}catch(IOException e){return;}
  File[] children=f.listFiles();if(children!=null)for(File c:children)discard(c);f.delete();
 }
}
