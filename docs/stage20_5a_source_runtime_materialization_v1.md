# Stage 20.5A — generated source and extraction-outpost runtime materialization v1

Status: implemented production seam and deterministic save/load composition.

## Closed boundary

`Stage20SourceSupplyMaterializer` restores every saved generated natural occurrence as an ordinary
finite Stage-18 source. `Stage20SourceOutpostMaterializer` binds every canonical
`INITIAL_EXTRACTION_SITE` to an ordinary Stage-18 station storage, installed facility state and
`Stage18StationProductionBridge` extraction path. No generator is invoked during bootstrap or
restore.

Fresh source outposts start with empty commodity and product storage. The commissioned-pristine
bootstrap policy supplies only the exact facility's power, heat rejection, labor and maintenance
capability. Commodity can appear only after the ordinary extraction runtime consumes finite source
reserve and atomically commits recovered mass to the outpost storage.

## Existing-content compatibility authority

The accepted Stage-20 world can contain canonical surface and deep-subsurface extraction sites, but
the Stage-18 station catalogue currently authors only a free-body mining-outpost chassis. Changing
that catalogue now would change the accepted industrial content fingerprint and reopen Stage 20.

`stage20_5.source-outpost-chassis-compatibility.v1` therefore authorizes only two constrained
variants:

- `location.surface` with `facility.extraction.surface`;
- `location.deep_subsurface` with `facility.extraction.deep`.

Both retain the existing `station.infrastructure.mining_outpost` physical storage capacities,
cargo-handling classes, transfer rate and maximum handled unit mass. Only the already-generated
exact extraction facility and exact generated location tag are substituted. No new capacity,
cargo, recipe, source, method or world placement is inferred. Every other zero/ambiguous mapping
still fails closed. Final dedicated surface/deep archetype authoring remains explicitly marked by
`stage22.review.source-outpost-surface-deep-archetypes`.

## Persistence and composition

`Stage20SourceOutpostCampaignPersistence` captures the live finite reserve in both the canonical
resource row and Stage-18 source snapshot, recomputes the world fingerprint and rebinds discovery to
that exact fingerprint. Storage and facility state are captured in the same Stage-18 industrial
state as generated industrial stations, yards and orders.

`Stage20GeneratedIndustrialRuntimeBridge` composes the Stage-20.5A and Stage-20.5C registries over
one shared industrial snapshot. Capture and restore preserve both station families without duplicate
identity or hidden replacement.

## Acceptance coverage

Focused tests prove:

- every canonical initial extraction site materializes exactly once;
- fresh outposts contain no cargo or products;
- compatibility variants retain the existing physical chassis envelope;
- extraction consumes finite source reserve before recovered cargo appears;
- rejected extraction mutates neither reserve nor storage;
- canonical reserve, discovery fingerprint, outpost storage and facility state round-trip exactly;
- source outposts and generated industrial stations coexist in one deterministic Stage-18 state.
