package kik.qa;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.text.Layout;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import org.json.*;import kik.i18n.*;

/**
 * The home hero's description reads whole on every slide: it fits its two lines, nothing is cut by the view, and it ends
 * on a sentence (a word cut with «…» only when no sentence fits). Runs on the real catalogue with a made-up profile and
 * only that profile in the list; the TV's own accounts and profiles are put back afterwards.
 */
public final class HeroAudit extends Instrumentation {
 JSONArray checks=new JSONArray();boolean passed=true;Context c;File dir;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(String label,boolean ok)throws Exception{checks.put(new JSONObject().put("name",label).put("passed",ok));passed&=ok;Bundle b=new Bundle();b.putString("stream",(ok?"PASS ":"FAIL ")+label+"\n");sendStatus(0,b);}
 Object field(Object on,String name)throws Exception{Field f=on.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(on);}
 <T>T ui(java.util.concurrent.Callable<T> f){Object[] v=new Object[2];runOnMainSync(()->{try{v[0]=f.call();}catch(Throwable e){v[1]=e;}});if(v[1]!=null)throw new RuntimeException((Throwable)v[1]);return (T)v[0];}
 void shot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
 @Override public void onStart(){
  Bundle result=new Bundle();c=getTargetContext();dir=new File(c.getExternalFilesDir(null),"design-audit");dir.mkdirs();
  SharedPreferences account=c.getSharedPreferences("kik_account",0),profiles=c.getSharedPreferences("kik_profiles",0),cloud=c.getSharedPreferences("kik_cloud",0);
  Map<String,?> savedAccount=new HashMap<>(account.getAll()),savedProfiles=new HashMap<>(profiles.getAll()),savedCloud=new HashMap<>(cloud.getAll());
  java.util.concurrent.atomic.AtomicBoolean busy=null;Activity home=null;
  try{
   // A made-up sign-in: the catalogue and saved synopses are public; background syncs, which the server would refuse, stay off.
   Field f=CloudSync.class.getDeclaredField("BUSY");f.setAccessible(true);busy=(java.util.concurrent.atomic.AtomicBoolean)f.get(null);
   long free=SystemClock.elapsedRealtime()+20000;while(!busy.compareAndSet(false,true)&&SystemClock.elapsedRealtime()<free)SystemClock.sleep(100);
   profiles.edit().putString("list","[]").commit();
   String qa=ProfileStore.create(c,"QA Hero","").id,token=AccountsAudit.token();
   cloud.edit().putString("token_"+qa,token).putString("remote_"+qa,"qa-hero").putString("name_"+qa,"QA Hero").putLong("offset_"+qa,0).commit();
   account.edit().clear().putString("accounts",new JSONArray().put(new JSONObject().put("email","hero@qa.invalid").put("token",token)).toString()).commit();
   profiles.edit().putString("current",qa).commit();ProfileStore.completeTasteSetup(c,qa);ProfileStore.picked=true;
   home=startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final Activity h=home;
   final TextView text=(TextView)field(h,"heroText");
   long until=SystemClock.elapsedRealtime()+40000;while(SystemClock.elapsedRealtime()<until&&ui(()->((List<?>)field(h,"slides")).size())<2)SystemClock.sleep(500);
   int slides=ui(()->((List<?>)field(h,"slides")).size());check("the hero has slides ("+slides+")",slides>=2);
   Method show=h.getClass().getDeclaredMethod("showSlide",int.class);show.setAccessible(true);
   for(int n=0;n<Math.min(slides,8);n++){final int at=n;
    // A refreshed catalogue may bring fewer slides while the audit runs.
    if(!ui(()->{if(at>=((List<?>)field(h,"slides")).size())return false;show.invoke(h,at);return true;}))break;
    // The description and a synopsis may arrive after the slide: wait until the text holds still for 3 s.
    String last="";long stable=SystemClock.elapsedRealtime(),end=SystemClock.elapsedRealtime()+20000;
    while(SystemClock.elapsedRealtime()<end){String now=ui(()->text.getText().toString());if(!now.equals(last)){last=now;stable=SystemClock.elapsedRealtime();}else if(!now.isEmpty()&&SystemClock.elapsedRealtime()-stable>3000)break;SystemClock.sleep(250);}
    final String shown=last;Layout l=ui(text::getLayout);int lines=l==null?99:l.getLineCount(),cut=l==null?0:l.getEllipsisCount(Math.max(0,lines-1));
    boolean sentence=shown.trim().matches("(?s).*[.!?…][»\"”)]?$");
    String title=ui(()->((TextView)field(h,"heroTitle")).getText().toString());
    Bundle b=new Bundle();b.putString("stream","  "+title+": "+shown+"\n");sendStatus(0,b);
    check("slide "+(n+1)+" fits two lines whole",!shown.isEmpty()&&lines<=2&&cut==0);
    check("slide "+(n+1)+" ends on a sentence or a marked cut",sentence);
    // «…» only when no sentence was there to end on: a whole one, however short, is shown instead.
    check("slide "+(n+1)+" is cut only when no sentence fits",!shown.endsWith("…")||!shown.matches("(?s).*[.!?] .*"));
    if(n<3)shot("hero-"+(n+1)+".png");
   }
  }catch(Throwable e){passed=false;Bundle b=new Bundle();b.putString("stream","FAIL "+e+"\n");sendStatus(0,b);}
  finally{
   if(home!=null){final Activity h=home;runOnMainSync(h::finish);}
   for(ProfileStore.Profile p:ProfileStore.list(c))if(p.name.startsWith("QA ")){cloud.edit().remove("token_"+p.id).commit();ProfileStore.delete(c,p.id);}
   AccountsAudit.restore(account,savedAccount);AccountsAudit.restore(profiles,savedProfiles);AccountsAudit.restore(cloud,savedCloud);
   if(busy!=null)busy.set(false);
  }
  try{java.nio.file.Files.write(new File(dir,"hero.json").toPath(),new JSONObject().put("passed",passed).put("checks",checks).toString().getBytes("UTF-8"));}catch(Exception ignored){}
  result.putString("passed",String.valueOf(passed));finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
