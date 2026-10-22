package kik.i18n;
import java.util.*;

/** Offline content similarity; source-language plots stay in the same vocabulary. */
final class ContentSimilarity {
 static final class Document {
  String title="",original="",plot="",country="";int year;double rating;
  final Set<String> genres=new HashSet<String>(),people=new HashSet<String>();
 }
 private static final Set<String> STOP=new HashSet<String>(Arrays.asList(("the and with from this that into their they have when after about who his her for are was were has its not but you our your she him them " +
  "это как для что его она они которые который чтобы после перед очень всех свои свой себя уже только также года лет жизнь жизни фильм история сериала сериал главный главные герои герой оказывается становится время однажды новый новая может должен вместе однако " +
  "цей ця це для що його вона вони який яка щоб після перед свої себе вже лише також року років життя фільм історія серіал головний герої герой стає час одного новий нова може разом проте").split("\\s+")));
 private static final java.util.regex.Pattern LATIN=java.util.regex.Pattern.compile("[a-z-]+");
 static String canonicalGenre(String value){
  String s=value==null?"":value.toLowerCase(Locale.ROOT).trim();
  String[][] aliases={{"drama","драм"},{"comedy","комед"},{"action","боев","бойов"},{"thriller","трилл","трил"},{"crime","кримин","кримін"},{"detective","детект"},{"fiction","фантаст","sci-fi"},{"fantasy","фэнтез","фентез"},{"horror","ужас","жах"},{"romance","мелодрам","романт"},{"adventures","приключ","пригод"},{"military","военн","військ"},{"historical","истор","істор"},{"biographical","биограф","біограф"},{"documentary","документ"},{"family","семейн","сімейн"},{"musical","мюзик","музык","музич"},{"sport","спорт"},{"western","вестерн"}};
  for(String[] group:aliases)for(String a:group)if(s.equals(a)||s.startsWith(a)&&!LATIN.matcher(a).matches())return group[0];return s;
 }
 static Set<String> genres(String link,String value,Document doc){
  Set<String> out=new HashSet<String>();String[] p=RecommendationRanker.parts(link);if(!p[1].isEmpty())out.add(canonicalGenre(p[1]));
  if(value!=null)for(String s:value.split("[,;/|]"))if(!s.trim().isEmpty())out.add(canonicalGenre(s));
  if(doc!=null)for(String s:doc.genres)if(!s.trim().isEmpty())out.add(canonicalGenre(s));return out;
 }
 private static final java.util.regex.Pattern WORDS=java.util.regex.Pattern.compile("[^\\p{L}\\p{N}]+"),THEME_WORDS=java.util.regex.Pattern.compile("[^\\p{L}]+"),DIGITS=java.util.regex.Pattern.compile("\\d+"),CYRILLIC=java.util.regex.Pattern.compile("[а-яіїєґ]+"),SUFFIX=java.util.regex.Pattern.compile("(иями|ами|ями|ого|ому|ими|ыми|ий|ый|ой|ая|яя|ое|ее|ые|ие|ов|ам|ям|ах|ях|ом|ем|ою|ую|ій|ої|ою|ів|ами|ях)$");
 private static Map<String,Double> tokens(Document d){
  Map<String,Double> out=new HashMap<String,Double>();if(d==null)return out;add(out,d.title+" "+d.original,1.8);add(out,d.plot,1);return out;
 }
 private static Set<String> themes(Document d){
  Set<String> out=new HashSet<String>();if(d==null)return out;String[] words=THEME_WORDS.split((d.title+" "+d.plot).toLowerCase(Locale.ROOT));
  String[][] groups={{"investigation","расслед","розсліду","детектив","сыщик","слідств","detective","murder","вбивств","убийств"},{"heist","ограб","пограб","heist","robbery","сховищ","заложник","заручник"},{"space","космос","космич","косміч","галак","астронав","зореліт","starship","spaceship"},{"survival","выжива","вижива","survival","survive","застряг","ловушк","пастц"},{"family","родин","семейн","сімейн","матір","матер","мать","батьк","отец","daughter","father","mother"},{"romance","кохан","любов","влюбл","закох","romance","romantic"},{"war","военн","військ","солдат","баталь","фронт","soldier","battlefield"},{"supernatural","призрак","привид","демон","проклят","haunted","ghost","demon"},{"youth","подрост","підліт","старшоклас","школьник","школяр","teenager","teenage"},{"politics","полит","політ","президент","корруп","коруп","politic","president"},{"time-travel","будущ","майбут","прошл","минул","timetravel"},{"zombies","зомб","zombie","апокалип","апокаліп"}};
  for(String[] group:groups)for(int i=1;i<group.length&&!out.contains(group[0]);i++)for(String word:words)if(word.startsWith(group[i])){out.add(group[0]);break;}return out;
 }
 private static void add(Map<String,Double> out,String value,double weight){
  if(value==null)return;value=value.substring(0,Math.min(5000,value.length())).toLowerCase(Locale.ROOT).replace('ё','е');
  for(String s:WORDS.split(value)){
   if(s.length()<3||STOP.contains(s)||DIGITS.matcher(s).matches())continue;
   // Conservative suffix reduction, not a language model or a full morphological parser.
   if(CYRILLIC.matcher(s).matches()&&s.length()>6)s=SUFFIX.matcher(s).replaceFirst("");
   out.put(s,Math.min(4d,out.getOrDefault(s,0d)+weight));
  }
 }
 static final class Index {
  final Map<Document,Map<String,Double>> vectors=new IdentityHashMap<Document,Map<String,Double>>();
  final Map<Document,Set<String>> topics=new IdentityHashMap<Document,Set<String>>();
  Index(Collection<Document> documents){
   Map<String,Integer> df=new HashMap<String,Integer>();for(Document d:documents)if(d!=null&&!vectors.containsKey(d)){Map<String,Double> v=tokens(d);vectors.put(d,v);topics.put(d,themes(d));for(String token:v.keySet())df.put(token,df.getOrDefault(token,0)+1);}
   int n=vectors.size();for(Map<String,Double> v:vectors.values()){double length=0;for(Map.Entry<String,Double> e:v.entrySet()){double w=(1+Math.log(e.getValue()))*(1+Math.log((n+1d)/(df.get(e.getKey())+1d)));e.setValue(w);length+=w*w;}if(length>0){length=Math.sqrt(length);for(Map.Entry<String,Double> e:v.entrySet())e.setValue(e.getValue()/length);}}
  }
  double text(Document a,Document b){Map<String,Double> x=vectors.get(a),y=vectors.get(b);if(x==null||y==null)return 0;double sum=0;for(Map.Entry<String,Double> e:x.entrySet())sum+=e.getValue()*y.getOrDefault(e.getKey(),0d);return Math.min(1,sum);}
  private static final class Features{final Set<String> genres;final String category;Features(String link,String genre,Document doc){genres=ContentSimilarity.genres(link,genre,doc);category=RecommendationRanker.parts(link)[0];}}
  private final Map<String,Features> features=new HashMap<String,Features>();
  private Features features(String link,String genre,Document doc){String key=link+"|"+genre;Features f=features.get(key);if(f==null){f=new Features(link,genre,doc);features.put(key,f);}return f;}
  double similarity(String a,String ag,Document ad,String b,String bg,Document bd){
   Features af=features(a,ag,ad),bf=features(b,bg,bd);double genre=overlap(af.genres,bf.genres);
   double people=ad==null||bd==null?0:overlap(ad.people,bd.people),era=ad==null||bd==null||ad.year==0||bd.year==0?0:Math.max(0,1-Math.abs(ad.year-bd.year)/20d);
   // Date/country alone cannot make two unrelated films similar.
   double topic=topics.containsKey(ad)&&topics.containsKey(bd)?overlap(topics.get(ad),topics.get(bd)):0;
   double core=0.33*genre+0.35*text(ad,bd)+0.13*topic+0.14*people+(af.category.equals(bf.category)?0.05:0);
   if(core>0.1)core+=0.025*era+(ad!=null&&bd!=null&&!ad.country.isEmpty()&&ad.country.equals(bd.country)?0.015:0);
   return Math.min(1,core);
  }
 }
 static double overlap(Set<String> a,Set<String> b){if(a.isEmpty()||b.isEmpty())return 0;int common=0;for(String s:a)if(b.contains(s))common++;return (double)common/Math.max(a.size(),b.size());}
}
