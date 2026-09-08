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
paired physical dimensions while still requiring a strictly positive paired aggregate advantage; it
does not lower a `1.0` pass fraction, delete an outlier, or convert a stochastic observation into a
faction-wide modifier. `Stage22CorePairPairedMetrics` mirrors this reduction in Java acceptance tests,
and `tools/stage22/summarize_evidence.py` independently uses the seed pair as the sampling unit.

The statistics output preserves source-file SHA-256, raw run count and paired seed count. Statistics
include mean, median, nearest-rank p05/p95, DEFAULT-minus-MIRRORED mean and a normal-approximation 95%
interval for the mean of **independent seed pairs**. The two mirrored runs are not treated as
independent samples. These diagnostic intervals are not themselves a balance acceptance criterion or
a claim about campaign victory. The directional acceptance assertion remains on the declared raw
physical dimensions; interval/percentile output is review evidence and outlier diagnostics.

Operational vector lanes also replay min/median/max coordinates on one declared raw diagnostic
metric, including both mirrored assignments, and require exact repeat equality. Patrol traces retain
sampled actor contacts, authorization, ammunition, protection and damage. B11 retains actual common
control fingerprints for linked/broken sender/broken receiver states. B13 retains the three exact
committed encounter exits after ordinary save/restore. Raw tactical lanes retain every sampled phase
for every coordinate. Selection references allow review against the unabridged original vectors.

A final balance report must still distinguish bounded authority evidence from complete campaign
trajectories, and must include actual recorded B18–B20 human responses. No generated statistic waives
those remaining contracts.
