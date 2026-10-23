package kik.i18n;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import android.util.Base64;
import java.util.concurrent.*;
import org.json.*;

/**
 * Netflix-style start screen: hero for the focused title, "continue watching" and the watch-later list
 * from this profile, and catalogue rows fetched by KIK's own site client (cached for an instant start).
 */
public final class HomeActivity extends Activity {
 static final int BG=0xff0d0d0f,RED=0xffe50914,GREY=0xffa3a3a8,CARD=0xff1b1b1f;

 static final class Item{String link,title,poster,wide,year,genre,rating,sub,description;boolean resume;float progress;}
 static final class Row{String title,recommendationId;boolean personalized;List<Item> items=new ArrayList<Item>();Row(String t){title=t;}}

 private FrameLayout root;private ImageView heroImage;private LinearLayout content;private ScrollView scroll;
 private TextView heroTitle,heroMeta,heroSub,heroText;private Button watch,more,later;private volatile int loadGeneration;private volatile boolean destroyed;
 private Item hero,pendingHero;private String focusedLink;private Runnable heroTask=()->{};private final Set<String> laterLinks=new HashSet<String>();
 private static final android.view.animation.Interpolator EASE=new android.view.animation.DecelerateInterpolator(1.6f);private boolean expanded,loadedOnce;
 private final Handler main=new Handler(Looper.getMainLooper());
 private volatile Map<String,String> uk=new HashMap<String,String>();
 private LinearLayout recommendedLine;private TextView recommendedHeading;private String recommendationKeys="";
 private final Map<String,LinearLayout> recommendationLines=new HashMap<String,LinearLayout>();private final Map<String,String> recommendationSignatures=new HashMap<String,String>();private List<Item> tasteChoices=new ArrayList<Item>();

