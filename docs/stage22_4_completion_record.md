# Stage 22 M22.4 — Industrial Union production package completion record

> Status: **CLOSURE CANDIDATE — implementation content complete; final status requires exact-head CI, merge of PR #351 and post-merge `main` verification.**  
> Package: `core.industrial_union`  
> Stable runtime/save identity: `faction.industrial_combine`  
> Working PR: #351 — `Stage 22.4: Industrial Union production package`

## 1. Scope and authority boundary

M22.4 promotes the Industrial Union from the accepted M22.0–M22.3 shared/core contracts into one end-to-end production package. It does not introduce a Union-owned simulation authority.

The package composes existing owners:

- stable faction identity and migration remain M22.0 identity/resolver authority;
- systemic policy/profile bindings remain the data-only M22.1 faction-profile contract;
- role, mission, production-manifest and visual-binding grammar remain M22.2 shared content seams;
- physical hull/module legality remains Stage 17.5 engineering authority;
- manufacturing, facilities, station infrastructure, inventory, freight and shipyard work remain Stage 18 authorities;
- NPC lifecycle, mission objective truth, discovery/knowledge and reputation remain Stage 21H authorities;
- Union production-side persistence owns only per-yard series qualification/commonality/retool debt and is fingerprint-bound;
- presentation assets are downstream of exact engineering fits and never own collision, hardpoints, fitting or simulation state.

No faction-name damage, armor, sensor, income, repair, inventory or free-production outcome was added. Production effects arise only from explicit series state, paid retool debt and observed common physical dependency availability.

## 2. M22.4 content floor

The validated Industrial Union package contains:

- **9 ship families** covering the exact shared role taxonomy: six military and three civilian/support roles;
- primary + distinct refit engineering fit for every family (**18 legal fits** total);
- **9 production manifests** for primary fits;
- **18 exact-fit visual bindings** backed by nine production base sprites;
- **9 authored hulls** and **10 authored modules**, including four common assemblies repeated across every series;
- Stage-22-authored engineering/manufacturing/shipyard integration through existing common authorities;
- **3 industrial station variants** bound to existing Stage-18 station/facility definitions;
- **3 assembly series** covering all nine families;
- one ordinary series yard with finite physical hull/module service profiles;
- **7 recurring NPCs** promoted without changing accepted Stage-21H identity/role semantics;
- **11 faction-facing mission templates** and **2 short story chains**;
- **8 character overlays** covering the required Industrial Union production/maintenance/officer/logistics/administration/field functions;
- a nine-family production ship base-sprite catalog governed by `docs/factions/industrial_union_visual_bible.md`.

## 3. Distinct systemic identity and serial-production evidence

`Stage22IndustrialUnionIndustrialProgram` makes the Union's intended identity measurable through ordinary production causality rather than a faction-name bonus.

The package defines three explicit series:

- screen: corvette / frigate / destroyer;
- capital: cruiser / battleship / carrier;
- logistics: freight / tanker / fleet-support.

All three repeatedly use the same four physical common assemblies:

- reactor bank;
- drive bank;
- sensor block;
- radiator panel.

Series qualification is not free. Initial qualification and every cross-series changeover require **259,200 work-seconds** and **2.4 × 10^12 J** before production can resume. Incomplete changeover fails closed and resets commonality on completion.

Same-series efficiency is earned from completed units:

- cold qualified production: work multiplier `0.96`, energy multiplier `0.98`;
- steady series: work multiplier `0.90–0.92`, energy multiplier `0.92–0.94` depending on series;
- steady thresholds: 3 units for screen/logistics and 4 for capital;
- maximum authored build-work reduction is **10%**;
- maximum steady throughput increase implied by work is about **11.11%**, below the formal 15% M22.4 cap.

Materials and manufacturing capabilities are unchanged by the projection. The common Stage-18 production grammar still consumes the ordinary finite inputs.

This is intentionally not an Empire cost discount. The Union advantage is conditional on series continuity, common assemblies and material flow; abrupt adaptation incurs finite retool debt.

## 4. Correlated fragility and observability evidence

`Stage22IndustrialUnionCommonalityNetwork` supplies the required systemic downside without branching on faction identity.

Inputs are observed availability of ordinary physical dependency domains:

- four repeated common assemblies;
- bulk logistics;
- yard facilities.

The projection uses weighted availability (50% assemblies / 30% bulk logistics / 20% yard facilities) and converts missing availability into additional ordinary process work/energy burden. It owns no inventory, freight, facility-health or construction state.

Acceptance evidence fixes two materially different cases:

- isolated 25% degradation of one of four common assemblies produces only about **4.76% throughput degradation** and is not classified as correlated disruption;
- correlated 25% degradation across all common assemblies, bulk logistics and yard facilities produces work burden `1.40`, energy burden `1.10` and about **28.57% throughput degradation** versus the same healthy steady-series state.

