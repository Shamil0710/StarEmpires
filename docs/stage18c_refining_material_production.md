# Stage 18C — Refining and material production

Status: implementation slice on `agent/stage18c-refining-materials`.

## Ownership

Stage 18C owns the first data-driven transformation boundary between Stage 18A extracted feedstocks and Stage 18A engineering materials / industrial consumables.

It does **not** own:

- component fabrication, module recipes or ammunition recipes — Stage 18D;
- facility archetypes and installed production-line capability — Stage 18E;
- persistent station storage, logistics and hauling — Stage 18F;
- shipyard / repair / refit integration — Stage 18G.

Legacy `ContentCatalog` item IDs and their save fingerprint remain untouched.

## Production chain

The implemented baseline closes the following Stage-18C outputs:

| Recipe | Input feedstock | Retained output |
|---|---|---|
| `refining.water_purification` | water ice | purified water |
| `refining.volatile_separation` | volatile feedstock | industrial gases |
| `refining.industrial_chemicals` | carbonaceous + volatile feedstock | industrial chemicals |
| `refining.structural_alloy` | metallic ore | structural alloy |
| `refining.light_alloy` | light-metal minerals | light alloy |
| `refining.conductor_metal` | conductor ore | conductor metal |
| `refining.refractory_alloy` | strategic-metal + metallic ore | refractory alloy |
| `refining.ceramic_glass` | silicate minerals | ceramic/glass material |
| `refining.carbon_material` | carbonaceous feedstock | carbon material |
| `refining.electronic_grade_material` | silicate + conductor + strategic feedstock | electronic-grade material |
| `refining.reactor_fuel` | fissile minerals | reactor fuel |

The v1 coefficients are gameplay-scale baseline abstractions, not claims of real-world chemical or metallurgical yield. Their role is to make mass, energy, work, maintenance and storage costs explicit and balanceable.

## Invariants

Every recipe is validated against the Stage 18A ontology and must satisfy all of these conditions:

1. every input is an `EXTRACTED_FEEDSTOCK`;
2. the output is an `ENGINEERING_MATERIAL` or `INDUSTRIAL_CONSUMABLE`;
3. input mass fractions sum to `1.0`;
4. retained-output fraction plus discarded-tailings fraction sums to `1.0`;
5. all required capability tags exist in the ontology;
6. energy, engineering work and maintenance work costs are finite and positive;
7. component families cannot be produced by an 18C recipe.

## Runtime settlement

`Stage18RefiningRuntime` performs preflight before mutating state. A batch is accepted only when all required conditions are simultaneously satisfiable:

- all input feedstocks are physically present;
- the process capability contains every recipe tag;
- the interval contains enough process energy;
- the interval contains enough engineering work-seconds;
- the interval contains enough maintenance work-seconds;
- the retained output has compatible storage capacity.

On success the runtime consumes all inputs and budgets, stores the retained output and reports discarded mass. On rejection no input mass or interval budget is consumed.

Output capacity is evaluated after accounting for input mass removed from the same storage class. This prevents a full dry-bulk hold from incorrectly blocking an ore-to-alloy conversion whose smaller retained output replaces part of the consumed ore.

## Stage 18B compatibility

`PhysicalMaterialStore.fromExtractionCargo(...)` can seed an 18C material store from the current Stage-18B `PhysicalCargoStore` snapshot. The storage-capacity map remains explicit at this handoff because shared persistent storage and logistics are deliberately deferred to Stage 18F.

This keeps 18C compatible with 18B without prematurely making either slice the final station-storage architecture.

## Validation target

The slice includes tests for:

- complete coverage of all eleven 18C material/consumable outputs;
- deterministic catalog fingerprinting and mass closure;
- rejection of component outputs and malformed mass balance;
- successful single-input refining;
- multi-feedstock consumption;
- atomic rejection on insufficient process power;
- atomic rejection on missing process capability;
- storage-class incompatibility;
- capacity released by consumed input in the same storage class.

The next implementation owner after 18C is Stage 18D: components plus module/ammunition recipes consuming these material families.
