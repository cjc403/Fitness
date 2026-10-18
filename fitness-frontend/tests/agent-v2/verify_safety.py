"""Build an auditable safety coverage report from actual JUnit, Node and live evidence.
Missing, skipped or failed evidence is NOT_RUN/FAILED, never an assumed pass.
"""
import argparse
import datetime
import json
import pathlib
import re
import xml.etree.ElementTree as ET


def verify(backend, frontend, live):
    tests = {}
    for report in pathlib.Path(backend).glob('TEST-*.xml'):
        for case in ET.parse(report).getroot().iter('testcase'):
            tests[(case.get('classname'), case.get('name'))] = not any(
                case.find(tag) is not None for tag in ('failure', 'error', 'skipped'))
    logs = '\n'.join(pathlib.Path(p).read_text(encoding='utf-8-sig') for p in frontend)
    here = pathlib.Path(__file__).resolve().parent
    frozen = json.loads((here / 'cases.json').read_text(encoding='utf-8'))['safetyCases']
    matrix = json.loads((here / 'safety-evidence.json').read_text(encoding='utf-8'))['cases']
    assert [(c['id'], c['name']) for c in frozen] == [(c['id'], c['name']) for c in matrix]
    lost = json.loads(pathlib.Path(live).read_text(encoding='utf-8-sig')) if live else {}
    results = []
    for case in matrix:
        proofs = []
        for item in case['evidence']:
            if item['suite'] == 'backend':
                state = tests.get((item['className'], item['name']))
            elif item['suite'] == 'frontend':
                state = bool(re.search(r'^✔ ' + re.escape(item['name']) + r' \(', logs, re.MULTILINE))
            else:
                rows = lost.get('cases', [])
                state = bool(rows) and not lost.get('errors') and not lost.get('blocked') and all(
                    c.get('status') == 'SUCCEEDED' and c.get('sameDocument') is True
                    and c.get('responseLost') is True and c.get('afterWrites') == 1
                    and c.get('duplicateRejected') is True for c in rows)
            proofs.append({**item, 'status': 'PASSED' if state is True else 'NOT_RUN' if state is None else 'FAILED'})
        results.append({**case, 'evidence': proofs, 'status': 'PASSED' if all(p['status'] == 'PASSED' for p in proofs) else 'NOT_VERIFIED'})
    return {'at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'scope': 'protocol/component/real-PG plus explicitly linked live evidence',
            'cases': results, 'total': len(results), 'passed': sum(c['status'] == 'PASSED' for c in results)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--backend', required=True, help='Fresh package target/surefire-reports')
    parser.add_argument('--frontend', action='append', required=True)
    parser.add_argument('--live-lost-response')
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    result = verify(args.backend, args.frontend, args.live_lost_response)
    pathlib.Path(args.out).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'total': result['total'], 'passed': result['passed'], 'unverified': [c['name'] for c in result['cases'] if c['status'] != 'PASSED']}, ensure_ascii=False))
    raise SystemExit(0 if result['passed'] == result['total'] == 40 else 1)
