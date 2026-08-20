# Stage 20E — Representative Generated-World Seed Corpus v1

> Status: **PROVISIONAL STAGE-20E CLOSEOUT EVIDENCE SLICE**  
> Corpus: `stage20e.representative-seed-corpus.v1`  
> Probe profile: `stage20e.representative-production-probe-profile.v1`  
> Stage-22 policy/content review: **required**

## Purpose

Stage-20E now has a production-style seed probe and a provenance-backed bootstrap service-level requirement profile. The next roadmap requirement is representative batch evidence from the real generation path rather than hand-authored fixture worlds.

This slice establishes that evidence without inventing a global accepted-seed percentage target.

```text
fixed root seeds selected before outcomes
→ one shared versioned representative probe profile
→ real Stage20GeneratedWorldProductionProbe per seed
→ Stage20GeneratedWorldBatchAcceptance
→ machine-readable per-seed outcomes + failure histogram
→ main-only CI evidence artifact
→ inspect measured distribution
→ later freeze accepted evidence / recalibrate only from causal findings
```

## Fixed corpus

The v1 corpus is exactly the contiguous interval:

```text
1, 2, 3, ..., 16
```

The interval is part of source code and is chosen before observing v1 outcomes. No seed may be dropped, replaced or retried because it produces an inconvenient topology, scarcity pattern, economic failure or faction-start rejection.

The corpus is intentionally modest for the first repository-exact whole-world measurement: each root evaluates a four-region world with 8–10 systems per region and runs the complete production probe. If later runtime evidence justifies a larger corpus, that must be introduced as a new version rather than silently editing v1 after seeing results.

## Representative probe profile

`Stage20RepresentativeGeneratedWorldProbeProfile` removes the remaining production-probe assumptions from private test fixtures and exposes them as one versioned evidence policy.

### Explicit Stage-20/22-reviewable policy

The v1 profile selects:

- `Stage20MacroGalaxyGeometryGenerator.GenerationRequest.representative()` — four regions, 8–10 systems each;
- major hub: `station.infrastructure.trade_logistics_hub`;
- four `RESOURCE_FIELD_ANCHOR` points per system;
- pre-resource industrial station choice from:
  - `station.infrastructure.frontier_multipurpose`;
  - `station.infrastructure.high_tech_hub`;
  - `station.infrastructure.industrial_station`;
  - `station.infrastructure.refinery_complex`;
- two stable representative actors: `faction.alpha`, `faction.beta`;
- freight reference: `EARLY_CIVILIAN_FREIGHTER`;
- eight allocated representative freighters.

These are evidence/generation policy choices. They are **not** new production bonuses, hidden capacity, guaranteed final content or statements that every real faction starts with eight free freighters. Stage 22 must review the policy/content assumptions before final balance promotion.

### Derived physical/economic authority

The profile does not author convenient physics around those choices. It consumes:

- `Stage20TopologyQualityCalibrationProfile.deriveCurrent()`;
- `Stage20BootstrapRequirementCalibrationProfile.deriveCurrent()`;
- `Stage20FactionStartAcceptanceProfile.current()`;
- compatible `EARLY_CIVILIAN_FREIGHTER` data from `Stage20FtlCalibrationProfile.deriveCurrent()`;
- freight payload/provenance from `Stage20RepresentativePropulsionCatalogLoader.loadDefault()`.

Therefore spool/transit/cooldown, translated mass, payload, topology budgets and the current 50 kg/s water-ice / 25 kg/s metallic-ore bootstrap service level are not restated as independent magic constants in the corpus harness.

## Anti-rescue invariant

The representative profile is derived once before any seed outcome is known.

Forbidden:

```text
seed 7 lacks viable supply
→ allocate more freighters to seed 7

seed 11 fails start placement
→ change infrastructure set for seed 11

seed 14 fails topology
→ replace 14 with a nicer root seed
```

Every v1 seed receives the same policy and the same current physical/economic calibration.

## Machine-readable evidence

`Stage20RepresentativeSeedCorpus.evaluateCurrent()` produces the existing `Stage20GeneratedWorldBatchAcceptance.BatchReport` plus exact profile/version metadata.

The JSON includes:

- schema/corpus/profile/probe versions;
- bootstrap requirement version;
- faction-start profile version;
- fixed requested seeds;
- accepted / rejected / unresolved-authority counts and measured fractions;
- normalized whole-seed failure-reason counts;
- every per-seed status;
- topology status and repair count;
- economic-acceptance presence;
- faction-start placement status;
- detailed normalized failure rows.

There is deliberately no field such as `minimumAcceptedFraction` in v1.

## CI evidence lifecycle

The repository-exact Java-17 test writes:

```text
target/stage20e-evidence/stage20e-representative-seed-corpus-v1.json
```

Pull requests execute the exact same generation test but do not retain routine artifacts. After merge, the existing `main` artifact upload also retains `target/stage20e-evidence/` together with the application/Javadocs/coverage artifact.

This creates the intended measurement-first sequence:

1. merge only if exact-head `clean verify` succeeds;
2. inspect the generated JSON from the authoritative `main` workflow artifact;
3. determine actual dominant failure classes;
4. only then create a follow-up snapshot/closeout decision.

A future committed benchmark snapshot must match the measured main evidence; it must not be hand-edited to create a preferred pass rate.

## What this slice does not close

This is batch observability, not final Stage-20E acceptance. Remaining authority identified by the production probe still includes:

- whole-route monetary delivered cost;
- actual initial inventory/buffer stock and depletion exposure;
- initial source/facility ownership and ownership concentration;
- any standalone celestial-body/field-extent authority still required by final Stage-20B/20E DoD.

The current faction-start v1 profile keeps those missing authorities diagnostic rather than fabricating values.

## Next roadmap action

After the authoritative main artifact is available:

```text
inspect fixed-corpus evidence
→ classify failures by causal layer
→ freeze machine-readable benchmark evidence
→ close delivered-cost / buffer / ownership authority where required
→ Stage-20E final acceptance decision
→ Stage-20F industrial specialization bootstrap
```

No failure class may be addressed by hidden resources, extra edges, post-failure facilities, invisible stock, per-seed freighters or relaxed physical requirements.
