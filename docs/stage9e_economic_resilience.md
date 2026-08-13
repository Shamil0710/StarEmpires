# Stage 9E — Economic resilience benchmark

## Status

**COMPLETE candidate.** Stage 9E proves the complete Stage-9 causal chain on the ordinary deterministic world simulation: destructive loss of a critical producer creates measurable shortages; faction pressure persists; the same Stage-9D response path funds a normal Stage-9B construction project; ordinary physical logistics delivers its materials; replacement capacity appears and performs a real production transform; the economic deficit then returns to its pre-shock baseline.

The stage is only considered complete after the final clean PR head passes the full Java 17 verification and is merged to `main`.

## Acceptance scenario

The deterministic seed is `0x9E5EED` (`10378989`). The scenario warms the ordinary three-system demo world for 1,000 fixed ticks and then destroys Corona's only foundry through the Stage-9C destruction API.

```text
stable Corona economy
        ↓
critical foundry destroyed
        ↓
steel capacity removed
        ↓
steel unmet demand and downstream weapons pressure rise
        ↓
persistent faction bottleneck observations
        ↓
miners fund a replacement ConstructionProject
        ↓
physical steel + energy delivery through TradeAI / TradeController
        ↓
materials fulfilled
        ↓
BUILDING
        ↓
replacement foundry completed
        ↓
replacement foundry emits real RESOURCE_TRANSFORM
        ↓
steel unmet demand returns to the pre-shock baseline
```

No benchmark code calls `deliverConstructionMaterial()` after the shock and no construction material is injected after the accounting baseline is captured.

## Measured deterministic result

The final Java 17 acceptance run produced:

| Metric | Result |
| --- | ---: |
| warmup / shock tick | 1000 |
| bottleneck detection tick | 1100 |
| investment decision tick | 1300 |
| first material delivery tick | 1330 |
| all construction materials fulfilled | 1330 |
| build start tick | 1330 |
| replacement completion tick | 1680 |
| economic recovery tick | 2060 |
| baseline steel unmet demand | 20 units |
| peak steel unmet demand | 374 units |
| peak downstream weapons unmet demand | 12 units |
| peak steel structural pressure | 1,800,000 basis points |
| project funding | 40,000 credits |
| delivered steel | 180 / 180 units |
| delivered energy | 120 / 120 units |
| foundries before shock | 1 |
| foundries immediately after shock | 0 |
| foundries after recovery | 1 |
| replacement project ID | 1 |
| money conservation | PASS |
| resource accounting | PASS |

The same-seed acceptance is executed twice and the complete `Stage9ERecoveryReport` must compare equal, including all causal ticks and deterministic economic metrics.

## Physical reserve fixture

Stage 10 inter-system logistics does not exist yet, so Stage 9E deliberately remains a local-system recovery proof rather than pretending to transport goods between star systems.

Before the shock, the scenario creates two ordinary faction-owned physical reserve carriers in Corona:

- `Corona Steel Reserve` — `MATERIAL_CARRIER` with physical steel cargo;
- `Corona Energy Reserve` — `GAS_LIQUID_CARRIER` with physical energy cargo.

Their cargo is assigned only after the authoritative lifecycle has registered the empty entities and before the benchmark captures initial physical-resource totals. Therefore those resources are part of the initial world state used by conservation accounting. They are not created after the shock.

The reserve carriers are explicitly held until the miners create the replacement project. At that deterministic event boundary their normal TradeAI routing is released. They then use the same route planner and TradeController as all other commercial fleets.

The destroyed foundry's remaining inventory is transferred through the Stage-9C destruction policy to a physical salvage vault; its destroyed wallet follows the explicit money-sink policy.

## World-level accounting

`Stage9EAccounting` reconciles the entire simulated world rather than only the target Corona system. It includes:

- every entity inventory in every star system;
- asteroid physical reserves;
- faction treasuries;
- entity wallets;
- explicit resource sources and sinks;
- recipe resource transforms;
- explicit money sources and sinks.

