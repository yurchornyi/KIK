package kik.qa;
import android.app.Instrumentation;import android.content.Context;import android.os.Bundle;import kik.i18n.*;import org.json.*;
/** Recover only the password-verified disposable full-audit profile after an OS process death. */
public final class RecoveryAudit extends Instrumentation {
 Bundle args;@Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 @Override public void onStart(){Bundle out=new Bundle();try{Context c=getTargetContext();String current=ProfileStore.current(c);JSONObject report=new JSONObject().put("current",current);JSONArray list=new JSONArray();ProfileStore.Profile candidate=null;for(ProfileStore.Profile p:ProfileStore.list(c)){list.put(new JSONObject().put("id",p.id).put("name",p.name).put("taste_setup",p.tasteSetup));if(p.id.equals(current))candidate=p;}report.put("profiles",list);
 if("recover".equals(args.getString("mode"))){String expected=args.getString("expectedQa"),original=args.getString("original");if(candidate==null||!candidate.id.equals(expected)||!candidate.name.equals("KIK Audit")||!ProfileStore.checkPassword(candidate,"Audit5678")||original==null||original.equals(expected)||ProfileStore.nameOf(c,original)==null)throw new IllegalStateException("Recovery guard rejected profile mismatch");c.getSharedPreferences("kik_profiles",0).edit().putString("current",original).commit();ProfileStore.delete(c,expected);report.put("restored_profile",original).put("removed_test_profile",expected).put("original_history_entries",Progress.recent(c).size());}
 out.putString("stream",report.toString());finish(-1,out);}catch(Throwable e){out.putString("stream",e.toString());finish(0,out);}}
}
