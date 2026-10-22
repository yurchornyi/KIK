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
 public static final String MODE="mode",CATEGORY="category",TITLE="title";
 private int columns=6;
 static final class G{String link,title,sub,poster,badge,year,genre,sourceTitle;float progress;}

 private String mode,category,genre,filter="last";private int page=0;private boolean loading,end;
 private LinearLayout grid,genres;private ScrollView scroll;private TextView status;private EditText query;
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
   query=new EditText(this);query.setSingleLine(true);query.setHint("Назва фільму або серіалу");query.setHintTextColor(0xff8a8592);query.setTextColor(Color.WHITE);query.setTextSize(20);
   query.setPadding(dp(18),0,dp(18),0);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
   query.setBackground(Ui.states(Ui.shape(0xff1f1f23,dp(8),dp(2),0xff2e2e33),Ui.shape(0xff1f1f23,dp(8),dp(2),Ui.RED)));
   content.addView(query,new LinearLayout.LayoutParams(dp(640),dp(56)));
   final Runnable run=()->reload();
   query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}
    public void afterTextChanged(Editable s){Ui.MAIN.removeCallbacks(run);if(s.toString().trim().length()>=2)Ui.MAIN.postDelayed(run,500);else{generation++;grid.removeAllViews();openRow=null;inRow=0;loading=false;end=true;page=0;status.setText("Введіть щонайменше 2 символи");status.setVisibility(View.VISIBLE);}}});
   query.setOnEditorActionListener((v,a,e)->{Ui.MAIN.removeCallbacks(run);reload();InputMethodManager im=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);im.hideSoftInputFromWindow(v.getWindowToken(),0);return true;});
   Ui.space(content,10);
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
  if("search".equals(mode)){status.setText("Почніть вводити назву");query.requestFocus();}else reload();
 }
 @Override protected void onResume(){super.onResume();if(("history".equals(mode)||"later".equals(mode))&&page>0)reload();}

 private void paintFilters(){for(TextView c:filterChips)Ui.select(c,filter.equals(c.getTag()));}
 private void toggleGenres(){
  View hs=(View)genres.getTag();boolean show=hs.getVisibility()!=View.VISIBLE;hs.setVisibility(show?View.VISIBLE:View.GONE);
  if(!show||genres.getChildCount()>0)return;
  Ui.bg(()->{try{final List<Site.Genre> list=Site.genres(category);List<String> names=new ArrayList<String>();for(Site.Genre x:list)names.add(x.name);
    final Map<String,String> uk=TitleTranslate.cached(names);
    Ui.MAIN.post(()->{TextView all=Ui.chip(this,"Усі");genres.addView(all,Ui.wrap(this,10));all.setOnClickListener(v->{genre=null;paintGenres();reload();});
     for(final Site.Genre x:list){String n=uk.get(x.name);TextView c=Ui.chip(this,n==null?x.name:n);c.setTag(x.slug);genres.addView(c,Ui.wrap(this,10));c.setOnClickListener(v->{genre=x.slug;paintGenres();reload();});}
     paintGenres();});
    Map<String,String> translated=TitleTranslate.resolve(names);Ui.MAIN.post(()->{if(isFinishing()||isDestroyed())return;for(int i=0;i<list.size()&&i+1<genres.getChildCount();i++){String t=translated.get(list.get(i).name);if(t!=null)((TextView)genres.getChildAt(i+1)).setText(t);}});
   }catch(Exception e){Ui.MAIN.post(()->Ui.toast(this,"Не вдалося завантажити жанри"));}});
 }
 private void paintGenres(){for(int i=0;i<genres.getChildCount();i++){TextView c=(TextView)genres.getChildAt(i);Ui.select(c,genre==null?c.getTag()==null:genre.equals(c.getTag()));}}

 private void reload(){cards.clear();generation++;page=0;end=false;loading=false;grid.removeAllViews();openRow=null;inRow=0;status.setFocusable(false);status.setText("Завантаження…");status.setVisibility(View.VISIBLE);more();}
 private void more(){
  if(loading||end)return;loading=true;final int gen=generation;final int next=page+1;
  final String q=query==null?null:query.getText().toString().trim();
  Ui.bg(()->{
   List<G> items=new ArrayList<G>();String error=null;
   try{
    if("catalog".equals(mode))for(Site.Card c:Site.list(category,genre,filter,next))items.add(of(c));
    else if("search".equals(mode)){if(q!=null&&q.length()>=2)for(Site.Card c:Site.search(q,next))items.add(of(c));}
    else if(next==1&&"history".equals(mode))for(Progress.Entry e:Progress.recent(this)){G g=new G();g.link=e.link;g.title=e.title;g.poster=e.poster;
      g.sub=(e.season!=null&&!"0".equals(e.season)?"S"+e.season+"E"+e.episode+"  •  ":"")+(e.finished()?"переглянуто":"зупинились на "+Ui.time(e.pos));g.progress=e.dur>0?(float)e.pos/e.dur:0;items.add(g);}
    else if(next==1&&"later".equals(mode)){JSONArray a=Progress.later(this);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;G g=new G();g.link=o.optString("link");g.title=o.optString("title");g.poster=o.optString("poster",null);g.sub=Ui.join(o.optString("year",""),o.optString("genre",""));items.add(g);}}
    Set<String> words=new HashSet<String>();for(G g:items){g.sourceTitle=g.title;words.add(g.title);if(g.genre!=null)words.add(g.genre);}
    Map<String,String> uk=TitleTranslate.cached(words);
    for(G g:items){String t=uk.get(g.title);if(t!=null)g.title=t;if(g.genre!=null){String gn=uk.get(g.genre);g.sub=Ui.join(g.year,gn==null?g.genre:gn);}}
   }catch(Exception e){error="Не вдалося завантажити. Перевірте інтернет і спробуйте ще раз.";}
   final List<G> got=items;final String err=error;
   Ui.MAIN.post(()->{
    if(gen!=generation||isFinishing()||isDestroyed())return;loading=false;page=next;
    if(err!=null){status.setText(err+"  Натисніть OK, щоб повторити.");status.setVisibility(View.VISIBLE);status.setFocusable(true);if(next==1)status.requestFocus();end=true;return;}
    if(got.isEmpty()){end=true;if(next==1){status.setText("history".equals(mode)?"Тут з’являться фільми, які ви почали дивитися":"later".equals(mode)?"Додайте фільми кнопкою «＋ Переглянути пізніше»":"Нічого не знайдено");status.setVisibility(View.VISIBLE);}return;}
    if(!"catalog".equals(mode)&&!"search".equals(mode))end=true;
    status.setVisibility(View.GONE);boolean first=grid.getChildCount()==0;View firstCard=add(got);
    if(first&&firstCard!=null&&!"search".equals(mode))firstCard.requestFocus();
   });
   if(err==null&&!got.isEmpty()){
    Set<String> words=new LinkedHashSet<String>();for(G g:got){words.add(g.sourceTitle);if(g.genre!=null)words.add(g.genre);}Map<String,String> translated=TitleTranslate.resolve(words);
    Ui.MAIN.post(()->{if(gen!=generation||isFinishing()||isDestroyed())return;for(G g:got){View v=cards.get(g);if(!(v instanceof ViewGroup))continue;String t=translated.get(g.sourceTitle);if(t!=null){g.title=t;((TextView)((ViewGroup)v).getChildAt(1)).setText(t);}String gn=translated.get(g.genre);if(gn!=null){g.sub=Ui.join(g.year,gn);((TextView)((ViewGroup)v).getChildAt(2)).setText(g.sub);}}});
   }
  });
 }
 private G of(Site.Card c){G g=new G();g.link=c.link;g.title=c.title;g.poster=c.poster;g.year=c.year();g.genre=c.genre();g.sub=Ui.join(g.year,g.genre);return g;}
 private View add(List<G> items){
  View first=null;
  for(final G g:items){
   if(openRow==null||inRow>=columns){openRow=new LinearLayout(this);openRow.setOrientation(0);openRow.setClipChildren(false);openRow.setClipToPadding(false);grid.addView(openRow);inRow=0;}
   View card=Ui.card(this,g.title,g.sub,g.poster,null,g.progress,null,()->FilmActivity.open(this,g.link,g.title,g.poster));
   cards.put(g,card);openRow.addView(card);inRow++;if(first==null)first=((ViewGroup)card).getChildAt(0);
  }
  return first;
 }
 @Override public void onBackPressed(){finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
 @Override protected void onDestroy(){generation++;super.onDestroy();}
}
