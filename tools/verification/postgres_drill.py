#!/usr/bin/env python3
"""Disposable network-isolated PostgreSQL schema/stop/restart drill, not app PG certification."""
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
IMAGE = 'postgres@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336'

def main():
    name = 'msa-stage1-pg-' + uuid.uuid4().hex[:10]
    out = ROOT / 'build/verification/stage1' / name
    out.mkdir(parents=True)
    results = []
    created = False
    server_version = None
    def docker(*args, check=True, data=None):
        return subprocess.run(['docker', *args], input=data, capture_output=True, text=True, check=check, timeout=120)
    def sql(query):
        return docker('exec', '-i', name, 'psql', '-U', 'postgres', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At', data=query).stdout.strip()
    def ready():
        for _ in range(100):
            if docker('exec', name, 'pg_isready', '-U', 'postgres', check=False).returncode == 0:
                return
            time.sleep(.2)
        raise RuntimeError('PostgreSQL readiness deadline exceeded')
    try:
        docker('run', '-d', '--name', name, '--network', 'none',
               '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', IMAGE)
        created = True
        ready()
        server_version = sql('show server_version;')
        assert server_version.split('.')[0] == '18'
        sql((ROOT / 'trace-service/src/main/resources/schema.sql').read_text())
        sql("insert into trace_event(trace_id,event_type,source_service) values ('1234567890abcdef1234567890abcdef','STAGE1_DRILL','verification-fixture');")
        assert sql("select count(*) from trace_event where event_type='STAGE1_DRILL';") == '1'
        results.append({'id': 'PG-SCHEMA', 'status': 'PASS', 'detail': 'existing Trace schema and insert/read on PostgreSQL'})
        docker('stop', '-t', '5', name)
        assert docker('exec', name, 'pg_isready', check=False).returncode != 0
        results.append({'id': 'PG-STOP', 'status': 'PASS', 'detail': 'database unavailable after owned-container stop'})
        docker('start', name);ready()
        assert sql("select count(*) from trace_event where event_type='STAGE1_DRILL';") == '1'
        results.append({'id': 'PG-RESTART', 'status': 'PASS', 'detail': 'fixture data retained across DB stop/start'})
    except Exception as exc:
        results.append({'id': 'PG-HARNESS', 'status': 'ERROR', 'detail': type(exc).__name__ + ': ' + str(exc)})
    finally:
        if created:
            docker('rm', '-f', '-v', name)
        report = {'recordedAt': datetime.now(timezone.utc).isoformat(), 'image': IMAGE, 'serverVersion': server_version,
                  'network': 'none; temporary trust-auth fixture; no published ports',
                  'results': results, 'cleanup': 'owned container and anonymous volume removed',
                  'limitations': 'No service JDBC integration, HA, backup restore, migration or production recovery validation.'}
        (out / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        for result in results:
            print(result['id'], result['status'], result['detail'])
        print('Report:', out / 'report.json')
    return 0 if all(r['status'] == 'PASS' for r in results) else 1

if __name__ == '__main__':
    raise SystemExit(main())
