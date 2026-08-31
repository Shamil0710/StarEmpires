# Stage 22.1 — versioned faction systemic-profile contract

> Status: **COMPLETE**
> Scope: M22.1 profile schema, authority bindings, deterministic validation and bounded persistence.
> Pull request: #344 (`stage22-1-faction-profile-contract`).
> Merge commit: `bfbd8a5ee55329c332d6bbd85c5039a515974cf5`.
> Post-merge CI: run `33405380066`, Java 17 verification **SUCCESS**.
> Stage 22 remains **ACTIVE**; M22.2 is the current milestone.

## 1. Decision

Stage 22.1 adds one immutable declarative profile layer for the two production-core packages:

| Canonical package | Existing stable runtime/save ID | Systemic profile |
|---|---|---|
| `core.empire` | `faction.imperial_directorate` | `profile.core.empire.v1` |
| `core.industrial_union` | `faction.industrial_combine` | `profile.core.industrial_union.v1` |

The profile is configuration and reference data. It does not own mutable faction identity, doctrine,
treasury, inventory, freight, fleets, diplomacy, territory, knowledge or recovery state.

No `faction.empire` or `faction.industrial-union` runtime identity is introduced. Public package and
localized display identity remain downstream projections over the Stage-22.0 compatibility lineages.

## 2. Versioned data boundary

`data/content/stage22-faction-profiles-v1.json` contains:

- exact schema and semantic catalog versions;
- two complete systemic profile records;
- seven bounded institutional doctrine axes per profile;
- explicit preference values for every accepted `StrategicGoalType`;
- eight policy references per profile;
- empty `SEED` authored-content manifest references reserved for M22.2;
- ship and character visual authority references;
- RU-source / RU+EN localization contracts;
- explicit compatibility-alias lists.

`Stage22FactionProfileCatalog` sorts every independent definition and computes a lowercase SHA-256
fingerprint from semantic values rather than JSON byte layout. Repeated loads therefore produce the
same catalog ordering and fingerprint.

## 3. Existing-authority binding

Each policy record declares which already accepted authority will consume later configuration:

| Policy kind | Existing authority seam | New mutable owner? |
|---|---|---|
| industrial | Stage-18 catalogs and industrial state | no |
| procurement | common faction policy command path | no |
| logistics | Stage-20 physical freight/order runtime | no |
| fleet | Stage-21 fleet readiness/order command path | no |
| diplomacy | common diplomacy/legal lifecycle | no |
| territory | common claim/control lifecycle | no |
| knowledge | discovery and actor-bounded observation state | no |
| recovery | finite settlement repair/replacement lifecycle | no |

The doctrine definition projects values into the existing `FactionDoctrineState` and
`FactionStrategicDoctrineProfile` types. The ordinary Stage-21 strategic planner remains responsible
for candidate scoring and selection. Faction names never grant an outcome or hidden multiplier.

## 4. Fail-closed validation

`Stage22FactionProfileLoader` rejects:

- unknown schema versions, malformed values and duplicate IDs;
- missing doctrine, policy, manifest, visual or localization references;
- incomplete policy-kind coverage or bindings to the wrong common authority;
- missing, cross-package or circular policy dependencies;
- a profile/stable-ID/package/class combination not approved by Stage 22.0;
- a core profile that references a post-core manifest;
- a ship/character visual mismatch, orphan visual or orphan systemic reference;
- localization that violates the governed RU-source and RU+EN path;
- a compatibility alias without an explicit `ALIAS`/`MIGRATE` governance target;
- a role with an unknown fit, no Stage-18 physical hull path or mismatched ship visual.

The M22.1 built-in baseline locks exactly two core packages, 16 policy bindings, four visual references
and empty M22.2 manifests. M22.2 may now populate those manifests only through its common validated seam.

## 5. Persistence decision

`Stage22FactionProfileBindingState` and `Stage22FactionProfileBindingCodec` provide a bounded sidecar
containing only:

- envelope and profile-schema versions;
- semantic catalog version and exact fingerprint;
- sorted `stableFactionId + runtimeFactionId + profileId + profileVersion` bindings.

Decode rejects bad magic, unknown versions, oversized values/counts, truncation and trailing bytes.
Materialization validates both directions of the existing `FactionIdentityResolver` mapping and the
exact current catalog schema/version/fingerprint. The encoding is byte-stable across round trips.

This sidecar deliberately does not rewrite the Stage-21 world-save envelope. A later Stage-22 save
wrapper may compose it beside existing state; it must not duplicate or migrate the underlying world
authorities implicitly.

## 6. Core-pair behavioral evidence

The acceptance fixture feeds equivalent lawful supply-dependency evidence through the ordinary
Stage-21 strategic planner:

- the Empire profile weights `DEFEND` above `STOCKPILE`;
- the Industrial Union profile weights `STOCKPILE` above `DEFEND`;
- supplying the same candidate values under a different faction ID produces the same result.

The difference is therefore explicit doctrine input, not a faction-name branch. Shared physical
evidence may still converge on the same rational action in later scenarios.

## 7. M22.2 boundary and deferrals

M22.1 defines the common manifest reference and validates a complete role chain if one is present.
M22.2 owns:

- shared role and mission taxonomy;
- role → legal fit → physical production path → visual binding;
- manufacturer/procurement lineage;
- localization naming rules and balance telemetry hooks.

Empire production breadth, Industrial Union production breadth, shared civilian breadth, pairwise
balance/soak and every post-core package remain M22.3+ work. Stage 22 is not complete after M22.1.

## 8. Closure evidence

All M22.1 external gates are satisfied:

1. exact PR head `b5eae85a25ec0e8e3d13af44f39b199d58d3ebd5` passed Java-17 verification in run `33394873060`;
2. final base/review audit found no base drift, submitted blocking reviews or unresolved review threads;
3. PR #344 merged from that tested head as `bfbd8a5ee55329c332d6bbd85c5039a515974cf5`;
4. push-to-`main` run `33405380066`, job `99531560484`, completed **SUCCESS**, including the full test/coverage/Javadoc/package step.

M22.1 is therefore closed. M22.2 is the first unfinished Stage-22 milestone.
