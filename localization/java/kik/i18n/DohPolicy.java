package kik.i18n;

import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Pure parts of the DNS-over-HTTPS bypass, shared with the regression check. */
public final class DohPolicy {
 /** IPv4 A records and the shortest TTL from a Cloudflare dns-json answer; empty when the name does not resolve. */
 public static final class Answer{public final List<String> addresses=new ArrayList<String>();public long ttlSeconds=300;}
 private static final Pattern RECORD=Pattern.compile("\\{[^{}]*\\}"),TYPE=Pattern.compile("\"type\"\\s*:\\s*(\\d+)"),
  DATA=Pattern.compile("\"data\"\\s*:\\s*\"([^\"]*)\""),TTL=Pattern.compile("\"TTL\"\\s*:\\s*(\\d+)"),STATUS=Pattern.compile("\"Status\"\\s*:\\s*(\\d+)");
 private static final Pattern IPV4=Pattern.compile("(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}");
 public static Answer parse(String json){
  Answer a=new Answer();if(json==null)return a;
  Matcher s=STATUS.matcher(json);if(s.find()&&!"0".equals(s.group(1)))return a;
  int at=json.indexOf("\"Answer\"");if(at<0)return a;
  long ttl=Long.MAX_VALUE;Matcher r=RECORD.matcher(json.substring(at));
  while(r.find()){String o=r.group();Matcher t=TYPE.matcher(o),d=DATA.matcher(o);if(!t.find()||!"1".equals(t.group(1))||!d.find()||!IPV4.matcher(d.group(1)).matches())continue;
   if(!a.addresses.contains(d.group(1)))a.addresses.add(d.group(1));Matcher l=TTL.matcher(o);if(l.find())ttl=Math.min(ttl,Long.parseLong(l.group(1)));}
  // Keep an address at least 5 minutes and at most an hour: fewer lookups, and a moved mirror is found again soon.
  a.ttlSeconds=ttl==Long.MAX_VALUE?300:Math.max(300,Math.min(3600,ttl));return a;
 }
 /** Names the bypass may resolve: real hostnames only, never IP literals or local names. */
 public static boolean eligible(String host){
  if(host==null||host.isEmpty()||host.length()>253||IPV4.matcher(host).matches()||host.indexOf(':')>=0)return false;
  String h=host.toLowerCase(Locale.ROOT);if(h.equals("localhost")||h.endsWith(".local")||h.indexOf('.')<0)return false;
  return h.matches("[a-z0-9.-]+");
 }
 /** The same URL aimed at an address; the Host header carries the name. */
 public static String rewrite(String url,String ip)throws MalformedURLException{URL u=new URL(url);return new URL(u.getProtocol(),ip,u.getPort(),u.getFile()+(u.getRef()==null?"":"#"+u.getRef())).toString();}
 public static String hostHeader(String url)throws MalformedURLException{URL u=new URL(url);int p=u.getPort();return p<0||p==u.getDefaultPort()?u.getHost():u.getHost()+":"+p;}
}
