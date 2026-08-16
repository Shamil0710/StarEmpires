# Star Empires — Stage 17.5F Shields / Armor / Compartments / Subsystem Damage

> Статус: **IMPLEMENTED — awaiting exact-head merge gate**  
> Stage: **17.5F**  
> Base main: `50314a43a26086a44e6b861072e316bfbbbe5ae6`  
> Назначение: закрыть authoritative protection/damage foundation между Stage-17.5E physical weapons и Stage-17.5G repair/refit, не вводя global-HP-only survivability и не создавая второй mass/power/heat/fitting model.

---

# 1. Главный результат

Stage 17.5F вводит единый физический путь для локального кинетического воздействия:

```text
physical projectile body
→ optional fitted shield coverage
→ finite field reserve / interaction power / heat
→ residual physical projectile
→ ordered protection stack
→ bounded heavy-impact response
→ STOPPED / RICOCHET / PERFORATED
→ aggregate fragments / spall
→ hull-local hit point
→ compartment
→ explicitly located fitted mounts
→ central DamageState
→ damage-aware DerivedShipState / sensor / weapon capability
```

Ключевой architectural rule:

> **Damage state изменяет уже существующие физические capabilities. Он не создаёт параллельные `damageBonus`, `classHP`, `armorChance` или player-only правила.**

Mass/volume authoring остаётся в Stage-17.5A schema, physical projectile state — в Stage 17.5E, common derived budgets — в `DerivedShipCalculator`.

---

# 2. Protection/damage content sidecar

Добавлены:

- `ShipProtectionCatalog`;
- `ShipProtectionCatalogLoader`;
- `data/content/ship-protection-runtime-v1.json`.

Stage-17.5A уже владеет:

- `MaterialDefinition`;
- `HeavyImpactResponseSurfaceDefinition` + explicit calibration domain;
- `ProtectionStackDefinition` / ordered layers;
- `HullDefinition`;
- compartments;
- slots/hardpoints;
- modules/fits.

Для local subsystem damage не хватало одного production fact:

```text
hull-local mount ID
→ containing compartment ID
```

Stage 17.5F не выводит эту связь из имени mount, класса корабля или role label. Вместо этого `HullDamageLayout` явно задаёт:

```text
compartment ID
→ structural damage capacity
→ subsystem coupling

mount ID
→ compartment ID
→ subsystem damage capacity
```

Loader валидирует все references против production `ShipEngineeringCatalog` и требует coverage всех authored hull compartments и mounts конкретного damage-layout hull.

Это sidecar только для protection/damage topology; он не дублирует mass/volume/power/heat/economy budgets.

---

# 3. Shield runtime

`ShieldFieldRuntime` реализует field как конечную физическую систему, а не как дополнительный HP bar с бесплатным восстановлением.

Definition учитывает:

```text
mount ID
field reserve J
interaction power W
recharge power W
recharge efficiency
heat per absorbed J
collapse/restart delay
coverage center / half arc
```

Persistent/runtime state учитывает:

```text
remaining reserve J
accumulated interaction heat J
collapsed state
restart remaining time
emitter integrity
```

Threat interaction ограничивается одновременно:

- coverage geometry;
- remaining field reserve;
- interaction power × interaction time;
- emitter integrity.

Recharge принимает только explicitly granted electrical power. `ShieldFieldRuntime` не создаёт power самостоятельно.

Emitter damage:

- уменьшает effective reserve capacity;
- уменьшает interaction/recharge capability;
- не может восстановить field energy;
- уничтоженный emitter не создаёт защитное поле.

`ShipShieldEngineeringAdapter` проецирует ordinary fitted `SHIELD_FIELD` module capability в этот runtime. Stage-17.5I Combat Test Content Pack позднее обязан дать representative production-valid shield modules/fits; Stage 17.5F не превращает текущий schema demonstrator в финальный content roster.

---

# 4. Heavy-impact material response

`HeavyImpactResolver` работает только через существующие ordered `ProtectionStackDefinition` и Stage-17.5A response-surface IDs.

Для каждого layer учитываются:

```text
material density
layer thickness
layer orientation
coverage fraction
projectile cross section
impact angle
projectile mass
projectile velocity / kinetic energy
bounded response coefficients
```

Terminal outcomes:

- `STOPPED`;
- `RICOCHET`;
- `PERFORATED`.

## No silent extrapolation

Перед material response resolver проверяет projectile mass/velocity против explicit `CalibrationDomainDefinition` response surface.

Outside-domain impact вызывает `OutsideCalibrationDomainException`.

Запрещено:

```text
unknown / uncalibrated impact
→ silently clamp inputs
→ pretend authoritative armor result
```

## Synthetic demonstrator warning

Текущий `response.synthetic_heavy_v1` и Stage-17.5F coefficients являются **synthetic API/runtime demonstrator data**.

Они нужны для:

- deterministic runtime contract;
- stop/ricochet/perforation path;
- no-extrapolation enforcement;
- compartment/subsystem integration tests.

