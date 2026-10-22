package kik.i18n;
import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;

/**
 * KIK's own watch history for the current profile: last episode, voice and position per title,
 * plus per-episode positions for the episode list. Stored as JSON in the app's files directory.
 */
public final class Progress {
 public static final class Entry{
  public String link,title,poster,season,episode,voice,voiceName;public long pos,dur,ts,watchedMs;public boolean observed;
  public boolean finished(){return dur>0&&pos>dur*0.93;}
 }
 private static File file(Context c,String kind){return file(c,kind,ProfileStore.current(c));}
 private static File file(Context c,String kind,String profile){return new File(c.getFilesDir(),"kik_"+kind+"_"+profile+".json");}
 static String norm(String link){if(link==null)return "";String s=link.replaceFirst("^https?://[^/]+","");while(s.startsWith("/"))s=s.substring(1);return s;}

 private static synchronized JSONObject all(Context c){return all(c,ProfileStore.current(c));}
 private static synchronized JSONObject all(Context c,String profile){try{return new JSONObject(Site.read(new android.util.AtomicFile(file(c,"progress",profile)).openRead()));}catch(Exception e){return new JSONObject();}}
 private static void atomicWrite(File file,String text)throws IOException{android.util.AtomicFile atomic=new android.util.AtomicFile(file);FileOutputStream out=null;try{out=atomic.startWrite();out.write(text.getBytes("UTF-8"));atomic.finishWrite(out);}catch(IOException e){if(out!=null)atomic.failWrite(out);throw e;}}
 private static synchronized void write(Context c,JSONObject o){try{atomicWrite(file(c,"progress"),o.toString());}catch(IOException e){android.util.Log.w("KIK","Save progress",e);}}

 public static synchronized void save(Context c,Entry e){
  JSONObject o=all(c);String k=norm(e.link);
  try{
   JSONObject prev=o.optJSONObject(k);JSONObject eps=prev==null?new JSONObject():prev.optJSONObject("eps");if(eps==null)eps=new JSONObject();
   eps.put((e.season==null?"0":e.season)+":"+(e.episode==null?"0":e.episode),new JSONArray().put(e.pos).put(e.dur));
   o.put(k,new JSONObject().put("link",e.link).put("title",e.title).put("poster",e.poster).put("season",e.season).put("episode",e.episode)
    .put("voice",e.voice).put("voiceName",e.voiceName).put("pos",e.pos).put("dur",e.dur).put("ts",System.currentTimeMillis()).put("eps",eps)
    .put("observed",e.observed||(prev!=null&&prev.optBoolean("observed"))).put("watched",Math.max(0,e.watchedMs)+(prev==null?0:prev.optLong("watched"))));
  }catch(JSONException ignored){}
  write(c,o);
 }
 public static synchronized void remove(Context c,String link){JSONObject o=all(c);o.remove(norm(link));write(c,o);}
 public static synchronized Entry get(Context c,String link){JSONObject x=all(c).optJSONObject(norm(link));return x==null?null:entry(x);}
 /** Position and duration of one episode, or null. */
 public static synchronized long[] episode(Context c,String link,String season,String episode){
  JSONObject x=all(c).optJSONObject(norm(link));if(x==null)return null;JSONObject eps=x.optJSONObject("eps");if(eps==null)return null;
  JSONArray a=eps.optJSONArray((season==null?"0":season)+":"+(episode==null?"0":episode));return a==null?null:new long[]{a.optLong(0),a.optLong(1)};
 }
 public static synchronized List<Entry> recent(Context c){return recent(c,ProfileStore.current(c));}
 public static synchronized List<Entry> recent(Context c,String profile){
  List<Entry> out=new ArrayList<Entry>();JSONObject o=all(c,profile);Iterator<String> k=o.keys();
  while(k.hasNext()){JSONObject x=o.optJSONObject(k.next());if(x!=null)out.add(entry(x));}
  Collections.sort(out,(a,b)->Long.compare(b.ts,a.ts));return out;
 }
 private static Entry entry(JSONObject x){
  Entry e=new Entry();e.link=x.optString("link",null);e.title=x.optString("title",null);e.poster=x.optString("poster",null);
  e.season=x.isNull("season")?null:x.optString("season",null);e.episode=x.isNull("episode")?null:x.optString("episode",null);
  e.voice=x.optString("voice",null);e.voiceName=x.optString("voiceName",null);e.pos=x.optLong("pos");e.dur=x.optLong("dur");e.ts=x.optLong("ts");e.observed=x.optBoolean("observed");e.watchedMs=x.optLong("watched");return e;
 }

 // ---------- watch later (same file format as the home screen used) ----------
 private static File laterFile(Context c){return new File(c.getFilesDir(),"kik_later_"+ProfileStore.current(c)+".json");}
 public static synchronized JSONArray later(Context c){return later(c,ProfileStore.current(c));}
 public static synchronized JSONArray later(Context c,String profile){try{return new JSONArray(Site.read(new android.util.AtomicFile(file(c,"later",profile)).openRead()));}catch(Exception e){return new JSONArray();}}
 public static synchronized boolean isLater(Context c,String link){JSONArray a=later(c);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&norm(o.optString("link")).equals(norm(link)))return true;}return false;}
 /** Adds or removes; returns the new state. */
 public static synchronized boolean toggleLater(Context c,String link,String title,String poster,String year,String genre){
  JSONArray a=later(c),next=new JSONArray();boolean found=false;
  for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&norm(o.optString("link")).equals(norm(link))){found=true;continue;}next.put(o);}
  JSONArray out=new JSONArray();
  try{if(!found)out.put(new JSONObject().put("link",link).put("title",title).put("poster",poster).put("year",year).put("genre",genre));for(int i=0;i<next.length();i++)out.put(next.get(i));
   atomicWrite(laterFile(c),out.toString());}catch(Exception e){android.util.Log.w("KIK","Save watch list",e);return found;}
  return !found;
 }
}
