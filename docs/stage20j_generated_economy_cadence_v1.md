# Stage 20J — generated economy cadence v1

Status: **MERGED** — PR #308, exact-head CI run `32545685272` passed; merge
`ef56f5d4b8c9f334f42ea0234f24f41b48545b2f` preserves the validated head tree exactly.

## Authority boundary

`Stage20GeneratedEconomyCadenceAcceptance` accepts only an exact accepted
`Stage20ResolvedGeneratedWorldProductionProbe` and the matching closed
`Stage20OperationalIndustrialSpecializationPlan`. It reads the retained Stage-18/20 physical state;
it does not materialize source production, inventory, cargo lots, orders, fleets or industrial
entities.

The Stage-20F runtime handoff remains unchanged:

- `SOURCE_SUPPLY_MATERIALIZATION`;
- `FREIGHT_FLEET_MATERIALIZATION`;
- `CARGO_ORDER_AND_LOT_MATERIALIZATION`;
- `INDUSTRIAL_ENTITY_MATERIALIZATION`.

## Causal metrics

The report retains:

| Layer | Physical derivation |
|---|---|
| extraction | finite useful occurrence mass / generated physical mine output kg/s |
| refining and components | exact Stage-18 recipe input ratios and input-limited output kg/s |
| selected operation | Stage-20F reserved input kg/s and active facility output kg/s |
| freight | payload, exact owned ship count, load/unload, local access, fitted FTL ready-again round trip |
| buffer | canonical stored mass / active consumption kg/s |
| construction | active yard + Stage-18 hull BOM + generated source output + one-freighter physical routes |
| trade | positive cross-sector capacity advantage capped by one-freighter delivered throughput |

`Stage20PhysicalFreightRouteEvaluator.FreightCycleAssessment` is the retained component view of the
existing evaluator. The compact historical `RouteAssessment` is projected from it, so Stage 20J does
not duplicate route physics.

Construction supply is deliberately conservative and reproducible: one representative owned
freighter serves the selected hull-input rows serially. Empty-pipeline ETA includes each one-way
delivery latency plus delivered mass/rate; steady-state replenishment is the same mass/rate without
pretending distance vanished.

## Representative accepted evidence

Root seed `1` with the production Stage-20F fixture yields:

| Measurement | Result |
|---|---:|
| finite extraction cadence rows | 113 |
| positive generated process rows | 312 |
| selected operational process rows | 1 |
| owned industrial freight routes | 1 |
| finite operational buffers | 1 |
| active yard construction rows | 1 |
| measurable cross-sector trade opportunities | 23 |
| representative freight payload | 12,000,000 kg |
| representative owned route one-way delivery | 993,258.655 s |
| representative owned route ready-again round trip | 1,986,685.310 s |
| representative owned route handling | 12 s |
| conductor-ore buffer depletion | 993,258.655 s |
| escort hull serial empty-pipeline supply ETA | 6,281,648.893 s |
| escort hull serial steady-state replenishment | 1,003,420.076 s |

The values are deterministic evidence for the accepted seed and current content fingerprints, not
universal balance constants.

## Acceptance

- mine cadence retains finite reserve and positive extraction rate;
- generated closure contains positive refining and component-manufacturing cadences;
- selected operation retains exact input consumption and output rates;
- every remote industrial input has distinct owned ships and a sustainable physical cycle;
- loading/unloading and round-trip ready-again time are explicit;
- initial buffers expose finite depletion time and cover retained first-delivery pipeline mass;
- active shipyard supply closes against the Stage-18 hull BOM;
- at least one positive cross-sector comparative-capacity trade opportunity exists;
- no hidden market restock is used;
- Stage-20F runtime materialization seams are neither hidden nor prematurely closed.

Stage 20K owns materialized campaign snapshot identity, deterministic codec and explicit
generator-version migration policy.
