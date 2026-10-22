package kik.i18n;
import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** KIK's own player: streams straight from the site, Netflix-style remote controls, resume and history. */
public final class KikPlayerActivity extends Activity {
 private static Site.Film film;private static Site.Voice voice;
 private static final float[] SPEEDS={0.75f,1f,1.25f,1.5f,2f};
 private String season,episode,title,poster,profileId;private Site.Stream stream;private String quality;private float speed=1f;
 private VideoView video;private MediaPlayer mp;private boolean prepared,seeking,failed,destroyed,active=true,playWhenReady=true;private int generation,subGeneration;private long resumeAt;
 private FrameLayout root;private LinearLayout top,bottom,buttons,options;private TextView name,sub,left,right,flash,loading,subtitle;private View played,track,knob;
 private final List<TextView> controls=new ArrayList<TextView>();private int focus=1;private boolean visible,progressFocus;
 private final WatchTracker watchTracker=new WatchTracker();private long watchedPending;
 private String menu;private int optFocus,optSelected;private List<String> optLabels=new ArrayList<String>();private List<Runnable> optActions=new ArrayList<Runnable>();
 private List<Object[]> cues=new ArrayList<Object[]>();private int subIndex=-1;
 private final Handler h=new Handler(Looper.getMainLooper());
 private final Runnable hide=()->{if(prepared&&!seeking&&playWhenReady&&menu==null)show(false);},tick=new Runnable(){public void run(){update();h.postDelayed(this,500);}},saver=new Runnable(){public void run(){if(active)save();h.postDelayed(this,5000);}};
 private int dp(float n){return Ui.dp(this,n);}

 public static void open(Context c,Site.Film f,Site.Voice v,String season,String episode,String title,String poster){
  film=f;voice=v;c.startActivity(new Intent(c,KikPlayerActivity.class).putExtra("season",season).putExtra("episode",episode).putExtra("title",title).putExtra("poster",poster));
 }
 static Site.Episode next(Site.Film f,String s,String e){boolean seen=false;for(Site.Episode x:f.episodes){if(seen)return x;if(x.season.equals(s)&&x.episode.equals(e))seen=true;}return null;}

