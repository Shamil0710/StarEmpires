# Ship Pricing Roadmap

## Status

**PLANNED economic follow-up after the Stage 14B physical ownership boundary.**

Stage 14B intentionally accepts an explicit `PlayerShipSaleOffer.priceMilliCredits`. This keeps ownership transfer, seller payment and physical FleetId transfer correct while the production/shipyard economy is still incomplete. The explicit price is a temporary seam, not the final valuation model.

## Required pricing invariant

A ship offered for sale must eventually be valued from authoritative live economic state rather than a hidden player-only multiplier or fixed arbitrary table.

Conceptually:

```text
ship sale price =
    current replacement cost of hull materials/components
  + current replacement cost of installed equipment
  + production/shipyard cost and build-time opportunity cost
  + condition / repair / depreciation adjustment
  + local supply-demand adjustment
  + seller-faction relationship adjustment
  + seller-faction commercial margin
```

The final implementation may normalize or compose these terms differently, but all material price drivers must remain inspectable and testable.

## Required inputs

### Live component/material prices

Hull and equipment replacement cost must be derived from the current prices of their real production inputs in the seller's relevant economic region/system, not only immutable catalog `basePrice` values. War, shortages, destroyed factories, transport disruption and regional abundance must therefore be able to change ship prices naturally.

### Physical configuration of the actual FleetId

When fitting exists, valuation must use the installed configuration of the concrete ship instance:

- hull/archetype;
- engines/thrusters;
- reactor/power systems;
- shields;
- armor;
- sensors/ECM;
- weapons;
- cargo/mining/utility modules;
- ammunition and other priced installed stores where appropriate.

Two ships of the same hull may therefore have materially different prices.

### Ship type / production complexity

Ship type may affect price through physically/economically justified production factors such as hull complexity, required shipyard class, labor/build time, specialist components or scarce production capacity. Avoid unexplained `frigate x2`-style multipliers where the same result can emerge from real inputs and production constraints.

### Seller faction relationship

Reputation/relationship may modify the commercial terms within bounded data-driven limits:

- trusted/allied buyer: possible discount or preferential terms;
- neutral buyer: standard terms;
- distrusted buyer: surcharge or restricted availability;
- hostile/forbidden buyer: sale denied rather than merely made absurdly expensive where policy requires it.

Relationship modifiers should remain secondary to physical replacement cost unless a faction policy explicitly says otherwise.

### Seller faction margin

The seller faction/shipyard needs an explicit data-driven commercial margin or pricing doctrine. Different factions may pursue different margins without receiving hidden money or arbitrary player-specific prices. Margin belongs to the seller's economic policy and must be observable in valuation diagnostics.

### Local market pressure

Ship prices may react to regional demand/supply and production capacity. Examples:

```text
war + high military losses
→ demand for combat hulls/components rises
→ replacement inputs become scarce
→ shipyard queue/opportunity cost rises
→ combat ship prices rise
```

and:

```text
surplus civilian hulls + low freight demand
→ seller inventory/availability rises
→ margin/market adjustment falls
→ civilian ship prices soften
```

### Condition and depreciation

Once damage/repair/fitting state is rich enough, used ships must not be valued as pristine replacements. Valuation should account for hull/armor/shield/equipment condition, required repairs and optionally age/service depreciation. Rare/out-of-production hulls may receive scarcity premiums only from explicit market/content rules.

## Architecture target

Introduce a shared read-only valuation boundary such as:

```text
ShipValuationService / ShipyardPricingService
        ↓
ShipValuationBreakdown
        ↓
PlayerShipSaleOffer.priceMilliCredits
```

`PlayerShipProgressionService` should continue to validate the offer and delegate the real money/ownership transfer to `PlayerOwnershipService`; it should not become responsible for pricing economics.

The valuation breakdown should expose enough diagnostics for UI/tests:

- material/component replacement cost;
- installed equipment cost;
- production/shipyard cost;
- condition adjustment;
- local market adjustment;
- relationship adjustment;
- seller margin;
- final price;
- source market/system and price revision/freshness where relevant.

## Deterministic acceptance requirements

Future tests must prove at minimum:

1. raising the live price of a required component/material raises replacement value of dependent ships;
2. the same hull with different installed equipment receives a different price;
3. relationship changes commercial terms without changing physical ship state;
4. seller-faction margin changes seller price predictably and deterministically;
5. a damaged/used ship is valued differently from an otherwise identical pristine ship once condition economics exist;
6. hostile/restricted policy can deny a sale rather than only alter price;
7. valuation is deterministic for equal world/fleet/seller/player state;
8. purchase still transfers exactly the quoted amount between the real player wallet and real seller wallet with no money creation;
9. no player-only hidden ship valuation formula exists alongside the shared pricing service.

## Scheduling

Do not interrupt Stage 14C for this pricing depth. Preserve the explicit offer seam now. Implement the first production-grade valuation service when ship production/fitting and market inputs are sufficiently authoritative to support it, then deepen it with Stage 17.5 fitting/condition and later faction doctrine/economic tuning.
