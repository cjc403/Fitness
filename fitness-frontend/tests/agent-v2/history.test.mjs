import test from 'node:test'
import assert from 'node:assert/strict'
import {historyTask,isExecutableTask} from '../../src/agent/taskHistory.mjs'
test('a task from an older login is visible but cannot execute, poll or block a new task',()=>{
 const t=historyTask({id:'old',version:3,status:'RUNNING',goal:'历史目标'},true)
 assert.equal(t.readOnly,true);assert.equal(t.restorable,false);assert.equal(isExecutableTask(t),false);assert.equal(t.goal,'历史目标')
 const own=historyTask({id:'same',version:2,status:'RUNNING'},false);assert.equal(own.restorable,true);assert.equal(isExecutableTask(own),true)
})
