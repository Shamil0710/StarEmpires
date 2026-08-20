# Stage 20E — Production-Style Generated World Seed Probe v1

> Status: **PROVISIONAL STAGE-20E INTEGRATION SLICE**  
> Implementation: `stage20e.production-seed-probe.v1`  
> Prerequisite: merged Stage-20B local physical resource hosts v1 (PR #263)

## Purpose

Stage-20E already had individual implementations for finite resource occurrence, extraction capacity, physical freight throughput, dependency diagnostics, faction-start evaluation/placement, whole-seed acceptance and batch observability.

The remaining evidence gap was that these layers could still be exercised independently with hand-authored fixture worlds.

`Stage20GeneratedWorldProductionProbe` closes that integration gap by evaluating **one exact root seed through the real generated-world chain**:

```text
root seed
→ Stage20MacroGalaxyGeometryGenerator
→ Stage20JumpTopologyGenerator
→ accepted ordinary topology or immediate seed rejection
→ Stage20SystemGeometryGenerator per system
→ Stage20LocalInfrastructureLayoutGenerator per system
→ Stage20LocalPhysicalResourceHostGenerator
→ Stage20ResourceOccurrenceGenerator
→ Stage20ExtractionSiteLogisticsResolver
→ Stage20BootstrapProductionCapacityCalculator
→ Stage20TheoreticalSupplyThroughputAnalyzer
→ Stage20FactionStartDependencyDiagnostics
→ Stage20FactionStartCandidateEvaluator
→ Stage20FactionStartPlacementGenerator
→ Stage20EconomicThroughputAcceptance
→ Stage20GeneratedWorldSeedAcceptance
```

The probe does not retry another seed internally and does not mutate a rejected seed into an accepted one.

## Authority boundary

The probe deliberately does **not** claim authority over several inputs that Stage 20 has not yet canonicalized.

### Explicitly injected rather than guessed

The caller must provide:

- macro generation request;
- accepted Stage-20A topology-quality profile;
- an initial local-infrastructure authoring profile;
- essential bootstrap demand/routing requirements;
- matching dependency-diagnostics requirements;
- current/versioned faction-start acceptance policy;
- stable faction IDs requiring starts;
- fitted loaded and return `JumpPlan` authority;
- physical freight payload and allocated freighter count.

The orchestration layer is therefore incapable of silently deciding that a civilization needs an arbitrary number of kilograms per second, that an FTL ship has a convenient range, or that extra freighters appear because a seed would otherwise fail.

### Still unresolved in v1

The probe preserves the existing explicit unresolved state for:

- monetary delivered cost;
- generated/authoritative inventory buffer stock;
- initial source ownership.

The current `stage20e.faction-start-acceptance.v1` profile does not yet make those authorities mandatory. A later Stage-20E closeout profile may tighten the gate after those upstream authorities exist.

## Initial infrastructure and anti-rescue ordering

Initial infrastructure is selected **before resource occurrence generation**.

For each accepted generated system, v1 creates:

1. one configured major hub station;
2. a fixed configured number of Stage-20C `RESOURCE_FIELD_ANCHOR` point anchors;
3. one Stage-20C jump-arrival point anchor;
4. one deterministic industrial station archetype selected from an explicitly supplied Stage-18-compatible set.

The industrial station choice is keyed only by root seed + stable system identity + the predeclared profile. It cannot inspect generated resources, reserve deficits, faction-start evaluation, economic acceptance or later failure state.

Therefore the following is forbidden by construction:

```text
seed is short of X
→ add refinery/deposit/freighter/edge
→ pretend original seed was viable
```

If the sampled infrastructure does not close the required physical economy, the seed remains rejected evidence.

## Resource host integration

The probe consumes `Stage20LocalPhysicalResourceHostGenerator` rather than manually authored `ResourceHostProfile` rows.

That makes the production path:

```text
Stage-20C authoritative SI resource anchor
→ generated Stage-18-backed physical host semantics
→ Stage-20E correlated occurrence
→ finite reserve / grade / recovery
→ optional explicit initial extraction site
```

No fallback deposit is introduced by the probe.

## Physical route and throughput authority

The probe uses `Stage20PhysicalGalacticRoutePlanner` and `Stage20PhysicalFreightRouteEvaluator`.

Inter-system travel therefore follows actual Stage-20D neighbor topology edge-by-edge. There is no Euclidean shortcut and no synthetic per-hop economic penalty.

Local freight-cycle time is derived from the generated Stage-20C calibrated SI layout:

- non-jump local access uses generated local infrastructure connections;
- jump arrival/departure access is carried separately by the generated jump-arrival connection;
- loading/unloading rates come from the actual Stage-18 major-hub station archetype;
- fitted jump spool/transit/cooldown remain supplied physical authority.

The probe currently uses a conservative maximum generated local-access consequence per system for representative freight throughput. This is an acceptance diagnostic, not a runtime cargo reservation system.

## Whole-seed rejection semantics

A topology-rejected seed stops before local/resource/economic materialization and is composed directly into the existing `Stage20GeneratedWorldSeedAcceptance` topology rejection.

A topology-accepted seed continues through all downstream layers, even when those layers prove that the world is economically invalid. No downstream rejection triggers re-generation inside the same root seed.

When bounded faction placement succeeds, quantitative essential-throughput acceptance is evaluated on the selected faction-start systems.

If placement itself fails, the probe still produces the mandatory economic acceptance evidence required by the current whole-seed composition API. It evaluates already accepted candidate systems when any exist; if none are accepted, it evaluates one deterministic existing system. That secondary report cannot rescue the placement failure: `Stage20GeneratedWorldSeedAcceptance` still reports the authoritative start-placement rejection.

## Test authority is not production balance authority

The integration regression suite supplies explicit numeric demand, route and freight values under IDs such as:

- `stage20e.production-probe.integration-demand.v1`;
- `stage20e.production-probe.integration-freight.v1`;
- `stage20e.production-probe.integration-infrastructure.v1`.

These values exist only to exercise the real orchestration seam. They are **not** a canonical Stage-20 economic demand baseline and must not be promoted to gameplay balance by copying them into production policy.

The repository still lacks a justified canonical `BootstrapRequirementProfile.current()` equivalent for the initial civilization/start economy. That remains an explicit Stage-20E closeout task.

## Batch observability

The probe can be passed directly to `Stage20GeneratedWorldBatchAcceptance`:

```text
fixed seed corpus
→ run exactly each requested root seed
→ collect ACCEPTED / REJECTED_SEED / UNRESOLVED_AUTHORITY
→ count normalized failure reasons
```

No minimum accepted fraction is introduced by this slice because the accepted roadmap currently provides no evidence-backed numeric target.

The immediate goal is measurement first. A future quantitative batch gate, if justified, must be versioned from observed corpus behavior rather than invented to make CI green.

## Regression coverage

The v1 regression suite proves:

1. a bounded corpus contains a topology-accepted representative macro seed;
2. that seed materializes the real local-layout, host, resource, logistics, supply, candidate, placement, economic and whole-seed layers;
3. generated resource occurrences resolve back to their generated physical host and exact SI anchor position;
4. a deliberately undersized topology request rejects before any downstream materialization;
5. real probe results can feed batch observability without a fabricated pass-rate gate;
6. dependency diagnostics cannot silently change the bootstrap commodity demand/routing requirement supplied by the acceptance authority.

## Stage-20E remaining work after this slice

This slice is **not** Stage-20E completion.

The next closeout work should use the new probe to establish and measure a fixed representative seed corpus, then resolve the missing upstream authorities rather than hiding them:

1. author a versioned, provenance-backed civilization/start `BootstrapRequirementProfile` from actual Stage-18 facility/recipe and Stage-20 logistics consequences;
2. run a fixed deterministic representative corpus through the production probe and preserve machine-readable distributions/failure reasons;
3. inspect whether failures reveal legitimate calibration problems, missing physical host/infrastructure authority, or intended scarcity — without per-seed rescue;
4. close delivered monetary-cost authority if required for Stage-20E DoD;
5. close initial buffer/stock authority if required for Stage-20E DoD;
6. attach initial source/facility ownership authority before ownership concentration becomes a hard gate;
7. only then declare Stage-20E accepted and move to Stage-20F industrial specialization bootstrap.

Stage-20F must consume the generated resources, facilities, storage, power/work limits and physical routes established here; it must not replace them with system-type percentage bonuses.
