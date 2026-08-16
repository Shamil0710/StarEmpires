# Star Empires — Stage 17.5E Projectile Representation Invariant

> **Status:** ACCEPTED architecture decision for Stage 17.5E  
> **Applies to:** kinetic projectiles, missiles, torpedoes, interceptors, residual kinetic bodies and relevant debris  
> **Does not redefine:** Stage 17.5F impact/armor/compartment damage solving or Stage 17.5H final UI/persistence surfaces

## 1. Canonical rule

Every **significant kinetic projectile, missile and interceptor** is an individual authoritative physical combat body with its own physical state and trajectory.

An authoritative physical body is **not required** to be:

- one Ashley ECS entity;
- one independently allocated heavyweight Java object;
- one visible sprite at every zoom level;
- one render call;
- one permanently materialized high-detail object outside the simulation detail window.

The simulation representation, ECS/materialization representation and render representation are separate concerns.

Canonical relation:

```text
authoritative physical body
    != mandatory Ashley Entity
    != mandatory visible sprite
    != visual tracer / VFX hitbox
```

## 2. Kinetic projectile state

A significant kinetic projectile preserves at minimum the Stage-17.5E physical contract:

```text
stable projectile identity
source / owner identity seam
material
shape
dimensions
mass
position
velocity
momentum
kinetic energy
spawn / simulation time state
```

Additional weapon-family data may be present where physically required.

Firing does **not** resolve to an authoritative hit/miss roll.

Canonical flow:

```text
fire-control TrackState / covariance
+ weapon pointing state / uncertainty
+ shooter kinematics
+ projectile muzzle state
        ↓
physical initial projectile state
        ↓
trajectory propagation
        ↓
actual geometric intersection or miss
        ↓
Stage 17.5F impact / protection / compartment path when intersection occurs
```

There is no authoritative `weaponAccuracy`, `projectileHitChance` or equivalent hidden hit roll replacing this path.

## 3. Guided weapons

Missiles, torpedoes and interceptors are also individual physical bodies.

Their richer runtime state may include:

```text
wet mass
dry mass
remaining propellant
thrust
exhaust velocity
mass flow
seeker state
guidance state
track / datalink input
terminal reserve
warhead / impact state
```

A guidance or seeker kill does **not** delete the physical body.

Canonical rule:

```text
guidance destroyed
→ guidance capability lost/degraded
→ physical missile body, position, velocity, momentum and kinetic energy remain
```

The body continues according to its remaining physical state until ordinary lifecycle, collision, destruction or simulation-LOD rules resolve it.

## 4. Beam weapons

Beam weapons do **not** create fake projectile bodies merely to fit the kinetic implementation.

Their authoritative runtime remains beam-specific:

```text
wavelength
aperture
jitter
beam power
range-derived spot geometry
dwell
local material response
thermal duty
```

Visual beam effects are representation only and do not create a ballistic body unless the specific weapon family physically ejects matter.

## 5. Efficient runtime representation

Individual physical identity does not require one heavyweight ECS entity per round.

Implementation may use a dense deterministic projectile store/pool such as structure-of-arrays or another cache-efficient representation, for example:

```text
ProjectileId[]
positionX[] / positionY[]
velocityX[] / velocityY[]
massKg[]
materialId[]
shape / dimensions[]
sourceId[]
spawnTick[]
```

This is preferred for large populations of simple ballistic bodies when it preserves:

- individual physical identity;
- deterministic ordering;
- trajectory continuity;
- collision correctness;
- ordinary ammunition accounting;
- no player/AI distinction.

Richer guided bodies may materialize through a more capable ECS/runtime representation when their seeker, propulsion, guidance, datalink or damage state requires it.

## 6. Simulation LOD

Simulation LOD may change **how** a projectile is represented, but must not create a different gameplay result merely because the camera, player ownership or rendering distance changed.

Allowed examples:

- dense projectile pool instead of Ashley entities;
- analytical propagation between deterministic interaction windows;
- bounded aggregate debris representation where individual fragment tracking is not gameplay-significant;
- dematerialization/rematerialization across tactical detail boundaries while preserving sufficient authoritative state.

Forbidden examples:

- deleting a projectile solely because it left the camera;
- converting an off-screen player or AI projectile into a different hit-probability formula;
- giving player projectiles higher-fidelity physics than AI projectiles;
- making remote projectiles disappear before their physical consequences are resolved;
- using render distance as weapon range.

## 7. Rendering invariant

Render representation is explicitly non-authoritative.

A physical projectile may be rendered as:

```text
close zoom       → small projectile sprite / body
medium zoom      → sprite + readable streak
far zoom         → tracer/streak only
extreme distance → not rendered, while simulation continues
```

The visual tracer may be intentionally much larger or longer than the real projectile for gameplay readability.

**Tracer, glow, sprite bounds and other VFX must never become the authoritative collision geometry unless they exactly represent the authored physical body by explicit design.**

Canonical relation:

```text
physical collision geometry
    != readability tracer
    != sprite pixel footprint
```

## 8. Misses remain physical

A projectile that misses does not disappear because an RNG result is `MISS`.

It continues as a physical body according to the simulation/LOD policy.

This is especially important for long time-of-flight combat and large kinetic weapons. A fired projectile may remain relevant after the tactical engagement if its trajectory can still produce a meaningful physical interaction.

Long-lived projectiles may eventually transition to a lower-detail world representation, but that transition must be deterministic and must not create hidden player-only or AI-only outcomes.

## 9. Point defense and residual bodies

Point defense acts on physical threats, not abstract hit chances.

A successful interception may produce several different physical outcomes, for example:

```text
complete destruction
propulsion kill
guidance kill
warhead kill
fragmentation
trajectory change
residual intact kinetic body
residual debris packet / bodies
```

Therefore a successful guidance kill or mission kill is not automatically equivalent to deleting all kinetic risk.

Stage 17.5F owns final spatial damage/fragment/debris interaction with armor, protection stacks, compartments and subsystems.

## 10. Determinism and performance acceptance

Stage 17.5E implementation must demonstrate that the chosen projectile representation can support large projectile populations without abandoning the physical model.

Acceptance must include at least:

1. two identical simulations produce identical projectile trajectories and ordering;
2. ammunition count decreases physically when projectiles/interceptors are launched;
3. a missed projectile continues instead of resolving through a hidden miss roll;
4. a guidance-killed missile remains a kinetic body;
5. render visibility does not affect trajectory/collision outcome;
6. player and AI projectiles use the same propagation rules;
7. dense/simple projectile representation does not require one Ashley Entity per round;
8. tracer/sprite dimensions do not affect authoritative collision geometry;
9. formation spacing can change interception geometry through physical positions;
10. large projectile waves remain compatible with deterministic performance-oriented pooling/LOD.

## 11. Final invariant

> **Physical individuality is mandatory where the projectile is gameplay-significant; heavyweight ECS identity and per-frame sprite rendering are not. The authoritative simulation tracks the physical body, while ECS materialization and rendering are implementation/LOD choices that may optimize performance without changing the physical outcome.**
