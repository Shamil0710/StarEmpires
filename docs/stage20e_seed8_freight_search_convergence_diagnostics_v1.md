# Stage 20E — seed 8 freight search convergence diagnostics v1

## Status

This is a **targeted read-only search characterization**, not a world-generation acceptance policy.

Diagnostic version: `stage20e.seed8-freight-search-convergence-diagnostics.v1`.

## Why seed 8 is measured

The coordinated fixed-corpus evidence evaluated the unchanged representative seed corpus under:

- representative v2-candidate generation and placement;
- the physical 13-freighter service-capacity requirement per faction start;
- finite producer capacities;
- globally coordinated supplier/freight planning.

Of the 15 seeds whose faction-start placement was accepted:

- 12 were globally feasible;
- 2 were proved infeasible by aggregate physical producer scarcity;
- seed 8 was the only unresolved case because the 2,000-node CI search measurement budget was exhausted.

The unresolved result is not evidence that seed 8 is physically invalid. Before changing any physical authority, the search behavior must be characterized.

## Frozen physical inputs

This diagnostic regenerates **exact root seed 8** with `Stage20RepresentativeGeneratedWorldProbeProfileV2` and does not replace it with another seed.

It uses:

- the same accepted faction-start placement;
- the same physical resource/supply state;
- the same generated topology and jump-edge state;
- the same local infrastructure layouts;
- the same representative freight payload;
- the same `Stage20BootstrapFreightCapacityRequirementProfile` 13-freighter service-capacity budget per start;
- the same `Stage20WholePlacementCapacityCorpusDiagnostics.physicalRoutes(...)` helper;
- the same `Stage20CoordinatedWholePlacementFreightPlanner` implementation.

No topology, demand, resources, producer capacity, route timing or freight authority is altered between search attempts.

## Search budget ladder

The diagnostic evaluates this bounded ladder:

1. 2,000 nodes — exact existing coordinated-corpus measurement budget;
2. 4,000 nodes;
3. 8,000 nodes.

The generated physical world is built once. The planner is then rerun deterministically from its initial state at each budget.

Evaluation stops at the **first resolved** result, where resolved means either:

- `ACCEPTED`; or
- proved `INFEASIBLE`.

`UNRESOLVED_SEARCH_BUDGET` remains unresolved and permits the next ladder rung.

The ladder is diagnostic evidence only. Its largest value is not a new minimum CI budget, a generated-world quality threshold or permission to reject seeds that require more search.

## Interpretation

### Resolution as `ACCEPTED`

If seed 8 resolves as accepted at 4,000 or 8,000 nodes, the 2,000-node corpus result is confirmed to be a bounded-search artifact rather than physical infeasibility.

The next decision is then between:

- improving deterministic planner pruning/search efficiency; or
- versioning a larger **diagnostic** evidence budget if the measured runtime is acceptable.

Either choice is search/evidence engineering, not physical balancing.

### Resolution as proved `INFEASIBLE`

If a larger bounded search proves infeasibility, inspect the explicit planner reason. Do not compensate with hidden resources, extra ships, demand reduction or topology rescue.

### Still unresolved at 8,000

If all three rungs remain unresolved, do not move to world tuning and do not classify seed 8 as rejected. The next slice must instrument or improve the coordinated planner itself, preferably with admissible pruning or stronger search-state diagnostics.

## Non-authorities

This diagnostic does not:

- modify `Stage20CoordinatedFreightCorpusDiagnostics.SEARCH_NODE_BUDGET_PER_SEED`;
- change production whole-seed acceptance;
- rewrite historical benchmark/evidence files;
- alter the fixed seed corpus;
- change 50/25 kg/s essential demand;
- change the 13-freighter service-capacity authority;
- add deposits, extraction capacity or jump edges;
- materialize freight ownership;
- create inventory buffers;
- define monetary delivered cost.

Stage 20E remains active until search evidence and the remaining economic acceptance authorities are closed explicitly.
