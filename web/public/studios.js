/** Original networks and production companies, never a regional streaming availability list. */
export const STUDIOS=[
 {id:'netflix',name:'Netflix',mark:'N',networks:[213],companies:['Netflix','Netflix Studios','Netflix Animation']},
 {id:'apple',name:'Apple TV+',mark:'tv+',networks:[2552],companies:['Apple Studios','Apple Original Films']},
 {id:'hbo',name:'HBO',mark:'HBO',networks:[49,3186],companies:['HBO','HBO Films','HBO Documentary Films','HBO Entertainment']},
 {id:'prime',name:'Prime Video',mark:'prime',networks:[1024],companies:['Amazon Studios','Amazon MGM Studios']},
 {id:'disney',name:'Disney+',mark:'Disney+',networks:[2739],companies:['Walt Disney Pictures','Walt Disney Animation Studios','Disney Television Animation','Disney Branded Television']},
 {id:'hulu',name:'Hulu',mark:'hulu',networks:[453],companies:['Hulu'],categories:['series','cartoons']},
 {id:'paramount',name:'Paramount+',mark:'P+',networks:[4330],companies:['Paramount Pictures','Paramount Animation','Paramount Players','Paramount Television Studios']},
 {id:'warner',name:'Warner Bros.',mark:'WB',networks:[],companies:['Warner Bros. Pictures','Warner Bros. Animation','Warner Bros. Television']},
 {id:'pixar',name:'Pixar',mark:'PIXAR',networks:[],companies:['Pixar'],categories:['cartoons'],within:'disney'},
 {id:'natgeo',name:'National Geographic',mark:'NAT GEO',networks:[43,1043],companies:['National Geographic','National Geographic Films','National Geographic Documentary Films','Nat Geo Wild'],categories:['series','films']},
 {id:'discovery',name:'Discovery',mark:'discovery',networks:[64,4353],companies:['Discovery Channel','Discovery Studios','Discovery Docs'],categories:['series','films']}
];
export const studioById=id=>STUDIOS.find(s=>s.id===id)||null;
/** Catalogue sections a studio makes: Pixar only animated films, Hulu series and animated series; the rest, every one.
 *  Filtered by a studio, a section it does not make is empty however many pages are read (14.11, owner: «нічого немає від Pixar, Hulu»). */
