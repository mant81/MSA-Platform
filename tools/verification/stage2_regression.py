#!/usr/bin/env python3
"""Gateway-first regression cases. Run stage1_baseline.py --stage2 to provision fixtures."""
import time
import uuid


def run(ports, mode, record, verify, request):
    gw = ports['gateway-service'];ui = ports['test-service'];trace = ports['trace-service']
    security = mode['security']
    code, _, admin = request(ui, '/api/auth/login', {'userId': security['adminUser'], 'password': security['adminPassword'], 'authType': 'PASSWORD'})
    if code != 200 or not admin.get('accessToken'):
        record('ADMIN-FIXTURE', 'BLOCKED', 'Administrator bootstrap/login not ready');return
    operations_headers = {'Authorization': 'Bearer ' + admin['accessToken']}
    user = 'stage2-' + uuid.uuid4().hex[:10]
    code, _, signup = request(ui, '/api/auth/signup', {'userId': user, 'userName': 'Regression', 'password': uuid.uuid4().hex})
    verify('UI-SIGNUP-AUTH', code == 200 and signup.get('success') and signup.get('accessToken'), 'UI→Gateway→Auth signup')
    if code != 200 or not signup.get('accessToken'):
        record('FLOW', 'BLOCKED', 'Auth signup failed');return
    headers = {'Authorization': 'Bearer ' + signup['accessToken']}
    for port, path, expected in [(gw, '/configs', 401), (ui, '/api/config/configs', 401)]:
        code, h, _ = request(port, path)
        verify('UNAUTH-' + str(port), code == expected and bool(h.get('X-Trace-Id')), f'HTTP {code}, response correlation present')
    code, h, _ = request(ui, '/api/config/configs', headers={**headers, 'X-Trace-Id': 'ui-stage2'})
    verify('UI-AUTH-AND-TRACE', code == 200 and h.get('X-Trace-Id') == 'ui-stage2', 'UI forwards auth and trace, returns correlation')

    successful_process_id = None
    for label, employee, expected_code, expected_error in [
        ('ACTIVE', 'EMPLOYEE-10001', 200, None),
        ('INACTIVE', 'EMPLOYEE-10002', 422, 'HR_NOT_ACTIVE'),
        ('MISSING', 'EMPLOYEE-NOT-FOUND', 422, 'HR_NOT_ACTIVE'),
        ('SIMULATED-TIMEOUT', 'EMPLOYEE-10003', 504, 'EXT_TIMEOUT'),
    ]:
        id = 'stage2-' + label.lower()
        code, h, body = request(ui, '/api/member/members/signup',
            {'memberNo': employee, 'memberName': 'Regression', 'status': 'TIMEOUT' if label == 'SIMULATED-TIMEOUT' else 'ACTIVE'},
            headers={**headers, 'X-Trace-Id': id})
        verify('FLOW-' + label, code == expected_code and body.get('errorCode') == expected_error
               and body.get('traceId') == id and h.get('X-Trace-Id') == id,
               f'UI→Gateway signup HTTP {code}, error={body.get("errorCode")}, Trace ID preserved')
        if not body.get('processId'):
            record('STATE-' + label, 'FAIL', 'processId missing');continue
        _, _, process = request(gw, '/members/processes/' + body['processId'], headers=operations_headers)
        _, _, steps = request(gw, '/members/processes/' + body['processId'] + '/steps', headers=operations_headers)
        verify('STATE-' + label, process.get('status') == ('SUCCESS' if expected_code == 200 else 'FAILED')
               and all(step['status'] != 'RUNNING' for step in steps), 'process and started steps terminal')
        verify('HR-HEADER-' + label, mode.get('lastHrTraceId') == id, 'same X-Trace-Id reached HR boundary')
        if expected_code == 200:
            successful_process_id = body['processId']
            _, _, saved = request(gw, '/members/' + employee, headers=headers)
            verify('MEMBER-SAVED', saved.get('memberNo') == employee, 'successful signup persisted member')
        if label == 'INACTIVE':
            hr_step = next(s for s in steps if s['stepId'] == 'CHECK_HR_EMPLOYEE')
            verify('BUSINESS-NOT-TIMEOUT', hr_step['status'] == 'FAILED' and hr_step['errorType'] == 'BUSINESS'
                   and hr_step['errorCode'] == 'HR_NOT_ACTIVE', 'business rejection distinct from transport timeout')

    for fault, employee, expected_code, expected_error in [
        ('unavailable', 'EMPLOYEE-10004', 503, 'HR_UNAVAILABLE'),
        ('delay', 'EMPLOYEE-10006', 504, 'HR_TIMEOUT'),
        ('disconnect', 'EMPLOYEE-10007', 503, 'HR_UNAVAILABLE')]:
        mode['value'] = fault
        id = 'stage2-' + fault
        code, h, body = request(gw, '/members/signup', {'memberNo': employee, 'memberName': 'Regression', 'status': 'ACTIVE'},
                                 headers={**headers, 'X-Trace-Id': id})
        verify('HR-FAULT-' + fault, code == expected_code and body.get('errorCode') == expected_error,
               f'HTTP {code}, error={body.get("errorCode")}')
        if body.get('processId'):
            _, _, process = request(gw, '/members/processes/' + body['processId'], headers=operations_headers)
            _, _, steps = request(gw, '/members/processes/' + body['processId'] + '/steps', headers=operations_headers)
            verify('FAULT-STATE-' + fault, process.get('status') == 'FAILED' and process.get('errorCode') == expected_error
                   and all(s['status'] != 'RUNNING' for s in steps), 'no process/step left RUNNING')
        else:
            record('FAULT-STATE-' + fault, 'FAIL', 'processId missing')
    mode['value'] = 'normal'
    code, _, body = request(gw, '/members/signup', {'memberNo': 'EMPLOYEE-10009', 'memberName': 'Recovered', 'status': 'ACTIVE'}, headers=headers)
    verify('HR-RECOVERED', code == 200 and body.get('status') == 'ACTIVE', 'signup succeeds after dependency recovery')

    if successful_process_id:
        code, _, rows = request(ui, '/api/trace/trace-events/process-id/' + successful_process_id, headers=operations_headers)
        verify('UI-TRACE-READ', code == 200 and isinstance(rows, list) and len(rows) > 0, 'UI→Gateway→Trace read route')
    else:
        record('UI-TRACE-READ', 'BLOCKED', 'No successful process to query')
    code, _, _ = request(gw, '/trace-events', body={'traceId': 'must-not-write'}, headers=headers)
    verify('TRACE-WRITE-NOT-EXPOSED', code in [403, 404], f'external write not routed, HTTP {code}')
    # Collector failure is isolated to Gateway; downstream collector remains available.
    mode['collector'] = 'unavailable'
    code, _, _ = request(gw, '/members', headers={**headers, 'X-Trace-Id': 'stage2-collector-failure'})
    verify('GATEWAY-COLLECTOR-ISOLATED', code == 200, f'collector 503 did not replace business response, HTTP {code}')
    time.sleep(.6)
    mode['collector'] = 'normal'

    def trace_status(id, status, error=None):
        for _ in range(40):
            _, _, rows = request(gw, '/trace-events/trace-id/' + id, headers=operations_headers)
            if any(r.get('serviceName') == 'gateway-service' and r.get('status') == status
                   and (error is None or r.get('errorCode') == error) for r in rows):
                return True
            time.sleep(.1)
        return False
    verify('GW-TRACE-SUCCESS', trace_status('stage2-active', 'SUCCESS'), 'success recorded centrally')
    verify('GW-TRACE-422', trace_status('stage2-inactive', 'FAILED', 'HTTP_422'), 'business HTTP error not recorded as success')
    verify('GW-TRACE-503', trace_status('stage2-unavailable', 'FAILED', 'HTTP_503'), 'dependency error recorded as failure')
    verify('GW-TRACE-504', trace_status('stage2-delay', 'FAILED', 'HTTP_504'), 'timeout response recorded as failure')
    code, h, _ = request(gw, '/members', headers=headers)
    verify('GENERATED-TRACE-ID', code == 200 and len(h.get('X-Trace-Id', '')) == 32, 'missing correlation generates one 32-character ID')
