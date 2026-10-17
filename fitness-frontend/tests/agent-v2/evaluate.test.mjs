import test from 'node:test'
import assert from 'node:assert/strict'
import { gradeCase,summarize } from './evaluate.mjs'
test('a 200 response for the wrong entity or changed extra field is a failure',()=>{
 const c={id:'price',role:'SUPER_ADMIN',kind:'write',expected:{path:'/courses',values:{id:2,fee:99}},confirmationPolicy:'ONE'}
 assert.equal(gradeCase(c,{status:'SUCCEEDED',path:'/courses',values:{id:1,fee:99},beforeWrites:0,afterWrites:1,sameDocument:true,duplicateRejected:true,forbiddenChanges:[]}).passed,false)
 assert.equal(gradeCase(c,{status:'SUCCEEDED',path:'/courses',values:{id:2,fee:99},beforeWrites:0,afterWrites:1,sameDocument:true,duplicateRejected:true,forbiddenChanges:['capacity']}).passed,false)
})
test('partial outcomes cannot be scored as full completion',()=>{assert.equal(gradeCase({id:'multi',kind:'multi',expected:{goals:2}},{status:'SUCCEEDED',sameDocument:true,verification:{complete:false,outcomes:[{status:'SATISFIED'},{status:'UNSATISFIED'}]}}).passed,false)})
test('denials are reported separately and cannot inflate positive completion',()=>{const a=gradeCase({id:'a',role:'COACH',kind:'deny'},{status:'WAITING_INPUT',afterWrites:0});const b=gradeCase({id:'b',role:'COACH',kind:'query'},{status:'FAILED'});const s=summarize([a,b]);assert.equal(s.positive.total,1);assert.equal(s.positive.passed,0);assert.equal(s.denials.passed,1)})
test('an incorrect confirmation is a hard failure and is never auto approved',()=>{assert.equal(gradeCase({id:'w',kind:'write',confirmationPolicy:'ONE',expected:{values:{fee:99}}},{status:'WAITING_CONFIRMATION',previewMatches:false,afterWrites:0}).passed,false)})
test('a success label without independently checked fixture evidence is not a pass',()=>{
 assert.equal(gradeCase({id:'q',kind:'query'},{status:'SUCCEEDED',sameDocument:true}).passed,false)
 assert.equal(gradeCase({id:'q',kind:'query',expected:{goals:1}},{status:'SUCCEEDED',sameDocument:true,verification:{complete:true,outcomes:[{status:'SATISFIED'}]}}).passed,false)
 assert.equal(gradeCase({id:'q',kind:'query',expected:{goals:1}},{status:'SUCCEEDED',sameDocument:true,evidenceChecked:true,verification:{complete:true,outcomes:[{status:'SATISFIED'}]}}).passed,true)
})
