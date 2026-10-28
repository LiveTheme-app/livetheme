package io.hmg.wallpaperapp;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import ai.pleos.playground.vehicle.api.model.capability.AccessType;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Isolated fixture storage. No vehicle service writes are performed by this check. */
final class VehicleChecks {
 static int count;
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;android.util.Log.i("VehicleChecks","PASS "+name);}
 static Field field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
 static void set(Object target,String name,Object value)throws Exception{field(target,name).set(target,value);}
 static void layout(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,1073741824),View.MeasureSpec.makeMeasureSpec(h,1073741824));v.layout(0,0,w,h);}
 static void run(Activity a)throws Exception{
  ai.pleos.playground.vehicle.api.model.area.SeatArea front=new ai.pleos.playground.vehicle.api.model.area.SeatArea(ai.pleos.playground.vehicle.constant.area.SeatValue.ROW_1_LEFT),global=new ai.pleos.playground.vehicle.api.model.area.SeatArea();check(VehicleWidgets.chooseArea(front,Arrays.asList(global)).equals(global),"global-only vehicle area supported");check(VehicleWidgets.chooseArea(front,Arrays.asList(front,global)).equals(front),"explicit seat area preferred");check(VehicleWidgets.chooseArea(front,Collections.emptyList()).equals(front),"unsupported does not target arbitrary seat");
  check(WidgetShelf.KEYS.length==10&&new HashSet<>(Arrays.asList(WidgetShelf.KEYS)).size()==10,"10 unique catalog entries");// 1.35: seat removed, 1.37: climate removed
  for(AccessType access:AccessType.values()){check(VehicleWidgets.canRead(access)==(access==AccessType.READ||access==AccessType.READ_WRITE),"read access "+access);check(VehicleWidgets.canWrite(access)==(access==AccessType.WRITE||access==AccessType.READ_WRITE),"write access "+access);}
  check(!VehicleWidgets.inRange(Double.NaN,0,100)&&!VehicleWidgets.inRange(50,Double.NaN,100)&&!VehicleWidgets.inRange(50,0,Double.POSITIVE_INFINITY),"nonfinite inputs rejected");
  check(!VehicleWidgets.inRange(-1,0,100)&&!VehicleWidgets.inRange(101,0,100)&&VehicleWidgets.inRange(0,0,100)&&VehicleWidgets.inRange(100,0,100),"actual capability bounds enforced");
  JSONObject legacy=CreatorTheme.defaults(),next=CreatorTheme.defaults();for(String key:new String[]{"ambient","trip","navigation"}){check(!legacy.getJSONObject("panel").getBoolean(key),"new widget defaults hidden "+key);next.getJSONObject("panel").put(key,true);next.getJSONObject("widgetStyles").put(key,"classic");next.getJSONObject("widgetSkins").put(key,new JSONObject().put("textColor","#ABCDEF"));next.getJSONObject("layouts").put("full/"+key,"0,0,1,1");}
  next.put("vehicleCommands",new JSONObject().put("temperature",30));next.put("latitude",37.5);JSONObject clean=CreatorTheme.validate(next);check(!clean.has("vehicleCommands")&&!clean.has("latitude"),"shared themes never contain vehicle commands or coordinates");
  for(String key:new String[]{"ambient","trip","navigation"}){check(clean.getJSONObject("widgetStyles").getString(key).equals("classic"),"style roundtrip "+key);check(clean.getJSONObject("widgetSkins").getJSONObject(key).getString("textColor").equals("#ABCDEF"),"skin roundtrip "+key);check(!CreatorTheme.forApplication(clean,legacy).getJSONObject("panel").getBoolean(key),"applying theme preserves personal visibility "+key);}
  VehicleWidgets host=new VehicleWidgets(a,()->{});Object card=host.cards.get("ambient"),metric=((ArrayList<?>)field(card,"fields").get(card)).get(1);final int[] writes={0};Class<?> write=field(metric,"write").getType();set(metric,"write",Proxy.newProxyInstance(write.getClassLoader(),new Class<?>[]{write},(proxy,m,args)->{writes[0]++;return null;}));set(metric,"readable",true);set(metric,"writable",true);set(metric,"min",16d);set(metric,"max",30d);Method apply=metric.getClass().getDeclaredMethod("apply",double.class);apply.setAccessible(true);
  apply.invoke(metric,22d);check(writes[0]==0,"background cannot send control");set(host,"active",true);set(card,"visible",true);apply.invoke(metric,31d);check(writes[0]==0,"out of range cannot send control");set(metric,"writable",false);apply.invoke(metric,22d);check(writes[0]==0,"read only cannot send control");set(metric,"writable",true);apply.invoke(metric,22d);check(writes[0]==1,"explicit valid action sends exactly once");apply.invoke(metric,22d);check(writes[0]==1,"pending operation blocks duplicate action");host.stop();check(!(boolean)field(host,"active").get(host),"foreground session stopped");
  android.content.SharedPreferences positions=a.getSharedPreferences("widget_layouts",0);Map<String,?> original=new HashMap<>(positions.getAll());
  for(int[] size:new int[][]{{841,1210},{1682,1210},{2560,1439}}){positions.edit().clear().commit();WidgetBoard board=new WidgetBoard(a,()->{});board.addWidget("clock","시계",new TextView(a),true);board.showWidget("clock",true);for(String key:new String[]{"ambient","trip","navigation"}){board.addWidget(key,WidgetShelf.nameOf(key),new TextView(a),false);board.showWidget(key,false);}layout(board,size[0],size[1]);for(String key:new String[]{"ambient","trip","navigation"}){check(board.dropWidget(key,size[0]*.5f,size[1]*.55f),"add new widget "+key+" "+size[0]);board.showWidget(key,true);layout(board,size[0],size[1]);check(key.equals(board.selectedKey()),"selection matches "+key+" "+size[0]);board.showWidget(key,false);}}
  SharedPreferences.Editor restore=positions.edit().clear();for(Map.Entry<String,?> e:original.entrySet())if(e.getValue() instanceof String)restore.putString(e.getKey(),(String)e.getValue());restore.commit();
  android.util.Log.i("VehicleChecks","DONE "+count);
 }
}
