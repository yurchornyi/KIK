package kik.i18n;
import android.content.Context;
import android.text.*;
import java.net.*;
import org.json.JSONObject;

/** Descriptions that read whole (the title page's three lines, the home hero's two): the full text when it fits, else KIK's short synopsis, cut only at a sentence. */
final class Synopsis {
 private Synopsis(){}
 private static final long TTL=30L*24*3600*1000;
 private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("kik_synopsis_v2",0);}
 /** Kept for a month, so a better server wording reaches the TV too. */
 static String cached(Context c,String link){String k=Progress.norm(link);android.content.SharedPreferences p=prefs(c);return System.currentTimeMillis()-p.getLong(k+"@",0)<TTL?p.getString(k,null):null;}
 /** Asks the KIK server; its answer is kept on the TV for a month. */
 static String fetch(Context c,String link){
  String key=Progress.norm(link);if(key.startsWith("uaserials/"))return null;HttpURLConnection h=null;
  try{h=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+"/api/synopsis?link="+URLEncoder.encode("https://hdrezka.sh/"+key,"UTF-8")).openConnection();CloudSync.authorize(h);h.setConnectTimeout(8000);h.setReadTimeout(25000);
   if(h.getResponseCode()!=200)return null;String s=new JSONObject(Site.read(h.getInputStream())).optString("summary","");if(s.isEmpty()||"null".equals(s))return null;
   prefs(c).edit().putString(key,s).putLong(key+"@",System.currentTimeMillis()).apply();return s;
  }catch(Exception e){android.util.Log.w("KIK","Synopsis",e);return null;}finally{if(h!=null)h.disconnect();}
 }
 static boolean fits(CharSequence text,TextPaint paint,int width,float spacing,int lines){return layout(text,paint,width,spacing).getLineCount()<=lines;}
 /** As much of the text as fits in the lines, ending on a whole sentence (a whole word with … only if a sentence is too long). */
 static String fit(String text,TextPaint paint,int width,float spacing,int lines){
  if(text==null)return "";text=text.trim();StaticLayout l=layout(text,paint,width,spacing);if(l.getLineCount()<=lines)return text;
  int end=l.getLineEnd(lines-1);String head=text.substring(0,end);
  int dot=Math.max(head.lastIndexOf(". "),Math.max(head.lastIndexOf("! "),head.lastIndexOf("? ")));if(dot>40)return head.substring(0,dot+1);
  for(int cut=head.length()-1;cut>20;cut--){if(head.charAt(cut)==' '){String t=head.substring(0,cut).replaceAll("[,;:—–-]+$","")+"…";if(fits(t,paint,width,spacing,lines))return t;}}
  return head;
 }
 /**
  * Short text that is whole in the lines: the description when it fits, else the short synopsis when it fits, else the
  * description's whole sentences, else the synopsis's; a word cut with … only when no sentence fits.
  */
 static String brief(String full,String brief,TextPaint paint,int width,float spacing,int lines){
  full=full==null?"":full.trim();if(fits(full,paint,width,spacing,lines))return full;
  String b=brief==null?"":brief.trim();if(!b.isEmpty()&&fits(b,paint,width,spacing,lines))return b;
  String own=shorten(full,paint,width,spacing,lines);if(!own.endsWith("…")||b.isEmpty())return own;
  String other=shorten(b,paint,width,spacing,lines);return other.endsWith("…")?own:other;
 }
 /** For two short lines: a whole sentence however short beats a cut one; with none, the cut falls at a comma, else at a word. */
 private static String shorten(String text,TextPaint paint,int width,float spacing,int lines){
  StaticLayout l=layout(text,paint,width,spacing);if(l.getLineCount()<=lines)return text;String head=text.substring(0,l.getLineEnd(lines-1));
  int dot=Math.max(head.lastIndexOf(". "),Math.max(head.lastIndexOf("! "),head.lastIndexOf("? ")));if(dot>=20)return head.substring(0,dot+1);
  for(int comma=head.lastIndexOf(", ");comma>head.length()/2;comma=head.lastIndexOf(", ",comma-1)){String t=head.substring(0,comma)+"…";if(fits(t,paint,width,spacing,lines))return t;}
  return fit(text,paint,width,spacing,lines);
 }
 @SuppressWarnings("deprecation") private static StaticLayout layout(CharSequence text,TextPaint paint,int width,float spacing){return new StaticLayout(text,paint,Math.max(1,width),Layout.Alignment.ALIGN_NORMAL,spacing,0,false);}
}
