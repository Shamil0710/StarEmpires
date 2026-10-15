# ref.industrial_union.small_craft.base.v1 — candidate batch 001

**Status:** SELECTED — REPOSITORY MASTER PERSISTENCE PENDING  
**Asset ID:** `industrial_union.small_craft.base`  
**Physical authority:** 29 x 14 x 6.5 m, 200 t  
**Production canvas:** 512x256 RGBA  
**Reference mode:** `GENERATE_5_SELECT_1`

## Source candidates

| Candidate | Generation ID | Result |
| --- | --- | --- |
| 01 | `e305e355-383d-4161-a640-18cd19c1da65` | valid |
| 02 | `3726dd22-54c2-4ae4-a352-5544132a197a` | valid |
| 03 | `c6fae47d-e5b2-46d4-b7ff-fa5896bbdd03` | hard reject |
| 04 | `92f9d865-c4a3-403e-ac9d-5c919b71e60b` | valid |
| 05 | `e49fcd1d-890e-4185-8e9b-fcbbcb3d6510` | **selected** |

All five were generated as separate one-object top-down transparent images after explicitly seeding the
generation context with the accepted Industrial Union production corvette/carrier construction
language.

## Deterministic review normalization

Each candidate was alpha-trimmed and uniformly fitted to a 512x256 transparent review canvas without
redrawing geometry.

Authoritative physical L/W = 29/14 = **2.071**.

| Candidate | Normalized visible bounds | Visual L/W | Relative aspect error |
| --- | ---: | ---: | ---: |
| 01 | 422x193 | 2.187 | 5.6% |
| 02 | 428x186 | 2.301 | 11.1% |
| 03 | 451x224 | 2.013 | 2.8% |
| 04 | 393x216 | 1.819 | 12.2% |
| 05 | 428x220 | 1.945 | **6.1%** |

Candidate 03 has the closest raw physical aspect, but aspect compliance does not waive the hard
silhouette/design rules.

## Candidate review

| Candidate | Hard reject | Engineering /25 | Base-role /20 | Faction /20 | Silhouette /15 | Fit-derivation /10 | Distinctive /10 | Total |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 01 | NO | 23 | 18 | 19 | 14 | 9 | 8 | 91 |
| 02 | NO | 22 | 17 | 18 | 13 | 8 | 8 | 86 |
| 03 | YES | — | — | — | — | — | — | — |
| 04 | NO | 23 | 19 | 19 | 14 | 9 | 8 | 92 |
| 05 | NO | 24 | 19 | 20 | 14 | 9 | 8 | **94** |

### Candidate 01

Strong repeated-module language and a useful three-engine family treatment. Slightly more
corvette-like/military in its forward massing than desired for a neutral carrier base hull.

### Candidate 02

Clean two-engine standardized layout and good physical aspect. The long narrow center spine leaves less
room for visually clear fit cassettes and reads less compact than the preferred Union carrier craft.

### Candidate 03

**Hard rejection.**

The long upper/lower lateral equipment booms create wing-like fragile protrusions and weaken the
required compact standardized body. They also make later fit overlays more likely to be confused with
the base silhouette.

The candidate is rejected despite its excellent 2.8% physical-aspect match.

### Candidate 04

Very strong compact modular construction with clear panel cassettes and two common engines. The body is
noticeably broader/shorter than authoritative physical L/W, and the center reads slightly more like a
small transport/shuttle than candidate 05.

### Candidate 05 — selected

Strengths:

- compact standardized Union construction without an Imperial central citadel;
- two clearly related common propulsion modules;
- replaceable-looking forward mission/crew block;
- repeated rectangular/trapezoidal equipment cassettes;
- several clean zones for interceptor/defence/strike hardware overlays;
- no baked shield identity;
- no permanent heavy strike weapon;
- no decorative wing/fins;
- strong faction readability through construction grammar, not only hue;
- 6.1% visual/physical L/W error after deterministic review normalization.

Score: **94/100**.

## Selected-reference QA

Candidate 05 was normalized without redrawing onto a 512x256 transparent canvas.

Measured selected draft:

- visible bounds: **428 x 220 px**;
- physical L/W: **2.071**;
- selected visual L/W: **1.945**;
- relative aspect error: **6.1%**;
- all four corners transparent;
- visible occupancy: approximately **38.1%**.

Downscale review:

- 25% (128x64): visible span approximately **112 x 51 px**;
- 12.5% (64x32): visible span approximately **59 x 26 px**;
- silhouette remains identifiable at both review scales;
- grayscale retains broad structural separation between engine modules, central service cassettes and
  forward mission block.

## Freeze decision

Status becomes `SELECTED`, `geometry_frozen=false`.

The selected source lineage and geometry are accepted, but the canonical normalized
`reference_master.png` must be persisted in the repository reference package before the row becomes
`FROZEN`.

Do not author interceptor/defence/strike overlays from an ephemeral session copy. Once the master is
persisted, rerun grayscale/downscale/silhouette/anchor QA against that exact repository blob and only
then derive the three Union fits.
