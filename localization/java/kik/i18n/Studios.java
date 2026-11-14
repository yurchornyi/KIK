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
 static final String[][] ALL={{"","Усі студії",""},{"netflix","Netflix",""},{"apple","Apple TV+","tv+"},{"hbo","HBO","HBO"},{"prime","Prime Video","prime"},{"disney","Disney+","Disney+"},{"hulu","Hulu","hulu"},{"paramount","Paramount+","+"},{"warner","Warner Bros.","WB"},{"pixar","Pixar","PIXAR"},{"peacock","Peacock","peacock"},{"natgeo","National Geographic","NAT GEO"},{"discovery","Discovery","discovery"}};
 /** Sections a studio makes (the website's studios.js): Pixar only cartoons, Hulu series and cartoons, National Geographic
  *  and Discovery series and films; null = every one. A studio picked in a section it never makes opens its own. */
 static String[] sections(String id){switch(id){case "pixar":return new String[]{"cartoons"};case "peacock":case "hulu":return new String[]{"series","cartoons"};case "natgeo":case "discovery":return new String[]{"series","films"};default:return null;}}
 static boolean makes(String id,String section){String[] own=sections(id);if(own==null)return true;for(String x:own)if(x.equals(section))return true;return false;}
 static String[] of(String id){if(id!=null&&!id.isEmpty())for(String[] s:ALL)if(s[0].equals(id))return s;return null;}
 static String name(String id){for(String[] s:ALL)if(s[0].equals(id))return s[1];return "";}
 static String id(JSONArray ids){if(ids!=null)for(int i=0;i<ids.length();i++)for(String[] s:ALL)if(!s[0].isEmpty()&&s[0].equals(ids.optString(i)))return s[0];return "";}
 private static final LinkedHashMap<String,String> cache=new LinkedHashMap<String,String>();
 /** When each answer in {@link #cache} arrived, for {@link #save}. */
 private static final HashMap<String,Long> found=new HashMap<String,Long>();
 private static final LinkedHashMap<String,JSONObject> waiting=new LinkedHashMap<String,JSONObject>();
 private static final WeakHashMap<FrameLayout,String> views=new WeakHashMap<FrameLayout,String>();
 /** The studio each frame's badge shows now ("" none): a repeated answer changes no view. */
 private static final WeakHashMap<FrameLayout,String> shown=new WeakHashMap<FrameLayout,String>();
 private static boolean busy,loaded;private static long retryAt;
 private static final String TAG="kik-studio-badge",STORE="kik_studios";
 private static final int LIMIT=800;private static final long KEEP_MS=14L*86_400_000L;
 static void paint(FrameLayout frame,String id){
  String now=id==null?"":id;if(now.equals(shown.get(frame))&&(now.isEmpty()||frame.findViewWithTag(TAG)!=null))return;shown.put(frame,now);
  View old=frame.findViewWithTag(TAG);if(old!=null)frame.removeView(old);String[] studio=null;for(String[] s:ALL)if(!s[0].isEmpty()&&s[0].equals(id))studio=s;if(studio==null)return;
  Context c=frame.getContext();frame.addView(badge(c,studio),new FrameLayout.LayoutParams(-2,Ui.dp(c,22),Gravity.TOP|Gravity.START){{setMargins(Ui.dp(c,7),Ui.dp(c,7),0,0);}});
 }
 /** Answers are kept a fortnight on the TV, those without a studio too: a start used to ask the service again about every
  *  card on Home, up to 25 batches of 12 behind the rows and posters (14.11, TCL), and each answer repainted every badge. */
 private static void load(Context context){
  if(loaded)return;loaded=true;
  // Kept for this list of studios only: a version that knows more of them asks again about titles it had none for.
  android.content.SharedPreferences prefs=context.getApplicationContext().getSharedPreferences(STORE,0);if(!known().equals(prefs.getString("studios","")))return;
  try{JSONObject o=new JSONObject(prefs.getString("cards","{}"));long now=System.currentTimeMillis();
   for(Iterator<String> it=o.keys();it.hasNext();){String key=it.next();JSONArray v=o.optJSONArray(key);long at=v==null?0:v.optLong(1);if(v!=null&&at<=now&&now-at<KEEP_MS){cache.put(key,v.optString(0));found.put(key,at);}}}
  catch(Exception ignored){}
 }
 private static String known(){StringBuilder b=new StringBuilder();for(String[] x:ALL)b.append(x[0]).append(',');return b.toString();}
 private static void save(Context context){
  try{JSONObject o=new JSONObject();for(Map.Entry<String,String> e:cache.entrySet()){Long at=found.get(e.getKey());if(at!=null)o.put(e.getKey(),new JSONArray().put(e.getValue()).put(at.longValue()));}
   context.getApplicationContext().getSharedPreferences(STORE,0).edit().putString("cards",o.toString()).putString("studios",known()).apply();}
  catch(JSONException ignored){}
 }
 /** The emblem pill (14.11, owner's pick «А»): the studio's own mark in its colours (Netflix's red ribbon N, Prime's
  *  blue smile, Hulu green…) on a dark pill with a thin light edge, 22 dp tall everywhere; the same as the website's studios.js. */
 static View badge(Context c,String[] studio){
  ImageView mark=new ImageView(c);mark.setTag(TAG);mark.setFocusable(false);mark.setClickable(false);mark.setContentDescription("Студія / платформа: "+studio[1]);
  mark.setImageDrawable(new Mark(studio[0],c.getResources().getDisplayMetrics().density));mark.setScaleType(ImageView.ScaleType.CENTER);
  int pad=Ui.dp(c,"netflix".equals(studio[0])?5:7);mark.setPadding(pad,0,pad,0);mark.setBackground(Ui.shape(0xe00b0b0d,Ui.dp(c,7),Ui.dp(c,1),0x26ffffff));return mark;
 }
 /** Called on the main thread. Weak view references and a bounded shared cache survive rebuilt rows safely. */
 static void watch(FrameLayout frame,String link,String title,String year,String known){
  if(link==null||title==null||title.isEmpty())return;load(frame.getContext());String key=Progress.norm(link);views.put(frame,key);
  if(known!=null&&!known.isEmpty())remember(key,known);
  if(cache.containsKey(key)){paint(frame,cache.get(key));return;}if(System.currentTimeMillis()<retryAt)return;
  try{waiting.put(key,new JSONObject().put("link",link).put("title",title).put("year",year==null?"":year));}catch(JSONException ignored){}
  app=frame.getContext().getApplicationContext();Ui.MAIN.removeCallbacks(FLUSH);Ui.MAIN.postDelayed(FLUSH,120);
 }
 private static Context app;
 private static void remember(String key,String id){if(!cache.containsKey(key)&&cache.size()>=LIMIT){String eldest=cache.keySet().iterator().next();cache.remove(eldest);found.remove(eldest);}cache.put(key,id);found.put(key,System.currentTimeMillis());}
 private static final Runnable FLUSH=()->flush();
 private static void flush(){
  if(busy||waiting.isEmpty()||System.currentTimeMillis()<retryAt)return;busy=true;
  final LinkedHashMap<String,JSONObject> batch=new LinkedHashMap<String,JSONObject>();Iterator<Map.Entry<String,JSONObject>> it=waiting.entrySet().iterator();while(it.hasNext()&&batch.size()<12){Map.Entry<String,JSONObject> e=it.next();batch.put(e.getKey(),e.getValue());it.remove();}
  Ui.bg(()->{JSONObject response=null;try{JSONArray items=new JSONArray();for(JSONObject item:batch.values())items.put(item);response=post(new JSONObject().put("items",items));}catch(Exception ignored){}
   final JSONObject got=response;Ui.MAIN.post(()->{busy=false;
    if(got==null||!got.optBoolean("configured")){retryAt=System.currentTimeMillis()+60000;waiting.clear();return;}
    JSONObject results=got.optJSONObject("results");for(String key:batch.keySet()){JSONObject art=results==null?null:results.optJSONObject(key);if(art!=null)remember(key,id(art.optJSONArray("studios")));}
    // Only the cards this answer is about: the others' badges are already right.
    for(Map.Entry<FrameLayout,String> entry:new ArrayList<Map.Entry<FrameLayout,String>>(views.entrySet())){FrameLayout frame=entry.getKey();String key=entry.getValue();if(frame!=null&&batch.containsKey(key)&&cache.containsKey(key))paint(frame,cache.get(key));}
    if(!waiting.isEmpty())Ui.MAIN.post(FLUSH);else if(app!=null)save(app);
   });
  });
 }
 private static JSONObject post(JSONObject body)throws IOException,JSONException{
  HttpURLConnection c=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+"/api/artwork/batch").openConnection();c.setConnectTimeout(4500);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
  try{try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}if(c.getResponseCode()!=200)throw new IOException("Studio metadata unavailable");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("Studio data limit");out.write(bytes,0,n);}return new JSONObject(out.toString("UTF-8"));}}finally{c.disconnect();}
 }
 /** One studio's mark, drawn by KIK (no brand files) in dp on a 22 dp tall box: a pictogram and/or a wordmark in the
  *  studio's own colours. Paths and shaders are made once; a draw allocates nothing. */
 static final class Mark extends Drawable {
  static final float H=22,CY=11;
  private final String id;private final float d,w;private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG),text2=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path=new Path();private final RectF base=new RectF();private String word="",word2="";private Shader left,right,arc;
  Mark(String id,float density){
   this.id=id;d=density;line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeJoin(Paint.Join.ROUND);text.setColor(0xfff4f4f6);text2.setColor(0xfff4f4f6);
   Typeface black=Typeface.create("sans-serif-black",Typeface.NORMAL),bold=Typeface.create("sans-serif",Typeface.BOLD);
   switch(id){
    case "netflix":left=new LinearGradient(0,0,0,24,new int[]{0xff7a0510,0xffb1060f},new float[]{0,.45f},Shader.TileMode.CLAMP);right=new LinearGradient(0,24,0,0,new int[]{0xff7a0510,0xffb1060f},new float[]{0,.45f},Shader.TileMode.CLAMP);break;
    case "apple":word="tv+";text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));text.setTextSize(11.5f);text.setLetterSpacing(-.026f);break;
    case "hbo":word="HB";text.setTypeface(black);text.setTextSize(11);text.setLetterSpacing(.05f);break;
    case "prime":word="prime";text.setTypeface(bold);text.setTextSize(11);text.setLetterSpacing(-.018f);break;
    case "disney":word="Disney";word2="+";text.setTypeface(Typeface.create(black,Typeface.ITALIC));text.setTextSize(10);text.setLetterSpacing(-.03f);text2.setTypeface(bold);text2.setTextSize(10.5f);text2.setColor(0xff59c9ff);break;
    case "hulu":word="hulu";text.setTypeface(black);text.setTextSize(13);text.setLetterSpacing(-.046f);text.setColor(0xff1ce783);break;
    case "paramount":word2="+";text2.setTypeface(bold);text2.setTextSize(14);break;
    case "warner":word="WB";text.setTypeface(black);text.setTextSize(8.2f);text.setLetterSpacing(-.024f);text.setTextAlign(Paint.Align.CENTER);break;
    case "pixar":word="PIXAR";text.setTypeface(black);text.setTextSize(10);text.setLetterSpacing(.16f);break;
    case "peacock":word="peacock";text.setTypeface(bold);text.setTextSize(11.5f);text.setLetterSpacing(-.026f);break;
    case "natgeo":word="NAT GEO";text.setTypeface(bold);text.setTextSize(9.5f);text.setLetterSpacing(.042f);break;
    case "discovery":word="discovery";text.setTypeface(bold);text.setTextSize(10.5f);text.setLetterSpacing(-.02f);break;
   }
   w=width();
   if("disney".equals(id))arc=new LinearGradient(0,0,w,0,new int[]{0x002f7bff,0xff59c9ff,0xffe6fbff},new float[]{0,.35f,1},Shader.TileMode.CLAMP);
  }
  private float width(){
   float t=text.measureText(word),t2=text2.measureText(word2);
   switch(id){
    case "netflix":return 17*14/24f;
    case "apple":case "pixar":return 18+t;
    case "hbo":return t+.4f+7.8f;
    case "prime":return Math.max(t,30);
    case "disney":return t+1+t2;
    case "hulu":return t;
    case "paramount":return 14+t2;
    case "warner":return 18*20/23f;
    case "peacock":return t+6;
    case "natgeo":return 13+t;
    case "discovery":return 16+t;
    default:return 1;
   }
  }
  @Override public int getIntrinsicWidth(){return (int)Math.ceil(w*d);}
  @Override public int getIntrinsicHeight(){return Math.round(H*d);}
  @Override public void draw(Canvas c){
   Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(d,d);fill.setShader(null);fill.setColor(Color.WHITE);line.setShader(null);
   switch(id){
    case "netflix":{c.save();c.translate(0,2.5f);c.scale(17/24f,17/24f);
     path.reset();path.moveTo(0,0);path.lineTo(4.5f,0);path.lineTo(4.5f,23.3f);path.quadTo(2.2f,23.5f,0,24.3f);path.close();fill.setShader(left);c.drawPath(path,fill);
     path.reset();path.moveTo(9.5f,-.3f);path.quadTo(11.8f,.5f,14,0);path.lineTo(14,24);path.lineTo(9.5f,24);path.close();fill.setShader(right);c.drawPath(path,fill);
     fill.setShader(null);fill.setColor(0xffe50914);path.reset();path.moveTo(0,0);path.lineTo(4.5f,0);path.lineTo(14,24);path.lineTo(9.5f,24);path.close();c.drawPath(path,fill);c.restore();break;}
    case "apple":{c.save();c.translate(0,4);c.scale(14/24f,14/24f);apple(c);c.restore();c.drawText(word,18,14.7f,text);break;}
    case "hbo":{c.drawText(word,0,14.9f,text);float x=text.measureText(word)+.4f+3.9f;line.setColor(Color.WHITE);line.setStrokeWidth(1.6f);c.drawCircle(x,CY,3.1f,line);c.drawCircle(x,CY,1,fill);break;}
    case "prime":{float t=text.measureText(word);c.drawText(word,(w-t)/2,11.6f,text);c.save();c.translate((w-30)/2,12);line.setColor(0xff1a98ff);line.setStrokeWidth(1.9f);
     path.reset();path.moveTo(1.5f,1.2f);path.rCubicTo(7,4.6f,18,4.8f,25.5f,.6f);c.drawPath(path,line);line.setStrokeWidth(1.7f);path.reset();path.moveTo(24.2f,.6f);path.rLineTo(3.6f,.9f);path.rLineTo(-1,3.3f);c.drawPath(path,line);c.restore();break;}
    case "disney":{line.setShader(arc);line.setStrokeWidth(1.6f);path.reset();path.moveTo(1,10.6f);path.cubicTo(12,2.2f,w*.62f,.6f,w-1,7.6f);c.drawPath(path,line);line.setShader(null);
     c.drawText(word,0,16,text);c.drawText(word2,text.measureText(word)+1,16,text2);break;}
    case "hulu":c.drawText(word,0,15.9f,text);break;
    case "paramount":{c.save();c.translate(0,2.5f);c.scale(17/24f,17/24f);fill.setColor(0xff2f7bff);path.reset();path.moveTo(3,21);path.lineTo(12,8);path.lineTo(21,21);path.close();c.drawPath(path,fill);
     fill.setColor(0xe6ffffff);path.reset();path.moveTo(9.7f,11.3f);path.lineTo(12,8);path.lineTo(14.3f,11.3f);path.lineTo(12,12.8f);path.close();c.drawPath(path,fill);
     fill.setColor(Color.WHITE);c.drawCircle(4.2f,10.6f,1.25f,fill);c.drawCircle(6.6f,6.6f,1.25f,fill);c.drawCircle(10,4,1.25f,fill);c.drawCircle(14,4,1.25f,fill);c.drawCircle(17.4f,6.6f,1.25f,fill);c.drawCircle(19.8f,10.6f,1.25f,fill);
     c.restore();c.drawText(word2,14,16.3f,text2);break;}
    case "warner":{c.save();c.translate(0,2);c.scale(18/23f,18/23f);path.reset();path.moveTo(1.5f,1.5f);path.lineTo(18.5f,1.5f);path.lineTo(18.5f,12);path.cubicTo(18.5f,16.9f,15.1f,20.2f,10,21.9f);path.cubicTo(4.9f,20.2f,1.5f,16.9f,1.5f,12);path.close();
     fill.setColor(0xff1f6fe8);c.drawPath(path,fill);line.setColor(0x55ffffff);line.setStrokeWidth(.8f);c.drawPath(path,line);c.drawText(word,10,13.6f,text);c.restore();break;}
    case "pixar":{c.save();c.translate(0,4);c.scale(14/24f,14/24f);base.set(2,19.8f,12,22.4f);c.drawRoundRect(base,1.3f,1.3f,fill);line.setColor(Color.WHITE);line.setStrokeWidth(1.9f);
     path.reset();path.moveTo(7,20.2f);path.lineTo(10,12.6f);path.lineTo(13.4f,5.2f);c.drawPath(path,line);path.reset();path.moveTo(12.4f,4.4f);path.lineTo(15,2.4f);path.lineTo(21.6f,7.6f);path.lineTo(16.6f,11.6f);path.close();c.drawPath(path,fill);
     c.restore();c.drawText(word,18,14.6f,text);break;}
    case "peacock":{c.drawText(word,0,15,text);float x=text.measureText(word)+3.5f;fill.setColor(0xffffb400);c.drawCircle(x,7.5f,1.8f,fill);fill.setColor(0xffa53ad9);c.drawCircle(x,13.5f,1.8f,fill);break;}
    case "natgeo":{line.setColor(0xffffce00);line.setStrokeWidth(2.2f);c.drawRect(1.1f,4.1f,8.9f,17.9f,line);c.drawText(word,13,14.6f,text);break;}
    case "discovery":{fill.setColor(0xff2d8cff);c.drawCircle(6.5f,CY,6,fill);line.setColor(Color.WHITE);line.setStrokeWidth(1);base.set(4,CY-6,9,CY+6);c.drawOval(base,line);
     line.setStrokeWidth(.8f);c.drawLine(.5f,CY,12.5f,CY,line);c.drawLine(1.6f,CY-2.9f,11.4f,CY-2.9f,line);c.drawLine(1.6f,CY+2.9f,11.4f,CY+2.9f,line);c.drawText(word,16,14.8f,text);break;}
   }
   c.restore();
  }
  /** The apple, on a 24-unit square. */
  private void apple(Canvas c){path.reset();path.moveTo(12,6.4f);path.rCubicTo(-2.3f,-1.9f,-5.8f,-1.2f,-7.2f,1.5f);path.rCubicTo(-2,3.7f,.1f,9.8f,3.5f,11.8f);path.rCubicTo(1.2f,.7f,2.2f,.2f,3.7f,.2f);path.rCubicTo(1.5f,0,2.5f,.5f,3.7f,-.2f);path.rCubicTo(3.4f,-2,5.5f,-8.1f,3.5f,-11.8f);path.rCubicTo(-1.4f,-2.7f,-4.9f,-3.4f,-7.2f,-1.5f);path.close();c.drawPath(path,fill);
   path.reset();path.moveTo(12.4f,5.6f);path.rCubicTo(.2f,-1.9f,1.5f,-3.2f,3.3f,-3.4f);path.rCubicTo(0,1.9f,-1.5f,3.4f,-3.3f,3.4f);path.close();c.drawPath(path,fill);}
  @Override public void setAlpha(int a){fill.setAlpha(a);line.setAlpha(a);text.setAlpha(a);text2.setAlpha(a);}
  @Override public void setColorFilter(ColorFilter f){fill.setColorFilter(f);line.setColorFilter(f);text.setColorFilter(f);text2.setColorFilter(f);}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
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
