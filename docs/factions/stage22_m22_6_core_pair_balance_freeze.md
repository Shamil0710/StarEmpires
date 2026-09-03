# Stage 22 M22.6 — core pair balance / freeze

> Status: **IMPLEMENTATION IN PROGRESS**  
> Pair: `core.empire` / `core.industrial_union`  
> Stable identities: `faction.imperial_directorate` / `faction.industrial_combine`

## 1. Scope

M22.6 closes the first cross-package balance/freeze gate. It does not add a faction-owned combat,
economy, logistics, territory or recovery authority. Evidence must compose the accepted Stage 17.5,
18, 19, 20 and 21 authorities plus the authored Stage-22 package/profile/visual contracts.

The canonical scenario contract is `Stage22CorePairBalanceCatalog` version
`stage22.core_pair_balance_suite.v1`. B00-B20 are all present. B15-B17 are not deferred because the
repository already contains production-ready territorial transition/control, treaty/market access,
bounded policy review and finite Industrial Union retool authorities.

## 2. Equal-burden protocol

`Stage22CorePairExperimentProtocol` owns experiment coordinates only. It cannot change gameplay
randomness or outcomes.

For each seed it schedules exactly two runs:

1. default slot/topology assignment;
2. mirrored slot/topology/hazard assignment.

Faction identity and doctrine remain attached to the faction, never to the slot. The tuning schedule
contains 30 paired seeds; the release-candidate schedule contains 100 paired seeds for materially
stochastic scenario runners.

## 3. Diagnostic pair evidence

`Stage22CorePairBalanceEvidence` derives current burdens from the accepted package validators and
production authorities. It deliberately does not calculate a synthetic power score or expected
winner.

The pairwise hypothesis is:

- Empire gains value through preparation, capital protection, reserves, repair and preservation;
- Empire pays capital intensity, rare-node concentration and visible remote support mass;
- Industrial Union gains value through series production, replacement throughput and commonality;
- Industrial Union pays material hunger, correlated shared-network disruption and finite retool debt.

Current executable gates require:

- the exact same nine-role floor on both packages;
- legal nonzero capital/support burdens on both sides;
- visible tanker/repair support burden for remote projection;
- measurable Industrial Union series-production improvement;
- positive finite Union retool work/energy debt;
- correlated Union commonality disruption of at least 25%, materially worse than isolated loss;
- no faction-name shortcut in the pairwise contract.

These checks observe existing authorities and are not fed back into gameplay.

## 4. Freeze discovery

`Stage22CorePairFreezeManifest.captureCurrent()` gathers the semantic freeze surface:

- stable faction IDs;
- package fingerprints;
- production-manifest fingerprints;
- engineering fingerprints;
- manufacturing fingerprints;
- physical shipyard fingerprints;
- station-infrastructure fingerprints;
- profile fingerprints/schema versions;
- character-lineup fingerprints;
- Industrial Union production-sidecar save version;
- exact B00-B20 scenario versions;
- aggregate SHA-256 freeze fingerprint.

The first exact-head CI run prints these values as `M22_6_DISCOVERY_FREEZE|...`. They are discovery
values only. A later commit must replace discovery-only acceptance with literal expected pins before
the package can be called frozen.

## 5. Remaining closure work

This foundation is not M22.6 completion. Before freeze sign-off the branch still requires:

- executable cross-package B00-B17 evidence using the common production/runtime authorities rather
  than package-local smoke only;
- deterministic probe, exact replay/save continuation and paired result vectors;
- 30-seed tuning evidence and 100+ paired-seed RC regression for materially stochastic runners;
- representative/outlier causal traces;
- B18 causal-explanation evidence;
- B19 Empire-vs-Union grayscale silhouette evidence;
- B20 shared-style plus faction-overlay character evidence;
- literal freeze pins for manifests/profiles/fingerprints/migrations/scenario versions;
- final signed-off balance report and known-limitations section;
- full exact-head CI, guarded merge, post-merge `main` CI and docs-only roadmap closeout.

No Stage 23 implementation may start before those gates are complete.