 @Override public void onCreate(Bundle state){
  super.onCreate(state);profileId=ProfileStore.current(this);Ui.screen(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  if(film==null||voice==null){finish();return;}
  season=getIntent().getStringExtra("season");episode=getIntent().getStringExtra("episode");title=getIntent().getStringExtra("title");poster=getIntent().getStringExtra("poster");
  root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
  video=new VideoView(this);root.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
  subtitle=Ui.text(this,"",26,Color.WHITE);subtitle.setGravity(Gravity.CENTER);subtitle.setShadowLayer(8,0,2,Color.BLACK);subtitle.setPadding(dp(80),0,dp(80),0);
  FrameLayout.LayoutParams sl=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sl.bottomMargin=dp(48);root.addView(subtitle,sl);
  loading=Ui.text(this,"Підготовка відео…",18,Color.WHITE);loading.setGravity(Gravity.CENTER);root.addView(loading,new FrameLayout.LayoutParams(dp(680),-2,Gravity.CENTER));
  buildControls();
  video.setOnPreparedListener(p->{mp=p;prepared=true;failed=false;paintSub();seeking=resumeAt>1000;loading.setText("Відновлення позиції…");loading.setVisibility(seeking?View.VISIBLE:View.GONE);
   p.setOnSeekCompleteListener(x->{if(destroyed||x!=mp)return;seeking=false;loading.setVisibility(View.GONE);if(!playWhenReady||!active)video.pause();mp.setVolume(1f,1f);update();Log.d("KIK.Player","Seek complete position="+video.getCurrentPosition()+" requested="+resumeAt);});
   if(!playWhenReady||!active)p.setVolume(0f,0f);applySpeed();if(seeking){if(active)video.start();if(Build.VERSION.SDK_INT>=26)p.seekTo(resumeAt,MediaPlayer.SEEK_CLOSEST);else video.seekTo((int)resumeAt);}else{if(playWhenReady&&active)video.start();else video.pause();p.setVolume(1f,1f);}show(true);Log.d("KIK.Player","Prepared quality="+quality+" playing="+video.isPlaying()+" speed="+speed);});
  video.setOnCompletionListener(p->onEnded());
  video.setOnErrorListener((p,what,extra)->{Log.w("KIK","Player error "+what+"/"+extra);failure("Не вдалося відтворити відео. OK — повторити.\nМожна вибрати іншу якість або озвучку.");return true;});
  h.post(tick);h.postDelayed(saver,5000);
  start(true);
 }
 @Override protected void onResume(){super.onResume();active=true;CloudSync.playing(profileId,true);if(prepared&&seeking){mp.setVolume(0f,0f);video.start();}else if(prepared&&!video.isPlaying()){playWhenReady=false;show(true);}}
 @Override protected void onPause(){super.onPause();active=false;save();CloudSync.playing(profileId,false);CloudSync.changed(this);if(prepared){playWhenReady=false;video.pause();}}
 @Override protected void onDestroy(){destroyed=true;CloudSync.playing(profileId,false);generation++;h.removeCallbacksAndMessages(null);video.stopPlayback();super.onDestroy();}

 // ---------- playback ----------
 /** Loads the stream for the current voice/episode; keepPosition continues from the same moment. */
 private void start(final boolean fromHistory){
  start(fromHistory,false);
 }
 private void start(final boolean fromHistory,final boolean refreshEpisodes){
  final int gen=++generation;final Site.Film selectedFilm=film;final Site.Voice selected=voice;final String ss=season,ee=episode;
  final long keep=prepared&&!seeking?video.getCurrentPosition():resumeAt;if(prepared)video.pause();if(fromHistory)playWhenReady=true;prepared=false;mp=null;failed=false;loading.setText("Підготовка відео…");loading.setVisibility(View.VISIBLE);
  name.setText(title);paintSub();
  Ui.bg(()->{
   try{
    Site.Film updated=null;if(refreshEpisodes&&selectedFilm.series){Site.Film copy=new Site.Film();copy.id=selectedFilm.id;copy.favs=selectedFilm.favs;copy.origin=selectedFilm.origin;try{Site.episodesFor(copy,selected.id);if(!copy.episodes.isEmpty())updated=copy;}catch(Exception e){Log.w("KIK","Voice episodes",e);}}
    final Site.Film episodeData=updated;
    Site.Voice use=selected;
    if(selectedFilm.series&&ss!=null)try{Site.Voice has=Site.voiceFor(selectedFilm,ss,ee,refreshEpisodes?selected.id:Progress.voice(this,selectedFilm.link));if(has!=null)use=has;}catch(Exception e){Log.w("KIK","Voice coverage",e);}
    final Site.Voice playing=use;
    final Site.Stream s=Site.stream(selectedFilm,playing,ss,ee);
    final List<Object[]> parsed=new ArrayList<Object[]>();
    h.post(()->{
     if(destroyed||gen!=generation)return;
     if(episodeData!=null){selectedFilm.seasons=new ArrayList<String>(episodeData.seasons);selectedFilm.episodes=new ArrayList<Site.Episode>(episodeData.episodes);}
     if(playing!=selected){voice=playing;paintSub();boolean ukExists=false;for(Site.Voice v:selectedFilm.voices)if(!v.premium&&PlaybackPolicy.language(v.name)==0)ukExists=true;
      if(PlaybackPolicy.language(playing.name)!=0&&ukExists)Ui.toast(this,"Української озвучки для цієї серії немає. Увімкнено «"+Ua.t(playing.name)+"».");}
     stream=s;if(s.qualities.isEmpty()){failure("Відео недоступне для цієї озвучки.\nВиберіть іншу озвучку або натисніть OK для повтору.");return;}
     quality=!fromHistory&&quality!=null&&s.qualities.containsKey(quality)?quality:pickQuality(s);
     if(fromHistory){long[] p=Progress.episode(this,film.link,season,episode);resumeAt=p!=null&&!(p[1]>0&&p[0]>p[1]*0.93)?p[0]:0;}else resumeAt=keep;
     subGeneration++;cues.clear();subIndex=-1;subtitle.setText("");
     play();
    });
   }catch(final Exception e){Log.w("KIK","Stream",e);h.post(()->{if(destroyed||gen!=generation)return;failure("Не вдалося підготувати відео.\nПеревірте інтернет. OK — повторити.");});}
  });
 }
 private void failure(String message){prepared=false;failed=true;focus=1;progressFocus=false;menu=null;options.setVisibility(View.GONE);loading.setText(message);loading.setVisibility(View.VISIBLE);show(true);}
 private void play(){
  seeking=false;String url=stream.qualities.get(quality);Map<String,String> headers=new HashMap<String,String>();headers.put("User-Agent",Site.UA);
  video.setVideoURI(Uri.parse(url),headers);
 }
 private String pickQuality(Site.Stream s){
  String want=getSharedPreferences("settings",0).getString("default_quality","1080p");int target=num(want);String best=null;int bestN=-1;
  for(String q:s.qualities.keySet()){if(q.equals(want))return q;int n=num(q);if(n<=target+1&&n>bestN){best=q;bestN=n;}}
  if(best==null)for(String q:s.qualities.keySet())best=q;return best;
 }
 private static int num(String q){Matcher m=Pattern.compile("(\\d+)").matcher(q==null?"":q);int n=m.find()?Integer.parseInt(m.group(1)):0;if(q!=null&&q.toLowerCase(Locale.ROOT).contains("ultra"))n+=1;if(q!=null&&(q.contains("2K")||q.contains("2k")))n=1440;if(q!=null&&(q.contains("4K")||q.contains("4k")))n=2160;return n;}
 private void applySpeed(){if(mp==null)return;try{mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(speed));if(!playWhenReady||!active)video.pause();Log.d("KIK.Player","Speed="+mp.getPlaybackParams().getSpeed()+" playing="+video.isPlaying());}catch(Exception e){Log.w("KIK","Speed",e);}}
 private void seek(long delta){if(!prepared)return;long d=video.getDuration(),t=Math.max(0,(seeking?resumeAt:video.getCurrentPosition())+delta);if(d>0)t=Math.min(Math.max(0,d-1500),t);resumeAt=t;seeking=true;loading.setText("Перемотування…");loading.setVisibility(View.VISIBLE);Log.d("KIK.Player","Seek requested="+t);if(mp!=null&&!playWhenReady)mp.setVolume(0f,0f);if(active)video.start();if(mp!=null&&Build.VERSION.SDK_INT>=26)mp.seekTo(t,MediaPlayer.SEEK_CLOSEST);else video.seekTo((int)t);update();}
 private void toggle(){if(!prepared)return;playWhenReady=!playWhenReady;if(!seeking){if(playWhenReady)video.start();else video.pause();}update();show(true);Log.d("KIK.Player","Playing="+video.isPlaying()+" position="+video.getCurrentPosition());}
 private void onEnded(){
  save();Site.Episode n=film.series?next(film,season,episode):null;
  boolean auto=getSharedPreferences("settings",0).getBoolean("auto_play_next_episode",true);
  if(n!=null&&auto){season=n.season;episode=n.episode;start(true);}else finish();
 }
 private void save(){
  if(!prepared||film==null||!profileId.equals(ProfileStore.current(this)))return;
  Progress.Entry e=new Progress.Entry();e.link=film.link;e.title=title;e.poster=poster;e.season=film.series?season:null;e.episode=film.series?episode:null;
  e.voice=voice.id;e.voiceName=voice.name;e.pos=seeking?resumeAt:video.getCurrentPosition();e.dur=video.getDuration();
  e.observed=true;e.watchedMs=watchedPending;if(e.dur>0){Progress.save(this,e);watchedPending=0;}
 }