The latter is above the required 25% material-degradation floor and demonstrates that commonality creates a real shared bottleneck surface rather than an unconditional buff.

Read-only diagnostics expose:

- active assembly series;
- commonality streak;
- exact shared-assembly availability;
- bulk-logistics and yard-facility availability;
- deterministic bottleneck dependency;
- network availability;
- work/energy burden;
- effective series multipliers;
- throughput relative to the healthy same-series state;
- correlated-disruption flag.

No telemetry value writes back into gameplay state.

## 5. Hull, fleet and shipyard evidence

`Stage22IndustrialUnionPackageValidator` proves for every required family that:

- primary/refit fits share one legal hull while retaining distinct exact fit fingerprints;
- fitted dry mass remains inside operational mass;
- continuous power and thermal margins are non-negative;
- staffed crew follows the accepted Stage-17.5 runtime rule `max(hull baseline, module crew burden)` and remains inside life-support capacity;
- every installed module has Stage-22 authored product provenance;
- every module has an ordinary manufacturing binding/profile;
- yard support facilities expose the required manufacturing capabilities;
- hull/module physical service profiles and industrial profiles exist in accepted shipyard authorities;
- the Union yard envelope can service each authored hull;
- production manifests match the exact primary hull, fit, installed modules and required facilities.

The package makes logistics assets first-class content: freight, tanker/replenishment and fleet logistics/repair/salvage are part of the same commonality family rather than decorative background ships.

M22.4 intentionally does not perform final Empire-vs-Union paired tuning. That remains M22.6.

## 6. Station, NPC and mission evidence

The three authored station variants resolve only to existing Stage-18 infrastructure and may claim only facilities actually installed by the selected archetype. Validation fails closed on a missing facility/archetype relationship.

The package contains seven recurring NPCs spanning accepted Stage-21H roles, including production/yard, military, logistics, official, exploration/intelligence and frontier functions. Their character overlays remain presentation metadata; they do not own NPC lifecycle or inventory.

The eleven mission templates remain within Stage-21H mission/objective authority. Validation proves issuer-role compatibility, objective-template compatibility and expected authority for every mission. Two authored story chains contain only package mission templates in authored order.

## 7. Ship visual gate

Industrial Union production ship art is governed by the canonical `docs/factions/industrial_union_visual_bible.md` rather than by Empire presentation rules.

Production evidence includes:

- nine separate, role-authored production base PNGs, one per required family;
- normalized `768x512` true-alpha canvases with forward/right facing and transparent corners;
- material, panel, service and wear detail ranging from 29,114 to 65,642 colors and from 140,666 to
  356,864 bytes per production PNG;
- visual proportions checked against each authored hull's physical length/width envelope;
- a contact-sheet and reproducible prompt/normalization audit in
  `docs/stage22_4_industrial_union_sprite_audit.md`;
- strict production resource existence checks;
- automated placeholder/detail, uniqueness, alpha, centering and physical-aspect regression checks in
  `Stage22IndustrialUnionProductionSpriteTest`;
- exact-fit bindings for all 18 primary/refit engineering fits;
- computed engineering fit fingerprints, so stale visual bindings fail closed;
- repeated common visual language across propulsion/service/sensor sections;
- role-readable distinctions for military and logistics/support silhouettes;
- explicit absence of an Imperial hierarchy/citadel presentation contract in Union authoring;
- presentation-only ownership: sprite assets cannot define physics, hardpoints, fit legality or simulation state.

The final pairwise grayscale/blind Empire-vs-Union comparison and any final cross-faction art tuning remain M22.6 pair acceptance, not a fabricated M22.4 result.

## 8. Character gate

The character package composes the required authority chain:

```text
shared Character Master Prompt
+ Industrial Union visual bible
+ role/function brief
+ individual/condition brief
```

`Stage22IndustrialUnionCharacterLineup` pins the canonical references:

- `docs/characters/character_master_prompt.md`;
- `docs/factions/industrial_union_visual_bible.md`.

Required functions include:

- assembly worker;
- maintenance specialist;
- production engineer;
- ship/fleet officer;
- logistics coordinator;
- plant director / technical administrator;
- field-repair variant.

An eighth overlay extends the practical lineup without copying Empire costume hierarchy. Status is expressed through qualification, responsibility, equipment precision and material quality. Namespace, document-reference, duplicate-ID and role-floor validation fail closed.

## 9. Persistence and security boundary

`Stage22IndustrialUnionProductionStateCodec` persists only the bounded production-side state required by M22.4:

