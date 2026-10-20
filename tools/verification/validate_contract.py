#!/usr/bin/env python3
"""Validate the proposed v1 event wire contract; this does not validate services."""
import copy
import json
from pathlib import Path
import sys

try:
    from jsonschema import Draft202012Validator, FormatChecker
except ImportError:
    sys.exit('Missing jsonschema: install tools/verification/requirements.txt in a virtualenv')

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'docs/contracts/v1'

def main():
    schema = json.loads((BASE / 'observation-event.schema.json').read_text())
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    valid = json.loads((BASE / 'examples/valid-events.json').read_text())
    for event in valid:
        validator.validate(event)
    invalid = []
    for name, patch in [
        ('legacy trace ID cannot be standard trace ID', {'traceId': '1234567890abcdef'}),
        ('zero trace ID', {'traceId': '0' * 32}),
        ('zero span ID', {'spanId': '0' * 16}),
        ('unsupported schema', {'schemaVersion': '2.0'}),
        ('unknown event', {'eventType': 'SERVICE_UP'}),
        ('invalid event UUID', {'eventId': 'invalid'}),
        ('invalid timestamp', {'occurredAt': 'yesterday'}),
        ('negative version', {'aggregateVersion': -1}),
        ('started cannot be terminal', {'status': 'SUCCEEDED'}),
        ('secret field excluded', {'password': 'fixture-only'}),
        ('client cannot set receivedAt', {'receivedAt': '2026-10-20T00:00:00Z'}),
    ]:
        invalid.append((name, dict(valid[0], **patch)))
    for field in schema['required']:
        event = copy.deepcopy(valid[0]);event.pop(field)
        invalid.append(('required ' + field, event))
    invalid.extend([
        ('finished needs duration', {k: v for k, v in valid[1].items() if k != 'durationMs'}),
        ('step needs execution ID', {k: v for k, v in valid[2].items() if k != 'stepExecutionId'}),
        ('attempt positive', dict(valid[2], attempt=0)),
        ('retryable boolean', dict(valid[4], retryable='yes')),
        ('error needs ID', {k: v for k, v in valid[4].items() if k != 'errorId'}),
        ('duration nonnegative', dict(valid[1], durationMs=-1)),
    ])
    for name, event in invalid:
        if validator.is_valid(event):
            raise AssertionError('Invalid fixture accepted: ' + name)
    print(f'PASS: schema valid, {len(valid)} valid examples accepted, {len(invalid)} invalid examples rejected')
    print('Scope: event shape only; delivery, state transitions and service support remain unverified.')

if __name__ == '__main__':
    main()
