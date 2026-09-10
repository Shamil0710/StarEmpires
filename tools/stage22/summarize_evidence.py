"""Summarize measured paired vectors; never turns diagnostic evidence into scenario sign-off."""
import argparse
import hashlib
import json
import math
import statistics
from pathlib import Path


def summarize(path, minimum_pairs):
    raw = path.read_bytes()
    doc = json.loads(raw)
    if doc['workingTreeDirty'] or len(doc['buildCommitSha']) != 40:
        raise ValueError(f'{path}: clean, exact source identity required')
    evidence = doc['evidence']
    rows = evidence.get('observations') if isinstance(evidence, dict) else evidence
    if not isinstance(rows, list) or not rows:
        raise ValueError(f'{path}: no raw paired observations')
    pairs = {}
    metrics_by_run = {}
    for row in rows:
        coordinate = row.get('coordinate', row)
        seed, permutation = coordinate['seed'], coordinate['permutation']
        if permutation not in ('DEFAULT', 'MIRRORED') or (seed, permutation) in metrics_by_run:
            raise ValueError(f'{path}: duplicate/invalid paired coordinate')
        if row.get('hardRuleBreaches'):
            raise ValueError(f'{path}: observed hard-rule breach')
        metrics = row.get('metrics')
        if metrics is None:  # Full tactical causal trajectory, not a batch ResultVector.
            metrics = {key: row[key] for key in ('empireVisibleTicks', 'unionVisibleTicks', 'unauthorizedTargetTicks')}
            if metrics['unauthorizedTargetTicks'] != 0:
                raise ValueError(f'{path}: unauthorized tactical target')
            for actor in row['phases'][-1]['weapons']:
                for key in ('shotsFired', 'ammunitionRounds'):
                    metrics[f"actor_{actor['entityId']}_{key}"] = actor[key]
        if not metrics or any(isinstance(x, bool) or not isinstance(x, (int, float)) or not math.isfinite(x) for x in metrics.values()):
            raise ValueError(f'{path}: missing/non-finite numeric metrics')
        metrics_by_run[(seed, permutation)] = metrics
        pairs.setdefault(seed, set()).add(permutation)
    if len(pairs) < minimum_pairs or any(p != {'DEFAULT', 'MIRRORED'} for p in pairs.values()):
        raise ValueError(f'{path}: expected at least {minimum_pairs} complete pairs')
    keys = set(next(iter(metrics_by_run.values())))
    if any(set(row) != keys for row in metrics_by_run.values()):
        raise ValueError(f'{path}: metric keys drift')
    results = {}
    for key in sorted(keys):
        ordered = sorted((values[key], seed, permutation) for (seed, permutation), values in metrics_by_run.items())
        values = [x[0] for x in ordered]
        paired_means = [statistics.mean(metrics_by_run[(seed, p)][key] for p in ('DEFAULT', 'MIRRORED')) for seed in sorted(pairs)]
        differences = [metrics_by_run[(seed, 'DEFAULT')][key] - metrics_by_run[(seed, 'MIRRORED')][key] for seed in sorted(pairs)]
        # The independent sampling unit is a SEED PAIR, never the two mirrored runs.
        half_width = 1.96 * statistics.stdev(paired_means) / math.sqrt(len(pairs)) if len(pairs) > 1 else None
        median = statistics.median(values)
        representative = min(ordered, key=lambda x: (abs(x[0] - median), x[1], x[2]))
        def ref(row):
            return {'value': row[0], 'seed': row[1], 'permutation': row[2]}
        results[key] = {
            'mean': statistics.mean(paired_means), 'median': median,
            'p05NearestRank': values[max(0, math.ceil(.05 * len(values)) - 1)],
            'p95NearestRank': values[math.ceil(.95 * len(values)) - 1],
            'approximate95PercentPairMeanHalfWidth': half_width,
            'defaultMinusMirroredMean': statistics.mean(differences),
            'minimum': ref(ordered[0]), 'representative': ref(representative), 'maximum': ref(ordered[-1]),
        }
    return {'evidenceId': doc['evidenceId'], 'sourceFile': path.name,
            'sha256': hashlib.sha256(raw).hexdigest(), 'buildCommitSha': doc['buildCommitSha'],
            'contentFingerprint': doc['contentFingerprint'], 'pairedSeedCount': len(pairs),
            'runCount': len(metrics_by_run), 'metrics': results, 'knownLimitations': doc['knownLimitations'],
            'scope': 'Diagnostics only; percentile/normal-approximation intervals do not approve a freeze or human gate.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--minimum-pairs', type=int, default=100)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    files = [p for p in sorted(args.directory.glob('*.json')) if p != args.output and not p.stem.endswith('-traces')]
    if not files or args.minimum_pairs < 2:
        parser.error('raw evidence and at least two independent pairs are required')
    reports = [summarize(p, args.minimum_pairs) for p in files]
    identities = {(r['buildCommitSha'], r['contentFingerprint']) for r in reports}
    if len(identities) != 1:
        raise ValueError('Mixed source/content identities cannot form one evidence batch')
    args.output.write_text(json.dumps({'schemaVersion': 1, 'reports': reports}, indent=2, allow_nan=False) + '\n')
    print(f'Validated {len(reports)} raw reports; minimum {args.minimum_pairs} complete pairs each')


if __name__ == '__main__':
    main()
