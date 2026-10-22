package kik.i18n;

import java.net.URI;
import java.util.Locale;

/** Pure policy shared by the downloader and regression checks. No credentials in release URLs. */
public final class UpdatePolicy {
 public static final long MAX_APK=96L*1024*1024;
 public static final int MAX_METADATA=64*1024;
 public static boolean releaseUrl(String value,String repository){
  try{URI u=new URI(value);return https(u)&&"github.com".equals(u.getHost())
   &&u.getRawPath().startsWith("/"+repository+"/releases/download/")
   &&u.getRawQuery()==null&&u.getRawFragment()==null&&!u.getRawPath().contains("%")
   &&!u.getRawPath().contains("/../")&&!u.getRawPath().contains("/./");}catch(Exception e){return false;}
 }
 public static boolean downloadHop(String value,String repository){
  try{URI u=new URI(value);if(!https(u)||u.getRawFragment()!=null)return false;
   if("github.com".equals(u.getHost()))return releaseUrl(value,repository)||
    (u.getRawPath().equals("/"+repository+"/releases/latest/download/update.json")&&u.getRawQuery()==null);
   return "release-assets.githubusercontent.com".equals(u.getHost())||"objects.githubusercontent.com".equals(u.getHost());
  }catch(Exception e){return false;}
 }
 public static boolean downloadHop(String value,String repository,String manifest){
  if(value.equals(manifest))try{URI u=new URI(value);return https(u)&&u.getRawQuery()==null&&u.getRawFragment()==null;}catch(Exception e){return false;}
  return downloadHop(value,repository);
 }
 private static boolean https(URI u){return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443);}
 public static boolean hash(String h){return h!=null&&h.matches("[a-fA-F0-9]{64}");}
 public static String normalizeHash(String h){return h.toLowerCase(Locale.ROOT);}
 public static boolean eligible(long installed,long next,int sdk,int minSdk,long size){
  return installed>0&&next>installed&&next<=Integer.MAX_VALUE&&minSdk>=24&&minSdk<=sdk&&size>0&&size<=MAX_APK;
 }
 private UpdatePolicy(){}
}
