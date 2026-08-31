# Stage 22.2 — shared core content seam

> Status: **COMPLETE**  
> Scope: faction-neutral role/mission/production/visual authoring contract before core-faction bulk data.  
> Implementation PR: #346 (`stage22-2-shared-core-content-seam`), superseding draft merge vehicle #345 without changing the tested implementation head.  
> Closed implementation on main: `ccd38f1d9d34c84b2f562635295a76826cdbbd11`.  
> Stage 22 remains **ACTIVE**. M22.3 — Empire production package is **OPEN/NEXT** and was not implemented during this closure.

## 1. Decision

M22.2 defines reusable authoring contracts, not nine production-ready faction ships. The common layer
must prove that later content can be authored through existing authorities without creating a second
engineering, manufacturing, logistics, mission, faction or presentation truth.

The common role taxonomy is exactly:

### Military families

1. `role.military.corvette`;
2. `role.military.frigate`;
3. `role.military.destroyer`;
4. `role.military.cruiser`;
5. `role.military.battleship`;
6. `role.military.carrier`.

### Support families

1. `role.support.freight`;
2. `role.support.tanker_replenishment`;
3. `role.support.fleet_logistics_repair_salvage`.

Dedicated patrol tiers, battlecruisers, carrier splits, mining gameplay geometry, dedicated
repair/salvage hulls, passenger/habitat hulls and non-interceptor small craft remain downstream scope.

## 2. Mission-profile contract

Every role has exactly one data-only mission profile. Mission profiles reference existing
`Stage21HNpcMissionState.ObjectiveAuthority` domains and never own objective truth. Examples:

- military screen/strike-defense reads fleet/operation authority;
- patrol/reconnaissance may also read discovery authority;
- freight reads economy/freight authority;
- tanker reads fleet/freight authority;
- logistics/repair/salvage reads fleet/freight/industry authority.

A mission description cannot grant damage, range, income, knowledge or a faction outcome.

## 3. Component / hull / facility manifest

`stage22-core-production-manifest-v1.json` defines a bounded reference format:

```text
ProductionManifestDefinition
  id
  fitId
  hullId
  componentIds[]
  shipyardId
  requiredFacilityIds[]
  contentMaturity
  semanticIntent
```

`contentMaturity` deliberately reuses the Stage-22.0 `Stage22ContentGovernanceCatalog.ContentMaturity`
vocabulary instead of declaring a second lifecycle enum:

- `SEED`;
- `CANDIDATE`;
- `VALIDATED`;
- `FROZEN`.

The shared destroyer exemplar remains `CANDIDATE`: it proves the common authoring path but does not
promote provisional Stage-17.5 content into final faction production data. Content maturity participates
in the deterministic production-manifest fingerprint, so a maturity promotion is an attributable
semantic change.

The built-in shared exemplar uses the already accepted engineering and Stage-18 physical seams:

```text
fit.escort_destroyer_schema_v1
  -> hull.escort_destroyer_v1
  -> exact installed module IDs
  -> yard.orbital_escort_v1
  -> facility.fabrication.heavy
  -> facility.fabrication.assembly
```

`Stage22CoreContentSeamValidator` rejects the exemplar unless:

- fit and hull match exactly;
- manifest component IDs equal the exact installed modules;
- every component resolves through `Stage18ManufacturingProductRegistry`;
- Stage-18 has a physical hull profile;
- the referenced yard exists;
- required facilities exactly match the yard requirement set and exist in `Stage18FacilityCatalog`;
- hull dimensions fit the berth and maximum operational mass fits yard service mass.

This exemplar proves the authoring seam. It does **not** promote the Stage-17.5 demonstrator into final
Empire or Industrial Union production content.

## 4. Fit fingerprint → visual binding

`Stage22FitFingerprint` hashes:

- the current authoritative engineering-catalog fingerprint;
- exact fit ID;
- exact hull ID;
- canonical mount-to-module assignments.

The resulting lowercase SHA-256 is a presentation invalidation key only. It never becomes simulation
identity or fitting authority.

The shared destroyer visual binding remains `CONCEPT`. It points at the common visual-authoring rules,
not an Empire/Union asset. `Stage22CoreContentSeamValidator` still resolves and exposes the exact fit
fingerprint so later `ENGINEERING_APPROVED`/`PRODUCTION` assets can pin it fail-closed. Asset lifecycle
therefore reuses the Stage-22.0 `AssetStatus` vocabulary (`CONCEPT`, `ENGINEERING_APPROVED`,
`PRODUCTION`, `DEPRECATED`) rather than defining an M22.2-only status owner.

## 5. Manufacturer / procurement lineage

The shared catalog contains lineage metadata with:

