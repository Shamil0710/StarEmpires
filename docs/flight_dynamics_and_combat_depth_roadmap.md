# Star Empires — Flight Dynamics and Combat Depth Roadmap

> Cross-cutting plan for ship mass/inertia, thrust-limited movement, cargo-dependent handling and the ordering of advanced tactical AI.  
> Added: **2026-08-14**; synchronized with revised Stage 18–23 ordering on **2026-08-17**.

---

## 1. Design decision

Two directions remain deliberately separated:

1. **civilian/strategic behavior can improve early** — flee, risk-aware routing, escorts, convoy decisions;
2. **advanced tactical combat AI is gated by combat depth** — it should not be tuned around a temporary combat model.

The major tactical-AI phase starts only after Stage 17.5 exposes stable physical capabilities: movement, armor/shields, multiple weapon families, fitting, sensors/tracks, consumables and subsystem damage.

---

## 2. Target flight model

The target is **game-friendly inertial space flight**, not unrestricted orbital mechanics.

Desired behavior:

- velocity cannot jump instantly;
- braking consumes time and available thrust;
- light ships respond faster than heavy ships;
- cargo/equipment/armor mass changes handling;
- propulsion damage reduces real capability;
- optional flight assist may translate intent into bounded thrust without bypassing physics.

Authoritative relation:

```text
acceleration = availableThrust / totalMass
```

Ship mass derives from real fitted/inventory state:

```text
totalMass = hull/structure
          + modules/equipment
          + armor
          + cargo
          + ammunition
          + reaction mass
          + other stores
```

---

## 3. Shared movement architecture

```text
player input / AI navigation intent
              ↓
      desired movement intent
              ↓
     shared flight controller
              ↓
 fitted mass + propulsion + damage + current velocity
              ↓
 authoritative fixed-tick acceleration
              ↓
       velocity / position
```

Player and AI express intent. Neither can set velocity directly to bypass the physical envelope.

Strategic planners may use validated travel-time approximations from the same capabilities.

---

## 4. Translational and rotational behavior

### Translational

Need:

- main thrust;
- braking/reverse thrust;
- lateral maneuvering thrust;
- current velocity;
- bounded acceleration;
- estimated braking distance/time.

Loaded freighter and empty freighter therefore differ without scripted state.

### Rotational

When facing/arcs matter, use hull geometry/moment-of-inertia approximation + maneuvering torque + angular acceleration/velocity. Capital ships must not rotate like fighters.

Rotational dynamics may arrive after translational baseline if necessary, but fitting/weapon geometry eventually consumes it.

---

## 5. Why movement belongs to combat and economy

Movement affects:

- ability to close/open range;
- escape probability;
- escort behavior;
- braking/overshoot;
- weapon arcs and firing windows;
- cargo-route timing;
- mining and construction logistics;
- convoy vulnerability;
- Stage-20 physical world calibration.

A heavy armor/refit choice therefore changes combat and logistics through the same mass model.

---

## 6. Combat-depth prerequisite

Before sophisticated tactical AI, Stage 17.5 must provide materially different:

- hull envelopes;
- mobility;
- armor/protection;
- shields;
- kinetic/beam/guided/PD weapons;
- ammunition and reaction mass;
- sensors/tracks/EW;
- fitting/equipment;
- subsystem damage/degradation;
- stable shared capability queries;
- representative production-valid hull/module/ammunition/fit content sufficient to build multiple materially different test fleets;
- replaceable tactical prototype visuals sufficient to inspect the combat model interactively.

No advanced AI should infer ability from hard-coded hull class names.

The representative Stage-17.5 content is **content-provisional**. It validates mechanics and may later be re-authored, replaced or explicitly accepted during Stage 22; temporary visuals are presentation-only and may be replaced without changing authoritative state.

---

## 7. Stage integration under the current roadmap

### Stage 14 — first playable loop

The original first-loop work established playable movement and useful seams. Any legacy direct-velocity behavior is a migration target for Stage 17.5 rather than a permanent physics rule.

### Stage 15 — fleets

Fleet orders, risk-aware routing and convoy behavior use shared movement semantics wherever already supported. Final weapon-aware tactical AI remains deferred.

### Stage 16 — construction/stations

Heavy construction cargo and logistics should ultimately pay the same mass/travel consequences.

### Stage 17 — player faction

Faction doctrine can author broad aggression/retreat/escort/risk preferences without granting physical bonuses.

### Stage 17.5 — Combat Depth / Ship Fitting Foundation

This is the explicit physical gate.

Required foundation:

```text
hull/module/material schema
→ central derived-ship calculator
→ thrust/reaction mass/power/thermal/FTL
→ sensors/tracks/EW
→ weapons/ammunition/PD
→ shields/armor/compartments/damage
→ shipyard/refit/repair seam
→ shared capability APIs
→ representative combat test content
→ tactical prototype visuals
→ deterministic multi-fleet acceptance
```

