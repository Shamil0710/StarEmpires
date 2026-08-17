# Star Empires — Stage 17.5I implementation record

> Status: **IMPLEMENTATION COMPLETE / GREEN — 17.5I-A–I-F complete; merge/post-merge gate pending**  
> Base main: `c81115051755c3af4af899bd4cbb783d5a045a95`  
> Canonical branch: `agent/stage17-5i-combat-test-content-acceptance`  
> Canonical acceptance contract: `docs/stage17_5i_combat_test_content_visual_acceptance.md`  
> Exact implementation checkpoint: `750604aa0a6216a739a584544dc5e1a439ffb378` / CI **#2789 SUCCESS** / **868 tests, 0 failures**

## 1. Purpose

Stage 17.5I proves the already implemented Stage-17.5A–H physical ship/combat foundation across several materially different production-valid configurations. It does not create final Stage-22 faction/content balance.

Hard rule:

```text
production schemas/runtime
+ content-provisional representative assets
+ deterministic scenario matrix
+ presentation-only tactical prototype visuals
→ evidence that Stage 17.5 can close
```

No Stage-17.5I fixture may introduce hidden combat stats, class-name bonuses, free consumables, virtual repair, player-only physics, fake scalar balance prices or rendering authority.

## 2. 17.5I-A — representative physical content vocabulary

Status: **IMPLEMENTED / GREEN**.

The canonical pack supplies the six mandatory hull families through the ordinary `ShipEngineeringCatalogLoader` / `ShipFittingValidator` path:

- corvette-scale combat hull;
- frigate-scale general-purpose hull;
- destroyer-scale escort/strike hull;
- cruiser-scale heavy combat hull;
- civilian bulk freighter;
- tanker/logistics hull.

IDs remain explicitly `test` / `stage17_5i` namespaced and tagged `content_provisional`. Stable semantic fingerprints and materially different physical envelopes are regression-tested. None of this content is automatically Stage-22 canon.

## 3. 17.5I-B — equipment, ammunition, fit and doctrine diversity

Status: **IMPLEMENTED / GREEN; CONTENT REMAINS PROVISIONAL**.

Five required acceptance doctrines are physically distinct ordinary fits:

- Fleet A — kinetic line;
- Fleet B — missile strike;
- Fleet C — high-mobility / beam;
- Fleet D — defensive / EW;
- Fleet E — balanced control.

Differences arise from real module choice, mass, power, heat, thrust, reaction mass, sensor/EW, shield/protection, ammunition and launcher state. There are no doctrine-name performance multipliers.

Acceptance proves that loaded fits validate, real ammunition feeds resolve, firing consumes authoritative consumables, and sensor/shield/propulsion differences derive from physical fitted content.

## 4. 17.5I-C — deterministic combat matrix

Status: **IMPLEMENTED / GREEN**.

### Representative pair exchange

`Stage175ICombatAcceptanceHarness` executes the required matrix:

```text
A-A
A-B
A-C
A-D
A-E
B-C
B-D
B-E
C-D
C-E
D-E
```

The harness traverses production-derived capability, sensor observation, EW/track fusion, kinetic fire control/projectiles, guided ammunition/body/guidance, beam delivery, layered defense, shields, heavy impact/compartment damage and post-damage capability. Results are physical measurements plus stable SHA-256 fingerprints; there is no synthetic winner score or hit-chance shortcut.

### Required variants

`Stage175ICombatMatrixCatalog` covers:

- equal count;
- approximately equal fitted fleet mass from real `DerivedShipCalculator.totalMassKg()`;
- compact and dispersed formations;
- partial ammunition;
- pre-damage;
- thermal stress;
- degraded information;
- protected logistics.

Equal-cost comparison is explicitly:

`DEFERRED_UNTIL_STAGE18_COMPARABLE_COST_BASIS`

because Stage 17.5 has heterogeneous construction inputs but no complete comparable industrial/resource/facility cost basis. This avoids manufacturing fake economics before Stage 18.

### Physical multi-body saturation

`Stage175IFleetSaturationHarness` materializes every attacking ship copy with independent physical ammunition state, consumes one real launcher feed, creates one real `GuidedWeaponBody` per firing copy and passes individual threats into `LayeredDefenseScheduler`. Defense stations have explicit positions, finite rounds, finite support channels and thermal availability.

