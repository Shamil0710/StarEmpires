# ref.empire.station.volatile_depot.v1 — candidate batch 001

**Status:** SELECTED + CLEAN MASTER PREPARED — FREEZE PENDING EXACT-HEAD CI  
**Asset ID:** `empire.station.volatile_depot`  
**Mode:** `GENERATE_5_SELECT_1`  
**Source generation ID:** `958326e2-a119-4402-916c-129f18f7aadc`

## Source/output quality

The backend again packaged five candidates on one board, but this time the board itself has genuine
transparent alpha and the object class/faction family is usable for the requested Empire station batch.

Candidates were isolated only for review. The selected candidate master is extracted from the source
alpha rather than background-matted from an opaque presentation board.

## Candidate review

| Candidate | Hard reject | Engineering /25 | Depot role /20 | Imperial identity /20 | Silhouette /15 | Derivative readiness /10 | Distinctive /10 | Total |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 01 | YES — generic ring infrastructure | 21 | 8 | 18 | 14 | 6 | 7 | **74** |
| 02 | YES — generic fortified/ship-like station | 22 | 8 | 19 | 13 | 6 | 7 | **75** |
| 03 | NO | 22 | 13 | 19 | 14 | 8 | 8 | **84** |
| 04 | NO | 24 | 20 | 19 | 14 | 10 | 8 | **95** |
| 05 | NO | 21 | 12 | 17 | 13 | 7 | 7 | **77** |

### Candidate 04 — selected

Candidate 04 is immediately legible as a volatile/water storage and transfer facility:

- multiple pressure/tank modules;
- tank groups split across the structure rather than one undifferentiated reservoir;
- protected central control/service spine;
- structural isolation and service-frame separation between tank groups;
- obvious manifold/service truss routes;
- left/right docking or transfer interfaces;
- a visible stowed handling/transfer-boom root;
- practical safety/engineering geometry;
- durable Imperial long-service construction language.

It does not encode stored quantity or fullness.

## Clean-master extraction

The source board already contains transparent object alpha.

The canonical master was produced by:

1. selecting candidate-04 board cell;
2. selecting the largest connected candidate component to exclude neighboring-board overhang;
3. preserving original RGBA object pixels;
4. uniformly scaling the unchanged candidate geometry;
5. centering on a transparent 1024x1024 canvas.

No station module was synthesized or redrawn.

Master:

`art_sources/stage23e/references/ref.empire.station.volatile_depot.v1/selected/reference_master.png`

SHA-256:

`846c3cb4f27cc6c115b3959d6d18ee81b59222559c415a120ca8de98ae5e7090`

Visible bounds at alpha > 16:

- x 92..931;
- y 229..793;
- 840 x 565 px.

## Reference anchors

Review-only zones:

- protected central service/control core;
- representative upper/lower tank groups on both sides;
- transfer-manifold/service-truss zone;
- left and right docking/transfer interfaces;
- stowed transfer-boom root;
- protected engineering spine.

These anchors do not define capacity, inventory, pressure, stored resource amount, transfer rate or
station collision geometry.

## Gate

`Stage23EEmpireVolatileDepotReferenceFreezeTest` pins the master checksum, alpha/canvas, visible
occupancy, downscale/grayscale readability, review-anchor support and continued Stage-18 exact physical
station-scale authority.

Set `geometry_frozen=true` only after exact-head CI succeeds.
