package kik.i18n;
import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Remote-first initial taste selection. Back/Skip discards the draft; Save commits it once. */
public final class TasteActivity extends Activity {
 private String profile;private boolean setup,busy,loading;private volatile boolean destroyed;private int columns;
 private LinearLayout grid;private View lastPoster;private ScrollView scroll;private TextView status,counter;private Button skip,save,retry;
 private final Map<String,HomeActivity.Item> choices=new LinkedHashMap<>();
 private final Set<String> selected=new LinkedHashSet<>(),previous=new HashSet<>();
 private final Map<String,FrameLayout> frames=new HashMap<>();private final Map<String,TextView> checks=new HashMap<>(),titles=new HashMap<>(),metas=new HashMap<>();
 private final Map<String,String> uk=new HashMap<>();
 // Cards in screen order; picking a title inserts similar ones right after it, once per title.
 private final List<String> order=new ArrayList<>();private final Map<String,View> cards=new HashMap<>();private final List<LinearLayout> rows=new ArrayList<>();private final Set<String> expanded=new HashSet<>();
 private static final int MAX_PICKS=5;
 private int dp(float n){return Ui.dp(this,n);}
 public static void open(Activity a,boolean setup){a.startActivity(new Intent(a,TasteActivity.class).putExtra("setup",setup));}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);Ui.screen(this);profile=ProfileStore.current(this);setup=getIntent().getBooleanExtra("setup",true);
  if(ProfileStore.nameOf(this,profile)==null){finish();return;}
  if(setup&&!ProfileStore.needsTasteSetup(this,profile)){goHome();return;}
  // Only the latest five likes start ticked; older likes stay liked and are not touched by this screen.
  for(RatingStore.Rating r:RatingStore.list(this,profile))if(r.value>0&&previous.size()<MAX_PICKS){String key=RecommendationRanker.key(r.link);previous.add(key);selected.add(key);}
  if(state!=null&&profile.equals(state.getString("profile"))){selected.clear();ArrayList<String> draft=state.getStringArrayList("selected");if(draft!=null)selected.addAll(draft);}
  columns=Math.max(2,(int)((getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density-64)/148));
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Ui.BG);setContentView(root);
  LinearLayout header=new LinearLayout(this);header.setOrientation(1);header.setPadding(dp(40),dp(18),dp(40),dp(8));root.addView(header,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout brand=new LinearLayout(this);brand.setBaselineAligned(false);brand.setGravity(Gravity.CENTER_VERTICAL);brand.addView(Brand.logo(this,56,26));TextView step=Ui.text(this,setup?"Знайомимося з вашим смаком":"Ваші вподобання",14,Ui.GREY);step.setIncludeFontPadding(false);step.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams stepLp=new LinearLayout.LayoutParams(-2,-2);stepLp.leftMargin=dp(20);brand.addView(step,stepLp);header.addView(brand);Ui.space(header,10);
  TextView heading=Ui.bold(this,setup?"Що вам подобається, "+ProfileStore.nameOf(this,profile)+"?":"Оберіть те, що вам подобається",28);heading.setMaxLines(2);heading.setEllipsize(TextUtils.TruncateAt.END);header.addView(heading);
  TextView hint=Ui.text(this,"Оберіть до "+MAX_PICKS+" фільмів чи серіалів. Після кожного з’являться схожі, а головна підлаштується під ваш смак.",15,Ui.GREY);hint.setMaxLines(2);header.addView(hint);
  scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(dp(32),0,dp(24),dp(8));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  grid=new LinearLayout(this);grid.setOrientation(1);grid.setClipChildren(false);grid.setClipToPadding(false);scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
  LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setClipChildren(false);footer.setClipToPadding(false);footer.setPadding(dp(40),dp(10),dp(40),dp(18));root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
  skip=Ui.pill(this,setup?"Пропустити":"Скасувати",false);skip.setId(View.generateViewId());footer.addView(skip,new LinearLayout.LayoutParams(-2,dp(48)));skip.setOnClickListener(v->complete(false));
  counter=Ui.text(this,"",15,Ui.GREY);counter.setGravity(Gravity.CENTER);footer.addView(counter,new LinearLayout.LayoutParams(0,-2,1));
  save=Ui.pill(this,setup?"Продовжити":"Зберегти",true);save.setId(View.generateViewId());footer.addView(save,new LinearLayout.LayoutParams(-2,dp(48)));save.setOnClickListener(v->complete(true));
  status=Ui.text(this,"Завантажуємо фільми та серіали…",15,Ui.GREY);status.setPadding(dp(8),dp(8),dp(8),dp(8));grid.addView(status);
  retry=Ui.pill(this,"Спробувати ще",false);grid.addView(retry,new LinearLayout.LayoutParams(-2,dp(48)));retry.setVisibility(View.GONE);retry.setOnClickListener(v->load());
  if(state!=null&&profile.equals(state.getString("profile"))){try{JSONArray a=new JSONArray(state.getString("choices","[]"));List<HomeActivity.Item> restored=new ArrayList<>();for(int n=0;n<a.length();n++){JSONObject o=a.getJSONObject(n);HomeActivity.Item i=new HomeActivity.Item();i.link=o.getString("link");i.title=o.getString("title");i.poster=o.optString("poster",null);i.year=o.optString("year",null);i.genre=o.optString("genre",null);restored.add(i);}append(restored);}catch(JSONException ignored){}}
  paintCounter();skip.requestFocus();if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::onBackPressed);load();
 }
 @Override protected void onSaveInstanceState(Bundle state){state.putString("profile",profile);state.putStringArrayList("selected",new ArrayList<>(selected));JSONArray a=new JSONArray();try{for(HomeActivity.Item i:choices.values())a.put(new JSONObject().put("link",i.link).put("title",i.title).put("poster",i.poster).put("year",i.year).put("genre",i.genre));state.putString("choices",a.toString());}catch(JSONException ignored){}super.onSaveInstanceState(state);}
 private boolean valid(){return !destroyed&&!isFinishing()&&profile.equals(ProfileStore.current(this))&&ProfileStore.nameOf(this,profile)!=null;}
 private void load(){if(loading)return;loading=true;retry.setVisibility(View.GONE);
  Ui.bg(()->{
   List<HomeActivity.Item> cached=TasteCatalogue.cached(this,profile);Map<String,String> translated=TitleTranslate.resolve(words(cached),Ui.TRANSLATE_WAIT);
   Ui.MAIN.post(()->{if(valid()){uk.putAll(translated);append(cached);}});
   List<HomeActivity.Item> fresh=Collections.emptyList();try{if(!TasteCatalogue.fresh(this))fresh=TasteCatalogue.refresh(this);}catch(InterruptedException e){Thread.currentThread().interrupt();}
   final Map<String,String> freshText=TitleTranslate.resolve(words(fresh),Ui.TRANSLATE_WAIT);final List<HomeActivity.Item> extra=fresh;Ui.MAIN.post(()->{loading=false;uk.putAll(freshText);if(!valid())return;append(extra);if(choices.isEmpty()){status.setText("Каталог зараз недоступний. Спробуйте ще або пропустіть цей крок.");status.setVisibility(View.VISIBLE);retry.setVisibility(View.VISIBLE);}});
   Set<String> all=words(cached);all.addAll(words(fresh));Map<String,String> got=TitleTranslate.resolve(all);Ui.MAIN.post(()->{if(!valid())return;uk.putAll(got);for(String key:choices.keySet()){HomeActivity.Item i=choices.get(key);titles.get(key).setText(tr(i.title));metas.get(key).setText(Ui.join(i.year,tr(i.genre)));paintChoice(key);}});
  });
 }
 private Set<String> words(List<HomeActivity.Item> items){Set<String> words=new LinkedHashSet<>();for(HomeActivity.Item i:items){words.add(i.title);if(i.genre!=null)words.add(i.genre);}return words;}
 private String tr(String s){if(s==null)return null;String got=uk.get(s);if(got!=null)return got;got=Ua.t(s);return got.equals(s)?TitleTranslate.shown(s):got;}
 private void append(List<HomeActivity.Item> items){insert(items,order.size(),TasteCatalogue.LIMIT);}
 /** Adds new titles at a position in the screen order and lays the rows out again, keeping the focused poster. */
 private List<String> insert(List<HomeActivity.Item> items,int at,int limit){
  List<String> added=new ArrayList<>();
  for(HomeActivity.Item i:items){String key=RecommendationRanker.key(i.link);if(choices.containsKey(key)||choices.size()>=limit)continue;choices.put(key,i);order.add(Math.min(at+added.size(),order.size()),key);added.add(key);
   View card=Ui.card(this,tr(i.title),Ui.join(i.year,tr(i.genre)),i.poster,null,0,()->posterFocus(key),()->toggle(key));FrameLayout frame=(FrameLayout)((LinearLayout)card).getChildAt(0);frame.setId(View.generateViewId());frames.put(key,frame);titles.put(key,(TextView)((LinearLayout)card).getChildAt(1));metas.put(key,(TextView)((LinearLayout)card).getChildAt(2));cards.put(key,card);
   TextView check=Ui.bold(this,"✓",22);check.setGravity(Gravity.CENTER);check.setBackground(Ui.shape(Ui.RED,dp(18),dp(2),Color.WHITE));FrameLayout.LayoutParams checkLp=new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.TOP|Gravity.END);checkLp.setMargins(0,dp(8),dp(8),0);frame.addView(check,checkLp);checks.put(key,check);
   frame.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setCheckable(true);info.setChecked(selected.contains(key));}});
   paintChoice(key);
  }
  if(!added.isEmpty())layout();
  if(!choices.isEmpty())status.setVisibility(View.GONE);wireFocus();return added;
 }
 private void layout(){
  View focused=getCurrentFocus();
  for(LinearLayout row:rows){row.removeAllViews();grid.removeView(row);}rows.clear();
  LinearLayout row=null;for(String key:order){if(row==null||row.getChildCount()==columns){row=new LinearLayout(this);row.setClipChildren(false);row.setClipToPadding(false);grid.addView(row);rows.add(row);}row.addView(cards.get(key));}
  if(focused!=null&&focused.isAttachedToWindow())focused.requestFocus();
 }
 /** A picked title opens up to six similar ones from its page, placed right after it on the screen. */
 private void similar(String key){
  HomeActivity.Item picked=choices.get(key);if(picked==null||!expanded.add(key))return;
  Ui.bg(()->{try{Site.Film f=Site.film(picked.link);List<HomeActivity.Item> found=new ArrayList<>();
    for(Site.Card c:f.related){if(found.size()>=6)break;HomeActivity.Item i=new HomeActivity.Item();i.link=c.link;i.title=c.title;i.poster=c.poster;i.year=c.year();i.genre=c.genre();if(!choices.containsKey(RecommendationRanker.key(i.link)))found.add(i);}
    Map<String,String> text=TitleTranslate.resolve(words(found),Ui.TRANSLATE_WAIT);
    Ui.MAIN.post(()->{if(!valid())return;uk.putAll(text);int at=order.indexOf(key);if(at<0)return;List<String> added=insert(found,at+1,TasteCatalogue.MAX);for(String k:added){View v=cards.get(k);v.setAlpha(0f);v.setScaleX(0.9f);v.setScaleY(0.9f);v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(Ui.EASE).start();}if(!added.isEmpty())Ui.toast(this,"Додано схожі на «"+tr(picked.title)+"»");});
   }catch(Exception e){android.util.Log.w("KIK","Taste similar: "+e);}});
 }
 private void posterFocus(String key){FrameLayout frame=frames.get(key);if(frame==null)return;lastPoster=frame;skip.setNextFocusUpId(frame.getId());save.setNextFocusUpId(frame.getId());frame.requestRectangleOnScreen(new Rect(-dp(5),-dp(5),frame.getWidth()+dp(5),frame.getHeight()+dp(5)),false);}
 private void wireFocus(){
  List<String> keys=new ArrayList<>(order);for(int n=0;n<keys.size();n++){FrameLayout f=frames.get(keys.get(n));f.setNextFocusLeftId(frames.get(keys.get(n%columns==0?n:n-1)).getId());f.setNextFocusRightId(frames.get(keys.get(n%columns==columns-1||n+1==keys.size()?n:n+1)).getId());f.setNextFocusUpId(frames.get(keys.get(n<columns?n:n-columns)).getId());f.setNextFocusDownId(n+columns<keys.size()?frames.get(keys.get(n+columns)).getId():(save.isEnabled()?save:skip).getId());}
  skip.setNextFocusLeftId(skip.getId());skip.setNextFocusRightId(save.isEnabled()?save.getId():skip.getId());save.setNextFocusLeftId(skip.getId());save.setNextFocusRightId(save.getId());skip.setNextFocusDownId(skip.getId());save.setNextFocusDownId(save.getId());View up=lastPoster!=null?lastPoster:keys.isEmpty()?null:frames.get(keys.get(0));if(up!=null){skip.setNextFocusUpId(up.getId());save.setNextFocusUpId(up.getId());}
 }
 private void toggle(String key){if(busy)return;if(!selected.remove(key)){if(selected.size()>=MAX_PICKS){Ui.toast(this,"Можна обрати до "+MAX_PICKS+". Зніміть позначку з іншого, щоб замінити.");return;}selected.add(key);similar(key);}paintChoice(key);paintCounter();}
 private void paintChoice(String key){HomeActivity.Item i=choices.get(key);if(i==null)return;boolean on=selected.contains(key);FrameLayout frame=frames.get(key);frame.setSelected(on);checks.get(key).setVisibility(on?View.VISIBLE:View.GONE);frame.setContentDescription(tr(i.title)+(on?". Обрано":". Не обрано")+". Натисніть OK, щоб змінити");}
 private void paintCounter(){counter.setText("Обрано: "+selected.size()+" з "+MAX_PICKS);save.setEnabled(!busy&&(!setup||!selected.isEmpty()));save.setAlpha(save.isEnabled()?1f:0.45f);skip.setEnabled(!busy);wireFocus();}
 private void complete(boolean keep){
  if(busy||!valid()||keep&&setup&&selected.isEmpty())return;busy=true;paintCounter();
  if(!keep&&!setup){finish();return;}
  List<RatingStore.Rating> changes=new ArrayList<>();if(keep)for(Map.Entry<String,HomeActivity.Item> e:choices.entrySet())if(selected.contains(e.getKey())!=previous.contains(e.getKey())){HomeActivity.Item i=e.getValue();RatingStore.Rating r=new RatingStore.Rating();r.link=i.link;r.title=tr(i.title);r.poster=i.poster;r.genre=i.genre;r.value=selected.contains(e.getKey())?1:0;changes.add(r);}
  Ui.bg(()->{boolean ok=valid()&&(!keep||RatingStore.setMany(this,profile,changes))&&(!setup||ProfileStore.completeTasteSetup(this,profile));
   // New picks must rebuild Home's personal rows instead of showing the saved ranking.
   if(ok&&keep&&!changes.isEmpty())getSharedPreferences("kik_home_personal",0).edit().remove(profile).apply();Ui.MAIN.post(()->{if(!valid())return;if(!ok){busy=false;paintCounter();status.setText("Не вдалося зберегти. Спробуйте ще раз.");status.setVisibility(View.VISIBLE);return;}if(setup)goHome();else finish();});});
 }
 private void goHome(){startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
 @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)complete(false);return true;}return super.dispatchKeyEvent(event);}
 @Override public void onBackPressed(){complete(false);}
 @Override protected void onDestroy(){destroyed=true;super.onDestroy();}
}
