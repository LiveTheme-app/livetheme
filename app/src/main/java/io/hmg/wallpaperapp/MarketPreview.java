package io.hmg.wallpaperapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.JSONObject;

/** Theme market MOCKUP for the business pitch: banner, tabs, creator cards, premium badges, a brand-collab slot and a detail sheet.
 *  Every creator, price and download count below is an invented example; only our own built-in backgrounds are used (no Workshop originals).
 *  Nothing here is bought or installed, and it never touches the real local community list. */
final class MarketPreview {
 static final String MOCK="시안 · 가상 예시 값";
 static final class Item{final String name,creator,note;final int background,price,downloads;final boolean fresh;final String accent;final String[] reacts;
  Item(String name,String creator,int background,String accent,int price,int downloads,boolean fresh,String note,String... reacts){this.name=name;this.creator=creator;this.background=background;this.accent=accent;this.price=price;this.downloads=downloads;this.fresh=fresh;this.note=note;this.reacts=reacts;}}
 /** Example catalogue. background = index of the three built-in scenes made for this project (달빛 연못, 수채화 빗속 창가, 수채화 노을 바다). */
 static final Item[] ITEMS={
  new Item("달빛 연못 · 충전 빛","예시 제작자 A",0,"#7FE3C4",0,12800,false,"충전량이 오를수록 연못 아래에서 빛이 번져요.","battery","night"),
  new Item("빗속 창가 · 재즈","예시 제작자 B",1,"#F2B880",2900,8400,false,"음악이 재생되면 빗방울이 빨라지고 앨범 색이 창에 스며요.","music","mood"),
  new Item("노을 바다 · 드라이브","예시 제작자 C",2,"#FF9E7A",3900,15200,true,"무드등 색이 노을 빛과 함께 바뀌어요.","mood","night"),
  new Item("한밤 연못","예시 제작자 A",0,"#8FA8FF",0,5100,true,"밤이 되면 차분하게 어두워지는 기본형 테마예요.","night"),
  new Item("창가의 오후","예시 제작자 D",1,"#C9B6FF",1900,2700,true,"메모와 일정이 잘 보이는 정돈된 배치예요.","music"),
  new Item("바다 위 배터리","예시 제작자 C",2,"#7CC7FF",0,9600,false,"배터리 링이 수평선 위에 떠 있어요.","battery","mood"),
 };
 static final String[][] CREATORS={{"예시 제작자 A","테마 2개 · 팔로워 1.2만"},{"예시 제작자 B","테마 1개 · 팔로워 3,400"},{"예시 제작자 C","테마 2개 · 팔로워 8,900"}};
 static final String[] TABS={"인기","신작","무료","프리미엄"};
 /** Tab filter shared with the checks. */
 static List<Item> filter(int tab){List<Item> out=new ArrayList<>();for(Item i:ITEMS)if(tab==0||tab==1&&i.fresh||tab==2&&i.price==0||tab==3&&i.price>0)out.add(i);if(tab==0)out.sort((a,b)->Integer.compare(b.downloads,a.downloads));return out;}
 static String price(Item i){return i.price==0?"무료":String.format(Locale.KOREA,"₩%,d",i.price);}
 static String downloads(Item i){return i.downloads>=10000?String.format(Locale.KOREA,"%.1f만",i.downloads/10000f):String.format(Locale.KOREA,"%,d",i.downloads);}
 static JSONObject theme(Item i){try{JSONObject t=CreatorTheme.defaults();t.put("name",i.name).put("author",i.creator);t.getJSONObject("background").put("type","builtin").put("index",i.background);t.getJSONObject("panel").put("accentColor",i.accent);return t;}catch(Exception e){throw new IllegalStateException(e);}}

