/**
 * KIK's holiday look on the site (holidays.js says which day, flags.js whether the owner allows it): the mark in the
 * header and in the intro before a video, the greeting and the poster ring in the holiday's colour, and one thin
 * decoration at the top of the page, as the owner approved them on a preview; the TV draws the same scenes itself.
 * Nothing keeps moving: the header mark plays its moment when KIK opens, at most every 30 minutes, and then stands still;
 * particles live only during that moment, the intro and the New Year's fireworks. With reduced motion nothing moves.
 */
import {holidayToday,greeting,shelfTitle,accent} from './holidays.js';
import {storage} from './storage.js';

/** What the site wears today, if the owner's «Свята» is on: the day's holiday (`id`, null before it) and the film shelf. */
export function holidayPlan(flags,now=new Date()){
 if(!flags?.holidays)return null;const t=holidayToday(now);if(!t.holiday&&!t.shelf)return null;
 return {id:t.holiday,greeting:t.holiday?greeting(t.holiday,t.year,t.month):'',accent:t.holiday?accent(t.holiday):'',shelf:t.shelf,shelfTitle:t.shelf?shelfTitle(t.shelf):''};
}
export const MOMENT_EVERY=30*60000;
const MOMENT_KEY='kik-holiday-moment';
/** The header mark plays again only 30 minutes after it last did (a clock set back plays it too). */
export const momentDue=(last,now)=>!(last<=now&&now-last<MOMENT_EVERY);
/** New Year's fireworks: once a year, in the first ten minutes of 1 January. `played` is the year they last played. */
export const fireworksDue=(date,played)=>date.getMonth()===0&&date.getDate()===1&&date.getHours()===0&&date.getMinutes()<10&&Number(played)!==date.getFullYear();
const reduced=()=>!!globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/* ---------- the mark: the KIK letters (viewBox 102 × 48) with the holiday's detail ---------- */
const RED='#E50914',ORANGE='#FF7A1A',SPRINKLES=['#E50914','#FFD500','#3D8BFF','#2FBF71','#FF7AB6'];
const GLOW={ind:'rgba(255,213,0,.5)',hw:'rgba(255,122,26,.7)',red:'rgba(229,9,20,.75)'};
const PATHS=['M8,8L8,40','M29,8L8,24L29,40','M51,8L51,40','M73,8L73,40','M94,8L73,24L94,40'];
let uid=0;
function strokes(cls,color,{skipI=false,drawn=false}={}){
 let s=`<g class="${cls}" fill="none" stroke="${color}" stroke-width="7.6" stroke-linecap="round" stroke-linejoin="round">`;
 PATHS.forEach((d,i)=>{if(skipI&&i===2)return;const c=[drawn?`s s${i+1}`:'',i===2?'pi':''].join(' ').trim();s+=`<path d="${d}" pathLength="1"${c?` class="${c}"`:''} style="--j:${i}"/>`;});
 return s+'</g>';
}
function nyBurst(){
 let r='';for(let k=0;k<8;k++){const a=k*Math.PI/4,r1=k%2?2.8:3.2,r2=k%2?5.2:7.2;r+=`<line x1="${(Math.cos(a)*r1).toFixed(2)}" y1="${(Math.sin(a)*r1).toFixed(2)}" x2="${(Math.cos(a)*r2).toFixed(2)}" y2="${(Math.sin(a)*r2).toFixed(2)}"/>`;}
 return `<g transform="translate(51 -6.5)"><g class="ny-b" stroke="#F5C451" stroke-width="1.7" stroke-linecap="round">${r}<circle r="1.4" fill="#FFE7A3" stroke="none"/></g></g>`;
}
const gift=()=>`<g transform="translate(108 37.3)"><g class="gf"><rect x="-6.5" y="-4.5" width="13" height="10.8" rx="1.4" fill="#E9B44C"/><rect x="-.8" y="-4.5" width="1.6" height="10.8" fill="${RED}"/><g class="gl"><rect x="-7.4" y="-7.8" width="14.8" height="3.8" rx="1" fill="#F6C964"/><rect x="-.8" y="-7.8" width="1.6" height="3.8" fill="${RED}"/><path d="M0,-7.8 C-2.6,-11.8 -6,-9.6 -2.4,-7.8Z M0,-7.8 C2.6,-11.8 6,-9.6 2.4,-7.8Z" fill="${RED}"/></g></g></g>`;
const HEART='M0,13 C-7,7.5 -11,3 -11,-3.5 C-11,-8.2 -7.8,-11.5 -4.2,-11.5 C-2,-11.5 -.7,-10.2 0,-8.5 C.7,-10.2 2,-11.5 4.2,-11.5 C7.8,-11.5 11,-8.2 11,-3.5 C11,3 7,7.5 0,13 Z';
function paska(k){
 const sprinkles=[[47.3,12.3,'#E50914'],[50.6,8.6,'#FFD500'],[54.2,11.2,'#3D8BFF'],[56.5,15.8,'#2FBF71'],[48.5,17,'#FF7AB6'],[52.6,15.2,'#E50914'],[45.3,18.6,'#FFD500'],[55.2,19.6,'#3D8BFF'],[51,19.9,'#2FBF71']];
 const body='M44.3,21 H57.7 V41.4 Q57.7,43.6 55.5,43.6 H46.5 Q44.3,43.6 44.3,41.4 Z';
 return `<g class="pk"><g class="pk-b"><path d="${body}" fill="url(#${k}c)"/><path d="${body}" fill="url(#${k}s)"/></g>`+
  `<g class="pk-g"><path d="M42.3,21.5 C42.3,9 46,5 51,5 C56,5 59.7,9 59.7,21.5 Q59,25.6 58,22.2 Q56.6,23.3 55.3,22 Q54.4,26.8 53.2,22.2 Q51.5,23.4 49.8,22.1 Q48.7,25.2 47.5,22.1 Q45.5,23.2 42.3,21.5 Z" fill="#F7F2E8"/><path d="M45,12 C46,8.4 48.2,6.6 51,6.4" fill="none" stroke="#fff" stroke-width=".9" stroke-linecap="round" opacity=".85"/></g>`+
  `<g>${sprinkles.map(([x,y,c],i)=>`<circle class="sp" cx="${x}" cy="${y}" r="1" fill="${c}" style="--i:${i}"/>`).join('')}</g></g>`;
}
function spider(){
 const legs=['M-1.6,-1.2 Q-4.5,-4.6 -6.6,-2.2','M-1.9,0 Q-5.2,-1.6 -7.2,1','M-1.9,1 Q-5,2 -6.4,4.6','M-1.4,2 Q-3.6,4.2 -4.4,6.8'],all=legs.map(d=>`<path d="${d}"/>`).join('')+legs.map(d=>`<path d="${d}" transform="scale(-1 1)"/>`).join('');
 return `<g class="hw-w"><line class="hw-th" x1="39.5" y1="-60" x2="39.5" y2="23" stroke="#fff" stroke-opacity=".45" stroke-width=".5"/><g transform="translate(39.5 26.5)"><g class="hw-sp"><g fill="none" stroke="#EDEDF0" stroke-width=".8" stroke-linecap="round">${all}</g><ellipse cx="0" cy="1.3" rx="2.4" ry="2.9" fill="#EDEDF0"/><circle cy="-2.1" r="1.6" fill="#EDEDF0"/></g></g></g>`;
}
/**
 * The holiday mark as SVG. `mode`: 'intro' (the clip before a video: the letters draw, then the holiday plays), 'moment'
 * (the header when KIK opens) or 'still' (the rest of the day, nothing moves). style.css (block «Holidays») animates it.
 */
