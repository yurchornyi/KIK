import kik.i18n.RecoveryPolicy;import java.util.*;
/** Failure injection without requiring network availability or an Android decoder. */
public final class RecoveryPolicyCheck {
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){RecoveryPolicy p=new RecoveryPolicy();Map<String,List<String>> q=new LinkedHashMap<String,List<String>>();q.put("1080p",Arrays.asList("first","second"));q.put("720p",Arrays.asList("third"));check(p.next(q,"1080p").equals("1080p\nfirst"));check(p.next(q,"1080p").equals("1080p\nsecond"));check(p.next(q,"1080p").equals("720p\nthird"));check(p.next(q,"1080p")==null);check(p.refresh());check(p.refresh());check(!p.refresh());p.progress(1000);p.interrupted();p.progress(20000);check(!p.refresh());p.progress(51000);check(p.refresh());check(RecoveryPolicy.media("https://video.example/a.mp4"));check(RecoveryPolicy.media("https://video.example/a.mp4:hls:manifest.m3u8?t=1"));for(String url:new String[]{"https://popads.net/a.mp4","https://x.doubleclick.net/a.mp4","https://video.example/ads/a.mp4","https://video.example/a.html","https://127.0.0.1/a.mp4","http://video.example/a.mp4","https://user:pass@video.example/a.mp4"})check(!RecoveryPolicy.media(url));System.out.println("Native recovery budget, alternatives, language-independent ranking and media guard passed");
  check(RecoveryPolicy.runtimeMs("2 ч. 22 мин.")==142*60000L&&RecoveryPolicy.runtimeMs("45 мин.")==45*60000L&&RecoveryPolicy.runtimeMs("1 год. 5 хв.")==65*60000L&&RecoveryPolicy.runtimeMs("128 min")==128*60000L&&RecoveryPolicy.runtimeMs(null)==0&&RecoveryPolicy.runtimeMs("")==0);
  // A 60 s stream of a two-hour film is the preview; a 150 s short with no known length, or a 60 s stream of a 90 s title, is the real thing.
  check(RecoveryPolicy.preview(60000,142*60000L)&&!RecoveryPolicy.preview(150000,0)&&!RecoveryPolicy.preview(60000,90000)&&RecoveryPolicy.preview(59000,0)&&!RecoveryPolicy.preview(0,142*60000L)&&!RecoveryPolicy.preview(200000,142*60000L));
  System.out.println("Recovery policy checks passed");
 }
}
