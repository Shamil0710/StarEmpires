# Star Empires — Stage 17.5E Weapons / Ammunition / Guidance / Layered Defense

> Статус: **IMPLEMENTATION COMPLETE — awaiting final exact-head merge gate**  
> Базовая ветка: green `main` after Stage 17.5D (`3f52b9e5cee0f733b5b07ec147bc2a364fe7a26f`)  
> Канонический план: `docs/stage17_5_combat_depth_implementation_plan.md`  
> Projectile representation decision: `docs/stage17_5e_projectile_representation_invariant.md`

---

## 1. Цель

Stage 17.5E заменяет абстрактные combat-вероятности фундаментом, в котором оружие работает через физические bodies, fire-control information, реальные боеприпасы, launcher readiness, propulsion, geometry и общие engineering budgets.

Главный инвариант:

```text
fitted hardware
+ physical ammunition
+ current track
+ launcher readiness
+ power / heat permission
+ geometry / time
→ physical fire / beam / guidance / defense plan
→ independent physical body or beam interaction
→ later Stage-17.5F impact / protection / damage path
```

Stage 17.5E не вводит authoritative:

- `weaponAccuracy`;
- `missileHitChance`;
- `PDChance`;
- universal weapon `maxRange` / hard range wall;
- free ammunition;
- class-name weapon bonuses.

---

## 2. Projectile representation invariant

Зафиксировано отдельным architecture decision:

> **Каждый значимый kinetic projectile, missile и interceptor является отдельным authoritative physical combat body. Отдельное физическое тело не обязано быть отдельной Ashley Entity или отдельным видимым sprite. Simulation, ECS materialization и rendering — разные representations.**

Следствия:

- projectile имеет stable simulation-local ID;
- projectile хранит source identity, deterministic spawn tick, material, shape, dimensions, mass, position и velocity;
- momentum / kinetic energy выводятся из физического состояния;
- промах не удаляет projectile автоматически;
- render distance, camera zoom, tracer visibility и sprite size не участвуют в authoritative trajectory;
- tracer / VFX не является hitbox;
- guidance kill не удаляет missile body;
- beam weapon не создаёт фиктивный projectile object.

Для больших simple-kinetic populations реализован `KineticProjectilePool`:

- structure-of-arrays storage;
- stable ascending projectile IDs as deterministic iteration order;
- no Ashley dependency;
- on-demand immutable `ProjectileBody` materialization;
- deterministic ballistic `advanceAll`;
- explicit lifecycle removal only;
- acceptance on 10,000-body wave and bit-identical repeatability.

---

## 3. Kinetic weapon runtime

Production objects:

```text
WeaponDefinition.KineticRound
ProjectileBody
KineticProjectilePool
WeaponFireControl
```

`KineticRound` defines:

- stable ammunition content ID;
- material ID;
- shape;
- length / diameter;
- mass;
- fitted muzzle velocity.

Derived quantities:

```text
momentum = m * v
kineticEnergy = 0.5 * m * v^2
```

`WeaponFireControl` consumes Stage-17.5D `TrackState` plus explicit shooter/target motion estimate and pointing jitter.

It returns:

- nominal time of flight;
- predicted aim point;
- inertial projectile launch velocity;
- propagated one-sigma aim uncertainty;
- separate bounded maneuver envelope.

It does **not** collapse these into a hit percentage.

Position-unknown bearing-only tracks cannot become fake exact fire solutions.

No arbitrary range wall exists: range affects time of flight, uncertainty growth and target maneuver opportunity.

---

## 4. Physical ammunition and launcher state

`AmmunitionRuntime` operates directly on the existing central:

```text
ShipEngineeringState.ConsumableState
```

No parallel magazine quantity exists.

A shot requires the concrete fitted mount/interface to have:

- `InterfaceKind.AMMUNITION`;
- sufficient item count;
- sufficient interface-native amount;
- sufficient physical ammunition mass.

Commit removes all three consistently.

Result:

```text
fire one 150 kg dart
→ ammunition count -1
→ ammunition mass -150 kg
→ total loaded ship mass -150 kg after re-derive
```

Generic cargo cannot substitute ammunition and one mount cannot silently borrow another mount's feed.

`WeaponLoadoutState` stores only:

