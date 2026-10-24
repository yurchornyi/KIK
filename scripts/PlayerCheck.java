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
  Method keys=KikPlayerActivity.class.getDeclaredMethod("playerKey",int.class);keys.setAccessible(true);
  for(int key:new int[]{4,19,20,21,22,23,62,66,85,89,90,126,127})require((Boolean)keys.invoke(null,key),"Player remote key must be handled");
  for(int key:new int[]{3,24,25,26,164,187})require(!(Boolean)keys.invoke(null,key),"System key must keep its system behaviour");
  Method label=KikPlayerActivity.class.getDeclaredMethod("sleepLabel",int.class);label.setAccessible(true);
  require("15 хв".equals(label.invoke(null,15))&&"45 хв".equals(label.invoke(null,45)),"Sleep minutes");require("1 год".equals(label.invoke(null,60))&&"2 год".equals(label.invoke(null,120)),"Sleep whole hours");require("1,5 год".equals(label.invoke(null,90)),"Sleep hour and a half");
  Method left=KikPlayerActivity.class.getDeclaredMethod("sleepLeft",long.class);left.setAccessible(true);
  require("30 с".equals(left.invoke(null,30000L))&&"1 с".equals(left.invoke(null,200L)),"Sleep seconds inside the last minute");require("1 хв".equals(left.invoke(null,60000L))&&"2 хв".equals(left.invoke(null,60001L)),"Sleep minutes round up");require("1 год".equals(left.invoke(null,3600000L))&&"1 год 5 хв".equals(left.invoke(null,3900000L)),"Sleep hours and minutes");
  Field minutes=KikPlayerActivity.class.getDeclaredField("SLEEP_MINUTES");minutes.setAccessible(true);int[] choices=(int[])minutes.get(null);require(choices.length==6&&choices[0]==15&&choices[5]==120,"Sleep choices 15 min to 2 h");
  System.out.println("Player checks passed: 37");
 }
}
