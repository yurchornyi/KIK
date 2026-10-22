package kik.i18n;
import java.lang.reflect.Method;
import java.util.*;

/** Locale and studio priority without changing provider IDs or entitlement checks. */
public final class PlaybackPolicy {
 public static Object call(Object target,String name) {
  if(target==null)return null;
  try {Method m=target.getClass().getMethod(name);m.setAccessible(true);return m.invoke(target);}catch(Exception e){return null;}
 }
 public static String string(Object target,String name) {Object v=call(target,name);return v==null?"":String.valueOf(v);}
 public static int rank(String title) {
  String s=title.toLowerCase(Locale.ROOT);
  boolean uk=s.contains("укра")||s.contains("ukrain")||s.contains("украї");
  if(!uk && (s.contains("оригинал")||s.contains("оригінал")||s.contains("original")||s.contains("англий")||s.contains("english")||s.contains("польск")||s.contains("корей")||s.contains("япон")||s.contains("франц")||s.contains("испан")||s.contains("немец")))return Integer.MAX_VALUE;
  int score=uk?0:1000;
  if(s.contains("дубляж")||s.contains("дубльован"))score+=0;
  else if(s.contains("hdrezka")||s.contains("rezka studio"))score+=15;
  else if(s.contains("lostfilm"))score+=25;
  else if(s.contains("многоголос")||s.contains("багатоголос"))score+=35;
  else if(s.contains("двухголос")||s.contains("двоголос"))score+=80;
  else if(s.contains("одноголос")||s.contains("авторск"))score+=150;
  else score+=100;
  if(s.contains("любител")||s.contains("аматор"))score+=200;
  if(s.contains("camrip")||s.contains("экран")||s.contains("екран"))score+=400;
  return score;
 }
 /** Keeps the voice saved with the viewer's progress so resume continues the same track. */
 public static Object bestVoice(String savedId,List<?> list,boolean premiumAllowed) {
  if(list==null)return null;
  if(savedId!=null&&!savedId.trim().isEmpty())for(Object v:list){
   boolean premium=Boolean.TRUE.equals(call(v,"isPremium"))||Boolean.TRUE.equals(call(v,"getIsPremium"));
   if(savedId.trim().equals(string(v,"getId").trim())&&(premiumAllowed||!premium))return v;
  }
  return bestVoice(list,premiumAllowed);
 }
 public static Object bestVoice(List<?> list,boolean premiumAllowed) {
  if(list==null)return null;
  Object best=null;double bestRank=Double.MAX_VALUE;
  for(Object v:list) {
   boolean premium=Boolean.TRUE.equals(call(v,"isPremium"))||Boolean.TRUE.equals(call(v,"getIsPremium"));
   if(premium&&!premiumAllowed)continue;
   int base=rank(string(v,"getName"));if(base==Integer.MAX_VALUE)continue;
   double popularity=0;
   try{popularity=Double.parseDouble(string(v,"getPopularityPercent").replace('%',' ').trim().replace(',','.'));}catch(Exception ignored){}
   double score=base-Math.min(100,Math.max(0,popularity))/1000;
   if(score<bestRank){bestRank=score;best=v;}
  }
  return best;
 }
 public static ArrayList<?> sources(ArrayList<?> input){ArrayList<Object> result=new ArrayList<Object>();if(input!=null)for(Object source:input)if(!"torrserver".equals(string(source,"getId")))result.add(source);return result;}
 public static List<?> genres(List<?> input) {return input==null||input.size()<=2?input:new ArrayList<Object>(input.subList(0,2));}
 public static String[] rating(Object film) {
  String[][] choices={{"IMDb","Imdb"},{"Рейтинг","HDRezka"},{"КП","Kinopoisk"}};
  for(String[] c:choices){String value=string(film,"getRating"+c[1]);if(!value.trim().isEmpty())return new String[]{c[0],value,string(film,"getVotes"+c[1])};}
  return null;
 }
}
