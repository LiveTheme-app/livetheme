package io.hmg.wallpaperapp;
import android.content.Context;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import org.json.JSONObject;

/** The widget catalog (1.38): real-looking previews in the current theme, one tap adds, placed widgets are marked. Long press starts dragging; vertical swipes browse. */
final class WidgetShelf extends LinearLayout {
 interface Actions{boolean visible(String key);void add(String key);void remove(String key);default void restore(String key){add(key);}void drag(DragItem item,View view);void close();void arrange();
  /** The applied theme, so previews use its plate colour, text colours and corners. Null draws the default look. */
  default JSONObject theme(){return null;}}
 static final class DragItem{final String key;DragItem(String key){this.key=key;}}
 static final String[] KEYS={"clock","calendar","music","memo","dday","weather","battery","ambient","trip","navigation"};
 /** Retired widgets (1.35 seat, 1.37 climate). Old themes and saved layouts may still name them: they are skipped, and their prefs stay on disk unread. */
 static final java.util.List<String> RETIRED=java.util.Arrays.asList("seat","climate");
 private static final String[] NAMES={"시계","캘린더","음악","메모","D-day","날씨·강수","차량 배터리","실내 무드등","드라이브 기록","내비 연동"};
 private static final String[] HINTS={"clock 시간 날짜","calendar 달력 일정","music 노래 재생","memo 노트 기록","dday 기념일 디데이","weather 비 강수 기온 차량 위치","battery 충전 배터리 전기차 EV","ambient 실내 조명 무드등 색상 밝기","trip 주행 운행 거리 시간 전비","navigation 내비 네비 목적지 지도 검색 충전소"};
 /** Catalog groups shown as segments (1.38). 일상 / 추가 가능 stay valid filters for older callers. */
 static final String[] CATEGORIES={"전체","시간","음악","기록","차량","사용 중"};
 private static final String[] GROUP={"시간","시간","음악","기록","시간","차량","차량","차량","기록","차량"};
 /** The default footprint on the board, shown on each card. */
 private static final String[] SIZES={"넓게","크게","넓게","보통","작게","보통","보통","보통","보통","넓게"};
 static String nameOf(String key){for(int i=0;i<KEYS.length;i++)if(KEYS[i].equals(key))return NAMES[i];return key;}
 static String sample(String key){switch(key){case "ambient":return "무드등 켜짐";case "trip":return "32.4 km";case "navigation":return "나의 목적지";default:return "—";}}
 static boolean vehicleKey(String key){return key.equals("weather")||key.equals("battery")||java.util.Arrays.asList(VehicleWidgets.KEYS).contains(key)||key.equals("navigation");}

