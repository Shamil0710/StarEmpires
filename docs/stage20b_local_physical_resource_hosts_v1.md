# Stage 20B — Local Physical Resource Hosts v1

> Status: **PROVISIONAL STAGE-20B / STAGE-20E INTEGRATION SLICE**  
> Implementation: `stage20b.local-physical-resource-hosts.v1`  
> Base: merged macro-region/system-placement work from PR #262

## Purpose

This slice removes the hand-authored `ResourceHostProfile` gap that remained after deterministic macro-region and star-system placement was introduced.

The existing Stage-20C local infrastructure generator already produces `RESOURCE_FIELD_ANCHOR` rows in authoritative `LocalPhysicalPosition` SI coordinates. `Stage20LocalPhysicalResourceHostGenerator` now materializes deterministic physical host semantics on those point anchors and projects them into the existing Stage-20E resource-occurrence input contract.

The causal chain for this slice is therefore:

```text
root seed
→ Stage-20B system geometry
→ Stage-20C SI resource-field point anchors
→ deterministic physical host class
→ Stage-18 natural extraction compatibility
→ generated Stage-20E ResourceHostProfile rows
→ correlated Stage-20E occurrence geography
→ finite reserves / explicit extraction sites
```

No test or production caller needs to invent host-class IDs, extraction environments or occurrence-affinity maps in order to run the resource generator.

## Physical host classes

v1 exposes four host classes, each backed by a real Stage-18 extraction environment and a real Stage-18 facility-location tag:

| Host class | Stage-18 environment | Location tag |
|---|---|---|
| `host.asteroid.free_body` | `FREE_BODY` | `location.free_body` |
| `host.rocky.surface` | `SURFACE` | `location.surface` |
| `host.rocky.deep_subsurface` | `DEEP_SUBSURFACE` | `location.deep_subsurface` |
| `host.volatile_bearing` | `VOLATILE_BEARING` | `location.volatile_site` |

A class is eligible for generation only when the exact loaded Stage-18 extraction catalog contains at least one `NATURAL_OCCURRENCE` method for that environment.

`SALVAGE_SITE` is intentionally excluded because Stage-18 models salvage as a bounded manufactured-asset stream, not a natural geological occurrence.

## Affinity authority

The first generated-host version deliberately does **not** invent geological richness multipliers.

For the selected physical environment, every Stage-18 occurrence type accepted by at least one natural extraction method receives a neutral compatibility affinity of exactly `1.0`.

That means:

```text
host profile
= physical compatibility gate
≠ deposit presence
≠ reserve size
≠ grade
≠ recovery bonus
```

Actual regional correlation, presence threshold, finite accessible mass, grade and source recovery remain owned by `Stage20ResourceOccurrenceGenerator` and its versioned Stage-20E generation profile.

## Determinism

Host-class selection is keyed by:

```text
rootSeed + systemId + resourceAnchorId
```

Input list ordering therefore cannot perturb an already identified host.

The result is sorted by `systemId + anchorId`, carries the exact Stage-18 extraction-catalog fingerprint and rejects duplicate per-system layouts.

## Spatial authority boundary

The physical host stores the exact `LocalPhysicalPosition` of its Stage-20C `RESOURCE_FIELD_ANCHOR`.

The Stage-20E `ResourceHostProfile` itself does not duplicate position; the occurrence generator resolves the same anchor ID back through the authoritative Stage-20C layout. Tests verify that generated occurrences inherit the exact generated-host/anchor position.

This v1 **does not** claim calibrated resource-field or celestial-body physical extents. The point-anchor status remains explicit. It also does not yet introduce standalone planets/moons with orbital mechanics.

That is intentional: Stage 20 requires internally coherent physical coordinates, but Star Empires is not required to become an orbital-mechanics simulator before economic geography can be evaluated.

## Anti-rescue invariant

This generator cannot read or react to:

- economic viability;
- faction-start acceptance;
- shortage diagnostics;
- delivered cost;
- reserve concentration;
- ownership;
- downstream whole-seed acceptance.

It cannot add a fallback host, change topology, increase reserve mass, install a facility or alter a rejected seed.

A later economic layer may reject the seed; it may not ask this layer to secretly create a better deposit.

## Validation coverage

Regression tests prove:

1. deterministic output independent of input-layout ordering;
2. exactly one generated host for every resource-field point anchor;
3. exact `LocalPhysicalPosition` equality between host and authoritative anchor;
4. occurrence compatibility comes only from real Stage-18 natural extraction methods;
5. generated host profiles feed the real Stage-20E occurrence generator without hand-authored `ResourceHostProfile` fixtures;
6. any concrete generated occurrence resolves back to the exact host class, extraction environment and SI anchor position.

## Remaining gap before Stage-20E production seed probe

This slice closes the manual **resource-host semantics/profile** gap, but does not by itself claim all Stage 20B local physical content complete.

The next roadmap action can now build the production-style whole-seed probe using:

```text
macro regions / generated systems
→ Stage-20D topology
→ Stage-20B system geometry
→ Stage-20C local layouts/resource anchors
→ generated physical hosts
→ Stage-20E finite resource occurrences/sites
→ physical production/throughput closure
→ faction-start diagnostics/evaluation/placement
→ whole-seed acceptance evidence
```

If that probe exposes a need for independent celestial-body placement or calibrated field extents, those remain an explicit Stage-20B follow-up rather than being fabricated inside economic code.
