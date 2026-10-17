export async function loadAgentVersion(probe){
 try{
  const result=await probe()
  if(result?.version===1||result?.version===2)return result.version
  throw Error('网页任务能力版本无法识别，请刷新或检查服务版本')
 }catch(error){
  if(error?.httpStatus===404)return 1
  throw error
 }
}
