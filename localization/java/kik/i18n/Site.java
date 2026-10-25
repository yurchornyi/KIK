package kik.i18n;
import android.content.*;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import org.json.*;

/**
 * KIK's own client for the catalogue site: passes the Anubis proof-of-work check, keeps its cookies,
 * and parses catalogue, search, film pages and stream links. Call only off the main thread.
 */
public final class Site {
 static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";
 static final String[] HOSTS={"hdrezka.sh","hdrezka.website","rezka.ag","hdrezka.ag"};
 private static String host;
 private static final Map<String,String> COOKIES=new LinkedHashMap<String,String>();
 private static boolean loaded;
 private static final ThreadLocal<String> PAGE_HOST=new ThreadLocal<String>();
 private static final ThreadLocal<Integer> PAGE_CODE=new ThreadLocal<Integer>();

 // ---------- models ----------
 public static final class Card{public String link,title,poster,info,type,studio;public String year(){Matcher m=Pattern.compile("(\\d{4}(?:\\s*-\\s*(?:\\d{4}|\\.\\.\\.))?)").matcher(info==null?"":info);return m.find()?m.group(1).replace(" ",""):null;}
  public String genre(){if(info==null)return null;String[] p=info.split(",");return p.length>0?p[p.length-1].trim():null;}}
 public static final class Voice{public String id,name;public boolean premium,camrip,ads,director;}
 public static final class Episode{public String season,episode,title;}
 public static final class Film{
  public String link,id,title,original,poster,description,year,country,duration,age,favs,rating,ratingName,origin,imdb,sourceUrl,nextEpisode,metadataCredit;
  public boolean series;public List<String> genres=new ArrayList<String>();
  public List<String> people=new ArrayList<String>();
  public List<Voice> voices=new ArrayList<Voice>();public List<Card> related=new ArrayList<Card>();public String defaultVoice,defaultVoiceName;public boolean defaultCamrip,defaultAds,defaultDirector;
  public List<String> seasons=new ArrayList<String>();public List<Episode> episodes=new ArrayList<Episode>();
  /** Episodes each voice covers, loaded on demand: voices differ in which seasons they have. */
  final Map<String,List<Episode>> coverage=new HashMap<String,List<Episode>>();
 }
 public static final class Stream{public LinkedHashMap<String,String> qualities=new LinkedHashMap<String,String>();public LinkedHashMap<String,List<String>> alternatives=new LinkedHashMap<String,List<String>>();public String subtitles;public List<String[]> subtitleList=new ArrayList<String[]>();}
 public static final class Genre{public String slug,name;}

 // ---------- transport ----------
 private static SharedPreferences prefs(){return UiRuntime.app.getSharedPreferences("kik_site",0);}
 private static synchronized void load(){
  if(loaded)return;loaded=true;
  host=prefs().getString("host",HOSTS[0]);blockedUntil=prefs().getLong("blocked_until",0);
  try{JSONObject o=new JSONObject(prefs().getString("cookies","{}"));Iterator<String> k=o.keys();while(k.hasNext()){String n=k.next();COOKIES.put(n,o.getString(n));}}catch(Exception ignored){}
 }
 private static synchronized void saveCookies(){prefs().edit().putString("cookies",new JSONObject(COOKIES).toString()).putString("host",host).apply();}
 public static String host(){load();return host;}
 public static String abs(String path){if(path==null)return null;if(path.startsWith("http"))return path;if(path.startsWith("//"))return "https:"+path;return "https://"+host()+(path.startsWith("/")?"":"/")+path;}