Trade, construction funding, taxation-style transfers and ordinary inventory transfers are transfers and therefore do not alter conserved totals.

For the measured run:

- initial total money: `6,933,000,000` milli-credits;
- final total money: `6,683,471,231` milli-credits;
- expected final money after explicit sinks/sources: `6,683,471,231` milli-credits;
- money delta after reconciliation: `0`;
- resource accounting delta: `0` for every active item.

## System defects exposed by the benchmark

Stage 9E was intentionally allowed to fail until the causal chain was physically valid. It exposed several structural problems that smaller Stage-9 tests did not reveal.

### 1. Spot scarcity pricing made construction economically impossible

An empty construction site originally inherited the normal market scarcity curve. A target of 180 steel with zero current stock created an extreme purchase price, so the data-driven 40,000-credit project wallet could not physically acquire its required materials.

Construction sites now use a runtime-derived `ProcurementPolicyComponent`. `ConstructionBidPolicy` derives bounded per-material bids from the persistent data-driven funding budget. The policy creates no money and no goods; it only defines how the project spends the money already transferred from its owner faction.

### 2. Construction inventory could be resold

A construction site originally remained a normal supplier once goods had been delivered. That allowed required materials to leave the site before completion.

A procurement market is now consumer-only: its sell price is always zero. When one material line reaches its target, that line's buy bid also becomes zero.

### 3. Profit-only logistics ignored faction construction priorities

A faction-owned carrier with already loaded cargo could rationally choose a higher-paying ordinary spot market instead of the faction's own strategic construction site.

`TradeAISystem` now checks same-faction active procurement markets first for already loaded compatible cargo. Importantly, it does not bypass the economic core: it builds a filtered `MarketDirectory` and invokes the same pure `TradeRoutePlanner`; the same `TradeController` executes the paid physical sale. If no valid same-faction procurement route exists, ordinary profit-per-time routing remains the fallback.

### 4. Existing-cargo sales could overfill target demand

`findBestExistingCargoSale()` originally limited an existing cargo sale by cargo amount and free inventory capacity, but not by outstanding target demand. A steel carrier could therefore put up to the entire 300-unit construction-site capacity into a project that required only 180 steel. `ConstructionProjectState.refresh()` correctly clamped reported delivered steel to 180, masking the physical overfill while leaving no space for the required 120 energy.

Existing-cargo route quantities are now capped by `targetStock - stock` whenever positive demand exists, matching the quantity semantics already used for new-cargo routes. Procurement bids also close when their target line is fulfilled.

This was the final defect preventing the full autonomous recovery chain.

## Persistence

`ProcurementPolicyComponent` is deliberately runtime-derived rather than a new authoritative schema field. On world restore, `ConstructionProjectService` reconstructs it from the persistent construction project and the same `ContentCatalog` before validating/refeshing the restored site. Existing WorldState v5 persistence therefore remains sufficient and no schema bump is required for the pricing policy.

## Definition of Done evidence

Stage 9E satisfies its functional Definition of Done when the final clean branch proves all of the following in one run:

- the critical producer physically disappears;
- steel shortage measurably exceeds baseline;
- downstream weapons pressure appears;
- persistent bottleneck observations lead to a funded replacement project;
- 180 steel and 120 energy arrive through normal logistics;
- construction reaches BUILDING and COMPLETED;
- the replacement foundry performs a real steel production transform;
- steel unmet demand returns to baseline;
- foundry count changes `1 -> 0 -> 1`;
- money reconciliation is exact;
- resource reconciliation is exact;
- same seed produces the same complete recovery report;
- normal Java 17 verification, JaCoCo, strict Javadoc and desktop packaging remain green.

## Handoff to Stage 10

Stage 9 is now functionally capable of self-repairing a local supply chain. Stage 10 should not add another economic model. It should provide the missing physical **inter-system transit layer** so the same markets, construction projects and faction priorities can source required cargo from another star system instead of relying on a local reserve fixture.