- design authority reference;
- manufacturer reference;
- common procurement-authoring reference;
- `IN_HOUSE`, `LICENSED` or `SHARED_STANDARD` relationship.

These are provenance/authoring metadata only. They do not mint money, inventory, production capacity
or procurement orders.

## 6. Localization rule

The common rule inherits the Stage-22.0 governance contract:

- source language: `ru`;
- localization path: `ru` + `en` (canonicalized deterministically by the catalogs);
- stable content IDs remain language-neutral;
- localized class/role names never alter simulation identity.

A localization rule drifting from the Stage-22.0 language contract fails load.

## 7. Support endurance gate

The support contract explicitly declares minimum stores-endurance floors and validates them against
`Stage20RepresentativeEnduranceProfile` rather than inventing hidden support bonuses:

| Common role | Existing calibration reference | Required floor |
|---|---|---:|
| freight | `BULK_FREIGHTER_LOADED` | 30 days |
| tanker/replenishment | `FLEET_TANKER_LOADED` | 45 days |
| fleet logistics/repair/salvage | `MINING_SHIP` | 30 days |

`MINING_SHIP` is used only as an accepted industrial-support endurance analogue; it does not claim that
a future repair/salvage hull is a mining hull. Dedicated physical support fits remain M22.3/M22.4/M22.5
authoring work.

Validation requires the accepted Stage-20 mission-stores endurance to meet or exceed the declared floor
and retains the positive margin as diagnostic evidence.

## 8. Balance telemetry

Common telemetry hooks are diagnostic-only and project existing authority values from:

- ship engineering;
- Stage-18 shipyard/physical production;
- Stage-20 endurance calibration.

Any hook with `diagnosticOnly=false` fails closed. Telemetry cannot feed a gameplay modifier.

## 9. No faction bias

Both common documents reject the core package/runtime tokens before parsing:

- `core.empire`;
- `faction.imperial_directorate`;
- `core.industrial_union`;
- `faction.industrial_combine`.

This applies to the role/mission/visual seam **and** the component/hull/facility production manifest,
so lineage, semantic rationale or physical-authoring metadata cannot silently encode Empire or
Industrial Union outcomes before their own production packages.

## 10. Validation evidence

Targeted tests cover:

- deterministic fit fingerprinting and rejection of unknown fits;
- deterministic common-seam and production-manifest fingerprints;
- exact nine-role / six-military / three-support taxonomy;
- one mission profile per role;
- complete shared destroyer role → mission → fit → physical production → visual chain;
- exact module/manufacturing/facility/shipyard references;
- governed `ContentMaturity.CANDIDATE` on the shared exemplar;
- all three support endurance floors;
- faction-specific leakage rejection in both shared JSON documents;
- unknown fit, unknown content maturity, gameplay telemetry, duplicate components and unsupported schema fail-closed behavior.

## 11. Authority boundaries and deferrals

M22.2 owns no mutable gameplay state and introduces no alternate state owner. It intentionally does not:

- author the Empire production package;
- author the Industrial Union production package;
- finalize any faction ship sprite;
- create nine artificial physical fits merely to satisfy taxonomy;
- promote provisional Stage-17.5/20 calibration into final faction balance;
- change save schemas, because all new M22.2 data is immutable catalog/validation metadata.

With M22.2 closed, those faction-specific responsibilities remain downstream. **M22.3 is now NEXT, not
partially implemented by this milestone.**

## 12. Closure evidence

All M22.2 closure gates are satisfied:

1. targeted architecture/acceptance tests passed on the exact implementation head;
2. final non-draft implementation PR #346 head `3e9cc6cf18fcd282c1e5271bc4d4f8e935347284` passed Java-17 CI run `33419292845`, job `99577451950` — **SUCCESS**, including tests, coverage, Javadoc and packaging;
3. the final pre-merge audit found no base drift (`behind 0`), submitted blocking review or unresolved review thread;
4. PR #346 was squash-merged with an exact-head guard, producing main commit `ccd38f1d9d34c84b2f562635295a76826cdbbd11`;
5. resulting `main` was verified at that exact SHA;
6. post-merge push CI run `33424642205`, job `99595144057` completed **SUCCESS**, including tests, coverage, Javadoc, desktop packaging and artifact upload;
7. final completion evidence is recorded in `docs/stage22_2_completion_record.md` and the canonical roadmap is advanced to M22.3 NEXT.

Draft PR #345 is not a second implementation lineage. It was superseded solely because the connected
Ready-for-review GraphQL mutation failed on its own schema normalization while GitHub correctly refused
to merge a draft. PR #346 used the identical implementation branch/head/base and received its own green
exact-head CI before merge.

M22.2 is therefore **COMPLETE**. Stage 22 remains active at M22.3.
