# Stage 19J — Tactical Validation Viewer, Scenario Coverage, Readability, and Inspection

**Status:** COMPLETE — ACCEPTED 2026-08-19  
**Parent stage:** Stage 19 — Strategic Warfare / Coercive Diplomacy / Advanced Combat Behavior  
**Stage 20 gate:** OPEN — Stage 19J acceptance criteria passed

## 1. Purpose

Stage 19A–19I established the authoritative strategic/tactical warfare runtime and the scaled 32-ship live validation path. Manual Stage-19I runs then exposed normal long-lived combat states that short automated acceptance runs had not exercised sufficiently. Stage 19 therefore remained open for a final validation-viewer and runtime-hardening slice rather than treating the current live viewer as optional polish.

Stage 19J turns the tactical viewer into a practical manual validation surface for battles of different scales while preserving the exact production combat runtime.

The viewer must make it possible to answer quickly:

- which scenario is running;
- which side and tactical role each visible ship belongs to;
- which ship is selected and what its authoritative state is;
- whether sensors, weapons, ammunition, engineering, damage, formation and targeting are behaving plausibly over long runs;
- at which scale or combat condition a runtime defect first appears.

## 2. Non-negotiable authority invariant

The tactical viewer remains a read-only presentation and control shell over the production runtime.

```text
scenario definition
→ production tactical runtime
→ authoritative fixed ticks
→ immutable/read-only presentation projection
→ viewer rendering / selection / inspection
```

Forbidden:

- viewer-owned movement, targeting, damage, sensor truth, ammunition, power, heat or engineering state;
- viewer-only combat bonuses or simplified large-battle physics;
- fabricated contacts or measurements to keep UI elements alive;
- direct UI mutation of authoritative ship state;
- separate combat implementations for 1v1, 4v4, 8v8 and 16v16.

Scenario choice changes initial conditions and authored loadouts only; all scenarios advance through the same production rules.

## 3. Mandatory interactive scenario set

One unified tactical viewer supports all of the following:

1. **1v1 Legacy Duel** — regression/reference case;
2. **4v4 Balanced** — first shared-runtime scale-up;
3. **8v8 Mixed** — mixed tactical doctrines/roles;
4. **8v8 Damaged / Depleted** — pre-damaged ships and depleted physical resources/ammunition;
5. **16v16 Mixed** — 32 exact-local ships without the maximum saturation loadout;
6. **16v16 Saturation** — 32 ships with concurrent kinetic, strike-missile, interceptor and decoy bodies.

A single scenario catalog/registry is the authoritative presentation-side list. The live session is no longer hard-wired to `createSaturation32()`.

### Launcher contract

Canonical launcher:

```text
run-tactical-sim.bat
```

Accepted behavior:

- no scenario argument: present a simple scenario-selection menu;
- scenario argument: launch that scenario directly;
- invalid scenario: fail clearly and list valid values;
- `R`: recreate the **currently selected scenario**, not an implicit saturation default.

Existing legacy launchers may remain as compatibility aliases, but Stage-17.5 1v1 tooling must not be mistaken for Stage-19J scaled validation.

## 4. Visual readability contract

Stage 19J uses schematic/debug graphics, not final production art. The objective is immediate battle-state readability.

### 4.1 Side colors

Sides are visually distinct everywhere relevant:

- **ALPHA:** cool cyan/blue family;
- **BETA:** warm orange/red family.

Side color influences at minimum:

- ship outline/accent;
- selected-ship highlight;
- labels/markers;
- target or ownership indicators where shown.

Color is not the only semantic cue: ship silhouettes and labels remain readable if color perception is imperfect.

### 4.2 Role-based ship silhouettes

The viewer visually distinguishes the accepted authored tactical roles/doctrines:

- **KINETIC** — narrow/axial gun-oriented silhouette;
- **MISSILE** — broader body with readable launcher-block language;
- **BEAM** — slender high-mobility directed-energy silhouette;
- **DEFENSIVE / EW** — compact body with side defensive/sensor nodes;
- **BALANCED** — neutral general-purpose silhouette.

