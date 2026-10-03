# ref.empire.station.refinery_complex.v1 — candidate batch 001

**Status:** SELECTED FOR CLEAN-MASTER PERSISTENCE — NOT FROZEN  
**Reference ID:** `ref.empire.station.refinery_complex.v1`  
**Generation ID:** `8bfb7e78-9299-411f-83d0-c797a893cce7`

The backend returned one five-object Empire station board rather than five independent files. Because
the requested object class and faction family are correct, the candidates were reviewed individually
after deterministic board-cell isolation. The source board itself is not canonical.

| Candidate | Eligibility | Engineering /25 | Refinery role /20 | Empire /20 | Silhouette /15 | Derivative /10 | Distinctive /10 | Total |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 01 | eligible | 21 | 12 | 18 | 14 | 7 | 7 | 79 |
| 02 | **SELECTED** | 24 | 19 | 19 | 14 | 9 | 9 | **94** |
| 03 | eligible | 22 | 16 | 19 | 13 | 9 | 8 | 87 |
| 04 | role floor fail — reads primarily as storage/depot | 22 | 10 | 18 | 13 | 8 | 8 | 79 |
| 05 | eligible, but drifts toward precision/high-tech hub | 22 | 15 | 18 | 14 | 8 | 8 | 85 |

## Candidate 02 — selected

Candidate 02 is the strongest refinery identity because it combines:

- multiple repeated enclosed process towers rather than one ceremonial core;
- visible protected inter-module trunks;
- side pressure/process vessels that read as intermediate processing/storage rather than the entire
  purpose of the station;
- an identifiable central service/control region;
- clear input/output-side module separation;
- adequate docking/service interfaces;
- dense but maintained Imperial long-service engineering language.

It remains visibly different from the selected volatile depot, whose primary identity is repeated
large storage tanks and containment/isolation framing.

## Freeze disposition

A deterministic transparent 1024x1024 working master was prepared locally from the selected board
cell without geometric redraw, but it is **not accepted as repository authority until the binary is
persisted in Git**.

Therefore:

- manifest advances to `SELECTED`;
- `source_asset_path` remains empty;
- `geometry_frozen=false`;
- no Stage-23E derivative animation/component package may treat this geometry as frozen yet.

Required next gate: persist the exact selected master in
`art_sources/stage23e/references/ref.empire.station.refinery_complex.v1/selected/`, then add
grayscale, 25%, 12.5%, silhouette and anchor QA.
