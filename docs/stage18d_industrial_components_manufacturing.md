# Stage 18D — Industrial components and manufacturing recipes

Status: implementation slice on `agent/stage18d-components-manufacturing`.

## Ownership

Stage 18D owns the physical transformation from Stage-18C engineering materials/industrial consumables into compact industrial components and then into existing Stage-17.5 modules/ammunition.

It does **not** own:

- station archetype bonuses or installed-facility throughput — Stage 18E;
- persistent station inventory, cargo transfer or hauling — Stage 18F;
- shipyard construction/refit/repair integration — Stage 18G;
- salvage/recycling recovery yields — Stage 18H;
- final promotion/re-authoring of content-provisional Stage-17.5I definitions — Stage 22.

## Component layer

The baseline deliberately stays compact:

```text
Stage-18C materials / consumables
→ HEAVY_COMPONENTS
→ ELECTRICAL_COMPONENTS
→ PRECISION_COMPONENTS
```

Each component recipe is mass-closed. Its input commodity fractions sum to `1.0`, so one kilogram of finished component requires one kilogram of physical material/consumable input. Energy, engineering work and maintenance work are additional finite process costs, not fictitious material mass.

The three recipes preserve the distinctions required by the Stage-18 plan:

- `HEAVY_COMPONENTS` emphasize structural/light/refractory material and heavy fabrication;
- `ELECTRICAL_COMPONENTS` emphasize conductor, ceramic, chemicals and electrical fabrication;
- `PRECISION_COMPONENTS` emphasize electronic-grade material, controlled conductor/refractory inputs and precision fabrication.

## Finished-product profiles

Stage 18D does not create generic `weapon tier 2` or `module unit` commodities. Reusable manufacturing profiles describe physical mass composition, while bindings point to real existing content IDs.

The baseline profiles cover:

- reactors;
- main drives;
- energy storage;
- sensors/EW/fire control;
- thermal control;
- shields;
- datalinks;
- kinetic, missile, beam and point-defense launcher modules;
- kinetic ammunition;
- guided ammunition.

A finished operation uses the authoritative physical unit mass from the Stage-17.5 definition. Example: `module.reactor_5gw_v1` has a physical mass of 2,200,000 kg, therefore one manufactured unit consumes 2,200,000 kg of the recipe's material/component shares before it can enter finished inventory.

Guided-ammunition `INDUSTRIAL_CHEMICALS` currently represent the baseline energetic/propellant material share. This is intentionally not a claim that all future missiles use one universal propellant. A technology-specific commodity should be split out only when source/storage/strategic behavior justifies it under the Stage-18 simplification rule.

## Existing content coverage and provenance

`Stage18ManufacturingProductRegistry` loads products through the existing production Stage-17.5 loaders rather than copying their masses into Stage-18 JSON.

Current coverage is complete for the present repository content:

```text
23 module identities
+ 6 physical ammunition identities
= 29 bound finished products
```

Stage-17.5I combat/doctrine definitions retain `STAGE17_5I_CONTENT_PROVISIONAL` provenance. Manufacturing compatibility means only that the economy can account for their physical production inputs; it does not promote them to final Stage-22 content.

## Capability seam

Two capability tags are added to the Stage-18 ontology:

- `capability.fabrication.assembly`;
- `capability.fabrication.ordnance`.

They are ordinary capability requirements, not station-class bonuses. Stage 18E will determine which installed facilities expose them, together with throughput, power, work, heat, maintenance and location constraints.

## Runtime invariants

`Stage18ManufacturingRuntime` preflights every operation before mutation. A component or product is committed only if all conditions are simultaneously satisfied:

1. all material/component inputs are present;
2. every required fabrication capability is present;
3. process energy is sufficient;
4. engineering work is sufficient;
5. maintenance work is sufficient;
6. compatible output storage has enough mass capacity after accounting for inputs removed from that same storage class.

On rejection, no input mass and no interval budget are consumed.

`ManufacturingInventory` stores Stage-18 commodity mass and countable finished modules/ammunition together under shared storage-class mass limits. This is a local Stage-18D settlement boundary only; Stage 18F remains the owner of persistent station storage/logistics.

## Legacy `item.weapons`

Stage 18D replaces the conceptual need for a generic finished-weapon production abstraction by mapping real modules/ammunition to physical recipes. Existing legacy `item.weapons` quantities are **not** silently converted, deleted or assigned invented kilograms. They remain a compatibility item until an explicit inventory migration/conversion policy is authored.

## Validation target

The slice tests:

- exactly three baseline component families;
- all thirteen manufacturing profiles;
- exact coverage of all 29 current Stage-17.5 manufactured identities;
- preservation of provisional content provenance;
- rejection of raw feedstock in component fabrication;
- exact recipe/profile mass closure;
- physical module unit mass taken from Stage-17.5 content;
- finite countable ammunition production;
- energy/work/maintenance/capability gates;
- storage-class constraints;
- atomic rejection with no partial consumption;
- handoff from the Stage-18C material-store snapshot without legacy `ItemType` conversion.

The next owner after Stage 18D is **Stage 18E — facility capability architecture**.
