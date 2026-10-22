import {mkdir,copyFile,cp} from 'node:fs/promises';
await mkdir('dist',{recursive:true});await cp('public','dist',{recursive:true});await copyFile('node_modules/hls.js/dist/hls.min.js','dist/hls.min.js');await copyFile('node_modules/hls.js/LICENSE','dist/hls.LICENSE.txt');
