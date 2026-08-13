# Stage 9D — Bottleneck analysis and AI economic response

## Status

**COMPLETE candidate.** Stage 9D adds deterministic physical bottleneck analysis, persistent faction-specific pressure hysteresis, and a planner that funds ordinary Stage-9B construction projects instead of spawning replacement capacity directly.

## Architecture

Stage 9D derives decisions from the same local ECS state used by markets, production and logistics:

```text
physical market stock / target stock
+ active production recipes / inventories
→ EconomicBottleneckAnalyzer
→ FactionEconomicPressureTracker
→ FactionInvestmentPlanner
→ WorldSimulation.createConstructionProject(...)
→ WorldSimulation.fundConstructionProject(...)
→ ordinary Stage-9B construction market/logistics
```

The response layer never creates materials, money, stations or passive production capacity.

## Bottleneck analyzer

For every deterministic `StarSystem × item` pair, `EconomicBottleneckAnalyzer` measures aggregate target-stock deficit, stock-above-target surplus, net unmet demand, stockouts and structural stock pressure. It also inspects active producer recipes and classifies producers as ready, blocked by missing physical inputs, or blocked by storage capacity.

Dominant causes are `PRODUCTION_CAPACITY_SHORTAGE`, `LOGISTICS_SHORTAGE`, or `STORAGE_CONGESTION`. Reports use stable ordering and a saturating severity score; severity is diagnostic ranking only and does not alter economic state.

## Persistent hysteresis

`FactionEconomicPressureState` stores pressure per `faction × controlled system × item`: cause, first/last observed tick, consecutive observations, peak/last unmet demand and cooldown watermark. World persistence advances from v4 to v5 and appends this canonical pressure layer. Legacy v1-v4 saves migrate neutrally; v4 construction projects are preserved and pressure starts empty.

Repeated evaluation in the same simulation tick does not increment hysteresis. A changed physical cause starts a new episode. A cleared shortage resets the episode while retaining cooldown.

## Faction response planner

A production project is eligible only when the pressure is owned by the faction, lies in controlled territory, is `PRODUCTION_CAPACITY_SHORTAGE`, persists for at least three distinct observations, still has unmet demand, is outside cooldown, has no active producer project for the same item, has a constructible producer archetype, and the faction treasury can fully fund it.

`LOGISTICS_SHORTAGE` and `STORAGE_CONGESTION` deliberately do not trigger another producer for that item. Candidates are ordered deterministically: faction-native archetype first, expected utility descending, stable archetype ID. Expected utility combines unmet demand, recipe output and required construction funding and is only a ranking seam.

Selected responses use the same public construction API intended for later player construction. The planner creates a normal ConstructionProject and transfers real treasury money into its project wallet. It has no AI-only station-spawn path.

## World runtime

`WorldSimulation.applyEconomicInvestmentDecision()` performs one deterministic pass: analyze bottlenecks, update persistent pressure at the current simulation tick, give each faction at most one opportunity, and return the number of new projects. Decision cadence remains caller-controlled so Stage 9E can measure response time independently of render frequency.

## Verification

Tests cover destroyed foundry → steel production-capacity shortage; input-starved foundry → logistics shortage; deterministic reports; v5 pressure round-trip and v4 migration; same-tick hysteresis protection; physical replacement foundry creation/funding; same-item anti-overbuilding; no redundant steel producer for logistics shortage; pressure continuation through world snapshot/restore; and zero-treasury budget gating. Normal Java 17 CI still enforces tests, JaCoCo, strict Javadoc and desktop packaging.

## Stage 9E handoff

Stage 9E must prove the complete causal chain:

```text
stable economy
→ destroy critical foundry
→ measurable shortage
→ sustained pressure
→ economic response
→ physical construction project
→ ordinary material delivery
→ BUILDING
→ replacement capacity online
→ economic recovery
```

The benchmark must record detection, decision, material-fulfillment, completion and recovery times plus peak unmet demand/pressure, project cost and conservation/accounting evidence.
