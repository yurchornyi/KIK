import kik.i18n.UpdatePolicy;
public final class UpdatePolicyCheck {
 static int count;
 static void test(boolean ok){count++;if(!ok)throw new AssertionError("Update policy check "+count);}
 public static void main(String[] args){String repo="yurchornyi/KIK",url="https://github.com/"+repo+"/releases/download/v1.2.11/KIK.apk";
  test(UpdatePolicy.releaseUrl(url,repo));
  for(String bad:new String[]{"http://github.com/"+repo+"/releases/download/v1/a.apk","https://github.com.evil.test/"+repo+"/releases/download/v1/a.apk",url+"?token=secret",url+"#fragment",url.replace("KIK/releases","other/releases"),url.replace("/v1.2.11/","/../"),url.replace("/v1.2.11/","/%2e%2e/"),url.replace("github.com/","user@github.com/"),url.replace("github.com/","github.com:8443/"),"file:///tmp/app.apk"})test(!UpdatePolicy.releaseUrl(bad,repo));
  test(UpdatePolicy.downloadHop("https://github.com/"+repo+"/releases/latest/download/update.json",repo));
  test(UpdatePolicy.downloadHop("https://release-assets.githubusercontent.com/test?sig=signed",repo));
  test(!UpdatePolicy.downloadHop("https://evil.githubusercontent.com/test",repo));
  test(!UpdatePolicy.downloadHop("http://release-assets.githubusercontent.com/test",repo));
  test(!UpdatePolicy.downloadHop("https://github.com/other/repo/releases/latest/download/update.json",repo));
  test(UpdatePolicy.eligible(11,12,34,24,25_000_000));
  test(!UpdatePolicy.eligible(11,11,34,24,25_000_000));
  test(!UpdatePolicy.eligible(12,11,34,24,25_000_000));
  test(!UpdatePolicy.eligible(11,12,34,35,25_000_000));
  test(!UpdatePolicy.eligible(11,12,34,23,25_000_000));
  test(!UpdatePolicy.eligible(11,12,34,24,0));
  test(!UpdatePolicy.eligible(11,12,34,24,UpdatePolicy.MAX_APK+1));
  test(!UpdatePolicy.eligible(11,Long.MAX_VALUE,34,24,1));
  test(UpdatePolicy.hash(new String(new char[64]).replace('\0','a')));
  test(!UpdatePolicy.hash("aabbcc"));test(!UpdatePolicy.hash(null));
  System.out.println("Update policy: "+count+" checks passed");
 }
}
