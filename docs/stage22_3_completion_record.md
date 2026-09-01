# Stage 22 M22.3 — Empire production package completion record

> Status: **CLOSURE CANDIDATE — implementation complete; final status requires exact-head CI and merge of PR #348.**  
> Package: `core.empire`  
> Stable runtime/save identity: `faction.imperial_directorate`  
> Working PR: #348 — `Stage 22.3: Empire production package`

## 1. Scope and authority boundary

M22.3 promotes the Empire from the shared M22.0–M22.2 authoring seams into one end-to-end production package. It does not introduce an Empire-owned simulation authority.

The package composes existing owners:

- stable faction identity and migration remain M22.0 `FactionIdentityResolver` / world identity state;
- systemic policy/profile bindings remain the data-only M22.1 faction-profile contract;
- role, mission, lineage, production-manifest and visual-binding grammar remain M22.2 shared content seams;
- physical hull/module legality remains Stage 17.5 engineering authority;
- manufacturing, facilities, station infrastructure, inventory, freight and shipyard work remain Stage 18 authorities;
- NPC lifecycle, mission objective truth, discovery/knowledge and reputation remain Stage 21H authorities;
- persistence is compositional and fingerprint-bound; authored content never creates free runtime assets;
- presentation assets are downstream of exact engineering fits and never own collision, hardpoint, fitting or simulation state.

No faction-name damage, armor, sensor, income, repair or production multiplier was added.

## 2. M22.3 content floor

The validated Empire package contains:

- **9 ship families** covering the exact shared role taxonomy: six military and three civilian/support roles;
- primary + alternate/refit engineering fit for every family;
- **9 production manifests** for primary fits;
- exact-fit visual bindings for **18 primary/refit fits**;
- Stage-22-authored engineering/manufacturing/shipyard integration through the existing common authorities;
- **3 signature station variants** bound to existing Stage-18 station/facility definitions;
- **3 industrial bottleneck definitions**, **3 reserve-policy intents** and a priced legal cargo structural substitution path;
- **3 manufacturer/design/procurement lineages**;
- **6 recurring NPCs** promoted without changing accepted Stage-21H identity/role semantics;
- **10 faction-facing mission templates** whose objective truth stays in Stage-21H;
- **2 short authored story chains** built only from package mission templates;
- a practical Empire character lineup covering the seven mandatory M22.3 functions plus logistics/recon support overlays;
- a nine-family production ship visual catalog with base, emissive and damage layers plus shared idle/thrust engine VFX.

## 3. Systemic and industrial evidence

`Stage22EmpireFactionProfileCatalog` promotes the Empire profile while preserving the M22.1 data-only policy model. Centralized procurement, readiness, knowledge, logistics and recovery preferences bind to existing common authority seams rather than mutating outcomes directly.

`Stage22EmpireIndustrialProgram` makes the intended Empire industrial identity visible through real dependencies:

- precision components constrain sensors, fire control, complex support and repair;
- refractory alloy constrains high-temperature machinery, weapons and repair;
- heavy components expose the capital-heavy hull/drive/support burden;
- reserve targets are planning intent only and require ordinary procurement, inventory and freight;
- the alternate cargo structural route reduces one structural-alloy burden only by consuming another accepted material and paying higher process energy/work.

`Stage22EmpireBalanceTelemetry` is diagnostic only. It derives visible capital mass share, support mass share, carrier projection bundle burden, battleship/corvette physical hierarchy, crew burden, yard concentration and repair coverage from legal package data. No telemetry value is fed back as a gameplay modifier.

## 4. Hull, fleet and shipyard evidence

`Stage22EmpirePackageValidator` proves for every required family that:

- primary and refit fits use the same legal hull but distinct exact fit fingerprints;
- hull mass, power and thermal budgets remain non-negative;
- every installed module has Stage-22 authored product provenance;
- every module has an ordinary manufacturing binding/profile;
- yard support facilities expose the required manufacturing capabilities;
- hull/module physical service profiles and industrial profiles exist in the accepted shipyard authorities;
- the common Empire yard envelope can service each authored hull;
- production manifests match exact primary hull, fit, installed modules and required facilities;
- every family has a real repair path;
- freight, tanker and fleet-logistics/repair/salvage families make support burden part of the fleet package.

M22.3 intentionally does not perform final paired tuning. Industrial Union comparison and pairwise balancing remain later Stage-22 milestones.

## 5. Ship visual gate

`Stage22EmpireShipVisualCatalog` remains presentation-only and derives world dimensions and hardpoint/service anchors from the accepted engineering catalog.

Production visual evidence includes:

- nine distinct family marker silhouettes;
- nine distinct alpha-mask silhouettes before color/heraldry;
- one base PNG, aligned emissive PNG and aligned damage PNG per family;
- shared engine idle/thrust VFX;
- production resource existence checks;
- alpha coverage/readability bounds;
- emissive/damage alignment bounds;
- exact engineering dimensions and engineering-hardpoint/service-compartment anchors;
- canonical provenance `docs/factions/empire_visual_bible.md`;
- exact fit fingerprint diagnostics for all 18 primary/refit fits.

