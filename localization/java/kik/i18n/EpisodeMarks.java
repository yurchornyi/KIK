package kik.i18n;
import android.content.Context;
import java.net.*;
import org.json.*;

/** Intro and closing credits of a series episode (web/episode-marks.mjs), found by the KIK scanner PC for profiles with
 *  «Пропуск реклами й заставок». Asking also puts the episode in the scanner's queue, so the player asks for the next
 *  episode too. Marks are kept for good; «not scanned yet» or «nothing found» is asked again after 2 hours. */
final class EpisodeMarks {
 private EpisodeMarks(){}
 private static final long RETRY=2L*3600*1000;
 private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("kik_episode_marks",0);}
 /** {scanned duration, credits or -1, intro start, end, start, end…} in seconds, or null. */
 static double[] get(Context c,String link,String voice,String season,String episode,String profile){
  String key=Progress.norm(link);if(key.startsWith("uaserials/")||voice==null||season==null||episode==null)return null;
  String id=key+"|"+voice+"|"+season+"|"+episode;android.content.SharedPreferences p=prefs(c);
  String kept=p.getString(id,null);double[] known=parse(kept);
  if(known!=null&&(known.length>2||known[1]>=0)||kept!=null&&System.currentTimeMillis()-p.getLong(id+"@",0)<RETRY)return known;
  HttpURLConnection h=null;
  try{h=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+"/api/marks?link="+enc("https://hdrezka.sh/"+key)+"&voice="+enc(voice)+"&season="+enc(season)+"&episode="+enc(episode)+(profile==null?"":"&profile_id="+enc(profile))).openConnection();
   CloudSync.authorize(h);h.setConnectTimeout(8000);h.setReadTimeout(15000);
   if(h.getResponseCode()!=200)return known;String body=Site.read(h.getInputStream());
   p.edit().putString(id,body).putLong(id+"@",System.currentTimeMillis()).apply();return parse(body);
  }catch(Exception x){android.util.Log.w("KIK","Episode marks",x);return known;}finally{if(h!=null)h.disconnect();}
 }
 private static String enc(String s)throws java.io.UnsupportedEncodingException{return URLEncoder.encode(s,"UTF-8");}
 static double[] parse(String json){
  if(json==null)return null;
  try{JSONObject o=new JSONObject(json);if(!o.has("duration"))return null;JSONArray a=o.optJSONArray("intro");int n=a==null?0:a.length();
   double[] r=new double[2+2*n];r[0]=o.getDouble("duration");r[1]=o.has("credits")&&!o.isNull("credits")?o.getDouble("credits"):-1;
   for(int i=0;i<n;i++){JSONArray x=a.getJSONArray(i);r[2+2*i]=x.getDouble(0);r[3+2*i]=x.getDouble(1);}return r;
  }catch(Exception x){return null;}
 }
}
