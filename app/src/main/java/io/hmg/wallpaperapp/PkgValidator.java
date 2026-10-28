package io.hmg.wallpaperapp;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounds checks before handing an external package to the renderer. */
final class PkgValidator {
 static long u32(RandomAccessFile f)throws IOException{return Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()));}
 static void validate(File file)throws IOException{
  try(RandomAccessFile f=new RandomAccessFile(file,"r")){
   if(f.length()<16||f.length()>64L*1024*1024)throw new IOException("장면 파일 크기를 확인해주세요 (최대 64MB)");
   long magicLength=u32(f);if(magicLength!=8)throw new IOException("지원하지 않는 장면 형식입니다");
   byte[] magic=new byte[8];f.readFully(magic);if(!new String(magic,StandardCharsets.US_ASCII).matches("PKGV[0-9]{4}"))throw new IOException("scene.pkg 형식이 아닙니다");
   long count=u32(f);if(count<1||count>5000)throw new IOException("장면 항목 수가 지원 범위를 벗어납니다");
   long[] ends=new long[(int)count];Set<String> names=new HashSet<>();
   for(int i=0;i<count;i++){
    long n=u32(f);if(n<1||n>4096||n+8>f.length()-f.getFilePointer())throw new IOException("손상된 장면 목록입니다");
    byte[] raw=new byte[(int)n];f.readFully(raw);String name=new String(raw,StandardCharsets.UTF_8).replace('\\','/');
    if(name.startsWith("/")||name.indexOf('\0')>=0||Arrays.asList(name.split("/",-1)).contains("..")||!names.add(name))throw new IOException("잘못된 장면 경로입니다");
    long offset=u32(f),size=u32(f);ends[i]=offset+size;
   }
   long payload=f.length()-f.getFilePointer();for(long end:ends)if(end>payload)throw new IOException("장면 데이터 범위가 잘못되었습니다");
   if(!names.contains("scene.json"))throw new IOException("scene.json이 없는 장면은 아직 지원하지 않습니다");
  }catch(EOFException e){throw new IOException("잘린 장면 파일입니다",e);}
 }
}
