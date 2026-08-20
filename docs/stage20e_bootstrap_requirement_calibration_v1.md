# Stage 20E — Bootstrap Requirement Calibration v1

> Status: **PROVISIONAL STAGE-20E CLOSEOUT SLICE**  
> Implementation: `stage20e.bootstrap-requirements.v1`  
> Reference station policy: `station.infrastructure.frontier_multipurpose`  
> Stage-22 balance review: **required**

## Purpose

The production seed probe previously required a hand-authored integration-only `BootstrapRequirementProfile` with convenient values (`1 kg/s`, effectively unbounded route time). That was sufficient to prove orchestration but was not acceptable as the production Stage-20E start-economy authority.

`Stage20BootstrapRequirementCalibrationProfile` replaces that test-only demand authority with a versioned derivation whose **policy choices are explicit** and whose **numeric consequences come from existing physical content**.

## Policy versus physical authority

Stage 18 does not mark commodities as "civilization essential". The ontology defines material-chain meaning, storage and physical recipes, not societal demand. Therefore v1 does not pretend that Stage 18 itself selected the start requirements.

The following are explicit Stage-20 policy:

- reference industrial cell: `station.infrastructure.frontier_multipurpose`;
- essential reference process stream: `refining.water_purification`;
- essential reference process stream: `refining.structural_alloy`;
- these streams are evaluated at their full simultaneous pristine physical process ceilings;
- the resulting extracted feedstocks are the ordinary-start essential commodities for this v1 gate.

This is a **minimal frontier sustainment baseline**, not a population consumption model and not a technological self-sufficiency promise. Advanced materials, reactor fuel, precision industry and other growth chains may legitimately require imports.

## Derived physical rates

The reference station actually installs:

- `facility.processing.volatiles`;
- `facility.processing.bulk_refinery`;
- `facility.fabrication.heavy`;
- `facility.fabrication.electrical`;
- `facility.fabrication.assembly`.

### Water purification

`refining.water_purification` consumes `commodity.feedstock.water_ice` at one kilogram per kilogram of gross input.

For `facility.processing.volatiles`:

- power ceiling: `15,000,000 W / 250,000 J/kg = 60 kg/s`;
- engineering ceiling: `10 work/s / 0.15 work/kg = 66.666... kg/s`;
- maintenance ceiling: `1 work/s / 0.02 work/kg = 50 kg/s`;
- station transfer ceiling: `500,000 kg/s`.

The physical minimum is therefore **50 kg/s water-ice input**. With the recipe's `0.94` useful output fraction, the corresponding purified-water output is **47 kg/s**.

### Structural alloy

`refining.structural_alloy` consumes `commodity.feedstock.metallic_ore` at one kilogram per kilogram of gross input.

For `facility.processing.bulk_refinery`:

- power ceiling: `100,000,000 W / 4,000,000 J/kg = 25 kg/s`;
- engineering ceiling: `50 work/s / 1.0 work/kg = 50 kg/s`;
- maintenance ceiling: `5 work/s / 0.12 work/kg = 41.666... kg/s`;
- station transfer ceiling: `500,000 kg/s`.

The physical minimum is therefore **25 kg/s metallic-ore input**. With the recipe's `0.68` output fraction, the structural-alloy stream is **17 kg/s**.

The two essential inbound streams total only `75 kg/s`, far below the station's `500,000 kg/s` aggregate transfer limit. The derivation rejects a future policy set if its simultaneous essential inbound demand exceeds the reference station's actual handling authority.

## Route-time authority from shared physical storage

Both selected feedstocks use `storage.dry_bulk`. The reference station has `40,000,000 kg` of that storage class.

The derivation does **not** give each commodity the entire dry-bulk capacity independently. It treats the storage as one shared physical buffer consumed by all selected essential streams:

```text
shared dry-bulk demand = 50 + 25 = 75 kg/s
shared coverage horizon = 40,000,000 / 75
                        = 533,333.333... s
                        ≈ 6.17 days
```

