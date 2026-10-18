import test from 'node:test'
import assert from 'node:assert/strict'
import {cases} from './case-catalog.mjs'
import {acceptanceGate,coreIds} from './evaluate.mjs'
const complete=()=>Array.from({length:3},()=>({cases:cases.map(({id,role,kind,split})=>({id,role,kind,split,status:kind==='deny'?'DENIED':'SUCCEEDED',grade:{id,role,kind,split,passed:true}})),groups:[{pageErrors:[],blocked:[]}]}))
test('core selection is frozen to four positive development cases per role',()=>{
 assert.equal(coreIds.length,24);assert.equal(new Set(coreIds).size,24)
 for(const role of new Set(cases.map(c=>c.role))){const selected=cases.filter(c=>coreIds.includes(c.id)&&c.role===role);assert.equal(selected.length,4);assert.ok(selected.every(c=>c.kind!=='deny'&&c.split==='dev'))}
})
test('three complete rounds pass; the promised combined gate permits one non-core failure in a round',()=>{
 const rounds=complete(),c=rounds[0].cases.find(c=>!coreIds.includes(c.id)&&c.split==='dev'&&c.kind!=='deny');c.status='FAILED';c.grade.passed=false
 const result=acceptanceGate(rounds,true);assert.equal(result.accepted,true);assert.equal(result.summary.positive.passed,323);assert.equal(result.core.passed,72)
})
test('core failure, missing or duplicate case, denial failure, page error and false success each block acceptance',()=>{
 for(const mutate of [r=>{r[0].cases.find(c=>coreIds.includes(c.id)).grade.passed=false},r=>r[0].cases.pop(),r=>r[0].cases[1]=r[0].cases[0],r=>{r[0].cases.find(c=>c.kind==='deny').grade.passed=false},r=>r[0].groups[0].pageErrors.push('unhandled'),r=>{const c=r[0].cases.find(c=>!coreIds.includes(c.id)&&c.kind!=='deny');c.grade.passed=false}]){
  const rounds=complete();mutate(rounds);assert.equal(acceptanceGate(rounds,true).accepted,false)
 }
 assert.equal(acceptanceGate(complete().slice(0,2),true).accepted,false);assert.equal(acceptanceGate(complete(),false).accepted,false)
})