- stable faction identity;
- package fingerprint;
- monotonic sequence;
- per-yard active/pending series;
- completed-unit/commonality counters;
- remaining retool work and energy.

The codec is deterministic and fail closed:

- 128 KiB total state limit;
- 1 KiB per text field;
- maximum 128 yard records;
- magic/file/envelope version checks;
- truncated, corrupt, future-version and trailing-byte rejection;
- byte-stable encode/decode round trip;
- bounded filesystem read;
- temporary-file write followed by atomic replace where supported, with safe replace fallback.

Large authored resources use `Stage22AuthoredResourceFragments`, which requires an explicit ordered list, rejects missing/empty fragments, caps reconstructed documents at 2 MiB and then hands the result to the ordinary validated catalog loaders.

The M22.4 diff introduces no process execution, network access, dynamic code loading or faction-specific hidden authority.

## 10. Solo B00–B14 gate

`Stage22IndustrialUnionSoloSmokeAcceptanceTest` implements the required M22.4 solo scenario gate as causal-integrity/content-legality evidence. It does not claim final pairwise balance tuning.

| Scenario | M22.4 evidence |
|---|---|
| B00 Catalog/authority audit | deterministic package/production fingerprints, exact 9-role coverage, NPC/mission/story floor |
| B01 Save/load/replay round-trip | byte-stable production-state codec round-trip |
| B02 Viable cold start | first qualification requires positive finite retool work and energy |
| B03 Planned expansion | all three station variants resolve to real Stage-18 station/facility definitions |
| B04 Critical-material shortage | four common assemblies consume ordinary heavy/electrical/precision inputs; no faction-resource vocabulary |
| B05 Single hub/route/shared-network loss | isolated loss stays small while correlated common-domain loss exceeds the 25% degradation floor |
| B06 Distributed raids | patrol/escort/freight roles and route-defense mission path exist |
| B07 Equal-burden patrol contest | frigate fit has legal mass/power/thermal margins |
| B08 Convoy escort/interdiction | Stage-21H fleet objective plus real freight content |
| B09 Prepared-system defense | capital reserve family plus bulk hub/series-yard infrastructure |
| B10 Forced offensive projection | carrier projection carries explicit tanker and support physical mass |
| B11 Degraded command/sensors | recon mission remains bounded by discovery/knowledge authority |
| B12 Magazine-limited engagement | authored weapon modules expose finite ammunition interfaces |
| B13 Long war/rolling attrition | every family retains ordinary physical shipyard repair/service coverage |
| B14 Post-war recovery | repair/salvage support hull plus finite repair-input and salvage-feed mission paths |

Final paired multi-seed outcomes and cross-faction review remain M22.6 responsibilities.

## 11. Principal automated evidence and closure sequence

Targeted M22.4 coverage includes:

- `Stage22AuthoredResourceFragmentsTest`;
- `Stage22IndustrialUnionIndustrialProgramTest`;
- `Stage22IndustrialUnionCommonalityNetworkTest`;
- `Stage22IndustrialUnionPackageAcceptanceTest`;
- `Stage22IndustrialUnionPackageValidatorTest`;
- `Stage22IndustrialUnionProductionSpriteTest`;
- `Stage22IndustrialUnionSoloSmokeAcceptanceTest`;
- full repository Stage-17.5/18/21/22 regression verification through CI.

Implementation-review state at creation of this record:

- implementation PR: #351;
- base: `main` at `6dbc0bbc1551d4b9359d726a03498b1c812d5a5b`;
- review submissions: none;
- unresolved review threads: none;
- `main` has not advanced from the PR base;
- implementation code candidate immediately before this record: `e2061cc858bbb9be88aec19063a6445a1467fb2f`;
- exact-head CI for the final documentation-bearing implementation tree is still required before merge.

M22.4 becomes **COMPLETE** only after the following sequence succeeds:

1. final PR head passes full Java-17 CI (`clean verify`, tests, coverage, Javadoc and desktop packaging);
2. PR #351 is re-audited at that exact head and merged without head drift;
3. resulting `main` commit passes post-merge CI;
4. a docs-only closeout records the exact implementation head, merge commit and post-merge CI and advances the canonical roadmap.

## 12. Intentionally deferred beyond M22.4

M22.4 does **not** implement:

- M22.5 civilian/minor ecosystem expansion;
- M22.6 paired Empire/Union multi-seed balance tuning and freeze;
- final Empire-vs-Union grayscale/blind silhouette comparison;
- final cross-faction dominance tuning;
- post-core sovereign faction production packages;
- Stage-23 polish/release-candidate work.

These are explicit later-stage boundaries, not missing Industrial Union authorities.

## 13. Next

**M22.4 remains the only active milestone until the closure sequence above is complete. M22.5 is blocked.**
