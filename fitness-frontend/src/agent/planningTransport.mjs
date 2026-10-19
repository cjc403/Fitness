// Only the scoped planner has identical-response replay. Business commands never retry here.
export async function fetchPlanningTurn(input,init={},options={}){
 const fetcher=options.fetcher||globalThis.fetch
 const planning=typeof input==='string'&&/^\/api\/v1\/agent\/tasks\/[^/?#]+\/next$/.test(input)&&init.method==='POST'&&typeof init.body==='string'
 if(!planning)return fetcher.call(globalThis,input,init)
 const canRetry=()=>!init.signal?.aborted&&options.canRetry?.()!==false
 const delay=Math.max(0,Math.min(1000,options.retryDelay??300))
 const wait=()=>new Promise((resolve,reject)=>{
  const aborted=()=>{clearTimeout(timer);init.signal?.removeEventListener('abort',aborted);reject(init.signal?.reason||new DOMException('Planning request cancelled','AbortError'))}
  const timer=setTimeout(()=>{init.signal?.removeEventListener('abort',aborted);resolve()},delay)
  init.signal?.addEventListener('abort',aborted,{once:true});if(init.signal?.aborted)aborted()
 })
 for(let attempt=0;attempt<3;attempt++){
  if(attempt>0&&!canRetry())throw init.signal?.reason||new DOMException('Planning request no longer active','AbortError')
  let response
  try{response=await fetcher.call(globalThis,input,init)}catch(error){
   if(!(error instanceof TypeError)||attempt===2||!canRetry())throw error
   await wait();continue
  }
  if([502,503,504].includes(response.status)&&attempt<2&&canRetry()){
   try{await response.body?.cancel?.()}catch{}
   await wait();continue
  }
  return response
 }
}
