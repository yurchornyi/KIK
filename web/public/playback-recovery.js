/** Direct media only. No provider pages, players, scripts, or advertising endpoints. */
const AD_HOSTS=['doubleclick.net','googlesyndication.com','googleadservices.com','popads.net','popcash.net','adsterra.com','exoclick.com'];
export function mediaUrl(value){try{const u=new URL(value),h=u.hostname.toLowerCase();const localFixture=u.protocol==='http:'&&['localhost','127.0.0.1'].includes(h)&&globalThis.location?.origin===u.origin;if(!localFixture&&u.protocol!=='https:'||u.username||u.password||!localFixture&&u.port&&u.port!=='443'||!localFixture&&(h==='localhost'||h.includes(':')||/^\d+\.\d+\.\d+\.\d+$/.test(h))||AD_HOSTS.some(x=>h===x||h.endsWith('.'+x)))return null;if(!/\.(mp4|m3u8)(?:$|[/:])/i.test(u.pathname)||/(?:^|\/)(?:ads?|vast|vpaid|preroll|popunder)(?:\/|\.)/i.test(u.pathname))return null;return u.href;}catch{return null;}}
export const cleanVoices=voices=>(voices||[]).filter(v=>!v.premium&&!v.ads);
/** Studios that burn betting logos or ad clips into the video; same lists as PlaybackPolicy on TV. */
export const sponsoredVoice=name=>/coldfilm|колдфильм|1win/i.test(name||'');
/** Seconds of the studio ident at the start of every video, ending inside the black gap after it. */
export const studioIntro=name=>/rezka studio|студійна озвучка/i.test(name||'')?9.5:/coldfilm|колдфильм/i.test(name||'')?13.5:0;
const height=q=>/4k/i.test(q)?2160:/2k/i.test(q)?1440:Number(String(q).match(/\d+/)?.[0]||0);
export function orderedStreams(qualities,wanted){const target=height(wanted)||1080;return (qualities||[]).flatMap((q,i)=>[...new Set([q.url,...q.alternatives||[]])].map((url,j)=>({quality:q.label,url:mediaUrl(url),rank:q.label===wanted?j:100+Math.abs(height(q.label)-target)+(height(q.label)>target?3000:0)+i/100+j/1000}))).filter(x=>x.url).sort((a,b)=>a.rank-b.rank);}
/** A bounded outage budget, reset only after sustained progress or explicit user action. */
export class RecoveryPlan {
 constructor(now=Date.now){this.now=now;this.reset();}
 reset(){this.tried=new Set();this.switches=0;this.refreshes=0;this.voiceAttempts=new Set();this.stableSince=0;this.started=this.now();}
 next(qualities,wanted){if(this.switches>=10)return null;const next=orderedStreams(qualities,wanted).find(x=>!this.tried.has(x.url));if(next){this.tried.add(next.url);this.switches++;}return next||null;}
 refresh(){if(this.refreshes>=2||this.switches>=10||this.now()-this.started>60000)return false;this.refreshes++;return true;}
 voice(voices,current){const first=voices.find(v=>v.id===current);this.voiceAttempts.add(current);if(this.voiceAttempts.size>=2||this.now()-this.started>60000||this.switches>=10)return null;const next=cleanVoices(voices).find(v=>v.lang===first?.lang&&!this.voiceAttempts.has(v.id));if(next){this.voiceAttempts.add(next.id);return next;}return null;}
 progress(now){if(!this.stableSince)this.stableSince=now;if(now-this.stableSince>=30000)this.reset();}
 interrupted(){this.stableSince=0;}
}