export function holidayLogo(id,mode='still',{label=''}={}){
 const k='hm'+(++uid),intro=mode==='intro',moment=mode==='moment';let defs='',parts='';
 const base=(color,options={})=>strokes('st',color,{drawn:intro,...options});
 switch(id){
  case 'ny':parts=base(RED)+nyBurst()+(intro?'<circle class="ny-fuse" cx="51" cy="40" r="2.4" fill="#FFE7A3"/>':'');break;
  case 'xmas':
   defs=`<radialGradient id="${k}r"><stop offset="0" stop-color="#F6D37A" stop-opacity=".6"/><stop offset="1" stop-color="#F6D37A" stop-opacity="0"/></radialGradient>`;
   parts=base(RED)+`<g transform="translate(51 -6)"><g class="xm-glow"><circle r="10" fill="url(#${k}r)"/></g><g class="xm-star"><path d="M0,-8 Q.9,-.9 6,0 Q.9,.9 0,7.5 Q-.9,.9 -6,0 Q-.9,-.9 0,-8Z" fill="#F6D37A"/></g></g>`;break;
  case 'nick':parts=base(RED)+gift();break;
  case 'val':parts=base(RED,{skipI:!intro})+`<g transform="translate(51 25)"><g class="vh"><path d="${HEART}" fill="${RED}" stroke="${RED}" stroke-width="5" stroke-linejoin="round"/></g></g>`;break;
  case 'easter':
   defs=`<linearGradient id="${k}c" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#E7B064"/><stop offset="1" stop-color="#A9672B"/></linearGradient><linearGradient id="${k}s" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#000" stop-opacity=".28"/><stop offset=".3" stop-color="#000" stop-opacity="0"/><stop offset=".7" stop-color="#fff" stop-opacity=".08"/><stop offset="1" stop-color="#000" stop-opacity=".32"/></linearGradient>`;
   parts=base(RED,{skipI:true})+paska(k);break;
  case 'ind':{
   // The line between the colours waves only while the mark plays; the still mark stands.
   const wave=mode==='still'?'':'<animate attributeName="x1" values="51;49;51;53;51" dur="4s" repeatCount="indefinite"/><animate attributeName="x2" values="51;53;51;49;51" dur="4s" repeatCount="indefinite"/>';
   defs=`<linearGradient id="${k}f" gradientUnits="userSpaceOnUse" x1="51" y1="4" x2="51" y2="44"><stop offset="0" stop-color="#1F6FE0"/><stop offset=".5" stop-color="#1F6FE0"/><stop offset=".5" stop-color="#FFD500"/><stop offset="1" stop-color="#FFD500"/>${wave}</linearGradient>`;
   const flag=`url(#${k}f)`;parts=intro?base(RED)+strokes('fl',flag):moment?strokes('st dimbase',flag)+strokes('fl',flag):strokes('st',flag);break;}
  case 'hw':parts=(intro?strokes('st hw-st',RED,{drawn:true}):strokes('st',ORANGE))+spider();break;
  default:parts=base(RED);
 }
 return `<svg class="hmark h-${id} m-${mode}" viewBox="0 0 102 48" style="--a:${intro?1.15:.3}s" focusable="false" ${label?`role="img" aria-label="${label}"`:'aria-hidden="true"'}>${defs?`<defs>${defs}</defs>`:''}${parts}</svg>`;
}

