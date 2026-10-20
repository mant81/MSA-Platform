#!/usr/bin/env python3
"""Create private, ignored development identities. Never prints key/credential values."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SERVICES = ['gateway-service', 'auth-service', 'member-service', 'config-service', 'hr-service', 'trace-service']
TRUSTED = {
    'gateway-service': [], 'auth-service': ['gateway-service'], 'member-service': ['gateway-service'],
    'config-service': ['gateway-service'], 'hr-service': ['gateway-service', 'member-service'],
    'trace-service': ['gateway-service', 'auth-service', 'member-service', 'config-service'],
}

def create(directory):
    directory = Path(directory).resolve()
    directory.mkdir(parents=True, exist_ok=True);directory.chmod(0o700)
    config_file = directory / 'fixture.json'
    if config_file.exists():
        config = json.loads(config_file.read_text())
        if config.get('formatVersion') != 2:
            raise RuntimeError('Legacy local fixtures must not be overwritten; use a new ignored build directory')
        return config
    public = directory / 'public';public.mkdir(mode=0o700)
    for kid in ['current', 'next', 'untrusted']:
        private = directory / (kid + '.key')
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(private)],
                       check=True, capture_output=True);private.chmod(0o600)
        if kid != 'untrusted':
            subprocess.run(['openssl', 'pkey', '-in', str(private), '-pubout', '-out', str(public / (kid + '.pem'))],
                           check=True, capture_output=True)
    tokens = {(caller, target): secrets.token_hex(32) for target, callers in TRUSTED.items() for caller in callers}
    files = {}
    for name in SERVICES:
        file = directory / (name + '.properties')
        file.write_text('identity.id=' + name + '\n' +
                        ''.join('outbound.' + target + '=' + token + '\n' for (caller, target), token in tokens.items() if caller == name) +
                        ''.join('trusted.' + caller + '=' + tokens[(caller, name)] + '\n' for caller in TRUSTED[name]))
        file.chmod(0o600);files[name] = str(file)
    config = {'formatVersion': 2, 'directory': str(directory), 'public': str(public), 'private': str(directory / 'current.key'),
              'files': files, 'adminUser': 'localadmin', 'adminPassword': secrets.token_urlsafe(24)}
    config_file.write_text(json.dumps(config));config_file.chmod(0o600)
    return config


def environment(config, service):
    env = os.environ.copy()
    for key in ['MSA_JWT_PRIVATE_KEY_FILE', 'MSA_BOOTSTRAP_ADMIN_USER', 'MSA_BOOTSTRAP_ADMIN_PASSWORD', 'MSA_SERVICE_CREDENTIALS_FILE']:
        env.pop(key, None)
    env['MSA_JWT_PUBLIC_KEY_DIRECTORY'] = config['public']
    env['MSA_JWT_KEY_ID'] = 'current'
    env['MSA_JWT_ISSUER'] = 'https://msa.local/auth';env['MSA_JWT_AUDIENCE'] = 'msa-platform'
    if service in config['files']:
        env['MSA_SERVICE_CREDENTIALS_FILE'] = config['files'][service]
    if service == 'auth-service':
        env['MSA_JWT_PRIVATE_KEY_FILE'] = config['private']
        env['MSA_BOOTSTRAP_ADMIN_USER'] = config['adminUser']
        env['MSA_BOOTSTRAP_ADMIN_PASSWORD'] = config['adminPassword']
    return env

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', default=str(ROOT / 'build/security/local-v2'))
    args = parser.parse_args()
    path = Path(args.directory).resolve()
    if not path.is_relative_to(ROOT / 'build'):
        parser.error('Development fixtures must be inside the ignored repository build directory')
    config = create(path)
    print('Local security fixtures ready:', config['directory'])
    print('Private keys and credentials were not printed. These are not production credentials.')
