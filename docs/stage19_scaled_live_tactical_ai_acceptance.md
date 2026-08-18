# Stage 19 — Scaled Live Tactical AI Acceptance Contract

> Status: **ACCEPTED AND SATISFIED — STAGE 19 COMPLETE**  
> Completion evidence: `docs/stage19i_exit_evidence_matrix.md`.  
> The requirements below remain the canonical Stage-19 acceptance contract and regression boundary.

## 1. Purpose

After tactical AI is introduced, it is not sufficient to validate it only through unit tests, 1v1 engagements, scripted playback, or aggregate outcome tables.

Stage 19 must include a **scaled live tactical simulation acceptance gate** in which multiple AI-controlled ships fight through the same authoritative combat runtime used by the game.

The live viewer is an observer/controller of simulation time only. It must not own movement, target selection, weapon resolution, damage, ammunition, sensors, EW, formations, or AI decisions.

## 2. Authority boundary

Required chain:

```text
persistent / scenario physical state
→ production tactical AI intent
→ production information model / TrackState
→ production flight dynamics
→ production engineering power / heat / reaction-mass budgets
→ production fire control
→ physical kinetic / beam / guided execution
→ production PD / interceptor / EW / decoy behavior
→ shields / armor / compartments / subsystem damage
→ changed capability and AI decisions
→ next fixed simulation tick
→ read-only live projection
```

Forbidden shortcuts:

- viewer-only movement or targeting;
- omniscient tactical AI bypassing sensor/track state;
- doctrine/class numeric combat bonuses;
- virtual ammunition, fuel, reaction mass, interceptors, shield energy, repair or heat sinks;
- separate simplified damage/collision rules for large battles;
- replacing an exact local battle with an aggregate roll merely because more ships are present;
- render FPS changing authoritative simulation results.

## 3. Mandatory scale ladder

Stage-19 acceptance must exercise the same AI/runtime at increasing scale rather than creating separate small- and large-battle implementations.

Minimum representative ladder:

1. **1v1 regression** — tactical decision correctness and deterministic single-step inspection;
2. **4v4 squadron** — target allocation, maneuver conflict, formation behavior and local coordination;
3. **8v8 fleet engagement** — multiple simultaneous tracks, weapon envelopes, defensive resources and damaged-unit behavior;
4. **scaled fleet scenario with at least 32 combatant ships total** — e.g. 16v16 or an equivalent asymmetric fleet composition;
5. **saturation scenario** — enough guided/kinetic/interceptor/decoy bodies to exercise dense ordnance and finite layered defense concurrently.

The exact hull mix remains content-provisional until Stage 22, but the scale gate itself is mandatory.

## 4. Tactical behavior that must be observable live

The scaled viewer must make it possible to inspect AI decisions including, where applicable:

- acceleration, braking and course changes;
- range control against different weapon envelopes;
- pursuit and disengagement;
- formation keeping and formation break/recovery;
- target selection and target reassignment;
- fire discipline and finite-ammunition consequences;
- missile salvo timing;
- PD/interceptor allocation under saturation;
- EW/ECCM/decoy reactions based on available information;
- behavior under degraded tracks or lost datalink;
- reactions to shield collapse and subsystem/compartment damage;
- damaged or ammunition-depleted ships changing behavior instead of fighting as pristine units;
- coordination without collision-like command oscillation or perpetual indecision.

Doctrine may alter preferences and decision policy, but never the physical rules or hidden performance multipliers.

## 5. Required scenario variants

At minimum the Stage-19 scaled matrix must include:

- materially different fleet doctrines;
- mixed hull roles rather than only homogeneous mirror fleets;
- full and partial ammunition states;
- fresh and pre-damaged ships;
- cold and thermally stressed starts;
- sensor-rich and degraded-information conditions;
- compact and dispersed formations;
- protected and exposed support/logistics elements where the scenario includes them;
- at least one retreat/disengagement objective rather than every battle being annihilation-only.

Stage 18 industrial costs may additionally support comparable-cost fleet cases once a real comparable cost basis exists.

