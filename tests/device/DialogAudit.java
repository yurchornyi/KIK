package kik.qa;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import org.json.*;import kik.i18n.*;

/** The update prompt on a TV: only the focused button lights up, whichever one that is. */
public final class DialogAudit extends Instrumentation {
 JSONArray checks=new JSONArray();boolean passed=true;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(String label,boolean ok)throws Exception{checks.put(new JSONObject().put("name",label).put("passed",ok));passed&=ok;Bundle b=new Bundle();b.putString("stream",(ok?"PASS ":"FAIL ")+label+"\n");sendStatus(0,b);}
 static float light(int c){return (0.2126f*Color.red(c)+0.7152f*Color.green(c)+0.0722f*Color.blue(c))/255f;}
 int pixel(Bitmap shot,View v){int[] at=new int[2];v.getLocationOnScreen(at);return shot.getPixel(at[0]+v.getWidth()/8,at[1]+v.getHeight()/2);}
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   Activity a=startActivitySync(new Intent(getTargetContext(),BasicSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   final AlertDialog[] d=new AlertDialog[1];
   runOnMainSync(()->d[0]=Ui.dialog(a).setTitle("Доступне оновлення KIK 9.9").setMessage("Перевірка діалогу.").setNegativeButton("Пізніше",null).setPositiveButton("Оновити",null).show());
   SystemClock.sleep(800);
   Button later=d[0].getButton(AlertDialog.BUTTON_NEGATIVE),update=d[0].getButton(AlertDialog.BUTTON_POSITIVE);
   for(boolean onLater:new boolean[]{true,false}){
    runOnMainSync(()->(onLater?later:update).requestFocus());SystemClock.sleep(600);
    Bitmap shot=getUiAutomation().takeScreenshot();
    float focused=light(pixel(shot,onLater?later:update)),idle=light(pixel(shot,onLater?update:later));
    check((onLater?"«Пізніше»":"«Оновити»")+" focused is bright ("+focused+")",focused>0.8f);
    check((onLater?"«Оновити»":"«Пізніше»")+" idle is dark ("+idle+")",idle<0.35f);
    File dir=new File(getTargetContext().getExternalFilesDir(null),"design-audit");dir.mkdirs();
    try(FileOutputStream out=new FileOutputStream(new File(dir,"dialog-"+(onLater?"later":"update")+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}
   }
   runOnMainSync(()->{d[0].dismiss();a.finish();});
  }catch(Throwable e){passed=false;result.putString("stream","FAIL "+e+"\n");}
  result.putString("passed",String.valueOf(passed));finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
