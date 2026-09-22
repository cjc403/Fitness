#!/usr/bin/env python3
"""Read a common validation password from stdin; keep it and all tokens in memory only."""
import argparse,json,sys,urllib.request,urllib.error
parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8080);args=parser.parse_args()
assert args.port in (8080,18080)
password=sys.stdin.read().strip()
assert password, 'A validation password must be supplied through stdin'
for username,role,home in (
    ('admin','SUPER_ADMIN','/commercial/overview'),
    ('manager','MANAGER','/commercial/overview'),
    ('coach','COACH','/coach/workbench'),
    ('reception','RECEPTION','/commercial/products'),
    ('member','MEMBER','/member/profile')):
    base='http://127.0.0.1:'+str(args.port)+'/api/v1'
    req=urllib.request.Request(base+'/auth/login',data=json.dumps({'username':username,'password':password}).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=15) as response:body=json.load(response)
    assert body.get('code')==0,'Login failed for '+username
    data=body['data'];assert data['userInfo']['role']==role
    token=data['accessToken']
    if data['userInfo'].get('mustChangePassword'):
        req=urllib.request.Request(base+home,headers={'Authorization':'Bearer '+token})
        try:
            urllib.request.urlopen(req,timeout=15)
            raise AssertionError('Mandatory password-change gate was bypassed for '+username)
        except urllib.error.HTTPError as error:
            assert error.code==403
            result=json.load(error)
            assert result.get('code')==1105, 'Unexpected error instead of mandatory password-change gate'
        print(username+': actual password login passed; mandatory first-login password-change protection verified')
        continue
    for path in (home,'/training/lessons'):
        with urllib.request.urlopen(urllib.request.Request(base+path,headers={'Authorization':'Bearer '+token}),timeout=15) as response:result=json.load(response)
        assert result.get('code')==0,'Authorized endpoint failed for '+username+' '+path
    if role in ('COACH','RECEPTION','MEMBER'):
        req=urllib.request.Request(base+'/training/settlements/1/approve',data=b'{}',headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
        try:
            urllib.request.urlopen(req,timeout=15)
            raise AssertionError('Settlement approval unexpectedly allowed for '+username)
        except urllib.error.HTTPError as error:assert error.code==403
    print(username+': actual password login, '+role+' workbench and private-training authorization passed')
print('All five actual logins and applicable workbench/password-change gates verified; no business data mutated')
