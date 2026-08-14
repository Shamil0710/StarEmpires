# Star Empires — Flight Dynamics and Combat Depth Roadmap

> Cross-cutting plan for ship mass/inertia, thrust-limited movement, cargo-dependent handling and the ordering of advanced tactical AI after the combat model becomes sufficiently expressive.
>
> Added: **2026-08-14** after Stage 13 established the first shared deterministic combat pipeline and after `docs/ai_behavior_roadmap.md` established civilian risk-aware behavior as a separate concern.

---

## 1. Design decision

Two future directions are explicitly separated:

1. **civilian / strategic behavior can improve early** — flee from attacks, avoid known dangerous systems, trade profit against risk and use escorts;
2. **advanced tactical combat AI is gated by combat-system depth** — it should not be tuned around a temporary combat model that only has simple range, shields, hull and one weapon profile.

Advanced tactical AI should become a major implementation phase only after the game has enough physical and combat variables for tactics to be meaningful, including at least:

- several ship classes with materially different movement/combat envelopes;
- meaningful armor and shield behavior;
- multiple weapon categories/range profiles;
- equipment/fitting choices that change capability;
- enough weapon/ship metadata for target and range decisions;
- shared inertial movement where acceleration and braking are real constraints.

This avoids writing sophisticated AI twice: first around a placeholder combat model, then again after armor, fitting, weapon classes and mobility change the optimal tactics.

---

## 2. Current movement seam

The current Stage-12 direct-control implementation intentionally provides a simple playable movement primitive: `PlayerDirectControlSystem` writes requested velocity directly from input speed and advances position during the fixed tick.

That is sufficient for the original playable harness but does **not** model ship inertia. A future flight-dynamics layer should replace the instantaneous-velocity assumption with shared thrust-limited acceleration while preserving the existing rule that UI/player input only submits intent and authoritative fixed-tick simulation mutates physical state.

The new movement model must be shared by player and AI wherever equivalent movement is simulated. AI must not receive hidden acceleration/braking advantages.

---

## 3. Game-feel target: inertial, not hardcore simulation

The target is a **game-friendly inertial space-flight model**, not an unrestricted orbital-mechanics simulator.

Desired feel:

- velocity does not jump instantly when the player presses a direction;
- releasing input does not necessarily stop the ship instantly;
- braking requires time and available reverse/maneuvering thrust;
- a light interceptor responds quickly;
- a heavy freighter responds slowly;
- the same freighter handles noticeably worse when heavily loaded;
- equipment/armor can trade survivability for mobility;
- a damaged propulsion system may later degrade acceleration or maneuverability;
- controls remain readable and predictable for a top-down sandbox.

Default controls may use **flight assist**: input represents desired motion, while the flight controller applies the physically available thrust needed to approach that motion. The underlying acceleration/braking remains real and bounded.

A later optional assist-off/drift mode may be considered, but is not required for the core design.

---

## 4. Mass model

In space the relevant property is **mass**, not gravitational weight.

A future authoritative ship mass can be derived as:

```text
totalMass = dryHullMass
          + cargoMass
          + armorMass
          + equipmentMass
          + ammunitionMass
          + other physical stores
```

Not every term must be introduced simultaneously. The model should grow as those mechanics become real.

### 4.1 Dry hull mass

Every ship archetype should eventually define a stable dry mass.

Examples of intended qualitative differences:

- interceptor / light fighter — very low mass;
- corvette — low/medium mass;
- frigate — medium mass;
- freighter — high dry mass plus large cargo-mass variation;
- carrier / capital ship — very high mass and correspondingly large absolute thrust, but lower thrust-to-mass response.

### 4.2 Cargo mass

Cargo must influence handling physically rather than being only an inventory integer.

Each physical item/resource may eventually expose unit mass. Then:

```text
cargoMass = sum(itemQuantity × itemUnitMass)
```

This creates useful emergent differences:

```text
empty bulk freighter
→ acceptable acceleration / braking

fully loaded bulk freighter
→ significantly more momentum
→ slower acceleration
→ longer braking distance
→ weaker lateral response
```

A cargo ship therefore behaves differently during the outbound empty leg and the loaded return leg without requiring a special scripted state.

### 4.3 Equipment and armor mass

When fitting becomes richer, installed modules and armor should contribute to mass where appropriate.

This enables real trade-offs:

- heavier armor improves survivability but harms acceleration;
- larger reactor/engine package may increase both mass and available power/thrust;
- extra cargo expansion may make a ship more profitable but less agile;
- weapon packages can influence handling through physical mass rather than hidden class penalties.

---

## 5. Propulsion model

The fundamental relationship should be conceptually equivalent to:

```text
acceleration = availableThrust / totalMass
```

The implementation may use game-scaled units, but the dependency should remain explicit and deterministic.

A ship archetype / propulsion fit can eventually distinguish:

- forward/main-engine thrust;
- reverse/braking thrust;
- lateral maneuvering thrust;
- rotational torque / turning authority;
- cruise/operational speed limits where required for game readability;
- boost/afterburn capability where later equipment supports it.

### 5.1 Braking

