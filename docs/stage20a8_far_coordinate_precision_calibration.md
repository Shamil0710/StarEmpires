# Stage 20A.8 — Far-Coordinate Numerical Precision Calibration

**Status:** ACCEPTED — exact-head Java-17 implementation CI green; final merge gate pending  
**Parent:** Stage 20A — Representative-Ship Scale Calibration  
**Date:** 2026-08-19

## 1. Purpose

Stage 20A.8 closes the numerical-representation requirement of the accepted unbounded-local-space contract before Stage 20 begins authoring physical star-system geometry.

The problem is distinct from gameplay scale:

```text
large local physical coordinate
+ small nearby tactical / docking / formation delta
→ small delta must remain numerically meaningful
```

A coordinate representation is invalid for Stage-20 physical authority if local meter-scale geometry disappears merely because the same object travelled far from the original presentation origin.

## 2. Production inventory and discovered debt

The current pre-Stage-20 ECS spatial seam is `TransformComponent`:

```text
Vector2 position
Vector2 velocity
```

libGDX `Vector2` stores IEEE-754 `float` values. `FlightDynamics` currently integrates that float transform directly.

That remains valid legacy/demo/runtime behavior for the existing bounded presentation worlds, but it cannot become the authoritative representation of the new Stage-20 unbounded SI local space.

Stage 20A.8 therefore does **not** silently reinterpret the old float coordinates as meters at arbitrary distance.

## 3. Accepted Stage-20 physical coordinate seam

A new immutable `LocalPhysicalPosition` stores each axis as:

```text
signed long numerical cell
+ normalized double local offset in meters
```

The numerical cell width is exactly:

```text
2^30 m = 1,073,741,824 m
```

and offsets are normalized to:

```text
-2^29 m <= offset < +2^29 m
```

This cell is a **numerical decomposition only**.

It is not:

- a star-system radius;
- a sector;
- a generated region;
- a jump edge;
- a materialization window;
- an LOD band;
- a collision cell;
- a gameplay boundary.

Crossing a numerical cell boundary changes only representation. Physical displacement remains continuous.

A power-of-two cell size is used so cell-scale multiplication is exactly representable over the local interaction domain and normalization does not introduce a decimal conversion scale.

## 4. Why one global float is rejected

Stage 20A.8 measures `Math.ulp(...)` at deterministic numerical stress magnitudes.

The probes include:

```text
1 Mm
1 Gm
30 Gm
1 Tm
10 Tm
100 Tm
1 Pm
1 Em
```

They are precision probes only, **not proposed system extents**.

The 30 Gm probe is also useful because prior Stage-20 calibration already contains sensor evidence on that order of magnitude, but A.8 does not promote that sensor probe into world size.

At large global magnitudes, float spacing grows until local meter-scale changes cannot be represented. Therefore:

```text
global float position
!= Stage-20 physical authority
```

## 5. Why one global double is also not the final strategy

A global `double` is dramatically better than float and remains sufficient over a large practical range, but its ULP also grows with coordinate magnitude.

Stage 20's contract says local space is conceptually unbounded; it must not acquire a hidden precision wall merely because one global scalar eventually loses centimeter-scale local resolution.

The hierarchical representation keeps the authoritative `double` offset near zero regardless of the signed numerical cell index.

Therefore large absolute travel does not force local interaction arithmetic to operate on a huge floating-point magnitude.

## 6. Precision error budget

Stage 20A.8 introduces the versioned numerical engineering budget:

```text
absolute local numerical error budget = 0.01 m
```

This is **not** a physical spacing rule. It is a precision-quality constraint.

The one-centimeter budget is intentionally much smaller than meter-scale ship, formation, docking and collision geometry while remaining loose enough to avoid pretending that Star Empires is a microscopic simulator.

If later accepted mechanics require sub-centimeter physical resolution, the versioned precision profile must be revisited rather than silently exceeding the budget.

At the worst normalized hierarchical offset (`2^29 m`), the authoritative double-offset ULP remains sub-micrometer and its half-ULP quantization bound remains far below the 1 cm budget.

## 7. Local displacement arithmetic

`LocalPhysicalPosition.displacementTo(...)` computes:

```text
cell delta * exact power-of-two cell size
+ local offset delta
```

through double precision and `Math.fma(...)`.

