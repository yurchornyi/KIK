package kik.i18n;
import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;

/**
 * «Тимчасовий вхід»: an account added for a visit leaves this TV by itself. It goes at once when the viewer leaves KIK
 * with Back, 5 minutes after KIK went to the background (Home, another app, standby), after 30 minutes with no remote
 * key and nothing playing, and when the TV restarts. The times are kept on disk: a process killed meanwhile ends the
 * visit the next time KIK opens.
 */
final class TemporarySignIn {
 static final long IDLE=30*60_000L,AWAY=5*60_000L;
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static Context app;private static boolean installed;private static int started;private static long active,written;
 private static WeakReference<Activity> top=new WeakReference<Activity>(null);
 private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences("kik_account",0);}

 /** Watches KIK's screens come and go; called once from the Application's start. */
 static void install(Context context){
  final Context c=context.getApplicationContext();if(installed||!(c instanceof Application))return;installed=true;app=c;
  ((Application)c).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
   @Override public void onActivityCreated(Activity a,Bundle b){keys(a);}
   @Override public void onActivityStarted(Activity a){top=new WeakReference<Activity>(a);if(started++==0)returned(c);}
   @Override public void onActivityResumed(Activity a){top=new WeakReference<Activity>(a);}
   @Override public void onActivityPaused(Activity a){}
   @Override public void onActivityStopped(Activity a){if(started>0&&--started==0)left(c);}
   @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
   @Override public void onActivityDestroyed(Activity a){if(top.get()==a)top=new WeakReference<Activity>(null);}
  });
 }
 /** Every remote key or touch on KIK's own screens counts as someone being there. */
 private static void keys(final Activity a){
  if(!a.getClass().getName().startsWith("kik."))return;Window w=a.getWindow();final Window.Callback inner=w==null?null:w.getCallback();if(inner==null)return;
  w.setCallback((Window.Callback)Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},(proxy,m,args)->{
   if(args!=null&&args.length==1&&(args[0] instanceof KeyEvent||args[0] instanceof MotionEvent))touch(a);
   try{return m.invoke(inner,args);}catch(InvocationTargetException e){throw e.getCause();}
  }));
 }
 /** Someone is there: a key on the remote, or a title playing (the player calls this while it plays). */
 static void touch(Context c){
  long now=System.currentTimeMillis();active=now;if(now-written<60_000)return;written=now;
  if(!Account.temporaryTokens(c).isEmpty())prefs(c).edit().putLong("temporary_active",now).putInt("temporary_boot",boot(c)).apply();
 }
 /** A temporary account was just signed in: its clock starts now. */
 static void begin(Context c){long now=System.currentTimeMillis();active=written=now;prefs(c).edit().putLong("temporary_active",now).putLong("temporary_left",0).putInt("temporary_boot",boot(c)).commit();idle();}
 /** Back on the home screen leaves KIK: the visit is over. */
 static void leaving(Context c){if(!Account.temporaryTokens(c).isEmpty())end(c);}

 private static void returned(Context c){
  MAIN.removeCallbacks(AWAY_CHECK);if(Account.temporaryTokens(c).isEmpty())return;
  if(over(c)){end(c);return;}
  long now=System.currentTimeMillis();active=written=now;prefs(c).edit().putLong("temporary_left",0).putLong("temporary_active",now).putInt("temporary_boot",boot(c)).apply();idle();
 }
 private static void left(Context c){
  MAIN.removeCallbacks(IDLE_CHECK);if(Account.temporaryTokens(c).isEmpty())return;
  prefs(c).edit().putLong("temporary_left",System.currentTimeMillis()).putLong("temporary_active",Math.max(active,prefs(c).getLong("temporary_active",0))).putInt("temporary_boot",boot(c)).commit();
  MAIN.postDelayed(AWAY_CHECK,AWAY+1000);
 }
 private static void idle(){MAIN.removeCallbacks(IDLE_CHECK);MAIN.postDelayed(IDLE_CHECK,60_000);}
 private static final Runnable AWAY_CHECK=()->{if(started==0&&app!=null&&over(app))end(app);};
 private static final Runnable IDLE_CHECK=new Runnable(){public void run(){if(started==0||app==null||Account.temporaryTokens(app).isEmpty())return;if(over(app)){end(app);return;}MAIN.postDelayed(this,60_000);}};
 /**
  * The visit is over after a restart of the TV, too long without anyone, or too long away from KIK. A clock that went
  * back a lot (a TV that booted with a wrong date) cannot tell how long it was, so it ends the visit too.
  */
 static boolean over(Context c){
  SharedPreferences p=prefs(c);long now=System.currentTimeMillis(),last=Math.max(active,p.getLong("temporary_active",0)),gone=p.getLong("temporary_left",0);int boot=p.getInt("temporary_boot",-1),current=boot(c);
  if(boot>=0&&current>=0&&boot!=current)return true;
  if(last>0&&(now-last>=IDLE||now-last<-60_000))return true;
  return gone>0&&(now-gone>=AWAY||now-gone<-60_000);
 }
 private static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
 /** Signs every temporary account out now; see {@link Account#expire}. */
 static void end(Context c){MAIN.removeCallbacks(IDLE_CHECK);MAIN.removeCallbacks(AWAY_CHECK);for(String token:Account.temporaryTokens(c))Account.expire(c,token);}

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
