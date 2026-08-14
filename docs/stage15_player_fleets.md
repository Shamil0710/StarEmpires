# Stage 15 — Player Fleets, Autonomous Orders and Strategic Command

Status: **COMPLETE after functional PR #47 + #48 + #49 merge evidence and final green aggregate CI.**

## Goal

Stage 15 turns multiple physically owned `FleetId` objects into a controllable company fleet without introducing a passive-income layer or a second movement/economy implementation.

The player can delegate persistent work while continuing to directly control one active ship. Delegated ships remain ordinary physical world objects: they accelerate/brake through Stage-14 inertia, jump through the Stage-10 transit FSM, carry real inventory, mine finite asteroids and trade with live market wallets/stocks.

## Implemented slices

### 15A/B — persistent orders + shared physical movement

PR #47 established the durable order contract:

- `HOLD`
- `MOVE`
- `TRADE`
- `MINE`
- `FOLLOW`
- `ESCORT`
- `PATROL`

`PlayerFleetOrderState` stores only stable world IDs/value data. `PlayerState` keeps at most one canonical order per owned `FleetId` and the playable persistence layer serializes it.

Inactive owned fleets no longer continue their previous NPC job accidentally. Direct control always wins for the active FleetId. Delegated movement uses:

```text
persistent fleet order
→ FlightCommandComponent
→ AutonomousFlightSystem
→ FlightDynamics
→ authoritative Transform
```

Thus delegated owned ships share the same finite acceleration/braking and cargo-mass effects as the player.

### 15C — physical delegated TRADE / MINE

PR #48 added a physical company-economy boundary.

TRADE:

```text
owned physical FleetId
→ inertial approach to source
→ real berth boundary
→ shared TradeController
→ real ship cargo increases
→ Stage-10 jump transit
→ inertial approach to destination
→ shared TradeController
→ real cargo decreases
→ station/player-company wallets transfer money
```

MINE:

```text
owned mining FleetId
→ inertial approach to explicit finite asteroid
→ MiningCommandComponent
→ shared MiningSystem extraction boundary
→ asteroid reserve decreases
→ real ship cargo increases
→ physical delivery to assigned market
→ shared TradeController sale
→ company wallet changes only after sale
```

Mining command mode is shared by direct player mining and delegated owned mining. No delegated mining income is granted without actual extraction and sale.

### 15D — civilian survival / interruption / resume

Civilian delegated fleets react only to a threat they can actually observe: a live non-owned combatant must currently target that owned FleetId through the real combat command state.

A threat does **not** delete or replace the durable economic order. It temporarily interrupts execution:

```text
TRADE / MINE / MOVE intent
→ actual attack observed
→ shared inertial flee intent
→ threat-clear hysteresis
→ original persistent order resumes
```

The transient survival state uses a bounded 30-tick clear hysteresis to prevent immediate flee/resume oscillation.

### 15E — non-omniscient cumulative whole-route risk

Playable schema v4 adds bounded persistent threat intelligence:

- `SYSTEM` observations;
- `LINK`/corridor observations;
- raw non-negative danger exposure score;
- observation confidence;
- authoritative observation tick.

A danger score is **not** treated as a probability.

`PlayerThreatObserver` only inspects systems that physically contain an owned FleetId and records actual attacks against owned fleets. It never scans unobserved remote systems.

`PlayerFleetRoutePlanner` evaluates every traversed segment:

```text
RouteExposure =
    Σ effectiveSystemDanger
  + Σ effectiveLinkDanger × linkExposureTime
  + Σ unknownSegmentUncertainty
```

Stored observations lose influence with age. Route choice is actor-specific and accounts for:

- real cargo utilization;
- current hull/shield damage;
- shared inertial mobility/acceleration;
- real co-located operational escort protection.

An escort can reduce expected actor loss exposure but never rewrites or removes the underlying observed danger.

All inter-system delegated orders use the same risk-aware next-hop planner instead of destination-only danger or plain BFS.

### 15F — FOLLOW / ESCORT / PATROL

