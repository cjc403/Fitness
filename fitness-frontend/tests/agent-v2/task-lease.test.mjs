import test from 'node:test'
import assert from 'node:assert/strict'
import {createDocumentLeaseId} from '../../src/agent/taskLease.mjs'
test('HTTP H5 can create a document lease without secure-context randomUUID',()=>{
 let n=0;const fake={getRandomValues:a=>{a.fill(++n);return a}}
 const a=createDocumentLeaseId(fake),b=createDocumentLeaseId(fake);assert.match(a,/^[a-zA-Z0-9_-]{8,128}$/);assert.notEqual(a,b)
})