## 6. AI correctness gates

The tactical AI cannot be considered accepted if scaled scenarios reveal systemic pathologies such as:

- repeated left/right or accelerate/brake oscillation without tactical cause;
- fleets permanently deadlocking around formation goals;
- all ships selecting the same low-value target when doctrine/geometry would not justify it;
- firing weapons without valid tracks or fire-control state;
- ignoring ammunition, heat, power, reaction mass or damaged capability;
- inability to retreat when retreat is an authored objective and physically possible;
- uncontrolled order churn every tick;
- friendly units becoming permanently stuck because local maneuver coordination cannot resolve conflicts;
- AI outcomes depending on render frame rate.

These are acceptance failures, not cosmetic issues.

## 7. Determinism and observability

For a fixed content fingerprint, seed, initial state and command policy:

```text
same scenario
+ same fixed tick schedule
→ same authoritative state fingerprint sequence
→ same final physical outcome
```

Required tooling:

- pause/resume;
- exact single-tick stepping;
- deterministic reset;
- simulation-speed controls that only change the number of fixed ticks processed per wall-clock second;
- debug overlays for tracks, maneuver intent, target assignment, formation state, weapons, ammunition, power/heat, damage and AI high-level state;
- headless execution of the same scenario used by the live viewer.

The viewer must remain read-only with respect to combat authority.

## 8. Performance / scalability gate

Stage 19 must profile the scaled scenarios rather than assuming that a working 1v1 implementation scales.

Record at minimum:

- active ships;
- active projectile/guided/interceptor/decoy bodies;
- authoritative simulation ticks per real second;
- mean and high-percentile tick duration;
- sensor/track workload;
- tactical-AI decision workload;
- ordnance/defense workload;
- memory growth over a sustained engagement.

Performance optimization may use bounded scheduling/cadence where architecture permits it, but must not silently change exact local combat outcomes, give different rules to player/AI, or turn visible fleet combat into an unrelated aggregate model.

Final real-time performance thresholds should be calibrated from representative hardware and profiling evidence rather than invented in advance. Failure to maintain usable live simulation at the required Stage-19 scale is a Stage-19 scalability issue to resolve before completion.

## 9. Stage-19 exit requirement

Stage 19 cannot be marked COMPLETE until all of the following are true simultaneously:

- production tactical AI drives real ship movement and combat decisions;
- AI consumes the same sensor/track and physical capability state as the player-facing simulation;
- 1v1, 4v4, 8v8 and at least one >=32-combatant exact local scenario run deterministically;
- a dense ordnance / layered-defense scenario runs through real finite resources;
- scaled live viewer displays those battles while they are being calculated, not as pre-recorded playback;
- headless and live execution share the same authoritative scenario/runtime;
- major maneuver/targeting/formation oscillations and deadlocks are absent or explicitly bounded by tested fallback behavior;
- damage, ammunition, heat, power and reaction-mass state materially affect AI decisions;
- measured performance and memory behavior are recorded and acceptable for continuing to Stage 20 spatial calibration;
- no hidden large-battle bonuses, free resources, omniscience or viewer-owned combat logic are introduced.

**Exit decision:** all rows above have direct production evidence in `docs/stage19i_exit_evidence_matrix.md`. PR #209 passed the exact-head Java-17 merge gate and was merged without moving its validated head. Stage 19 is COMPLETE.

This scaled live gate remains a **mandatory Stage-19 regression artifact**, not optional presentation polish.

## 10. Relationship to later stages

- **Stage 18** supplies the real industrial/logistical cost and replenishment basis consumed by warfare.
- **Stage 19** owns tactical/strategic combat behavior and this scaled live AI validation.
- **Stage 20** now calibrates the physical generated-space geometry around proven Stage-19 maneuver/engagement behavior.
- **Stage 22** re-authors/rebalances provisional combat content and repeats representative fleet-scale soak/balance cases with production content.
- **Stage 23** may replace prototype visuals and optimize presentation, but may not replace the authoritative tactical simulation model.
