# Stage 20E — Coordinated freight production acceptance profile v1

> Status: **CANDIDATE COMPUTATIONAL ACCEPTANCE POLICY / NO PHYSICAL AUTHORITY CHANGE**  
> Version: `stage20e.coordinated-freight-acceptance-profile.v1`

## Purpose

The resolved freight production primitive requires two independent caller authorities:

```text
finite physical freight capacity per accepted start
+ bounded exact-search work per commodity
```

The first is already derived physically by `stage20e.bootstrap-freight-capacity-requirement.v1` and currently equals `13` representative early civilian freighters per ordinary faction start.

The second is computational, not physical. This profile makes the bounded search work explicit rather than hiding `2000` inside the production probe.

## Current v1 policy

```text
physical capacity authority = stage20e.bootstrap-freight-capacity-requirement.v1
required freighters/start = 13
searchNodeBudgetPerCommodity = 2000
```

The `2000` work bound comes from the verified production-path fixed 1..16 corpus where the same bound reproduced the already accepted physical frontier closure:

```text
accepted placement seeds = 15
freight accepted = 12
freight infeasible = 3
freight unresolved = 0
total frontier search nodes = 20595
```

This does **not** turn `12/3/0` into a pass-rate target. The corpus is evidence that the selected deterministic work bound is currently sufficient for the representative fixed set. A future generated seed that exhausts the same budget remains `UNRESOLVED_AUTHORITY`; it is not physically infeasible and must not be repaired by changing resources, topology, demand or fleet capacity.

## Separation of authorities

The profile deliberately embeds the already derived capacity object instead of restating `13` as an authored number. Therefore a later physical recalibration changes the derived capacity through its own authority and cannot silently diverge from this computational policy.

Likewise, increasing the search-node budget in a future version may reduce computationally unresolved worlds but cannot change the underlying physical feasible set.

## Stage-22 boundary

The Stage-22 review flag is inherited exactly from the physical freight-capacity authority. This profile may not clear that review requirement independently.

## Explicit non-authorities

This slice does not:

- change resources, producer throughput or demand;
- change topology, FTL, payload or local handling;
- create ships or ownership;
- grant cargo, inventory, money or deliveries;
- accept or reject any world by itself;
- inspect generated seed outcomes at runtime.

`deriveCurrent()` only reconstructs the accepted physical capacity and the versioned computational bound with recorded provenance.

## Next causal slice

The production generated-world profile can now supply this object explicitly to the resolved-freight probe path. Whole-seed composition must continue to preserve:

```text
search budget exhausted
→ coordinated freight unresolved
→ whole seed UNRESOLVED_AUTHORITY
```

never:

```text
search budget exhausted
→ physical INFEASIBLE
```