Они **не являются утверждением о реальной бронестойкости** и не являются финальным Stage-22 balance/content dataset.

Stage 22 может заменить response datasets/calibration coefficients без изменения runtime architecture.

---

# 5. Ricochet / residual bodies / fragments

Ricochet является explicit material-response outcome, а не random chance.

Текущий demonstrator использует authored shallow-angle threshold внутри того же bounded calibration domain.

При ricochet:

- projectile сохраняет stable physical identity semantics;
- residual kinetic energy остаётся физической величиной;
- internal compartment damage не создаётся автоматически;
- external aggregate fragment/spall output остаётся диагностируемым.

При perforation:

- residual projectile energy продолжается после ordered protection stack;
- aggregate internal fragment/spall energy добавляется к internal damage routing;
- protection solver не удаляет последствия только потому, что primary projectile потерял часть энергии.

Stage-17.5E dense projectile storage/rendering separation остаётся неизменной.

---

# 6. Compartment and subsystem damage

`ShipDamageRuntime` хранит:

```text
compartmentIntegrityById
+ central DamageState.moduleIntegrityByMount
```

Hit routing использует hull-local impact point и authored compartment centers с deterministic stable-ID tie break.

После material perforation:

```text
internal energy
→ local compartment structural degradation
→ authored subsystem coupling fraction
→ only installed mounts located in that compartment
→ mount integrity degradation
```

Нет:

- единственного global HP как sole survivability authority;
- случайного выбора subsystem без authored topology;
- class-name damage multiplier;
- player-only damage path.

Например hit в weapons compartment может повредить `weapon_spinal`, но не должен автоматически ухудшать sensor mount в другом compartment.

---

# 7. Damage-aware derived capabilities

Pre-17.5F `DamageState` уже существовал как future seam, но fitting validator намеренно запрещал non-pristine damage.

Stage 17.5F активирует его.

`ShipFittingValidator` теперь:

- принимает non-pristine damage;
- требует, чтобы damaged mount существовал на hull;
- требует, чтобы на damaged mount был реально установлен module;
- продолжает валидировать исходный production fit/budgets без repair-by-validation.

`DerivedShipCalculator` применяет mount integrity к operational capability, сохраняя физическую installed mass/volume.

Examples:

```text
drive integrity ↓
→ available thrust ↓
→ acceleration ↓
→ physical installed mass unchanged

reactor integrity ↓
→ continuous power supply ↓
→ power margin ↓

thermal module integrity ↓
→ heat rejection ↓
→ thermal margin ↓

sensor integrity ↓
→ effective aperture ↓
→ receiver noise ↑
→ bearing/range uncertainty ↑
→ destroyed sensor exposes no mode

weapon integrity ↓
→ launcher cycle time ↑
→ pointing jitter ↑
→ destroyed weapon exposes no launcher
```

Damage therefore changes the same capability model consumed by later combat/AI/UI work.

---

# 8. Kinetic end-to-end protection composition

`KineticProtectionRuntime` composes the Stage-17.5E physical projectile with Stage-17.5F protection/damage layers:

```text
ProjectileBody
→ ShieldFieldRuntime
→ residual ProjectileBody with conserved mass/identity semantics and reduced speed from residual KE
→ HeavyImpactResolver
→ ShipDamageRuntime when internal energy exists
```

A sufficiently strong covered field can stop the projectile before armor.

A partially saturated field cannot delete the projectile: a lower-energy physical residual body reaches material protection.

This closes the Stage-17.5E → 17.5F kinetic handoff without returning to hit-chance/global-HP abstractions.

---

# 9. Magazine / weapon consequences

Stage 17.5E ammunition remains central physical `ConsumableState` inventory.

Stage 17.5F does not spawn/delete ammunition as a damage shortcut.

Weapon subsystem damage instead affects fitted launcher capability:

- damaged mount cycles more slowly;
- damaged mount has worse pointing uncertainty;
- destroyed mount exposes no launcher;
- ammunition quantity/mass remains physical until ordinary firing/loss/destruction handling changes it.

Detailed magazine-content destruction/secondary-event policy can be expanded when Stage-17.5H composes final live weapon/loadout persistence and Stage-17.5I supplies representative combat content; it must not create free ammunition or generic damage rolls.

---

# 10. Beam handoff

Stage-17.5E `BeamWeaponRuntime` already produces physical:

```text
wavelength
aperture / jitter
range-derived spot
irradiance
beam power
interaction time / dwell
energy delivered
power demand
waste heat
```

Stage 17.5F does **not** invent an uncalibrated universal beam-ablation coefficient inside the heavy-impact response surface.

Current synthetic production data is explicitly heavy-impact demonstrator data. Wavelength/material-specific beam response requires its own calibrated content contract before becoming authoritative.

The final live beam power/thermal/material commit belongs to the Stage-17.5H integration boundary and Stage-22 calibrated content expansion where required; no fake projectile or `beamDamageChance` is introduced here.

---

# 11. Persistence and live engineering-runtime boundary

Important explicit boundary:

