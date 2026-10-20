#!/usr/bin/env python3
"""Security checks using disposable trusted test keys; never emit credentials or tokens."""
import base64
from concurrent.futures import ThreadPoolExecutor
import json
import http.client
from pathlib import Path
import subprocess
import time
import uuid


def run(ports, mode, record, verify, request):
    from stage2_regression import run as workflow
    workflow(ports, mode, record, verify, request)
    config = mode['security'];gw = ports['gateway-service'];ui = ports['test-service']
    password = uuid.uuid4().hex
    user = 'sec' + uuid.uuid4().hex[:10]
    code, headers, signup = request(ui, '/api/auth/signup',
        {'userId': user, 'userName': 'Security fixture', 'password': password, 'role': 'ADMIN', 'status': 'ACTIVE'})
    verify('SEC-SIGNUP', code == 200 and bool(signup.get('accessToken')), 'self signup creates an account')
    if code != 200 or not signup.get('accessToken'):
        record('SECURITY', 'BLOCKED', 'Security fixture signup failed');return
    token = signup['accessToken'];refresh = signup['refreshToken']
    bearer = {'Authorization': 'Bearer ' + token}
    verify('TOKEN-NO-CACHE', 'no-store' in headers.get('Cache-Control', ''), 'UI token response is not cacheable')
    def decode(raw):
        part = raw.split('.')[1]
        return json.loads(base64.urlsafe_b64decode(part + '=' * (-len(part) % 4)))
    claims = decode(token)
    verify('NO-SELF-ADMIN', claims['roles'] == ['USER'], 'signup role input cannot grant privileges')
    jwt_header_part = token.split('.')[0]
    jwt_header = json.loads(base64.urlsafe_b64decode(jwt_header_part + '=' * (-len(jwt_header_part) % 4)))
    verify('SIGNED-ACCESS', jwt_header.get('alg') == 'RS256' and jwt_header.get('kid') == 'current' and len(token.split('.')) == 3 and claims.get('token_use') == 'access'
           and claims.get('iss') == 'https://msa.local/auth' and (claims.get('aud') == 'msa-platform' or claims.get('aud') == ['msa-platform']), 'RS256 access-token claims issued')
    code, _, admin = request(ui, '/api/auth/login',
        {'userId': config['adminUser'], 'password': config['adminPassword'], 'authType': 'PASSWORD'})
    if code != 200:
        record('SEC-ADMIN', 'BLOCKED', 'Administrator login failed');return
    admin_headers = {'Authorization': 'Bearer ' + admin['accessToken']}
    for label, body, expected in [
        ('WRONG-PASSWORD', {'userId': user, 'password': 'incorrect-password', 'authType': 'PASSWORD'}, 401),
        ('MISSING-USER', {'userId': 'missinguser', 'password': password, 'authType': 'PASSWORD'}, 401),
        ('UNSUPPORTED-AUTH', {'userId': user, 'password': password, 'authType': 'OTP'}, 400)]:
        code, _, body = request(ui, '/api/auth/login', body)
        verify('LOGIN-' + label, code == expected and not body.get('accessToken'), f'HTTP {code}; no token issued')
    for label, body, expected in [
        ('WEAK-PASSWORD', {'userId': user + 'a', 'userName': 'Fixture', 'password': '1234'}, 400),
        ('PASSWORD-BYTES', {'userId': user + 'b', 'userName': 'Fixture', 'password': '가' * 30}, 400),
        ('DUPLICATE', {'userId': user, 'userName': 'Fixture', 'password': password}, 409)]:
        code, _, body = request(ui, '/api/auth/signup', body)
        verify('SIGNUP-' + label, code == expected and not body.get('accessToken'), f'HTTP {code}; request rejected')
    for label, path, body, expected in [
        ('USER-LIST', '/users', None, 403), ('TRACE', '/trace-events', None, 403),
        ('CONFIG-WRITE', '/configs', {'configKey': 'denied', 'configValue': 'x'}, 403),
        ('AUTH-PREFIX-BYPASS', '/auth/login-extra', {}, 401), ('ACTUATOR-METADATA', '/actuator/env', None, 403)]:
        code, _, _ = request(gw, path, body, headers={} if label == 'AUTH-PREFIX-BYPASS' else bearer)
        verify('RBAC-' + label, code == expected, f'HTTP {code}')
    code, _, rows = request(gw, '/users', headers=admin_headers)
    verify('ADMIN-USERS-SAFE', code == 200 and isinstance(rows, list) and any(r['userId'] == user and r['role'] == 'USER' for r in rows)
           and all('passwordHash' not in r and 'password' not in r for r in rows) and password not in json.dumps(rows), 'admin list contains no password/hash fields')
    code, _, _ = request(gw, '/users', {'userId': 'backdoor', 'passwordHash': password, 'role': 'ADMIN'}, headers=admin_headers)
    verify('NO-LEGACY-USER-WRITE', code == 403, f'legacy account write API blocked, HTTP {code}')
    code, _, _ = request(gw, '/configs', {'configKey': 'sec-' + user, 'configValue': 'ok'}, headers=admin_headers)
    verify('ADMIN-CONFIG-WRITE', code == 200, f'authorized write HTTP {code}')

    def encode(value):
        return base64.urlsafe_b64encode(json.dumps(value, separators=(',', ':')).encode()).rstrip(b'=').decode()
    def signed(patch=None, remove=(), kid='current', private='current'):
        payload = {**claims, **(patch or {})}
        for name in remove:
            payload.pop(name, None)
        data = (encode({'alg': 'RS256', 'typ': 'JWT', 'kid': kid}) + '.' + encode(payload)).encode()
        signature = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(Path(config['directory']) / (private + '.key'))],
                                   input=data, capture_output=True, check=True).stdout
        return data.decode() + '.' + base64.urlsafe_b64encode(signature).rstrip(b'=').decode()
    now = int(time.time())
    invalid = [
        ('PREFIX', 'jwt-access-token:PASSWORD:forged'), ('MALFORMED', 'malformed'),
        ('ALG-NONE', encode({'alg': 'none'}) + '.' + encode(claims) + '.'),
        ('WRONG-SIGNATURE', signed(private='untrusted')),
        ('UNKNOWN-KID', signed(kid='unknown')), ('WRONG-ISSUER', signed({'iss': 'https://other.invalid'})),
        ('WRONG-AUDIENCE', signed({'aud': ['other']})), ('EXPIRED', signed({'iat': now - 600, 'nbf': now - 600, 'exp': now - 120})),
        ('FUTURE', signed({'iat': now + 120, 'nbf': now + 120, 'exp': now + 500})),
        ('MISSING-EXP', signed(remove=['exp'])), ('MISSING-IAT', signed(remove=['iat'])),
        ('MISSING-NBF', signed(remove=['nbf'])), ('NOT-ACCESS', signed({'token_use': 'refresh'})),
        ('SCALAR-ROLE', signed({'roles': 'ADMIN'})), ('LIFETIME', signed({'exp': now + 7200})),
        ('TAMPERED-PAYLOAD', token.split('.')[0] + '.' + encode({**claims, 'roles': ['ADMIN']}) + '.' + token.split('.')[2]),
    ]
    for label, raw in invalid:
        code, _, _ = request(gw, '/members', headers={'Authorization': 'Bearer ' + raw})
        verify('JWT-' + label, code == 401, f'HTTP {code}; invalid token rejected')
    code, _, _ = request(gw, '/members', headers={'Authorization': 'Bearer ' + signed(kid='next', private='next')})
    verify('JWT-ROTATION-TRUSTED-KEY', code == 200, 'pre-distributed next public key accepted')
    ops = {'Authorization': 'Bearer ' + signed({'roles': ['OPERATIONS']})}
    code, _, _ = request(gw, '/trace-events', headers=ops)
    verify('OPS-TRACE-READ', code == 200, 'operations role may read Trace')
    code, _, _ = request(gw, '/configs', {'configKey': 'ops-denied', 'configValue': 'x'}, headers=ops)
    verify('OPS-WRITE-DENIED', code == 403, 'operations role cannot perform administrative write')
    code, _, _ = request(gw, '/users', headers={**bearer, 'X-Roles': 'ADMIN', 'X-Service-Id': 'gateway-service', 'X-Service-Token': 'forged'})
    verify('HEADER-PRIVILEGE-FORGERY', code == 403, 'untrusted identity/role headers do not grant privileges')

    def credentials(service, target):
        props = {}
        for line in Path(config['files'][service]).read_text().splitlines():
            key, value = line.split('=', 1);props[key] = value
        return {'X-Service-Id': props['identity.id'], 'X-Service-Token': props['outbound.' + target]}
    for service, path in [('auth-service', '/users'), ('config-service', '/configs'), ('member-service', '/members'),
                          ('hr-service', '/hr/employees/EMPLOYEE-10001'), ('trace-service', '/trace-events')]:
        code, _, _ = request(ports[service], path, headers=admin_headers)
        verify('DIRECT-BYPASS-' + service, code == 401, f'JWT alone cannot bypass service boundary, HTTP {code}')
    connection = http.client.HTTPConnection('127.0.0.1', ports['trace-service'], timeout=5)
    connection.putrequest('POST', '/trace-events')
    writer = credentials('member-service', 'trace-service')
    connection.putheader('X-Service-Id', writer['X-Service-Id'])
    connection.putheader('X-Service-Id', 'auth-service')
    connection.putheader('X-Service-Token', writer['X-Service-Token'])
    connection.putheader('Content-Length', '2');connection.putheader('Content-Type', 'application/json')
    connection.endheaders(b'{}')
    response = connection.getresponse();status = response.status;response.read();connection.close()
    verify('SERVICE-DUPLICATE-IDENTITY', status == 401, 'ambiguous service identity headers rejected')
    code, _, _ = request(ports['member-service'], '/members', headers=credentials('gateway-service', 'member-service'))
    verify('SERVICE-ONLY-NO-USER', code == 401, 'service credential alone cannot impersonate a user')
    code, _, _ = request(ports['member-service'], '/members', headers={**admin_headers, 'X-Service-Id': 'gateway-service', 'X-Service-Token': 'x' * 64})
    verify('SERVICE-WRONG-TOKEN', code == 401, 'invalid internal credential rejected')
    code, _, _ = request(ports['auth-service'], '/users', headers={**admin_headers, **credentials('gateway-service', 'member-service')})
    verify('SERVICE-CROSS-TARGET-REUSE', code == 401, 'a receiver cannot reuse its Gateway credential at another service')
    code, _, _ = request(ports['trace-service'], '/trace-events', headers={**admin_headers, **credentials('member-service', 'trace-service')})
    verify('WRITER-CANNOT-READ', code == 403, 'Trace writer credential cannot query operations data')
    fixture_id = uuid.uuid4().hex
    event = {'traceId': fixture_id, 'eventType': 'SECURITY_TEST', 'sourceService': 'forged-service'}
    code, _, _ = request(ports['trace-service'], '/trace-events', event)
    verify('COLLECTOR-UNAUTH-WRITE', code == 401, 'unauthenticated event injection denied')
    code, _, _ = request(ports['trace-service'], '/trace-events', event, headers=credentials('member-service', 'trace-service'))
    _, _, rows = request(gw, '/trace-events/trace-id/' + fixture_id, headers=admin_headers)
    verify('COLLECTOR-BOUND-SOURCE', code == 200 and len(rows) == 1 and rows[0].get('sourceService') == 'member-service',
           'collector derives source identity from authenticated caller, not payload')

    # Opaque refresh rotation, replay family revocation, logout and concurrent reuse.
    code, _, rotated = request(ui, '/api/auth/refresh', {'refreshToken': refresh}, headers={'Authorization': 'Bearer expired'})
    verify('REFRESH-ROTATE', code == 200 and rotated.get('refreshToken') != refresh and bool(rotated.get('accessToken')),
           'UI strips stale access header and rotates refresh token')
    code, _, _ = request(gw, '/auth/refresh', {'refreshToken': refresh})
    verify('REFRESH-REPLAY', code == 401, 'used refresh token rejected')
    code, _, _ = request(gw, '/auth/refresh', {'refreshToken': rotated.get('refreshToken')})
    verify('REFRESH-FAMILY-REVOKED', code == 401, 'replay revokes descendant refresh token')
    def login():
        return request(gw, '/auth/login', {'userId': user, 'password': password, 'authType': 'PASSWORD'})[2]
    fresh = login()
    code, _, _ = request(ui, '/api/auth/logout', {'refreshToken': fresh['refreshToken']})
    verify('LOGOUT', code == 204, 'logout revokes refresh family')
    code, _, _ = request(gw, '/auth/refresh', {'refreshToken': fresh['refreshToken']})
    verify('LOGOUT-REUSE', code == 401, 'logged-out refresh cannot be reused')
    fresh = login()
    def rotate(_):
        return request(gw, '/auth/refresh', {'refreshToken': fresh['refreshToken']})
    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(rotate, range(2)))
    verify('REFRESH-CONCURRENT', sorted(row[0] for row in results) == [200, 401], 'exactly one rotation wins')
    winner = next((row[2] for row in results if row[0] == 200), {})
    code, _, _ = request(gw, '/auth/refresh', {'refreshToken': winner.get('refreshToken')})
    verify('CONCURRENT-FAMILY-REVOKED', code == 401, 'concurrent reuse invalidates winner descendant')
