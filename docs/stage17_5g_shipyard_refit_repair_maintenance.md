# Star Empires — Stage 17.5G Shipyard / Refit / Repair / Maintenance

> Статус: **IMPLEMENTED — final exact-head / merge / post-merge gate pending**  
> Родительский план: `docs/stage17_5_combat_depth_implementation_plan.md`  
> База реализации: Stage 17.5A–F production engineering, fitting and local damage model.

## 1. Назначение

Stage 17.5G закрывает инженерную границу между физическим кораблём и будущей полной промышленной экономикой Stage 18.

Авторитетная причинная цепочка:

```text
HullDefinition / ModuleDefinition / InstalledFit / DamageState
+ authored construction / maintenance metadata
+ shipyard physical capability
→ deterministic work requirements
→ real physical input settlement + engineering work
→ build / refit / repair / maintenance completion
→ same production fitting/damage state
```

Главный инвариант:

> **Верфь не является tier-number shortcut и не создаёт корабль, ремонт, модуль или ресурс из кредита/таймера. Она проверяет физическую совместимость, требует реальные inputs и engineering work, а refit/repair сохраняют идентичность и condition physical asset.**

## 2. Production industrial requirement vocabulary

Добавлены:

- `ShipyardIndustrialCatalog`;
- `ShipyardIndustrialCatalogLoader`;
- `data/content/shipyard-industrial-v1.json`.

Каталог связывается только со stable Stage-17.5A IDs и задаёт инженерные требования, а не отдельную экономику.

### Hull industrial profile

Для корпуса задаются:

```text
construction inputs
fabrication capabilities
tooling tags
precision requirement
industrial power requirement
labor / automation requirement
assembly work
full-loss compartment repair inputs/work
```

Loader проверяет, что:

- hull ID существует в `ShipEngineeringCatalog`;
- все compartment repair definitions ссылаются на реальные compartments;
- repair profile существует для каждого compartment корпуса;
- inputs/tags/profiles не дублируются;
- все величины finite и находятся в допустимых диапазонах;
- collections bounded.

### Module industrial profile

Для модуля задаются:

```text
fabrication capabilities
tooling tags
precision requirement
industrial power
labor / automation
manufacturing work
installation work
removal work
```

`ModuleDefinition.constructionInputs()` и `ModuleDefinition.maintenance()` остаются authoritative engineering metadata. Stage 17.5G не создаёт параллельные module stats.

## 3. Shipyard capability model

`ShipyardEngineeringService.ShipyardCapability` проверяет физические возможности конкретной facility:

```text
berth dimensions
maximum service mass
fabrication capabilities
handled physical inputs
tooling
precision capability
work rate
labor capacity
automation capacity
industrial power
```

Отдельного `yardTier` как авторитетной причины build/refit/repair нет.

Неподдерживаемая работа отклоняется детерминированными `FeasibilityCode`, включая:

- berth envelope / mass;
- missing fabrication capability;
- missing material handling;
- missing tooling;
- insufficient precision;
- insufficient industrial power;
- insufficient labor/automation;
- missing hull/module industrial profile;
- invalid target fit;
- attempted hull replacement through refit.

## 4. Build

`planBuild(...)`:

1. валидирует target fit через общий `ShipFittingValidator`;
2. собирает hull construction inputs;
3. добавляет реальные `ModuleDefinition.constructionInputs()` каждого установленного модуля;
4. добавляет manufacturing + installation work;
5. рассчитывает required berth envelope и physical service mass;
6. проверяет facility capabilities;
7. выдаёт immutable `WorkPlan`.

`completeBuild(...)` запрещён, пока:

- plan infeasible;
- не settlement всех required physical inputs;
- не выполнен полный engineering work.

Само выделение нового `EntityId` и создание world entity остаются обычной world/persistence границей, а не скрытым shipyard allocator.

## 5. Refit

Refit изменяет **тот же физический asset**.

Hard rules:

- `EntityId` сохраняется;
- смена hull ID через refit запрещена и требует BUILD path;
- target fit проходит тот же production fitting validator;
- consumables, привязанные к удаляемому hardware, должны быть физически выгружены/перенесены до refit;
- removal и installation имеют реальный engineering work;
- новый модуль требует manufacturing/construction inputs по текущему provisional Stage-18 seam;
- removed modules не уничтожаются автоматически.

### Condition continuity

`ShipyardRefitContinuity` является обязательным condition-preserving handoff для дальнейшей live/persistence integration.

Он гарантирует:

```text
same module remains on same mount
→ retains damage integrity
→ retains maintenance age

module removed/replaced
→ removed physical module carries its integrity + service age outward

new physical module installed
→ starts pristine with service age 0

compartment damage
→ remains on the same hull unless separately repaired
```

Таким образом, refit нельзя использовать как бесплатный ремонт повреждённого или просроченного оборудования.

## 6. Repair

Repair строится непосредственно из Stage-17.5F local state:

- `ShipDamageRuntime.Snapshot.compartmentIntegrityById`;
- `DamageState.moduleIntegrityByMount`.

### Structural / compartment repair

Для каждого повреждённого compartment:

```text
integrity loss
× authored full-loss repair inputs
→ required physical structural inputs

integrity loss
× authored full-loss repair work
→ required engineering work
```

### Module repair

Для каждого повреждённого установленного модуля:

```text
integrity loss
× ModuleDefinition.constructionInputs
→ physical repair inputs

maintenanceWorkSeconds
× repairComplexity
× integrity loss
→ repair work
```

Repair completion возвращает `Snapshot` с восстановленной structural/module integrity только после полного settlement. Деньги сами по себе не ремонтируют корабль.

