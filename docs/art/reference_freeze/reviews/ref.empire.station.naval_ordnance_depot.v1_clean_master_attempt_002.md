# ref.empire.station.naval_ordnance_depot.v1 — clean-master attempt 002

**Status:** REJECTED — candidate-count / packaging violation  
**Generation ID:** `25ef5eb1-94f1-41d3-b6a8-82a6837cbf8a`

The existing naval-ordnance reference remains selected from its original reviewed five-preview batch,
but lacks a clean canonical master.

A replacement clean-master discovery batch was requested with:

- heavily protected citadel/service core;
- separated armored magazine modules;
- safe handling corridors;
- reinforced military loading berths;
- security/sensor structures;
- stowed loading arm;
- large magazine doors/shutters;
- clear isolation zones;
- no firing state and no visiting ships.

## Actual output

The backend returned **eight** station concepts in one sheet rather than the contractually required five
candidate files.

Several designs contain plausible military/depot language, but accepting one would silently waive the
finite `GENERATE_5_SELECT_1` review contract and make candidate numbering/provenance ambiguous.

## Decision

**HARD REJECT FOR PACKAGING.**

The output does not supersede the existing selected design and is not assigned a candidate number.

The reference remains:

- `SELECTED`;
- `geometry_frozen=false`;
- original reviewed candidate remains preferred;
- clean 1024x1024 canonical master still required.
