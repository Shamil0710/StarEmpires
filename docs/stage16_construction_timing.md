# Star Empires — Stage 16: модель времени строительства

> Статус: **ACTIVE FOUNDATION — Stage 16 ещё не завершён**
>
> Функциональная база слита в **PR #51** (`a32584a928d97a014dd2cbb32fdeaed4fe0c65eb`).
>
> Валидация: **CI #1151**, run `31826504541`, **454/454 теста**, strict Javadoc, JaCoCo и desktop packaging.
>
> Полная спецификация Stage 16: `docs/stage16_player_construction.md`.

---

## 1. Цель модели

Время строительства станции должно зависеть от физического масштаба и производственной сложности проекта, а не быть одним произвольным финальным таймером в station archetype.

Текущее правило Stage 16:

```text
materialWork =
    Σ(requiredAmount_i × constructionHandlingWeight_i)

buildTime =
    baseSetupSeconds
  + materialWork / baselineAssemblyRate
```

Существующее значение `construction.buildSeconds` переосмыслено как **базовое время подготовки / archetype complexity allowance**, а не как полная длительность строительства.

Material bill остаётся authoritative: если объект требует больше реальных компонентов, он обычно требует больше сборочной работы и времени.

---

## 2. Почему текущий «вес» — не килограммы

Content catalog пока не содержит authoritative physical mass для каждого предмета/компонента. Если назвать текущие cargo units килограммами, мы создадим ложную точность и усложним будущую интеграцию fitting/mass модели.

Поэтому сейчас каждая item category получает нормализованный объём **construction handling / fabrication work**:

| Категория | Work на требуемую единицу |
| --- | ---: |
| `MATERIAL` | `1.00` |
| `GAS_LIQUID` | `0.55` |
| `FINISHED_GOODS` | `1.60` |

Интерпретация:

- сырой structural material — базовая единица работы;
- жидкости/энергетические bulk cargo требуют меньше assembly handling на inventory unit;
- готовые assemblies/components требуют больше монтажа, интеграции и тестирования.

Текущая базовая производительность site:

```text
12 construction-work units / simulation second
```

Это balance parameters, а не физические SI units.

---

## 3. Текущая authoritative формула

Реализована в `ConstructionDurationPolicy`:

```text
W = Σ(q_i × h_i)

T_material = W / R

T_total = T_setup + T_material
```

Где:

- `q_i` — точное required amount из material bill станции;
- `h_i` — normalized handling/fabrication work для категории предмета;
- `R` — baseline assembly rate;
- `T_setup` — authored `buildSeconds`, теперь setup/complexity allowance;
- `T_total` — длительность, используемая при создании реального construction project.

Итоговое время переводится в authoritative fixed ticks и сохраняется в `ConstructionProjectState.buildDurationTicks`.

---

## 4. Пример: mining base

Текущие требования `station.mining_base`:

```text
120 steel × 1.00 = 120 work
 60 energy × 0.55 =  33 work
--------------------------------
material work       = 153 work
```

При 12 work/s:

```text
material assembly = 153 / 12 = 12.75 s
base setup        = 25.00 s
--------------------------------
calculated total  = 37.75 s
```

Это намеренно дольше старого отдельного 25-секундного таймера: теперь физический масштаб материалов действительно участвует во времени строительства.

---

## 5. Persistence rule

Формула вычисляется **в момент создания нового проекта**.

После создания:

```text
calculated total seconds
→ authoritative fixed ticks
→ ConstructionProjectState.buildDurationTicks
→ persisted save contract
```

Уже идущий проект не пересчитывает длительность после load.

Это необходимо, потому что будущие изменения assembly rate, category weights, tech tiers, complexity или capability площадки не должны молча менять уже начатую стройку.

`ConstructionDurationIntegrationTest` доказывает, что новый реальный project получает рассчитанное число ticks и сохраняет точное значение через `WorldStateCodec` save/restore.

---

## 6. Будущее расширение: tech tier, complexity и capability

Архитектура оставляет явную точку расширения для технологического тира и коэффициента сложности, но PR #51 сознательно не вводит искусственные tiers до появления authoritative content model.

Целевая форма:

```text
materialWork =
    Σ(quantity_i × work_i)

effectiveAssemblyRate =
    baselineAssemblyRate
  × builderCapability
  × siteInfrastructure
  × conditionFactor

baseWorkTime =
    baseSetupSeconds
  + materialWork / effectiveAssemblyRate

finalBuildTime =
    baseWorkTime
  × techTierFactor
  × complexityFactor
```

### `techTierFactor`

Отражает технологическую сложность, точность и объём интеграционных требований, а не просто физический размер.

Качественная иерархия может выглядеть так:

```text
простая storage / mining platform
< промышленный refinery
< advanced shipyard
< high-tech research / military installation
```

Но точные tiers и коэффициенты должны быть data-driven.

### `complexityFactor`

Различает объекты внутри одного широкого tech tier. Например, большой механически простой depot может иметь меньшую integration complexity, чем более компактный research facility с большим количеством высокоточных систем.

### `effectiveAssemblyRate`

Должен зависеть от реально существующей инфраструктуры:

- базовая construction site;
- специализированный builder;
- orbital yard;
- upgraded construction facility;
- повреждённая/ограниченная площадка.

Высокий tech tier не обязан всегда означать более долгую стройку: sufficiently advanced yard может компенсировать часть сложности большей производительностью.

---

## 7. Будущая интеграция настоящей массы компонентов

Когда items/components получат authoritative unit mass, формула должна развиваться без изменения persistence contract проекта.

Возможная форма:

```text
materialWork_i =
    quantity_i
  × f(
        unitMass_i,
        fabricationClass_i,
        installationComplexity_i
    )
```

Нужно сохранять различие между:

- физически массивной bulk structure;
- лёгкой, но технически сложной электроникой;
- готовыми preassembled modules;
- fluids/consumables, которые нужно доставить, но не обязательно долго монтировать.

Поэтому масса не должна автоматически становиться единственным фактором времени строительства.

---

## 8. Связь с tech tiers кораблей

Аналогичная модель понадобится при будущем shipbuilding:

```text
ship production work
→ hull/component material work
→ required shipyard capability
→ ship tech tier
→ fitting/integration complexity
→ authoritative production time
```

`ShipArchetype.techTier` не должен напрямую задавать цену или боевую силу. Его эффект должен проявляться через реальные компоненты, доступные верфи, tooling, время, scarcity и fitting requirements.

---

## 9. Граница Stage 16

Наличие формулы времени **не означает завершение Stage 16**.

Полный vertical slice должен использовать существующий Stage-9 physical project pipeline:

```text
игрок выбирает legal site / station archetype
→ real player funding
→ physical construction site
→ real material demand
→ physical deliveries
→ formula-derived build duration
→ construction progress
→ ordinary completed station entity
→ player ownership
→ ordinary station economy / logistics
→ save/load continuation
```

Запрещены instant placement, virtual materials и UI-only completion.