/* ---------- the thin decoration at the top of the page: drawn once, it never blinks ---------- */
function garland(width=3840){
 let path='',lamps='',k=0;const seg=120,sag=10;
 for(let x0=-60;x0<width;x0+=seg){const x1=x0+seg,cx=x0+seg/2,cy=3+sag*2;path+=`M${x0},3 Q${cx},${cy} ${x1},3 `;
  for(const t of [.25,.5,.75]){const bx=(1-t)*(1-t)*x0+2*(1-t)*t*cx+t*t*x1,by=(1-t)*(1-t)*3+2*(1-t)*t*cy+t*t*3;lamps+=`<circle cx="${bx.toFixed(1)}" cy="${(by+2.6).toFixed(1)}" r="2.3"${k++%3?' opacity=".55"':''}/>`;}}
 return `<svg width="${width}" height="34" viewBox="0 0 ${width} 34"><path d="${path}" fill="none" stroke="#fff" stroke-opacity=".16" stroke-width=".8"/><g fill="#FFE2A6">${lamps}</g></svg>`;
}
function stitch(){
 const px=[[9,1],[7,3],[11,3],[5,5],[13,5],[7,7],[11,7],[9,9]];
 return `<svg width="100%" height="12"><defs><pattern id="hm-stitch" width="20" height="12" patternUnits="userSpaceOnUse"><g fill="#D0122E">${px.map(([x,y])=>`<rect x="${x}" y="${y}" width="2" height="2"/>`).join('')}</g><rect x="9" y="5" width="2" height="2" fill="#F2C14E"/></pattern></defs><rect width="100%" height="12" fill="url(#hm-stitch)" opacity=".9"/></svg>`;
}
/** Christmas: a garland; Easter: a cross-stitch strip; Independence Day: a blue and yellow line; other days nothing. */
export function holidayTop(id){return id==='xmas'?garland():id==='easter'?stitch():id==='ind'?'<div class="holiday-flag"><i></i><i></i></div>':'';}

