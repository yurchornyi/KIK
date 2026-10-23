package kik.i18n;
import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Poster grid for a catalogue section, search, history or the watch-later list. */
public final class BrowseActivity extends Activity {
 public static final String MODE="mode",CATEGORY="category",TITLE="title",QUERY="query",GENRE="genre";
 private int columns=6;
 static final class G{String link,title,sub,poster,badge,year,genre,sourceTitle;float progress;}

 private String mode,category,genre,filter="popular";private int page=0;private boolean loading,end;
 /** The last page failed: the cards stay, the same page is asked again by the button (not by scrolling). */
 private boolean failed;
 /** Titles already on screen (catalogue and search): a repeated source page adds nothing and ends the list. */
 private final Set<String> seen=new HashSet<String>();

 private TextView heading,categoryButton,genreButton,orderButton,summary;private Button loadMore;
 /** Genre list being fetched for this category, and a counter that makes a late list for another category inert. */
 private int genreGeneration;
 static final String[][] SECTIONS={{"films","Фільми"},{"series","Серіали"},{"cartoons","Мультфільми"},{"animation","Аніме"}};
 /** The search came with a name already (OpenTitleActivity): results take focus, no keyboard over them. */
 private boolean handedOver;
 /** The search a pause in typing starts. */
 private final Runnable typed=()->reload();
 private LinearLayout grid,idle;private ScrollView scroll;private TextView status;private EditText query;
 private int generation;
 private final Map<G,View> cards=new IdentityHashMap<G,View>();
 private LinearLayout openRow;private int inRow;
 private int dp(float n){return Ui.dp(this,n);}

 public static void open(Context c,String mode,String category,String title){
  c.startActivity(new Intent(c,BrowseActivity.class).putExtra(MODE,mode).putExtra(CATEGORY,category).putExtra(TITLE,title));
 }
 /** The catalogue already filtered to one genre (from «Жанри» on the home screen). */
 public static void openGenre(Context c,String category,String slug,String name){
  c.startActivity(new Intent(c,BrowseActivity.class).putExtra(MODE,"catalog").putExtra(CATEGORY,category).putExtra(GENRE,slug).putExtra(TITLE,name));
 }

