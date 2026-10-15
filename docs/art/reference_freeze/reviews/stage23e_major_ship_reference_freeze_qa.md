# Stage 23E — Major Ship Reference Freeze QA

**Scope:** 18 promoted production base masters  
**Factions:** Empire + Industrial Union  
**Decision:** freeze base geometry when the exact branch head passes the Stage-23E automated gate  
**Runtime authority:** unchanged; engineering/content state remains authoritative

## 1. Source integrity

The selected reference rows continue to point at the exact production paths and Git blob SHAs recorded
by the prior promotion audit. This batch does not modify any of the 18 PNG masters.

Empire masters are the accepted M22.3 production family bases. Industrial Union masters are the
accepted M22.4 production bases. No new major-ship base generation is justified.

## 2. New Stage-23E freeze gate

`Stage23EMajorShipReferenceFreezeTest` makes the remaining visual-reference gate executable.

For every promoted base it verifies:

- 768x512 RGBA production canvas;
- transparent corners and safe padding;
- 25% (192x128) downscale silhouette span and visible-cell floor;
- 12.5% (96x64) downscale silhouette span and visible-cell floor;
- grayscale/luminance range and quantized tonal separation at both review scales;
- unique 12.5% silhouette signatures within each faction;
- pairwise silhouette overlap below the near-duplicate threshold;
- engineering-derived hardpoint/service/engine review anchors project onto or near visible hull
  geometry after mapping the authoritative physical envelope to the sprite's visible bounds.

The test does not infer gameplay geometry from pixels. Anchor coordinates originate from accepted
engineering definitions (or the existing Imperial engineering-derived visual catalog) and are used only
to check that painted geometry can support those presentation locations.

## 3. Authority boundary

Freezing these references means only that the **base visual geometry** is stable enough to author
aligned Stage-23E layers and markers.

It does not freeze or redefine:

- hull dimensions;
- collision/selection geometry;
- hardpoint capability;
- installed modules;
- weapon arcs;
- ship balance;
- save identity.

For Empire, existing damage/emissive/engine layers remain subject to the separate Stage-23E production
integration audit.

For Industrial Union, damage/emissive/engine/wreck layers are still Stage-23E production work; this
freeze covers only the accepted base masters.

## 4. Freeze disposition

When the exact branch head passes the test and required CI:

- all 9 Empire major-ship base references become `FROZEN`;
- all 9 Industrial Union major-ship base references become `FROZEN`;
- `geometry_frozen=true` is authoritative only for those base-reference rows;
- later production work must derive from these bases unless a concrete release defect is documented
  and the reference is deliberately reopened.

The manifest update and this review are part of the same acceptance batch so CI validates the exact
documented state.
