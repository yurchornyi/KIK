import {mkdir,writeFile} from 'node:fs/promises';import {AVATARS,avatarSvg} from './public/avatars.js';
await mkdir(new URL('./dist/avatars/',import.meta.url),{recursive:true});await Promise.all(AVATARS.map(a=>writeFile(new URL('./dist/avatars/'+a.id+'.svg',import.meta.url),avatarSvg(a.id))));
