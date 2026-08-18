# Stage 19J — Tactical Validation Viewer, Scenario Coverage, Readability, and Inspection

**Status:** ACTIVE — REQUIRED FOR STAGE 19 EXIT  
**Parent stage:** Stage 19 — Strategic Warfare / Coercive Diplomacy / Advanced Combat Behavior  
**Stage 20 gate:** BLOCKED until Stage 19J acceptance criteria pass

## 1. Purpose

Stage 19A–19I established the authoritative strategic/tactical warfare runtime and the scaled 32-ship live validation path. Manual Stage-19I runs then exposed normal long-lived combat states that short automated acceptance runs had not exercised sufficiently. Stage 19 therefore remains open for a final validation-viewer and runtime-hardening slice rather than treating the current live viewer as optional polish.

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

One unified tactical viewer must be able to launch all of the following:

1. **1v1 Legacy Duel** — regression/reference case;
2. **4v4 Balanced** — first shared-runtime scale-up;
3. **8v8 Mixed** — mixed tactical doctrines/roles;
4. **8v8 Damaged / Depleted** — pre-damaged ships and depleted physical resources/ammunition;
5. **16v16 Mixed** — 32 exact-local ships without the maximum saturation loadout;
6. **16v16 Saturation** — 32 ships with concurrent kinetic, strike-missile, interceptor and decoy bodies.

A single scenario catalog/registry must be the authoritative presentation-side list. The live session must no longer be hard-wired to `createSaturation32()`.

### Launcher contract

Preferred canonical launcher:

```text
run-tactical-sim.bat
```

Required behavior:

- no scenario argument: present a simple scenario-selection menu;
- scenario argument: launch that scenario directly;
- invalid scenario: fail clearly and list valid values;
- `R`: recreate the **currently selected scenario**, not an implicit saturation default.

Existing legacy launchers may remain as compatibility aliases, but must delegate clearly enough that Stage-17.5 1v1 tooling cannot be mistaken for Stage-19J scaled validation.

## 4. Visual readability contract

Stage 19J uses schematic/debug graphics, not final production art. The objective is immediate battle-state readability.

### 4.1 Side colors

Sides must be visually distinct everywhere relevant:

- **ALPHA:** cool cyan/blue family;
- **BETA:** warm orange/red family.

Side color must influence at minimum:

- ship outline/accent;
- selected-ship highlight;
- labels/markers;
- target or ownership indicators where shown.

Color must not be the only semantic cue: ship silhouettes and labels remain readable if color perception is imperfect.

### 4.2 Role-based ship silhouettes

At minimum the viewer must visually distinguish these tactical roles/doctrines:

- **KINETIC** — narrow/axial gun-oriented silhouette;
- **MISSILE** — broader body with readable launcher-block language;
- **DEFENSIVE / INTERCEPTOR** — compact body with side defensive nodes;
- **EW / SENSOR** — antenna/sensor-oriented protrusions or markers;
- **BALANCED** — neutral general-purpose silhouette;
- **COMMAND / SUPPORT** — optional when the scenario exposes such a role.

The classification belongs to presentation/projection code. Core combat rules must not depend on presentation role names.

### 4.3 General readability

Required:

- larger, clearer ship marks than the current prototype triangles where practical;
- thicker high-contrast outlines against the dark background;
- readable projectile/ordnance marks with visually distinguishable major categories where available;
- clear selected-ship bracket/ring;
- clear current-target indication for the selected ship when authoritative target information exists;
- HUD text that does not overlap the battle area unnecessarily.

## 5. Mouse selection and ship inspection

### 5.1 Selection

- left click on a visible ship selects it;
- left click on empty tactical space clears selection;
- selection uses screen-space hit testing against the current projected ship mark;
- selection never changes authoritative tactical orders or targets;
- selected ship receives an unmistakable visual highlight.

Optional keyboard cycling (`TAB` or equivalent) may supplement mouse selection but cannot replace it.

### 5.2 Inspection panel

Selecting a ship opens a persistent inspection panel containing a larger schematic preview plus authoritative/read-only statistics available from the runtime/projection.

Required identity fields:

- entity ID;
- side;
- hull/class/model identifier where available;
- tactical role/doctrine.

Required condition fields where the runtime exposes them:

- mean integrity / hull condition;
- shield state;
- heat / thermal state;
- electrical/bus energy or power state;
- alive / disabled / destroyed state;
- reaction mass / propellant state when applicable.

Required kinematic fields:

- position;
- velocity;
- acceleration;
- heading/orientation where available.

Required combat fields where available:

- primary weapon;
- secondary weapon;
- remaining ammunition / feed state;
- current authoritative target/track.

Required information/control fields where available:

- sensor/contact/track state;
- current objective/order/formation;
- ECM/ECCM/deception state.

If a datum is not represented authoritatively, the panel must display it as unavailable/omitted rather than inventing a value.

## 6. HUD and controls

The normal HUD must show at least:

