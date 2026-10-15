import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveConfig } from 'vite'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const root=fileURLToPath(new URL('../',import.meta.url))
const configFile=fileURLToPath(new URL('../vite.config.ts',import.meta.url))
const config=(port,extra={})=>resolveConfig({root,configFile,logLevel:'silent',server:{port},...extra},'serve')

test('desktop and mobile dev servers cannot overwrite each others dependency cache',async()=>{
  const desktop=await config(5173),mobile=await config(5174)
  assert.notEqual(desktop.cacheDir,mobile.cacheDir)
})
test('build cache is separate from both live development entries',async()=>{
  const desktop=await config(5173),mobile=await config(5174)
  const build=await resolveConfig({root,configFile,logLevel:'silent'},'build')
  assert.notEqual(build.cacheDir,desktop.cacheDir);assert.notEqual(build.cacheDir,mobile.cacheDir)
})
test('isolated test harness cache overrides remain respected',async()=>{
  const path=join(tmpdir(),'fitchain-explicit-test-cache')
  const resolved=await config(5173,{cacheDir:path})
  assert.equal(resolved.cacheDir.replaceAll('\\','/'),path.replaceAll('\\','/'))
})
