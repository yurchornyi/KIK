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
 public static final String MODE="mode",CATEGORY="category",TITLE="title",QUERY="query";
 private int columns=6;
 static final class G{String link,title,sub,poster,badge,year,genre,sourceTitle;float progress;}

 private String mode,category,genre,filter="last";private int page=0;private boolean loading,end;
 /** The search came with a name already (OpenTitleActivity): results take focus, no keyboard over them. */
 private boolean handedOver;
 /** The search a pause in typing starts. */
 private final Runnable typed=()->reload();
 private LinearLayout grid,genres,idle;private ScrollView scroll;private TextView status;private EditText query;
 private final List<TextView> filterChips=new ArrayList<TextView>();private int generation;
 private final Map<G,View> cards=new IdentityHashMap<G,View>();
 private LinearLayout openRow;private int inRow;
 private int dp(float n){return Ui.dp(this,n);}

 public static void open(Context c,String mode,String category,String title){
  c.startActivity(new Intent(c,BrowseActivity.class).putExtra(MODE,mode).putExtra(CATEGORY,category).putExtra(TITLE,title));
 }

 @Override public void onCreate(Bundle state){
  super.onCreate(state);Ui.screen(this);columns=Math.max(2,(int)((getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density-64)/148));
  mode=getIntent().getStringExtra(MODE);category=getIntent().getStringExtra(CATEGORY);if(mode==null)mode="catalog";
  FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Ui.BG);setContentView(root);
  scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setClipChildren(false);content.setClipToPadding(false);content.setPadding(dp(32),dp(22),dp(24),dp(40));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
  LinearLayout head=new LinearLayout(this);head.setOrientation(0);head.setGravity(Gravity.CENTER_VERTICAL);content.addView(head);
  View logo=Brand.logo(this,56,26);LinearLayout.LayoutParams markLp=new LinearLayout.LayoutParams(dp(56),dp(26));markLp.rightMargin=dp(20);head.addView(logo,markLp);
  TextView title=Ui.bold(this,getIntent().getStringExtra(TITLE),30);head.addView(title);
  Ui.enter(head,0);
  Ui.space(content,14);
  if("search".equals(mode)){
   query=new EditText(this);query.setSingleLine(true);query.setHint("Назва фільму або серіалу");query.setHintTextColor(0xff8a8592);query.setTextColor(Color.WHITE);query.setTextSize(21);
   query.setPadding(dp(22),0,dp(22),0);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);query.setCompoundDrawablePadding(dp(14));
   android.graphics.drawable.Drawable lens=new LensIcon(dp(22));lens.setBounds(0,0,dp(22),dp(22));query.setCompoundDrawables(lens,null,null,null);
   query.setBackground(Ui.states(Ui.shape(0xff1f1f23,dp(30),dp(2),0xff2e2e33),Ui.shape(0xff26262b,dp(30),dp(2),Ui.RED)));
   LinearLayout bar=new LinearLayout(this);bar.setOrientation(0);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setClipChildren(false);bar.setClipToPadding(false);content.addView(bar);
   LinearLayout.LayoutParams ql=new LinearLayout.LayoutParams(dp(760),dp(60));ql.setMargins(dp(4),dp(6),dp(14),0);bar.addView(query,ql);
   // Spoken search: the TV's own voice screen (Google's), in Ukrainian; the remote's microphone key does the same.
   if(voiceAvailable()){TextView mic=new TextView(this);mic.setFocusable(true);mic.setContentDescription("Пошук голосом");
    android.graphics.drawable.Drawable icon=new MicIcon(dp(26));icon.setBounds(0,0,dp(26),dp(26));mic.setCompoundDrawables(icon,null,null,null);mic.setPadding(dp(17),0,0,0);
    mic.setBackground(Ui.states(Ui.shape(0xff1f1f23,dp(30),dp(2),0xff2e2e33),Ui.shape(0xff26262b,dp(30),dp(2),Ui.RED)));
    mic.setOnClickListener(v->listen());LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(dp(60),dp(60));ml.setMargins(0,dp(6),0,0);bar.addView(mic,ml);}
   final Runnable run=typed;
   query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}
    public void afterTextChanged(Editable s){Ui.MAIN.removeCallbacks(run);if(s.toString().trim().length()>=3)Ui.MAIN.postDelayed(run,1100);else{generation++;grid.removeAllViews();openRow=null;inRow=0;loading=false;end=true;page=0;status.setVisibility(View.GONE);showIdle(true);}}});
   query.setOnEditorActionListener((v,a,e)->{Ui.MAIN.removeCallbacks(run);remember(query.getText().toString());reload();InputMethodManager im=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);im.hideSoftInputFromWindow(v.getWindowToken(),0);return true;});
   Ui.space(content,10);
   idle=new LinearLayout(this);idle.setOrientation(1);idle.setClipChildren(false);idle.setClipToPadding(false);content.addView(idle);
  }
  if("catalog".equals(mode)){
   LinearLayout filters=new LinearLayout(this);filters.setOrientation(0);filters.setClipChildren(false);filters.setClipToPadding(false);content.addView(filters);
   String[][] f={{"last","Нові"},{"popular","Популярні"},{"watching","Зараз дивляться"}};
   for(final String[] x:f){TextView c=Ui.chip(this,x[1]);filters.addView(c,Ui.wrap(this,10));filterChips.add(c);c.setTag(x[0]);
    c.setOnClickListener(v->{filter=x[0];paintFilters();reload();});}
   final TextView g=Ui.chip(this,"Жанри ▾");filters.addView(g,Ui.wrap(this,10));
   g.setOnClickListener(v->toggleGenres());
   paintFilters();
   HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setClipToPadding(false);genres=new LinearLayout(this);genres.setOrientation(0);genres.setClipChildren(false);genres.setClipToPadding(false);genres.setPadding(dp(4),dp(12),dp(4),dp(4));hs.addView(genres);hs.setVisibility(View.GONE);
   content.addView(hs);genres.setTag(hs);
  }
  status=Ui.text(this,"Завантаження…",16,Ui.GREY);status.setPadding(dp(8),dp(16),0,dp(6));content.addView(status);
  status.setBackground(Ui.states(Ui.shape(0,dp(5),0,0),Ui.shape(0xff333337,dp(5),dp(2),Color.WHITE)));status.setOnClickListener(v->reload());status.setFocusable(false);
  grid=new LinearLayout(this);grid.setOrientation(1);grid.setClipChildren(false);grid.setClipToPadding(false);content.addView(grid);
  scroll.getViewTreeObserver().addOnScrollChangedListener(()->{if(!loading&&!end&&("catalog".equals(mode)||"search".equals(mode))){View last=grid.getChildAt(grid.getChildCount()-1);if(last!=null&&last.getBottom()-scroll.getScrollY()<scroll.getHeight()*2)more();}});
  if("search".equals(mode)){status.setVisibility(View.GONE);showIdle(true);query.requestFocus();
   // A name handed over (OpenTitleActivity found no sure match): typed in already, results load at once.
   String given=getIntent().getStringExtra(QUERY);if(given!=null&&given.trim().length()>=2){handedOver=true;getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);query.setText(given.trim());query.setSelection(query.length());query.clearFocus();}}
  else reload();
 }
 @Override protected void onResume(){super.onResume();if(("history".equals(mode)||"later".equals(mode))&&page>0)reload();}

 private void paintFilters(){for(TextView c:filterChips)Ui.select(c,filter.equals(c.getTag()));}
 private void toggleGenres(){
  View hs=(View)genres.getTag();boolean show=hs.getVisibility()!=View.VISIBLE;hs.setVisibility(show?View.VISIBLE:View.GONE);
  if(!show||genres.getChildCount()>0)return;
  Ui.bg(()->{try{final List<Site.Genre> list=Site.genres(category);List<String> names=new ArrayList<String>();for(Site.Genre x:list)names.add(x.name);
    final Map<String,String> uk=TitleTranslate.resolve(names,Ui.TRANSLATE_WAIT);
    Ui.MAIN.post(()->{TextView all=Ui.chip(this,"Усі");genres.addView(all,Ui.wrap(this,10));all.setOnClickListener(v->{genre=null;paintGenres();reload();});
     for(final Site.Genre x:list){String n=uk.get(x.name);TextView c=Ui.chip(this,n==null?TitleTranslate.shown(x.name):n);c.setTag(x.slug);genres.addView(c,Ui.wrap(this,10));c.setOnClickListener(v->{genre=x.slug;paintGenres();reload();});}
     paintGenres();});
    Map<String,String> translated=TitleTranslate.resolve(names);Ui.MAIN.post(()->{if(isFinishing()||isDestroyed())return;for(int i=0;i<list.size()&&i+1<genres.getChildCount();i++){String t=translated.get(list.get(i).name);((TextView)genres.getChildAt(i+1)).setText(t!=null?t:list.get(i).name);}});
   }catch(Exception e){Ui.MAIN.post(()->Ui.toast(this,"Не вдалося завантажити жанри"));}});
 }
 private void paintGenres(){for(int i=0;i<genres.getChildCount();i++){TextView c=(TextView)genres.getChildAt(i);Ui.select(c,genre==null?c.getTag()==null:genre.equals(c.getTag()));}}

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
    for(final G g:items){View card=Ui.card(this,g.title,g.sub,g.poster,null,0,null,()->FilmActivity.open(this,g.link,g.title,g.poster));row.addView(card);}});
  });
 }
 private void reload(){if(idle!=null)showIdle(false);cards.clear();generation++;page=0;end=false;loading=false;grid.removeAllViews();openRow=null;inRow=0;status.setFocusable(false);status.setText("Завантаження…");status.setVisibility(View.VISIBLE);more();}
 private void more(){
  if(loading||end)return;loading=true;final int gen=generation;final int next=page+1;
  final String q=query==null?null:query.getText().toString().trim();final int[] reached={next};
  Ui.bg(()->{
   List<G> items=new ArrayList<G>();String error=null;
   try{
    // A page can be fully hidden by the country filter: keep paging while the source still has titles.
    if("catalog".equals(mode)||"search".equals(mode))for(int tries=0;tries<5&&items.isEmpty();tries++){
     if("search".equals(mode)&&(q==null||q.length()<2))break;reached[0]=next+tries;
     for(Site.Card c:"catalog".equals(mode)?Site.list(category,genre,filter,reached[0]):Site.search(q,reached[0]))items.add(of(c));
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
    if(gen!=generation||isFinishing()||isDestroyed())return;loading=false;page=reached[0];
    if(err!=null){status.setText(err+"  Натисніть OK, щоб повторити.");status.setVisibility(View.VISIBLE);status.setFocusable(true);if(next==1)status.requestFocus();end=true;return;}
    if(got.isEmpty()){end=true;if(next==1){status.setText("history".equals(mode)?"Тут з’являться фільми, які ви почали дивитися":"later".equals(mode)?"Додайте фільми кнопкою «＋ Переглянути пізніше»":"Нічого не знайдено");status.setVisibility(View.VISIBLE);}return;}
    if(!"catalog".equals(mode)&&!"search".equals(mode))end=true;
    status.setVisibility(View.GONE);boolean first=grid.getChildCount()==0;View firstCard=add(got);
    if(first&&firstCard!=null&&(!"search".equals(mode)||handedOver))firstCard.requestFocus();
   });
   if(err==null&&!got.isEmpty()){
    Set<String> words=new LinkedHashSet<String>();for(G g:got){words.add(g.sourceTitle);if(g.genre!=null)words.add(g.genre);}Map<String,String> translated=TitleTranslate.resolve(words);
    Ui.MAIN.post(()->{if(gen!=generation||isFinishing()||isDestroyed())return;for(G g:got){View v=cards.get(g);if(!(v instanceof ViewGroup))continue;String t=translated.get(g.sourceTitle);g.title=t!=null?t:g.sourceTitle;((TextView)((ViewGroup)v).getChildAt(1)).setText(g.title);if(g.genre!=null){String gn=translated.get(g.genre);g.sub=Ui.join(g.year,gn!=null?gn:g.genre);((TextView)((ViewGroup)v).getChildAt(2)).setText(g.sub);}}});
   }
  });
 }
 private G of(Site.Card c){G g=new G();g.link=c.link;g.title=c.title;g.poster=c.poster;g.year=c.year();g.genre=c.genre();g.sub=Ui.join(g.year,g.genre);return g;}
 private View add(List<G> items){
  View first=null;
  for(final G g:items){
   if(openRow==null||inRow>=columns){openRow=new LinearLayout(this);openRow.setOrientation(0);openRow.setClipChildren(false);openRow.setClipToPadding(false);grid.addView(openRow);inRow=0;}
   View card=Ui.card(this,g.title,g.sub,g.poster,null,g.progress,null,()->{if(query!=null)remember(query.getText().toString());FilmActivity.open(this,g.link,g.title,g.poster);});
   // History: hold OK on a poster to remove the title from history.
   if("history".equals(mode)){View target=((ViewGroup)card).getChildAt(0);target.setOnLongClickListener(v->{Ui.dialog(this).setTitle("Прибрати з історії?").setMessage("«"+g.title+"»").setNegativeButton("Скасувати",null).setPositiveButton("Прибрати",(d,w)->{Progress.remove(this,g.link);Ui.toast(this,"Прибрано з історії");reload();}).show();return true;});}
   cards.put(g,card);openRow.addView(card);inRow++;if(first==null)first=((ViewGroup)card).getChildAt(0);
  }
  return first;
 }
 @Override public void onBackPressed(){finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
 @Override protected void onDestroy(){generation++;super.onDestroy();}
 /** Magnifier for the search field, drawn in the hint colour. */
 private static final class LensIcon extends android.graphics.drawable.Drawable {
  private final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);private final int size;
  LensIcon(int size){this.size=size;p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(size/9f);p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setColor(0xff8a8592);}
  @Override public void draw(android.graphics.Canvas c){float r=size*0.32f,cx=size*0.42f,cy=size*0.42f;c.drawCircle(cx,cy,r,p);c.drawLine(cx+r*0.72f,cy+r*0.72f,size*0.92f,size*0.92f,p);}
  @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
  @Override public void setAlpha(int a){p.setAlpha(a);}@Override public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);}@Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
 }

 private static final int VOICE=41;
 private Intent voiceIntent(){return new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
  .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"uk-UA").putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,"Скажіть назву фільму або серіалу").putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS,1);}
 private boolean voiceAvailable(){return voiceIntent().resolveActivity(getPackageManager())!=null;}
 private void listen(){try{startActivityForResult(voiceIntent(),VOICE);}catch(Exception e){Ui.toast(this,"Голосовий пошук недоступний");}}
 /** What was said goes into the field and is searched at once; the first result takes focus, as from the launcher. */
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);if(request!=VOICE||result!=RESULT_OK||data==null||query==null)return;
  ArrayList<String> heard=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(heard==null||heard.isEmpty()||heard.get(0)==null||heard.get(0).trim().length()<2)return;
  String q=heard.get(0).trim();handedOver=true;query.setText(q);query.setSelection(query.length());query.clearFocus();remember(q);Ui.MAIN.removeCallbacks(typed);reload();
 }
 /** The remote's microphone key on the search screen: KIK's own voice search, not the TV's assistant. */
 @Override public boolean dispatchKeyEvent(KeyEvent e){
  int k=e.getKeyCode();
  if(query!=null&&(k==KeyEvent.KEYCODE_SEARCH||k==KeyEvent.KEYCODE_VOICE_ASSIST)&&voiceAvailable()){if(e.getAction()==KeyEvent.ACTION_UP&&!e.isCanceled())listen();return true;}
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
