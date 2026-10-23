package kik.i18n;
import java.net.URI;
import java.util.*;

/** Content-based ranking. No Android state or shared user preferences. */
final class RecommendationRanker {
 /** An explicit «Подобається» / «Не подобається» outweighs any watching signal; the same weights as the web. */
 static final double LIKED=6,DISLIKED=-6;
 static final class Candidate {
  final String link,genre,key,group;final String[] parts;
  ContentSimilarity.Document document;int impressions;
  /** Sum of signal weights whose source page lists this title under "see also"; negative for disliked seeds. */
  double related;
  Candidate(String link,String genre){this.link=link;this.genre=genre;this.key=key(link);this.parts=parts(link);this.group=parts[0]+"/"+parts[1];}
 }
 static final class Signal {
  final String link,genre;final double weight;
  ContentSimilarity.Document document;
  Signal(String link,String genre,double weight){this.link=link;this.genre=genre;this.weight=weight;}
 }
 static final class Query {
  final String category,genre;final double weight;
  Query(String category,String genre,double weight){this.category=category;this.genre=genre;this.weight=weight;}
 }
 static double watchedWeight(long position,long duration,long timestamp,long now){
  if(!meaningful(position,duration))return 0;
  long weeks=Math.max(0,now-timestamp)/(7*24*60*60_000L);
  return (duration>0&&position>duration*0.93?4d:3d)/(1+Math.min(8,weeks)*0.25);
 }
 static boolean meaningful(long position,long duration){return position>=120_000||(duration>0&&position>=duration*0.1);}
 private static String path(String link){
  if(link==null)return "";
  try{URI uri=new URI(link);String path=uri.getPath();return path==null?"":("uaserials.com".equals(uri.getHost())?"uaserials/":"")+path.replaceAll("^/+|/+$","");}catch(Exception e){return "";}
 }
 static String key(String link){String path=path(link);java.util.regex.Matcher id=java.util.regex.Pattern.compile("^(films|series|cartoons|animation)/[^/]+/(\\d+)(?:[-.]|$)").matcher(path);return id.find()?id.group(1)+"/"+id.group(2):path;}
 static String[] parts(String link){
  String[] p=path(link).split("/");
  if(p.length<3||!Arrays.asList("films","series","cartoons","animation").contains(p[0])||!p[1].matches("[a-z0-9-]+"))return new String[]{"",""};
  return new String[]{p[0],p[1]};
 }
 private static Set<String> genres(String text){
  Set<String> out=new HashSet<String>();if(text==null)return out;
  for(String word:text.toLowerCase(Locale.ROOT).split("[,;/|]")){word=word.trim();if(!word.isEmpty())out.add(word);}return out;
 }
 private static long order(String profile,String link){
  // Stable per profile across process restarts, also spreads discovery choices for new profiles.
  long h=0xcbf29ce484222325L;String value=profile+"|"+key(link);
  for(int i=0;i<value.length();i++){h^=value.charAt(i);h*=0x100000001b3L;}return h&Long.MAX_VALUE;
 }
 static List<Query> queries(List<Signal> signals){
  Map<String,Double> weights=new HashMap<String,Double>();
  for(Signal s:signals){String[] p=parts(s.link);if(p[0].isEmpty()||s.weight<=0)continue;String k=p[0]+"/"+p[1];weights.put(k,weights.getOrDefault(k,0d)+s.weight);}
  List<Query> out=new ArrayList<Query>();for(Map.Entry<String,Double> e:weights.entrySet()){String[] p=e.getKey().split("/");out.add(new Query(p[0],p[1],e.getValue()));}
  Collections.sort(out,(a,b)->{int n=Double.compare(b.weight,a.weight);return n!=0?n:(a.category+"/"+a.genre).compareTo(b.category+"/"+b.genre);});
  return out.subList(0,Math.min(2,out.size()));
 }
 static List<Candidate> rank(String profile,List<Signal> signals,Set<String> excluded,List<Candidate> candidates,int limit){
  Set<String> omit=new HashSet<String>();for(String s:excluded)omit.add(key(s));
  Map<String,Candidate> unique=new LinkedHashMap<String,Candidate>();
  for(Candidate c:candidates){String k=c.key;if(k.isEmpty()||c.parts[0].isEmpty()||omit.contains(k))continue;unique.putIfAbsent(k,c);}
  List<ContentSimilarity.Document> documents=new ArrayList<ContentSimilarity.Document>();for(Candidate c:unique.values())documents.add(c.document);for(Signal s:signals)documents.add(s.document);ContentSimilarity.Index index=new ContentSimilarity.Index(documents);
  Map<String,Double> scores=new HashMap<String,Double>();
  for(Candidate c:unique.values()){
   double positive=0,total=0,strongest=0,negative=0;
   for(Signal s:signals){double match=index.similarity(c.link,c.genre,c.document,s.link,s.genre,s.document);
    if(s.weight>0){positive+=match*s.weight;total+=s.weight;strongest=Math.max(strongest,match*Math.min(1,s.weight/4));}
    else if(s.weight<0)negative=Math.max(negative,match*Math.min(1,Math.abs(s.weight)/4));
   }
   if(negative>0.55&&strongest<negative*0.8&&c.related<=0)continue;
   double score=0.04+(total>0?0.65*strongest+0.35*positive/total:0)-0.85*negative;
   if(c.document!=null)score+=Math.max(0,Math.min(10,c.document.rating))*0.003;
   // The source's own "see also" list captures what other viewers pair with a title.
   score+=0.14*Math.max(-1,Math.min(1,c.related/6));
   score-=Math.min(4,c.impressions)*0.018;scores.put(c.key,score);
  }
  List<Candidate> pool=new ArrayList<Candidate>();for(Candidate c:unique.values())if(scores.containsKey(c.key))pool.add(c);List<Candidate> out=new ArrayList<Candidate>();Map<String,Integer> counts=new HashMap<String,Integer>();
  Map<Candidate,Double> redundancy=new IdentityHashMap<Candidate,Double>();Map<Candidate,Long> ties=new IdentityHashMap<Candidate,Long>();
  for(Candidate c:pool){redundancy.put(c,0d);ties.put(c,order(profile,c.link));}
  while(!pool.isEmpty()&&out.size()<Math.max(0,limit)){
   Candidate best=null;double bestScore=-Double.MAX_VALUE;long tie=Long.MAX_VALUE;
   for(Candidate c:pool){String group=c.group;double raw=scores.get(c.key);double score=raw-Math.max(0,raw)*0.18*redundancy.get(c)-0.025*counts.getOrDefault(group,0);long t=ties.get(c);
    if(score>bestScore||(score==bestScore&&t<tie)){best=c;bestScore=score;tie=t;}
   }
   out.add(best);pool.remove(best);counts.put(best.group,counts.getOrDefault(best.group,0)+1);
   // Each pair contributes once. Taking the running maximum preserves the original diversity score.
   if(out.size()<Math.max(0,limit))for(Candidate c:pool)redundancy.put(c,Math.max(redundancy.get(c),index.similarity(c.link,c.genre,c.document,best.link,best.genre,best.document)));
  }
  return out;
 }
}
