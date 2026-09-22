#!/usr/bin/env python3
"""Explicit isolated stage mutations; production mode performs GET only."""
import argparse
import base64
import datetime as dt
import hashlib
import hmac
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor

parser = argparse.ArgumentParser()
parser.add_argument('--db', required=True)
parser.add_argument('--port', type=int, default=18080)
parser.add_argument('--readonly', action='store_true')
args = parser.parse_args()
stage = ('fitchain_stage_finance_20260917', 18080)
target = (args.db, args.port)
allowed = (target in (('fitness_db', 8080), stage)) if args.readonly else target == stage
if not allowed:
    parser.error('Target guard: exact isolated staging database AND port required for mutations; readonly target mismatch')


def sql(query):
    environment = os.environ.copy()
    if args.readonly:
        environment['PGOPTIONS'] = '-c default_transaction_read_only=on'
    result = subprocess.run(['runuser', '-u', 'postgres', '--', '/usr/pgsql-18/bin/psql',
                             '-X', '-v', 'ON_ERROR_STOP=1', '-d', args.db, '-Atc', query],
                            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            universal_newlines=True, env=environment)
    return result.stdout.strip()


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b'=')


def identity(who):
    assert who in ('admin', 'manager', 'coach', 'reception', 'member')
    return json.loads(sql("SELECT row_to_json(t) FROM (SELECT u.id,u.username,r.code,u.venue_id,u.must_change_password FROM sys_user u JOIN role r ON r.id=u.role_id WHERE u.username='" + who + "' AND u.status='ACTIVE') t"))


def token(who):
    row = identity(who)
    secret = os.environ['JWT_SECRET'].encode()
    algorithm = 'HS512' if len(secret) >= 64 else 'HS384' if len(secret) >= 48 else 'HS256'
    digest = {'HS512': hashlib.sha512, 'HS384': hashlib.sha384, 'HS256': hashlib.sha256}[algorithm]
    header = b64(json.dumps({'alg': algorithm, 'typ': 'JWT'}).encode())
    payload = b64(json.dumps({'sub': str(row['id']), 'username': row['username'],
                             'role': row['code'], 'organizationId': row['venue_id'],
                             'iat': int(time.time()), 'exp': int(time.time()) + 600}).encode())
    message = header + b'.' + payload
    return (message + b'.' + b64(hmac.new(secret, message, digest).digest())).decode()


MEMBER_REFUND_FIELDS = {'id', 'refundNo', 'entitlementNo', 'status', 'amount',
                        'requestedAt', 'approvedAt', 'paidAt'}


def assert_member_refund(record):
    assert set(record) == MEMBER_REFUND_FIELDS


