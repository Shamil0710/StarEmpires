# Star Empires — Stage 17.5I implementation record

> Status: **ACTIVE — 17.5I-A / I-B / I-C GREEN; I-D NEXT**  
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

## 2. Implementation slices

### 17.5I-A — representative physical content vocabulary

Status: **IMPLEMENTED / GREEN**.

The canonical content pack provides all six mandatory representative hull families through the ordinary `ShipEngineeringCatalogLoader` / `ShipFittingValidator` boundary:

- corvette-scale combat hull;
- frigate-scale general-purpose hull;
- destroyer-scale escort/strike hull;
- cruiser-scale heavy combat hull;
- civilian bulk freighter;
- tanker/logistics hull.

The IDs remain explicitly `test` / `stage17_5i` namespaced and the material is tagged `content_provisional`. Stable semantic fingerprints and materially different physical envelopes are asserted by tests. This content is production-valid test material, not Stage-22 final canon.

### 17.5I-B — equipment, ammunition, fit and doctrine diversity

Status: **IMPLEMENTED / GREEN; CONTENT REMAINS PROVISIONAL**.

Five required acceptance doctrines are represented by physically different ordinary fits:

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

Status: **IMPLEMENTED / GREEN**.

The deterministic acceptance layer now has three complementary pieces rather than one synthetic battle score.

#### Representative pair exchange

`Stage175ICombatAcceptanceHarness` executes the exact required pair matrix:

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

It runs fixed inspectable geometry through production Stage-17.5 seams for:

- derived ship capability;
- active sensor observation;
- EW / track fusion;
- kinetic fire control and physical projectile materialization;
- guided ammunition consumption, body materialization and guidance;
- beam delivery;
- layered defense assignment;
- shields;
- heavy-impact / compartment damage;
- post-damage capability derivation.

The result contains physical measurements plus a stable SHA-256 fingerprint. It deliberately contains no winner score, hit probability or doctrine multiplier.

#### Required scenario variants

`Stage175ICombatMatrixCatalog` provides canonical deterministic cases for:

- equal fleet count;
- approximately equal fitted fleet mass using `DerivedShipCalculator.totalMassKg()` rather than a hidden mass tier;
- small compact formation;
- large dispersed formation;
- partial ammunition;
- pre-damage;
- thermal stress;
- degraded information;
- protected logistics.

A scalar equal-cost case is explicitly reported as:

`DEFERRED_UNTIL_STAGE18_COMPARABLE_COST_BASIS`

This is intentional. Stage 17.5 currently has heterogeneous construction component quantities, not a complete comparable industrial/resource/facility cost basis. Inventing a conversion rate here would create fake balance economics and would pre-empt Stage 18. The deferral is represented and regression-tested rather than silently omitted.

#### Physical multi-body saturation

`Stage175IFleetSaturationHarness` closes the count/formation ambiguity that a representative one-ship exchange cannot prove by itself.

For every attacking ship copy it creates:

```text
independent physical consumable state
→ one real launcher feed
→ one AmmunitionRuntime consumption
→ one individual GuidedWeaponBody
→ one individual LayeredDefenseScheduler.Threat
```

The interceptor screen is also explicit finite scenario state:

- physical station identities;
- support-channel count;
- physical interceptor-round count;
- thermal availability;
- real station geometry.

Increasing the attacker count therefore increases actual bodies, consumed rounds and production-derived fleet mass rather than multiplying a post-hoc combat score. Compact/dispersed spacing changes the actual threat/station coordinates and deterministic fingerprint. Empty ammunition produces no missile bodies; thermal lockout removes assignments without deleting inbound threats.

The interceptor screen is a generic layered-defense acceptance fixture. Its explicit finite rounds are not claimed to be part of the current provisional doctrine-D ship store; final doctrine composition is Stage-22 content work.

### Stage-17.5I integration defect found by I-C

The I-C end-to-end path exposed a real missing connection that earlier subsystem tests did not catch: hull `baseSignatureGeometryAreaM2` existed in the Stage-17.5A hull schema, but `DerivedShipCalculator` did not seed the runtime scalar `radar_cross_section_m2` channel from it. Tests that injected `SignatureState` directly therefore passed while a genuinely derived hull had zero active-radar RCS.

The production derivation boundary now seeds:

```text
hull.baseSignatureGeometryAreaM2
→ DerivedShipState.signatureContributions[radar_cross_section_m2]
→ ShipSensorEngineeringAdapter
→ SignatureState.radarCrossSectionM2
→ active radar equation
```

Module signature contributions are then accumulated normally. This remains the frozen v1.0 scalar midpoint model; Stage 20 may replace the seed with aspect/frequency-aware projected geometry without changing the shared signature budget contract.

`DerivedShipSignatureGeometryTest` prevents regression and proves that active radar can acquire a real Stage-17.5I derived hull without manually injected target RCS.

### 17.5I-D — Tactical Prototype Visual Set

Status: **NEXT**.

Presentation-only tactical adapters/assets are required for at least:

