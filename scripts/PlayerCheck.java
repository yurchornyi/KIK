import kik.i18n.*;import java.lang.reflect.*;import java.util.*;
public final class PlayerCheck {
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Method parse=KikPlayerActivity.class.getDeclaredMethod("vtt",String.class);parse.setAccessible(true);
  String fixture="WEBVTT\r\n\r\n00:01.000 --> 00:03.500\r\n<i>Привіт</i>\r\nСвіт\r\n\r\n1\r\n01:02:03,456 --> 01:02:05,000\r\nДруга репліка";
  List<Object[]> cues=(List<Object[]>)parse.invoke(null,fixture);
  require(cues.size()==2,"VTT/SRT cues");require((Long)cues.get(0)[0]==1000L&&(Long)cues.get(0)[1]==3500L,"Short timestamps");require("Привіт\nСвіт".equals(cues.get(0)[2]),"Markup and multiline text");require((Long)cues.get(1)[0]==3723456L,"Hour timestamp and comma");require(((List)parse.invoke(null,"invalid")).isEmpty(),"Invalid subtitles");
  Method num=KikPlayerActivity.class.getDeclaredMethod("num",String.class);num.setAccessible(true);require((Integer)num.invoke(null,"4k")==2160,"4K resolution");require((Integer)num.invoke(null,"2K")==1440,"2K resolution");require((Integer)num.invoke(null,"1080p Ultra")>(Integer)num.invoke(null,"1080p"),"Ultra ordering");
  Site.Film f=new Site.Film();for(int i=1;i<=3;i++){Site.Episode e=new Site.Episode();e.season=i==3?"2":"1";e.episode=i==3?"1":String.valueOf(i);f.episodes.add(e);}
  Method next=KikPlayerActivity.class.getDeclaredMethod("next",Site.Film.class,String.class,String.class);next.setAccessible(true);require(next.invoke(null,f,"1","1")==f.episodes.get(1),"Next episode");require(next.invoke(null,f,"1","2")==f.episodes.get(2),"Next season");require(next.invoke(null,f,"2","1")==null,"Last episode");
  System.out.println("Player checks passed: 11");
 }
}