 // ---------- controls ----------
 private void buildControls(){
  top=new LinearLayout(this);top.setOrientation(1);top.setPadding(dp(48),dp(28),dp(48),dp(60));
  top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xcc000000,0x00000000}));
  name=Ui.bold(this,"",30);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);sub=Ui.text(this,"",18,0xffbdbdbd);top.addView(name);top.addView(sub);
  root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
  bottom=new LinearLayout(this);bottom.setOrientation(1);bottom.setClipChildren(false);bottom.setClipToPadding(false);bottom.setPadding(dp(48),dp(90),dp(48),dp(28));
  bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0xf2000000,0xb3000000,0x00000000}));
  LinearLayout bar=new LinearLayout(this);bar.setOrientation(0);bar.setGravity(Gravity.CENTER_VERTICAL);
  left=Ui.text(this,"0:00",18,Color.WHITE);right=Ui.text(this,"",18,0xffbdbdbd);right.setGravity(Gravity.END);
  FrameLayout tr=new FrameLayout(this);
  track=new View(this);track.setBackground(Ui.shape(0x55ffffff,dp(3),0,0));tr.addView(track,new FrameLayout.LayoutParams(-1,dp(6),Gravity.CENTER_VERTICAL));
  played=new View(this);played.setBackground(Ui.shape(Ui.RED,dp(3),0,0));tr.addView(played,new FrameLayout.LayoutParams(0,dp(6),Gravity.CENTER_VERTICAL|Gravity.START));
  knob=new View(this);GradientDrawable kd=new GradientDrawable();kd.setShape(GradientDrawable.OVAL);kd.setColor(Ui.RED);kd.setStroke(dp(2),Color.WHITE);knob.setBackground(kd);tr.addView(knob,new FrameLayout.LayoutParams(dp(16),dp(16),Gravity.CENTER_VERTICAL|Gravity.START));
  bar.addView(left,new LinearLayout.LayoutParams(dp(96),-2));bar.addView(tr,new LinearLayout.LayoutParams(0,dp(30),1));bar.addView(right,new LinearLayout.LayoutParams(dp(96),-2));
  bottom.addView(bar);
  buttons=new LinearLayout(this);buttons.setOrientation(0);buttons.setClipChildren(false);buttons.setClipToPadding(false);buttons.setGravity(Gravity.CENTER_VERTICAL);buttons.setPadding(0,dp(14),0,0);bottom.addView(buttons);
  addControl("⟲10",()->seek(-10000));addControl("❚❚",()->toggle());addControl("10⟳",()->seek(10000));
  View gap=new View(this);buttons.addView(gap,new LinearLayout.LayoutParams(0,1,1));
  if(film.series)addControl("Наступна серія",()->{if(!prepared)return;Site.Episode n=next(film,season,episode);if(n!=null){save();season=n.season;episode=n.episode;start(true);}else Ui.toast(this,"Це остання серія");});
  if(film.voices.size()>1)addControl("Озвучка",()->openVoices());
  addControl("Якість",()->openQualities());
  addControl("Швидкість",()->openSpeeds());
  addControl("Субтитри",()->openSubtitles());
  options=new LinearLayout(this);options.setOrientation(0);options.setGravity(Gravity.END);options.setPadding(0,dp(12),0,0);options.setVisibility(View.GONE);
  HorizontalScrollView os=new HorizontalScrollView(this);os.setHorizontalScrollBarEnabled(false);os.addView(options);bottom.addView(os);
  root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
  flash=Ui.bold(this,"",44);flash.setGravity(Gravity.CENTER);flash.setPadding(dp(26),dp(14),dp(26),dp(14));flash.setBackground(Ui.shape(0x99000000,dp(40),0,0));flash.setAlpha(0);
  root.addView(flash,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
  top.setAlpha(0);bottom.setAlpha(0);top.setVisibility(View.GONE);bottom.setVisibility(View.GONE);
 }
 private void addControl(String label,Runnable r){
  boolean icon=label.length()<=4;TextView t=Ui.bold(this,label,icon?20:16);t.setSingleLine(true);t.setGravity(Gravity.CENTER);t.setPadding(icon?0:dp(12),0,icon?0:dp(12),0);t.setTag(r);t.setOnClickListener(v->{r.run();show(true);});
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(icon?dp(54):-2,dp(48));lp.setMargins(0,0,dp(8),0);buttons.addView(t,lp);controls.add(t);
 }
 private void paintSub(){
  String s=film.series&&season!=null?"Сезон "+season+" · Серія "+episode:"";
  sub.setText(Ui.join(s,Ua.t(voice.name),quality));
 }
 private void openMenu(String kind,List<String> labels,List<Runnable> actions,int selected){
  if(labels.isEmpty()){Ui.toast(this,"Немає доступних варіантів");return;}
  menu=kind;optLabels=labels;optActions=actions;optSelected=optFocus=Math.min(labels.size()-1,Math.max(0,selected));options.removeAllViews();
  for(int i=0;i<labels.size();i++){final int at=i;TextView t=Ui.bold(this,labels.get(i),16);t.setSingleLine(true);t.setGravity(Gravity.CENTER);t.setPadding(dp(18),0,dp(18),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(44));lp.setMargins(dp(8),dp(4),dp(8),dp(4));options.addView(t,lp);t.setOnClickListener(v->{Runnable r=optActions.get(at);closeMenu();r.run();show(true);});}
  options.setVisibility(View.VISIBLE);show(true);options.post(this::placeSubtitles);
 }
 private void closeMenu(){menu=null;options.setVisibility(View.GONE);paint();}
 private void openVoices(){
  List<String> l=new ArrayList<String>();List<Runnable> a=new ArrayList<Runnable>();int sel=0;
  for(final Site.Voice v:film.voices){if(v.premium)continue;if(v==voice)sel=l.size();l.add(Ua.t(v.name));a.add(()->{if(v==voice)return;save();voice=v;Progress.chooseVoice(this,film.link,v.id);start(false,true);});}
  openMenu("voice",l,a,sel);
 }
 private void openQualities(){
  if(stream==null||!prepared)return;List<String> l=new ArrayList<String>();List<Runnable> a=new ArrayList<Runnable>();int sel=0;
  for(final String q:stream.qualities.keySet()){if(q.equals(quality))sel=l.size();l.add(q);a.add(()->{if(q.equals(quality))return;resumeAt=prepared&&!seeking?video.getCurrentPosition():resumeAt;prepared=false;mp=null;quality=q;paintSub();loading.setText("Підготовка відео…");loading.setVisibility(View.VISIBLE);play();});}
  openMenu("quality",l,a,sel);
 }
 private void openSpeeds(){List<String> l=new ArrayList<String>();List<Runnable> a=new ArrayList<Runnable>();int sel=1;for(int i=0;i<SPEEDS.length;i++){final float s=SPEEDS[i];if(s==speed)sel=i;l.add((s==(int)s?String.valueOf((int)s):String.valueOf(s))+"x");a.add(()->{speed=s;applySpeed();});}openMenu("speed",l,a,sel);}
 private void openSubtitles(){
  List<String> l=new ArrayList<String>();List<Runnable> a=new ArrayList<Runnable>();l.add("Вимкнено");a.add(()->{subGeneration++;subIndex=-1;cues.clear();subtitle.setText("");});
  if(stream!=null)for(int i=0;i<stream.subtitleList.size();i++){final int index=i;final String[] s=stream.subtitleList.get(i);l.add(Ua.t(s[0]));a.add(()->loadSubtitles(s[1],index));}
  if(l.size()==1){Ui.toast(this,"Для цього відео немає субтитрів");return;}
  openMenu("subs",l,a,subIndex+1);
 }
 private void loadSubtitles(final String url,final int index){
  final int gen=++subGeneration;Ui.bg(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent",Site.UA);String body=Site.read(c.getInputStream());final List<Object[]> parsed=vtt(body);h.post(()->{if(destroyed||gen!=subGeneration)return;cues=parsed;subIndex=index;update();});}
   catch(Exception e){h.post(()->{if(!destroyed&&gen==subGeneration)Ui.toast(this,"Не вдалося завантажити субтитри");});}finally{if(c!=null)c.disconnect();}});
 }
 private static List<Object[]> vtt(String body){
  List<Object[]> out=new ArrayList<Object[]>();
  Matcher m=Pattern.compile("(\\d{1,2}:)?(\\d{2}):(\\d{2})[.,](\\d{3})\\s*-->\\s*(\\d{1,2}:)?(\\d{2}):(\\d{2})[.,](\\d{3})[^\\n]*\\n(.*?)(?:\\n\\s*\\n|$)",Pattern.DOTALL).matcher(body.replace("\r",""));
  while(m.find()){long a=ms(m.group(1),m.group(2),m.group(3),m.group(4)),b=ms(m.group(5),m.group(6),m.group(7),m.group(8));out.add(new Object[]{a,b,m.group(9).replaceAll("<[^>]+>","").trim()});}
  return out;
 }
 private static long ms(String hh,String mm,String ss,String mmm){long h=hh==null?0:Long.parseLong(hh.replace(":",""));return ((h*60+Long.parseLong(mm))*60+Long.parseLong(ss))*1000+Long.parseLong(mmm);}

 private void show(boolean on){
  h.removeCallbacks(hide);
  if(on){h.postDelayed(hide,menu!=null?8000:4500);if(!visible){visible=true;fade(top,true);fade(bottom,true);}paint();bottom.post(this::placeSubtitles);return;}
  if(!visible)return;visible=false;closeMenu();progressFocus=false;fade(top,false);fade(bottom,false);placeSubtitles();
 }
 private void placeSubtitles(){FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)subtitle.getLayoutParams();lp.bottomMargin=visible?Math.max(dp(140),bottom.getHeight()-dp(40)):dp(48);subtitle.setLayoutParams(lp);}
 private void fade(final View v,boolean on){
  v.animate().cancel();
  if(on){v.setVisibility(View.VISIBLE);v.setTranslationY(v==top?-dp(12):dp(12));v.animate().alpha(1).translationY(0).setDuration(300).setInterpolator(Ui.EASE).start();}
  else v.animate().alpha(0).translationY(v==top?-dp(12):dp(12)).setDuration(320).withEndAction(()->v.setVisibility(View.GONE)).start();
 }
 private void pulse(String s){flash.setText(s);flash.animate().cancel();flash.setScaleX(0.9f);flash.setScaleY(0.9f);flash.animate().alpha(1).scaleX(1).scaleY(1).setDuration(200).setInterpolator(Ui.EASE).withEndAction(()->flash.animate().alpha(0).setStartDelay(500).setDuration(300).start()).start();}
 private void update(){
  if(video!=null)watchedPending+=watchTracker.sample(SystemClock.elapsedRealtime(),prepared?video.getCurrentPosition():0,prepared&&!seeking&&active&&playWhenReady&&video.isPlaying());
  if(prepared){
   long p=seeking?resumeAt:video.getCurrentPosition(),d=video.getDuration();left.setText(Ui.time(p));right.setText(d>0?"-"+Ui.time(d-p):"");
   int w=((View)played.getParent()).getWidth();ViewGroup.LayoutParams lp=played.getLayoutParams();lp.width=d>0&&w>0?(int)(w*Math.min(1f,(float)p/d)):0;played.setLayoutParams(lp);knob.setTranslationX(Math.max(0,lp.width-dp(8)));
   controls.get(1).setText(playWhenReady?"❚❚":"▶");
   if(!cues.isEmpty()){String txt="";for(Object[] c:cues)if(p>=(Long)c[0]&&p<=(Long)c[1]){txt=(String)c[2];break;}if(!txt.equals(subtitle.getText().toString()))subtitle.setText(txt);}
  }
 }
 private void paint(){
  for(int i=0;i<controls.size();i++){boolean f=menu==null&&!progressFocus&&i==focus;TextView t=controls.get(i);
   Ui.paintControl(t,f);}
  for(int i=0;i<options.getChildCount();i++){TextView t=(TextView)options.getChildAt(i);boolean f=i==optFocus;Ui.paintControl(t,f);if(!f&&i==optSelected)t.setBackground(Ui.shape(Ui.RED,dp(24),0,0));if(f){final View target=t;options.post(()->((HorizontalScrollView)options.getParent()).smoothScrollTo(Math.max(0,target.getLeft()-dp(120)),0));}}
  knob.animate().scaleX(progressFocus?1.6f:1f).scaleY(progressFocus?1.6f:1f).setDuration(180).start();
 }

 @Override public boolean dispatchKeyEvent(KeyEvent e){
  int k=e.getKeyCode();
  if(k==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)onBackPressed();return true;}
  if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
  switch(k){
   case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:case KeyEvent.KEYCODE_SPACE:toggle();if(!visible)pulse(video.isPlaying()?"▶":"❚❚");return true;
   case KeyEvent.KEYCODE_MEDIA_PLAY:playWhenReady=true;if(prepared)video.start();show(true);return true;
   case KeyEvent.KEYCODE_MEDIA_PAUSE:playWhenReady=false;if(prepared){if(seeking&&mp!=null)mp.setVolume(0f,0f);else video.pause();}update();show(true);return true;
   case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:seek(10000);pulse("10 »");return true;
   case KeyEvent.KEYCODE_MEDIA_REWIND:seek(-10000);pulse("« 10");return true;
  }
  boolean center=k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER;
  if(failed&&center&&menu==null&&focus==1){start(false);return true;}
  if(!visible){
   if(k==KeyEvent.KEYCODE_DPAD_LEFT){seek(-10000);pulse("« 10");return true;}
   if(k==KeyEvent.KEYCODE_DPAD_RIGHT){seek(10000);pulse("10 »");return true;}
   if(center){toggle();pulse(video.isPlaying()?"▶":"❚❚");show(true);return true;}
   focus=1;progressFocus=false;show(true);return true;
  }
  show(true);
  if(menu!=null){
   if(k==KeyEvent.KEYCODE_DPAD_LEFT)optFocus=Math.max(0,optFocus-1);
   else if(k==KeyEvent.KEYCODE_DPAD_RIGHT)optFocus=Math.min(optLabels.size()-1,optFocus+1);
   else if(center){Runnable r=optActions.get(optFocus);closeMenu();r.run();return true;}
   else if(k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN){closeMenu();return true;}
   paint();return true;
  }
  if(progressFocus){
   if(k==KeyEvent.KEYCODE_DPAD_LEFT)seek(-10000);else if(k==KeyEvent.KEYCODE_DPAD_RIGHT)seek(10000);
   else if(k==KeyEvent.KEYCODE_DPAD_DOWN)progressFocus=false;else if(center)toggle();
   paint();return true;
  }
  if(k==KeyEvent.KEYCODE_DPAD_LEFT)focus=Math.max(0,focus-1);
  else if(k==KeyEvent.KEYCODE_DPAD_RIGHT)focus=Math.min(controls.size()-1,focus+1);
  else if(k==KeyEvent.KEYCODE_DPAD_UP)progressFocus=true;
  else if(center)((Runnable)controls.get(focus).getTag()).run();
  paint();return true;
 }
 @Override public void onBackPressed(){if(menu!=null){closeMenu();return;}if(visible){show(false);return;}save();finish();}
}
