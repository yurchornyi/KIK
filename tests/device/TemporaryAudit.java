package kik.qa;
import android.app.*;import android.app.job.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import org.json.*;import kik.i18n.*;

/**
 * «Додати профіль» and «Тимчасовий вхід», with made-up sign-ins: the add screen shows two whole cards and Back returns to
 * the profiles; another account's sign-in offers the tick box; a temporary account is marked on «Хто дивиться?», counts
 * remote keys as someone there, survives leaving KIK, standby and a restart of the TV, and runs out after an idle hour
 * or 12 hours after its sign-in, also from the background job. When it runs out its profile leaves while the other
 * account stays. The TV's own accounts and profiles are put back afterwards.
 */
public final class TemporaryAudit extends Instrumentation {
 JSONArray checks=new JSONArray();boolean passed=true;Context c;File dir;Class<?> temporary;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(String label,boolean ok)throws Exception{checks.put(new JSONObject().put("name",label).put("passed",ok));passed&=ok;Bundle b=new Bundle();b.putString("stream",(ok?"PASS ":"FAIL ")+label+"\n");sendStatus(0,b);}
 Object call(Class<?> cls,Object on,String name,Class<?>[] types,Object... args)throws Exception{Method m=cls.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(on,args);}
 Object field(Class<?> cls,Object on,String name)throws Exception{Field f=cls.getDeclaredField(name);f.setAccessible(true);return f.get(on);}
 void setField(Class<?> cls,Object on,String name,Object v)throws Exception{Field f=cls.getDeclaredField(name);f.setAccessible(true);f.set(on,v);}
 boolean over()throws Exception{return (Boolean)call(temporary,null,"over",new Class<?>[]{Context.class},c);}
 void shot(String name)throws Exception{SystemClock.sleep(900);Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
 View find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=find(((ViewGroup)v).getChildAt(i),text);if(f!=null)return f;}return null;}
 /** The view and its whole box inside the screen, nothing cut off. */
 boolean whole(View v){if(v==null)return false;int[] at=new int[2];v.getLocationOnScreen(at);android.util.DisplayMetrics m=c.getResources().getDisplayMetrics();return at[0]>=0&&at[1]>=0&&at[0]+v.getWidth()<=m.widthPixels&&at[1]+v.getHeight()<=m.heightPixels&&v.getWidth()>0;}
 static String token(){StringBuilder s=new StringBuilder();Random r=new Random();for(int i=0;i<64;i++)s.append("0123456789abcdef".charAt(r.nextInt(16)));return s.toString();}
 /** Moves a temporary account's sign-in time, as if it had been made then. */
 void setSince(SharedPreferences account,String token,long since)throws Exception{JSONArray a=new JSONArray(account.getString("accounts","[]"));for(int i=0;i<a.length();i++)if(token.equals(a.getJSONObject(i).optString("token")))a.getJSONObject(i).put("since",since);account.edit().putString("accounts",a.toString()).commit();}
 void link(SharedPreferences cloud,String profile,String token,String remote,String name){cloud.edit().putString("token_"+profile,token).putString("remote_"+profile,remote).putString("name_"+profile,name).putLong("offset_"+profile,0).commit();}
 @Override public void onStart(){
  Bundle result=new Bundle();c=getTargetContext();dir=new File(c.getExternalFilesDir(null),"design-audit");dir.mkdirs();
  SharedPreferences account=c.getSharedPreferences("kik_account",0),profiles=c.getSharedPreferences("kik_profiles",0),cloud=c.getSharedPreferences("kik_cloud",0);
  Map<String,?> savedAccount=new HashMap<>(account.getAll()),savedProfiles=new HashMap<>(profiles.getAll()),savedCloud=new HashMap<>(cloud.getAll());
  java.util.concurrent.atomic.AtomicBoolean busy=null;
  try{
   // The server refuses the made-up sign-ins: a background sync would sign both out before the visit's own end.
   // Holding its busy flag keeps background syncs off; the expiry's own upload still runs.
   busy=(java.util.concurrent.atomic.AtomicBoolean)field(CloudSync.class,null,"BUSY");long free=SystemClock.elapsedRealtime()+20000;while(!busy.compareAndSet(false,true)&&SystemClock.elapsedRealtime()<free)SystemClock.sleep(100);
   temporary=Class.forName("kik.i18n.TemporarySignIn");
   String first=token(),second=token();
   // Only the made-up profiles exist while it runs: an ended or refused fake sign-in must never sign out the TV's real ones.
   profiles.edit().putString("list","[]").commit();
   String yura=ProfileStore.create(c,"QA Юра","").id,guest=ProfileStore.create(c,"QA Гість","").id;
   link(cloud,yura,first,"qa-remote-1","QA Юра");link(cloud,guest,second,"qa-remote-2","QA Гість");
   account.edit().clear().putString("accounts",new JSONArray().put(new JSONObject().put("email","first@qa.invalid").put("token",first)).put(new JSONObject().put("email","guest@qa.invalid").put("token",second).put("temporary",true).put("since",System.currentTimeMillis())).toString()).commit();
   profiles.edit().putString("current",yura).commit();
   check("only the guest's sign-in is temporary",((List<?>)call(Account.class,null,"temporaryTokens",new Class<?>[]{Context.class},c)).equals(Collections.singletonList(second)));

   final ProfilesActivity picker=(ProfilesActivity)startActivitySync(new Intent(c,ProfilesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(700);
   View root=picker.getWindow().getDecorView();
   check("«Хто дивиться?» marks the temporary account's profile",find(root,"QA Гість")!=null&&find(root,"Тимчасовий вхід")!=null);
   shot("temporary-picker.png");
   check("KIK's screens pass remote keys through a watcher",Proxy.isProxyClass(picker.getWindow().getCallback().getClass()));
   setField(temporary,null,"active",0L);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(300);
   check("a remote key counts as someone there",System.currentTimeMillis()-(Long)field(temporary,null,"active")<5000);

   // «+ Додати профіль»: two whole cards, the first in focus; Back returns to the profiles.
   runOnMainSync(()->{try{call(ProfilesActivity.class,picker,"addSigned",new Class<?>[0]);}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(900);
   View own=find(root,"Новий профіль"),other=find(root,"Інший обліковий запис");
   check("the add screen offers both ways",own!=null&&other!=null&&find(root,"first@qa.invalid")!=null);
   check("both cards fit the screen whole",own!=null&&other!=null&&whole((View)own.getParent())&&whole((View)other.getParent()));
   check("the new profile card has the focus",own!=null&&((View)own.getParent()).isFocused());
   shot("add-profile.png");
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(500);
   check("▶ moves to the other account",other!=null&&((View)other.getParent()).isFocused());
   shot("add-profile-other.png");
   runOnMainSync(picker::onBackPressed);SystemClock.sleep(600);
   check("Back returns to «Хто дивиться?»",!picker.isFinishing()&&find(root,"Хто дивиться?")!=null&&find(root,"Інший обліковий запис")==null);

   // Another account's sign-in: the tick box turns with OK.
   final Activity signIn=startActivitySync(new Intent(c,SignInActivity.class).putExtra("account",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(900);
   View box=find(signIn.getWindow().getDecorView(),"Тимчасовий вхід");
   check("another account's sign-in offers «Тимчасовий вхід»",box!=null&&whole((View)box.getParent().getParent()));
   View heading=find(signIn.getWindow().getDecorView(),"Інший обліковий запис"),phone=find(signIn.getWindow().getDecorView(),"Увійти з телефону");
   check("the whole first screen fits, from the title to «Увійти з телефону»",whole(heading)&&whole(phone));
   runOnMainSync(()->((View)box.getParent().getParent()).performClick());SystemClock.sleep(300);
   check("OK ticks it",(Boolean)field(SignInActivity.class,signIn,"temporary"));
   shot("temporary-sign-in.png");
   runOnMainSync(signIn::finish);SystemClock.sleep(500);

   // The clock: fresh, closed KIK, standby and a restart keep it; an idle hour or 12 hours end it.
   call(temporary,null,"begin",new Class<?>[]{Context.class},c);
   check("a fresh temporary sign-in is not over",!over());
   long now=System.currentTimeMillis(),idle=(Long)field(temporary,null,"IDLE"),lifetime=(Long)field(temporary,null,"LIFETIME");
   check("an idle hour, 12 hours in all",idle==3_600_000L&&lifetime==12*3_600_000L);
   JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);JobInfo job=jobs.getPendingJob(0x4b4954);
   check("a background job waits for the deadline",job!=null&&job.isPersisted()&&job.getMinLatencyMillis()>idle-60_000&&job.getMinLatencyMillis()<=idle+1000);
   // Leaving KIK with Back used to end the visit at once: now it stays.
   // The picker is still the task's root: starting it again would only bring that task forward, never return.
   runOnMainSync(()->Ui.leaveApp(picker));SystemClock.sleep(1500);
   check("leaving KIK with Back keeps the visit",!over()&&((List<?>)call(Account.class,null,"temporaryTokens",new Class<?>[]{Context.class},c)).size()==1);
   setField(temporary,null,"active",0L);account.edit().putLong("temporary_active",now-idle+5*60_000).putInt("temporary_boot",account.getInt("temporary_boot",0)+1).putLong("temporary_left",now-10*60_000).commit();
   setSince(account,second,now-idle+5*60_000);
   check("55 minutes after closing KIK, after a restart, it is still there",!over());
   account.edit().putLong("temporary_active",now-idle-1000).commit();setSince(account,second,now-idle-1000);
   check("an hour with no one there ends it",over());
   account.edit().putLong("temporary_active",now).commit();setField(temporary,null,"active",now);
   setSince(account,second,now-lifetime-1000);
   check("12 hours after the sign-in end it even while someone is there",over());
   setSince(account,second,now);
   check("used just now and signed in just now, it goes on",!over());

   // The guest is watching when the visit runs out with KIK closed: the background job lets only the guest go.
   profiles.edit().putString("current",guest).commit();
   setField(temporary,null,"active",0L);account.edit().putLong("temporary_active",now-idle-1000).commit();setSince(account,second,now-idle-1000);
   check("KIK is closed and its job is waiting",jobs.getPendingJob(0x4b4954)!=null);
   getUiAutomation().executeShellCommand("cmd jobscheduler run -f "+c.getPackageName()+" "+0x4b4954).close();
   long until=SystemClock.elapsedRealtime()+20000;while(ProfileStore.nameOf(c,guest)!=null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(200);
   runOnMainSync(()->{});// the profile goes and the clock is cleared in one step on the main thread: let it finish
   JSONArray left=(JSONArray)call(Account.class,null,"accounts",new Class<?>[]{Context.class},c);
   check("the temporary account leaves with its profile",ProfileStore.nameOf(c,guest)==null&&left.length()==1&&"first@qa.invalid".equals(left.getJSONObject(0).optString("email")));
   check("the other account stays and its profile becomes current",ProfileStore.nameOf(c,yura)!=null&&CloudSync.linked(c,yura)&&yura.equals(ProfileStore.current(c)));
   check("no temporary sign-in or clock is left",((List<?>)call(Account.class,null,"temporaryTokens",new Class<?>[]{Context.class},c)).isEmpty()&&!account.contains("temporary_active")&&!account.contains("temporary_boot"));
   check("no background job is left",jobs.getPendingJob(0x4b4954)==null);
   SystemClock.sleep(1200);shot("temporary-ended.png");
  }catch(Throwable e){passed=false;Bundle b=new Bundle();b.putString("stream","FAIL "+e+"\n");sendStatus(0,b);}
  finally{
   if(busy!=null)busy.set(false);
   // Fake sign-ins must not outlive the audit: put the TV's accounts, profiles and bindings back exactly.
   for(ProfileStore.Profile p:ProfileStore.list(c))if(p.name.startsWith("QA ")){cloud.edit().remove("token_"+p.id).commit();ProfileStore.delete(c,p.id);}
   AccountsAudit.restore(account,savedAccount);AccountsAudit.restore(profiles,savedProfiles);AccountsAudit.restore(cloud,savedCloud);
   try{c.getSharedPreferences("kik_tv_channels",0).edit().remove("profile").commit();Method m=TvChannels.class.getDeclaredMethod("publish",Context.class,boolean.class);m.setAccessible(true);m.invoke(null,c,true);}catch(Exception ignored){}
  }
  try{java.nio.file.Files.write(new File(dir,"temporary.json").toPath(),new JSONObject().put("passed",passed).put("checks",checks).toString().getBytes("UTF-8"));}catch(Exception ignored){}
  result.putString("passed",String.valueOf(passed));finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
