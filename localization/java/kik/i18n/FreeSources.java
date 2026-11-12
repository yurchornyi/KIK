package kik.i18n;
import java.io.*;import java.net.*;import java.util.*;import org.json.*;
/** Bounded public data requests to KIK's fixed metadata service; no user credentials. */
final class FreeSources {
 static boolean rezka(String link){return link!=null&&link.matches("^https://(hdrezka\\.sh|hdrezka\\.website|rezka\\.ag|hdrezka\\.ag)/(films|series|cartoons|animation|documentary)/[a-z0-9-]+/[0-9]+-[^/?#]+\\.html$");}
 static boolean ua(String link){return link!=null&&link.matches("^https://uaserials\\.com/[0-9]+-[a-z0-9-]+\\.html");}
 static String canonical(String key){return key.startsWith("uaserials/")?"https://uaserials.com/"+key.substring(10):"https://hdrezka.sh/"+key;}
 static JSONObject get(String path)throws IOException{return get(path,6500);}
 static JSONObject get(String path,int timeout)throws IOException{HttpURLConnection c=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+path).openConnection();CloudSync.authorize(c);c.setConnectTimeout(4500);c.setReadTimeout(timeout);c.setInstanceFollowRedirects(false);try{if(c.getResponseCode()!=200)throw refused(c);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("Public data limit");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}}catch(JSONException e){throw new IOException(e);}finally{c.disconnect();}}
 /** POST to KIK's own service, signed in as this TV; used when HDRezka refuses the home network. */
 static JSONObject post(String path,JSONObject body,int timeout)throws IOException{HttpURLConnection c=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+path).openConnection();CloudSync.authorize(c);c.setConnectTimeout(4500);c.setReadTimeout(timeout);c.setInstanceFollowRedirects(false);c.setDoOutput(true);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json");
  try{try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes("UTF-8"));}if(c.getResponseCode()!=200)throw refused(c);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("Public data limit");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}}catch(JSONException e){throw new IOException(e);}finally{c.disconnect();}}
 /** The service's refusal as the «Упс» screens read it: its status, and retry:true when the film source is busy (503). */
 private static IOException refused(HttpURLConnection c)throws IOException{
  int code=c.getResponseCode();String message="Service HTTP "+code;boolean retry=false;
  try(InputStream in=c.getErrorStream()){if(in!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while(out.size()<16384&&(n=in.read(b))!=-1)out.write(b,0,n);
   JSONObject o=new JSONObject(out.toString("UTF-8"));message=o.optString("error",message);retry=o.optBoolean("retry");}}catch(IOException|JSONException ignored){}
  return new OopsPolicy.ServiceError(code,retry,message);
 }
 static String enc(String v){try{return URLEncoder.encode(v,"UTF-8");}catch(Exception e){return "";}}
 /** Cached public film facts for background UI. Stream URLs still come from Site after the viewer starts playback. */
 static Site.Film metadata(String link)throws IOException{
  if(!rezka(link))throw new IOException("Source link");JSONObject d=get("/api/film?link="+enc(link),20000);
  Site.Film f=new Site.Film();f.link=link;f.id=Site.opt(d,"id");f.title=d.optString("sourceTitle",Site.opt(d,"title"));f.original=Site.opt(d,"original");f.poster=Site.opt(d,"poster");f.description=Site.opt(d,"description");f.year=Site.opt(d,"year");f.country=Site.opt(d,"country");f.duration=Site.opt(d,"duration");f.age=Site.opt(d,"age");f.rating=Site.opt(d,"rating");f.imdb=Site.opt(d,"imdb");f.series=d.optBoolean("series");
  if(f.title==null||f.title.isEmpty())throw new IOException("Film metadata unavailable");
  JSONArray genres=d.optJSONArray("genres");for(int i=0;genres!=null&&i<genres.length();i++)f.genres.add(genres.optString(i));
  JSONArray episodes=d.optJSONArray("episodes");for(int i=0;episodes!=null&&i<episodes.length();i++){JSONObject x=episodes.optJSONObject(i);if(x==null)continue;Site.Episode e=new Site.Episode();e.season=x.optString("season");e.episode=x.optString("episode");e.title=Site.opt(x,"title");if(!e.season.isEmpty()&&!e.episode.isEmpty()){f.episodes.add(e);if(!f.seasons.contains(e.season))f.seasons.add(e.season);}}
  JSONArray related=d.optJSONArray("related");for(int i=0;related!=null&&i<related.length();i++){JSONObject x=related.optJSONObject(i);if(x==null||!rezka(x.optString("link")))continue;Site.Card c=new Site.Card();c.link=x.optString("link");c.title=x.optString("sourceTitle",x.optString("title"));c.poster=Site.opt(x,"poster");c.info=x.optString("sourceInfo",Site.opt(x,"info"));f.related.add(c);}
  return f;
 }
 static List<Site.Card> cards(String query)throws IOException{JSONArray a=get("/api/catalogue?source=uaserials"+(query==null?"":"&query="+enc(query))).optJSONArray("items");List<Site.Card> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){JSONObject d=a.optJSONObject(i);// A title also on HDRezka arrives as that playable card under its Ukrainian name.
   if(d==null)continue;boolean twin=rezka(d.optString("link"));if(!twin)continue;Site.Card c=new Site.Card();c.link=d.optString("link");c.title=d.optString("title");c.poster=Site.opt(d,"poster");c.info=twin?Site.opt(d,"info"):"Українською";c.type=twin?Site.opt(d,"type"):"KIK";out.add(c);}return out;}
 static Site.Film film(String link)throws IOException{if(!ua(link))throw new IOException("Source link");JSONObject d=get("/api/film?link="+enc(link));
  // The server found the same title on HDRezka: open it natively, with seasons, episodes and voices.
  if(rezka(d.optString("link"))){Site.Film f=Site.film(d.optString("link"));if(!d.optString("title").isEmpty())f.title=d.optString("title");if(!d.optString("description").isEmpty())f.description=d.optString("description");return f;}
  Site.Film f=new Site.Film();f.link=link;f.id=d.optString("id");f.title=d.optString("title");f.original=Site.opt(d,"original");f.poster=Site.opt(d,"poster");f.description=d.optString("description","");f.year=Site.opt(d,"year");f.imdb=Site.opt(d,"imdb");f.sourceUrl=link;f.series=d.optBoolean("series");JSONArray genres=d.optJSONArray("genres");if(genres!=null)for(int i=0;i<genres.length();i++)f.genres.add(genres.optString(i));return f;}
 static void enrich(Site.Film f)throws IOException{if(f.imdb==null||!f.imdb.matches("tt[0-9]{5,12}"))return;JSONObject m=get("/api/metadata?imdb="+f.imdb+"&kind="+(f.series?"tv":"movie"));if(f.sourceUrl==null&&!m.isNull("title")&&!m.optString("title").isEmpty())f.title=m.optString("title");if(!m.isNull("overview")&&!m.optString("overview").isEmpty())f.description=m.optString("overview");JSONObject n=m.optJSONObject("nextEpisode");if(n!=null)f.nextEpisode="Наступна серія: "+n.optString("airdate")+" • S"+n.optInt("season")+"E"+n.optInt("episode")+"\nДата виходу не гарантує наявності озвучення.";JSONArray credits=m.optJSONArray("credits");if(credits!=null&&credits.length()>0){StringBuilder b=new StringBuilder();for(int i=0;i<credits.length();i++){JSONObject v=credits.optJSONObject(i);if(v==null)continue;if(b.length()>0)b.append(" • ");b.append(v.optString("name")).append(' ').append(v.optString("license",""));}f.metadataCredit=b.toString();}}
 /** Titles like this one that KIK can play, for «Вам може сподобатися»; empty when the service is unreachable. */
 static List<Site.Card> similar(String link){List<Site.Card> out=new ArrayList<>();try{JSONArray a=get("/api/similar?link="+enc(link),15000).optJSONArray("items");for(int i=0;a!=null&&i<a.length();i++){JSONObject d=a.optJSONObject(i);if(d==null||!rezka(d.optString("link")))continue;Site.Card c=new Site.Card();c.link=d.optString("link");c.title=d.optString("title");c.poster=Site.opt(d,"poster");c.info=Site.opt(d,"info");if(!CountryFilter.blocked(c.info))out.add(c);}}catch(Exception ignored){}return out;}
 // The service may ask a model for the spelling the first time, which takes a few seconds.
 static List<String> aliases(String query){List<String> out=new ArrayList<>();try{JSONArray a=get("/api/search-aliases?query="+enc(query),14000).optJSONArray("aliases");for(int i=0;a!=null&&i<Math.min(2,a.length());i++)out.add(a.getString(i));}catch(Exception ignored){}return out;}
 private FreeSources(){}
}