That shared coverage horizon becomes the maximum accepted supplier-route time for both essential feedstocks. This makes the route-time bound a consequence of actual storage and actual derived process draw rather than a hand-authored `1e9 s` placeholder.

For v1, the generic intermediate-input route bound is the minimum derived essential storage horizon, and the generic minimum intermediate throughput is the minimum derived essential input rate. They remain conservative compatibility fields for the existing bootstrap validator; later profiles may become more commodity-specific if Stage-20E/20F requires it.

## Resulting current BootstrapRequirementProfile

The derived current profile is equivalent to:

```text
version = stage20e.bootstrap-requirements.v1
maxIntermediateInputRouteTimeS = 533333.333333...
minIntermediateInputThroughputKgPerSecond = 25

essential commodity.feedstock.water_ice:
  min supplier throughput = 50 kg/s
  max supplier route time = 533333.333333... s

essential commodity.feedstock.metallic_ore:
  min supplier throughput = 25 kg/s
  max supplier route time = 533333.333333... s
```

These numbers are **outputs of the current catalog/policy derivation**, not independent balance constants.

## Provenance

`DerivedProfile` preserves the exact semantic fingerprints of:

- Stage-18A resource ontology;
- Stage-18C refining recipes;
- Stage-18E facilities;
- Stage-18F station infrastructure.

It also preserves the explicit reference station, process-policy rows and per-process limiter evidence. If any physical catalog changes, the profile is recomputed from the changed authority rather than silently retaining stale kg/s values.

The policy selection itself remains marked `stage22ReviewRequired=true` because deciding that these two streams define the ordinary start is a balance/design decision, not a result of physics.

## Production-probe integration

`Stage20GeneratedWorldProductionProbeTest` now constructs its `AcceptanceAuthority` from `Stage20BootstrapRequirementCalibrationProfile.deriveCurrent()`.

The old `stage20e.production-probe.integration-demand.v1` helper and its arbitrary `1 kg/s / 1e9 s` values are removed from the production-probe regression path.

The probe still does not invent missing monetary delivered cost, buffer inventory or ownership state. This calibration closes **demand/routing requirement authority**, not those separate upstream authorities.

## Anti-cheat / anti-rescue invariants

The calibration:

- never examines a generated seed before choosing its essential process policy;
- never lowers demand because a particular seed fails;
- never lengthens route time because a supplier is inconveniently distant;
- never adds resource deposits, facilities, storage or freighters;
- never counts the same physical storage-class capacity independently for multiple essential commodities;
- never treats pristine theoretical process ceilings as guaranteed runtime production — they are the reference service-level demand used by the generation gate.

## Regression evidence

Tests prove that:

1. water demand is recomputed as `50 kg/s` from the actual volatile-processing facility and water-purification recipe;
2. structural demand is recomputed as `25 kg/s` from the actual bulk refinery and structural-alloy recipe;
3. limiter identities are preserved (`MAINTENANCE_WORK` and `PROCESS_POWER` respectively);
4. shared dry-bulk storage produces one `40,000,000 / 75` route horizon rather than double-counted storage;
5. dependency diagnostics preserve exactly the economic rate/time projection;
6. all Stage-18 semantic fingerprints remain explicit provenance;
7. the production whole-seed probe consumes this derived authority.

## Remaining Stage-20E closeout

After this slice, the next roadmap action is to run a **fixed deterministic representative root-seed corpus** through the production probe and preserve machine-readable acceptance/failure distributions. That measurement must come before inventing any global accepted-seed percentage target.

The corpus should reveal whether ordinary failures come from:

- topology quality;
- real resource scarcity/host distribution;
- extraction/logistics limitations;
- the newly derived 50/25 kg/s bootstrap service level;
- faction-start concentration/redundancy constraints;
- still-unresolved authority such as monetary delivered cost, initial stock/buffers or ownership.

Failures remain evidence. They must not trigger per-seed deposits, extra infrastructure, extra transport or relaxed requirements.