`DerivedShipCalculator` is now damage-aware, and Stage-17.5F produces authoritative local compartment/module integrity state.

However the existing Stage-17.5C `ShipEngineeringRuntime` still internally derives its live operating envelope from `DamageState.pristine()`.

Stage 17.5F deliberately does **not** fork or duplicate that runtime.

The roadmap already assigns Stage 17.5H the final shared capability/API/UI/persistence composition, including live engineering grants and combat state persistence.

Therefore Stage 17.5H must compose:

```text
persistent/local DamageState
+ compartment integrity
+ shield runtime state
+ existing engineering runtime state
→ damage-aware live power/thermal/thrust/FTL/weapon capability
→ capability APIs / UI
→ binary persistence where required
```

Until that integration slice, Stage-17.5F acceptance proves the authoritative damage model and damage-aware central capability derivation independently; it does not falsely claim that every pre-existing Stage-17.5C live call site already consumes damage.

---

# 12. Destruction / salvage boundary

Stage 17.5F establishes local structural/subsystem degradation and the information required to determine loss of capabilities.

It does not create a second destruction/salvage economy.

Terminal destruction/disabling must continue through ordinary asset lifecycle/economy paths when composed by later integration/acceptance work.

No rule is introduced of the form:

```text
local subsystem damage
→ spawn arbitrary salvage value
```

Stage 18 later supplies the full material/component salvage/recycling graph.

---

# 13. Automated acceptance

Stage-17.5F tests cover at minimum:

- protection catalog semantic references and invalid layouts;
- finite shield coverage/reserve/power/heat/collapse/restart/recharge;
- damaged/destroyed emitter capability reduction;
- bounded response-surface enforcement;
- `STOPPED` / `RICOCHET` / `PERFORATED` outcomes;
- residual projectile energy after partial shield interaction;
- shield stop before armor;
- aggregate fragment/spall result;
- hull-local compartment routing;
- only local installed mounts receiving subsystem damage;
- damaged drive reducing thrust/acceleration without deleting mass;
- damaged reactor reducing power supply/margin;
- damaged thermal module reducing heat rejection;
- damaged sensor worsening aperture/noise/measurement floors;
- destroyed sensor exposing no sensor mode;
- damaged weapon worsening cycle/jitter;
- strict loader/input validation;
- pre-17.5F legacy expectation updated so valid non-pristine damage is no longer rejected.

Exact final CI result is part of the merge gate and is not pre-declared by this document.

---

# 14. Explicit non-goals / deferred work

Stage 17.5F does not:

- create the Stage-22 broad/final armor/shield/material catalog;
- claim synthetic response coefficients are real-world armor calibration;
- create final faction hull/protection doctrines;
- create final shield visuals/VFX;
- replace Stage-17.5E projectile storage/rendering architecture;
- create a universal uncalibrated beam-material damage coefficient;
- duplicate Stage-17.5C live engineering runtime;
- finish final binary persistence/capability/UI composition assigned to 17.5H;
- implement Stage-17.5G shipyard/refit/repair/maintenance economy seam;
- implement Stage-18 material/component repair/salvage industrial graph.

---

# 15. Handoff to Stage 17.5G

Immediate next slice after exact-head merge/post-merge verification:

> **Stage 17.5G — shipyard / fitting / repair / maintenance economy seam.**

17.5G must consume the same:

```text
HullDefinition
InstalledFit
ShipDamageRuntime.Snapshot
DamageState.moduleIntegrityByMount
HullDamageLayout
maintenance metadata
```

and define what facility capability, work and provisional material/component inputs are required to restore damaged physical assets.

17.5G must not repair by replacing ship IDs or by resetting damage to pristine without an ordinary repair transaction/work path.

Stage 18 later replaces provisional generic inputs with the complete resource/component/facility production graph.

---

# 16. Handoff to Stage 17.5H / 17.5I

Stage 17.5H owns final integration of the Stage-17.5F state into:

- shared capability APIs;
- live engineering power/thermal/thrust/FTL state;
- weapon/loadout lifecycle where required;
- shield/damage persistence;
- UI projections;
- migration surfaces.

Stage 17.5I then exercises this through the already-mandatory Combat Test Content Pack and Tactical Prototype Visual Set.

Representative test fleets must therefore be able to visibly demonstrate:

```text
shield absorption / collapse
→ armor stop / ricochet / perforation
→ local compartment hit
→ subsystem degradation
→ changed thrust / power / thermal / sensor / weapon capability
→ persistent post-combat state
```

without prototype visuals becoming authoritative simulation state.

---

# 17. Completion criterion

Stage 17.5F implementation is ready for merge when the exact PR head proves:

```text
physical Stage-17.5E threat
→ finite shield interaction
→ bounded material response without extrapolation
→ stop / ricochet / perforation
→ local compartment/subsystem damage
→ central DamageState
→ damage-aware common capabilities
→ full repository CI green
```

After the exact merge SHA receives green post-merge CI, roadmap priority advances to **Stage 17.5G**.
