// User supplied identifiers remain local; the planner receives opaque task-scoped references.
export class UserData {
 constructor(){this.values=new Map();}
 protect(text){return String(text).replace(/(?<!\d)\d{17}[0-9Xx](?!\d)|(?<!\d)1[3-9]\d{9}(?!\d)|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g,value=>{
  for(const [key,stored] of this.values)if(stored===value)return key;
  const key=`{{USER_DATA_${this.values.size+1}}}`;this.values.set(key,value);return key;
 });}
 resolve(text){return String(text).replace(/\{\{USER_DATA_\d+\}\}/g,key=>{if(!this.values.has(key))throw new Error('用户资料引用不属于当前任务');return this.values.get(key);});}
 clear(){this.values.clear();}
}
