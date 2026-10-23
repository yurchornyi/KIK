package kik.i18n;
import android.content.Context;
import java.net.*;
import org.json.*;

/** Ad clips inside a sponsored voice track, found by the KIK scanner PC. One small request per episode.
 *  Confirmed breaks are kept for good; while only AI hints (or nothing) are known, the answer is asked again after 12 hours. */
final class AdBreaks {
 private AdBreaks(){}
 private static final long RETRY=12L*3600*1000;
 private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("kik_ad_breaks_v2",0);}
 /** {breaks, hints}, each {scanned duration, start, end, start, end…} in seconds or null. */
 static double[][] get(Context c,String link,String voice,String season,String episode){
  String key=Progress.norm(link);if(key.startsWith("uaserials/")||voice==null)return null;
  String s=season==null?"":season,e=episode==null?"":episode,id=key+"|"+voice+"|"+s+"|"+e;android.content.SharedPreferences p=prefs(c);
  String kept=p.getString(id,null);double[][] known=parse(kept);
  if(known!=null&&known[0]!=null&&known[1]==null||kept!=null&&System.currentTimeMillis()-p.getLong(id+"@",0)<RETRY)return known;
  HttpURLConnection h=null;
  try{h=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+"/api/ad-breaks?link="+URLEncoder.encode("https://hdrezka.sh/"+key,"UTF-8")+"&voice="+URLEncoder.encode(voice,"UTF-8")+"&season="+URLEncoder.encode(s,"UTF-8")+"&episode="+URLEncoder.encode(e,"UTF-8")).openConnection();h.setConnectTimeout(8000);h.setReadTimeout(15000);
   if(h.getResponseCode()!=200)return known;String body=Site.read(h.getInputStream());double[][] got=parse(body);
   p.edit().putString(id,body).putLong(id+"@",System.currentTimeMillis()).apply();return got;
  }catch(Exception x){android.util.Log.w("KIK","Ad breaks",x);return known;}finally{if(h!=null)h.disconnect();}
 }
 static double[][] parse(String json){if(json==null)return null;try{JSONObject o=new JSONObject(json);if(!o.has("duration"))return new double[2][];double d=o.getDouble("duration");return new double[][]{ranges(d,o.optJSONArray("breaks")),ranges(d,o.optJSONArray("suggested"))};}catch(Exception x){return null;}}
 private static double[] ranges(double duration,JSONArray a)throws JSONException{if(a==null||a.length()==0)return null;double[] r=new double[1+2*a.length()];r[0]=duration;for(int i=0;i<a.length();i++){JSONArray x=a.getJSONArray(i);r[1+2*i]=x.getDouble(0);r[2+2*i]=x.getDouble(1);}return r;}
}
