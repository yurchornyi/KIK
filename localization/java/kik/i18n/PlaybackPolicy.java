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
 private static final String[] UKRAINIAN={"укра","ukrain","украї","1+1","постмодерн","postmodern","le doyen","dniprofilm","так треба","uateam","ua team","hurtom","цікава ідея","eneida"};
 private static final String[] FOREIGN={"оригинал","оригінал","original","субтитр","англий","англій","english","польск","польськ","корей","япон","франц","испан","іспан","немец","німец","грузин","белорус","білорус","казах","узбек","армян","вірмен","азербайдж","литов","латыш","эстон","турец","італій","итальян","китай","чешск","португал","иврит","арабск"};
 private static boolean any(String s,String[] words){for(String w:words)if(s.contains(w))return true;return false;}
 /** 0 Ukrainian, 1 Russian (unmarked tracks on the source are Russian), 2 original or another language. */
 public static int language(String title) {
  String s=title==null?"":title.toLowerCase(Locale.ROOT);
  if(any(s,UKRAINIAN))return 0;
  return any(s,FOREIGN)?2:1;
 }
 /** Studio quality inside one language: lower is better. */
 static int studio(String s) {
  int score;
  if(s.contains("дубляж")||s.contains("дубльован"))score=0;
  else if(s.contains("hdrezka")||s.contains("rezka studio"))score=15;
  else if(s.contains("lostfilm"))score=25;
  else if(s.contains("newstudio")||s.contains("кубик в кубе")||s.contains("baibako")||s.contains("coldfilm")||s.contains("tvshows")||s.contains("jaskier")||s.contains("red head sound")||s.contains("amedia")||s.contains("кураж")||s.contains("постмодерн")||s.contains("postmodern")||s.contains("le doyen")||s.contains("1+1"))score=30;
  else if(s.contains("многоголос")||s.contains("багатоголос"))score=35;
  else if(s.contains("студия")||s.contains("студія")||s.contains("studio"))score=45;
  else if(s.contains("двухголос")||s.contains("двоголос"))score=80;
  else if(s.contains("одноголос")||s.contains("авторск")||s.contains("автор"))score=150;
  else score=100;
  if(s.contains("любител")||s.contains("аматор"))score+=200;
  if(s.contains("camrip")||s.contains("экран")||s.contains("екран"))score+=400;
  return score;
 }
 /** Full order for every track: language first (Ukrainian, then Russian, then the rest), then studio quality. */
 public static int order(String title,boolean camrip,boolean ads) {
  String s=title==null?"":title.toLowerCase(Locale.ROOT);
  return language(title)*1000+studio(s)+(camrip?400:0)+(ads?60:0);
 }
 /** Automatic choice never starts original or third-language audio. */
 public static int rank(String title) {
  int language=language(title);
  return language==2?Integer.MAX_VALUE:order(title,false,false);
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