Canonical Stage-17.5I combat-content/visual gate: `docs/stage17_5i_combat_test_content_visual_acceptance.md`.

### Stage 18 — Resources / Industry / Infrastructure

Stage 18 does **not** add the major tactical-AI phase. It makes the physical ships economically manufacturable and supportable.

It connects fitted mass/equipment/consumables to:

- resource extraction;
- refining/materials;
- industrial components;
- ammunition and reaction-mass production;
- repair inputs;
- shipyard capabilities;
- replacement/salvage economics.

This ordering matters because Stage 19 warfare should already have real logistical endurance and replacement consequences.

### Stage 19 — Strategic Warfare / Advanced Combat Behavior

Major tactical/strategic combat-AI phase:

```text
19A consume Stage-17.5 capability model
19B weapon/track/range/retreat tactical AI
19C fleet doctrine / screen / escort / coordination
19D mobilization / war goals / fronts / blockade
19E target real Stage-18 logistics/industry
19F conflict → losses/rerouting/shortages/replacement consequences
```

AI optimizes against the intended long-term physical model rather than temporary placeholders.

### Stage 20 — Physical World Generation

Generated distances and topology are calibrated from representative fitted ships and real logistics:

- station → station;
- station → resource source;
- jump arrival → hub;
- inner → outer system;
- multi-hop trade/fleet routes.

No separate strategic distance system.

### Stage 21 — RPG / Living World

Commander/NPC personality may modify decision preferences above faction doctrine but never physical capabilities or knowledge.

### Stage 22 — Content / Balance Alpha

Large matrices tune movement, fitting, combat, industrial cost and fleet doctrine together.

Stage 22 also owns the content review of Stage-17.5 provisional ships/modules/fits: they must be re-authored/rebalanced to the accepted technology, industry, faction and visual paradigms or explicitly promoted after review. Stage-17.5 prototype identity is never automatic canon.

### Stage 23 — Polish / RC

Final control feel, HUD readability, production ship/projectile/VFX replacement, performance and regression hardening. No new foundational flight physics.

---

## 8. Acceptance scenarios

Mature baseline must prove:

1. **Light vs heavy acceleration:** lighter ship reaches requested velocity faster for comparable thrust-to-mass conditions.
2. **Cargo mass:** same freighter accelerates/brakes differently empty vs loaded.
3. **No instant stop:** velocity changes only within available thrust/mass envelope.
4. **Shared player/AI physics:** equivalent state produces equivalent limits.
5. **Braking distance:** speed/mass/thrust produce measurable stopping differences.
6. **Armor/equipment trade-off:** heavier fit pays movement cost through mass.
7. **Damage:** propulsion/thermal/power damage changes real movement capability.
8. **Save/load:** persistent movement/fit/consumable state continues deterministically.
9. **Fleet constraint:** escort planning respects slowest critical protected asset.
10. **Industrial coupling:** Stage-18 replacement/refit inputs correspond to actual fitted equipment rather than class cost multipliers.
11. **World coupling:** Stage-20 route ETA uses the same representative capability envelope.
12. **Multi-fleet combat:** materially different kinetic-line, missile-strike, high-mobility/beam, defensive/EW and balanced-control fleets can be assembled from ordinary production-valid definitions and compared through deterministic scenario matrices.
13. **Combat readability:** at least one interactive battle exposes ship orientation/scale, kinetic/beam/guided fire, interception, shield interaction, armor/penetration, subsystem damage and wreck/debris state through temporary replaceable tactical visuals.
14. **Content boundary:** changing/replacing Stage-17.5 prototype art or later Stage-22 content definitions does not require a second combat physics model.

---

## 9. Design constraints

1. Mass and thrust are authoritative gameplay data.
2. Cargo mass comes from real inventory content/state where practical.
3. Player and AI use the same local dynamics.
4. Movement remains deterministic and simulation-time driven.
5. Flight assist cannot bypass acceleration/braking limits.
6. Advanced tactical AI is gated by stable Stage-17.5 combat depth.
7. Civilian survival/risk AI is not blocked by that gate.
8. Ship differentiation emerges from fitted data, not class-name conditionals.
9. Strategic ETA and local motion must remain compatible.
10. Damage/fuel/power/heat modify the same capability model.
11. Stage-18 industry produces and maintains the same physical fitted state used by flight/combat.
12. Stage-20 geometry is calibrated against these capabilities rather than arbitrary map units.
13. Stage-17.5 Combat Test Content Pack must use production schemas/runtime, never test-only combat stats.
14. Stage-17.5 hull/module/ammunition/fit identities and prototype visuals remain content-provisional until explicit later-stage content review.
15. Rendering/VFX are projections of authoritative simulation state and cannot become a parallel combat authority.