 private volatile boolean foreground;
 private String lastProfileName,lastProfileAvatar;
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}

 @Override public void onCreate(Bundle state){
  super.onCreate(state);if(Account.gate(this))return;TvChannels.start(this);
  getWindow().setFlags(1024,1024);getWindow().getDecorView().setSystemUiVisibility(5894);
  getWindow().setBackgroundDrawable(null);
  root=new FrameLayout(this);root.setBackgroundColor(BG);setContentView(root);
  int heroH=Math.round(getResources().getDisplayMetrics().heightPixels*0.60f);
  heroImage=new ImageView(this);
  // The backdrop is one opaque, half-resolution bitmap with its fades baked in (blending is costly on this GPU).
  heroImage.setScaleType(ImageView.ScaleType.FIT_XY);heroImage.setBackgroundColor(BG);
  root.addView(heroImage,new FrameLayout.LayoutParams(-1,heroH));
  scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setSmoothScrollingEnabled(true);
  scroll.getViewTreeObserver().addOnScrollChangedListener(()->heroImage.setTranslationY(-Math.min(scroll.getScrollY(),heroH)));
  content=new LinearLayout(this);content.setOrientation(1);content.setClipChildren(false);content.setClipToPadding(false);
  scroll.addView(content,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
  content.addView(topBar());
  LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setClipChildren(false);info.setClipToPadding(false);info.setPadding(dp(40),dp(6),dp(40),dp(12));info.setMinimumHeight(dp(200));info.setGravity(Gravity.BOTTOM);
  heroTitle=text("",32,Color.WHITE);heroTitle.setTypeface(Typeface.DEFAULT_BOLD);heroTitle.setSingleLine(true);heroTitle.setEllipsize(TextUtils.TruncateAt.END);
  heroMeta=text("",14,0xffd8d8dc);heroMeta.setSingleLine(true);heroSub=text("",13,0xffefb8bb);heroSub.setSingleLine(true);heroSub.setTypeface(Typeface.DEFAULT_BOLD);
  heroText=text("",14,0xffd0d0d4);heroText.setLines(2);heroText.setEllipsize(TextUtils.TruncateAt.END);heroText.setLineSpacing(0,1.12f);
  info.addView(heroTitle,new LinearLayout.LayoutParams(Ui.contentWidth(this,650,80),-2));space(info,5);info.addView(heroMeta);space(info,3);info.addView(heroSub);space(info,8);
  info.addView(heroText,new LinearLayout.LayoutParams(Ui.contentWidth(this,500,80),-2));space(info,14);
  LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(0);buttons.setClipChildren(false);buttons.setClipToPadding(false);info.addView(Ui.horizontal(this,buttons),new LinearLayout.LayoutParams(-1,-2));
  watch=action("Дивитися",HeroActions.PLAY,true);buttons.addView(watch,new LinearLayout.LayoutParams(-2,dp(48)));
  later=action("Мій список",HeroActions.ADD,false);LinearLayout.LayoutParams lp0=new LinearLayout.LayoutParams(-2,dp(48));lp0.setMargins(dp(10),0,0,0);buttons.addView(later,lp0);
  later.setOnClickListener(v->toggleLater(hero));
  more=action("Детальніше",HeroActions.INFO,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(48));lp.setMargins(dp(10),0,0,0);buttons.addView(more,lp);
  watch.setOnClickListener(v->{if(hero!=null)startActivity(new Intent(this,FilmActivity.class).putExtra("link",hero.link).putExtra("title",tr(hero.title)).putExtra("poster",hero.poster).putExtra("play",true));});
  more.setOnClickListener(v->open(hero));
  content.addView(info);
  Ui.enter(info,60);
  heroText.setText("Завантаження…");
  ProfileStore.askOnce(this);
  if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::onBackPressed);
  load();
 }

 private long cloudRevision=CloudSync.revision();
 private final Runnable cloudTick=new Runnable(){public void run(){if(destroyed||!foreground)return;CloudSync.now(HomeActivity.this);long next=CloudSync.revision();if(next!=cloudRevision){cloudRevision=next;load();}main.postDelayed(this,15000);}};
 @Override protected void onResume(){super.onResume();foreground=true;AppUpdater.resumed(this);CloudSync.changed(this);main.removeCallbacks(cloudTick);main.postDelayed(cloudTick,15000);if(loadedOnce){String n=ProfileStore.nameOf(this,ProfileStore.current(this)),avatar=ProfileStore.avatarOf(this,ProfileStore.current(this));if(!java.util.Objects.equals(n,lastProfileName)||!java.util.Objects.equals(avatar,lastProfileAvatar)){content.removeViewAt(0);content.addView(topBar(),0);}load();}}
 @Override protected void onPause(){foreground=false;AppUpdater.paused(this);main.removeCallbacks(cloudTick);super.onPause();}
 @Override protected void onDestroy(){destroyed=true;loadGeneration++;main.removeCallbacksAndMessages(null);super.onDestroy();}
 private long backAt;private TextView backHint;
 /** Back twice leaves the app; the first press only shows a quiet hint (no dialog). */
 // The app opts into predictive back, so onBackPressed is not called on Android 13+: handle the key itself.
 @Override public boolean dispatchKeyEvent(KeyEvent e){
  if(e.getKeyCode()==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)onBackPressed();return true;}
  return super.dispatchKeyEvent(e);
 }
 @Override public void onBackPressed(){
  long now=SystemClock.elapsedRealtime();
  if(now-backAt<3000){finishAffinity();return;}
  backAt=now;
  if(backHint==null){backHint=text("Натисніть «Назад» ще раз, щоб вийти",16,Color.WHITE);backHint.setPadding(dp(22),dp(10),dp(22),dp(10));backHint.setBackground(shape(0xe61b1b1f,dp(22),0,0));
   root.addView(backHint,new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL){{bottomMargin=dp(40);}});}
  backHint.animate().cancel();backHint.setAlpha(0f);backHint.setTranslationY(dp(16));
  backHint.animate().alpha(1f).translationY(0).setDuration(240).withEndAction(()->backHint.animate().alpha(0f).setStartDelay(2300).setDuration(300).start()).start();
 }

 private View topBar(){
  LinearLayout bar=new LinearLayout(this);bar.setOrientation(0);bar.setBaselineAligned(false);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(40),dp(16),dp(40),dp(8));
  bar.addView(Brand.logo(this,64,30));
  LinearLayout menuRow=new LinearLayout(this);menuRow.setGravity(Gravity.CENTER_VERTICAL);
  HorizontalScrollView navigation=Ui.horizontal(this,menuRow);LinearLayout.LayoutParams navLp=new LinearLayout.LayoutParams(0,-2,1);navLp.setMargins(dp(14),0,dp(10),0);bar.addView(navigation,navLp);
  menuRow.addView(menu("Головна",v->{if(hero!=null)watch.requestFocus();}));
  menuRow.addView(menu("Каталог ▾",v->Ui.dialog(this).setTitle("Що подивимось?").setItems(new String[]{"Фільми","Серіали","Мультфільми","Аніме"},(d,n)->BrowseActivity.open(this,"catalog",new String[]{"films","series","cartoons","animation"}[n],new String[]{"Фільми","Серіали","Мультфільми","Аніме"}[n])).show()));
  menuRow.addView(menu("Пошук",v->BrowseActivity.open(this,"search",null,"Пошук")));
  menuRow.addView(menu("Мій список",v->BrowseActivity.open(this,"later",null,"Переглянути пізніше")));
  menuRow.addView(menu("Історія",v->BrowseActivity.open(this,"history",null,"Історія переглядів")));
  menuRow.addView(menu("Налаштування",v->startActivity(new Intent(this,BasicSettingsActivity.class))));

  String name=ProfileStore.nameOf(this,ProfileStore.current(this));lastProfileName=name;lastProfileAvatar=ProfileStore.avatarOf(this,ProfileStore.current(this));
  ImageView avatar=Avatars.view(this,lastProfileAvatar,36);
  avatar.setOnClickListener(v->startActivity(new Intent(this,ProfilesActivity.class)));
  bar.addView(avatar,new LinearLayout.LayoutParams(dp(36),dp(36)));
  avatar.setContentDescription("Профіль: "+(name==null?"вибрати":name));
  return bar;
 }

 // ---------- data ----------
 static final String[][] ROWS={{"Нове на KIK","","last"},{"Нові фільми","films","last"},{"Нові серіали","series","last"},{"Популярне","","popular"},{"Зараз дивляться","","watching"},{"Мультфільми","cartoons","last"},{"Аніме","animation","last"}};
 private static long refreshedAt;
 private static final Map<String,String[]> DETAILS=new ConcurrentHashMap<String,String[]>();
 private void load(){
  final long started=SystemClock.elapsedRealtime();final int gen=++loadGeneration;final String profile=ProfileStore.current(this);
  new Thread(()->{
   final Recommendations.Snapshot preferences=Recommendations.snapshot(this,profile);
   final List<Row> local=localRows(profile);List<Row> cached=fromCache();
   final Set<String> links=new HashSet<String>();JSONArray la=Progress.later(this,profile);for(int k=0;k<la.length();k++){JSONObject o=la.optJSONObject(k);if(o!=null)links.add(norm(o.optString("link")));}
   Log.d("KIK.Load","Home data "+(SystemClock.elapsedRealtime()-started)+"ms");List<Row> all=homeRows(local,cached,preferences);translateNow(all);Log.d("KIK.Load","Home ranking "+(SystemClock.elapsedRealtime()-started)+"ms");
   final List<Row> first=all;
   main.post(()->{if(!valid(gen,profile))return;laterLinks.clear();laterLinks.addAll(links);if(!first.isEmpty()||loadedOnce)show(first);Log.d("KIK.Load","Home rendered "+(SystemClock.elapsedRealtime()-started)+"ms");translateLater(first,gen,profile);});
   long cacheAge=System.currentTimeMillis()-getSharedPreferences("kik_home",0).getLong("updated",0);
   if(!cached.isEmpty()&&(SystemClock.elapsedRealtime()-refreshedAt<15*60_000||cacheAge>=0&&cacheAge<15*60_000)){recommendLater(preferences,cached,gen,profile);return;}
   List<Row> site=siteRows(cached.isEmpty()?row->{final List<Row> initial=homeRows(local,Collections.singletonList(row),preferences);translateNow(initial);main.post(()->{
    if(!valid(gen,profile))return;show(initial);translateLater(initial,gen,profile);
   });}:null);
   if(site.isEmpty()){main.post(()->{if(valid(gen,profile)&&first.isEmpty()){heroTitle.setText("Що подивимось?");heroText.setText("Не вдалося завантажити новинки. Відкрийте каталог або спробуйте ще раз.");HeroActions.label(watch,"Спробувати ще",HeroActions.RETRY);watch.setOnClickListener(v->load());more.setVisibility(View.GONE);later.setVisibility(View.GONE);watch.requestFocus();}});return;}
   refreshedAt=SystemClock.elapsedRealtime();saveCache(site);
   if(cached.isEmpty()){final List<Row> rest=new ArrayList<Row>(Recommendations.withoutDisliked(preferences,site.subList(1,site.size())));translateNow(rest);main.post(()->{if(!valid(gen,profile))return;
    // The first catalogue row is already usable. Append remaining rows without replacing focus.
    appendRows(rest);translateLater(rest,gen,profile);
   });}
   recommendLater(preferences,site,gen,profile);
  },"kik-home").start();
 }
 private List<Row> homeRows(List<Row> local,List<Row> catalogue,Recommendations.Snapshot preferences){
  List<Row> out=new ArrayList<Row>(local);out.addAll(Recommendations.rows(this,preferences,catalogue));out.addAll(Recommendations.withoutDisliked(preferences,catalogue));return out;
 }
 private static String recommendationKeys(Row row){StringBuilder keys=new StringBuilder();for(Item i:row.items)keys.append(norm(i.link)).append('|');return keys.toString();}
 private void updateRecommendations(Row row){
  // Enrich the row in place, without moving the remote focus or changing the hero.
  LinearLayout line=recommendationLines.get(row.recommendationId);String signature=recommendationKeys(row);
  if(line==null||line.hasFocus()||row.items.isEmpty()||signature.equals(recommendationSignatures.get(row.recommendationId)))return;
  if(row.personalized)recommendedHeading.setText(row.title);line.removeAllViews();for(Item i:row.items)line.addView(card(i));recommendationSignatures.put(row.recommendationId,signature);
 }
 private boolean valid(int gen,String profile){return !isFinishing()&&!destroyed&&gen==loadGeneration&&profile.equals(ProfileStore.current(this));}
 private Set<String> words(List<Row> rows){Set<String> words=new LinkedHashSet<String>();for(Row r:rows)for(Item i:r.items){if(i.title!=null)words.add(i.title);if(i.genre!=null)words.add(i.genre);}return words;}
 /** Cached labels before the first frame; missing translations update later. */
 private void translateNow(List<Row> rows){mergeTranslations(TitleTranslate.cached(words(rows)));}
 private synchronized void mergeTranslations(Map<String,String> m){Map<String,String> n=new HashMap<String,String>(uk);n.putAll(m);uk=n;}
 private void translateLater(List<Row> rows,int gen,String profile){
  main.postDelayed(()->{if(!valid(gen,profile)||!foreground)return;new Thread(()->{Map<String,String> got=TitleTranslate.resolve(words(rows));main.post(()->{if(!valid(gen,profile))return;mergeTranslations(got);translateLabels(content);if(hero!=null){heroTitle.setText(tr(hero.title));heroMeta.setText(join(hero.year,tr(hero.genre),hero.rating==null?null:"★ "+hero.rating));}});},"kik-home-text").start();},800);
 }
 private static final class Label{final Item item;final boolean meta;Label(Item i,boolean m){item=i;meta=m;}}
 private void translateLabels(View v){if(v instanceof TextView&&v.getTag() instanceof Label){Label l=(Label)v.getTag();((TextView)v).setText(l.meta?(l.item.resume?l.item.sub:join(l.item.year,tr(l.item.genre))):tr(l.item.title));}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)translateLabels(((ViewGroup)v).getChildAt(i));}
 private void recommendLater(Recommendations.Snapshot preferences,List<Row> catalogue,int gen,String profile){
  main.postDelayed(()->{if(!valid(gen,profile)||!foreground)return;new Thread(()->{
   Recommendations.refresh(this,preferences);if(!valid(gen,profile)||!foreground)return;
   Recommendations.refreshAi(this,preferences,catalogue);if(!valid(gen,profile)||!foreground)return;
   Recommendations.enrich(this,preferences,catalogue,()->valid(gen,profile)&&foreground);
   if(!valid(gen,profile))return;final List<Row> updated=Recommendations.rows(this,preferences,catalogue);translateNow(updated);
   main.post(()->{if(valid(gen,profile)){for(Row r:updated)updateRecommendations(r);translateLater(updated,gen,profile);}});
  },"kik-home-recommendations").start();},1800);
 }
 private List<Row> localRows(String profile){
  List<Row> rows=new ArrayList<Row>();
  Row resume=new Row("Продовжити перегляд");
  for(Progress.Entry e:Progress.recent(this,profile)){
   if(e.finished()&&e.season==null)continue;
   Item i=new Item();i.link=e.link;i.title=e.title;i.poster=e.poster;i.resume=true;
   i.progress=e.dur>0?Math.min(1f,(float)e.pos/e.dur):0;
   i.sub=(e.season!=null&&!"0".equals(e.season)?"S"+e.season+"E"+e.episode+"  •  ":"")+(e.finished()?"наступна серія":"продовжити з "+Ui.time(e.pos));
   String[] d=DETAILS.get(norm(e.link));if(d!=null){i.description=d[0];i.wide=d[1];i.rating=d[2];i.year=d[3];i.genre=d[4];}
   resume.items.add(i);if(resume.items.size()>=20)break;
  }
  if(!resume.items.isEmpty())rows.add(resume);
  Row saved=new Row("Переглянути пізніше");JSONArray a=Progress.later(this,profile);
  for(int k=0;k<a.length();k++){JSONObject o=a.optJSONObject(k);if(o==null)continue;Item i=new Item();i.link=o.optString("link",null);i.title=o.optString("title",null);i.poster=o.optString("poster",null);i.year=o.optString("year",null);i.genre=o.optString("genre",null);if(i.link!=null&&i.title!=null)saved.items.add(i);}
  if(!saved.items.isEmpty())rows.add(saved);
  return rows;
 }
 private List<Row> siteRows(java.util.function.Consumer<Row> first){
  final Row[] out=new Row[ROWS.length];List<Thread> threads=new ArrayList<Thread>();
  for(int k=0;k<ROWS.length;k++){final int idx=k;Thread t=new Thread(()->{
   try{Row r=new Row(ROWS[idx][0]);for(Site.Card c:Site.list(ROWS[idx][1],null,ROWS[idx][2],1)){Item i=new Item();i.link=c.link;i.title=c.title;i.poster=c.poster;i.year=c.year();i.genre=c.genre();r.items.add(i);if(r.items.size()>=15)break;}out[idx]=r;}
   catch(Exception e){Log.w("KIK","Home row "+ROWS[idx][0]+": "+e);}});
   threads.add(t);t.start();
   if(k==0)try{t.join();}catch(InterruptedException ignored){} // the first request passes the bot check for the rest
  }
  if(first!=null&&out[0]!=null&&!out[0].items.isEmpty())first.accept(out[0]);
  for(Thread t:threads)try{t.join(25000);}catch(InterruptedException ignored){}
  List<Row> rows=new ArrayList<Row>();for(Row r:out)if(r!=null&&!r.items.isEmpty())rows.add(r);try{Row ua=new Row("Українською");for(Site.Card c:FreeSources.cards(null)){Item i=new Item();i.link=c.link;i.title=c.title;i.poster=c.poster;i.genre="Українською";i.sub="Українська озвучка";ua.items.add(i);if(ua.items.size()>=15)break;}if(!ua.items.isEmpty())rows.add(ua);}catch(Exception ignored){}if(first!=null&&!rows.isEmpty()&&(out[0]==null||out[0].items.isEmpty()))first.accept(rows.get(0));return rows;
 }
 private void saveCache(List<Row> rows){
  JSONArray a=new JSONArray();
  try{for(Row r:rows){JSONArray items=new JSONArray();for(Item i:r.items)items.put(new JSONObject().put("link",i.link).put("title",i.title).put("poster",i.poster).put("year",i.year).put("genre",i.genre));a.put(new JSONObject().put("title",r.title).put("items",items));}}catch(JSONException ignored){}
  getSharedPreferences("kik_home",0).edit().putString("rows",a.toString()).putLong("updated",System.currentTimeMillis()).apply();
 }
 private List<Row> fromCache(){
  List<Row> rows=new ArrayList<Row>();
  try{JSONArray a=new JSONArray(getSharedPreferences("kik_home",0).getString("rows","[]"));
   for(int k=0;k<a.length();k++){JSONObject o=a.getJSONObject(k);Row r=new Row(o.getString("title"));JSONArray items=o.getJSONArray("items");
    for(int n=0;n<items.length();n++){JSONObject x=items.getJSONObject(n);Item i=new Item();i.link=x.optString("link");i.title=x.optString("title");i.poster=x.optString("poster",null);i.year=x.optString("year",null);i.genre=x.optString("genre",null);
     String[] d=DETAILS.get(norm(i.link));if(d!=null){i.description=d[0];i.wide=d[1];i.rating=d[2];}r.items.add(i);}
    rows.add(r);}
  }catch(JSONException ignored){}
  return rows;
 }
 /** Fetches the film page for the hero's description, large picture and rating once the focus settles. */
 private Runnable detailTask=()->{};
 private void details(final Item i){
  main.removeCallbacks(detailTask);
  if(i.description!=null)return;
  detailTask=()->new Thread(()->{
   try{Site.Film f=Site.film(i.link);String d=f.description==null?"":DescTranslate.tr(f.description,Ui.TRANSLATE_WAIT);mergeTranslations(TitleTranslate.resolve(f.genres.isEmpty()?Collections.<String>emptySet():Collections.singleton(f.genres.get(0)),Ui.TRANSLATE_WAIT));
    final String[] g=new String[]{d,f.poster,f.rating,f.year,f.genres.isEmpty()?null:f.genres.get(0)};DETAILS.put(norm(i.link),g);
    main.post(()->{i.description=g[0];if(g[1]!=null)i.wide=g[1];if(g[2]!=null)i.rating=g[2];if(i.year==null)i.year=g[3];if(i.genre==null)i.genre=g[4];
     if(!destroyed&&hero==i){heroText.setText(i.description);heroMeta.setText(join(i.year,tr(i.genre),i.rating==null?null:"★ "+i.rating));heroText.setAlpha(0.3f);heroText.animate().alpha(1f).setDuration(260).start();if(i.wide!=null)Images.crossfade(i.wide,heroImage);}});
    String translated=DescTranslate.tr(f.description);Map<String,String> got=TitleTranslate.resolve(Collections.singleton(g[4]));
    main.post(()->{if(destroyed)return;mergeTranslations(got);g[0]=translated;i.description=translated;if(hero==i){heroText.setText(translated);heroMeta.setText(join(i.year,tr(i.genre),i.rating==null?null:"★ "+i.rating));}});
   }catch(Exception e){Log.w("KIK","Hero details: "+e);}
  },"kik-hero").start();
  main.postDelayed(detailTask,600);
 }
 private static String norm(String link){return Progress.norm(link);}
 private static String time(long ms){return Ui.time(ms);}

 // ---------- ui ----------
 private void show(List<Row> rows){
  boolean initial=!loadedOnce;loadedOnce=true;boolean actionFocused=watch.hasFocus()||later.hasFocus()||more.hasFocus();
  recommendedLine=null;recommendedHeading=null;recommendationKeys="";
  recommendationLines.clear();recommendationSignatures.clear();tasteChoices=new ArrayList<Item>();Set<String> choices=new HashSet<String>();for(int pos=0;pos<15&&tasteChoices.size()<40;pos++)for(Row r:rows)if(pos<r.items.size()){Item i=r.items.get(pos);if(choices.add(norm(i.link)))tasteChoices.add(i);if(tasteChoices.size()>=40)break;}
  while(content.getChildCount()>2)content.removeViewAt(2);
  View first=null;
  View again=null;
  View[] focus=appendRowViews(rows);first=focus[0];again=focus[1];
  space(content,40);
  if(rows.isEmpty()){heroTitle.setText("Ласкаво просимо");heroText.setText("Завантажуємо новинки… Тим часом можна відкрити «Фільми», «Серіали» або «Пошук».");watch.setVisibility(View.GONE);more.setVisibility(View.GONE);return;}
  watch.setVisibility(View.VISIBLE);more.setVisibility(View.VISIBLE);later.setVisibility(View.VISIBLE);
  watch.setOnClickListener(v->{if(hero!=null){startActivity(new Intent(this,FilmActivity.class).putExtra("link",hero.link).putExtra("title",tr(hero.title)).putExtra("poster",hero.poster).putExtra("play",true));overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}});
  if(pendingHero!=null){Item keep=pendingHero;pendingHero=null;hero=null;setHero(keep);later.requestFocus();return;}
  if(again!=null&&!actionFocused){((ViewGroup)again).getChildAt(0).requestFocus();return;}
  setHero(rows.get(0).items.get(0));
  if(initial){watch.requestFocus();scroll.scrollTo(0,0);}else if(actionFocused)watch.requestFocus();
 }

 private View[] appendRowViews(List<Row> rows){View[] focus=new View[2];
  for(Row r:rows){
   TextView h=text(r.title,18,Color.WHITE);h.setTypeface(Typeface.DEFAULT_BOLD);h.setPadding(dp(40),dp(8),0,dp(2));h.setSingleLine(true);h.setEllipsize(TextUtils.TruncateAt.END);
   if(r.personalized){LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setClipChildren(false);header.setClipToPadding(false);header.setPadding(0,dp(4),dp(40),dp(4));header.addView(h,new LinearLayout.LayoutParams(0,-2,1));TextView customize=Ui.chip(this,"Налаштувати смак");header.addView(customize,new LinearLayout.LayoutParams(-2,dp(44)));customize.setOnClickListener(v->chooseTaste());content.addView(header);}else content.addView(h);
   HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setClipToPadding(false);hs.setPadding(dp(32),dp(6),dp(32),dp(6));
   LinearLayout line=new LinearLayout(this);line.setOrientation(0);line.setClipChildren(false);line.setClipToPadding(false);hs.addView(line);content.addView(hs);
   if(r.personalized){recommendedLine=line;recommendedHeading=h;recommendationKeys=recommendationKeys(r);}
   if(r.recommendationId!=null){recommendationLines.put(r.recommendationId,line);recommendationSignatures.put(r.recommendationId,recommendationKeys(r));}
   for(Item i:r.items){View c=card(i);line.addView(c);if(focus[0]==null)focus[0]=c;if(focus[1]==null&&focusedLink!=null&&focusedLink.equals(i.link))focus[1]=c;}
  }
  return focus;
 }
 private void appendRows(List<Row> rows){if(rows.isEmpty())return;content.removeViewAt(content.getChildCount()-1);appendRowViews(rows);space(content,40);for(Row r:rows)for(Item i:r.items)if(tasteChoices.size()<40)tasteChoices.add(i);}

 private View card(final Item i){
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setClipChildren(false);box.setClipToPadding(false);box.setPadding(dp(8),dp(8),dp(8),dp(6));
  FrameLayout frame=new FrameLayout(this);frame.setFocusable(true);frame.setClickable(true);
  // Rounded corners come from a shader-drawn poster, not outline clipping (costly on this GPU).
  ImageView img=new PosterView(this,dp(8));frame.addView(img,new FrameLayout.LayoutParams(-1,-1));
  final View ring=new View(this);ring.setBackground(shape(0,dp(8),dp(3),RED));ring.setAlpha(0f);frame.addView(ring,new FrameLayout.LayoutParams(-1,-1));
  if(i.rating!=null){TextView r=text("★ "+i.rating,12,Color.WHITE);r.setPadding(dp(6),dp(2),dp(6),dp(2));r.setBackground(shape(0xcc000000,dp(4),0,0));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);p.setMargins(0,dp(6),dp(6),0);frame.addView(r,p);}
  if(i.resume){View track=new View(this);track.setBackgroundColor(0xff77777b);frame.addView(track,new FrameLayout.LayoutParams(-1,dp(3),Gravity.BOTTOM));View bar=new View(this);bar.setBackgroundColor(RED);frame.addView(bar,new FrameLayout.LayoutParams(Math.max(dp(3),Math.round(dp(124)*i.progress)),dp(3),Gravity.BOTTOM|Gravity.START));}
  box.addView(frame,new LinearLayout.LayoutParams(dp(124),dp(164)));
  TextView t=text(tr(i.title),14,0xffe6e6ea);t.setTag(new Label(i,false));t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);t.setPadding(dp(2),dp(8),dp(2),0);
  box.addView(t,new LinearLayout.LayoutParams(dp(124),-2));
  TextView s=text(i.resume?i.sub:join(i.year,tr(i.genre)),12,GREY);s.setTag(new Label(i,true));s.setSingleLine(true);s.setEllipsize(TextUtils.TruncateAt.END);s.setPadding(dp(2),dp(2),dp(2),0);
  box.addView(s,new LinearLayout.LayoutParams(dp(124),-2));
  frame.setContentDescription(tr(i.title));
  frame.setOnFocusChangeListener((v,f)->{
   Ui.focus(v,f,1.05f);ring.animate().alpha(f?1f:0f).setDuration(140).start();
   t.setTextColor(f?Color.WHITE:0xffe6e6ea);
   if(f){final String profile=ProfileStore.current(this);Ui.bg(()->RatingStore.expose(this,profile,i.link));focusedLink=i.link;main.removeCallbacks(heroTask);heroTask=()->setHero(i);main.postDelayed(heroTask,200);if(box.getParent()!=null){final HorizontalScrollView hs=(HorizontalScrollView)box.getParent().getParent();scrollRowIntoView(hs);hs.post(()->hs.smoothScrollTo(Math.max(0,box.getLeft()-dp(32)),0));}}
  });
  frame.setOnClickListener(v->open(i));
  frame.setOnLongClickListener(v->{rate(i);return true;});
  Images.load(i.poster,img,dp(136));
  return box;
 }
 private void rate(Item i){final String profile=ProfileStore.current(this);int value=RatingStore.get(this,profile,i.link);Ui.dialog(this).setTitle(tr(i.title)).setSingleChoiceItems(new String[]{"Подобається","Не подобається","Без оцінки"},value>0?0:value<0?1:2,(d,n)->{boolean saved=RatingStore.set(this,profile,i.link,tr(i.title),i.poster,i.genre,n==0?1:n==1?-1:0);d.dismiss();if(saved)load();else Ui.toast(this,"Не вдалося зберегти оцінку");}).setNegativeButton("Назад",null).show();}
 private void chooseTaste(){TasteActivity.open(this,false);}
 private void scrollRowIntoView(View row){
  int screen=getResources().getDisplayMetrics().heightPixels;
  scroll.smoothScrollTo(0,Math.max(0,row.getBottom()-screen+dp(48)));
 }

 private void setHero(Item i){
  if(i==hero)return;hero=i;expanded=false;more.setText("Детальніше");heroText.setLines(2);
  heroTitle.setText(tr(i.title));
  heroMeta.setText(join(i.year,tr(i.genre),i.rating==null?null:"★ "+i.rating));
  heroSub.setText(i.resume?i.sub:"");heroSub.setVisibility(i.resume?View.VISIBLE:View.INVISIBLE);
  HeroActions.label(watch,i.resume?"Продовжити":"Дивитися",HeroActions.PLAY);
  paintLater();
  heroText.setText(i.description==null?"":i.description);details(i);
  Images.crossfade(i.wide!=null?i.wide:i.poster,heroImage);
  for(View v:new View[]{heroTitle,heroMeta,heroSub,heroText}){v.animate().cancel();v.setAlpha(0.35f);v.animate().alpha(1f).setDuration(240).setInterpolator(EASE).withLayer().start();}
 }

 // ---------- watch later ----------
 private void toggleLater(Item i){
  if(i==null||i.link==null)return;
  boolean on=Progress.toggleLater(this,i.link,tr(i.title),i.poster,i.year,tr(i.genre));
  if(on)laterLinks.add(norm(i.link));else laterLinks.remove(norm(i.link));
  paintLater();
  HeroActions.focus(later,later.isFocused());Ui.toast(this,on?"Додано до мого списку":"Прибрано з мого списку");
  pendingHero=hero;load();
 }
 private void paintLater(){if(hero==null)return;boolean saved=laterLinks.contains(norm(hero.link));HeroActions.label(later,saved?"У списку":"Мій список",saved?HeroActions.CHECK:HeroActions.ADD);later.setSelected(saved);}

 private String tr(String s){if(s==null)return null;String t=uk.get(s);return t==null?TitleTranslate.shown(s):t;}
 private static String join(String... parts){StringBuilder b=new StringBuilder();for(String p:parts)if(p!=null&&!p.isEmpty()){if(b.length()>0)b.append("  •  ");b.append(p);}return b.toString();}

 private void open(Item i){
  if(i==null||i.link==null)return;
  FilmActivity.open(this,i.link,tr(i.title),i.poster);overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
 }

 private TextView menu(String label,View.OnClickListener l){
  TextView t=text(label,14,0xffd0d0d4);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);t.setFocusable(true);t.setClickable(true);t.setPadding(dp(12),dp(9),dp(12),dp(9));
  t.setBackground(states(shape(0,dp(24),0,0),shape(Color.WHITE,dp(24),dp(2),RED)));t.setTextColor(Ui.buttonText(false));
  t.setOnFocusChangeListener((v,f)->{Ui.focus(v,f,1.04f);if(f)scroll.smoothScrollTo(0,0);});
  t.setOnClickListener(l);return t;
 }
 private Button action(String label,int icon,boolean primary){
  Button b=HeroActions.button(this,label,icon,primary);
  b.setOnFocusChangeListener((v,f)->{HeroActions.focus(v,f);if(f)scroll.smoothScrollTo(0,0);});
  return b;
 }
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 private void space(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 private static StateListDrawable states(Drawable normal,Drawable focused){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},focused);s.addState(new int[]{},normal);return s;}
 private static GradientDrawable shape(int fill,int radius,int stroke,int strokeColor){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(radius);if(stroke>0)d.setStroke(stroke,strokeColor);return d;}
 static int avatarColor(String name){int[] c={0xffe50914,0xff2b7de9,0xff1fa45b,0xfff5a623,0xff8e44ad,0xff16a2b8};return c[Math.abs((name==null?0:name.hashCode())%c.length)];}

 /** Poster with rounded corners drawn through a BitmapShader (centre-cropped), with a placeholder while loading. */
 static final class PosterView extends ImageView{
  private final float r;private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
  PosterView(Context c,float radius){super(c);r=radius;fill.setColor(CARD);setScaleType(ScaleType.FIT_XY);}
  @Override public void setImageBitmap(Bitmap b){setImageDrawable(b==null?null:new Round(b,r));}
  @Override protected void onDraw(Canvas c){if(getDrawable()==null)c.drawRoundRect(0,0,getWidth(),getHeight(),r,r,fill);else super.onDraw(c);}
 }
 static final class Round extends Drawable{
  private final Bitmap b;private final float r;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final RectF box=new RectF();
  Round(Bitmap b,float r){this.b=b;this.r=r;p.setShader(new BitmapShader(b,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));}
  @Override protected void onBoundsChange(Rect bounds){
   box.set(bounds);float s=Math.max(bounds.width()/(float)b.getWidth(),bounds.height()/(float)b.getHeight());
   Matrix m=new Matrix();m.setScale(s,s);m.postTranslate(bounds.left+(bounds.width()-b.getWidth()*s)/2f,bounds.top+(bounds.height()-b.getHeight()*s)/2f);p.getShader().setLocalMatrix(m);
  }
  @Override public void draw(Canvas c){c.drawRoundRect(box,r,r,p);}
  @Override public void setAlpha(int a){p.setAlpha(a);}
  @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }

 /** Minimal poster loader: background fetch, downsampled decode, shared memory cache. */
 static final class Images{
  static void clear(){MEM.evictAll();File dir=imageDir();File[] files=dir==null?null:dir.listFiles();if(files!=null)for(File f:files)f.delete();}
  private static final ExecutorService POOL=Executors.newFixedThreadPool(4);
  private static final ExecutorService HERO_POOL=Executors.newFixedThreadPool(2);
  private static File imageDir(){if(UiRuntime.app==null)return null;return new File(UiRuntime.app.getCacheDir(),"kik-posters");}
  private static byte[] imageBytes(String url)throws Exception{
   File dir=imageDir(),file=null;
   if(dir!=null){dir.mkdirs();byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8"));StringBuilder key=new StringBuilder();for(byte x:digest)key.append(String.format("%02x",x));file=new File(dir,key.toString());
    if(file.isFile()&&file.length()>0&&System.currentTimeMillis()-file.lastModified()<7L*24*60*60*1000){try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toByteArray();}}
   }
   HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent",Site.UA);
   byte[] data;try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[16384];int n;while((n=in.read(buf))>0){out.write(buf,0,n);if(out.size()>8*1024*1024)throw new IOException("Poster exceeds cache limit");}data=out.toByteArray();}finally{c.disconnect();}
   BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Invalid poster");
   if(file!=null){try{File tmp=File.createTempFile("poster-",".tmp",dir);try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);}if(!tmp.renameTo(file))tmp.delete();trimImages(dir);}catch(IOException ignored){}}
   return data;
  }
  private static synchronized void trimImages(File dir){File[] files=dir.listFiles();if(files==null)return;long size=0;for(File f:files)size+=f.length();if(size<=32L*1024*1024)return;Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));for(File f:files){long length=f.length();if(f.delete())size-=length;if(size<=24L*1024*1024)break;}}

  private static final LruCache<String,Bitmap> MEM=new LruCache<String,Bitmap>(48*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
  private static final Handler MAIN=new Handler(Looper.getMainLooper());
  /** Keep every edge of portrait and landscape artwork; bake fades into one opaque bitmap. */
  static Bitmap backdrop(Bitmap src,int heroW,int heroH){
   Bitmap out=Bitmap.createBitmap(heroW,heroH,Bitmap.Config.RGB_565);Canvas c=new Canvas(out);c.drawColor(BG);
   BackdropLayout art=BackdropLayout.fit(src.getWidth(),src.getHeight(),heroW,heroH);
   RectF bounds=new RectF(art.left,art.top,art.left+art.width,art.top+art.height);
   c.drawBitmap(src,null,bounds,new Paint(Paint.FILTER_BITMAP_FLAG));
   Paint g=new Paint();
   // Blend the image's own edges, including narrow posters, into the surrounding graphite.
   g.setShader(new LinearGradient(bounds.left,0,bounds.left+art.width*0.18f,0,new int[]{BG,0x000d0d0f},null,Shader.TileMode.CLAMP));c.drawRect(bounds,g);
   g.setShader(new LinearGradient(bounds.right-art.width*0.06f,0,bounds.right,0,new int[]{0x000d0d0f,BG},null,Shader.TileMode.CLAMP));c.drawRect(bounds,g);
   g.setShader(new LinearGradient(0,bounds.top,0,bounds.top+art.height*0.06f,new int[]{BG,0x000d0d0f},null,Shader.TileMode.CLAMP));c.drawRect(bounds,g);
   g.setShader(new LinearGradient(0,0,heroW,0,new int[]{0xff0d0d0f,0xf20d0d0f,0x7a0d0d0f,0x200d0d0f},new float[]{0f,0.28f,0.63f,1f},Shader.TileMode.CLAMP));c.drawRect(0,0,heroW,heroH,g);
   g.setShader(new LinearGradient(0,heroH*0.45f,0,heroH,new int[]{0x000d0d0f,0xff0d0d0f},null,Shader.TileMode.CLAMP));c.drawRect(0,0,heroW,heroH,g);
   g.setShader(new LinearGradient(0,0,0,heroH*0.3f,new int[]{0x990d0d0f,0x000d0d0f},null,Shader.TileMode.CLAMP));c.drawRect(0,0,heroW,heroH,g);
   return out;
  }
  static void load(String url,final ImageView view,final int width){
   if(url==null||url.isEmpty()){view.setImageDrawable(null);return;}
   final String u=url.startsWith("//")?"https:"+url:url.startsWith("/")?"https://"+Site.host()+url:url;
   final String key=u+"@poster:"+width;view.setTag(key);
   Bitmap hit=MEM.get(key);if(hit!=null){view.setImageBitmap(hit);return;}
   view.setImageDrawable(null);
   new VisiblePoster(u,key,width,view).attach();
  }
  /** Off-screen catalogue posters must not consume the connection while the viewer starts video. */
  private static final class VisiblePoster implements View.OnAttachStateChangeListener,ViewTreeObserver.OnScrollChangedListener,ViewTreeObserver.OnGlobalLayoutListener{
   final String url,key;final int width;final ImageView view;boolean started;ViewTreeObserver observer;
   VisiblePoster(String u,String k,int w,ImageView v){url=u;key=k;width=w;view=v;}
   void attach(){view.addOnAttachStateChangeListener(this);if(view.isAttachedToWindow())onViewAttachedToWindow(view);}
   public void onViewAttachedToWindow(View v){observer=view.getViewTreeObserver();observer.addOnScrollChangedListener(this);observer.addOnGlobalLayoutListener(this);view.post(this::attempt);}
   public void onViewDetachedFromWindow(View v){remove();}
   void remove(){if(observer!=null&&observer.isAlive()){observer.removeOnScrollChangedListener(this);observer.removeOnGlobalLayoutListener(this);}observer=null;}
   public void onScrollChanged(){attempt();}public void onGlobalLayout(){attempt();}
   void attempt(){if(started)return;if(!key.equals(view.getTag())){remove();view.removeOnAttachStateChangeListener(this);return;}Rect rect=new Rect();if(!view.isShown()||!view.getGlobalVisibleRect(rect)||rect.width()==0||rect.height()==0)return;started=true;remove();view.removeOnAttachStateChangeListener(this);fetch(url,key,width,0,view,false);}
  }
  /** Hero backdrop: keeps the current picture until the next one is ready, then crossfades. */
  static void crossfade(String url,final ImageView view){
   if(url==null||url.isEmpty())return;
   // Capture this view's size now: opening Film must not change Home's in-flight artwork.
   ViewGroup.LayoutParams lp=view.getLayoutParams();
   int viewWidth=view.getWidth()>0?view.getWidth():lp!=null&&lp.width>0?lp.width:view.getResources().getDisplayMetrics().widthPixels;
   int viewHeight=lp!=null&&lp.height>0?lp.height:view.getHeight();
   if(viewHeight<=0){view.post(()->crossfade(url,view));return;}
   final int width=Math.max(1,viewWidth/2),height=Math.max(1,viewHeight/2);
   final String u=url.startsWith("//")?"https:"+url:url.startsWith("/")?"https://"+Site.host()+url:url;
   final String key=u+"@hero-fit-v2:"+width+"x"+height;if(key.equals(view.getTag()))return;view.setTag(key);
   Bitmap hit=MEM.get(key);if(hit!=null){swap(view,hit);return;}
   fetch(u,key,width,height,view,true);
  }
  private static void swap(ImageView view,Bitmap b){
   Drawable old=view.getDrawable();
   if(old==null){view.setImageBitmap(b);view.setAlpha(0f);view.animate().alpha(1f).setDuration(400).start();return;}
   if(old instanceof TransitionDrawable)old=((TransitionDrawable)old).getDrawable(1);
   TransitionDrawable t=new TransitionDrawable(new Drawable[]{old,new BitmapDrawable(view.getResources(),b)});t.setCrossFadeEnabled(true);
   view.setImageDrawable(t);t.startTransition(320);
  }
  private static void fetch(final String u,final String key,final int width,final int height,final ImageView view,final boolean cross){
   (cross?HERO_POOL:POOL).execute(()->{
    Bitmap b=null;
    try{
     byte[] data=imageBytes(u);
     BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);
     int s=1;while(o.outWidth/(s*2)>=width)s*=2;
     o=new BitmapFactory.Options();o.inSampleSize=s;o.inPreferredConfig=Bitmap.Config.RGB_565;b=BitmapFactory.decodeByteArray(data,0,data.length,o);
     if(b!=null&&cross)b=backdrop(b,width,height);
     if(b!=null)b.prepareToDraw();
     if(b!=null)MEM.put(key,b);else Log.w("KIK","Poster not decoded "+u+" bytes="+data.length);
    }catch(Exception e){Log.w("KIK","Poster "+u+": "+e);}
    final Bitmap bitmap=b;
    MAIN.post(()->{if(!key.equals(view.getTag())||bitmap==null)return;if(cross)swap(view,bitmap);else{view.setImageBitmap(bitmap);view.setAlpha(0f);view.animate().alpha(1f).setDuration(220).start();}});
   });
  }
 }
}
