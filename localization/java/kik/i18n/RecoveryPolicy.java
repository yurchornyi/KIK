package kik.i18n;
import java.net.URI;import java.util.*;
/** Shared native outage budget and direct-media filtering. No provider HTML can enter the player. */
public final class RecoveryPolicy {
 private final Set<String> tried=new HashSet<String>();private int switches,refreshes;private long stable;
 public void reset(){tried.clear();switches=0;refreshes=0;stable=0;}
 public String next(Map<String,List<String>> sources,String wanted){if(switches>=10)return null;List<String> qualities=new ArrayList<String>(sources.keySet());qualities.sort((a,b)->Integer.compare(rank(a,wanted),rank(b,wanted)));for(String q:qualities)for(String url:sources.get(q))if(tried.add(url)){switches++;return q+"\n"+url;}return null;}
 public boolean refresh(){if(refreshes>=2||switches>=10)return false;refreshes++;return true;}
 public void used(String url){if(tried.add(url))switches++;}
 public void progress(long now){if(stable==0)stable=now;if(now-stable>=30000)reset();}
 public void interrupted(){stable=0;}
 /** The site's running time («2 ч. 22 мин.», «45 хв.», «128 min») in ms; 0 when unknown. */
 public static long runtimeMs(String text){
  if(text==null)return 0;String s=text.toLowerCase(Locale.ROOT);long h=0,m=0;
  java.util.regex.Matcher a=java.util.regex.Pattern.compile("(\\d+)\\s*(ч|год|h)").matcher(s);if(a.find())h=Long.parseLong(a.group(1));
  java.util.regex.Matcher b=java.util.regex.Pattern.compile("(\\d+)\\s*(мин|хв|min)").matcher(s);if(b.find())m=Long.parseLong(b.group(1));
  else if(h==0){java.util.regex.Matcher n=java.util.regex.Pattern.compile("\\d+").matcher(s);if(n.find())m=Long.parseLong(n.group());}
  return (h*60+m)*60000L;
 }
 /**
  * Without premium the source's top quality is a one-minute preview of the film. A stream counts as one only when it is
  * short and the title is known to run much longer (the site's running time or a saved full duration); with no length
  * known, only under 90 s. A short film or episode keeps its best quality.
  */
 public static boolean preview(long ms,long expectedMs){if(ms<=0)return false;if(expectedMs>0)return ms<180000&&ms*2<expectedMs;return ms<90000;}
 private static int height(String q){String s=q==null?"":q.toLowerCase(Locale.ROOT);if(s.contains("4k"))return 2160;if(s.contains("2k"))return 1440;java.util.regex.Matcher m=java.util.regex.Pattern.compile("[0-9]+").matcher(s);return m.find()?Integer.parseInt(m.group()):1080;}
 private static int rank(String q,String want){if(q.equals(want))return -1;int h=height(q),target=height(want);return Math.abs(h-target)+(h>target?3000:0);}
 /** A title page of the catalogue (or its Ukrainian partner), never another site: what an outside intent may open. */
 public static boolean title(String value){try{URI u=new URI(value);String host=u.getHost(),path=u.getPath();if(!"https".equals(u.getScheme())||host==null||u.getUserInfo()!=null||path==null)return false;host=host.toLowerCase(Locale.ROOT);return (host.matches("(www\\.)?(hdrezka\\.(sh|ag|website|me|ac)|hdrezka-home\\.tv|rezka\\.ag)")&&path.matches("/(films|series|cartoons|animation|documentary)/[a-z0-9/_-]+\\.html"))||(host.equals("uaserials.com")&&path.matches("/[0-9]+-[a-z0-9-]+\\.html"));}catch(Exception e){return false;}}
 public static boolean media(String value){try{URI u=new URI(value);String host=u.getHost(),path=u.getPath();if(!"https".equals(u.getScheme())||host==null||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443)return false;host=host.toLowerCase(Locale.ROOT);if(host.equals("localhost")||host.contains(":")||host.matches("[0-9.]+"))return false;for(String ad:new String[]{"doubleclick.net","googlesyndication.com","googleadservices.com","popads.net","popcash.net","adsterra.com","exoclick.com"})if(host.equals(ad)||host.endsWith("."+ad))return false;return path!=null&&path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m3u8)($|[/:].*)")&&!path.toLowerCase(Locale.ROOT).matches(".*(^|/)(ads?|vast|vpaid|preroll|popunder)(/|\\.).*");}catch(Exception e){return false;}}
}
