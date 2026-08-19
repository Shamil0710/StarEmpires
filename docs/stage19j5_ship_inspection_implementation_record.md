# Stage 19J.5 — Selected Ship Inspection Implementation Record

**Parent:** `docs/stage19j_tactical_validation_viewer.md`  
**Scope:** persistent read-only selected-ship inspection panel for the Stage-19J tactical viewer

## Implemented surface

The tactical viewer now reserves a stable right-side inspection column so the tactical map does not change width when selection changes.

When no ship is selected, the column presents a neutral selection hint. When a ship is selected, the panel shows:

- enlarged role-based schematic preview;
- stable entity ID and battle side;
- presentation role and authored doctrine ID;
- authoritative hull and installed fit IDs;
- wreck state and mean/minimum integrity diagnostics;
- current shield emitter count, collapsed emitter count, total current reserve and minimum emitter integrity;
- shared electrical bus energy and current ship/local heat;
- physical reaction mass and total ammunition item count;
- current position, velocity, speed and heading;
- actor-selected authoritative target and fire requested/authorized state;
- actor-local track count plus current track information when available;
- physical ammunition-feed identities;
- survival action/reason and formation diagnostics.

## Explicit unavailable values

Stage 19J does not fabricate inspection fields.

`TransformComponent` currently exposes position and velocity but no independently authoritative acceleration state, therefore the inspection projection reports acceleration as `N/A`.

The current selected-combatant debug/read model does not expose a single authoritative aggregate ECM/ECCM inspection state suitable for this panel, therefore that field is also reported as `N/A` rather than inferred from doctrine or visual role.

Likewise, shield reserve is displayed as authoritative current joules and emitter state. The panel does not invent a percentage unless a matching authoritative capacity projection is available.

## Read-only data path

```text
production LiveTacticalBattleDeceptionRuntime
+ immutable TacticalPrototypeVisualSnapshot
+ immutable ScaledTacticalDebugSnapshot
→ ShipInspectionProjection
→ immutable ShipInspectionSnapshot
→ ShipInspectionPanelRenderer
```

`ScaledLiveTacticalSimulationSession.inspectionSnapshot(entityId)` performs only read projections. Regression coverage compares the whole-runtime deterministic fingerprint before and after inspection to prove that inspection does not mutate combat state.

## Interaction boundary

- left-click selection remains the sole way the panel changes selected identity;
- clicking inside the inspection column does not clear or alter tactical selection;
- clicking empty **tactical map** space still clears selection;
- reset clears selection and rebuilds the currently chosen canonical scenario;
- the panel cannot issue orders, retarget weapons or alter tracks, engineering, shields, ammunition, heat or movement.

## Layout

The right inspection column is always reserved. Its width is responsive but bounded for debug usability. `WorldMapLayout` receives only the remaining tactical-map width, so ships, hit testing and selection overlays all use the same reduced map rectangle.

The panel is rendered last so long debug-HUD strings cannot visually overwrite inspection data.

## Regression coverage

`ShipInspectionProjectionTest` verifies:

1. identity/hull/fit data matches the materialized selected combatant;
2. inspection preserves the complete runtime fingerprint;
3. unsupported acceleration and ECM/ECCM fields remain explicitly unavailable;
4. invalid/stale entity IDs return no inspection card;
5. velocity/speed values follow authoritative state after fixed ticks.

## Stage gate

19J.5 is complete only after Java-17 tests, coverage, Javadoc and packaging succeed on the exact pull-request head and that exact head is merged into `main`.
