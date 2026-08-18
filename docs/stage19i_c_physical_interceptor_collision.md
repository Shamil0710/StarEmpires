# Stage 19I-C — Physical Interceptor / Threat Collision

Status: IMPLEMENTED IN THIS SLICE, PENDING CI/MERGE

## Purpose

Complete the first physical layered-defense consequence path. A `LayeredDefenseScheduler` assignment and interceptor launch do not themselves defeat a threat. The interceptor must physically contact a moving guided threat through shared swept geometry.

## Swept body-body geometry

`TacticalCollisionGeometry.firstSegmentCircleHitFraction(...)` resolves the first contact of two moving bodies in relative coordinates. Each guided body uses an orientation-independent circumscribed radius:

`0.5 * hypot(lengthM, diameterM)`

The collision radius is the sum of interceptor and threat radii. Endpoint overlap is not required, so high relative velocities cannot tunnel through one another solely because the fixed tick is larger than the body dimensions.

The geometry helper contains no factions, hit chance, damage or defense policy.

## Guided-body ownership

`LiveTacticalBattleOrdnanceRuntime.removeGuidedBody(...)` is a package-local physical-removal seam. It may be called only after a physical interception has already been established. Removal also releases the strike launcher's active support-channel ownership; historical launch/ship-impact counters are not rewritten.

## Provisional body-body material response

The project does not yet own an explosive fragmentation/debris solver for interceptor collisions. `GuidedBodyCollisionResolver` therefore applies a deliberately conservative perfectly inelastic response:

- interceptor and threat both cease to exist as guided bodies;
- both original physical masses are preserved as two ordinary `ProjectileBody` residuals;
- both residuals preserve their original material, shape and source identity;
- both receive the shared center-of-mass velocity;
- total linear momentum and total mass are conserved;
- kinetic energy lost by the inelastic response is recorded as unresolved deformation/fragmentation/heat and is **not** granted as abstract ship damage;
- both residuals enter the one existing production projectile pool.

This avoids inventing a composite material or deleting physical mass. A later fragmentation/warhead model may replace this provisional response while preserving the same physical-authority boundary.

## Runtime ordering

For each fixed tick the defense layer:

1. snapshots active strike and interceptor positions;
2. advances the shared ship/kinetic/guided ordnance runtime;
3. guides and propagates existing interceptors;
4. resolves swept interceptor-versus-guided-body contact;
5. transfers physical collision residuals into the common projectile pool;
6. schedules and materializes new interceptor launches for surviving threats.

New interceptors therefore cannot retroactively intercept a threat during the tick in which they were assigned.

### Known same-tick ordering boundary

The wrapped guided-ordnance runtime currently resolves guided-body/ship contact before the outer defense collision phase. If a threat reaches a ship and would also meet an interceptor during that exact same fixed interval, ship-impact processing currently wins ordering priority.

This is explicitly not considered final Stage-19 acceptance. Before closing Stage 19I, scaled evidence must either demonstrate that the ambiguity is immaterial at the accepted fixed timestep or motivate a unified earliest-event resolver across ship/body/body contacts.

## Acceptance

The gate requires:

- swept fast-body crossing detection independent of endpoint overlap;
- deterministic miss and start-overlap cases;
- provisional collision response to conserve total mass and linear momentum;
- a real B-fit interceptor-loaded defender to receive a scheduler assignment and launch;
- a subsequent swept physical interceptor/threat contact;
- the strike and interceptor to leave their guided pools only after physical contact;
- two ordinary residual projectile bodies to enter the common projectile pool;
- identical fixed-tick runs to reproduce the same defense fingerprint.

## Still pending

Stage 19 remains open. Mandatory follow-up includes:

- actor-bounded ordnance sensing instead of the temporary exact-local bridge;
- EW/ECCM, seeker degradation/loss and decoys;
- kinetic point-defense integration with explicit data-driven role routing;
- damaged/depleted tactical behavior, retreat and reassignment;
- 4v4 defensive-screen acceptance, 8v8, 32+ and saturation;
- performance/memory profiling;
- live/headless parity and final Stage-19 exit evidence.