Braking is not an instantaneous `velocity = 0` operation.

The flight controller should calculate available deceleration from the current mass and appropriate braking/reverse thrust. A heavy loaded vessel therefore needs more distance and time to stop.

Useful player-facing information later includes:

- current speed;
- acceleration capability;
- estimated braking distance/time;
- mass/load state;
- optional vector indicator when velocity and facing/intended movement differ.

### 5.2 Turning / rotational inertia

Once facing and weapon arcs matter, rotational behavior should also differ between hulls.

A practical model can use:

- hull rotational inertia class or computed moment-of-inertia approximation;
- maneuvering torque;
- angular acceleration;
- bounded angular velocity.

This prevents a capital ship from rotating like a fighter and gives weapon arcs/hardpoints tactical meaning.

Rotational inertia can be introduced after translational acceleration if needed; it does not have to block the first movement-dynamics slice.

---

## 6. Shared movement architecture

The intended architecture is:

```text
player input / AI navigation intent
              ↓
      desired movement intent
              ↓
     shared flight controller
              ↓
 mass + propulsion + current velocity
              ↓
 authoritative fixed-tick acceleration
              ↓
 Transform velocity / position
```

Important invariant:

**player and AI express intent; the same physical flight dynamics decides what the ship can actually do.**

This prevents future tactical AI from cheating by setting its velocity directly while the player is acceleration-limited.

Path planners may estimate travel time using the same ship mobility envelope at a strategic approximation level, but local execution remains authoritative.

---

## 7. Why this matters to combat

Movement dynamics becomes part of the combat model rather than only presentation.

Examples:

- a light interceptor can close distance rapidly but may have low durability;
- a missile ship can attempt to preserve range only if its thrust-to-mass ratio permits it;
- a heavy brawler may win once it closes but struggle to catch a faster target;
- a loaded freighter may be unable to escape an attacker that the same empty hull could outrun;
- escorts must account for the slowest protected ship;
- braking distance matters when overshooting a target or station;
- armor/fitting choices alter both combat durability and maneuverability.

Therefore advanced tactical AI should be trained/balanced only after these relationships exist.

---

## 8. Combat-depth prerequisite before advanced tactical AI

Stage 13 remains the correct minimal combat foundation, but it is intentionally not the final tactical model.

Before the **major advanced combat-AI phase**, implement a combat-depth layer with enough of the following mechanics to create real tactical choices.

### 8.1 Ship classes and hull roles

At minimum several materially different combat hull envelopes should exist, for example:

- light fighter/interceptor;
- corvette;
- frigate;
- heavier combat vessel;
- civilian transport/miner with limited or optional armament.

Differences should come from data such as:

- mass;
- thrust/mobility;
- hull/armor/shield capacity;
- hardpoints / weapon compatibility;
- cargo/equipment capacity;
- sensor/signature values where introduced.

### 8.2 Armor

Armor should become more than additional generic hull HP.

Possible staged mechanics:

- armor durability / mitigation;
- armor class/thickness by hull or fitted package;
- damage-type interaction;
- armor degradation;
- later directional/sectional armor only if it improves gameplay enough to justify complexity.

The first useful version should remain deterministic and data-driven.

### 8.3 Shields

Shields can later gain meaningful distinctions such as:

- capacity;
- recharge rate/delay;
- power demand;
- damage-type efficiency;
- overload or temporary collapse behavior.

This creates decisions about burst damage, disengagement and re-engagement instead of shields being only a second HP bar.

### 8.4 Weapon categories

Introduce several weapon envelopes before sophisticated weapon-aware AI, for example:

- short-range rapid weapons;
- medium-range general-purpose weapons;
- long-range guns/energy weapons;
- missiles/torpedoes with limited ammunition or other constraints;
- point defense;
- later area/specialized weapons where justified.

Relevant data may include:

- damage and damage type;
- range / preferred range;
- cooldown/burst;
- projectile speed;
- tracking/accuracy;
- ammunition or energy use;
- hardpoint/firing arc;
- target-size effectiveness;
- special effect tags.

### 8.5 Equipment / fitting

Ships should eventually have data-driven equipment that changes their actual capabilities, such as:

- engines/thrusters;
- reactors/power systems;
- shields;
- armor packages;
- sensors;
- ECM/ECCM;
- cargo modules;
- mining equipment;
- weapon mounts/ammunition support;
- utility/defensive systems.

The exact slot model can be decided when fitting work begins. The critical AI requirement is that capability be queryable from authoritative state rather than inferred from hard-coded ship names.

---

## 9. Advanced combat AI gate

The major tactical-AI implementation should not start merely because more AI code would be possible.

Suggested gate:

**Advanced tactical combat AI becomes ACTIVE only when:**

1. shared inertial movement is operational for representative player/AI ships;
2. at least several ship classes have distinct mobility/durability envelopes;
3. armor and shields have stable first-pass mechanics;
4. multiple weapon categories produce different preferred engagement behavior;
5. fitting/equipment can materially alter at least mobility, defense or weapons;
6. combat state exposes these capabilities through stable data/query APIs;
7. deterministic combat acceptance tests exist for the enriched model.

