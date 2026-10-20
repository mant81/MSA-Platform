#!/usr/bin/env python3
"""Foreground single-VM development launcher with private local fixture files."""
import argparse
import os
from pathlib import Path
import shutil
from prepare_local import ROOT, SERVICES, create, environment


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('service', choices=SERVICES + ['test-service'])
    parser.add_argument('--port', type=int)
    args = parser.parse_args()
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
    jar = ROOT / args.service / 'build/libs' / (args.service + '-0.1.0.jar')
    if not java or not jar.exists():
        parser.error('Install Java 25 and build the repository first')
    config = create(ROOT / 'build/security/local-v3')
    command = [java, '-Xms64m', '-Xmx256m', '-jar', str(jar)]
    if args.port is not None:
        command.append('--server.port=' + str(args.port))
    os.execvpe(java, command, environment(config, args.service))

if __name__ == '__main__':
    main()
