package kik.i18n;
import android.content.*;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import java.util.*;
import java.util.concurrent.*;

/**
 * What this player plays, told straight to the TV Home launcher (com.gathin.tvhome) for its Now Playing card: title,
 * season and episode, poster, the TMDB 16:9 backdrop, position and length. Android hides media sessions from the
 * launcher on a low-RAM TV (TCL), so the card cannot read them there; this broadcast reaches it on any TV. It goes to
 * that one package only. The launcher's Continue watching starts the report's `open` intent: the player still open
 * comes to the front and plays on (OpenTitleActivity), else the title opens playing from where it was left.
 */
final class LauncherReport {
 static final String LAUNCHER="com.gathin.tvhome",NOW_PLAYING="com.gathin.tvhome.action.NOW_PLAYING",RESUME="kik.intent.action.RESUME",CONTINUE="kik.continue";
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->new Thread(r,"kik-launcher"));
 private LauncherReport(){}

 /** Left in the player: what plays and where it stopped. The backdrop lookup is cached by TvChannels. */
 static void playing(Context context,String link,String title,String original,String year,String subtitle,String poster,String profile,String season,String episode,long position,long duration){
  Context c=context.getApplicationContext();long at=System.currentTimeMillis();
  IO.execute(()->{
   String backdrop="";
   try{backdrop=TvChannels.backdrops(c,Collections.singletonList(link),Collections.singletonList(title)).getOrDefault(Progress.norm(link),"");}
   catch(Exception e){Log.w("KIK","Launcher backdrop",e);}
   // How Continue watching comes back here: this player if it is still open, else the title playing from its place.
   Intent open=new Intent(OpenTitleActivity.ACTION).setClassName(c.getPackageName(),OpenTitleActivity.class.getName()).putExtra(android.app.SearchManager.QUERY,title)
    .putExtra(OpenTitleActivity.LINK,link).putExtra(OpenTitleActivity.POSTER,poster).putExtra(OpenTitleActivity.PROFILE,profile).putExtra(OpenTitleActivity.PLAY,true).putExtra(CONTINUE,true);
   if(season!=null&&episode!=null)open.putExtra(OpenTitleActivity.SEASON,season).putExtra(OpenTitleActivity.EPISODE,episode);
   send(c,new Intent(NOW_PLAYING).putExtra("open",open.toUri(Intent.URI_INTENT_SCHEME)).putExtra("state","paused").putExtra("title",title==null?"":title).putExtra("original",original==null?"":original).putExtra("year",year==null?"":year).putExtra("subtitle",subtitle==null?"":subtitle)
    .putExtra("poster",poster==null?"":poster).putExtra("backdrop",backdrop).putExtra("position",position).putExtra("duration",duration).putExtra("at",at));
  });
 }

 /** The player closed: nothing to go back to. */
 static void stopped(Context context){Context c=context.getApplicationContext();IO.execute(()->send(c,new Intent(NOW_PLAYING).putExtra("state","stopped").putExtra("at",System.currentTimeMillis())));}

 private static void send(Context c,Intent i){
  try{c.sendBroadcast(i.setPackage(LAUNCHER).putExtra("package",c.getPackageName()));}catch(Exception e){Log.w("KIK","Launcher report",e);}
 }

 /** RESUME from the launcher, for the player that is open: `play` runs on the main thread. */
 static BroadcastReceiver listen(Context c,Runnable play){
  BroadcastReceiver r=new BroadcastReceiver(){@Override public void onReceive(Context x,Intent i){asked=SystemClock.elapsedRealtime();play.run();}};
  IntentFilter f=new IntentFilter(RESUME);
  if(Build.VERSION.SDK_INT>=33)c.registerReceiver(r,f,Context.RECEIVER_EXPORTED);else c.registerReceiver(r,f);
  return r;
 }
 private static long asked=-1;
 /** Continue watching reached the open player another way (OpenTitleActivity): it plays as it comes to the front. */
 static void ask(){asked=SystemClock.elapsedRealtime();}
 /** RESUME came a moment ago, maybe before the player was back in front: it plays as soon as it is. */
 static boolean askedLately(){return asked>0&&SystemClock.elapsedRealtime()-asked<8000;}
 static void forget(){asked=-1;}
}
