# Stage 22.0 — content authority, identity governance and inventory completion record

> Status: **COMPLETE**
> Implementation head verified: `925d05183a0693a5b38d78671b093383b3bdbc93`  
> Final tested PR head: `adb5b6e50d1f1a1ddbb4b2a93349bce6fa92f330`
> Pull request: #343 (`stage22-0-content-authority-inventory`) — merged as `aa83bf1f4dad5151c635e5709e2fb8f47f90f4f5`
> Post-merge `main` verification: **success**
> Next milestone: **Stage 22.1**

## 1. Closeout scope

Stage 22.0 establishes the governance boundary required before Stage-22 bulk authoring. It does not add the Stage-22.1 faction profile implementation, Imperial production content or post-core faction packages.

The completed scope is:

- deterministic inventory of governed content sources and procedural definitions;
- explicit maturity/disposition for provisional and compatibility content;
- deterministic reverse-reference and source-digest evidence;
- explicit stable faction identity classification and compatibility decisions;
- generated-world identity policy for the current Stage-20 `faction.alpha` / `faction.beta` bootstrap lineage;
- explicit quarantine of technical `faction.*` principals that are not mutable world factions;
- common Stage-22 authoring-manifest vocabulary, provenance requirements, fit-fingerprint binding requirement, alpha floors and cut priorities;
- acceptance tests preventing a second faction registry or silent ID/fallback migration.

## 2. Authority boundary preserved

Stage 22.0 deliberately remains downstream of existing runtime authorities:

- `ContentCatalog` and the established domain loaders remain gameplay/content authority;
- `WorldFactionIdentityState` remains mutable world faction identity state;
- `FactionIdentityResolver` remains the runtime identity/collision resolution seam;
- Stage-22 governance metadata validates and classifies those authorities rather than replacing them.

No parallel mutable faction state owner, gameplay content registry or faction-name-driven mechanics were introduced.

## 3. Content inventory evidence

`Stage22ContentInventory` and `stage22-content-governance-v1.json` provide machine-readable inventory evidence across the governed Stage-22 entry surface.

Acceptance coverage proves:

- every governed source is readable and receives a deterministic SHA-256/byte-length digest;
- the current governance set contains 20 governed source resources;
- definition and reverse-reference inventories are deterministic;
- provisional definitions cannot remain implicitly `PRESERVE`;
- the Stage-21 strategic FTL test module and derived doctrine fits have explicit replacement dispositions rather than accidental production promotion;
- governance, inventory and identity-evidence fingerprints are deterministic.

## 4. Faction identity closure

The core compatibility lineages are preserved rather than renamed in authoritative state:

| Stable runtime/save ID | Canonical Stage-22 package | Canonical display identity | State-owner decision |
|---|---|---|---|
| `faction.imperial_directorate` | `core.empire` | `Империя` | preserve existing owner |
| `faction.industrial_combine` | `core.industrial_union` | `Индустриальный Союз` | preserve existing owner |

The package/display binding is presentation and authoring metadata. It does not create `faction.empire` or `faction.industrial-union` as second mutable state owners.

Other authored compatibility identities are explicitly classified and preserved without fuzzy name matching. Large-demo names such as `frontier_union`, `free_ports` and `research_consortium` are not silently mapped to future Stage-22 factions.

## 5. Generated and technical identities

The current Stage-20 production bootstrap still emits `faction.alpha` and `faction.beta`; Stage 22.0 therefore classifies them `WORLD_GENERATED` and preserves their stable IDs/runtime slots for existing campaigns. They receive no core sovereign package binding and cannot be promoted by fuzzy migration.

Three technical principals are explicitly quarantined outside mutable faction authority:

- `faction.acceptance.actor`;
- `faction.acceptance.opponent`;
- `faction.playable-generated-world.observer`.

`Stage22RepositoryFactionReferenceAuditTest` fails if a new production faction literal appears without governance/quarantine or if one of these technical principals is accidentally promoted to governed mutable faction identity.

## 6. Persistence and migration evidence

`Stage22FactionIdentityGovernanceAcceptanceTest` verifies that supported world-state round trips preserve stable faction IDs and runtime slots byte-for-byte through the existing codec path. Core package/display bindings therefore do not rewrite authoritative save identity.

`Stage22GeneratedWorldIdentityGovernanceAcceptanceTest` separately protects generated Alpha/Beta continuity and rejects canonical sovereign remapping of those generated identities.

No unknown-ID-to-neutral fallback and no display-name-based migration were introduced.

## 7. Verification evidence

Implementation verification was performed on exact PR implementation head:

`925d05183a0693a5b38d78671b093383b3bdbc93`

GitHub Actions evidence:

- workflow run: `33373129115`;
- Java 17 verification job: `99428520824`;
- required Maven command: `./mvnw --batch-mode --no-transfer-progress clean verify`;
- conclusion: **success**;
- test, coverage, Javadoc and desktop packaging step: **success**.

Earlier red gates were corrected without weakening quality controls:

1. repository faction audit was narrowed from incidental member/prose matches to actual production faction literals while keeping explicit technical-principal quarantine;
2. global content-ID syntax validation was separated from local engineering slot/interface/compartment/hardpoint IDs;
3. Stage-22 public record APIs received complete Javadoc parameter documentation instead of disabling doclint or warning failure.

Final exact-PR-head evidence:

- head: `adb5b6e50d1f1a1ddbb4b2a93349bce6fa92f330`;
- workflow run: `33374714953`;
- Java 17 verification job: `99433501569`;
- conclusion: **success**.

## 8. Final repository closeout gate — satisfied

The final repository gate completed without widening Stage 22.0:

1. full exact-head Java-17 `clean verify` succeeded for the final PR head;
2. the reviewed base did not drift;
3. no unresolved review thread or submitted review blocked merge;
4. PR #343 merged with the exact tested head as `aa83bf1f4dad5151c635e5709e2fb8f47f90f4f5`;
5. push-to-`main` workflow run `33390051128`, Java-17 job `99481356911`, completed **success**.

This record is therefore authoritative completion evidence for M22.0.

## 9. Intentional deferrals to Stage 22.1+

Stage 22.0 does **not** implement:

- the common runtime faction-profile contract;
- the Imperial production profile/gold slice;
- final Stage-22 production ship/module families;
- bulk character, NPC, mission, localization, VFX, audio or UI asset manifests;
- production-complete post-core faction packages;
- replacement of the Stage-20 Alpha/Beta new-campaign bootstrap policy.

Those items remain downstream work and must consume the governance seams established here rather than bypass them.

## 10. Closure conclusion

The Stage-22 entry authority problem is resolved at the implementation level: existing runtime/save authority is preserved, provisional content is explicitly dispositioned, faction identities have deterministic compatibility rules, generated and technical identities cannot silently become authored sovereign factions, and later authoring has a single governance contract.

Repository status after the final docs-head and post-merge CI gates:

- **Stage 22: ACTIVE**;
- **Stage 22.0: COMPLETE**;
- **Stage 22.1: closure candidate in PR #344**.
