package kik.i18n;
import android.content.*;
import android.util.Log;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Each request captures a profile; only public catalogue metadata is shared. */
final class Recommendations {
 private static final long TTL=6*60*60_000L;
 private static final AtomicBoolean WARMING=new AtomicBoolean();
 static final class Snapshot {
  final String profile;final List<RecommendationRanker.Signal> signals=new ArrayList<RecommendationRanker.Signal>();
  final Set<String> excluded=new HashSet<String>(),disliked=new HashSet<String>();String fingerprint;
  final Map<String,String> titles=new HashMap<String,String>();final Map<String,Integer> exposures;
  Snapshot(String p,Map<String,Integer> e){profile=p;exposures=e;}
 }
 static Snapshot snapshot(Context c,String profile){
  Snapshot s=new Snapshot(profile,RatingStore.exposures(c,profile));long now=System.currentTimeMillis();int count=0;Set<String> rated=new HashSet<String>();
  for(RatingStore.Rating r:RatingStore.list(c,profile)){
   String key=RecommendationRanker.key(r.link);rated.add(key);s.excluded.add(r.link);s.titles.put(key,r.title);if(r.value<0)s.disliked.add(key);
   // «Не цікавить» (-2) only hides the title: it says nothing about the taste.
   if(r.value>=-1&&count++<50)s.signals.add(new RecommendationRanker.Signal(r.link,r.genre,r.value>0?RecommendationRanker.LIKED:RecommendationRanker.DISLIKED));
  }count=0;
  for(Progress.Entry e:Progress.recent(c,profile)){
   long engagement=e.observed?e.watchedMs:e.pos;boolean meaningful=RecommendationRanker.meaningful(engagement,e.dur);if(meaningful)s.excluded.add(e.link);
   // A brief accidental launch is not a preference. Finished short titles still count.
   boolean first=e.season==null||"1".equals(e.season)&&"1".equals(e.episode);
   double weight=e.observed&&RecommendationRanker.abandoned(e.watchedMs,e.pos,e.dur,e.ts,now,first)?RecommendationRanker.ABANDONED:RecommendationRanker.watchedWeight(engagement,e.dur,e.ts,now);if(weight<0)s.excluded.add(e.link);if(count>=20||weight==0||rated.contains(RecommendationRanker.key(e.link)))continue;
   s.titles.put(RecommendationRanker.key(e.link),e.title);
   s.signals.add(new RecommendationRanker.Signal(e.link,null,weight));count++;
  }
  JSONArray saved=Progress.later(c,profile);count=0;
  for(int i=0;i<saved.length();i++){JSONObject o=saved.optJSONObject(i);if(o==null)continue;String link=o.optString("link","");s.excluded.add(link);if(count++<20&&!rated.contains(RecommendationRanker.key(link))){s.signals.add(new RecommendationRanker.Signal(link,o.optString("genre",null),2));s.titles.put(RecommendationRanker.key(link),o.optString("title"));}}
  StringBuilder stamp=new StringBuilder("v3|"+CountryFilter.signature()+"|");for(RecommendationRanker.Signal x:s.signals)stamp.append(RecommendationRanker.key(x.link)).append(':').append(x.genre).append(':').append(x.weight).append('|');s.fingerprint=stamp.toString();
  return s;
 }
 private static SharedPreferences prefs(Context c,String profile){return c.getSharedPreferences("kik_recommendations_"+profile,0);}
 static List<HomeActivity.Item> cached(Context c,Snapshot s){
  SharedPreferences p=prefs(c,s.profile);if(!s.fingerprint.equals(p.getString("signature","")))return Collections.emptyList();
  List<HomeActivity.Item> out=new ArrayList<HomeActivity.Item>();
  try{JSONArray a=new JSONArray(p.getString("items","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);HomeActivity.Item x=new HomeActivity.Item();x.link=o.getString("link");x.title=o.getString("title");x.poster=o.optString("poster",null);x.year=o.optString("year",null);x.genre=o.optString("genre",null);out.add(x);}}catch(Exception e){Log.w("KIK","Recommendation cache",e);}return out;
 }
 private static Map<String,HomeActivity.Item> items(Context c,Snapshot s,List<HomeActivity.Row> catalogue){
  Map<String,HomeActivity.Item> items=new LinkedHashMap<String,HomeActivity.Item>();
  for(HomeActivity.Item x:cached(c,s))items.put(RecommendationRanker.key(x.link),x);
  for(HomeActivity.Row row:catalogue)for(HomeActivity.Item x:row.items)items.putIfAbsent(RecommendationRanker.key(x.link),x);
  for(RecommendationRanker.Signal signal:s.signals)if(signal.weight>0)for(HomeActivity.Item x:RecommendationMetadata.related(c,signal.link))items.putIfAbsent(RecommendationRanker.key(x.link),x);
  return items;
 }
 private static List<RecommendationRanker.Candidate> pool(Context c,Snapshot s,Map<String,HomeActivity.Item> items){
  for(RecommendationRanker.Signal signal:s.signals)signal.document=RecommendationMetadata.get(c,signal.link);
  Map<String,Double> related=new HashMap<String,Double>();for(RecommendationRanker.Signal signal:s.signals)for(HomeActivity.Item x:RecommendationMetadata.related(c,signal.link))related.merge(RecommendationRanker.key(x.link),signal.weight,Double::sum);
  List<RecommendationRanker.Candidate> pool=new ArrayList<RecommendationRanker.Candidate>();for(HomeActivity.Item x:items.values()){ContentSimilarity.Document facts=RecommendationMetadata.get(c,x.link);if(facts!=null&&CountryFilter.blocked(facts.country))continue;RecommendationRanker.Candidate candidate=new RecommendationRanker.Candidate(x.link,x.genre);candidate.document=RecommendationMetadata.get(c,x.link);if(candidate.document==null){candidate.document=new ContentSimilarity.Document();candidate.document.title=x.title==null?"":x.title;}candidate.impressions=s.exposures.getOrDefault(RecommendationRanker.key(x.link),0);candidate.related=related.getOrDefault(RecommendationRanker.key(x.link),0d);pool.add(candidate);}return pool;
 }
 static HomeActivity.Row row(Context c,Snapshot s,List<HomeActivity.Row> catalogue){
  Map<String,HomeActivity.Item> items=items(c,s,catalogue);List<RecommendationRanker.Candidate> pool=pool(c,s,items);
  HomeActivity.Row row=new HomeActivity.Row(s.signals.stream().anyMatch(x->x.weight>0)?"Рекомендовано для вас":"Відкрийте для себе");
  row.personalized=true;row.recommendationId="main";
  List<RecommendationRanker.Candidate> ranked=RecommendationRanker.rank(s.profile,s.signals,s.excluded,pool,AI_POOL);
  List<String> ai=aiOrder(c,s);List<String> local=new ArrayList<String>();Map<String,HomeActivity.Item> byPath=new HashMap<String,HomeActivity.Item>();
  for(RecommendationRanker.Candidate x:ranked){HomeActivity.Item it=items.get(RecommendationRanker.key(x.link));String path=Progress.norm(it.link);local.add(path);byPath.put(path,it);}
  int overlap=0;for(String k:ai)if(byPath.containsKey(k))overlap++;
  if(overlap>=3){row.title="Рекомендовано для вас · AI";local=blend(local,ai);}
  for(String path:local){row.items.add(byPath.get(path));if(row.items.size()>=15)break;}return row;
 }
 private static final int AI_POOL=40;
 private static String info(String... parts){StringBuilder b=new StringBuilder();for(String x:parts)if(x!=null&&!x.isEmpty()){if(b.length()>0)b.append(", ");b.append(x);}return b.length()>300?b.substring(0,300):b.toString();}
 /** 60% the local taste ranking, 40% the account's AI order, the same mix as the web version. */
 static List<String> blend(List<String> local,List<String> ai){
  Map<String,Integer> at=new HashMap<String,Integer>();for(int i=0;i<ai.size();i++)at.putIfAbsent(ai.get(i),i);
  final Map<String,Double> score=new HashMap<String,Double>();int n=Math.max(1,local.size());
  for(int i=0;i<local.size();i++){Integer a=at.get(local.get(i));score.put(local.get(i),.6*(1-(double)i/n)+.4*(a==null?0:1-(double)a/Math.max(1,ai.size())));}
  List<String> out=new ArrayList<String>(local);out.sort((x,y)->Double.compare(score.get(y),score.get(x)));return out;
 }
 private static List<String> aiOrder(Context c,Snapshot s){
  SharedPreferences p=prefs(c,s.profile);List<String> out=new ArrayList<String>();if(!CloudSync.linked(c,s.profile)||!"ai".equals(p.getString("ai_mode",""))||!p.getString("ai_taste","").equals(s.fingerprint))return out;
  try{JSONArray a=new JSONArray(p.getString("ai_keys","[]"));for(int i=0;i<a.length();i++)out.add(a.getString(i));}catch(JSONException ignored){}return out;
 }
 /**
  * For a TV profile linked to a KIK account, sends the local top candidates (public titles, genres, a short
  * description) to the account. The server ranks them with AI only when the administrator enabled enhanced
  * recommendations for that profile; otherwise the answer is the standard order and nothing changes here.
  */
 static void refreshAi(Context c,Snapshot s,List<HomeActivity.Row> catalogue){
  if(!CloudSync.linked(c,s.profile)||s.signals.stream().noneMatch(x->x.weight>0))return;
  Map<String,HomeActivity.Item> items=items(c,s,catalogue);List<RecommendationRanker.Candidate> ranked=RecommendationRanker.rank(s.profile,s.signals,s.excluded,pool(c,s,items),AI_POOL);
  JSONArray send=new JSONArray();StringBuilder stamp=new StringBuilder();
  try{for(RecommendationRanker.Candidate x:ranked){HomeActivity.Item it=items.get(RecommendationRanker.key(x.link));String path=Progress.norm(it.link);if(path.startsWith("uaserials/"))continue;ContentSimilarity.Document d=x.document;
    send.put(new JSONObject().put("link","https://hdrezka.sh/"+path).put("title",it.title==null?"":it.title).put("original",d==null?"":d.original).put("info",info(it.year,it.genre,d==null?"":String.join(", ",d.genres))).put("year",it.year==null?"":it.year).put("description",d==null||d.plot==null?"":d.plot.length()>240?d.plot.substring(0,240):d.plot));stamp.append(path).append(',');}
  }catch(JSONException e){return;}
  if(send.length()<3)return;
  SharedPreferences p=prefs(c,s.profile);String signature=s.fingerprint+"|"+stamp.toString().hashCode();long age=System.currentTimeMillis()-p.getLong("ai_updated",0);
  if(age>=0&&(signature.equals(p.getString("ai_signature",""))?age<TTL:age<30*60_000L))return;
  JSONObject r=CloudSync.recommend(c,s.profile,send);if(r==null)return;
  synchronized(Recommendations.class){if(!s.profile.equals(ProfileStore.current(c))||ProfileStore.nameOf(c,s.profile)==null)return;
   JSONArray keys=r.optJSONArray("keys");p.edit().putString("ai_signature",signature).putString("ai_taste",s.fingerprint).putString("ai_mode",r.optString("mode","standard")).putString("ai_keys",keys==null?"[]":keys.toString()).putLong("ai_updated",System.currentTimeMillis()).apply();}
  Log.d("KIK","Account recommendations mode="+r.optString("mode")+" status="+r.optString("status"));
 }
 static List<HomeActivity.Row> rows(Context c,Snapshot s,List<HomeActivity.Row> catalogue){
  List<HomeActivity.Row> rows=new ArrayList<HomeActivity.Row>();HomeActivity.Row main=row(c,s,catalogue);if(!main.items.isEmpty())rows.add(main);
  List<RecommendationRanker.Signal> seeds=new ArrayList<RecommendationRanker.Signal>();for(RecommendationRanker.Signal x:s.signals)if(x.weight>=3&&s.titles.get(RecommendationRanker.key(x.link))!=null)seeds.add(x);Collections.sort(seeds,(a,b)->Double.compare(b.weight,a.weight));
  if(seeds.isEmpty())return rows;
  Map<String,HomeActivity.Item> items=items(c,s,catalogue);List<RecommendationRanker.Candidate> pool=pool(c,s,items);Set<String> excluded=new HashSet<String>(s.excluded);for(HomeActivity.Item x:main.items)excluded.add(x.link);
  int count=0;Set<String> represented=new HashSet<String>();List<ContentSimilarity.Document> docs=new ArrayList<ContentSimilarity.Document>();for(RecommendationRanker.Candidate x:pool)docs.add(x.document);for(RecommendationRanker.Signal x:seeds)docs.add(x.document);ContentSimilarity.Index index=new ContentSimilarity.Index(docs);
  for(RecommendationRanker.Signal seed:seeds){
   String[] category=RecommendationRanker.parts(seed.link);String group=category[0]+"/"+category[1];if(!represented.add(group))continue;
   HomeActivity.Row reason=new HomeActivity.Row((seed.weight>=6?"Тому що вам подобається «":"Тому що ви дивилися «")+s.titles.get(RecommendationRanker.key(seed.link))+"»");reason.recommendationId=RecommendationRanker.key(seed.link);
   Set<String> seeAlso=new HashSet<String>();for(HomeActivity.Item x:RecommendationMetadata.related(c,seed.link))seeAlso.add(RecommendationRanker.key(x.link));
   List<RecommendationRanker.Signal> context=new ArrayList<RecommendationRanker.Signal>();context.add(seed);for(RecommendationRanker.Signal x:s.signals)if(x.weight<0)context.add(x);
   for(RecommendationRanker.Candidate x:RecommendationRanker.rank(s.profile,context,excluded,pool,30)){if(!seeAlso.contains(x.key)&&index.similarity(x.link,x.genre,x.document,seed.link,seed.genre,seed.document)<0.25)continue;reason.items.add(items.get(RecommendationRanker.key(x.link)));if(reason.items.size()>=8)break;}
   if(reason.items.size()>=3){rows.add(reason);for(HomeActivity.Item x:reason.items)excluded.add(x.link);if(++count==2)break;}
  }return rows;
 }
 /** «Не подобається» hides the title from the catalogue rows too; search, history and «Мій список» still show it. */
 static List<HomeActivity.Row> withoutDisliked(Snapshot s,List<HomeActivity.Row> rows){
  if(s.disliked.isEmpty())return rows;List<HomeActivity.Row> out=new ArrayList<HomeActivity.Row>();
  for(HomeActivity.Row r:rows){HomeActivity.Row copy=new HomeActivity.Row(r.title);copy.personalized=r.personalized;copy.recommendationId=r.recommendationId;for(HomeActivity.Item x:r.items)if(!s.disliked.contains(RecommendationRanker.key(x.link)))copy.items.add(x);if(!copy.items.isEmpty())out.add(copy);}
  return out;
 }
 static void enrich(Context c,Snapshot s,List<HomeActivity.Row> catalogue,BooleanSupplier valid){
  if(catalogue.isEmpty()||!WARMING.compareAndSet(false,true))return;
  ExecutorService workers=Executors.newFixedThreadPool(2);
  try{
   LinkedHashSet<String> links=new LinkedHashSet<String>();for(RecommendationRanker.Signal x:s.signals)if(x.weight>0?RecommendationMetadata.needsRelated(c,x.link):RecommendationMetadata.needs(c,x.link)){links.add(x.link);if(links.size()>=4)break;}
   HomeActivity.Row top=row(c,s,catalogue);int candidates=0;for(HomeActivity.Item x:top.items)if(RecommendationMetadata.needs(c,x.link)){links.add(x.link);if(++candidates>=2||links.size()>=8)break;}
   // Explore beyond the current top row so known titles cannot permanently crowd out unseen ones.
   List<HomeActivity.Item> exploration=new ArrayList<HomeActivity.Item>(items(c,s,catalogue).values());Collections.shuffle(exploration,new Random(Objects.hash(s.profile,System.currentTimeMillis()/86_400_000L)));
   for(HomeActivity.Item x:exploration)if(links.size()<8&&!s.excluded.contains(x.link)&&RecommendationMetadata.needs(c,x.link))links.add(x.link);
   List<Callable<Void>> jobs=new ArrayList<Callable<Void>>();for(String link:links)jobs.add(()->{if(valid.getAsBoolean()&&!Thread.currentThread().isInterrupted())try{RecommendationMetadata.remember(c,FreeSources.metadata(link));}catch(Exception e){RecommendationMetadata.failed(c,link);}return null;});
   workers.invokeAll(jobs,40,TimeUnit.SECONDS);
  }catch(InterruptedException e){Thread.currentThread().interrupt();}finally{workers.shutdownNow();WARMING.set(false);}
 }
 static void refresh(Context c,Snapshot s){
  if(s.signals.isEmpty())return;SharedPreferences p=prefs(c,s.profile);long age=System.currentTimeMillis()-p.getLong("updated",0);
  if(s.fingerprint.equals(p.getString("signature",""))&&age>=0&&age<TTL&&!cached(c,s).isEmpty())return;
  Map<String,Site.Card> cards=new LinkedHashMap<String,Site.Card>();
  for(RecommendationRanker.Query q:RecommendationRanker.queries(s.signals)){
   if(!s.profile.equals(ProfileStore.current(c))||ProfileStore.nameOf(c,s.profile)==null)return;
   try{for(Site.Card x:Site.list(q.category,q.genre,"popular",1))cards.put(RecommendationRanker.key(x.link),x);}catch(Exception e){Log.w("KIK","Recommendation catalogue",e);}
  }
  if(cards.isEmpty())return;
  try{JSONArray a=new JSONArray();for(Site.Card x:cards.values())a.put(new JSONObject().put("link",x.link).put("title",x.title).put("poster",x.poster).put("year",x.year()).put("genre",x.genre()));
   // Deleted or switched profiles cannot receive a delayed response.
   synchronized(Recommendations.class){if(s.profile.equals(ProfileStore.current(c))&&ProfileStore.nameOf(c,s.profile)!=null)p.edit().putString("signature",s.fingerprint).putString("items",a.toString()).putLong("updated",System.currentTimeMillis()).apply();}
  }catch(JSONException e){Log.w("KIK","Save recommendations",e);}
 }
 static synchronized void delete(Context c,String profile){c.getSharedPreferences("kik_recommendations_"+profile,0).edit().clear().commit();if(android.os.Build.VERSION.SDK_INT>=24)c.deleteSharedPreferences("kik_recommendations_"+profile);}
}
