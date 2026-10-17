"""Aggregate-only snapshot; no rows, credentials or password hashes are exported."""
import json,re,subprocess,sys
db=sys.argv[1]
assert db in ['fitness_db','fitchain_stage_mobile_20261017']
def sql(query):
 return subprocess.check_output(['runuser','-u','postgres','--','/usr/pgsql-18/bin/psql','-X','-qAt','-v','ON_ERROR_STOP=1','-d',db,'-c',query],universal_newlines=True).strip()
tables=sql("select tablename from pg_tables where schemaname='public' order by tablename").splitlines()
state={}
for table in tables:
 assert re.fullmatch('[a-z_]+',table)
 value=sql('select json_build_object(\'count\',count(*),\'digest\',md5(coalesce(string_agg(to_jsonb(t)::text,\'\' order by to_jsonb(t)::text),\'\'))) from "'+table+'" t')
 state[table]=json.loads(value)
print(json.dumps({'database':db,'tables':state},sort_keys=True))
