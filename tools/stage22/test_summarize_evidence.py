import json
import tempfile
import unittest
from pathlib import Path

from summarize_evidence import summarize


class PairedStatisticsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'test.json'
        self.doc = {'workingTreeDirty': False, 'buildCommitSha': 'a' * 40,
                    'contentFingerprint': 'b' * 64, 'evidenceId': 'test-only-fixture',
                    'knownLimitations': ['Artificial statistics unit fixture; never human or balance evidence.'],
                    'evidence': {'observations': [
                        {'seed': seed, 'permutation': p, 'metrics': {'value': v}, 'hardRuleBreaches': []}
                        for seed, p, v in [(1, 'DEFAULT', 0), (1, 'MIRRORED', 10),
                                           (2, 'DEFAULT', 10), (2, 'MIRRORED', 20)]]}}

    def run_summary(self):
        self.path.write_text(json.dumps(self.doc))
        return summarize(self.path, 2)

    def test_uncertainty_uses_two_independent_pairs_instead_of_four_runs(self):
        report = self.run_summary()
        metric = report['metrics']['value']
        self.assertEqual(2, report['pairedSeedCount'])
        self.assertEqual(10, metric['mean'])
        self.assertEqual(-10, metric['defaultMinusMirroredMean'])
        self.assertAlmostEqual(9.8, metric['approximate95PercentPairMeanHalfWidth'])
        self.assertEqual({'value': 10, 'seed': 1, 'permutation': 'MIRRORED'}, metric['representative'])

    def test_rejects_duplicates_missing_pairs_nonfinite_breaches_and_dirty_source(self):
        mutations = [
            lambda d: d['evidence']['observations'].append(d['evidence']['observations'][0]),
            lambda d: d['evidence']['observations'].pop(),
            lambda d: d['evidence']['observations'][0]['metrics'].update(value=float('nan')),
            lambda d: d['evidence']['observations'][0]['hardRuleBreaches'].append('test-breach'),
            lambda d: d.update(workingTreeDirty=True),
        ]
        original = json.dumps(self.doc)
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                self.doc = json.loads(original)
                mutation(self.doc)
                with self.assertRaises(ValueError):
                    self.run_summary()


if __name__ == '__main__':
    unittest.main()