Increasing ship count therefore increases actual bodies, ammunition use and production-derived fleet mass instead of multiplying a post-hoc score. Formation spacing changes actual geometry. Empty stores create no missiles; thermal lockout removes defense assignments without deleting inbound threats.

### Integration defect found by I-C

I-C exposed that hull `baseSignatureGeometryAreaM2` existed in Stage-17.5A schema but was not seeded into runtime `radar_cross_section_m2`. Direct sensor unit tests had hidden the issue by injecting `SignatureState` manually.

The production path now is:

```text
hull.baseSignatureGeometryAreaM2
→ DerivedShipState.signatureContributions[radar_cross_section_m2]
→ ShipSensorEngineeringAdapter
→ SignatureState.radarCrossSectionM2
→ active radar equation
```

`DerivedShipSignatureGeometryTest` prevents regression. Stage 20 may replace the scalar midpoint seed with aspect/frequency-aware geometry without changing the common signature budget contract.

## 5. 17.5I-D — Tactical Prototype Visual Set

Status: **IMPLEMENTED / GREEN**.

The tactical prototype is deliberately split into a headless pure projection layer and an OpenGL presentation shell.

`TacticalPrototypeVisualSnapshot` provides deterministically sorted immutable glyphs for:

- top-down ships and wrecks;
- kinetic projectiles;
- guided missiles;
- interceptors;
- explicit deception/decoy contacts;
- cosmetic wreck debris;
- continuous beam segments;
- physical shield sectors and collapse/reserve state;
- shield / armor / penetration impacts;
- local compartment damage.

`Stage175ITacticalVisualProjection` consumes existing authoritative types directly:

- `ProjectileBody`;
- `GuidedWeaponBody`;
- `BeamWeaponRuntime.BeamSolution`;
- `ShieldFieldRuntime.Definition/State`;
- `ShipDamageRuntime.Snapshot`;
- `ElectronicWarfareState.DeceptionSource`;
- `KineticProtectionRuntime.Result`;
- physical hull geometry.

Rejected beams produce no beam visual; shield reserve/collapse comes from the real field state; damage markers come from real compartment integrity and authored compartment centers. Wreck/debris appears only after authoritative destruction state. Debris layout is deterministic cosmetic presentation and is never returned to collision/damage simulation.

`TacticalPrototypeRenderer` is a libGDX `ShapeRenderer` consumer of `TacticalPrototypeVisualSnapshot` only. It holds no simulation engine, combat service, entity mutation or persistence reference. Stage 23 may replace it with sprites/VFX without changing combat semantics.

Exact I-D checkpoint:

```text
head: ae8e7d641473331506807f83a201e1d34ab1804b
CI:   #2766
result: SUCCESS
```

## 6. 17.5I-E — full-chain physical / persistence acceptance

Status: **IMPLEMENTED / GREEN**.

### One shared engineering interval

Stage H left one integration risk: independent sensor, beam and shield-recharge calls could each observe the same continuous reactor margin in one tick.

`ShipEngineeringGrantService.IntervalBudget` now makes the interval reservation explicit and common to:

- `ShipObservationEngineeringService`;
- `ShipBeamEngineeringService`;
- `ShipShieldEngineeringService`.

Acceptance chain:

```text
sensor operation
→ measurement / track
→ beam admission
→ shield recharge
→ one shared continuous-power budget
→ physical ENERGY_STORAGE draw only for residual demand
→ bounded storage discharge power
→ deterministic local heat commit
→ denied operation leaves ship + budget unchanged
```

`Stage175ISharedEngineeringIntervalAcceptanceTest` exercises the real facades rather than a fabricated grant object.

Checkpoint:

```text
head: 09556d783955aa7967847b0a7364141390e020a5
CI:   #2772
result: SUCCESS
```

### Mid-combat production persistence

`Stage175ICombatPersistenceAcceptanceTest` round-trips one combined combat-relevant state through production `ContentBoundSaveCodec` envelope v2 and the Stage-H mappers.

It simultaneously preserves:

- partial real ammunition;
- shared stored energy;
- ship/local heat;
- thrust ceiling;
- coolant;
- FTL cooldown field;
- compartment and module damage;
- partial shield reserve/heat/collapse state;
- maintenance age;
- weapon feed identity and launcher cooldowns;
- sensor tracks, received measurements and pending datalink measurements.

