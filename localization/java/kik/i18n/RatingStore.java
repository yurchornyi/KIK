package kik.i18n;
import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;
import java.io.*;
import java.util.*;
import org.json.*;

/** Explicit preferences and actual focused-title exposures, isolated by profile. */
final class RatingStore {
 static final class Rating {String link,title,poster,genre;int value;long time;}
 private static AtomicFile file(Context c,String profile){return new AtomicFile(new File(c.getFilesDir(),"kik_taste_"+profile+".json"));}
 private static JSONObject all(Context c,String p){try{return new JSONObject(Site.read(file(c,p).openRead()));}catch(Exception e){return new JSONObject();}}
 private static boolean write(Context c,String p,JSONObject o){FileOutputStream out=null;AtomicFile f=file(c,p);try{out=f.startWrite();out.write(o.toString().getBytes("UTF-8"));f.finishWrite(out);CloudSync.changed(c);return true;}catch(Exception e){if(out!=null)f.failWrite(out);Log.w("KIK","Save taste",e);return false;}}
 static synchronized List<Rating> list(Context c,String profile){
  List<Rating> out=new ArrayList<Rating>();JSONObject ratings=all(c,profile).optJSONObject("ratings");if(ratings==null)return out;Iterator<String> keys=ratings.keys();while(keys.hasNext()){JSONObject o=ratings.optJSONObject(keys.next());if(o==null)continue;Rating r=new Rating();r.link=o.optString("link");r.title=o.optString("title");r.poster=o.optString("poster",null);r.genre=o.optString("genre",null);r.value=o.optInt("value");r.time=o.optLong("time");out.add(r);}Collections.sort(out,(a,b)->Long.compare(b.time,a.time));return out;
 }
 /** What a tap did, in the same words on the TV and the web. */
 static String said(int value){return value>0?"Подобається · покажемо більше схожого":value<0?"Не подобається · сховаємо це й схоже":"Оцінку скасовано";}
 /** The card's answer after 👍 / 👎: «Дякуємо! Покажемо більше схожого». */
 static String thanks(int value){String s=said(value).replaceFirst("^[^·]*· ","");return "Дякуємо! "+Character.toUpperCase(s.charAt(0))+s.substring(1);}
 static synchronized int get(Context c,String profile,String link){for(Rating r:list(c,profile))if(RecommendationRanker.key(r.link).equals(RecommendationRanker.key(link)))return r.value;return 0;}
 static synchronized boolean set(Context c,String profile,String link,String title,String poster,String genre,int value){
  if(!profile.equals(ProfileStore.current(c))||ProfileStore.nameOf(c,profile)==null||RecommendationRanker.parts(link)[0].isEmpty())return false;
  JSONObject root=all(c,profile),ratings=root.optJSONObject("ratings");if(ratings==null)ratings=new JSONObject();String key=RecommendationRanker.key(link);
  try{if(value==0)ratings.remove(key);else ratings.put(key,new JSONObject().put("link",link).put("title",title).put("poster",poster).put("genre",genre).put("value",value>0?1:-1).put("time",System.currentTimeMillis()));root.put("ratings",ratings);return write(c,profile,root);}catch(Exception e){Log.w("KIK","Rate title",e);return false;}
 }
 /** Commit the whole onboarding selection once: cancellation or an inactive profile cannot partly save it. */
 static synchronized boolean setMany(Context c,String profile,List<Rating> changes){
  if(!profile.equals(ProfileStore.current(c))||ProfileStore.nameOf(c,profile)==null)return false;
  JSONObject root=all(c,profile),ratings=root.optJSONObject("ratings");if(ratings==null)ratings=new JSONObject();
  try{for(Rating r:changes){if(RecommendationRanker.parts(r.link)[0].isEmpty())return false;String key=RecommendationRanker.key(r.link);if(r.value==0)ratings.remove(key);else ratings.put(key,new JSONObject().put("link",r.link).put("title",r.title).put("poster",r.poster).put("genre",r.genre).put("value",r.value>0?1:-1).put("time",System.currentTimeMillis()));}root.put("ratings",ratings);return write(c,profile,root);}catch(Exception e){Log.w("KIK","Save initial taste",e);return false;}
 }
 static synchronized Map<String,Integer> exposures(Context c,String profile){Map<String,Integer> out=new HashMap<String,Integer>();JSONObject e=all(c,profile).optJSONObject("exposures");if(e!=null){Iterator<String> keys=e.keys();while(keys.hasNext()){String key=keys.next();JSONObject x=e.optJSONObject(key);if(x!=null&&System.currentTimeMillis()-x.optLong("time")<14*86_400_000L)out.put(key,x.optInt("count"));}}return out;}
 static synchronized void expose(Context c,String profile,String link){
  if(!profile.equals(ProfileStore.current(c))||ProfileStore.nameOf(c,profile)==null)return;
  JSONObject root=all(c,profile),all=root.optJSONObject("exposures");if(all==null)all=new JSONObject();String key=RecommendationRanker.key(link);JSONObject old=all.optJSONObject(key);long now=System.currentTimeMillis();
  // Moving back and forth with the remote is a single exposure, not repeated rejection.
  if(old!=null&&now-old.optLong("time")<86_400_000L)return;
  try{all.put(key,new JSONObject().put("count",old==null?1:Math.min(4,old.optInt("count")+1)).put("time",now));List<String> remove=new ArrayList<String>();Iterator<String> keys=all.keys();while(keys.hasNext()){String k=keys.next();if(now-all.optJSONObject(k).optLong("time")>14*86_400_000L)remove.add(k);}for(String k:remove)all.remove(k);if(all.length()>200){final JSONObject entries=all;List<String> keysByAge=new ArrayList<String>();Iterator<String> it=all.keys();while(it.hasNext())keysByAge.add(it.next());Collections.sort(keysByAge,(a,b)->Long.compare(entries.optJSONObject(a).optLong("time"),entries.optJSONObject(b).optLong("time")));for(int i=0;i<keysByAge.size()-200;i++)all.remove(keysByAge.get(i));}root.put("exposures",all);write(c,profile,root);}catch(Exception e){Log.w("KIK","Recommendation exposure",e);}
 }
 /** Titles the player already asked «Як вам?» about, on this device: never asked twice, whatever the answer. */
 static synchronized boolean asked(Context c,String profile,String link){JSONObject a=all(c,profile).optJSONObject("asked");return a!=null&&a.has(RecommendationRanker.key(link));}
 static synchronized void markAsked(Context c,String profile,String link){
  if(!profile.equals(ProfileStore.current(c))||ProfileStore.nameOf(c,profile)==null||RecommendationRanker.parts(link)[0].isEmpty())return;
  JSONObject root=all(c,profile),a=root.optJSONObject("asked");if(a==null)a=new JSONObject();
  try{a.put(RecommendationRanker.key(link),System.currentTimeMillis());if(a.length()>500){final JSONObject entries=a;List<String> keys=new ArrayList<String>();Iterator<String> it=a.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys,(x,y)->Long.compare(entries.optLong(x),entries.optLong(y)));for(int i=0;i<keys.size()-500;i++)a.remove(keys.get(i));}root.put("asked",a);write(c,profile,root);}catch(Exception e){Log.w("KIK","Rating prompt",e);}
 }
 static synchronized void delete(Context c,String profile){file(c,profile).delete();}
}