The classification belongs to presentation/projection code. Core combat rules do not depend on presentation role names.

### 4.3 General readability

Accepted:

- larger, clearer ship marks than the original prototype triangles;
- thicker high-contrast outlines against the dark background;
- readable projectile/ordnance marks with visually distinguishable major categories;
- clear selected-ship ring/highlight;
- selected identity in the HUD and detailed inspection panel;
- optional labels that remain off by default for dense 16v16 readability.

## 5. Mouse selection and ship inspection

### 5.1 Selection

Accepted behavior:

- left click on a visible ship selects it;
- left click on empty tactical space clears selection;
- selection uses screen-space hit testing against the current projected role-aware ship mark;
- overlapping hits resolve deterministically to the nearest marker center with stable entity-ID tie-break;
- selection persists across normal fixed ticks while the physical entity remains represented;
- selection never changes authoritative tactical orders or targets;
- selected ship receives an unmistakable side-colored highlight.

### 5.2 Inspection panel

Selecting a ship opens a persistent right-side inspection panel containing a larger schematic preview plus authoritative/read-only statistics available from the runtime/projection.

Accepted identity fields include:

- entity ID;
- side;
- hull and installed-fit identifiers;
- tactical role/doctrine.

Accepted condition fields include:

- mean integrity and minimum module integrity;
- shield emitter/collapse/reserve state;
- heat/thermal state;
- electrical bus energy;
- wreck state;
- reaction mass / propellant and physical ammunition state.

Accepted kinematic fields include:

- position;
- velocity;
- scalar speed;
- heading/orientation.

Acceleration is displayed as unavailable because no independently authoritative acceleration field currently exists.

Accepted combat/information fields include:

- physical weapon-feed identities;
- remaining ammunition aggregate;
- current authoritative target/fire request/authorization;
- current actor-local track summaries;
- survival action/reason and formation state.

Aggregate ECM/ECCM is displayed as unavailable because the current selected-ship read model does not expose one authoritative aggregate value. Stage 19J deliberately does not infer it from doctrine or visual role.

## 6. HUD and controls

The normal HUD shows scenario identity, authoritative tick, simulation speed, current camera zoom, alive counts and selected identity.

Accepted controls:

- `SPACE` — pause/resume;
- `N` or `RIGHT` — exactly one authoritative tick while paused;
- `R` — deterministic reset of the current scenario;
- `1`, `2`, `4`, `8` — presentation-time fixed-tick batch speed;
- `F1` — HUD/debug visibility toggle;
- `F2` — optional compact ship labels;
- `C` — reset presentation camera framing only;
- mouse wheel over tactical map — cursor-anchored zoom;
- middle mouse button + drag — pan;
- left mouse button — selection/clear selection;
- `ESC` — exit.

Camera zoom/pan changes presentation coordinates only and never physical simulation coordinates.

## 7. Accepted implementation boundaries

### Scenario plumbing

- `TacticalScenarioId`;
- `TacticalScenarioDefinition`;
- `TacticalScenarioCatalog`;
- scenario-aware `ScaledLiveTacticalSimulationSession`;
- unified launcher/parser path.

### Presentation classification

- `ShipVisualRole`;
- `ShipVisualClassifier`;
- `TacticalSidePalette`;
- role/side data carried only through immutable visual projection.

### Selection

- `TacticalShipMarkerMetrics`;
- `ShipHitTestService`;
- `ShipSelectionController`;
- `TacticalSelectionOverlayRenderer`.

### Inspection

- `ShipInspectionProjection`;
- immutable `ShipInspectionSnapshot`;
- `ShipInspectionPanelRenderer`.

### Camera / debug usability

- `TacticalCameraController` over existing immutable `WorldMapLayout`;
- optional read-only ship labels.

The existing read-only tactical and debug projections were extended rather than bypassed with ad-hoc mutation paths.

## 8. Implementation slices

### 19J.1 — Scenario catalog and unified launcher — COMPLETE

