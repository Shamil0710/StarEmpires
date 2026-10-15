# ref.industrial_union.station.mining_outpost.v1 — generation attempt 001

**Status:** REJECTED — faction/style drift  
**Reference ID:** `ref.industrial_union.station.mining_outpost.v1`  
**Expected mode:** `GENERATE_5_SELECT_1`  
**Generation ID:** `a2ab3446-7063-47da-ab82-9ff38c900bd5`

## Requested family

Industrial Union mining outpost:

- standardized extractor/service blocks;
- repeated ore-handling modules;
- explicit material-flow path;
- modular storage;
- standardized docking interface;
- survey/sensor block;
- visible stowed extractor + handling arm;
- replaceable service panels;
- graphite/mill-steel/slate-olive/light assembly grey with restrained ochre/amber and muted teal;
- no Imperial citadel/heraldry language.

## Actual output

The backend reproduced the same cream/burgundy/brass Imperial station family used by the immediately
preceding Empire batch.

The result includes Imperial-style protected cores, burgundy striping and heraldic/institutional
construction language rather than the Union's standardized repeated-module industrial grammar.

## Decision

**HARD REJECT.**

No candidate number or score is assigned. The board is not repurposed as an Empire reference because
its provenance belongs to the Union mining-outpost request.

The manifest remains:

- `PLANNED`;
- `geometry_frozen=false`;
- no source/master path.

## Next action

Do not spend repeated Union generation batches while the backend remains locked to the Imperial station
family. Continue independent Empire reference work and retry Union only after a successful faction-style
reset/reseed.
