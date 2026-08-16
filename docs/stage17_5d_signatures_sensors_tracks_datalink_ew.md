# Stage 17.5D — Signatures, Sensors, Tracks, Datalink and EW

> **Status: COMPLETE IMPLEMENTATION SLICE — awaiting exact-head merge gate**  
> Date: 2026-08-16  
> Base green `main`: `33313ef41bf5e020b095db2a242fc18e684d4643`  
> Runtime/content verification head: `a05decb9ce2511655e53ac0174bf1f5c8f1c06f7`  
> CI: run `#2462` / Actions `31966376332` — **SUCCESS**.

## 1. Purpose

Stage 17.5D introduces the common physical information model used by player and AI ships:

```text
physical target state
→ channelized signature
→ propagation / receiver noise / interference
→ SensorMeasurement
→ covariance + geometry + freshness
→ TrackState
→ DETECTED / CLASSIFIED / TRACKED / FIRE_CONTROL
```

The runtime never accepts player ownership, AI ownership, faction identity, doctrine class or a scalar stealth bonus as an accuracy input.

There is no authored universal `sensorRange` wall. Observation quality emerges from physical source/reflection terms, geometry, aperture, receiver noise, interference, processing state and information thresholds.

---

## 2. Channelized signatures

`SignatureState` is a physical multi-channel state rather than one stealth number.

Current channels/terms include:

- thermal radiant power;
- engine-plume radiant power;
- radar cross section;
- reflected optical power;
- active radio emissions;
- deliberate jammer emissions.

Static fitted signature contributions come from the same Stage-17.5A/B engineering content and `DerivedShipState` used by other ship capabilities.

The production sensor demonstrator no longer carries a static `active_radar_w` contribution merely because radar hardware is installed. Active radar emission is generated only while active observation actually executes.

---

## 3. Physical sensor model

`SensorDefinition` describes a sensing mode through physical/measurement parameters including:

- observed channel;
- aperture;
- receiver noise;
- detection/classification/track/fire-control SNR thresholds;
- bearing error floor;
- ranging error fraction for ranging modes;
- active transmitter power/gain where applicable;
- incremental active-mode electrical demand and waste heat;
- ECCM processing gain, power demand and waste heat.

Passive channels use inverse-square source/reflection propagation.

Active monostatic radar derives target return from transmitter power/gain, target radar cross section, receiving aperture and range-dependent propagation. It provides direct range only because its measurement mode is physically range-capable.

Information thresholds are SNR requirements, not distance limits.

---

## 4. Bearing-only measurements and covariance

`SensorMeasurement` represents unknown range explicitly:

```text
rangeM = null
rangeVarianceM2 = null
```

for bearing-only evidence.

A passive detection or classification therefore cannot manufacture exact target range.

`TrackCovariance` keeps explicit bearing/range/position uncertainty. The initial representation is intentionally compact rather than pretending to be a full Kalman matrix; its boundary can later be replaced by a richer estimator without returning to scalar accuracy bonuses.

---

## 5. Distributed sensing and track fusion

`ShipSensorRuntime` fuses physical measurements rather than granting a fleet-wide recon aura.

A position solution can arise from:

- direct range-capable measurements; or
- deterministic multi-observer bearing triangulation with non-degenerate geometry.

Independent range-capable observers reduce position covariance through the common fusion model.

Datalink transport contributes measurement age and transport covariance; it does not improve data merely because observers belong to the same faction.

---

## 6. Information states and stale-track degradation

The shared progression is:

```text
DETECTED
→ CLASSIFIED
→ TRACKED
→ FIRE_CONTROL
```

The strongest state depends on actual measurement evidence, solved geometry, covariance and freshness.

`ageTrack(...)` increases uncertainty through explicit process-noise terms. A previously good fire-control solution therefore degrades as evidence becomes stale instead of remaining exact indefinitely.

---

## 7. Electronic warfare

`ElectronicWarfareState` uses explicit physical/measurable mechanisms:

### Noise jamming

A jammer has:

- emitter identity;
- position;
- radiated power;
- gain;
- waveform-overlap fraction.

Its interference reaches a receiver through propagation geometry and receiving aperture.

### ECCM

ECCM reduces effective interference through an explicit processing gain and has explicit incremental electrical and thermal cost.

### Deception

Deception is represented as explicit alternate measurement hypotheses with source identity and apparent bearing/range biases.

The runtime does **not** roll one global `decoyChance` and does not silently choose a false target on behalf of downstream tracking/AI.

---

## 8. Fitted engineering integration

`ShipSensorEngineeringAdapter` consumes the central `DerivedShipState` rather than a parallel sensor-stat catalog.

For each fitted `SENSOR_EW_FIRE_CONTROL` module it reads namespaced physical modes from the module's existing `capabilityParameters` and binds them to the actual physical mount.

The production `module.sensor_array_escort_v1` now authors a passive thermal mode and active radar mode in the same engineering schema.

Active radar physical closure in the current demonstrator is:

```text
radiated transmitter power = 45 MW
transmit gain = 10
incremental electrical demand = 60 MW
incremental waste heat = 15 MW
```

With ECCM enabled:

```text
additional ECCM electrical demand = 5 MW
additional ECCM waste heat = 2.5 MW
```

These are demonstrator capability values, not final balance constants or map-range values.

---

## 9. Common player/AI observation seam

`ShipObservationService` is an ownership-neutral two-phase boundary:

```text
fitted sensor + runtime mode
→ planOperation(...)
→ OperationPlan(power, heat)
→ engineering grant
→ execute(...)
→ measurement / hypotheses / operational RF emission
```

