package kik.i18n;
import android.content.Context;
import android.text.*;
import java.net.*;
import org.json.JSONObject;

/** The title page's three-line description: the full text when it fits, else KIK's short synopsis, cut only at a sentence. */
final class Synopsis {
 private Synopsis(){}
 private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("kik_synopsis",0);}
 static String cached(Context c,String link){return prefs(c).getString(Progress.norm(link),null);}
 /** Asks the KIK server once per title; its answer is kept on the TV for good. */
 static String fetch(Context c,String link){
  String key=Progress.norm(link);if(key.startsWith("uaserials/"))return null;HttpURLConnection h=null;
  try{h=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+"/api/synopsis?link="+URLEncoder.encode("https://hdrezka.sh/"+key,"UTF-8")).openConnection();h.setConnectTimeout(8000);h.setReadTimeout(25000);
   if(h.getResponseCode()!=200)return null;String s=new JSONObject(Site.read(h.getInputStream())).optString("summary","");if(s.isEmpty()||"null".equals(s))return null;
   prefs(c).edit().putString(key,s).apply();return s;
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
 @SuppressWarnings("deprecation") private static StaticLayout layout(CharSequence text,TextPaint paint,int width,float spacing){return new StaticLayout(text,paint,Math.max(1,width),Layout.Alignment.ALIGN_NORMAL,spacing,0,false);}
}
