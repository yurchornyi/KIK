/** Bounded HTTP/1.1 over verified TLS for the fixed catalogue hosts. No public proxy endpoint. */
const ALLOWED=new Set(['hdrezka.sh','hdrezka.website','rezka.ag','hdrezka.ag']);
const MAX=4*1024*1024;
function index(bytes,pattern,start=0){outer:for(let i=start;i<=bytes.length-pattern.length;i++){for(let j=0;j<pattern.length;j++)if(bytes[i+j]!==pattern[j])continue outer;return i;}return -1;}
export function parseHttp(bytes){
 const at=index(bytes,[13,10,13,10]);if(at<0||at>65536)throw Error('Invalid source headers');
 const lines=new TextDecoder('iso-8859-1').decode(bytes.subarray(0,at)).split('\r\n'),status=Number(lines.shift().match(/^HTTP\/1\.[01] (\d{3})\b/)?.[1]);if(status<200||status>599)throw Error('Invalid source response');
 const headers=new Headers();for(const line of lines){const i=line.indexOf(':');if(i<1)throw Error('Invalid source header');headers.append(line.slice(0,i).trim(),line.slice(i+1).trim());}
 let body=bytes.subarray(at+4);
 if(headers.get('transfer-encoding')?.toLowerCase().includes('chunked')){
  let at=0,size=0,parts=[];for(;;){const end=index(body,[13,10],at);if(end<0||end-at>40)throw Error('Invalid source chunk');const raw=new TextDecoder().decode(body.subarray(at,end)).split(';',1)[0];if(!/^[a-f0-9]+$/i.test(raw))throw Error('Invalid source chunk');const n=parseInt(raw,16);at=end+2;if(!n)break;if(n>MAX||at+n+2>body.length||body[at+n]!==13||body[at+n+1]!==10)throw Error('Incomplete source body');size+=n;if(size>MAX)throw Error('Source limit');parts.push(body.subarray(at,at+n));at+=n+2;}
  const joined=new Uint8Array(size);let cursor=0;for(const p of parts){joined.set(p,cursor);cursor+=p.length;}body=joined;
 }else{const length=headers.get('content-length');if(length!==null&&(!/^\d+$/.test(length)||Number(length)!==body.length))throw Error('Incomplete source response');}
 headers.delete('transfer-encoding');headers.delete('content-length');return {status,headers,body};
}
export async function socketHttp(value,options={}){
 const url=new URL(value);if(url.protocol!=='https:'||!ALLOWED.has(url.hostname)||url.port&&url.port!=='443'||url.username||url.password)throw Error('Untrusted source socket');
 const {connect}=await import('cloudflare:sockets');const socket=connect({hostname:url.hostname,port:443},{secureTransport:'on'});socket.closed.catch(()=>{});let timer;
 try{
  timer=setTimeout(()=>socket.close().catch(()=>{}),15000);await socket.opened;
  const headers={...options.headers,Host:url.hostname,Connection:'close','Accept-Encoding':'gzip'};const body=options.body?new TextEncoder().encode(options.body):null;if(body)headers['Content-Length']=String(body.length);
  const head=[`${options.method||'GET'} ${url.pathname+url.search} HTTP/1.1`,...Object.entries(headers).map(([k,v])=>{if(/[\r\n]/.test(k+v))throw Error('Invalid request header');return k+': '+v;}),'',''].join('\r\n'),writer=socket.writable.getWriter();await writer.write(new TextEncoder().encode(head));if(body)await writer.write(body);
  const reader=socket.readable.getReader(),chunks=[];let length=0;for(;;){const {done,value}=await reader.read();if(done)break;length+=value.length;if(length>MAX+65536)throw Error('Source response limit');chunks.push(value);}
  const all=new Uint8Array(length);let at=0;for(const b of chunks){all.set(b,at);at+=b.length;}const parsed=parseHttp(all);
  if(parsed.headers.get('content-encoding')==='gzip'){
   const reader=new Response(parsed.body).body.pipeThrough(new DecompressionStream('gzip')).getReader(),chunks=[];let length=0;for(;;){const {done,value}=await reader.read();if(done)break;length+=value.length;if(length>MAX){await reader.cancel();throw Error('Decoded source limit');}chunks.push(value);}const body=new Uint8Array(length);let at=0;for(const b of chunks){body.set(b,at);at+=b.length;}parsed.body=body;parsed.headers.delete('content-encoding');
  }
  return new Response(parsed.body,{status:parsed.status,headers:parsed.headers});
 }finally{clearTimeout(timer);await socket.close().catch(()=>{});}
}
