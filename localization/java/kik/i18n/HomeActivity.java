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

 static final class Item{String link,title,poster,wide,year,genre,rating,age,duration,sub,heroLine,description;boolean resume,wideArt;float progress;long left;}
 static final class Row{String title,recommendationId;boolean personalized;List<Item> items=new ArrayList<Item>();Row(String t){title=t;}}

 private FrameLayout root;private ImageView heroImage;private LinearLayout content,heroInfo;private ScrollView scroll;private Oops oops;private volatile Throwable rowError;
 private TextView heroTitle,heroMeta,heroSub,heroText;private LinearLayout heroResume;private View heroBar;private Button watch,more,later;private volatile int loadGeneration;private volatile boolean destroyed;
 private Item hero;private String focusedLink;private Runnable heroTask=()->{};private final Set<String> laterLinks=new HashSet<String>();
 private static final android.view.animation.Interpolator EASE=new android.view.animation.DecelerateInterpolator(1.6f);private boolean expanded,loadedOnce;
 private final Handler main=new Handler(Looper.getMainLooper());
 private volatile Map<String,String> uk=new HashMap<String,String>();
 private LinearLayout menuRow;private LinearLayout recommendedLine;private TextView recommendedHeading;private String recommendationKeys="";
 /** The heading and the line of every row on screen, by title, and the rows themselves: one row can be redrawn alone. */
 private final Map<String,View[]> rowViews=new HashMap<String,View[]>();private List<Row> shownRows=new ArrayList<Row>();
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
  LinearLayout info=heroInfo=new LinearLayout(this);info.setOrientation(1);info.setClipChildren(false);info.setClipToPadding(false);info.setPadding(dp(40),dp(6),dp(40),dp(12));info.setMinimumHeight(dp(200));info.setGravity(Gravity.BOTTOM);
  heroTitle=text("",32,Color.WHITE);heroTitle.setTypeface(Typeface.DEFAULT_BOLD);heroTitle.setLetterSpacing(-0.01f);heroTitle.setShadowLayer(dp(10),0,dp(1),0x99000000);heroTitle.setSingleLine(true);heroTitle.setEllipsize(TextUtils.TruncateAt.END);
  heroMeta=text("",15,0xffd8d8dc);heroMeta.setTypeface(Ui.MEDIUM);heroMeta.setSingleLine(true);heroMeta.setEllipsize(TextUtils.TruncateAt.END);heroSub=text("",14,0xffd8d8dc);heroSub.setSingleLine(true);
  heroText=text("",14,0xffd0d0d4);heroText.setLines(2);heroText.setEllipsize(TextUtils.TruncateAt.END);heroText.setLineSpacing(0,1.12f);
  info.addView(heroTitle,new LinearLayout.LayoutParams(Ui.contentWidth(this,650,80),-2));space(info,5);info.addView(heroMeta);space(info,3);// Resume state reads like Netflix: a thin progress bar, then "Сезон 32 · Серія 1 · ще 15 хв".
  heroResume=new LinearLayout(this);heroResume.setOrientation(0);heroResume.setGravity(Gravity.CENTER_VERTICAL);
  FrameLayout track=new FrameLayout(this);track.setBackground(Ui.shape(0xff4a4a50,dp(2),0,0));heroBar=new View(this);heroBar.setBackground(Ui.shape(RED,dp(2),0,0));track.addView(heroBar,new FrameLayout.LayoutParams(0,-1));
  LinearLayout.LayoutParams trackLp=new LinearLayout.LayoutParams(dp(120),dp(4));trackLp.setMargins(0,0,dp(12),0);heroResume.addView(track,trackLp);heroResume.addView(heroSub);
  info.addView(heroResume);space(info,8);
  info.addView(heroText,new LinearLayout.LayoutParams(Ui.contentWidth(this,500,80),-2));space(info,14);
  LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(0);buttons.setClipChildren(false);buttons.setClipToPadding(false);
  // The row's padding leaves room for the focus ring and zoom; pulled back by it, the buttons start where the title does, as on the film page.
  LinearLayout.LayoutParams buttonsLp=new LinearLayout.LayoutParams(-1,-2);buttonsLp.setMargins(-dp(8),0,-dp(8),0);info.addView(Ui.horizontal(this,buttons),buttonsLp);
  watch=action("Дивитися",HeroActions.PLAY,true);buttons.addView(watch,new LinearLayout.LayoutParams(-2,dp(48)));
  later=action("Мій список",HeroActions.ADD,false);LinearLayout.LayoutParams lp0=new LinearLayout.LayoutParams(-2,dp(48));lp0.setMargins(dp(10),0,0,0);buttons.addView(later,lp0);
  later.setOnClickListener(v->toggleLater(hero));
  more=action("",HeroActions.INFO,false);HeroActions.round(more);more.setContentDescription("Детальніше");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(48),dp(48));lp.setMargins(dp(10),0,0,0);buttons.addView(more,lp);
  watch.setOnClickListener(v->{if(hero!=null)startActivity(new Intent(this,FilmActivity.class).putExtra("link",hero.link).putExtra("title",tr(hero.title)).putExtra("poster",hero.poster).putExtra("play",true));});
  more.setOnClickListener(v->open(hero));
  // No extra controls: ◀ past the first button and ▶ past the last one turn the slides, as on Disney+ and Prime Video.
  watch.setOnKeyListener((v,k,e)->k==KeyEvent.KEYCODE_DPAD_LEFT&&e.getAction()==KeyEvent.ACTION_DOWN&&turn(-1));
  more.setOnKeyListener((v,k,e)->k==KeyEvent.KEYCODE_DPAD_RIGHT&&e.getAction()==KeyEvent.ACTION_DOWN&&turn(1));
  dots=new LinearLayout(this);dots.setOrientation(0);dots.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-2,dp(6));dl.topMargin=dp(16);info.addView(dots,dl);
  content.addView(info);
  Ui.enter(info,60);
  heroText.setText("Завантаження…");
  ProfileStore.askOnce(this);
  if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::onBackPressed);
  load();
 }

 private long cloudRevision=CloudSync.revision();
 private final Runnable cloudTick=new Runnable(){public void run(){if(destroyed||!foreground)return;CloudSync.now(HomeActivity.this);long next=CloudSync.revision();if(next!=cloudRevision){cloudRevision=next;load();}main.postDelayed(this,15000);}};
 @Override protected void onResume(){super.onResume();foreground=true;AppUpdater.resumed(this);AppUpdater.prompt(this);AppUpdater.lookup(this,this::paintSettingsBadge);CloudSync.changed(this);main.removeCallbacks(cloudTick);main.postDelayed(cloudTick,15000);if(loadedOnce){String n=ProfileStore.nameOf(this,ProfileStore.current(this)),avatar=ProfileStore.avatarOf(this,ProfileStore.current(this));if(!java.util.Objects.equals(n,lastProfileName)||!java.util.Objects.equals(avatar,lastProfileAvatar)){content.removeViewAt(0);content.addView(topBar(),0);}load();}}
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
  if(now-backAt<3000){Ui.leaveApp(this);return;}
  backAt=now;
  if(backHint==null){backHint=text("Натисніть «Назад» ще раз, щоб вийти",16,Color.WHITE);backHint.setPadding(dp(22),dp(10),dp(22),dp(10));backHint.setBackground(shape(0xe61b1b1f,dp(22),0,0));
   root.addView(backHint,new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL){{bottomMargin=dp(40);}});}
  backHint.animate().cancel();backHint.setAlpha(0f);backHint.setTranslationY(dp(16));
  backHint.animate().alpha(1f).translationY(0).setDuration(240).withEndAction(()->backHint.animate().alpha(0f).setStartDelay(2300).setDuration(300).start()).start();
 }

 /** The catalogue's four sections drop down right under «Каталог ▾»: no separate window, no scrolling; Back closes it. */
 private void catalogueMenu(View anchor){
  final String[] names={"Фільми","Серіали","Мультфільми","Аніме"},ids={"films","series","cartoons","animation"};
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(0);panel.setPadding(dp(8),dp(8),dp(8),dp(8));panel.setBackground(Ui.menuPanel(this));
  LinearLayout list=new LinearLayout(this);list.setOrientation(1);panel.addView(list);
  final PopupWindow popup=new PopupWindow(panel,ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,true);
  for(int i=0;i<names.length;i++){final int n=i;TextView item=Ui.menuItem(this,names[i],false);list.addView(item,new LinearLayout.LayoutParams(dp(180),-2));
   item.setOnClickListener(v->{popup.dismiss();BrowseActivity.open(this,"catalog",ids[n],names[n]);});}
  popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);popup.setElevation(dp(10));popup.setAnimationStyle(android.R.style.Animation_Dialog);
  View line=new View(this);line.setBackgroundColor(0x33ffffff);LinearLayout.LayoutParams lineLp=new LinearLayout.LayoutParams(dp(1),-1);lineLp.setMargins(dp(10),dp(4),dp(10),dp(4));panel.addView(line,lineLp);
  LinearLayout genres=new LinearLayout(this);genres.setOrientation(1);panel.addView(genres);
  popup.showAsDropDown(anchor,-dp(6),dp(4));list.getChildAt(0).requestFocus();
  fillGenres(genres,popup);
 }
 /** Film genres from the source, fetched once per run; the home rows and the «Жанри» menu share them. */
 private static volatile List<Site.Genre> filmGenres;
 private static List<Site.Genre> filmGenres(){List<Site.Genre> g=filmGenres;if(g!=null&&!g.isEmpty())return g;try{g=Site.genres("films");}catch(Exception e){Log.w("KIK","Genres: "+e);return new ArrayList<Site.Genre>();}if(!g.isEmpty())filmGenres=g;return g;}
 /** Ukrainian names for the genres the source spells in Russian. */
 static final String[][] GENRE_NAMES={{"Комедии","Комедії"},{"Боевики","Бойовики"},{"Ужасы","Жахи"},{"Фантастика","Фантастика"},{"Драмы","Драми"},{"Триллеры","Трилери"},{"Детективы","Детективи"},{"Приключения","Пригоди"},{"Мелодрамы","Мелодрами"},{"Документальные","Документальні"},{"Семейные","Сімейні"},{"Криминал","Кримінал"},{"Фэнтези","Фентезі"},{"Военные","Воєнні"},{"Исторические","Історичні"},{"Биографические","Біографічні"},{"Спортивные","Спортивні"},{"Музыкальные","Музичні"},{"Вестерны","Вестерни"},{"Мюзиклы","Мюзикли"},{"Артхаус","Артхаус"},{"Короткометражные","Короткометражні"},{"Эротика","Еротика"},{"Концерт","Концерти"},{"Стендап","Стендап"},{"Детские","Дитячі"},{"Зарубежные","Зарубіжні"},{"Театр","Театр"},{"Аниме","Аніме"}};
 /** The everyday genres the menus show: the first twelve of GENRE_NAMES. */
 static final int MAIN_GENRES=12;
 static List<Site.Genre> mainGenres(List<Site.Genre> got){List<Site.Genre> out=new ArrayList<Site.Genre>();for(int n=0;n<MAIN_GENRES;n++)for(Site.Genre g:got)if(GENRE_NAMES[n][0].equalsIgnoreCase(g.name.trim())&&!out.contains(g))out.add(g);return out;}
 static boolean genreKnown(String ru){if(ru!=null)for(String[] g:GENRE_NAMES)if(g[0].equalsIgnoreCase(ru.trim()))return true;return false;}
 static String genreName(String ru){if(ru==null)return null;for(String[] g:GENRE_NAMES)if(g[0].equalsIgnoreCase(ru.trim()))return g[1];return TitleTranslate.shown(ru);}
 /** Genre rows reuse the already loaded home cards, without six more catalogue requests on startup. */
 /** The first five letters match both «Комедія» and «Комедии»; a shorter name («Шоу») is matched whole. */
 private static String stem(String name){String s=name.toLowerCase(Locale.ROOT);return s.substring(0,Math.min(5,s.length()));}
 private List<Row> genreRows(Recommendations.Snapshot taste,List<Row> catalogue){
  final Map<String,Double> weight=new HashMap<String,Double>();
  if(taste!=null)for(RecommendationRanker.Signal s:taste.signals)if(s.genre!=null)for(String[] g:GENRE_NAMES){String key=s.genre.toLowerCase(Locale.ROOT);if(key.contains(stem(g[0]))||key.contains(stem(g[1])))weight.merge(g[0],s.weight,Double::sum);}
  List<String[]> picked=new ArrayList<String[]>();for(int n=0;n<10;n++)picked.add(GENRE_NAMES[n]);
  Collections.sort(picked,(a,b)->Double.compare(weight.getOrDefault(b[0],0d),weight.getOrDefault(a[0],0d)));
  List<Row> rows=new ArrayList<Row>();for(String[] genre:picked){Row row=new Row(genre[1]);Set<String> seen=new HashSet<String>();
   for(Row source:catalogue)for(Item item:source.items)if(item.genre!=null&&item.genre.toLowerCase(Locale.ROOT).contains(stem(genre[0]))&&seen.add(norm(item.link))&&row.items.size()<15)row.items.add(item);
   if(row.items.size()>=3)rows.add(row);if(rows.size()>=6)break;
  }return rows;
 }
 /** The catalogue's right half: every film genre in a grid, like Netflix's categories; OK opens that genre. */
 private void fillGenres(final LinearLayout box,final PopupWindow popup){
  TextView head=text("Жанри",13,0xff8c8c8c);head.setPadding(dp(16),dp(8),0,dp(4));box.addView(head);
  final TextView wait=text("Завантаження…",15,0xff8c8c8c);wait.setPadding(dp(16),dp(11),dp(28),dp(11));box.addView(wait);
  new Thread(()->{final List<Site.Genre> all=mainGenres(filmGenres());main.post(()->{if(destroyed||!popup.isShowing())return;
   if(all.isEmpty()){wait.setText("Не вдалося завантажити жанри");return;}
   box.removeView(wait);LinearLayout row=null;
   for(int n=0;n<all.size();n++){final Site.Genre g=all.get(n);if(n%3==0){row=new LinearLayout(this);row.setOrientation(0);box.addView(row);}
    final String name=genreName(g.name);TextView item=Ui.menuItem(this,name,false);row.addView(item,new LinearLayout.LayoutParams(dp(170),-2));
    item.setOnClickListener(v->{popup.dismiss();BrowseActivity.openGenre(this,"films",g.slug,name);});}
   popup.update(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);});},"kik-genres").start();
 }
 private TextView settingsItem;
 /** A red dot on «Налаштування» while a postponed update waits there. */
 private void paintSettingsBadge(){if(settingsItem==null||destroyed)return;boolean waiting=AppUpdater.availableName(this)!=null;if(!waiting){settingsItem.setText("Налаштування");return;}android.text.SpannableString t=new android.text.SpannableString("Налаштування  ●");t.setSpan(new android.text.style.ForegroundColorSpan(RED),t.length()-1,t.length(),0);settingsItem.setText(t);}
 private View topBar(){
  LinearLayout bar=new LinearLayout(this);bar.setOrientation(0);bar.setBaselineAligned(false);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(40),dp(26),dp(40),dp(8));
  bar.addView(Brand.logo(this,64,30));
  menuRow=new LinearLayout(this);menuRow.setGravity(Gravity.CENTER_VERTICAL);
  HorizontalScrollView navigation=Ui.horizontal(this,menuRow);LinearLayout.LayoutParams navLp=new LinearLayout.LayoutParams(0,-2,1);navLp.setMargins(dp(14),0,dp(10),0);bar.addView(navigation,navLp);
  menuRow.addView(menu("Головна",v->{if(hero!=null)watch.requestFocus();}));
  menuRow.addView(menu("Каталог ▾",this::catalogueMenu));
  menuRow.addView(menu("Пошук",v->BrowseActivity.open(this,"search",null,"Пошук")));
  menuRow.addView(menu("Мій список",v->BrowseActivity.open(this,"later",null,"Переглянути пізніше")));
  menuRow.addView(menu("Історія",v->BrowseActivity.open(this,"history",null,"Історія переглядів")));
  TextView settings=menu("Налаштування",v->startActivity(new Intent(this,BasicSettingsActivity.class)));menuRow.addView(settings);settingsItem=settings;paintSettingsBadge();

  String name=ProfileStore.nameOf(this,ProfileStore.current(this));lastProfileName=name;lastProfileAvatar=ProfileStore.avatarOf(this,ProfileStore.current(this));
  ImageView avatar=Avatars.view(this,lastProfileAvatar,40);
  avatar.setOnClickListener(v->startActivity(new Intent(this,ProfilesActivity.class)));
  // The remote reaches «Хто дивиться?» from the menu: right of «Налаштування» is the avatar, not the posters below.
  avatar.setFocusable(true);avatar.setId(View.generateViewId());avatar.setForeground(states(shape(0,dp(10),0,0),shape(0,dp(10),dp(3),Color.WHITE)));avatar.setOnFocusChangeListener((v,f)->Ui.focus(v,f,1.1f));settings.setId(View.generateViewId());settings.setNextFocusRightId(avatar.getId());avatar.setNextFocusLeftId(settings.getId());avatar.setNextFocusRightId(avatar.getId());
  // As on Netflix: just the profile picture at the end of the menu line, a little larger so it reads on the projector.
  bar.addView(avatar,new LinearLayout.LayoutParams(dp(40),dp(40)));
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
   Log.d("KIK.Load","Home data "+(SystemClock.elapsedRealtime()-started)+"ms");
   // Personal rows take over a second to rank on the projector: show last time's rows at once and re-rank behind the screen.
   final List<Row> saved=savedRecommendations(profile);List<Row> all=new ArrayList<Row>(local);
   if(saved!=null){all.addAll(saved);all.addAll(Recommendations.withoutDisliked(preferences,cached));}else all=homeRows(profile,local,cached,preferences);
   Log.d("KIK.Load","Home rows "+(SystemClock.elapsedRealtime()-started)+"ms"+(saved!=null?" (saved)":""));translateNow(all);Log.d("KIK.Load","Home ranking "+(SystemClock.elapsedRealtime()-started)+"ms");
   final List<Row> first=all;
   main.post(()->{if(!valid(gen,profile))return;laterLinks.clear();laterLinks.addAll(links);if((!first.isEmpty()||loadedOnce)&&!(loadedOnce&&signature(first).equals(shownSignature)))show(first);Log.d("KIK.Load","Home rendered "+(SystemClock.elapsedRealtime()-started)+"ms");translateLater(first,gen,profile);});
   if(saved!=null&&!cached.isEmpty()){final List<Row> fresh=Recommendations.rows(this,preferences,cached);saveRecommendations(profile,fresh);translateNow(fresh);main.post(()->{if(valid(gen,profile))for(Row r:fresh)updateRecommendations(r);});}
   long cacheAge=System.currentTimeMillis()-getSharedPreferences("kik_home",0).getLong("updated",0);
   if(!cached.isEmpty()&&(SystemClock.elapsedRealtime()-refreshedAt<6*60*60_000L||cacheAge>=0&&cacheAge<6*60*60_000L)){recommendLater(preferences,cached,gen,profile);return;}
   List<Row> site=siteRows(cached.isEmpty()?row->{final List<Row> initial=homeRows(profile,local,Collections.singletonList(row),preferences);translateNow(initial);main.post(()->{
    if(!valid(gen,profile))return;show(initial);translateLater(initial,gen,profile);
   });}:null,preferences);
   if(site.isEmpty()){final Throwable why=rowError;main.post(()->{if(valid(gen,profile)&&first.isEmpty())fail(why);});return;}
   refreshedAt=SystemClock.elapsedRealtime();saveCache(site);
   if(cached.isEmpty()){final List<Row> rest=new ArrayList<Row>(Recommendations.withoutDisliked(preferences,site.subList(1,site.size())));translateNow(rest);main.post(()->{if(!valid(gen,profile))return;
    // The first catalogue row is already usable. Append remaining rows without replacing focus.
    appendRows(rest);translateLater(rest,gen,profile);
   });}
   recommendLater(preferences,site,gen,profile);
  },"kik-home").start();
 }
 /** {@code profile} is the one these rows were built for: the viewer may have switched while the catalogue loaded. */
 private List<Row> homeRows(String profile,List<Row> local,List<Row> catalogue,Recommendations.Snapshot preferences){
  List<Row> out=new ArrayList<Row>(local);List<Row> personal=Recommendations.rows(this,preferences,catalogue);if(!catalogue.isEmpty())saveRecommendations(profile,personal);out.addAll(personal);out.addAll(Recommendations.withoutDisliked(preferences,catalogue));return out;
 }
 /** Last ranked personal rows per profile, so the next start shows them without waiting for the ranking. */
 private void saveRecommendations(String profile,List<Row> rows){
  try{JSONArray a=new JSONArray();for(Row r:rows){JSONArray items=new JSONArray();for(Item i:r.items)items.put(new JSONObject().put("link",i.link).put("title",i.title).put("poster",i.poster).put("wide",i.wide).put("year",i.year).put("genre",i.genre).put("rating",i.rating));a.put(new JSONObject().put("title",r.title).put("id",r.recommendationId).put("personalized",r.personalized).put("items",items));}
   getSharedPreferences("kik_home_personal",0).edit().putString(profile,a.toString()).apply();}catch(JSONException ignored){}
 }
 private List<Row> savedRecommendations(String profile){
  String raw=getSharedPreferences("kik_home_personal",0).getString(profile,null);if(raw==null)return null;
  try{JSONArray a=new JSONArray(raw);List<Row> out=new ArrayList<Row>();for(int n=0;n<a.length();n++){JSONObject o=a.getJSONObject(n);Row r=new Row(o.getString("title"));r.recommendationId=o.isNull("id")?null:Site.opt(o,"id");r.personalized=o.optBoolean("personalized");JSONArray items=o.getJSONArray("items");
    for(int k=0;k<items.length();k++){JSONObject x=items.getJSONObject(k);Item i=new Item();i.link=x.getString("link");i.title=x.optString("title");i.poster=x.isNull("poster")?null:Site.opt(x,"poster");i.wide=x.isNull("wide")?null:Site.opt(x,"wide");i.year=x.isNull("year")?null:Site.opt(x,"year");i.genre=x.isNull("genre")?null:Site.opt(x,"genre");i.rating=x.isNull("rating")?null:Site.opt(x,"rating");r.items.add(i);}
    if(!r.items.isEmpty())out.add(r);}return out;}catch(JSONException e){return null;}
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
  main.postDelayed(()->{if(!valid(gen,profile)||!foreground)return;new Thread(()->{Map<String,String> got=TitleTranslate.resolve(words(rows));main.post(()->{if(!valid(gen,profile))return;mergeTranslations(got);translateLabels(content);if(hero!=null){heroTitle.setText(tr(hero.title));heroMeta.setText(facts(hero));}});},"kik-home-text").start();},800);
 }
 private static final class Label{final Item item;final boolean meta;Label(Item i,boolean m){item=i;meta=m;}}
 private void translateLabels(View v){if(v instanceof TextView&&v.getTag() instanceof Label){Label l=(Label)v.getTag();String shown=l.meta?(l.item.resume?l.item.sub:join(l.item.year,tr(l.item.genre))):tr(l.item.title);if(!TextUtils.equals(((TextView)v).getText(),shown))((TextView)v).setText(shown);}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)translateLabels(((ViewGroup)v).getChildAt(i));}
 private void recommendLater(Recommendations.Snapshot preferences,List<Row> catalogue,int gen,String profile){
  main.postDelayed(()->{if(!valid(gen,profile)||!foreground)return;new Thread(()->{
   Recommendations.refresh(this,preferences);if(!valid(gen,profile)||!foreground)return;
   Recommendations.refreshAi(this,preferences,catalogue);if(!valid(gen,profile)||!foreground)return;
   Recommendations.enrich(this,preferences,catalogue,()->valid(gen,profile)&&foreground);
   if(!valid(gen,profile))return;final List<Row> updated=Recommendations.rows(this,preferences,catalogue);saveRecommendations(profile,updated);translateNow(updated);
   main.post(()->{if(valid(gen,profile)){for(Row r:updated)updateRecommendations(r);translateLater(updated,gen,profile);}});
  },"kik-home-recommendations").start();},1800);
 }
 private List<Row> localRows(String profile){
  List<Row> rows=new ArrayList<Row>();
  Row resume=new Row(RESUME_ROW);
  for(Progress.Entry e:Progress.recent(this,profile)){
   if(e.finished()&&e.season==null)continue;
   Item i=new Item();i.link=e.link;i.title=e.title;i.poster=e.poster;i.resume=true;
   i.progress=e.dur>0?Math.min(1f,(float)e.pos/e.dur):0;
   i.sub=(e.season!=null&&!"0".equals(e.season)?"S"+e.season+"E"+e.episode+" · ":"")+(e.finished()?"наступна серія":"з "+Ui.time(e.pos));
   long left=e.dur>e.pos?(e.dur-e.pos+59999)/60000:0;i.left=e.finished()?0:left;
   i.heroLine=join(e.season!=null&&!"0".equals(e.season)?"Сезон "+e.season+" · Серія "+e.episode:null,e.finished()?"далі наступна серія":left>0?"ще "+left+" хв":"з "+Ui.time(e.pos));
   String[] d=DETAILS.get(norm(e.link));if(d!=null){i.description=d[0];i.wide=d[1];i.rating=d[2];i.year=d[3];i.genre=d[4];i.age=d[5];i.duration=d[6];}
   resume.items.add(i);if(resume.items.size()>=20)break;
  }
  if(!resume.items.isEmpty())rows.add(resume);
  Row saved=laterRow(profile);if(!saved.items.isEmpty())rows.add(saved);
  return rows;
 }
 static final String LATER_ROW="Переглянути пізніше",RESUME_ROW="Продовжити перегляд";
 private Row laterRow(String profile){
  Row saved=new Row(LATER_ROW);JSONArray a=Progress.later(this,profile);
  for(int k=0;k<a.length();k++){JSONObject o=a.optJSONObject(k);if(o==null)continue;Item i=new Item();i.link=Site.opt(o,"link");i.title=Site.opt(o,"title");i.poster=Site.opt(o,"poster");i.year=Site.opt(o,"year");i.genre=Site.opt(o,"genre");if(i.link!=null&&i.title!=null)saved.items.add(i);}
  return saved;
 }
 private List<Row> siteRows(java.util.function.Consumer<Row> first,Recommendations.Snapshot taste){
  final Row[] out=new Row[ROWS.length];List<Thread> threads=new ArrayList<Thread>();rowError=null;
  for(int k=0;k<ROWS.length;k++){final int idx=k;Thread t=new Thread(()->{
   try{Row r=new Row(ROWS[idx][0]);for(Site.Card c:Site.list(ROWS[idx][1],null,ROWS[idx][2],1)){Item i=new Item();i.link=c.link;i.title=c.title;i.poster=c.poster;i.year=c.year();i.genre=c.genre();r.items.add(i);if(r.items.size()>=15)break;}out[idx]=r;}
   catch(Exception e){Log.w("KIK","Home row "+ROWS[idx][0]+": "+e);if(rowError==null)rowError=e;}});
   threads.add(t);t.start();
   if(k==0)try{t.join();}catch(InterruptedException ignored){} // the first request passes the bot check for the rest
  }
  if(first!=null&&out[0]!=null&&!out[0].items.isEmpty())first.accept(out[0]);
  for(Thread t:threads)try{t.join(25000);}catch(InterruptedException ignored){}
  List<Row> rows=new ArrayList<Row>();for(Row r:out)if(r!=null&&!r.items.isEmpty())rows.add(r);rows.addAll(genreRows(taste,rows));try{Row ua=new Row("Українською");for(Site.Card c:FreeSources.cards(null)){Item i=new Item();i.link=c.link;i.title=c.title;i.poster=c.poster;i.genre="Українською";i.sub="Українська озвучка";ua.items.add(i);if(ua.items.size()>=15)break;}if(!ua.items.isEmpty())rows.add(ua);}catch(Exception ignored){}if(first!=null&&!rows.isEmpty()&&(out[0]==null||out[0].items.isEmpty()))first.accept(rows.get(0));return rows;
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
    for(int n=0;n<items.length();n++){JSONObject x=items.getJSONObject(n);Item i=new Item();i.link=x.optString("link");i.title=x.optString("title");i.poster=Site.opt(x,"poster");i.year=Site.opt(x,"year");i.genre=Site.opt(x,"genre");
     String[] d=DETAILS.get(norm(i.link));if(d!=null){i.description=d[0];i.wide=d[1];i.rating=d[2];i.age=d[5];i.duration=d[6];}r.items.add(i);}
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
   try{Site.Film f=FreeSources.metadata(i.link);RecommendationMetadata.remember(this,f);String d=f.description==null?"":f.description;
    final String[] g=new String[]{d,f.poster,f.rating,f.year,f.genres.isEmpty()?null:f.genres.get(0),f.age,f.duration};DETAILS.put(norm(i.link),g);
    main.post(()->{i.description=g[0];if(g[1]!=null&&!i.wideArt)i.wide=g[1];if(g[2]!=null)i.rating=g[2];if(i.year==null)i.year=g[3];if(i.genre==null)i.genre=g[4];if(g[5]!=null)i.age=g[5];if(g[6]!=null)i.duration=g[6];
     if(!destroyed&&hero==i){heroText.setText(i.description);heroMeta.setText(facts(i));heroText.setAlpha(0.3f);heroText.animate().alpha(1f).setDuration(260).start();if(i.wide!=null&&!i.wideArt)Images.crossfade(i.wide,heroImage);}});
   }catch(Exception e){Log.w("KIK","Hero details: "+e);}
  },"kik-hero").start();
  main.postDelayed(detailTask,600);
 }
 private static String norm(String link){return Progress.norm(link);}
 private static String time(long ms){return Ui.time(ms);}

 // ---------- ui ----------
 /** What the rows would show; coming back to Home with the same rows keeps the screen as it is instead of rebuilding it. */
 private static String signature(List<Row> rows){StringBuilder b=new StringBuilder();for(Row r:rows){b.append(r.title).append('\n');for(Item i:r.items)b.append(i.link).append('|').append(i.sub).append('|').append(i.resume).append('|').append(Math.round(i.progress*100)).append('\n');}return b.toString();}
 private String shownSignature="";
 /**
  * Nothing to show and the catalogue did not load: KIK's «Упс» screen fills the page under the top bar, which stays usable
  * (search, profile, settings). The hero is hidden rather than dimmed; a temporary cause tries again by itself.
  */
 private void fail(Throwable e){
  closeOops();heroInfo.setVisibility(View.GONE);heroImage.setVisibility(View.INVISIBLE);
  oops=new Oops(this,Oops.kind(this,e),"","home",pressed->{closeOops();heroText.setText("Завантаження…");load();});
  View bar=content.getChildAt(0);int height=(scroll.getHeight()>0?scroll.getHeight():getResources().getDisplayMetrics().heightPixels)-bar.getHeight();
  content.addView(oops,content.indexOfChild(heroInfo)+1,new LinearLayout.LayoutParams(-1,Math.max(dp(400),height)));
 }
 private void closeOops(){if(oops==null)return;content.removeView(oops);oops=null;heroInfo.setVisibility(View.VISIBLE);heroImage.setVisibility(View.VISIBLE);}
 private void show(List<Row> rows){
  closeOops();shownSignature=signature(rows);
  boolean initial=!loadedOnce;loadedOnce=true;boolean actionFocused=watch.hasFocus()||later.hasFocus()||more.hasFocus();
  recommendedLine=null;recommendedHeading=null;recommendationKeys="";
  recommendationLines.clear();recommendationSignatures.clear();rowViews.clear();shownRows=new ArrayList<Row>(rows);tasteChoices=new ArrayList<Item>();Set<String> choices=new HashSet<String>();for(int pos=0;pos<15&&tasteChoices.size()<40;pos++)for(Row r:rows)if(pos<r.items.size()){Item i=r.items.get(pos);if(choices.add(norm(i.link)))tasteChoices.add(i);if(tasteChoices.size()>=40)break;}
  while(content.getChildCount()>2)content.removeViewAt(2);
  View first=null;
  View again=null;
  View[] focus=appendRowViews(rows);first=focus[0];again=focus[1];
  space(content,40);
  if(rows.isEmpty()){heroTitle.setText("Ласкаво просимо");heroText.setText("Завантажуємо новинки… Тим часом можна відкрити «Фільми», «Серіали» або «Пошук».");watch.setVisibility(View.GONE);more.setVisibility(View.GONE);return;}
  watch.setVisibility(View.VISIBLE);more.setVisibility(View.VISIBLE);later.setVisibility(View.VISIBLE);
  watch.setOnClickListener(v->{if(hero!=null){startActivity(new Intent(this,FilmActivity.class).putExtra("link",hero.link).putExtra("title",tr(hero.title)).putExtra("poster",hero.poster).putExtra("play",true));overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}});
  if(again!=null&&!actionFocused){((ViewGroup)again).getChildAt(0).requestFocus();return;}
  startSlides(rows);
  if(initial){watch.requestFocus();scroll.scrollTo(0,0);}else if(actionFocused)watch.requestFocus();
 }

 private View[] appendRowViews(List<Row> rows){View[] focus=new View[2];
  for(Row r:rows){
   TextView h=rowHeading(r.title);View head=h;
   if(r.personalized){LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setClipChildren(false);header.setClipToPadding(false);header.setPadding(0,dp(4),dp(40),dp(4));header.addView(h,new LinearLayout.LayoutParams(0,-2,1));head=header;}
   content.addView(head);HorizontalScrollView hs=rowLine(r);LinearLayout line=(LinearLayout)hs.getChildAt(0);content.addView(hs);rowViews.put(r.title,new View[]{head,hs});
   if(r.personalized){recommendedLine=line;recommendedHeading=h;recommendationKeys=recommendationKeys(r);}
   if(r.recommendationId!=null){recommendationLines.put(r.recommendationId,line);recommendationSignatures.put(r.recommendationId,recommendationKeys(r));}
   for(int n=0;n<line.getChildCount();n++){View c=line.getChildAt(n);Item i=r.items.get(n);if(focus[0]==null)focus[0]=c;if(focus[1]==null&&focusedLink!=null&&focusedLink.equals(i.link))focus[1]=c;}
  }
  return focus;
 }
 private TextView rowHeading(String title){TextView h=text(title,18,Color.WHITE);h.setTypeface(Typeface.DEFAULT_BOLD);h.setPadding(dp(40),dp(8),0,dp(2));h.setSingleLine(true);h.setEllipsize(TextUtils.TruncateAt.END);return h;}
 private HorizontalScrollView rowLine(Row r){
  HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setClipToPadding(false);hs.setPadding(dp(32),dp(6),dp(32),dp(6));
  LinearLayout line=new LinearLayout(this);line.setOrientation(0);line.setClipChildren(false);line.setClipToPadding(false);hs.addView(line);for(Item i:r.items)line.addView(card(i));return hs;
 }
 /** «Мій список» on the hero changes one row: it is redrawn in place (added after «Продовжити перегляд» or taken away), so the
  *  other rows keep their scroll, the hero and its focus stay, and the next load sees nothing to rebuild. */
 private void refreshLaterRow(){
  if(!loadedOnce||content.getChildCount()<2)return;
  Row fresh=laterRow(ProfileStore.current(this));View[] views=rowViews.get(LATER_ROW);int at=-1;for(int n=0;n<shownRows.size();n++)if(LATER_ROW.equals(shownRows.get(n).title))at=n;
  if(fresh.items.isEmpty()){if(views!=null){content.removeView(views[0]);content.removeView(views[1]);rowViews.remove(LATER_ROW);}if(at>=0)shownRows.remove(at);}
  else if(views!=null){LinearLayout line=(LinearLayout)((HorizontalScrollView)views[1]).getChildAt(0);line.removeAllViews();for(Item i:fresh.items)line.addView(card(i));if(at>=0)shownRows.set(at,fresh);else shownRows.add(0,fresh);}
  else{
   View[] resume=rowViews.get(RESUME_ROW);int index=resume!=null?content.indexOfChild(resume[1])+1:2,rowIndex=0;for(int n=0;n<shownRows.size();n++)if(RESUME_ROW.equals(shownRows.get(n).title))rowIndex=n+1;
   TextView h=rowHeading(fresh.title);HorizontalScrollView hs=rowLine(fresh);content.addView(h,index);content.addView(hs,index+1);rowViews.put(LATER_ROW,new View[]{h,hs});shownRows.add(rowIndex,fresh);
  }
  shownSignature=signature(shownRows);
 }
 private void appendRows(List<Row> rows){if(rows.isEmpty())return;content.removeViewAt(content.getChildCount()-1);appendRowViews(rows);shownRows.addAll(rows);shownSignature=signature(shownRows);space(content,40);for(Row r:rows)for(Item i:r.items)if(tasteChoices.size()<40)tasteChoices.add(i);}

 /** A shelf card; the focused one stays at the row's left edge and the row slides under it, as on Netflix. */
 private View card(final Item i){
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setClipChildren(false);box.setClipToPadding(false);box.setPadding(dp(8),dp(8),dp(8),dp(6));
  FrameLayout frame=new FrameLayout(this);frame.setFocusable(true);frame.setClickable(true);
  // Rounded corners come from a shader-drawn poster, not outline clipping (costly on this GPU).
  ImageView img=new PosterView(this,dp(8));frame.addView(img,new FrameLayout.LayoutParams(-1,-1));
  final View ring=new View(this);ring.setBackground(shape(0,dp(8),dp(3),RED));ring.setAlpha(0f);frame.addView(ring,new FrameLayout.LayoutParams(-1,-1));
  Studios.watch(frame,i.link,i.title,i.year,null);
  if(i.rating!=null){TextView r=text("★ "+i.rating,12,Color.WHITE);r.setPadding(dp(6),dp(2),dp(6),dp(2));r.setBackground(shape(0xcc000000,dp(4),0,0));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);p.setMargins(0,dp(6),dp(6),0);frame.addView(r,p);}
  if(i.resume){View track=new View(this);track.setBackgroundColor(0xff77777b);frame.addView(track,new FrameLayout.LayoutParams(-1,dp(3),Gravity.BOTTOM));View bar=new View(this);bar.setBackgroundColor(RED);frame.addView(bar,new FrameLayout.LayoutParams(Math.max(dp(3),Math.round(dp(150)*i.progress)),dp(3),Gravity.BOTTOM|Gravity.START));}
  box.addView(frame,new LinearLayout.LayoutParams(dp(150),dp(200)));
  TextView t=text(tr(i.title),14,0xffe6e6ea);t.setTag(new Label(i,false));t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);t.setPadding(dp(2),dp(8),dp(2),0);
  box.addView(t,new LinearLayout.LayoutParams(dp(150),-2));
  TextView s=text(i.resume?i.sub:join(i.year,tr(i.genre)),12,i.resume?0xffd6d6dc:GREY);s.setTag(new Label(i,true));s.setSingleLine(true);s.setEllipsize(TextUtils.TruncateAt.END);s.setPadding(dp(2),dp(2),dp(2),0);
  box.addView(s,new LinearLayout.LayoutParams(dp(150),-2));
  frame.setContentDescription(tr(i.title));
  frame.setOnFocusChangeListener((v,f)->{
   Ui.focus(v,f,1.05f);ring.animate().alpha(f?1f:0f).setDuration(140).start();
   t.setTextColor(f?Color.WHITE:0xffe6e6ea);
   if(f){final String profile=ProfileStore.current(this);Ui.bg(()->RatingStore.expose(this,profile,i.link));focusedLink=i.link;main.removeCallbacks(heroTask);heroTask=()->feature(i);main.postDelayed(heroTask,200);if(box.getParent()!=null){final HorizontalScrollView hs=(HorizontalScrollView)box.getParent().getParent();scrollRowIntoView(hs);hs.post(()->{View line=(View)box.getParent();if(line==null||destroyed)return;int left=line.getLeft()+box.getLeft(),target=Math.max(0,left-dp(40));if(target!=hs.getScrollX())hs.smoothScrollTo(target,0);});}}
  });
  frame.setOnClickListener(v->open(i));
  frame.setOnLongClickListener(v->{rate(i);return true;});
  Images.load(i.poster,img,dp(168));
  return box;
 }
 /** Long press on a card: like, dislike, «not interested» (hides the title without judging the taste; the chosen one is red and
  *  pressing it again clears it) and, for a started title, remove it from history. A compact dark panel over the dimmed home, which fades and grows in on its own (no system dialog animation). */
 private void rate(Item i){
  final String profile=ProfileStore.current(this);final int value=RatingStore.get(this,profile,i.link);
  final LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(8),dp(12),dp(8),dp(8));box.setBackground(Ui.menuPanel(this));
  TextView title=text(tr(i.title),14,GREY);title.setTypeface(Typeface.DEFAULT_BOLD);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(dp(14),0,dp(14),dp(8));box.addView(title);
  final PopupWindow popup=new PopupWindow(box,dp(280),ViewGroup.LayoutParams.WRAP_CONTENT,true);
  popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);popup.setElevation(dp(14));popup.setAnimationStyle(0);
  TextView first=null;
  for(final int want:new int[]{1,-1,-2}){
   TextView c=action(box,want>0?"Подобається":want<-1?"Не цікавить":"Не подобається",want<-1?new FilmActivity.ThumbIcon(dp(20),FilmActivity.ThumbIcon.hide()):new FilmActivity.ThumbIcon(dp(20),want<0),value==want);
   c.setOnClickListener(v->{int next=value==want?0:want;boolean saved=RatingStore.set(this,profile,i.link,tr(i.title),i.poster,i.genre,next);popup.dismiss();if(saved){Ui.toast(this,RatingStore.said(next));load();}else Ui.toast(this,"Не вдалося зберегти оцінку");});
   if(first==null||value==want)first=c;
  }
  if(Progress.get(this,i.link)!=null){TextView c=action(box,"Прибрати з історії",new FilmActivity.ThumbIcon(dp(20),FilmActivity.ThumbIcon.trash()),false);c.setOnClickListener(v->{Progress.remove(this,i.link);popup.dismiss();Ui.toast(this,"Прибрано з історії");load();});}
  // A title in «Переглянути пізніше» leaves the list from here; the row is redrawn in place, the hero button follows.
  if(Progress.isLater(this,i.link)){TextView c=action(box,"Прибрати зі списку",new FilmActivity.ThumbIcon(dp(20),FilmActivity.ThumbIcon.minus()),false);c.setOnClickListener(v->{popup.dismiss();if(Progress.isLater(this,i.link))Progress.toggleLater(this,i.link,tr(i.title),i.poster,i.year,tr(i.genre));laterLinks.remove(norm(i.link));paintLater();Ui.toast(this,"Прибрано з мого списку");refreshLaterRow();});}
  if(value!=0){TextView hint=text("Натисніть оцінку ще раз, щоб її зняти",12,GREY);hint.setPadding(dp(14),dp(6),dp(14),dp(4));box.addView(hint);}
  final TextView focus=first;
  popup.showAtLocation(root,Gravity.CENTER,0,0);
  // The window manager dims the home behind the panel (no translucent layer of our own for the weak GPU to redraw).
  for(ViewParent p=box.getParent();p instanceof View;p=p.getParent()){View v=(View)p;if(!(v.getLayoutParams() instanceof WindowManager.LayoutParams))continue;WindowManager.LayoutParams lp=(WindowManager.LayoutParams)v.getLayoutParams();lp.flags|=WindowManager.LayoutParams.FLAG_DIM_BEHIND;lp.dimAmount=0.62f;try{getWindowManager().updateViewLayout(v,lp);}catch(RuntimeException e){Log.w("KIK","Menu dim: "+e);}break;}
  if(Ui.animations()){box.setAlpha(0f);box.setScaleX(0.94f);box.setScaleY(0.94f);box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(EASE).start();}
  box.post(focus::requestFocus);
 }
 /** A menu row: icon and label on a transparent line, white with black text when focused; the chosen rating keeps a red icon. */
 private TextView action(LinearLayout box,String label,FilmActivity.ThumbIcon icon,boolean chosen){
  TextView c=new TextView(this);c.setText(label);c.setTextSize(15);c.setSingleLine(true);c.setEllipsize(TextUtils.TruncateAt.END);c.setIncludeFontPadding(false);c.setFocusable(true);c.setClickable(true);
  c.setTypeface(chosen?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);c.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);c.setPadding(dp(14),0,dp(14),0);c.setCompoundDrawablePadding(dp(12));
  c.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{Color.BLACK,chosen?Color.WHITE:0xffe6e6ea}));
  c.setBackground(Ui.states(Ui.shape(0,dp(10),0,0),Ui.shape(Color.WHITE,dp(10),0,0)));
  icon.setBounds(0,0,dp(20),dp(20));if(chosen)icon.setColorFilter(new PorterDuffColorFilter(RED,PorterDuff.Mode.SRC_IN));c.setCompoundDrawables(icon,null,null,null);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.setMargins(0,0,0,dp(2));box.addView(c,lp);return c;
 }
 private void scrollRowIntoView(View row){
  int target=FocusScrollPolicy.target(scroll.getScrollY(),scroll.getHeight(),dp(16),dp(24),row.getTop(),row.getBottom(),dp(8));
  if(target!=scroll.getScrollY())scroll.smoothScrollTo(0,target);
 }

 // ---------- featured slideshow ----------
 /** As on the website, the hero features recommendations, never a title already in «Продовжити перегляд»; it turns to the
  *  next one (up to 10) every 9 s, wherever the focus is. A focused card takes a turn of the show: see feature(). */
 private final List<Item> slides=new ArrayList<Item>();private int slide;private LinearLayout dots;
 private final Runnable nextSlide=()->{if(destroyed||slides.isEmpty())return;if(foreground&&!expanded)showSlide((slide+1)%slides.size());else{scheduleSlide();paintDots();}};
 private List<Item> candidates=new ArrayList<Item>();private String slideKeys="";
 private void startSlides(List<Row> rows){
  Set<String> started=new HashSet<String>();for(Row r:rows)for(Item i:r.items)if(i.resume)started.add(norm(i.link));
  List<Item> found=new ArrayList<Item>();Set<String> seen=new HashSet<String>();
  for(int pass=0;pass<2&&found.isEmpty();pass++)for(Row r:rows){if(pass==0&&!r.personalized)continue;for(Item i:r.items){if(found.size()>=24)break;if(!i.resume&&!started.contains(norm(i.link))&&seen.add(norm(i.link)))found.add(i);}}
  if(found.isEmpty())found.add(rows.get(0).items.get(0));
  // Home redraws every few seconds while it syncs: the same picks keep the slide on screen and its timer.
  StringBuilder keys=new StringBuilder();for(Item i:found)keys.append(norm(i.link)).append(',');
  if(keys.toString().equals(slideKeys)&&!slides.isEmpty())return;
  slideKeys=keys.toString();candidates=found;arrange();keepTurn();wideArt(found);
 }
 /** Up to 10 slides. Once three or more picks have a wide picture, only those are shown, so the hero is always filled
 *  like the website's; until then (or without enough), the rest follow with their posters. */
 private void arrange(){slides.clear();for(Item x:candidates)if(x.wideArt&&slides.size()<10)slides.add(x);if(slides.size()>=3)return;for(Item x:candidates)if(!x.wideArt&&slides.size()<10)slides.add(x);}
 /** Wide 16:9 pictures for the candidates, as the website's hero shows; a title without one keeps its poster. */
 private void wideArt(final List<Item> want){
  final List<String> links=new ArrayList<String>(),titles=new ArrayList<String>();for(Item x:want)if(!x.wideArt){links.add(x.link);titles.add(x.title==null?"":x.title);}
  if(links.isEmpty())return;
  Ui.bg(()->{final Map<String,String> art=TvChannels.backdrops(this,links,titles);main.post(()->{if(destroyed)return;boolean any=false;
   for(Item x:want){String w=art.get(norm(x.link));if(w==null)continue;x.wide=w;x.wideArt=true;any=true;if(hero==x)Images.crossfade(w,heroImage);}
   if(!any||slides.isEmpty()||want!=candidates)return;arrange();keepTurn();});});
 }
 private void showSlide(int n){borrowed=false;slide=n;setHero(slides.get(n));scheduleSlide();paintDots();}
 /** One turn ends, the next begins; a borrowed turn ends too, even with a single slide. */
 private void scheduleSlide(){main.removeCallbacks(nextSlide);slideAt=0;if(slides.size()>1||borrowed&&!slides.isEmpty()){slideAt=android.os.SystemClock.uptimeMillis();main.postDelayed(nextSlide,SLIDE_MS);}}
 private static final long SLIDE_MS=9000;private long slideAt;
 /** A title on the hero that is not a slide of its own: it holds the current pill for one turn. */
 private boolean borrowed;
 /**
  * A focused card features its title, Netflix-style, while the show goes on: a title among the slides makes the show jump
  * to its pill; any other borrows the current pill, which fills again for it. Then the show continues with the next pill.
  */
 private void feature(Item i){
  if(i==null||i==hero)return;
  int at=-1;for(int k=0;k<slides.size();k++)if(norm(slides.get(k).link).equals(norm(i.link)))at=k;
  if(at>=0){showSlide(at);return;}
  borrowed=true;setHero(i);scheduleSlide();paintDots();
 }
 /** One slide back or forward by hand; the pill starts filling again and the show goes on from there. */
 private boolean turn(int by){int n=slides.size();if(n<2)return false;showSlide(((slide+by)%n+n)%n);return true;}
 /** The picks changed under the hero: the title on screen keeps its pill (or its borrowed turn) and its timer. */
 private void keepTurn(){
  if(slides.isEmpty())return;int at=slides.indexOf(hero);
  if(at>=0){borrowed=false;slide=at;paintDots();if(slideAt==0)scheduleSlide();return;}
  if(borrowed&&hero!=null){slide=Math.min(slide,slides.size()-1);paintDots();if(slideAt==0)scheduleSlide();return;}
  showSlide(0);
 }
 /** Small pills under the buttons. The slide on screen is a wider track that fills up in white over the 9 s it stays,
 *  like Netflix; the fill only scales (a render property), so the weak GPU never redraws the hero for it. */
 private void paintDots(){dots.removeAllViews();if(slides.size()<2)return;for(int k=0;k<slides.size();k++){boolean on=k==slide;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(on?28:8),dp(6));lp.setMargins(0,0,dp(6),0);
  if(!on){View d=new View(this);d.setBackground(Ui.shape(0x55ffffff,dp(3),0,0));dots.addView(d,lp);continue;}
  FrameLayout track=new FrameLayout(this);track.setBackground(Ui.shape(0x55ffffff,dp(3),0,0));View fill=new View(this);fill.setBackground(Ui.shape(Color.WHITE,dp(3),0,0));long spent=slideAt==0?0:Math.min(SLIDE_MS,android.os.SystemClock.uptimeMillis()-slideAt);fill.setPivotX(0);fill.setScaleX(spent/(float)SLIDE_MS);track.addView(fill,new FrameLayout.LayoutParams(-1,-1));dots.addView(track,lp);
  fill.animate().scaleX(1f).setDuration(SLIDE_MS-spent).setInterpolator(new android.view.animation.LinearInterpolator()).start();}}

 private void setHero(Item i){
  if(i==hero)return;hero=i;expanded=false;heroText.setLines(2);
  heroTitle.setText(tr(i.title));
  heroMeta.setText(facts(i));
  heroSub.setText(i.resume?i.heroLine:"");heroResume.setVisibility(i.resume?View.VISIBLE:View.INVISIBLE);heroBar.getLayoutParams().width=Math.max(dp(4),Math.round(dp(120)*i.progress));heroBar.requestLayout();
  // Continue fills up to where the title was left and says how much is left, like the launcher's Now Playing card.
  HeroActions.label(watch,i.resume?(i.left>0?"Продовжити · ще "+Ui.minutes(i.left):"Продовжити"):"Дивитися",HeroActions.PLAY);
  Ui.watched(watch,i.resume&&i.left>0?i.progress:0);
  paintLater();
  heroText.setText(i.description==null?"":i.description);details(i);
  Images.crossfade(i.wide!=null?i.wide:i.poster,heroImage);if(!i.wideArt&&!candidates.contains(i))wideArt(java.util.Collections.singletonList(i));
  for(View v:new View[]{heroTitle,heroMeta,heroResume,heroText}){v.animate().cancel();v.setAlpha(0.35f);v.animate().alpha(1f).setDuration(240).setInterpolator(EASE).withLayer().start();}
 }

 // ---------- watch later ----------
 private void toggleLater(Item i){
  if(i==null||i.link==null)return;
  boolean on=Progress.toggleLater(this,i.link,tr(i.title),i.poster,i.year,tr(i.genre));
  if(on)laterLinks.add(norm(i.link));else laterLinks.remove(norm(i.link));
  paintLater();
  HeroActions.focus(later,later.isFocused());Ui.toast(this,on?"Додано до мого списку":"Прибрано з мого списку");
  refreshLaterRow();
 }
 private void paintLater(){if(hero==null)return;boolean saved=laterLinks.contains(norm(hero.link));HeroActions.label(later,saved?"У списку":"Мій список",saved?HeroActions.CHECK:HeroActions.ADD);later.setSelected(saved);}

 private String tr(String s){if(s==null)return null;String t=uk.get(s);return t==null?TitleTranslate.shown(s):t;}
 private static String join(String... parts){return Ui.join(parts);}
 private CharSequence facts(Item i){return Ui.facts(this,i.year,i.age,i.rating,Ui.minutes(i.duration,tr(i.duration)),tr(i.genre));}

 private void open(Item i){
  if(i==null||i.link==null)return;
  FilmActivity.open(this,i.link,tr(i.title),i.poster,i.year);overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
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
   HttpURLConnection c=Doh.open(url);c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent",Site.UA);
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
   Paint g=new Paint(),smooth=new Paint(Paint.FILTER_BITMAP_FLAG);
   if(src.getWidth()<src.getHeight()*1.1f){
    // A poster (no wide picture anywhere): it still fills the hero like a slide, never sits in a frame. Blurred to the
    // edges (a 16 px copy stretched back up), with the sharp poster cropped wide on the right where the backdrop would be.
    int bw=16,bh=Math.max(1,Math.round(16f*src.getHeight()/src.getWidth()));Bitmap blur=Bitmap.createScaledBitmap(src,bw,bh,true);
    float top=(heroW*bh/(float)bw-heroH)/2;c.drawBitmap(blur,null,new RectF(0,-Math.max(0,top),heroW,heroH+Math.max(0,top)),smooth);blur.recycle();c.drawColor(0x730d0d0f);
    RectF right=new RectF(heroW*0.4f,0,heroW,heroH);float k=Math.max(right.width()/src.getWidth(),heroH/(float)src.getHeight()),sw=right.width()/k,sh=heroH/k,sx=(src.getWidth()-sw)/2,sy=(src.getHeight()-sh)*0.35f;
    c.drawBitmap(src,new Rect(Math.round(sx),Math.round(sy),Math.round(sx+sw),Math.round(sy+sh)),right,smooth);
    g.setShader(new LinearGradient(right.left,0,right.left+right.width()*0.3f,0,new int[]{0xff0d0d0f,0x000d0d0f},null,Shader.TileMode.CLAMP));c.drawRect(right,g);
   }else{
   BackdropLayout art=BackdropLayout.fit(src.getWidth(),src.getHeight(),heroW,heroH);
   RectF bounds=new RectF(art.left,art.top,art.left+art.width,art.top+art.height);
   c.drawBitmap(src,null,bounds,smooth);
   // Blend the image's own edges into the surrounding graphite.
   g.setShader(new LinearGradient(bounds.left,0,bounds.left+art.width*0.18f,0,new int[]{BG,0x000d0d0f},null,Shader.TileMode.CLAMP));c.drawRect(bounds,g);
   g.setShader(new LinearGradient(bounds.right-art.width*0.06f,0,bounds.right,0,new int[]{0x000d0d0f,BG},null,Shader.TileMode.CLAMP));c.drawRect(bounds,g);
   g.setShader(new LinearGradient(0,bounds.top,0,bounds.top+art.height*0.06f,new int[]{BG,0x000d0d0f},null,Shader.TileMode.CLAMP));c.drawRect(bounds,g);
   }
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
   // Not in a window yet (a page's onCreate): the fetch would find it detached and drop the picture for good.
   if(!view.isAttachedToWindow()){view.post(()->crossfade(url,view));return;}
   // Capture this view's size now: opening Film must not change Home's in-flight artwork.
   ViewGroup.LayoutParams lp=view.getLayoutParams();
   int viewWidth=view.getWidth()>0?view.getWidth():lp!=null&&lp.width>0?lp.width:view.getResources().getDisplayMetrics().widthPixels;
   int viewHeight=lp!=null&&lp.height>0?lp.height:view.getHeight();
   if(viewHeight<=0){view.post(()->crossfade(url,view));return;}
   final int width=Math.max(1,viewWidth/2),height=Math.max(1,viewHeight/2);
   final String u=url.startsWith("//")?"https:"+url:url.startsWith("/")?"https://"+Site.host()+url:url;
   final String key=u+"@hero-fit-v3:"+width+"x"+height;if(key.equals(view.getTag()))return;view.setTag(key);
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
   final long queued=android.os.SystemClock.elapsedRealtime();
   (cross?HERO_POOL:POOL).execute(()->{
    if(!key.equals(view.getTag())||!view.isAttachedToWindow())return;
    Bitmap b=null;
    try{
     long started=android.os.SystemClock.elapsedRealtime();
     byte[] data=imageBytes(u);
     long done=android.os.SystemClock.elapsedRealtime();if(done-queued>1000)Log.d("KIK.Load","Picture "+(done-queued)+"ms, queued "+(started-queued)+"ms, "+Uri.parse(u).getHost());
     BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);
     int s=ImageDecodePolicy.sample(o.outWidth,o.outHeight,width,height>0?height:Math.max(1,width*3/2));
     o=new BitmapFactory.Options();o.inSampleSize=s;o.inPreferredConfig=Bitmap.Config.RGB_565;b=BitmapFactory.decodeByteArray(data,0,data.length,o);
     if(b!=null&&cross)b=backdrop(b,width,height);
     if(b!=null)b.prepareToDraw();
     if(b!=null)MEM.put(key,b);else Log.w("KIK","Poster not decoded "+u+" bytes="+data.length);
    }catch(Exception e){Log.w("KIK","Poster "+u+": "+e);}
    final Bitmap bitmap=b;
    MAIN.post(()->{if(!key.equals(view.getTag())||!view.isAttachedToWindow()||bitmap==null)return;if(cross)swap(view,bitmap);else{view.setImageBitmap(bitmap);view.setAlpha(0f);view.animate().alpha(1f).setDuration(220).start();}});
   });
  }
 }
}
