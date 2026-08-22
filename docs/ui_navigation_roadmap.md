# Star Empires — UI, Camera and Navigation Roadmap

> Cross-cutting implementation plan for player-facing navigation, camera control, maps and screen readability.
>
> Added: **2026-08-14** after the first Stage-12 playable test build.
>
> This plan complements `docs/development_roadmap.md`. It does not replace functional stage DoD: presentation must expose the real simulation rather than inventing parallel state.

---

## 1. Goals

The playable client must remain readable when the simulation becomes busier. A player should be able to answer, without guessing:

- where the active ship is;
- what important objects are nearby;
- where stations, gates, fleets and objectives are located in the current system;
- how to reach another system;
- what is happening economically and militarily at the strategic level;
- what is selected, targeted, docked, damaged, hostile, friendly or actionable;
- what changed and why.

The UI is therefore treated as an information architecture problem, not only a visual-polish task.

---

## 2. Camera and view control

### UX-A1 — Camera zoom

**Priority: near-term playable usability; implement before final UI polish.**

Required:

- mouse-wheel zoom in/out;
- bounded minimum and maximum zoom;
- stable camera centering/follow on the active ship;
- zoom must not change simulation coordinates or physics;
- readable transition between close tactical view and wider situational view;
- optional smooth interpolation must remain presentation-only;
- HUD remains screen-space and must not scale with world zoom;
- save/load does not need to persist zoom initially, but the camera must recover safely after load, jump and active-ship changes.

Later extensions:

- camera pan / temporary detach from active ship;
- “return to active ship” hotkey;
- configurable zoom sensitivity;
- tactical zoom presets.

### UX-A2 — Readable world-space presentation

At every supported zoom level:

- ships and stations must preserve recognizable silhouettes;
- important small objects may receive presentation-only markers when their sprites become too small;
- selection/target/docking/range indicators must remain readable;
- labels should use distance/importance-based visibility rules rather than showing everything at once;
- VFX must not conceal gameplay-critical state.

---

## 3. Sector / current-system minimap

### UX-B — Sector minimap

**Priority: before the game grows into multiple simultaneous fleets and dense combat.**

The normal flight HUD must contain a minimap of the current star system / local sector.

Minimum contents:

- player ship;
- owned ships;
- nearby known NPC ships/fleets;
- stations;
- jump points / gates / system exits;
- selected target;
- current objective or navigation destination;
- hostile/friendly/neutral distinction;
- viewport/camera footprint where useful.

Required behavior:

- minimap coordinates derive from authoritative world positions;
- no independent “fake” minimap simulation;
- clipping/normalization for objects outside displayed range;
- configurable or context-sensitive range;
- click interaction may be added later, but the first version may be read-only;
- filters should prevent information overload when object counts grow.

Later extensions:

- click to select object;
- click/drag to pan camera;
- navigation waypoint placement;
- danger/combat indicators;
- resource, trade and mission overlays where appropriate.

---

## 4. Global galaxy map

### UX-C — Global map

**Priority: build progressively alongside inter-system player navigation, fleets and strategic gameplay; do not postpone the first useful version until Stage 22.**

The global map must represent the same `Galaxy -> Sector -> StarSystem` topology already used by simulation and route planning.

Minimum first version:

- discovered systems;
- current system;
- known jump links;
- selected destination;
- route preview using the real galactic route planner;
- ownership/faction information where known;
- basic system inspection panel;
- undiscovered information hidden according to player discoveries.

Progressive strategic layers:

- player and owned fleet locations;
- station ownership;
- faction territory;
- diplomatic state;
- trade-route and logistics visualization;
- market shortages/surpluses;
- construction/expansion projects;
- conflict, blockade and danger/risk overlays;
- missions/objectives;
- search and filtering by system, station, faction, resource and fleet.

Interaction goals:

- select system;
- inspect known system data;
- build route;
- issue movement/orders when the corresponding Stage-15+ command systems exist;
- jump/travel commands must delegate to the same authoritative Stage-10 travel APIs rather than teleporting entities.

---

## 5. Serious UI/readability pass

### UX-D — Information hierarchy

The current test HUD is a development harness and is not the target interface.

Future production UI must establish clear hierarchy between:

1. **immediate ship state** — hull, shield, speed, cargo, selected target, weapon state;
2. **contextual interaction** — dock, trade, mine, attack, jump, interact;
3. **navigation** — minimap, destination, distance, route/status;
4. **economy** — credits, cargo, market prices, availability, profit/loss cues;
5. **world events** — combat warnings, arrivals, destruction, mission/faction/economic events;
6. **strategic management** — fleets, stations, construction, faction, territory and logistics.

### UX-D1 — Readability rules

- never rely on raw debug IDs as primary player-facing labels;
- consistent iconography and terminology;
- clear selection state;
- clear friend/neutral/hostile state;
- critical warnings must be visually distinct from routine information;
- avoid permanent walls of text over the playfield;
- use panels/tooltips/context views for detail;
- important numbers should explain their unit and meaning;
- screen-space text must remain readable at supported resolutions/UI scales;
- support UI scaling independently from world zoom;
- avoid visual clutter from labels, engine glow, projectiles and other VFX;
- animations should communicate state changes, not delay input or obscure data.