The test asserts exact `GameState` equality, content fingerprint equality and deterministic byte-for-byte re-encode. Core `GameStateCodec` remains schema v4; no test-only save format was introduced.

Checkpoint:

```text
head: 6fcc1843680cfc84bf2c9a3dec4aa2df889d73cf
CI:   #2774
result: SUCCESS
```

### Finite-magazine destruction chain

`Stage175IPhysicalDestructionScenario` and `Stage175IFullChainDestructionAcceptanceTest` execute a real destruction chain using ordinary Stage-I content and subsystem APIs.

The attacker uses doctrine A's physical primary magazine. Every 150 kg dart is removed by `AmmunitionRuntime.consumeOne`; no infinite projectile source exists. Each projectile then follows:

```text
physical ProjectileBody
→ fitted finite ShieldFieldRuntime
→ HeavyImpactResolver material stack
→ penetration / compartment energy
→ ShipDamageRuntime local compartment + mount integrity
→ shield-emitter integrity follows actual module damage
→ damage-aware DerivedShipCalculator
```

The scenario continues until each target compartment reaches zero structural integrity **and every mount physically located in that compartment also reaches zero integrity**. The fitted doctrine-A magazine is sufficient without a hidden damage multiplier.

Final acceptance proves:

- finite real magazine use;
- non-zero finite shield absorption;
- armor/material penetration and internal damage;
- complete local compartment/mount destruction;
- production-derived acceleration falls to exactly zero;
- destroyed sensor capability disappears;
- the same final damage snapshot projects as a wreck with deterministic cosmetic debris;
- presentation does not mutate authoritative damage.

Checkpoint:

```text
head: ab8515ababebd669060570d5a078c45d396b35b5
CI:   #2775
result: SUCCESS
```

### Post-combat production persistence

`Stage175IPostCombatPersistenceAcceptanceTest` takes the final destroyed physical state, captures it with `EntityStateMapper`, round-trips production `ContentBoundSaveCodec` v2, restores the ECS entity and projects the restored state.

It proves that save/load does not silently:

- repair compartments;
- restore destroyed modules;
- refill/restart shields;
- replace the physical ship;
- convert the wreck into a pristine tactical entity.

The restored entity remains a wreck under the same presentation projection.

## 7. 17.5I-F — interactive tactical acceptance / closeout

Status: **IMPLEMENTED / GREEN**.

### Thin immutable playback

`Stage175ITacticalAcceptancePlayback` composes exactly three immutable frames:

1. engagement — kinetic projectile, guided missile, interceptor, EW/deception, beam, shield and thrust;
2. penetration — shield/armor/penetration/local damage;
3. wreck — subsystem loss plus deterministic debris.

The authoritative destruction snapshots come from `Stage175IPhysicalDestructionScenario`. The playback does not own a second damage resolver and cannot mutate authoritative combat state.

The engagement frame also consumes one real doctrine-B missile round before creating its `GuidedWeaponBody`; finite interceptor assignment is taken from `Stage175IFleetSaturationHarness` rather than invented by the renderer.

### Interactive desktop gate

`Stage175ITacticalAcceptanceApp` is a dedicated presentation-only desktop viewer exposed through:

```text
java -jar target/star-empires-1.0-SNAPSHOT-all.jar --tactical-acceptance
```

Controls:

```text
SPACE       play / pause
LEFT / P    previous frame
RIGHT / N   next frame
R           reset to first frame and pause
ESC         exit
```

The application owns only immutable playback data, frame index/time and libGDX presentation resources. User input cannot fire a weapon, repair a ship, replenish resources, recharge shields or apply damage.

The OpenGL shell receives the same narrow JaCoCo treatment as existing renderer shells. `Stage175ITacticalAcceptancePlayback`, physical scenario, projection and classification logic remain headless-tested.

### Final pre-closeout exact-head evidence

Exact implementation checkpoint before canonical-document updates:

```text
head: 750604aa0a6216a739a584544dc5e1a439ffb378
CI:   #2789
result: SUCCESS
suite: 868 tests, 0 failures / 0 errors / 0 skipped
coverage: PASS
strict Javadoc: PASS
shaded desktop package: PASS
```

