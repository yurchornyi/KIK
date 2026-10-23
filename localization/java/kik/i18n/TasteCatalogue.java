package kik.i18n;
import android.content.Context;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** The most popular films first, then series: a shared starter catalogue; selections remain in RatingStore. */
final class TasteCatalogue {
 static final int LIMIT=48;
 /** Similar titles opened by picks may grow the screen past the starter catalogue. */
 static final int MAX=120;
 private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("kik_taste_popular",0);}
 private static HomeActivity.Item item(JSONObject x){HomeActivity.Item i=new HomeActivity.Item();i.link=x.optString("link");i.title=x.optString("title");i.poster=x.optString("poster",null);i.year=x.optString("year",null);i.genre=x.optString("genre",null);return i;}
 private static void add(Map<String,HomeActivity.Item> all,HomeActivity.Item i){if(i.link==null||i.title==null||i.title.isEmpty()||RecommendationRanker.parts(i.link)[0].isEmpty())return;all.putIfAbsent(RecommendationRanker.key(i.link),i);}
 static List<HomeActivity.Item> cached(Context c,String profile){
  Map<String,HomeActivity.Item> all=new LinkedHashMap<>();
  for(RatingStore.Rating r:RatingStore.list(c,profile))if(r.value>0){HomeActivity.Item i=new HomeActivity.Item();i.link=r.link;i.title=r.title;i.poster=r.poster;i.genre=r.genre;add(all,i);}
  try{JSONArray a=new JSONArray(prefs(c).getString("items","[]"));for(int n=0;n<a.length();n++)add(all,item(a.getJSONObject(n)));}catch(JSONException ignored){}
  return balanced(new ArrayList<>(all.values()));
 }
 static boolean fresh(Context c){long age=System.currentTimeMillis()-prefs(c).getLong("updated",0);return age>=0&&age<86_400_000L&&!prefs(c).getString("items","[]").equals("[]");}
 static List<HomeActivity.Item> refresh(Context c)throws InterruptedException{
  ExecutorService workers=Executors.newFixedThreadPool(3);
  List<Callable<List<Site.Card>>> jobs=new ArrayList<>();for(String[] q:new String[][]{{"films","1"},{"films","2"},{"series","1"}})jobs.add(()->Site.list(q[0],null,"popular",Integer.parseInt(q[1])));
  Map<String,HomeActivity.Item> all=new LinkedHashMap<>();
  try{for(Future<List<Site.Card>> f:workers.invokeAll(jobs,12,TimeUnit.SECONDS))if(!f.isCancelled())try{for(Site.Card card:f.get()){HomeActivity.Item i=new HomeActivity.Item();i.link=card.link;i.title=card.title;i.poster=card.poster;i.year=card.year();i.genre=card.genre();add(all,i);}}catch(ExecutionException ignored){}}
  finally{workers.shutdownNow();}
  List<HomeActivity.Item> out=balanced(new ArrayList<>(all.values()));
  if(!out.isEmpty()){JSONArray a=new JSONArray();try{for(HomeActivity.Item i:out)a.put(new JSONObject().put("link",i.link).put("title",i.title).put("poster",i.poster).put("year",i.year).put("genre",i.genre));prefs(c).edit().putString("items",a.toString()).putLong("updated",System.currentTimeMillis()).apply();}catch(JSONException ignored){}}
  return out;
 }
 /** Two popular films for every popular series, each in the source's order. */
 private static List<HomeActivity.Item> balanced(List<HomeActivity.Item> source){
  ArrayDeque<HomeActivity.Item> films=new ArrayDeque<>(),series=new ArrayDeque<>();List<HomeActivity.Item> out=new ArrayList<>();
  for(HomeActivity.Item i:source){String g=RecommendationRanker.parts(i.link)[0];(g.equals("series")?series:films).add(i);}
  while(out.size()<LIMIT&&(!films.isEmpty()||!series.isEmpty())){for(int n=0;n<2&&!films.isEmpty();n++)out.add(films.poll());if(!series.isEmpty())out.add(series.poll());}
  return out.size()>LIMIT?out.subList(0,LIMIT):out;
 }

}
