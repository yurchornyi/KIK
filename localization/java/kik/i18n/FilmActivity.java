package kik.i18n;
import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Film / series page: backdrop, Ukrainian title and description, voice choice, seasons and episodes. */
public final class FilmActivity extends Activity {
 static Site.Film current;
 private boolean autoplayed;private String link,title,poster,profile;private Site.Film film;private Site.Voice voice,preferred;private String season;private int voiceCheck;
 private TextView name,original,meta,desc,voiceChip,status,tasteChip,dislikeChip,sourceInfo;private Button watch,later;private ImageView backdrop;
 private LinearLayout body,voices,seasons,episodes;private HorizontalScrollView voicesBox;private View epHeader;
 private final Map<String,String> uk=new HashMap<String,String>();
 private int dp(float n){return Ui.dp(this,n);}

 public static void open(Context c,String link,String title,String poster){open(c,link,title,poster,null);}
 /** {@code year} is the card's own year, e.g. "2011-2019": a title page lists only the premiere date. */
 public static void open(Context c,String link,String title,String poster,String year){
  c.startActivity(new Intent(c,FilmActivity.class).putExtra("link",link).putExtra("title",title).putExtra("poster",poster).putExtra("year",year));
 }
 /** A series' run from its card, or else from the source's search card for the same title; null if unknown. */
 private static String run(Site.Film f,String hint){
  if(hint!=null&&hint.matches("\\d{4}\\s*-.*"))return hint;if(!f.series||f.year!=null&&f.year.contains("-"))return null;
  try{for(Site.Card c:Site.search(f.original!=null&&!f.original.isEmpty()?f.original:f.title,1))if(Progress.norm(c.link).equals(Progress.norm(f.link))){String y=c.year();return y!=null&&y.contains("-")?y:null;}}catch(Exception ignored){}
  return null;
 }

