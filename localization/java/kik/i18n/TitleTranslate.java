package kik.i18n;
import android.content.SharedPreferences;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import org.json.JSONArray;

/**
 * Display-only machine translation of Russian catalog text (titles, voice names) into Ukrainian.
 * Unknown strings show as-is, are translated in batches, then a Compose state bump redraws them.
 */
public final class TitleTranslate {
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final Map<String,String> MEM=new HashMap<String,String>();
 private static final LinkedHashSet<String> QUEUE=new LinkedHashSet<String>();
 private static final Set<String> TRIED=new HashSet<String>();
 /** Strings the translation service has already answered (or failed) for: only these may show untranslated. */
 private static final Set<String> DONE=new HashSet<String>();
 private static final java.util.concurrent.ExecutorService POOL=java.util.concurrent.Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"kik-titles-batch");t.setDaemon(true);return t;});
 private static boolean loaded,scheduled,running;
 private static Object state;
 private static int version;

 public static String lookup(String s){
  if(!candidate(s))return null;
  Object st=state();
  if(st!=null)PlaybackPolicy.call(st,"getValue");
  synchronized(MEM){
   load();
   String hit=MEM.get(s);if(hit!=null)return hit;
   if(TRIED.add(s)){QUEUE.add(s);schedule();}
  }
  return null;
 }

 /** Cache-only lookup: native screens never wait for the translation service to become usable. */
 public static Map<String,String> cached(Collection<String> texts){
  Map<String,String> out=new HashMap<String,String>();
  synchronized(MEM){if(UiRuntime.app==null)return out;load();for(String s:texts){String hit=MEM.get(s);if(hit!=null)out.put(s,hit);}}
  return out;
 }

 /** Blocking variant for native screens (call off the main thread): translations for all given strings. */
 public static Map<String,String> resolve(Collection<String> texts){
  Map<String,String> out=new HashMap<String,String>();List<String> miss=new ArrayList<String>();
  synchronized(MEM){load();for(String s:texts){if(!candidate(s))continue;String hit=MEM.get(s);if(hit!=null)out.put(s,hit);else if(!miss.contains(s))miss.add(s);}}
  // Batches go out together: a screen with many new titles waits for one round trip, not one per 40 strings.
  List<List<String>> parts=new ArrayList<List<String>>();List<java.util.concurrent.Future<Map<String,String>>> calls=new ArrayList<java.util.concurrent.Future<Map<String,String>>>();
  for(int i=0;i<miss.size();i+=40){final List<String> part=new ArrayList<String>(miss.subList(i,Math.min(miss.size(),i+40)));parts.add(part);calls.add(POOL.submit(()->fetch(part)));}
  for(int i=0;i<parts.size();i++){
   Map<String,String> got;try{got=calls.get(i).get();}catch(Exception e){got=new HashMap<String,String>();}
   remember(parts.get(i),got);out.putAll(got);
  }
  return out;
 }

 /** What a screen may show for catalogue text it has no translation for: nothing while the translation is still on its way, so Russian never flashes first. */
 public static String shown(String s){
  if(s==null)return null;
  synchronized(MEM){if(!candidate(s))return s;load();if(MEM.containsKey(s)||DONE.contains(s))return s;}
  return "";
 }
 private static void remember(List<String> batch,Map<String,String> got){
  synchronized(MEM){
   DONE.addAll(batch);if(got.isEmpty())return;
   SharedPreferences.Editor e=prefs().edit();
   for(Map.Entry<String,String> x:got.entrySet()){MEM.put(x.getKey(),x.getValue());MEM.put(x.getValue(),x.getValue());e.putString(x.getKey(),x.getValue());}
   e.apply();
  }
 }

 /** Bounded blocking variant for text about to appear (off the main thread): waits at most ms so screens show Ukrainian from the start; on a slow network the rest arrives later. */
 public static Map<String,String> resolve(final Collection<String> texts,long ms){
  java.util.concurrent.FutureTask<Map<String,String>> task=new java.util.concurrent.FutureTask<Map<String,String>>(()->resolve(texts));
  new Thread(task,"kik-titles-now").start();
  try{return task.get(ms,java.util.concurrent.TimeUnit.MILLISECONDS);}catch(Exception e){return cached(texts);}
 }

 private static boolean candidate(String s){
  if(s==null||s.length()<2||s.length()>160||UiRuntime.app==null)return false;
  boolean cyr=false;
  for(int i=0;i<s.length();i++){char c=s.charAt(i);
   if(c=='і'||c=='ї'||c=='є'||c=='ґ'||c=='І'||c=='Ї'||c=='Є'||c=='Ґ')return false;
   if(c>='Ѐ'&&c<='ӿ')cyr=true;
  }
  return cyr;
 }
 private static SharedPreferences prefs(){return UiRuntime.app.getSharedPreferences("kik_title_uk",0);}
 private static void load(){
  if(loaded)return;loaded=true;
  // A translation is its own translation: Ukrainian titles saved in history are not sent again.
  for(Map.Entry<String,?> e:prefs().getAll().entrySet())if(e.getValue() instanceof String){MEM.put(e.getKey(),(String)e.getValue());MEM.putIfAbsent((String)e.getValue(),(String)e.getValue());}
 }
 private static void schedule(){
  if(scheduled||running)return;scheduled=true;
  MAIN.postDelayed(new Runnable(){public void run(){scheduled=false;flush();}},250);
 }
 private static void flush(){
  final List<String> batch=new ArrayList<String>();
  synchronized(MEM){Iterator<String> it=QUEUE.iterator();while(it.hasNext()&&batch.size()<40){batch.add(it.next());it.remove();}}
  if(batch.isEmpty())return;
  running=true;
  new Thread(new Runnable(){public void run(){
   Map<String,String> got=fetch(batch);
   remember(batch,got);
   synchronized(MEM){
    running=false;
    if(!QUEUE.isEmpty())MAIN.post(new Runnable(){public void run(){schedule();}});
   }
   if(!got.isEmpty())MAIN.post(new Runnable(){public void run(){bump();}});
  }},"kik-titles").start();
 }
 private static Map<String,String> fetch(List<String> batch){
  Map<String,String> out=new HashMap<String,String>();
  HttpURLConnection c=null;
  try{
   StringBuilder body=new StringBuilder();
   for(String s:batch){if(body.length()>0)body.append('&');body.append("q=").append(URLEncoder.encode(s,"UTF-8"));}
   c=(HttpURLConnection)new URL("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=ru&tl=uk").openConnection();
   c.setConnectTimeout(4000);c.setReadTimeout(6000);c.setDoOutput(true);c.setRequestMethod("POST");
   c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
   c.setRequestProperty("Content-Type","application/x-www-form-urlencoded;charset=UTF-8");
   OutputStream o=c.getOutputStream();o.write(body.toString().getBytes("UTF-8"));o.close();
   if(c.getResponseCode()!=200){Log.w("KIK","Title translation HTTP "+c.getResponseCode());return out;}
   BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
   StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);r.close();
   // One query answers ["text"] or [["text","ru"]]; several answer one entry per query in order.
   JSONArray a=new JSONArray(b.toString());
   for(int i=0;i<a.length()&&i<batch.size();i++){
    Object v=a.get(i);String t=(v instanceof JSONArray?((JSONArray)v).optString(0,""):String.valueOf(v)).trim();
    if(!t.isEmpty())out.put(batch.get(i),t);
   }
  }catch(Exception e){Log.w("KIK","Title translation failed: "+e);}
  finally{if(c!=null)c.disconnect();}
  return out;
 }

 /** Compose snapshot state (obfuscated ds4 with policy sf5.X in RezkaTV 1.2.0) read by every hooked Text. */
 private static Object state(){
  if(state!=null||Looper.myLooper()!=Looper.getMainLooper())return state;
  try{
   Object policy=Class.forName("sf5").getField("X").get(null);
   Constructor<?> k=Class.forName("ds4").getConstructor(Object.class,Class.forName("va6"));
   state=k.newInstance(Integer.valueOf(0),policy);
  }catch(Exception e){Log.w("KIK","Title refresh state",e);state=new Object();}
  return state;
 }
 private static void bump(){
  Object st=state();
  try{st.getClass().getMethod("setValue",Object.class).invoke(st,Integer.valueOf(++version));}catch(Exception ignored){}
 }
}
