package io.hmg.wallpaperapp;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.model.area.SeatArea;
import ai.pleos.playground.vehicle.api.model.capability.*;
import ai.pleos.playground.vehicle.constant.area.SeatValue;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.json.JSONObject;
import java.util.*;

/** One foreground vehicle session. Shared themes carry appearance, never vehicle commands. */
final class VehicleWidgets {
 /** 1.35: the seat heating/ventilation widget was removed (driving-time controls are not this app's job). Saved "seat" layouts and prefs stay on disk, unread. */
 static final String[] KEYS={"ambient","trip"};   // 1.37: the climate widget was removed too (see WidgetShelf.RETIRED)
 final LinkedHashMap<String,Card> cards=new LinkedHashMap<>();
 private final Activity activity;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final SharedPreferences preferences;
 private final Runnable changed;
 private Vehicle vehicle;
 private boolean active;
 private int generation;
 private JSONObject theme;
 /** Mood-light colour and brightness for theme data links. */
 java.util.function.BiConsumer<Integer,Double> ambientState;
 private AlertDialog controls;private Card controlsCard;private final ArrayList<Runnable> controlBindings=new ArrayList<>();
 private final Runnable poll=new Runnable(){public void run(){if(!active)return;for(Card c:cards.values())if(c.visible)for(Metric f:c.fields){if(f.skip>0){f.skip--;continue;}f.refresh();}handler.postDelayed(this,15000);}};

