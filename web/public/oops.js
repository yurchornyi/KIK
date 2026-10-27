/**
 * KIK's «Упс» screens: the logo, what happened in plain words and a retry. While the cause is temporary (no connection,
 * the film source paused, a slow answer, KIK itself restarting) the screen counts down and retries by itself, so the
 * viewer never has to press anything. Plain DOM, no dependencies; styles in style.css (.oops).
 */
const COPY={
 offline:['Упс, немає зв’язку','Перевірте інтернет. KIK продовжить сам, щойно з’єднання повернеться.'],
 source:['Упс, джерело фільмів мовчить','Ми вже працюємо над цим. KIK сам спробує ще раз — нічого натискати не треба.'],
 server:['Упс, KIK на хвилинку недоступний','Ми вже працюємо над цим. KIK сам спробує ще раз за мить.'],
 slow:['Упс, щось довго','Відповідь затримується. Ми вже працюємо над цим і спробуємо ще раз.'],
 missing:['Упс, такого тут немає','Можливо, цю назву прибрали з джерела. Пошукайте інший фільм.'],
 other:['Упс, щось пішло не так','Ми вже працюємо над цим. Спробуйте ще раз за мить.'],
};
const AUTO=new Set(['offline','source','server','slow']);
/** Seconds before each automatic retry of the same thing; the last step repeats. */
export const RETRY_STEPS=[10,20,30,60];
const attempts=new Map();
/** What went wrong, from the flags api() puts on its errors: picks the words and whether KIK retries by itself. */
export function errorKind(error,online=true){
 if(!online||error?.offline)return 'offline';
 if(error?.slow)return 'slow';
 if(error?.retry)return 'source';
 if(error?.server||error?.status>=500)return 'server';
 if(error?.status===404)return 'missing';
 return 'other';
}
export const oopsCopy=kind=>COPY[kind]||COPY.other;
/** The wait before the next automatic retry of `key`: longer while the same thing keeps failing, from the start after a quiet 3 minutes. */
export function retryWait(key,now=Date.now()){
 const last=key?attempts.get(key):null,n=last&&now-last.at<180000?last.n+1:0;
 if(key){attempts.set(key,{n,at:now});if(attempts.size>50)attempts.delete(attempts.keys().next().value);}
 return RETRY_STEPS[Math.min(n,RETRY_STEPS.length-1)];
}
function node(tag,attrs={},...children){const n=document.createElement(tag);for(const [k,v] of Object.entries(attrs))if(v!=null)n.setAttribute(k,String(v));for(const c of children)if(c!=null)n.append(c);return n;}
/**
 * The screen itself. `retry` reloads whatever failed (it may replace this screen); `title` names the film it was about;
 * `key` ties repeated failures of the same thing together so the waits grow; `size` is 'page', 'modal' or 'compact'.
 */
export function oops({error,retry,title='',key='',size='modal',kind=errorKind(error,typeof navigator==='undefined'||navigator.onLine!==false)}={}){
 const [heading,text]=oopsCopy(kind),auto=!!retry&&AUTO.has(kind);
 const status=node('p',{class:'oops-status',role:'status'}),bar=node('span',{class:'oops-bar','aria-hidden':'true'},node('span'));
 const again=retry?node('button',{class:'pill red',type:'button'},auto?'Спробувати зараз':'Спробувати ще'):null;
 const box=node('section',{class:'oops oops-'+size,role:'alert','aria-live':'assertive'},
  node('div',{class:'oops-mark','aria-hidden':'true'},node('img',{src:'/logo.svg',alt:'',width:102,height:48})),
  title?node('p',{class:'oops-title'},title):null,
  node('h2',{},heading),node('p',{class:'oops-text'},text),
  kind==='other'&&error?.message?node('p',{class:'oops-detail'},error.message):null,
  again?node('div',{class:'oops-actions'},again):null,
  auto?bar:null,auto?status:null);
 if(!retry)return box;
 let timer=0,done=false;
 const stop=()=>{clearInterval(timer);removeEventListener('online',run);};
 function run(){if(done||!box.isConnected)return stop();done=true;stop();again.disabled=true;status.textContent='Пробуємо ще раз…';bar.classList.add('busy');retry();}
 again.addEventListener('click',()=>{attempts.delete(key);run();});
 if(!auto)return box;
 const wait=retryWait(key);let left=wait;
 const paint=()=>{if(kind==='offline'&&navigator.onLine===false){status.textContent='Чекаємо на з’єднання…';bar.firstChild.style.transform='scaleX(0)';return;}
  status.textContent='Спробуємо ще раз через '+left+' с';bar.firstChild.style.transform='scaleX('+(1-left/wait).toFixed(3)+')';};
 // A hidden tab or a lost connection holds the countdown; the connection coming back retries at once.
 timer=setInterval(()=>{if(!box.isConnected)return stop();if(document.hidden||kind==='offline'&&navigator.onLine===false)return paint();if(--left<=0)run();else paint();},1000);
 addEventListener('online',run);paint();
 return box;
}
