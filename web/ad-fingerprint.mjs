/** Audio fingerprints for spliced-in ad clips (Haitsma–Kalker style): 32-bit codes from band-energy slopes, 8 kHz mono PCM. */
export const RATE=8000,FRAME=1024,HOP=256,FPS=RATE/HOP;
const BANDS=33,LOW=300,HIGH=2000,SILENCE=40*40;
function fft(re,im){const n=re.length;for(let i=1,j=0;i<n;i++){let b=n>>1;for(;j&b;b>>=1)j^=b;j^=b;if(i<j){[re[i],re[j]]=[re[j],re[i]];[im[i],im[j]]=[im[j],im[i]];}}
 for(let len=2;len<=n;len<<=1){const a=-2*Math.PI/len,wr=Math.cos(a),wi=Math.sin(a);for(let i=0;i<n;i+=len){let cr=1,ci=0;for(let k=0;k<len/2;k++){const p=i+k,q=p+len/2,tr=re[q]*cr-im[q]*ci,ti=re[q]*ci+im[q]*cr;re[q]=re[p]-tr;im[q]=im[p]-ti;re[p]+=tr;im[p]+=ti;const t=cr*wr-ci*wi;ci=cr*wi+ci*wr;cr=t;}}}}
const edges=Array.from({length:BANDS+1},(_,i)=>Math.round(LOW*Math.pow(HIGH/LOW,i/BANDS)*FRAME/RATE));
const window=Float64Array.from({length:FRAME},(_,i)=>0.5-0.5*Math.cos(2*Math.PI*i/(FRAME-1)));
/** One code per HOP samples; code i covers audio from i/FPS seconds. */
export function fingerprint(pcm){
 const frames=Math.max(0,Math.floor((pcm.length-FRAME)/HOP)+1),out=new Uint32Array(frames),re=new Float64Array(FRAME),im=new Float64Array(FRAME);let prev=null;
 for(let f=0;f<frames;f++){const at=f*HOP;let power=0;for(let i=0;i<FRAME;i++){re[i]=pcm[at+i]*window[i];im[i]=0;power+=pcm[at+i]*pcm[at+i];}fft(re,im);
  const e=new Float64Array(BANDS);for(let b=0;b<BANDS;b++){let s=1e-9;for(let k=edges[b];k<edges[b+1]||k===edges[b];k++)s+=re[k]*re[k]+im[k]*im[k];e[b]=s;}
  // Near-silence gets code 0, which never counts as a match: quiet scenes in two videos look alike.
  let code=0;if(prev&&power/FRAME>=SILENCE)for(let b=0;b<32;b++)if(e[b]-e[b+1]-(prev[b]-prev[b+1])>0)code|=1<<b;out[f]=code>>>0;prev=e;}
 return out;
}
const bits=x=>{x-=(x>>>1)&0x55555555;x=(x&0x33333333)+((x>>>2)&0x33333333);return (((x+(x>>>4))&0x0f0f0f0f)*0x01010101)>>>24;};
/** Bit error rate of b against a from the given offsets over n codes. Unrelated audio scores ≈0.5, the same clip ≲0.3. */
export function errorRate(a,ai,b,bi,n){let e=0;for(let i=0;i<n;i++){const x=a[ai+i],y=b[bi+i];e+=x&&y?bits(x^y):16;}return e/(32*n);}
/** Votes for alignments of needle inside hay. Whole 32-bit codes rarely survive re-encoding, so each code votes through three 16-bit slices. */
const SLICES=[0,8,16];
const indexes=new WeakMap();
function index(hay){let at=indexes.get(hay);if(at)return at;at=new Map();for(let i=0;i<hay.length;i++){const c=hay[i];if(!c)continue;for(const k of SLICES){const key=k<<16|(c>>>k)&0xffff,l=at.get(key);if(l){if(l.length<64)l.push(i);}else at.set(key,[i]);}}indexes.set(hay,at);return at;}
function offsets(hay,needle,step=1){const at=index(hay);
 const votes=new Map();for(let j=0;j<needle.length;j+=step){const c=needle[j];if(!c)continue;for(const k of SLICES){const l=at.get(k<<16|(c>>>k)&0xffff);if(!l||l.length>=64)continue;for(const i of l){const o=i-j;votes.set(o,(votes.get(o)||0)+1);}}}
 return votes;}
