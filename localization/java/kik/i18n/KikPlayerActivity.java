package kik.i18n;
import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.os.*;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/**
 * KIK's own player: streams straight from the site through ExoPlayer, Netflix-style remote controls, resume and
 * history. A large buffer rides out a slow CDN; dropped connections are retried from the same byte, and only then
 * does recovery move to another address, a lower quality, fresh links or another voice in the same language.
 */
public final class KikPlayerActivity extends Activity {
 private static Site.Film openingFilm;private static Site.Voice openingVoice;
 private Site.Film film;private Site.Voice voice;
 private static final float[] SPEEDS={0.75f,1f,1.25f,1.5f,2f};
 /** Buffer: start after 1.5 s, refill to 5 s after a stall, keep up to 2 min but no more than 48 MB of heap. */
 private static final int MIN_BUFFER=30000,MAX_BUFFER=120000,START_BUFFER=1500,REBUFFER=5000,BUFFER_BYTES=48*1024*1024;
 /** Buffering longer than this, or a picture that does not move, counts as a dead stream. */
 private static final long STALL_MS=30000,FROZEN_MS=15000,RETRY_MS=20000,ENDED_SLACK_MS=90000,INTRO_HOLD_MS=2550,WARM_MS=180000,SHORT_HLS_MS=180000;
 private String season,episode,title,poster,profileId;private Site.Stream stream;private String quality;private float speed=1f;private boolean manualQuality;
 private ExoPlayer player;private SurfaceView surface;private FrameLayout screen;private float aspect;
 /** Ad clips of the playing file (AdBreaks): confirmed ones are jumped over, AI hints get the «Пропустити рекламу» button. */
 private double[] ads,hints;private long hintTo=-1;private TextView skipAd;
 /** «Як вам?» at the credits (PlaybackPolicy.askRating): playback goes on, it hides by itself, each title is asked once. */
 private LinearLayout ask;private TextView askTitle;private View askTimer;private static final long ASK_MS=8000;private final List<TextView> askChoices=new ArrayList<TextView>();private int askFocus;private boolean askOn,askChecked,finishAfterAsk;
 private String askItem;private long watchedItem;private final Runnable askHide=()->hideAsk(true);
 private boolean prepared,seeking,failed,destroyed,active=true,playWhenReady=true,offline;private int generation,subGeneration,playGeneration;private long resumeAt;
 private FrameLayout root,wait;private ImageView pulse,emblem;private android.animation.ObjectAnimator turning;private Dots dots;private boolean waitOn=true,waitBranded=true;private android.animation.ObjectAnimator breathing;private LinearLayout top,bottom,buttons,options;private TextView name,sub,left,right,flash,loading,subtitle;private View played,track,knob;
 private final List<TextView> controls=new ArrayList<TextView>();private int focus=1;private boolean visible,progressFocus;
 private final WatchTracker watchTracker=new WatchTracker();private long watchedPending;
 private final RecoveryPolicy recovery=new RecoveryPolicy();private boolean recovering;private int refreshAttempt,voiceAttempt,sameAttempt,autoRetry,earlyEnds;private final Set<String> failedVoices=new HashSet<String>();
 private long lastMovement,lastPosition,recoveryStarted,bufferingSince,seekAt,stableSince;private final List<Long> rebuffers=new ArrayList<Long>();
 private String menu;private int optFocus,optSelected;private List<String> optLabels=new ArrayList<String>();private List<Runnable> optActions=new ArrayList<Runnable>();
 private List<Object[]> cues=new ArrayList<Object[]>();private int subIndex=-1;
 private ConnectivityManager.NetworkCallback network;
 private ExoPlayer intro;private SurfaceView introSurface;private final Runnable introTimeout=this::endIntro;
 private final Handler h=new Handler(Looper.getMainLooper());
 private final Runnable hide=()->{if(prepared&&!seeking&&playWhenReady&&menu==null)show(false);},tick=new Runnable(){public void run(){update();h.postDelayed(this,500);}},saver=new Runnable(){public void run(){if(active)save();h.postDelayed(this,5000);}},retry=()->{if(!destroyed&&failed)retryNow();};
 private int dp(float n){return Ui.dp(this,n);}

 public static void open(Context c,Site.Film f,Site.Voice v,String season,String episode,String title,String poster){
  openingFilm=f;openingVoice=v;c.startActivity(new Intent(c,KikPlayerActivity.class).putExtra("season",season).putExtra("episode",episode).putExtra("title",title).putExtra("poster",poster));
 }
 /** A stream fetched on the title page while the viewer reads it, so OK starts at once. Links stay valid for minutes. */
 private static final class Warm{final String key;final Site.Voice voice;final Site.Stream stream;final long at=SystemClock.elapsedRealtime();Warm(String k,Site.Voice v,Site.Stream st){key=k;voice=v;stream=st;}}
 private static volatile Warm warm;private static volatile String warming;
 private static String warmKey(Site.Film f,Site.Voice v,String s,String e){return f.id+"|"+v.id+"|"+s+"|"+e;}
 static void prefetch(Context c,final Site.Film f,final Site.Voice v,final String s,final String e){
  if(f==null||v==null||f.id==null||f.sourceUrl!=null)return;final String key=warmKey(f,v,s,e);Warm w=warm;
  if(key.equals(warming)||w!=null&&key.equals(w.key)&&SystemClock.elapsedRealtime()-w.at<WARM_MS/2)return;warming=key;final Context app=c.getApplicationContext();
  Ui.bg(()->{try{Site.Voice use=v;if(f.series&&s!=null){Site.Voice has=Site.voiceFor(f,s,e,Progress.voice(app,f.link));if(has!=null)use=has;}warm=new Warm(key,use,Site.stream(f,use,s,e,false));Log.d("KIK.Player","Prefetched "+key);}catch(Exception x){Log.w("KIK","Prefetch",x);}finally{if(key.equals(warming))warming=null;}});
 }
 private static Warm takeWarm(Site.Film f,Site.Voice v,String s,String e){Warm w=warm;if(w==null||!w.key.equals(warmKey(f,v,s,e))||SystemClock.elapsedRealtime()-w.at>WARM_MS)return null;warm=null;return w;}
 static Site.Episode next(Site.Film f,String s,String e){boolean seen=false;for(Site.Episode x:f.episodes){if(seen)return x;if(x.season.equals(s)&&x.episode.equals(e))seen=true;}return null;}

