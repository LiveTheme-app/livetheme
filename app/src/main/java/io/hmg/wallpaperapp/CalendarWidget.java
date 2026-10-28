package io.hmg.wallpaperapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Compact calendar with local day notes; no calendar account or provider permission. */
final class CalendarWidget extends LinearLayout {
 private final SharedPreferences store;
 private final Runnable changed;
 private LocalDate selected;
 private LocalDate lastToday;
 private final TextView title,summary;
 private final LinearLayout days;
 private final Button previous,next,month;
 private boolean monthView;private org.json.JSONObject palette;
 void setPalette(org.json.JSONObject next){String old=palette==null?"":palette.toString();palette=next;if(!old.equals(next.toString()))refresh();}
 private final DateTimeFormatter heading=DateTimeFormatter.ofPattern("M월 d일 EEEE",Locale.KOREA);
 private final Runnable dayTick=new Runnable(){public void run(){LocalDate today=LocalDate.now();if(!today.equals(lastToday)){if(selected.equals(lastToday))selected=today;refresh();}postDelayed(this,60000);}};

 CalendarWidget(Context context,Runnable changed){
  super(context);this.changed=changed;store=context.getSharedPreferences("calendar",Context.MODE_PRIVATE);
  // Always open on today; a day picked in an earlier session is not where a new note belongs.
  selected=LocalDate.now();
  setOrientation(VERTICAL);setPadding(dp(20),dp(16),dp(20),dp(16));
  GradientDrawable background=new GradientDrawable();background.setColor(0xbb142129);background.setCornerRadius(dp(22));background.setStroke(dp(1),0x24ffffff);setBackground(background);
  LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout words=new LinearLayout(context);words.setOrientation(VERTICAL);
  words.addView(text("CALENDAR",11,0xffb7c9ce));title=text("",21,Color.WHITE);words.addView(title);
  header.addView(words,new LinearLayout.LayoutParams(0,-2,1));
  previous=button("‹");previous.setContentDescription("위젯 이전 달");previous.setPadding(0,0,0,0);previous.setOnClickListener(v->changeMonth(-1));header.addView(previous,new LinearLayout.LayoutParams(dp(76),dp(AppUi.TOUCH)));
  next=button("›");next.setContentDescription("위젯 다음 달");next.setPadding(0,0,0,0);next.setOnClickListener(v->changeMonth(1));header.addView(next,new LinearLayout.LayoutParams(dp(76),dp(AppUi.TOUCH)));
  month=button("월간");month.setContentDescription("월간 캘린더 열기");month.setOnClickListener(v->openEditor());header.addView(month,new LinearLayout.LayoutParams(dp(88),dp(AppUi.TOUCH)));addView(header);
  days=new LinearLayout(context);days.setOrientation(VERTICAL);LinearLayout.LayoutParams weekBounds=new LinearLayout.LayoutParams(-1,0,1);weekBounds.setMargins(0,dp(12),0,dp(12));addView(days,weekBounds);
  summary=text("",16,0xffe0eee5);summary.setMinHeight(dp(48));summary.setMaxLines(2);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
  summary.setGravity(Gravity.CENTER_VERTICAL);summary.setContentDescription("선택한 날짜의 일정 편집");summary.setOnClickListener(v->openEditor());addView(summary);
  setContentDescription("캘린더 위젯");refresh();
 }
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView text(String value,int size,int color){TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);WidgetDesign.textTheme(t,palette);return t;}
 private Button button(String value){Button b=new Button(getContext());b.setText(value);b.setTextSize(14);b.setTextColor(0xffe8f3f0);GradientDrawable pill=new GradientDrawable();pill.setColor(0x403e7775);pill.setCornerRadius(dp(14));b.setBackground(pill);return b;}
 private String event(LocalDate day){return store.getString("event_"+day,"");}
 boolean isMonthView(){return monthView;}
 void prepareSize(int width,int height){boolean expanded=width>=dp(400)&&height>=dp(480);if(monthView!=expanded){monthView=expanded;refresh();}}
 private void changeMonth(int delta){LocalDate day=selected.plusMonths(delta);if(day.getYear()<1900||day.getYear()>2100)return;select(day);}
 private void select(LocalDate day){selected=day;refresh();changed.run();}
 private void refresh(){
  lastToday=LocalDate.now();title.setText(monthView?selected.format(DateTimeFormatter.ofPattern("yyyy년 M월",Locale.KOREA)):selected.format(heading));days.removeAllViews();
  previous.setVisibility(monthView?VISIBLE:GONE);next.setVisibility(monthView?VISIBLE:GONE);month.setText(monthView?"일정":"월간");
  previous.setEnabled(selected.getYear()>1900||selected.getMonthValue()>1);next.setEnabled(selected.getYear()<2100||selected.getMonthValue()<12);
  LocalDate first=selected.withDayOfMonth(1);int offset=first.getDayOfWeek().getValue()%7;
  LocalDate start=monthView?first.minusDays(offset):selected.minusDays(selected.getDayOfWeek().getValue()%7);
  String[] weekday={"일","월","화","수","목","금","토"};
  if(monthView){LinearLayout labels=new LinearLayout(getContext());for(String label:weekday){TextView cell=text(label,13,0xffb7c9ce);cell.setGravity(Gravity.CENTER);labels.addView(cell,new LinearLayout.LayoutParams(0,-1,1));}days.addView(labels,new LinearLayout.LayoutParams(-1,dp(24)));}
  int rows=monthView?(offset+first.lengthOfMonth()+6)/7:1;
  for(int row=0;row<rows;row++){
   LinearLayout week=new LinearLayout(getContext());days.addView(week,new LinearLayout.LayoutParams(-1,0,1));
   for(int column=0;column<7;column++){
   final LocalDate day=start.plusDays(row*7+column);
   if(monthView&&!YearMonth.from(day).equals(YearMonth.from(selected))){week.addView(new TextView(getContext()),new LinearLayout.LayoutParams(0,-1,1));continue;}
   boolean hasEvent=!event(day).isEmpty();
   TextView cell=text((monthView?"":weekday[column]+"\n")+day.getDayOfMonth()+(hasEvent?" •":""),16,column==0?0xffe8aaa8:column==6?0xffa8cce8:0xffe7efee);
   cell.setTag("calendar-day");cell.setTextColor(Color.parseColor(palette==null?"#E7EFEE":palette.optString("textColor","#E7EFEE")));cell.setGravity(Gravity.CENTER);cell.setContentDescription(day+" 날짜 선택"+(hasEvent?", 일정 있음":""));
   GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(12));bg.setColor(Color.TRANSPARENT);
   if(day.equals(lastToday)||day.equals(selected))bg.setStroke(dp(1),palette==null?0xffaad9ce:Color.parseColor(palette.optString("accentColor","#A8DCD0")));cell.setBackground(bg);
   if(day.getYear()>=1900&&day.getYear()<=2100)cell.setOnClickListener(v->select(day));
   else{cell.setEnabled(false);cell.setAlpha(.35f);}
   LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(0,-1,1);bounds.setMargins(dp(1),dp(1),dp(1),dp(1));week.addView(cell,bounds);
   }
  }
  String note=event(selected);summary.setText((monthView?selected.format(DateTimeFormatter.ofPattern("M/d"))+" · ":"")+(note.isEmpty()?"일정 추가하기  ＋":note));
 }
 void openEditor(){
  Context context=getContext();ScrollView scroll=new ScrollView(context);LinearLayout body=new LinearLayout(context);body.setOrientation(VERTICAL);body.setPadding(dp(16),dp(8),dp(16),dp(8));scroll.addView(body);
  CalendarView calendar=new CalendarView(context);calendar.setFirstDayOfWeek(Calendar.SUNDAY);calendar.setMinDate(epoch(LocalDate.of(1900,1,1)));calendar.setMaxDate(epoch(LocalDate.of(2100,12,31)));calendar.setDate(epoch(selected),false,true);body.addView(calendar,new LinearLayout.LayoutParams(-1,dp(350)));
  LocalDate[] editing={selected};Map<LocalDate,String> drafts=new LinkedHashMap<>();
  TextView selectedLabel=text(selected.format(heading),20,Color.WHITE);body.addView(selectedLabel);
  EditText note=AppUi.input(context,event(selected),"이 날짜에 할 일을 적어주세요",2,280);note.setContentDescription("선택한 날짜의 일정 내용");
  note.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(280)});body.addView(note,new LinearLayout.LayoutParams(-1,-2));
  boolean[] changing={false};calendar.setOnDateChangeListener((view,year,month,day)->{
   if(changing[0])return;
   drafts.put(editing[0],note.getText().toString());editing[0]=LocalDate.of(year,month+1,day);selectedLabel.setText(editing[0].format(heading));
   note.setText(drafts.containsKey(editing[0])?drafts.get(editing[0]):event(editing[0]));
  });
  Button today=AppUi.button(context,"오늘로 이동",false,null);today.setOnClickListener(v->{
   drafts.put(editing[0],note.getText().toString());editing[0]=LocalDate.now();changing[0]=true;calendar.setDate(epoch(editing[0]),false,true);changing[0]=false;
   selectedLabel.setText(editing[0].format(heading));note.setText(drafts.containsKey(editing[0])?drafts.get(editing[0]):event(editing[0]));
  });body.addView(today);
  body.addView(text("일정을 비우고 저장하면 삭제됩니다. 취소하면 수정 전 상태를 유지해요.",12,0xffb7c9ce));
  scroll.removeView(body);AppUi.form((android.app.Activity)context,"캘린더 일정","날짜를 고르고 일정을 적어주세요. 취소하면 수정 전 내용을 유지해요.",body,"저장",()->{
    drafts.put(editing[0],note.getText().toString());SharedPreferences.Editor write=store.edit();
    for(Map.Entry<LocalDate,String> draft:drafts.entrySet()){String value=draft.getValue().trim();if(value.isEmpty())write.remove("event_"+draft.getKey());else write.putString("event_"+draft.getKey(),value);}
    selected=editing[0];write.apply();refresh();changed.run();
  });
 }
 private long epoch(LocalDate day){return day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(dayTick);post(dayTick);}
 @Override protected void onDetachedFromWindow(){removeCallbacks(dayTick);super.onDetachedFromWindow();}
}