```text
SCENARIO: <name>
TICK/TIME: <authoritative values> | SPEED: X1/X2/X4/X8
ALPHA ALIVE: <n> | BETA ALIVE: <n>
SELECTED: <entity/role/side or NONE>
```

Mandatory controls:

- `SPACE` — pause/resume;
- `N` or `RIGHT` — exactly one authoritative tick while paused;
- `R` — deterministic reset of the current scenario;
- `1`, `2`, `4`, `8` — presentation-time fixed-tick batch speed;
- `F1` — HUD/debug visibility toggle as currently supported;
- `ESC` — exit;
- left mouse button — selection/clear selection.

Stage 19J should also provide camera **zoom** and **pan** sufficient to inspect both small and 32-ship battles without changing physical simulation coordinates. Optional label/debug toggles may be added once the core selection/inspection path is stable.

## 7. Proposed implementation boundaries

Names may change during implementation, but responsibility separation must remain explicit.

### Scenario plumbing

- `TacticalScenarioId`;
- `TacticalScenarioDefinition`;
- `TacticalScenarioCatalog`;
- scenario-aware `ScaledLiveTacticalSimulationSession` (or successor);
- one unified launcher/parser path.

### Presentation classification

- `ShipVisualRole`;
- `ShipVisualProfile`;
- `ShipVisualClassifier`;
- `SideColorPalette` / equivalent centralized side-style policy.

### Selection

- `ShipHitTestService`;
- `ShipSelectionController` / selected entity state.

### Inspection

- `ShipInspectionProjection`;
- immutable `ShipInspectionSnapshot`;
- inspection-panel renderer.

The existing read-only tactical and debug projections should be extended rather than bypassed with ad-hoc direct mutation paths.

## 8. Implementation slices

### 19J.1 — Scenario catalog and unified launcher

- remove saturation-only session construction;
- expose all six mandatory scenarios;
- implement scenario-aware deterministic reset;
- show scenario identity in HUD;
- add launcher menu/direct argument path.

### 19J.2 — Side palette and baseline readability

- centralized ALPHA/BETA palette;
- clearer outlines and ship marks;
- improved ordnance/target marks;
- alive counts and selected identity in HUD.

### 19J.3 — Role-based schematic ships

- classify presentation role from authored fit/doctrine/capability data;
- render at least five distinct role silhouettes;
- preserve side-color overlay independently of silhouette.

### 19J.4 — Mouse selection

- screen-space hit testing;
- click-to-select / click-empty-to-clear;
- selected highlight;
- selection persistence across normal ticks while entity remains present.

### 19J.5 — Inspection panel

- immutable inspection projection;
- enlarged schematic model preview;
- identity, condition, kinematic, combat, sensor/control fields;
- graceful handling of destroyed/disappeared entities and unavailable fields.

### 19J.6 — Camera and debug usability

- zoom;
- pan;
- optional labels/target lines/debug-overlay controls;
- ensure HUD/panel remain usable at all mandatory battle scales.

### 19J.7 — Long-run runtime acceptance and closeout

- deterministic replay/reset/parity checks remain green;
- run every mandatory scenario through a meaningful uninterrupted soak;
- include at least one **10 simulated minute** 16v16 saturation run (or a stricter deterministic duration accepted in the implementation record);
- normal combat states such as destroyed sensors, stale/lost contacts, depleted ammunition, destroyed/disabled ships and damaged engineering must not produce uncaught exceptions;
- record any newly discovered runtime defects and regression tests before Stage 19 closes.

## 9. Acceptance criteria

Stage 19J is complete only when all of the following are true:

1. all six mandatory scenarios launch through one interactive tactical-viewer path;
2. reset deterministically recreates the currently selected scenario;
3. ALPHA and BETA are immediately distinguishable by color and non-color cues;
4. at least five tactical ship roles have distinct readable schematic silhouettes;
5. mouse selection and empty-space deselection work reliably;
6. selected ships receive an obvious highlight;
7. an inspection panel shows a model/schematic preview and authoritative ship statistics without mutating combat state;
8. HUD identifies scenario, time/tick, speed, alive counts and selection;
9. zoom/pan make both small and 32-ship scenarios inspectable;
10. no viewer-specific combat authority or hidden information shortcut is introduced;
11. existing headless/live deterministic parity and core Stage-19 acceptance remain green;
12. long-run manual/automated soak reaches normal damage/contact-loss/depletion states without uncaught runtime exceptions;
13. Java-17 CI/tests are green on the exact PR head;
14. implementation/acceptance evidence is recorded before Stage 19 is marked COMPLETE again.

## 10. Stage gate

Stage 19A–19I remain accepted implementation history, but **Stage 19 as a whole is reopened and ACTIVE until Stage 19J closes**.

Stage 20 work that depends on the final tactical validation baseline is blocked by this gate. Stage 20 planning/contracts may remain in the repository, but Stage 20 must not be declared the active implementation stage until Stage 19J acceptance is complete.

This is not Stage-23 polish. Final production sprites/VFX/UX still belong to later content/polish stages; Stage 19J requires only the schematic readability and inspection tooling needed to validate the current combat runtime correctly.