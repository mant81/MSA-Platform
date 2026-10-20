#!/usr/bin/env python3
"""Fresh per-service PostgreSQL app integration, dump/restore and restart on owned resources only."""
from collections import Counter
from datetime import datetime,timezone
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time
import uuid
from stage1_baseline import available_ports
ROOT=Path(__file__).resolve().parents[2]
IMAGE='postgres@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336'

def main():
    name='msa-wbs-pg-'+uuid.uuid4().hex[:10];out=ROOT/'build/verification/postgres'/name;out.mkdir(parents=True)
    port=available_ports(1)[0];password=secrets.token_urlsafe(32);env={**os.environ,'POSTGRES_PASSWORD':password,'MSA_VERIFY_PG_PASSWORD':password}
    results=[];created=False
    def docker(*args,data=None,check=True):return subprocess.run(['docker',*args],input=data,capture_output=True,check=check,timeout=60,env=env)
    def sql(query,database='postgres'):
        return docker('exec','-i',name,'psql','-U','postgres','-d',database,'-v','ON_ERROR_STOP=1','-At',data=query.encode()).stdout.decode().strip()
    def ready():
        deadline=time.monotonic()+45
        while time.monotonic()<deadline:
            if docker('exec',name,'pg_isready','-h','127.0.0.1','-U','postgres',check=False).returncode==0:return
            time.sleep(.2)
        raise RuntimeError('DB readiness deadline')
    def passed(id,detail):results.append({'id':id,'status':'PASS','detail':detail});print(id,'PASS',detail,flush=True)
    try:
        docker('run','-d','--name',name,'-p',f'127.0.0.1:{port}:5432','-e','POSTGRES_PASSWORD',IMAGE);created=True;ready()
        for service in ['auth','member','trace','config','hr']:sql('create database '+service+'db;')
        log=out/'application-regression.log'
        with log.open('w') as stream:
            completed=subprocess.run([sys.executable,str(ROOT/'tools/verification/stage1_baseline.py'),'--next','--postgres-port',str(port),'--java',str(Path(os.environ['JAVA_HOME'])/'bin/java')],cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,env=env,timeout=300)
        if completed.returncode:raise RuntimeError('PostgreSQL app regression failed; inspect ignored application-regression.log')
        passed('PG-APP','All seven applications and Gateway-first regression executed against five PostgreSQL databases')
        counts=sql('select (select count(*) from member), (select count(*) from member_process), (select count(*) from trace_outbox);','memberdb')
        dump=docker('exec',name,'pg_dump','-U','postgres','-Fc','memberdb').stdout
        dumpfile=out/'member.dump';dumpfile.write_bytes(dump);dumpfile.chmod(0o600)
        sql('create database member_restore;')
        docker('exec','-i',name,'pg_restore','-U','postgres','--exit-on-error','-d','member_restore',data=dump)
        assert sql('select (select count(*) from member), (select count(*) from member_process), (select count(*) from trace_outbox);','member_restore')==counts
        passed('PG-RESTORE','Dump restored to a new database; member/process/outbox counts match')
        docker('stop','-t','5',name);docker('start',name);ready()
        assert sql('select (select count(*) from member), (select count(*) from member_process), (select count(*) from trace_outbox);','memberdb')==counts
        passed('PG-RESTART','Business state and pending/delivered outbox rows survived PostgreSQL restart')
        for service in ['auth','member','trace','config','hr']:
            assert int(sql('select count(*) from flyway_schema_history where success=true;',service+'db'))>=1
        passed('PG-MIGRATIONS','Flyway history present for all five service-owned databases')
    except Exception as exc:
        results.append({'id':'PG-DRILL','status':'ERROR','detail':type(exc).__name__+': '+str(exc)});print(results[-1],flush=True)
    finally:
        if created:
            logs=docker('logs',name,check=False);(out/'database.log').write_bytes(logs.stdout+logs.stderr)
            docker('rm','-f','-v',name,check=False)
        report={'recordedAt':datetime.now(timezone.utc).isoformat(),'image':IMAGE,'results':results,'cleanup':'owned container/anonymous volume removed','productionReady':False,'limitations':['No production RTO/RPO acceptance','No HA or operational security acceptance']}
        (out/'report.json').write_text(json.dumps(report,indent=2)+'\n');print('Report:',out/'report.json')
    return 0 if all(r['status']=='PASS' for r in results) else 1
if __name__=='__main__':raise SystemExit(main())
