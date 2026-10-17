import test from 'node:test'
import assert from 'node:assert/strict'
import {frozenRequestTarget} from '../../src/agent/requestGate.mjs'
test('cancel reason is part of the actual frozen target and other query parameters are rejected',()=>{
 const value=frozenRequestTarget('/api/v1/admin/bookings/7/cancel',{reason:'后台取消'});assert.equal(new URL(value,'http://local').searchParams.get('reason'),'后台取消')
 assert.throws(()=>frozenRequestTarget('/api/v1/admin/courses/1',{reason:'x'}));assert.throws(()=>frozenRequestTarget('/api/v1/admin/bookings/7/cancel',{venueId:2}));assert.throws(()=>frozenRequestTarget('/api/v1/admin/bookings/7/cancel?reason=x',{reason:'y'}))
})
