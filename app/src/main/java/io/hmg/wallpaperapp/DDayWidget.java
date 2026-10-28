package io.hmg.wallpaperapp;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.widget.*;
import java.time.*;
import java.time.temporal.ChronoUnit;

final class DDayWidget extends LinearLayout {
 private final SharedPreferences store;private final TextView title,value,date;private final Runnable changed;
 private final Runnable tick=new Runnable(){public void run(){refresh();postDelayed(this,60000);}};
 DDayWidget(Context c,Runnable changed){super(c);this.changed=changed;store=c.getSharedPreferences("dday",Context.MODE_PRIVATE);setOrientation(VERTICAL);setPadding(24,16,24,16);title=text("D-DAY",14);value=text("",30);date=text("",14);setContentDescription("D-day 날짜와 제목 편집");setOnClickListener(v->edit());refresh();}
 private TextView text(String s,int size){TextView v=new TextView(getContext());v.setText(s);v.setTextSize(size);v.setTextColor(0xffe0eee5);v.setMaxLines(1);v.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(v);return v;}
 static String count(LocalDate today,LocalDate target){long n=ChronoUnit.DAYS.between(today,target);return n==0?"D-DAY":n>0?"D-"+n:"D+"+(-n);}
 void refresh(){String raw=store.getString("date","");title.setText(store.getString("title","D-DAY"));try{LocalDate day=LocalDate.parse(raw);value.setText(count(LocalDate.now(),day));date.setText(day.toString());}catch(Exception e){value.setText("기념일을 추가하세요");date.setText("눌러서 제목과 날짜 설정");}changed.run();}
 @Override protected void onMeasure(int w,int h){boolean tall=android.view.View.MeasureSpec.getMode(h)!=android.view.View.MeasureSpec.UNSPECIFIED&&android.view.View.MeasureSpec.getSize(h)>android.view.View.MeasureSpec.getSize(w)*.8f;title.setLetterSpacing(.14f);title.setPadding(0,0,0,Math.round((tall?14:0)*getResources().getDisplayMetrics().density));value.setTextSize(value.getText().length()>10?22:tall?56:30);date.setPadding(0,Math.round((tall?12:0)*getResources().getDisplayMetrics().density),0,0);super.onMeasure(w,h);}
 void edit(){LinearLayout body=new LinearLayout(getContext());body.setOrientation(VERTICAL);EditText name=AppUi.input(getContext(),store.getString("title",""),"여행, 생일, 기념일…",1,40);name.setContentDescription("D-day 제목 입력");body.addView(name);LocalDate[] target={LocalDate.now()};try{target[0]=LocalDate.parse(store.getString("date",""));}catch(Exception ignored){}Button dateButton=AppUi.button(getContext(),target[0].toString(),false,null);dateButton.setContentDescription("D-day 날짜 선택");dateButton.setOnClickListener(v->{LocalDate initial=target[0];new DatePickerDialog(getContext(),(picker,y,m,day)->{target[0]=LocalDate.of(y,m+1,day);dateButton.setText(target[0].toString());},initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();});LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-1,AppUi.dp(getContext(),64));bounds.topMargin=AppUi.dp(getContext(),16);body.addView(dateButton,bounds);AppUi.form((Activity)getContext(),"기념일 설정","제목과 날짜를 함께 확인한 뒤 저장해주세요.",body,"저장",()->{store.edit().putString("title",name.getText().toString().trim().isEmpty()?"D-DAY":name.getText().toString().trim()).putString("date",target[0].toString()).apply();refresh();});
 }
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(tick);post(tick);}
 @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
}