The final **Empire-vs-Industrial-Union** grayscale blind comparison cannot be truthfully completed before M22.4 authors the second package. It is therefore not fabricated here; it remains a pairwise visual acceptance item for the Union/core-pair milestones. M22.3 closes the Empire-side silhouette/readability/provenance gate.

## 6. Character gate

The character package composes, in the required order:

```text
shared Character Master Prompt
+ Empire visual bible
+ role brief
+ individual/condition brief
```

`Stage22EmpireCharacterLineup` enforces the canonical references:

- `docs/characters/character_master_prompt.md`;
- `docs/factions/empire_visual_bible.md`.

The mandatory lineup covers:

- industrial worker/technician;
- fleet enlisted specialist;
- line officer;
- senior officer;
- civil administrator;
- noble/high official;
- damaged/tired field variant.

Additional logistics and recon overlays reuse the same art-style authority. Status remains readable through cut, material quality, equipment discipline and restrained insignia; the field-worn variant explicitly preserves faction/rank readability and the same art style.

`Stage22EmpireCharacterLineupTest` provides deterministic fingerprint/reference checks, required-role coverage, hierarchy/style assertions and fail-closed tests for wrong authority references, namespace escape, duplicate IDs and required-role loss.

## 7. Persistence and deterministic fingerprint contract

M22.3 keeps state ownership compositional:

- package, production, engineering, manufacturing, shipyard, station, visual and character catalogs expose deterministic semantic fingerprints;
- the accepted M22.1 faction-profile sidecar is captured against the existing resolver, encoded, decoded and validated without identity reinterpretation;
- stable runtime/save identity remains `faction.imperial_directorate`; authored package key remains `core.empire`;
- tests reject silent replacement by `faction.empire`;
- exact primary/refit visual bindings are pinned to computed engineering fit fingerprints;
- malformed, stale or incompatible authored references fail closed.

No save/load path regenerates ships, cargo, treasury, knowledge, repair or mission outcome.

## 8. Solo B00–B14 gate

`Stage22EmpireSoloSmokeAcceptanceTest` implements the required M22.3 solo scenario gate as causal integrity/content-legality smoke evidence. These tests deliberately do not claim final pairwise outcome tuning.

| Scenario | M22.3 evidence |
|---|---|
| B00 Catalog/authority audit | deterministic package/production/profile fingerprints and preserved stable identity |
| B01 Save/load/replay round-trip | deterministic profile-binding codec round-trip against resolver |
| B02 Viable cold start | all nine manifests/families and required yard facilities exist without hidden dependency |
| B03 Planned expansion | three signature variants resolve to real Stage-18 station/facility definitions |
| B04 Critical-material shortage | finite precision/heavy/electrical construction inputs; no Empire free-resource vocabulary |
| B05 Single hub/route loss | production-yard concentration is visible in telemetry |
| B06 Distributed raids | patrol/escort/support roles and route-defense mission path exist |
| B07 Equal-burden patrol contest | patrol fit has legal mass/power/thermal margins |
| B08 Convoy escort/interdiction | Stage-21H fleet objective + real freight support content |
| B09 Prepared-system defense | capital reserve role + arsenal/depot infrastructure |
| B10 Forced offensive projection | carrier projection requires additional tanker/support physical mass |
| B11 Degraded command/sensors | recon objective and knowledge policy remain bounded by discovery/knowledge authority |
| B12 Magazine-limited engagement | finite ammunition interfaces exist on authored weapon modules |
| B13 Long war/rolling attrition | all nine families retain ordinary repair/service coverage |
| B14 Post-war recovery | repair/salvage support family and grounded repair/recovery mission paths |

Final paired multi-seed outcome tuning, B18–B20 cross-faction review and Stage-22 freeze remain later milestone responsibilities.

## 9. Principal automated evidence

Targeted M22.3 coverage includes:

- `Stage22EmpirePackageLoaderTest`;
- `Stage22EmpirePackageAcceptanceTest`;
- `Stage22EmpireSoloSmokeAcceptanceTest`;
- `Stage22EmpireIndustrialProgramAcceptanceTest`;
- `Stage22AuthoredProductionBridgeTest`;
- `Stage22AuthoredShipyardIndustrialBridgeTest`;
- `Stage22EmpireShipyardRuntimeAcceptanceTest`;
- `Stage22EmpireProfilePersistenceAcceptanceTest`;
- `Stage22EmpireShipVisualCatalogTest`;
- `Stage22EmpireCharacterLineupTest`;
- upstream Stage-22 governance/profile/shared-seam suites retained in the full repository verify.

The stage is not declared merged/complete by this record alone. Exact PR-head CI, PR readiness, merge and post-merge `main` verification are the final closure steps required by `docs/development_roadmap.md` and the project development controller.

## 10. Intentionally deferred beyond M22.3

M22.3 does **not** implement:

- Industrial Union production content (M22.4);
- shared civilian/minor ecosystem expansion (M22.5);
- paired Empire/Union tuning and freeze (M22.6);
- cross-faction grayscale silhouette comparison before Union production art exists;
- final faction-vs-faction dominance tuning;
- post-core sovereign faction packages;
- Stage-23 polish/release-candidate work.

These are explicit later-stage boundaries, not missing Empire-package authorities.