This checkpoint includes:

- shared engineering interval contention;
- mid-combat persistence;
- finite-magazine destruction;
- tactical immutable playback;
- dedicated desktop validation mode;
- post-combat destroyed-state persistence.

## 8. Canonical branch consolidation

Stage 17.5I temporarily diverged into:

- `agent/stage17-5i-combat-test-content-acceptance` — canonical line;
- `agent/stage17-5i-combat-test-acceptance` — earlier experimental aggregate-runner line.

Commit `0f2d8981048e28e8c33fbddae18d885159486eb1` records an **ours-style consolidation merge**. The experimental line remains in Git history, but its incompatible working tree is not imported. Only the canonical content-acceptance branch continues.

I-C was re-authored directly against actual production APIs instead of adding compatibility shims for failed experimental assumptions.

A later oversized playback prototype was also discarded before closeout. The accepted architecture deliberately separates authoritative `Stage175IPhysicalDestructionScenario` from the thin presentation-only `Stage175ITacticalAcceptancePlayback`.

## 9. Canonical assets and regressions

Core Stage-17.5I implementation files include:

- `src/main/resources/data/content/stage17_5i-combat-test-engineering-v1.json`;
- `src/main/resources/data/content/stage17_5i-doctrine-engineering-v1.json`;
- `src/main/resources/data/content/stage17_5i-weapon-ammunition-v1.json`;
- `src/main/resources/data/content/stage17_5i-weapon-launchers-v1.json`;
- `src/main/resources/data/content/stage17_5i-protection-runtime-v1.json`;
- `Stage175ICombatTestContentPack`;
- `Stage175ICombatTestProtectionPack`;
- `Stage175ICombatTestWeaponPack`;
- `Stage175IFleetDoctrineCatalog`;
- `Stage175ICombatAcceptanceHarness`;
- `Stage175ICombatMatrixCatalog`;
- `Stage175IFleetSaturationHarness`;
- `Stage175IPhysicalDestructionScenario`;
- `TacticalPrototypeVisualSnapshot`;
- `Stage175ITacticalVisualProjection`;
- `Stage175ITacticalAcceptancePlayback`;
- `TacticalPrototypeRenderer`;
- `Stage175ITacticalAcceptanceApp`.

Important regression tests include:

- `Stage175ICombatAcceptanceHarnessTest`;
- `Stage175ICombatMatrixCatalogTest`;
- `Stage175IFleetSaturationHarnessTest`;
- `DerivedShipSignatureGeometryTest`;
- `Stage175ISharedEngineeringIntervalAcceptanceTest`;
- `Stage175ICombatPersistenceAcceptanceTest`;
- `Stage175IFullChainDestructionAcceptanceTest`;
- `Stage175ITacticalVisualProjectionTest`;
- `Stage175ITacticalAcceptancePlaybackTest`;
- `Stage175IPostCombatPersistenceAcceptanceTest`.

## 10. Exit-gate conclusion

Stage 17.5I implementation now proves:

1. all six mandatory hull families use production schema;
2. five doctrine fixtures differ through physical content rather than labels;
3. ammunition, mass, power, heat, propulsion, sensors, shields and damage use common runtime semantics;
4. all eleven required doctrine pairs execute deterministically;
5. required non-cost variants and physical saturation are covered;
6. equal-cost comparison is explicitly deferred rather than fabricated before Stage 18;
7. hull signature geometry reaches the production sensor equation;
8. overlapping incremental capabilities share one real interval power/storage budget;
9. a finite physical magazine can drive shield → armor → local damage → subsystem loss → destruction;
10. mid-combat and post-destruction state survive the production persistence boundary;
11. the mandatory tactical visual families project from authoritative state;
12. an interactive desktop validation client can inspect the battle without becoming combat authority;
13. Stage-17.5I content remains provisional rather than silently becoming Stage-22 canon.

The only remaining repository-level work is the manual protected-process equivalent:

```text
final documented exact branch head CI
→ inspect exact diff/head SHA
→ PR exact-head CI
→ merge exact SHA
→ post-merge CI on exact new main
```

After that merge/post-merge gate succeeds, **Stage 17.5 is COMPLETE and Stage 18 becomes NEXT**.
