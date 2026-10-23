/** Finds clips spliced into one voice track by aligning its picture with another voice of the same episode.
 *  Input: 12×9 gray thumbnails of the centre 70% of each frame at FPS (logos in the corners stay out). */
export const FPS=5,SIZE=108;
const WIN=10*FPS,STEP=FPS,RANGE=15*60*FPS;
/** Frame vectors normalised to zero mean and unit spread; flat frames (black, fades) get null. */
export function frames(gray){const n=Math.floor(gray.length/SIZE),out=new Array(n);
 for(let f=0;f<n;f++){let m=0;for(let i=0;i<SIZE;i++)m+=gray[f*SIZE+i];m/=SIZE;let v=0;for(let i=0;i<SIZE;i++)v+=(gray[f*SIZE+i]-m)**2;const sd=Math.sqrt(v/SIZE);
  if(sd<6){out[f]=null;continue;}const x=new Float32Array(SIZE);for(let i=0;i<SIZE;i++)x[i]=(gray[f*SIZE+i]-m)/sd;out[f]=x;}
 return out;}
/** Mean absolute difference of two normalised frames: the same shot in another encode scores ≲0.35, other shots ≳0.6. */
export function distance(a,b){if(!a||!b)return null;let d=0;for(let i=0;i<SIZE;i++)d+=Math.abs(a[i]-b[i]);return d/SIZE;}
const SAME=0.45;
const luma=gray=>{const n=Math.floor(gray.length/SIZE),out=new Float64Array(n);for(let f=0;f<n;f++){let s=0;for(let i=0;i<SIZE;i++)s+=gray[f*SIZE+i];out[f]=s/SIZE;}return out;};
/** Best reference offset (in frames) for each STEP of `track`: {t, d} or null where the window is ambiguous. */
export function offsets(track,ref){
 const a=luma(track.gray),b=luma(ref.gray),fa=track.frames,fb=ref.frames,out=[];
 for(let t=0;t+WIN<=a.length;t+=STEP){
  let ma=0;for(let i=0;i<WIN;i++)ma+=a[t+i];ma/=WIN;let va=0;for(let i=0;i<WIN;i++)va+=(a[t+i]-ma)**2;if(va/WIN<4){out.push(null);continue;}
  let best=-2,bd=0;const lo=Math.max(0,t-RANGE),hi=Math.min(b.length-WIN,t+RANGE);
  for(let s=lo;s<=hi;s++){let mb=0;for(let i=0;i<WIN;i++)mb+=b[s+i];mb/=WIN;let vb=0,c=0;for(let i=0;i<WIN;i++){const y=b[s+i]-mb;vb+=y*y;c+=(a[t+i]-ma)*y;}const r=c/Math.sqrt(va*vb+1e-9);if(r>best){best=r;bd=s-t;}}
  if(best<0.9){out.push(null);continue;}
  // The thumbnails must agree too, not only the brightness curve.
  let ok=0,seen=0;for(let i=0;i<WIN;i+=2){const d=distance(fa[t+i],fb[t+i+bd]);if(d===null)continue;seen++;if(d<SAME)ok++;}
  out.push(seen>=5&&ok>=seen*0.8?{t,d:bd}:null);
 }
 return out;}
/** Spans of `track` (seconds) that have no counterpart in `ref`: picture inserted between two stretches that line up. */
export function insertions(track,ref,{min=5,max=150}={}){
 const list=offsets(track,ref).filter(Boolean),segs=[];
 for(const o of list){const last=segs.at(-1);if(last&&Math.abs(last.d-o.d)<=2&&o.t-last.end<=60*FPS){last.end=o.t+WIN;last.n++;}else segs.push({d:o.d,start:o.t,end:o.t+WIN,n:1});}
 const stable=segs.filter(s=>s.n>=3),found=[];
 for(let i=1;i<stable.length;i++){const p=stable[i-1],q=stable[i],extra=p.d-q.d;if(extra<min*FPS||extra>max*FPS)continue;
  // Walk frame by frame from both sides to the last frames that still match the reference.
  const same=(f,d)=>{const x=distance(track.frames[f],ref.frames[f+d]);return x===null?null:x<SAME;};
  let a=p.end-1,miss=0;for(let f=p.end-WIN;f<q.start+WIN&&miss<FPS;f++){const s=same(f,p.d);if(s===true){a=f;miss=0;}else if(s===false)miss++;}
  let b=q.start,missB=0;for(let f=q.start+WIN-1;f>p.end-WIN&&missB<FPS;f--){const s=same(f,q.d);if(s===true){b=f;missB=0;}else if(s===false)missB++;}
  const gap=b-(a+1);
  // The unmatched picture must be exactly as long as the timeline grew, else this is not a clean splice.
  // Thumbnail i shows the picture at (i+0.5)/FPS: ffmpeg's fps filter samples the middle of each interval.
  if(Math.abs(gap-extra)<=FPS)found.push({start:(a+1.5)/FPS,end:(b+0.5)/FPS,extra:extra/FPS});}
 return found;}
/** Share of frames of a[ai..] that show the same picture as b[bi..] within ±0.4 s (fast CGI shifts between encodes).
 *  The same ad clip scores ≳0.7; a sponsor read spoken over different scenes scores 0. */
export function pictureMatch(a,ai,b,bi,length){let ok=0,seen=0;
 for(let i=0;i<length;i++){const x=a[ai+i];if(!x)continue;let best=9;for(let s=-2;s<=2;s++){const d=distance(x,b[bi+i+s]);if(d!==null&&d<best)best=d;}seen++;if(best<0.6)ok++;}
 return seen<length/4?0:ok/seen;}
