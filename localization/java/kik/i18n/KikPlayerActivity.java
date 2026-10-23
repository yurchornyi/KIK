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
 private static Site.Film film;private static Site.Voice voice;
 private static final float[] SPEEDS={0.75f,1f,1.25f,1.5f,2f};
 /** Buffer: start after 1.5 s, refill to 5 s after a stall, keep up to 2 min but no more than 48 MB of heap. */
 private static final int MIN_BUFFER=30000,MAX_BUFFER=120000,START_BUFFER=1500,REBUFFER=5000,BUFFER_BYTES=48*1024*1024;
 /** Buffering longer than this, or a picture that does not move, counts as a dead stream. */
 private static final long STALL_MS=30000,FROZEN_MS=15000,RETRY_MS=20000;
 private String season,episode,title,poster,profileId;private Site.Stream stream;private String quality;private float speed=1f;private boolean manualQuality;
 private ExoPlayer player;private SurfaceView surface;private FrameLayout screen;private float aspect;
 private boolean prepared,seeking,failed,destroyed,active=true,playWhenReady=true,offline;private int generation,subGeneration,playGeneration;private long resumeAt;
 private FrameLayout root;private LinearLayout top,bottom,buttons,options;private TextView name,sub,left,right,flash,loading,subtitle;private View played,track,knob;
 private final List<TextView> controls=new ArrayList<TextView>();private int focus=1;private boolean visible,progressFocus;
 private final WatchTracker watchTracker=new WatchTracker();private long watchedPending;
 private final RecoveryPolicy recovery=new RecoveryPolicy();private boolean recovering;private int refreshAttempt,voiceAttempt,sameAttempt,autoRetry;private final Set<String> failedVoices=new HashSet<String>();
 private long lastMovement,lastPosition,recoveryStarted,bufferingSince,seekAt,stableSince;private final List<Long> rebuffers=new ArrayList<Long>();
 private String menu;private int optFocus,optSelected;private List<String> optLabels=new ArrayList<String>();private List<Runnable> optActions=new ArrayList<Runnable>();
 private List<Object[]> cues=new ArrayList<Object[]>();private int subIndex=-1;
 private ConnectivityManager.NetworkCallback network;
 private final Handler h=new Handler(Looper.getMainLooper());
 private final Runnable hide=()->{if(prepared&&!seeking&&playWhenReady&&menu==null)show(false);},tick=new Runnable(){public void run(){update();h.postDelayed(this,500);}},saver=new Runnable(){public void run(){if(active)save();h.postDelayed(this,5000);}},retry=()->{if(!destroyed&&failed)retryNow();};
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
  screen=new FrameLayout(this);root.addView(screen,new FrameLayout.LayoutParams(-1,-1));
  surface=new SurfaceView(this);screen.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
  screen.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)fit();});
  subtitle=Ui.text(this,"",26,Color.WHITE);subtitle.setGravity(Gravity.CENTER);subtitle.setShadowLayer(8,0,2,Color.BLACK);subtitle.setPadding(dp(80),0,dp(80),0);
  FrameLayout.LayoutParams sl=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sl.bottomMargin=dp(48);root.addView(subtitle,sl);
  loading=Ui.text(this,"Підготовка відео…",18,Color.WHITE);loading.setGravity(Gravity.CENTER);loading.setShadowLayer(8,0,2,Color.BLACK);root.addView(loading,new FrameLayout.LayoutParams(dp(680),-2,Gravity.CENTER));
  buildControls();
  player=createPlayer();player.setVideoSurfaceView(surface);
  watchNetwork();
  h.post(tick);h.postDelayed(saver,5000);
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
 @Override protected void onPause(){super.onPause();active=false;save();CloudSync.playing(profileId,false);CloudSync.changed(this);playWhenReady=false;if(player!=null)player.setPlayWhenReady(false);}
 @Override protected void onDestroy(){destroyed=true;TvChannels.stopped(this);CloudSync.playing(profileId,false);generation++;h.removeCallbacksAndMessages(null);if(network!=null)try{((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(network);}catch(Exception ignored){}if(player!=null){player.release();player=null;}super.onDestroy();}

 private long pos(){return player==null?0:Math.max(0,player.getCurrentPosition());}
 /** For the device audits: position, duration and whether frames are advancing right now. */
 public long position(){return pos();}public long duration(){return dur();}public boolean playing(){return player!=null&&player.isPlaying();}
 private long dur(){long d=player==null?C.TIME_UNSET:player.getDuration();return d==C.TIME_UNSET||d<0?0:d;}

 // ---------- playback ----------
 /** Loads the stream for the current voice/episode; keepPosition continues from the same moment. */
 private void start(final boolean fromHistory){
  start(fromHistory,false);
 }
 private void start(final boolean fromHistory,final boolean refreshEpisodes){
  h.removeCallbacks(retry);
  if(!recovering){recovery.reset();failedVoices.clear();refreshAttempt=0;voiceAttempt=0;sameAttempt=0;rebuffers.clear();recoveryStarted=SystemClock.elapsedRealtime();}
  lastMovement=SystemClock.elapsedRealtime();bufferingSince=0;playGeneration++;final int gen=++generation;final Site.Film selectedFilm=film;final Site.Voice selected=voice;final String ss=season,ee=episode;
  final long keep=prepared?pos():resumeAt;if(player!=null)player.setPlayWhenReady(false);if(fromHistory)playWhenReady=true;prepared=false;failed=false;loading.setText(recovering?loading.getText():"Підготовка відео…");loading.setVisibility(View.VISIBLE);
  name.setText(title);paintSub();
  Ui.bg(()->{
   try{
    Site.Film updated=null;if(refreshEpisodes&&selectedFilm.series){Site.Film copy=new Site.Film();copy.id=selectedFilm.id;copy.favs=selectedFilm.favs;copy.origin=selectedFilm.origin;try{Site.episodesFor(copy,selected.id);if(!copy.episodes.isEmpty())updated=copy;}catch(Exception e){Log.w("KIK","Voice episodes",e);}}
    final Site.Film episodeData=updated;
    Site.Voice use=selected;
    if(selectedFilm.series&&ss!=null)try{Site.Voice has=Site.voiceFor(selectedFilm,ss,ee,(refreshEpisodes||recovering)?selected.id:Progress.voice(this,selectedFilm.link));if(has!=null)use=has;}catch(Exception e){Log.w("KIK","Voice coverage",e);}
    final Site.Voice playing=use;
    final boolean rotate=recovering&&refreshAttempt>0;
    final Site.Stream s=Site.stream(selectedFilm,playing,ss,ee,rotate);
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
     subGeneration++;cues.clear();subIndex=-1;subtitle.setText("");
     play();
    });
   }catch(final Exception e){Log.w("KIK","Stream",e);h.post(()->{if(destroyed||gen!=generation)return;recovering=false;if(!online()){offline=true;failure("Немає з'єднання з інтернетом.\nВідео продовжиться, щойно мережа з'явиться.",false);return;}recover(null);});}
  });
 }
 /** Shows the problem; unless the viewer has to choose, retries by itself every 20 s for 10 minutes. */
 private void failure(String message,boolean auto){
  prepared=false;failed=true;focus=1;progressFocus=false;menu=null;options.setVisibility(View.GONE);loading.setText(message);loading.setVisibility(View.VISIBLE);show(true);
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
  DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http);
  MediaItem item=MediaItem.fromUri(url);MediaSource source=hls(url)?new HlsMediaSource.Factory(data).setAllowChunklessPreparation(true).setLoadErrorHandlingPolicy(retries).createMediaSource(item):new ProgressiveMediaSource.Factory(data).setLoadErrorHandlingPolicy(retries).setContinueLoadingCheckIntervalBytes(512*1024).createMediaSource(item);
  Log.d("KIK.Player","Play quality="+quality+" hls="+hls(url)+" at="+resumeAt+" alternatives="+stream.alternatives.get(quality));
  player.setPlaybackSpeed(speed);player.setMediaSource(source,resumeAt);player.setPlayWhenReady(playWhenReady&&active);player.prepare();paintSub();
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
   boolean first=!prepared;prepared=true;failed=false;offline=false;h.removeCallbacks(retry);seeking=false;bufferingSince=0;autoRetry=0;loading.setVisibility(View.GONE);lastMovement=now;lastPosition=pos();
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
  if(expired&&recovery.refresh()){refreshAttempt++;recovering=true;loading.setText("Оновлюємо посилання…");loading.setVisibility(View.VISIBLE);start(false);return;}
  LinkedHashMap<String,List<String>> options=new LinkedHashMap<String,List<String>>();if(stream!=null)for(String q:stream.qualities.keySet()){List<String> urls=stream.alternatives.get(q);options.put(q,urls==null?Arrays.asList(stream.qualities.get(q)):urls);}
  String candidate=recovery.next(options,quality);if(candidate!=null){String[] parts=candidate.split("\n",2);quality=parts[0];stream.qualities.put(quality,parts[1]);loading.setText("Відновлюємо потік…");loading.setVisibility(View.VISIBLE);play();return;}
  if(recovery.refresh()){refreshAttempt++;recovering=true;loading.setText("Шукаємо резервну адресу…");loading.setVisibility(View.VISIBLE);start(false);return;}
  failedVoices.add(voice.id);if(voiceAttempt<1){String language=PlaybackPolicy.lang(voice.name);for(Site.Voice v:film.voices)if(!v.premium&&!v.ads&&!failedVoices.contains(v.id)&&PlaybackPolicy.lang(v.name).equals(language)){voice=v;voiceAttempt++;recovering=true;start(false);return;}}
  failure("Відео тимчасово недоступне. Пробуємо ще раз автоматично…\nOK — повторити зараз або виберіть іншу мову.",true);
 }
 /** Re-open the same address at the same moment; ExoPlayer keeps the decoders and continues where it stopped. */
 private void retrySame(){
  if(player==null||stream==null){retryNow();return;}
  failed=false;loading.setText("Відновлюємо з'єднання…");loading.setVisibility(View.VISIBLE);bufferingSince=0;lastMovement=SystemClock.elapsedRealtime();
  Log.d("KIK.Player","Retry same address at "+resumeAt+" attempt="+sameAttempt);
  if(player.getPlayerError()!=null){player.seekTo(resumeAt);player.setPlayWhenReady(playWhenReady&&active);player.prepare();}else play();
 }
 private String pickQuality(Site.Stream s){
  String want=getSharedPreferences("settings",0).getString("default_quality","1080p");int target=num(want);String best=null;int bestN=-1;
  for(String q:s.qualities.keySet()){if(q.equals(want))return q;int n=num(q);if(n<=target+1&&n>bestN){best=q;bestN=n;}}
  if(best==null)for(String q:s.qualities.keySet())best=q;return best;
 }
 private static int num(String q){Matcher m=Pattern.compile("(\\d+)").matcher(q==null?"":q);int n=m.find()?Integer.parseInt(m.group(1)):0;if(q!=null&&q.toLowerCase(Locale.ROOT).contains("ultra"))n+=1;if(q!=null&&(q.contains("2K")||q.contains("2k")))n=1440;if(q!=null&&(q.contains("4K")||q.contains("4k")))n=2160;return n;}
 private void applySpeed(){if(player!=null)player.setPlaybackSpeed(speed);Log.d("KIK.Player","Speed="+speed);}
 private void seek(long delta){if(!prepared||player==null)return;long d=dur(),t=Math.max(0,pos()+delta);if(d>0)t=Math.min(Math.max(0,d-1500),t);resumeAt=t;seekAt=SystemClock.elapsedRealtime();lastMovement=seekAt;Log.d("KIK.Player","Seek requested="+t);player.seekTo(t);update();}
 private void toggle(){if(!prepared||player==null)return;playWhenReady=!playWhenReady;player.setPlayWhenReady(playWhenReady&&active);lastMovement=SystemClock.elapsedRealtime();update();show(true);Log.d("KIK.Player","Playing="+playWhenReady+" position="+pos());}
 private void onEnded(){
  save();Site.Episode n=film.series?next(film,season,episode):null;
  boolean auto=getSharedPreferences("settings",0).getBoolean("auto_play_next_episode",true);
  if(n!=null&&auto){season=n.season;episode=n.episode;start(true);}else finish();
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
  if(film.series)addControl("Наступна серія",()->{if(!prepared&&!failed)return;Site.Episode n=next(film,season,episode);if(n!=null){save();season=n.season;episode=n.episode;start(true);}else Ui.toast(this,"Це остання серія");});
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
  for(final String q:stream.qualities.keySet()){if(q.equals(quality))sel=l.size();l.add(q);a.add(()->{if(q.equals(quality))return;resumeAt=prepared?pos():resumeAt;prepared=false;quality=q;manualQuality=true;rebuffers.clear();paintSub();loading.setText("Підготовка відео…");loading.setVisibility(View.VISIBLE);play();});}
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
  long now=SystemClock.elapsedRealtime();
  if(!destroyed&&!failed&&!recovering&&!offline&&active&&playWhenReady&&player!=null&&stream!=null){
   int state=player.getPlaybackState();long position=pos();
   if(state==Player.STATE_BUFFERING||state==Player.STATE_IDLE&&player.getPlayerError()==null){
    if(bufferingSince==0)bufferingSince=now;long waited=now-bufferingSince;
    if(waited>800){loading.setText(!prepared?"Підготовка відео…":now-seekAt<3000?"Перемотування…":"Завантаження… "+bufferedPercent());loading.setVisibility(View.VISIBLE);}
    if(waited>STALL_MS){Log.w("KIK.Player","Stalled "+waited+" ms in state "+state);recover(null);}
   }else if(state==Player.STATE_READY){
    if(position>lastPosition+50||position<lastPosition-500){lastMovement=now;lastPosition=position;recovery.progress(now);if(stableSince==0)stableSince=now;if(now-stableSince>30000){sameAttempt=0;voiceAttempt=0;failedVoices.clear();recoveryStarted=now;stableSince=now;}}
    else if(player.isPlaying()&&now-lastMovement>FROZEN_MS){Log.w("KIK.Player","Picture frozen at "+position);lastMovement=now;recover(null);}
   }
  }
  if(player!=null)watchedPending+=watchTracker.sample(now,prepared?pos():0,prepared&&!seeking&&active&&playWhenReady&&player.isPlaying());
  if(prepared&&player!=null){
   long p=pos(),d=dur();left.setText(Ui.time(p));right.setText(d>0?"-"+Ui.time(d-p):"");
   int w=((View)played.getParent()).getWidth();ViewGroup.LayoutParams lp=played.getLayoutParams();lp.width=d>0&&w>0?(int)(w*Math.min(1f,(float)p/d)):0;played.setLayoutParams(lp);knob.setTranslationX(Math.max(0,lp.width-dp(8)));
   controls.get(1).setText(playWhenReady?"❚❚":"▶");
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
  if(k==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)onBackPressed();return true;}
  if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
  switch(k){
   case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:case KeyEvent.KEYCODE_SPACE:toggle();if(!visible)pulse(playWhenReady?"▶":"❚❚");return true;
   case KeyEvent.KEYCODE_MEDIA_PLAY:playWhenReady=true;if(player!=null)player.setPlayWhenReady(active);show(true);return true;
   case KeyEvent.KEYCODE_MEDIA_PAUSE:playWhenReady=false;if(player!=null)player.setPlayWhenReady(false);update();show(true);return true;
   case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:seek(10000);pulse("10 »");return true;
   case KeyEvent.KEYCODE_MEDIA_REWIND:seek(-10000);pulse("« 10");return true;
  }
  boolean center=k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER;
  if(failed&&center&&menu==null&&focus==1){autoRetry=0;offline=false;retryNow();return true;}
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
 @Override public void onBackPressed(){if(menu!=null){closeMenu();return;}if(visible){show(false);return;}save();finish();}
}