const WIN=Math.round(2*FPS),MATCH=0.3,SHORT=8;
/** A run found with 2-second windows can start or end up to a second inside the film around the clip: move both edges to where
 *  two consecutive quarter-second slices still match. Returns [from,to] in needle codes (hay = needle + o), or null. */
function solid(hay,needle,o,from,to){
 const ok=j=>j>=0&&j+SHORT<=needle.length&&o+j>=0&&o+j+SHORT<=hay.length&&errorRate(hay,o+j,needle,j,SHORT)<0.35;
 let s=from;while(s<to&&!(ok(s)&&ok(s+SHORT)))s++;let e=to;while(e>s&&!(ok(e-SHORT)&&ok(e-2*SHORT)))e--;
 return e-s>=WIN?[s,e]:null;}
/** Spans where `needle` plays inside `hay` (both fingerprints): [{at, from, to}] in codes; `from..to` is the matched part of the needle. */
export function locate(hay,needle,{minSeconds=10}={}){
 const votes=offsets(hay,needle),found=[],min=Math.round(minSeconds*FPS);
 const best=[...votes].filter(([,v])=>v>=8).sort((a,b)=>b[1]-a[1]).slice(0,40);
 for(const [o] of best){if(found.some(f=>Math.abs(f.at-o)<FPS))continue;
  // Longest stretch of matching windows, bridging up to two seconds of quiet or masked audio.
  let from=-1,to=-1,run=-1,end=-1;const step=Math.round(FPS/4);
  for(let j=Math.max(0,-o);j+WIN<=needle.length&&o+j+WIN<=hay.length;j+=step){if(errorRate(hay,o+j,needle,j,WIN)>=MATCH)continue;if(run<0||j-end>2*FPS)run=j;end=j+WIN;if(end-run>to-from){from=run;to=end;}}
  const r=from>=0?solid(hay,needle,o,from,to):null;if(r&&r[1]-r[0]>=min)found.push({at:o,from:r[0],to:r[1]});}
 return found.sort((a,b)=>a.at-b.at);
}
/** Long runs of identical audio shared by two different videos: [{a, b, length}] in codes (start in each, run length). */
export function shared(a,b,{minSeconds=12}={}){
 const votes=offsets(a,b,2),runs=[],min=Math.round(minSeconds*FPS);
 for(const [o,v] of [...votes].filter(([,v])=>v>=12).sort((x,y)=>y[1]-x[1]).slice(0,80)){
  // o maps b index j to a index j+o; walk b for every matching window at this offset.
  let start=-1,end=-1;const flush=()=>{const r=start>=0?solid(a,b,o,start,end):null;if(r&&r[1]-r[0]>=min&&!runs.some(x=>x.o===o&&x.b===r[0]))runs.push({o,a:r[0]+o,b:r[0],length:r[1]-r[0]});start=end=-1;};
  for(let j=Math.max(0,-o);j+WIN<=b.length&&j+o+WIN<=a.length;j+=Math.round(FPS/2)){if(errorRate(a,j+o,b,j,WIN)<MATCH){if(start<0)start=j;end=j+WIN;}else flush();}flush();}
 return runs.sort((x,y)=>x.a-y.a).filter((r,i,l)=>!l.slice(0,i).some(p=>Math.abs(p.a-r.a)<FPS*2&&Math.abs(p.b-r.b)<FPS*2));
}
export const toBase64=f=>Buffer.from(f.buffer,f.byteOffset,f.byteLength).toString('base64');
export const fromBase64=s=>{const b=Buffer.from(s,'base64');return new Uint32Array(b.buffer.slice(b.byteOffset,b.byteOffset+b.byteLength));};