- top-down ships;
- kinetic projectile paths;
- guided missile/interceptor paths;
- beam paths;
- propulsion/thruster state;
- shield state;
- impact / penetration indication;
- subsystem/compartment damage indication;
- disablement / wreck / debris state.

The visual layer must consume authoritative simulation state and events. Replacing, hiding or changing a visual must never alter combat state.

### 17.5I-E — full-chain acceptance

Status: **PLANNED AFTER I-D**.

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

Stage I-E must also include the Stage-17.5H shared-engineering contention stress case so overlapping sensor / beam / shield-recharge work cannot consume the same continuous reactor margin more than once in one deterministic interval.

### 17.5I-F — closeout

Status: **PLANNED**.

Only after exact-head CI, deterministic regression output and the interactive-readability gate are green may canonical roadmap status change from `17.5I NEXT/ACTIVE` to `Stage 17.5 COMPLETE` and Stage 18 become active.

## 3. Canonical branch consolidation

Stage 17.5I temporarily diverged into two implementation lines from the same green Stage-17.5H main:

- `agent/stage17-5i-combat-test-content-acceptance` — canonical content/doctrine/protection line;
- `agent/stage17-5i-combat-test-acceptance` — earlier experimental aggregate-runner line.

Both early experimental aggregate runners attempted assumed rather than actual production API names and failed compilation. Commit `0f2d8981048e28e8c33fbddae18d885159486eb1` records an **ours-style consolidation merge**:

- the experimental branch remains preserved in Git history for reference;
- its incompatible working tree is not imported;
- the last known green content/protection tree remains authoritative;
- only `agent/stage17-5i-combat-test-content-acceptance` continues as the Stage-17.5I implementation line.

I-C was then re-authored directly against the real production APIs rather than adding compatibility shims.

## 4. Current canonical checkpoint

Production-valid provisional content/runtime acceptance files now include:

- `src/main/resources/data/content/stage17_5i-combat-test-engineering-v1.json`;
- `src/main/resources/data/content/stage17_5i-doctrine-engineering-v1.json`;
- `src/main/resources/data/content/stage17_5i-weapon-ammunition-v1.json`;
- `src/main/resources/data/content/stage17_5i-weapon-launchers-v1.json`;
- `src/main/resources/data/content/stage17_5i-protection-runtime-v1.json`;
- `src/main/java/com/spacesim/content/ship/Stage175ICombatTestContentPack.java`;
- `src/main/java/com/spacesim/content/ship/Stage175ICombatTestProtectionPack.java`;
- `src/main/java/com/spacesim/content/weapon/Stage175ICombatTestWeaponPack.java`;
- `src/main/java/com/spacesim/ship/Stage175IFleetDoctrineCatalog.java`;
- `src/main/java/com/spacesim/ship/Stage175ICombatAcceptanceHarness.java`;
- `src/main/java/com/spacesim/ship/Stage175ICombatMatrixCatalog.java`;
- `src/main/java/com/spacesim/ship/Stage175IFleetSaturationHarness.java`.

Important I-C regression tests include:

- `Stage175ICombatAcceptanceHarnessTest`;
- `Stage175ICombatMatrixCatalogTest`;
- `Stage175IFleetSaturationHarnessTest`;
- `DerivedShipSignatureGeometryTest`.

Current tests prove:

1. all six mandatory hull families load through production schema;
2. representative hull physical envelopes are materially different;
3. baseline and doctrine fits pass the ordinary fitting validator;
4. exactly five required doctrine fixtures exist without numeric role bonuses;
5. doctrine differences emerge from physical module/stores choices;
6. physical ammunition feeds resolve and real rounds are consumed from authoritative consumables;
7. sensor/EW and shield capability differences are derived from installed modules;
8. hull signature geometry reaches the production sensor equation;
9. all eleven required doctrine pairings execute deterministically;
10. required non-cost scenario variants are represented and deterministic;
11. equal-mass counts are derived from actual fitted mass;
12. multi-ship saturation creates one physical missile body per firing ship copy;
13. finite defense rounds/channels and thermal availability physically bound assignments;
14. formation spacing changes actual body/station geometry;
15. semantic result fingerprints remain stable;
16. all Stage-17.5I content remains explicitly provisional rather than silently becoming Stage-22 canon.

Latest completed implementation CI checkpoint before this documentation update:

```text
head: 0df350bc16fc18269c90d793c050a036830f4689
CI:   #2759
result: SUCCESS
```

## 5. Immediate next slice

```text
17.5I-A GREEN
→ 17.5I-B GREEN
→ 17.5I-C GREEN
→ 17.5I-D Tactical Prototype Visual Set NEXT
→ 17.5I-E interactive + headless full-chain acceptance
→ 17.5I-F exact-head closeout
→ Stage 17.5 COMPLETE
→ Stage 18
```

The visual prototype must remain a consumer of authoritative simulation state/events. No renderer, sprite, trail or effect may become a source of combat truth.
