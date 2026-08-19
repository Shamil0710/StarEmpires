# Stage 19J.6 — Camera and Debug Usability Implementation Record

**Parent:** `docs/stage19j_tactical_validation_viewer.md`  
**Scope:** presentation-only camera navigation and optional tactical labels

## Implemented controls

- **mouse wheel over tactical map** — zoom in/out around the cursor using the existing immutable `WorldMapLayout.zoomByScroll(...)` rules;
- **middle mouse button + drag** — pan the current tactical view;
- **C** — reset only the presentation camera to centered full-world framing;
- **F2** — toggle compact `entityId / role` ship labels;
- existing selection, pause, fixed-tick step, speed, reset and inspection controls remain unchanged.

The HUD now displays the current camera zoom multiplier.

## Camera continuity

`TacticalCameraController` owns only the current immutable `WorldMapLayout`. It delegates zoom limits, cursor anchoring, world-edge clamping and screen/world transforms to the pre-existing map layout implementation.

Window resize preserves current camera center and zoom through `WorldMapLayout.resize(...)` rather than reconstructing a centered 1x layout. The reserved inspection-panel column remains excluded from the tactical map rectangle.

Scenario reset (`R`) does not implicitly alter presentation camera framing. Camera reset is an explicit independent `C` action.

## Input-space contract

libGDX pointer coordinates use top-left Y while `WorldMapLayout` uses bottom-left screen coordinates. Scroll, pan and left-click selection therefore share the same conversion before interacting with the map.

Scroll events outside the tactical-map rectangle are ignored, so the inspection column never changes camera zoom.

Pan begins only when the middle mouse button is pressed inside the tactical map. Once active, pointer delta is converted through `WorldMapLayout.panByScreen(...)`, which clamps the view to physical world bounds.

## Labels

Ship labels are intentionally optional and disabled by default for 16v16 readability. When enabled with `F2`, each currently visible ship receives a compact stable entity ID plus presentation role, colored with the same ALPHA/BETA palette used by hull outlines.

Labels remain a read-only visualization and reveal no information that the validation viewer does not already project as physical combatants.

## Authority boundary

```text
mouse/keyboard presentation input
→ TacticalCameraController
→ immutable WorldMapLayout
→ renderer / selection / labels
```

No camera or label operation:

- advances simulation time;
- issues orders;
- changes authoritative target or tracks;
- changes position/velocity of any combat entity;
- modifies damage, shields, ammunition, power, heat or engineering state.

## Regression coverage

`TacticalCameraControllerTest` verifies:

1. wheel zoom delegates to existing bounded zoom behavior;
2. panning changes the camera center only after zoom provides movable world margins;
3. window resize preserves current center/zoom;
4. explicit camera reset returns to centered full-world framing.

Existing `WorldMapLayoutTest` continues to own the lower-level coordinate, cursor-anchor, zoom-limit and world-clamp behavior.

## Stage gate

19J.6 is complete only after Java-17 tests, coverage, Javadoc and packaging pass on the exact pull-request head and that exact head is merged into `main`.
