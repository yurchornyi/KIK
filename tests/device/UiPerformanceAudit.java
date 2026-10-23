package kik.qa;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.Callable;import java.util.function.BooleanSupplier;import org.json.*;import kik.i18n.*;

/** Real TV frame timings on a 50-key D-pad route over Home, and search work cancelled when the screen closes. Uses a disposable local profile. */
public final class UiPerformanceAudit extends Instrumentation {
 Context context;String original,list,qa;Activity home,search;boolean passed;String error="";List<Long> frames=Collections.synchronizedList(new ArrayList<Long>());List<long[]> stages=Collections.synchronizedList(new ArrayList<long[]>());JSONObject result=new JSONObject();HandlerThread metrics;
 Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 <T>T ui(Callable<T> action){Object[] out=new Object[2];runOnMainSync(()->{try{out[0]=action.call();}catch(Throwable e){out[1]=e;}});if(out[1]!=null)throw new RuntimeException((Throwable)out[1]);return (T)out[0];}
 boolean await(BooleanSupplier condition,long timeout){long end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){if(condition.getAsBoolean())return true;SystemClock.sleep(50);}return false;}
 void check(String name,boolean ok)throws Exception{result.put(name,ok);if(!ok)throw new AssertionError(name);}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){context=getTargetContext();original=ProfileStore.current(context);list=context.getSharedPreferences("kik_profiles",0).getString("list","[]");try{
  qa=ProfileStore.create(context,"QA Performance","Performance123").id;context.getSharedPreferences("kik_profiles",0).edit().putString("current",qa).commit();ProfileStore.picked=true;ProfileStore.completeTasteSetup(context,qa);
  long start=SystemClock.elapsedRealtime();home=startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  check("home_ready",await(()->ui(()->(Boolean)field(home,"loadedOnce")),60000));result.put("home_ready_ms",SystemClock.elapsedRealtime()-start);
  metrics=new HandlerThread("kik-frame-audit");metrics.start();Window.OnFrameMetricsAvailableListener listener=(window,frame,dropped)->{long duration=frame.getMetric(FrameMetrics.TOTAL_DURATION);if(duration>0){frames.add(duration);stages.add(new long[]{frame.getMetric(FrameMetrics.INPUT_HANDLING_DURATION),frame.getMetric(FrameMetrics.ANIMATION_DURATION),frame.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),frame.getMetric(FrameMetrics.DRAW_DURATION),frame.getMetric(FrameMetrics.SYNC_DURATION),frame.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),frame.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION),frame.getMetric(FrameMetrics.GPU_DURATION)});}};
  ui(()->{home.getWindow().addOnFrameMetricsAvailableListener(listener,new Handler(metrics.getLooper()));return null;});
  for(int i=0;i<50;i++){sendKeyDownUpSync(new int[]{KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_UP}[i%5]);SystemClock.sleep(100);}
  SystemClock.sleep(500);ui(()->{home.getWindow().removeOnFrameMetricsAvailableListener(listener);return null;});
  List<Long> samples;synchronized(frames){samples=new ArrayList<Long>(frames);}Collections.sort(samples);check("frames_recorded",!samples.isEmpty());
  String[] stageNames={"input","animation","layout","draw","sync","commands","swap","gpu"};for(int index=0;index<stageNames.length;index++){List<Long> values=new ArrayList<Long>();synchronized(stages){for(long[] stage:stages)values.add(stage[index]);}Collections.sort(values);result.put(stageNames[index]+"_p95_ms",values.get(Math.min(values.size()-1,(int)(values.size()*.95)))/1e6);}
  int slow=0;for(long duration:samples)if(duration>32_000_000)slow++;result.put("frames",samples.size()).put("frame_p50_ms",samples.get(samples.size()/2)/1e6).put("frame_p95_ms",samples.get(Math.min(samples.size()-1,(int)(samples.size()*.95)))/1e6).put("frames_over_32ms",slow);
  check("remote_focus_retained",ui(()->home.getCurrentFocus()!=null));
  // A query typed just before leaving search must not start a search on the closed screen (TV search waits 1.1 s after 3 letters).
  search=startActivitySync(new Intent(context,BrowseActivity.class).putExtra("mode","search").putExtra("title","Пошук").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  ui(()->{((EditText)field(search,"query")).setText("mat");return null;});
  ui(()->{search.finish();return null;});check("search_destroyed",await(()->ui(()->search.isDestroyed()),5000));int generation=ui(()->(Integer)field(search,"generation"));SystemClock.sleep(1600);
  check("search_callback_cancelled",generation==ui(()->(Integer)field(search,"generation"))&&!ui(()->(Boolean)field(search,"loading")));passed=true;
 }catch(Throwable failure){error=failure.toString();}
 finally{try{ui(()->{if(search!=null&&!search.isDestroyed())search.finish();if(home!=null&&!home.isDestroyed())home.finish();return null;});if(metrics!=null)metrics.quitSafely();context.getSharedPreferences("kik_profiles",0).edit().putString("current",original).commit();if(qa!=null)ProfileStore.delete(context,qa);context.getSharedPreferences("kik_profiles",0).edit().putString("list",list).putString("current",original).commit();result.put("passed",passed).put("error",error);File out=new File(context.getExternalFilesDir(null),"design-audit/ui-performance.json");out.getParentFile().mkdirs();try(FileOutputStream stream=new FileOutputStream(out)){stream.write(result.toString(2).getBytes("UTF-8"));}}catch(Exception failure){passed=false;error=failure.toString();}Bundle out=new Bundle();out.putString("stream","UI performance passed="+passed+" "+result+" "+error);finish(passed?-1:0,out);}}
}
