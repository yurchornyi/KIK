package kik.i18n;
import android.app.job.*;
import android.os.CancellationSignal;
/** Persisted background preparation. Installation only happens on an idle KIK screen. */
public final class UpdateJob extends JobService {
 private volatile JobParameters running;
 private CancellationSignal cancellation;
 @Override public boolean onStartJob(JobParameters p){running=p;cancellation=new CancellationSignal();AppUpdater.check(this,false,()->{if(running==p){running=null;jobFinished(p,false);}},cancellation);return true;}
 @Override public boolean onStopJob(JobParameters p){running=null;if(cancellation!=null)cancellation.cancel();return true;}
}
