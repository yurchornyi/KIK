package kik.qa;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import org.json.*;import kik.i18n.*;

/**
 * Two accounts on one TV, with made-up sign-ins and no network: «Хто дивиться?» shows both with their emails, the
 * current profile's account signs the requests, settings lists both, and an account the website signed out leaves
 * alone while the other stays. The TV's own accounts and profiles are put back afterwards.
 */
public final class AccountsAudit extends Instrumentation {
 JSONArray checks=new JSONArray();boolean passed=true;Context c;File dir;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(String label,boolean ok)throws Exception{checks.put(new JSONObject().put("name",label).put("passed",ok));passed&=ok;Bundle b=new Bundle();b.putString("stream",(ok?"PASS ":"FAIL ")+label+"\n");sendStatus(0,b);}
 Object call(Class<?> cls,String name,Class<?>[] types,Object... args)throws Exception{Method m=cls.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}
 void shot(String name)throws Exception{SystemClock.sleep(900);Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
 boolean shows(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(shows(((ViewGroup)v).getChildAt(i),text))return true;return false;}
 static String token(){StringBuilder s=new StringBuilder();Random r=new Random();for(int i=0;i<64;i++)s.append("0123456789abcdef".charAt(r.nextInt(16)));return s.toString();}
 void link(SharedPreferences cloud,String profile,String token,String remote,String name){cloud.edit().putString("token_"+profile,token).putString("remote_"+profile,remote).putString("name_"+profile,name).putLong("offset_"+profile,0).commit();}
 @Override public void onStart(){
  Bundle result=new Bundle();c=getTargetContext();dir=new File(c.getExternalFilesDir(null),"design-audit");dir.mkdirs();
  SharedPreferences account=c.getSharedPreferences("kik_account",0),profiles=c.getSharedPreferences("kik_profiles",0),cloud=c.getSharedPreferences("kik_cloud",0);
  Map<String,?> savedAccount=new HashMap<>(account.getAll()),savedProfiles=new HashMap<>(profiles.getAll()),savedCloud=new HashMap<>(cloud.getAll());
  try{
   String first=token(),second=token();
   String yura=ProfileStore.create(c,"QA Юра","").id,guest=ProfileStore.create(c,"QA Гість","").id;
   link(cloud,yura,first,"qa-remote-1","QA Юра");link(cloud,guest,second,"qa-remote-2","QA Гість");
   account.edit().clear().putString("accounts",new JSONArray().put(new JSONObject().put("email","first@qa.invalid").put("token",first)).put(new JSONObject().put("email","second@qa.invalid").put("token",second)).toString()).commit();
   profiles.edit().putString("current",yura).commit();
   check("the first viewer's titles use the first account",("Bearer "+first).equals(call(CloudSync.class,"bearer",new Class<?>[0])));
   profiles.edit().putString("current",guest).commit();
   check("the guest's titles use the guest's own account",("Bearer "+second).equals(call(CloudSync.class,"bearer",new Class<?>[0]))&&second.equals(Account.token(c)));

   Activity picker=startActivitySync(new Intent(c,ProfilesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   View root=picker.getWindow().getDecorView();SystemClock.sleep(600);
   check("«Хто дивиться?» shows both accounts' profiles with their emails",shows(root,"QA Юра")&&shows(root,"QA Гість")&&shows(root,"first@qa.invalid")&&shows(root,"second@qa.invalid"));
   shot("accounts-picker.png");runOnMainSync(picker::finish);

   Activity settings=startActivitySync(new Intent(c,BasicSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(600);
   check("settings offers «Облікові записи»",shows(settings.getWindow().getDecorView(),"Облікові записи"));
   runOnMainSync(()->{try{Method m=BasicSettingsActivity.class.getDeclaredMethod("accounts");m.setAccessible(true);m.invoke(settings);}catch(Exception e){throw new RuntimeException(e);}});
   shot("accounts-settings.png");runOnMainSync(settings::finish);SystemClock.sleep(500);

   // The website signed the guest's account out of this TV: only it leaves; the first account keeps its profile.
   call(Account.class,"revoked",new Class<?>[]{Context.class,String.class},c,second);
   long until=SystemClock.elapsedRealtime()+5000;while(ProfileStore.nameOf(c,guest)!=null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(200);
   JSONArray left=(JSONArray)call(Account.class,"accounts",new Class<?>[]{Context.class},c);
   check("a revoked account leaves with its profile",ProfileStore.nameOf(c,guest)==null&&left.length()==1&&"first@qa.invalid".equals(left.getJSONObject(0).optString("email")));
   check("the other account and its profile stay, and become current",ProfileStore.nameOf(c,yura)!=null&&CloudSync.linked(c,yura)&&yura.equals(ProfileStore.current(c)));
   SystemClock.sleep(1500);
  }catch(Throwable e){passed=false;Bundle b=new Bundle();b.putString("stream","FAIL "+e+"\n");sendStatus(0,b);}
  finally{
   // Fake sign-ins must not outlive the audit: put the TV's accounts, profiles and bindings back exactly.
   for(ProfileStore.Profile p:ProfileStore.list(c))if(p.name.startsWith("QA ")){cloud.edit().remove("token_"+p.id).commit();ProfileStore.delete(c,p.id);}
   restore(account,savedAccount);restore(profiles,savedProfiles);restore(cloud,savedCloud);
  }
  try{java.nio.file.Files.write(new File(dir,"accounts.json").toPath(),new JSONObject().put("passed",passed).put("checks",checks).toString().getBytes("UTF-8"));}catch(Exception ignored){}
  result.putString("passed",String.valueOf(passed));finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
 static void restore(SharedPreferences p,Map<String,?> saved){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:saved.entrySet()){Object v=x.getValue();if(v instanceof String)e.putString(x.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(x.getKey(),(Boolean)v);else if(v instanceof Long)e.putLong(x.getKey(),(Long)v);else if(v instanceof Integer)e.putInt(x.getKey(),(Integer)v);else if(v instanceof Float)e.putFloat(x.getKey(),(Float)v);else if(v instanceof Set)e.putStringSet(x.getKey(),(Set<String>)v);}e.commit();}
}
