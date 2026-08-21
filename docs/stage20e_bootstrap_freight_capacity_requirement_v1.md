# Stage 20E — Bootstrap freight-capacity requirement v1

Status: **candidate calibration authority; Stage-22 review required**.

This slice follows the fixed-corpus freight-portfolio measurement from PR #274. That evidence showed that the old single-supplier final gate is too restrictive for many physically serviceable supplier portfolios, but it also showed that simply summing route capacities would illegally reuse the same finite eight-freighter fleet across suppliers.

## Purpose

`Stage20BootstrapFreightCapacityRequirementProfile` derives the minimum representative freight-service capacity required **per ordinary faction start** from accepted physical and economic authorities. It does not inspect corpus outcomes and it does not choose a fleet size by trying values until generated seeds pass.

The authority consumes:

- Stage-20E v2 essential demand rates;
- the corrected Stage-20E supplier-service cadence;
- the accepted Stage-20A five-hop early-civilian-freighter ready-again cadence;
- the representative physical freight payload;
- Stage-20C/18 local-access and hub transfer-rate consequences already used by the production probe.

## Physical derivation

For the current symmetric representative transport authority, one repeatable five-hop regional round trip is:

`2 × endpoint handling`
`+ 2 × maximum non-jump source local access`
`+ 4 × maximum jump access`
`+ 2 × regional five-hop FTL ready-again time`.

One representative freighter therefore has sustainable service capacity:

`min(payload mass / repeatable round-trip seconds, hub transfer mass rate)`.

The minimum integer freight-service fleet requirement for one ordinary faction start is:

`ceil(total essential bootstrap demand / one-freighter sustainable throughput)`.

No extra reserve factor or corpus-derived margin is added in v1.

## Measured current authority

Exact-head CI #4032 evaluated the profile from the current accepted upstream authorities and produced:

- bootstrap requirement authority: `stage20e.bootstrap-requirements.v2`;
- service cadence authority: `stage20e.bootstrap-service-cadence.v1`;
- inter-system cadence authority: `stage20a.intersystem-cadence.v1`;
- representative freight class: `EARLY_CIVILIAN_FREIGHTER`;
- regional envelope: **5 hops**;
- total essential bootstrap demand: **75.0 kg/s**;
- representative payload: **12,000,000 kg**;
- five-hop FTL ready-again time: **1,475.0 s**;
- repeatable reference round-trip cycle: **1,987,865.3103799568 s**;
- one-freighter sustainable service throughput: **6.036626293210149 kg/s**;
- derived minimum freight-service capacity: **13 freighters per ordinary faction start**.

The value **13** is not selected from the representative seed corpus and is not a pass-rate target. The profile has no dependency on generation/corpus classes; the integer is the direct ceiling of the accepted 75 kg/s service requirement divided by the one-freighter sustainable throughput derived from payload, local handling/access and five-hop ready-again cadence. The previous representative eight-freighter policy is therefore under-sized relative to these currently accepted service assumptions, but this calibration slice does not itself replace or provision that policy.

## Semantic boundary

This profile is a **capacity requirement**, not a hidden grant. It does not:

- materialize ships;
- assign ownership of freighters or supplier facilities;
- grant starting inventory;
- alter resource occurrence, extraction, topology or FTL;
- change bootstrap demand rates;
- mutate the frozen v1 corpus baseline;
- claim that one galaxy-global fleet may be reused by multiple factions.

A later Stage-20E integration slice must use a finite shared-fleet allocator and explicit start-service/ownership authority before portfolio throughput can replace the historical single-supplier acceptance path.

## Evidence policy

The regression test emits `STAGE20E_BOOTSTRAP_FREIGHT_CAPACITY_REQUIREMENT_BEGIN/END` so CI records the exact current derived round-trip duration, one-freighter throughput and integer minimum fleet requirement. The numeric result is evidence from upstream authorities, not an input selected from representative seed outcomes.