A denied or insufficient engineering grant produces:

- no observation execution;
- no measurement;
- no deceptive hypotheses;
- no active RF emission.

Therefore UI or AI code is not given a direct authoritative “turn radar on and receive contact” mutation path.

### Explicit Stage-17.5H handoff

Stage 17.5D defines and tests the grant boundary, but does **not** claim that `EngineeringGrant` issuance is already automatically committed into every live `ShipEngineeringRuntime.advance(...)` tick.

Stage 17.5H must bind operation-plan power/heat requests into the authoritative common engineering operating command/state before exposing final player/AI capability APIs or UI controls. This is an explicit integration task, not hidden completed functionality.

---

## 10. Datalink and local knowledge

`SensorKnowledgeComponent` owns local-system received information for one observer/network node:

- current tracks;
- actually received measurements;
- physically transmitted measurements still waiting for datalink delivery.

`SensorKnowledgeRuntime`:

- receives local measurements;
- transmits existing measurements rather than truth state;
- applies explicit delivery latency;
- refuses already-too-old evidence;
- exposes pending measurements only after delivery time;
- prunes stale measurement history;
- recomputes or ages tracks through the same `ShipSensorRuntime` used locally.

Two independent knowledge nodes given identical physical evidence produce identical `TrackState`, regardless of whether a caller labels one “player” and another “AI”.

---

## 11. Identity and persistence boundary

Current Stage-17.5D target/observer identity values are local-system entity identities.

Therefore local track memory must not be carried into another star system as if the same numeric target IDs remained globally meaningful.

`SensorKnowledgeComponent` supplies an explicit deterministic snapshotable runtime boundary and `clearLocalKnowledge()` for a system-identity-domain transition.

Stage 17.5D deliberately does **not** raise `GameState` schema again or add binary track persistence to `EntityState/GameStateCodec`.

Final binary persistence/migration of knowledge that is actually required by the live capability API belongs to Stage 17.5H. Legacy saves must receive empty/neutral knowledge rather than fabricated contacts.

If future strategic intelligence must survive across systems, it requires a separate world-stable contact/intelligence identity. Reusing a local `EntityId` as a galaxy-wide identity is forbidden.

---

## 12. Acceptance evidence

Automated acceptance covers the canonical Stage-17.5D DoD:

| Acceptance | Result |
| --- | --- |
| Bearing-only contact has no exact range | PASS |
| Two passive observers triangulate only through real geometry | PASS |
| Distributed ranging observers reduce position covariance | PASS |
| Active radar provides range and produces observable RF emission | PASS |
| Active radar cannot execute through common service without an engineering grant | PASS |
| Active/ECCM operating power and heat are explicit | PASS |
| Noise jammer can suppress a marginal observation | PASS |
| ECCM can recover information at explicit power cost | PASS |
| Deception remains an explicit alternate hypothesis | PASS |
| Datalink evidence is unavailable before physical delivery latency | PASS |
| Shared measurements improve a solution only after delivery/fusion | PASS |
| Stale covariance grows and information state degrades | PASS |
| Identical player/AI evidence produces identical `TrackState` | PASS |
| Production engineering JSON projects fitted sensor modes without static active-radar emission | PASS |

Verification checkpoints:

- core sensor/EW runtime: `1a3865fa9aae8d0ca2dd6a10c829d0b758c12769`, CI `#2449` — SUCCESS;
- fitted engineering adapter: `adafc4f139bf4eb4a5f8bbfc6dd3aeb74414da72`, CI `#2455` — SUCCESS;
- datalink/shared knowledge runtime: `464242ae00a221faae13b95089d0749fccbb5991`, CI `#2459` — SUCCESS;
- common observation operating gate: `c988f3ef73036df58994c4f321eb1ade083906d7`, CI `#2461` — SUCCESS;
- consolidated player/AI/DoD acceptance: `a05decb9ce2511655e53ac0174bf1f5c8f1c06f7`, CI `#2462` — SUCCESS.

---

## 13. Explicit non-goals / later handoffs

Stage 17.5D deliberately does not implement:

- weapon hit or guidance logic — Stage 17.5E;
- compartment/subsystem damage events that author aperture/processing degradation — Stage 17.5F;
- shipyard/refit/repair lifecycle — Stage 17.5G;
- final live capability API/UI wiring, engineering-grant commit and binary sensor-knowledge persistence — Stage 17.5H;
- advanced tactical sensor doctrine/formation behavior — Stage 19;
- final system-distance/sensor cadence calibration — Stage 20.

`SensorRuntimeState.apertureFraction` and `processingFraction` are already explicit physical degradation seams for Stage 17.5F. D does not invent a generic damaged-sensor accuracy multiplier.

---

## 14. Completion decision

The Stage-17.5D definition of done is satisfied at the production information-model boundary:

- signatures are channelized physical states, not scalar stealth;
- passive bearing-only evidence cannot manufacture range;
- distributed sensing improves solutions through geometry/covariance;
- active radar ranges but emits;
- ECM/ECCM/deception are explicit interference/processing/hypothesis mechanics;
- stale tracks degrade;
- datalink sharing has latency/freshness/covariance consequences;
- fitted sensor modes derive from the same engineering content as the rest of the ship;
- player and AI use the same measurement/track model;
- no class-name bonus, universal sensor range, player-only information path or random decoy-chance shortcut was introduced.

**Next implementation slice after merge gate: Stage 17.5E — kinetic / beam / guided weapons / PD / ammunition.**
