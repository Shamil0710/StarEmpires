# Stage 19E — Raids / Interdiction / Blockade

**Status:** implementation slice.

Stage 19E introduces the smallest physical warfare-operation language needed by later strategic war and economic-pressure stages. It composes existing Stage-15 fleet placement/routing, Stage-19A information, Stage-19B tactical intent, Stage-17.5 combat physics and Stage-18 physical economy. It does not introduce a parallel combat, pathfinding, market or production runtime.

## 1. Causal contract

```text
real aggressor FleetId
+ real system / topology edge / local entity
+ operational combat capability
        ↓
physical warfare operation is active
        ↓
actor actually observes the operation
        ↓
ordinary persistent SYSTEM/LINK threat intel
        ↓
existing route-risk / civilian reroute decision
        ↓
ordinary jumps / delays / avoidance / interception
        ↓
ordinary combat + destruction when contact occurs
        ↓
real cargo / ship / repair / replacement consequences
```

The operation itself never applies an economic percentage modifier.

## 2. Physical operation anchors

`PhysicalWarfareOperation` supports three operation kinds:

- `RAID` — one physical aggressor `FleetId`, one real system and one concrete local `EntityId` target;
- `INTERDICTION` — one physical aggressor `FleetId` and one existing undirected topology edge;
- `BLOCKADE` — one physical aggressor `FleetId` and one real star system.

`PhysicalWarfareOperationService` is read-only and reports an operation active only when:

1. the aggressor still exists as the same stable `FleetId`;
2. it is currently `IN_SYSTEM`, not detached in transit;
3. its local entity still exists;
4. it has an operational `CombatComponent`;
5. the target anchor exists in authoritative world state;
6. the aggressor is physically in the required target system/edge endpoint.

A destroyed, detached or otherwise absent fleet therefore cannot maintain an operation through a detached strategic flag.

### Interdiction geometry before Stage 20

The current pre-Stage-20 world has real neighbor topology and real jump timing but does not yet author local jump-gate/edge approach coordinates. Therefore Stage 19E uses the narrowest honest current anchor: an interdiction fleet must be materialized at either endpoint of the exact topology edge it interdicts.

This is intentionally not treated as remote edge-wide weapon range. Stage 20 may later refine the local operational geometry once physical jump-arrival/departure regions are generated. It must preserve the same rule that a fleet cannot interdict an unrelated edge from elsewhere in the galaxy.

## 3. Information boundary

`PlayerWarfareObservationService` is an explicit observation bridge, not a scanner.

Creating or maintaining a physical operation does **not** automatically reveal it to the player and does not change route planning. The caller must supply an operation actually observed in that actor's information domain together with observed danger/confidence. The service revalidates physical activity and writes only through the existing `PlayerThreatIntelService`:

- `RAID` / `BLOCKADE` → ordinary `SYSTEM` danger;
- `INTERDICTION` → ordinary `LINK` danger.

Discovery, topology validation, canonical link identity, observation ordering and persistence remain owned by the existing Stage-15 threat-intelligence path.

Consequently:

```text
hidden blockade != hidden route debuff
hidden interdiction != omniscient reroute
```

Only observed information can influence that actor's route choice.

## 4. Raid tactical behavior

`RaidTacticalPlanner` composes raid behavior through the existing Stage-19B `INTERCEPT` posture.

```text
actor-visible TrackState contacts
        ↓
ObservedThreatAssessmentService
        ↓
ObservedTacticalIntentPlanner(INTERCEPT)
        ↓
TacticalIntentCommandAdapter
        ↓
ordinary movement / weapon / ammunition / damage runtime
```

The wrapper does not inspect hidden ECS targets. A contact absent from the supplied actor-visible contact set cannot affect raid behavior. Unknown disposition may cause cautious movement, but autonomous fire still requires the existing Stage-19B known-hostile + sufficient-track-quality admission.

## 5. What Stage 19E explicitly does not do

Stage 19E does **not**:

- subtract production throughput because a `BLOCKADE` object exists;
- delete cargo because a route is marked `INTERDICTION`;
- change market stock, storage, prices or wallets directly;
- grant a raider hidden accuracy, damage, speed, armor or sensor bonuses;
- create ammunition, reaction mass, repair material or replacement ships;
- invent topology edges, emergency corridors or teleporting patrols;
- damage a target directly from strategic code;
- let a non-combat or transit fleet maintain coercive presence;
- expose hidden enemy operations to player/AI planning;
- persist a second warfare-world state before the Stage-19H persistence owner is reached.

Real combat losses continue through the Stage-17.5 combat/destruction seams. Real economic consequences continue through Stage-18 storage, logistics, production, repair, shipyard and replacement constraints.

## 6. Acceptance

Stage 19E acceptance requires:

1. a blockade is active only while an operational combat FleetId is materially present in the target system;
2. moving that fleet into detached transit immediately makes the local blockade inactive;
3. interdiction can target only an existing topology edge;
4. raid can target only a concrete local entity and cannot target the aggressor itself;
5. validating an operation is read-only with respect to world/economic state;
6. an unobserved physical interdiction does not alter actor threat intel or route choice;
7. observing that interdiction writes ordinary LINK threat intel;
8. the existing route planner may then select a longer real discovered alternative route;
9. raid tactical intent uses actor-visible contacts only;
10. an unknown-disposition raid contact cannot authorize autonomous fire;
11. a known-hostile sufficiently tracked contact may request fire only through the existing production command/combat path;
12. repeated decisions over identical state are deterministic.

## 7. Transition to Stage 19F

**Stage 19F — Warfare ↔ Stage-18 physical economy** consumes the consequences of these operations rather than adding abstract wartime modifiers.

The next causal closure is:

```text
observed interdiction / raid / blockade
→ changed physical routing + contact probability
→ actual combat / destruction / delay
→ missing delivered mass / damaged ships / spent ammunition / reaction mass
→ Stage-18 storage, repair, manufacturing and shipyard demand
→ measurable readiness / shortage / replacement pressure
```

Stage 19F must demonstrate this chain using physical quantities and ordinary Stage-18 state. It may not replace missing material with a generic `war exhaustion` production multiplier.
