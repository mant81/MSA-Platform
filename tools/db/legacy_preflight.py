#!/usr/bin/env python3
"""Read-only PostgreSQL V1 preflight. Uses configured psql/PGSERVICE/PGPASSFILE; never prints data."""
import argparse
import subprocess
CHECKS={
 'member':{
  'duplicate_member_no':'select count(*) from (select member_no from member group by member_no having count(*)>1) t',
  'duplicate_process_id':'select count(*) from (select process_id from member_process group by process_id having count(*)>1) t',
  'duplicate_step':'select count(*) from (select process_id,step_id from member_process_step group by process_id,step_id having count(*)>1) t',
 },
 'auth':{'password_reset_required':"select count(*) from auth_user where password_hash !~ '^\\$2[aby]\\$'",'invalid_role':"select count(*) from auth_user where role not in ('USER','OPERATIONS','ADMIN')"},
 'trace':{'missing_authenticated_source':'select count(*) from trace_event where source_service is null or source_service=\'\''},
}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--service',choices=CHECKS,required=True);args=p.parse_args();blocked=False
 for name,sql in CHECKS[args.service].items():
  try:
   result=subprocess.run(['psql','--no-password','-X','-At','-v','ON_ERROR_STOP=1','-c',sql],capture_output=True,text=True,check=True,timeout=30)
   count=int(result.stdout.strip());blocked|=count>0;print(name+': '+str(count))
  except (OSError,ValueError,subprocess.SubprocessError):
   print('Preflight failed: verify psql connection and exact V1 schema; no baseline or data change performed.');return 2
 print('BLOCKED: resolve counts before migration' if blocked else 'PASS: counts only; schema review, backup and explicit baseline still required')
 return 1 if blocked else 0
if __name__=='__main__':raise SystemExit(main())
