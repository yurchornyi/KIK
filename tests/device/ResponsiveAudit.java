package kik.qa;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import org.json.*;
import kik.i18n.*;

/** Checks real measured geometry and D-pad reachability at the current TV size/density. */
public final class ResponsiveAudit extends Instrumentation {
 private Context context;private String original,qa,label;private Bundle arguments;
 private final List<Activity> activities=new ArrayList<>();private final JSONArray checks=new JSONArray();private boolean passed=true;
 @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
 private <T>T ui(Callable<T> action){Object[] result=new Object[2];runOnMainSync(()->{try{result[0]=action.call();}catch(Throwable e){result[1]=e;}});if(result[1]!=null)throw new RuntimeException((Throwable)result[1]);return (T)result[0];}
 private void check(String name,boolean ok,String details)throws Exception{checks.put(new JSONObject().put("name",name).put("passed",ok).put("details",details));if(!ok)passed=false;Bundle b=new Bundle();b.putString("stream",(ok?"PASS ":"FAIL ")+name+" "+details+"\n");sendStatus(0,b);}
 private boolean await(BooleanSupplier action,long timeout){long end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){try{if(action.getAsBoolean())return true;}catch(Exception ignored){}SystemClock.sleep(80);}return false;}
 private Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
 private Activity launch(Class<? extends Activity> type,Intent extras)throws Exception{long start=SystemClock.elapsedRealtime();Intent i=new Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(extras!=null)i.putExtras(extras);if(!activities.isEmpty()){ui(()->{for(Activity previous:activities)previous.finish();return null;});activities.clear();}Activity a=startActivitySync(i);activities.add(a);ui(()->{a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);return null;});waitForIdleSync();await(()->ui(()->a.hasWindowFocus()),5000);SystemClock.sleep(400);check(type.getSimpleName()+" opens",true,(SystemClock.elapsedRealtime()-start)+"ms");return a;}
 private List<View> views(View v){List<View> out=new ArrayList<>();out.add(v);if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)out.addAll(views(group.getChildAt(i)));}return out;}
 private void geometry(Activity activity,String name)throws Exception{
  snapshot(name+"-initial");List<View> nodes=ui(()->views(activity.getWindow().getDecorView()));int count=0;
  for(View node:nodes){if(!ui(()->node.isShown()&&node.isFocusable()&&node.isEnabled()))continue;
   if(!ui(node::requestFocus))continue;SystemClock.sleep(200);
   final String text=ui(()->node instanceof TextView?((TextView)node).getText().toString():String.valueOf(node.getContentDescription()));
   String problem=ui(()->{int[] location=new int[2];node.getLocationOnScreen(location);int width=activity.getResources().getDisplayMetrics().widthPixels;Rect visible=new Rect();boolean shown=node.getGlobalVisibleRect(visible);if(!shown||location[0]<0||location[0]+node.getWidth()>width+2)return "offscreen x="+location[0]+" width="+node.getWidth()+" screen="+width;
    if(node instanceof TextView){TextView t=(TextView)node;if(t.getLayout()!=null&&t.getLineCount()==1&&t.getLayout().getLineWidth(0)>t.getWidth()-t.getCompoundPaddingLeft()-t.getCompoundPaddingRight()+2&&t.getEllipsize()==null)return "unhandled text overflow";}return "";});
   check(name+" focus: "+text,problem.isEmpty(),problem);count++;if(count>=(name.equals("settings")?45:20))break;
  }
  check(name+" focusable actions",count>0,"count="+count);
  for(int key:new int[]{KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP}){sendKeyDownUpSync(key);SystemClock.sleep(100);check(name+" D-pad "+key,ui(()->activity.getCurrentFocus()!=null),"");}
  snapshot(name);
 }
 private void snapshot(String name)throws Exception{File dir=new File(context.getExternalFilesDir(null),"ui-audit");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,label+"-"+name+".png"))){Bitmap shot=getUiAutomation().takeScreenshot();try{shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}}}
 @Override public void onStart(){context=getTargetContext();original=ProfileStore.current(context);label=arguments.getString("label","tv");
  // Recover only this suite's disposable profile after a disconnected instrumentation session.
  for(ProfileStore.Profile profile:ProfileStore.list(context)){
   boolean own=(profile.name.equals("QA long profile for responsive audit")&&ProfileStore.checkPassword(profile,"audit"))||(profile.name.equals("QA Local Player")&&ProfileStore.checkPassword(profile,"Local6789"))||(profile.name.equals("QA Loading")&&ProfileStore.checkPassword(profile,"audit"));
   if(!own||ProfileStore.FIRST.equals(profile.id))continue;
   if(profile.id.equals(original))for(ProfileStore.Profile other:ProfileStore.list(context))if(!other.name.startsWith("QA ")){original=other.id;break;}
   context.getSharedPreferences("kik_profiles",0).edit().putString("current",original).commit();ProfileStore.delete(context,profile.id);
  }
  try{
   if("true".equals(arguments.getString("cleanupOnly"))){check("original profile selected",ProfileStore.nameOf(context,original)!=null&&!ProfileStore.nameOf(context,original).startsWith("QA "),"");return;}
   qa=ProfileStore.create(context,"QA long profile for responsive audit","audit").id;
   context.getSharedPreferences("kik_profiles",0).edit().putString("current",qa).commit();ProfileStore.picked=true;ProfileStore.completeTasteSetup(context,qa);
   if("settings".equals(arguments.getString("screen"))){geometry(launch(BasicSettingsActivity.class,null),"settings");return;}
   Activity home=launch(HomeActivity.class,null);check("home data ready",await(()->ui(()->(Boolean)field(home,"loadedOnce")),30000),"");geometry(home,"home");
   Activity settings=launch(BasicSettingsActivity.class,null);geometry(settings,"settings");
   Activity profiles=launch(ProfilesActivity.class,null);geometry(profiles,"profiles");
   Activity sync=launch(SyncActivity.class,null);geometry(sync,"sync");
   Activity history=launch(BrowseActivity.class,new Intent().putExtra("mode","history").putExtra("title","Історія перегляду"));check("history empty state",await(()->ui(()->((TextView)field(history,"status")).getText().toString().contains("Тут з’являться")),10000),"");
   Activity catalog=launch(BrowseActivity.class,new Intent().putExtra("mode","catalog").putExtra("category","films").putExtra("title","Фільми"));check("catalogue ready",await(()->ui(()->((ViewGroup)field(catalog,"grid")).getChildCount()>0),30000),"");geometry(catalog,"catalogue");
   Activity search=launch(BrowseActivity.class,new Intent().putExtra("mode","search").putExtra("title","Пошук"));geometry(search,"search");
   Activity taste=launch(TasteActivity.class,new Intent().putExtra("setup",false));check("taste choices ready",await(()->ui(()->!((Map)field(taste,"choices")).isEmpty()),30000),"");geometry(taste,"taste");
   String link="https://hdrezka.sh/series/drama/646-vo-vse-tyazhkie-2008.html";
   Activity film=launch(FilmActivity.class,new Intent().putExtra("link",link).putExtra("title","Breaking Bad"));check("film ready",await(()->ui(()->((View)field(film,"watch")).isEnabled()),30000),"");geometry(film,"film");
  }catch(Throwable error){passed=false;try{check("unexpected error",false,error.toString());}catch(Exception ignored){}}
  finally{try{ui(()->{for(Activity a:activities)if(!a.isDestroyed())a.finish();return null;});context.getSharedPreferences("kik_profiles",0).edit().putString("current",original).commit();if(qa!=null)ProfileStore.delete(context,qa);File dir=new File(context.getExternalFilesDir(null),"ui-audit");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,label+".json"))){out.write(new JSONObject().put("passed",passed).put("width",context.getResources().getDisplayMetrics().widthPixels).put("height",context.getResources().getDisplayMetrics().heightPixels).put("density",context.getResources().getDisplayMetrics().density).put("checks",checks).toString(2).getBytes("UTF-8"));}}catch(Exception e){passed=false;}Bundle result=new Bundle();result.putString("stream","Responsive audit passed="+passed+" checks="+checks.length());finish(passed?-1:0,result);}
 }
}