/* ---------- particles: one canvas, alive only while an effect plays ---------- */
function heartPath(x,cx,cy,r){x.beginPath();x.moveTo(cx,cy+r*.9);x.bezierCurveTo(cx-r*1.3,cy+r*.1,cx-r*.9,cy-r*.9,cx,cy-r*.35);x.bezierCurveTo(cx+r*.9,cy-r*.9,cx+r*1.3,cy+r*.1,cx,cy+r*.9);x.fill();}
function star4(x,cx,cy,r){x.beginPath();x.moveTo(cx,cy-r);x.quadraticCurveTo(cx,cy,cx+r,cy);x.quadraticCurveTo(cx,cy,cx,cy+r);x.quadraticCurveTo(cx,cy,cx-r,cy);x.quadraticCurveTo(cx,cy,cx,cy-r);x.fill();}
/** Sparks, hearts, confetti and fireworks on a canvas. Sizes follow `scale` (else the canvas width over 960, as on the TV). */
export class HolidayFX{
 constructor(canvas,{scale,onIdle}={}){this.c=canvas;this.x=canvas.getContext?.('2d')||null;this.p=[];this.t=new Set();this.raf=0;this.last=0;this.w=1;this.h=1;this.scale=scale;this.onIdle=onIdle;}
 resize(){const r=this.c.getBoundingClientRect(),dpr=Math.min(2,globalThis.devicePixelRatio||1);this.w=r.width||1;this.h=r.height||1;this.c.width=Math.max(1,Math.round(this.w*dpr));this.c.height=Math.max(1,Math.round(this.h*dpr));this.x?.setTransform(dpr,0,0,dpr,0,0);}
 get s(){return this.scale??this.w/960;}
 /** Everything stops at once: timers, the frame loop and the particles. */
 stop(){for(const id of this.t)clearTimeout(id);this.t.clear();this.p.length=0;if(this.raf)cancelAnimationFrame(this.raf);this.raf=0;this.x?.clearRect(0,0,this.w,this.h);}
 /** Stops and takes the canvas away: the page's overlay, once its effect is over. */
 finish(){this.stop();this.c.remove();}
 at(ms,f){const id=setTimeout(()=>{this.t.delete(id);try{f();}finally{this.idle();}},ms);this.t.add(id);}
 idle(){if(!this.p.length&&!this.t.size&&!this.raf)this.onIdle?.();}
 /** A point of the mark (in its 102 × 48 units) on this canvas. */
 pt(el,lx,ly){const r=el.getBoundingClientRect(),c=this.c.getBoundingClientRect();return {x:r.left-c.left+lx/102*r.width,y:r.top-c.top+ly/48*r.height};}
 box(el){const r=el.getBoundingClientRect(),c=this.c.getBoundingClientRect();return {x:r.left-c.left,y:r.top-c.top,w:r.width,h:r.height};}
 add(o){if(!this.x)return;this.p.push({vx:0,vy:0,g:0,drag:0,age:0,life:1,size:1.5,rot:0,spin:0,type:'dot',color:'#fff',...o});if(!this.raf){this.last=performance.now();this.raf=requestAnimationFrame(t=>this.tick(t));}}
 burst(x,y,o){const s=this.s,n=o.n||50;for(let i=0;i<n;i++){const a=i/n*Math.PI*2+Math.random()*.3,v=(o.speed||160)*s*(.45+Math.random()*.55);this.add({x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v,g:(o.g||100)*s,drag:o.drag||1.6,life:(o.life||1.2)*(.7+Math.random()*.45),size:(o.size||1.5)*(.8+Math.random()*.5),color:o.colors[i%o.colors.length],type:o.type||'spark'});}}
 sparks(x,y,n){const s=this.s;for(let i=0;i<n;i++){const a=Math.random()*Math.PI*2,v=(30+Math.random()*60)*s;this.add({x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v-20*s,g:120*s,drag:2,life:.35+Math.random()*.3,size:1.1,color:Math.random()<.5?'#FFE7A3':'#FFFFFF',type:'dot'});}}
 hearts(x,y,n,colors){const s=this.s;for(let i=0;i<n;i++)this.at(i*120,()=>this.add({x:x+(Math.random()-.5)*40*s,y,vx:(Math.random()-.5)*20*s,vy:-(40+Math.random()*30)*s,g:-6*s,drag:.4,life:1.8+Math.random()*.8,size:(5+Math.random()*4)*s,color:colors[i%colors.length],type:'heart',sw:Math.random()*6}));}
 confetti(x,y,n,colors){const s=this.s;for(let i=0;i<n;i++){const a=-Math.PI/2+(Math.random()-.5)*2.2,v=(80+Math.random()*90)*s;this.add({x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v,g:170*s,drag:1.4,life:1.1+Math.random()*.5,size:(2+Math.random()*1.4)*s,rot:Math.random()*6,spin:(Math.random()-.5)*14,color:colors[i%colors.length],type:'conf'});}}
 twinkles(x,y,n,color){const s=this.s;for(let i=0;i<n;i++)this.at(Math.random()*600,()=>{const a=Math.random()*Math.PI*2,r=(10+Math.random()*26)*s;this.add({x:x+Math.cos(a)*r,y:y+Math.sin(a)*r,life:.6+Math.random()*.7,size:1+Math.random()*1.3,color,type:'tw'});});}
 /** Falling snow for the intro only: it lasts until the intro stops it. */
 snow(n){for(let i=0;i<n;i++)this.add({x:Math.random()*this.w,y:Math.random()*this.h,type:'flake',life:Infinity,vy:9+Math.random()*12,sw:.6+Math.random()*.9,amp:6+Math.random()*10,ph:Math.random()*9,size:.9+Math.random()*1.3,alpha:.25+Math.random()*.4});}
 rocket(x,ty,y0,cb){const s=this.s,g=320*s,dy=Math.max(20,y0-ty);this.add({x,y:y0,vx:(Math.random()-.5)*10*s,vy:-Math.sqrt(2*g*dy),g,type:'rocket',life:9,color:'#FFE7A3',cb});}
 /** Six rockets over the box `b` (on this canvas), about four seconds in all. */
 fireworks(b){
  const sets=[['#FFE7A3','#F5C451','#FFFFFF'],['#E50914','#FF8A8A','#FFE7A3'],['#FFFFFF','#BFD8FF'],['#F5C451','#FFFFFF']];
  for(let i=0;i<6;i++)this.at(i*420,()=>{const c=sets[i%sets.length];this.rocket(b.x+b.w*(.42+Math.random()*.5),b.y+b.h*(.1+Math.random()*.28),b.y+b.h*.82,(x,y)=>this.burst(x,y,{n:70,colors:c,speed:200,g:70,life:1.5,size:1.4}));});
 }
 tick(t){
  const dt=Math.min(.05,(t-this.last)/1000);this.last=t;const x=this.x,s=this.s;x.clearRect(0,0,this.w,this.h);
  for(let i=this.p.length-1;i>=0;i--){
   const p=this.p[i];p.age+=dt;
   if(p.type==='flake'){p.y+=p.vy*s*dt;p.x+=Math.sin((p.age+p.ph)*p.sw)*p.amp*s*dt;if(p.y>this.h+6){p.y=-6;p.x=Math.random()*this.w;}
    x.globalCompositeOperation='source-over';x.globalAlpha=p.alpha;x.fillStyle='#fff';x.beginPath();x.arc(p.x,p.y,Math.max(.7,p.size*s*1.5),0,7);x.fill();continue;}
   const dr=Math.exp(-p.drag*dt);p.vx*=dr;p.vy*=dr;p.vy+=p.g*dt;p.x+=p.vx*dt;p.y+=p.vy*dt;p.rot+=p.spin*dt;
   if(p.type==='rocket'){if(p.vy>=0){this.p.splice(i,1);p.cb?.(p.x,p.y);continue;}
    x.globalCompositeOperation='lighter';x.globalAlpha=.9;x.strokeStyle=p.color;x.lineWidth=Math.max(1,1.8*s);x.lineCap='round';x.beginPath();x.moveTo(p.x,p.y);x.lineTo(p.x-p.vx*.06,p.y-p.vy*.06);x.stroke();continue;}
   if(p.age>=p.life){this.p.splice(i,1);continue;}
   const k=1-p.age/p.life;
   switch(p.type){
    case 'spark':x.globalCompositeOperation='lighter';x.globalAlpha=Math.min(1,k*1.5);x.strokeStyle=p.color;x.lineWidth=Math.max(.8,p.size*s*1.6);x.lineCap='round';x.beginPath();x.moveTo(p.x,p.y);x.lineTo(p.x-p.vx*.05,p.y-p.vy*.05);x.stroke();break;
    case 'dot':x.globalCompositeOperation='lighter';x.globalAlpha=Math.min(1,k*1.5);x.fillStyle=p.color;x.beginPath();x.arc(p.x,p.y,Math.max(.7,p.size*s*1.3),0,7);x.fill();break;
    case 'tw':{const a=Math.sin(p.age/p.life*Math.PI);x.globalCompositeOperation='lighter';x.globalAlpha=a;x.fillStyle=p.color;star4(x,p.x,p.y,Math.max(1,p.size*s*4*a));break;}
    case 'heart':x.globalCompositeOperation='source-over';x.globalAlpha=Math.min(1,k*1.4)*.9;x.fillStyle=p.color;heartPath(x,p.x+Math.sin(p.age*3+p.sw)*4*s,p.y,Math.max(2,p.size));break;
    case 'conf':x.globalCompositeOperation='source-over';x.globalAlpha=Math.min(1,k*1.6);x.fillStyle=p.color;x.save();x.translate(p.x,p.y);x.rotate(p.rot);x.fillRect(-p.size,-p.size*.45,p.size*2,p.size*.9);x.restore();break;
   }
  }
  x.globalAlpha=1;x.globalCompositeOperation='source-over';
  this.raf=this.p.length?requestAnimationFrame(tt=>this.tick(tt)):0;if(!this.raf)this.idle();
 }
}

