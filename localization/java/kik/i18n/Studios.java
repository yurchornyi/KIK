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
 /** id, name, the wordmark on the badge (empty: the pictogram alone); the same emblems as the website's studios.js. */
 static final String[][] ALL={{"","Усі студії",""},{"netflix","Netflix",""},{"apple","Apple TV+","tv+"},{"hbo","HBO","HBO"},{"prime","Prime Video","prime"},{"disney","Disney+","Disney+"},{"hulu","Hulu","hulu"},{"paramount","Paramount+","+"},{"warner","Warner Bros.","WB"},{"pixar","Pixar","PIXAR"}};
 static String name(String id){for(String[] s:ALL)if(s[0].equals(id))return s[1];return "";}
 static String id(JSONArray ids){if(ids!=null)for(int i=0;i<ids.length();i++)for(String[] s:ALL)if(!s[0].isEmpty()&&s[0].equals(ids.optString(i)))return s[0];return "";}
 private static final LinkedHashMap<String,String> cache=new LinkedHashMap<String,String>();
 private static final LinkedHashMap<String,JSONObject> waiting=new LinkedHashMap<String,JSONObject>();
 private static final WeakHashMap<FrameLayout,String> views=new WeakHashMap<FrameLayout,String>();
 private static boolean busy;private static long retryAt;
 private static final String TAG="kik-studio-badge";
 static void paint(FrameLayout frame,String id){
  View old=frame.findViewWithTag(TAG);if(old!=null)frame.removeView(old);String[] studio=null;for(String[] s:ALL)if(!s[0].isEmpty()&&s[0].equals(id))studio=s;if(studio==null)return;
  Context c=frame.getContext();frame.addView(badge(c,studio),new FrameLayout.LayoutParams(-2,Ui.dp(c,22),Gravity.TOP|Gravity.START){{setMargins(Ui.dp(c,7),Ui.dp(c,7),0,0);}});
 }
 /** The emblem pill: a white pictogram and/or a short wordmark on a dark rounded pill with a thin edge, 22 dp tall everywhere. */
 static TextView badge(Context c,String[] studio){
  TextView mark=Ui.bold(c,studio[2],10);mark.setTag(TAG);mark.setFocusable(false);mark.setClickable(false);mark.setIncludeFontPadding(false);mark.setGravity(Gravity.CENTER_VERTICAL);mark.setSingleLine(true);
  mark.setContentDescription("Студія / платформа: "+studio[1]);mark.setTextColor(0xfff4f4f6);
  if("disney".equals(studio[0]))mark.setTypeface(Typeface.create("sans-serif",Typeface.BOLD_ITALIC));
  else if("pixar".equals(studio[0])){mark.setTypeface(Typeface.create("serif",Typeface.BOLD));mark.setLetterSpacing(0.12f);mark.setTextSize(9);}
  else if("hbo".equals(studio[0]))mark.setLetterSpacing(0.04f);
  else if("paramount".equals(studio[0]))mark.setTextSize(13);
  int glyph=glyphOf(studio[0]),pad=Ui.dp(c,glyph>=0&&studio[2].isEmpty()?5:7);
  if(glyph>=0){int size=Ui.dp(c,14);Glyph g=new Glyph(glyph,0xdd111113);g.setBounds(0,0,size,size);mark.setCompoundDrawables(g,null,null,null);mark.setCompoundDrawablePadding(Ui.dp(c,4));}
  mark.setPadding(pad,0,pad,0);mark.setBackground(Ui.shape(0xdd111113,Ui.dp(c,6),Ui.dp(c,1),0x22ffffff));return mark;
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
private static int glyphOf(String id){return "netflix".equals(id)?0:"apple".equals(id)?1:"prime".equals(id)?2:"paramount".equals(id)?3:"warner".equals(id)?4:-1;}
 static final class Glyph extends Drawable {
  private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG);private final int kind,back;private final Path path=new Path();
  Glyph(int kind,int back){this.kind=kind;this.back=back;fill.setColor(Color.WHITE);line.setColor(Color.WHITE);line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeJoin(Paint.Join.ROUND);}
  @Override public void draw(Canvas c){
   Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);
   if(kind==0){c.drawRect(4,2,8.5f,22,fill);c.drawRect(15.5f,2,20,22,fill);path.reset();path.moveTo(4,2);path.lineTo(8.5f,2);path.lineTo(15.5f,22);path.lineTo(11,22);path.close();c.drawPath(path,fill);}
   else if(kind==1){path.reset();path.moveTo(12,6.4f);path.rCubicTo(-2.3f,-1.9f,-5.8f,-1.2f,-7.2f,1.5f);path.rCubicTo(-2,3.7f,.1f,9.8f,3.5f,11.8f);path.rCubicTo(1.2f,.7f,2.2f,.2f,3.7f,.2f);path.rCubicTo(1.5f,0,2.5f,.5f,3.7f,-.2f);path.rCubicTo(3.4f,-2,5.5f,-8.1f,3.5f,-11.8f);path.rCubicTo(-1.4f,-2.7f,-4.9f,-3.4f,-7.2f,-1.5f);path.close();c.drawPath(path,fill);
    path.reset();path.moveTo(12.4f,5.6f);path.rCubicTo(.2f,-1.9f,1.5f,-3.2f,3.3f,-3.4f);path.rCubicTo(0,1.9f,-1.5f,3.4f,-3.3f,3.4f);path.close();c.drawPath(path,fill);}
   else if(kind==2){line.setStrokeWidth(2.4f);path.reset();path.moveTo(3.5f,14.5f);path.cubicTo(8.3f,18.7f,16,18.8f,21,14.8f);c.drawPath(path,line);line.setStrokeWidth(2.2f);path.reset();path.moveTo(17.3f,12.2f);path.lineTo(21.5f,13.3f);path.lineTo(20.2f,17.4f);c.drawPath(path,line);}
   else if(kind==3){path.reset();path.moveTo(3.5f,20.5f);path.lineTo(12,6);path.lineTo(20.5f,20.5f);path.close();c.drawPath(path,fill);fill.setColor(back);path.reset();path.moveTo(9.6f,11.4f);path.lineTo(12,6);path.lineTo(14.4f,11.4f);path.lineTo(12,13);path.close();c.drawPath(path,fill);fill.setColor(Color.WHITE);
    float[][] stars={{5,9.2f},{8,5.4f},{12,2.6f},{16,5.4f},{19,9.2f}};for(float[] s:stars)c.drawCircle(s[0],s[1],1.4f,fill);}
   else{line.setStrokeWidth(2.2f);path.reset();path.moveTo(4.5f,3);path.lineTo(19.5f,3);path.lineTo(19.5f,11.5f);path.cubicTo(19.5f,16.1f,16.5f,19.4f,12,21);path.cubicTo(7.5f,19.4f,4.5f,16.1f,4.5f,11.5f);path.close();c.drawPath(path,line);line.setStrokeWidth(1.9f);path.reset();path.moveTo(8,8);path.lineTo(9.6f,15);path.lineTo(12,10);path.lineTo(14.4f,15);path.lineTo(16,8);c.drawPath(path,line);}
   c.restore();
  }
  @Override public void setAlpha(int a){fill.setAlpha(a);line.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){fill.setColorFilter(f);line.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 static final class FilterIcon extends Drawable {
  private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);private final int size;
  FilterIcon(int size){this.size=size;pen.setColor(Color.WHITE);pen.setStrokeWidth(size/14f);pen.setStrokeCap(Paint.Cap.ROUND);}
  public void draw(Canvas canvas){canvas.save();canvas.scale(size/24f,size/24f);pen.setStrokeWidth(1.5f);for(int y:new int[]{6,12,18})canvas.drawLine(3,y,21,y,pen);canvas.drawLine(8,3,8,9,pen);canvas.drawLine(16,9,16,15,pen);canvas.drawLine(9,15,9,21,pen);canvas.restore();}
  public void setAlpha(int alpha){pen.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){pen.setColorFilter(filter);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
 }
 /** A menu checkbox: an empty rounded square, or a white one with a dark tick once the studio is chosen. */
 static final class CheckIcon extends Drawable {
  private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF box=new RectF();private final Path tick=new Path();private final boolean on;
  CheckIcon(int size,boolean on){this.on=on;pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);}
  public void draw(Canvas canvas){Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);box.set(2,2,22,22);
   if(on){pen.setStyle(Paint.Style.FILL);pen.setColor(Color.WHITE);canvas.drawRoundRect(box,5,5,pen);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(2.8f);pen.setColor(0xff141414);tick.reset();tick.moveTo(6.5f,12.5f);tick.lineTo(10.5f,16.5f);tick.lineTo(17.5f,8);canvas.drawPath(tick,pen);}
   else{pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.8f);pen.setColor(0x99ffffff);box.inset(.9f,.9f);canvas.drawRoundRect(box,4.5f,4.5f,pen);}
   canvas.restore();}
  public void setAlpha(int alpha){pen.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){pen.setColorFilter(filter);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
