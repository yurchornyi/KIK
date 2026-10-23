package kik.i18n;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.File;

/** Small, remote-friendly settings surface. Playback preferences keep the existing adapter keys. */
public final class BasicSettingsActivity extends Activity {
 private SharedPreferences prefs;
 private TextView updateStatus;
 private final Runnable updateChanged=()->{if(updateStatus!=null)updateStatus.setText(AppUpdater.status(this));};
 private final String[] qualities={"1080p","720p","480p","360p","2k","4k"};
 private LinearLayout content;
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);UiRuntime.settingsOpen=true;Ui.screen(this);
  getWindow().setFlags(1024,1024);
  getWindow().getDecorView().setSystemUiVisibility(5894);
  prefs=getSharedPreferences("settings",0);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff0d0d0f);scroll.setFillViewport(true);
  content=new LinearLayout(this);content.setOrientation(1);content.setClipChildren(false);content.setClipToPadding(false);content.setPadding(dp(48),dp(26),dp(48),dp(24));
  scroll.addView(content,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
  TextView title=text("Налаштування",30);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);content.addView(title);Ui.enter(title,0);
  TextView subtitle=text("Відео та відтворення",16);subtitle.setTextColor(0xffb5b1be);space(6);content.addView(subtitle);space(18);
  TextView quality=text("Якість за замовчуванням",20);content.addView(quality);space(6);
  LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.setClipChildren(false);row.setClipToPadding(false);content.addView(row);
  String selected=prefs.getString("default_quality","1080p");Button selectedButton=null;
  for(final String q:qualities){
   Button b=button(q);b.setTextSize(16);b.setPadding(dp(12),0,dp(12),0);if(q.equals(selected)){b.setText("✓ "+q);selectedButton=b;}
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(105),dp(50));lp.setMargins(0,0,dp(8),0);row.addView(b,lp);
   b.setOnClickListener(v->{prefs.edit().putString("default_quality",q).apply();UiRuntime.updateState("j",q);for(int i=0;i<row.getChildCount();i++)((Button)row.getChildAt(i)).setText(qualities[i]);b.setText("✓ "+q);});
  }
  space(6);TextView help=text("Якщо вибрана якість недоступна, плеєр використає доступний варіант.",14);help.setTextColor(0xffa29daa);content.addView(help);space(18);
  Switch next=new Switch(this);next.setText("Автоматично вмикати наступну серію");next.setTextColor(Color.WHITE);next.setTextSize(19);next.setPadding(dp(14),dp(8),dp(14),dp(8));next.setFocusable(true);next.setBackground(focusBackground());next.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.RED,Ui.GREY}));next.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff713038,0xff46464b}));next.setChecked(prefs.getBoolean("auto_play_next_episode",true));next.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("auto_play_next_episode",on).apply();UiRuntime.updateState("E",on);});content.addView(next,new LinearLayout.LayoutParams(dp(670),dp(52)));space(18);
  space(18);TextView countries=text("Приховати фільми з країн",20);content.addView(countries);space(6);
  final java.util.Set<String> hidden=new java.util.LinkedHashSet<String>(CountryFilter.excluded());
  LinearLayout countryRow=null;
  for(int i=0;i<CountryFilter.CHOICES.length;i++){
   final String[] choice=CountryFilter.CHOICES[i];
   if(i%4==0){countryRow=new LinearLayout(this);countryRow.setOrientation(0);countryRow.setClipChildren(false);countryRow.setClipToPadding(false);content.addView(countryRow);space(8);}
   final Button b=button((hidden.contains(choice[0])?"✓ ":"")+choice[1]);b.setTextSize(16);b.setPadding(dp(12),0,dp(12),0);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(200),dp(50));lp.setMargins(0,0,dp(8),0);countryRow.addView(b,lp);
   b.setOnClickListener(v->{if(!hidden.remove(choice[0]))hidden.add(choice[0]);b.setText((hidden.contains(choice[0])?"✓ ":"")+choice[1]);
    prefs.edit().putString("excluded_countries",String.join(",",hidden)).apply();CountryFilter.set(hidden);
    // Cached rows were built with the old filter.
    getSharedPreferences("kik_home",0).edit().clear().apply();});
  }
  TextView countryHelp=text("Позначені країни не показуються в каталозі, пошуку й рекомендаціях. Історія та «Мій список» не змінюються.",14);countryHelp.setTextColor(0xffa29daa);content.addView(countryHelp,new LinearLayout.LayoutParams(dp(900),-2));space(18);
  Button cache=button("Очистити кеш зображень");content.addView(cache,new LinearLayout.LayoutParams(dp(350),dp(48)));
  cache.setOnClickListener(v->Ui.dialog(this).setTitle("Очистити кеш зображень?").setMessage("Обране та історія перегляду збережуться.").setNegativeButton("Скасувати",null).setPositiveButton("Очистити",(dialog,which)->{new Thread(()->{long deleted=clearImages(getCacheDir());HomeActivity.Images.clear();runOnUiThread(()->Toast.makeText(this,"Кеш зображень очищено",Toast.LENGTH_SHORT).show());}).start();}).show());
  space(18);String who=ProfileStore.nameOf(this,ProfileStore.current(this));
  Button profiles=button(who==null?"Профілі":"Профіль: "+who+" • змінити");content.addView(profiles,new LinearLayout.LayoutParams(dp(420),dp(48)));
  profiles.setOnClickListener(v->startActivity(new Intent(this,ProfilesActivity.class)));
  space(18);Button sync=button("Синхронізація профілю");content.addView(sync,new LinearLayout.LayoutParams(dp(350),dp(48)));sync.setOnClickListener(v->startActivity(new Intent(this,SyncActivity.class)));
  space(22);TextView updates=text("Оновлення KIK • "+AppUpdater.version(this),20);content.addView(updates);space(8);
  Switch automatic=new Switch(this);automatic.setText("Оновлювати автоматично");automatic.setTextColor(Color.WHITE);automatic.setTextSize(19);automatic.setPadding(dp(14),dp(8),dp(14),dp(8));automatic.setFocusable(true);automatic.setBackground(focusBackground());automatic.setThumbTintList(next.getThumbTintList());automatic.setTrackTintList(next.getTrackTintList());automatic.setChecked(AppUpdater.enabled(this));automatic.setOnCheckedChangeListener((v,on)->AppUpdater.setEnabled(this,on));content.addView(automatic,new LinearLayout.LayoutParams(dp(670),dp(52)));space(8);
  updateStatus=text(AppUpdater.status(this),14);updateStatus.setTextColor(0xffb5b1be);content.addView(updateStatus,new LinearLayout.LayoutParams(dp(670),-2));space(10);
  Button check=button("Перевірити оновлення");content.addView(check,new LinearLayout.LayoutParams(dp(350),dp(48)));check.setOnClickListener(v->AppUpdater.manual(this));space(8);
  Button permission=button("Дозвіл на встановлення");content.addView(permission,new LinearLayout.LayoutParams(dp(350),dp(48)));permission.setOnClickListener(v->AppUpdater.permission(this));
  space(18);TextView about=text("KIK • Вбудований плеєр • Українська → російська",14);about.setTextColor(0xffa29daa);content.addView(about);space(10);
  Button sources=button("Джерела даних");content.addView(sources,new LinearLayout.LayoutParams(dp(350),dp(46)));sources.setOnClickListener(v->FreeSources.credits(this));space(14);
  LinearLayout author=new LinearLayout(this);author.setOrientation(0);author.setGravity(Gravity.CENTER_VERTICAL);
  halfCircle=new HalfCircle(this);author.addView(halfCircle,new LinearLayout.LayoutParams(dp(18),dp(18)));
  TextView name=text("Створено Чорним",15);name.setTextColor(0xffd8d6de);name.setLetterSpacing(0.06f);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-2,-2);np.setMargins(dp(10),0,0,0);author.addView(name,np);
  content.addView(author);space(14);
  Button back=button("Готово");content.addView(back,new LinearLayout.LayoutParams(dp(160),dp(46)));back.setOnClickListener(v->finish());
  if(selectedButton!=null)selectedButton.requestFocus();
 }
 /** Author mark: a circle with one filled half, turning slowly only while settings are visible. */
 private static final class HalfCircle extends View {
  private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG),fill=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF box=new RectF();
  HalfCircle(Context c){super(c);line.setStyle(Paint.Style.STROKE);line.setColor(0xffd8d6de);line.setStrokeWidth(c.getResources().getDisplayMetrics().density);fill.setColor(0xffd8d6de);setLayerType(LAYER_TYPE_HARDWARE,null);}
  @Override protected void onDraw(Canvas canvas){float s=line.getStrokeWidth();box.set(s,s,getWidth()-s,getHeight()-s);canvas.drawArc(box,-90,180,true,fill);canvas.drawOval(box,line);}
 }
 private HalfCircle halfCircle;private android.animation.ObjectAnimator turning;
 private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);return t;}
 private void space(int size){View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,dp(size)));}
 private Button button(String text){return Ui.pill(this,text,false);}
 private StateListDrawable focusBackground(){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},shape(Ui.CARD,Ui.RED));s.addState(new int[]{},shape(0xff1f1f23,0xff2e2e33));return s;}
 private GradientDrawable shape(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(24));d.setStroke(dp(2),stroke);return d;}
 private long clearImages(File root){long n=0;File[] files=root.listFiles();if(files==null)return 0;for(File f:files){String name=f.getName().toLowerCase(java.util.Locale.ROOT);if(f.isDirectory()&&(name.contains("image")||name.contains("coil"))){n+=removeTree(f);} }return n;}
 private long removeTree(File f){long n=f.length();if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File c:a)n+=removeTree(c);}f.delete();return n;}
 @Override protected void onResume(){super.onResume();if(halfCircle!=null){turning=android.animation.ObjectAnimator.ofFloat(halfCircle,"rotation",halfCircle.getRotation(),halfCircle.getRotation()+360);turning.setDuration(6000);turning.setRepeatCount(android.animation.ValueAnimator.INFINITE);turning.setInterpolator(new android.view.animation.LinearInterpolator());turning.start();}AppUpdater.listen(updateChanged);updateChanged.run();AppUpdater.resumed(this);}
 @Override protected void onPause(){if(turning!=null){turning.cancel();turning=null;}AppUpdater.paused(this);AppUpdater.unlisten(updateChanged);super.onPause();}
 @Override protected void onDestroy(){UiRuntime.settingsOpen=false;super.onDestroy();}
}
