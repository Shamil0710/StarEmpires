# ref.industrial_union.station.mining_outpost.v1 — generation attempt 002

**Status:** REJECTED — persistent faction/style drift after production-asset reseed  
**Generation ID:** `8a772f8e-836a-4bd2-858f-ecbc61ed6a06`

Before retrying, the generation context was explicitly reseeded with two real repository production
sprites from Industrial Union:

- `src/main/resources/assets/ships/industrial_union/production/corvette/corvette_base.png`;
- `src/main/resources/assets/ships/industrial_union/production/freight/freight_base.png`.

These references clearly establish the Union production grammar:

- graphite/mill steel;
- olive/slate utility armor;
- light standardized assembly panels;
- restrained ochre/amber service accents;
- muted teal instrumentation;
- repeated modular sections;
- no warm-ivory/burgundy Imperial heraldry.

The request again required five separate Union mining-outpost candidates.

## Actual output

The backend again produced a single five-station board in the Imperial cream/burgundy/brass family,
including explicit heraldic eagle-like panels.

It failed both:

1. faction/style authority;
2. five-independent-file packaging.

## Decision

**HARD REJECT.**

This confirms the current generation backend is persistently retaining the Imperial station family even
after explicit Union production-asset reseeding.

No candidate is scored or selected. The row remains `PLANNED`, `geometry_frozen=false`.

Further Union station generation should be suspended until the image context can be cleanly reset;
repeating the same call would consume batches without producing admissible evidence.
