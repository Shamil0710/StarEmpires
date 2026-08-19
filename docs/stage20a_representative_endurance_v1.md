# Stage 20A Closure — Representative Endurance / Sustained-Thrust v1

**Status:** ACCEPTED — implementation head passed exact-head Java-17 CI; final status-only merge gate pending  
**Parent:** Stage 20A representative-ship scale calibration / closure remediation  
**Date:** 2026-08-19

## 1. Purpose

Close `REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE` for the nine-role Stage-20 representative fleet without silently converting cargo mass, old benchmark values or production module data into unsupported autonomy rules.

The Stage-20 plan requires every representative to expose, among other quantities:

```text
sustained vs max-thrust consequences
stores/endurance
```

The accepted propulsion v2 profile already owns each representative's current:

- departure/wet mass;
- reaction mass;
- maximum/reference thrust;
- effective exhaust velocity;
- max-thrust acceleration;
- max-thrust mass flow;
- delta-v and route consequences.

This slice adds only the two missing calibration inputs:

```text
sustainedThrustN
missionStoresEnduranceS
```

and derives their consequences through the same physical equations.

## 2. Authority boundary

This is a Stage-20 **calibration policy**, not production ship content.

Every endurance reference remains:

```text
PROVISIONAL_ACCEPTED_REFERENCE
stage22ReviewRequired = true
```

The profile does not claim that:

- the old v0.2 benchmark is production engineering;
- a nominal mission-endurance duration is equal to crew-survival time;
- cargo mass can be divided by a hidden consumption coefficient to create autonomy;
- max thrust can be sustained indefinitely;
- sustained thrust is a class bonus;
- one endurance duration applies to every fit, damage state, crew state or mission load.

Production fits may later replace these values through the accepted common engineering/logistics contract.

## 3. Source audit

### 3.1 Sustained thrust

`docs/benchmarks/ship_reference_designs_v0_2.json` already authors `sustainedForwardThrustN` for seven roles represented in the current Stage-20 matrix:

```text
TORPEDO_CORVETTE       0.6 MN
ESCORT_DESTROYER      3.3 MN
CRUISER               7.0 MN
BATTLESHIP           33.0 MN
CARRIER_AVIATION     25.0 MN
BULK_FREIGHTER        4.0 MN
FLEET_TANKER          8.0 MN
```

That file is historically `authoring-benchmark-only`. This slice therefore explicitly accepts those thrust values for Stage-20 calibration only; it does not promote the old hulls/fits to production.

No historical physical sustained-thrust value was found for:

```text
EARLY_CIVILIAN_FREIGHTER
MINING_SHIP
```

Those two values are bounded authoring decisions in this document.

### 3.2 Mission stores / autonomy

The repository contains role-language such as limited corvette endurance, long-range cruiser stores, carrier aviation reserves and logistics support, but it does **not** contain an accepted numerical crew-consumption/life-support equation capable of deriving exact mission days from stored kilograms.

Therefore the current `missionStoresEnduranceS` values are explicit operational-policy seeds. They mean:

> nominal time the representative calibration load case is intended to operate away from replenishment under routine mission conditions before stores/logistics become the planning constraint.

They do not mean:

- emergency survival duration;
- combat ammunition endurance;
- maximum reactor lifetime;
- maintenance interval;
- reaction-mass endurance;
- a promise that the generated world will force replenishment exactly at that time.

## 4. Bounded sustained-thrust authoring

The two new sustained-thrust seeds are:

```text
EARLY_CIVILIAN_FREIGHTER
max        = 5.6 MN
sustained  = 1.8 MN
ratio      ≈ 0.3214

MINING_SHIP
max        = 7.0 MN
sustained  = 2.1 MN
ratio      = 0.3000
```

These ratios stay inside the existing civilian/logistics neighborhood represented by:

```text
bulk freighter 4 / 12 MN  = 0.3333
tanker         8 / 25 MN  = 0.3200
```

No new drive technology or hidden acceleration multiplier is introduced.

## 5. Mission-endurance policy seeds

Current nominal operational stores endurance:

```text
TORPEDO_CORVETTE          7 days
EARLY_CIVILIAN_FREIGHTER 14 days
ESCORT_DESTROYER         30 days
BULK_FREIGHTER_LOADED    45 days
MINING_SHIP              45 days
FLEET_TANKER_LOADED      60 days
CRUISER                   90 days
BATTLESHIP               120 days
CARRIER_AVIATION_GROUP   120 days
```

Rationale is role ordering rather than an invented consumption equation:

