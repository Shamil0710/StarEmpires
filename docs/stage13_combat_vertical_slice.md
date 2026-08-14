# Stage 13 — Combat Vertical Slice

Status: **COMPLETE — PR #35. Functional merge main: `8023b780a05f280ce906585a5ced047cfde5e1f2`.**

## Goal

Stage 13 adds the first reusable deterministic physical combat pipeline without creating separate player and AI combat rules.

The core invariant is:

```text
player combat input ─┐
                     ├─> CombatCommandComponent
CombatAI intent ─────┘
                              ↓
                      CombatSystem fixed tick
                              ↓
                       CombatController
                 range / cooldown / shield / hull
                              ↓
                  lethal DestructionRequest
                              ↓
                WorldSimulation.destroyEntity
                              ↓
              Stage-9 destruction + salvage
```

Player and AI therefore submit intent to one authoritative fire/damage path. Combat never grants hidden money/resources, removes entities through a parallel lifecycle or creates scripted replacement assets.

## 13A — Data-driven weapon model

The canonical content catalog now contains explicit weapon definitions. The first production weapon is:

- `weapon.pulse_laser_mk1`;
- display name: `Импульсный лазер Mk I`;
- damage per shot: 21;
- cooldown: 0.5 simulation seconds;
- range: 150 world units.

`ship.guard_frigate` references the weapon through `weaponId`. Its legacy 42 DPS envelope is preserved as 21 damage every 0.5 seconds. Weapon values participate in the semantic content fingerprint, so gameplay-relevant weapon changes invalidate incompatible content fingerprints rather than silently changing save semantics.

`ContentCatalogLoader` validates that combat archetypes reference a real weapon and that non-combat archetypes do not declare one.

## 13B — Shared command and authoritative damage

Two transient ECS components define runtime intent/state:

- `CombatCommandComponent` — persistent local target identity plus `fireRequested`;
- `CombatRuntimeComponent` — equipped weapon ID and remaining cooldown.

Neither component contains a private player/AI damage formula.

`CombatController.tryFire(...)` is the single Stage-13 fire boundary. It validates:

- attacker and target are distinct live combat entities;
- both have physical transforms;
- attacker has a resolvable data-driven weapon;
- weapon cooldown is ready;
- target is inside physical weapon range.

A successful hit applies damage in deterministic order:

```text
weapon damage
→ shields absorb as much as possible
→ remaining damage reaches hull
→ hull/shields clamp to zero
→ weapon cooldown starts
```

The Stage-13 slice uses instantaneous hit resolution. Projectile travel, interception and advanced weapon classes are deliberately not implied by this implementation.

## 13C — Player and AI use the same commands

`CombatAISystem` performs target selection only. It does not mutate shields/hull and does not calculate damage.

The current minimal AI policy is intentionally narrow:

- only live combat entities are considered;
- own `PlayerControlledComponent` entity is not driven by CombatAI;
- a different runtime faction ID is treated as hostile for this vertical slice;
- only targets already inside weapon range are considered;
- nearest target wins;
- exact distance ties use the lowest persistent `EntityId`.

The explicit EntityId tie-break is covered by `CombatAISystemTest` and keeps the decision deterministic regardless of collection iteration accidents.

`PlayerRuntime` exposes:

- `selectCombatTarget(EntityId)`;
- `setFireIntent(boolean)`;
- `clearCombatIntent()`.

These methods write the same `CombatCommandComponent` consumed by `CombatSystem`. Docking and jump initiation clear combat intent. Player code never applies damage directly.

## 13D — Physical destruction and salvage seam

`CombatSystem` never removes an Ashley entity while iterating the engine. A lethal shot instead emits an immutable `DestructionRequest` containing persistent victim/attacker IDs.

After simulation advancement, `CombatDestructionResolver`:

1. drains lethal requests in deterministic system order;
2. revalidates that the physical victim still exists and has zero hull;
3. calls the existing world boundary:

```java
WorldSimulation.destroyEntity(
    systemId,
    victimId,
    DestructionPolicy.salvageResources())
```

This reuses the Stage-9 destruction lifecycle, FleetId reconciliation, resource accounting and salvage semantics. Resource cargo can become physical salvage; destroyed assets disappear from the real world registry. Combat does not respawn or replace them for free.

## Simulation integration

The local Ashley pipeline now contains:

```text
MarketSystem
ConsumptionSystem
ProductionSystem
AsteroidSpawnSystem
MiningSystem
TradeAISystem
CombatAISystem
CombatSystem
PriceRecorderSystem
```

Combat therefore advances from the same fixed/strategic simulation clocks as the rest of the world. The active local system provides the intended tactical fidelity. Aggregated remote combat fidelity remains explicitly outside this vertical slice and will be revisited with Stage 18 strategic warfare.

## End-to-end Stage-13 acceptance

`Stage13CombatAcceptanceTest` proves two complementary paths.

### Shared controller rules

- a target at 151 units is rejected as `OUT_OF_RANGE` for a 150-unit weapon;
- an in-range 21-damage hit against 10 shields applies 10 shield damage and 11 hull damage;
- an immediate second shot is rejected by cooldown.

### Player-versus-AI physical destruction

The acceptance scenario uses existing physical world fleets, gives the player combat capability for the isolated test setup, positions two different-faction ships in range and proves:

```text
player selects target through PlayerRuntime
→ player writes shared fire intent
→ AI writes the same command type
→ both fire through CombatSystem / CombatController
→ shields/hull change through one path
→ lethal shot emits persistent victim ID
→ ordinary WorldSimulation destruction executes
→ destroyed FleetId leaves the world registry
→ physical resource cargo is transferred to salvage
→ surviving FleetId remains live
```

No test-only damage shortcut is used to finish the kill after combat begins.

## CI evidence

Final pre-merge PR CI run **#915** (`31789559847`) completed successfully on Java 17:

- **412 tests run**;
- 0 failures;
- 0 errors;
- 0 skipped;
- JaCoCo thresholds passed;
- strict Javadoc gate passed;
- shaded desktop JAR packaged and uploaded.

The Stage-12 playable trade acceptance and all previous persistence/economy/world benchmarks remained green in the same run.

## Deliberate limitations / later-stage seams

Stage 13 intentionally does **not** attempt to solve the complete combat game:

- no projectile simulation or advanced weapon families;
- no advanced combat VFX;
- no pursuit/interception/tactical maneuver AI;
- no diplomacy-aware rules of engagement or fleet doctrine;
- no strategic fleet warfare, blockade or territory capture;
- no fidelity guarantee for aggregated remote tactical combat;
- target/fire intent is transient;
- sub-second weapon cooldown is transient across save/load;
- salvage collection itself is not yet a complete player progression loop;
- combat loss does not yet own a dedicated replacement-demand planner beyond the existing physical/economic destruction mechanisms;
- the current Stage-12 downloadable desktop harness does not yet expose a finished combat HUD/targeting UX.

The presentation/UI gaps are intentionally carried into Stage 14 and the parallel `docs/ui_navigation_roadmap.md` rather than being hidden until final polish.

## Definition of Done

A player-controlled ship and an AI ship can enter the same physical combat model, select targets, exchange deterministic data-driven weapon fire, validate physical range/cooldown, damage shields then hull, destroy a real world asset through the ordinary destruction pipeline and expose physical salvage/loss consequences. **Completed.**
