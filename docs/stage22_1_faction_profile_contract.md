# Stage 22.1 — versioned faction systemic-profile contract

> Status: **CLOSURE CANDIDATE**
> Scope: M22.1 profile schema, authority bindings, deterministic validation and bounded persistence.
> Pull request: #344 (`stage22-1-faction-profile-contract`).
> Stage 22 remains **ACTIVE**; M22.2+ are outside this slice.

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

The built-in baseline additionally locks exactly two core packages, 16 policy bindings, four visual
references and empty M22.2 manifests. This prevents accidental early production-content promotion.

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
It intentionally authors no production role bindings yet. M22.2 owns:

- shared role and mission taxonomy;
- role → legal fit → physical production path → visual binding;
- manufacturer/procurement lineage;
- localization naming rules and balance telemetry hooks.

Empire production breadth, Industrial Union production breadth, shared civilian breadth, pairwise
balance/soak and every post-core package remain M22.3+ work. Stage 22 is not complete after M22.1.

## 8. Closure gate

M22.1 becomes complete only when all of the following are true:

1. the final PR head passes Java-17 `clean verify`, including tests, coverage, Javadoc and packaging;
2. no base drift or unresolved blocking review remains;
3. PR #344 merges with the exact tested head;
4. the resulting push-to-`main` Java-17 verification succeeds.

Until those external gates succeed, this document is a closure candidate and M22.2 must not start.
