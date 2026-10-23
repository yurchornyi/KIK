import kik.i18n.DohPolicy;
public final class DohCheck {
 static int count;
 static void test(boolean ok){count++;if(!ok)throw new AssertionError("DoH check "+count);}
 public static void main(String[] args)throws Exception{
  DohPolicy.Answer a=DohPolicy.parse("{\"Status\":0,\"Question\":[{\"name\":\"hdrezka.sh\",\"type\":1}],\"Answer\":[{\"name\":\"hdrezka.sh\",\"type\":5,\"TTL\":30,\"data\":\"cdn.example.\"},{\"name\":\"cdn.example\",\"type\":1,\"TTL\":120,\"data\":\"104.21.5.9\"},{\"name\":\"cdn.example\",\"type\":1,\"TTL\":900,\"data\":\"172.67.1.2\"}]}");
  test(a.addresses.size()==2&&a.addresses.get(0).equals("104.21.5.9"));test(a.ttlSeconds==300);
  test(DohPolicy.parse("{\"Status\":3,\"Answer\":[{\"type\":1,\"data\":\"1.2.3.4\"}]}").addresses.isEmpty());
  test(DohPolicy.parse("{\"Status\":0}").addresses.isEmpty());test(DohPolicy.parse(null).addresses.isEmpty());
  test(DohPolicy.parse("{\"Status\":0,\"Answer\":[{\"type\":1,\"TTL\":99999,\"data\":\"999.1.1.1\"},{\"type\":1,\"TTL\":99999,\"data\":\"8.8.4.4\"}]}").ttlSeconds==3600);
  test(DohPolicy.eligible("hdrezka.sh"));test(DohPolicy.eligible("static.hdrezka.ac"));
  for(String bad:new String[]{null,"","1.1.1.1","::1","localhost","tv.local","intranet","evil host.com"})test(!DohPolicy.eligible(bad));
  test(DohPolicy.rewrite("https://hdrezka.sh/films/a.html?x=1","104.21.5.9").equals("https://104.21.5.9/films/a.html?x=1"));
  test(DohPolicy.rewrite("http://cdn.example:8080/a.m3u8","1.2.3.4").equals("http://1.2.3.4:8080/a.m3u8"));
  test(DohPolicy.hostHeader("https://hdrezka.sh/a").equals("hdrezka.sh"));test(DohPolicy.hostHeader("https://hdrezka.sh:443/a").equals("hdrezka.sh"));test(DohPolicy.hostHeader("http://cdn.example:8080/a").equals("cdn.example:8080"));
  System.out.println("DoH policy: "+count+" checks passed");
 }
}
