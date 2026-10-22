package kik.i18n;
import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;
import org.json.*;
import java.io.*;
import java.util.*;

/** Bounded cache of public film facts. Contains no profile identifiers or watch events. */
final class RecommendationMetadata {
 private static JSONObject data;private static final long DAY=86_400_000L;
 private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"kik_content_metadata.json"));}
 private static JSONObject data(Context c){if(data==null)try{data=new JSONObject(Site.read(file(c).openRead()));}catch(Exception e){data=new JSONObject();}return data;}
 private static void trim(JSONObject all){if(all.length()<=300)return;List<String> keys=new ArrayList<String>();Iterator<String> it=all.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys,(a,b)->Long.compare(all.optJSONObject(a).optLong("time"),all.optJSONObject(b).optLong("time")));for(int i=0;i<keys.size()-300;i++)all.remove(keys.get(i));}
 private static void write(Context c){FileOutputStream out=null;AtomicFile f=file(c);try{out=f.startWrite();out.write(data.toString().getBytes("UTF-8"));f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);Log.w("KIK","Content metadata",e);}}
 static synchronized void remember(Context c,Site.Film f){
  if(f==null||f.link==null)return;
  try{JSONObject o=new JSONObject().put("link",f.link).put("title",f.title).put("original",f.original).put("poster",f.poster).put("plot",f.description==null?"":f.description.substring(0,Math.min(5000,f.description.length()))).put("country",f.country).put("year",f.year).put("rating",f.rating).put("genres",new JSONArray(f.genres)).put("people",new JSONArray(f.people)).put("time",System.currentTimeMillis());
   JSONObject all=data(c);all.put(RecommendationRanker.key(f.link),o);
   if(all.length()>300){List<String> keys=new ArrayList<String>();Iterator<String> it=all.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys,(a,b)->Long.compare(all.optJSONObject(a).optLong("time"),all.optJSONObject(b).optLong("time")));for(int i=0;i<keys.size()-300;i++)all.remove(keys.get(i));}write(c);
  }catch(Exception e){Log.w("KIK","Remember film facts",e);}
 }
 static synchronized ContentSimilarity.Document get(Context c,String link){
  JSONObject o=data(c).optJSONObject(RecommendationRanker.key(link));if(o==null||o.optString("title","").isEmpty())return null;
  ContentSimilarity.Document d=new ContentSimilarity.Document();d.title=o.optString("title","");d.original=o.optString("original","");d.plot=o.optString("plot","");d.country=o.optString("country","");try{d.year=Integer.parseInt(o.optString("year","0").replaceAll("[^0-9]",""));}catch(Exception ignored){}try{d.rating=Double.parseDouble(o.optString("rating","0"));}catch(Exception ignored){}
  JSONArray genres=o.optJSONArray("genres"),people=o.optJSONArray("people");if(genres!=null)for(int i=0;i<genres.length();i++)d.genres.add(genres.optString(i));if(people!=null)for(int i=0;i<people.length();i++)d.people.add(people.optString(i).toLowerCase(Locale.ROOT).trim());return d;
 }
 static synchronized boolean needs(Context c,String link){JSONObject o=data(c).optJSONObject(RecommendationRanker.key(link));if(o==null)return true;long age=System.currentTimeMillis()-o.optLong("time");return age<0||age>(o.optBoolean("failed")?DAY/24:30*DAY);}
 static synchronized void failed(Context c,String link){try{JSONObject all=data(c);String key=RecommendationRanker.key(link);JSONObject o=all.optJSONObject(key);if(o==null)o=new JSONObject();o.put("time",System.currentTimeMillis()).put("failed",true);all.put(key,o);trim(all);write(c);}catch(Exception ignored){}}
}
