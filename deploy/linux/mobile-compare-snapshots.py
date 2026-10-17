import json,sys
a,b=[json.load(open(p))['tables'] for p in sys.argv[1:]]
# The migration ledger advances; every legacy business/account/permission row is preserved.
ignored={'flyway_schema_history'}
checked=[]
for name,state in a.items():
 if name in ignored:continue
 if b.get(name)!=state:raise SystemExit('Legacy table changed: '+name)
 checked.append(name)
print(json.dumps({'legacyTablesUnchanged':checked,'newTables':sorted(set(b)-set(a))}))