 private static String cookieHeader(){StringBuilder b=new StringBuilder();synchronized(Site.class){for(Map.Entry<String,String> e:COOKIES.entrySet()){if(b.length()>0)b.append("; ");b.append(e.getKey()).append('=').append(e.getValue());}}return b.toString();}
 private static void takeCookies(HttpURLConnection c){
  List<String> set=c.getHeaderFields().get("Set-Cookie");if(set==null)set=c.getHeaderFields().get("set-cookie");if(set==null)return;
  synchronized(Site.class){for(String s:set){String pair=s.split(";",2)[0];int eq=pair.indexOf('=');if(eq>0)COOKIES.put(pair.substring(0,eq).trim(),pair.substring(eq+1).trim());}}
  saveCookies();
 }
 private static boolean mirror(String host){if(host==null)return false;for(String h:HOSTS)if(h.equalsIgnoreCase(host))return true;return false;}
 // The catalogue bans a whole home network for bursts of requests: two at a time, spaced out, and none at all while a ban lasts.
 private static final Semaphore SLOTS=new Semaphore(2,true);
 private static final long GAP_MS=300,BAN_PAUSE_MS=30*60_000L;
 private static long nextAt;private static volatile long blockedUntil;
 static final String IP_BLOCKED="Source blocked this IP";
 /** HDRezka's own 403 page: it refuses this network's public IP until it changes or the ban expires. */
 static boolean blocked(String html){return html!=null&&html.contains("подозрительная активность с вашего IP");}
 /** True for the error {@link #page} and {@link #film} throw while the catalogue bans this network. */
 public static boolean blocked(Throwable e){for(;e!=null;e=e.getCause())if(e.getMessage()!=null&&e.getMessage().contains(IP_BLOCKED))return true;return false;}
 private static void ban(){blockedUntil=System.currentTimeMillis()+BAN_PAUSE_MS;Log.w("KIK","Catalogue banned this IP; pausing requests for 30 min");if(UiRuntime.app!=null)prefs().edit().putLong("blocked_until",blockedUntil).apply();}
 /** The viewer asked to try again (say, after restarting the router): one request may test whether the ban is over. */
 public static void retryNow(){blockedUntil=0;if(UiRuntime.app!=null)prefs().edit().remove("blocked_until").apply();}
 private static String send(String url,String form,boolean xhr)throws IOException{
  if(!mirror(new URL(url).getHost()))return transfer(url,form,xhr);
  load();if(System.currentTimeMillis()<blockedUntil)throw new IOException(IP_BLOCKED);
  try{SLOTS.acquire();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Request cancelled");}
  try{
   long wait;synchronized(SLOTS){long now=System.currentTimeMillis();wait=Math.max(0,nextAt-now);nextAt=Math.max(now,nextAt)+GAP_MS;}
   if(wait>0)try{Thread.sleep(wait);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Request cancelled");}
   String body=transfer(url,form,xhr);
   if(blocked(body)){ban();throw new IOException(IP_BLOCKED);}
   return body;
  }finally{SLOTS.release();}
 }
 private static String transfer(String url,String form,boolean xhr)throws IOException{
  for(int hop=0;hop<6;hop++){
   HttpURLConnection c=Doh.open(url);
   c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(15000);
   c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept-Encoding","gzip");c.setRequestProperty("Accept-Language","ru-RU,ru;q=0.9,uk;q=0.8");
   // The catalogue's cookies go only to its own mirrors, never to a host a redirect points at.
   boolean own=mirror(new URL(url).getHost());String ck=own?cookieHeader():"";if(!ck.isEmpty())c.setRequestProperty("Cookie",ck);
   if(xhr){String base=new URL(url).getProtocol()+"://"+new URL(url).getHost();c.setRequestProperty("X-Requested-With","XMLHttpRequest");c.setRequestProperty("Referer",base+"/");c.setRequestProperty("Origin",base);}
   if(form!=null){c.setDoOutput(true);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");OutputStream o=c.getOutputStream();o.write(form.getBytes("UTF-8"));o.close();}
   int code=c.getResponseCode();if(own)takeCookies(c);
   if(code>=300&&code<400&&c.getHeaderField("Location")!=null){url=new URL(new URL(url),c.getHeaderField("Location")).toString();form=null;c.disconnect();continue;}
   InputStream in=code>=400?c.getErrorStream():c.getInputStream();
   String body=read(in);PAGE_HOST.set(new URL(url).getHost());PAGE_CODE.set(code);c.disconnect();
   if(code>=500)throw new IOException("HTTP "+code);
   return body;
  }
  throw new IOException("Too many redirects");
 }
 static String read(InputStream in)throws IOException{
  if(in==null)return "";
  BufferedInputStream b=new BufferedInputStream(in);b.mark(2);int a=b.read(),z=b.read();b.reset();
  InputStream s=(a==0x1f&&z==0x8b)?new GZIPInputStream(b):b;
  ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;while((n=s.read(buf))>0)o.write(buf,0,n);s.close();return o.toString("UTF-8");
 }

 /** Page fetch that solves the bot check when it appears and tries other mirrors on network failure. */
 public static String page(String path)throws IOException{
  load();IOException last=null;
  if(path.startsWith("http")){URL u=new URL(path);if(Arrays.asList(HOSTS).contains(u.getHost()))path=u.getFile();}
  final boolean mirror=!path.startsWith("http");String status=null;StringBuilder detail=new StringBuilder();boolean cancelled=false;
  for(int attempt=0;attempt<HOSTS.length;attempt++){
   if(Thread.currentThread().isInterrupted())throw new IOException("Request cancelled");
   String url=abs(path),tried=new URL(url).getHost();
   try{
    String h=send(url,null,false);
    if(h.contains("anubis_challenge")){h=solve(h,url);}
    if(h.contains("anubis_challenge"))throw new IOException("bot check not passed");
    if(mirror){Integer code=PAGE_CODE.get();if(code!=null&&(code==403||code==451))SiteReport.failed("http",tried,tried+" HTTP "+code);else SiteReport.ok(tried,Doh.used(tried));}
    return h;
   }catch(IOException e){
    if(blocked(e))throw e;
    last=e;Log.w("KIK","Site "+host+": "+e);
    String kind=SiteReport.kind(e);if(kind==null)cancelled=true;else{status=status==null||status.equals(kind)?kind:"mixed";if(detail.length()>0)detail.append(", ");detail.append(SiteReport.detail(tried,kind,e));}
    if(path.startsWith("http"))path=path.replaceFirst("^https?://[^/]+","");
    synchronized(Site.class){host=HOSTS[(Arrays.asList(HOSTS).indexOf(host)+1+HOSTS.length)%HOSTS.length];}
   }
  }
  // Every mirror failed: tell the admin how, unless the app itself cancelled the request.
  if(mirror&&!cancelled&&status!=null)SiteReport.failed(status,host,detail.toString());
  throw last;
 }
 private static String solve(String html,String url)throws IOException{
  try{
   Matcher m=Pattern.compile("id=\"anubis_challenge\"[^>]*>(.*?)</script>",Pattern.DOTALL).matcher(html);if(!m.find())return html;
   JSONObject ch=new JSONObject(m.group(1));JSONObject c=ch.getJSONObject("challenge");int d=ch.getJSONObject("rules").optInt("difficulty",2);
   String data=c.getString("randomData"),prefix=new String(new char[d]).replace('\0','0');
   MessageDigest sha=MessageDigest.getInstance("SHA-256");long t=System.currentTimeMillis();String hash=null;int nonce=0;
   for(;;nonce++){byte[] h=sha.digest((data+nonce).getBytes("UTF-8"));String hex=hex(h);if(hex.startsWith(prefix)){hash=hex;break;}}
   long el=System.currentTimeMillis()-t+500;
   String q="id="+URLEncoder.encode(c.getString("id"),"UTF-8")+"&response="+hash+"&nonce="+nonce+"&redir="+URLEncoder.encode(url,"UTF-8")+"&elapsedTime="+el;
   Log.d("KIK","Site check solved with difficulty "+d);
   return send("https://"+new URL(url).getHost()+"/.within.website/x/cmd/anubis/api/pass-challenge?"+q,null,false);
  }catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}
 }
 private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}
 static String text(String html){if(html==null)return null;return unescape(html.replaceAll("(?s)<br\\s*/?>","\n").replaceAll("(?s)<[^>]+>","")).replaceAll("[ \\t\\x0B\\f\\r]+"," ").trim();}
 static String unescape(String s){return s.replace("&nbsp;"," ").replace("&quot;","\"").replace("&#039;","'").replace("&laquo;","«").replace("&raquo;","»").replace("&mdash;","—").replace("&ndash;","–").replace("&hellip;","…").replace("&lt;","<").replace("&gt;",">").replace("&amp;","&");}
 private static String find(String s,String re){Matcher m=Pattern.compile(re,Pattern.DOTALL).matcher(s);return m.find()?m.group(1):null;}

 // ---------- catalogue ----------
 /** category: films, series, cartoons, animation or "" for everything; filter: last, popular, watching. */
 public static String listPath(String category,String genre,String filter,int page){
  String base=category==null||category.isEmpty()?"/":"/"+category+"/"+(genre==null||genre.isEmpty()?"":genre+"/");
  return base+(page>1?"page/"+page+"/":"")+"?filter="+(filter==null?"last":filter);
 }
 public static List<Card> cards(String html){
  List<Card> out=new ArrayList<Card>();
  String[] chunks=html.split("<div class=\"b-content__inline_item\"");int raw=0;
  for(int i=1;i<chunks.length;i++){
   String b=chunks[i];String link=find(b,"^[^>]*data-url=\"([^\"]+)\"");if(link==null)continue;
   b=b.replaceAll("(?s)<!--.*?-->","");
   Card c=new Card();c.link=link;
   c.poster=find(b,"<img src=\"([^\"]+)\"");
   c.title=text(find(b,"b-content__inline_item-link\">\\s*<a[^>]*>(.*?)</a>"));
   c.info=text(find(b,"b-content__inline_item-link\">\\s*<a[^>]*>.*?</a>\\s*<div[^>]*>(.*?)</div>"));
   c.type=text(find(b,"<i class=\"entity\">(.*?)</i>"));
   if(c.title==null)continue;raw++;
   if(!CountryFilter.blocked(c.info))out.add(c);
  }
  RAW.set(raw);return out;
 }
 private static final ThreadLocal<Integer> RAW=new ThreadLocal<Integer>();
 /** Titles on the last parsed page of this thread before the country filter; 0 means the source has no more pages. */
 public static int lastRaw(){Integer n=RAW.get();return n==null?0:n;}
 public static List<Card> list(String category,String genre,String filter,int page)throws IOException{return cards(listPage(listPath(category,genre,filter,page)));}
 private static final long LIST_TTL_MS=30*60_000L;
 /** Catalogue lists change slowly: reopening the home screen reads them from disk for half an hour instead of asking the site again. */
 private static String listPage(String path)throws IOException{
  File f=UiRuntime.app==null?null:new File(UiRuntime.app.getCacheDir(),"site_list_"+Integer.toHexString(path.hashCode())+".html");
  if(f!=null&&f.isFile()&&System.currentTimeMillis()-f.lastModified()<LIST_TTL_MS)try{return read(new FileInputStream(f));}catch(IOException ignored){}
  String html=page(path);
  if(f!=null&&html.contains("b-content__inline_item"))try{FileOutputStream o=new FileOutputStream(f);o.write(html.getBytes("UTF-8"));o.close();}catch(IOException ignored){}
  return html;
 }
 public static List<Card> studioList(String category,String genre,String filter,String studio,int page)throws IOException{
  JSONObject result=FreeSources.get("/api/catalogue?category="+FreeSources.enc(category)+"&genre="+FreeSources.enc(genre==null?"":genre)+"&filter="+FreeSources.enc(filter)+"&studio="+FreeSources.enc(studio)+"&page="+page,60000);
  List<Card> out=new ArrayList<Card>();JSONArray items=result.optJSONArray("items");if(items==null)throw new IOException("Studio catalogue unavailable");
  for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null||!FreeSources.rezka(item.optString("link")))continue;Card c=new Card();c.link=item.optString("link");c.title=item.optString("title");c.poster=item.optString("poster",null);c.info=item.optString("sourceInfo",item.optString("info",null));c.type=item.optString("type",null);c.studio=Studios.id(item.optJSONArray("studios"));if(!CountryFilter.blocked(c.info))out.add(c);}
  RAW.set(result.optBoolean("hasMore",false)?1:0);return out;
 }
 public static List<Card> search(String query,int page)throws IOException{return search(query,page,true);}
 /** fix=false for lookups by a known name (title pages, deep links): other names only when nothing was found, as before. */
 public static List<Card> search(String query,int page,boolean fix)throws IOException{
  // Ukrainian names come from KIK's own service; ask it while the source search runs instead of after it.
  FutureTask<List<Card>> ua=null;if(page==1){ua=new FutureTask<List<Card>>(()->FreeSources.cards(query));Thread t=new Thread(ua,"kik-search-ua");t.setDaemon(true);t.start();}
  List<Card> out=new ArrayList<Card>();try{out.addAll(cards(page("/search/?do=search&subaction=search&q="+URLEncoder.encode(query,"UTF-8")+(page>1?"&page="+page:""))));}catch(IOException e){if(page>1)throw e;}
  // The source spells titles in Russian: retry Ukrainian letters with their closest Russian forms, then other names.
  String alt=searchForm(query);if(out.isEmpty()&&lastRaw()==0&&!alt.equals(query.toLowerCase(Locale.ROOT).trim()))try{out.addAll(cards(page("/search/?do=search&subaction=search&q="+URLEncoder.encode(alt,"UTF-8")+(page>1?"&page="+page:""))));}catch(IOException e){if(page>1)throw e;}
  out=new ArrayList<Card>(relevant(query,out));
  // A typo, the wrong keyboard layout or a name in another language: KIK's service names the titles it most likely meant, and those go first.
  if(page==1&&(fix?!matches(query,out):out.isEmpty())){List<Card> fixed=new ArrayList<Card>();for(String alias:FreeSources.aliases(query)){try{fixed.addAll(relevant(alias,cards(page("/search/?do=search&subaction=search&q="+URLEncoder.encode(alias,"UTF-8")))));}catch(IOException ignored){}if(!fixed.isEmpty())break;}
   if(!fixed.isEmpty()){fixed.addAll(out);out=fixed;}}
  // One card per title: a UASerials twin of an HDRezka result replaces it in place with the Ukrainian name.
  if(page==1)try{LinkedHashMap<String,Card> unique=new LinkedHashMap<String,Card>();for(Card c:out)unique.putIfAbsent(Progress.norm(c.link),c);
   List<Card> extra;try{extra=ua.get(8,TimeUnit.SECONDS);}catch(Exception e){ua.cancel(true);extra=new ArrayList<Card>();}
   for(Card c:extra){Card old=unique.get(Progress.norm(c.link));if(old!=null){old.title=c.title;if(old.poster==null)old.poster=c.poster;}else unique.put(Progress.norm(c.link),c);}
   out=new ArrayList<Card>(unique.values());}catch(RuntimeException ignored){}
  return out;
 }
 static String searchForm(String q){return q.toLowerCase(Locale.ROOT).trim().replace("ї","и").replace("і","и").replace("є","е").replace("ґ","г").replace("'","").replace("’","").replace("ʼ","");}
 private static String fold(String s){return searchForm(s==null?"":s).replace("ё","е").replace("ы","и").replace("э","е").replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
 /** Stable relevance order inside a result page: exact title, prefix, whole phrase, all words, then source order. */
 static List<Card> relevant(String query,List<Card> cards){
  String q=fold(query);if(q.isEmpty()||cards.size()<2)return cards;String[] words=q.split(" ");
  final Map<Card,Integer> score=new IdentityHashMap<Card,Integer>();
  for(Card c:cards){String t=fold(c.title);int s=0;
   if(t.equals(q))s=100;else if(t.startsWith(q+" ")||t.startsWith(q))s=60;else if((" "+t+" ").contains(" "+q+" "))s=45;else if(t.contains(q))s=35;
   else{int hit=0;for(String w:words)if(w.length()>1&&(" "+t).contains(" "+w))hit++;s=hit==words.length?25:hit*15/Math.max(1,words.length);}
   String year=c.year();if(year!=null)for(String w:words)if(w.length()==4&&year.startsWith(w))s+=20;
   score.put(c,s);}
  List<Card> out=new ArrayList<Card>(cards);Collections.sort(out,(a,b)->Integer.compare(score.get(b),score.get(a)));return out;
 }
 /** Does one of the first results really answer the query, typos allowed? Mirrors web/public/search-match.js. */
 static boolean matches(String query,List<Card> cards){int n=0;for(Card c:cards){if(n++>=12)break;if(c.title!=null)for(String name:c.title.split("\\s+/\\s+"))if(nameScore(query,name)>=0.6)return true;}return false;}
 private static String loose(String s){return java.text.Normalizer.normalize(s==null?"":s,java.text.Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[ії]","и").replaceAll("[єэ]","е").replace("ґ","г").replace("ё","е").replace("ы","и").replaceAll("['’ʼ]","").replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
 /** Edit distance with swaps of neighbours, stopped once it exceeds max. */
 static int distance(String a,String b,int max){if(Math.abs(a.length()-b.length())>max)return max+1;int[] prev2=null,prev=new int[b.length()+1];for(int j=0;j<=b.length();j++)prev[j]=j;
  for(int i=1;i<=a.length();i++){int[] row=new int[b.length()+1];row[0]=i;int best=i;for(int j=1;j<=b.length();j++){int d=Math.min(Math.min(prev[j]+1,row[j-1]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));if(prev2!=null&&i>1&&j>1&&a.charAt(i-1)==b.charAt(j-2)&&a.charAt(i-2)==b.charAt(j-1))d=Math.min(d,prev2[j-2]+1);row[j]=d;if(d<best)best=d;}
   if(best>max)return max+1;prev2=prev;prev=row;}return prev[b.length()];}
 private static int slack(String w){return w.length()<4?0:w.length()<7?1:2;}
 private static boolean wordHit(String w,String[] words){int k=slack(w);for(String x:words)if(x.startsWith(w)||w.length()>3&&distance(w,x.substring(0,Math.min(x.length(),w.length()+1)),k)<=k||distance(w,x,k)<=k)return true;return false;}
 static double nameScore(String query,String name){String q=loose(query),t=loose(name);if(q.isEmpty()||t.isEmpty())return 0;if(t.equals(q))return 1;if(t.startsWith(q))return .9;
  List<String> words=new ArrayList<String>();for(String w:q.split(" "))if(w.length()>1)words.add(w);if(words.isEmpty())return 0;String[] title=t.split(" ");int found=0;for(String w:words)if(wordHit(w,title))found++;if(found==words.size())return .7;
  String a=q.replace(" ",""),b=t.replace(" ","");if(a.length()>5&&distance(a,b.substring(0,Math.min(b.length(),a.length())),slack(a))<=slack(a))return .6;return .5*found/words.size();}
 public static List<Genre> genres(String category)throws IOException{
  String html=page(listPath(category,null,"last",1));List<Genre> out=new ArrayList<Genre>();Set<String> seen=new HashSet<String>();
  Matcher m=Pattern.compile("href=\"(?:https?://[^/]+)?/"+Pattern.quote(category)+"/([a-z0-9\\-]+)/\"[^>]*>([^<]+)</a>").matcher(html);
  while(m.find()){if("page".equals(m.group(1))||!seen.add(m.group(1)))continue;Genre g=new Genre();g.slug=m.group(1);g.name=unescape(m.group(2)).trim();out.add(g);}
  return out;
 }

 // ---------- film page ----------
 private static final class FilmPage{final String html,origin;final long at=System.nanoTime();FilmPage(String h,String o){html=h;origin=o;}}
 private static final Map<String,FilmPage> FILM_PAGES=new LinkedHashMap<String,FilmPage>(16,0.75f,true);
 private static final Map<String,FutureTask<FilmPage>> FILM_PENDING=new HashMap<String,FutureTask<FilmPage>>();
 public static Film film(String link)throws IOException{return film(link,false);}
 private static Film film(String link,boolean force)throws IOException{
  if(FreeSources.ua(link))return FreeSources.film(link);
  String key=Progress.norm(link);FilmPage cached;FutureTask<FilmPage> task;boolean owner=false;
  synchronized(FILM_PAGES){
   cached=force?null:FILM_PAGES.get(key);
   if(cached!=null&&System.nanoTime()-cached.at>120_000_000_000L)cached=null;
   task=force?null:FILM_PENDING.get(key);
   if(cached==null&&task==null){task=new FutureTask<FilmPage>(()->new FilmPage(page(link),PAGE_HOST.get()));if(!force)FILM_PENDING.put(key,task);owner=true;}
  }
  if(cached==null){
   if(owner)task.run();
   try{cached=task.get();synchronized(FILM_PAGES){FILM_PAGES.put(key,cached);while(FILM_PAGES.size()>16)FILM_PAGES.remove(FILM_PAGES.keySet().iterator().next());}}
   catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
   catch(ExecutionException e){throw new IOException(e.getCause());}
   finally{if(owner&&!force)synchronized(FILM_PAGES){FILM_PENDING.remove(key);}}
  }
  // Parse a separate model per caller: voice/episode changes cannot mutate another screen's film.
  Film f=parseFilm(link,cached.html);f.origin=cached.origin;
  // An error or stub page parses to a film with no title and no tracks: say the page failed instead, and fetch it again next time.
  if((f.title==null||f.title.isEmpty())&&f.voices.isEmpty()&&f.id==null){synchronized(FILM_PAGES){FILM_PAGES.remove(key);}throw new IOException("Film page unreadable: "+PAGE_CODE.get());}if(UiRuntime.app!=null)RecommendationMetadata.remember(UiRuntime.app,f);return f;
 }
 /** Kept independent of transport so catalogue markup changes can be regression-checked locally. */
 static Film parseFilm(String link,String h){
  Film f=new Film();f.link=link;f.imdb=find(h,"imdb\\.com/title/(tt[0-9]+)");
  f.title=text(find(h,"<h1[^>]*>(.*?)</h1>"));
  f.original=text(find(h,"<div class=\"b-post__origtitle\"[^>]*>(.*?)</div>"));
  f.poster=find(h,"<div class=\"b-sidecover\">\\s*<a href=\"([^\"]+)\"");
  if(f.poster==null)f.poster=find(h,"<div class=\"b-sidecover\">.*?<img src=\"([^\"]+)\"");
  f.description=text(find(h,"<div class=\"b-post__description_text\"[^>]*>(.*?)</div>"));
  String info=find(h,"<table class=\"b-post__info\">(.*?)</table>");
  if(info!=null){
   Matcher r=Pattern.compile("<tr>\\s*<td class=\"l\"><h2>(.*?)</h2>:?</td>\\s*<td[^>]*>(.*?)</td>\\s*</tr>",Pattern.DOTALL).matcher(info);
   while(r.find()){
    String k=text(r.group(1)),v=r.group(2);
    if(k.startsWith("Рейтинг")){String imdb=find(v,"imdb\">.*?<span class=\"bold\">([\\d.]+)</span>"),kp=find(v,"kp\">.*?<span class=\"bold\">([\\d.]+)</span>");
     if(imdb!=null){f.rating=imdb;f.ratingName="IMDb";}else if(kp!=null){f.rating=kp;f.ratingName="КП";}}
    else if(k.startsWith("Дата выхода")){f.year=find(v,"(\\d{4})");}
    else if(k.startsWith("Год")){f.year=find(v,"(\\d{4})");}
    else if(k.startsWith("Страна"))f.country=text(v);
    else if(k.startsWith("Жанр")){Matcher g=Pattern.compile("itemprop=\"genre\">(.*?)</span>").matcher(v);while(g.find())f.genres.add(text(g.group(1)));}
    else if(k.startsWith("Режиссер")||k.startsWith("Режиссёр")||k.startsWith("В ролях")){Matcher people=Pattern.compile("<a[^>]*>(.*?)</a>",Pattern.DOTALL).matcher(v);while(people.find()&&f.people.size()<16){String person=text(people.group(1));if(person!=null&&!person.isEmpty()&&!f.people.contains(person))f.people.add(person);}}
    else if(k.startsWith("Время"))f.duration=text(v);
    else if(k.startsWith("В переводе"))f.defaultVoiceName=text(v);
    else if(k.startsWith("Возраст"))f.age=find(text(v),"(\\d+\\+)");
   }
  }
  if(info!=null){Matcher people=Pattern.compile("itemprop=\"(?:actor|director)\"[^>]*>.*?itemprop=\"name\"[^>]*>(.*?)</span>",Pattern.DOTALL).matcher(info);while(people.find()&&f.people.size()<16){String person=text(people.group(1));if(person!=null&&!person.isEmpty()&&!f.people.contains(person))f.people.add(person);}}
  if(f.rating==null){String hd=find(h,"b-post__rating.*?<span class=\"num\">([\\d.]+)</span>");if(hd!=null){f.rating=hd;f.ratingName="Рейтинг";}}
  f.favs=find(h,"id=\"ctrl_favs\"[^>]*value=\"([^\"]*)\"");
  Matcher init=Pattern.compile("initCDN(Series|Movies)Events\\((\\d+),\\s*(\\d+)(?:,\\s*(\\d+),\\s*(\\d+),\\s*(\\d+))?").matcher(h);
  if(init.find()){f.series="Series".equals(init.group(1));f.id=init.group(2);f.defaultVoice=init.group(3);
   if(!f.series){f.defaultCamrip="1".equals(init.group(4));f.defaultAds="1".equals(init.group(5));f.defaultDirector="1".equals(init.group(6));}}
  if(f.id==null){f.id=find(h,"data-id=\"(\\d+)\"");f.series=link.contains("/series/")||h.contains("simple-episodes-list");}
  String tl=find(h,"id=\"translators-list\"[^>]*>(.*?)</ul>");
  if(tl!=null){Matcher v=Pattern.compile("<li([^>]*)>(.*?)</li>",Pattern.DOTALL).matcher(tl);Set<String> ids=new HashSet<String>();
   while(v.find()){String a=v.group(1);String anchor=find(v.group(2),"^\\s*<a([^>]*)>");if(anchor!=null)a+=" "+anchor;Voice x=new Voice();x.id=find(a,"data-translator_id=\"(\\d+)\"");if(x.id==null||!ids.add(x.id))continue;
    String title=find(a,"title=\"([^\"]*)\"");x.name=unescape(title!=null&&!title.isEmpty()?title:text(v.group(2)));
    if(v.group(2).contains("<img")&&!x.name.contains("(")){String lang=find(v.group(2),"alt=\"([^\"]+)\"");if(lang!=null)x.name+=" ("+lang+")";}
    x.premium=a.contains("b-prem_translator");x.camrip="1".equals(find(a,"data-camrip=\"(\\d)\""));x.ads="1".equals(find(a,"data-ads=\"(\\d)\""));x.director="1".equals(find(a,"data-director=\"(\\d)\""));
    f.voices.add(x);}
  }
  if(f.voices.isEmpty()&&f.defaultVoice!=null){Voice x=new Voice();x.id=f.defaultVoice;x.name=f.defaultVoiceName!=null?f.defaultVoiceName:f.series?"Озвучка":"Основна доріжка";x.camrip=f.defaultCamrip;x.ads=f.defaultAds;x.director=f.defaultDirector;f.voices.add(x);}
  f.voices.removeIf(v->v.ads);
  // Best track first: Ukrainian, then Russian, then the rest; studio quality inside each language.
  Collections.sort(f.voices,(x,y)->Integer.compare(PlaybackPolicy.order(x.name,x.camrip,x.ads),PlaybackPolicy.order(y.name,y.camrip,y.ads)));
  int side=h.indexOf("class=\"b-sidelist\"");
  if(side>=0)for(Card c:cards(h.substring(side))){if(f.related.size()>=12)break;c.link="https://"+HOSTS[0]+"/"+Progress.norm(c.link);if(!Progress.norm(c.link).equals(Progress.norm(link)))f.related.add(c);}
  if(f.series)parseEpisodes(h,f);
  return f;
 }
 private static void parseEpisodes(String h,Film f){
  f.seasons.clear();f.episodes.clear();
  Matcher s=Pattern.compile("data-tab_id=\"(\\d+)\"[^>]*>(.*?)</(?:li|a)>",Pattern.DOTALL).matcher(h);while(s.find())if(!f.seasons.contains(s.group(1)))f.seasons.add(s.group(1));
  Matcher e=Pattern.compile("<(?:li|a)[^>]*data-season_id=\"(\\d+)\"[^>]*data-episode_id=\"(\\d+)\"[^>]*>(.*?)</(?:li|a)>",Pattern.DOTALL).matcher(h);Set<String> seen=new HashSet<String>();
  while(e.find()){if(!seen.add(e.group(1)+":"+e.group(2)))continue;Episode x=new Episode();x.season=e.group(1);x.episode=e.group(2);x.title=text(e.group(3));f.episodes.add(x);}
  if(f.seasons.isEmpty())for(Episode x:f.episodes)if(!f.seasons.contains(x.season))f.seasons.add(x.season);
 }
 static List<Episode> covered(Film f,String voice)throws IOException{
  synchronized(f.coverage){List<Episode> known=f.coverage.get(voice);if(known!=null)return known;}
  Film copy=new Film();copy.id=f.id;copy.favs=f.favs;copy.origin=f.origin;copy.link=f.link;episodesFor(copy,voice);
  synchronized(f.coverage){f.coverage.put(voice,copy.episodes);}return copy.episodes;
 }
 /** The viewer's own choice first, then the source order (Ukrainian, Russian, the rest): the first voice that has this episode. */
 public static Voice voiceFor(Film f,String season,String episode,String preferred)throws IOException{
  List<Voice> order=new ArrayList<Voice>();for(Voice v:f.voices)if(!v.premium&&v.id.equals(preferred))order.add(v);
  // Stay in the chosen language (best studio first) before falling back to Ukrainian, Russian, the rest.
  if(!order.isEmpty()){String l=PlaybackPolicy.lang(order.get(0).name);for(Voice v:f.voices)if(!v.premium&&!order.contains(v)&&PlaybackPolicy.lang(v.name).equals(l))order.add(v);}
  for(Voice v:f.voices)if(!v.premium&&!order.contains(v))order.add(v);
  for(Voice v:order){if(Thread.currentThread().isInterrupted())break;for(Episode e:covered(f,v.id))if(e.season.equals(season)&&e.episode.equals(episode))return v;}
  return null;
 }
 /** Every season and episode the leading voices have, so a season missing in one voice still shows. */
 public static List<Episode> allEpisodes(Film f,int voices){
  Map<String,Episode> all=new HashMap<String,Episode>();for(Episode e:f.episodes)all.put(e.season+":"+e.episode,e);int n=0;
  for(Voice v:f.voices){if(v.premium)continue;if(n++>=voices)break;try{for(Episode e:covered(f,v.id))all.putIfAbsent(e.season+":"+e.episode,e);}catch(IOException ignored){}}
  List<Episode> out=new ArrayList<Episode>(all.values());Collections.sort(out,(a,b)->{int s=Integer.compare(num(a.season),num(b.season));return s!=0?s:Integer.compare(num(a.episode),num(b.episode));});return out;
 }
 private static int num(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
 /** Seasons and episodes differ per voice for series. */
 public static void episodesFor(Film f,String voice)throws IOException{
  String body=send(filmEndpoint(f),"id="+f.id+"&translator_id="+voice+"&favs="+enc(f.favs)+"&action=get_episodes",true);
  try{JSONObject o=new JSONObject(body);if(!o.optBoolean("success"))return;parseEpisodes(o.optString("seasons")+o.optString("episodes"),f);}catch(JSONException e){throw new IOException(e);}
 }
 public static Stream stream(Film f,Voice v,String season,String episode)throws IOException{return stream(f,v,season,episode,false);}
 public static Stream stream(Film f,Voice v,String season,String episode,boolean rotate)throws IOException{
  if(v==null||v.premium||v.ads)throw new IOException("No clean voice");IOException last=null;String original=f.origin==null?host():f.origin;int start=Arrays.asList(HOSTS).indexOf(original);if(start<0)start=0;
  long deadline=System.nanoTime()+25_000_000_000L;
  for(int attempt=0;attempt<HOSTS.length&&System.nanoTime()<deadline;attempt++){
   String mirror=HOSTS[(start+attempt+(rotate?1:0))%HOSTS.length];
   try{if(attempt>0||rotate){String url="https://"+mirror+"/"+Progress.norm(f.link);String html=send(url,null,false);if(html.contains("anubis_challenge"))html=solve(html,url);Film fresh=parseFilm(f.link,html);if(fresh.id==null||!fresh.id.equals(f.id))throw new IOException("Title mismatch");f.favs=fresh.favs;f.origin=mirror;}
    return requestStream(f,v,season,episode);
   }catch(IOException e){last=e;}
  }throw last==null?new IOException("Mirrors unavailable"):last;
 }
 private static String filmEndpoint(Film f){return "https://"+(f.origin==null?host():f.origin)+"/ajax/get_cdn_series/?t="+System.currentTimeMillis();}
 private static Stream requestStream(Film f,Voice v,String season,String episode)throws IOException{
  String form="id="+f.id+"&translator_id="+v.id+"&favs="+enc(f.favs)+(f.series?"&season="+season+"&episode="+episode+"&action=get_stream":"&is_camrip="+(v.camrip?1:0)+"&is_ads="+(v.ads?1:0)+"&is_director="+(v.director?1:0)+"&action=get_movie");
  String body=send(filmEndpoint(f),form,true);
  try{
   JSONObject o=new JSONObject(body);if(!o.optBoolean("success"))throw new IOException(o.optString("message","Відео недоступне"));
   String url=o.optString("url","");if(url.startsWith("#h"))url=decode(url);
   Stream s=new Stream();
   Matcher m=Pattern.compile("\\[([^\\]]+)\\]([^,\\[]+)").matcher(url);
   while(m.find()){List<String> alts=new ArrayList<String>();for(String a:m.group(2).split(" or ")){String urlPart=a.trim();if(RecoveryPolicy.media(urlPart)&&!alts.contains(urlPart))alts.add(urlPart);}// HLS first: 6-second segments start and resume at once, while the MP4 copy must load a multi-megabyte index
   // before the first frame. The MP4 stays as the backup address.
   alts.sort((a,b)->Boolean.compare(!KikPlayerActivity.hls(a),!KikPlayerActivity.hls(b)));if(!alts.isEmpty()){String label=text(m.group(1));s.qualities.put(label,alts.get(0));s.alternatives.put(label,alts);}}if(s.qualities.isEmpty())throw new IOException("No direct media");
   // Menus list the best quality first.
   List<String> labels=new ArrayList<String>(s.qualities.keySet());Collections.sort(labels,(x,y)->Integer.compare(quality(y),quality(x)));LinkedHashMap<String,String> sorted=new LinkedHashMap<String,String>();for(String q:labels)sorted.put(q,s.qualities.get(q));s.qualities=sorted;
   Object sub=o.opt("subtitle");
   if(sub instanceof String&&!((String)sub).isEmpty()){Matcher t=Pattern.compile("\\[([^\\]]+)\\](https?://[^,\\[]+)").matcher((String)sub);while(t.find())s.subtitleList.add(new String[]{t.group(1),t.group(2).trim()});}
   return s;
  }catch(JSONException e){throw new IOException(e);}
 }
 /**
  * The CDN answers every request with a redirect to the node that holds the file. Following it once up front lets
  * the player fetch each HLS segment straight from that node instead of paying the extra round trip every 6 s.
  * Any failure keeps the original address.
  */
 public static String resolve(String url){
  String at=url;
  for(int hop=0;hop<3;hop++){
   HttpURLConnection c=null;
   try{c=Doh.open(at);c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Range","bytes=0-0");
    int code=c.getResponseCode();String next=c.getHeaderField("Location");
    if(code<300||code>399||next==null)return at;
    String target=new URL(new URL(at),next).toString();if(!RecoveryPolicy.media(target))return url;at=target;
   }catch(Exception e){return at;}finally{if(c!=null)c.disconnect();}
  }
  return at;
 }
 static int quality(String q){
  String s=q==null?"":q.toLowerCase(Locale.ROOT);Matcher m=Pattern.compile("(\\d+)").matcher(s);int n=m.find()?Integer.parseInt(m.group(1))*2:0;
  if(s.contains("4k"))n=2160*2;else if(s.contains("2k"))n=1440*2;if(s.contains("ultra"))n+=1;return n;
 }
 private static String enc(String s){try{return URLEncoder.encode(s==null?"":s,"UTF-8");}catch(UnsupportedEncodingException e){return "";}}
 /** Legacy "#h" stream encoding: base64 with inserted junk blocks. */
 static String decode(String s){
  s=s.substring(2).replace("//_//","");
  String[] trash={"@","#","!","^","$"};
  for(int len=2;len<=3;len++)for(String p:combos(trash,len))s=s.replace(android.util.Base64.encodeToString(p.getBytes(),android.util.Base64.NO_WRAP),"");
  try{return new String(android.util.Base64.decode(s,android.util.Base64.DEFAULT),"UTF-8");}catch(Exception e){return s;}
 }
 private static List<String> combos(String[] a,int n){List<String> out=new ArrayList<String>();if(n==0){out.add("");return out;}for(String p:combos(a,n-1))for(String x:a)out.add(p+x);return out;}
}
