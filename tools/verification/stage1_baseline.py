#!/usr/bin/env python3
"""Isolated H2 service baseline with an HR fault proxy. Exit 2 = known app defects."""
import argparse
from datetime import datetime, timezone
import hashlib
import http.server
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid
import sys

ROOT = Path(__file__).resolve().parents[2]
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def request(port, path, body=None, headers=None, timeout=5):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=data,
                                 headers={'Content-Type': 'application/json', **(headers or {})})
    try:
        response = HTTP.open(req, timeout=timeout)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        raw = response.read().decode()
        try:
            content = json.loads(raw)
        except ValueError:
            content = raw
        return response.status, dict(response.headers), content


def available_ports(count):
    sockets = []
    try:
        for _ in range(count):
            sock = socket.socket();sock.bind(('127.0.0.1', 0));sockets.append(sock)
        return [sock.getsockname()[1] for sock in sockets]
    finally:
        for sock in sockets:
            sock.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default=shutil.which('java'))
    parser.add_argument('--postgres-port', type=int, help='owned disposable PostgreSQL fixture port')
    parser.add_argument('--next', action='store_true', help='run remaining WBS development regression')
    parser.add_argument('--stage3', action='store_true', help='run signed JWT/RBAC/service-auth/refresh security regression')
    parser.add_argument('--stage2', action='store_true', help='run Gateway/UI stage2 regression suite')
    args = parser.parse_args()
    if args.next: args.stage3=True
    if not args.java:
        parser.error('Java 25 required; set JAVA_HOME/PATH or --java')
    java_version = subprocess.run([args.java, '-version'], capture_output=True, text=True, check=True).stderr
    if not ('"25' in java_version):
        parser.error('Java 25 required')
    services = ['trace-service', 'gateway-service', 'config-service', 'auth-service', 'hr-service', 'member-service', 'test-service']
    jars = {s: ROOT / s / 'build/libs' / (s + '-0.1.0.jar') for s in services}
    for jar in jars.values():
        if not jar.is_file():
            parser.error(f'Build the repository first: missing {jar}')
    run_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:6]
    out = ROOT / ('build/verification/next' if args.next else 'build/verification/stage3' if args.stage3 else 'build/verification/stage2' if args.stage2 else 'build/verification/stage1') / run_id
    out.mkdir(parents=True)
    ports = dict(zip(services + ['fault-proxy'], available_ports(8)))
    processes = {};logs = [];results = [];launches = {}
    sys.path.insert(0, str(ROOT / 'tools/security'))
    from prepare_local import create, environment
    security = create(out / 'security')
    mode = {'value': 'normal'}
    mode['security'] = security

    class Proxy(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            try:
                current_mode = mode['value']
                if current_mode == 'disconnect':
                    self.connection.shutdown(socket.SHUT_RDWR);self.connection.close();return
                if current_mode == 'delay':
                    time.sleep(1)
                if current_mode == 'unavailable':
                    status, body = 503, {'code': 'HR_UNAVAILABLE'}
                else:
                    mode['lastHrTraceId'] = self.headers.get('X-Trace-Id')
                    mode['lastHrTraceparent'] = self.headers.get('traceparent')
                    status, _, body = request(ports['hr-service'], self.path, headers={key: self.headers[key] for key in ['X-Trace-Id', 'traceparent', 'X-Process-Id', 'Authorization', 'X-Service-Id', 'X-Service-Token'] if self.headers.get(key)})
                payload = json.dumps(body).encode()
                self.send_response(status);self.send_header('Content-Type', 'application/json')
                self.end_headers();self.wfile.write(payload)
            except (BrokenPipeError, ConnectionResetError):
                pass
        def do_POST(self):
            try:
                if self.headers.get('Transfer-Encoding', '').lower() == 'chunked':
                    chunks = []
                    while True:
                        size = int(self.rfile.readline().strip().split(b';')[0], 16)
                        if not size:
                            self.rfile.readline();break
                        chunks.append(self.rfile.read(size));self.rfile.read(2)
                    raw = b''.join(chunks)
                else:
                    raw = self.rfile.read(int(self.headers.get('Content-Length', 0)))
                payload = json.loads(raw)
                if mode.get('collector') == 'unavailable':
                    status, body = 503, {'code': 'COLLECTOR_UNAVAILABLE'}
                else:
                    try:
                        status, _, body = request(ports['trace-service'], self.path, payload, headers={key: self.headers[key] for key in ['X-Service-Id', 'X-Service-Token'] if self.headers.get(key)})
                    except OSError:
                        status, body = 503, {'code': 'COLLECTOR_STARTING'}
                encoded = json.dumps(body).encode()
                self.send_response(status);self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length',str(len(encoded)))
                self.end_headers();self.wfile.write(encoded)
            except (BrokenPipeError, ConnectionResetError):
                pass
        def log_message(self, *unused):
            pass

    proxy = http.server.ThreadingHTTPServer(('127.0.0.1', ports['fault-proxy']), Proxy)
    proxy.daemon_threads = False
    threading.Thread(target=proxy.serve_forever, daemon=True).start()

    def record(id, status, detail, wbs=None):
        results.append({'id': id, 'status': status, 'detail': detail, 'wbs': wbs})
        print(f'{id}: {status} {detail}', flush=True)

    def verify(id, condition, detail, wbs=None):
        record(id, 'PASS' if condition else 'FAIL', detail, wbs)

    fatal = None
    try:
        for service in services:
            flags = [f'--server.port={ports[service]}', '--server.address=127.0.0.1',
                     f'--app.trace-service-url=http://127.0.0.1:{ports["trace-service"]}']
            if args.postgres_port and service in ['auth-service','member-service','trace-service','config-service','hr-service']:
                database=service.removesuffix('-service')+'db'
                flags += ['--app.database-type=POSTGRESQL',f'--app.datasource-url=jdbc:postgresql://127.0.0.1:{args.postgres_port}/{database}','--app.datasource-username=postgres']
            if service == 'member-service':
                flags += [f'--app.hr-service-url=http://127.0.0.1:{ports["fault-proxy"]}',
                          f'--app.auth-service-url=http://127.0.0.1:{ports["auth-service"]}',
                          '--spring.cloud.openfeign.client.config.default.connectTimeout=250',
                          '--spring.cloud.openfeign.client.config.default.readTimeout=250']
            if args.next and service == 'member-service':
                flags = [flag for flag in flags if not flag.startswith('--app.trace-service-url=')]
                flags += [f'--app.trace-service-url=http://127.0.0.1:{ports["fault-proxy"]}', '--app.outbox-delay-ms=200']
            if args.next and service == 'trace-service':
                flags += [f'--app.monitor.targets=hr-1=hr-service@http://127.0.0.1:{ports["hr-service"]},missing-1=missing-service@http://127.0.0.1:1', '--app.monitor.interval-ms=500']
            if service == 'test-service':
                flags += [f'--app.gateway-url=http://127.0.0.1:{ports["gateway-service"]}']
            if service == 'gateway-service':
                if args.stage2 or args.stage3:
                    flags = [flag for flag in flags if not flag.startswith('--app.trace-service-url=')]
                    flags += [f'--app.trace-service-url=http://127.0.0.1:{ports["fault-proxy"]}']
                routes = [
                    ('config-service', '/configs/**'), ('auth-service', '/auth/**,/users/**'),
                    ('member-service', '/members/**'), ('hr-service', '/hr/**')]
                if args.stage2 or args.stage3:
                    routes.append(('trace-service', '/trace-events/**,/operations/**' if args.next else '/trace-events/**'))
                for index, (target, pattern) in enumerate(routes):
                    prefix = f'--spring.cloud.gateway.server.webflux.routes[{index}]'
                    flags += [f'{prefix}.id={target}', f'{prefix}.uri=http://127.0.0.1:{ports[target]}',
                              f'{prefix}.predicates[0]=Path={pattern}']
                    if target == 'trace-service':
                        flags += [f'{prefix}.predicates[1]=Method=GET,POST' if args.next else f'{prefix}.predicates[1]=Method=GET']
            log = (out / (service + '.log')).open('w');logs.append(log)
            service_env=environment(security,service)
            if args.postgres_port and service in ['auth-service','member-service','trace-service','config-service','hr-service']:
                service_env['APP_DATASOURCE_PASSWORD']=os.environ['MSA_VERIFY_PG_PASSWORD']
            launches[service] = (service_env, log)
            processes[service] = subprocess.Popen([args.java, '-Xms32m', '-Xmx192m', '-jar', str(jars[service]), *flags],
                cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, env=service_env)
        paths = {service: ('/' if service == 'test-service' else '/actuator/health') for service in services}
        deadline = time.monotonic() + 90
        waiting = set(services)
        while waiting:
            for service in list(waiting):
                if processes[service].poll() is not None:
                    raise RuntimeError(f'{service} exited; inspect its run log')
                try:
                    code, _, _ = request(ports[service], paths[service], timeout=1)
                    if code == 200:
                        waiting.remove(service);record('START-' + service, 'PASS', 'HTTP response confirmed')
                except (OSError, urllib.error.URLError):
                    pass
            if time.monotonic() > deadline:
                raise RuntimeError('Startup deadline exceeded: ' + ','.join(sorted(waiting)))
            time.sleep(.2)
        def restart_member():
            old=processes['member-service'];old.terminate();old.wait(timeout=40)
            child_env,child_log=launches['member-service']
            processes['member-service']=subprocess.Popen(old.args,cwd=ROOT,stdout=child_log,stderr=subprocess.STDOUT,env=child_env)
            deadline=time.monotonic()+60
            while time.monotonic()<deadline:
                if processes['member-service'].poll() is not None:raise RuntimeError('Restarted Member exited')
                try:
                    if request(ports['member-service'],'/actuator/health',timeout=1)[0]==200:return
                except OSError:pass
                time.sleep(.2)
            raise RuntimeError('Member restart readiness timeout')
        mode['restartMember']=restart_member
        mode['persistent']=bool(args.postgres_port)
        if args.next:
            from next_regression import run
            run(ports, mode, record, verify, request)
        elif args.stage3:
            from stage3_regression import run
            run(ports, mode, record, verify, request)
        elif args.stage2:
            from stage2_regression import run
            run(ports, mode, record, verify, request)
        else:
            gateway = ports['gateway-service'];member = ports['member-service'];trace = ports['trace-service']
            auth = {'Authorization': 'Bearer jwt-access-token:PASSWORD:stage1', 'X-Trace-Id': 'stage1-baseline'}
            code, _, body = request(gateway, '/configs')
            verify('GW-UNAUTH', code == 401, f'protected API HTTP {code}')
            code, _, body = request(gateway, '/configs', headers=auth)
            verify('GW-AUTH', code == 200, f'authenticated API HTTP {code}', '2.1')
            if code != 200:
                record('GW-FLOW', 'BLOCKED', 'end-to-end signup blocked by authenticated Gateway failure', '2.1')
            _, _, hr = request(ports['fault-proxy'], '/hr/employees/EMPLOYEE-10001')
            verify('HR-CONTRACT', hr.get('active') is True and 'exists' not in hr, 'actual HR wire contract active=true')
            for label, employee, expected in [('ACTIVE', 'EMPLOYEE-10003', 'ACTIVE'), ('INACTIVE', 'EMPLOYEE-10002', 'FAILED')]:
                code, _, body = request(member, '/members/signup', {'memberNo': employee, 'memberName': 'Stage1 fixture', 'status': 'ACTIVE'}, headers=auth)
                verify('DIRECT-DIAG-' + label, code == 200 and isinstance(body, dict) and body.get('status') == expected,
                       f'direct diagnostic only: HTTP {code}, outcome={body.get("status") if isinstance(body, dict) else "unknown"}', '2.2')
            for fault in ['unavailable', 'delay', 'disconnect']:
                mode['value'] = fault
                # Verify the injected condition independently from the already-blocked Gateway.
                try:
                    status, _, _ = request(ports['fault-proxy'], '/hr/employees/EMPLOYEE-10001', timeout=.3)
                    injected = fault == 'unavailable' and status == 503
                except (OSError, urllib.error.URLError):
                    injected = fault in ['delay', 'disconnect']
                verify('FIXTURE-' + fault, injected, 'fault injector condition verified')
                before = time.monotonic()
                code, _, _ = request(member, '/members/signup', {'memberNo': 'EMPLOYEE-10004', 'memberName': 'Stage1 fixture', 'status': 'ACTIVE'}, timeout=5)
                elapsed = round(time.monotonic() - before, 3)
                _, _, history = request(member, '/members/processes')
                state = history[0]['status'] if history else 'MISSING'
                verify('DIRECT-DIAG-' + fault, code in [502, 503, 504] and state != 'RUNNING',
                       f'direct diagnostic: HTTP {code}, latest process={state}, elapsed={elapsed}s', '2.2')
            mode['value'] = 'normal'
            verify('HR-RECOVERY', request(ports['fault-proxy'], '/hr/employees/EMPLOYEE-10001')[0] == 200, 'fault proxy recovered')
            verify('TRACE-READ', isinstance(request(trace, '/trace-events')[2], list), 'central trace collection readable')
            verify('UI-HTML', request(ports['test-service'], '/')[0] == 200, 'HTML only, protected UI flow not certified')
    except Exception as exc:
        fatal = type(exc).__name__ + ': ' + str(exc)
        record('HARNESS', 'ERROR', fatal)
    finally:
        # Finish in-flight proxy requests before stopping their HR dependency.
        proxy.shutdown();proxy.server_close()
        for process in processes.values():
            if process.poll() is None:
                process.terminate()
        for process in processes.values():
            try:
                process.wait(timeout=8)
            except subprocess.TimeoutExpired:
                process.kill();process.wait()
        for log in logs:
            log.close()
        fingerprints = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in ROOT.rglob('*') if p.is_file() and
                        ('build' not in p.relative_to(ROOT).parts and '.git' not in p.relative_to(ROOT).parts and
                         '.gradle' not in p.relative_to(ROOT).parts and p.suffix in ['.java', '.gradle', '.yml', '.sql', '.xml', '.py', '.json'])}
        report = {'runId': run_id, 'environment': 'isolated-postgres-local' if args.postgres_port else 'isolated-h2-local', 'productionReady': False,
                  'java': java_version.splitlines()[0], 'ports': ports,
                  'jarSha256': {s: hashlib.sha256(p.read_bytes()).hexdigest() for s, p in jars.items()},
                  'sourceSha256': fingerprints, 'results': results,
                  'counts': {status: sum(r['status'] == status for r in results) for status in ['PASS', 'FAIL', 'BLOCKED', 'ERROR']},
                  'cleanup': 'only harness-owned child processes stopped; no existing service/container touched'}
        (out / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print('Report:', out / 'report.json', flush=True)
    return 1 if fatal else 2 if any(r['status'] in ['FAIL', 'BLOCKED'] for r in results) else 0

if __name__ == '__main__':
    raise SystemExit(main())
