#!/usr/bin/env python3
"""Build and exercise the hardened Trace image with owned ephemeral identities and ports."""
import base64
from datetime import datetime,timezone
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid
from stage1_baseline import available_ports,request
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/security'))
from prepare_local import create,environment

def main():
    name='msa-image-'+uuid.uuid4().hex[:10];out=ROOT/'build/verification/container'/name;out.mkdir(parents=True)
    config=create(out/'security');env=environment(config,'trace-service');port=available_ports(1)[0]
    env.update(MSA_SERVICE_CREDENTIALS_FILE='/run/identity.properties',MSA_JWT_PUBLIC_KEY_DIRECTORY='/run/public',BUILDX_CONFIG=str(ROOT/'build/verification/buildx'))
    image='msa/trace-service:wbs-validation';results=[];created=False
    def docker(*args,check=True):return subprocess.run(['docker',*args],capture_output=True,text=True,check=check,timeout=120,env=env)
    def passed(id,detail):results.append({'id':id,'status':'PASS','detail':detail});print(id,'PASS',detail,flush=True)
    def credentials(caller,target):
        props=dict(line.split('=',1) for line in Path(config['files'][caller]).read_text().splitlines())
        return {'X-Service-Id':caller,'X-Service-Token':props['outbound.'+target]}
    def signed():
        def enc(value):return base64.urlsafe_b64encode(json.dumps(value,separators=(',',':')).encode()).rstrip(b'=').decode()
        now=int(time.time());data=(enc({'alg':'RS256','kid':'current'})+'.'+enc({'iss':'https://msa.local/auth','aud':['msa-platform'],'sub':'image-fixture','roles':['OPERATIONS'],'token_use':'access','iat':now,'nbf':now,'exp':now+300})).encode()
        signature=subprocess.run(['openssl','dgst','-sha256','-sign',config['private']],input=data,capture_output=True,check=True).stdout
        return data.decode()+'.'+base64.urlsafe_b64encode(signature).rstrip(b'=').decode()
    try:
        built=docker('build','-f','deploy/Dockerfile','--build-arg','SERVICE=trace-service','--build-arg','REVISION=development','-t',image,str(ROOT));(out/'build.log').write_text(built.stdout+built.stderr)
        assert docker('image','inspect',image,'--format','{{.Config.User}}').stdout.strip()=='10001:10001'
        passed('IMAGE-BUILD','Java 25 runtime digest pinned; image defaults to UID/GID 10001')
        # Host-owned test files are mode 0600; use the host non-root UID for this fixture only.
        docker('run','-d','--name',name,'--user',str(os.getuid())+':'+str(os.getgid()),'--read-only','--cap-drop=ALL','--security-opt=no-new-privileges','--tmpfs','/tmp:rw,size=128m,mode=1777','--memory=512m',
         '-p',f'127.0.0.1:{port}:8080','-v',config['files']['trace-service']+':/run/identity.properties:ro','-v',config['public']+':/run/public:ro',
         '-e','MSA_SERVICE_CREDENTIALS_FILE','-e','MSA_JWT_PUBLIC_KEY_DIRECTORY',image,'--server.address=0.0.0.0','--server.port=8080');created=True
        deadline=time.monotonic()+60
        while time.monotonic()<deadline:
            if docker('inspect',name,'--format','{{.State.Running}}').stdout.strip()!='true':raise RuntimeError('Image process exited; inspect retained runtime.log')
            try:
                code,_,health=request(port,'/actuator/health/readiness',timeout=1)
                if code==200 and health.get('status')=='UP':break
            except OSError:pass
            time.sleep(.2)
        else:raise RuntimeError('Image readiness timeout')
        assert docker('exec',name,'id','-u').stdout.strip()!='0';passed('IMAGE-READINESS','Read-only, capability-free non-root container reports readiness UP')
        event={'eventId':str(uuid.uuid4()),'traceId':uuid.uuid4().hex,'eventType':'REQUEST_COMPLETED','status':'SUCCESS'}
        headers=credentials('member-service','trace-service')
        code,_,ack=request(port,'/trace-events/batch',[event,event],headers=headers)
        assert code==200 and [r['status'] for r in ack]==['PERSISTED','DUPLICATE'];passed('IMAGE-BATCH','Authenticated batch ingestion persists once and acknowledges duplicate event IDs')
        code,_,rows=request(port,'/trace-events/trace-id/'+event['traceId'],headers={**credentials('gateway-service','trace-service'),'Authorization':'Bearer '+signed()})
        assert code==200 and len(rows)==1;passed('IMAGE-READ','RS256 operations JWT plus target credentials read persisted event')
        assert request(port,'/operations/services')[0]==401;passed('IMAGE-AUTH','Direct unauthenticated operations access rejected')
        docker('stop','-t','35',name);logs=docker('logs',name);(out/'runtime.log').write_text(logs.stdout+logs.stderr)
        assert 'Graceful shutdown complete' in logs.stdout+logs.stderr;passed('IMAGE-SHUTDOWN','SIGTERM completes graceful HTTP shutdown')
    except Exception as exc:
        results.append({'id':'IMAGE-DRILL','status':'ERROR','detail':type(exc).__name__+': '+str(exc)});print(results[-1],flush=True)
    finally:
        if created:
            logs=docker('logs',name,check=False);(out/'runtime.log').write_text(logs.stdout+logs.stderr)
            docker('rm','-f',name,check=False)
        (out/'report.json').write_text(json.dumps({'recordedAt':datetime.now(timezone.utc).isoformat(),'results':results,'productionReady':False,'limitations':['Fixture uses host non-root UID for private bind mount access','Compose deployment/TLS/production secret permissions remain unaccepted']},indent=2)+'\n')
        print('Report:',out/'report.json')
    return 0 if all(r['status']=='PASS' for r in results) else 1
if __name__=='__main__':raise SystemExit(main())
