package kik.i18n;
import android.app.job.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/**
 * Android TV home-screen channels for the TV Home launcher: Continue, For you and New episodes, always for the
 * profile that last played a video on this TV (choosing a profile alone changes nothing). Each program carries a
 * 16:9 TMDB backdrop and an intent that opens the title in KIK, or resumes it for Continue. The upstream RezkaTV
 * channels (Новинки, Смотрят, Горячие, История, Обновления сериалов) are deleted.
 */
public final class TvChannels {
 static final String TAG="KIK.Channels";
 private static final String CONTINUE="tvhome:continue",FOR_YOU="tvhome:for_you",NEW_EPISODES="tvhome:new_episodes";
 private static final String[][] CHANNELS={{CONTINUE,"Продовжити перегляд"},{FOR_YOU,"Для вас"},{NEW_EPISODES,"Нові серії"}};
 private static final int JOB=0x4b494b01,LIMIT=15;
 private static final long ARTWORK_TTL=30L*86_400_000L,MISS_TTL=3L*86_400_000L;
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->new Thread(r,"kik-channels"));
 private TvChannels(){}

 private static boolean supported(){return Build.VERSION.SDK_INT>=26;}
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("kik_tv_channels",0);}

 /** Playback started: a new profile takes over all three channels. */
 public static void played(Context context,String profile){
  if(!supported()||profile==null)return;Context c=context.getApplicationContext();
  if(profile.equals(prefs(c).getString("profile",null)))return;
  prefs(c).edit().putString("profile",profile).apply();Log.d(TAG,"Last playing profile is now "+profile);
  IO.execute(()->publish(c,true));
 }
 /** A profile left the TV (deleted, or its account signed out): if the channels showed it, its titles leave the launcher now, not at the next refresh. */
 public static void removed(Context context,String profile){
  if(!supported())return;Context c=context.getApplicationContext();String last=prefs(c).getString("profile",null);if(last!=null&&!last.equals(profile))return;
  prefs(c).edit().remove("profile").apply();IO.execute(()->publish(c,true));
 }
 /** Playback stopped: the position changed, so Continue is rebuilt from local history. */
 public static void stopped(Context context){if(!supported())return;Context c=context.getApplicationContext();IO.execute(()->publish(c,false));}
 /** On app start: the periodic refresh, and a first publication if the channels are not there yet. */
 public static void start(Context context){
  if(!supported())return;Context c=context.getApplicationContext();
  try{JobScheduler js=c.getSystemService(JobScheduler.class);if(js.getPendingJob(JOB)==null)js.schedule(new JobInfo.Builder(JOB,new ComponentName(c,Job.class)).setPeriodic(4*3_600_000L).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());}catch(Exception e){Log.w(TAG,"Schedule",e);}
  if(System.currentTimeMillis()-prefs(c).getLong("published",0)>3_600_000L)IO.execute(()->publish(c,true));
 }

 /** Periodic refresh of For you and New episodes for the remembered profile. */
 public static final class Job extends JobService {
  @Override public boolean onStartJob(JobParameters params){if(UiRuntime.app==null)UiRuntime.app=getApplicationContext();IO.execute(()->{try{publish(getApplicationContext(),true);}finally{jobFinished(params,false);}});return true;}
  @Override public boolean onStopJob(JobParameters params){return true;}
 }

 /** The profile that last played; before any playback, the one with the most recent history. */
 static String profile(Context c){
  String p=prefs(c).getString("profile",null);if(p!=null&&ProfileStore.nameOf(c,p)!=null)return p;
  String best=ProfileStore.current(c);long ts=-1;for(ProfileStore.Profile x:ProfileStore.list(c)){List<Progress.Entry> r=Progress.recent(c,x.id);if(!r.isEmpty()&&r.get(0).ts>ts){ts=r.get(0).ts;best=x.id;}}
  return best;
 }

 private static final class Program{String link,title,poster,season,episode;long pos,dur;int type;boolean play;}

 private static synchronized void publish(Context c,boolean full){
  try{
   String profile=profile(c);ContentResolver cr=c.getContentResolver();
   Map<String,Long> ids=channels(c,cr);
   List<Program> resume=continueWatching(c,profile);
   fill(c,cr,ids.get(CONTINUE),resume,profile);
   if(full){
    List<Program> forYou=forYou(c,profile);fill(c,cr,ids.get(FOR_YOU),forYou,profile);
    List<Program> fresh=newEpisodes(c,profile);fill(c,cr,ids.get(NEW_EPISODES),fresh,profile);
   }
   prefs(c).edit().putLong("published",System.currentTimeMillis()).apply();
  }catch(Exception e){Log.w(TAG,"Publish",e);}
 }

 /** Our three channels in a fixed order, kept by id; every other channel of this app (RezkaTV's) is deleted. */
 private static Map<String,Long> channels(Context c,ContentResolver cr){
  Map<String,Long> found=new HashMap<String,Long>();
  try(android.database.Cursor q=cr.query(TvContract.Channels.CONTENT_URI,new String[]{TvContract.Channels._ID,TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID,TvContract.Channels.COLUMN_DISPLAY_NAME},null,null,null)){
   while(q!=null&&q.moveToNext()){long id=q.getLong(0);String key=q.getString(1);
    boolean ours=false;for(String[] ch:CHANNELS)if(ch[0].equals(key))ours=true;
    if(ours&&!found.containsKey(key))found.put(key,id);
    else{cr.delete(TvContract.buildChannelUri(id),null,null);Log.d(TAG,"Deleted old channel "+q.getString(2));}}
  }
  Intent home=new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  for(String[] ch:CHANNELS){
   ContentValues v=new ContentValues();v.put(TvContract.Channels.COLUMN_TYPE,TvContract.Channels.TYPE_PREVIEW);v.put(TvContract.Channels.COLUMN_DISPLAY_NAME,ch[1]);
   v.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID,ch[0]);v.put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI,home.toUri(Intent.URI_INTENT_SCHEME));
   Long id=found.get(ch[0]);
   if(id!=null)cr.update(TvContract.buildChannelUri(id),v,null,null);
   else{Uri u=cr.insert(TvContract.Channels.CONTENT_URI,v);if(u==null)continue;id=ContentUris.parseId(u);found.put(ch[0],id);logo(c,cr,id);Log.d(TAG,"Created channel "+ch[1]);}
  }
  return found;
 }
 private static void logo(Context c,ContentResolver cr,long id){
  try{Drawable d=c.getPackageManager().getApplicationIcon(c.getPackageName());Bitmap b=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);d.setBounds(0,0,160,160);d.draw(canvas);
   try(OutputStream out=cr.openOutputStream(TvContract.buildChannelLogoUri(id))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}catch(Exception e){Log.w(TAG,"Channel logo",e);}
 }

 /** Replaces a channel's programs: old ones out, new ones in, highest weight first. */
 private static void fill(Context c,ContentResolver cr,Long channel,List<Program> programs,String profile){
  if(channel==null)return;
  Map<String,String> art=backdrops(c,programs);
  List<ContentValues> rows=new ArrayList<ContentValues>();int weight=programs.size();
  for(Program p:programs){
   String wide=art.get(Progress.norm(p.link));if(wide==null||!wide.startsWith("https://")){Log.d(TAG,"No 16:9 artwork: "+p.title);continue;}
   Intent open=new Intent(OpenTitleActivity.ACTION).setClassName(c.getPackageName(),OpenTitleActivity.class.getName()).putExtra(android.app.SearchManager.QUERY,p.title).putExtra(OpenTitleActivity.LINK,p.link).putExtra(OpenTitleActivity.POSTER,p.poster).putExtra(OpenTitleActivity.PROFILE,profile).putExtra(OpenTitleActivity.PLAY,p.play);
   if(p.season!=null)open.putExtra(OpenTitleActivity.SEASON,p.season).putExtra(OpenTitleActivity.EPISODE,p.episode);
   ContentValues v=new ContentValues();v.put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,channel);v.put(TvContract.PreviewPrograms.COLUMN_TYPE,p.type);
   v.put(TvContract.PreviewPrograms.COLUMN_TITLE,p.title);v.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,wide);v.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,TvContract.PreviewPrograms.ASPECT_RATIO_16_9);
   v.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI,open.toUri(Intent.URI_INTENT_SCHEME));v.put(TvContract.PreviewPrograms.COLUMN_WEIGHT,weight--);v.put(TvContract.PreviewPrograms.COLUMN_SEARCHABLE,1);
   v.put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID,Progress.norm(p.link)+(p.season==null?"":"#"+p.season+":"+p.episode));
   if(p.season!=null){v.put(TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER,p.season);v.put(TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,p.episode);v.put(TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE,"Сезон "+p.season+" · Серія "+p.episode);}
   if(p.dur>0){v.put(TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,(int)Math.min(Integer.MAX_VALUE,p.pos));v.put(TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,(int)Math.min(Integer.MAX_VALUE,p.dur));}
   rows.add(v);
  }
  List<Long> old=new ArrayList<Long>();
  try(android.database.Cursor q=cr.query(TvContract.buildPreviewProgramsUriForChannel(channel),new String[]{TvContract.PreviewPrograms._ID},null,null,null)){while(q!=null&&q.moveToNext())old.add(q.getLong(0));}
  for(long id:old)cr.delete(TvContract.buildPreviewProgramUri(id),null,null);
  if(!rows.isEmpty())cr.bulkInsert(TvContract.PreviewPrograms.CONTENT_URI,rows.toArray(new ContentValues[0]));
  Log.d(TAG,"Channel "+channel+": "+rows.size()+" programs for profile "+profile);
 }

 // ---------- lists ----------
 private static List<Program> continueWatching(Context c,String profile){
  List<Program> out=new ArrayList<Program>();
  for(Progress.Entry e:Progress.recent(c,profile)){
   // A film opened for a moment is not "started"; a series that moved on to its next episode is.
   if(e.link==null||e.title==null||e.finished()||e.season==null&&e.pos<60_000)continue;
   Program p=new Program();p.link=e.link;p.title=e.title;p.poster=e.poster;p.season=e.season;p.episode=e.episode;p.pos=e.pos;p.dur=e.dur;p.play=true;
   p.type=e.season!=null?TvContract.PreviewPrograms.TYPE_TV_EPISODE:TvContract.PreviewPrograms.TYPE_MOVIE;out.add(p);if(out.size()>=LIMIT)break;
  }return out;
 }
 /** The same ranking as KIK's home row, from the profile's cached candidates, with its country filter. */
 private static List<Program> forYou(Context c,String profile){
  List<Program> out=new ArrayList<Program>();
  try{
   Recommendations.Snapshot s=Recommendations.snapshot(c,profile);Recommendations.refresh(c,s);
   HomeActivity.Row row=Recommendations.row(c,s,new ArrayList<HomeActivity.Row>());
   List<String> names=new ArrayList<String>();for(HomeActivity.Item x:row.items)if(x!=null&&x.title!=null)names.add(x.title);
   Map<String,String> uk=new HashMap<String,String>();try{uk=TitleTranslate.resolve(names);}catch(Exception ignored){}
   for(HomeActivity.Item x:row.items){if(x==null||x.link==null||x.title==null)continue;
    ContentSimilarity.Document d=RecommendationMetadata.get(c,x.link);if(d!=null&&CountryFilter.blocked(d.country))continue;
    Program p=new Program();p.link=x.link;String t=uk.get(x.title);p.title=t==null?Ua.t(x.title):t;p.poster=x.poster;
    p.type=RecommendationRanker.parts(x.link)[0].equals("series")?TvContract.PreviewPrograms.TYPE_TV_SERIES:TvContract.PreviewPrograms.TYPE_MOVIE;out.add(p);if(out.size()>=LIMIT)break;}
  }catch(Exception e){Log.w(TAG,"For you",e);}
  return out;
 }
 /** Series the profile follows: the next episode after a finished one, newest first, then the latest episode of series saved to My list. */
 private static List<Program> newEpisodes(Context c,String profile){
  List<Program> out=new ArrayList<Program>();Set<String> seen=new HashSet<String>();int checked=0;
  for(Progress.Entry e:Progress.recent(c,profile)){
   if(e.link==null||e.season==null||!e.finished()||!seen.add(Progress.norm(e.link)))continue;if(++checked>10)break;
   try{Site.Film f=FreeSources.metadata(e.link);if(!f.series||f.sourceUrl!=null)continue;
    // An unfinished episode is already in Continue; a finished one points at the next, if it is out.
    Site.Episode n=e.finished()?KikPlayerActivity.next(f,e.season,e.episode):null;if(n==null)continue;
    Program p=new Program();p.link=e.link;p.title=e.title;p.poster=e.poster;p.season=n.season;p.episode=n.episode;p.play=true;p.type=TvContract.PreviewPrograms.TYPE_TV_EPISODE;out.add(p);
   }catch(Exception x){Log.w(TAG,"Series "+e.link,x);}
   if(out.size()>=LIMIT)break;
  }
  // Series saved to My list and not started yet: their latest episode, opening the title page.
  JSONArray later=Progress.later(c,profile);
  for(int i=0;i<later.length()&&out.size()<LIMIT&&checked<16;i++){JSONObject o=later.optJSONObject(i);if(o==null)continue;String link=Site.opt(o,"link");
   if(link==null||!Progress.norm(link).startsWith("series/")||!seen.add(Progress.norm(link))||started(c,profile,link))continue;checked++;
   try{Site.Film f=FreeSources.metadata(link);if(!f.series||f.sourceUrl!=null||f.episodes.isEmpty())continue;Site.Episode n=f.episodes.get(f.episodes.size()-1);
    Program p=new Program();p.link=link;String t=Site.opt(o,"title");p.title=Ua.t(t==null||t.isEmpty()?f.title:t);p.poster=o.optString("poster",f.poster);p.season=n.season;p.episode=n.episode;p.type=TvContract.PreviewPrograms.TYPE_TV_EPISODE;out.add(p);
   }catch(Exception x){Log.w(TAG,"Series "+link,x);}
  }return out;
 }
 private static boolean started(Context c,String profile,String link){String k=Progress.norm(link);for(Progress.Entry e:Progress.recent(c,profile))if(k.equals(Progress.norm(e.link)))return true;return false;}

 // ---------- artwork ----------
 /** The same backdrops for the home hero's slides, keyed by Progress.norm(link); call off the main thread. */
 static Map<String,String> backdrops(Context c,List<String> links,List<String> titles){List<Program> ps=new ArrayList<Program>();for(int i=0;i<links.size();i++){Program p=new Program();p.link=links.get(i);p.title=titles.get(i);ps.add(p);}return backdrops(c,ps);}
 /** 16:9 TMDB backdrops (w1280) through the KIK server, cached for a month (misses for three days). */
 private static Map<String,String> backdrops(Context c,List<Program> programs){
  SharedPreferences cache=c.getSharedPreferences("kik_tv_artwork_v2",0);Map<String,String> out=new HashMap<String,String>();long now=System.currentTimeMillis();
  JSONArray ask=new JSONArray();
  try{for(Program p:programs){String key=Progress.norm(p.link);String hit=cache.getString(key,null);long at=cache.getLong(key+"@",0);
    if(hit!=null&&now-at<(hit.isEmpty()?MISS_TTL:ARTWORK_TTL)){if(!hit.isEmpty())out.put(key,hit);continue;}
    if(key.startsWith("uaserials/")||ask.length()>=24)continue;
    // The artwork service can match a title without opening a source page for each launcher card.
    ContentSimilarity.Document d=RecommendationMetadata.get(c,p.link);String title=d!=null&&!d.title.isEmpty()?d.title:p.title,original=d==null?"":d.original,year=d==null||d.year==0?"":String.valueOf(d.year);
    ask.put(new JSONObject().put("link","https://hdrezka.sh/"+key).put("title",title).put("original",original).put("year",year));}
  }catch(JSONException ignored){}
  if(ask.length()==0)return out;
  HttpURLConnection h=null;
  try{h=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+"/api/artwork/batch").openConnection();h.setConnectTimeout(10000);h.setReadTimeout(30000);h.setRequestMethod("POST");h.setDoOutput(true);h.setRequestProperty("Content-Type","application/json; charset=UTF-8");
   byte[] body=new JSONObject().put("items",ask).toString().getBytes("UTF-8");try(OutputStream o=h.getOutputStream()){o.write(body);}
   if(h.getResponseCode()!=200)return out;
   JSONObject r=new JSONObject(Site.read(h.getInputStream())),results=r.optJSONObject("results");if(results==null||!r.optBoolean("configured",true))return out;
   SharedPreferences.Editor ed=cache.edit();
   for(int i=0;i<ask.length();i++){String key=ask.getJSONObject(i).getString("link").substring("https://hdrezka.sh/".length());JSONObject a=results.optJSONObject(key);
    String wide=a==null?"":a.optString("backdrop","");if(!wide.startsWith("https://"))wide="";ed.putString(key,wide).putLong(key+"@",now);if(!wide.isEmpty())out.put(key,wide);}
   ed.apply();
  }catch(Exception e){Log.w(TAG,"Artwork",e);}finally{if(h!=null)h.disconnect();}
  return out;
 }
}