The method has an explicit operation-level exact-integer guard for cell deltas that exceed the exactly representable integer domain of double. That guard is **not a world edge**: both physical positions remain valid; only that particular direct local-displacement operation is rejected because it would no longer satisfy its precision contract.

Ordinary tactical, sensor, docking, traffic and materialization interactions are many orders of magnitude inside that arithmetic domain.

## 8. Presentation / floating-origin boundary

`LocalPresentationFrame` provides the accepted camera-relative seam:

```text
authoritative LocalPhysicalPosition
- authoritative nearby presentation origin
→ double relative displacement
→ float presentation coordinate
```

The float cast therefore happens only after large common coordinates have been removed.

Changing the frame origin:

```text
camera rebase
materialization-window rebase
renderer floating origin
```

must never change:

- physical cell coordinates;
- physical local offsets;
- velocity;
- damage;
- consumables;
- identity;
- current system;
- pairwise physical distance.

A projection outside finite float range fails explicitly instead of clamping, teleporting or mutating physical state.

## 9. Deterministic rebasing probes

The calibration profile includes two representative rebasing tests:

1. very large positive/negative numerical cell coordinates with a nearby 120 m × -75 m pairwise displacement;
2. a pair whose physical displacement crosses both X and Y numerical cell boundaries.

For both probes:

- pairwise authoritative displacement is calculated from hierarchical coordinates;
- two different nearby presentation origins are applied;
- float presentation pairwise error is measured;
- both errors must remain inside the 1 cm budget;
- the immutable authoritative physical positions must remain byte-for-value equivalent before and after presentation rebasing.

## 10. Machine-readable implementation

Added:

- `LocalPhysicalPosition`;
- `LocalPresentationFrame`;
- `Stage20FarCoordinatePrecisionCalibrationProfile`;
- `Stage20FarCoordinatePrecisionCalibrationCalculator`;
- `Stage20FarCoordinatePrecisionCalibrationProfileTest`.

Current calibration profile version:

```text
stage20a.far-coordinate-precision.v1
```

The profile exposes:

```text
cellSizeM
maximumOffsetMagnitudeM
absoluteErrorBudgetM
maximumHierarchicalOffsetUlpM
maximumHierarchicalHalfUlpErrorM
hierarchicalPhysicalCoordinatesRequired
legacyGlobalFloatPhysicalAuthorityAllowed
cameraRelativeFloatPresentationAllowed
naive global float/double ULP probes
camera-relative rebase errors
remaining legacy migration constraints
```

## 11. Authority boundary

Stage 20A.8 deliberately does not rewrite every existing pre-Stage-20 ECS path in one slice.

Current explicit gap:

```text
TransformComponent / FlightDynamics legacy float integration
```

must not be used as the authoritative far-coordinate storage/execution path for Stage-20B generated physical entities.

Stage-20B physical state must use `LocalPhysicalPosition` or a deterministic equivalent satisfying the same accepted precision profile. Legacy UI/demo systems may receive camera-relative float projections through a presentation adapter.

This preserves existing stages without pretending their bounded float coordinate implementation already satisfies the new world-generation contract.

## 12. Acceptance criteria

Stage 20A.8 is accepted because exact-head implementation CI proved simultaneously:

- calibration output is deterministic;
- hierarchical normalized-offset worst-case quantization remains inside the 1 cm budget;
- global float fails the budget at far-coordinate probes and is therefore not physical authority;
- a naive global double is shown to eventually exceed the same budget rather than being treated as mathematically unbounded;
- crossing a numerical cell boundary preserves the requested physical displacement;
- camera-relative rebasing preserves pairwise physical geometry;
- changing only presentation origin does not mutate authoritative physical state;
- unresolved legacy float runtime migration remains machine-visible rather than hidden.

The full Java-17 `clean verify` gate passed 1212 tests with zero failures and zero errors; Javadoc and JaCoCo checks also passed.

## 13. Next slice

After Stage 20A.8 passes the final merge gate, the next implementation slice is **Stage 20A.9 — materialization / LOD distance-band calibration**.

A.9 must use the now-separated physical coordinate domain and presentation frame to define when objects are:

```text
fully materialized / tactical
reduced local simulation
strategic / aggregate representation
rendered / culled
```

without making any LOD or render boundary a physical world edge and without deleting causal authoritative state.
