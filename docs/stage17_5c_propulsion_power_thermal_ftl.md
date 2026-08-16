# Stage 17.5C — Propulsion, Reaction Mass, Power, Thermal and FTL

> **Status: COMPLETE IMPLEMENTATION SLICE — awaiting exact-head merge gate**  
> Date: 2026-08-16  
> Base green `main`: `17f6932350cca0a2942b596fc82ffb334015c90d`  
> Runtime/content verification head: `9a6fa4c04031c6b9d6da271eb5fb800841dc30ef`  
> CI: run `#2410` / Actions `31958676279` / Java 17 verification job `95193194737` — **SUCCESS**.

## 1. Purpose

Stage 17.5C promotes the Stage-17.5B derived engineering boundary into deterministic mutable physical runtime for propulsion, reaction mass, shared electrical power, thermal state and fitted FTL.

Canonical operating boundary:

```text
InstalledFit
+ RuntimeState
+ OperatingCommand
+ fixed dt
→ ShipEngineeringRuntime.advance(...)
→ TickResult
```

FTL planning/commit remains a separate explicit operation because an inter-system transition is a world/FSM event rather than an ordinary local propulsion tick:

```text
InstalledFit + RuntimeState
→ planJump(...)
→ JumpPlan
→ commitJump(...) exactly once at jump commit boundary
```

The runtime does not accept player ownership, AI ownership, faction, doctrine role or ship-class name as a performance input.

---

## 2. Propulsion and physical reaction mass

The production runtime preserves the accepted equations:

```text
a = F / m
mdot = F / ve
Pjet >= 0.5 F ve
deltaV = ve ln(m0 / m1)
```

Reaction mass is a real interface-bound persistent consumable. A thrusting drive:

1. resolves requested throttle against the installed drive;
2. applies the current physical thrust ceiling for that mount;
3. limits operation by available reaction mass;
4. consumes reaction mass from the same `ConsumableState` used by the central derived calculator;
5. recomputes derived mass/acceleration/delta-v from the resulting physical state.

No class-name propulsion multiplier exists.

### Jet-power closure

Stage 17.5C validates that every operating main drive has a positive authored `jet_power_w` and that it is not below the ideal kinetic minimum:

```text
0.5 * thrust_n * exhaust_velocity_mps
```

The existing 17.5A escort-drive demonstrator was therefore closed with:

```text
F  = 13.2 MN
ve = 65 km/s
Pjet(min) = 429 GW
```

`continuousPowerDemandW = 2.1 GW` remains a separate onboard electrical demand. It is not reused as exhaust jet power.

The authoring value is a physics-closure seed for the schema demonstrator, not a claim that the current module is final balance content.

---

## 3. Drive damage seam

Stage 17.5C does not introduce a generic `damageMultiplier`.

Runtime state contains explicit per-mount physical thrust ceilings:

```text
thrustLimitNByMount
```

A damaged drive therefore loses capability by reducing its real thrust ceiling. The same reduced thrust also reduces reaction-mass flow through:

```text
mdot = F / ve
```

The Stage-17.5F protection/compartment/subsystem damage solver will later authoritatively change these physical capability limits from actual damage events. Stage 17.5C supplies the runtime consequence seam without inventing the later damage model.

---

## 4. Shared electrical power

Stage 17.5C distinguishes:

- continuous generation;
- continuous demand;
- peak demand;
- shared `ENERGY_STORAGE`;
- module-local stored energy;
- deterministic brownout/load shedding.

Only explicitly shared `ENERGY_STORAGE` contributes to the ship-wide stored-energy bus. Local sensor/weapon buffers are not silently pooled into propulsion or FTL energy.

When demand exceeds available supply/storage, loads are shed deterministically from authored/runtime priority rather than receiving hidden free power.

FTL charge energy is also physically split between:

```text
reactor contribution during spool
+ required draw from shared ENERGY_STORAGE
```

so a ship is not incorrectly required to hold its entire jump energy in batteries when the reactor can supply part of it during spool.

---

## 5. Thermal topology

Runtime thermal state is explicitly layered:

```text
module-local heat
→ coolant transfer
→ ship heat bus / thermal store
→ radiator rejection to space
```

The mutable state includes:

- `localHeatJByMount`;
- `shipHeatStoredJ`;
- `coolantBusCapacityW`.

Cooling damage can therefore reduce real heat-transfer capacity. Heat then accumulates locally and modules throttle/shut down through thermal constraints rather than a generic combat debuff.

A zero-capacity ship heat store is not treated as a free heat sink: incoming heat saturates the thermal path instead of disappearing.

Radiator rejection and coolant transfer remain physical rates in watts; stored heat remains joules.

---

## 6. Fitted FTL capability

`ShipEngineeringRuntime.planJump(...)` resolves fitted FTL from installed modules and current physical state.

A jump plan includes:

```text
translated mass
translated-mass limit
required translation energy
reactor contribution during spool
shared stored-energy draw
charge power
spool time
edge transit time
cooldown
jump heat
failure reason
```

Planning rejects deterministically when applicable for:

- no fitted FTL capability;
- translated mass above the drive envelope;
- active cooldown;
- insufficient shared stored energy after reactor contribution;
- invalid physical authored parameters.

Local sensor/weapon energy buffers cannot satisfy FTL demand.

The Stage-10 short topology edge remains compatibility/test geometry. Final world-scale edge distributions are intentionally deferred to Stage 20 spatial-scale calibration.

---

## 7. One authoritative inter-system jump FSM

Stage 17.5C does **not** create a second fitted-ship jump path.

`FleetJumpService` remains the single ordinary neighbor-edge FSM:

