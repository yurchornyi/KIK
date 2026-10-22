package kik.qa;
import android.app.*;import android.app.job.*;import android.content.*;import android.content.pm.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.Callable;import org.json.*;import kik.i18n.*;
/** Real PackageManager signature/archive checks, manifest permissions and remote settings layout. */
public final class UpdateAudit extends Instrumentation {
 Bundle args;Context c;JSONArray checks=new JSONArray();boolean passed=true;Activity settings;
 @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 <T>T ui(Callable<T> f){Object[] out=new Object[2];runOnMainSync(()->{try{out[0]=f.call();}catch(Throwable t){out[1]=t;}});if(out[1]!=null)throw new RuntimeException((Throwable)out[1]);return (T)out[0];}
 void check(String name,boolean ok)throws Exception{checks.put(new JSONObject().put("name",name).put("passed",ok));if(!ok)throw new AssertionError(name);}
 Object call(Class<?> type,Object object,String name,Class<?>[] types,Object... values)throws Exception{Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,values);}
 Object release(JSONObject j)throws Exception{Constructor<?> ctor=Class.forName("kik.i18n.AppUpdater$Release").getDeclaredConstructor(JSONObject.class);ctor.setAccessible(true);return ctor.newInstance(j);}
 void validate(Object r)throws Exception{call(r.getClass(),r,"validate",new Class[]{Context.class},c);}
 void verify(Object r)throws Exception{call(AppUpdater.class,null,"verify",new Class[]{Context.class,r.getClass()},c,r);}
 void rejects(String name,JSONObject source,String key,Object value,boolean archive)throws Exception{JSONObject j=new JSONObject(source.toString());j.put(key,value);boolean rejected=false;try{Object r=release(j);if(archive)verify(r);else validate(r);}catch(Exception e){rejected=true;}check(name,rejected);}
 void buttons(View v,List<Button> out){if(v instanceof Button)out.add((Button)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)buttons(((ViewGroup)v).getChildAt(i),out);}
 @Override public void onStart(){c=getTargetContext();SharedPreferences p=c.getSharedPreferences("kik_updates",0);boolean automatic=p.getBoolean("automatic",true);File ready=new File(c.getFilesDir(),"updates/ready.apk");
 try{
  if("arm".equals(args.getString("mode"))){p.edit().putBoolean("automatic",true).remove("last_check").remove("last_attempt").remove("install_failed").commit();AppUpdater.schedule(c);check("automatic_update_armed",true);return;}
  p.edit().putBoolean("automatic",false).commit();AppUpdater.schedule(c);
  PackageInfo own=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_PERMISSIONS);List<String> permissions=Arrays.asList(own.requestedPermissions);
  check("install_packages_permission",permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES"));check("self_update_without_action_permission",permissions.contains("android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION"));
  ServiceInfo service=c.getPackageManager().getServiceInfo(new ComponentName(c,UpdateJob.class),0);check("job_requires_system_permission",service.exported&&"android.permission.BIND_JOB_SERVICE".equals(service.permission));
  ActivityInfo receiver=c.getPackageManager().getReceiverInfo(new ComponentName(c,UpdateReceiver.class),0);check("installer_receiver_internal",!receiver.exported);
  JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);check("disabled_cancels_job",scheduler.getPendingJob(0x4b494b)==null);
  p.edit().putBoolean("automatic",true).commit();AppUpdater.schedule(c);JobInfo job=scheduler.getPendingJob(0x4b494b);check("persisted_daily_job",job!=null&&job.isPersisted()&&job.isPeriodic()&&job.getIntervalMillis()==12*60*60_000L);p.edit().putBoolean("automatic",false).commit();AppUpdater.schedule(c);
  File fixture=new File(c.getExternalFilesDir(null),"update-fixture");JSONObject data=new JSONObject(new String(Files.readAllBytes(new File(fixture,"update.json").toPath()),"UTF-8"));ready.getParentFile().mkdirs();Files.copy(new File(fixture,"candidate.apk").toPath(),ready.toPath(),StandardCopyOption.REPLACE_EXISTING);
  Object r=release(data);validate(r);verify(r);check("real_new_apk_signature_and_identity",true);
  rejects("wrong_package",data,"application_id","evil.package",false);rejects("wrong_repository",data,"apk_url",data.getString("apk_url").replace("yurchornyi/KIK","other/repo"),false);rejects("wrong_signing_certificate",data,"signer_sha256",new String(new char[64]).replace('\0','0'),false);rejects("incompatible_sdk",data,"min_sdk",999,false);rejects("invalid_size",data,"size_bytes",0,false);
  rejects("wrong_digest",data,"sha256",new String(new char[64]).replace('\0','0'),true);rejects("wrong_apk_version",data,"version_code",data.getLong("version_code")+1,true);
  try(RandomAccessFile file=new RandomAccessFile(ready,"rw")){file.seek(300);int b=file.read();file.seek(300);file.write(b^0x80);}boolean corrupt=false;try{verify(r);}catch(Exception e){corrupt=true;}check("modified_bytes_rejected",corrupt);Files.copy(new File(fixture,"candidate.apk").toPath(),ready.toPath(),StandardCopyOption.REPLACE_EXISTING);
  settings=startActivitySync(new Intent(c,BasicSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(800);
  List<Button> all=ui(()->{List<Button> result=new ArrayList<>();buttons(settings.getWindow().getDecorView(),result);return result;});boolean checkButton=false,permissionButton=false,fit=true;
  for(Button b:all){String label=b.getText().toString();checkButton|=label.equals("Перевірити оновлення");permissionButton|=label.equals("Дозвіл на встановлення");if(label.contains("оновлен")||label.contains("встановлення")){ui(()->{b.requestFocus();return null;});SystemClock.sleep(200);fit&=ui(()->b.getLayout()!=null&&b.getLayout().getEllipsisCount(0)==0&&b.getHeight()>=b.getLineHeight()+b.getPaddingTop()+b.getPaddingBottom());}}
  check("update_buttons_visible_and_fit",checkButton&&permissionButton&&fit);
  AppUpdater.paused(settings);p.edit().putBoolean("automatic",true).commit();call(AppUpdater.class,null,"offer",new Class[]{Context.class,r.getClass(),boolean.class},c,r,false);check("no_install_without_idle_foreground_screen",p.getInt("session",-1)==-1);p.edit().putBoolean("automatic",false).commit();
  check("android_install_permission_enabled",AppUpdater.installAllowed(c));
 }catch(Throwable e){passed=false;try{checks.put(new JSONObject().put("error",e.toString()));}catch(Exception ignored){}}
 finally{if(!"arm".equals(args.getString("mode"))){if(settings!=null)ui(()->{settings.finish();return null;});ready.delete();p.edit().remove("prepared").putBoolean("automatic",automatic).commit();AppUpdater.schedule(c);}try{File out=new File(c.getExternalFilesDir(null),"design-audit/updater.json");out.getParentFile().mkdirs();Files.write(out.toPath(),new JSONObject().put("passed",passed).put("checks",checks).toString(2).getBytes("UTF-8"));}catch(Exception e){passed=false;}Bundle b=new Bundle();b.putString("stream","Updater audit passed="+passed+" checks="+checks.length());finish(passed?-1:0,b);}
 }
}
