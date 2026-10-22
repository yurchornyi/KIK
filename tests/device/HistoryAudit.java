package kik.qa;
import android.app.Instrumentation;import android.content.Context;import android.os.Bundle;import org.json.*;import kik.i18n.*;
/** Inspect, or remove only a timestamp/position-matched trace of the known playback test. */
public final class HistoryAudit extends Instrumentation {
 Bundle args;
 @Override public void onCreate(Bundle values){super.onCreate(values);args=values;start();}
 @Override public void onStart(){Bundle result=new Bundle();try{Context c=getTargetContext();Progress.Entry candidate=null;for(Progress.Entry e:Progress.recent(c))if(e.link!=null&&e.link.matches("https?://[^/]+/series/[^/]+/646[-.].*"))candidate=e;JSONObject o=new JSONObject().put("entries",Progress.recent(c).size());if(candidate!=null){o.put("position",candidate.pos).put("timestamp",candidate.ts).put("observed",candidate.observed).put("watched",candidate.watchedMs);if("remove".equals(args.getString("mode"))){if(candidate.ts!=Long.parseLong(args.getString("expectedTimestamp"))||candidate.pos!=Long.parseLong(args.getString("expectedPosition")))throw new IllegalStateException("Entry changed; removal rejected");Progress.remove(c,candidate.link);o.put("removed",true).put("entries_after",Progress.recent(c).size());}}result.putString("stream",o.toString());finish(-1,result);}catch(Throwable e){result.putString("stream",e.toString());finish(0,result);}}
}
