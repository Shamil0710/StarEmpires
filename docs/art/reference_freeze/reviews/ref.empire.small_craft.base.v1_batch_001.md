# ref.empire.small_craft.base.v1 — candidate batch 001

**Status:** SELECTED — REPOSITORY MASTER PERSISTENCE PENDING  
**Asset ID:** `empire.small_craft.base`  
**Physical authority:** 28 x 12 x 6 m, 180 t  
**Production canvas:** 512x256 RGBA  
**Reference mode:** `GENERATE_5_SELECT_1`

## Source candidates

| Candidate | Generation ID | Result |
| --- | --- | --- |
| 01 | `d86df650-f5dd-4f48-a2da-beb12e6b8a56` | valid |
| 02 | `41d6ce43-de5b-4359-b460-b8da0d2c1486` | valid |
| 03 | `6335f255-e31b-4512-8683-c2104336035e` | **selected** |
| 04 | `08cd0efc-8ce5-4879-9104-fae8ac0f74a6` | valid |
| 05 | `eddf5a14-18b3-421e-bf8f-f431865cb8ca` | hard reject |

All candidates were generated as separate one-object top-down transparent images.

## Candidate review

| Candidate | Hard reject | Engineering /25 | Base-role /20 | Faction /20 | Silhouette /15 | Fit-derivation /10 | Distinctive /10 | Total |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 01 | NO | 22 | 16 | 18 | 13 | 8 | 8 | 85 |
| 02 | NO | 22 | 17 | 18 | 14 | 9 | 8 | 88 |
| 03 | NO | 23 | 18 | 19 | 14 | 9 | 8 | **91** |
| 04 | NO | 21 | 16 | 17 | 13 | 8 | 8 | 83 |
| 05 | YES | — | — | — | — | — | — | — |

Candidate 05 is rejected because its large rear lateral plates read as wing/fins and undermine the
"compact carrier craft base hull, no atmospheric-aircraft styling" constraint.

## Selected candidate 03

Reasons:

- compact wedge/arrowhead silhouette reads as carrier small craft rather than a miniature capital ship;
- three-engine rear cluster is clear and serviceable;
- protected forward sensor/nose block;
- neutral side equipment regions can accept interceptor/defence/strike overlays;
- no mandatory shield hardware;
- no baked fit-specific launcher/weapon identity;
- Imperial ivory/gunmetal/burgundy/brass language remains readable without depending only on color.

## Normalization / QA

Candidate 03 was alpha-trimmed and normalized without redrawing onto the canonical 512x256 canvas.

Measured selected draft:

- visible bounds: 480 x 223 px inside 512x256;
- physical L/W target: 28/12 = 2.333;
- selected visual L/W: 2.152;
- relative aspect error: 7.8%;
- all four canvas corners fully transparent;
- visible occupancy approximately 52.3%.

Local review views were produced for:

- grayscale;
- 25% scale (128x64);
- 12.5% scale (64x32);
- silhouette-only;
- anchor/attachment review.

The 12.5% view remains identifiable as a compact arrowhead craft and the silhouette remains distinct.

## Presentation anchors reviewed

Review-only visual points:

- three rear engine locations;
- forward sensor/nose region;
- upper and lower generic fit zones;
- central service/core region.

These review points do **not** create gameplay hardpoints. Authoritative fitted modules and any future
runtime attachment coordinates remain content/engineering authority.

## Freeze decision

Status remains `SELECTED`, `geometry_frozen=false`.

The visual geometry itself passes the local reference QA, but the canonical normalized
`reference_master.png` must be persisted in the repository reference package before the row becomes
`FROZEN`. Do not derive the six fit overlays from an unpersisted/ephemeral master.
