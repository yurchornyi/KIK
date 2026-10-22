package kik.qa;
import android.app.Instrumentation;import android.content.*;import android.os.Bundle;import java.io.*;import java.security.*;import java.util.*;import org.json.*;import kik.i18n.*;
/** A private snapshot proves that app replacement preserved profile credentials, preferences and history. */
public final class UpdateDataAudit extends Instrumentation {
 Bundle args;@Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 String hash(byte[] b)throws Exception{StringBuilder out=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(b))out.append(String.format(Locale.ROOT,"%02x",x&255));return out.toString();}
 byte[] read(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
 void collect(File f,File base,TreeMap<String,String> all)throws Exception{if(!f.exists())return;if(f.isDirectory()){for(File x:f.listFiles())collect(x,base,all);}else all.put(f.getAbsolutePath().substring(base.getAbsolutePath().length()+1),hash(read(f)));}
 JSONObject snapshot(Context c)throws Exception{
  TreeMap<String,String> all=new TreeMap<>();File base=c.getFilesDir();collect(new File(base,"playback_cache"),base,all);collect(new File(base,"profiles"),base,all);
  for(File f:base.listFiles()){String n=f.getName();if(n.startsWith("kik_progress_")||n.startsWith("kik_later_"))collect(f,base,all);
   if(n.startsWith("kik_taste_")&&n.endsWith(".json")){JSONObject root=new JSONObject(new String(read(f),"UTF-8"));JSONObject ratings=root.optJSONObject("ratings");all.put(n+"/ratings",hash((ratings==null?"{}":ratings.toString()).getBytes("UTF-8")));}}
  JSONObject profiles=new JSONObject(new TreeMap<String,Object>(c.getSharedPreferences("kik_profiles",0).getAll()));all.put("profiles_preferences",hash(profiles.toString().getBytes("UTF-8")));
  JSONObject settings=new JSONObject(new TreeMap<String,Object>(c.getSharedPreferences("settings",0).getAll()));all.put("playback_preferences",hash(settings.toString().getBytes("UTF-8")));
  return new JSONObject(all);
 }
 @Override public void onStart(){Bundle out=new Bundle();try{Context c=getTargetContext();File saved=new File(c.getFilesDir(),"update-audit-snapshot.json");JSONObject now=snapshot(c);boolean compare="compare".equals(args.getString("mode"));if(compare){JSONObject before=new JSONObject(new String(read(saved),"UTF-8"));if(!before.toString().equals(now.toString()))throw new AssertionError("Profile/history/preferences snapshot mismatch");}else try(FileOutputStream f=new FileOutputStream(saved)){f.write(now.toString().getBytes("UTF-8"));}
  JSONObject report=new JSONObject().put("mode",compare?"compare":"snapshot").put("passed",true).put("files_and_preferences",now.length()).put("history_entries",Progress.recent(c).size()).put("profile_count",ProfileStore.list(c).size()).put("version",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);out.putString("stream",report.toString());finish(-1,out);
 }catch(Throwable e){out.putString("stream",e.toString());finish(0,out);}}
}
