package kik.i18n;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;

/**
 * «Тимчасовий вхід»: an account added for a visit leaves this TV by itself after {@link TemporaryPolicy#IDLE} with
 * nobody using KIK, or {@link TemporaryPolicy#LIFETIME} after its sign-in. Closing KIK, standby and a restart of the TV
 * keep it. The times are on disk and checked when KIK starts or comes back, while it is open, and by a background job
 * at the moment the visit runs out, so a TV left with KIK closed lets the visitor go on time too.
 */
public final class TemporarySignIn {
 static final long IDLE=TemporaryPolicy.IDLE,LIFETIME=TemporaryPolicy.LIFETIME;
 private static final int JOB=0x4b4954;
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static Context app;private static boolean installed;private static int started;private static long active,written;
 private static WeakReference<Activity> top=new WeakReference<Activity>(null);
 private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences("kik_account",0);}

 /** Watches KIK's screens come and go and checks the clock; called once from the Application's start. */
 static void install(Context context){
  final Context c=context.getApplicationContext();if(installed||!(c instanceof Application))return;installed=true;app=c;
  ((Application)c).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
   @Override public void onActivityCreated(Activity a,Bundle b){keys(a);}
   @Override public void onActivityStarted(Activity a){top=new WeakReference<Activity>(a);if(started++==0)returned(c);}
   @Override public void onActivityResumed(Activity a){top=new WeakReference<Activity>(a);touch(a);}
   @Override public void onActivityPaused(Activity a){}
   @Override public void onActivityStopped(Activity a){if(started>0&&--started==0)left(c);}
   @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
   @Override public void onActivityDestroyed(Activity a){if(top.get()==a)top=new WeakReference<Activity>(null);}
  });
  // A process started for a job or the launcher's channels checks too; an opening screen checks in returned().
  MAIN.post(()->{if(started==0&&!Account.temporaryTokens(c).isEmpty())check(c,null);});
 }
 /** Every remote key or touch on KIK's own screens counts as someone being there. */
 private static void keys(final Activity a){
  if(!a.getClass().getName().startsWith("kik."))return;Window w=a.getWindow();final Window.Callback inner=w==null?null:w.getCallback();if(inner==null)return;
  w.setCallback((Window.Callback)Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},(proxy,m,args)->{
   if(args!=null&&args.length==1&&(args[0] instanceof KeyEvent||args[0] instanceof MotionEvent))touch(a);
   try{return m.invoke(inner,args);}catch(InvocationTargetException e){throw e.getCause();}
  }));
 }
 /** Someone is there: a key on the remote, a screen opening, or a title playing (the player calls this while it plays). */
 static void touch(Context c){
  long now=System.currentTimeMillis();active=now;if(now-written<60_000)return;written=now;
  if(!Account.temporaryTokens(c).isEmpty())prefs(c).edit().putLong("temporary_active",now).apply();
 }
 /** A temporary account was just signed in ({@link Account#save} noted when): its idle clock starts now. */
 static void begin(Context c){long now=System.currentTimeMillis();active=written=now;prefs(c).edit().putLong("temporary_active",now).remove("temporary_left").remove("temporary_boot").commit();plan(c);}

 /** KIK is on screen again: a visit that ran out meanwhile ends now, otherwise coming back counts as use. */
 private static void returned(Context c){
  if(Account.temporaryTokens(c).isEmpty())return;
  if(!due(c).isEmpty()){check(c,null);return;}
  long now=System.currentTimeMillis();active=written=now;prefs(c).edit().putLong("temporary_active",now).apply();plan(c);
 }
 /** KIK left the screen (Back, Home, another app, standby): the last use is saved and the job waits for the deadline. */
 private static void left(Context c){
  if(Account.temporaryTokens(c).isEmpty())return;
  prefs(c).edit().putLong("temporary_active",last(c)).commit();plan(c);
 }
 private static long last(Context c){return Math.max(active,prefs(c).getLong("temporary_active",0));}
 /** The temporary sign-ins whose visit is over now. */
 static List<String> due(Context c){
  long now=System.currentTimeMillis(),last=last(c);List<String> out=new ArrayList<String>();
  for(String token:Account.temporaryTokens(c))if(TemporaryPolicy.over(now,Account.since(c,token),last))out.add(token);
  return out;
 }
 static boolean over(Context c){return !due(c).isEmpty();}
 /** The first moment one of the visits runs out if nobody uses KIK; 0 without any. */
 static long deadline(Context c){
  long last=last(c),first=0;
  for(String token:Account.temporaryTokens(c)){long d=TemporaryPolicy.deadline(Account.since(c,token),last);if(d>0&&(first==0||d<first))first=d;}
  return first;
 }

 private static final Runnable CHECK=()->{if(app!=null)check(app,null);};
 /** Ends the visits that are over and plans the next check; {@code done} runs once the leaving accounts are gone. */
 static void check(Context c,Runnable done){
  List<String> over=due(c);
  if(!over.isEmpty()){end(c,over,done);return;}
  plan(c);if(done!=null)done.run();
 }
 /**
  * Wakes up at the next deadline: a timer while KIK's process lives, and a persisted job for when it does not (closed
  * KIK, standby, a restart). The timer also looks again every 15 minutes in case the clock was set meanwhile.
  */
 private static void plan(Context c){
  MAIN.removeCallbacks(CHECK);Context a=c.getApplicationContext();JobScheduler jobs=null;
  try{jobs=(JobScheduler)a.getSystemService(Context.JOB_SCHEDULER_SERVICE);}catch(Exception ignored){}
  long due=deadline(a);
  if(due==0){if(jobs!=null)jobs.cancel(JOB);return;}
  long wait=Math.max(0,due-System.currentTimeMillis())+1000;
  MAIN.postDelayed(CHECK,Math.min(wait,15*TemporaryPolicy.MINUTE));
  if(jobs!=null)try{jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(a,Job.class)).setMinimumLatency(wait).setOverrideDeadline(wait+15*TemporaryPolicy.MINUTE).setPersisted(true).build());}
  catch(Exception e){Log.w("KIK.Temporary","Schedule unavailable",e);}
 }
 /** Signs every temporary account out now. */
 static void end(Context c){end(c,Account.temporaryTokens(c),null);}
 /** Signs these temporary accounts out (see {@link Account#expire}); {@code done} runs after the last one. */
 private static void end(final Context c,List<String> tokens,final Runnable done){
  MAIN.removeCallbacks(CHECK);final int[] waiting={tokens.size()};
  if(tokens.isEmpty()){plan(c);if(done!=null)done.run();return;}
  Runnable each=()->{if(--waiting[0]==0){plan(c);if(done!=null)done.run();}};
  for(String token:tokens)Account.expire(c,token,each);
 }

 /** The background end of a visit: runs at the deadline with KIK closed or the TV in standby. */
 public static final class Job extends JobService {
  @Override public boolean onStartJob(JobParameters p){install(getApplicationContext());check(getApplicationContext(),()->jobFinished(p,false));return true;}
  @Override public boolean onStopJob(JobParameters p){return false;}
 }

 /** «1 год», «45 хв»: how the sign-in screen states the rules. */
 static String span(long ms){return ms%3_600_000L==0?ms/3_600_000L+" год":ms/60_000L+" хв";}

 /**
  * One temporary account has left. On screen the viewer is told and picks a profile again if they were watching one of
  * its profiles; in the background KIK's screens close, and it opens afresh with «Хто дивиться?».
  */
 static void ended(Context c,String email,boolean watching){
  if(Account.temporaryTokens(c).isEmpty())prefs(c).edit().remove("temporary_active").remove("temporary_left").remove("temporary_boot").apply();
  boolean signedIn=Account.signedIn(c);Activity shown=top.get();
  if(started>0){
   Toast.makeText(c,"Тимчасовий вхід завершено"+(email.isEmpty()?"":": "+email+" вийшов з TV"),Toast.LENGTH_LONG).show();
   if(!signedIn)c.startActivity(new Intent(c,SignInActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
   else if(watching)Account.reopen(c);
   else if(shown instanceof ProfilesActivity)((ProfilesActivity)shown).refresh();
  }else if(watching||!signedIn){ProfileStore.picked=false;if(shown!=null)shown.finishAffinity();}
  else if(shown instanceof ProfilesActivity)((ProfilesActivity)shown).refresh();
 }
 private TemporarySignIn(){}
}