 @Override public void onCreate(Bundle state){
  super.onCreate(state);Ui.screen(this);profile=ProfileStore.current(this);
  link=getIntent().getStringExtra("link");title=getIntent().getStringExtra("title");poster=getIntent().getStringExtra("poster");
  FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Ui.BG);setContentView(root);
  int h=Math.round(getResources().getDisplayMetrics().heightPixels*0.72f);
  backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.FIT_XY);root.addView(backdrop,new FrameLayout.LayoutParams(-1,h));
  ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
  body=new LinearLayout(this);body.setOrientation(1);body.setClipChildren(false);body.setClipToPadding(false);body.setPadding(dp(40),dp(24),dp(40),dp(40));scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
  body.addView(Brand.logo(this,56,26));Ui.space(body,16);
  name=Ui.bold(this,title==null?"":title,34);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);body.addView(name,new LinearLayout.LayoutParams(Ui.contentWidth(this,760,80),-2));
  original=Ui.text(this,"",17,Ui.GREY);body.addView(original);Ui.space(body,8);
  meta=Ui.text(this,"",14,0xffd8d8dc);body.addView(meta);Ui.space(body,12);
  desc=Ui.text(this,"",15,0xffd0d0d4);desc.setLineSpacing(0,1.15f);desc.setMaxLines(3);desc.setEllipsize(TextUtils.TruncateAt.END);body.addView(desc,new LinearLayout.LayoutParams(Ui.contentWidth(this,640,80),-2));
  sourceInfo=Ui.text(this,"",13,Ui.GREY);sourceInfo.setVisibility(View.GONE);body.addView(sourceInfo);
  Ui.space(body,20);
  LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(0);buttons.setClipChildren(false);buttons.setClipToPadding(false);buttons.setGravity(Gravity.CENTER_VERTICAL);buttons.setPadding(dp(8),0,dp(8),0);HorizontalScrollView actionsBox=new HorizontalScrollView(this);actionsBox.setHorizontalScrollBarEnabled(false);actionsBox.setClipChildren(false);actionsBox.setClipToPadding(false);actionsBox.setPadding(0,dp(4),0,dp(4));actionsBox.addView(buttons);LinearLayout.LayoutParams actionsLp=new LinearLayout.LayoutParams(-1,-2);actionsLp.setMargins(-dp(8),0,-dp(8),0);body.addView(actionsBox,actionsLp);
  watch=Ui.pill(this,"▶  Дивитися",true);buttons.addView(watch,new LinearLayout.LayoutParams(-2,dp(48)));watch.setEnabled(false);
  later=Ui.pill(this,"＋  Мій список",false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(48));lp.setMargins(dp(12),0,0,0);buttons.addView(later,lp);
  voiceChip=Ui.chip(this,"Озвучка");LinearLayout.LayoutParams lv=new LinearLayout.LayoutParams(-2,dp(48));lv.setMargins(dp(12),0,0,0);buttons.addView(voiceChip,lv);voiceChip.setVisibility(View.GONE);
  tasteChip=thumb(false);LinearLayout.LayoutParams tasteLp=new LinearLayout.LayoutParams(dp(56),dp(48));tasteLp.setMargins(dp(12),0,0,0);buttons.addView(tasteChip,tasteLp);tasteChip.setOnClickListener(v->rate(1));
  dislikeChip=thumb(true);LinearLayout.LayoutParams dislikeLp=new LinearLayout.LayoutParams(dp(56),dp(48));dislikeLp.setMargins(dp(12),0,0,0);buttons.addView(dislikeChip,dislikeLp);dislikeChip.setOnClickListener(v->rate(-1));paintTaste();
  voicesBox=new HorizontalScrollView(this);voicesBox.setHorizontalScrollBarEnabled(false);voices=new LinearLayout(this);voices.setOrientation(0);voices.setClipChildren(false);voices.setClipToPadding(false);voicesBox.setClipToPadding(false);voices.setPadding(dp(6),dp(14),dp(6),dp(6));voicesBox.addView(voices);voicesBox.setVisibility(View.GONE);body.addView(voicesBox);
  status=Ui.text(this,"Завантаження…",16,Ui.GREY);status.setPadding(0,dp(14),0,0);body.addView(status);
  status.setBackground(Ui.states(Ui.shape(0,dp(4),0,0),Ui.shape(Ui.CARD,dp(4),dp(2),Color.WHITE)));status.setOnClickListener(v->{status.setFocusable(false);status.setText("Завантаження…");load();});status.setFocusable(false);
  epHeader=Ui.bold(this,"Серії",20);epHeader.setPadding(0,dp(20),0,dp(8));epHeader.setVisibility(View.GONE);body.addView(epHeader);
  HorizontalScrollView sb=new HorizontalScrollView(this);sb.setHorizontalScrollBarEnabled(false);seasons=new LinearLayout(this);seasons.setOrientation(0);seasons.setClipChildren(false);seasons.setClipToPadding(false);sb.setClipToPadding(false);seasons.setPadding(dp(4),dp(4),dp(4),dp(4));sb.addView(seasons);body.addView(sb);
  HorizontalScrollView eb=new HorizontalScrollView(this);eb.setHorizontalScrollBarEnabled(false);eb.setClipToPadding(false);episodes=new LinearLayout(this);episodes.setOrientation(0);episodes.setClipChildren(false);episodes.setClipToPadding(false);episodes.setPadding(dp(8),dp(10),dp(8),dp(10));eb.addView(episodes);body.addView(eb);
  watch.setOnClickListener(v->play(null,null));
  later.setOnClickListener(v->{boolean on=Progress.toggleLater(this,link,name.getText().toString(),poster,film==null?null:film.year,film==null||film.genres.isEmpty()?null:tr(film.genres.get(0)));paintLater(on);Ui.toast(this,on?"Додано до «Переглянути пізніше»":"Прибрано зі списку");});
  voiceChip.setOnClickListener(v->{boolean show=voicesBox.getVisibility()!=View.VISIBLE;voicesBox.setVisibility(show?View.VISIBLE:View.GONE);if(show&&voices.getChildCount()>0)voices.getChildAt(0).requestFocus();});
  paintLater(Progress.isLater(this,link));
  if(poster!=null)HomeActivity.Images.crossfade(poster,backdrop);
  later.requestFocus();Ui.enter(name,0);Ui.enter(buttons,70);
  load();
 }
 @Override protected void onResume(){super.onResume();paintTaste();if(film!=null){pickVoice();buildVoices();Progress.Entry last=Progress.get(this,link);if(last!=null&&last.season!=null&&film.seasons.contains(last.season))season=last.season;paintWatch();if(film.series)showEpisodes();warm();}}

 private int loadGeneration;
 private void load(){
  final long started=android.os.SystemClock.elapsedRealtime();
  final int generation=++loadGeneration;
  Ui.bg(()->{
   try{
    final Site.Film f=Site.film(link);
    // Finished series read «2011–2019», running ones «2026»: the page itself names only the premiere.
    String hint=getIntent().getStringExtra("year");if(hint!=null&&hint.matches("\\d{4}\\s*-.*"))f.year=hint;
    List<String> words=new ArrayList<String>();words.add(f.title);words.addAll(f.genres);if(f.country!=null)words.addAll(Arrays.asList(f.country.split(",\\s*")));if(f.duration!=null)words.add(f.duration);
    // Title and description translate side by side, so the page waits once, not twice.
    final java.util.concurrent.FutureTask<String> about=new java.util.concurrent.FutureTask<String>(()->DescTranslate.tr(f.description,Ui.TRANSLATE_WAIT));new Thread(about,"kik-film-text").start();
    final Map<String,String> cached=TitleTranslate.resolve(words,Ui.TRANSLATE_WAIT);final String shown=about.get();
    Ui.MAIN.post(()->{if(isFinishing()||isDestroyed()||generation!=loadGeneration)return;uk.putAll(cached);show(f,shown);android.util.Log.d("KIK.Load","Film page "+(android.os.SystemClock.elapsedRealtime()-started)+"ms");});
    // Playback can start as soon as the page is parsed. Translation only updates text, never voice/focus.
    try{FreeSources.enrich(f);RecommendationMetadata.remember(this,f);}catch(Exception ignored){}
    // The page lists one voice's episodes; show every season the leading voices have.
    if(f.series&&f.sourceUrl==null){final List<Site.Episode> all=Site.allEpisodes(f,4);Ui.MAIN.post(()->{if(isFinishing()||isDestroyed()||generation!=loadGeneration||film!=f||all.size()<=f.episodes.size())return;
     f.episodes=new ArrayList<Site.Episode>(all);f.seasons.clear();for(Site.Episode x:all)if(!f.seasons.contains(x.season))f.seasons.add(x.season);if(season==null||!f.seasons.contains(season))season=f.seasons.isEmpty()?null:f.seasons.get(0);showEpisodes();});}
    Map<String,String> got=TitleTranslate.resolve(words);
    final String d=DescTranslate.tr(f.description);
    String span=run(f,null);if(span!=null)f.year=span;
    Ui.MAIN.post(()->{if(isFinishing()||isDestroyed()||generation!=loadGeneration||film!=f)return;uk.putAll(got);showText(f,d);});
   }catch(Exception e){Ui.MAIN.post(()->{if(isFinishing()||isDestroyed()||generation!=loadGeneration||film!=null)return;status.setText("Не вдалося відкрити сторінку. Натисніть OK, щоб повторити.");status.setFocusable(true);status.requestFocus();});}
  });
 }
 private String tr(String s){if(s==null)return null;String t=uk.get(s);if(t!=null)return t;t=Ua.t(s);return t.equals(s)?TitleTranslate.shown(s):t;}
 private void show(Site.Film f,String description){
  // A UASerials title may open as its HDRezka twin: history, list and voices follow the playable link.
  if(f.sourceUrl==null&&!Progress.norm(f.link).equals(Progress.norm(link)))link=f.link;
  film=f;current=f;status.setVisibility(View.GONE);
  showText(f,description);
  if(f.poster!=null){poster=poster==null?f.poster:poster;HomeActivity.Images.crossfade(f.poster,backdrop);}
  for(View v:new View[]{name,meta,desc}){v.setAlpha(0.3f);v.animate().alpha(1f).setDuration(260).start();}
  pickVoice();buildVoices();
  if(f.series){season=null;Progress.Entry e=Progress.get(this,link);if(e!=null&&e.season!=null&&f.seasons.contains(e.season))season=e.season;if(season==null&&!f.seasons.isEmpty())season=f.seasons.get(0);showEpisodes();}
  watch.setEnabled(voice!=null&&f.sourceUrl==null);paintWatch();if(f.sourceUrl!=null)watch.setText("Перегляд у KIK недоступний");if(voice!=null&&later.hasFocus())watch.requestFocus();
  warm();
  if(voice!=null&&getIntent().getBooleanExtra("play",false)&&!autoplayed){autoplayed=true;play(f.series?getIntent().getStringExtra("season"):null,f.series?getIntent().getStringExtra("episode"):null);}
  if(voice==null&&f.sourceUrl==null){status.setText("Для цього фільму немає доступних доріжок.");status.setVisibility(View.VISIBLE);}
 }
 private String synopsisAsked;
 /** Three lines that say what the title is about: the description if it fits, else the short synopsis (cached, else asked for once). */
 private void fitDescription(final Site.Film f,String description){
  final int width=dp(640)-desc.getPaddingLeft()-desc.getPaddingRight();final float spacing=desc.getLineSpacingMultiplier();
  String full=description==null?"":description.trim(),brief=Synopsis.cached(this,f.link);
  if(full.isEmpty()||Synopsis.fits(full,desc.getPaint(),width,spacing,3)){desc.setText(full);return;}
  if(brief!=null){desc.setText(Synopsis.fit(brief,desc.getPaint(),width,spacing,3));return;}
  desc.setText(Synopsis.fit(full,desc.getPaint(),width,spacing,3));if(f.link.equals(synopsisAsked))return;synopsisAsked=f.link;final Context app=getApplicationContext();
  Ui.bg(()->{final String got=Synopsis.fetch(app,f.link);if(got==null)return;Ui.MAIN.post(()->{if(isFinishing()||isDestroyed()||film!=f)return;desc.setAlpha(0f);desc.setText(Synopsis.fit(got,desc.getPaint(),width,spacing,3));desc.animate().alpha(1f).setDuration(250).start();});});
 }
 private void showText(Site.Film f,String description){
  name.setText(tr(f.title));original.setText(f.original==null?"":f.original);
  List<String> g=new ArrayList<String>();for(String x:f.genres)g.add(tr(x));
  String country=null;if(f.country!=null){StringBuilder b=new StringBuilder();for(String c:f.country.split(",\\s*")){if(b.length()>0)b.append(", ");b.append(tr(c));}country=b.toString();}
  meta.setMaxLines(2);meta.setEllipsize(TextUtils.TruncateAt.END);meta.setText(Ui.join(f.year,country,TextUtils.join(", ",g.subList(0,Math.min(2,g.size()))),tr(f.duration),f.age,f.rating==null?null:(f.ratingName+" "+f.rating)));
  fitDescription(f,description);String info=Ui.join(f.nextEpisode,f.metadataCredit==null?null:"Дані: "+f.metadataCredit,f.sourceUrl==null?null:"Немає доступного потоку для перегляду у KIK.");sourceInfo.setText(info);sourceInfo.setVisibility(info.isEmpty()?View.GONE:View.VISIBLE);
 }
 private void pickVoice(){
  voice=null;Progress.Entry e=Progress.get(this,link);String chosen=Progress.voice(this,link);
  if(chosen!=null)for(Site.Voice v:film.voices)if(v.id.equals(chosen)&&!v.premium)voice=v;
  if(voice==null&&e!=null&&e.voice!=null)for(Site.Voice v:film.voices)if(v.id.equals(e.voice)&&!v.premium)voice=v;
  if(voice==null){int best=Integer.MAX_VALUE;for(Site.Voice v:film.voices){if(v.premium)continue;int r=PlaybackPolicy.order(v.name,v.camrip,v.ads);if(r<best){best=r;voice=v;}}}
  if(voice==null&&film.defaultVoice!=null)for(Site.Voice v:film.voices)if(v.id.equals(film.defaultVoice)&&!v.premium)voice=v;
  preferred=voice;
 }
 /** Voices cover different seasons: show the voice that will really play in the selected season, and say when Ukrainian is missing there. */
 private void seasonVoice(){
  if(film==null||!film.series||preferred==null||season==null||film.sourceUrl!=null)return;String first=null;for(Site.Episode e:film.episodes)if(season.equals(e.season)){first=e.episode;break;}if(first==null)return;
  final String s=season,e=first;final int gen=++voiceCheck;final Site.Film f=film;final Site.Voice want=preferred;
  Ui.bg(()->{Site.Voice v=null;try{v=Site.voiceFor(f,s,e,want.id);}catch(Exception ignored){}final Site.Voice got=v;
   Ui.MAIN.post(()->{if(isFinishing()||isDestroyed()||gen!=voiceCheck||film!=f||got==null)return;voice=got;buildVoices();
});});
 }
 /** Is there a free voice in this language, and (for a series, once known) one that has the selected season? */
 private boolean offered(String l){
  for(Site.Voice x:film.voices)if(!x.premium&&PlaybackPolicy.lang(x.name).equals(l)&&covers(x))return true;
  return false;
 }
 /** Does this studio have the selected season (true until its episode list is known)? */
 private boolean covers(Site.Voice x){
  if(!film.series||season==null)return true;
  List<Site.Episode> eps;synchronized(film.coverage){eps=film.coverage.get(x.id);}if(eps==null)return true;for(Site.Episode e:eps)if(season.equals(e.season))return true;
  return false;
 }
 private void buildVoices(){
  voices.removeAllViews();
  // Always Ukrainian, Russian, English: a language this title (or season) does not have stays grey. Each one plays its best free studio.
  boolean free=false;for(Site.Voice v:film.voices)if(!v.premium)free=true;
  voiceChip.setVisibility(free?View.VISIBLE:View.GONE);
  if(voice!=null)voiceChip.setText(PlaybackPolicy.label(PlaybackPolicy.lang(voice.name)));
  String current=voice==null?null:PlaybackPolicy.lang(voice.name);
  for(final String l:new String[]{"uk","ru","en"}){
   Site.Voice best=null;for(Site.Voice x:film.voices)if(!x.premium&&PlaybackPolicy.lang(x.name).equals(l)){best=x;break;}final Site.Voice v=best;boolean on=v!=null&&offered(l);
   TextView c=Ui.chip(this,PlaybackPolicy.label(l));voices.addView(c,Ui.wrap(this,10));Ui.select(c,l.equals(current));
   c.setEnabled(on);c.setFocusable(on);c.setAlpha(on?1f:0.35f);if(!on)continue;
   c.setOnClickListener(x->{if(!l.equals(current))choose(v,true);else{voicesBox.setVisibility(View.GONE);voiceChip.requestFocus();}});
  }
  // Then every studio of the chosen language, best first, so the viewer can pick one by name.
  List<Site.Voice> studios=new ArrayList<Site.Voice>();for(Site.Voice x:film.voices)if(!x.premium&&PlaybackPolicy.lang(x.name).equals(current))studios.add(x);
  if(studios.size()<2)return;
  View divider=new View(this);divider.setBackgroundColor(0x33ffffff);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(1),dp(28));dl.gravity=Gravity.CENTER_VERTICAL;dl.setMargins(dp(4),0,dp(14),0);voices.addView(divider,dl);
  for(final Site.Voice v:studios){
   boolean on=covers(v);TextView c=Ui.chip(this,Ua.t(v.name));voices.addView(c,Ui.wrap(this,10));Ui.select(c,v==voice);
   c.setEnabled(on);c.setFocusable(on);c.setAlpha(on?1f:0.35f);if(!on)continue;
   c.setOnClickListener(x->choose(v,false));
  }
 }
 /** A language keeps the studio row open on the new language; a studio closes the row. */
 private void choose(final Site.Voice v,boolean language){
  voice=v;preferred=v;Progress.chooseVoice(this,link,v.id);buildVoices();warm();
  if(language){int at=Arrays.asList("uk","ru","en").indexOf(PlaybackPolicy.lang(v.name));if(at>=0)voices.getChildAt(at).requestFocus();}
  else{voicesBox.setVisibility(View.GONE);voiceChip.requestFocus();}
  if(film.series)Ui.bg(()->{try{Site.covered(film,v.id);}catch(Exception ignored){}Ui.MAIN.post(()->{if(!film.seasons.contains(season))season=film.seasons.isEmpty()?null:film.seasons.get(0);showEpisodes();});});
 }
 private void showEpisodes(){
  seasonVoice();
  epHeader.setVisibility(film.episodes.isEmpty()?View.GONE:View.VISIBLE);((TextView)epHeader).setText(film.seasons.size()>1&&season!=null?"Серії · Сезон "+season:"Серії");
  seasons.removeAllViews();View chosen=null,resume=null;
  if(film.seasons.size()>1)for(final String s:film.seasons){TextView c=Ui.chip(this,"Сезон "+s);seasons.addView(c,Ui.wrap(this,10));Ui.select(c,s.equals(season));if(s.equals(season))chosen=c;c.setOnClickListener(v->{season=s;showEpisodes();seasons.post(()->{for(int i=0;i<seasons.getChildCount();i++)if(((TextView)seasons.getChildAt(i)).getText().toString().endsWith(" "+s))seasons.getChildAt(i).requestFocus();});});}
  episodes.removeAllViews();
  Progress.Entry last=Progress.get(this,link);
  for(final Site.Episode e:film.episodes){
   if(season!=null&&!season.equals(e.season))continue;
   FrameLayout card=new FrameLayout(this);card.setFocusable(true);card.setClickable(true);
   card.setBackground(Ui.states(Ui.shape(0xff1f1f23,dp(16),0,0),Ui.shape(0xff2a2a30,dp(16),dp(2),Ui.RED)));
   TextView t=Ui.bold(this,"Серія "+e.episode,17);t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);FrameLayout.LayoutParams titleLp=new FrameLayout.LayoutParams(-1,-2);titleLp.setMargins(dp(14),dp(11),dp(34),0);card.addView(t,titleLp);
   boolean here=last!=null&&e.season.equals(last.season)&&e.episode.equals(last.episode);
   long[] p=Progress.episode(this,link,e.season,e.episode);
   boolean finished=p!=null&&p[1]>0&&p[0]>p[1]*0.93;
   String sub=p==null||p[0]<=0?"Дивитися":finished?"Переглянуто":(here?"Продовжити · ":"Зупинено · ")+Ui.time(p[0]);
   if(finished)sub="";
   TextView s=Ui.text(this,sub,12,here&&!finished?0xffefb8bb:Ui.GREY);s.setSingleLine(true);s.setEllipsize(TextUtils.TruncateAt.END);FrameLayout.LayoutParams statusLp=new FrameLayout.LayoutParams(-1,-2);statusLp.setMargins(dp(14),dp(39),dp(12),0);card.addView(s,statusLp);
   TextView cue=Ui.text(this,finished?"✓":"▶",14,Color.WHITE);cue.setGravity(Gravity.CENTER);FrameLayout.LayoutParams cueLp=new FrameLayout.LayoutParams(dp(20),dp(24),Gravity.TOP|Gravity.END);cueLp.setMargins(0,dp(10),dp(10),0);card.addView(cue,cueLp);
   if(finished)card.setAlpha(0.5f);
   if(p!=null&&p[1]>0&&!finished){FrameLayout progress=new FrameLayout(this);progress.setBackground(Ui.shape(0xff414147,dp(2),0,0));FrameLayout.LayoutParams progressLp=new FrameLayout.LayoutParams(-1,dp(3),Gravity.BOTTOM);progressLp.setMargins(dp(14),0,dp(14),dp(8));card.addView(progress,progressLp);View bar=new View(this);bar.setBackground(Ui.shape(Ui.RED,dp(2),0,0));progress.addView(bar,new FrameLayout.LayoutParams((int)(dp(136)*Math.max(0,Math.min(1f,(float)p[0]/p[1]))),-1));}
   card.setContentDescription("Серія "+e.episode+". "+(finished?"Переглянуто":sub));
   final float rest=finished?0.5f:1f;
   card.setOnFocusChangeListener((v,f)->{v.animate().alpha(f?1f:rest).scaleX(f?1.06f:1f).scaleY(f?1.06f:1f).setDuration(160).setInterpolator(Ui.EASE).start();if(f)v.requestRectangleOnScreen(new Rect(-dp(8),-dp(8),v.getWidth()+dp(8),v.getHeight()+dp(8)),false);});
   card.setOnClickListener(v->play(e.season,e.episode));if(here)resume=card;
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(164),dp(72));lp.setMargins(0,0,dp(12),0);episodes.addView(card,lp);
  }
  reveal(chosen);reveal(resume);
  paintWatch();
 }
 /** Long rows (32 seasons, 20+ episodes): scroll so the selected season and the episode to continue are on screen. */
 private void reveal(final View v){
  if(v==null||!(v.getParent()!=null&&v.getParent().getParent() instanceof HorizontalScrollView))return;final HorizontalScrollView box=(HorizontalScrollView)v.getParent().getParent();
  box.post(()->box.scrollTo(Math.max(0,v.getLeft()-dp(8)),0));
 }
 private void paintWatch(){
  if(film==null)return;if(film.sourceUrl!=null){watch.setText("Перегляд у KIK недоступний");return;}Progress.Entry e=Progress.get(this,link);
  if(e!=null&&!e.finished())watch.setText(film.series&&e.season!=null?"▶  Продовжити S"+e.season+"E"+e.episode:"▶  Продовжити з "+Ui.time(e.pos));
  else if(film.series&&e!=null&&e.finished())watch.setText("▶  Наступна серія");
  else watch.setText("▶  Дивитися");
 }
 private void paintLater(boolean on){later.setText(on?"✓  У списку":"＋  Мій список");}
 private TextView thumb(boolean down){TextView button=Ui.chip(this,"");button.setPadding(dp(16),0,dp(16),0);ThumbIcon icon=new ThumbIcon(dp(24),down);icon.setBounds(0,0,dp(24),dp(24));button.setCompoundDrawables(icon,null,null,null);button.setContentDescription(down?"Не подобається":"Подобається");return button;}
 private void paintTaste(){if(tasteChip==null||dislikeChip==null)return;int value=RatingStore.get(this,profile,link);Ui.select(tasteChip,value>0);Ui.select(dislikeChip,value<0);}
 private void rate(int desired){
  int value=RatingStore.get(this,profile,link)==desired?0:desired;
  boolean saved=RatingStore.set(this,profile,link,name.getText().toString(),poster,film==null?null:TextUtils.join(", ",film.genres),value);paintTaste();Ui.toast(this,saved?RatingStore.said(value):"Не вдалося зберегти оцінку");
 }
 /** Monochrome, state-aware icons match the shared focused and selected button palette. */
 static final class ThumbIcon extends Drawable {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path hand=new Path();private final int size;private final boolean down;
  ThumbIcon(int size,Path shape){this.size=size;this.down=false;paint.setColor(Color.WHITE);hand.set(shape);}
  /** A filled bin with three cut-out slots, on the same 24-unit grid as the thumb. */
  static Path trash(){Path p=new Path();p.setFillType(Path.FillType.EVEN_ODD);p.addRect(9,2.5f,15,4.5f,Path.Direction.CW);p.addRect(3.5f,4.5f,20.5f,7,Path.Direction.CW);p.moveTo(5.5f,8);p.lineTo(18.5f,8);p.lineTo(17.4f,21.5f);p.lineTo(6.6f,21.5f);p.close();for(float x:new float[]{8.6f,11.4f,14.2f})p.addRect(x,10.5f,x+1.2f,19,Path.Direction.CW);return p;}
  ThumbIcon(int size,boolean down){this.size=size;this.down=down;paint.setColor(Color.WHITE);hand.addRect(2,10,6,21,Path.Direction.CW);hand.moveTo(8,10);hand.lineTo(13,4);hand.quadTo(14,2,15,3);hand.quadTo(16,4,15.5f,6);hand.lineTo(15,9);hand.lineTo(20,9);hand.quadTo(22,9,21.5f,11.5f);hand.lineTo(19.2f,19.5f);hand.quadTo(19,21,17.5f,21);hand.lineTo(9,21);hand.quadTo(8,21,8,20);hand.close();}
  @Override public void draw(Canvas canvas){Rect r=getBounds();canvas.save();canvas.translate(r.left,r.top);canvas.scale(r.width()/24f,r.height()/24f);if(down)canvas.rotate(180,12,12);canvas.drawPath(hand,paint);canvas.restore();}
  @Override public boolean isStateful(){return true;}
  @Override protected boolean onStateChange(int[] states){int color=Ui.buttonText(false).getColorForState(states,Color.WHITE);if(paint.getColor()==color)return false;paint.setColor(color);invalidateSelf();return true;}
  @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
  @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }

 /** Starts the KIK player; with no episode given, resumes the last one or starts from the first. */
 private void play(String s,String e){
  if(film==null||voice==null)return;
  String[] t=target(s,e);KikPlayerActivity.open(this,film,voice,t[0],t[1],name.getText().toString(),poster);
 }
 /** Fetches the stream OK would play now, while the viewer reads the page. */
 private void warm(){if(film!=null&&voice!=null&&film.sourceUrl==null&&!isFinishing()){String[] t=target(null,null);KikPlayerActivity.prefetch(this,film,voice,t[0],t[1]);}}
 private String[] target(String s,String e){
  if(film.series&&s==null){
   Progress.Entry last=Progress.get(this,link);
   if(last!=null&&last.season!=null){s=last.season;e=last.episode;
    if(last.finished()){Site.Episode n=KikPlayerActivity.next(film,s,e);if(n!=null){s=n.season;e=n.episode;}}}
   else if(!film.episodes.isEmpty()){Site.Episode first=null;for(Site.Episode x:film.episodes)if(season==null||season.equals(x.season)){first=x;break;}if(first==null)first=film.episodes.get(0);s=first.season;e=first.episode;}
  }
  return new String[]{s,e};
 }
 @Override public void onBackPressed(){if(voicesBox.getVisibility()==View.VISIBLE){voicesBox.setVisibility(View.GONE);voiceChip.requestFocus();return;}finish();}
}
