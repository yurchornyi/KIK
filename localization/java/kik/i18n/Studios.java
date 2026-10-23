package kik.i18n;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

/** Quiet, optional public metadata. Requests never hold up posters, focus or playback. */
final class Studios {
 static final String[][] ALL={{"","Усі студії",""},{"netflix","Netflix","N"},{"apple","Apple TV+","tv+"},{"hbo","HBO","HBO"},{"prime","Prime Video","prime"},{"disney","Disney+","Disney+"},{"hulu","Hulu","hulu"},{"paramount","Paramount+","P+"},{"warner","Warner Bros.","WB"},{"pixar","Pixar","PIXAR"}};
 static String name(String id){for(String[] s:ALL)if(s[0].equals(id))return s[1];return "";}
 static String id(JSONArray ids){if(ids!=null)for(int i=0;i<ids.length();i++)for(String[] s:ALL)if(!s[0].isEmpty()&&s[0].equals(ids.optString(i)))return s[0];return "";}
 private static final LinkedHashMap<String,String> cache=new LinkedHashMap<String,String>();
 private static final LinkedHashMap<String,JSONObject> waiting=new LinkedHashMap<String,JSONObject>();
 private static final WeakHashMap<FrameLayout,String> views=new WeakHashMap<FrameLayout,String>();
 private static boolean busy;private static long retryAt;
 private static final String TAG="kik-studio-badge";
 static void paint(FrameLayout frame,String id){
  View old=frame.findViewWithTag(TAG);if(old!=null)frame.removeView(old);String[] studio=null;for(String[] s:ALL)if(!s[0].isEmpty()&&s[0].equals(id))studio=s;if(studio==null)return;
  Context c=frame.getContext();TextView mark=Ui.bold(c,studio[2],10);mark.setTag(TAG);mark.setFocusable(false);mark.setClickable(false);mark.setContentDescription("Студія / платформа: "+studio[1]);mark.setPadding(Ui.dp(c,6),Ui.dp(c,3),Ui.dp(c,6),Ui.dp(c,3));mark.setBackground(Ui.shape(0xdd111113,Ui.dp(c,4),0,0));
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);lp.setMargins(Ui.dp(c,7),Ui.dp(c,7),0,0);frame.addView(mark,lp);
 }
 /** Called on the main thread. Weak view references and a bounded shared cache survive rebuilt rows safely. */
 static void watch(FrameLayout frame,String link,String title,String year,String known){
  if(link==null||title==null||title.isEmpty())return;String key=Progress.norm(link);views.put(frame,key);
  if(known!=null&&!known.isEmpty())remember(key,known);
  if(cache.containsKey(key)){paint(frame,cache.get(key));return;}if(System.currentTimeMillis()<retryAt)return;
  try{waiting.put(key,new JSONObject().put("link",link).put("title",title).put("year",year==null?"":year));}catch(JSONException ignored){}
  Ui.MAIN.removeCallbacks(FLUSH);Ui.MAIN.postDelayed(FLUSH,120);
 }
 private static void remember(String key,String id){if(cache.size()>=400)cache.remove(cache.keySet().iterator().next());cache.put(key,id);}
 private static final Runnable FLUSH=()->flush();
 private static void flush(){
  if(busy||waiting.isEmpty()||System.currentTimeMillis()<retryAt)return;busy=true;
  final LinkedHashMap<String,JSONObject> batch=new LinkedHashMap<String,JSONObject>();Iterator<Map.Entry<String,JSONObject>> it=waiting.entrySet().iterator();while(it.hasNext()&&batch.size()<12){Map.Entry<String,JSONObject> e=it.next();batch.put(e.getKey(),e.getValue());it.remove();}
  Ui.bg(()->{JSONObject response=null;try{JSONArray items=new JSONArray();for(JSONObject item:batch.values())items.put(item);response=post(new JSONObject().put("items",items));}catch(Exception ignored){}
   final JSONObject got=response;Ui.MAIN.post(()->{busy=false;
    if(got==null||!got.optBoolean("configured")){retryAt=System.currentTimeMillis()+60000;waiting.clear();return;}
    JSONObject results=got.optJSONObject("results");for(String key:batch.keySet()){JSONObject art=results==null?null:results.optJSONObject(key);if(art!=null)remember(key,id(art.optJSONArray("studios")));}
    for(Map.Entry<FrameLayout,String> entry:new ArrayList<Map.Entry<FrameLayout,String>>(views.entrySet())){FrameLayout frame=entry.getKey();if(frame!=null&&cache.containsKey(entry.getValue()))paint(frame,cache.get(entry.getValue()));}
    if(!waiting.isEmpty())Ui.MAIN.post(FLUSH);
   });
  });
 }
 private static JSONObject post(JSONObject body)throws IOException,JSONException{
  HttpURLConnection c=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+"/api/artwork/batch").openConnection();c.setConnectTimeout(4500);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
  try{try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}if(c.getResponseCode()!=200)throw new IOException("Studio metadata unavailable");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("Studio data limit");out.write(bytes,0,n);}return new JSONObject(out.toString("UTF-8"));}}finally{c.disconnect();}
 }
 static final class FilterIcon extends Drawable {
  private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);private final int size;
  FilterIcon(int size){this.size=size;pen.setColor(Color.WHITE);pen.setStrokeWidth(size/14f);pen.setStrokeCap(Paint.Cap.ROUND);}
  public void draw(Canvas canvas){canvas.save();canvas.scale(size/24f,size/24f);pen.setStrokeWidth(1.5f);for(int y:new int[]{6,12,18})canvas.drawLine(3,y,21,y,pen);canvas.drawLine(8,3,8,9,pen);canvas.drawLine(16,9,16,15,pen);canvas.drawLine(9,15,9,21,pen);canvas.restore();}
  public void setAlpha(int alpha){pen.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){pen.setColorFilter(filter);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
 }
}