/** The intro's particles, in step with the CSS of the mark (its holiday starts at 1.15 s). */
export function introFx(id,fx,svg){
 const a=1150,P=(lx,ly)=>fx.pt(svg,lx,ly);
 switch(id){
  case 'ny':
   for(let t=0;t<600;t+=40)fx.at(a+t,()=>{const q=Math.pow((t+40)/600,1.6),p=P(51,40-46*q);fx.sparks(p.x,p.y,3);});
   fx.at(a+600,()=>{const p=P(51,-6.5);fx.burst(p.x,p.y,{n:80,colors:['#FFE7A3','#F5C451','#FFFFFF'],speed:230,g:90,life:1.4,size:1.5});});
   fx.at(a+950,()=>{const p=P(8,-40);fx.burst(p.x,p.y,{n:50,colors:['#E50914','#FF8A8A','#FFE7A3'],speed:170,g:80,life:1.2,size:1.3});});
   fx.at(a+1250,()=>{const p=P(94,-34);fx.burst(p.x,p.y,{n:50,colors:['#FFFFFF','#F5C451'],speed:170,g:80,life:1.2,size:1.3});});break;
  case 'xmas':fx.at(a,()=>fx.snow(14));fx.at(a+700,()=>{const p=P(51,-6);fx.twinkles(p.x,p.y,8,'#F6D37A');});break;
  case 'nick':fx.at(a+200,()=>fx.snow(10));fx.at(a+900,()=>{const p=P(108,29);fx.burst(p.x,p.y,{n:18,colors:['#F6D37A','#FFFFFF'],speed:90,g:40,life:.9,size:1.2,type:'dot'});});break;
  case 'val':fx.at(a+950,()=>{const p=P(51,12);fx.hearts(p.x,p.y,6,['#FF4D6D','#E50914','#FF8FA3']);});break;
  case 'easter':fx.at(a+1650,()=>{const p=P(51,5);fx.confetti(p.x,p.y,24,SPRINKLES);});break;
 }
}
/** The header moment's particles (its holiday starts at 0.3 s); false when this holiday has none. On Valentine's Day the
 *  hearts rise from the greeting over the hero, or from the mark when the hero is not there yet. */