Before this gate, combat AI should remain deliberately simple and correct rather than elaborate and disposable.

Civilian flee/risk-routing AI is **not** blocked by this gate because its value already exists with the current living economy and shared travel model.

---

## 10. Revised stage ordering

### Stage 14 — First complete player loop

Keep the current Stage-14 goals, but add a **flight-dynamics foundation before final v0.3 acceptance** if implementation risk remains manageable.

Recommended order inside Stage 14:

```text
14A player mining
14B real ship purchase / switching
14C navigation + HUD + minimap
14E flight-dynamics baseline
14D first-hour acceptance / telemetry
```

`14E` is deliberately named as an inserted cross-cutting slice rather than renumbering existing work.

Minimum 14E target:

- dry mass per representative hull;
- cargo contributes real mass;
- thrust-limited acceleration;
- non-instant braking;
- shared movement executor for direct player movement and compatible AI movement paths;
- deterministic tests showing a light ship and a loaded freighter accelerate/stop differently;
- HUD/debug exposure for speed/mass/acceleration sufficient to tune the model.

If implementing all of this inside Stage 14 would destabilize the first-loop milestone, move 14E immediately after Stage 14 and **before Stage 15 fleet behavior**, rather than postponing it until advanced combat.

### Stage 15 — Fleets / autonomous orders

Civilian and fleet-level AI may already improve here:

- flee from attack;
- risk-aware route selection;
- escort-aware route decisions;
- convoy cohesion based on the slowest critical vessel;
- movement orders executed through the shared inertial model.

Do **not** attempt the final sophisticated weapon-aware tactical AI yet.

### Stage 16 — Construction / station ownership

Risk-aware logistics and convoy movement use real ship mass/mobility. Heavy construction cargo can therefore have real transport-time and vulnerability consequences.

### Stage 17 — Player faction

Introduce faction doctrine parameters for civilian risk, escort preference and broad aggression/retreat policy. These doctrine values may exist before the advanced tactical executor that will later consume the richer combat-specific subset.

### Stage 17.5 — Combat Depth / Ship Fitting Foundation

Insert a dedicated prerequisite slice before strategic warfare if the mechanics have not already matured organically.

Target:

- representative hull classes;
- armor mechanics;
- richer shields;
- multiple weapon categories;
- equipment/fitting foundation;
- mobility/fitting mass integration;
- stable combat capability queries;
- deterministic enriched-combat acceptance tests.

This stage is the explicit **gate before advanced tactical AI**.

### Stage 18 — Strategic Warfare + Advanced Combat Behavior

Stage 18 should then be ordered internally as:

```text
18A consume stable combat-depth capability model
18B advanced tactical AI / range / target / retreat / formation behavior
18C fleet combat doctrine / escort / screen / coordinated actions
18D strategic war / fronts / blockade / territory effects
18E conflict → traffic rerouting → economic consequence validation
```

The advanced AI now optimizes against mechanics that are intended to survive into later development instead of temporary placeholders.

### Stage 20–21

Commander personality can specialize doctrine later, and Stage 21 scenario/soak matrices tune the resulting movement + fitting + tactical ecosystem.

---

## 11. Acceptance scenarios for flight dynamics

At minimum the mature baseline should prove deterministic cases such as:

1. **Light vs heavy acceleration:** a light interceptor reaches the same requested speed faster than a heavy freighter.
2. **Cargo mass:** the same freighter accelerates and brakes more slowly when loaded with a heavy cargo than when empty.
3. **No instant stop:** releasing or reversing movement cannot zero velocity in one tick unless the physical thrust/mass envelope genuinely permits it.
4. **Shared player/AI physics:** equivalent player and AI ships under equivalent thrust/mass conditions receive the same physical acceleration limits.
5. **Braking distance:** a faster/heavier ship requires measurably more distance/time to stop.
6. **Armor/equipment trade-off:** a heavier fitted configuration can gain capability while paying the corresponding movement cost once fitting exists.
7. **Save/load continuity:** any persistent velocity/mass-dependent state required for deterministic continuation survives save/load correctly.
8. **Fleet constraint:** an escort group protecting a heavy transport does not plan tactical movement as if every member had interceptor mobility.

---

## 12. Design constraints

1. Use mass and thrust as authoritative gameplay data; do not fake handling differences only in animation.
2. Cargo mass must come from real inventory content/state where practical.
3. Player and AI use the same local flight-dynamics rules.
4. Movement remains fixed-tick and deterministic.
5. Do not introduce full unrestricted Newtonian complexity unless it demonstrably improves the intended game.
6. Flight assist may make controls convenient but may not bypass physical acceleration/braking limits.
7. Advanced tactical AI is gated by stable combat depth; civilian survival/risk AI is not.
8. Ship/fitting differentiation should emerge from data and shared systems, not scattered class-name conditionals.
9. Strategic travel-time estimates and local movement should be compatible enough that planners do not systematically choose physically impossible schedules.
10. Any future propulsion damage, fuel, power or heat mechanics must modify the same capability model rather than adding parallel exceptions.
