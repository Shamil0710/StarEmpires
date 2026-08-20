# Stage 20E — Production-Style Generated World Seed Probe v1

> Status: **PROVISIONAL STAGE-20E INTEGRATION SLICE**  
> Implementation: `stage20e.production-seed-probe.v1`  
> Prerequisite: merged Stage-20B local physical resource hosts v1 (PR #263)

## Purpose

`Stage20GeneratedWorldProductionProbe` evaluates **one exact root seed through the generated-world chain**:

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

The probe deliberately does **not** claim authority over inputs that Stage 20 has not canonicalized.

The caller still supplies macro generation, topology-quality, initial infrastructure, faction-start policy, stable faction IDs and fitted transport authority. The probe cannot add deposits, topology edges, stations, stock or freighters to rescue a seed.

### Bootstrap demand/routing authority

The former integration-only `stage20e.production-probe.integration-demand.v1` authority has been removed from the production-probe regression path.

The current regression now consumes `Stage20BootstrapRequirementCalibrationProfile.deriveCurrent()` (`stage20e.bootstrap-requirements.v1`). That profile makes the essential-process **selection** explicit Stage-20 policy and derives numeric kg/s and supplier-route horizons from current Stage-18 recipes, facilities, station transfer/storage and shared physical storage consequences.

See `docs/stage20e_bootstrap_requirement_calibration_v1.md` for the full derivation and provenance boundary.

### Still unresolved in v1

The probe preserves explicit unresolved state for:

- monetary delivered cost;
- generated/authoritative inventory buffer stock;
- initial source ownership.

The current `stage20e.faction-start-acceptance.v1` profile does not make those authorities mandatory. A later Stage-20E closeout profile may tighten the gate only after upstream authority exists.

## Initial infrastructure and anti-rescue ordering

Initial infrastructure is selected **before resource occurrence generation**.

For each accepted generated system, v1 creates:

1. one configured major hub station;
2. a fixed configured number of Stage-20C `RESOURCE_FIELD_ANCHOR` point anchors;
3. one distinct Stage-20C `JUMP_ARRIVAL_ANCHOR` for every incident Stage-20D ordinary edge;
4. one deterministic industrial station archetype selected from an explicitly supplied Stage-18-compatible set.

The industrial station choice is keyed only by root seed + stable system identity + the predeclared profile. It cannot inspect generated resources, reserve deficits, faction-start evaluation, economic acceptance or later failure state.

```text
seed is short of X
→ add refinery/deposit/freighter/edge
→ pretend original seed was viable
```

is forbidden by construction. If sampled infrastructure does not close the required physical economy, the seed remains rejected evidence.

## Stage-20D edge materialization

After local layouts are generated, `Stage20JumpEdgeStateMaterializer` creates an exact-coverage `Stage20JumpEdgeCatalog`.

The probe authors one arrival anchor per incident edge so the materializer never falls back to shared or legacy coordinates. The catalog is retained in `ProbeResult` and supplied to both loaded and return `Stage20PhysicalGalacticRoutePlanner` instances. Freight routing therefore respects the same explicit physical edge state used by Stage-20D execution planning.

## Resource host integration

The probe consumes `Stage20LocalPhysicalResourceHostGenerator` rather than manually authored `ResourceHostProfile` rows:

```text
Stage-20C authoritative SI resource anchor
→ generated Stage-18-backed physical host semantics
→ Stage-20E correlated occurrence
→ finite reserve / grade / recovery
→ optional explicit initial extraction site
```

No fallback deposit is introduced.

## Physical route and throughput authority

The probe uses `Stage20PhysicalGalacticRoutePlanner` and `Stage20PhysicalFreightRouteEvaluator` over the exact-coverage jump-edge catalog. Inter-system travel follows actual Stage-20D neighbor edges; there is no Euclidean shortcut and no synthetic per-hop economic penalty.

Local freight-cycle time comes from generated Stage-20C calibrated SI layout consequences:

- non-jump local access uses generated local infrastructure connections;
- jump arrival/departure access uses generated jump-arrival connections;
- loading/unloading rates come from the actual Stage-18 major-hub station archetype;
- extraction production is capped by `Stage20ExtractionSiteLogisticsResolver`;
- fitted jump spool/transit/cooldown remain supplied physical authority.

The probe uses a conservative maximum generated local-access consequence per system for representative freight throughput. This is an acceptance diagnostic, not a runtime cargo-reservation model.

## Regression transport authority

The integration test uses the compatible `EARLY_CIVILIAN_FREIGHTER` row from `Stage20FtlCalibrationProfile.deriveCurrent()` and converts accepted Stage-20A translated mass, translation energy, spool, edge transit and cooldown into an executable route-planning `JumpPlan`.

Freight payload comes from `Stage20RepresentativePropulsionCatalogLoader.loadDefault()` through `FreightFleetProfile.fromMissionCargoStoresReference(...)`. Stage-22 review provenance remains intact. These are calibration facts rather than arbitrary integration-only ship physics.

## Whole-seed rejection semantics

A topology-rejected seed stops before local/resource/economic materialization and composes directly into the existing topology rejection.

A topology-accepted seed continues through downstream layers even when those layers prove the world economically invalid. No downstream rejection triggers regeneration inside the same root seed.

When bounded faction placement succeeds, quantitative essential-throughput acceptance is evaluated on selected starts. If placement fails, the current composition API still requires an economic report; the probe evaluates accepted candidates if any, otherwise one deterministic existing system. That diagnostic cannot rescue the placement failure.

## Batch observability

The probe feeds `Stage20GeneratedWorldBatchAcceptance` directly:

```text
fixed seed corpus
→ run each requested root seed exactly once
→ collect ACCEPTED / REJECTED_SEED / UNRESOLVED_AUTHORITY
→ count normalized failure reasons
```

No minimum accepted fraction is introduced because the roadmap contains no evidence-backed numeric target. Measurement comes first; any later quantitative gate must be versioned from observed corpus behavior rather than invented to make CI green.

## Regression coverage

The current regression suite proves:

1. a bounded corpus contains a topology-accepted representative macro seed;
2. that seed materializes layout, exact edge catalog, host, resource, logistics, supply, candidate, placement, economic and whole-seed layers;
3. each generated system has one physical jump-arrival anchor per incident ordinary edge;
4. generated resource occurrences resolve to their generated physical host and exact SI anchor position;
5. a deliberately undersized topology request rejects before downstream materialization;
6. real probe results feed batch observability without a fabricated pass-rate gate;
7. dependency diagnostics cannot silently change the derived bootstrap demand;
8. representative transport consumes accepted Stage-20A FTL/propulsion calibration;
9. bootstrap demand consumes `stage20e.bootstrap-requirements.v1` rather than integration-only `1 kg/s / 1e9 s` values.

## Stage-20E remaining work after bootstrap-demand calibration

Stage-20E remains **ACTIVE**. The next closeout work is:

1. run a fixed deterministic representative root-seed corpus through the production probe and preserve machine-readable distributions/failure reasons;
2. inspect whether failures reveal legitimate topology/resource/calibration/infrastructure scarcity without per-seed rescue;
3. close delivered monetary-cost authority if required for Stage-20E DoD;
4. close initial buffer/stock authority if required for Stage-20E DoD;
5. attach initial source/facility ownership authority before ownership concentration becomes a hard gate;
6. close any remaining standalone celestial-body/field-extent authority exposed as an explicit Stage-20B follow-up;
7. only then declare Stage-20E accepted and move to Stage-20F industrial specialization bootstrap.

Stage-20F must consume generated resources, facilities, storage, power/work limits and physical routes established here; it must not replace them with system-type percentage bonuses.