## 7. Scheduled maintenance

`MaintenanceState` хранит service age по установленным mounts.

`advanceMaintenance(...)` увеличивает elapsed service age, а `planMaintenance(...)` использует уже существующие:

- `serviceIntervalSeconds`;
- `maintenanceWorkSeconds`.

Completion сбрасывает service age только у фактически serviced mounts.

Stage 17.5G не вводит скрытый generic maintenance penalty и не изобретает универсальный расходник. Материальные maintenance consumables/parts, если они нужны широкому production content, должны быть authored через Stage 18/22 accepted ontology.

## 8. Common economy seam

`ShipyardEconomyBridge` доказывает интеграцию с существующим ordinary `InventoryComponent` без второго склада.

`PhysicalInputBinding` является временной Stage-18-facing границей:

```text
engineering contentId
→ ordinary runtime inventory item ID
```

`consumeRequiredInputs(...)`:

1. проверяет все mappings;
2. проверяет полный stock до первой мутации;
3. атомарно списывает ordinary inventory;
4. возвращает `WorkSettlement`;
5. completion API дополнительно проверяет required work.

При недостаточном stock никакой частичной траты не происходит.

Текущий legacy inventory хранит integer units, поэтому provisional fractional requirement округляется вверх только на compatibility boundary. **Stage 18 обязан определить окончательную commodity/component granularity и authoritative content binding.**

Stage 17.5G намеренно не создаёт:

- parallel inventory;
- parallel wallet;
- shipyard-only currency;
- virtual deliveries;
- abstract material grants;
- final production price model.

Pricing/payment использует/будет использовать существующие ordinary wallet/trade/ledger seams. Полный finished-goods and industrial market graph принадлежит Stage 18.

## 9. Derived performance

Refit не применяет performance modifier.

Acceptance проверяет:

```text
source InstalledFit
→ refit completion
→ target InstalledFit
→ same DerivedShipCalculator
→ physically changed mass / power / other derived capability
```

То есть изменение performance возникает только из состава fit и общих budgets.

## 10. Player / AI symmetry

`ShipyardEngineeringService` не принимает:

- player flag;
- AI flag;
- faction doctrine bonus;
- ship class bonus.

Одинаковые fit, damage, consumables и facility capabilities дают одинаковый `WorkPlan` независимо от инициатора.

## 11. Stage 18 handoff

Stage 17.5G фиксирует **что требуется инженерно**. Stage 18 обязан реализовать **как это физически производится и доставляется**.

Stage 18 owns:

- raw resource occurrence;
- extraction compatibility;
- refining/purification;
- authoritative engineering materials/components;
- finished module/ammunition/facility recipes;
- actual facility production capabilities;
- storage/logistics;
- market availability/pricing from ordinary economy;
- shipyard industrial supply chains;
- salvage/recycling;
- final content-ID/runtime-item binding and commodity unit granularity.

`component.heavy`, `component.electrical`, `component.precision` and current demonstrator work/power/capability values are **provisional integration vocabulary**, not final Stage-18/22 balance content.

Stage 18 may re-author those definitions while preserving the accepted Stage-17.5G interfaces and causality.

## 12. Stage 17.5H handoff

Stage 17.5H owns final live integration/persistence surfaces, including:

- attaching completed fit/refit to live `EngineeringComponent`;
- persistence/migration of maintenance state where required;
- persistence of Stage-17.5F damage/shield state;
- live composition of damage with Stage-17.5C power/thermal/thrust/FTL runtime;
- weapon/sensor engineering grants and required persistent runtime state;
- refit reconciliation of live module-local heat/energy/coolant/shield/launcher-cycle states;
- UI/capability query surfaces;
- binary save/load continuity.

Stage 17.5G deliberately does **not** fork `ShipEngineeringRuntime` or introduce a second persistence model to close those seams early.

## 13. Acceptance coverage

Stage-17.5G tests cover:

- strict industrial content references and complete compartment repair topology;
- build feasibility and full physical input/work settlement;
- rejection by berth/mass/fabrication/material-handling/tooling/precision/power/labor/automation constraints;
- damage-scaled structural/module repair;
- same-`EntityId` repair;
- same-`EntityId` refit;
- removed-module return;
- preservation of removed-module damage/service age;
- preservation of retained module damage/service age;
- no stale consumables on removed hardware;
- central `DerivedShipCalculator` performance after refit;
- authored maintenance intervals/work;
- atomic ordinary-inventory consumption;
- deterministic ownership-neutral work planning.

Implementation checkpoints before documentation closeout:

- `f8f3bf3da5cee65a780db623433b102afca1f9b6` — CI #2620 SUCCESS;
- `933757012724a66eeb0911cf44f7d1e941e52971` — CI #2622 SUCCESS;
- `9585be889fa2e1832445fd30a62e8448938124db` — CI #2626 SUCCESS;
- `bdd95fe1774b0426a368e10c1307e1d8b082c2f9` — CI #2630 SUCCESS.

Final documentation head still requires its own exact-head green CI before merge.

## 14. Completion definition

Stage 17.5G может быть отмечен `COMPLETE` после exact-head + post-merge gates, когда выполняется:

> **production-valid fitted ship можно проверить на пригодность конкретной физической верфи, построить/переоснастить/починить/обслужить только через реальные engineering requirements, physical inputs и work; refit/repair сохраняют persistent asset identity и condition; performance по-прежнему выводится общим calculator; player и AI используют одну boundary; Stage 18 получает чистый seam для полноценной промышленной экосистемы без необходимости переписывать корабельную модель.**
