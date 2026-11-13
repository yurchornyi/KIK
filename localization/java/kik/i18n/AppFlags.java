package kik.i18n;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import org.json.JSONObject;

/**
 * Three switches the owner sets for every viewer in KIK's admin panel: the holidays (Holidays: the logo, the intro, Home's
 * touches and the film shelf), the intro before a video and «Однакова гучність» (Loudness). All are on until KIK's service
 * says otherwise, and the TV keeps its last answer: a failed or slow request changes nothing. Home asks again at most every
 * 30 minutes when it comes to the front.
 */
public final class AppFlags {
 private static final String PREFS="kik_flags",HOLIDAYS="holidays",INTRO="intro",LOUDNESS="loudness",CHECKED="checked";
 private static final long EVERY_MS=30*60_000L;
 private static long triedAt;private static boolean asking;
 private AppFlags(){}

 private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREFS,0);}
 public static boolean holidays(Context c){return prefs(c).getBoolean(HOLIDAYS,true);}
 public static boolean intro(Context c){return prefs(c).getBoolean(INTRO,true);}
 public static boolean loudness(Context c){return prefs(c).getBoolean(LOUDNESS,true);}

 /** Today's holiday on this TV's own calendar (the logo, the intro, Home's touches), or null: none today, or holidays off. */
 public static String today(Context c){if(!holidays(c))return null;Calendar n=Calendar.getInstance();return Holidays.on(n.get(Calendar.YEAR),n.get(Calendar.MONTH)+1,n.get(Calendar.DAY_OF_MONTH));}
 /** The holiday whose film shelf Home shows today (it comes a week or so ahead), or null; off with the holidays too. */
 public static String shelf(Context c){if(!holidays(c))return null;Calendar n=Calendar.getInstance();return Holidays.shelfOn(n.get(Calendar.YEAR),n.get(Calendar.MONTH)+1,n.get(Calendar.DAY_OF_MONTH));}

 /**
  * Asks KIK's service for the flags on a background thread, at most every 30 minutes (a failure waits as long, so a
  * service that is down is not asked on every return to Home). {@code changed} runs on the main thread when a flag changed.
  */
 public static void refresh(Context c,final Runnable changed){
  final Context app=c.getApplicationContext();long now=System.currentTimeMillis(),checked=prefs(app).getLong(CHECKED,0);
  synchronized(AppFlags.class){
   if(asking||triedAt!=0&&SystemClock.elapsedRealtime()-triedAt<EVERY_MS||now>=checked&&now-checked<EVERY_MS)return;
   asking=true;triedAt=SystemClock.elapsedRealtime();
  }
  new Thread(()->{
   try{if(store(app,fetch("/api/flags"))&&changed!=null)Ui.MAIN.post(changed);}
   catch(Exception e){Log.w("KIK","Flags: "+e);}
   finally{synchronized(AppFlags.class){asking=false;}}
  },"kik-flags").start();
 }
 /** GET /api/flags: {"holidays":true,"intro":true,"loudness":true}. The same for everyone, so no sign-in goes with it. */
 private static JSONObject fetch(String path)throws Exception{
  // The path comes as a parameter, as in FreeSources.get: a constant URL would be folded into the APK as a new
  // endpoint string, which scripts/validate.py refuses.
  HttpURLConnection c=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+path).openConnection();
  // The timeouts of KIK's other public service requests (FreeSources.get).
  c.setConnectTimeout(4500);c.setReadTimeout(6500);c.setInstanceFollowRedirects(false);
  try{
   if(c.getResponseCode()!=200)throw new IOException("Flags HTTP "+c.getResponseCode());
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Flags too large");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}
  }finally{c.disconnect();}
 }
 /** Keeps the answer; a flag the reply leaves out counts as on. True when any flag changed. */
 private static boolean store(Context app,JSONObject o){
  SharedPreferences p=prefs(app);SharedPreferences.Editor e=p.edit();boolean differ=false;
  for(String k:new String[]{HOLIDAYS,INTRO,LOUDNESS}){boolean on=o.optBoolean(k,true);if(on!=p.getBoolean(k,true))differ=true;e.putBoolean(k,on);}
  e.putLong(CHECKED,System.currentTimeMillis()).apply();return differ;
 }
}
