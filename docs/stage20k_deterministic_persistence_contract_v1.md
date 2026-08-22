# Stage 20K — deterministic seed/persistence contract v1

Status: **MERGED** — PR #309, exact-head CI run `32546544626`, merge
`db085890deb2c31051da55f73929cb24460bc81f` (merge tree equals validated head
`017cbb8a18c2f31b91a326f846ea94f62f49cbd7`).

## Saved authority, not a regeneration recipe

`Stage20GeneratedCampaignPersistentState` is the aggregate campaign envelope. Its generation tuple
is provenance:

```text
worldSeed
+ resolved generator version
+ source generator version
+ representative generation profile
+ Stage-18 content fingerprint
```

Resume always selects `PRESERVE_SAVED_MATERIALIZED_WORLD`. Even when the installed tuple matches,
load does not rerun generation. When it differs, the saved world remains authoritative and
`EXPLICIT_MIGRATION_OR_NEW_WORLD_REQUIRED` blocks implicit replacement.

## Canonical materialized snapshot

The snapshot stores length-delimited, domain-qualified, stable-ID rows. It sorts by
`domain + stableId`, rejects duplicate identities, encodes floating-point values in exact hexadecimal
form, and computes separate SHA-256 fingerprints for:

- the materialized physical/generated authority;
- the machine-readable generation-quality report.

The physical rows retain:

- macro sectors/systems and ordinary topology;
- every Stage-20D edge, open/closed state, fitted transit parameters and both exact arrival
  positions/velocities;
- every Stage-20C placement and calibrated local connection;
- physical resource hosts, occurrence IDs, finite initial/current reserves and extraction sites;
- accepted faction starts and the reconstructed Stage-20E freight ownership pools, commitments,
  routes and pre-`FleetId` materialization ordinals;
- Stage-20F owner/station process and active-yard authority;
- Stage-20H special locations, finite linked resources and salvage streams.

The quality rows retain both source/resolved whole-seed decisions plus the complete Stage-20D
topology-quality distribution, sector diagnostics, bridges/articulations and violations.

## Typed runtime sidecars

The aggregate embeds existing deterministic codecs rather than flattening live state into generator
rows:

| Sidecar | Preserved authority |
|---|---|
| `Stage20MaterializationPersistentState` | core `GameState` plus exact hierarchical far-local position and velocity |
| `Stage18IndustrialState` | remaining finite sources, inventory, facilities, yards and long-running orders |
| `Stage20DiscoveryPersistentState` | observer-local durable knowledge bound to the exact saved world fingerprint |

The codec is versioned, bounded, rejects truncation/trailing bytes/unknown enum values, validates
all cross-sidecar seed/version/fingerprint bindings, and supports atomic file replacement.

## Runtime bridge remains explicit

Persistence does not pretend planning rows are already live runtime objects. These seams remain
machine-readable:

- `SOURCE_SUPPLY_MATERIALIZATION`;
- `FREIGHT_FLEET_MATERIALIZATION`;
- `CARGO_ORDER_AND_LOT_MATERIALIZATION`;
- `INDUSTRIAL_ENTITY_MATERIALIZATION`;
- `LIVE_ARRIVAL_AUTHORITY_INTEGRATION`.

The first four exactly mirror Stage 20F. The fifth carries the Stage-20D requirement to apply the
persisted arrival endpoint position/velocity to the real inter-system transition authority.

## Acceptance evidence

- two independent headless seed-`1` resolved generations produce identical canonical physical rows,
  world fingerprint, quality rows and quality fingerprint;
- reversing input row order produces the same canonical snapshot;
- aggregate binary encoding is byte-identical across repeats and round-trips value-exactly;
- a dematerialized entity at multi-trillion-cell coordinates retains exact position and velocity;
- discovery owners, finite occurrences, extraction facilities, freight ownership slots,
  industrial process/yard plans and special locations survive the aggregate round trip;
- generator-version mismatch preserves the saved snapshot and requires explicit migration/new-world
  policy;
- truncated, trailing and fingerprint-tampered envelopes fail closed.

Stage 20L consumes these fingerprints and persisted seams in the final physical-world acceptance
matrix (`docs/stage20l_physical_world_acceptance_matrix_v1.md`); it does not close the deferred
runtime bridge.
