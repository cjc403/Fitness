import test from 'node:test'
import assert from 'node:assert/strict'
import { summarizePageContext, chooseSemanticAction } from '../../src/agent/pageContext.mjs'

test('a bound entity is chosen by id and venue instead of repeated button text',()=>{
  const context={entities:[{type:'course',id:'1',venueId:'1',label:'流瑜伽',actions:[{action:'edit',ref:'wrong'}]},{type:'course',id:'2',venueId:'2',label:'流瑜伽',actions:[{action:'edit',ref:'right'}]}],fields:[],errors:[]}
  assert.equal(chooseSemanticAction(context,{kind:'entity_action',entityType:'course',id:'2',venueId:'2',action:'edit'}).arguments.ref,'right')
  assert.equal(chooseSemanticAction(context,{kind:'entity_action',entityType:'course',label:'流瑜伽',action:'edit'}),null)
})
test('semantic field filling skips values already achieved and never guesses duplicate fields',()=>{
  const context={fields:[{key:'fee',name:'价格',value:'99',ref:'fee',writable:true}],errors:[]}
  assert.deepEqual(chooseSemanticAction(context,{kind:'fill_field',field:'fee',value:'99'}),{action:'observe',arguments:{},alreadySatisfied:true})
  assert.equal(chooseSemanticAction(context,{kind:'fill_field',field:'fee',value:'109'}).action,'fill')
  context.fields.push({...context.fields[0],ref:'fee2'})
  assert.equal(chooseSemanticAction(context,{kind:'fill_field',field:'fee',value:'109'}),null)
})
test('context summaries retain validation and truncation without logging private values',()=>{
  const summary=summarizePageContext({scope:{title:'编辑课程'},entities:[],fields:[{name:'手机号',value:'13812345678'}],errors:['时间已被占用'],truncated:true})
  assert.ok(summary.includes('时间已被占用'));assert.ok(summary.includes('截断'));assert.ok(!summary.includes('13812345678'))
})
test('semantic radio selection clicks the requested choice and never types into it',()=>{
 const context={fields:[{key:'mode',name:'力量',value:'STRENGTH',ref:'strength',writable:true,controlType:'radio',checked:true},{key:'mode',name:'时长',value:'DURATION',ref:'duration',writable:true,controlType:'radio',checked:false}]}
 assert.deepEqual(chooseSemanticAction(context,{kind:'fill_field',field:'mode',value:'DURATION'}),{action:'click',arguments:{ref:'duration'}})
})
test('project training goal aliases select the visible business option',()=>{
 const context={fields:[{key:'goal',name:'训练目标',value:'综合训练',ref:'goal',writable:true,controlType:'combobox'}]}
 assert.deepEqual(chooseSemanticAction(context,{kind:'fill_field',field:'goal',value:'综合体能'}),{action:'select_option',arguments:{ref:'goal',value:'体能'}})
})
test('a prospect willing to try maps to the actual interested follow-up option',()=>{
 const context={fields:[{key:'outcome',name:'跟进结果',value:'已联系',ref:'result',controlType:'combobox',writable:true}]}
 assert.equal(chooseSemanticAction(context,{kind:'fill_field',field:'outcome',value:'愿意体验'}).arguments.value,'有意向')
 context.fields[0].value='有意向';assert.equal(chooseSemanticAction(context,{kind:'fill_field',field:'outcome',value:'愿意体验'}).alreadySatisfied,true)
})
