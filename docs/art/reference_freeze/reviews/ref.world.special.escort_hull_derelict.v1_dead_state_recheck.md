# ref.world.special.escort_hull_derelict.v1 — dead-state recheck

**Status:** SELECTED — NOT FROZEN  
**Source:** `src/main/resources/assets/stage20_5/special/imperial_derelict_v1.png`  
**Source Git blob:** `cdd9a7ff005f76687157e431cd6640a44307527c`

The selected geometry was re-opened directly from the repository source.

## What passes

- recognizable former escort/warship silhouette;
- right-facing production orientation;
- persistent wreck/derelict presentation rather than an explosion frame;
- no baked fireball, smoke plume or flying debris;
- exposed/damaged internal structure remains readable;
- main engine interiors are dark enough to read as non-thrusting;
- geometry remains suitable for promotion rather than regeneration.

## Remaining blocker

Small cyan and amber service/navigation-like light pixels remain visible across the otherwise dead hull.
At close review scale they can still read as normal powered service emission.

The Stage-23E dead-state contract explicitly requires suppression of such active-looking emission before
the reference is frozen.

## Decision

Do **not** set `geometry_frozen=true` yet.

The geometry remains selected and should be preserved. The next valid action is a deterministic
emissive/dead-state cleanup that does not redesign the hull. After cleanup, repeat grayscale,
25%/12.5%, silhouette and knowledge-scope QA and record the cleaned source checksum.
