# Star Empires — Stage 17.5I implementation record

> Status: **ACTIVE — final Stage 17.5 exit gate**  
> Base main: `c81115051755c3af4af899bd4cbb783d5a045a95`  
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

Status: **IN PROGRESS**.

Required minimum hull families:

- corvette-scale combat hull;
- frigate-scale general-purpose hull;
- destroyer-scale escort/strike hull;
- cruiser-scale heavy combat hull;
- civilian bulk freighter;
- tanker/logistics hull.

First checkpoint uses `Stage175ICombatTestContentPack` and the ordinary `ShipEngineeringCatalogLoader` / `ShipFittingValidator`. The IDs are explicitly `test` / `stage17_5i` namespaced and the material is tagged `content_provisional`.

This first checkpoint intentionally proves schema/identity/physical-envelope acceptance before weapon/doctrine breadth is added. A baseline reactor-only fit is not a combat acceptance fleet and cannot satisfy I-B/I-C by itself.

### 17.5I-B — equipment, ammunition, fit and doctrine diversity

Planned immediately after I-A is green.

Must add enough ordinary production definitions to create:

- Fleet A — kinetic line;
- Fleet B — missile strike;
- Fleet C — high-mobility / beam;
- Fleet D — defensive / EW;
- Fleet E — balanced control.

Differences must arise from real mass, volume, power, stored energy, heat, reaction mass, sensor/EW, shield/protection, ammunition and launcher choices.

### 17.5I-C — deterministic combat matrix harness

Must execute at least:

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

with deterministic scenario fingerprints and variations for count/mass, spacing, ammunition, pre-damage, thermal state, information state and protected logistics assets.

### 17.5I-D — Tactical Prototype Visual Set

Presentation-only adapters/assets for top-down ships, kinetic/guided/beam paths, propulsion, shields, impacts, penetration, subsystem damage and wreck/debris state.

Replacing or hiding a visual cannot alter authoritative simulation state.

### 17.5I-E — full-chain acceptance

At least one interactive and one headless scenario must collectively exercise detection → tracks/EW → fire control → weapon use → engineering/consumable expenditure → defense → shields/protection → compartment/subsystem damage → changed capability → destruction/disablement → persistent post-combat state.

### 17.5I-F — closeout

Only after exact-head CI, deterministic regression output and the interactive-readability gate are green may canonical roadmap status change from `17.5I NEXT/ACTIVE` to `Stage 17.5 COMPLETE` and Stage 18 become active.

## 3. Current checkpoint

Branch:

`agent/stage17-5i-combat-test-content-acceptance`

Initial files:

- `src/main/resources/data/content/stage17_5i-combat-test-engineering-v1.json`;
- `src/main/java/com/spacesim/content/ship/Stage175ICombatTestContentPack.java`;
- `src/test/java/com/spacesim/content/ship/Stage175ICombatTestContentPackTest.java`.

Acceptance assertions already required at this checkpoint:

1. all six mandatory hull families load through the production schema;
2. stable semantic fingerprint exists;
3. representative hull physical envelopes are materially different;
4. every baseline fit passes the ordinary fitting validator;
5. provisional namespace/status is explicit rather than silently becoming Stage-22 canon.
