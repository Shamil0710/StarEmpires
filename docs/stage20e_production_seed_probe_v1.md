# Stage 20E — Production-Style Generated World Seed Probe v1

> Status: **PROVISIONAL STAGE-20E INTEGRATION SLICE**  
> Implementation: `stage20e.production-seed-probe.v1`  
> Prerequisite: merged Stage-20B local physical resource hosts v1 (PR #263)

## Purpose

Stage-20E already had individual implementations for finite resource occurrence, extraction capacity, physical freight throughput, dependency diagnostics, faction-start evaluation/placement, whole-seed acceptance and batch observability. The remaining evidence gap was that these layers could still be exercised independently with hand-authored fixture worlds.

`Stage20GeneratedWorldProductionProbe` closes that integration gap by evaluating **one exact root seed through the generated-world chain**:

```text
root seed
→ Stage20MacroGalaxyGeometryGenerator
→ Stage20JumpTopologyGenerator
→ accepted ordinary topology or immediate seed rejection
→ Stage20SystemGeometryGenerator per system
→ Stage20LocalInfrastructureLayoutGenerator per system
→ Stage20JumpEdgeStateMaterializer exact-coverage edge catalog
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

The probe deliberately does **not** claim authority over inputs that Stage 20 has not yet canonicalized.

### Explicitly injected rather than guessed

The caller must provide:

- macro generation request;
- accepted Stage-20A topology-quality profile;
- initial local-infrastructure authoring profile;
- essential bootstrap demand/routing requirements;
- matching dependency-diagnostics requirements;
- current/versioned faction-start acceptance policy;
- stable faction IDs requiring starts;
- executable fitted loaded and return `JumpPlan` authority;
- physical freight payload and allocated freighter count.

The orchestration layer therefore cannot silently decide that a civilization needs a convenient number of kilograms per second, that a ship has arbitrary FTL capability, or that extra freighters appear because a seed would otherwise fail.

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
3. one distinct Stage-20C `JUMP_ARRIVAL_ANCHOR` for every incident Stage-20D ordinary edge;
4. one deterministic industrial station archetype selected from an explicitly supplied Stage-18-compatible set.

The industrial station choice is keyed only by root seed + stable system identity + the predeclared profile. It cannot inspect generated resources, reserve deficits, faction-start evaluation, economic acceptance or later failure state.

Therefore the following is forbidden by construction:

```text
seed is short of X
→ add refinery/deposit/freighter/edge
→ pretend original seed was viable
```

If the sampled infrastructure does not close the required physical economy, the seed remains rejected evidence.

## Stage-20D edge materialization

A production probe must not stop at abstract graph adjacency. After local layouts are generated, `Stage20JumpEdgeStateMaterializer` consumes them and creates an exact-coverage `Stage20JumpEdgeCatalog`.

The probe deliberately authors one arrival anchor per incident edge so the materializer never falls back to shared or legacy coordinates. The resulting catalog is retained in `ProbeResult` and supplied to both loaded and return `Stage20PhysicalGalacticRoutePlanner` instances. Freight routing therefore respects the same explicit physical edge availability/transit metadata used by Stage-20D execution planning.

## Resource host integration

The probe consumes `Stage20LocalPhysicalResourceHostGenerator` rather than manually authored `ResourceHostProfile` rows:

```text
Stage-20C authoritative SI resource anchor
→ generated Stage-18-backed physical host semantics
→ Stage-20E correlated occurrence
→ finite reserve / grade / recovery
→ optional explicit initial extraction site
```

No fallback deposit is introduced by the probe.

## Physical route and throughput authority

The probe uses `Stage20PhysicalGalacticRoutePlanner` and `Stage20PhysicalFreightRouteEvaluator` over the exact-coverage jump-edge catalog. Inter-system travel therefore follows actual Stage-20D neighbor edges. There is no Euclidean shortcut and no synthetic per-hop economic penalty.

Local freight-cycle time is derived from generated Stage-20C calibrated SI layout consequences:

- non-jump local access uses generated local infrastructure connections;
- jump arrival/departure access uses generated jump-arrival connections;
- loading/unloading rates come from the actual Stage-18 major-hub station archetype;
- extraction production is separately capped by `Stage20ExtractionSiteLogisticsResolver`, so unresolved source handling never becomes free production;
- fitted jump spool/transit/cooldown remain supplied physical authority.

The probe uses a conservative maximum generated local-access consequence per system for representative freight throughput. This is an acceptance diagnostic, not a runtime cargo-reservation model.

## Regression transport authority

The integration test no longer uses arbitrary hand-written FTL timing/mass values. It selects the compatible `EARLY_CIVILIAN_FREIGHTER` row from `Stage20FtlCalibrationProfile.deriveCurrent()` and converts its accepted Stage-20A translated mass, translation energy, spool, edge transit and cooldown into an executable route-planning `JumpPlan`.

Freight payload comes from `Stage20RepresentativePropulsionCatalogLoader.loadDefault()` through `FreightFleetProfile.fromMissionCargoStoresReference(...)`. Its Stage-22 review flag and provenance remain intact. This is still calibration authority rather than final production hull content, but it is materially different from inventing integration-only ship physics.

## Whole-seed rejection semantics

A topology-rejected seed stops before local/resource/economic materialization and is composed directly into the existing `Stage20GeneratedWorldSeedAcceptance` topology rejection.

A topology-accepted seed continues through all downstream layers, even when those layers prove that the world is economically invalid. No downstream rejection triggers regeneration inside the same root seed.

When bounded faction placement succeeds, quantitative essential-throughput acceptance is evaluated on selected faction-start systems. If placement fails, the probe still produces the mandatory economic report required by the current whole-seed composition API. It evaluates accepted candidate systems when any exist; if none exist, it evaluates one deterministic existing system. That diagnostic cannot rescue the placement failure. A later whole-seed composition revision may remove this fallback once the API can represent placement failure without requiring downstream start-system economics.

## Test demand is not production balance authority

The integration regression suite still supplies explicit demand and infrastructure values under IDs such as:

- `stage20e.production-probe.integration-demand.v1`;
- `stage20e.production-probe.integration-infrastructure.v1`.

Those values exercise orchestration only. They are **not** a canonical civilization demand baseline and must not be copied into gameplay balance.

The repository still lacks a justified canonical `BootstrapRequirementProfile.current()` equivalent for the initial civilization/start economy. That is the next Stage-20E closeout task.

## Batch observability

The probe can feed `Stage20GeneratedWorldBatchAcceptance` directly:

```text
fixed seed corpus
→ run each requested root seed exactly once
→ collect ACCEPTED / REJECTED_SEED / UNRESOLVED_AUTHORITY
→ count normalized failure reasons
```

No minimum accepted fraction is introduced by this slice because the accepted roadmap provides no evidence-backed numeric target. Measurement comes first; any later quantitative batch gate must be versioned from observed corpus behavior rather than invented to make CI green.

## Regression coverage

The v1 suite proves:

1. a bounded corpus contains a topology-accepted representative macro seed;
2. that seed materializes the real layout, exact edge catalog, host, resource, logistics, supply, candidate, placement, economic and whole-seed layers;
3. each generated system has exactly one physical jump-arrival anchor per incident ordinary edge;
4. generated resource occurrences resolve back to their generated physical host and exact SI anchor position;
5. a deliberately undersized topology request rejects before downstream materialization;
6. real probe results feed batch observability without a fabricated pass-rate gate;
7. dependency diagnostics cannot silently change supplied bootstrap demand;
8. representative transport tests consume accepted Stage-20A FTL/propulsion calibration instead of arbitrary ship physics.

## Stage-20E remaining work after this slice

This slice is **not** Stage-20E completion. The next closeout work should use the probe to establish a versioned demand authority and a fixed representative seed corpus, then resolve missing upstream authorities rather than hiding them:

1. author a versioned, provenance-backed civilization/start `BootstrapRequirementProfile` from actual Stage-18 facility/recipe and Stage-20 logistics consequences;
2. run a fixed deterministic representative corpus through the production probe and preserve machine-readable distributions/failure reasons;
3. inspect whether failures reveal legitimate calibration problems, missing physical host/infrastructure authority, or intended scarcity — without per-seed rescue;
4. close delivered monetary-cost authority if required for Stage-20E DoD;
5. close initial buffer/stock authority if required for Stage-20E DoD;
6. attach initial source/facility ownership authority before ownership concentration becomes a hard gate;
7. close any remaining standalone celestial-body/field-extent authority exposed by the probe as an explicit Stage-20B follow-up;
8. only then declare Stage-20E accepted and move to Stage-20F industrial specialization bootstrap.

Stage-20F must consume generated resources, facilities, storage, power/work limits and physical routes established here; it must not replace them with system-type percentage bonuses.