def public_request(path, method='GET', body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request('http://127.0.0.1:' + str(args.port) + '/api/v1' + path,
                                 data=data, method=method, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=20) as response:
        result = json.load(response)
    assert result.get('code') == 0, (path, result.get('code'), result.get('message'))
    return result.get('data')


def request(path, who='admin', method='GET', body=None, expected_http=200, success=True, access_token=None):
    if args.readonly:
        assert method == 'GET' and body is None, 'Readonly mode rejects every mutation'
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request('http://127.0.0.1:' + str(args.port) + '/api/v1' + path,
                                 data=data, method=method,
                                 headers={'Authorization': 'Bearer ' + (access_token or token(who)),
                                          'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            status, result = response.status, json.load(response)
    except urllib.error.HTTPError as error:
        status, result = error.code, json.load(error)
    assert status == expected_http, (path, status, result.get('code'))
    if success:
        assert result.get('code') == 0, (path, result.get('code'), result.get('message'))
    return result.get('data') if success else result


today = dt.datetime.utcnow() + dt.timedelta(hours=8)
dates = '?from=' + today.strftime('%Y-%m-01') + '&to=' + today.strftime('%Y-%m-%d')
for who in ('admin', 'manager', 'reception'):
    if identity(who)['must_change_password']:
        r = request('/finance/refunds', who, expected_http=403, success=False)
        assert r['code'] == 1105
        continue
    request('/finance/refunds', who)
    request('/finance/report' + dates, who)
for who in ('coach', 'member'):
    if who == 'member' and not identity(who)['must_change_password']:
        records = request('/finance/me/refunds', who)
        assert isinstance(records, list)
        for r in records:
            assert_member_refund(r)
    request('/finance/refunds', who, expected_http=403, success=False)
    request('/finance/report' + dates, who, expected_http=403, success=False)
print('Finance readonly role scopes and redacted member status verified')
if args.readonly:
    assert sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1") == '9'
    print('Production GET-only acceptance passed; no business mutations performed')
    raise SystemExit(0)

# Everything below requires the exact isolated clone AND 18080; create a member through the public workflow.
manager = identity('manager')
venue_id = manager['venue_id']
tag = uuid.uuid4().hex[:12]
venue_code = sql("SELECT code FROM venue WHERE id=" + str(venue_id) + " AND status='ACTIVE'")
assert venue_code and '\n' not in venue_code
fixture_username = 'stage_member_' + tag
fixture_password = 'S!' + uuid.uuid4().hex + 'aA9'
phone = '19' + str(int(tag[:9], 16) % 1000000000).zfill(9)
registration = public_request('/auth/register/member', 'POST', {
    'username': fixture_username, 'password': fixture_password,
    'realName': 'Isolated finance fixture', 'phone': phone, 'venueCode': venue_code})
member_id = int(registration['memberId'])
login = public_request('/auth/login', 'POST', {'username': fixture_username, 'password': fixture_password})
fixture_token = login['accessToken']
assert login['userInfo']['role'] == 'MEMBER' and not login['userInfo'].get('mustChangePassword')
product = request('/commercial/products', 'admin', 'POST', {'name': 'Isolated count package ' + tag,
                  'category': 'PACKAGE', 'productType': 'TIMES_CARD', 'validityDays': 90, 'totalUses': 3,
                  'price': 10, 'accessAllowed': True, 'venueId': venue_id, 'status': 'ACTIVE'})
order = request('/commercial/orders', 'reception', 'POST', {'memberId': member_id, 'productId': product['id'],
                'paymentMethod': 'CASH', 'remark': 'Isolated staged financial acceptance only'})
credits = request('/commercial/entitlements?memberId=' + str(member_id))
credit = next(c for c in credits if c['sourceOrderId'] == order['id'])
ent_id = credit['id']
q = request('/finance/entitlements/' + str(ent_id) + '/quote')
assert q['verificationStatus'] == 'VERIFIED' and q['refundableAmount'] == 10
app_body = {'entitlementId': ent_id, 'version': q['version'], 'reason': 'Isolated remaining refund',
            'idempotencyKey': uuid.uuid4().hex}
with ThreadPoolExecutor(max_workers=2) as pool:
    applications = list(pool.map(lambda _: request('/finance/refunds', 'reception', 'POST', app_body), range(2)))
refund = applications[0]
assert refund['id'] == applications[1]['id'] and refund['status'] == 'PENDING'
assert request('/commercial/entitlements/' + str(ent_id) + '/freeze', 'admin', 'POST', {'note': 'stage'}, success=False)['code'] != 0
request('/finance/refunds/' + str(refund['id']) + '/approve', 'reception', 'POST',
        {'reason': 'Permission denial probe'}, expected_http=403, success=False)
request('/finance/refunds/' + str(refund['id']) + '/approve', 'manager', 'POST', {'reason': 'Isolated review'})
pay = {'amount': 10, 'channel': 'CASH', 'externalReference': 'STAGE-' + tag,
       'occurredAt': today.strftime('%Y-%m-%dT%H:%M:%S'), 'note': 'Synthetic isolated proof; not a real refund'}
request('/finance/refunds/' + str(refund['id']) + '/pay', 'reception', 'POST', pay,
        expected_http=403, success=False)
with ThreadPoolExecutor(max_workers=2) as pool:
    paid = list(pool.map(lambda _: request('/finance/refunds/' + str(refund['id']) + '/pay', 'manager', 'POST', pay), range(2)))
assert all(r['status'] == 'PAID' for r in paid)
order_now = next(o for o in request('/commercial/orders') if o['id'] == order['id'])
assert order_now['refundedAmount'] == 10
credit_now = next(e for e in request('/commercial/entitlements?memberId=' + str(member_id)) if e['id'] == ent_id)
assert credit_now['status'] == 'REFUNDED' and credit_now['remainingUses'] == 0
assert request('/commercial/entitlements/' + str(ent_id) + '/refund', 'admin', 'POST',
               {'amount': 1, 'paymentMethod': 'CASH', 'reason': 'forbidden old bypass'}, success=False)['code'] != 0
payment_rows = request('/commercial/payments')
assert len([p for p in payment_rows if p['orderId'] == order['id'] and p['transactionType'] == 'REFUND']) == 1
request('/finance/report' + dates)
mine = request('/finance/me/refunds', method='GET', access_token=fixture_token)
assert any(r['id'] == refund['id'] for r in mine)
for record in mine:
    assert_member_refund(record)
request('/finance/refunds', method='GET', expected_http=403, success=False, access_token=fixture_token)
request('/finance/report' + dates, method='GET', expected_http=403, success=False, access_token=fixture_token)
if not identity('member')['must_change_password']:
    original_mine = request('/finance/me/refunds', 'member')
    assert all(r['id'] != refund['id'] for r in original_mine)
print('PostgreSQL new value account, same-key concurrent request/payment, approval, termination and old-bypass rejection verified')