- corvette remains a short-endurance patrol/attack craft;
- early civilian freighter supports short regional commerce without capital-logistics autonomy;
- escort can remain with convoys/fleets for multi-week operations;
- bulk freighter/miner can complete extended industrial/logistics cycles;
- tanker is deliberately more autonomous than the ordinary bulk carrier it supports;
- cruiser is the independent long-range combatant;
- battleship and fleet carrier require capital-scale stores/support and receive the longest current nominal calibration endurance.

All values remain Stage-22-review-required balance/content inputs.

## 6. Derived physical consequences

For each representative, the runtime profile combines the accepted Stage-20 propulsion envelope with the endurance policy and derives:

```text
sustainedToMaxThrustRatio
sustainedAccelerationMps2
maxMassFlowKgPerS
sustainedMassFlowKgPerS
fullReactionMassBurnAtMaxS
fullReactionMassBurnAtSustainedS
missionStoresEnduranceS
```

Equations remain:

```text
acceleration = thrust / current representative wet mass
massFlow     = thrust / effective exhaust velocity
burn time    = represented reaction mass / mass flow
```

The calculation therefore automatically uses the **current** representative propulsion state.

Important example: the production escort currently uses its accepted production physical envelope, including its current effective exhaust velocity and represented reaction-mass load. The 3.3 MN sustained-thrust seed is combined with those current production values. The old v0.2 escort's exhaust/reaction-mass values are not copied back into production.

## 7. Expected representative consequences

Approximate current consequences include:

```text
TORPEDO_CORVETTE
max acceleration       ~1.0280 m/s²
sustained acceleration ~0.2804 m/s²
full RM burn @ max      ~7.58 h
full RM burn @ sustain ~27.78 h

EARLY_CIVILIAN_FREIGHTER
max acceleration        0.2000 m/s²
sustained acceleration ~0.0643 m/s²
full RM burn @ max     ~31.75 h
full RM burn @ sustain ~98.77 h

MINING_SHIP
max acceleration        0.1250 m/s²
sustained acceleration  0.0375 m/s²
full RM burn @ max     ~44.44 h
full RM burn @ sustain ~148.15 h

CRUISER
max acceleration       ~0.3984 m/s²
sustained acceleration ~0.0996 m/s²
full RM burn @ max     ~24.80 h
full RM burn @ sustain ~99.21 h
```

These burn durations are propulsion consequences, not mission-endurance replacements.

## 8. Machine-readable implementation

Resource:

```text
src/main/resources/data/calibration/stage20-representative-endurance-v1.json
```

Profile version:

```text
stage20a.representative-endurance.v1
```

Each entry stores separate provenance for:

```text
sustainedThrustSourceEvidenceId
missionStoresSourceEvidenceId
```

so later code cannot confuse historical thrust evidence with newly authored mission policy.

## 9. Readiness impact

Exactly one blocker changes:

```text
REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE
BLOCKING_STAGE20B_ENTRY
→ SATISFIED
```

Expected total Stage-20A blockers:

```text
13 → 12
```

No route/topology/station/sensor/weapon/PD/materialization blocker is removed by this slice.

## 10. Regression requirements

Tests prove:

- all nine current Stage-20 propulsion representatives have exactly one endurance entry;
- every entry has explicit sustained-thrust and mission-stores provenance;
- sustained thrust is positive and never exceeds current max thrust;
- derived sustained acceleration is lower than or equal to max acceleration;
- current max mass flow reproduces the existing propulsion envelope;
- sustained mass flow follows the current representative exhaust velocity;
- represented reaction mass lasts longer at sustained thrust than max thrust whenever sustained < max;
- mission endurance is positive but independent of cargo/reaction-mass equations;
- current production escort retains production max-thrust/mass/exhaust authority while only its sustained-thrust policy remains provisional;
- readiness removes exactly one blocker, producing 12 remaining Stage-20B blockers.

## 11. Acceptance evidence

Implementation head `360aab855256bb99ad4a04d826599182fdb6b209` passed the complete Java-17 CI gate, including the full test, coverage, Javadoc and packaging verification. The readiness acceptance test requires exactly twelve remaining Stage-20B blockers.

## 12. Deferred work

This slice does not define:

- per-person food/water/oxygen consumption;
- closed-loop life-support efficiency;
- maintenance-spares depletion physics;
- ammunition endurance;
- production civilian/mining/cruiser/carrier fits;
- technology/faction-specific endurance modifiers;
- final Stage-22 balance.

Those systems may later replace provisional mission-duration seeds while preserving the accepted Stage-20 physical/time interfaces.
