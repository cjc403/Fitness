const active=['RUNNING','PAUSED','WAITING_CONFIRMATION','WAITING_INPUT']
export const isExecutableTask=t=>!!t&&!t.readOnly&&active.includes(t.status)
export function historyTask(t,readOnly=false){
 const unfinished=!['SUCCEEDED','FAILED','PARTIAL','CANCELLED'].includes(t.status)
 return {...t,remoteVersion:t.version,durable:true,readOnly,restorable:unfinished&&!readOnly,status:unfinished?'PAUSED':t.status,steps:[],artifacts:[],pauseReason:'USER'}
}
