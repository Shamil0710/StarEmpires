# Stage 19I-D — degraded/lost ordnance track continuity

Status: implementation/acceptance slice for the Stage 19I actor-bounded information gate.

## Problem closed by this slice

`LiveTacticalOrdnanceObservationRuntime` already derived velocity only from successive observer-local Cartesian radar solutions. However, before this slice an aged track retained its previous `velocityKnown` state even after ordinary `TrackQualityPolicy` downgraded the hypothesis below `TRACKED`. A later single fresh measurement could therefore inherit a pre-loss velocity baseline.

That behavior was not authoritative-target omniscience, but it granted unjustified continuity of actor knowledge across a real information-quality break.

## Rule

Velocity continuity is now valid only while the ordinary production `TrackState` remains:

- position-known; and
- `TRACKED` or `FIRE_CONTROL`.

When aging downgrades the hypothesis below that boundary:

1. actor-local velocity becomes unknown and returns to canonical zero values;
2. the observer-local Cartesian sample used for velocity differencing is discarded;
3. one later fresh position solution may reacquire the track but cannot restore velocity;
4. a second temporally distinct post-loss Cartesian solution is required to establish velocity again.

No target authoritative velocity is ever read.

## Test calibration seam

Production constructors continue to use `TrackQualityPolicy.defaultPolicy()`. A package-private constructor accepts an explicit policy only so acceptance can shorten age thresholds and exercise loss/reacquisition in a few physical ticks rather than waiting the production default sixty seconds.

The sensor equations, scan phase, body trajectory, engineering power/heat grants and measurement generation remain production paths.

## Acceptance

The focused fixture uses one finite physical radar-repeater decoy as a persistent target:

- two ordinary radar scans first establish velocity;
- observer updates are deliberately skipped long enough for the shortened policy to age the hypothesis below `TRACKED`;
- a non-scan observer update proves velocity continuity is cleared before any reacquisition;
- the next physical radar scan reacquires position but leaves velocity unknown;
- the following scan establishes velocity from two new post-loss Cartesian solutions;
- identical loss/reacquisition sequences replay deterministically.

## Remaining Stage 19I-D

This closes stale velocity continuity. Richer deception/association hypotheses and larger multi-ship information stress remain before the 8v8/32+/saturation scale gates can be considered complete.
