/** Browser storage can throw in private mode, when blocked or when its quota is full: account, settings and playback keep working. */
export function createStorage(getStorage=()=>globalThis.localStorage){
 return {
  getItem(key){try{return getStorage().getItem(key);}catch{return null;}},
  setItem(key,value){try{getStorage().setItem(key,value);return true;}catch{return false;}},
  removeItem(key){try{getStorage().removeItem(key);return true;}catch{return false;}},
  keys(){try{return Object.keys(getStorage());}catch{return [];}}
 };
}
export const storage=createStorage();