`FOLLOW` resolves the physical target FleetId continuously and maintains a real separation radius through shared inertial movement.

`ESCORT` uses the same formation/navigation boundary. A real operational escort contributes to protected-fleet route-risk mitigation only while physically co-located. Advanced weapon-aware screening, focus fire and formation combat remain deliberately gated behind Stage 17.5/18.

`PATROL` cycles its persistent ordered system list. A patrol physically dwells at each reached waypoint before selecting the next system and traveling through the ordinary risk-aware Stage-10 transit path.

### 15G — first functional global fleet map

The first strategic command layer is implemented as a thin read/command UI over authoritative state.

`GlobalFleetMapModel` exposes only:

- discovered StarSystems;
- topology links whose endpoints are discovered;
- player-owned FleetIds;
- active transit destination;
- persistent fleet order;
- stored player threat intelligence.

It does **not** enumerate hidden remote NPCs or expose arbitrary remote ECS state.

`PlayerStrategicCommandService` submits ordinary persistent commands and previews the exact same cumulative-risk route planner used by runtime execution.

The Stage-15 desktop strategic-map harness supports fleet/system selection, MOVE/HOLD/FOLLOW/ESCORT/PATROL assignment, route-risk preview, pause/time scale and the shared save/load file. GPU rendering is kept separate from the headless-tested map model and command services.

## Persistence

Current playable schema: **v4**.

Persistent:

- player wallet/reputation/discovery;
- owned FleetIds and active FleetId;
- fleet orders;
- system/link threat observations with confidence and tick;
- world FleetId/transit/cargo/economy state through existing world persistence.

Transient by design:

- `FlightCommandComponent`;
- current flee vector/hysteresis runtime state;
- patrol dwell timer;
- manual mining command state.

Older playable schemas v1/v2/v3 migrate with empty threat intelligence rather than synthetic knowledge.

## Acceptance coverage

Stage-15 acceptance proves, across its functional PRs:

1. inactive owned fleets physically HOLD instead of continuing old NPC work;
2. MOVE uses finite shared acceleration/braking and survives save/load;
3. inter-system orders preserve the same FleetId through Stage-10 transit;
4. delegated TRADE physically buys, carries, jumps and sells real cargo;
5. delegated MINE decreases finite asteroid reserve, increases real cargo and earns money only after ordinary sale;
6. an actual attack interrupts a civilian order, produces only local observed intel, causes physical flee, uses hysteresis and resumes the original order;
7. dangerous intermediate systems/links affect whole-route choice even when the destination is unchanged;
8. loaded/damaged/less-mobile actors carry a higher risk cost;
9. FOLLOW converges through shared inertia rather than velocity snapping;
10. ESCORT reduces protected actor exposure only when a real escort exists and does not erase raw danger;
11. PATROL performs physical dwell and real Stage-10 transit while retaining its persistent cycle;
12. global-map projection cannot leak undiscovered topology or non-owned FleetIds;
13. a global-map MOVE persists ordinary intent without teleporting the physical fleet.

## Deliberate boundaries after Stage 15

Stage 15 guarantees the shared inertial order pipeline for **player-owned delegated fleets**. Legacy autonomous behavior of generic non-owned NPC `TradeAI`/`MiningSystem` remains a compatibility path and should be migrated progressively as later strategic AI work needs the richer movement boundary; it is not reclassified as a player-fleet feature merely to claim completion.

Current combat ROE is still the Stage-13 minimal different-faction model. Diplomacy-aware hostility, weapon-aware tactical positioning, screening, focus fire and advanced retreat doctrine remain gated behind Stage 17.5 combat depth and Stage 18 warfare AI.

The Stage-15 global map is the first functional strategic layer, currently exposed as a dedicated harness. Later fleet/station/faction stages expand and ultimately unify it with the normal game presentation instead of building another simulation path.

## Next stage

**Stage 16 — Player Construction / Stations** should reuse the already-physical Stage-9 construction project pipeline. The player must fund projects, move required materials and receive a real persistent station; instant UI construction and virtual material delivery remain forbidden.
