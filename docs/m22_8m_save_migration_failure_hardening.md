# M22.8M — Save / Load, Migration and Failure Hardening

Status: **IMPLEMENTED — exact-head CI / merge gate pending**  
Parent contract: `docs/m22_8_carrier_small_craft_operations.md`  
Scope boundary: M22.8M only. M22.8N final integrated causal soak remains mandatory.

## 1. Purpose

M22.8M closes the persistence gap left after M22.8A–L by composing the already-authoritative
carrier/small-craft state into one generated-campaign checkpoint without creating a second simulation
authority or granting replacement assets/resources during restore.

The current campaign envelope is `m22.8.generated-campaign.v4`. It embeds the accepted Stage-21I
runtime plus independently versioned M22.8 sidecars:

```text
Stage-21I generated campaign
+ A individual small-craft physical state / allocator
+ B physical hangar occupancy
+ C deterministic flight-deck queues / active work / watermark
+ M D mission intent + G pending physical delivery + H carrier-wing association
= M22.8 v4 checkpoint
```

## 2. Authority boundaries

Persistence stores and restores durable values owned by existing authorities. It does not execute
combat, manufacture craft, move freight, refill ammunition/propellant, repair damage, change
treasury state, or create wing readiness.

- Stage 17.5 remains engineering/fitting/damage/maintenance authority.
- Stage 18 remains manufacture, inventory, repair inputs and logistics authority.
- Stage 19 remains exact local tactical authority.
- Stage 21 remains strategic fleet/operation/readiness/recovery authority.
- M22.8A/B/C remain craft identity, physical occupancy and flight-deck authorities.
- M22.8D/G/H runtime values are serialized by the M operations sidecar.
- `Stage228CampaignAuthority` is the composition root over the accepted
  `GeneratedCampaignCoordinator`; it is not a parallel campaign timeline.

## 3. Versioning and supported migration

Current native layout:

- file version / schema: **4**;
- runtime: `m22.8.generated-campaign.v4`;
- operations sidecar schema: **1**;
- operations runtime: `m22.8m.operations.v1`.

Supported native migration is additive and non-granting:

| Source | Preserved | Added as empty |
| --- | --- | --- |
| M22.8A / v1 | Stage-21 + craft/allocator | hangars, deck, operations |
| M22.8B / v2 | Stage-21 + craft/allocator + hangars | deck, operations |
| M22.8C / v3 | Stage-21 + craft/allocator + hangars + deck | operations |
| supported Stage-20.5 / Stage-21 chain | migrated accepted Stage-21 state | all M22.8 sidecars |

A migration is not allowed to synthesize a mission, pending delivery, carrier-wing membership,
small craft, ammunition, reaction mass, repair material or replacement.

## 4. Persisted M operations state

The operations sidecar stores only:

1. M22.8D mission rows and the exact next mission-ID watermark;
2. M22.8G produced-but-not-yet-delivered craft rows;
3. M22.8H explicit carrier-to-wing identity associations.

Carrier wing rosters intentionally retain lawfully consumed/lost `SmallCraftId` values while the
physical craft registry does not. The monotonic small-craft allocator remains the guard against
identity reuse.

Binary decoding is bounded by payload, row, wing and string limits. Unknown versions/enums,
truncation, trailing bytes, duplicate rows and incompatible semantic/runtime identifiers fail closed.

## 5. Cross-state restore invariants

Restore constructs independent state first and validates the composition before returning a live
`Stage228CampaignAuthority`.

Mandatory checks include:

- active missions reference issued, surviving craft;
- a `LAUNCH_QUEUED` mission has a matching physical launch request/operation and READY/LAUNCHING
  occupancy;
- ACTIVE / RETURNING missions are physically deployed and retain neither bay occupancy nor deck work;
- `RECOVERY_PENDING` has matching recovery work, or represents the lawful post-cycle seam where the
  craft is already in SERVICING and mission completion has not yet been committed;
- pending physical delivery references an existing produced craft of the same design and cannot
  already be embarked, active on a mission or assigned to a wing;
- surviving wing craft are either embarked on the declared host or lawfully deployed on an active
  mission;
- a missing wing craft may be retained as permanent-loss evidence only when its identity is below the
  persisted allocator watermark; a never-issued future ID fails closed;
- one craft cannot belong to multiple carrier wings, and one carrier `FleetId` cannot have multiple
  persisted wing associations;
- flight-deck processed-tick watermark cannot be ahead of the restored authoritative world tick.

Validation failures return no live campaign and do not mutate the source state used to construct the
checkpoint.

## 6. Acceptance evidence

`Stage228CarrierCheckpointHardeningTest` covers the M22.8M required checkpoint families in one
composed lifecycle fixture and focused negative/migration cases:

- PARKED and READY craft;
- SERVICING / rearm-refuel shaped finite consumable state;
- damaged craft under service/repair-shaped state;
- launch queue and recovery queue;
- deployed ACTIVE mission;
- permanent lost identity retained as wing-loss evidence;
- produced physical replacement pending delivery, not silently inserted into the wing;
- exact byte roundtrip through v4 and restore through the campaign composition root;
- allocator watermark continuity after loss;
- native v1, v2 and v3 migration without later-state grants;
- Stage-21 migration without M22.8 grants;
- v3 flight-deck work preserved without inventing a D mission;
- unsupported/truncated operations sidecars fail closed;
- active mission / deck lifecycle mismatch fails closed;
- never-issued wing identity fails closed;
- post-recovery SERVICING seam remains restorable.

Exact repository CI on the final PR head and post-merge main verification remain the acceptance gate;
this document must not be treated as evidence of a green run before those checks complete.

## 7. Deliberately remaining M22.8N work

M22.8M does **not** close M22.8 or Stage 22. M22.8N must still prove, in one deterministic integrated
acceptance corpus, the complete causal chain:

```text
industry/resources
→ manufacture + physical delivery
→ hangar/service readiness
→ launch + mission
→ Stage-19 track-bounded combat
→ consumption/damage/loss
→ recovery/turnaround
→ strategic readiness consequence
→ funded physical repair/replacement
→ save/load continuation
```

N must include PLAYER and AI use of shared authorities, a carrier/wing degradation or loss path, a
successful recovery path, and a dense-wing long-run case with no hidden grants or duplicate
identities. Only after N passes exact-head CI, merges, and post-merge main is verified may Stage 22
be re-closed and Stage 23 become OPEN/NEXT.
