# M22.8H — Stage-21 strategic carrier operations integration

Status: implementation slice.  
Parent contract: `docs/m22_8_carrier_small_craft_operations.md`.

## Purpose

M22.8H connects the already accepted individual small-craft authority to the existing Stage-21
strategic command/operation layer. It does **not** introduce a carrier combat score, a virtual wing
pool, a second strategic operation state, or a second tactical engine.

The physical truth remains:

- ordinary strategic ships/carriers/escorts are existing `FleetId` assets;
- individual embarked/deployed craft are existing `SmallCraftId` assets;
- bay occupancy is owned by `SmallCraftHangarRegistry`;
- launch/recovery timing is owned by `SmallCraftFlightDeckOperations`;
- individual mission lifecycle is owned by `SmallCraftMissionState`;
- exact local combat is owned by Stage 19 through `SmallCraftTacticalEncounterService`;
- production/delivery/turnaround is owned by M22.8G and Stage 18;
- money remains ordinary faction-treasury / wallet money.

## Strategic readiness projection

`CarrierWingStrategicReadinessService` is a read-only projection. An explicit
`CarrierWingAssignment` associates one ordinary carrier `FleetId` with the individual craft
currently expected to form its wing. The association stores no replacement physical state.

For every surviving craft the projection derives:

- structural readiness from current compartment and installed-module integrity;
- ammunition readiness from physical AMMUNITION interface load versus authored capacity;
- propellant readiness from physical REACTION_MASS interface load versus authored capacity;
- maintenance readiness from current service age versus authored service interval;
- availability from the actual lifecycle: READY/LAUNCHING embarked craft or ACTIVE deployed craft.

A missing previously-associated `SmallCraftId` contributes zero readiness and is retained as an
observed loss in the projection. Its identity is never recreated and the allocator is never rewound.
SERVICING/PARKED/RECOVERING/RETURNING assets do not become free combat availability.

The derived wing dimensions conservatively bound the matching ordinary carrier entry in a copied
`FleetForceRegistry`. The original fleet entry, engineering payload, craft state and bay state are
not mutated. Stage-21D/E therefore consumes the same readiness API it already owns.

## Strategic operation admission and continuation

`CarrierStrategicOperationService` is a facade over `StrategicOperationService`.

M22.8H explicitly admits carrier groups for the current contract families:

- ESCORT;
- INTERCEPT → INTERCEPTION;
- RAID;
- GUARD → DEFENSE.

Admission requires:

1. an already accepted active Stage-21D order;
2. the declared carrier to be an ordinary member of that command group;
3. a current individual-wing readiness projection for that carrier;
4. the projected carrier group to meet the existing Stage-21E `SupplyPolicy`.

The created operation remains an ordinary `StrategicOperationState.OperationState` with ordinary
`FleetId` participants. Subsequent wing losses, depletion or servicing are projected again and fed
to the existing `reviewSupplyAndReadiness` path. Withdrawal/failure therefore remains the accepted
Stage-21E decision, not a carrier-only shortcut.

BLOCKADE and INVASION are intentionally not added to the H carrier admission set merely because a
carrier exists; they remain ordinary Stage-21 operation families and can be extended only by an
explicit later contract.

## Exact tactical handoff

`CarrierStrategicTacticalEncounterService` validates the strategic boundary and delegates exact
combat to M22.8E / Stage 19.

It proves that:

- the carrier is an ordinary participant of the active in-area Stage-21E operation;
- the supplied wing association belongs to that carrier;
- every deployed small-craft tactical participant is an admitted individual `SmallCraftId`.

It does not commit ordinary carrier/escort/target engineering itself. Those participants are passed
as detached `ExternalCombatant` payloads and returned as detached outcomes to the pre-existing
world authority. Individual craft outcomes use the already accepted M22.8E semantics: survivors
commit consumables/damage to the same identity; catastrophic losses permanently remove that
identity and fail its mission. Unrelated hangar occupancy is not touched.

## Generated-world combined commit-back

`GeneratedWorldCarrierEncounterService` closes the production generated-world seam that remains
outside the detached M22.8E API.

For one `CONTACT_CONFIRMED` Stage-21E operation it:

- revalidates every ordinary operation `FleetId` and the confirmed target against the live generated
  world, including exact system co-location, faction allegiance, persisted engineering and Stage-20
  physical kinematics;
- converts ordinary fleets and explicitly deployed `SmallCraftId` participants into one shared
  encounter-local coordinate frame;
- executes one M22.8E / Stage-19 tactical exchange rather than resolving the wing and surface ships
  in separate combat systems;
- commits surviving ordinary-fleet engineering and kinematics back to the same ordinary entities;
- removes catastrophically destroyed ordinary fleets through the existing world destruction and
  Stage-20 physical-sidecar authorities;
- retains the Stage-21 operation's encounter reference as synchronously resolved, so save/load does
  not depend on hidden in-memory battle state.

If the declared carrier host itself is physically destroyed, every craft still physically embarked
in that exact host is removed with the host and any active mission for those craft is marked FAILED.
This prevents orphaned bay occupancy or free survival of craft that never completed a physical
launch handoff. Deployed craft are not included in that host-loss cascade; their outcome remains the
exact Stage-19 result.

## Post-battle recovery and replacement

`CarrierPostBattleRecoveryService` composes three existing authorities rather than creating a
replacement economy:

1. `WorldSimulation.transferFromFactionTreasury` moves already-existing faction money into a
   caller-owned procurement wallet and records the ordinary money transfer;
2. `SmallCraftPhysicalLogisticsService.settleBuild` requires finite Stage-18 hull inputs, finished
   modules and yard work before a fresh `SmallCraftId` can exist;
3. `SmallCraftPhysicalLogisticsService.confirmDelivery` requires ordinary physical-arrival evidence
   before the produced craft can enter a carrier/station bay, and arrival enters SERVICING rather
   than READY.

Funding is not material creation. If the physical build cannot settle, transferred money remains
conserved in the procurement wallet for later ordinary procurement; no craft, materials or yard work
are synthesized.

## Acceptance coverage

The H tests cover the following failure-closed boundaries:

- individual loss lowers strategic readiness and cannot reuse the destroyed ID;
- servicing lowers availability without mutating craft state;
- ESCORT / INTERCEPT / RAID / GUARD are admitted through ordinary Stage-21D/E;
- an in-progress operation receives the ordinary Stage-21 withdrawal decision after a physical wing
  loss crosses its existing readiness policy;
- unsupported carrier operation families cannot bypass Stage-21;
- tactical participants outside the explicit carrier-wing association are rejected before Stage 19;
- exact tactical loss commits to the individual craft registry while unrelated bay state and detached
  carrier engineering remain untouched;
- a combined generated-world encounter commits ordinary FleetId survivor/destruction consequences and
  individual craft consequences from one Stage-19 result, with no duplicate tactical authority;
- physical carrier destruction clears still-embarked craft on that exact host instead of leaving
  orphan occupancy or implicitly surviving hangar contents;
- denied treasury funding consumes no Stage-18 stock/work and allocates no replacement;
- successful production funding uses the ordinary world treasury, finite Stage-18 settlement, a fresh
  persistent craft identity and physical delivery before SERVICING occupancy.

## Deliberate non-goals

M22.8H does not add UI, production small-craft content/art, final balance tuning, scale optimization,
or the final M22.8 save-envelope expansion. Those remain M22.8I–M respectively. H also does not seed
free small craft into new campaigns: production content and bootstrap policy remain later slices.
