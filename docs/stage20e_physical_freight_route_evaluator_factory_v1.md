# Stage 20E — Physical freight route evaluator factory v1

> Status: **CANDIDATE PRODUCTION INTEGRATION SEAM / NO PHYSICS CHANGE**  
> Version: `stage20e.physical-freight-route-evaluator-factory.v1`

## Purpose

The generated-world production probe already owns all physical facts needed to evaluate repeated freight service:

- accepted explicit-neighbor topology;
- exact physical jump-edge state;
- fitted loaded outbound and return jump plans;
- generated local Stage-20C layouts;
- major-hub loading/unloading transfer rates;
- representative freight payload.

The historical production probe evaluates routes with the transport profile's configured active-freighter count. Later Stage-20E diagnostics proved a separately derived finite service-capacity requirement of `13` freighters per ordinary faction start and reconstructed the same physical evaluator with that explicit count.

Production acceptance must be able to consume the derived finite capacity without depending on a class named `Diagnostics` and without creating a second movement model. This factory provides that seam.

## Contract

`Stage20PhysicalFreightRouteEvaluatorFactory.create(...)` receives the existing physical authorities plus one explicit positive `activeFreighterCount`.

It creates a new `FreightFleetProfile` that changes only:

```text
activeFreighterCount
```

while preserving exactly:

```text
payloadMassKgPerFreighter
loaded outbound fitted jump plan
return fitted jump plan
jump-edge physical state
local access times
hub transfer rates
Stage-22 review flag
source provenance
```

The returned object is the existing `Stage20PhysicalFreightRouteEvaluator`; no alternate throughput formula is introduced.

## Parity gate

The regression reconstructs fixed seed `1` with the unchanged representative v2-candidate inputs and compares this factory against the already accepted whole-placement diagnostic reconstruction at explicit count `13`.

For a deterministic sample of generated system pairs it requires exact equality of `RouteAssessment` results at both:

- `1` allocated freighter;
- `13` allocated freighters.

The factory also fails closed on zero/negative fleet allocation and incomplete local-layout coverage.

## Explicit non-authorities

This slice does not:

- derive or choose the number `13`;
- create ships or ownership;
- modify payload mass;
- modify FTL/local route physics;
- change generated topology or layouts;
- change producer capacity or bootstrap demand;
- change whole-seed acceptance yet.

It is only a production-safe adapter for an already supplied finite fleet count.

## Next causal slice

Use this factory together with `Stage20BootstrapFreightCapacityRequirementProfile` and the accepted maximum-cap frontier resolver to build a resolved whole-placement freight acceptance result after faction-start placement.

That result can then replace the historical single-supplier `Stage20EconomicThroughputAcceptance` at the **whole-seed acceptance boundary**, while preserving the older gate for baseline diagnostics where required.
