/** `cloudflare:sockets` for Node, so the Worker's catalogue code runs unchanged on the scanner PC. */
import tls from 'node:tls';
export function connect({hostname,port}){
 const s=tls.connect({host:hostname,port,servername:hostname});
 const opened=new Promise((resolve,reject)=>{s.once('secureConnect',resolve);s.once('error',reject);}),closed=new Promise(resolve=>s.once('close',resolve));
 const readable=new ReadableStream({start(c){s.on('data',d=>c.enqueue(new Uint8Array(d)));s.on('end',()=>c.close());s.on('error',e=>{try{c.error(e);}catch{}});}});
 const writable=new WritableStream({write(chunk){return new Promise((resolve,reject)=>s.write(chunk,e=>e?reject(e):resolve()));}});
 return {opened,closed,readable,writable,close:async()=>s.destroy()};
}