```text
mount + physical feed → ammunition content identity
```

It deliberately stores no count/mass.

`WeaponMountRuntime` stores only per-mount cycle readiness; it does not duplicate ammunition, power or heat state.

---

## 5. Versioned ammunition content

Added production resources:

- `data/content/weapon-ammunition-v1.json`;
- `data/content/weapon-launchers-v1.json`.

`WeaponAmmunitionCatalog` / loader are versioned and fingerprinted.

Physical ammunition content is separate from launcher hardware:

```text
ammunition item
→ material / shape / dimensions / body mass / propulsion / seeker

launcher engineering module
→ common mass / volume / power / heat / recoil / fitted mount

launcher profile
→ feed / cycle / support channels / pointing / supported projectile envelope
```

The ammunition loader validates material IDs against the existing authoritative `ShipEngineeringCatalog`; it does not copy material definitions.

The loader explicitly rejects probability/range abstractions such as:

- `weaponAccuracy`;
- `missileHitChance`;
- `PDChance`;
- `hardRangeM`;
- `maxRangeM`.

Production demonstrators include:

- 150 kg rail dart;
- 1,000 kg guided interceptor body.

The current rail dart uses the already-productionized `material.high_strength_steel_v1`; higher-density penetrator content can be added later through ordinary content expansion without changing the runtime architecture.

---

## 6. Fitted railgun integration

`ShipWeaponEngineeringAdapter` composes:

```text
DerivedShipState
+ exact engineering module ID
+ linked launcher profile
+ physical feed loadout
+ ammunition content
→ FittedKineticMount
```

The adapter does not infer performance from hull class or doctrine role.

For `module.railgun_large_v1` it validates:

```text
projectile mass = 150 kg
muzzle velocity = 9,000 m/s
physical recoil = m * v = 1,350,000 N*s
```

The physical recoil must agree with the engineering module's authored recoil impulse.

Loaded projectile geometry must fit the linked launcher envelope.

Missing/incorrect ammunition identity is rejected instead of producing a free default round.

---

## 7. Beam weapon runtime

Production object:

```text
BeamWeaponRuntime
```

Beam firing is not represented as a ballistic body.

Inputs include:

- wavelength;
- aperture;
- pointing jitter;
- beam power;
- electrical demand;
- waste heat;
- continuous dwell limit;
- target fire-control track.

Range enters continuously through:

```text
diffraction radius
+ pointing displacement
+ track-position uncertainty
→ effective spot radius
→ irradiance
```

A more distant target can remain a valid geometric beam solution while spot size grows and irradiance falls.

Beam solution explicitly returns:

- delivered beam energy;
- electrical energy demand;
- local waste heat.

Stage 17.5E does not create a parallel thermal store. Final live command composition/commit into the shared Stage-17.5C power/thermal runtime remains a Stage-17.5H integration responsibility.

---

## 8. Guided weapon runtime

Production objects:

```text
WeaponDefinition.GuidedWeapon
GuidedWeaponBody
GuidanceRuntime
```

A guided body carries:

- stable body/source/target identities;
- physical material/shape/dimensions;
- dry mass + remaining propellant;
- position / velocity;
- seeker state;
- guidance state;
- optional future impact-payload ID seam.

Propulsion uses:

```text
mdot = thrust / exhaustVelocity
Δv = ve * ln(m0 / m1)
```

Burns consume real propellant.

`GuidanceRuntime` produces deterministic thrust direction and bounded burn duration from physical track state. It does not decide a missile hit.

Information-source behavior is explicit:

- onboard-seeker guidance requires a live seeker;
- datalink guidance may continue after seeker loss;
- destroyed guidance/control cannot be restored merely because datalink data exists.

Terminal reserve protects authored delta-v from non-terminal guidance expenditure.

### Guidance-kill invariant

```text
seeker/guidance disabled
≠ body deleted
```

The residual body retains:

- material;
- shape / dimensions;
- mass;
- position / velocity;
- momentum / kinetic energy;
- ballistic trajectory.

It can therefore remain a collision/PD threat and later enter the same Stage-17.5F impact path.

---

## 9. Layered defense

Production object:

```text
LayeredDefenseScheduler
```

Threat priority is deterministic:

```text
predicted ballistic impact time
→ stable threat ID tie-break
```

