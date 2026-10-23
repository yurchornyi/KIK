/** Records sent per /api/sync request; a larger offline queue goes up in several batches. */
export const SYNC_BATCH=200;
/** Page-close sync: within the browser's 64 KiB beacon quota, the last playback position first. What does not fit stays queued locally. */
export function beaconPayload(profile,pending,latest=null){
 const records=(latest?[latest,...pending.filter(r=>r.kind!==latest.kind||r.key!==latest.key)]:pending).slice(0,SYNC_BATCH);
 const encode=()=>new Blob([JSON.stringify({source_schema:2,profile_id:profile,records})],{type:'application/json'});
 let payload=encode();
 while(payload.size>60000&&records.length>1){records.splice(Math.ceil(records.length/2));payload=encode();}
 return payload;
}
