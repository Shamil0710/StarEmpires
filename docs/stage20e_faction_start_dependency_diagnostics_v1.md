# Stage 20E — Faction-start dependency diagnostics v1

Status: **PROVISIONAL STAGE-20E IMPLEMENTATION SLICE**  
Implementation: `stage20e.faction-start-dependency-diagnostics.v1`  
Stage 20E remains **ACTIVE** after this slice; faction-start candidate acceptance/placement and later Stage-20 work are not closed here.

## Purpose

This slice implements the roadmap ordering:

```text
topology + resources + facilities
→ viability / dependency diagnostics
→ faction-start candidate evaluation
→ bounded deterministic placement
```

Only the first arrow is implemented here. The diagnostics are read-only and cannot rescue a bad seed.

The implementation consumes existing Stage-20D/20E authority:

- explicit ordinary neighbor topology;
- physically evaluated supply routes;
- Stage-20 theoretical supply-throughput closure;
- finite generated resource reserves;
- explicit initial extraction sites.

It does not introduce a second route model, faction-only resource rules, hidden deposits or abstract industrial bonuses.

## Metrics implemented

For every required commodity at one candidate start system the report records:

- local physical supply rate;
- total physically reachable supply rate;
- essential local supply coverage;
- import dependency;
- local export potential;
- throughput headroom;
- viable supplier count;
- external supplier count;
- supplier concentration (HHI over delivered-capacity shares);
- route concentration (HHI by final physical gateway edge);
- critical gateway dependency (largest external gateway share);
- a proven edge-disjoint path floor (`0`, `1` or `2`);
- delivered-cost min / median / max when an accepted cost authority is supplied;
- physical buffer depletion exposure when buffer/consumption authority is supplied;
- concentration of reachable finite recoverable reserves;
- reserve ownership concentration when ownership is authoritative for every relevant source.

Aggregate candidate diagnostics additionally expose:

- requirement-weighted essential local supply coverage;
- import dependency by authored dependency family;
- local export potential by family;
- worst throughput headroom;
- worst supplier / route / gateway concentration;
- minimum external supplier count across import-dependent commodities;
- minimum proven path-redundancy floor across import-dependent commodities;
- unresolved delivered-cost / buffer / ownership authority counts.

These are authoritative-derived diagnostics, not objectives, scripted missions or generation bonuses.

## Physical-route invariant

Every external supplier route returned by the injected `RouteEvaluator` is revalidated against:

```text
topology.neighbors(current)
```

A route such as:

```text
A → C
```

is rejected when the authoritative topology requires:

```text
A → B → C
```

Route concentration is derived from the final real gateway edge into the candidate system. It is not calculated from Euclidean system distance.

## Path redundancy evidence

The v1 diagnostics deliberately report only a bounded evidence floor:

- `0` — no external path evidence;
- `1` — a path exists but at least one edge of a deterministic shortest path is a single-edge cut for that supplier/candidate pair;
- `2` — removal of every edge on that path still leaves connectivity, proving at least two edge-disjoint paths.

The value is not presented as an exact count above two and therefore does not overstate topology knowledge.

## Finite reserve authority

`initialOperationalReserves(...)` includes only generated occurrences that already have an explicit `InitialExtractionSite`.

Recoverable useful mass is derived from the generated finite source state:

```text
recoverableMassKg =
    initialAccessibleMassKg
  × gradeFraction
  × sourceRecoveryFraction
```

An occurrence with no installed extraction site is not silently treated as immediately available production capacity.

## Explicit unresolved authority

Stage 20E does not yet have universal authority for all of the following in every procedural pre-faction world state:

- full monetary delivered cost including every future operating/risk/political cost;
- generated/current inventory buffers and sustained consumption for every candidate commodity;
- ownership of every generated natural source before faction placement.

Therefore the diagnostics use explicit seams:

```text
DeliveredCostEvaluator -> OptionalDouble
BufferStateProvider     -> Optional<BufferState>
ReserveSource.ownerId   -> Optional<String>
```

Missing authority remains missing. The implementation must not substitute:

- Euclidean distance cost;
- arbitrary `costPerHop`;
- fictional starting stock;
- guessed consumption;
- synthetic owner assignment.

The aggregate report carries unresolved-authority counts so a later acceptance profile can distinguish:

```text
physically bad candidate
≠ authority not implemented/resolved yet
```

## Anti-rescue invariant

This slice never:

- adds a fallback deposit;
- increases source grade/reserves;
- grants a facility;
- grants stock or throughput;
- opens/creates a jump edge;
- changes supplier ownership;
- moves a faction start;
- mutates the generated world.

If diagnostics reveal a bad candidate, the next faction-start evaluator may reject that candidate or reject the seed according to explicit calibrated policy. It may not repair the world through hidden grants.

## Acceptance coverage

`Stage20FactionStartDependencyDiagnosticsTest` covers:

1. local coverage, import dependency, supplier concentration, shared-gateway concentration, throughput headroom, cost band, buffer exposure and reserve/ownership concentration on a line topology;
2. unresolved cost/buffer/ownership authorities remain explicitly empty and counted;
3. a cyclic topology proves a second edge-disjoint supplier path;
4. a non-neighbor route shortcut is rejected;
5. finite reserve projection includes only sources backed by explicit initial extraction sites.

## Next roadmap slice

After this diagnostics layer passes the repository-exact gate, Stage 20E still requires:

```text
versioned faction-start acceptance bands
+ candidate evaluation
+ bounded deterministic asymmetric placement
+ monopoly / unrecoverable-start rejection evidence
```

The evaluator must consume these diagnostics rather than invent a separate start-quality score from raw map distance or system labels.

After faction-start placement is closed, work continues into Stage 20F industrial specialization bootstrap according to `docs/stage20_physical_world_generation_plan.md`.