### UX-D2 — Feedback / event readability

The player should receive understandable feedback for actions and simulation consequences:

- docking rejected because of distance;
- purchase rejected because of price, stock, capacity, access or credits;
- jump unavailable and why;
- target out of range / weapon on cooldown;
- shield/hull damage source;
- ship/fleet destroyed;
- cargo/credits changed after a transaction;
- route became invalid;
- construction/mission/faction event changed state.

Whenever possible, the UI should expose the reason returned by the authoritative simulation service instead of re-implementing the rule in presentation code.

---

## 6. Milestone mapping

### During / immediately after Stage 13 — Combat Vertical Slice

Add only the UI needed to make combat meaningfully testable:

- camera zoom foundation;
- clear target selection/highlight;
- hull/shield/weapon cooldown display;
- hostile/friendly readability;
- readable hit/damage/destruction feedback;
- first lightweight minimap if combat scale makes off-screen awareness necessary.

The combat pipeline remains the priority; UI must reveal it, not replace it.

### Stage 14 — First Complete Player Economic Loop

Promote the test harness toward a real playable HUD:

- production-oriented basic HUD layout;
- camera zoom and follow behavior considered required usability;
- current-system minimap considered required;
- readable docking/trade/mining interaction panels;
- navigation destination and route status;
- notifications/event feedback sufficient for an hour-long playtest;
- initial UI-scale/resolution validation.

This is the first point where the game should be judged not only as mechanically playable, but also as understandable without reading developer text.

### Stage 15–18 — Fleet & Empire Sandbox

Develop the global map into a strategic command surface:

- fleet locations and orders;
- station/territory overlays;
- trade flows and logistics;
- shortages and construction projects;
- diplomacy/war/blockade/risk layers;
- filtering, search and decluttering;
- interaction with fleet/order systems through shared simulation APIs.

### Stage 19–21 — Exploration / RPG / Content Alpha

Extend both maps with discovery and world-content information:

- unknown vs discovered systems;
- anomalies/derelicts/special locations;
- missions/objectives;
- NPC/faction-relevant markers where appropriate;
- performance and decluttering tests under realistic content density.

### Stage 22 — Final UX / Polish

Stage 22 is **not** the first time maps and usable UI appear. It is the final consolidation pass:

- visual consistency across all screens;
- complete input rebinding and control discoverability;
- onboarding/tutorialization;
- accessibility and UI scaling;
- notification history and filtering;
- final map layers/search/filter polish;
- large-list performance;
- resolution/aspect-ratio testing;
- release-quality save/load/error UX;
- final readability/VFX/bloom tuning.

---

## 7. Acceptance criteria

Before calling the player-facing UX mature enough for alpha:

1. The player can zoom smoothly from tactical to useful situational scale without affecting simulation behavior.
2. The player can understand their current system from the minimap without searching blindly off-screen.
3. The player can open a global map, inspect discovered systems and understand/review an inter-system route.
4. Global-map route commands use the real topology/planner/travel pipeline.
5. The player can visually distinguish player-owned, friendly, neutral and hostile relevant entities.
6. Important combat/economic/navigation failures expose a human-readable reason.
7. Core actions do not require reading raw debug IDs or developer-only text.
8. UI remains readable at representative resolutions and at several world zoom levels.
9. Dense simulation scenes remain understandable through filtering/decluttering rather than hiding simulation state.
10. Strategic overlays remain views over authoritative world state; presentation never becomes a second source of truth.

---

## 8. Design constraint

The project should not wait until Stage 22 to become readable. Camera control, minimap, global navigation and basic information hierarchy are functional usability requirements and will be introduced progressively as soon as the corresponding gameplay systems need them. Stage 22 remains the final polish/hardening phase, not the first UI implementation phase.

## 9. Stage-21 generated-world implementation checkpoint

Implemented in the first production-facing generated-world command interface:

- bounded resolution-aware UI scale and regenerated Latin/Cyrillic TTF fonts;
- separate current-system, galaxy, faction and logistics tabs;
- mouse selection for every rendered local object, global system, faction row and freight row;
- scrollable structured inspector projections over existing runtime authority;
- real Stage-20.5 sprite bindings for supported ships/stations/resources/derelicts;
- generated-system activation without fleet teleportation;
- atomic runtime save/load without generator replay;
- a dedicated Windows launcher.

Still open for later Stage-21/23 slices:

- camera pan/zoom within the generated local-system command map;
- player-order context actions and route building from the new surface;
- search, filters and high-density list virtualization;
- full accessibility/rebinding/onboarding validation;
- mission/NPC/reputation overlays once their authoritative Stage-21 state exists.

Detailed contract: `docs/generated_world_command_ui.md`.
