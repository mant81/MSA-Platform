"""Recovery HTTP checks. SQL fixture mutation is restricted to an explicitly labelled owned PG container."""
from concurrent.futures import ThreadPoolExecutor
import base64
import hashlib
import json
import os
import re
import subprocess
import uuid


def run(ports, mode, verify, request, admin, user, owner):
    gw=ports['gateway-service']
    code,_,_=request(gw,'/members/requests',headers=user)
    verify('RECOVERY-USER-LIST-DENIED',code==403,'ordinary user cannot enumerate recovery requests','4.2')
    code,_,_=request(gw,'/members/requests/'+'0'*64+'/allow-retry',{'reasonCode':'DB_ROLLBACK_CONFIRMED'},headers=user)
    verify('RECOVERY-USER-APPROVE-DENIED',code==403,'ordinary user cannot authorize a retry','4.2')
    def encode(value):return base64.urlsafe_b64encode(json.dumps(value,separators=(',',':')).encode()).rstrip(b'=').decode()
    original=user['Authorization'].removeprefix('Bearer ');part=original.split('.')[1]
    claims=json.loads(base64.urlsafe_b64decode(part+'='*(-len(part)%4)));claims['roles']=['OPERATIONS']
    signed=(encode({'alg':'RS256','kid':'current'})+'.'+encode(claims)).encode()
    signature=subprocess.run(['openssl','dgst','-sha256','-sign',mode['security']['private']],input=signed,capture_output=True,check=True).stdout
    ops={'Authorization':'Bearer '+signed.decode()+'.'+base64.urlsafe_b64encode(signature).rstrip(b'=').decode()}
    code,_,_=request(gw,'/members/requests',headers=ops)
    verify('RECOVERY-OPS-LIST',code==200,'operations role can read recovery state','4.2')
    code,_,_=request(gw,'/members/requests/'+'0'*64+'/allow-retry',{'reasonCode':'DB_ROLLBACK_CONFIRMED'},headers=ops)
    verify('RECOVERY-OPS-APPROVE-DENIED',code==403,'operations role cannot grant a retry','4.2')
    code,_,rows=request(gw,'/members/requests',headers=admin)
    verify('RECOVERY-ADMIN-LIST',code==200 and isinstance(rows,list) and len(rows)<=100,'bounded recovery query available to administrator','4.2')
    if not mode.get('persistent'):
        return
    name=os.environ.get('MSA_VERIFY_PG_CONTAINER','');label=os.environ.get('MSA_VERIFY_PG_LABEL','')
    assert re.fullmatch(r'msa-wbs-pg-[a-f0-9]{10}',name) and re.fullmatch(r'[a-f0-9]{32}',label),'Owned PG fixture binding missing'
    observed=subprocess.check_output(['docker','inspect',name,'--format','{{index .Config.Labels "codex.msa.fixture"}}'],text=True).strip()
    assert observed==label,'PG fixture ownership mismatch'
    def sql(query):
        return subprocess.check_output(['docker','exec','-i',name,'psql','-U','postgres','-d','memberdb','-v','ON_ERROR_STOP=1','-At'],input=query.encode()).decode().strip()
    assert re.fullmatch(r'[A-Za-z0-9._-]{3,50}',owner)
    body={'memberNo':'EMPLOYEE-10004','memberName':'Recovery fixture','status':'ACTIVE'}
    assert sql("select count(*) from member where member_no='EMPLOYEE-10004';")=='0'
    key='recovery-'+uuid.uuid4().hex;hash_key=hashlib.sha256((owner+'\n'+key).encode()).hexdigest()
    fingerprint=hashlib.sha256(json.dumps(list(body.values()),separators=(',',':')).encode()).hexdigest()
    sql(f"insert into signup_request(request_key,fingerprint,status,created_at,updated_at,owner_id,member_no,initial_trace_id) values ('{hash_key}','{fingerprint}','RECOVERY_REQUIRED',current_timestamp,current_timestamp,'{owner}','EMPLOYEE-10004','orphan-trace-fixture');")
    retry_headers={**user,'Idempotency-Key':key}
    code,_,_=request(gw,'/members/signup',body,headers=retry_headers)
    verify('RECOVERY-NO-AUTO-RETRY',code==409,'orphan claim remains blocked before operator approval','4.2')
    def approve(_):return request(gw,'/members/requests/'+hash_key+'/allow-retry',{'reasonCode':'PROCESS_CRASH_CONFIRMED'},headers={**admin,'X-Trace-Id':'recovery-approval'})
    with ThreadPoolExecutor(max_workers=2) as pool: approvals=list(pool.map(approve,range(2)))
    verify('RECOVERY-CONCURRENT-APPROVAL',sorted(r[0] for r in approvals)==[200,409],'exactly one approval and one conflict','4.2')
    code,_,_=request(gw,'/members/signup',{**body,'memberName':'changed'},headers=retry_headers)
    verify('RECOVERY-PAYLOAD-CONFLICT',code==409,'operator approval does not permit a different input','4.2')
    with ThreadPoolExecutor(max_workers=6) as pool: replies=list(pool.map(lambda _:request(gw,'/members/signup',body,headers=retry_headers),range(6)))
    successes=[r for r in replies if r[0]==200]
    verify('RECOVERY-CONCURRENT-RETRY',bool(successes) and all(r[0] in [200,409] for r in replies) and len({r[2]['processId'] for r in successes})==1,'six simultaneous retries create one process','4.2')
    verify('RECOVERY-SINGLE-EFFECT',sql("select count(*) from member where member_no='EMPLOYEE-10004';")=='1','one business effect committed on PostgreSQL','4.2')
    code,_,audit=request(gw,'/members/requests/'+hash_key+'/audit',headers=admin)
    actions=[row.get('ACTION',row.get('action')) for row in audit] if code==200 else []
    verify('RECOVERY-AUDIT',code==200 and sorted(actions)==['RETRY_ALLOWED','RETRY_STARTED'],'approval and original-owner retry both audited','4.2')
    code,_,_=approve(None)
    verify('RECOVERY-COMPLETED-BLOCKED',code==409,'completed request cannot be authorized again','4.2')
