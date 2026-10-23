import {mkdir,copyFile,cp,rm,readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
await rm('dist',{recursive:true,force:true});await mkdir('dist',{recursive:true});await cp('public','dist',{recursive:true});await copyFile('node_modules/hls.js/dist/hls.min.js','dist/hls.min.js');await copyFile('node_modules/hls.js/LICENSE','dist/hls.LICENSE.txt');

await import('./avatar-assets.mjs');

// Fingerprinted URLs (?v=<content hash>) let the server cache scripts and styles for a year: a deploy changes the URL.
// A module's hash covers its imports' URLs, so a changed dependency also renames every module that loads it.
const versions=new Map(),graph=new Map(),binary=new Map();
const fingerprint=text=>createHash('sha256').update(text).digest('hex').slice(0,10);
async function version(name){
 if(versions.has(name))return versions.get(name);
 let text=await readFile('dist/'+name,'utf8');const deps=[];
 if(name.endsWith('.js')){
  const imports=[...text.matchAll(/(\bfrom\s*|\bimport\s*\(\s*)'\.\/([\w.-]+\.js)'/g)].map(m=>m[2]);
  const urls=new Map();for(const dep of new Set(imports)){deps.push(dep);urls.set(dep,await version(dep));}
  text=text.replace(/(\bfrom\s*|\bimport\s*\(\s*)'\.\/([\w.-]+\.js)'/g,(m,lead,dep)=>`${lead}'./${dep}?v=${urls.get(dep)}'`);
  text=text.replace(/'\/(hls\.min\.js|tmdb-logo\.svg)'/g,(m,file)=>`'/${file}?v=${binary.get(file)}'`);
  await writeFile('dist/'+name,text);
 }
 const v=fingerprint(text);versions.set(name,v);graph.set(name,deps);return v;
}
for(const file of ['hls.min.js','tmdb-logo.svg','logo.svg'])binary.set(file,fingerprint(await readFile('dist/'+file)));
const closure=(name,seen=new Set())=>{for(const dep of graph.get(name)||[])if(!seen.has(dep)){seen.add(dep);closure(dep,seen);}return seen;};
for(const page of ['index.html','sources.html']){
 let html=await readFile('dist/'+page,'utf8');
 for(const [,file] of html.matchAll(/(?:href|src)="\/([\w.-]+\.(?:js|css))"/g))await version(file);
 html=html.replace(/((?:href|src)=")\/([\w.-]+\.(js|css|svg))"/g,(m,lead,file)=>versions.has(file)||binary.has(file)?`${lead}/${file}?v=${versions.get(file)||binary.get(file)}"`:m);
 // The browser fetches every module of the app at once instead of discovering them import by import.
 if(page==='index.html'&&versions.has('app.js'))html=html.replace('</head>',[...closure('app.js')].map(dep=>`<link rel="modulepreload" href="/${dep}?v=${versions.get(dep)}">`).join('')+'</head>');
 await writeFile('dist/'+page,html);
}
