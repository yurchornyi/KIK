package kik.i18n;
import android.content.SharedPreferences;
import android.os.Looper;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONArray;

/** Machine translation of catalog descriptions into Ukrainian, cached on the device. */
public final class DescTranslate {
 private static final Set<String> PENDING=Collections.synchronizedSet(new HashSet<String>());
 private static SharedPreferences prefs(){return UiRuntime.app==null?null:UiRuntime.app.getSharedPreferences("kik_desc_uk",0);}
 private static String key(String s){return Integer.toHexString(s.hashCode())+"_"+s.length();}
 private static boolean cyrillic(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c>='Ѐ'&&c<='ӿ')return true;}return false;}

 /** Translates a cached Film's description in place; the class is reached reflectively. */
 public static void film(Object film) {
  if(film==null)return;
  try{
   String d=(String)film.getClass().getMethod("getDescription").invoke(film);
   String t=tr(d);
   if(t!=d)film.getClass().getMethod("setDescription",String.class).invoke(film,t);
  }catch(Exception e){Log.w("KIK","Cached description translation",e);}
 }

 public static String cached(String text){
  if(text==null)return null;SharedPreferences p=prefs();return p==null?text:p.getString(key(text),text);
 }

 public static String tr(final String text) {
  if(text==null||text.trim().length()<2||!cyrillic(text))return text;
  final SharedPreferences p=prefs();if(p==null)return text;
  final String k=key(text);String cached=p.getString(k,null);
  if(cached!=null)return cached;
  if(Looper.myLooper()==Looper.getMainLooper()){
   // Legacy UI hook: return immediately and populate the cache asynchronously.
   if(!PENDING.add(k))return text;
   Thread t=new Thread(new Runnable(){public void run(){try{fetch(text,p,k);}finally{PENDING.remove(k);}}},"kik-translate");
   t.start();
   return text;
  }
  String out=fetch(text,p,k);
  return out==null?text:out;
 }

 private static String fetch(String text,SharedPreferences p,String k) {
  HttpURLConnection c=null;
  try {
   c=(HttpURLConnection)new URL("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=uk").openConnection();
   c.setConnectTimeout(3000);c.setReadTimeout(5000);c.setDoOutput(true);c.setRequestMethod("POST");
   c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
   c.setRequestProperty("Content-Type","application/x-www-form-urlencoded;charset=UTF-8");
   OutputStream o=c.getOutputStream();o.write(("q="+URLEncoder.encode(text,"UTF-8")).getBytes("UTF-8"));o.close();
   int code=c.getResponseCode();
   if(code!=200){Log.w("KIK","Description translation HTTP "+code);return null;}
   BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
   StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);r.close();
   // Response: [["translated","ru"]] with detected source, or ["translated"].
   Object first=new JSONArray(b.toString()).get(0);
   String result=(first instanceof JSONArray?((JSONArray)first).optString(0,""):String.valueOf(first)).trim();
   if(result.isEmpty())return null;
   Log.d("KIK","Description translated, "+text.length()+" chars");
   p.edit().putString(k,result).putString(key(result),result).apply();
   return result;
  }catch(Exception e){Log.w("KIK","Description translation failed: "+e);return null;}
  finally{if(c!=null)c.disconnect();}
 }
}