 @Override public void onCreate(Bundle state){
  super.onCreate(state);Ui.screen(this);columns=Math.max(1,(int)((getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density-64)/148));
  mode=getIntent().getStringExtra(MODE);category=getIntent().getStringExtra(CATEGORY);genre=getIntent().getStringExtra(GENRE);if(mode==null)mode="catalog";
  FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Ui.BG);setContentView(root);
  scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setClipChildren(false);content.setClipToPadding(false);content.setPadding(dp(32),dp(22),dp(24),dp(40));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
  LinearLayout head=new LinearLayout(this);head.setOrientation(0);head.setBaselineAligned(false);head.setGravity(Gravity.CENTER_VERTICAL);content.addView(head);
  View logo=Brand.logo(this,56,26);LinearLayout.LayoutParams markLp=new LinearLayout.LayoutParams(dp(56),dp(26));markLp.rightMargin=dp(20);head.addView(logo,markLp);
  String titleText=getIntent().getStringExtra(TITLE);heading=Ui.bold(this,titleText==null?"Каталог":titleText,30);heading.setIncludeFontPadding(false);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setMaxLines(2);heading.setEllipsize(TextUtils.TruncateAt.END);head.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  Ui.enter(head,0);
  Ui.space(content,14);
  if("search".equals(mode)){
   query=new EditText(this);query.setSingleLine(true);query.setHint("Назва фільму або серіалу");query.setHintTextColor(0xff8a8592);query.setTextColor(Color.WHITE);query.setTextSize(21);
   query.setPadding(dp(22),0,dp(22),0);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);query.setCompoundDrawablePadding(dp(14));
   android.graphics.drawable.Drawable lens=new LensIcon(dp(22));lens.setBounds(0,0,dp(22),dp(22));query.setCompoundDrawables(lens,null,null,null);
   query.setBackground(Ui.states(Ui.shape(0xff1f1f23,dp(30),dp(2),0xff2e2e33),Ui.shape(0xff26262b,dp(30),dp(2),Ui.RED)));
   LinearLayout bar=new LinearLayout(this);bar.setOrientation(0);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setClipChildren(false);bar.setClipToPadding(false);content.addView(bar);
   LinearLayout.LayoutParams ql=new LinearLayout.LayoutParams(Ui.contentWidth(this,760,146),dp(60));ql.setMargins(dp(4),dp(6),dp(14),0);bar.addView(query,ql);
   // Spoken search: the TV's own voice screen (Google's), in Ukrainian; the remote's microphone key does the same.
   if(voiceAvailable()){mic=new TextView(this);mic.setFocusable(true);mic.setContentDescription("Пошук голосом");
    android.graphics.drawable.Drawable icon=new MicIcon(dp(26));icon.setBounds(0,0,dp(26),dp(26));mic.setCompoundDrawables(icon,null,null,null);mic.setPadding(dp(17),0,0,0);
    mic.setBackground(Ui.states(Ui.shape(0xff1f1f23,dp(30),dp(2),0xff2e2e33),Ui.shape(0xff26262b,dp(30),dp(2),Ui.RED)));
    mic.setOnClickListener(v->listen());LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(dp(60),dp(60));ml.setMargins(0,dp(6),0,0);bar.addView(mic,ml);}
   final Runnable run=typed;
   query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}
    public void afterTextChanged(Editable s){Ui.MAIN.removeCallbacks(run);if(s.toString().trim().length()>=3)Ui.MAIN.postDelayed(run,1100);else{generation++;cards.clear();seen.clear();failed=false;grid.removeAllViews();openRow=null;inRow=0;loading=false;end=true;page=0;status.setVisibility(View.GONE);updateMore();showIdle(true);}}});
   query.setOnEditorActionListener((v,a,e)->{Ui.MAIN.removeCallbacks(run);remember(query.getText().toString());reload();InputMethodManager im=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);im.hideSoftInputFromWindow(v.getWindowToken(),0);return true;});
   Ui.space(content,10);
   idle=new LinearLayout(this);idle.setOrientation(1);idle.setClipChildren(false);idle.setClipToPadding(false);content.addView(idle);
  }
  if("catalog".equals(mode)){
   // Netflix-style header: the category is the title itself («Фільми ▾»), genre and order are two quiet dropdowns beside it.
   if(category==null||category.isEmpty())category="films";
   // Three equal dropdowns: category, genre, order.
   heading.setVisibility(View.GONE);
   categoryButton=dropButton(sectionName(category));categoryButton.setOnClickListener(v->categoryMenu(v));head.addView(categoryButton,new LinearLayout.LayoutParams(-2,dp(44)));
   String given=getIntent().getStringExtra(TITLE);
   genreButton=dropButton(genre!=null&&given!=null?given:"Усі жанри");genreButton.setOnClickListener(v->genreMenu(v));
   orderButton=dropButton(orderName(filter));orderButton.setOnClickListener(v->orderMenu(v));
   LinearLayout.LayoutParams gl=new LinearLayout.LayoutParams(-2,dp(44));gl.leftMargin=dp(10);head.addView(genreButton,gl);
   LinearLayout.LayoutParams ol=new LinearLayout.LayoutParams(-2,dp(44));ol.leftMargin=dp(10);head.addView(orderButton,ol);
   head.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
  }
  status=Ui.text(this,"Завантаження…",16,Ui.GREY);status.setPadding(dp(8),dp(16),0,dp(6));content.addView(status);
  status.setBackground(Ui.states(Ui.shape(0,dp(5),0,0),Ui.shape(0xff333337,dp(5),dp(2),Color.WHITE)));status.setOnClickListener(v->more());status.setFocusable(false);
  grid=new LinearLayout(this);grid.setOrientation(1);grid.setClipChildren(false);grid.setClipToPadding(false);content.addView(grid);
  // Under the posters: how many are shown, and an explicit next page (also the retry after a failed page).
  summary=Ui.text(this,"",15,Ui.GREY);summary.setPadding(dp(8),dp(18),0,dp(10));summary.setVisibility(View.GONE);content.addView(summary);
  loadMore=Ui.pill(this,"Показати ще",false);loadMore.setOnClickListener(v->more());loadMore.setVisibility(View.GONE);
  LinearLayout.LayoutParams moreLp=new LinearLayout.LayoutParams(-2,-2);moreLp.leftMargin=dp(4);content.addView(loadMore,moreLp);
  scroll.getViewTreeObserver().addOnScrollChangedListener(()->{if(!loading&&!end&&!failed&&("catalog".equals(mode)||"search".equals(mode))){View last=grid.getChildAt(grid.getChildCount()-1);if(last!=null&&last.getBottom()-scroll.getScrollY()<scroll.getHeight()*2)more();}});
  if("search".equals(mode)){status.setVisibility(View.GONE);showIdle(true);query.requestFocus();
   // A name handed over (OpenTitleActivity found no sure match): typed in already, results load at once.
   String given=getIntent().getStringExtra(QUERY);if(given!=null&&given.trim().length()>=2){handedOver=true;getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);query.setText(given.trim());query.setSelection(query.length());query.clearFocus();}}
  else reload();
 }
 // History and My list change only after watching or editing: rebuild them only then, so coming back from a title does not jump.
 @Override protected void onResume(){super.onResume();if(("history".equals(mode)||"later".equals(mode))&&page>0&&!listSignature().equals(shownList))reload();}
 private String shownList="";
 private String listSignature(){if("later".equals(mode))return String.valueOf(Progress.later(this));StringBuilder b=new StringBuilder();for(Progress.Entry e:Progress.recent(this))b.append(e.link).append(e.season).append(e.episode).append(e.pos/1000).append(e.finished()).append('\n');return b.toString();}

 private static final String[][] ORDERS={{"popular","Популярні"},{"last","Нові"},{"watching","Зараз дивляться"}};
 private static String sectionName(String slug){for(String[] x:SECTIONS)if(x[0].equals(slug))return x[1];return "Каталог";}
 private static String orderName(String slug){for(String[] x:ORDERS)if(x[0].equals(slug))return x[1];return ORDERS[0][1];}
 private TextView dropButton(String label){return Ui.dropButton(this,label);}
 /** The dark dropdown the home menu uses: plain items, the white focus pill with the red edge; the chosen one is red. */
 private PopupWindow dropdown(View anchor,View panel){return dropdown(anchor,panel,ViewGroup.LayoutParams.WRAP_CONTENT);}
 private PopupWindow dropdown(View anchor,View panel,int width){
  panel.setBackground(Ui.menuPanel(this));
  PopupWindow popup=new PopupWindow(panel,width,ViewGroup.LayoutParams.WRAP_CONTENT,true);
  popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);popup.setElevation(dp(10));popup.setAnimationStyle(android.R.style.Animation_Dialog);
  popup.showAsDropDown(anchor,0,dp(6));return popup;
 }
 private TextView option(String label,boolean chosen){return Ui.menuItem(this,label,chosen);}
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(8),dp(8),dp(8),dp(8));return l;}
 private void categoryMenu(View anchor){
  LinearLayout list=column();final PopupWindow popup=dropdown(anchor,list);View chosen=null;
  for(final String[] x:SECTIONS){TextView o=option(x[1],x[0].equals(category));list.addView(o,new LinearLayout.LayoutParams(dp(200),-2));if(x[0].equals(category))chosen=o;o.setOnClickListener(v->{popup.dismiss();selectCategory(x[0],x[1]);});}
  if(chosen!=null)chosen.requestFocus();
 }
 private void orderMenu(View anchor){
  LinearLayout list=column();final PopupWindow popup=dropdown(anchor,list);View chosen=null;
  for(final String[] x:ORDERS){TextView o=option(x[1],x[0].equals(filter));list.addView(o,new LinearLayout.LayoutParams(dp(220),-2));if(x[0].equals(filter))chosen=o;
   o.setOnClickListener(v->{popup.dismiss();if(filter.equals(x[0]))return;filter=x[0];orderButton.setText(x[1]+"  ▾");reload();});}
  if(chosen!=null)chosen.requestFocus();
 }
 /** Another category in place: its own genres, the same order, the list from page one. */
 private void selectCategory(String slug,String name){
  if(slug.equals(category))return;category=slug;genre=null;genreGeneration++;genreList=null;genreFor=null;
  genreButton.setText("Усі жанри  ▾");categoryButton.setText(name+"  ▾");reload();
 }
 private List<Site.Genre> genreList;private String genreFor;private Map<String,String> genreUk=new HashMap<String,String>();
 /** Genres of the shown category as a grid: everyday ones first, «Усі жанри» at the top. Fetched once per category. */
 private void genreMenu(View anchor){
  final LinearLayout box=column();final PopupWindow popup=dropdown(anchor,box,dp(3*170+16));
  final TextView all=option("Усі жанри",genre==null);box.addView(all,new LinearLayout.LayoutParams(dp(170),-2));all.setOnClickListener(v->{popup.dismiss();if(genre==null)return;genre=null;genreButton.setText("Усі жанри  ▾");reload();});all.requestFocus();
  if(genreList!=null&&category.equals(genreFor)){fillGenreGrid(box,popup,genreList);return;}
  final TextView wait=Ui.text(this,"Завантаження…",14,Ui.GREY);wait.setPadding(dp(16),dp(10),dp(16),dp(10));box.addView(wait);
  final int request=++genreGeneration;final String requested=category;
  Ui.bg(()->{try{List<Site.Genre> got=Site.genres(requested);List<String> unknown=new ArrayList<String>();for(Site.Genre g:got)if(!HomeActivity.genreKnown(g.name))unknown.add(g.name);
    final Map<String,String> uk=unknown.isEmpty()?new HashMap<String,String>():TitleTranslate.resolve(unknown,Ui.TRANSLATE_WAIT);
    final List<Site.Genre> list=ordered(got,uk);
    Ui.MAIN.post(()->{if(isFinishing()||isDestroyed()||request!=genreGeneration||!requested.equals(category))return;genreUk.putAll(uk);genreList=list;genreFor=requested;if(!popup.isShowing())return;box.removeView(wait);
     if(list.isEmpty()){wait.setText("Для цієї категорії жанрів немає");box.addView(wait);return;}fillGenreGrid(box,popup,list);});
   }catch(Exception e){Ui.MAIN.post(()->{if(request==genreGeneration&&popup.isShowing())wait.setText("Не вдалося завантажити жанри");});}});
 }
 private String genreLabel(Site.Genre g){return HomeActivity.genreKnown(g.name)?HomeActivity.genreName(g.name):genreUk.containsKey(g.name)?genreUk.get(g.name):TitleTranslate.shown(g.name);}
 private List<Site.Genre> ordered(List<Site.Genre> got,Map<String,String> uk){return HomeActivity.mainGenres(got);}
 private void fillGenreGrid(LinearLayout box,final PopupWindow popup,List<Site.Genre> list){
  LinearLayout row=null;View chosen=null;
  for(int n=0;n<list.size();n++){final Site.Genre g=list.get(n);if(n%3==0){row=new LinearLayout(this);row.setOrientation(0);box.addView(row);}
   final String name=genreLabel(g);TextView o=option(name,g.slug.equals(genre));row.addView(o,new LinearLayout.LayoutParams(dp(170),-2));if(g.slug.equals(genre))chosen=o;
   o.setOnClickListener(v->{popup.dismiss();if(g.slug.equals(genre))return;genre=g.slug;genreButton.setText(name+"  ▾");reload();});}
  if(chosen!=null)chosen.requestFocus();
 }

 /** Recent searches of this profile, newest first. */
 private SharedPreferences searches(){return getSharedPreferences("kik_search_"+ProfileStore.current(this),0);}
 private List<String> recent(){List<String> out=new ArrayList<String>();try{JSONArray a=new JSONArray(searches().getString("recent","[]"));for(int i=0;i<a.length();i++)out.add(a.getString(i));}catch(Exception ignored){}return out;}
 private void remember(String q){q=q==null?"":q.trim();if(q.length()<2)return;List<String> list=recent();for(Iterator<String> it=list.iterator();it.hasNext();)if(it.next().equalsIgnoreCase(q))it.remove();list.add(0,q);JSONArray a=new JSONArray();for(int i=0;i<Math.min(8,list.size());i++)a.put(list.get(i));searches().edit().putString("recent",a.toString()).apply();}
 /** Empty search: recent searches and recommendations instead of a blank screen. */
 private void showIdle(boolean on){
  if(idle==null)return;idle.setVisibility(on?View.VISIBLE:View.GONE);if(!on||idle.getChildCount()>0)return;
  List<String> list=recent();
  if(!list.isEmpty()){
   TextView h=Ui.bold(this,"Останні пошуки",20);h.setPadding(dp(4),dp(18),0,dp(10));idle.addView(h);
   LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.setClipChildren(false);row.setClipToPadding(false);row.setPadding(dp(4),dp(4),dp(4),dp(4));HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setClipToPadding(false);hs.addView(row);idle.addView(hs);
   for(final String q:list){TextView c=Ui.chip(this,q);row.addView(c,Ui.wrap(this,10));c.setOnClickListener(v->{query.setText(q);query.setSelection(query.length());remember(q);reload();});}
   TextView clear=Ui.chip(this,"Очистити");clear.setAlpha(0.7f);row.addView(clear,Ui.wrap(this,10));clear.setOnClickListener(v->{searches().edit().clear().apply();idle.removeAllViews();showIdle(true);query.requestFocus();});
  }
  final TextView h=Ui.bold(this,"Рекомендовано для вас",20);h.setPadding(dp(4),dp(22),0,dp(10));h.setVisibility(View.GONE);idle.addView(h);
  final LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.setClipChildren(false);row.setClipToPadding(false);row.setPadding(dp(4),dp(4),dp(4),dp(12));HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setClipToPadding(false);hs.addView(row);idle.addView(hs);
  final String profile=ProfileStore.current(this);
  Ui.bg(()->{
   List<G> items=new ArrayList<G>();String[] heading={"Рекомендовано для вас"};
   try{Recommendations.Snapshot s=Recommendations.snapshot(this,profile);HomeActivity.Row r=Recommendations.row(this,s,new ArrayList<HomeActivity.Row>());if(!"Рекомендовано для вас".equals(r.title))heading[0]=r.title;for(HomeActivity.Item x:r.items){if(x==null)continue;G g=new G();g.link=x.link;g.title=x.title;g.poster=x.poster;g.year=x.year;g.genre=x.genre;g.sub=Ui.join(g.year,g.genre);items.add(g);if(items.size()>=12)break;}}catch(Exception ignored){}
   if(items.isEmpty())try{heading[0]="Популярне зараз";for(Site.Card c:Site.list("",null,"popular",1)){items.add(of(c));if(items.size()>=12)break;}}catch(Exception ignored){}
   Set<String> words=new HashSet<String>();for(G g:items){words.add(g.title);if(g.genre!=null)words.add(g.genre);}Map<String,String> uk=TitleTranslate.resolve(words);
   for(G g:items){String t=uk.get(g.title);if(t!=null)g.title=t;if(g.genre!=null){String gn=uk.get(g.genre);if(gn!=null)g.sub=Ui.join(g.year,gn);}}
   Ui.MAIN.post(()->{if(isFinishing()||isDestroyed()||items.isEmpty())return;h.setText(heading[0]);h.setVisibility(View.VISIBLE);
    for(final G g:items){View card=Ui.card(this,g.title,g.sub,g.poster,null,0,null,()->FilmActivity.open(this,g.link,g.title,g.poster,g.year));row.addView(card);}});
  });
 }
 private boolean paged(){return "catalog".equals(mode)||"search".equals(mode);}
 private void reload(){Ui.MAIN.removeCallbacks(typed);if(isFinishing()||isDestroyed())return;if("history".equals(mode)||"later".equals(mode))shownList=listSignature();if(idle!=null)showIdle(false);cards.clear();seen.clear();generation++;page=0;end=false;loading=false;failed=false;grid.removeAllViews();openRow=null;inRow=0;scroll.scrollTo(0,0);status.setFocusable(false);status.setText("Завантаження…");status.setVisibility(View.VISIBLE);updateMore();more();}
 /** The count and the next-page button under the posters. */
 private void updateMore(){
  if(loadMore==null)return;boolean show=paged()&&page>0&&(!end||failed)&&!seen.isEmpty();
  loadMore.setVisibility(show?View.VISIBLE:View.GONE);loadMore.setEnabled(!loading);loadMore.setText(loading?"Завантаження…":failed?"Спробувати ще":"Показати ще");
  String text=!paged()||seen.isEmpty()?"":failed?"Не вдалося завантажити наступну сторінку. Список збережено.":"Показано: "+cards.size()+(end?" · це всі результати":"");
  summary.setText(text);summary.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);
 }
 private void more(){
  if(loading||end)return;
  final boolean focusMore=loadMore!=null&&loadMore.hasFocus();loading=true;updateMore();
  if(page==0){status.setFocusable(false);status.setText("Завантаження…");status.setVisibility(View.VISIBLE);}
  final int gen=generation;final int next=page+1;final long started=android.os.SystemClock.elapsedRealtime();
  // What the request is for, captured now: a chip pressed meanwhile reloads and makes this answer obsolete anyway.
  final String requestedCategory=category,requestedGenre=genre,requestedFilter=filter;
  final String q=query==null?null:query.getText().toString().trim();final int[] reached={next};
  Ui.bg(()->{
   List<G> items=new ArrayList<G>();String error=null;
   try{
    // A page can be fully hidden by the country filter: keep paging while the source still has titles.
    if("catalog".equals(mode)||"search".equals(mode))for(int tries=0;tries<5&&items.isEmpty();tries++){
     if("search".equals(mode)&&(q==null||q.length()<2))break;reached[0]=next+tries;
     for(Site.Card c:"catalog".equals(mode)?Site.list(requestedCategory,requestedGenre,requestedFilter,reached[0]):Site.search(q,reached[0]))items.add(of(c));
     if(Site.lastRaw()==0)break;}
    else if(next==1&&"history".equals(mode))for(Progress.Entry e:Progress.recent(this)){G g=new G();g.link=e.link;g.title=e.title;g.poster=e.poster;
      g.sub=(e.season!=null&&!"0".equals(e.season)?"S"+e.season+"E"+e.episode+"  •  ":"")+(e.finished()?"переглянуто":"зупинились на "+Ui.time(e.pos));g.progress=e.dur>0?(float)e.pos/e.dur:0;items.add(g);}
    else if(next==1&&"later".equals(mode)){JSONArray a=Progress.later(this);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;G g=new G();g.link=o.optString("link");g.title=o.optString("title");g.poster=o.optString("poster",null);g.sub=Ui.join(o.optString("year",""),o.optString("genre",""));items.add(g);}}
    Set<String> words=new HashSet<String>();for(G g:items){g.sourceTitle=g.title;words.add(g.title);if(g.genre!=null)words.add(g.genre);}
    Map<String,String> uk=TitleTranslate.resolve(words,Ui.TRANSLATE_WAIT);
    for(G g:items){String t=uk.get(g.title);g.title=t!=null?t:TitleTranslate.shown(g.title);if(g.genre!=null){String gn=uk.get(g.genre);g.sub=Ui.join(g.year,gn==null?TitleTranslate.shown(g.genre):gn);}}
   }catch(Exception e){error="Не вдалося завантажити. Перевірте інтернет і спробуйте ще раз.";}
   final List<G> got=items;final String err=error;
   Ui.MAIN.post(()->{
    if(gen!=generation||isFinishing()||isDestroyed())return;loading=false;
    // A failed page keeps the cards and the page number: OK on the message (first page) or the button asks it again.
    if(err!=null){failed=true;if(seen.isEmpty()){status.setText(err+"  Натисніть OK, щоб повторити.");status.setVisibility(View.VISIBLE);status.setFocusable(true);status.requestFocus();updateMore();}
     else{updateMore();loadMore.requestFocus();Ui.toast(this,"Не вдалося завантажити наступну сторінку. Список збережено.");}return;}
    failed=false;page=reached[0];status.setFocusable(false);
    List<G> fresh=got;if(paged()){fresh=new ArrayList<G>();for(G g:got)if(seen.add(Progress.norm(g.link)))fresh.add(g);}
    // Nothing new: an empty page, or the source repeating a page it has already given.
    if(fresh.isEmpty()){end=true;if(cards.isEmpty()){status.setText("history".equals(mode)?"Тут з’являться фільми, які ви почали дивитися":"later".equals(mode)?"Додайте фільми кнопкою «＋ Переглянути пізніше»":"Нічого не знайдено");status.setVisibility(View.VISIBLE);}else status.setVisibility(View.GONE);
     updateMore();if(focusMore)focusLast();return;}
    if(!paged()||reached[0]>=100)end=true;
    status.setVisibility(View.GONE);boolean first=grid.getChildCount()==0;View firstCard=add(fresh);updateMore();android.util.Log.d("KIK.Load","Browse "+mode+" page "+next+" "+(android.os.SystemClock.elapsedRealtime()-started)+"ms");
    if(firstCard!=null&&(focusMore||first&&(!"search".equals(mode)||handedOver)))firstCard.requestFocus();
   });
   if(err==null&&!got.isEmpty()){
    Set<String> words=new LinkedHashSet<String>();for(G g:got){words.add(g.sourceTitle);if(g.genre!=null)words.add(g.genre);}Map<String,String> translated=TitleTranslate.resolve(words);
    Ui.MAIN.post(()->{if(gen!=generation||isFinishing()||isDestroyed())return;for(G g:got){View v=cards.get(g);if(!(v instanceof ViewGroup))continue;String t=translated.get(g.sourceTitle);g.title=t!=null?t:g.sourceTitle;((TextView)((ViewGroup)v).getChildAt(1)).setText(g.title);if(g.genre!=null){String gn=translated.get(g.genre);g.sub=Ui.join(g.year,gn!=null?gn:g.genre);((TextView)((ViewGroup)v).getChildAt(2)).setText(g.sub);}}});
   }
  });
 }
 /** Focus on the last poster when the button that had it disappears. */
 private void focusLast(){if(openRow!=null&&openRow.getChildCount()>0){View v=openRow.getChildAt(openRow.getChildCount()-1);if(v instanceof ViewGroup)((ViewGroup)v).getChildAt(0).requestFocus();}}
 private G of(Site.Card c){G g=new G();g.link=c.link;g.title=c.title;g.poster=c.poster;g.year=c.year();g.genre=c.genre();g.sub=Ui.join(g.year,g.genre);return g;}
 private View add(List<G> items){
  View first=null;
  for(final G g:items){
   if(openRow==null||inRow>=columns){openRow=new LinearLayout(this);openRow.setOrientation(0);openRow.setClipChildren(false);openRow.setClipToPadding(false);grid.addView(openRow);inRow=0;}
   View card=Ui.card(this,g.title,g.sub,g.poster,null,g.progress,null,()->{if(query!=null)remember(query.getText().toString());FilmActivity.open(this,g.link,g.title,g.poster,g.year);});
   // History: hold OK on a poster to remove the title from history.
   if("history".equals(mode)){View target=((ViewGroup)card).getChildAt(0);target.setOnLongClickListener(v->{Ui.dialog(this).setTitle("Прибрати з історії?").setMessage("«"+g.title+"»").setNegativeButton("Скасувати",null).setPositiveButton("Прибрати",(d,w)->{Progress.remove(this,g.link);Ui.toast(this,"Прибрано з історії");reload();}).show();return true;});}
   cards.put(g,card);openRow.addView(card);inRow++;if(first==null)first=((ViewGroup)card).getChildAt(0);
  }
  return first;
 }
 @Override public void onBackPressed(){finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
 @Override protected void onDestroy(){generation++;genreGeneration++;Ui.MAIN.removeCallbacks(typed);stopListening();cards.clear();super.onDestroy();}
 /** Magnifier for the search field, drawn in the hint colour. */
 private static final class LensIcon extends android.graphics.drawable.Drawable {
  private final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);private final int size;
  LensIcon(int size){this.size=size;p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(size/9f);p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setColor(0xff8a8592);}
  @Override public void draw(android.graphics.Canvas c){float r=size*0.32f,cx=size*0.42f,cy=size*0.42f;c.drawCircle(cx,cy,r,p);c.drawLine(cx+r*0.72f,cy+r*0.72f,size*0.92f,size*0.92f,p);}
  @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
  @Override public void setAlpha(int a){p.setAlpha(a);}@Override public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);}@Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
 }

 private static final int VOICE=41;
 private TextView mic;private android.speech.SpeechRecognizer recognizer;
 private Intent voiceIntent(){return new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
  .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"uk-UA").putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,"Скажіть назву фільму або серіалу").putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS,1);}
 private boolean voiceAvailable(){return voiceIntent().resolveActivity(getPackageManager())!=null;}
 /**
  * Listens in place, the way the launcher does: no screen over KIK, the words appear in the field as they are heard,
  * the microphone pulses with the voice. The remote's own microphone reaches the recognizer through the TV's remote
  * service. Without the microphone permission (asked once) or a recognition service, Google's voice screen instead.
  */
 private void listen(){
  if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
   if(!getPreferences(0).getBoolean("mic_asked",false)){getPreferences(0).edit().putBoolean("mic_asked",true).apply();requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},VOICE);return;}
   voiceScreen();return;}
  if(!android.speech.SpeechRecognizer.isRecognitionAvailable(this)){voiceScreen();return;}
  stopListening();
  try{recognizer=android.speech.SpeechRecognizer.createSpeechRecognizer(this);}catch(RuntimeException e){voiceScreen();return;}
  recognizer.setRecognitionListener(new android.speech.RecognitionListener(){
   public void onReadyForSpeech(Bundle b){}public void onBeginningOfSpeech(){}public void onBufferReceived(byte[] b){}public void onEvent(int t,Bundle b){}
   public void onRmsChanged(float db){if(mic!=null&&recognizer!=null){float sc=1.05f+0.25f*Math.max(0f,Math.min(1f,(db+2f)/12f));mic.animate().cancel();mic.animate().scaleX(sc).scaleY(sc).setDuration(120).start();}}
   public void onEndOfSpeech(){}
   public void onPartialResults(Bundle b){String w=first(b);if(!w.isEmpty()){query.setText(w);query.setSelection(query.length());}}
   public void onResults(Bundle b){stopListening();String w=first(b);if(w.length()>=2)searchHeard(w);else notHeard();}
   public void onError(int error){stopListening();notHeard();}
  });
  query.setText("");query.setHint("Слухаю…");if(mic!=null){mic.setActivated(true);mic.requestFocus();}
  try{recognizer.startListening(voiceIntent().putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS,true).putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName()));}
  catch(RuntimeException e){stopListening();voiceScreen();}
 }
 private void stopListening(){
  if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(RuntimeException ignored){}recognizer=null;}
  if(mic!=null){mic.setActivated(false);mic.animate().cancel();mic.animate().scaleX(1f).scaleY(1f).setDuration(200).start();}
  if(query!=null)query.setHint("Назва фільму або серіалу");
 }
 private void notHeard(){if(query.length()==0)query.setHint("Не почув. Натисніть мікрофон і скажіть ще раз");}
 private static String first(Bundle b){ArrayList<String> l=b==null?null:b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);return l==null||l.isEmpty()||l.get(0)==null?"":l.get(0).trim();}
 private void searchHeard(String q){handedOver=true;query.setText(q);query.setSelection(query.length());query.clearFocus();remember(q);Ui.MAIN.removeCallbacks(typed);reload();}
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
  super.onRequestPermissionsResult(request,permissions,results);if(request==VOICE)listen();
 }
 @Override protected void onPause(){stopListening();super.onPause();}
 /** Google TV's own voice screen (the Google app's) when there is one, not the chooser between it and the speech module. */
 private void voiceScreen(){
  Intent i=voiceIntent();for(android.content.pm.ResolveInfo r:getPackageManager().queryIntentActivities(i,0))if(r.activityInfo!=null&&"com.google.android.katniss".equals(r.activityInfo.packageName)){i.setClassName(r.activityInfo.packageName,r.activityInfo.name);break;}
  try{startActivityForResult(i,VOICE);}catch(Exception e){Ui.toast(this,"Голосовий пошук недоступний");}}
 /** What was said goes into the field and is searched at once; the first result takes focus, as from the launcher. */
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);if(request!=VOICE||result!=RESULT_OK||data==null||query==null)return;
  ArrayList<String> heard=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(heard==null||heard.isEmpty()||heard.get(0)==null||heard.get(0).trim().length()<2)return;
  searchHeard(heard.get(0).trim());
 }
 /** The remote's microphone key on the search screen: KIK's own voice search, not the TV's assistant; it starts on the
  * press, so a remote that sends the voice only while the key is held is heard too. */
 @Override public boolean dispatchKeyEvent(KeyEvent e){
  int k=e.getKeyCode();
  if(query!=null&&(k==KeyEvent.KEYCODE_SEARCH||k==KeyEvent.KEYCODE_VOICE_ASSIST)&&voiceAvailable()){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)listen();return true;}
  return super.dispatchKeyEvent(e);
 }
 /** A microphone, drawn in the hint colour like the magnifier. */
 private static final class MicIcon extends android.graphics.drawable.Drawable {
  private final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);private final int size;
  MicIcon(int size){this.size=size;p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(size/11f);p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setColor(0xffd8d4de);}
  @Override public void draw(android.graphics.Canvas c){float w=size*0.30f,cx=size/2f;c.drawRoundRect(new android.graphics.RectF(cx-w/2,size*0.08f,cx+w/2,size*0.60f),w/2,w/2,p);
   c.drawArc(new android.graphics.RectF(cx-size*0.30f,size*0.22f,cx+size*0.30f,size*0.76f),20,140,false,p);c.drawLine(cx,size*0.76f,cx,size*0.92f,p);}
  @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
  @Override public void setAlpha(int a){p.setAlpha(a);}@Override public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);}@Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
 }
}