export function momentFx(id,fx,svg){
 const a=300,P=(lx,ly)=>fx.pt(svg,lx,ly);
 switch(id){
  case 'ny':fx.at(a+200,()=>{const p=P(51,-6.5);fx.burst(p.x,p.y,{n:40,colors:['#FFE7A3','#F5C451','#FFFFFF'],speed:120,g:60,life:1,size:1.2});});return true;
  case 'xmas':fx.at(a+300,()=>{const p=P(51,-6);fx.twinkles(p.x,p.y,7,'#F6D37A');});return true;
  case 'nick':fx.at(a+900,()=>{const p=P(108,29);fx.burst(p.x,p.y,{n:14,colors:['#F6D37A','#FFFFFF'],speed:70,g:40,life:.8,size:1.1,type:'dot'});});return true;
  case 'val':fx.at(a+250,()=>{const g=document.querySelector('.hero .holiday-greeting'),b=g?fx.box(g):null,p=b?{x:b.x+Math.min(b.w,160)/2,y:b.y}:P(51,44);fx.hearts(p.x,p.y,6,['#FF4D6D','#E50914','#FF8FA3']);});return true;
  case 'easter':fx.at(a+400,()=>{const p=P(51,5);fx.confetti(p.x,p.y,14,SPRINKLES);});return true;
 }
 return false;
}
/** A canvas over the whole window for one effect; it goes once the effect is over. */
function overlay(scale){const canvas=document.createElement('canvas');canvas.className='holiday-fx';canvas.setAttribute('aria-hidden','true');document.body.append(canvas);const fx=new HolidayFX(canvas,{scale,onIdle:()=>fx.finish()});fx.resize();return fx;}

