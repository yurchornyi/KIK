package kik.i18n;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import java.nio.ByteBuffer;

/**
 * «Однакова гучність»: one level for every film. The player's sound passes through here as 16-bit PCM; its loudness is
 * measured every 50 ms (bass left out, as the ear hears it; silence ignored) and a slowly moving gain brings it to one
 * target, so a quiet film is lifted and a loud one turned down. A peak limiter and a soft clip keep a lifted film from
 * crackling. Passthrough sound (Dolby to a receiver) never comes here and stays as it is.
 */
public final class Loudness extends BaseAudioProcessor {
 /** The settings switch, on by default. */
 public static final String PREF="kik_even_loudness";
 /** Where every film ends up: −20 dBFS of filtered RMS, about the level of a well-mixed streaming film. */
 static final double TARGET_DB=-20,MIN_GAIN_DB=-9,MAX_GAIN_DB=12,GATE_DB=-50,CEILING=0.89;
 /** Seconds for the measured level to follow a louder or a quieter passage; the first audible seconds follow faster. */
 static final double LOUDER_S=3,QUIETER_S=10,WARMUP_S=4,WARM_S=0.8;
 private int channels,blockFrames,inBlock;private double hpA,releasePerSample;
 private double[] lastIn,lastOut;private double sumSq,levelDb=TARGET_DB,heard,gain=1,blockStart=1,blockEnd=1,envelope,outSq;private int outCount;
 private volatile double shownGainDb,shownOutDb=-100;

 /** The gain now applied, in dB (0 while the level is unknown). */
 public double gainDb(){return shownGainDb;}
 /** The level coming out, in dB of filtered RMS, over the last half second; -100 before any sound. */
 public double outputDb(){return shownOutDb;}

 @Override protected AudioProcessor.AudioFormat onConfigure(AudioProcessor.AudioFormat f){
  if(f.encoding!=C.ENCODING_PCM_16BIT||f.channelCount<1||f.sampleRate<=0)return AudioProcessor.AudioFormat.NOT_SET;
  channels=f.channelCount;blockFrames=Math.max(1,f.sampleRate/20);
  hpA=Math.exp(-2*Math.PI*100.0/f.sampleRate);releasePerSample=Math.exp(-1.0/(0.15*f.sampleRate));
  lastIn=new double[channels];lastOut=new double[channels];return f;
 }
 @Override public void queueInput(ByteBuffer in){
  int frames=in.remaining()/(2*channels);if(frames==0){in.position(in.limit());return;}
  ByteBuffer out=replaceOutputBuffer(frames*2*channels);
  for(int n=0;n<frames;n++){
   // The gain glides across each block from the last block's value to this one's: no steps you could hear.
   double t=(double)inBlock/blockFrames,g=blockStart+(blockEnd-blockStart)*t,peak=0;
   for(int ch=0;ch<channels;ch++){
    double x=in.getShort(in.position()+2*(n*channels+ch))/32768.0;
    // A 100 Hz high-pass for the measurement only: rumble and bass weigh little in how loud a film sounds.
    double y=hpA*(lastOut[ch]+x-lastIn[ch]);lastIn[ch]=x;lastOut[ch]=y;sumSq+=y*y;
    peak=Math.max(peak,Math.abs(x));
   }
   envelope=peak>envelope?peak:envelope*releasePerSample;
   double applied=envelope*g>CEILING?CEILING/envelope:g;
   for(int ch=0;ch<channels;ch++){
    double v=clip(in.getShort(in.position()+2*(n*channels+ch))/32768.0*applied);outSq+=lastOut[ch]*lastOut[ch]*applied*applied;
    out.putShort((short)Math.max(-32768,Math.min(32767,Math.round(v*32768))));
   }
   if(++inBlock>=blockFrames)block();
  }
  in.position(in.limit());out.flip();
 }
 /** Every 50 ms: a new level from what was heard, a new gain toward the target. */
 private void block(){
  double seconds=(double)inBlock/(blockFrames*20),ms=sumSq/(inBlock*channels);sumSq=0;inBlock=0;
  if(ms>Math.pow(10,GATE_DB/10)){
   double db=10*Math.log10(ms),tau=heard<WARMUP_S?WARM_S:db>levelDb?LOUDER_S:QUIETER_S;
   levelDb+=(db-levelDb)*(1-Math.exp(-seconds/tau));heard+=seconds;
  }
  double wanted=Math.pow(10,Math.max(MIN_GAIN_DB,Math.min(MAX_GAIN_DB,TARGET_DB-levelDb))/20);
  blockStart=blockEnd;blockEnd=wanted;gain=wanted;shownGainDb=20*Math.log10(gain);
  if(++outCount>=10){shownOutDb=outSq>0?10*Math.log10(outSq/(outCount*blockFrames*channels)):-100;outSq=0;outCount=0;}
 }
 /** Above −1 dBFS the wave bends instead of breaking: the last guard against crackle. */
 private static double clip(double v){double a=Math.abs(v),k=CEILING;if(a<=k)return v;return Math.signum(v)*(k+(1-k)*Math.tanh((a-k)/(1-k)));}
 // A seek keeps the film's level; a new stream starts from the target again.
 @Override protected void onFlush(){inBlock=0;sumSq=0;envelope=0;}
 @Override protected void onReset(){levelDb=TARGET_DB;heard=0;gain=blockStart=blockEnd=1;envelope=0;sumSq=0;inBlock=0;outSq=0;outCount=0;shownGainDb=0;shownOutDb=-100;lastIn=lastOut=null;}
}
