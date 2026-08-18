# Stage 19I-I — scaled live / headless / read-only presentation parity

Status: implementation/acceptance slice after the green 32-ship saturation/profiling gate.

## Purpose

The Stage-19I scale ladder is not complete merely because a headless 32-ship runtime works. A live viewer must consume the same authoritative battle without creating a second combat clock, simplified viewer simulation or mutable presentation path.

This gate therefore makes the accepted 32-ship saturation setup reusable by both headless and live/presentation execution and proves that presentation reads cannot change the outcome.

## One saturation factory

`Stage19ScaledLiveTacticalFactory.createSaturation32()` is the single setup owner for parity validation. It creates:

- `LiveTacticalBattleScenario.mixed16v16()`;
- the same four STRIKE+DECOY B-fit specialists used by the accepted saturation gate;
- the same four INTERCEPTOR-screen B-fit specialists;
- the normal shared production chain through control, weapon, ordnance, decoy, observation, layered defense and deception runtimes.

There is no live-specific doctrine, ammo quantity, AI policy or physics configuration.

## Read-only scaled projection

`ScaledLiveTacticalSimulationProjection` converts the current production state into the existing immutable `TacticalPrototypeVisualSnapshot` already consumed by `TacticalPrototypeRenderer`.

It projects:

- all materialized ships: authoritative entity identity, position, physical hull dimensions and mean compartment integrity;
- ordinary kinetic/residual `ProjectileBody` state as `KINETIC_PROJECTILE`;
- offensive `GuidedWeaponBody` state as `GUIDED_MISSILE`;
- physical interceptor bodies as `INTERCEPTOR`;
- physical decoy bodies as `DECOY`.

Heading is a presentation derivation from authoritative velocity. When a ship has no velocity yet, only its authored side orientation is used for the initial visual facing.

The current scaled runtime does not expose a dedicated read model for instantaneous thrust fraction or historical beam/impact/shield visual events. The projection therefore leaves those fields neutral/empty rather than inferring simulation facts that do not exist.

The projection owns no clock and calls no mutation/update method.

## Live session

`ScaledLiveTacticalSimulationSession` is deliberately thin:

- its default constructor obtains exactly one fresh runtime from `Stage19ScaledLiveTacticalFactory`;
- `advanceOneTick()` delegates exactly once to the production runtime;
- `snapshot()` performs only the read-only projection;
- `fingerprint()` returns the authoritative production fingerprint.

A renderer may request zero, one or many snapshots between fixed ticks without changing simulation state.

## Acceptance

### Read-only non-mutation

After advancing the scaled live session, the test captures authoritative tick and fingerprint, performs 40 consecutive projection reads and requires:

- tick unchanged;
- fingerprint unchanged;
- exactly 32 projected ships;
- projected body counts by kind exactly equal the corresponding authoritative physical pools.

### Headless/live replay parity

Two fresh runtimes are created from the same factory:

- one is advanced directly as headless authority;
- one is advanced through `ScaledLiveTacticalSimulationSession` while arbitrary snapshot reads occur before and after fixed ticks.

After the identical 80-tick schedule, both authoritative fingerprints must be exactly equal.

### Saturation presentation completeness

A live session advances for at most 240 ticks while being projected each tick. At least one immutable snapshot must contain all four required body classes simultaneously:

- kinetic/residual;
- STRIKE guided;
- interceptor;
- decoy.

This proves the existing presentation snapshot/renderer model can represent the accepted saturation state without changing battle authority.

## Scope boundary

This gate validates the live **session/projection/renderer authority boundary**. It deliberately reuses the existing list-based `TacticalPrototypeRenderer`; no second scaled renderer is introduced.

A manual graphical validation entry point may still be useful for visual inspection, camera framing and final presentation polish, but those concerns are not allowed to become a second simulation path and are not required for authoritative parity.

## Remaining Stage 19I exit review

After this gate, the scale/runtime/presentation ladder itself is covered. Stage 19I must still undergo a final requirement-matrix review before being marked complete, with particular attention to still-explicit behavior gaps rather than silently interpreting them as passed:

- finite ammunition currently stops physical firing, but a zero-ammunition tactical behavior transition still requires explicit acceptance/design;
- power/heat already gate real sensor/ECCM operations, but the final matrix must explicitly prove the resulting information loss changes AI decisions;
- ship-target fire control still uses the documented zero target-motion estimate seam because production `TrackState` has no velocity-estimate channel;
- datalink range/latency/transport-noise parameters are not yet authored content;
- richer deception/association behavior remains separate from the physical decoy diversion already accepted.

Stage 19 and Stage 19I must not be declared complete until that final matrix review is closed and green.
