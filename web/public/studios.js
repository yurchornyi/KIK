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
/** One emblem per studio, drawn in KIK's own hand (no brand files): a white 24-unit pictogram beside a short wordmark. */
const GLYPHS={
 netflix:'<path d="M4 2h4.5v20H4zM15.5 2H20v20h-4.5zM4 2h4.5l7 20h-4.5z"/>',
 apple:'<path d="M12 6.4c-2.3-1.9-5.8-1.2-7.2 1.5-2 3.7.1 9.8 3.5 11.8 1.2.7 2.2.2 3.7.2s2.5.5 3.7-.2c3.4-2 5.5-8.1 3.5-11.8-1.4-2.7-4.9-3.4-7.2-1.5z"/><path d="M12.4 5.6c.2-1.9 1.5-3.2 3.3-3.4 0 1.9-1.5 3.4-3.3 3.4z"/>',
 prime:'<path d="M3.5 14.5c4.8 4.2 12.5 4.3 17.5.3" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/><path d="M17.3 12.2l4.2 1.1-1.3 4.1" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>',
 paramount:'<path d="M3.5 20.5 12 6l8.5 14.5z"/><path d="M9.6 11.4 12 6l2.4 5.4-2.4 1.6z" fill="#111"/><circle cx="5" cy="9.2" r="1.4"/><circle cx="8" cy="5.4" r="1.4"/><circle cx="12" cy="2.6" r="1.4"/><circle cx="16" cy="5.4" r="1.4"/><circle cx="19" cy="9.2" r="1.4"/>',
 warner:'<path d="M4.5 3h15v8.5c0 4.6-3 7.9-7.5 9.5C7.5 19.4 4.5 16.1 4.5 11.5z" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"/><path d="M8 8l1.6 7 2.4-5 2.4 5L16 8" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>'
};
const WORDS={netflix:'',apple:'tv+',hbo:'HBO',prime:'prime',disney:'Disney+',hulu:'hulu',paramount:'+',warner:'WB',pixar:'PIXAR'};
export function studioBadge(ids,document=globalThis.document){
 const studio=(ids||[]).map(studioById).find(Boolean);if(!studio)return null;
 const badge=document.createElement('span');badge.className='studio-badge studio-'+studio.id;
 if(GLYPHS[studio.id]){const glyph=document.createElement('span');glyph.className='studio-glyph';glyph.innerHTML='<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">'+GLYPHS[studio.id]+'</svg>';badge.append(glyph);}
 if(WORDS[studio.id]){const word=document.createElement('span');word.className='studio-word';word.textContent=WORDS[studio.id];badge.append(word);}
 badge.title=studio.name;badge.setAttribute('aria-label','Студія / платформа: '+studio.name);badge.dataset.studio=studio.id;return badge;
}
