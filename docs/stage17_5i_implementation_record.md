# Star Empires — Stage 17.5I implementation record

> Status: **ACTIVE — final Stage 17.5 exit gate**  
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

No Stage-17.5I fixture may introduce hidden combat stats, class-name bonuses, free consumables, virtual repair, player-only physics or rendering authority.

## 2. Implementation slices

### 17.5I-A — representative physical content vocabulary

Status: **IMPLEMENTED ON CANONICAL BRANCH**.

The canonical content pack now provides all six mandatory representative hull families through the ordinary `ShipEngineeringCatalogLoader` / `ShipFittingValidator` boundary:

- corvette-scale combat hull;
- frigate-scale general-purpose hull;
- destroyer-scale escort/strike hull;
- cruiser-scale heavy combat hull;
- civilian bulk freighter;
- tanker/logistics hull.

The IDs remain explicitly `test` / `stage17_5i` namespaced and the material is tagged `content_provisional`. Stable semantic fingerprints and materially different physical envelopes are asserted by tests. This content is production-valid test material, not Stage-22 final canon.

### 17.5I-B — equipment, ammunition, fit and doctrine diversity

Status: **IMPLEMENTED ON CANONICAL BRANCH; ACCEPTANCE EXPANSION MAY STILL ADD COVERAGE**.

Five required acceptance doctrines are now represented by physically different ordinary fits:

- Fleet A — kinetic line;
- Fleet B — missile strike;
- Fleet C — high-mobility / beam;
- Fleet D — defensive / EW;
- Fleet E — balanced control.

Differences arise from authored module choice and real mass, power, heat, thrust, reaction mass, sensor/EW, shield/protection, ammunition and launcher state rather than doctrine-name multipliers.

Current acceptance coverage proves that:

- all five doctrine fits pass the ordinary fitting validator with their real loaded consumables;
- doctrine fits produce materially different loaded mass, thrust, delta-v, power/thermal and sensor/shield envelopes;
- physical ammunition is resolved through the ordinary launcher/ammunition catalogs and `AmmunitionRuntime`;
- firing consumes the authoritative physical consumable state;
- doctrine content and protection sidecars retain stable production fingerprints and provisional content status.

### 17.5I-C — deterministic combat matrix harness

Status: **NEXT — implementation must target the actual production APIs**.

The required pair matrix remains:

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

Required variations remain count/mass, spacing, ammunition, pre-damage, thermal state, information state and protected logistics assets.

Two experimental harness implementations were reviewed during branch consolidation. Both attempted to call assumed rather than actual production APIs and failed compilation. They are therefore not part of the canonical working tree. The next harness must be implemented directly against the current contracts, including `AmmunitionRuntime.ConsumptionResult`, `DerivedShipState.accelerationMps2()` and `DerivedShipState.continuousHeatMarginW()` rather than compatibility aliases or guessed method names.

### 17.5I-D — Tactical Prototype Visual Set

Status: **PLANNED AFTER THE HEADLESS MATRIX IS GREEN**.

Presentation-only adapters/assets are still required for top-down ships, kinetic/guided/beam paths, propulsion, shields, impacts, penetration, subsystem damage and wreck/debris state.

Replacing, hiding or changing a visual must never alter authoritative simulation state.

### 17.5I-E — full-chain acceptance

Status: **PLANNED**.

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

### 17.5I-F — closeout

Status: **PLANNED**.

Only after exact-head CI, deterministic regression output and the interactive-readability gate are green may canonical roadmap status change from `17.5I NEXT/ACTIVE` to `Stage 17.5 COMPLETE` and Stage 18 become active.

## 3. Canonical branch consolidation

Stage 17.5I temporarily diverged into two implementation lines from the same green Stage-17.5H main:

- `agent/stage17-5i-combat-test-content-acceptance` — content/doctrine/protection line;
- `agent/stage17-5i-combat-test-acceptance` — an earlier experimental aggregate-runner line.

The experimental line accumulated useful design evidence but its latest deterministic runner failed compilation because it referenced APIs that do not exist in the production contracts. Its last runner attempted stale/assumed surfaces including `WeaponFireControl.KineticSolution`, `AmmunitionRuntime.roundsOnMount(...)` and `ShipCapabilityService.Snapshot.thermalEndurance()`.

The canonical line later reproduced the same category of mistake in a second experimental harness by referencing `AmmunitionRuntime.FireResult`, `DerivedShipState.maximumAccelerationMps2()` and `DerivedShipState.heatMarginW()`.

To prevent duplicated implementation and accidental re-merging, commit `0f2d8981048e28e8c33fbddae18d885159486eb1` records an **ours-style consolidation merge**:

- the experimental branch remains preserved in Git history for reference;
- its incompatible working tree is not imported;
- the two red canonical harness commits are removed from the working tree;
- the resulting tree is exactly the last known green Stage-17.5I content/protection checkpoint `c9892a4b8bde87b6c8ffe0197ffa1007141239dc`.

From this point forward, only `agent/stage17-5i-combat-test-content-acceptance` is the active Stage-17.5I implementation line.

## 4. Current canonical checkpoint

Implemented production-valid provisional content includes:

- `src/main/resources/data/content/stage17_5i-combat-test-engineering-v1.json`;
- `src/main/resources/data/content/stage17_5i-doctrine-engineering-v1.json`;
- `src/main/resources/data/content/stage17_5i-weapon-ammunition-v1.json`;
- `src/main/resources/data/content/stage17_5i-weapon-launchers-v1.json`;
- `src/main/resources/data/content/stage17_5i-protection-runtime-v1.json`;
- `src/main/java/com/spacesim/content/ship/Stage175ICombatTestContentPack.java`;
- `src/main/java/com/spacesim/content/ship/Stage175ICombatTestProtectionPack.java`;
- `src/main/java/com/spacesim/content/weapon/Stage175ICombatTestWeaponPack.java`;
- `src/main/java/com/spacesim/ship/Stage175IFleetDoctrineCatalog.java`.

Current tests prove:

1. all six mandatory hull families load through production schema;
2. representative hull physical envelopes are materially different;
3. baseline and doctrine fits pass the ordinary fitting validator;
4. exactly five required doctrine fixtures exist without numeric role bonuses;
5. doctrine differences emerge from physical module/stores choices;
6. physical ammunition feeds resolve and real rounds are consumed from authoritative consumables;
7. sensor/EW and shield capability differences are derived from installed modules;
8. doctrine protection layouts resolve all authored mounts;
9. semantic fingerprints remain stable;
10. all Stage-17.5I content remains explicitly provisional rather than silently becoming Stage-22 canon.

## 5. Immediate next slice

```text
single canonical green content/protection checkpoint
→ implement deterministic matrix harness against actual production APIs
→ add matrix/variant regression tests and stable result fingerprints
→ exact-head green CI
→ Tactical Prototype Visual Set
→ interactive + headless full-chain acceptance
→ Stage 17.5 closeout
```

The next harness must reuse the existing production subsystems and must not create compatibility shims solely to make acceptance code compile.