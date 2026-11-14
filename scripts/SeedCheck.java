package kik.i18n;
import java.util.*;
/** «Тому що ви дивилися» (14.11): which titles may head a row, at most six of them, and how they turn by the day. */
public final class SeedCheck {
 static void check(String message,boolean ok){if(!ok)throw new AssertionError(message);}
 static RecommendationRanker.Signal s(String link,double weight,double seed){return new RecommendationRanker.Signal(link,null,weight,seed);}
 static List<String> firstTwo(List<RecommendationRanker.Signal> seeds){List<String> out=new ArrayList<>();for(int i=0;i<Math.min(2,seeds.size());i++)out.add(seeds.get(i).link);return out;}
 public static void main(String[] args){
  long day=86_400_000L,noon=20_000*day+12*3_600_000L;
  Map<String,String> titles=new HashMap<>();List<RecommendationRanker.Signal> signals=new ArrayList<>();
  // Watched in the last month, the weeks wearing their weight down: Show 1 the freshest.
  for(int i=1;i<=6;i++){String link="https://hdrezka.sh/series/drama/"+i+"-show-"+i+".html";titles.put(RecommendationRanker.key(link),"Show "+i);signals.add(s(link,10-i,3));}
  String old="https://hdrezka.sh/films/drama/50-old.html";titles.put(RecommendationRanker.key(old),"Old");signals.add(s(old,9.5,0));
  String liked="https://hdrezka.sh/films/comedy/51-liked.html";titles.put(RecommendationRanker.key(liked),"Liked");signals.add(new RecommendationRanker.Signal(liked,null,RecommendationRanker.LIKED));
  String saved="https://hdrezka.sh/films/comedy/53-saved.html";titles.put(RecommendationRanker.key(saved),"Saved");signals.add(new RecommendationRanker.Signal(saved,null,2));
  String nameless="https://hdrezka.sh/films/drama/52-nameless.html";signals.add(s(nameless,20,4));
  List<RecommendationRanker.Signal> today=RecommendationRanker.seeds(signals,titles,noon,0);
  Set<String> got=new HashSet<>();for(RecommendationRanker.Signal x:today)got.add(x.link);
  check("six titles at most",today.size()==RecommendationRanker.SEEDS);
  check("a liked title can head a row",got.contains(liked));
  check("a title watched over 30 days ago cannot",!got.contains(old));
  check("a saved title cannot",!got.contains(saved));
  check("a title without a name cannot",!got.contains(nameless));
  check("the weakest of seven is left out",!got.contains("https://hdrezka.sh/series/drama/6-show-6.html"));
  // Each day starts two further on: three days show all six at the top.
  Set<String> shown=new HashSet<>();List<String> first=firstTwo(today),next=firstTwo(RecommendationRanker.seeds(signals,titles,noon+day,0));
  check("tomorrow's two rows stand on other titles",Collections.disjoint(first,next));
  for(int d=0;d<3;d++)shown.addAll(firstTwo(RecommendationRanker.seeds(signals,titles,noon+d*day,0)));
  check("three days turn through all six",shown.equals(got));
  check("the same day gives the same rows",RecommendationRanker.seeds(signals,titles,noon+3_600_000L,0).get(0).link.equals(today.get(0).link));
  // The day is the viewer's: 22:00 UTC is already tomorrow three hours east.
  check("the local day counts",firstTwo(RecommendationRanker.seeds(signals,titles,noon+10*3_600_000L,3*3_600_000L)).equals(next));
  check("one title is not turned",RecommendationRanker.seeds(Collections.singletonList(signals.get(0)),titles,noon,0).size()==1);
  System.out.println("SeedCheck ok");
 }
}