```text
MOVING_TO_JUMP
→ JUMP_PENDING
→ IN_TRANSIT
→ ARRIVING
```

For a fleet with `EngineeringComponent`:

- request validates current fitted FTL state but spends nothing;
- `MOVING_TO_JUMP → JUMP_PENDING` re-plans and uses fitted spool time;
- physical state is checked again at the commit boundary;
- `commitJump` executes exactly once immediately before `FleetWorldService.beginTransfer`;
- fitted `edgeTransitSeconds` determines detached transit duration;
- the existing detach/attach boundary preserves the same `FleetId` and physical engineering payload.

If state becomes invalid before commit, the jump is cancelled without spending energy and without detaching the fleet.

A fleet that has `EngineeringComponent` may not fall back to legacy timing when fitted physics rejects the jump.

Fleets with no engineering component retain the historical Stage-10 timing path only as an explicit migration compatibility seam. This does not create a second rule for a fitted player ship or fitted AI ship.

Ordinary inter-system movement remains one explicit `GalaxyTopology.neighbors(...)` edge per hop.

---

## 8. Persistent engineering state — GameState v4

Stage 17.5C raises local `GameState` schema from v3 to **v4**.

`EngineeringComponent` stores only authoritative source state:

```text
InstalledFit
+ RuntimeState
```

The persistent `EntityState.EngineeringState` contains:

- fitted hull ID;
- installed module assignments;
- physical consumable/interface loads;
- shared-bus energy;
- ship stored heat;
- local heat by mount;
- thrust ceilings by mount;
- coolant-bus capacity;
- FTL cooldown by mount.

Derived mass, acceleration, delta-v, margins and other calculated values are **not** serialized.

Historical v1-v3 local saves migrate conservatively:

```text
engineering = null
```

No fit, reaction mass, battery charge, coolant capacity or cooldown is fabricated from a legacy archetype/class name.

---

## 9. Fleet transfer and save/load continuation

Engineering state is preserved across:

```text
Ashley entity
→ EntityState
→ binary GameState v4
→ Ashley entity
```

and across:

```text
IN_SYSTEM
→ detach
→ FleetTransitState.EntityState
→ optional WorldState save/load
→ attach
→ IN_SYSTEM
```

The world-level `FleetId` remains unchanged.

A fitted FTL jump saved after entering `IN_TRANSIT` has already paid its physical commit. Restoring that active jump and later arriving does not subtract jump energy or add heat/cooldown a second time.

---

## 10. Acceptance evidence

Automated acceptance now covers:

| Acceptance | Result |
| --- | --- |
| Reaction-mass depletion changes mass / acceleration / delta-v | PASS |
| Shared power bus excludes sensor/weapon local buffers | PASS |
| Reactor contribution reduces required FTL battery draw during spool | PASS |
| Deterministic load shedding under power deficit | PASS |
| Cooling-path damage accumulates local heat and can remove drive thrust | PASS |
| Explicit damaged-drive thrust ceiling reduces thrust and mass flow without generic debuff | PASS |
| FTL translated-mass envelope rejects overweight ship | PASS |
| Fitted request/spool/transit uses one existing neighbor-only FleetJumpService FSM | PASS |
| Jump resources commit exactly once at `JUMP_PENDING → IN_TRANSIT` | PASS |
| Cancelling before commit spends no jump energy | PASS |
| Fitted physical rejection cannot fall back to legacy jump | PASS |
| Physical state change during approach revalidates and cancels before commit | PASS |
| Engineering ECS/binary round-trip preserves physical state | PASS |
| Fleet detach/transit/attach preserves engineering state and `FleetId` | PASS |
| Active fitted FTL save/load reaches destination without double commit | PASS |
| Default Stage-17.5A drive satisfies `Pjet >= 0.5 F ve` after production closure | PASS |

The runtime/content verification head `9a6fa4c04031c6b9d6da271eb5fb800841dc30ef` passed the complete Java-17 `clean verify` gate in CI run `#2410`.

---

## 11. Compatibility and explicit non-goals

Stage 17.5C deliberately does **not** claim that every existing live legacy ship has already been converted into a fully authored fitted production hull.

The Stage-17.5A `ship-engineering-v1.json` remains a schema demonstrator, not the final complete fleet catalog. Its existing drive was made physically compatible with the C runtime, but Stage 17.5C does not add arbitrary battery/FTL modules solely to make the demonstrator pretend to be final content.

Still deferred:

- Stage 17.5D sensor/signature/track/EW equations;
- Stage 17.5E weapon/ammunition/guidance/PD runtime;
- Stage 17.5F protection/compartment/subsystem damage solver that produces physical damage-state changes;
- Stage 17.5G shipyard/refit/repair/maintenance lifecycle;
- Stage 17.5H capability UI/API/full migration surfaces;
- advanced tactical AI — Stage 19;
- production world spatial calibration — Stage 20.

The runtime also does not invent per-frame simulation work for empty/far space; later LOD/materialization must preserve this physical state rather than changing the rules.

---

## 12. Stage 17.5C completion decision

The Stage-17.5C definition of done is satisfied at the production engineering/runtime boundary:

- carried physical mass and reaction mass affect motion through the common equations;
- drive damage has an explicit physical thrust-capability seam;
- power/storage/load shedding use shared physical budgets;
- local/coolant/ship/radiator thermal topology is active;
- FTL is a fitted mass/energy/power/heat/time/cooldown capability;
- fitted FTL composes with the existing neighbor-only world jump FSM;
- physical mutable state survives entity persistence, fleet transfer and active-jump save/load;
- no player-only capability path or class-name multiplier was introduced.

**Next implementation slice after merge gate: Stage 17.5D — signatures / sensors / tracks / datalink / EW.**
