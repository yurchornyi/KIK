/** Original vector avatars, shared by profile forms and the server whitelist. */
const entries=[['cat','Кіт','#9d544b'],['fox','Лис','#29747a'],['panda','Панда','#657eb1'],['robot','Робот','#735397'],['astronaut','Космонавт','#385c8a'],['owl','Сова','#53766c'],['dog','Пес','#a5744e'],['ninja','Ніндзя','#7f566f'],['alien','Прибулець','#525b91'],['bear','Ведмідь','#4d7690'],['penguin','Пінгвін','#6b6b9e'],['rabbit','Кролик','#94647e'],['tiger','Тигр','#995f36'],['lion','Лев','#a97b35'],['koala','Коала','#4e7d81'],['raccoon','Єнот','#696690'],['dragon','Дракон','#537d51'],['dinosaur','Динозавр','#3b8076'],['unicorn','Єдиноріг','#8b609e'],['shark','Акула','#376e9a'],['octopus','Восьминіг','#9c5984'],['whale','Кит','#4b669c'],['sloth','Лінивець','#8b7559'],['otter','Видра','#577e78']];
export const AVATARS=entries.map(([id,name,color])=>({id,name,color}));export const validAvatar=id=>AVATARS.some(a=>a.id===id);export const avatarUrl=id=>'/avatars/'+(validAvatar(id)?id:'bear')+'.svg';
export function avatarSvg(id){const a=AVATARS.find(a=>a.id===id)||AVATARS[9],i=AVATARS.indexOf(a);const c=(x,y,r,color)=>`<circle cx="${x}" cy="${y}" r="${r}" fill="${color}"/>`,p=(d,color)=>`<path d="${d}" fill="${color}"/>`;let art='';const ink='#202536',cream='#ffe6c6',white='#fffaf2',face=['#efbd70','#e99455',white,'#b9dce2','#e8b581','#b3a0d8',cream,ink,'#a7dcb0','#c6946e',ink,cream,'#eeaa54','#dbaa60','#a9b8c6','#a7a6ac','#92c89a','#83be9b',cream,'#8bb1d0','#da9fc8','#8eb4db','#cbb396','#b89474'][i];
 if([0,1,5,12,16].includes(i))art+=p('M23 43L20 13L45 31M55 31L80 13L77 43',face);
 if([2,6,9,13,14,15,22,23].includes(i))art+=c(27,29,14,i===2?ink:face)+c(73,29,14,i===2?ink:face);
 if(i===11)art+=`<ellipse cx="35" cy="28" rx="8" ry="24" fill="${cream}"/><ellipse cx="65" cy="28" rx="8" ry="24" fill="${cream}"/>`;
 if(i===13)art+=c(50,54,40,'#8e603a');if(i===4)art+=c(50,47,36,'#dbe7ed')+c(50,47,29,ink);
 if(i===17)art+=p('M20 73L12 50L23 41L17 24L36 24L50 13L64 26L81 24L80 73',face);
 if(i===18)art+=p('M44 33L51 6L59 33','#efcf87');
 if(i===19)art+=p('M22 61L8 46L26 45L50 12L75 47L91 48L78 64',face);
 if(i===20)for(let j=0;j<5;j++)art+=`<path d="M${28+j*11} 65q-8 25-14 11" fill="none" stroke="${face}" stroke-width="10" stroke-linecap="round"/>`;
 if(i===21)art+=p('M73 60Q97 32 89 70Q96 80 81 82L63 79',face);
 art+=`<rect x="22" y="29" width="56" height="54" rx="25" fill="${face}"/>`;
 if([2,15,22].includes(i))art+=`<ellipse cx="37" cy="49" rx="12" ry="13" fill="${i===22?'#94775f':ink}"/><ellipse cx="63" cy="49" rx="12" ry="13" fill="${i===22?'#94775f':ink}"/>`;
 if(i===1)art+=p('M23 53L41 57L50 77L59 57L77 53L50 84',cream);
 if(i===3)art+=`<rect x="27" y="39" width="46" height="24" rx="9" fill="${ink}"/><path d="M50 27V14" stroke="${white}" stroke-width="3"/>`+c(50,12,5,'#f0bd69');
 if(i===7)art+=`<rect x="20" y="39" width="60" height="18" rx="5" fill="#e385a0"/>`;
 if(i===10)art+=`<ellipse cx="50" cy="67" rx="20" ry="21" fill="${white}"/>`;
 if(i===12)art+=p('M23 38L38 43L23 48M77 38L62 43L77 48M47 30L50 43L54 30',ink);
 if(i===16)art+=p('M27 37L21 19L40 30M61 30L79 19L73 39','#efcf87');
 const eyeColor=[2,3,7,15].includes(i)?white:ink;art+=c(39,49,3.5,eyeColor)+c(61,49,3.5,eyeColor);
 if(i===8)art+=`<ellipse cx="38" cy="49" rx="6" ry="9" fill="${ink}"/><ellipse cx="62" cy="49" rx="6" ry="9" fill="${ink}"/>`;
 if(i===19)art+=p('M35 66L65 66L60 75L55 68L50 75L45 68L40 75',white);else art+=`<path d="M43 68q7 8 14 0" fill="none" stroke="${ink}" stroke-width="2.5" stroke-linecap="round"/>`;
 return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><linearGradient id="bg" x2="1" y2="1"><stop stop-color="${a.color}"/><stop offset="1" stop-color="#202331"/></linearGradient></defs><rect width="100" height="100" rx="20" fill="url(#bg)"/>${c(12,8,36,'#ffffff10')}${art}</svg>`;}
