/** Original networks and production companies, never a regional streaming availability list. */
export const STUDIOS=[
 {id:'netflix',name:'Netflix',mark:'N',networks:[213],companies:['Netflix','Netflix Studios','Netflix Animation']},
 {id:'apple',name:'Apple TV+',mark:'tv+',networks:[2552],companies:['Apple Studios','Apple Original Films']},
 {id:'hbo',name:'HBO',mark:'HBO',networks:[49,3186],companies:['HBO','HBO Films','HBO Documentary Films','HBO Entertainment']},
 {id:'prime',name:'Prime Video',mark:'prime',networks:[1024],companies:['Amazon Studios','Amazon MGM Studios']},
 {id:'disney',name:'Disney+',mark:'Disney+',networks:[2739],companies:['Walt Disney Pictures','Walt Disney Animation Studios','Disney Television Animation','Disney Branded Television']},
 {id:'hulu',name:'Hulu',mark:'hulu',networks:[453],companies:['Hulu']},
 {id:'paramount',name:'Paramount+',mark:'P+',networks:[4330],companies:['Paramount Pictures','Paramount Animation','Paramount Players','Paramount Television Studios']},
 {id:'warner',name:'Warner Bros.',mark:'WB',networks:[],companies:['Warner Bros. Pictures','Warner Bros. Animation','Warner Bros. Television']},
 {id:'pixar',name:'Pixar',mark:'PIXAR',networks:[],companies:['Pixar']}
];
export const studioById=id=>STUDIOS.find(s=>s.id===id)||null;
const normalize=s=>String(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');
/** IDs are used for original TV networks; company names require an exact match. */
export function studiosFor(details={}){
 return STUDIOS.filter(s=>(details.networks||[]).some(n=>s.networks.includes(n.id))||
  (details.production_companies||[]).some(c=>s.companies.some(name=>normalize(name)===normalize(c.name)))).map(s=>s.id);
}
export function studioBadge(ids,document=globalThis.document){
 const studio=(ids||[]).map(studioById).find(Boolean);if(!studio)return null;
 const badge=document.createElement('span');badge.className='studio-badge studio-'+studio.id;badge.textContent=studio.mark;
 badge.title=studio.name;badge.setAttribute('aria-label','Студія / платформа: '+studio.name);badge.dataset.studio=studio.id;return badge;
}