 private final Activity a;private final LinearLayout list;private final Button[] tabs=new Button[TABS.length];private final int columns;
 static void show(Activity a){if(AppUi.driving){AppUi.notice(a,AppUi.DRIVE_NOTICE);return;}new MarketPreview(a);}
 private int dp(int v){return AppUi.dp(a,v);}
 private MarketPreview(Activity a){
  this.a=a;int widthDp=Math.round(a.getWindow().getDecorView().getWidth()/a.getResources().getDisplayMetrics().density);columns=widthDp>=1500?3:widthDp>=1000?2:1;
  LinearLayout shell=new LinearLayout(a);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(28),dp(24),dp(28),dp(20));
  LinearLayout head=new LinearLayout(a);head.setGravity(Gravity.CENTER_VERTICAL);TextView title=AppUi.text(a,"테마 마켓",30,AppUi.INK);title.setTypeface(AppUi.SEMIBOLD);head.addView(title);
  TextView chip=AppUi.text(a,MOCK,14,0xff1c1c1e);chip.setTypeface(AppUi.SEMIBOLD);chip.setPadding(dp(12),dp(6),dp(12),dp(6));chip.setBackground(AppUi.shape(a,0xffff9f0a,12,0));LinearLayout.LayoutParams cb=new LinearLayout.LayoutParams(-2,-2);cb.leftMargin=dp(16);head.addView(chip,cb);
  head.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));AlertDialog[] dialog={null};Button close=AppUi.iconButton(a,"ic_close","닫기",()->dialog[0].dismiss());head.addView(close,new LinearLayout.LayoutParams(dp(AppUi.TOUCH),dp(AppUi.TOUCH)));shell.addView(head);
  ScrollView scroll=new ScrollView(a);LinearLayout page=new LinearLayout(a);page.setOrientation(LinearLayout.VERTICAL);scroll.addView(page);LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(-1,0,1);sb.topMargin=dp(16);shell.addView(scroll,sb);
  page.addView(banner(ITEMS[2]));
  LinearLayout nav=AppUi.segments(a);for(int i=0;i<TABS.length;i++){final int n=i;tabs[i]=AppUi.addSegment(nav,TABS[i],()->select(n));}LinearLayout.LayoutParams nb=new LinearLayout.LayoutParams(-1,-2);nb.topMargin=dp(24);page.addView(nav,nb);
  list=new LinearLayout(a);list.setOrientation(LinearLayout.VERTICAL);page.addView(list);select(0);
  section(page,"인기 제작자");LinearLayout people=new LinearLayout(a);for(int i=0;i<CREATORS.length;i++){LinearLayout.LayoutParams pb=new LinearLayout.LayoutParams(0,-2,1);if(i>0)pb.leftMargin=dp(16);people.addView(creator(CREATORS[i][0],CREATORS[i][1]),pb);}page.addView(people);
  section(page,"브랜드·차종 협업");LinearLayout collab=new LinearLayout(a);String[][] slots={{"브랜드 협업 테마 자리","예: 신차 출시 기념 한정 테마"},{"차종 전용 테마 자리","예: 차종별 실내 무드등과 맞춘 테마"}};for(int i=0;i<2;i++){TextView slot=AppUi.text(a,slots[i][0]+"\n"+slots[i][1],17,AppUi.MUTED);slot.setGravity(Gravity.CENTER);slot.setMinHeight(dp(150));android.graphics.drawable.GradientDrawable dash=AppUi.shape(a,0x14ffffff,24,0);dash.setStroke(dp(2),0x55ffffff,dp(10),dp(8));slot.setBackground(dash);LinearLayout.LayoutParams lb=new LinearLayout.LayoutParams(0,-2,1);if(i>0)lb.leftMargin=dp(16);collab.addView(slot,lb);}page.addView(collab);
  TextView foot=AppUi.text(a,"이 화면은 사업 설명용 시안이에요. 제작자·가격·다운로드 수는 가상의 예시이고, 결제 기능은 없어요. 배경은 라이브테마 자체 제작 기본 배경만 썼어요.",14,AppUi.MUTED);foot.setPadding(0,dp(24),0,dp(8));page.addView(foot);
  dialog[0]=AppUi.dialog(a).setView(shell).create();dialog[0].setOnShowListener(v->AppUi.prepareDialog(a,dialog[0],shell,1400,.94f));dialog[0].show();
 }
 private void section(LinearLayout page,String title){TextView t=AppUi.text(a,title,22,AppUi.INK);t.setTypeface(AppUi.SEMIBOLD);t.setPadding(0,dp(28),0,dp(12));page.addView(t);}
 private void select(int tab){for(int i=0;i<tabs.length;i++)AppUi.segment(tabs[i],i==tab);list.removeAllViews();List<Item> items=filter(tab);LinearLayout row=null;for(int i=0;i<items.size();i++){if(i%columns==0){row=new LinearLayout(a);LinearLayout.LayoutParams rb=new LinearLayout.LayoutParams(-1,-2);rb.topMargin=dp(16);list.addView(row,rb);}LinearLayout.LayoutParams cb=new LinearLayout.LayoutParams(0,-2,1);if(i%columns>0)cb.leftMargin=dp(16);row.addView(card(items.get(i)),cb);}if(row!=null)for(int k=items.size()%columns;k>0&&k<columns;k++){LinearLayout.LayoutParams fill=new LinearLayout.LayoutParams(0,1,1);fill.leftMargin=dp(16);row.addView(new View(a),fill);}}
 private View preview(Item i){FrameLayout f=new FrameLayout(a);f.setBackground(AppUi.shape(a,0xff101217,18,0));f.setClipToOutline(true);f.addView(new ThemePreview(a,theme(i),null),new FrameLayout.LayoutParams(-1,-2));if(i.price>0){TextView badge=AppUi.text(a,"프리미엄",13,0xff1c1c1e);badge.setTypeface(AppUi.SEMIBOLD);badge.setPadding(dp(10),dp(5),dp(10),dp(5));badge.setBackground(AppUi.shape(a,0xffffd60a,10,0));FrameLayout.LayoutParams bb=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);bb.setMargins(dp(10),dp(10),dp(10),0);f.addView(badge,bb);}if(i.fresh){TextView n=AppUi.text(a,"NEW",13,AppUi.INK);n.setTypeface(AppUi.SEMIBOLD);n.setPadding(dp(10),dp(5),dp(10),dp(5));n.setBackground(AppUi.shape(a,AppUi.ACCENT,10,0));FrameLayout.LayoutParams nb=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);nb.setMargins(dp(10),dp(10),0,0);f.addView(n,nb);}return f;}
 private View card(Item i){LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(12),dp(12),dp(14));c.setBackground(AppUi.shape(a,AppUi.CARD,24,0));c.addView(preview(i));
  TextView name=AppUi.text(a,i.name,20,AppUi.INK);name.setTypeface(AppUi.SEMIBOLD);name.setPadding(dp(4),dp(12),dp(4),dp(2));name.setMaxLines(1);c.addView(name);TextView meta=AppUi.text(a,i.creator+" · 받은 수 "+downloads(i)+" · 예시",14,AppUi.MUTED);meta.setPadding(dp(4),0,dp(4),dp(10));c.addView(meta);
  Button get=AppUi.button(a,i.price==0?"받기":price(i),i.price>0,()->mock());c.addView(get,new LinearLayout.LayoutParams(-1,dp(AppUi.TOUCH)));c.setOnClickListener(v->detail(i));c.setContentDescription(i.name+" 상세 보기");return c;}
 private View banner(Item i){FrameLayout b=new FrameLayout(a);b.setBackground(AppUi.shape(a,0xff101217,28,0));b.setClipToOutline(true);JSONObject scene=theme(i);try{for(String k:CreatorTheme.panelKeys())scene.getJSONObject("panel").put(k,false);}catch(Exception ignored){}// background only, so the headline never sits on a preview clock
  ThemePreview p=new ThemePreview(a,scene,null);b.addView(p,new FrameLayout.LayoutParams(-1,dp(380)));
  LinearLayout words=new LinearLayout(a);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(28),dp(24),dp(28),dp(24));words.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xcc0b0c10,0x000b0c10}));
  TextView eyebrow=AppUi.text(a,"이번 주 추천",16,0xffffd60a);eyebrow.setTypeface(AppUi.SEMIBOLD);words.addView(eyebrow);TextView t=AppUi.text(a,i.name,34,AppUi.INK);t.setTypeface(AppUi.SEMIBOLD);words.addView(t);TextView s=AppUi.text(a,i.note+"\n"+i.creator+" · "+price(i)+" · 예시",17,0xffd1d1d6);s.setPadding(0,dp(6),0,dp(16));words.addView(s);
  Button open=AppUi.button(a,"자세히 보기",true,()->detail(i));words.addView(open,new LinearLayout.LayoutParams(dp(200),dp(AppUi.TOUCH)));b.addView(words,new FrameLayout.LayoutParams(Math.min(dp(620),Math.round(a.getWindow().getDecorView().getWidth()*.6f)),-1,Gravity.START));return b;}
 private View creator(String name,String meta){LinearLayout c=new LinearLayout(a);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setMinimumHeight(dp(96));c.setBackground(AppUi.shape(a,AppUi.CARD,24,0));TextView avatar=AppUi.text(a,name.substring(name.length()-1),24,AppUi.INK);avatar.setGravity(Gravity.CENTER);avatar.setTypeface(AppUi.SEMIBOLD);avatar.setBackground(AppUi.shape(a,AppUi.ACCENT,32,0));c.addView(avatar,new LinearLayout.LayoutParams(dp(64),dp(64)));LinearLayout w=new LinearLayout(a);w.setOrientation(LinearLayout.VERTICAL);w.setPadding(dp(14),0,0,0);TextView n=AppUi.text(a,name,18,AppUi.INK);n.setTypeface(AppUi.SEMIBOLD);w.addView(n);w.addView(AppUi.text(a,meta+" · 예시",14,AppUi.MUTED));c.addView(w,new LinearLayout.LayoutParams(0,-2,1));c.setOnClickListener(v->mock());return c;}
 static final String[][] REACT={{"battery","배터리·충전"},{"music","음악 재생"},{"mood","실내 무드등"},{"night","밤낮"}};
 private void detail(Item i){
  LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(24),dp(24),dp(20));body.addView(preview(i));
  TextView name=AppUi.text(a,i.name,28,AppUi.INK);name.setTypeface(AppUi.SEMIBOLD);name.setPadding(0,dp(16),0,dp(2));body.addView(name);body.addView(AppUi.text(a,i.creator+" · 받은 수 "+downloads(i)+" · "+MOCK,15,AppUi.MUTED));TextView note=AppUi.text(a,i.note,18,0xffd1d1d6);note.setPadding(0,dp(12),0,dp(12));body.addView(note);
  TextView label=AppUi.text(a,"반응하는 차량 데이터",16,AppUi.MUTED);label.setPadding(0,0,0,dp(8));body.addView(label);LinearLayout chips=new LinearLayout(a);for(String r:i.reacts)for(String[] k:REACT)if(k[0].equals(r)){TextView chipView=AppUi.text(a,k[1],16,AppUi.INK);chipView.setPadding(dp(14),dp(8),dp(14),dp(8));chipView.setBackground(AppUi.shape(a,AppUi.FILL,16,0));int icon=AppUi.res(a,r.equals("battery")?"ic_plus":r.equals("music")?"ic_play":r.equals("mood")?"ic_sliders":"ic_info");if(icon!=0){android.graphics.drawable.Drawable d=a.getDrawable(icon).mutate();d.setTint(Color.WHITE);int s=dp(20);d.setBounds(0,0,s,s);chipView.setCompoundDrawablesRelative(d,null,null,null);chipView.setCompoundDrawablePadding(dp(8));}LinearLayout.LayoutParams cb=new LinearLayout.LayoutParams(-2,-2);cb.rightMargin=dp(10);chips.addView(chipView,cb);}body.addView(chips);
  AlertDialog[] d={null};LinearLayout foot=new LinearLayout(a);Button back=AppUi.button(a,"돌아가기",false,()->d[0].dismiss());foot.addView(back,new LinearLayout.LayoutParams(0,dp(AppUi.TOUCH),1));Button get=AppUi.button(a,i.price==0?"받기":price(i)+" 구매",true,this::mock);LinearLayout.LayoutParams gb=new LinearLayout.LayoutParams(0,dp(AppUi.TOUCH),1);gb.leftMargin=dp(16);foot.addView(get,gb);LinearLayout.LayoutParams fb=new LinearLayout.LayoutParams(-1,-2);fb.topMargin=dp(20);body.addView(foot,fb);
  ScrollView scroll=new ScrollView(a);scroll.addView(body);d[0]=AppUi.dialog(a).setView(scroll).create();d[0].setOnShowListener(v->AppUi.prepareDialog(a,d[0],scroll,840,.9f));d[0].show();
 }
 private void mock(){AppUi.notice(a,"시안 화면이에요 · 결제·받기 기능은 없어요");}
}