 @Override public void onCreate(Bundle state){
  super.onCreate(state);film=openingFilm;voice=openingVoice;profileId=ProfileStore.current(this);Ui.screen(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  if(film==null||voice==null){finish();return;}
  season=getIntent().getStringExtra("season");episode=getIntent().getStringExtra("episode");title=getIntent().getStringExtra("title");poster=getIntent().getStringExtra("poster");
  root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
  screen=new FrameLayout(this);root.addView(screen,new FrameLayout.LayoutParams(-1,-1));
  surface=new SurfaceView(this);screen.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
  screen.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)fit();});
  subtitle=Ui.text(this,"",26,Color.WHITE);subtitle.setGravity(Gravity.CENTER);subtitle.setShadowLayer(8,0,2,Color.BLACK);subtitle.setPadding(dp(80),0,dp(80),0);
  FrameLayout.LayoutParams sl=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sl.bottomMargin=dp(48);root.addView(subtitle,sl);
  skipAd=Ui.text(this,"Пропустити рекламу",20,Color.BLACK);skipAd.setGravity(Gravity.CENTER);skipAd.setIncludeFontPadding(false);skipAd.setMinHeight(dp(52));skipAd.setPadding(dp(30),0,dp(30),0);skipAd.setBackground(Ui.shape(0xffffffff,dp(26),0,0));skipAd.setVisibility(View.GONE);
  FrameLayout.LayoutParams kl=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);kl.rightMargin=dp(56);kl.bottomMargin=dp(52);root.addView(skipAd,kl);
  // KIK's waiting screen: the intro's settled frame (logo, «Створено Чорним» below), with what is happening under the logo.
  wait=new FrameLayout(this);wait.setBackgroundColor(Color.BLACK);ImageView mark=new ImageView(this);mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
  mark.setImageBitmap(asset("kik/wait.png"));wait.addView(mark,new FrameLayout.LayoutParams(-1,-1));
  // The logo itself breathes: a small separate layer, so the weak GPU never redraws the whole screen.
  pulse=new ImageView(this);pulse.setScaleType(ImageView.ScaleType.FIT_XY);pulse.setImageBitmap(asset("kik/wait-logo.png"));wait.addView(pulse,new FrameLayout.LayoutParams(0,0));
  // The half-circle by «Створено Чорним» turns slowly, as on the web (6 s a turn).
  emblem=new ImageView(this);emblem.setScaleType(ImageView.ScaleType.FIT_XY);emblem.setImageBitmap(asset("kik/wait-emblem.png"));wait.addView(emblem,new FrameLayout.LayoutParams(0,0));
  wait.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->placeLogo());root.addView(wait,new FrameLayout.LayoutParams(-1,-1));
  loading=Ui.text(this,"Підготовка відео",14,0xffbdb9c6);loading.setGravity(Gravity.CENTER);FrameLayout.LayoutParams ll=new FrameLayout.LayoutParams(dp(680),-2,Gravity.CENTER);ll.topMargin=dp(92);root.addView(loading,ll);
  dots=new Dots(this);FrameLayout.LayoutParams dl=new FrameLayout.LayoutParams(dp(44),dp(10),Gravity.CENTER);dl.topMargin=dp(128);root.addView(dots,dl);
  buildControls();
  player=createPlayer();player.setVideoSurfaceView(surface);
  watchNetwork();
  h.post(tick);h.postDelayed(saver,5000);
  playIntro();
  start(true);
 }
 private ExoPlayer createPlayer(){
  DefaultLoadControl load=new DefaultLoadControl.Builder().setBufferDurationsMs(MIN_BUFFER,MAX_BUFFER,START_BUFFER,REBUFFER).setTargetBufferBytes(BUFFER_BYTES).setPrioritizeTimeOverSizeThresholds(false).build();
  DefaultRenderersFactory renderers=new DefaultRenderersFactory(this).setEnableDecoderFallback(true).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
  ExoPlayer p=new ExoPlayer.Builder(this,renderers).setLoadControl(load).setHandleAudioBecomingNoisy(true).build();
  p.addListener(new Player.Listener(){
   @Override public void onPlaybackStateChanged(int state){onState(state);}
   @Override public void onPlayerError(PlaybackException error){Log.w("KIK.Player","Player error "+error.getErrorCodeName()+" http="+httpCode(error),error);recover(error);}
   @Override public void onVideoSizeChanged(VideoSize size){if(size.width>0&&size.height>0){aspect=size.width*size.pixelWidthHeightRatio/size.height;fit();}}
   @Override public void onRenderedFirstFrame(){Log.d("KIK.Player","First frame after "+(SystemClock.elapsedRealtime()-recoveryStarted)+" ms");}
  });
  return p;
 }
 /** Letterbox the picture inside the screen at its own aspect ratio. */
 private void fit(){
  int w=screen.getWidth(),hh=screen.getHeight();if(w==0||hh==0||aspect<=0)return;
  int vw=w,vh=Math.round(w/aspect);if(vh>hh){vh=hh;vw=Math.round(hh*aspect);}
  FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)surface.getLayoutParams();if(lp.width==vw&&lp.height==vh)return;lp.width=vw;lp.height=vh;lp.gravity=Gravity.CENTER;surface.setLayoutParams(lp);
 }
 /** Retry as soon as the connection is back instead of burning the recovery budget while offline. */
 private void watchNetwork(){
  try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);network=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network n){h.post(()->{if(destroyed||!offline)return;offline=false;Log.d("KIK.Player","Network back, resuming");sameAttempt=0;if(stream==null)retryNow();else retrySame();});}};cm.registerDefaultNetworkCallback(network);}catch(Exception e){Log.w("KIK","Network callback",e);network=null;}
 }
 private boolean online(){try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkInfo n=cm.getActiveNetworkInfo();return n!=null&&n.isConnected();}catch(Exception e){return true;}}
 @Override protected void onResume(){super.onResume();active=true;CloudSync.playing(profileId,true);if(prepared&&player!=null&&!player.isPlaying()){playWhenReady=false;show(true);}}
 @Override protected void onPause(){super.onPause();active=false;endIntro();save();CloudSync.playing(profileId,false);CloudSync.changed(this);playWhenReady=false;if(player!=null)player.setPlayWhenReady(false);}
 @Override protected void onDestroy(){destroyed=true;TvChannels.stopped(this);CloudSync.playing(profileId,false);generation++;h.removeCallbacksAndMessages(null);if(intro!=null){intro.release();intro=null;}if(breathing!=null){breathing.cancel();breathing=null;}if(turning!=null){turning.cancel();turning=null;}if(network!=null)try{((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(network);}catch(Exception ignored){}if(player!=null){player.release();player=null;}super.onDestroy();}

 private long pos(){return player==null?0:Math.max(0,player.getCurrentPosition());}
 /** For the device audits: position, duration and whether frames are advancing right now. */
 public long position(){return pos();}public long duration(){return dur();}public boolean playing(){return player!=null&&player.isPlaying();}public float rate(){return player==null?0:player.getPlaybackParameters().speed;}
 private long dur(){long d=player==null?C.TIME_UNSET:player.getDuration();return d==C.TIME_UNSET||d<0?0:d;}

 // ---------- playback ----------
 /** Loads the stream for the current voice/episode; keepPosition continues from the same moment. */
 private void start(final boolean fromHistory){
  start(fromHistory,false);
 }
 private void start(final boolean fromHistory,final boolean refreshEpisodes){
  h.removeCallbacks(retry);
  if(!recovering){recovery.reset();failedVoices.clear();refreshAttempt=0;voiceAttempt=0;sameAttempt=0;earlyEnds=0;rebuffers.clear();recoveryStarted=SystemClock.elapsedRealtime();}
  lastMovement=SystemClock.elapsedRealtime();bufferingSince=0;playGeneration++;final int gen=++generation;final Site.Film selectedFilm=film;final Site.Voice selected=voice;final String ss=season,ee=episode;
  final long keep=prepared?pos():resumeAt;if(player!=null)player.setPlayWhenReady(false);if(fromHistory)playWhenReady=true;prepared=false;failed=false;loading.setText(recovering?loading.getText():"Підготовка відео");waiting(true);
  name.setText(title);paintSub();
  Ui.bg(()->{
   try{
    Site.Film updated=null;if(refreshEpisodes&&selectedFilm.series){Site.Film copy=new Site.Film();copy.id=selectedFilm.id;copy.favs=selectedFilm.favs;copy.origin=selectedFilm.origin;try{Site.episodesFor(copy,selected.id);if(!copy.episodes.isEmpty())updated=copy;}catch(Exception e){Log.w("KIK","Voice episodes",e);}}
    final Site.Film episodeData=updated;
    Site.Voice use=selected;final Warm w=refreshEpisodes||recovering?null:takeWarm(selectedFilm,selected,ss,ee);if(w!=null){use=w.voice;Log.d("KIK.Player","Using prefetched stream");}
    if(w==null&&selectedFilm.series&&ss!=null)try{Site.Voice has=Site.voiceFor(selectedFilm,ss,ee,(refreshEpisodes||recovering)?selected.id:Progress.voice(this,selectedFilm.link));if(has!=null)use=has;}catch(Exception e){Log.w("KIK","Voice coverage",e);}
    final Site.Voice playing=use;
    final boolean rotate=recovering&&refreshAttempt>0;
    final Site.Stream s=w!=null?w.stream:Site.stream(selectedFilm,playing,ss,ee,rotate);
    h.post(()->{
     if(destroyed||gen!=generation)return;
     if(episodeData!=null){selectedFilm.seasons=new ArrayList<String>(episodeData.seasons);selectedFilm.episodes=new ArrayList<Site.Episode>(episodeData.episodes);}
     if(playing!=selected){voice=playing;paintSub();
      // Say so only when the episode is not in the language the viewer wants (their choice, else the best one offered).
      String chosen=Progress.voice(this,selectedFilm.link),desired=null;for(Site.Voice v:selectedFilm.voices)if(!v.premium&&(chosen==null?desired==null:v.id.equals(chosen)))desired=PlaybackPolicy.lang(v.name);
      String got=PlaybackPolicy.lang(playing.name);if(desired!=null&&!desired.equals(got))Ui.toast(this,PlaybackPolicy.label(desired)+" озвучка для цієї серії недоступна. Увімкнено: "+PlaybackPolicy.label(got).toLowerCase(Locale.ROOT)+".");}
     recovering=false;stream=s;if(s.qualities.isEmpty()){failure("Відео недоступне для цієї озвучки.\nВиберіть іншу озвучку або натисніть OK для повтору.",false);return;}
     quality=quality!=null&&s.qualities.containsKey(quality)&&(!fromHistory||manualQuality)?quality:pickQuality(s);
     if(fromHistory){long[] p=Progress.episode(this,film.link,season,episode);resumeAt=p!=null&&!(p[1]>0&&p[0]>p[1]*0.93)?p[0]:0;}else resumeAt=keep;
     // Start after the voice studio's ident: the film begins at once, as on Netflix.
     resumeAt=Math.max(resumeAt,PlaybackPolicy.introMs(playing.name));
     subGeneration++;cues.clear();subIndex=-1;subtitle.setText("");
     play();
     ads=null;hints=null;showHint(-1);if(PlaybackPolicy.sponsoredVoice(playing.name)){final String vid=playing.id,link=selectedFilm.link;Ui.bg(()->{final double[][] a=AdBreaks.get(this,link,vid,ss,ee);h.post(()->{if(!destroyed&&gen==generation&&a!=null){ads=a[0];hints=a[1];}});});}
    });
   }catch(final Exception e){Log.w("KIK","Stream",e);h.post(()->{if(destroyed||gen!=generation)return;recovering=false;if(!online()){offline=true;failure("Немає з'єднання з інтернетом.\nВідео продовжиться, щойно мережа з'явиться.",false);return;}recover(null);});}
  });
 }
 /** Shows the problem; unless the viewer has to choose, retries by itself every 20 s for 10 minutes. */
 private void failure(String message,boolean auto){
  prepared=false;failed=true;focus=1;progressFocus=false;menu=null;options.setVisibility(View.GONE);loading.setText(message);waiting(true);show(true);
  h.removeCallbacks(retry);if(auto&&autoRetry<30){autoRetry++;h.postDelayed(retry,RETRY_MS);}
 }
 /** Starts over with a fresh recovery budget at the same moment of the same episode. */
 private void retryNow(){h.removeCallbacks(retry);recovering=false;failed=false;start(false);}
 private void play(){
  seeking=false;failed=false;final String address=stream.qualities.get(quality);recovery.used(address);lastMovement=SystemClock.elapsedRealtime();lastPosition=resumeAt;bufferingSince=0;
  final int gen=++playGeneration;final long t0=SystemClock.elapsedRealtime();
  Ui.bg(()->{final String url=Site.resolve(address);h.post(()->{if(destroyed||gen!=playGeneration||player==null)return;Log.d("KIK.Player","Resolved in "+(SystemClock.elapsedRealtime()-t0)+" ms"+(url.equals(address)?"":" via node"));load(url);});});
 }
 private void load(String url){
  DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(Site.UA).setConnectTimeoutMs(12000).setReadTimeoutMs(20000).setAllowCrossProtocolRedirects(true);
  DefaultLoadErrorHandlingPolicy retries=new Retries();
  DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,Doh.media(http));
  MediaItem item=MediaItem.fromUri(url);MediaSource source=hls(url)?new HlsMediaSource.Factory(data).setAllowChunklessPreparation(true).setLoadErrorHandlingPolicy(retries).createMediaSource(item):new ProgressiveMediaSource.Factory(data).setLoadErrorHandlingPolicy(retries).setContinueLoadingCheckIntervalBytes(512*1024).createMediaSource(item);
  Log.d("KIK.Player","Play quality="+quality+" hls="+hls(url)+" at="+resumeAt+" alternatives="+stream.alternatives.get(quality));
  player.setPlaybackSpeed(speed);player.setMediaSource(source,resumeAt);player.setPlayWhenReady(wanted());player.prepare();paintSub();
 }
 /**
  * Up to 8 in-place retries with growing pauses: a short CDN hiccup resumes from the same byte, unseen by the viewer.
  * A link the server refuses or no longer has fails at once, so recovery can fetch a working one.
  */
 private static final class Retries extends DefaultLoadErrorHandlingPolicy {
  Retries(){super(8);}
  @Override public long getRetryDelayMsFor(LoadErrorInfo info){
   int http=httpCode(info.exception);boolean missing=false;for(Throwable t=info.exception;t!=null;t=t.getCause())if(t instanceof FileNotFoundException)missing=true;
   if(missing||http>=400&&http<500&&http!=408&&http!=429)return C.TIME_UNSET;
   return super.getRetryDelayMsFor(info);
  }
 }
 static boolean hls(String url){String path=Uri.parse(url).getPath();return path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".m3u8");}
 private void onState(int state){
  if(destroyed)return;long now=SystemClock.elapsedRealtime();
  if(state==Player.STATE_READY){
   if(!prepared&&truncated())return;
   boolean first=!prepared;prepared=true;failed=false;offline=false;h.removeCallbacks(retry);seeking=false;bufferingSince=0;autoRetry=0;waiting(false);lastMovement=now;lastPosition=pos();
   if(first){if(!active)player.setPlayWhenReady(false);show(true);TvChannels.played(this,profileId);Log.d("KIK.Player","Prepared quality="+quality+" playing="+player.isPlaying()+" speed="+speed+" after "+(now-recoveryStarted)+" ms");}
   update();
  }else if(state==Player.STATE_BUFFERING){
   if(bufferingSince==0)bufferingSince=now;
   if(prepared){seeking=now-seekAt<3000;if(!seeking&&playWhenReady){rebuffers.add(now);Log.d("KIK.Player","Rebuffer at "+pos()+" buffered="+player.getBufferedPosition());lowerIfStruggling(now);}}
  }else if(state==Player.STATE_ENDED){onEnded();}
 }
 /** Three stalls within three minutes on an automatic quality: step one quality down, Netflix-style. */
 private void lowerIfStruggling(long now){
  while(!rebuffers.isEmpty()&&now-rebuffers.get(0)>180000)rebuffers.remove(0);
  if(manualQuality||rebuffers.size()<3||stream==null)return;
  String lower=null;boolean past=false;for(String q:stream.qualities.keySet()){if(past){lower=q;break;}if(q.equals(quality))past=true;}
  if(lower==null)return;rebuffers.clear();Log.d("KIK.Player","Lowering quality "+quality+" -> "+lower);resumeAt=pos();quality=lower;Ui.toast(this,"Повільне з'єднання: якість знижено до "+lower);play();
 }
 private static int httpCode(Throwable t){for(int i=0;t!=null&&i<8;i++,t=t.getCause())if(t instanceof HttpDataSource.InvalidResponseCodeException)return ((HttpDataSource.InvalidResponseCodeException)t).responseCode;return 0;}
 /** Preserve the same episode, language, position, speed and pause state across failures. */
 private void recover(PlaybackException error){
  if(destroyed||recovering||!profileId.equals(ProfileStore.current(this)))return;
  if(prepared){long current=pos();resumeAt=current>0?current:lastPosition;save();}else if(player!=null&&player.getCurrentPosition()>0)resumeAt=player.getCurrentPosition();
  prepared=false;seeking=false;bufferingSince=0;recovery.interrupted();stableSince=0;
  int code=error==null?0:error.errorCode,http=httpCode(error);
  boolean io=error==null||code>=2000&&code<3000;
  if(io&&!online()){offline=true;failure("Немає з'єднання з інтернетом.\nВідео продовжиться, щойно мережа з'явиться.",false);return;}
  // An expired or missing link will not come back: go straight for fresh links. A network blip: same address once more.
  boolean expired=http==401||http==403||http==410||http==451,missing=http==404||code==PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND;
  if(io&&!expired&&!missing&&error!=null&&sameAttempt<2){sameAttempt++;retrySame();return;}
  if(expired&&recovery.refresh()){refreshAttempt++;recovering=true;loading.setText("Оновлюємо посилання");waiting(true);start(false);return;}
  LinkedHashMap<String,List<String>> options=new LinkedHashMap<String,List<String>>();if(stream!=null)for(String q:stream.qualities.keySet()){List<String> urls=stream.alternatives.get(q);options.put(q,urls==null?Arrays.asList(stream.qualities.get(q)):urls);}
  String candidate=recovery.next(options,quality);if(candidate!=null){String[] parts=candidate.split("\n",2);quality=parts[0];stream.qualities.put(quality,parts[1]);loading.setText("Відновлюємо потік");waiting(true);play();return;}
  if(recovery.refresh()){refreshAttempt++;recovering=true;loading.setText("Шукаємо резервну адресу");waiting(true);start(false);return;}
  failedVoices.add(voice.id);if(voiceAttempt<1){String language=PlaybackPolicy.lang(voice.name);for(Site.Voice v:film.voices)if(!v.premium&&!v.ads&&!failedVoices.contains(v.id)&&PlaybackPolicy.lang(v.name).equals(language)){voice=v;voiceAttempt++;recovering=true;start(false);return;}}
  failure("Відео тимчасово недоступне. Пробуємо ще раз автоматично…\nOK — повторити зараз або виберіть іншу мову.",true);
 }
 /** Re-open the same address at the same moment; ExoPlayer keeps the decoders and continues where it stopped. */
 private void retrySame(){
  if(player==null||stream==null){retryNow();return;}
  failed=false;loading.setText("Відновлюємо з'єднання");waiting(true);bufferingSince=0;lastMovement=SystemClock.elapsedRealtime();
  Log.d("KIK.Player","Retry same address at "+resumeAt+" attempt="+sameAttempt);
  if(player.getPlayerError()!=null){player.seekTo(resumeAt);player.setPlayWhenReady(wanted());player.prepare();}else play();
 }
 private String pickQuality(Site.Stream s){
  String want=getSharedPreferences("settings",0).getString("default_quality","1080p");int target=num(want);String best=null;int bestN=-1;
  for(String q:s.qualities.keySet()){if(q.equals(want))return q;int n=num(q);if(n<=target+1&&n>bestN){best=q;bestN=n;}}
  if(best==null)for(String q:s.qualities.keySet())best=q;return best;
 }
 private static int num(String q){Matcher m=Pattern.compile("(\\d+)").matcher(q==null?"":q);int n=m.find()?Integer.parseInt(m.group(1)):0;if(q!=null&&q.toLowerCase(Locale.ROOT).contains("ultra"))n+=1;if(q!=null&&(q.contains("2K")||q.contains("2k")))n=1440;if(q!=null&&(q.contains("4K")||q.contains("4k")))n=2160;return n;}
 private void applySpeed(){if(player!=null)player.setPlaybackSpeed(speed);Log.d("KIK.Player","Speed="+speed);}
 private void seek(long delta){if(!prepared||player==null)return;long d=dur(),t=Math.max(0,pos()+delta);if(d>0)t=Math.min(Math.max(0,d-1500),t);resumeAt=t;seekAt=SystemClock.elapsedRealtime();lastMovement=seekAt;Log.d("KIK.Player","Seek requested="+t);player.seekTo(t);update();}
 private void toggle(){if(!prepared||player==null)return;playWhenReady=!playWhenReady;player.setPlayWhenReady(wanted());lastMovement=SystemClock.elapsedRealtime();update();show(true);Log.d("KIK.Player","Playing="+playWhenReady+" position="+pos());}
 /** The KIK ident plays above everything while the stream loads underneath; the video starts when it ends. */
 private void playIntro(){
  if(intro!=null||!getSharedPreferences("settings",0).getBoolean("intro",true))return;
  try{
   introSurface=new SurfaceView(this);introSurface.setZOrderOnTop(true);root.addView(introSurface,new FrameLayout.LayoutParams(-1,-1));
   intro=new ExoPlayer.Builder(this).build();intro.setVideoSurfaceView(introSurface);
   intro.addListener(new Player.Listener(){
    @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED)endIntro();}
    @Override public void onPlayerError(PlaybackException error){Log.w("KIK.Player","Intro",error);endIntro();}
   });
   intro.setMediaItem(MediaItem.fromUri("asset:///kik/intro.mp4"));intro.setPlayWhenReady(true);intro.prepare();paintWait();
   h.postDelayed(introTimeout,6000);h.postDelayed(introWatch,100);
  }catch(Exception e){Log.w("KIK.Player","Intro",e);endIntro();}
 }
 private void endIntro(){
  if(intro==null&&introSurface==null)return;h.removeCallbacks(introTimeout);
  if(intro!=null){intro.release();intro=null;}paintWait();if(introSurface!=null){root.removeView(introSurface);introSurface=null;}
  if(player!=null&&!destroyed)player.setPlayWhenReady(wanted());lastMovement=SystemClock.elapsedRealtime();
 }
 /** Without premium the source serves a one-minute preview for its top quality: drop it and play the next one down. */
 private boolean truncated(){
  long d=dur();if(stream==null||quality==null||d<=0||d>=SHORT_HLS_MS||stream.qualities.size()<2)return false;
  String lower=null;boolean past=false;for(String q:stream.qualities.keySet()){if(past){lower=q;break;}if(q.equals(quality))past=true;}
  if(lower==null)return false;Log.w("KIK.Player",quality+" is a "+d+" ms preview, switching to "+lower);
  stream.qualities.remove(quality);stream.alternatives.remove(quality);quality=lower;resumeAt=Math.max(resumeAt,pos());play();return true;
 }
 private Bitmap asset(String name){try(InputStream in=getAssets().open(name)){BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.RGB_565;return BitmapFactory.decodeStream(in,null,o);}catch(Exception e){Log.w("KIK","Wait screen "+name,e);return null;}}
 /** The logo crop sits exactly where it was cut from the 1280×720 frame, as the background is scaled. */
 private void placeLogo(){
  int w=wait.getWidth(),hh=wait.getHeight();if(w==0||hh==0)return;float k=Math.min(w/1280f,hh/720f);float x0=(w-1280*k)/2,y0=(hh-720*k)/2;
  FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)pulse.getLayoutParams();int lw=Math.round(440*k),lh=Math.round(230*k),lx=Math.round(x0+420*k),ly=Math.round(y0+244*k);
  // The line and the dots sit on the frame's own grid: between the logo (bottom 432) and the credit (668), centred on 486 and 522.
  FrameLayout.LayoutParams tl=(FrameLayout.LayoutParams)loading.getLayoutParams(),dl=(FrameLayout.LayoutParams)dots.getLayoutParams();int tm=Math.round((486-360)*k),dm=Math.round((522-360)*k);
  if(tl.topMargin!=tm){tl.topMargin=tm;loading.setLayoutParams(tl);}if(dl.topMargin!=dm){dl.topMargin=dm;dots.setLayoutParams(dl);}
  FrameLayout.LayoutParams el=(FrameLayout.LayoutParams)emblem.getLayoutParams();int es=Math.round(18*k),ex=Math.round(x0+551*k),ey=Math.round(y0+668*k);
  if(el.width!=es||el.leftMargin!=ex||el.topMargin!=ey){el.width=es;el.height=es;el.leftMargin=ex;el.topMargin=ey;el.gravity=Gravity.TOP|Gravity.START;emblem.setLayoutParams(el);}
  if(lp.width==lw&&lp.height==lh&&lp.leftMargin==lx&&lp.topMargin==ly)return;lp.width=lw;lp.height=lh;lp.leftMargin=lx;lp.topMargin=ly;lp.gravity=Gravity.TOP|Gravity.START;pulse.setLayoutParams(lp);
 }
 private void waiting(boolean on){waiting(on,true);}
 private void waiting(boolean on,boolean branded){waitOn=on;waitBranded=branded;paintWait();}
 /** The intro runs first; the waiting screen only takes over from it, never shows before it. */
 private void paintWait(){
  boolean on=waitOn&&intro==null;if(on&&loading.getVisibility()!=View.VISIBLE){loading.setAlpha(0f);loading.animate().alpha(1f).setDuration(350).start();}
  loading.setVisibility(on?View.VISIBLE:View.GONE);boolean show=on&&waitBranded;wait.setVisibility(show?View.VISIBLE:View.GONE);dots.setVisibility(show&&!failed?View.VISIBLE:View.GONE);
  // On the KIK screen the line reads like the credit below it (small caps, wide tracking, its grey); over the picture it needs a shadow.
  boolean quiet=show&&!failed;loading.setAllCaps(quiet);loading.setLetterSpacing(quiet?0.22f:0.02f);loading.setTextColor(quiet?0xff9d9aa6:0xffe6e3ea);loading.setTextSize(quiet?13:17);
  if(show)loading.setShadowLayer(0,0,0,0);else loading.setShadowLayer(8,0,2,Color.BLACK);
  if(show&&breathing==null){pulse.setLayerType(View.LAYER_TYPE_HARDWARE,null);breathing=android.animation.ObjectAnimator.ofPropertyValuesHolder(pulse,android.animation.PropertyValuesHolder.ofFloat(View.ALPHA,1f,0.55f),android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X,1f,0.96f),android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y,1f,0.96f));
   breathing.setDuration(1100);breathing.setRepeatCount(android.animation.ValueAnimator.INFINITE);breathing.setRepeatMode(android.animation.ValueAnimator.REVERSE);breathing.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());breathing.start();
   turning=android.animation.ObjectAnimator.ofFloat(emblem,View.ROTATION,0f,360f);turning.setDuration(6000);turning.setRepeatCount(android.animation.ValueAnimator.INFINITE);turning.setInterpolator(new android.view.animation.LinearInterpolator());turning.start();}
  else if(!show&&breathing!=null){breathing.cancel();breathing=null;if(turning!=null){turning.cancel();turning=null;}pulse.setAlpha(1f);pulse.setScaleX(1f);pulse.setScaleY(1f);pulse.setLayerType(View.LAYER_TYPE_NONE,null);}
 }
 /** Cut the intro on its settled frame when the video is not ready yet: the waiting screen continues it seamlessly. */
 private final Runnable introWatch=new Runnable(){public void run(){if(intro==null)return;if(!prepared&&intro.getCurrentPosition()>=INTRO_HOLD_MS){endIntro();return;}h.postDelayed(this,100);}};
 /** Three small red dots that brighten one after another under the waiting line; only this strip redraws. */
 static final class Dots extends View{
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  Dots(Context c){super(c);paint.setColor(Ui.RED);}
  @Override protected void onDraw(Canvas canvas){
   float r=getHeight()*0.28f,gap=getWidth()/3f,t=(android.os.SystemClock.uptimeMillis()%1200)/1200f;
   for(int i=0;i<3;i++){float phase=(t-i*0.18f+1f)%1f;float v=(float)Math.max(0,Math.sin(phase*Math.PI));paint.setAlpha((int)(60+195*v));canvas.drawCircle(gap*(i+0.5f),getHeight()/2f,r*(0.75f+0.25f*v),paint);}
   if(getVisibility()==VISIBLE)postInvalidateOnAnimation();
  }
  @Override protected void onVisibilityChanged(View v,int visibility){super.onVisibilityChanged(v,visibility);if(visibility==VISIBLE)postInvalidateOnAnimation();}
 }
 private boolean wanted(){return playWhenReady&&active&&intro==null;}
 private void onEnded(){
  // A broken stream can report the end long before the credits: resume it instead of marking the episode watched.
  if(truncated())return;
  long d=dur(),at=pos();if(d>0&&at<d-ENDED_SLACK_MS&&earlyEnds++<3){Log.w("KIK.Player","Ended early at "+at+" of "+d+", resuming");resumeAt=at;retrySame();return;}
  save();Site.Episode n=film.series?next(film,season,episode):null;
  boolean auto=getSharedPreferences("settings",0).getBoolean("auto_play_next_episode",true);
  if(n!=null&&auto){season=n.season;episode=n.episode;playIntro();start(true);}else if(ask!=null&&ask.getVisibility()==View.VISIBLE)finishAfterAsk=true;else finish();
 }
 private void save(){
  if(!prepared||film==null||player==null||!profileId.equals(ProfileStore.current(this)))return;
  Progress.Entry e=new Progress.Entry();e.link=film.link;e.title=title;e.poster=poster;e.season=film.series?season:null;e.episode=film.series?episode:null;
  e.voice=voice.id;e.voiceName=voice.name;e.pos=pos();e.dur=dur();
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
  if(film.series)addControl("Наступна серія",()->{if(!prepared&&!failed)return;Site.Episode n=next(film,season,episode);if(n!=null){save();season=n.season;episode=n.episode;playIntro();start(true);}else Ui.toast(this,"Це остання серія");});
  if(PlaybackPolicy.langs(voiceNames()).size()>1)addControl("Озвучка",()->openVoices());
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
 /** Round transport button drawn as vectors: replay 10, play/pause, forward 10. The text only names the state. */
 private static final class Icon extends TextView {
  private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final RectF box=new RectF();
  Icon(Context c,String state){super(c);setText(state);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);ink.setTextAlign(Paint.Align.CENTER);ink.setTypeface(Typeface.DEFAULT_BOLD);}
  @Override protected void onDraw(Canvas canvas){
   float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2,r=Math.min(w,h)/2,u=r/10f;String s=getText().toString();ink.setColor(getCurrentTextColor());
   if(s.equals("❚❚")){ink.setStyle(Paint.Style.FILL);box.set(cx-3.6f*u,cy-4.4f*u,cx-1.2f*u,cy+4.4f*u);canvas.drawRoundRect(box,u*.7f,u*.7f,ink);box.offset(4.8f*u,0);canvas.drawRoundRect(box,u*.7f,u*.7f,ink);}
   else if(s.equals("▶")){ink.setStyle(Paint.Style.FILL);path.reset();path.moveTo(cx-2.8f*u,cy-4.8f*u);path.lineTo(cx+5f*u,cy);path.lineTo(cx-2.8f*u,cy+4.8f*u);path.close();canvas.drawPath(path,ink);}
   else{boolean back=s.startsWith("⟲");ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.1f*u);box.set(cx-5.6f*u,cy-5.6f*u,cx+5.6f*u,cy+5.6f*u);
    // Circle open at the top; the arrowhead sits at one end of the gap and points across it.
    canvas.drawArc(box,-60,300,false,ink);
    float R=5.6f*u,ax=cx+(back?.5f:-.5f)*R,ay=cy-.866f*R,dir=back?-1:1;ink.setStyle(Paint.Style.FILL);path.reset();path.moveTo(ax+dir*2.6f*u,ay);path.lineTo(ax-dir*.4f*u,ay-2.1f*u);path.lineTo(ax-dir*.4f*u,ay+2.1f*u);path.close();canvas.drawPath(path,ink);
    ink.setTextSize(4.6f*u);canvas.drawText("10",cx,cy+1.7f*u,ink);}
  }
 }
 private void addControl(String label,Runnable r){
  if(label.equals("⟲10")||label.equals("❚❚")||label.equals("10⟳")){boolean main=label.equals("❚❚");Icon t=new Icon(this,label);t.setTag(r);t.setOnClickListener(v->{r.run();show(true);});
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(main?64:54),dp(main?64:54));lp.setMargins(0,0,dp(14),0);buttons.addView(t,lp);controls.add(t);return;}
  boolean icon=label.length()<=4;TextView t=Ui.bold(this,label,icon?20:16);t.setSingleLine(true);t.setGravity(Gravity.CENTER);t.setPadding(icon?0:dp(12),0,icon?0:dp(12),0);t.setTag(r);t.setOnClickListener(v->{r.run();show(true);});
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(icon?dp(54):-2,dp(48));lp.setMargins(0,0,dp(8),0);buttons.addView(t,lp);controls.add(t);
 }
 private List<String> voiceNames(){List<String> n=new ArrayList<String>();for(Site.Voice v:film.voices)if(!v.premium)n.add(v.name);return n;}
 private void paintSub(){
  String s=film.series&&season!=null?"Сезон "+season+" · Серія "+episode:"";
  sub.setText(Ui.join(s,PlaybackPolicy.label(PlaybackPolicy.lang(voice.name)),quality));
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
  List<String> names=new ArrayList<String>();for(Site.Voice v:film.voices)if(!v.premium)names.add(v.name);
  for(final String lang:PlaybackPolicy.langs(names)){Site.Voice best=null;for(Site.Voice x:film.voices)if(!x.premium&&PlaybackPolicy.lang(x.name).equals(lang)){best=x;break;}final Site.Voice v=best;
   if(voice!=null&&PlaybackPolicy.lang(voice.name).equals(lang))sel=l.size();l.add(PlaybackPolicy.label(lang));
   a.add(()->{if(voice!=null&&PlaybackPolicy.lang(voice.name).equals(lang))return;save();voice=v;Progress.chooseVoice(this,film.link,v.id);start(false,true);});}
  openMenu("voice",l,a,sel);
 }
 private void openQualities(){
  if(stream==null||!prepared)return;List<String> l=new ArrayList<String>();List<Runnable> a=new ArrayList<Runnable>();int sel=0;
  for(final String q:stream.qualities.keySet()){if(q.equals(quality))sel=l.size();l.add(q);a.add(()->{if(q.equals(quality))return;resumeAt=prepared?pos():resumeAt;prepared=false;quality=q;manualQuality=true;rebuffers.clear();paintSub();loading.setText("Підготовка відео");waiting(true);play();});}
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
  final int gen=++subGeneration;Ui.bg(()->{HttpURLConnection c=null;try{c=Doh.open(url);c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent",Site.UA);String body=Site.read(c.getInputStream());final List<Object[]> parsed=vtt(body);h.post(()->{if(destroyed||gen!=subGeneration)return;cues=parsed;subIndex=index;update();});}
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
 private void placeSubtitles(){FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)subtitle.getLayoutParams();int margin=visible?Math.max(dp(140),bottom.getHeight()-dp(40)):dp(48);if(lp.bottomMargin!=margin){lp.bottomMargin=margin;subtitle.setLayoutParams(lp);}
  // The rating card rises above the open controls, as «Пропустити рекламу» does.
  if(ask!=null){FrameLayout.LayoutParams al=(FrameLayout.LayoutParams)ask.getLayoutParams();int at=visible?bottom.getHeight()+dp(8):dp(28);if(al.bottomMargin!=at){al.bottomMargin=at;ask.setLayoutParams(al);}}}
 private void fade(final View v,boolean on){
  v.animate().cancel();
  if(!Ui.animations()){v.setAlpha(on?1:0);v.setTranslationY(0);v.setVisibility(on?View.VISIBLE:View.GONE);return;}
  if(on){v.setVisibility(View.VISIBLE);v.setTranslationY(v==top?-dp(12):dp(12));v.animate().alpha(1).translationY(0).setDuration(300).setInterpolator(Ui.EASE).start();}
  else v.animate().alpha(0).translationY(v==top?-dp(12):dp(12)).setDuration(320).withEndAction(()->v.setVisibility(View.GONE)).start();
 }
 /** The «Пропустити рекламу» button for an AI hint: shown while playback is inside the clip, OK jumps to its end. */
 private void showHint(long to){hintTo=to;if(skipAd==null)return;int v=to>0?View.VISIBLE:View.GONE;if(skipAd.getVisibility()!=v)skipAd.setVisibility(v);}
 private void skipHint(){long to=hintTo;showHint(-1);if(player==null||to<=0)return;Log.d("KIK.Player","Ad hint skipped "+pos()+" -> "+to);resumeAt=to;seekAt=SystemClock.elapsedRealtime();lastMovement=seekAt;player.seekTo(to);}
 private void maybeAsk(long position){
  long d=dur();if(askChecked||d<=0||position<PlaybackPolicy.creditsAt(d)||!profileId.equals(ProfileStore.current(this)))return;askChecked=true;
  boolean last=film.series&&next(film,season,episode)==null;Progress.Entry saved=film.series?null:Progress.get(this,film.link);
  long watched=Math.max(watchedItem,saved==null?0:saved.watchedMs+watchedPending);int finished=film.series?Progress.finishedEpisodes(this,film.link):0;
  if(!PlaybackPolicy.askRating(position,d,watched,film.series,last,finished)||RatingStore.get(this,profileId,film.link)!=0||RatingStore.asked(this,profileId,film.link))return;
  Log.d("KIK.Player","Rating prompt at "+position+" of "+d+" watched="+watched+" finished="+finished);showAsk();
 }
 private void showAsk(){
  if(ask==null){
   // One quiet line, as on Netflix: the question, two round thumbs, «Пропустити»; a thin line under it runs out before it hides.
   ask=new LinearLayout(this);ask.setOrientation(1);ask.setClipChildren(false);ask.setClipToPadding(false);ask.setPadding(dp(22),dp(10),dp(14),dp(8));ask.setBackground(Ui.shape(0xe6141418,dp(32),0,0));
   LinearLayout line=new LinearLayout(this);line.setOrientation(0);line.setGravity(Gravity.CENTER_VERTICAL);line.setClipChildren(false);line.setClipToPadding(false);ask.addView(line);
   askTitle=Ui.bold(this,"",17);askTitle.setSingleLine(true);askTitle.setEllipsize(TextUtils.TruncateAt.END);askTitle.setMaxWidth(dp(420));line.addView(askTitle,new LinearLayout.LayoutParams(-2,-2));
   String[] labels={"Подобається","Не подобається","Пропустити"};
   for(int i=0;i<labels.length;i++){final int value=i==0?1:i==1?-1:0;TextView t=Ui.bold(this,i<2?"":labels[i],15);t.setSingleLine(true);t.setGravity(Gravity.CENTER);t.setContentDescription(labels[i]);LinearLayout.LayoutParams lp;
    if(i<2){FilmActivity.ThumbIcon icon=new FilmActivity.ThumbIcon(dp(20),i==1);icon.setBounds(0,0,dp(20),dp(20));t.setCompoundDrawables(icon,null,null,null);t.setPadding(dp(12),0,0,0);lp=new LinearLayout.LayoutParams(dp(44),dp(44));lp.setMargins(dp(i==0?16:10),dp(2),0,dp(2));}
    else{t.setPadding(dp(16),0,dp(16),0);lp=new LinearLayout.LayoutParams(-2,dp(44));lp.setMargins(dp(10),dp(2),0,dp(2));}
    t.setOnClickListener(v->answer(value));line.addView(t,lp);askChoices.add(t);}
   askTimer=new View(this);askTimer.setBackground(Ui.shape(Ui.RED,dp(1),0,0));askTimer.setPivotX(0);LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,dp(2));tl.setMargins(0,dp(6),dp(8),0);ask.addView(askTimer,tl);
   FrameLayout.LayoutParams al=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);al.leftMargin=dp(48);al.bottomMargin=dp(28);root.addView(ask,al);
  }
  // The card owns ◀ ▶ OK while it is up, so open controls (not a menu) step aside for it.
  if(visible&&menu==null)show(false);
  askTitle.setText("Як вам «"+title+"»?");for(TextView t:askChoices)t.setVisibility(View.VISIBLE);askTimer.setVisibility(View.VISIBLE);askFocus=0;askOn=true;paintAsk();placeSubtitles();
  // Slides in from the left; the thumbs follow one after another.
  ask.animate().cancel();ask.setVisibility(View.VISIBLE);ask.setAlpha(0);ask.setTranslationX(-dp(40));ask.setTranslationY(0);ask.animate().alpha(1).translationX(0).setDuration(380).setInterpolator(Ui.EASE).start();
  for(int i=0;i<askChoices.size();i++){View t=askChoices.get(i);t.setAlpha(0);t.setTranslationX(-dp(12));t.animate().alpha(1).translationX(0).setStartDelay(160+i*70).setDuration(260).setInterpolator(Ui.EASE).start();}
  RatingStore.markAsked(this,profileId,film.link);askCountdown();
 }
 /** Hides after ASK_MS without a press; any press on the card starts the count again. */
 private void askCountdown(){h.removeCallbacks(askHide);h.postDelayed(askHide,ASK_MS);askTimer.animate().cancel();askTimer.setScaleX(1);askTimer.animate().scaleX(0).setStartDelay(0).setDuration(ASK_MS).setInterpolator(new android.view.animation.LinearInterpolator()).start();}
 private void paintAsk(){for(int i=0;i<askChoices.size();i++){TextView t=askChoices.get(i);boolean f=i==askFocus;Ui.paintControl(t,f);if(!f&&i==2){t.setBackground(null);t.setTextColor(0xffbdb9c6);}android.graphics.drawable.Drawable icon=t.getCompoundDrawables()[0];if(icon!=null)icon.setColorFilter(new PorterDuffColorFilter(f?Color.BLACK:Color.WHITE,PorterDuff.Mode.SRC_IN));}}
 /** 👍 / 👎 save the same rating as the title page (synced, moves recommendations); 0 is «Пропустити». */
 private void answer(int value){
  if(!askOn)return;askOn=false;h.removeCallbacks(askHide);
  if(value==0){hideAsk(true);return;}
  boolean saved=RatingStore.set(this,profileId,film.link,title,poster,TextUtils.join(", ",film.genres),value);
  View chosen=askChoices.get(value>0?0:1);chosen.animate().cancel();chosen.setAlpha(1);chosen.setTranslationX(0);chosen.animate().setStartDelay(0).scaleX(1.3f).scaleY(1.3f).setDuration(140).withEndAction(()->chosen.animate().scaleX(1).scaleY(1).setDuration(180).start()).start();askTimer.animate().cancel();askTimer.setVisibility(View.GONE);
  askTitle.setText(saved?RatingStore.thanks(value):"Не вдалося зберегти оцінку");for(int i=0;i<askChoices.size();i++)if(i!=(value>0?0:1))askChoices.get(i).setVisibility(View.GONE);h.postDelayed(askHide,2200);
 }
 private void hideAsk(boolean fade){
  askOn=false;h.removeCallbacks(askHide);if(ask==null)return;ask.animate().cancel();
  Runnable gone=()->{ask.setVisibility(View.GONE);if(finishAfterAsk&&!destroyed){finishAfterAsk=false;finish();}};
  if(fade)ask.animate().alpha(0).translationX(-dp(40)).setDuration(300).withEndAction(gone).start();else gone.run();
 }
 private void pulse(String s){flash.setText(s);flash.animate().cancel();flash.setScaleX(0.9f);flash.setScaleY(0.9f);flash.animate().alpha(1).scaleX(1).scaleY(1).setDuration(200).setInterpolator(Ui.EASE).withEndAction(()->flash.animate().alpha(0).setStartDelay(500).setDuration(300).start()).start();}
 private void update(){
  long now=SystemClock.elapsedRealtime();
  if(!destroyed&&!failed&&!recovering&&!offline&&active&&playWhenReady&&player!=null&&stream!=null){
   int state=player.getPlaybackState();long position=pos();
   if(state==Player.STATE_BUFFERING||state==Player.STATE_IDLE&&player.getPlayerError()==null){
    if(bufferingSince==0)bufferingSince=now;long waited=now-bufferingSince;
    // A seek or a short dip keeps the picture with a line of text; a real stall gets the KIK waiting screen.
    if(waited>800){boolean seek=prepared&&now-seekAt<3000;loading.setText(!prepared?"Підготовка відео":seek?"Перемотування…":"Зачекайте, завантажуємо відео · "+bufferedPercent());waiting(true,!prepared||!seek&&waited>2000);}
    if(waited>STALL_MS){Log.w("KIK.Player","Stalled "+waited+" ms in state "+state);recover(null);}
   }else if(state==Player.STATE_READY){
    // Jump over an ad clip spliced into a sponsored voice track; a plain seek, nothing is drawn.
    long skip=seeking?-1:PlaybackPolicy.adSkip(ads,position,dur());if(skip>0){Log.d("KIK.Player","Ad skipped "+position+" -> "+skip);resumeAt=skip;seekAt=now;lastMovement=now;player.seekTo(skip);Ui.toast(this,"Рекламу пропущено");return;}
    // A clip only the AI saw is never skipped on its own: the viewer decides with OK.
    showHint(seeking?-1:PlaybackPolicy.adSkip(hints,position,dur()));
    if(!seeking)maybeAsk(position);
    if(position>lastPosition+50||position<lastPosition-500){lastMovement=now;lastPosition=position;recovery.progress(now);if(stableSince==0)stableSince=now;if(now-stableSince>30000){sameAttempt=0;voiceAttempt=0;failedVoices.clear();recoveryStarted=now;stableSince=now;}}
    else if(player.isPlaying()&&now-lastMovement>FROZEN_MS){Log.w("KIK.Player","Picture frozen at "+position);lastMovement=now;recover(null);}
   }
  }
  String item=season+":"+episode;if(!item.equals(askItem)){askItem=item;watchedItem=0;askChecked=false;if(ask!=null&&ask.getVisibility()==View.VISIBLE)hideAsk(false);}
  if(player!=null){long watched=watchTracker.sample(now,prepared?pos():0,prepared&&!seeking&&active&&playWhenReady&&player.isPlaying());watchedPending+=watched;watchedItem+=watched;}
  if(prepared&&player!=null){
   long p=pos(),d=dur();left.setText(Ui.time(p));right.setText(d>0?"-"+Ui.time(d-p):"");
   int w=((View)played.getParent()).getWidth();ViewGroup.LayoutParams lp=played.getLayoutParams();int width=d>0&&w>0?(int)(w*Math.min(1f,(float)p/d)):0;if(lp.width!=width){lp.width=width;played.setLayoutParams(lp);}knob.setTranslationX(Math.max(0,lp.width-dp(8)));
   String icon=playWhenReady?"❚❚":"▶";if(!icon.contentEquals(controls.get(1).getText()))controls.get(1).setText(icon);
   if(!cues.isEmpty()){String txt="";for(Object[] c:cues)if(p>=(Long)c[0]&&p<=(Long)c[1]){txt=(String)c[2];break;}if(!txt.equals(subtitle.getText().toString()))subtitle.setText(txt);}
  }
 }
 /** How much of the 5 s needed to continue is already loaded. */
 private String bufferedPercent(){if(player==null)return "";long ahead=Math.max(0,player.getBufferedPosition()-pos());return Math.min(99,ahead*100/REBUFFER)+"%";}
 private void paint(){
  for(int i=0;i<controls.size();i++){boolean f=menu==null&&!progressFocus&&i==focus;TextView t=controls.get(i);
   if(t instanceof Icon){GradientDrawable o=new GradientDrawable();o.setShape(GradientDrawable.OVAL);o.setColor(f?Color.WHITE:0x26ffffff);if(f)o.setStroke(dp(2),Ui.RED);t.setBackground(o);t.setTextColor(f?Color.BLACK:Color.WHITE);t.animate().scaleX(f?1.08f:1f).scaleY(f?1.08f:1f).setDuration(150).start();t.invalidate();}
   else Ui.paintControl(t,f);}
  for(int i=0;i<options.getChildCount();i++){TextView t=(TextView)options.getChildAt(i);boolean f=i==optFocus;Ui.paintControl(t,f);if(!f&&i==optSelected)t.setBackground(Ui.shape(Ui.RED,dp(24),0,0));if(f){final View target=t;options.post(()->((HorizontalScrollView)options.getParent()).smoothScrollTo(Math.max(0,target.getLeft()-dp(120)),0));}}
  knob.animate().scaleX(progressFocus?1.6f:1f).scaleY(progressFocus?1.6f:1f).setDuration(180).start();
 }

 @Override public boolean dispatchKeyEvent(KeyEvent e){
  int k=e.getKeyCode();
  if(!playerKey(k))return super.dispatchKeyEvent(e);
  if(intro!=null){if(e.getAction()==KeyEvent.ACTION_UP)endIntro();return true;}
  if(k==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)onBackPressed();return true;}
  if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
  switch(k){
   case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:case KeyEvent.KEYCODE_SPACE:toggle();if(!visible)pulse(playWhenReady?"▶":"❚❚");return true;
   case KeyEvent.KEYCODE_MEDIA_PLAY:playWhenReady=true;if(player!=null)player.setPlayWhenReady(wanted());show(true);return true;
   case KeyEvent.KEYCODE_MEDIA_PAUSE:playWhenReady=false;if(player!=null)player.setPlayWhenReady(false);update();show(true);return true;
   case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:seek(10000);pulse("10 »");return true;
   case KeyEvent.KEYCODE_MEDIA_REWIND:seek(-10000);pulse("« 10");return true;
  }
  boolean center=k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER;
  if(failed&&center&&menu==null&&focus==1){autoRetry=0;offline=false;retryNow();return true;}
  // While «Пропустити рекламу» is on screen, OK always means it, whatever the controls have in focus.
  if(center&&hintTo>0&&menu==null&&!failed){skipHint();return true;}
  // «Як вам?» takes ◀ ▶ and OK while the controls are hidden; ▲ ▼ still open them and playback goes on.
  if(askOn&&!visible&&menu==null&&!failed){
   if(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT){askFocus=Math.max(0,Math.min(askChoices.size()-1,askFocus+(k==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)));paintAsk();askCountdown();return true;}
   if(center){answer(askFocus==0?1:askFocus==1?-1:0);return true;}
  }
  if(!visible){
   if(k==KeyEvent.KEYCODE_DPAD_LEFT){seek(-10000);pulse("« 10");return true;}
   if(k==KeyEvent.KEYCODE_DPAD_RIGHT){seek(10000);pulse("10 »");return true;}
   if(center){toggle();pulse(playWhenReady?"▶":"❚❚");show(true);return true;}
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
 /** Remote keys the player handles; any other key (volume, mute, power, home…) keeps its system behaviour. */
 private static boolean playerKey(int key){switch(key){case KeyEvent.KEYCODE_BACK:case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:case KeyEvent.KEYCODE_SPACE:case KeyEvent.KEYCODE_MEDIA_PLAY:case KeyEvent.KEYCODE_MEDIA_PAUSE:case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:case KeyEvent.KEYCODE_MEDIA_REWIND:case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_DPAD_RIGHT:case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:return true;default:return false;}}
 @Override public void onBackPressed(){if(menu!=null){closeMenu();return;}if(visible){show(false);return;}if(askOn){answer(0);return;}save();finish();}
}
