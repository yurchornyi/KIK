import {register} from 'node:module';
register('data:text/javascript,'+encodeURIComponent(`export async function resolve(s,c,n){return s==='cloudflare:sockets'?{url:${JSON.stringify(new URL('./sockets.mjs',import.meta.url).href)},shortCircuit:true}:n(s,c);}`));