Threats are first tested for actual ballistic intersection with defended geometry.

A defense station is constrained by:

- physical position;
- launcher readiness;
- support channels;
- physical ammunition rounds;
- thermal availability;
- safe minimum intercept distance;
- interceptor mass/thrust/propellant/exhaust/burn time;
- time remaining before predicted impact.

The scheduler searches physical reachability rather than applying `PDChance`.

Formation spacing therefore changes defense naturally: the same interceptor can be unable to reach a fast inbound threat from the central protected ship but become feasible when launched by a forward escort.

Repeated waves expose finite channels/ammunition/thermal permission.

If one station is infeasible, deterministic station iteration provides a fallback to another physically feasible station.

Fragment/spall/debris creation after actual interception belongs to Stage 17.5F; Stage 17.5E already preserves any residual kinetic body instead of silently deleting it.

---

## 10. Player / AI symmetry

No Stage-17.5E API contains a player/AI performance branch.

Both consume the same:

- tracks;
- fitted weapon definitions;
- ammunition feeds;
- launcher cycles;
- projectile propagation;
- guidance;
- beam geometry;
- layered-defense scheduling.

Stage 19 may choose different tactics, but cannot use different weapon physics.

---

## 11. Explicit boundaries

### Stage 17.5F

Owns:

- shield interaction;
- bounded heavy-impact material response;
- armor layers;
- ricochet / perforation / stop;
- fragments / spall / debris generation;
- compartment routing;
- subsystem damage;
- magazine-damage consequences.

Stage 17.5E provides physical body/material/geometry/energy state for this path.

### Stage 17.5H

Owns final live capability/API/UI/persistence integration, including where required:

- binary persistence of `WeaponLoadoutState` / launcher-cycle state;
- authoritative live command composition of beam/weapon power and heat into Stage-17.5C engineering runtime;
- public `getFireSolution(...)` / ammunition-endurance capability queries;
- final UI projection without direct ECS mutation.

Derived fire solutions are recomputed; they are not authoritative save data.

### Legacy Stage-13 combat

The old DPS/range vertical slice is not silently replaced inside Stage 17.5E. Stage 17.5E establishes the production physical weapon foundation; consumer migration is completed through later shared capability/live-integration work rather than introducing a second hidden combat path.

---

## 12. Verification evidence

Focused checkpoints:

- **CI #2476 — SUCCESS:** physical weapon definitions, kinetic fire-control, projectile body, central ammunition depletion;
- **CI #2481 — SUCCESS:** loadout identity, launcher cycle, guided propulsion and guidance-kill semantics;
- **CI #2483 — SUCCESS:** guided residual body material/geometry preserved;
- **CI #2493 — SUCCESS:** production ammunition/launcher content + fitted railgun integration;
- **CI #2501 — SUCCESS:** combined kinetic/beam/guidance/layered-defense functional checkpoint;
- **CI #2506 — SUCCESS:** dense deterministic large-wave kinetic projectile pool;
- **CI #2507 — SUCCESS:** consolidated Stage-17.5E production acceptance.

Consolidated acceptance verifies:

1. production railgun + physical ammunition produces a valid physical fire solution;
2. firing removes the same count/mass used by central ship derivation;
3. projectile identity/spawn tick/trajectory survive independently of rendering;
4. beam behavior degrades continuously with range instead of hitting a hard wall;
5. guidance kill preserves a physical threat body;
6. destroyed guidance cannot be bypassed through datalink;
7. formation position changes interceptor feasibility;
8. finite support channels/ammunition/thermal permission saturate layered defense;
9. thousands of simple projectiles remain individual deterministic bodies without one Ashley entity each.

---

## 13. Completion statement

Stage 17.5E is implementation-complete when this branch passes the final exact-head CI/PR/merge/post-merge gate.

The implemented contract is:

> **Weapons produce physical consequences through current information, fitted hardware, real ammunition, time, energy/heat requirements and geometry. Kinetic/guided bodies exist independently; beams use physical spot/dwell behavior; layered defense is deterministic and resource/geometry limited. No authoritative accuracy/hit-chance/PD-chance or arbitrary range wall replaces those rules.**

After merge and post-merge verification, the roadmap advances to **Stage 17.5F — shields / armor / compartments / subsystem damage**.
