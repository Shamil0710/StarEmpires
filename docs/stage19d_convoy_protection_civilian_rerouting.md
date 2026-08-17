# Stage 19D — Convoy Protection / Civilian Rerouting

**Status:** implementation slice.

Stage 19D composes already-authoritative Stage-15 strategic movement, Stage-19A observed threat intelligence and Stage-19B tactical intent. It does not introduce a second combat, pathfinding, logistics or economy runtime.

## 1. Civilian rerouting

`CivilianReroutePlanner` is a read-only decision seam over `PlayerFleetRoutePlanner`.

```text
current physical FleetId
+ current discovered origin/destination
+ actor-stored threat intelligence
+ previous transient selected path
        ↓
PlayerFleetRoutePlanner
        ↓
CONTINUE / REROUTE / HOLD
```

The selected path is still produced exclusively by the Stage-15 cumulative route-risk planner. Therefore:

- only discovered systems may be traversed;
- only real topology neighbor edges may be used;
- physical Stage-10 jump timing remains part of route cost;
- system/link exposure, uncertainty and actor vulnerability remain visible diagnostics;
- cargo/damage/mobility continue to affect actor-specific vulnerability through existing physical state;
- a missing discovered route produces `HOLD`, never an emergency edge, teleport or hidden safe corridor;
- the Stage-19D planner does not mutate cargo, storage, production, money, consumables, threat intelligence or world topology.

The existing `PlayerFleetOrderExecutor` already resolves `nextRiskAwareHop` again before each ordinary jump. Consequently changed actor-known threat information changes the next real jump without replacing the durable economic/formation order. Stage 19D makes that reroute decision explicit and testable rather than adding parallel navigation state.

## 2. Convoy protection

Strategic convoy membership/proximity remains the existing durable `ESCORT` FleetId order. The Stage-15 route planner may reduce the protected actor's expected route-loss vulnerability only when a real owned operational escort is physically co-located; observed route danger itself is never rewritten.

`ConvoyProtectionPlanner` adds the Stage-19D local tactical composition:

```text
actor-visible TrackState contacts
+ real escort position
+ known protected convoy position
        ↓
ObservedTacticalIntentPlanner(SCREEN)
        ↓
existing TacticalIntentCommandAdapter
        ↓
ordinary flight/combat runtimes
```

The wrapper deliberately delegates target choice, screening geometry and fire admission to Stage 19B. Unknown-disposition contacts may cause cautious screen movement, but autonomous fire still requires actor-known hostile disposition plus sufficient production track quality.

No contact absent from the escort actor's information domain can affect the decision.

## 3. Physical / economic ownership boundaries

Stage 19D does **not**:

- create cargo or replacement goods;
- grant escort speed, armor, ammunition, fuel or reaction mass;
- alter station storage or Stage-18F transfer semantics;
- apply abstract convoy-safety or route-throughput bonuses;
- teleport escorts into formation;
- bypass Stage-10 jump timing;
- inspect hidden remote enemy entities;
- execute raids, interdiction or blockades.

Stage 18 remains authoritative for physical supply/storage/production. Stage 17.5 remains authoritative for ship capability, sensors, weapons, damage and consumables.

## 4. Acceptance

Stage 19D acceptance requires:

1. a newly observed dangerous link can select a longer real discovered alternative route;
2. repeating an unchanged decision is deterministic;
3. an undiscovered/unreachable destination yields `HOLD` rather than invented topology;
4. route assessment is read-only with respect to stored threat intelligence;
5. a known-hostile observed tactical track produces `SCREEN` intent on the threat-facing side of the protected convoy;
6. contacts absent from actor knowledge cannot influence convoy behavior;
7. unknown disposition may drive screening movement but cannot authorize autonomous fire.

Existing Stage-15 acceptance remains part of the contract: ordinary delegated trade/mining/supply orders keep their durable job while local civilian survival temporarily interrupts physical motion, and every inter-system leg continues through the shared risk-aware jump path.

## 5. Next slice

**Stage 19E — raids / interdiction / blockade.**

19E may disrupt physical routes, facilities and logistics only by acting on real assets/locations and producing ordinary simulation consequences. It must not model blockade as an abstract production penalty or a magical market modifier.
