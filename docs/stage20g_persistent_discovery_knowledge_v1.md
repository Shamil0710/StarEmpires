# Stage 20G — Persistent Discovery Knowledge v1

**Status:** IMPLEMENTED CANDIDATE — exact-head CI required before merge

**Authority:** `stage20g.persistent-discovery-knowledge.v1`

**Scope:** world-stable static/resource knowledge only

## Purpose

Stage 20G requires durable discovery without turning generated truth into observer knowledge. The
accepted split is now explicit:

```text
world-stable static object
→ Stage20DiscoveryKnowledgeState

system-local mobile target
→ SensorMeasurement
→ TrackState / covariance / age (Stage 17.5)
```

`TRACKED` remains in the shared discovery vocabulary, but a persistent static row rejects it. Mobile
fleet contacts must continue through the existing Stage-17.5 identity and persistence boundary; a
local `targetId` is never promoted into a galaxy-wide identity.

## Static discovery states

Absence is canonical `UNKNOWN`. Persisted rows distinguish:

- `DETECTED`: rough contact, no classification or exact location;
- `CLASSIFIED`: stable class/family evidence, still no exact location;
- `KNOWN_STATIC_LOCATION`: classified world-stable object with authoritative SI location.

Weaker later reports add provenance and freshness but cannot erase stronger survey knowledge.
Conflicting stable identity or location claims fail closed.

## Provenance and freshness

Every retained row contains one or more `DiscoveryEvidence` items. Sources cover:

- passive sensors;
- active scans;
- probes/recon;
- purchased/shared map data;
- faction intelligence;
- physical visit/survey;
- persistent-infrastructure broadcasts.

Freshness is never inferred from the source label. Each evidence item explicitly carries either a
finite `freshUntilSeconds` or a non-expiring marker. Expiry yields `STALE`; it does not silently
delete durable map memory.

## Resource knowledge is not reserve truth

Resource knowledge progresses independently:

```text
NONE
→ HOST_KNOWN
→ RESOURCE_INDICATION
→ CLASSIFIED_RESOURCE_FAMILY
→ ESTIMATED_GRADE_RESERVE
→ SURVEYED_DEPOSIT
```

The estimate payload contains non-degenerate grade and recoverable-mass intervals plus confidence.
It rejects zero-width intervals, so neither initial nor remaining physical reserve can masquerade as
observer knowledge. The generated `Stage20ResourceOccurrenceWorld` remains the only physical source
authority.

## Persistence boundary

`Stage20DiscoveryPersistentState` is an explicitly versioned sidecar bound to:

- exact root seed;
- exact generation version;
- exact generated-world fingerprint.

`Stage20DiscoveryPersistenceCodec` provides deterministic bounded binary encode/decode and atomic
file replacement. Owner snapshots, static rows and evidence histories are canonicalized before
writing. Unknown objects consume no rows.

## Acceptance evidence

Headless tests cover:

- `UNKNOWN → DETECTED → CLASSIFIED → KNOWN_STATIC_LOCATION` without leaked location;
- finite/current/stale and permanent evidence behavior;
- weaker-report non-downgrade;
- conflicting identity/location rejection;
- explicit rejection of static `TRACKED` rows;
- resource-survey interval enforcement;
- deterministic byte encoding and exact binary/file round-trip;
- malformed/truncated/trailing data rejection;
- duplicate owner/object identity rejection.

## Next dependent slice

The next Stage-20G slice must consume this authority from generated bootstrap without granting every
generated object to every faction. It must also project mobile visibility only from Stage-17.5
`TrackState` and prove a measurable physical detection-to-fire-control interval from existing
Stage-20 calibration profiles.
