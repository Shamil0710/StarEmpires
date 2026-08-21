# Stage 20E — coordinated freight corpus diagnostics v1

## Status

This document defines a **read-only fixed-corpus measurement**, not a new production acceptance policy.

The diagnostic version is `stage20e.coordinated-freight-corpus-diagnostics.v1` and measures the globally coordinated whole-placement freight planner introduced by `stage20e.coordinated-whole-placement-freight-planner.v1`.

## Why this diagnostic exists

The previous whole-placement capacity corpus evidence established that the corrected representative v2-candidate placement succeeds for 15 of the fixed 16 seeds and that all 15 accepted placements can satisfy each start independently with the physically derived finite freight service-capacity requirement.

However, independently selected start portfolios can reserve the same finite producer capacity. In that evidence only 4 of 15 independently selected placements survived whole-placement producer reservation while 11 of 15 conflicted. That conflict proves only that the **independent supplier choices** cannot coexist; it does **not** prove that the generated seed lacks a globally feasible supplier mix.

The coordinated planner therefore searches supplier/freighter choices for all placed starts simultaneously while enforcing:

- the same essential bootstrap demand;
- the same generated topology and physical supply;
- the same finite producer capacities;
- the same fitted physical freight routes;
- the independently derived finite freight service-capacity budget per start;
- explicit integer freight-route prefixes;
- shared producer capacity across local and remote consumers.

This corpus diagnostic asks whether the fixed representative seeds are feasible under that coordinated model.

## Frozen inputs

The diagnostic replays the existing fixed seed corpus exactly as authored by `Stage20RepresentativeSeedCorpus`; it does not replace failed seeds or search for friendlier examples.

Generation and placement use `Stage20RepresentativeGeneratedWorldProbeProfileV2` unchanged. The production probe therefore still decides topology, physical resource state, infrastructure, theoretical supply and faction-start placement before this diagnostic runs.

Only seeds with an already accepted v2-candidate placement reach coordinated freight planning.

The finite freight bound is taken from `Stage20BootstrapFreightCapacityRequirementProfile`. With the current physical calibration this authority derives 13 freighters per faction start from essential demand, payload and the reference physical service cycle. The diagnostic does not create or materialize those hulls; it measures the service capacity that the generation contract would need to support.

## Physical route parity

The diagnostic deliberately does not author another freight timing model.

It reuses `Stage20WholePlacementCapacityCorpusDiagnostics.physicalRoutes(...)`, the same representative diagnostic route construction used for the preceding finite-fleet and producer-reservation evidence. That construction preserves:

- generated jump topology and jump-edge state;
- fitted loaded and return FTL plans;
- generated local infrastructure layouts;
- jump-arrival and local-access travel times;
- Stage-18 major-hub transfer mass rates;
- representative freight payload and source provenance.

The helper is package-visible only so adjacent Stage-20E corpus diagnostics can share one physical calculation. Its formulas are not changed by this slice.

## Search-budget semantics

`SEARCH_NODE_BUDGET_PER_SEED = 2000` is a **bounded CI measurement budget**.

It is not:

- a world-quality threshold;
- a pass-rate target;
- a physical property of a faction;
- permission to reject a seed merely because the planner did not finish within the diagnostic budget.

The planner has three materially different outcomes and the corpus report preserves them:

1. `ACCEPTED` — a globally coordinated finite-freight/shared-producer plan was found;
2. `INFEASIBLE` — the planner proved that no plan exists under the supplied physical/fleet authorities explored by its complete bounded state space;
3. `UNRESOLVED_SEARCH_BUDGET` — the CI measurement budget ended before either proof was reached.

The third outcome must remain unresolved. It must never be silently converted into seed rejection or used as justification to tune resources, demand, fleet size or topology.

## Evidence output

For every fixed seed the diagnostic records:

- unchanged placement status;
- coordinated planner status when placement was accepted;
- explicit failure/unresolved reason when present;
- search nodes visited;
- remote freighters used by an accepted coordinated plan.

The aggregate report records:

- fixed corpus size;
- accepted-placement count;
- coordinated accepted / infeasible / unresolved counts;
- failure-reason counts;
- total and maximum search-node use;
- total remote freighters used by accepted plans.

The regression test checks accounting and semantic partitions only. It intentionally does **not** assert a desired number or fraction of accepted seeds.

## Interpretation and next action

### If coordinated planning accepts most or all accepted placements

Then the prior 11/15 independent reservation conflicts were primarily a supplier-selection coordination artifact rather than evidence of insufficient aggregate freight or producer capacity. Stage-20E can treat the throughput/fleet/shared-producer feasibility question as causally characterized and continue to the remaining economic acceptance authorities, especially:

- whole-route monetary delivered cost;
- actual initial inventory/buffer stock and depletion exposure;
- initial source/facility ownership and ownership concentration;
- any still-unresolved shared endpoint/source transfer reservation semantics.

This evidence alone does not authorize changing production whole-seed acceptance.

### If the planner proves seeds infeasible

Inspect the explicit planner reason before changing any world-generation authority:

- `SINGLE_START_INFEASIBLE`;
- `GLOBAL_PRODUCER_CAPACITY_INSUFFICIENT`;
- `COORDINATED_ALLOCATION_INFEASIBLE`.

Do not compensate by adding hidden ships, fallback deposits, fake edges, lower demand or other rescue mutations.

### If seeds remain unresolved

The immediate problem is bounded-search evidence, not physical world quality. Improve or characterize the planner/search authority first. Do not classify unresolved seeds as rejected.

## Non-authorities

This diagnostic does not:

- change `Stage20GeneratedWorldProductionProbe` acceptance;
- rewrite the frozen representative baseline;
- alter the fixed seed corpus;
- change essential commodity demand rates;
- increase physical resource occurrence or extraction capacity;
- add topology edges or non-neighbor shortcuts;
- grant or materialize freight hulls;
- create starting inventory or buffer stock;
- assign facility/resource ownership;
- define delivered monetary cost.

Those remain separate Stage-20E authorities and must be closed explicitly before Stage 20F / Stage 21 begins.
