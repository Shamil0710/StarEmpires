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

## 6. Runtime integration evidence — 2026-09-04

The current continuation adds these probes; neither the scenario suite nor M22.6 is signed off:

| Evidence | Executed authority and scope | Remaining boundary |
| --- | --- | --- |
| `Stage22CorePairFreightProbe` / B05 | 100 paired load-sensitivity seeds; finite Stage-18 stock loading; exact Stage-20 lost-lot provenance; surviving alternate-route delivery; byte-stable freight save and continuation; no reused destroyed ID | Two declared routes, not a stochastic generated-campaign balance batch. Stage-21D alternate-route planning does not rewrite an in-flight Stage-20 order. No automatic loss-to-salvage/replacement bridge is claimed. |
| `Stage22CorePairRecoveryProbe` / B03, B14 | Both exact destroyer fits at 25/50/75% damage; paid missing-yard-support construction; physical Stage-21G repair through the Stage-18 yard; material/work rejection; facility persistence and repair continuation | Full recovery still needs replacement and rolling-attrition curves. Starting construction kits and repair stock are explicit scenario resources. |

The repair probe exposed two integration defects that catalog-only tests did not exercise:

1. Neither industrial-station archetype includes the precision fabrication facility required by the
   authored core yards. Stage-18H completion now projects a validated, fully paid construction order
   into the existing station roster. The generated industrial restorer replays persisted completed
   orders before validating installed facility identities. Power/labor allocations remain separate;
   restoring a newly installed facility does not activate it for free. Existing save schema is retained.
2. The Stage-21G component-repair path could encounter an invalid runtime shield contract after
   consuming repair inputs. It now validates restored emitter capability before settlement. The raw
   authoring catalogs remain distinct from the existing M22.6 runtime-completed core catalog; the
   incompatible-catalog regression checks unchanged stock, yard work and engineering state.

`target/stage22-evidence/` retains source SHA, dirty-tree flag, content fingerprint, raw observations
and limitations. CI archives these small reports on the exact tested checkout, including the PR
merge-checkout SHA where applicable. A dirty local discovery run is not freeze evidence.

### Tactical controls and a causal collision defect

`Stage22CorePairTacticalProbe` runs both exact destroyer fits with the common Stage-19 control,
sensor, flight, launcher, protection and compartment-damage authorities. Thirty paired initial
geometries cover normal engagement, either faction's destroyed sensor and a four-round magazine.
Reports retain raw starting mass/crew/power/ammunition/reaction-mass burdens and timed state vectors.
The common tactical policy is explicitly identified; equal role is not equal economic burden.
An engineering-state save at the scenario start must reproduce the complete sampled continuation.
This does not claim that an in-flight battle has a save/restore contract.

The first physical traces exposed repeated armor/shield settlement while a penetrating residual
was still traversing the same hull. A focused reproducer observed two impacts for one crossing.
The shared Stage-19 weapon runtime now remembers resolved surface contacts until the residual
leaves that hull, preserves the physical residual, and permits a later re-entry. The contact state
is included in deterministic runtime fingerprints. Both native and externally resolved residuals
are covered; no faction modifier or projectile deletion hides the repeated-impact defect.

Freeze discovery schema 2 also includes the **runtime-completed** engineering catalog, ammunition,
launchers and their schema/migration versions. Raw package fingerprints alone omitted the executable
sensor/shield mode projection and the launcher/ammunition catalogs. These additional values remain
discovery evidence until the full balance and human-review gates are satisfied.

### Committed encounter and treaty boundaries

The exact Stage-19 encounter resolver now accepts an explicit engineering/protection/ammunition/
launcher universe while its default constructor retains legacy compatibility. Ordnance, defense,
decoy and deception layers take weapon catalogs from their shared weapon authority. Previously those
layers independently reloaded the old Stage-17.5I weapon pack, so a core kinetic module failed the
guided-mount preflight even though the direct kinetic duel worked.

`Stage22CorePairEncounterContinuationProbe` exercises three bounded encounters through the complete
Stage-19 stack. It compares uninterrupted and saved/reloaded encounter-boundary continuation in both
permutations, verifies that detached resolution does not mutate input world-owned components, and
rejects any fit substitution or replenishment of spent rounds/reaction mass. The existing
`Stage21EGeneratedWorldStage19Authority` already accepts a resolver; generated-world handoff/commit
acceptance with actual core ships is still needed before claiming the campaign integration closed.

`Stage22CorePairTreatyProbe` uses the actual `faction.imperial_directorate` and
`faction.industrial_combine` identities. It mirrors market-owner/visitor roles and verifies binary
saves while offered, active and breached. An offer grants nothing; acceptance produces mutual access
and a 0-bps exemption; breach restores denial and the declared 750-bps rate. This is authority and
continuation evidence, not a physical trade-volume recovery curve.

The [100-pair tactical archive](../evidence/stage22/m22_6/tactical-42ebd4cc/README.md) retains
the clean-source `42ebd4cc` diagnostic runs and raw vectors. It is not a signed-off balance report.

Full verification for `42ebd4cc` logged `BUILD SUCCESS`, 1,965 tests (zero failures/errors, one existing
skip), successful Javadoc/coverage/package and evidence upload. The workflow nevertheless ended
`cancelled` at the job's 15-minute boundary, so it is **not** a green merge gate. The job allowance is
now 20 minutes to include setup and cleanup; the Maven command, tests and coverage gates are unchanged.
