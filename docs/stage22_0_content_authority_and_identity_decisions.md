# Stage 22.0 — content authority and faction identity decision record

> Status: **ACTIVE DECISION RECORD — not a Stage-22.0 completion claim.**  
> Scope: inventory/disposition/migration decisions required before Stage-22 bulk authoring.  
> Runtime authority remains the accepted Stage 0–21 systems; this document does not introduce a new faction or content registry.

## 1. Decision summary

Stage 22.0 separates three concepts that must not be conflated:

1. **stable runtime/save identity** — the ID already stored in authoritative world state;
2. **canonical production package identity** — the authored Empire/Industrial Union package selected by Stage 22;
3. **player-facing display identity** — localized presentation projected from the package/governance layer.

The governing rule is:

```text
existing stable save ID
+ explicit Stage-22 package/display binding
!= new mutable faction state owner
```

`ContentCatalog`, `WorldFactionIdentityState` and `FactionIdentityResolver` remain the authority for runtime identity and collision detection. Stage-22 governance is downstream metadata plus validation.

## 2. Core-pair stable-ID disposition

### 2.1 Empire

Current compatibility lineage:

`faction.imperial_directorate`

Decision:

- preserve the exact stable ID in supported saves and runtime references;
- bind it to canonical package key `core.empire`;
- project canonical display identity `Империя`;
- do **not** create `faction.empire` as a second mutable state owner;
- future profile/content data must reference the preserved compatibility lineage through the common profile binding seam.

Reason: diplomacy, territory, fleets, knowledge, economy and persistence already carry stable IDs. Renaming every reference has no gameplay value and creates unnecessary migration/collision risk.

### 2.2 Industrial Union

Current compatibility lineage:

`faction.industrial_combine`

Decision:

- preserve the exact stable ID in supported saves and runtime references;
- bind it to canonical package key `core.industrial_union`;
- project canonical display identity `Индустриальный Союз`;
- do not create a second mutable state owner for a prettier lore ID.

The same compatibility argument as the Empire applies.

## 3. Legacy authored/minor/transnational identities

| Stable ID | Stage-22 class | Decision |
|---|---|---|
| `faction.neutral` | `MINOR_AUTHORED` | preserve; not a sovereign core package |
| `faction.trade_league` | `TRANSNATIONAL_NETWORK` | preserve; not the post-core League of Free Systems |
| `faction.miners` | `MINOR_AUTHORED` | preserve; functional/minor organization |

These actors may receive reduced civilian/contact content later in Stage 22, but they do not inherit major-faction doctrine by fallback.

## 4. Large-demo compatibility identities

| Stable ID | Decision |
|---|---|
| `faction.imperial_directorate` | preserve + bind to Empire package |
| `faction.frontier_union` | preserve; no automatic Frontier Confederation mapping |
| `faction.industrial_combine` | preserve + bind to Industrial Union package |
| `faction.free_ports` | preserve; no automatic League mapping |
| `faction.research_consortium` | preserve; no Directorate/Consortium name matching |

Display-name resemblance is never sufficient migration evidence.

## 5. Stage-20 generated-world technical identities

The current Stage-20 representative profile v1, inherited by v2/v3 and consumed by `Stage20PlayableGeneratedWorldFactory`, still supplies:

- `faction.alpha`;
- `faction.beta`.

Therefore they are **not test-only in the current production bootstrap** even though their origin is a representative fixture policy.

Stage-22.0 decision:

- classify them `WORLD_GENERATED`;
- preserve exact IDs in existing generated-world saves;
- bind them to no core or post-core sovereign package;
- forbid fuzzy migration;
- replacing the Alpha/Beta **new-campaign generation policy** requires an explicit versioned profile decision in later Stage-22 work;
- replacement of the new-campaign policy must not rewrite already persisted Alpha/Beta campaigns in place.

This distinction lets Stage 22 retire the representative policy later without corrupting compatibility state.

## 6. Provisional engineering/content disposition

Accepted Stage-17.5/19 mechanics remain authority. Their provisional authored data does not become final Stage-22 content automatically.

Effective entry decisions:

- Stage-17.5/17.5I engineering, protection, weapon/ammunition and shipyard seed definitions: `REAUTHOR` through existing schemas/authorities;
- Stage-18 resource/extraction/refining/manufacturing/facility/station/shipyard/consumable foundations: `PRESERVE`;
- legacy `catalog-v1.json`: `PRESERVE` as compatibility bridge until explicit replacement/migration exists;
- hardcoded `module.test_stage21_strategic_ftl_v1`: `REPLACE` with reviewed Stage-22 production FTL families;
- five `.stage21_strategic_v1` derived doctrine fits: `REPLACE`, not production promotion.

A source-level disposition is projected onto every discovered definition by `Stage22ContentInventory`; procedural definitions have explicit individual entries.

## 7. Machine-readable governance and evidence

Runtime resources introduced by Stage 22.0:

- `data/content/stage22-content-governance-v1.json` — versioned governance input;
- `Stage22ContentGovernanceCatalog` / loader — strict immutable governance model and semantic fingerprint;
- `Stage22ContentInventory` — governed definition/reverse-reference/source-digest inventory and aggregate fingerprint;
- `Stage22FactionIdentityEvidence` — non-authoritative `telemetryEvent + fixture` evidence required by the migration/disposition contract.

The governance resource also locks:

- asset binding families;
- asset maturity states;
- content maturity states;
- RU source copy + EN localization path;
- provenance requirement;
- fit-fingerprint visual binding requirement;
- quantified alpha floors;
- product cut priorities.

## 8. Hard prohibitions carried forward

Stage 22.0 does not permit:

- a second faction registry;
- display-name based migration;
- unknown ID -> neutral fallback;
- faction-name combat/production/sensor modifiers;
- silent promotion of Stage-17.5/19 provisional content;
- promotion of `faction.alpha`/`faction.beta` into authored sovereign factions;
- early production-complete post-core faction packages;
- Stage-22.1 systemic profile implementation before the 22.0 exit gate.

## 9. Exit evidence required before this decision record can support 22.0 completion

The following still gate the later completion record:

- repository-wide production faction literal audit is green;
- current Stage-20 generated policy IDs are covered by governance tests;
- governance/inventory/evidence fingerprints are deterministic;
- supported save round-trip keeps stable IDs and runtime slots;
- exact PR head passes the repository Java-17 verification gate;
- final diff/base/review audit is clean;
- only then may roadmap status move to `22.0 COMPLETE / 22.1 NEXT`.

No statement in this file marks Stage 22.0 complete before those checks exist.
