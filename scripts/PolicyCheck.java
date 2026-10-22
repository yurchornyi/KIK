import kik.i18n.PlaybackPolicy;
import java.util.*;
public class PolicyCheck {
 public static class Voice {
  String name;boolean premium;
  Voice(String name,boolean premium){this.name=name;this.premium=premium;}
  public String getName(){return name;}
  public boolean isPremium(){return premium;}
  public String getPopularityPercent(){return "50%";}
 }
 public static class Film {
  public String getRatingImdb(){return "7.9";}
  public String getVotesImdb(){return "123";}
  public String getRatingHDRezka(){return "8.9";}
  public String getVotesHDRezka(){return "999";}
 }
 public static void main(String[] args){
  Voice ru=new Voice("HDrezka Studio",false);
  Voice ua=new Voice("Украинский двухголосый (Украинский)",false);
  Voice dub=new Voice("Дубляж (Украинский)",false);
  Voice premium=new Voice("HDrezka Studio (Украинский)",true);
  Voice original=new Voice("Оригинал (+субтитры)",false);
  if(PlaybackPolicy.bestVoice(Arrays.asList(ru,ua),false)!=ua)throw new AssertionError("Ukrainian must win");
  if(PlaybackPolicy.bestVoice(Arrays.asList(ua,dub),false)!=dub)throw new AssertionError("Dubbing priority");
  if(PlaybackPolicy.bestVoice(Arrays.asList(premium,ua),false)!=ua)throw new AssertionError("Premium must be excluded");
  if(PlaybackPolicy.bestVoice(Arrays.asList(premium,ua),true)!=premium)throw new AssertionError("Premium only with entitlement");
  if(PlaybackPolicy.bestVoice(Arrays.asList(original,ru),false)!=ru)throw new AssertionError("Russian fallback");
  if(PlaybackPolicy.bestVoice(Arrays.asList(original),false)!=null)throw new AssertionError("Do not select original audio");
  if(PlaybackPolicy.bestVoice(Collections.emptyList(),false)!=null)throw new AssertionError("Empty choices");
  if(PlaybackPolicy.genres(Arrays.asList("a","b","c")).size()!=2)throw new AssertionError("At most two genres");
  if(!"IMDb".equals(PlaybackPolicy.rating(new Film())[0]))throw new AssertionError("Single preferred rating");
  Voice by=new Voice("Белорусский двухголосый (Белорусский)",false),author=new Voice("Живов",false),uaAuthor=new Voice("Одноголосый закадровый (Украинский)",false),ruDub=new Voice("Дубляж",false);
  if(PlaybackPolicy.bestVoice(Arrays.asList(by,author),false)!=author)throw new AssertionError("Russian before third languages");
  if(PlaybackPolicy.bestVoice(Arrays.asList(ruDub,uaAuthor),false)!=uaAuthor)throw new AssertionError("Any Ukrainian track before Russian dubbing");
  if(!(PlaybackPolicy.order("Оригинал (+субтитры)",false,false)>PlaybackPolicy.order("Живов",false,false)))throw new AssertionError("Original listed last");
  if(!(PlaybackPolicy.order("Дубляж",true,false)>PlaybackPolicy.order("Дубляж",false,false)))throw new AssertionError("Camrip lower");
  if(!"en".equals(PlaybackPolicy.lang("Оригинал (+субтитры)"))||!"ru".equals(PlaybackPolicy.lang("Red Head Sound"))||!"uk".equals(PlaybackPolicy.lang("Украинский многоголосый (Украинский)"))||!"other".equals(PlaybackPolicy.lang("Грузинский многоголосый (Грузинский)")))throw new AssertionError("Language groups");
  if(!PlaybackPolicy.langs(Arrays.asList("Оригинал (+субтитры)","Дубляж","Coldfilm","Украинский многоголосый (Украинский)","Грузинский (Грузинский)")).equals(Arrays.asList("uk","ru","en")))throw new AssertionError("Three languages, best first, third languages hidden");
  System.out.println("Playback policy checks passed: 15");
 }
}
