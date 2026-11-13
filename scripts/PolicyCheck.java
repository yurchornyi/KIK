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
  double[] ads={2500,900,920.6,1740,1770};
  if(PlaybackPolicy.adSkip(ads,899_900,2_500_000)!=-1||PlaybackPolicy.adSkip(ads,900_000,2_500_000)!=920_600||PlaybackPolicy.adSkip(ads,1750_000,2_501_500)!=1_770_000)throw new AssertionError("Jump over a known ad clip");
  if(PlaybackPolicy.adSkip(ads,919_500,2_500_000)!=-1||PlaybackPolicy.adSkip(ads,910_000,2_510_000)!=-1||PlaybackPolicy.adSkip(null,910_000,2_500_000)!=-1||PlaybackPolicy.adSkip(ads,910_000,0)!=-1)throw new AssertionError("Never skip on another cut, at the very end or without data");
  if(!PlaybackPolicy.sponsoredVoice("колдфильм (Coldfilm)")||PlaybackPolicy.sponsoredVoice("TVShows"))throw new AssertionError("Sponsored voices");
  // Intro and credits (EpisodeMarks), the same cases as web/episode-marks.test.mjs.
  double[] marks={2668,2565,0.9,4.9,176,183};
  if(PlaybackPolicy.openingLogo(marks,2_668_400)!=4_900||PlaybackPolicy.openingLogo(marks,2_700_000)!=0||PlaybackPolicy.openingLogo(new double[]{100,-1,0,40},100_000)!=0||PlaybackPolicy.openingLogo(null,100_000)!=0)throw new AssertionError("Only a short opening logo of this very file is passed");
  if(PlaybackPolicy.introSkip(marks,178_000,2_668_000)!=183_000||PlaybackPolicy.introSkip(marks,182_000,2_668_000)!=-1||PlaybackPolicy.introSkip(marks,10_000,2_668_000)!=-1||PlaybackPolicy.introSkip(marks,178_000,3_000_000)!=-1)throw new AssertionError("Skip-intro button inside an intro of this file");
  long[] sure=PlaybackPolicy.nextOffer(marks,2_668_000),guess=PlaybackPolicy.nextOffer(null,1_300_000),other=PlaybackPolicy.nextOffer(marks,3_000_000),odd=PlaybackPolicy.nextOffer(new double[]{2668,100},2_668_000);
  if(sure[0]!=2_565_000||sure[1]!=1||guess[1]!=0||guess[0]!=1_260_000||other[1]!=0||odd[1]!=0||PlaybackPolicy.nextOffer(marks,0)!=null||PlaybackPolicy.nextOffer(null,60_000)!=null)throw new AssertionError("Next episode counts down only after scanned credits");
  if(PlaybackPolicy.knobAt(1000,32,0,600_000)!=0||PlaybackPolicy.knobAt(1000,32,300_000,600_000)!=484||PlaybackPolicy.knobAt(1000,32,600_000,600_000)!=968||PlaybackPolicy.knobAt(1000,32,700_000,600_000)!=968||PlaybackPolicy.knobAt(0,32,1,2)!=0||PlaybackPolicy.knobAt(1000,32,5,0)!=0)
   throw new AssertionError("Progress knob stays on the track");
  long film=6_600_000;
  if(PlaybackPolicy.creditsAt(film)!=6_270_000||PlaybackPolicy.creditsAt(1_320_000)!=1_254_000||PlaybackPolicy.creditsAt(9_000_000)!=8_640_000)throw new AssertionError("Credits are the last 5%, 45 s to 6 min");
  if(!PlaybackPolicy.askRating(6_300_000,film,3_000_000,false,false,0)||PlaybackPolicy.askRating(6_200_000,film,3_000_000,false,false,0))throw new AssertionError("Ask at a film's credits");
  if(PlaybackPolicy.askRating(6_300_000,film,60_000,false,false,0)||PlaybackPolicy.askRating(59_000,60_000,60_000,false,false,0))throw new AssertionError("Never ask after a seek to the end or a preview");
  if(PlaybackPolicy.askRating(2_600_000,2_700_000,2_000_000,true,false,2)||!PlaybackPolicy.askRating(2_600_000,2_700_000,2_000_000,true,true,0)||!PlaybackPolicy.askRating(2_600_000,2_700_000,2_000_000,true,false,3))throw new AssertionError("A series is asked at its last episode or after three episodes");
  System.out.println("Playback policy checks passed: 22");
 }
}
