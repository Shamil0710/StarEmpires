# M22.8D — small-craft mission and shared command model

Status: **IMPLEMENTED CANDIDATE — exact-head CI and prerequisite M22.8C merge required for acceptance**  
Parent gate: `M22.8 — Carrier / Small-Craft Operations`

## Scope

M22.8D adds the reusable mission vocabulary and one common PLAYER/AI validation path for individual
persistent small craft. It does not add a second tactical engine, hidden targeting truth, virtual
wing pools or UI-owned mission state.

Required mission vocabulary is represented directly:

- CAP;
- QRA;
- interception;
- escort;
- anti-ship strike;
- reconnaissance / sensor extension;
- EW/support;
- return;
- recover;
- divert.

## Authority reuse

The D-layer composes existing authorities instead of replacing them:

- `SmallCraftRegistry` remains the individual physical identity/engineering owner from M22.8A;
- `SmallCraftHangarRegistry` remains the physical occupancy/readiness owner from M22.8B;
- `SmallCraftFlightDeckOperations` remains the launch/recovery/no-teleport owner from M22.8C;
- `FleetCommandState.OrderSource` is reused for the shared PLAYER/AI source vocabulary;
- `FactionActorObservationSnapshot.ObservationEvidence` is reused for actor-bounded provenance and
  freshness; the mission service has no `WorldSimulation` reference and cannot query hidden truth;
- `ProductionEngineeringRuntimeResolver.planDeltaV` performs current fitted reaction-mass,
  power/thermal and damage-aware endurance preflight using the ordinary Stage-17.5 engineering model;
- authored `ModuleFamily` values provide lawful weapon and sensor/EW capability checks without
  class-name bonuses.

## Command contract

`SmallCraftMissionCommandService.submit(...)` is the only acceptance path. PLAYER and AI differ
only by the persisted `OrderSource` value and receive identical validation.

A submission fails closed when:

- the issuing faction does not own the craft;
- the supplied target kind is invalid for the mission family;
- target evidence is stale or future-dated;
- the evidence context names a different actor-known target reference than the mission target;
- an embarked craft is not physically `READY`;
- a deployed craft is still present in bay occupancy;
- a command lacks a lawful command/datalink link;
- the physical craft-to-command-node distance exceeds the currently supplied command-link range;
- the fitted craft lacks a required authored weapon or sensor/EW module family, or that required subsystem is physically destroyed;
- the ordinary Stage-17.5 engineering runtime cannot satisfy the supplied physical mission delta-v;
- a second embarked active mission would double-commit the same craft.

Deployed retasking deterministically cancels the previous active mission and creates one new mission
identity. No physical craft or resource is created by this state transition.

## Physical lifecycle boundary

D does not treat a mission timer as movement.

For an embarked deployment mission:

```text
READY
→ D accepts mission
→ C queues/executes launch handling
→ LAUNCH_QUEUED
→ E later materializes the same SmallCraftId in local flight
→ package-level physical handoff confirms C launch
→ bay occupancy released
→ mission ACTIVE
```

Recovery likewise remains physical:

```text
ACTIVE / RETURNING
→ E later brings the same craft to a recovery gate
→ D/E physical seam offers recovery to C
→ RECOVERY_PENDING
→ C performs finite recovery cycle
→ craft becomes SERVICING in the real bay
→ mission COMPLETE
```

Command/UI code cannot call the package-level physical handoff methods directly and cannot teleport a
craft between embarked and deployed state.

## Persistence boundary

`SmallCraftMissionState` is immutable, deterministic and allocator-safe, including the invariant of
at most one active mission per `SmallCraftId`. This D slice deliberately does **not** advance the
M22.8 campaign file format yet because live Stage-19 tactical materialization/commit-back is still
owned by E/H and exhaustive parked/deployed/recovery checkpoint coverage is explicitly owned by
M22.8M.

Until those integration slices bind mission state into `Stage228CampaignAuthority`, D is a
production command contract rather than a second independently persisted campaign authority. M22.8M
must persist this exact state rather than reconstructing missions from UI or tactical entities.

## Acceptance evidence

Automated coverage proves:

- PLAYER and AI use the same ready-craft launch validation path;
- stale actor knowledge and evidence for a different target are rejected before flight-deck mutation;
- both embarked launch commands and deployed retasks require lawful datalink availability/range;
- infeasible physical delta-v and physically destroyed required subsystems are rejected before launch mutation;
- target-family mismatch fails closed;
- completed launch handling retains bay occupancy until explicit physical handoff;
- deployed recovery remains outside the bay until C starts physical recovery;
- mission completion requires real `SERVICING` occupancy after recovery;
- deployed retask supersedes the prior mission without duplicating craft identity.

## Deferred intentionally

- M22.8E — Stage-19 local tactical entity materialization, track-bounded execution and combat
  commit-back;
- M22.8F — carrier-group doctrine/AI scheduling;
- M22.8G — live Stage-18 manufacture, supply and replacement;
- M22.8H — Stage-21 strategic operation composition;
- M22.8I — player-facing carrier UI;
- M22.8J — production small-craft hulls/fits/assets;
- M22.8M — exhaustive mission/save migration hardening and campaign-envelope integration.