 VehicleWidgets(Activity activity,Runnable changed){
  this.activity=activity;this.changed=changed;preferences=activity.getSharedPreferences("vehicle_widgets",Context.MODE_PRIVATE);
  card("ambient","실내 무드등","조명 색상과 밝기를 내 취향으로");
  card("trip","드라이브 기록","이번 운행의 거리·시간·전비");
  setup();
 }
 private void card(String key,String name,String description){cards.put(key,new Card(key,name,description));}
 private SeatArea area(Card card){return new SeatArea(preferences.getBoolean(card.key+"Right",false)?SeatValue.ROW_1_RIGHT:SeatValue.ROW_1_LEFT);}
 private String areaName(Card card){return preferences.getBoolean(card.key+"Right",false)?"앞좌석 오른쪽":"앞좌석 왼쪽";}
 private interface Probe{void run(int request);}
 private interface Write{void run(double value,int request);}
 private final class Metric {
  final Card card;final String label,unit;final double step;final boolean integral;
  Probe probe,reader;Write write;SeatArea target;Double value;double min,max;boolean readable,writable,busy,known;int request,failures,skip;String state="연결 대기";
  Metric(Card card,String label,String unit,double step,boolean integral){this.card=card;this.label=label;this.unit=unit;this.step=step;this.integral=integral;card.fields.add(this);}
  boolean current(int token){return active&&card.visible&&token==request;}
  // Capability is asked once; afterwards only the value is read. Unsupported items stop polling, and repeated read failures back off up to 2 minutes.
  void forget(){known=false;reader=null;failures=0;skip=0;}
  void deliver(int token,Runnable action){handler.post(()->{if(current(token)){action.run();card.render();}});}
  Function1<Exception,Unit> failure(int token){return e->{deliver(token,()->{if(++failures==1)android.util.Log.w("LiveVehicle",card.key+"/"+label+": "+e.getClass().getSimpleName()+" "+e.getMessage());skip=Math.min(8,failures)-1;busy=false;value=null;writable=false;state=e instanceof SecurityException?"접근 권한 확인 필요":"차량에서 값을 받지 못했어요";});return Unit.INSTANCE;};}
  void refresh(){if(!active||!card.visible||busy||probe==null||known&&!readable)return;busy=true;int token=++request;state="확인 중";card.render();handler.postDelayed(()->{if(current(token)&&busy){busy=false;value=null;writable=false;state="응답 대기 · 눌러서 다시 확인";request++;card.render();}},5000);try{if(reader!=null)reader.run(token);else probe.run(token);}catch(RuntimeException e){failure(token).invoke(e);}}
  Unit capability(int token,CapabilityConfig config,AreaBounds<? extends Number> bounds,Probe read){
   deliver(token,()->{AccessType access=config==null?AccessType.NONE:config.getAccessType();readable=canRead(access);writable=canWrite(access)&&write!=null&&bounds!=null;min=bounds==null||bounds.getMinValue()==null?Double.NaN:bounds.getMinValue().doubleValue();max=bounds==null||bounds.getMaxValue()==null?Double.NaN:bounds.getMaxValue().doubleValue();writable&=Double.isFinite(min)&&Double.isFinite(max)&&min<=max;known=true;reader=readable?read:null;if(!readable){busy=false;value=null;state="이 차량에서 지원하지 않아요";return;}try{read.run(token);}catch(RuntimeException e){failure(token).invoke(e);}});return Unit.INSTANCE;
  }
  Unit accept(int token,Number result){deliver(token,()->{busy=false;failures=0;value=result!=null&&Double.isFinite(result.doubleValue())?result.doubleValue():null;state=value==null?"현재 값 확인 불가":writable?"조절 가능":"조회 전용";});return Unit.INSTANCE;}
  String formatted(){return value==null?"—":integral?String.format(Locale.KOREA,"%.0f%s",value,unit):String.format(Locale.KOREA,"%.1f%s",value,unit);}
  void apply(double target){
   if(!active||!card.visible||busy||!readable||!writable||write==null||!inRange(target,min,max)){AppUi.notice(activity,"차량의 현재 지원 범위와 연결을 다시 확인해주세요");return;}
   busy=true;int token=++request;state="변경 확인 중";card.render();handler.postDelayed(()->{if(current(token)&&busy){busy=false;value=null;state="변경 결과 미확인 · 다시 조회해주세요";request++;card.render();}},5000);
   try{write.run(target,token);}catch(RuntimeException e){failure(token).invoke(e);}
  }
  Unit acknowledged(int token){deliver(token,()->{busy=false;value=null;state="변경 결과 확인 중";refresh();});return Unit.INSTANCE;}
 }
 static SeatArea chooseArea(SeatArea requested,Collection<SeatArea> supported){if(supported!=null){if(supported.contains(requested))return requested;for(SeatArea candidate:supported)if(candidate.getValues().length==0)return candidate;}return requested;}
 static boolean canRead(AccessType access){return access==AccessType.READ||access==AccessType.READ_WRITE;}
 static boolean canWrite(AccessType access){return access==AccessType.WRITE||access==AccessType.READ_WRITE;}
 static boolean inRange(double value,double min,double max){return Double.isFinite(value)&&Double.isFinite(min)&&Double.isFinite(max)&&min<=max&&value>=min&&value<=max;}
 private <T extends Number> Unit bounded(Metric field,int token,CapabilityConfig config,Map<SeatArea,AreaBounds<T>> bounds,Probe read){
  field.target=chooseArea(area(field.card),bounds==null?java.util.Collections.emptySet():bounds.keySet());AreaBounds<T> range=bounds==null?null:bounds.get(field.target);return field.capability(token,range==null?new CapabilityConfig(AccessType.NONE):config,range,read);
 }
 private void setup(){
  Card ambient=cards.get("ambient"),trip=cards.get("trip");
  Metric color=new Metric(ambient,"조명 색상","",1,true);
  color.probe=t->vehicle.getLight().checkAmbientLightBrightnessColorCapabilities((config,areas)->{
   color.target=chooseArea(area(ambient),areas);boolean supported=areas!=null&&areas.contains(color.target);return color.capability(t,supported?config:new CapabilityConfig(AccessType.NONE),new AreaBounds<Integer>(Integer.MIN_VALUE,Integer.MAX_VALUE),r->vehicle.getLight().getAmbientLightBrightnessColor(color.target,n->color.accept(r,n),color.failure(r)));},color.failure(t));
  color.write=(v,t)->vehicle.getLight().setAmbientLightBrightnessColor(color.target,(int)v,(a,n)->color.acknowledged(t),color.failure(t));
  Metric brightness=new Metric(ambient,"밝기","",1,true);
  brightness.probe=t->vehicle.getLight().checkAmbientLightBrightnessCapabilities((config,bounds)->bounded(brightness,t,config,bounds,r->vehicle.getLight().getAmbientLightBrightness(brightness.target,n->brightness.accept(r,n),brightness.failure(r))),brightness.failure(t));
  brightness.write=(v,t)->vehicle.getLight().setAmbientLightBrightness(brightness.target,(int)v,(a,n)->brightness.acknowledged(t),brightness.failure(t));
  Metric distance=new Metric(trip,"이번 운행 거리","km",1,false);
  distance.probe=t->vehicle.getOdometer().checkTripCurrentDrivingDistanceCapabilities(config->distance.capability(t,config,null,r->vehicle.getOdometer().getTripCurrentDrivingDistance(n->distance.accept(r,n!=null&&n>=0?n/1000f:null),distance.failure(r))),distance.failure(t));
  Metric time=new Metric(trip,"이번 운행 시간","분",1,true);
  time.probe=t->vehicle.getOdometer().checkTripCurrentDrivingTimeCapabilities(config->time.capability(t,config,null,r->vehicle.getOdometer().getTripCurrentDrivingTime(n->time.accept(r,n!=null&&n>=0?n:null),time.failure(r))),time.failure(t));
  Metric efficiency=new Metric(trip,"이번 운행 전비"," km/kWh",.1,false);
  efficiency.probe=t->vehicle.getEvBattery().checkCurrentEvEfficiencyCapabilities(config->efficiency.capability(t,config,null,r->vehicle.getEvBattery().getCurrentEvEfficiency(n->efficiency.accept(r,n!=null&&n>=0?n:null),efficiency.failure(r))),efficiency.failure(t));
 }
 void sync(JSONObject theme,boolean foreground){
  this.theme=theme;JSONObject panel=theme.optJSONObject("panel");boolean any=false;for(Card c:cards.values()){boolean visible=panel.optBoolean(c.key,false);if(!visible&&c.visible)for(Metric f:c.fields){f.forget();f.request++;f.busy=false;f.value=null;f.writable=false;f.state="연결 대기";}c.visible=visible;any|=visible;}
  if(foreground&&any){if(!active){active=true;generation++;try{vehicle=new Vehicle(activity);vehicle.initialize();}catch(RuntimeException e){active=false;for(Card c:cards.values())c.status.setText("차량 서비스에 연결할 수 없어요");return;}handler.post(poll);}else for(Card c:cards.values())if(c.visible)for(Metric f:c.fields)if(f.state.equals("연결 대기"))f.refresh();}
  else stop();
 }
 /** A user-confirmed web-theme request; uses the same checked write path as the control form. False when the car does not offer it now. */
 /** Mood-light colour support: true when it can be written, false when the car reported it unsupported, null while still unknown. */
 Boolean ambientSupported(){Card ambient=cards.get("ambient");if(ambient==null)return false;Metric m=ambient.fields.get(0);if(m.readable&&m.writable)return true;return "이 차량에서 지원하지 않아요".equals(m.state)?Boolean.FALSE:null;}
 boolean requestAmbient(String action,double value){
  Card ambient=cards.get("ambient");if(ambient==null||!active||!ambient.visible)return false;Metric m=ambient.fields.get("ambient.color".equals(action)?0:1);
  if(!m.readable||!m.writable||m.busy||m.write==null||!inRange(value,m.min,m.max))return false;m.apply(value);return true;
 }
 void stop(){if(!active&&vehicle==null)return;active=false;generation++;handler.removeCallbacksAndMessages(null);for(Card c:cards.values())for(Metric f:c.fields){f.forget();f.request++;f.busy=false;f.value=null;f.writable=false;f.state="연결 대기";}if(controls!=null){controls.dismiss();controls=null;}if(vehicle!=null){try{vehicle.release();}catch(RuntimeException ignored){}vehicle=null;}}
 final class Card extends LinearLayout {
  final String key,name;final ArrayList<Metric> fields=new ArrayList<>();final TextView value,detail,status;boolean visible;String signature="";
  Card(String key,String name,String description){super(activity);this.key=key;this.name=name;setOrientation(VERTICAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(24),dp(20),dp(24),dp(18));TextView title=label(name,13);title.setLetterSpacing(.08f);value=label("—",38);detail=label(description,16);detail.setMaxLines(2);status=label("눌러서 차량 연결과 설정 확인",12);status.setPadding(0,dp(12),0,0);setContentDescription(name+" 차량 위젯");setOnClickListener(v->showControls(this));}
  TextView label(String text,int size){TextView t=new TextView(activity);t.setText(text);t.setTextSize(size);t.setTextColor(0xffe0eee5);t.setMaxLines(1);t.setEllipsize(TextUtils.TruncateAt.END);addView(t);return t;}
  void render(){
   if(key.equals("ambient")&&ambientState!=null){Double color=fields.get(0).value;ambientState.accept(color==null?null:color.intValue()|0xff000000,fields.get(1).value);}
   String primary="—",secondary="",footer=key.equals("trip")?"이번 운행 · 눌러서 상세 보기":(!fields.isEmpty()&&fields.get(0).target!=null&&fields.get(0).target.getValues().length==0?"차량 공통 영역":areaName(this))+" · 눌러서 조절";
   if(!fields.isEmpty()){Metric first=fields.get(0);primary=key.equals("ambient")?(first.value==null?"—":String.format(Locale.ROOT,"#%06X",first.value.intValue()&0xffffff)):first.formatted();}
   for(int i=1;i<fields.size();i++){Metric f=fields.get(i);if(secondary.length()>0)secondary+="  ·  ";secondary+=f.label+" "+f.formatted();}
   boolean pending=false,available=false;for(Metric f:fields){pending|=f.busy;available|=f.value!=null;}
   if(pending)footer="차량 상태 확인 중…";else if(!available)footer=fields.isEmpty()?"차량 연결 대기":fields.get(0).state;
   if(controlsCard==this)for(Runnable bind:new ArrayList<>(controlBindings))bind.run();String next=primary+secondary+footer;if(next.equals(signature))return;signature=next;value.setText(primary);detail.setText(secondary);status.setText(footer);changed.run();
  }
 }
 private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}

 private void showControls(Card card){
  if(AppUi.driving){AppUi.notice(activity,AppUi.DRIVE_NOTICE);return;}
  if(controls!=null)controls.dismiss();controlsCard=card;controlBindings.clear();
  LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(20),dp(24),dp(16));AppUi.section(body,card.name,card.key.equals("trip")?"현재 운행의 실제 차량 데이터예요.":"값을 고르면 바로 바뀌어요. 실제 결과를 여기서 확인해요.");
  if(!card.key.equals("trip")){LinearLayout areas=new LinearLayout(activity);for(boolean right:new boolean[]{false,true}){Button choose=AppUi.button(activity,right?"앞좌석 오른쪽":"앞좌석 왼쪽",preferences.getBoolean(card.key+"Right",false)==right,null);choose.setSelected(preferences.getBoolean(card.key+"Right",false)==right);choose.setOnClickListener(v->{preferences.edit().putBoolean(card.key+"Right",right).apply();for(Metric f:card.fields){f.forget();f.request++;f.busy=false;f.value=null;f.refresh();}showControls(card);});LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(0,dp(AppUi.TOUCH),1);bounds.rightMargin=dp(6);areas.addView(choose,bounds);}body.addView(areas);}
  ScrollView scroll=new ScrollView(activity);LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  for(Metric f:card.fields){
   LinearLayout group=new LinearLayout(activity);group.setOrientation(LinearLayout.VERTICAL);group.setPadding(dp(16),dp(16),dp(16),dp(16));group.setBackground(AppUi.shape(activity,AppUi.CARD,18,0));LinearLayout.LayoutParams gb=new LinearLayout.LayoutParams(-1,-2);gb.topMargin=dp(12);list.addView(group,gb);
   TextView heading=AppUi.text(activity,"",22,AppUi.INK);heading.setContentDescription(card.name+" "+f.label+" 현재값");group.addView(heading);TextView state=AppUi.text(activity,"",15,AppUi.MUTED);state.setPadding(0,dp(6),0,dp(12));state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);group.addView(state);
   controlBindings.add(()->{heading.setText(f.label+"  "+(card.key.equals("ambient")&&f==card.fields.get(0)?f.value==null?"—":String.format(Locale.ROOT,"#%06X",f.value.intValue()&0xffffff):f.formatted()));state.setText(f.state);});
   if(f.write==null)continue;
   if(card.key.equals("ambient")&&f==card.fields.get(0)){
    LinearLayout colors=new LinearLayout(activity);int accent=Color.parseColor(theme.optJSONObject("panel").optString("accentColor","#A8DCD0"));int[] palette={accent,0xff4db6d8,0xff9876cc,0xffffdfb1};String[] labels={"테마 색","청록","보라","따뜻한 빛"};for(int i=0;i<palette.length;i++){final int target=palette[i];Button b=AppUi.button(activity,labels[i],false,()->f.apply(target));b.setTextSize(14);controlBindings.add(()->{b.setEnabled(f.value!=null&&f.writable&&!f.busy);b.setAlpha(b.isEnabled()?1:.4f);});LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(0,dp(AppUi.TOUCH),1);bounds.rightMargin=dp(4);colors.addView(b,bounds);}group.addView(colors);
   }else{
    TextView target=AppUi.text(activity,"",18,AppUi.INK);SeekBar slider=new SeekBar(activity);slider.setContentDescription(card.name+" "+f.label+" 조절");boolean[] dirty={false};double[] range={Double.NaN,Double.NaN};
    slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}// Sent only when the driver releases a value they moved themselves: one explicit action, one write.
    public void onStopTrackingTouch(SeekBar b){if(dirty[0]&&f.writable&&!f.busy&&f.value!=null){dirty[0]=false;f.apply(f.min+b.getProgress()*f.step);}}public void onProgressChanged(SeekBar b,int n,boolean user){if(user)dirty[0]=true;target.setText("바꿀 값  "+formatTarget(f.min+n*f.step,f));}});
    group.addView(target);group.addView(slider,new LinearLayout.LayoutParams(-1,dp(AppUi.TOUCH)));
    controlBindings.add(()->{boolean supported=f.writable&&Double.isFinite(f.min)&&Double.isFinite(f.max)&&f.min<=f.max;if(range[0]!=f.min||range[1]!=f.max){range[0]=f.min;range[1]=f.max;dirty[0]=false;if(supported)slider.setMax(Math.min(10000,Math.max(0,(int)Math.round((f.max-f.min)/f.step))));}boolean available=supported&&f.value!=null&&!f.busy;slider.setEnabled(available);if(available&&!dirty[0]&&!slider.isPressed())slider.setProgress((int)Math.round((f.value-f.min)/f.step));target.setText(supported?"바꿀 값  "+formatTarget(f.min+slider.getProgress()*f.step,f):"이 항목은 현재 조절할 수 없어요");slider.setVisibility(supported?View.VISIBLE:View.GONE);});
   }
  }
  LinearLayout footer=new LinearLayout(activity);Button refresh=AppUi.button(activity,"다시 확인",false,()->{for(Metric f:card.fields){f.forget();f.request++;f.busy=false;f.refresh();}});footer.addView(refresh,new LinearLayout.LayoutParams(0,dp(AppUi.TOUCH),1));Button close=AppUi.button(activity,"닫기",true,()->controls.dismiss());LinearLayout.LayoutParams closeBounds=new LinearLayout.LayoutParams(0,dp(AppUi.TOUCH),1);closeBounds.leftMargin=dp(8);footer.addView(close,closeBounds);LinearLayout.LayoutParams fb=new LinearLayout.LayoutParams(-1,-2);fb.topMargin=dp(16);body.addView(footer,fb);
  controls=AppUi.dialog(activity).setView(body).create();AlertDialog current=controls;current.setOnDismissListener(v->{if(controls==current){controlsCard=null;controlBindings.clear();controls=null;}});current.setOnShowListener(v->AppUi.prepareDialog(activity,current,body,720,.88f));current.show();for(Runnable bind:new ArrayList<>(controlBindings))bind.run();
 }
 private String formatTarget(double value,Metric field){return String.format(Locale.KOREA,field.integral?"%.0f%s":"%.1f%s",value,field.unit);}
}
