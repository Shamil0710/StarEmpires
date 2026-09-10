# M22.6 reproducible paired machine regression

Status: executable candidate protocol; no freeze or human review is approved by this document.

The ordinary `clean verify` gate retains every existing assertion and its normal batch sizes.
`-Dstage22.evidenceProfile=rc` expands the existing seeded tactical, B06, B07, B09, B11,
B13 and cross-scenario dominance tests to the canonical 100 distinct seeds, each with DEFAULT and
MIRRORED assignments. Unknown profile names fail closed. This is test orchestration, not gameplay
configuration. Deterministic construction, treaty and persistence controls are still checked by the
full repository gate; repeating deterministic fixture IDs is not claimed as additional RNG coverage.

The `Stage 22 paired RC evidence` workflow runs seven independent lanes from the same push SHA when
its commit message contains `[m22-rc]`, or when manually dispatched. Each lane retains raw JSON,
Surefire output and `statistics.json`. Run it together with full Java-17 `clean verify` before
considering a candidate. A successful targeted lane cannot substitute for the full gate.

For example:

```bash
./mvnw --batch-mode --no-transfer-progress -Dstage22.evidenceProfile=rc -Dtest=Stage22CorePairRollingAttritionMachineEvidenceAcceptanceTest test
python3 tools/stage22/summarize_evidence.py target/stage22-evidence --minimum-pairs 100 --output target/stage22-evidence/statistics.json
```

Use a clean checkout/output directory for each lane. The summarizer rejects dirty-source evidence,
incomplete or duplicated pairs, non-finite metrics, observed **hard-rule** breaches and mixed
source/content identities. Hard rules are causal/integrity contracts that must hold on every run:
authority ownership, actor-bounded knowledge, physical arrival/admission, conservation, finite stores,
save/load continuation and other fail-closed invariants. They are not relaxed by the RC profile.

Materially stochastic outcome hypotheses are not hard rules merely because their expected advantage
is directional. In particular, surviving shield/compartment protection in B07/B09/Gate C keeps every
individual run, including inversions/outliers, in raw evidence. The canonical balance framework §6.2
requires DEFAULT and MIRRORED observations to be averaged **per seed pair before aggregation across
seeds**. M22.6 therefore evaluates the authored Empire survivability/robustness hypothesis on those
paired physical dimensions rather than requiring every individual run to point in the same direction.
It does not lower a `1.0` hard-rule pass fraction, delete an outlier, or convert a stochastic
observation into a faction-wide modifier.

For the declared B07/B09/Gate-C Empire survivability dimensions, the acceptance implementation uses
the same normal-approximation pair-mean arithmetic as the independent evidence summarizer:

```text
pairedDifference(seed) = mean(DEFAULT, MIRRORED)_Empire
                       - mean(DEFAULT, MIRRORED)_Union
sampleSd = sample standard deviation of pairedDifference over seeds
approx95HalfWidth = 1.96 * sampleSd / sqrt(pairCount)
approx95LowerBound = mean(pairedDifference) - approx95HalfWidth
```

The stochastic directional claim passes only when `approx95LowerBound > 0` for both surviving shield
reserve and mean compartment integrity. The independent sampling unit is the complete seed pair. This
is deliberately stricter than checking only a positive aggregate mean while preserving every
individual inversion in the archive. `Stage22CorePairPairedMetrics` implements this acceptance-side
reduction in Java and has fixed-arithmetic unit coverage; `tools/stage22/summarize_evidence.py`
independently computes the same pair-mean interval for retained evidence.

The statistics output preserves source-file SHA-256, raw run count and paired seed count. Statistics
also include mean, median, nearest-rank p05/p95, DEFAULT-minus-MIRRORED mean and the same
normal-approximation 95% interval for the mean of **independent seed pairs**. The two mirrored runs are
not treated as independent samples. For metrics that are not explicitly declared directional
acceptance dimensions, these interval/percentile values remain diagnostics and outlier-review aids;
they do not invent a campaign-victory criterion or waive scenario-specific contracts.

Operational vector lanes also replay min/median/max coordinates on one declared raw diagnostic
metric, including both mirrored assignments, and require exact repeat equality. Patrol traces retain
sampled actor contacts, authorization, ammunition, protection and damage. B11 retains actual common
control fingerprints for linked/broken sender/broken receiver states. B13 retains the three exact
committed encounter exits after ordinary save/restore. Raw tactical lanes retain every sampled phase
for every coordinate. Selection references allow review against the unabridged original vectors.

A final balance report must still distinguish bounded authority evidence from complete campaign
trajectories, and must include actual recorded B18–B20 human responses. No generated statistic waives
those remaining contracts.
