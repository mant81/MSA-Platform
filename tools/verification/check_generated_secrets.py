#!/usr/bin/env python3
"""Compare this project's generated fixture secrets against Git candidate files, without printing values.
This narrow check is not a general secret scanner or vulnerability assessment.
"""
import json
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[2]

def main():
    values=set();fixtures=0
    def local(path):
        result=Path(path).resolve()
        if not result.is_relative_to((ROOT/'build').resolve()):raise RuntimeError('Fixture path escaped ignored build directory')
        return result
    for path in (ROOT/'build').rglob('fixture.json'):
        config=json.loads(path.read_text())
        if not {'directory','files','adminPassword'}.issubset(config):continue
        fixtures+=1;values.add(config['adminPassword'].encode())
        for file in config['files'].values():
            for line in local(file).read_text().splitlines():
                if '=' in line:
                    key,value=line.split('=',1)
                    if key!='identity.id':values.add(value.encode())
        for private in local(config['directory']).glob('*.key'):values.add(private.read_bytes())
    candidates=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT).split(b'\0')
    count=0
    for name in candidates:
        if not name:continue
        path=ROOT/name.decode()
        if not path.is_file():continue
        count+=1;data=path.read_bytes()
        if any(value and value in data for value in values):
            print('FAIL: generated credential material detected in a Git candidate; values withheld.');return 1
    print(f'PASS: {count} Git candidate files checked against {fixtures} locally generated fixture sets; no values disclosed.')
    return 0
if __name__=='__main__':raise SystemExit(main())
