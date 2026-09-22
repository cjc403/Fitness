#!/usr/bin/env python3
"""Isolated PostgreSQL staging acceptance. Never mutate production; never print tokens/secrets."""
import argparse, base64, datetime as dt, hashlib, hmac, json, os, subprocess, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

parser=argparse.ArgumentParser()
parser.add_argument('--db', required=True)
parser.add_argument('--port',type=int,default=18080)
parser.add_argument('--readonly',action='store_true')
args=parser.parse_args()
if not args.readonly:
    assert (args.db,args.port)==('fitchain_stage_pt_20260917',18080), 'Mutation requires the exact staging database AND staging API port'
else:
    assert (args.db,args.port) in (('fitness_db',8080),('fitchain_stage_pt_20260917',18080)), 'Read-only target mismatch'

def sql(query):
    result=subprocess.run(['runuser','-u','postgres','--','/usr/pgsql-18/bin/psql','-X','-v','ON_ERROR_STOP=1','-d',args.db,'-Atc',query],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
    return result.stdout.strip()

def b64(data):return base64.urlsafe_b64encode(data).rstrip(b'=')
def token(username):
    row=json.loads(sql("SELECT row_to_json(t) FROM (SELECT u.id,u.username,r.code,u.venue_id FROM sys_user u JOIN role r ON r.id=u.role_id WHERE u.username='"+username+"')t"))
    secret=os.environ['JWT_SECRET'].encode()
    algorithm='HS512' if len(secret)>=64 else 'HS384' if len(secret)>=48 else 'HS256'
    digest={'HS512':hashlib.sha512,'HS384':hashlib.sha384,'HS256':hashlib.sha256}[algorithm]
    head=b64(json.dumps({'alg':algorithm,'typ':'JWT'}).encode())
    body=b64(json.dumps({'sub':str(row['id']),'username':row['username'],'role':row['code'],'organizationId':row['venue_id'],'iat':int(time.time()),'exp':int(time.time())+600}).encode())
    message=head+b'.'+body
    return (message+b'.'+b64(hmac.new(secret,message,digest).digest())).decode()

def request(path,who='admin',method='GET',body=None,expected_http=200,success=True):
    data=None if body is None else json.dumps(body).encode()
    req=urllib.request.Request('http://127.0.0.1:'+str(args.port)+'/api/v1'+path,data=data,method=method,headers={'Authorization':'Bearer '+token(who),'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(req,timeout=15) as response:status=response.status;result=json.load(response)
    except urllib.error.HTTPError as error:status=error.code;result=json.load(error)
    assert status==expected_http,(path,status)
    if success:assert result.get('code')==0,(path,result.get('code'),result.get('message'))
    return result.get('data') if success else result

# These check authorization against real active accounts; actual administrator password login is checked separately.
for who in ('admin','manager','coach','reception','member'):
    request('/training/lessons',who)
    if who!='member':request('/training/commission-rules',who);request('/training/settlements',who)
    if who in ('coach','reception','member'):request('/training/settlements/1/approve',who,'POST',{},403,False)
print('Five-role endpoint and restricted-operation checks passed')
if args.readonly:
    print('Read-only production acceptance passed; no business mutations performed')
    raise SystemExit(0)

member=json.loads(sql("SELECT row_to_json(t) FROM (SELECT m.id,m.venue_id FROM member m JOIN sys_user u ON u.id=m.user_id WHERE u.username='member')t"))
coach=int(sql("SELECT id FROM sys_user WHERE username='coach'"))
product=request('/commercial/products')
package=next(p for p in product if p['productType']=='PRIVATE_PACKAGE' and p['venueId']==member['venue_id'] and p['status']=='ACTIVE')
order=request('/commercial/orders','admin','POST',{'memberId':member['id'],'productId':package['id'],'paymentMethod':'CASH','remark':'ISOLATED staging acceptance only'})
credits=request('/commercial/entitlements?memberId='+str(member['id']))
credit=next(e for e in credits if e['sourceOrderId']==order['id'])
cid=int(sql("INSERT INTO course(name,type,duration_minutes,max_capacity,fee,venue_id,status) VALUES('ISOLATED PT validation','PRIVATE',60,1,999,"+str(member['venue_id'])+",'ACTIVE') RETURNING id" ).splitlines()[0])
sid=int(sql("INSERT INTO course_schedule(course_id,coach_id,coach_name,venue_id,scheduled_start_time,scheduled_end_time,max_students,enrolled_count,status) VALUES("+str(cid)+","+str(coach)+",'Staging coach',"+str(member['venue_id'])+",CURRENT_TIMESTAMP+INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '1 day 1 hour',1,0,'SCHEDULED') RETURNING id").splitlines()[0])
request('/member/bookings','member','POST',{'scheduleId':sid,'entitlementId':credit['id']})
# The existing member booking endpoint intentionally returns Result<Void>.
bid=int(sql('SELECT id FROM booking WHERE schedule_id='+str(sid)+' AND member_id='+str(member['id'])))
row=next(e for e in request('/commercial/me/entitlements','member') if e['id']==credit['id'])
assert row['reservedUses']==1 and row['remainingUses']==credit['remainingUses']
for action,body in (('freeze',{'note':'staging'}),('refund',{'amount':1,'paymentMethod':'CASH','reason':'staging'})):
    assert request('/commercial/entitlements/'+str(credit['id'])+'/'+action,'admin','POST',body,200,False)['code']!=0
sql("UPDATE course_schedule SET scheduled_start_time=CURRENT_TIMESTAMP-INTERVAL '2 hours',scheduled_end_time=CURRENT_TIMESTAMP-INTERVAL '1 hour' WHERE id="+str(sid))
request('/admin/bookings/'+str(bid)+'/check-in','coach','POST',{})
request('/training/lessons/'+str(bid)+'/submit','coach','POST',{})
with ThreadPoolExecutor(max_workers=2) as pool:
    results=list(pool.map(lambda _:request('/training/lessons/'+str(bid)+'/confirm','member','POST',{}),range(2)))
assert all(r['status']=='CONFIRMED' and r['commissionAmount'] is None for r in results)
row=next(e for e in request('/commercial/me/entitlements','member') if e['id']==credit['id'])
assert row['reservedUses']==0 and row['remainingUses']==credit['remainingUses']-1
lesson=next(l for l in request('/training/lessons','coach') if l['bookingId']==bid)
assert round(lesson['unitPrice'],2)==round(order['paidAmount']/package['totalUses'],2)
assert sql("SELECT count(*) FROM entitlement_operation WHERE operation_type='PT_CONSUME' AND entitlement_id="+str(credit['id']))=='1'
month=sql("SELECT to_char(confirmed_at,'YYYY-MM') FROM private_lesson WHERE booking_id="+str(bid))
sheet=request('/training/settlements','manager','POST',{'coachId':coach,'month':month})
assert request('/training/settlements/'+str(sheet['id'])+'/approve','manager','POST',{},200,False)['code']!=0
assert request('/training/lessons/'+str(bid)+'/reverse','manager','POST',{'reason':'staging settled prohibition'},200,False)['code']!=0
prior=sql("SELECT to_char(CURRENT_DATE-n*INTERVAL '1 month','YYYY-MM') FROM generate_series(1,120)n WHERE NOT EXISTS (SELECT 1 FROM coach_settlement WHERE coach_id="+str(coach)+" AND venue_id="+str(member['venue_id'])+" AND settlement_month=to_char(CURRENT_DATE-n*INTERVAL '1 month','YYYY-MM')) ORDER BY n LIMIT 1")
assert prior, 'No isolated historical fixture month available'
sql("UPDATE coach_settlement SET settlement_month='"+prior+"' WHERE id="+str(sheet['id']))
approved=request('/training/settlements/'+str(sheet['id'])+'/approve','manager','POST',{})
assert approved['status']=='APPROVED'
approval_snapshot=sql('SELECT approved_at::text FROM coach_settlement WHERE id='+str(sheet['id']))
again=request('/training/settlements/'+str(sheet['id'])+'/approve','manager','POST',{})
assert again['status']=='APPROVED'
# PostgreSQL stores microseconds; compare persisted values rather than the first in-memory nanosecond response.
assert sql('SELECT approved_at::text FROM coach_settlement WHERE id='+str(sheet['id']))==approval_snapshot
print('PostgreSQL package sale, reservation, safeguards, concurrent member signature, exactly-once consumption and monthly approval passed')
