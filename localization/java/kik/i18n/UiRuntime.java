package kik.i18n;
import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import java.lang.reflect.*;
import java.util.*;

public final class UiRuntime {
 public static Context app;
 public static boolean settingsOpen;
 private static java.lang.ref.WeakReference<Object> settingsModel;
 public static void captureSettings(Object model){settingsModel=new java.lang.ref.WeakReference<Object>(model);}
 public static void updateState(String name,Object value){Object model=settingsModel==null?null:settingsModel.get();Object flow=field(model,name);if(flow!=null)try{Method m=flow.getClass().getMethod("k",Object.class);m.setAccessible(true);m.invoke(flow,value);}catch(Exception e){Log.w("KIK","Preference state update",e);}}
 private static boolean pendingSettings;
 private static long lastSettings;
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final Map<Object,Boolean> STARTED=Collections.synchronizedMap(new WeakHashMap<Object,Boolean>());
 public static void init(Context context) {
  app=context.getApplicationContext();
  SharedPreferences prefs=app.getSharedPreferences("settings",0);
  SharedPreferences.Editor e=prefs.edit().putBoolean("use_external_player",false).putString("external_player_package","");
  if(!prefs.getBoolean("kik_simple_v1",false))e.putString("default_quality","1080p").putBoolean("kik_simple_v1",true);
  // Netflix-like palette through the app's own custom theme: near-black background, red accent.
  e.putString("theme_type","custom").putLong("custom_color",0xFFE50914L).putLong("custom_secondary_color",0xFFB81D24L)
   .putLong("custom_background_color",0xFF0D0D0FL).putLong("custom_surface_color",0xFF1B1B1FL).putBoolean("amoled_theme",false)
   .putString("browse_navigation_style","top");
  e.apply();
  AppUpdater.schedule(app);
  TemporarySignIn.install(app);
 }
 public static String defaultQuality(){return app==null?"1080p":app.getSharedPreferences("settings",0).getString("default_quality","1080p");}
 public static boolean hideAction(String label) {return label!=null && Arrays.asList("Отзывы","Торренты","Поделиться","Внешний","Трейлер","Облако","Стартовая страница").contains(label);}
 public static void settings(final Object onBack) {
  if(app==null||settingsOpen||pendingSettings||SystemClock.elapsedRealtime()-lastSettings<700)return;
  pendingSettings=true;
  MAIN.post(new Runnable(){public void run(){
   try {
    lastSettings=SystemClock.elapsedRealtime();settingsOpen=true;
    Object nav=field(onBack,"B");
    if(nav!=null){Method back=nav.getClass().getMethod("c");back.setAccessible(true);back.invoke(nav);}
    app.startActivity(new Intent(app,BasicSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
   }catch(Exception e){Log.e("KIK","Settings launch failed",e);settingsOpen=false;}
   finally{pendingSettings=false;}
  }});
 }
 public static void invoke(Object callback) throws Exception {
  if(callback==null)return;
  Method m=callback.getClass().getMethod("a");m.setAccessible(true);m.invoke(callback);
 }
 public static void autoStart(final Object callback,final Object draft,final Object busy,boolean enabled) {
  if(!enabled||draft==null||STARTED.containsKey(draft)||PlaybackPolicy.call(busy,"getValue")!=null)return;
  STARTED.put(draft,true);
  MAIN.post(new Runnable(){public void run(){
   try {Log.d("KIK","Starting built-in playback with automatically selected voice");invoke(callback);}
   catch(Exception e){Log.e("KIK","Automatic playback failed",e);}
  }});
 }
 public static String playbackStatus(Object draft) {
  Object flow=field(draft,"c");Object state=PlaybackPolicy.call(flow,"getValue");Object error=field(state,"i");
  if(error!=null)return "Не вдалося підготувати відео. Натисніть Назад і спробуйте ще раз.";
  return "Підготовка відео…";
 }
 private static long lastExit;
 /** The catalog's "exit app?" prompt becomes a return to the KIK home screen. */
 public static boolean exitDialog(String title,final Object dismiss){
  if(app==null||title==null||!title.startsWith("\u0412\u044b\u0439\u0442\u0438 \u0438\u0437 \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u044f"))return false;
  if(SystemClock.elapsedRealtime()-lastExit>1000){
   lastExit=SystemClock.elapsedRealtime();
   MAIN.post(new Runnable(){public void run(){
    try{invoke(dismiss);}catch(Exception e){Log.w("KIK","Exit dismiss",e);}
    app.startActivity(new Intent(app,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
   }});
  }
  return true;
 }
 private static Object field(Object object,String name){if(object==null)return null;try{Field f=object.getClass().getField(name);return f.get(object);}catch(Exception e){return null;}}
}
