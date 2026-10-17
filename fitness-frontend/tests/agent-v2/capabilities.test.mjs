import test from 'node:test'
import assert from 'node:assert/strict'
import {loadAgentVersion} from '../../src/agent/capabilities.mjs'
test('legacy fallback requires an explicit old version or missing endpoint',async()=>{
 assert.equal(await loadAgentVersion(async()=>({version:2})),2)
 assert.equal(await loadAgentVersion(async()=>({version:1})),1)
 assert.equal(await loadAgentVersion(async()=>{throw {httpStatus:404}}),1)
 for(const failure of [{httpStatus:500},{httpStatus:401},{httpStatus:403},Error('network unavailable')])await assert.rejects(loadAgentVersion(async()=>{throw failure}))
 await assert.rejects(loadAgentVersion(async()=>({version:7})))
})