- removed saturation-only session construction;
- exposed all six mandatory scenarios;
- implemented scenario-aware deterministic reset;
- show scenario identity in HUD;
- added launcher menu/direct argument path.

### 19J.2 — Side palette and baseline readability — COMPLETE

- centralized ALPHA/BETA palette;
- clearer outlines and ship marks;
- improved ordnance marks;
- alive counts and selected identity in HUD.

### 19J.3 — Role-based schematic ships — COMPLETE

- classified presentation role from authored doctrine/fit data;
- rendered five distinct role silhouettes;
- preserved side-color overlay independently of silhouette.

### 19J.4 — Mouse selection — COMPLETE

- screen-space hit testing;
- click-to-select / click-empty-to-clear;
- selected highlight;
- selection persistence across normal ticks while entity remains present.

Canonical record: `docs/stage19j4_mouse_selection_implementation_record.md`.

### 19J.5 — Inspection panel — COMPLETE

- immutable inspection projection;
- enlarged schematic model preview;
- identity, condition, kinematic, combat and sensor/control fields where authoritatively available;
- graceful unavailable-field handling without invented values.

Canonical record: `docs/stage19j5_ship_inspection_implementation_record.md`.

### 19J.6 — Camera and debug usability — COMPLETE

- cursor-anchored zoom;
- bounded pan;
- camera continuity across resize;
- optional labels/debug usability controls;
- HUD/panel usable at mandatory battle scales.

Canonical record: `docs/stage19j6_camera_usability_implementation_record.md`.

### 19J.7 — Long-run runtime acceptance and closeout — COMPLETE

- ordinary deterministic/CI acceptance remains green;
- every mandatory scenario has a meaningful uninterrupted soak crossing the 120-second sensor freshness horizon;
- 16v16 Saturation completed **600 simulated seconds / 12,000 fixed ticks**;
- the accepted saturation run observed normal track loss and damage states without uncaught runtime exceptions;
- the damaged/depleted 8v8 case exposed real physical depletion;
- a newly discovered sub-calibration residual-impact runtime defect was fixed without weakening the strict no-extrapolation validation contract;
- closeout evidence is recorded in `docs/stage19j7_long_run_acceptance_record.md`.

## 9. Acceptance criteria

Stage 19J acceptance is satisfied:

1. all six mandatory scenarios launch through one interactive tactical-viewer path;
2. reset deterministically recreates the currently selected scenario;
3. ALPHA and BETA are immediately distinguishable by color and non-color cues;
4. five authored tactical ship roles have distinct readable schematic silhouettes;
5. mouse selection and empty-space deselection work reliably;
6. selected ships receive an obvious highlight;
7. an inspection panel shows a model/schematic preview and authoritative ship statistics without mutating combat state;
8. HUD identifies scenario, tick, speed, alive counts, zoom and selection;
9. zoom/pan make both small and 32-ship scenarios inspectable;
10. no viewer-specific combat authority or hidden information shortcut was introduced;
11. existing headless/live deterministic parity and core Stage-19 acceptance remain green;
12. long-run soak reaches damage/contact-loss/depletion states without uncaught runtime exceptions;
13. Java-17 CI/tests are green on the accepted runtime head;
14. implementation and long-run acceptance evidence is recorded.

## 10. Closeout gate

**Stage 19J is COMPLETE.** Stage 19A–19I remain accepted implementation history and Stage 19J supplies the final tactical validation/readability/runtime-hardening evidence required to close Stage 19 as a whole.

Canonical long-run evidence: `docs/stage19j7_long_run_acceptance_record.md`.

The Stage 20 dependency gate is therefore open. Stage 20 may become the next active implementation stage after this final Stage-19J documentation head itself passes ordinary Java-17 CI plus the dedicated Stage-19J long-soak workflow and that exact head is merged into `main`.

This remains distinct from Stage-23 polish. Final production sprites/VFX/UX still belong to later content/polish stages; Stage 19J closes only the schematic readability, inspection and runtime-validation tooling needed to validate the current combat runtime correctly.
