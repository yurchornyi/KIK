package kik.i18n;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import java.nio.*;
import java.util.Random;

/** Loudness on synthetic sound: a quiet and a loud film end up at one level, silence and hiss stay quiet, peaks never crackle. */
public final class LoudnessCheck {
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);System.out.println("PASS "+message);}
 static final int RATE=48000,CH=2;
 /** Plays `seconds` of a 440 Hz tone at `rmsDb` (plus an optional burst) and returns the output's RMS in dB over the last two seconds, and its peak. */
 static double[] play(Loudness l,double seconds,double rmsDb,double noiseDb,double burstAt){
  double amp=Math.pow(10,rmsDb/20)*Math.sqrt(2),noise=Math.pow(10,noiseDb/20)*Math.sqrt(3);Random r=new Random(7);
  int frames=(int)(seconds*RATE),chunk=RATE/100;double sum=0;long counted=0;int peak=0;
  for(int at=0;at<frames;at+=chunk){
   ByteBuffer in=ByteBuffer.allocateDirect(chunk*CH*2).order(ByteOrder.nativeOrder());
   for(int n=0;n<chunk;n++){double t=(at+n)/(double)RATE,v=rmsDb>-200?amp*Math.sin(2*Math.PI*440*t):0;v+=noiseDb>-200?noise*(r.nextDouble()*2-1):0;if(burstAt>=0&&t>=burstAt&&t<burstAt+0.3)v=0.99*Math.signum(Math.sin(2*Math.PI*90*t));
    short s=(short)Math.max(-32768,Math.min(32767,Math.round(v*32767)));for(int c=0;c<CH;c++)in.putShort(s);}
   in.flip();l.queueInput(in);ByteBuffer out=l.getOutput();
   while(out.hasRemaining()){int i=out.position()/(2*CH);short s=out.getShort();peak=Math.max(peak,Math.abs((int)s));double x=s/32768.0;if(at+i>=frames-2*RATE){sum+=x*x;counted++;}}
  }
  return new double[]{counted==0||sum==0?-200:10*Math.log10(sum/counted),peak};
 }
 static Loudness fresh()throws Exception{Loudness l=new Loudness();l.configure(new AudioProcessor.AudioFormat(RATE,CH,C.ENCODING_PCM_16BIT));l.flush();return l;}
 public static void main(String[] args)throws Exception{
  double quiet=play(fresh(),20,-30,-300,-1)[0],loud=play(fresh(),20,-10,-300,-1)[0];
  System.out.printf("quiet film -30 dB -> %.1f dB, loud film -10 dB -> %.1f dB%n",quiet,loud);
  require(Math.abs(quiet-loud)<=2.5,"a quiet and a loud film end up within 2.5 dB (they were 20 dB apart)");
  require(Math.abs(quiet-Loudness.TARGET_DB)<=2.5&&Math.abs(loud-Loudness.TARGET_DB)<=2.5,"both land near the target");
  double early=play(fresh(),6,-30,-300,-1)[0];System.out.printf("quiet film after 6 s -> %.1f dB%n",early);
  require(early>=quiet-3,"a quiet film is lifted within the first seconds");
  require(play(fresh(),5,-300,-300,-1)[1]==0,"silence stays silent");
  Loudness hiss=fresh();double hissOut=play(hiss,8,-300,-62,-1)[0];System.out.printf("hiss -62 dB -> %.1f dB, gain %.1f dB%n",hissOut,hiss.gainDb());
  require(Math.abs(hiss.gainDb())<0.5,"hiss under the gate is not lifted");
  double[] burst=play(fresh(),12,-32,-300,9);System.out.printf("burst peak %d of 32767%n",(int)burst[1]);
  require(burst[1]<=32767*0.995,"a full-scale burst in a lifted quiet film never hits the ceiling hard");
  Loudness other=new Loudness();other.configure(new AudioProcessor.AudioFormat(RATE,CH,C.ENCODING_PCM_FLOAT));
  require(!other.isActive(),"sound it cannot read (float, passthrough) goes by untouched");
 }
}
