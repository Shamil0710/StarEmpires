# Star Empires — Stage 17.5I implementation record

> Status: **ACTIVE — 17.5I-A / I-B / I-C / I-D GREEN; I-E NEXT**  
> Base main: `c81115051755c3af4af899bd4cbb783d5a045a95`  
> Canonical branch: `agent/stage17-5i-combat-test-content-acceptance`  
> Canonical acceptance contract: `docs/stage17_5i_combat_test_content_visual_acceptance.md`

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

## 2. Completed slices

### 17.5I-A — representative physical content vocabulary

Status: **IMPLEMENTED / GREEN**.

The canonical pack supplies the six mandatory hull families through the ordinary `ShipEngineeringCatalogLoader` / `ShipFittingValidator` path:

- corvette-scale combat hull;
- frigate-scale general-purpose hull;
- destroyer-scale escort/strike hull;
- cruiser-scale heavy combat hull;
- civilian bulk freighter;
- tanker/logistics hull.

IDs remain explicitly `test` / `stage17_5i` namespaced and tagged `content_provisional`. Stable semantic fingerprints and materially different physical envelopes are regression-tested. None of this content is automatically Stage-22 canon.

### 17.5I-B — equipment, ammunition, fit and doctrine diversity

Status: **IMPLEMENTED / GREEN; CONTENT REMAINS PROVISIONAL**.

Five required acceptance doctrines are physically distinct ordinary fits:

- Fleet A — kinetic line;
- Fleet B — missile strike;
- Fleet C — high-mobility / beam;
- Fleet D — defensive / EW;
- Fleet E — balanced control.

Differences arise from real module choice, mass, power, heat, thrust, reaction mass, sensor/EW, shield/protection, ammunition and launcher state. There are no doctrine-name performance multipliers.

Acceptance proves that loaded fits validate, real ammunition feeds resolve, firing consumes authoritative consumables, and sensor/shield/propulsion differences derive from physical fitted content.

### 17.5I-C — deterministic combat matrix

Status: **IMPLEMENTED / GREEN**.

#### Representative pair exchange

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

#### Required variants

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

#### Physical multi-body saturation

`Stage175IFleetSaturationHarness` materializes every attacking ship copy with independent physical ammunition state, consumes one real launcher feed, creates one real `GuidedWeaponBody` per firing copy and passes individual threats into `LayeredDefenseScheduler`. Defense stations have explicit positions, finite rounds, finite support channels and thermal availability.

Increasing ship count therefore increases actual bodies, ammunition use and production-derived fleet mass instead of multiplying a post-hoc score. Formation spacing changes actual geometry. Empty stores create no missiles; thermal lockout removes defense assignments without deleting inbound threats.

#### Integration defect found by I-C

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

### 17.5I-D — Tactical Prototype Visual Set

Status: **IMPLEMENTED / GREEN**.

The tactical prototype is deliberately split into a headless pure projection layer and an OpenGL presentation shell.

#### Immutable presentation snapshot

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

No glyph is authoritative simulation state.

#### Authoritative-state projection

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

#### Replaceable renderer

`TacticalPrototypeRenderer` is a libGDX `ShapeRenderer` consumer of `TacticalPrototypeVisualSnapshot` only. It draws temporary top-down silhouettes, thrust plumes, projectile/guided trails, decoys, beams, shield arcs, impacts, local damage and wreck/debris markers.

The renderer holds no simulation engine, combat service, entity mutation or persistence reference. It may be replaced by Stage-23 sprites/VFX without changing combat semantics.

The OpenGL shell is excluded from headless JaCoCo in the same narrow way as existing `WorldMapRenderer` / `LocalMinimapRenderer`; the immutable snapshot and projection/classification logic remain covered by headless tests.

`Stage175ITacticalVisualProjectionTest` proves required visual-family coverage, deterministic wreck/debris projection, rejected-beam behavior, collapsed-shield projection, immutable snapshots and non-mutation of authoritative projectile/guided/damage objects.

Exact I-D checkpoint:

```text
head: ae8e7d641473331506807f83a201e1d34ab1804b
CI:   #2766
result: SUCCESS
```

## 3. Active slice — 17.5I-E full-chain acceptance

Status: **NEXT / ACTIVE**.

At least one interactive and one headless scenario must collectively exercise:

```text
detection
→ tracks / EW / ECCM
→ fire control
→ kinetic / beam / guided weapon use
→ launcher / magazine / power / thermal consumption
→ interception / point defense / decoys
→ shields / armor / material response
→ compartment / subsystem damage
→ changed capability
→ disablement / destruction
→ persistent post-combat state
```

I-E must also close the Stage-17.5H same-interval engineering contention risk:

```text
sensor + beam + shield recharge overlap one engineering interval
→ one shared continuous-power budget
→ no duplicated reactor margin
→ physical ENERGY_STORAGE draw only for the residual demand
→ deterministic local heat admission
```

The interactive scene will wire the already-green I-D snapshot/renderer to authoritative I-E scenario state. It must not create a second simulation path.

## 4. Planned closeout — 17.5I-F

Only after exact-head CI, deterministic regression output and the interactive-readability gate are green may canonical roadmap status change to `Stage 17.5 COMPLETE` and Stage 18 become active.

## 5. Canonical branch consolidation

Stage 17.5I temporarily diverged into:

- `agent/stage17-5i-combat-test-content-acceptance` — canonical line;
- `agent/stage17-5i-combat-test-acceptance` — earlier experimental aggregate-runner line.

Commit `0f2d8981048e28e8c33fbddae18d885159486eb1` records an **ours-style consolidation merge**. The experimental line remains in Git history, but its incompatible working tree is not imported. Only the canonical content-acceptance branch continues.

I-C was then re-authored directly against actual production APIs instead of adding compatibility shims for failed experimental assumptions.

## 6. Current canonical assets and regressions

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
- `TacticalPrototypeVisualSnapshot`;
- `Stage175ITacticalVisualProjection`;
- `TacticalPrototypeRenderer`.

Important regression tests include:

- `Stage175ICombatAcceptanceHarnessTest`;
- `Stage175ICombatMatrixCatalogTest`;
- `Stage175IFleetSaturationHarnessTest`;
- `DerivedShipSignatureGeometryTest`;
- `Stage175ITacticalVisualProjectionTest`.

Current evidence proves:

1. all six mandatory hull families use production schema;
2. five doctrine fixtures differ through physical content rather than labels;
3. ammunition, mass, power, heat, propulsion, sensors, shields and damage use common runtime semantics;
4. all eleven required doctrine pairs execute deterministically;
5. required non-cost variants and physical saturation are covered;
6. hull signature geometry reaches the production sensor equation;
7. the mandatory tactical visual families project from authoritative state;
8. rendering remains presentation-only and replaceable;
9. Stage-17.5I content remains provisional rather than silently becoming Stage-22 canon.

## 7. Immediate sequence

```text
17.5I-A GREEN
→ 17.5I-B GREEN
→ 17.5I-C GREEN
→ 17.5I-D GREEN
→ 17.5I-E full-chain interactive + headless acceptance NEXT
→ 17.5I-F exact-head closeout
→ Stage 17.5 COMPLETE
→ Stage 18
```
