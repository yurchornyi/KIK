/** Shared defaults and quality fallback for web playback. */
export const DEFAULT_SETTINGS=Object.freeze({quality:'1080p',speed:1,audio:'uk',autoplay:true});
export function preferredQuality(qualities,preferred){if(!qualities?.length)return null;const exact=qualities.find(q=>q.label===preferred);if(exact)return exact;const rank=q=>Number(q.label.match(/\d+/)?.[0])||0,sorted=[...qualities].sort((a,b)=>rank(b)-rank(a));if(preferred==='auto')return sorted[0];const target=Number(String(preferred).match(/\d+/)?.[0])||1080;return sorted.find(q=>rank(q)<=target)||sorted.at(-1);}
