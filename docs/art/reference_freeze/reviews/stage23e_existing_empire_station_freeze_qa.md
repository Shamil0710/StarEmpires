# Stage 23E — Existing Empire Station Reference Freeze QA

**Scope:** `ref.empire.station.industrial_station.v1` and
`ref.empire.station.trade_logistics_hub.v1`  
**Mode:** `PROMOTE_EXISTING`

Canonical geometry remains the exact Stage-20.5 production PNG for each reference:

- industrial: `src/main/resources/assets/stage20_5/stations/imperial_industrial_station_v1.png`
  (Git blob `60c924e58ad2628f034b67a333eff5b18c3cc008`);
- trade/logistics: `src/main/resources/assets/stage20_5/stations/imperial_trade_hub_v1.png`
  (Git blob `79908fe3954eb972381d0809b14245432a08dd34`).

For `PROMOTE_EXISTING`, these already-shipped production PNGs define geometry; they are not redrawn
merely to imitate the output canvas used for newly generated references.

`Stage23EExistingEmpireStationReferenceFreezeTest` verifies:

- exact source canvas and RGBA alpha;
- all four transparent corners;
- safe silhouette span;
- grayscale tonal separation;
- 25% and 12.5% readability;
- normalized industrial-vs-trade silhouette distinction;
- review anchor zones land on authored geometry;
- runtime bindings still resolve to the same PNGs;
- Stage-18 station archetypes retain exact physical-geometry authority.

Committed review artifacts for each reference include:

- alpha-derived `reference_silhouette.svg`;
- `reference_anchor_review.svg`;
- `reference_qa_sheet.svg`.

The review artifacts are presentation-only and never define facilities, capacity, collision, footprint,
population, economy or hidden state.

If exact-head CI passes, the two promoted references can move from `SELECTED` to `FROZEN`.
