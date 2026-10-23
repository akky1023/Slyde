"""Preserve observed editor events and panel-relative strokes as future test material."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

from capture import decode_touch


def gestures(frames, width, height):
    active = {}
    completed = []
    for frame in frames:
        if frame['type'] == 'dropped':
            for key in list(active):
                if key[0] == frame['device']:
                    item = active.pop(key)
                    item['incomplete'] = True
                    completed.append(item)
            continue
        for point in frame['pointers']:
            key = (frame['device'], point['slot'], point['trackingId'])
            item = active.setdefault(key, {'device': key[0], 'slot': key[1], 'trackingId': key[2],
                'startUptimeMs': frame['timeSeconds'] * 1000, 'points': []})
            sample = {'uptimeMs': frame['timeSeconds'] * 1000, **point}
            if 'normalizedX' in point and 'normalizedY' in point:
                sample['screenX'] = point['normalizedX'] * width
                sample['screenY'] = point['normalizedY'] * height
            item['points'].append(sample)
        for point in frame['ended']:
            key = (frame['device'], point['slot'], point.get('trackingId'))
            if key in active:
                item = active.pop(key)
                item['endUptimeMs'] = frame['timeSeconds'] * 1000
                completed.append(item)
    for item in active.values():
        item['incomplete'] = True
        completed.append(item)
    return completed


def analyze(events, strokes):
    cases = []
    current = None
    sequence_issues = []
    last = {}
    for event in events:
        sid = event['sessionId']
        if event['seq'] != last.get(sid, 0) + 1:
            sequence_issues.append({'sessionId': sid, 'seq': event['seq']})
        last[sid] = event['seq']
        if event['type'] == 'caseStart':
            current = {'sessionId': sid, **event['data'], 'startUptimeMs': event['uptimeMs'], 'events': []}
            cases.append(current)
        if current is not None and current['sessionId'] == sid:
            current['events'].append(event)
        if event['type'] == 'caseEnd' and current is not None:
            snapshot = event['data']['snapshot']
            current.update(endUptimeMs=event['uptimeMs'], actual=snapshot['text'], finalSnapshot=snapshot,
                matchesExpected=current['expected'] == snapshot['text'], gestureValidation='unreviewed')
            current['strokes'] = [stroke for stroke in strokes
                if stroke['startUptimeMs'] >= current['startUptimeMs']
                and stroke.get('endUptimeMs', float('inf')) <= current['endUptimeMs']]
            # Includes touches on app controls and candidate rows; no automatic key labeling.
            current['strokeSelection'] = 'all_complete_strokes_in_case_time_window'
            current = None
    return {'schema': 1, 'sequenceIssues': sequence_issues, 'eventCount': len(events),
        'eventTypes': dict(Counter(e['type'] for e in events)), 'cases': cases,
        'sessionEnded': any(e['type'] == 'sessionEnd' for e in events),
        'coordinateWarning': 'panel-relative, not keyboard-relative; keyboard bounds and key labels require video review'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sessions', type=Path, required=True)
    parser.add_argument('--capture', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    events = [json.loads(line) for line in args.sessions.read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    metadata = json.loads((args.capture / 'metadata.json').read_text(encoding='utf-8-sig'))
    device = next(e['data'] for e in events if e['type'] == 'sessionStart')
    with (args.capture / 'getevent.txt').open(encoding='utf-8', errors='replace') as source:
        frames = list(decode_touch(source, metadata['axes']))
    strokes = gestures(frames, device['displayWidthPx'], device['displayHeightPx'])
    result = analyze(events, strokes)
    result['source'] = {'sessions': str(args.sessions.resolve()), 'capture': str(args.capture.resolve()),
        'sessionSha256': hashlib.sha256(args.sessions.read_bytes()).hexdigest()}
    result['device'] = device
    result['touchFrameCount'] = len(frames)
    result['gestureCount'] = len(strokes)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'observations.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    with (args.output / 'touches.jsonl').open('w', encoding='utf-8') as output:
        for frame in frames:
            output.write(json.dumps(frame, ensure_ascii=False) + '\n')
    with (args.output / 'gestures.jsonl').open('w', encoding='utf-8') as output:
        for stroke in strokes:
            output.write(json.dumps(stroke, ensure_ascii=False) + '\n')
    lines = ['# アルテ実入力の記録結果', '',
        f"{len(events)}イベント、{len(result['cases'])}例題。シーケンス欠落: {len(result['sequenceIssues'])}。",
        '', '| 例題 | 目標 | 実際の結果 | 文字列一致 | 完結したタッチ操作数 |', '| --- | --- | --- | --- | --- |']
    for case in result['cases']:
        lines.append(f"| {case['caseId']} | {case['expected']} | {case.get('actual') or '（空）'} | {'一致' if case.get('matchesExpected') else '不一致'} | {len(case.get('strokes', []))} |")
    lines += ['', '操作数にはアプリのボタンや変換候補のタッチも含みます。文字列の一致だけでは、指定のグライド操作の成功を保証しません。',
        '', '原ログの未確定文字・削除・訂正を保持しています。数値座標は画面全体に対する座標で、キーボード内の座標ではありません。']
    (args.output / 'REPORT.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(json.dumps({'events': len(events), 'cases': len(result['cases']), 'matchingCases': sum(bool(c.get('matchesExpected')) for c in result['cases']),
        'sequenceIssues': result['sequenceIssues'], 'touchFrames': len(frames), 'gestures': len(strokes)}, ensure_ascii=True))


if __name__ == '__main__':
    main()
