# Stage 23E — Escort-Hull Derelict Reference Promotion Audit

**Status:** SELECTED FOR PROMOTION — NOT FROZEN  
**Reference:** `ref.world.special.escort_hull_derelict.v1`  
**Asset:** `world.special.escort_hull_derelict`  
**Source:** `src/main/resources/assets/stage20_5/special/imperial_derelict_v1.png`  
**Git blob SHA:** `cdd9a7ff005f76687157e431cd6640a44307527c`

## Decision

Promote the existing derelict geometry instead of generating five replacement hull concepts.

The current source already provides:

- a recognizable former escort/warship silhouette;
- right-facing production orientation;
- persistent-hull presentation rather than an explosion frame;
- damaged/open internal structure;
- no baked fireball, smoke trail or flying debris;
- enough surviving hull identity for a finite salvage location.

The existing Stage-20.5 catalog also resolves `LocationKind.DERELICT` explicitly to the governed
`DERELICT` visual role. The same resolver deliberately rejects anomaly through this legacy path,
so promotion of the derelict does not imply that other Stage-20H special locations are already
visually authored.

## Required cleanup before FROZEN

The geometry is selected, but the final persistent dead-state master still needs Stage-23E review:

1. suppress/remove any pixels that read as normal active navigation/service emission;
2. ensure engine interiors read dead rather than idling;
3. preserve the current recognizable escort silhouette;
4. do not add active fire, smoke or fresh explosion effects;
5. verify grayscale and 25% / 12.5% readability;
6. verify silhouette-only recognition;
7. confirm that the image does not imply salvage quantity or specific surviving subsystem state;
8. record the cleaned source checksum before setting `geometry_frozen=true`.

If this can be achieved as a layer/emissive cleanup, the base geometry must remain unchanged.
Only reopen five-candidate generation if a concrete geometry defect is found.

## Outcome

- current source: **SELECTED**;
- geometry frozen: **false**;
- new concept generation required now: **0**;
- next action: dead-state cleanup + final reference QA.