/**
 * The page on the holiday: the accent colour (greeting, poster ring), the decoration at the top and the header mark,
 * which plays its moment now (or when the tab is first looked at) unless it did in the last 30 minutes.
 */
export function dressUp(plan){
 const root=document.documentElement;root.classList.add('holiday');root.style.setProperty('--holiday-accent',plan.accent);
 const top=holidayTop(plan.id);if(top&&!document.querySelector('.holiday-top')){const strip=document.createElement('div');strip.className='holiday-top';strip.setAttribute('aria-hidden','true');strip.innerHTML=top;document.body.prepend(strip);}
 const img=document.querySelector('.header .brand img');if(!img)return;
 const mark=document.createElement('span');mark.className='holiday-mark';mark.innerHTML=holidayLogo(plan.id,'still');img.replaceWith(mark);
 const play=()=>{
  const now=Date.now();if(reduced()||!momentDue(Number(storage.getItem(MOMENT_KEY))||0,now))return;storage.setItem(MOMENT_KEY,String(now));
  mark.innerHTML=holidayLogo(plan.id,'moment');const fx=overlay(1);if(!momentFx(plan.id,fx,mark.firstElementChild))fx.finish();
  setTimeout(()=>{mark.innerHTML=holidayLogo(plan.id,'still');},2600);
 };
 if(!document.hidden)play();else document.addEventListener('visibilitychange',function shown(){if(document.hidden)return;document.removeEventListener('visibilitychange',shown);play();});
}
/** How long the holiday intro stays (app.js playIntro): a little longer than the plain one, for the greeting. */
export const HOLIDAY_INTRO_MS=3650;
/**
 * The holiday scene inside the intro `node`, already on the player (app.js playIntro): the letters draw, the holiday
 * plays around them, the greeting rises under them. Returns what stops its particles when the intro ends.
 */
export function holidayIntro(node,plan){
 node.classList.add('holiday-intro');node.style.setProperty('--holiday-accent',plan.accent);node.style.setProperty('--glow',GLOW[plan.id]||GLOW.red);
 const scene=document.createElement('div'),logo=document.createElement('div'),words=document.createElement('div'),canvas=document.createElement('canvas');
 scene.className='intro-holiday';logo.className='intro-logo';logo.innerHTML=holidayLogo(plan.id,'intro');words.className='intro-greeting';words.textContent=plan.greeting;canvas.className='intro-fx';
 scene.append(logo,words);node.prepend(scene);node.append(canvas);
 const fx=new HolidayFX(canvas);fx.resize();introFx(plan.id,fx,logo.firstElementChild);
 return ()=>fx.stop();
}
/** The New Year's fireworks over `el` (the home banner), when it is on screen; false when they could not play. */
export function fireworksOver(el){
 if(reduced())return false;const r=el.getBoundingClientRect();if(r.bottom<=0||r.top>=(globalThis.innerHeight||0))return false;
 const fx=overlay(Math.max(.5,Math.min(1.3,r.width/960)));fx.fireworks(fx.box(el));return true;
}
