# M22.8I — Player-facing carrier UI

Status: IMPLEMENTED / exact-head verification required before merge.

## Scope

M22.8I exposes persistent carrier/small-craft state through the existing generated-world military inspector without making the UI authoritative.

The read model derives carrier state from accepted M22.8 authorities:

- persistent `SmallCraftId` engineering state;
- physical hangar occupancy and capacity;
- finite launch/recovery deck queue and active phases;
- M22.8D mission state;
- actor-bounded mission-target visibility.

The UI shows individual craft identity, installed fit, structure, ammunition, reaction mass, maintenance/service/repair state, mission state, deck work and failure diagnostics.

## Command boundary

PLAYER mission submission and queued-launch cancellation are routed through `SmallCraftMissionCommandService`, the same M22.8D validation path used by AI.

Rejected commands return unchanged mission state plus a stable diagnostic family and domain validation detail. Presentation code has no direct mutation path into the craft registry, hangar registry, flight-deck sequencer or mission state.

## Acceptance

Automated coverage proves:

- capture is read-only and does not mutate craft, hangar, deck or mission authority;
- hidden target references are not leaked;
- actor-visible targets remain visible;
- PLAYER mission submission validates through the shared authority and only then creates a physical launch request;
- invalid mission/recovery commands leave physical and mission state unchanged;
- launch cancellation after the physical handoff boundary is rejected;
- the optional carrier source reaches the existing generated-world military inspector;
- campaigns without carrier bindings remain empty and no UI path synthesizes free craft.

M22.8I introduces no virtual fighter pool, UI-owned readiness, player-only mission rules or persistence shortcut.
