package kik.i18n;
import java.util.*;
/** User-visible recommendation invariants, executable without a device or network. */
public final class RecommendationCheck {
 static int checks;
 static void check(String message,boolean ok){checks++;if(!ok)throw new AssertionError(message);}
 static RecommendationRanker.Candidate c(String link,String genre){return new RecommendationRanker.Candidate(link,genre);}
 static RecommendationRanker.Signal s(String link,String genre,double weight){return new RecommendationRanker.Signal(link,genre,weight);}
 static List<RecommendationRanker.Candidate> rank(String profile,List<RecommendationRanker.Signal> signals,Set<String> excluded,List<RecommendationRanker.Candidate> pool,int n){return RecommendationRanker.rank(profile,signals,excluded,pool,n);}
 static List<String> links(List<RecommendationRanker.Candidate> items){List<String> out=new ArrayList<>();for(RecommendationRanker.Candidate c:items)out.add(c.link);return out;}
 static ContentSimilarity.Document doc(String title,String plot,String genre,String person){ContentSimilarity.Document d=new ContentSimilarity.Document();d.title=title;d.plot=plot;if(genre!=null)d.genres.add(genre);if(person!=null)d.people.add(person);return d;}
 public static void main(String[] args){
  List<RecommendationRanker.Candidate> pool=Arrays.asList(c("https://one/films/drama/1.html","Драма"),c("https://one/films/comedy/2.html","Комедія"),c("https://one/series/drama/3.html","Драма"));
  List<RecommendationRanker.Signal> drama=Arrays.asList(s("/films/drama/old.html",null,4)),comedy=Arrays.asList(s("/films/comedy/old.html",null,4));Set<String> none=Collections.emptySet();
  check("Drama profile leads with drama",rank("a",drama,none,pool,3).get(0)==pool.get(0));
  check("Comedy profile leads with comedy",rank("b",comedy,none,pool,3).get(0)==pool.get(1));
  check("Ranking a second profile cannot change the first",rank("a",drama,none,pool,3).get(0)==pool.get(0));
  check("Saved-title signal influences ranking",rank("a",Arrays.asList(s("/films/comedy/saved.html","Комедія",2)),none,pool,3).get(0)==pool.get(1));
  Set<String> excluded=new HashSet<>(Arrays.asList("https://mirror/films/drama/1.html?ref=old","/films/comedy/2.html"));
  check("Watched and saved titles excluded across mirrors",rank("a",drama,excluded,pool,10).size()==1&&rank("a",drama,excluded,pool,10).get(0)==pool.get(2));
  List<RecommendationRanker.Candidate> duplicates=Arrays.asList(pool.get(0),c("https://mirror/films/drama/1.html?ref=other",null),c("bad",null),c("/unknown/drama/x.html",null));
  check("Mirrors deduplicate and invalid catalogue paths disappear",rank("a",drama,none,duplicates,20).size()==1);
  check("No candidate input mutation",pool.size()==3&&pool.get(0).genre.equals("Драма"));
  List<RecommendationRanker.Candidate> many=new ArrayList<>();for(int i=0;i<50;i++)many.add(c("/films/"+(i%2==0?"drama":"comedy")+"/"+i+".html",null));
  List<String> coldA=links(rank("new-a",Collections.emptyList(),none,many,15)),coldB=links(rank("new-b",Collections.emptyList(),none,many,15));
  check("Cold start stable across repeated loads",coldA.equals(links(rank("new-a",Collections.emptyList(),none,many,15))));
  check("New profiles get their own discovery mix",!coldA.equals(coldB));
  check("Clearing profile signals removes its old taste",!links(rank("a",drama,none,many,15)).equals(links(rank("a",Collections.emptyList(),none,many,15))));
  check("Result limit",rank("a",drama,none,many,15).size()==15);
  check("No duplicate recommendations",new HashSet<>(coldA).size()==15);
  check("Zero limit",rank("a",drama,none,pool,0).isEmpty());
  check("Empty catalogue",rank("a",drama,none,Collections.emptyList(),15).isEmpty());
  check("All seen titles give empty result",rank("a",drama,new HashSet<>(links(pool)),pool,15).isEmpty());
  List<RecommendationRanker.Query> queries=RecommendationRanker.queries(Arrays.asList(s("/films/comedy/1.html",null,2),s("/films/drama/1.html",null,4),s("/films/comedy/2.html",null,4),s("/series/drama/1.html",null,1),s("https://x/unknown/path/a",null,999)));
  check("Fetch only the two strongest interests",queries.size()==2&&queries.get(0).category.equals("films")&&queries.get(0).genre.equals("comedy")&&queries.get(1).genre.equals("drama"));
  check("No targeted requests for new profiles",RecommendationRanker.queries(Collections.emptyList()).isEmpty());
  check("Path excludes queries and fragments",RecommendationRanker.key("https://mirror/films/drama/1.html?ref=2#top").equals(RecommendationRanker.key("/films/drama/1.html")));
  check("Renamed slugs keep the same film identity",RecommendationRanker.key("/films/drama/123-old-title.html").equals(RecommendationRanker.key("/films/thriller/123-renamed-title.html")));
  check("Film identity retains content category",!RecommendationRanker.key("/films/drama/123-old-title.html").equals(RecommendationRanker.key("/series/drama/123-old-title.html")));
  long now=1_800_000_000_000L;
  check("Accidental short start is not a taste signal",RecommendationRanker.watchedWeight(30_000,7_200_000,now,now)==0);
  check("Sustained watching is a taste signal",RecommendationRanker.watchedWeight(180_000,7_200_000,now,now)>0);
  long days=86_400_000L;
  check("Given up after 6 minutes and not resumed is a mild dislike",RecommendationRanker.abandoned(360_000,400_000,6_600_000,now-5*days,now,true));
  check("A recent stop is not yet given up",!RecommendationRanker.abandoned(360_000,400_000,6_600_000,now-days,now,true));
  check("A glance or a seek is never given up",!RecommendationRanker.abandoned(10_000,3_000_000,6_600_000,now-5*days,now,true));
  check("Later episodes of a series are never given up",!RecommendationRanker.abandoned(360_000,400_000,2_700_000,now-5*days,now,false));
  check("A quarter of the film is not giving up",!RecommendationRanker.abandoned(600_000,1_700_000,6_600_000,now-5*days,now,true));
  check("Short completed video counts",RecommendationRanker.watchedWeight(50_000,52_000,now,now)>0);
  check("Completed title is stronger than partial watching",RecommendationRanker.watchedWeight(7_000_000,7_200_000,now,now)>RecommendationRanker.watchedWeight(180_000,7_200_000,now,now));
  check("Recent interest outweighs old interest",RecommendationRanker.watchedWeight(180_000,7_200_000,now,now)>RecommendationRanker.watchedWeight(180_000,7_200_000,now-60L*24*60*60_000,now));
  check("Future device timestamp stays bounded",RecommendationRanker.watchedWeight(180_000,7_200_000,now+86_400_000,now)==3);
  RecommendationRanker.Signal heist=s("/films/drama/seed.html",null,6);heist.document=doc("Пограбування","Банда планує пограбування банку. Сховище, заручники, поліція, злочинці.","Драма",null);
  RecommendationRanker.Candidate crime=c("/films/drama/bank.html",null),family=c("/films/drama/family.html",null);crime.document=doc("Банк","Пограбування банку. Злочинці захоплюють заручників, поліція оточує сховище.","Драма",null);family.document=doc("Родина","Донька повертається додому до матері. Родина намагається пробачити образи.","Драма",null);
  check("Plot distinguishes films in the same genre",rank("a",Arrays.asList(heist),none,Arrays.asList(family,crime),2).get(0)==crime);
  RecommendationRanker.Signal dislike=s("/films/drama/disliked.html",null,-5);dislike.document=heist.document;
  check("Explicit dislike suppresses similar stories",rank("a",Arrays.asList(dislike),none,Arrays.asList(crime,family),2).get(0)==family);
  RecommendationRanker.Signal crimeLike=s("/films/drama/liked.html",null,6);crimeLike.document=crime.document;
  check("Explicit positive preference can outrank a broad old taste",rank("a",Arrays.asList(crimeLike,s("/films/drama/other.html",null,2)),none,Arrays.asList(family,crime),2).get(0)==crime);
  ContentSimilarity.Document ua=doc("","","Драма",null),ru=doc("","","Драмы",null);ContentSimilarity.Index cross=new ContentSimilarity.Index(Arrays.asList(ua,ru));
  check("Genre aliases match across source and UI languages",Math.abs(cross.similarity("/films/drama/a",null,ua,"/films/drama/b",null,ru)-cross.similarity("/films/drama/a",null,ua,"/films/drama/b",null,ua))<0.000001);
  RecommendationRanker.Signal actor=s("/films/drama/actor-seed.html",null,6);actor.document=doc("","",null,"actor one");RecommendationRanker.Candidate withActor=c("/films/drama/actor.html",null),withoutActor=c("/films/drama/no-actor.html",null);withActor.document=doc("","",null,"actor one");withoutActor.document=doc("","",null,"actor two");
  check("People add useful similarity",rank("a",Arrays.asList(actor),none,Arrays.asList(withoutActor,withActor),2).get(0)==withActor);
  ContentSimilarity.Document unrelated=doc("","Зореліт летить крізь галактику, астронавти досліджують космос",null,null);ContentSimilarity.Index semantic=new ContentSimilarity.Index(Arrays.asList(heist.document,crime.document,unrelated));
  check("Related plot text beats unrelated plot text",semantic.text(heist.document,crime.document)>semantic.text(heist.document,unrelated));
  check("Strong negative match can be omitted entirely",!rank("a",Arrays.asList(dislike),none,Arrays.asList(crime,family),2).contains(crime));
  check("Disliking a story does not ban every film in its genre",rank("a",Arrays.asList(dislike),none,Arrays.asList(crime,family),2).contains(family));
  ContentSimilarity.Document english=doc("","Bank robbery: hostages, criminals, heist.",null,null),ukrainian=doc("","Пограбування. Злочинці взяли заручників у сховищі.",null,null);ContentSimilarity.Index themes=new ContentSimilarity.Index(Arrays.asList(english,ukrainian,unrelated));
  check("Theme vocabulary bridges Ukrainian and English plots",themes.similarity("/films/drama/a",null,english,"/films/drama/b",null,ukrainian)>themes.similarity("/films/drama/a",null,english,"/films/drama/c",null,unrelated));
  check("Empty metadata is safe",new ContentSimilarity.Index(Collections.emptyList()).similarity("/films/drama/a",null,null,"/films/comedy/b",null,null)<0.1);
  ContentSimilarity.Document oldDoc=doc("","",null,null),newDoc=doc("","",null,null);oldDoc.year=1990;newDoc.year=1990;oldDoc.country=newDoc.country="US";
  check("Country and year alone cannot imply a taste match",new ContentSimilarity.Index(Arrays.asList(oldDoc,newDoc)).similarity("/films/drama/a",null,oldDoc,"/series/comedy/b",null,newDoc)==0);
  RecommendationRanker.Candidate seenMany=c("/films/drama/repeated.html",null),fresh=c("/films/drama/fresh.html",null);seenMany.impressions=4;
  check("Repeated exposure gently favors a fresh equal match",rank("a",drama,none,Arrays.asList(seenMany,fresh),2).get(0)==fresh);
  check("Negative signals never create targeted genre fetches",RecommendationRanker.queries(Arrays.asList(dislike)).isEmpty());
  check("Short accidental launch remains eligible",!RecommendationRanker.meaningful(30_000,7_200_000));
  WatchTracker tracker=new WatchTracker();check("First sample cannot invent watch time",tracker.sample(0,0,true)==0);check("Advancing playback counts wall time",tracker.sample(500,500,true)==500);check("Pause adds no watch time",tracker.sample(1000,500,false)==0);check("Resuming does not count the pause gap",tracker.sample(1500,500,true)==0);check("Faster playback counts actual elapsed time",tracker.sample(2000,1500,true)==500);check("Seeking ahead is not watch time",tracker.sample(2500,60_000,true)==0);check("Playback after seek resumes counting",tracker.sample(3000,60_500,true)==500);check("Buffering stall is not watch time",tracker.sample(3500,60_500,true)==0);check("Background gaps are not watch time",tracker.sample(13_500,70_500,true)==0);check("Seeking backward is not watch time",tracker.sample(14_000,50_500,true)==0);tracker.reset();check("New stream resets observation baseline",tracker.sample(14_500,0,true)==0);
  RecommendationRanker.Candidate plain=c("https://one/films/drama/71.html","Драма"),seeAlso=c("https://one/films/drama/72.html","Драма");seeAlso.related=6;
  check("See-also titles of a liked film rank higher",links(rank("p",Arrays.asList(s("https://one/films/drama/70.html","Драма",6)),new HashSet<>(),Arrays.asList(plain,seeAlso),2)).get(0).equals(seeAlso.link));
  System.out.println("Recommendation checks passed: "+checks);
 }
}