 private final Actions actions;private final LinearLayout grid,undo;private final TextView count;private final EditText search;private final ScrollView scroll;private final Button clear;
 private final java.util.ArrayList<Button> filters=new java.util.ArrayList<>();private String category="전체",removed;private int columns=2,cardWidth;
 WidgetShelf(Context c,Actions actions){
  super(c);this.actions=actions;scroll=new ScrollView(c);setOrientation(VERTICAL);setPadding(dp(AppUi.S5),dp(AppUi.S5),dp(AppUi.S5),dp(AppUi.S4));setElevation(dp(24));setBackground(AppUi.shape(c,AppUi.GLASS_SOLID,AppUi.R_SHEET,AppUi.EDGE));
  LinearLayout header=AppUi.header(c,"위젯","",actions::close);count=AppUi.text(c,"",AppUi.T_CAPTION,AppUi.MUTED);((LinearLayout)header.getChildAt(0)).addView(count);
  header.getChildAt(header.getChildCount()-1).setContentDescription("위젯 보관함 닫기");addView(header);
  // search: magnifier inside the field, ✕ appears only while there is text
  FrameLayout searchBox=new FrameLayout(c);search=new EditText(c);search.setSingleLine(true);search.setTextSize(AppUi.T_CALLOUT+1);search.setTextColor(AppUi.INK);search.setHintTextColor(AppUi.FAINT);search.setHint("위젯 이름으로 찾기");search.setContentDescription("위젯 검색");search.setPadding(dp(56),0,dp(84),0);search.setBackground(AppUi.shape(c,AppUi.FILL,AppUi.R_CONTROL,0));search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);AppUi.inline(search);search.setInputType(InputType.TYPE_CLASS_TEXT);search.setOnEditorActionListener((v,id,event)->{hideKeyboard();search.clearFocus();return true;});
  int glass=AppUi.res(c,"ic_search");if(glass!=0){android.graphics.drawable.Drawable d=c.getDrawable(glass);d.setAlpha(150);int s=dp(24);d.setBounds(0,0,s,s);search.setCompoundDrawablesRelative(d,null,null,null);search.setCompoundDrawablePadding(dp(12));search.setPadding(dp(18),0,dp(84),0);}
  searchBox.addView(search,new FrameLayout.LayoutParams(-1,dp(AppUi.TOUCH)));clear=AppUi.iconButton(c,"ic_close","위젯 검색 지우기",()->search.setText(""));clear.setBackground(null);clear.setVisibility(GONE);searchBox.addView(clear,new FrameLayout.LayoutParams(dp(AppUi.TOUCH),dp(AppUi.TOUCH),Gravity.END|Gravity.CENTER_VERTICAL));
  LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(-1,-2);sb.setMargins(0,dp(AppUi.S4),0,dp(AppUi.S3));addView(searchBox,sb);
  LinearLayout tabs=AppUi.segments(c);for(String name:CATEGORIES){Button b=AppUi.addSegment(tabs,name,null);b.setOnClickListener(v->{hideKeyboard();search.clearFocus();category=name;scroll.scrollTo(0,0);render();});filters.add(b);}LinearLayout.LayoutParams tb=new LinearLayout.LayoutParams(-1,-2);tb.bottomMargin=dp(AppUi.S4);addView(tabs,tb);
  scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);grid=new LinearLayout(c);grid.setOrientation(VERTICAL);grid.setPadding(0,0,0,dp(AppUi.S2));scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  undo=new LinearLayout(c);undo.setGravity(Gravity.CENTER_VERTICAL);undo.setPadding(dp(AppUi.S4),0,dp(AppUi.S1),0);undo.setBackground(AppUi.shape(c,AppUi.CARD,AppUi.R_CONTROL,0));undo.setVisibility(GONE);LinearLayout.LayoutParams ub=new LinearLayout.LayoutParams(-1,dp(AppUi.TOUCH));ub.topMargin=dp(AppUi.S2);addView(undo,ub);
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int n,int after){}public void onTextChanged(CharSequence s,int a,int before,int n){clear.setVisibility(s.length()>0?VISIBLE:GONE);scroll.scrollTo(0,0);render();}public void afterTextChanged(Editable e){}});setFocusableInTouchMode(true);requestFocus();render();
 }
 private void hideKeyboard(){((android.view.inputmethod.InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);}
 private int dp(int n){return AppUi.dp(getContext(),n);}
 private TextView text(String value,int size,int color){return AppUi.text(getContext(),value,size,color);}
 static boolean matches(int i,String query,String category,boolean visible){String q=query.trim().toLowerCase(Locale.ROOT);boolean include=category.equals("사용 중")?visible:category.equals("추가 가능")?!visible:category.equals("일상")?!vehicleKey(KEYS[i]):category.equals("전체")?true:GROUP[i].equals(category);return include&&(q.isEmpty()||(NAMES[i]+" "+KEYS[i]+" "+HINTS[i]).toLowerCase(Locale.ROOT).contains(q));}
 @Override protected void onMeasure(int ws,int hs){int w=Math.min(MeasureSpec.getSize(ws),dp(1180)),inner=w-getPaddingLeft()-getPaddingRight(),n=inner<dp(520)?1:inner<dp(980)?2:3,cw=(inner-dp(AppUi.S4)*(n-1))/n;if(n!=columns||cw!=cardWidth){columns=n;cardWidth=cw;render();}super.onMeasure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),hs);}
 private void render(){
  if(grid==null)return;int offset=scroll.getScrollY();grid.removeAllViews();for(Button b:filters)AppUi.segment(b,b.getText().toString().equals(category));
  int total=0,used=0;LinearLayout row=null;for(int i=0;i<KEYS.length;i++){boolean visible=actions.visible(KEYS[i]);if(visible)used++;if(!matches(i,search.getText().toString(),category,visible))continue;if(total%columns==0){row=new LinearLayout(getContext());LinearLayout.LayoutParams rb=new LinearLayout.LayoutParams(-1,-2);rb.bottomMargin=dp(AppUi.S4);grid.addView(row,rb);}LinearLayout.LayoutParams cb=new LinearLayout.LayoutParams(Math.max(1,cardWidth),-2);if(total%columns>0)cb.leftMargin=dp(AppUi.S4);row.addView(card(i,visible),cb);total++;}
  count.setText(KEYS.length+"개 중 "+used+"개 사용 중");
  if(total==0)grid.addView(AppUi.empty(getContext(),"찾는 위젯이 없어요","다른 이름으로 찾거나 ‘전체’를 눌러 보세요"),new LinearLayout.LayoutParams(-1,-2));scroll.post(()->scroll.scrollTo(0,offset));
 }
 private void hide(String key){actions.remove(key);removed=key;undo.removeAllViews();TextView message=text(nameOf(key)+" 위젯을 숨겼어요",AppUi.T_CALLOUT,AppUi.INK);message.setMaxLines(1);undo.addView(message,new LinearLayout.LayoutParams(0,-2,1));Button restore=AppUi.plain(getContext(),"되돌리기",()->{if(removed!=null){actions.restore(removed);removed=null;undo.setVisibility(GONE);render();}});undo.addView(restore,new LinearLayout.LayoutParams(-2,dp(AppUi.TOUCH)));undo.setVisibility(VISIBLE);render();}
 private View card(int i,boolean visible){
  String key=KEYS[i],name=NAMES[i];Context c=getContext();LinearLayout card=new LinearLayout(c);card.setOrientation(VERTICAL);card.setPadding(dp(AppUi.S3),dp(AppUi.S3),dp(AppUi.S3),dp(AppUi.S3));card.setBackground(AppUi.card(c,visible));
  FrameLayout sample=new FrameLayout(c);sample.setBackground(AppUi.shape(c,0xff101318,AppUi.R_CARD-6,0));sample.setClipToOutline(true);sample.addView(new Preview(c,i,actions.theme()),new FrameLayout.LayoutParams(-1,-1));
  if(visible){TextView badge=AppUi.badge(c,"배치됨",AppUi.ACCENT);int check=AppUi.res(c,"ic_check");if(check!=0){android.graphics.drawable.Drawable d=c.getDrawable(check);int s=dp(16);d.setBounds(0,0,s,s);badge.setCompoundDrawablesRelative(d,null,null,null);badge.setCompoundDrawablePadding(dp(6));}FrameLayout.LayoutParams b=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);b.setMargins(0,dp(AppUi.S2),dp(AppUi.S2),0);sample.addView(badge,b);}
  card.addView(sample,new LinearLayout.LayoutParams(-1,dp(150)));
  LinearLayout titleRow=new LinearLayout(c);titleRow.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(name,AppUi.T_HEADLINE-2,AppUi.INK);title.setTypeface(AppUi.SEMIBOLD);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView size=text(SIZES[i],AppUi.T_BADGE,AppUi.MUTED);size.setPadding(dp(8),dp(3),dp(8),dp(3));size.setBackground(AppUi.shape(c,AppUi.FILL,8,0));titleRow.addView(size);LinearLayout.LayoutParams tr=new LinearLayout.LayoutParams(-1,-2);tr.setMargins(dp(AppUi.S1),dp(AppUi.S3),dp(AppUi.S1),dp(AppUi.S3));card.addView(titleRow,tr);
  LinearLayout controls=new LinearLayout(c);
  if(visible){Button edit=AppUi.button(c,"편집",false,()->{hideKeyboard();actions.add(key);});edit.setContentDescription(name+" 위젯 편집");controls.addView(edit,new LinearLayout.LayoutParams(0,dp(AppUi.TOUCH),1));Button remove=AppUi.button(c,"숨기기",false,()->hide(key));remove.setTextColor(AppUi.MUTED);remove.setContentDescription(name+" 위젯 숨기기");LinearLayout.LayoutParams rb=new LinearLayout.LayoutParams(0,dp(AppUi.TOUCH),1);rb.leftMargin=dp(AppUi.S2);controls.addView(remove,rb);}
  else{Button add=AppUi.button(c,"추가",false,()->{hideKeyboard();actions.add(key);});add.setTextColor(AppUi.ACCENT);add.setBackground(AppUi.shape(c,AppUi.ACCENT_SOFT,AppUi.R_CONTROL,0));AppUi.pressable(add);int plus=AppUi.res(c,"ic_plus");if(plus!=0){android.graphics.drawable.Drawable d=c.getDrawable(plus).mutate();d.setTint(AppUi.ACCENT);int s=dp(22);d.setBounds(0,0,s,s);add.setCompoundDrawablesRelative(d,null,null,null);add.setCompoundDrawablePadding(dp(8));}add.setContentDescription(name+" 위젯 추가");controls.addView(add,new LinearLayout.LayoutParams(-1,dp(AppUi.TOUCH)));}
  card.addView(controls);
  card.setContentDescription(name+" 위젯 끌어 추가");card.setFocusable(true);AppUi.pressable(card);card.setOnClickListener(v->{hideKeyboard();actions.add(key);});card.setOnLongClickListener(v->{hideKeyboard();actions.drag(new DragItem(key),card);return true;});return card;
 }
 /** A miniature of the widget as it looks on the board: the theme's plate (colour, opacity, corner) on a dark backdrop, with its text colours and a typical value. */
 private static final class Preview extends View {
  private final int kind;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private int plate=0x99142129,ink=0xffe0eee5,muted=0xffaac8c4,accent=0xffa8dcd0;private float corner=22;
  Preview(Context c,int kind,JSONObject theme){super(c);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
   if(theme!=null)try{JSONObject panel=WidgetDesign.panelFor(theme,KEYS[kind]);int opacity=Math.max(35,Math.min(95,panel.optInt("opacity",60)));plate=WidgetDesign.alpha(Color.parseColor(panel.optString("color","#142129")),Math.round(opacity*2.55f));ink=Color.parseColor(panel.optString("textColor","#E0EEE5"));muted=Color.parseColor(panel.optString("mutedColor","#AAC8C4"));accent=Color.parseColor(panel.optString("accentColor","#A8DCD0"));corner=(float)panel.optDouble("corner",22);}catch(RuntimeException ignored){}}
  private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(s,x,y,p);}
  private void rect(Canvas c,float x,float y,float w,float h,float r,int color){p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,r,r,p);}
  @Override protected void onDraw(Canvas c){super.onDraw(c);
   // backdrop: a soft dusk gradient, like a wallpaper behind the glass plate
   p.setShader(new LinearGradient(0,0,getWidth(),getHeight(),0xff1d2a3a,0xff3a2f45,Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),getHeight(),p);p.setShader(null);
   float pw=kind==4?190:kind==1?300:290,ph=kind==1?132:kind==4?120:110;float s=Math.min(getWidth()*.86f/pw,getHeight()*.84f/ph);c.save();c.translate((getWidth()-pw*s)/2,(getHeight()-ph*s)/2);c.scale(s,s);
   rect(c,0,0,pw,ph,Math.min(28,corner*.8f),plate);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x24ffffff);c.drawRoundRect(.5f,.5f,pw-.5f,ph-.5f,Math.min(28,corner*.8f),Math.min(28,corner*.8f),p);p.setStyle(Paint.Style.FILL);
   String key=KEYS[kind];
   switch(key){
    case "clock":text(c,java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),20,70,58,ink,false);text(c,java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 E요일",Locale.KOREAN)),22,96,15,muted,false);break;
    case "calendar":text(c,java.time.LocalDate.now().getMonthValue()+"월",16,26,15,muted,false);String[] days={"일","월","화","수","목","금","토"};java.time.LocalDate now=java.time.LocalDate.now(),start=now.minusDays(now.getDayOfWeek().getValue()%7);int today=now.getDayOfMonth();for(int i=0;i<7;i++){text(c,days[i],18+i*40,50,11,muted,false);for(int r=0;r<2;r++){int n=start.plusDays(i+r*7).getDayOfMonth();if(n==today&&r==0){rect(c,10+i*40,58+r*30,30,24,8,accent);text(c,""+n,16+i*40,76+r*30,14,0xff10201e,true);}else text(c,""+n,16+i*40,76+r*30,14,ink,false);}}break;
    case "music":rect(c,16,18,74,74,14,WidgetDesign.blend(accent,0xff000000,.45f));text(c,"♫",38,68,34,ink,false);text(c,"지금 듣는 곡",104,44,18,ink,true);text(c,"나의 플레이리스트",104,68,13,muted,false);rect(c,104,84,168,4,2,0x40ffffff);rect(c,104,84,80,4,2,accent);break;
    case "memo":text(c,"MY NOTE",18,30,11,accent,true);text(c,"잠시 쉬어가도 괜찮아",18,64,19,ink,false);rect(c,18,80,200,2,1,0x33ffffff);break;
    case "dday":text(c,"D-DAY",18,30,11,accent,true);text(c,"D-7",18,86,46,ink,true);break;
    case "weather":p.setColor(0xffffd27a);c.drawCircle(42,46,16,p);text(c,"18°",72,62,38,ink,false);text(c,"맑음 · 비 소식 없음",20,94,13,muted,false);break;
    case "battery":text(c,"EV BATTERY",18,30,11,accent,true);text(c,"72%",18,74,38,ink,true);rect(c,18,88,250,8,4,0x33ffffff);rect(c,18,88,180,8,4,accent);break;
    case "ambient":p.setColor(accent);c.drawCircle(44,56,22,p);p.setColor(0x33ffffff);c.drawCircle(44,56,30,p);text(c,"무드등 켜짐",86,52,19,ink,true);text(c,"밝기 40",86,76,13,muted,false);break;
    case "trip":text(c,"이번 운행",18,30,11,accent,true);text(c,"32.4 km",18,72,32,ink,true);text(c,"48분 · 5.2 km/kWh",18,96,13,muted,false);break;
    default:text(c,"내비 연동",18,30,11,accent,true);text(c,"나의 목적지",18,68,24,ink,true);text(c,"기본 내비에서 열려요",18,94,13,muted,false);break;
   }c.restore();
  }
 }
 static View.DragShadowBuilder shadow(View view){return new View.DragShadowBuilder(view);}
}
