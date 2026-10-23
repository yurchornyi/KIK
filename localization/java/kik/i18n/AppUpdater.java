package kik.i18n;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** KIK's own release channel. All I/O stays off the main thread; only an idle foreground screen installs. */
public final class AppUpdater {
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->new Thread(r,"kik-updates"));
 private static final AtomicBoolean BUSY=new AtomicBoolean();
 private static final int JOB=0x4b494b;
 private static final long CHECK_INTERVAL=6*60*60_000L,RETRY_INTERVAL=60*60_000L;
 private static volatile WeakReference<Activity> screen=new WeakReference<>(null);
 private static volatile WeakReference<Runnable> listener=new WeakReference<>(null);
 private static volatile String message="";
 private static Intent confirmation;
 private static String confirmationToken;
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("kik_updates",0);}
 public static boolean enabled(Context c){return prefs(c).getBoolean("automatic",true);}
 public static String status(Context c){String text=message.isEmpty()?prefs(c).getString("status","Автоматична перевірка через GitHub Releases"):message;if(text.startsWith("Установлено останню версію • "))return "Установлено останню версію • "+version(c);return text;}
 public static String version(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "";}}
 public static void listen(Runnable r){listener=new WeakReference<>(r);}
 public static void unlisten(Runnable r){if(listener.get()==r)listener.clear();}
 private static void state(Context c,String text){message=text;prefs(c).edit().putString("status",text).apply();MAIN.post(()->{Runnable r=listener.get();if(r!=null)r.run();});}
 public static void setEnabled(Context c,boolean on){prefs(c).edit().putBoolean("automatic",on).apply();schedule(c);state(c,on?"Автооновлення ввімкнено":"Автооновлення вимкнено");}
 public static void schedule(Context context){
  Context c=context.getApplicationContext();try{
   JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
   if(!enabled(c)){jobs.cancel(JOB);return;}
   if(jobs.getPendingJob(JOB)==null)jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(c,UpdateJob.class))
    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setPersisted(true).setPeriodic(12*60*60_000L).build());
  }catch(Exception e){Log.w("KIK.Update","Schedule unavailable",e);}
 }
 public static void resumed(Activity a){
  screen=new WeakReference<>(a);schedule(a);
  MAIN.postDelayed(()->{if(active()==a&&enabled(a)){resumeConfirmation(a);check(a,false,null);}},10_000);
 }
 public static void paused(Activity a){if(screen.get()==a)screen.clear();}
 private static Activity active(){Activity a=screen.get();return a!=null&&!a.isFinishing()&&!a.isDestroyed()&&a.hasWindowFocus()?a:null;}
 /** The newest release on the server when it is newer than this install, e.g. "1.6.0-uk"; Settings and the menu point to it. */
 public static String availableName(Context c){try{return prefs(c).getLong("available_code",0)>installedCode(c)?prefs(c).getString("available_name",null):null;}catch(Exception e){return null;}}
 /** Reads the release manifest (at most every 20 minutes) off the main thread, then runs {@code then} on the main thread. */
 public static void lookup(Context context,Runnable then){
  final Context c=context.getApplicationContext();
  IO.execute(()->{try{long now=System.currentTimeMillis(),at=prefs(c).getLong("available_at",0);
    if(now<at||now-at>20*60_000L){Release r=new Release(new JSONObject(new String(fetch(UpdateConfig.MANIFEST_URL,UpdatePolicy.MAX_METADATA),"UTF-8")));r.validate(c);
     prefs(c).edit().putLong("available_at",now).putLong("available_code",r.code).putString("available_name",r.name).apply();}
   }catch(Exception e){Log.w("KIK.Update","Lookup: "+e.getClass().getSimpleName()+": "+e.getMessage());}
   MAIN.post(then);});
 }
 private static long promptedAt;
 /** On every start (and after half an hour away) an available update is offered over the screen; «Пізніше» keeps it highlighted in Settings. */
 public static void prompt(Activity a){
  if(SystemClock.elapsedRealtime()-promptedAt<30*60_000L&&promptedAt!=0)return;
  lookup(a,()->{String name=availableName(a);Runnable r=listener.get();if(r!=null)r.run();
   if(name==null||a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus()||SystemClock.elapsedRealtime()-promptedAt<30*60_000L&&promptedAt!=0)return;promptedAt=SystemClock.elapsedRealtime();
   Ui.dialog(a).setTitle("Доступне оновлення KIK "+name).setMessage("Нова версія вже готова. Профілі, історія та «Мій список» збережуться.\nЯкщо відкласти, оновлення чекатиме в «Налаштуваннях».")
    .setNegativeButton("Пізніше",null).setPositiveButton("Оновити",(d,w)->manual(a)).show();});
 }
 public static boolean installAllowed(Context c){return Build.VERSION.SDK_INT<26||c.getPackageManager().canRequestPackageInstalls();}
 public static void permission(Activity a){
  try{a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())));}
  catch(Exception e){try{a.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));}catch(Exception ignored){Toast.makeText(a,"Дозвольте встановлення оновлень для KIK у налаштуваннях Android",Toast.LENGTH_LONG).show();}}
 }
 public static void manual(Activity a){
  screen=new WeakReference<>(a);
  if(confirmation!=null){resumeConfirmation(a);return;}
  check(a,true,null);
 }
 public static void check(Context context,boolean manual,Runnable finished){
  check(context,manual,finished,null);
 }
 static void check(Context context,boolean manual,Runnable finished,CancellationSignal cancellation){
  final Context c=context.getApplicationContext();
  if(!manual&&!enabled(c)){if(finished!=null)finished.run();return;}
  if(!BUSY.compareAndSet(false,true)){if(finished!=null)finished.run();return;}
  IO.execute(()->{try{
   cancelled(cancellation);cleanInstalled(c);
   Release ready=prepared(c);
   if(ready!=null){cancelled(cancellation);offer(c,ready,manual);return;}
   long now=System.currentTimeMillis(),last=prefs(c).getLong("last_check",0),attempt=prefs(c).getLong("last_attempt",0);
   if(!manual&&((now>=last&&now-last<CHECK_INTERVAL)||(now>=attempt&&now-attempt<RETRY_INTERVAL)))return;
   prefs(c).edit().putLong("last_attempt",now).apply();state(c,"Перевірка оновлень…");
   String raw=new String(fetch(UpdateConfig.MANIFEST_URL,UpdatePolicy.MAX_METADATA),"UTF-8");cancelled(cancellation);
   Release r=new Release(new JSONObject(raw));r.validate(c);
   prefs(c).edit().putLong("last_check",now).apply();
   if(r.code<=installedCode(c)){state(c,"Установлено останню версію • "+version(c));return;}
   if(!manual&&!enabled(c))return;
   state(c,"Завантаження KIK "+r.name+"…");download(c,r,manual,cancellation);
   verify(c,r);if(!prefs(c).edit().putString("prepared",r.json.toString()).remove("install_failed").commit())throw new IOException("Prepared state");
   cancelled(cancellation);offer(c,r,manual);
  }catch(OperationCanceledException e){state(c,"Перевірку відкладено до підключення Wi-Fi");
  }catch(Exception e){Log.w("KIK.Update","Update deferred: "+e.getClass().getSimpleName()+": "+e.getMessage());state(c,"Не вдалося оновити. Можна повторити перевірку.");}
  finally{BUSY.set(false);if(finished!=null)finished.run();}});
 }
 private static void offer(Context c,Release r,boolean manual)throws Exception{
  if(!manual&&!enabled(c))return;
  long failed=prefs(c).getLong("install_failed",0),now=System.currentTimeMillis();
  if(!manual&&now>=failed&&now-failed<24*60*60_000L){state(c,"Оновлення готове • повторіть установлення в налаштуваннях");return;}
  if(prefs(c).getInt("session",-1)>=0){
   long age=System.currentTimeMillis()-prefs(c).getLong("session_time",0);
   if(confirmation!=null){MAIN.post(()->{Activity a=active();if(a!=null)resumeConfirmation(a);});return;}
   if(age>=0&&age<10*60_000L){state(c,"Оновлення передано Android");return;}
   abandon(c);
  }
  Activity a=active();
  if(a==null){state(c,"KIK "+r.name+" готовий • установлення на головному екрані");return;}
  if(!installAllowed(c)){
   state(c,"KIK "+r.name+" готовий • потрібен дозвіл Android");
   MAIN.post(()->{Activity current=active();if(current==null)return;
    if(manual){permission(current);return;}
    if(!prefs(c).getBoolean("permission_explained",false)){
     prefs(c).edit().putBoolean("permission_explained",true).apply();
     Ui.dialog(current).setTitle("Автооновлення KIK").setMessage("Дозвольте KIK встановлювати власні оновлення в налаштуваннях Android. Профілі та історія збережуться.")
      .setNegativeButton("Пізніше",null).setPositiveButton("Дозволити",(d,w)->permission(current)).show();
    }
   });return;
  }
  // Recheck the persisted APK immediately before handing it to Android.
  verify(c,r);
  if(active()!=a||(!manual&&!enabled(c)))return;
  install(c,r,a,manual);
 }
 private static void install(Context c,Release r,Activity a,boolean manual)throws Exception{
  PackageInstaller installer=c.getPackageManager().getPackageInstaller();
  PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
  params.setAppPackageName(c.getPackageName());params.setSize(r.size);
  if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
  int id=installer.createSession(params);boolean committed=false;
  try(PackageInstaller.Session session=installer.openSession(id)){
   try(InputStream in=new FileInputStream(apk(c));OutputStream out=session.openWrite("base.apk",0,r.size)){
    byte[] b=new byte[64*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);session.fsync(out);
   }
   if(active()!=a||(!manual&&!enabled(c)))return;
   String token=UUID.randomUUID().toString();
   if(!prefs(c).edit().putInt("session",id).putLong("session_time",System.currentTimeMillis()).putString("session_token",token).commit())throw new IOException("Session state");
   Intent result=new Intent(c,UpdateReceiver.class).setAction(c.getPackageName()+".UPDATE_RESULT").setData(Uri.parse("kik-update://result/"+token));
   int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
   PendingIntent callback=PendingIntent.getBroadcast(c,id,result,flags);
   state(c,"Установлення KIK "+r.name+"…");
   session.commit(callback.getIntentSender());committed=true;
   Log.i("KIK.Update","Committed verified self-update code="+r.code+" session="+id);
  }finally{if(!committed){try{installer.abandonSession(id);}catch(Exception ignored){}prefs(c).edit().remove("session").remove("session_token").apply();}}
 }
 static void result(Context c,Intent intent){
  if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())){
   confirmation=null;IO.execute(()->{try{cleanInstalled(c);}catch(Exception ignored){}schedule(c);});return;
  }
  String expected=prefs(c).getString("session_token","");
  if(expected.isEmpty()||intent.getData()==null||!expected.equals(intent.getData().getLastPathSegment()))return;
  int id=prefs(c).getInt("session",-1);
  if(intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2)!=id)return;
  int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
  if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
   confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);confirmationToken=expected;
   state(c,"Android просить підтвердити оновлення");Activity a=active();if(a!=null)resumeConfirmation(a);
  }else{
   confirmation=null;confirmationToken=null;
   prefs(c).edit().remove("session").remove("session_token").apply();
   if(status==PackageInstaller.STATUS_SUCCESS){state(c,"KIK оновлено • "+version(c));IO.execute(()->{try{cleanInstalled(c);}catch(Exception ignored){}});}
   else{prefs(c).edit().putLong("last_check",System.currentTimeMillis()).putLong("install_failed",System.currentTimeMillis()).apply();state(c,status==PackageInstaller.STATUS_FAILURE_ABORTED?"Оновлення скасовано • повторіть у налаштуваннях":"Android не встановив оновлення • повторіть у налаштуваннях");}
   Log.i("KIK.Update","Install result="+status+" session="+id);
  }
 }
 private static void resumeConfirmation(Activity a){
  if(confirmation==null||active()!=a)return;
  if(!Objects.equals(confirmationToken,prefs(a).getString("session_token",""))){confirmation=null;return;}
  Intent i=confirmation;confirmation=null;
  try{a.startActivity(i);}catch(Exception e){abandon(a);state(a,"Підтвердьте оновлення через налаштування KIK");}
 }
 private static void abandon(Context c){int id=prefs(c).getInt("session",-1);if(id>=0)try{c.getPackageManager().getPackageInstaller().abandonSession(id);}catch(Exception ignored){}
  prefs(c).edit().remove("session").remove("session_token").apply();confirmation=null;confirmationToken=null;
 }
 private static File directory(Context c)throws IOException{File d=new File(c.getFilesDir(),"updates");if(!d.isDirectory()&&!d.mkdirs())throw new IOException("Update directory");return d;}
 private static File apk(Context c)throws IOException{return new File(directory(c),"ready.apk");}
 private static long installedCode(Context c)throws Exception{PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
 private static void cleanInstalled(Context c)throws Exception{
  String raw=prefs(c).getString("prepared","");if(raw.isEmpty())return;
  Release r=new Release(new JSONObject(raw));if(r.code<=installedCode(c)){
   apk(c).delete();prefs(c).edit().remove("prepared").remove("session").remove("session_token").remove("install_failed").apply();state(c,"KIK оновлено • "+version(c));
  }
 }
 private static Release prepared(Context c)throws Exception{
  String raw=prefs(c).getString("prepared","");if(raw.isEmpty())return null;
  try{Release r=new Release(new JSONObject(raw));r.validate(c);if(r.code<=installedCode(c))return null;verify(c,r);return r;}
  catch(Exception e){apk(c).delete();prefs(c).edit().remove("prepared").apply();throw e;}
 }
 static final class Release {
  final JSONObject json;final long code,size;final String name,url,sha,signer;final int min;
  Release(JSONObject j)throws Exception{json=j;code=j.getLong("version_code");name=j.getString("version_name");size=j.getLong("size_bytes");url=j.getString("apk_url");sha=j.getString("sha256");signer=j.getString("signer_sha256");min=j.getInt("min_sdk");}
  void validate(Context c)throws Exception{
   if(json.getInt("schema")!=1||!c.getPackageName().equals(json.getString("application_id"))||!UpdatePolicy.hash(sha)||!UpdatePolicy.hash(signer)
    ||name.length()>64||name.isEmpty()||!UpdatePolicy.releaseUrl(url,UpdateConfig.REPOSITORY)||size<=0||size>UpdatePolicy.MAX_APK||min<24||min>Build.VERSION.SDK_INT||code<=0||code>Integer.MAX_VALUE)throw new IOException("Release metadata");
   if(!signer.equalsIgnoreCase(signer(c.getPackageManager().getPackageInfo(c.getPackageName(),signatureFlags()))))throw new SecurityException("Release signer");
  }
 }
 private static int signatureFlags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
 private static String signer(PackageInfo p)throws Exception{
  android.content.pm.Signature[] signatures=Build.VERSION.SDK_INT>=28?p.signingInfo==null?null:p.signingInfo.getApkContentsSigners():p.signatures;
  if(signatures==null||signatures.length!=1)throw new SecurityException("Expected one signing certificate");
  return hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()));
 }
 private static void verify(Context c,Release r)throws Exception{
  File f=apk(c);if(f.length()!=r.size||!r.sha.equalsIgnoreCase(fileHash(f)))throw new SecurityException("APK digest");
  PackageInfo p=c.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(),signatureFlags());
  long code=p==null?-1:Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;
  if(p==null||!c.getPackageName().equals(p.packageName)||code!=r.code||!r.name.equals(p.versionName)
   ||!UpdatePolicy.eligible(installedCode(c),code,Build.VERSION.SDK_INT,p.applicationInfo.minSdkVersion,r.size)
   ||p.applicationInfo.minSdkVersion!=r.min||!r.signer.equalsIgnoreCase(signer(p))
   ||!signer(p).equals(signer(c.getPackageManager().getPackageInfo(c.getPackageName(),signatureFlags()))))throw new SecurityException("APK identity/signature");
 }
 private static String fileHash(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}return hex(md.digest());}
 private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
 private static HttpURLConnection open(String value)throws IOException{
  for(int i=0;i<6;i++){
   if(!UpdatePolicy.downloadHop(value,UpdateConfig.REPOSITORY,UpdateConfig.MANIFEST_URL))throw new IOException("Untrusted update URL");
   HttpURLConnection c=(HttpURLConnection)new URL(value).openConnection();c.setConnectTimeout(15_000);c.setReadTimeout(30_000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","KIK-Updater");c.setRequestProperty("Accept-Encoding","identity");
   int status=c.getResponseCode();
   if(status>=300&&status<400){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("Missing redirect");value=new URL(new URL(value),location).toString();continue;}
   if(status!=200){c.disconnect();throw new IOException("HTTP "+status);}return c;
  }throw new IOException("Too many redirects");
 }
 private static byte[] fetch(String url,int max)throws IOException{
  HttpURLConnection c=open(url);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Metadata too large");out.write(b,0,n);}return out.toByteArray();
  }finally{c.disconnect();}
 }
 private static void cancelled(CancellationSignal signal){if(signal!=null)signal.throwIfCanceled();}
 private static void download(Context c,Release r,boolean manual,CancellationSignal cancellation)throws Exception{
  File part=new File(directory(c),"download.part");HttpURLConnection connection=open(r.url);boolean done=false;
  try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(part)){
   long length=connection.getContentLengthLong();if(length>=0&&length!=r.size)throw new IOException("APK content length");
   long count=0,started=SystemClock.elapsedRealtime();int shown=-1;byte[] b=new byte[65536];int n;
   while((n=in.read(b))!=-1){cancelled(cancellation);if(!manual&&!enabled(c))throw new IOException("Disabled");
    count+=n;if(count>r.size||SystemClock.elapsedRealtime()-started>15*60_000L)throw new IOException("APK limit");out.write(b,0,n);
    int step=(int)(count*10/r.size);if(step!=shown){shown=step;state(c,"Завантаження KIK "+r.name+" • "+(step*10)+"%");}
   }
   if(count!=r.size)throw new IOException("Incomplete APK");out.getFD().sync();done=true;
  }finally{connection.disconnect();if(!done)part.delete();}
  if(!r.sha.equalsIgnoreCase(fileHash(part))){part.delete();throw new SecurityException("Downloaded APK digest");}
  if(!part.renameTo(apk(c))){part.delete();throw new IOException("APK rename");}
 }
 private AppUpdater(){}
}
