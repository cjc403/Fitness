import { mkdir, lstat, realpath, open, readFile } from 'node:fs/promises';
import path from 'node:path';
import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { AgentError, dateRange } from './policy.mjs';

const cell=v=>'"'+String(v??'').replace(/^[\s\u0000-\u001f]*[=+@-]/,m=>"'"+m).replaceAll('"','""')+'"';
async function noLinks(absolute){
 const parsed=path.parse(absolute);let cursor=parsed.root;
 for(const part of absolute.slice(parsed.root.length).split(path.sep).filter(Boolean)){
  cursor=path.join(cursor,part);try{if((await lstat(cursor)).isSymbolicLink())throw new AgentError('成果目录不能包含链接');}catch(e){if(e.code!=='ENOENT')throw e;}
 }
}
const fixedOpen=file=>new Promise((resolve,reject)=>{
 if(process.platform!=='win32')return reject(new AgentError('当前系统未配置安全文件查看器'));
 const proc=spawn(path.join(process.env.SystemRoot||'C:\\Windows','System32','notepad.exe'),[file],{detached:true,stdio:'ignore',windowsHide:false});proc.once('error',reject);proc.once('spawn',()=>{proc.unref();resolve();});
});
export class ArtifactStore{
 constructor(root,{openFile=fixedOpen}={}){this.root=path.resolve(root);this.openFile=openFile;this.entries=new Map();}
 async safeRoot(){await noLinks(this.root);await mkdir(this.root,{recursive:true});await noLinks(this.root);const canonical=await realpath(this.root);if(canonical.toLowerCase()!==this.root.toLowerCase())throw new AgentError('成果目录真实路径发生变化');return canonical;}
 async schedule(taskId,rows,from,to,beforeEffect=async()=>{}){
  if(!/^[A-Za-z0-9_-]{1,80}$/.test(taskId))throw new AgentError('任务标识不正确');dateRange(from,to);
  if(!Array.isArray(rows)||rows.length>5000)throw new AgentError('课表返回格式不正确或数据过多');
  const root=await this.safeRoot(),id=randomUUID(),name=`FitChain课表_${from}_${to}_${id.slice(0,8)}.csv`,file=path.join(root,name);
  const csv='\uFEFF'+[['课程','教练','开始时间','结束时间','状态'],...rows.map(r=>[r.courseName??r.course?.name,r.coachName,r.startTime??r.scheduledStartTime,r.endTime??r.scheduledEndTime,r.status])].map(r=>r.map(cell).join(',')).join('\r\n')+'\r\n';
  const data=Buffer.from(csv,'utf8');await beforeEffect();const handle=await open(file,'wx',0o600);try{await handle.writeFile(data);}finally{await handle.close();}
  await noLinks(file);const result={id,name,bytes:data.length,sha256:createHash('sha256').update(data).digest('hex'),path:file};this.entries.set(id,{taskId,result});return result;
 }
 async open(taskId,id,beforeEffect=async()=>{}){
  const e=this.entries.get(id);if(!e||e.taskId!==taskId)throw new AgentError('成果文件不属于当前任务',403);
  await this.safeRoot();await noLinks(e.result.path);if(path.dirname(e.result.path)!==this.root)throw new AgentError('成果文件路径不安全');
  const bytes=await readFile(e.result.path);if(createHash('sha256').update(bytes).digest('hex')!==e.result.sha256)throw new AgentError('成果文件已变化，请重新生成');
  await beforeEffect();await this.openFile(e.result.path);return '已请求 Windows 打开文件；尚未验证文件窗口';
 }
}