export const makes=(studio,category)=>!!studio&&(!studio.categories||studio.categories.includes(category));
/** The section to show for studios: the current one when one of them makes it, else the first studio's own first section. */
export function studioCategory(ids,category){const studios=[].concat(ids||[]).map(studioById).filter(Boolean);return !studios.length||studios.some(s=>makes(s,category))?category:studios[0].categories[0];}
const normalize=s=>String(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');
/** IDs are used for original TV networks; company names require an exact match. The first id is the badge: the
 *  original network before a studio that only made the title (a Netflix series from a Warner studio is Netflix's), and
 *  a studio before the one it belongs to (a Pixar film co-made by Walt Disney Pictures is Pixar's; 14.11, owner: «нічого
 *  немає від Pixar»). */
export function studiosFor(details={}){
 const network=s=>(details.networks||[]).some(n=>s.networks.includes(n.id)),
  company=s=>(details.production_companies||[]).some(c=>s.companies.some(name=>normalize(name)===normalize(c.name)));
 const found=STUDIOS.filter(s=>network(s)||company(s)),rank=s=>network(s)?0:found.some(x=>x.id===s.within)?1:2;
 return found.map((s,i)=>({id:s.id,rank:rank(s),i})).sort((a,b)=>a.rank-b.rank||a.i-b.i).map(x=>x.id);
}
/** One emblem per studio, drawn in KIK's own hand (no brand files): the studio's mark in its own colours on a dark pill
 *  (owner's pick «А», 14.11), the same as Studios.Mark on the TV. Gradient ids are unique per badge: a badge hidden with
 *  display:none must not hold the only copy another badge points at. */
let marks=0;
const APPLE='<svg width="14" height="14" viewBox="0 0 24 24" fill="#fff" aria-hidden="true"><path d="M12 6.4c-2.3-1.9-5.8-1.2-7.2 1.5-2 3.7.1 9.8 3.5 11.8 1.2.7 2.2.2 3.7.2s2.5.5 3.7-.2c3.4-2 5.5-8.1 3.5-11.8-1.4-2.7-4.9-3.4-7.2-1.5z"/><path d="M12.4 5.6c.2-1.9 1.5-3.2 3.3-3.4 0 1.9-1.5 3.4-3.3 3.4z"/></svg>';
const word=(text,extra='')=>`<span class="studio-word">${text}</span>${extra}`;
function mark(id){const g='studio-mark-'+(++marks);switch(id){
 case 'netflix':return `<svg width="10" height="17" viewBox="0 0 14 24" aria-hidden="true"><defs><linearGradient id="${g}l" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7a0510"/><stop offset=".45" stop-color="#b1060f"/></linearGradient><linearGradient id="${g}r" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#7a0510"/><stop offset=".45" stop-color="#b1060f"/></linearGradient></defs><path d="M0 0h4.5v23.3Q2.2 23.5 0 24.3z" fill="url(#${g}l)"/><path d="M9.5-.3Q11.8.5 14 0v24H9.5z" fill="url(#${g}r)"/><path d="M0 0h4.5L14 24H9.5z" fill="#e50914"/></svg>`;
 case 'apple':return APPLE+word('tv+');
 case 'hbo':return word('HB','<svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true"><circle cx="5" cy="5" r="3.1" fill="none" stroke="#fff" stroke-width="1.6"/><circle cx="5" cy="5" r="1" fill="#fff"/></svg>');
 case 'prime':return `<span class="studio-stack">${word('prime')}<svg width="30" height="6.6" viewBox="0 0 30 6.6" aria-hidden="true"><path d="M1.5 1.2c7 4.6 18 4.8 25.5.6" fill="none" stroke="#1a98ff" stroke-width="1.9" stroke-linecap="round"/><path d="M24.2.6l3.6.9-1 3.3" fill="none" stroke="#1a98ff" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg></span>`;
 case 'disney':return `<span class="studio-arc"><svg width="45" height="10" viewBox="0 0 54 12" aria-hidden="true"><defs><linearGradient id="${g}" x1="0" x2="1"><stop offset="0" stop-color="#2f7bff" stop-opacity="0"/><stop offset=".35" stop-color="#59c9ff"/><stop offset="1" stop-color="#e6fbff"/></linearGradient></defs><path d="M3 11C14 1 38-1 52 8" fill="none" stroke="url(#${g})" stroke-width="1.6" stroke-linecap="round"/></svg>${word('Disney','<i>+</i>')}</span>`;
 case 'hulu':return word('hulu');
 case 'paramount':return '<svg width="17" height="17" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 21 12 8l9 13z" fill="#2f7bff"/><path d="M9.7 11.3 12 8l2.3 3.3-2.3 1.5z" fill="#fff" opacity=".9"/><g fill="#fff"><circle cx="4.2" cy="10.6" r="1.25"/><circle cx="6.6" cy="6.6" r="1.25"/><circle cx="10" cy="4" r="1.25"/><circle cx="14" cy="4" r="1.25"/><circle cx="17.4" cy="6.6" r="1.25"/><circle cx="19.8" cy="10.6" r="1.25"/></g></svg>'+word('+');
 case 'warner':return '<svg width="15.5" height="18" viewBox="0 0 20 23" aria-hidden="true"><path d="M1.5 1.5h17V12c0 4.9-3.4 8.2-8.5 9.9C4.9 20.2 1.5 16.9 1.5 12z" fill="#1f6fe8" stroke="#ffffff55" stroke-width=".8"/><text x="10" y="13.6" text-anchor="middle" font-weight="900" font-size="8.2" fill="#fff" letter-spacing="-.2">WB</text></svg>';
 case 'natgeo':return '<svg width="10" height="14" viewBox="0 0 10 14" aria-hidden="true"><rect x="1.1" y="1.1" width="7.8" height="11.8" fill="none" stroke="#ffce00" stroke-width="2.2"/></svg>'+word('NAT GEO');
 case 'discovery':return '<svg width="13" height="13" viewBox="0 0 13 13" aria-hidden="true"><circle cx="6.5" cy="6.5" r="6" fill="#2d8cff"/><ellipse cx="6.5" cy="6.5" rx="2.5" ry="6" fill="none" stroke="#fff" stroke-width="1"/><path d="M.5 6.5h12M1.6 3.6h9.8M1.6 9.4h9.8" fill="none" stroke="#fff" stroke-width=".8"/></svg>'+word('discovery');
 case 'pixar':return '<svg width="14" height="14" viewBox="0 0 24 24" fill="#fff" aria-hidden="true"><rect x="2" y="19.8" width="10" height="2.6" rx="1.3"/><path d="M7 20.2 10 12.6 13.4 5.2" fill="none" stroke="#fff" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/><path d="M12.4 4.4 15 2.4 21.6 7.6 16.6 11.6z"/></svg>'+word('PIXAR');
 default:return '';
}}
export function studioBadge(ids,document=globalThis.document){
 // Artwork this browser kept from before 14.11 may still list Disney before Pixar.
 const known=(ids||[]).map(studioById).filter(Boolean),studio=known.find(s=>s.within&&known.some(x=>x.id===s.within))||known[0];if(!studio)return null;
 const badge=document.createElement('span');badge.className='studio-badge studio-'+studio.id;badge.innerHTML=mark(studio.id);
 badge.title=studio.name;badge.setAttribute('aria-label','Студія / платформа: '+studio.name);badge.dataset.studio=studio.id;return badge;
}

