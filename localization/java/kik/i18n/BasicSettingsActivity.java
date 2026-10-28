package kik.i18n;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

/** Balanced settings cards with compact controls and a continuous D-pad focus path. */
public final class BasicSettingsActivity extends Activity {
 private SharedPreferences prefs;
 private TextView updateStatus;
 private Button updateNow;
 // A postponed update stays visible here: a red «Оновити до …» on top of the updates card until it is installed.
 private final Runnable updateChanged=()->{if(updateStatus!=null)updateStatus.setText(AppUpdater.status(this));if(updateNow!=null){String name=AppUpdater.availableName(this);updateNow.setVisibility(name==null?View.GONE:View.VISIBLE);if(name!=null)updateNow.setText("Оновити до "+name);}linkColumn(this.rightColumn);};
 private final String[] qualities={"4k","2k","1080p","720p","480p","360p"};
 private final List<Button> qualityButtons=new ArrayList<>();
 private int cardWidth;
 private LinearLayout rightColumn;
 private int dp(float n){return Ui.dp(this,n);}

 @Override public void onCreate(Bundle state){
  super.onCreate(state);UiRuntime.settingsOpen=true;Ui.screen(this);prefs=getSharedPreferences("settings",0);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);scroll.setFillViewport(true);scroll.setClipToPadding(false);
  LinearLayout page=column();page.setPadding(dp(40),dp(24),dp(40),dp(28));
  scroll.addView(page,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
  LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
  heading.addView(Brand.logo(this,56,26));TextView title=Ui.bold(this,"Налаштування",28);
  LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.leftMargin=dp(20);heading.addView(title,titleLp);
  Button done=Ui.pill(this,"Готово",false);done.setTextSize(15);heading.addView(done,new LinearLayout.LayoutParams(-2,dp(44)));done.setOnClickListener(v->finish());
  page.addView(heading);Ui.space(page,8);page.addView(Ui.text(this,"Ваш профіль, перегляд і пристрої",14,Ui.GREY));Ui.space(page,22);

  int available=Ui.contentWidth(this,1120,80);boolean wide=available>=dp(720);
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);layout.setClipChildren(false);layout.setClipToPadding(false);
  page.addView(layout,new LinearLayout.LayoutParams(available,-2));
  LinearLayout left=column(),right=column();rightColumn=right;cardWidth=wide?(available-dp(20))/2:available;
  layout.addView(left,new LinearLayout.LayoutParams(cardWidth,-2));
  LinearLayout.LayoutParams rightLp=new LinearLayout.LayoutParams(cardWidth,-2);if(wide)rightLp.leftMargin=dp(20);layout.addView(right,rightLp);
  int inner=cardWidth-dp(40);

