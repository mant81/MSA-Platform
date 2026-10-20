#!/usr/bin/env python3
"""Gateway-first continuation checks; ephemeral credentials remain inside ignored build outputs."""
from concurrent.futures import ThreadPoolExecutor
import time
import uuid


def run(ports, mode, record, verify, request):
    from stage3_regression import run as security_regression
    security_regression(ports, mode, record, verify, request)
    gw=ports['gateway-service'];ui=ports['test-service'];config=mode['security']
    code, _, admin=request(ui,'/api/auth/login',{'userId':config['adminUser'],'password':config['adminPassword'],'authType':'PASSWORD'})
    if code!=200:
        record('NEXT-ADMIN','BLOCKED','Admin fixture unavailable');return
    headers={'Authorization':'Bearer '+admin['accessToken']}
    def wait(check, seconds=15):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            result=check()
            if result:return result
            time.sleep(.2)
        return None
    mode['value']='normal'
    body={'memberNo':'EMPLOYEE-10010','memberName':'Reliable fixture','status':'ACTIVE'}
    key='next-'+uuid.uuid4().hex
    incoming='00-'+'1234567890abcdef'*2+'-'+'1234567890abcdef'+'-01'
    shared={**headers,'Idempotency-Key':key,'traceparent':incoming,'X-Trace-Id':'next-idempotent'}
    with ThreadPoolExecutor(max_workers=6) as pool:
        replies=list(pool.map(lambda _:request(gw,'/members/signup',body,headers=shared),range(6)))
    success=[r for r in replies if r[0]==200]
    verify('NEXT-CONCURRENT-CLAIM',bool(success) and all(r[0] in [200,409] for r in replies) and len({r[2].get('processId') for r in success})==1,'six concurrent requests produce a single process', '4.2')
    if not success:return
    result=success[0][2];pid=result['processId']
    code,_,replay=request(gw,'/members/signup',body,headers=shared)
    verify('NEXT-REPLAY',code==200 and replay.get('processId')==pid,'completed request returns original result','4.2')
    code,_,_=request(gw,'/members/signup',{**body,'memberName':'changed'},headers=shared)
    verify('NEXT-PAYLOAD-CONFLICT',code==409,'same key with changed payload rejected','4.2')
    code,_,members=request(gw,'/members',headers=headers)
    verify('NEXT-SINGLE-MEMBER',code==200 and sum(r['memberNo']==body['memberNo'] for r in members)==1,'business side effect persisted once','4.2')
    child=mode.get('lastHrTraceparent','')
    verify('NEXT-W3C-HR',child.startswith('00-'+'1234567890abcdef'*2+'-') and child!=incoming,'W3C trace preserved and child span propagated through Feign','4.3')
    def process_events():
        code,_,events=request(gw,'/trace-events/process-id/'+pid,headers=headers)
        return events if code==200 and any(e.get('eventType')=='REQUEST_COMPLETED' and e.get('sourceService')=='member-service' for e in events) else None
    events=wait(process_events)
    verify('NEXT-OUTBOX-DELIVERED',bool(events) and all(e.get('eventId') for e in events),'durable process events acknowledged centrally','4.4')
    verify('NEXT-PARENT-SPANS',bool(events) and any(e.get('parentSpanId') and e.get('spanId') for e in events),'parent span identifiers stored centrally','4.3')
    hr_events=[e for e in (events or []) if e.get('sourceService')=='hr-service']
    hr_steps=[e for e in (events or []) if e.get('sourceService')=='member-service' and e.get('stepId')=='CHECK_HR_EMPLOYEE']
    verify('NEXT-HR-SPAN-LINK',bool(hr_events) and any(hr.get('parentSpanId')==step.get('spanId') for hr in hr_events for step in hr_steps),'HR server span is a child of the Member HR business step','4.3')
    code,_,page=request(gw,'/operations/flows?processId='+pid+'&limit=2',headers=headers)
    verify('NEXT-FLOW-CURSOR',code==200 and len(page.get('events',[]))==2 and page.get('hasMore') and page.get('completeness')=='UNVERIFIED','flow API bounds pages and declares completeness','4.5')
    verify('NEXT-PROJECTION',any(r.get('STATUS',r.get('status'))=='SUCCEEDED' for r in page.get('projections',[])),'terminal projection agrees with committed workflow','4.5')
    verify('NEXT-STEP-PROJECTION',len(page.get('steps',[]))==4 and all(r.get('status')=='SUCCEEDED' and r.get('attempt')==1 for r in page.get('steps',[])),'all four terminal business steps visible independently of event page','4.5')
    verify('NEXT-COMPLETENESS-REASON',page.get('completenessDetails',{}).get('reason')=='EXPECTED_EVENT_MANIFEST_UNAVAILABLE','no completeness claim without expected event manifest','4.5')
    code,_,window=request(gw,'/operations/flows?processId='+pid+'&since=2000-01-01T00:00:00Z&until=2001-01-01T00:00:00Z',headers=headers)
    verify('NEXT-FLOW-WINDOW',code==200 and not window.get('events') and len(window.get('steps',[]))==4,'event-time window is separate from process projection scope','4.5')
    code,_,_=request(gw,'/operations/flows?since=2026-01-01T00:00:00Z&until=2025-01-01T00:00:00Z',headers=headers)
    verify('NEXT-FLOW-INVALID-WINDOW',code==400,'inverted event-time bounds rejected','4.5')
    # Interrupt collector after process begins, then verify post-recovery outbox export.
    mode['collector']='unavailable'
    outage_body={'memberNo':'EMPLOYEE-10003','memberName':'Outbox recovery','status':'ACTIVE'}
    code,_,outage=request(gw,'/members/signup',outage_body,headers={**headers,'Idempotency-Key':'outage-'+uuid.uuid4().hex})
    verify('NEXT-COLLECTOR-OUTAGE',code==200 and bool(outage.get('processId')),'business commit succeeds while collector returns 503','4.4')
    if mode.get('persistent'):
        mode['restartMember']()
        code,_,after_restart=request(gw,'/members/'+outage_body['memberNo'],headers=headers)
        verify('NEXT-APP-RESTART',code==200 and after_restart.get('memberNo')==outage_body['memberNo'],'Member JVM restart retains committed business data and unsent outbox','4.4')
    time.sleep(.6);mode['collector']='normal'
    def recovered():
        code,_,rows=request(gw,'/trace-events/process-id/'+outage.get('processId','missing'),headers=headers)
        return code==200 and any(e.get('eventType')=='REQUEST_COMPLETED' and e.get('sourceService')=='member-service' for e in rows)
    verify('NEXT-OUTBOX-RECOVERY',bool(wait(recovered)),'pending business events exported after collector recovery','4.4')
    for path,label in [('/operations/errors','ERROR-GROUPS'),('/operations/services','SERVICE-STATES'),('/operations/incidents','INCIDENTS'),('/operations/alerts','ALERT-DELIVERY')]:
        code,_,data=request(gw,path,headers=headers);verify('NEXT-'+label,code==200,'protected operations API available', '5.2' if 'errors' in path else '6.2')
    def states():
        code,_,status=request(gw,'/operations/services',headers=headers)
        return status if code==200 and status.get('services',{}).get('hr-service')=='UP' and status.get('services',{}).get('missing-service')=='DOWN' else None
    status=wait(states)
    verify('NEXT-ACTUAL-PROBES',bool(status),'actual HTTP probes distinguish live HR and unreachable expected instance','6.2')
    code,_,incidents=request(gw,'/operations/incidents',headers=headers)
    open_incidents=[{k.lower():v for k,v in r.items()} for r in incidents] if code==200 else []
    open_incidents=[r for r in open_incidents if r['state']=='OPEN']
    if open_incidents:
        id=open_incidents[0]['incident_id'];code,_,ack=request(gw,'/operations/incidents/'+id+'/ack',{},headers=headers)
        verify('NEXT-INCIDENT-ACK',code==200 and ack.get('updated')==1,'operator ACK stored once with audit','6.4')
    else:record('NEXT-INCIDENT-ACK','FAIL','Expected unreachable-instance incident missing')
    code,_,delivery=request(gw,'/operations/alerts',headers=headers)
    verify('NEXT-ALERT-UNCONFIGURED',code==200 and delivery.get('configured') is False and delivery.get('pending',0)>0,'missing external receiver is visible; pending alerts retained','6.4')
    code,_,metrics=request(gw,'/actuator/prometheus',headers=headers)
    verify('NEXT-METRICS',code==200 and 'http_server_requests' in str(metrics),'authenticated Prometheus HTTP metrics available','6.3')
    code,_,_=request(gw,'/actuator/prometheus')
    verify('NEXT-METRICS-AUTH',code==401,'metrics are not publicly accessible','6.3')
    code,_,html=request(ui,'/operations')
    verify('NEXT-OPERATIONS-PAGE',code==200 and 'operations.js' in str(html),'operations page served separately from sample UI','7.1')
    for resource in ['flows','services','errors','incidents','alerts']:
        code,_,_=request(ui,'/api/operations/'+resource,headers=headers)
        verify('NEXT-UI-'+resource.upper(),code==200,'UI→Gateway→protected operations API','7.1')

    code,_,error_events=request(gw,'/trace-events/trace-id/stage2-inactive',headers=headers)
    linked=next((e for e in error_events if e.get('sourceService')=='gateway-service' and e.get('causedByErrorId')),None) if code==200 else None
    verify('NEXT-ERROR-CAUSAL-LINK',bool(linked) and any(e.get('errorId')==linked['causedByErrorId'] and e.get('sourceService')=='member-service' for e in error_events),'Gateway propagation error refers to Member root error','5.2')
    if linked:
        code,_,causes=request(gw,'/operations/errors/'+linked['causedByErrorId']+'/causes',headers=headers)
        verify('NEXT-ERROR-CAUSE-API',code==200 and len(causes)>=2,'root and propagated errors are traversable','5.2')
    code,_,user=request(ui,'/api/auth/signup',{'userId':'next-'+uuid.uuid4().hex[:10],'userName':'Role fixture','password':uuid.uuid4().hex})
    user_headers={'Authorization':'Bearer '+user.get('accessToken','')}
    code,_,_=request(gw,'/operations/services',headers=user_headers)
    verify('NEXT-USER-OPS-DENIED',code==403,'ordinary user cannot query service operations','3.3')
    code,_,_=request(gw,'/operations/incidents/unknown/ack',{},headers=user_headers)
    verify('NEXT-USER-ACK-DENIED',code==403,'ordinary user cannot acknowledge operational incidents','3.3')
    code,_,_=request(gw,'/members/processes',headers=user_headers)
    verify('NEXT-USER-LOCAL-PROCESS-DENIED',code==403,'ordinary user cannot read backend operational process histories','3.3')
    code,_,_=request(gw,'/members',{'memberNo':'BYPASS','memberName':'denied'},headers=user_headers)
    verify('NEXT-USER-DIRECT-WRITE-DENIED',code==403,'ordinary user signup must use trace-aware idempotent workflow','3.3')
    verify('NEXT-DEPLOYMENT-CONTEXT',bool(events) and all(e.get('environment') and e.get('instanceId') and e.get('deploymentVersion') for e in events),'process events carry environment, instance and deployment metadata','5.2')

    from recovery_regression import run as recovery
    recovery(ports,mode,verify,request,headers,user_headers,user['userId'])

    started=time.monotonic()
    def sample(_):
        before=time.monotonic();code,_,_=request(gw,'/members',headers=headers)
        return code,(time.monotonic()-before)*1000
    with ThreadPoolExecutor(max_workers=8) as pool: samples=list(pool.map(sample,range(100)))
    duration=time.monotonic()-started;latencies=sorted(item[1] for item in samples)
    verify('NEXT-TRIAL-LOAD',all(item[0]==200 for item in samples),
           f'100 read requests / concurrency 8; observed RPS={100/duration:.1f}, p95={latencies[94]:.1f}ms, p99={latencies[98]:.1f}ms; local trial only','9.3')
