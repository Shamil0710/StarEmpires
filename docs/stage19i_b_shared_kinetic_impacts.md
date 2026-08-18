# Stage 19I-B — shared kinetic impact/protection gate

**Status:** implementation slice. This extends the shared 4v4 authority chain through physical swept collision, shield/material response and persistent local damage. It is still not the full Stage-19I exit gate.

## Purpose

A shared projectile body is not sufficient if impact is later decided by endpoint distance, a hit roll or one duel-specific target. This slice makes ballistic bodies interact with all physical ships in one battle through relative swept geometry and the existing Stage-17.5F protection stack.

## Relative swept collision

For every fixed tick, the battle stores each ship's start position before the shared control/flight phase. Existing projectile bodies then advance over the same interval while each target moves from its start to end position.

Collision is solved in target-relative coordinates:

```text
relative projectile start = projectile start - target start
relative projectile end   = projectile end   - target end
→ first segment/AABB intersection fraction in [0,1]
```

The footprint uses the production hull bounding length and width. The first physical intersection wins; equal-fraction ties use stable target entity ID only for deterministic ordering.

Newly spawned projectiles use the post-movement target position for both endpoints because muzzle exit occurs after the shared movement phase in the current tick ordering.

This prevents high-speed tunneling and avoids giving later-updated ships newer geometry.

## No allegiance collision immunity

After muzzle exit, a `ProjectileBody` is a physical body, not a target-owned damage event. Collision considers every combatant except the source entity. Friendly ships therefore cannot become intangible merely because the projectile was aimed at an opponent.

## Production protection path

At the first swept intersection, the projectile is reconstructed at the exact impact fraction and passed to the existing:

```text
KineticProtectionRuntime
→ ShieldFieldRuntime interaction
→ HeavyImpactResolver armor/material response
→ ShipDamageRuntime local compartment/subsystem damage
```

The target uses its existing production hull, installed fit, protection layout, persistent shield states and persistent damage snapshot.

No battle-local hit points or damage scalar are introduced.

## Shield continuity

Operational fitted shield definitions are re-derived through `ShipShieldEngineeringAdapter` from the target's current damage-aware state. The first deterministic emitter whose authored coverage contains the incoming world/local threat direction is supplied to `KineticProtectionRuntime`.

After an impact:

- shield interaction reserve/collapse state replaces the existing state for that mount;
- local damage snapshot is replaced only when `ShipDamageRuntime` produced a damage event;
- emitter integrity is re-derived from the post-damage fitted state and applied back to persistent shield continuity;
- destroyed emitters are clamped to zero integrity rather than silently retaining pre-damage capability.

The current physical transform model has no hull rotation channel, so world axes and hull-local axes are currently identical for collision footprint and threat direction. This is an explicit present-model boundary, not a hidden orientation assumption in hit probability.

## Residual bodies

If the production material response leaves a residual projectile, that existing `ProjectileBody` continues for the unused fraction of the tick and remains in the shared body set. A stopped body is removed only because the production protection result physically stopped it.

This slice resolves at most one ship intersection per projectile per tick. A residual body can intersect another ship on a later tick; same-tick chained multi-hull penetration remains a future refinement if required by saturation profiling.

## Acceptance requirements

The Stage-19I 4v4 acceptance in this slice requires:

- swept geometry detects bodies crossing an entire hull between endpoints;
- moving-target relative motion is represented without endpoint-overlap shortcuts;
- all combatants continue using finite physical ammunition and launcher cycles;
- shared projectile bodies resolve at least one production protection interaction;
- physical incoming fire is observed on both sides of the symmetric 4v4 fixture;
- at least one target's persistent shield reserve or local compartment integrity changes;
- projectile disappearance is explainable by a resolved physical impact;
- identical scenarios and fixed ticks produce equality-identical control, stores, cooldowns, projectile, impact-count, shield and damage fingerprints.

## Still required for Stage 19I

The remaining major scaled-combat work includes:

- guided missiles/guidance bodies;
- point defense/interceptors;
- EW/ECCM/decoys and degraded information scenarios;
- damaged and ammunition-depleted behavior acceptance;
- explicit retreat/disengagement objective completion;
- 8v8 and >=32 combatants;
- dense ordnance saturation;
- sustained performance/memory profiling;
- live viewer projection of the same shared authoritative runtime.