  LinearLayout playback=card(left,"Відтворення","Мова та якість залежать від джерела.");
  playback.addView(Ui.text(this,"Якість за замовчуванням",14,Ui.GREY));Ui.space(playback,10);
  int columns=Math.max(2,Math.min(3,(inner+dp(8))/dp(100)));
  GridLayout qualityGrid=new GridLayout(this);qualityGrid.setColumnCount(columns);qualityGrid.setClipChildren(false);qualityGrid.setClipToPadding(false);
  playback.addView(qualityGrid,new LinearLayout.LayoutParams(-1,-2));
  String selected=prefs.getString("default_quality","1080p");Button selectedButton=null;
  for(String quality:qualities){
   Button b=Ui.pill(this,quality,false);b.setTextSize(14);b.setPadding(dp(8),0,dp(8),0);qualityButtons.add(b);
   GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=(inner+dp(8))/columns-dp(8);lp.height=dp(44);lp.rightMargin=dp(8);lp.bottomMargin=dp(10);qualityGrid.addView(b,lp);
   if(quality.equals(selected)){b.setText("✓ "+quality);b.setSelected(true);selectedButton=b;}
   b.setOnClickListener(v->{prefs.edit().putString("default_quality",quality).apply();UiRuntime.updateState("j",quality);for(int i=0;i<qualityButtons.size();i++){Button choice=qualityButtons.get(i);boolean active=qualities[i].equals(quality);choice.setText((active?"✓ ":"")+qualities[i]);choice.setSelected(active);}});
  }
  Ui.space(playback,6);
  Switch next=toggle(playback,"Наступна серія автоматично",prefs.getBoolean("auto_play_next_episode",true));
  next.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("auto_play_next_episode",on).apply();UiRuntime.updateState("E",on);});
  Switch intro=toggle(playback,"Заставка перед відео",prefs.getBoolean("intro",true));intro.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("intro",on).apply());

  LinearLayout countries=card(left,"Каталог","Приховайте країни, які не хочете бачити в добірках.");
  Set<String> hidden=new LinkedHashSet<>(CountryFilter.excluded());GridLayout countryGrid=new GridLayout(this);countryGrid.setColumnCount(2);countryGrid.setClipChildren(false);countryGrid.setClipToPadding(false);countries.addView(countryGrid);
  for(String[] choice:CountryFilter.CHOICES){
   Button b=Ui.pill(this,(hidden.contains(choice[0])?"✓ ":"")+choice[1],false);b.setTextSize(14);b.setPadding(dp(8),0,dp(8),0);b.setSelected(hidden.contains(choice[0]));
   GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=(inner-dp(10))/2;lp.height=dp(42);lp.rightMargin=dp(10);lp.bottomMargin=dp(10);countryGrid.addView(b,lp);
   b.setOnClickListener(v->{if(!hidden.remove(choice[0]))hidden.add(choice[0]);boolean active=hidden.contains(choice[0]);b.setText((active?"✓ ":"")+choice[1]);b.setSelected(active);prefs.edit().putString("excluded_countries",String.join(",",hidden)).apply();CountryFilter.set(hidden);getSharedPreferences("kik_home",0).edit().clear().apply();});
  }
  TextView help=Ui.text(this,"Історія та «Мій список» збережуться.",13,Ui.GREY);help.setPadding(0,dp(4),0,0);countries.addView(help);

  LinearLayout profile=card(right,"Профіль і пристрої",null);
  LinearLayout identity=new LinearLayout(this);identity.setGravity(Gravity.CENTER_VERTICAL);ImageView avatar=Avatars.view(this,ProfileStore.avatarOf(this,ProfileStore.current(this)),40);avatar.setFocusable(false);identity.addView(avatar,new LinearLayout.LayoutParams(dp(40),dp(40)));
  TextView name=Ui.bold(this,ProfileStore.nameOf(this,ProfileStore.current(this)),17);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(0,-2,1);nameLp.leftMargin=dp(12);identity.addView(name,nameLp);profile.addView(identity);Ui.space(profile,14);
  action(profile,"Змінити рекомендації",()->TasteActivity.open(this,false));
  action(profile,"Керувати профілями",()->startActivity(new Intent(this,ProfilesActivity.class)));
  action(profile,"Синхронізація профілю",()->startActivity(new Intent(this,SyncActivity.class)));
  if(Account.signedIn(this))action(profile,"Облікові записи",this::accounts);

  LinearLayout updates=card(right,"Оновлення KIK","Версія "+AppUpdater.version(this));
  updateNow=Ui.pill(this,"Оновити",true);updateNow.setTextSize(15);updateNow.setBackground(Ui.states(Ui.shape(Ui.RED,dp(22),0,0),Ui.shape(Color.WHITE,dp(22),dp(2),Ui.RED)));LinearLayout.LayoutParams now=new LinearLayout.LayoutParams(-1,dp(44));now.bottomMargin=dp(12);updates.addView(updateNow,now);updateNow.setOnClickListener(v->AppUpdater.manual(this));updateNow.setVisibility(View.GONE);
  Switch automatic=toggle(updates,"Оновлювати автоматично",AppUpdater.enabled(this));automatic.setOnCheckedChangeListener((v,on)->AppUpdater.setEnabled(this,on));
  updateStatus=Ui.text(this,AppUpdater.status(this),13,Ui.GREY);updateStatus.setLineSpacing(0,1.15f);updateStatus.setPadding(0,dp(6),0,dp(12));updates.addView(updateStatus);
  action(updates,"Перевірити оновлення",()->AppUpdater.manual(this));action(updates,"Дозвіл на встановлення",()->AppUpdater.permission(this));

  LinearLayout about=card(right,"Про KIK","Українська → російська · Вбудований плеєр");
  action(about,"Джерела даних",()->FreeSources.credits(this));
  action(about,"Очистити кеш зображень",()->Ui.dialog(this).setTitle("Очистити кеш зображень?").setMessage("Обране та історія перегляду збережуться.").setNegativeButton("Скасувати",null).setPositiveButton("Очистити",(dialog,which)->Ui.bg(()->{clearImages(getCacheDir());HomeActivity.Images.clear();runOnUiThread(()->Ui.toast(this,"Кеш зображень очищено"));})).show());
  TextView author=Ui.text(this,"◐  Створено Чорним",13,Ui.GREY);author.setPadding(0,dp(8),0,0);about.addView(author);
  linkColumn(right);
  if(selectedButton!=null)selectedButton.requestFocus();
 }
 /** Every account on this TV with its own «Вийти», and «+ Інший обліковий запис» for another email. */
 private void accounts(){
  org.json.JSONArray list=Account.accounts(this);String[] items=new String[list.length()+1];String[] tokens=new String[list.length()];
  for(int i=0;i<list.length();i++){org.json.JSONObject a=list.optJSONObject(i);tokens[i]=a==null?"":a.optString("token");items[i]="Вийти: "+(a==null||a.optString("email").isEmpty()?"обліковий запис":a.optString("email"));}
  items[list.length()]="+ Інший обліковий запис";
  Ui.dialog(this).setTitle("Облікові записи на TV").setItems(items,(d,which)->{if(which<tokens.length)Account.confirmLeave(this,tokens[which]);else SignInActivity.addAccount(this);}).setNegativeButton("Закрити",null).show();
 }
 // Up/Down stays inside the column: the taller catalogue grid on the left otherwise wins the focus search from the lower right cards.
 private void linkColumn(LinearLayout column){
  if(column==null)return;
  ArrayList<View> views=new ArrayList<>();column.addFocusables(views,View.FOCUS_DOWN,View.FOCUSABLES_ALL);
  for(int i=0;i<views.size();i++){View v=views.get(i);if(v.getId()==View.NO_ID)v.setId(View.generateViewId());}
  for(int i=0;i<views.size();i++){View v=views.get(i);v.setNextFocusUpId(i>0?views.get(i-1).getId():View.NO_ID);v.setNextFocusDownId(i+1<views.size()?views.get(i+1).getId():v.getId());}
 }
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setClipChildren(false);l.setClipToPadding(false);return l;}
 private LinearLayout card(LinearLayout parent,String title,String description){
  LinearLayout card=column();card.setPadding(dp(20),dp(18),dp(20),dp(18));card.setBackground(Ui.shape(Ui.CARD,dp(20),dp(1),0xff2c2c32));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(18);parent.addView(card,lp);
  card.addView(Ui.bold(this,title,19));Ui.space(card,8);
  if(description!=null){TextView text=Ui.text(this,description,13,Ui.GREY);text.setLineSpacing(0,1.2f);card.addView(text);Ui.space(card,14);}else Ui.space(card,6);
  return card;
 }
 private Button action(LinearLayout card,String label,Runnable run){Button b=Ui.pill(this,label,false);b.setTextSize(15);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.bottomMargin=dp(10);card.addView(b,lp);b.setOnClickListener(v->run.run());return b;}
 private Switch toggle(LinearLayout card,String label,boolean checked){
  Switch toggle=new Switch(this);toggle.setText(label);toggle.setTextColor(Color.WHITE);toggle.setTextSize(16);toggle.setMaxLines(2);toggle.setEllipsize(TextUtils.TruncateAt.END);toggle.setMinHeight(dp(48));toggle.setSwitchPadding(dp(14));toggle.setPadding(dp(12),dp(8),dp(12),dp(8));toggle.setFocusable(true);toggle.setBackground(Ui.states(Ui.shape(0xff25252b,dp(14),0,0),Ui.shape(0xff33333a,dp(14),dp(2),Ui.RED)));
  toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.RED,Ui.GREY}));toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff713038,0xff46464b}));toggle.setChecked(checked);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);card.addView(toggle,lp);return toggle;
 }
 private long clearImages(File root){long n=0;File[] files=root.listFiles();if(files!=null)for(File f:files){String name=f.getName().toLowerCase(Locale.ROOT);if(f.isDirectory()&&(name.contains("image")||name.contains("coil")))n+=removeTree(f);}return n;}
 private long removeTree(File f){long n=f.length();if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File c:a)n+=removeTree(c);}f.delete();return n;}
 @Override protected void onResume(){super.onResume();AppUpdater.listen(updateChanged);updateChanged.run();AppUpdater.resumed(this);AppUpdater.lookup(this,updateChanged);}
 @Override protected void onPause(){AppUpdater.paused(this);AppUpdater.unlisten(updateChanged);super.onPause();}
 @Override protected void onDestroy(){UiRuntime.settingsOpen=false;super.onDestroy();}
}
