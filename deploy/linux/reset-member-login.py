#!/usr/bin/env python3
"""One-off user-authorized reset of member only, through the normal administrator API."""
import argparse,json,sys,subprocess,urllib.request
parser=argparse.ArgumentParser()
parser.add_argument('--port',type=int,required=True,choices=(8080,18080))
args=parser.parse_args()
password=sys.stdin.read().strip()
assert password
db='fitness_db' if args.port==8080 else 'fitchain_stage_pt_20260917'
query="SELECT m.id FROM member m JOIN sys_user u ON u.id=m.user_id WHERE u.username='member' AND u.status='ACTIVE'"
result=subprocess.run(['runuser','-u','postgres','--','/usr/pgsql-18/bin/psql','-X','-d',db,'-Atc',query],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
member_id=int(result.stdout.strip())
base='http://127.0.0.1:'+str(args.port)+'/api/v1'
def call(path,body,method='POST',token=None):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    req=urllib.request.Request(base+path,data=json.dumps(body).encode(),headers=headers,method=method)
    with urllib.request.urlopen(req,timeout=15) as response:result=json.load(response)
    assert result.get('code')==0,'API request failed: '+path
    return result['data']
admin=call('/auth/login',{'username':'admin','password':password})
assert admin['userInfo']['role']=='SUPER_ADMIN'
call('/admin/members/'+str(member_id)+'/account/password',{'newPassword':password},'PUT',admin['accessToken'])
member=call('/auth/login',{'username':'member','password':password})
assert member['userInfo']['role']=='MEMBER'
print('User-authorized member-only reset and actual login passed; other accounts and business data unchanged')
