# ref.world.special.escort_hull_derelict.v1 — cleanup attempt 001

**Status:** REJECTED — edit-target drift  
**Source geometry:** `src/main/resources/assets/stage20_5/special/imperial_derelict_v1.png`  
**Source Git blob:** `cdd9a7ff005f76687157e431cd6640a44307527c`  
**Generation ID:** `8e849311-ebbb-48cd-b571-6984c5ffd51a`

Requested operation:

- preserve the exact derelict hull geometry;
- suppress normal navigation/service emissive pixels;
- make engine interiors read dead rather than idling;
- do not add fire, smoke, explosion or debris;
- do not redesign armor, damage openings or silhouette.

Actual backend output:

- unrelated five-station presentation sheet;
- no geometry-preserving derelict edit.

Decision: **HARD REJECT**.

The returned image has zero reference authority and is not repurposed for any station reference.
The derelict remains `SELECTED`, `geometry_frozen=false`.
