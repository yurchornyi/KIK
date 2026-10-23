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
 static String norm(String link){if(link==null)return "";String prefix=FreeSources.ua(link)?"uaserials/":"";String s=link.replaceFirst("^https?://[^/]+","");while(s.startsWith("/"))s=s.substring(1);return prefix+s;}

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
  write(c,o);CloudSync.changed(c);
 }
 public static synchronized void remove(Context c,String link){JSONObject o=all(c);o.remove(norm(link));write(c,o);CloudSync.removedHistory(c,ProfileStore.current(c),link);}
 public static synchronized Entry get(Context c,String link){JSONObject x=all(c).optJSONObject(norm(link));return x==null?null:entry(x);}
 /** Position and duration of one episode, or null. */
 public static synchronized long[] episode(Context c,String link,String season,String episode){
  JSONObject x=all(c).optJSONObject(norm(link));if(x==null)return null;JSONObject eps=x.optJSONObject("eps");if(eps==null)return null;
  JSONArray a=eps.optJSONArray((season==null?"0":season)+":"+(episode==null?"0":episode));return a==null?null:new long[]{a.optLong(0),a.optLong(1)};
 }
 /** Episodes of a series watched to the credits (93%, as Entry.finished). */
 public static synchronized int finishedEpisodes(Context c,String link){
  JSONObject x=all(c).optJSONObject(norm(link)),eps=x==null?null:x.optJSONObject("eps");if(eps==null)return 0;int n=0;
  Iterator<String> k=eps.keys();while(k.hasNext()){JSONArray a=eps.optJSONArray(k.next());if(a!=null&&a.optLong(1)>0&&a.optLong(0)>a.optLong(1)*0.93)n++;}return n;
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

 static synchronized JSONObject cloudData(Context c,String p){try{return new JSONObject(all(c,p).toString());}catch(Exception e){return new JSONObject();}}
 static synchronized void cloudApply(Context c,String p,JSONObject progress,JSONArray later)throws IOException{atomicWrite(file(c,"progress",p),progress.toString());atomicWrite(file(c,"later",p),later.toString());}
 // ---------- watch later (same file format as the home screen used) ----------
 /** Voice the viewer chose explicitly for one title in this profile; wins over automatic ordering. */
 public static synchronized String voice(Context c,String link){JSONObject chosen=choices(c,link);if(chosen!=null&&!chosen.optString("voice").isEmpty())return chosen.optString("voice");try{return new JSONObject(Site.read(new android.util.AtomicFile(file(c,"voices")).openRead())).optString(norm(link),null);}catch(Exception e){return null;}}
 public static synchronized void chooseVoice(Context c,String link,String voice){
  if(link==null||voice==null)return;JSONObject o;try{o=new JSONObject(Site.read(new android.util.AtomicFile(file(c,"voices")).openRead()));}catch(Exception e){o=new JSONObject();}
  try{o.remove(norm(link));o.put(norm(link),voice);while(o.length()>500)o.remove(o.keys().next());atomicWrite(file(c,"voices"),o.toString());}catch(Exception e){android.util.Log.w("KIK","Save voice",e);}
  choose(c,link,voice,"voice",voice);
 }
 /** Voice, quality and speed the viewer picked for one title in this profile (null if none): the player opens with them
  *  next time; synced as the «voice» record, the same as on the web. */
 public static synchronized JSONObject choices(Context c,String link){return cloudChoices(c,ProfileStore.current(c)).optJSONObject(norm(link));}
 /** Remembers one pick for the title; the voice playing now is kept with it, as the synced record needs one. */
 public static synchronized void choose(Context c,String link,String voice,String key,Object value){
  if(link==null||norm(link).isEmpty())return;String p=ProfileStore.current(c);JSONObject all=cloudChoices(c,p);
  try{JSONObject o=all.optJSONObject(norm(link));if(o==null)o=new JSONObject();all.remove(norm(link));o.put("link",link).put(key,value).put("ts",System.currentTimeMillis());if(o.optString("voice").isEmpty()&&voice!=null)o.put("voice",voice);all.put(norm(link),o);
   while(all.length()>500)all.remove(all.keys().next());atomicWrite(file(c,"choices",p),all.toString());}catch(Exception e){android.util.Log.w("KIK","Save choices",e);}
 }
 static synchronized JSONObject cloudChoices(Context c,String p){try{return new JSONObject(Site.read(new android.util.AtomicFile(file(c,"choices",p)).openRead()));}catch(Exception e){return new JSONObject();}}
 static synchronized void cloudApplyChoices(Context c,String p,JSONObject all)throws IOException{atomicWrite(file(c,"choices",p),all.toString());}
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
   atomicWrite(laterFile(c),out.toString());CloudSync.changed(c);}catch(Exception e){android.util.Log.w("KIK","Save watch list",e);return found;}
  return !found;
 }
}
