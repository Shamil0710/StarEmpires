# Star Empires — Stage 16: строительство игрока и владение станциями

> Статус: **COMPLETE — историческая implementation specification**
>
> Stage 16 закрыт PR #56–#70. Финальный functional merge: PR #70, merge commit `74bb854a79226280f1770032c1725b9ff32fd40e`. Финальный gate: CI #1337 / run `31849675260`, **484/484 tests**, strict Javadoc, JaCoCo и shaded desktop JAR build green.
>
> Фактический итог, реализованные slices и сознательно отложенные ограничения: `docs/stage16_completion_record.md`.
>
> Ниже сохранена исходная детализированная спецификация Stage 16. Формулировки в будущем времени отражают design contract, по которому велась реализация; при расхождении с фактическим состоянием приоритет имеет completion record и текущий код `main`.
>
> Основание: Stage 9 уже содержит физические persistent construction projects; Stage 15 завершён и даёт игроку несколько реальных FleetId, persistent orders и общую инерционную логистику.
>
> Базовая формула времени строительства реализована в PR #51. Подробности: `docs/stage16_construction_timing.md`.

---

## 1. Цель Stage 16

Stage 16 должен впервые позволить игроку превратить заработанные деньги, собственные корабли и физические ресурсы в **реальную принадлежащую ему станцию**, которая продолжает жить в общей экономике мира.

Целевая цепочка:

```text
игрок выбирает тип станции и допустимое место
→ создаётся реальная стройплощадка
→ игрок финансирует проект из собственного кошелька
→ стройплощадка формирует реальный спрос на материалы
→ материалы физически покупаются / перевозятся / доставляются
→ строительство начинается только после выполнения требований
→ время идёт в общей simulation clock
→ стройплощадка может существовать, торговать, быть уничтожена и сохраняться
→ после завершения появляется обычная station entity
→ PlayerState получает владение этой конкретной станцией
→ станция работает через существующую экономику
→ save/load сохраняет и незавершённый проект, и готовый актив
```

Stage 16 не создаёт отдельную «экономику игрока». Станция игрока должна подчиняться тем же рынкам, inventory, wallet, production, destruction и persistence правилам, что и станции NPC.

---

## 2. Жёсткие инварианты

1. **Никакого мгновенного спавна станции из UI.** UI отправляет команду создать construction project.
2. **Никаких виртуальных стройматериалов.** Каждая требуемая единица товара должна существовать в реальном inventory и физически попасть на площадку.
3. **Деньги не исчезают и не появляются без ledger semantics.** Финансирование — реальный transfer из кошелька игрока в project/site wallet.
4. **WorldState остаётся player-agnostic.** Человеческий игрок не должен становиться скрытой специальной фракцией только ради строительства.
5. **Владение активом и faction/legal affiliation — разные понятия.** Как и у кораблей Stage 12–15, станция может принадлежать игроку независимо от её юридической/фракционной принадлежности.
6. **Проект продолжает жить без игрока рядом.** Уход в другую систему, ускорение времени и save/load не должны останавливать физически обеспеченную стройку.
7. **Разрушение реально.** Уничтоженная стройплощадка не восстанавливается автоматически; проект переходит в FAILED через существующий destruction pipeline.
8. **Player и AI используют общий construction core.** Player-facing слой валидирует и адаптирует команды, но не дублирует Stage-9 construction logic.
9. **Результат строительства — обычная station entity.** Никакого второго «player station» типа сущности.
10. **Техтиры не являются линейным множителем качества.** Будущая tier-модель должна выражать производственную и технологическую сложность, а не `T3 = ×3 ко всему`.

---

## 3. Главный архитектурный разрыв, который надо закрыть

Текущий `ConstructionProjectService` Stage 9 предполагает, что проект принадлежит faction:

```text
ownerFactionContentId
→ faction treasury
→ construction site wallet
→ при завершении/отмене остаток обратно в faction treasury
→ готовая станция получает faction identity
```

Это несовместимо с уже принятой архитектурой игрока:

```text
PlayerState.walletMilliCredits
PlayerState.ownedFleetIds
PlayerState.factionContentId = optional affiliation, а не ownership
```

Игрок до Stage 17 ещё не обязан иметь собственную faction treasury. Поэтому Stage 16 начинается не с UI, а с разделения трёх понятий:

```text
экономический владелец / beneficiary
юридическая или faction affiliation
источник финансирования
```

Они не должны больше неявно означать одно и то же.

> Этот архитектурный разрыв фактически закрыт в Stage 16.1–16.3: ownership хранится в `PlayerState`, world construction settlement/legal affiliation разделены, а player funding идёт через отдельную atomic boundary. См. `docs/stage16_completion_record.md`.

---

# 4. Детализация по подэтапам

## 16A — Модель владения и persistent schema

**Первый обязательный подэтап.**

### 16A.1. Владение проектами игрока

`PlayerState` должен получить persistent ссылки на construction projects, принадлежащие игроку. Рекомендуемая форма:

```text
ownedConstructionProjectIds: List<ConstructionProjectId>
```

Игрок не получает копию world project state. Источником истины по материалам, статусу, времени и site entity остаётся `WorldSimulation` / `ConstructionProjectState`.

### 16A.2. Владение готовыми станциями

`PlayerState` должен получить persistent список конкретных физических станций. Рекомендуемая форма:

```text
OwnedStationRef(
    StarSystemId systemId,
    EntityId stationEntityId
)
```

или эквивалентный стабильный тип поверх уже существующего `DiscoveredObjectRef`.

Требования:

- одна станция не может дублироваться в ownership;
- station ref должен указывать на существующую station entity после загрузки;
- уничтоженная станция удаляется из player ownership при reconciliation;
- ownership не должен переписывать `FactionComponent` автоматически;
- PlayerState codec получает новую bounded schema и миграцию старых saves с пустыми списками проектов/станций.

### 16A.3. Разделение ownership и faction identity

Stage 16 должен прекратить использование `ownerFactionContentId` как универсального ответа на все вопросы проекта.

Минимальная целевая семантика:

```text
project beneficiary/ownership
!=
legalFactionContentId (optional)
!=
funding source
```

Для faction-AI проектов текущая семантика должна сохраниться через migration/default policy.

Для независимого игрока `legalFactionContentId` может быть `null`. Это не должно создавать выдуманную «player faction» до Stage 17.

### 16A.4. Settlement policy

Construction core должен явно знать, что делать с остатком site wallet после завершения/отмены.

Рекомендуемые режимы:

```text
FACTION_REFUND
EXTERNAL_OWNER_RETAIN_WITH_ASSET
EXTERNAL_OWNER_REFUND_ON_CANCEL
```

Для первого player vertical slice предлагается:

- при успешном завершении неиспользованный construction wallet становится стартовым operating wallet готовой станции;
- при отмене до BUILDING неиспользованный денежный остаток возвращается игроку обычным transfer;
- деньги никогда не исчезают скрыто.

Это позволяет сохранить player-agnostic WorldState: `PlayerConstructionService` выполняет player-wallet часть операции, а общий construction core работает с реальным site wallet и settlement contract.

### Acceptance 16A

- старый save мигрирует без owned stations/projects;
- новый save сохраняет project ownership и station ownership;
- faction affiliation игрока не является обязательной для владения станцией;
- один физический `EntityId` не может быть добавлен как две разные станции игрока;
- destruction/reload не оставляет ложный ownership.

---

## 16B — PlayerConstructionService и авторинг проекта

Player-facing API должен быть отдельным адаптером над `WorldSimulation`, по аналогии с `PlayerMarketService`, `PlayerMiningService` и Stage-15 order services.

Предлагаемые границы:

```text
PlayerConstructionService
PlayerConstructionView
ConstructionPlacementPolicy
ConstructionAccessPolicy
```

### 16B.1. Query/view

Игрок должен иметь возможность запросить:

- какие station archetypes вообще constructible;
- доступен ли конкретный archetype сейчас;
- funding requirement;
- полный material bill;
- расчётное materialWork;
- расчётное build duration;
- причины недоступности;
- разрешено ли выбранное место.

UI не вычисляет это самостоятельно.

### 16B.2. Начальный placement scope

Для первого Stage-16 vertical slice размещение новой станции разрешается только:

- в **discovered** системе;
- в текущей active system игрока;
- по конечным координатам внутри допустимых world bounds;
- вне запрещённых зон/пересечений.

Удалённое строительство через strategic map без физического присутствия не входит в первый slice. Позже его можно открыть через real construction fleet / builder capability.

### 16B.3. Геометрическая валидность места

`ConstructionPlacementPolicy` должен проверять authoritative world state, а не UI sprite overlap.

Минимум:

- границы локальной карты;
- clearance от существующих stations;
- clearance от других construction sites;
- clearance от крупных asteroid/resource objects;
- exclusion zone вокруг canonical jump-arrival anchor;
- отсутствие физически недопустимого пересечения.

Clearance должен быть data-driven либо выводиться из authoritative collision/size metadata. Не хранить важное правило только в UI.

### 16B.4. Территория и разрешение на строительство

Stage 8 уже знает controlling faction системы. Stage 16 вводит общий `ConstructionAccessPolicy`.

Начальный безопасный baseline:

- unclaimed system → разрешено;
- территория faction, с которой игрок официально аффилирован → разрешено;
- чужая контролируемая территория → запрещено без explicit permission;
- hostile/закрытая территория → запрещено.

Не придумывать скрытый reputation threshold. Позже Stage 17/20 сможет добавить licenses, treaties, reputation permissions и faction policy через тот же API.

### 16B.5. Создание проекта

Успешная команда:

```text
PlayerConstructionService.createProject(...)
→ повторная authoritative validation
→ обычный WorldSimulation construction project
→ реальная construction-site entity
→ project ID добавляется в PlayerState ownership
```

Станция на этом шаге **не существует**.

### Acceptance 16B

- UI preview сам ничего не создаёт;
- illegal location отклоняется без изменения денег/мира;
- foreign territory без разрешения отклоняется;
- валидная команда создаёт ровно один project + одну site entity;
- save/load после создания сохраняет тот же `ConstructionProjectId` и site `EntityId`.

---

## 16C — Финансирование и экономика стройплощадки

### 16C.1. Player wallet → site wallet

Финансирование player project должно идти:

```text
PlayerState.walletMilliCredits
→ temporary authoritative wallet adapter
→ construction site WalletComponent
→ EconomicLedger MONEY_TRANSFER
```

Нельзя напрямую уменьшить PlayerState и отдельно увеличить site wallet без атомарной rollback semantics.

### 16C.2. Что означает minimumFunding

`construction.fundingCredits` — это не магическая цена станции и не resource sink.

Это минимальная ликвидность/бюджет реальной стройплощадки, которая позволяет:

- оплачивать поступающие материалы через market path;
- поддерживать project economy;
- после completion оставить осмысленный operating capital, если settlement policy это предусматривает.

Стоимость станции возникает из совокупности:

```text
реально потраченные материалы
+ реально выплаченные поставщикам деньги
+ retained/returned project liquidity
+ время и производственная сложность
```

### 16C.3. Дополнительное финансирование

Игрок может вносить больше minimumFunding. Это повышает способность site покупать материалы, но не ускоряет строительство само по себе.

Нельзя реализовать `pay more → instant build`.

### 16C.4. Site как настоящий рынок

Construction site продолжает использовать обычные:

```text
InventoryComponent
WalletComponent
MarketComponent
```

Недостающие материалы отражаются как реальный спрос. Generic traders могут обнаружить этот спрос и продать товар обычным `TradeController` путём, если маршрут/доступ/цена выгодны.

### Acceptance 16C

- недостаточно денег у игрока → никакой частичной скрытой эмиссии;
- успешное funding уменьшает player wallet и увеличивает site wallet на одинаковую сумму;
- ledger фиксирует transfer;
- save/load сохраняет оба остатка;
- дополнительные деньги не меняют buildDurationTicks.

---

## 16D — Физическая доставка материалов и fleet logistics

### 16D.1. Manual delivery

Существующий world API `deliverConstructionMaterial` проверяет inventory, но не является достаточным player-facing physics boundary.

`PlayerConstructionService.deliver(...)` должен дополнительно требовать:

- source fleet принадлежит игроку;
- source fleet физически находится в той же системе;
- fleet не находится в jump transit;
- ship находится внутри material-transfer / docking range site;
- относительная скорость достаточно мала для передачи;
- item действительно требуется проекту;
- quantity реально существует в ship InventoryComponent.

Только после этого вызывается общий world transfer.

### 16D.2. Покупка материалов игроком

Игрок может:

```text
купить материал на обычном рынке
→ загрузить в реальный трюм
→ физически прилететь к site
→ передать материал
```

Никакого construction-store интерфейса с мгновенной доставкой.

### 16D.3. Поставка обычными NPC

Construction-site market остаётся доступным обычной экономике. Generic TradeAI может снабжать проект как любого другого покупателя.

Это важно: игрок может строить через рынок, не превращаясь в единственного перевозчика галактики.

### 16D.4. Целевая поставка собственным флотом

Stage 16 должен подготовить/желательно реализовать persistent приказ снабжения construction project поверх Stage-15 fleet orders.

Предлагаемый тип:

```text
SUPPLY_PROJECT
```

Минимальная семантика:

```text
fleet получает ConstructionProjectId
→ читает фактически недостающие материалы
→ выбирает доступный discovered supplier через обычный рынок
→ учитывает cumulative whole-route risk
→ физически покупает товар
→ физически летит/прыгает
→ доставляет на site
→ повторяет до fulfillment или невозможности продолжать
```

Он не создаёт груз, не резервирует рынок и не телепортирует доставку.

Если `SUPPLY_PROJECT` окажется слишком большим для первого PR, manual delivery + ordinary TradeAI являются минимальным Stage-16 baseline, но до закрытия Stage 16 желательно доказать хотя бы одну автономную owned-fleet поставку.

### 16D.5. Конкуренция и частичные поставки

Project должен корректно переживать:

- частичные поставки;
- изменение рыночной цены;
- исчезновение supplier stock;
- уничтожение перевозчика;
- нехватку site wallet;
- поставку другим NPC раньше игрока.

Никаких reserved materials только потому, что UI уже показал план.

### Acceptance 16D

- transfer через всю систему невозможен;
- неподвижный/близкий owned fleet может передать реальный cargo;
- stock source уменьшается ровно на accepted amount;
- stock site увеличивается на ту же величину;
- project materials refresh совпадает с inventory;
- ordinary AI trader может продать требуемый товар на site;
- supply/order сценарий не нарушает Stage-15 inertia/risk routing.

---

## 16E — Время, выполнение строительства и будущая capability-модель

### 16E.1. Уже реализованная база

Текущая authoritative формула:

```text
materialWork = Σ(requiredAmount × constructionHandlingWeight(itemCategory))

buildTime = baseSetupSeconds + materialWork / baselineAssemblyRate
```

Сейчас:

```text
MATERIAL       = 1.00 work / unit
GAS_LIQUID     = 0.55 work / unit
FINISHED_GOODS = 1.60 work / unit
baselineAssemblyRate = 12 work / simulation second
```

Это не килограммы.

### 16E.2. State machine

Сохраняется общий Stage-9 lifecycle:

```text
PLANNED
→ FUNDED
→ AWAITING_MATERIALS
→ BUILDING
→ COMPLETED
```

Terminal states:

```text
CANCELLED
FAILED
```

BUILDING начинается только когда реальные material requirements fulfilled.

### 16E.3. Время должно идти в общей simulation clock

Проект строится по fixed ticks и должен одинаково корректно продолжаться:

- в active system;
- в remote coarse simulation;
- при time scale;
- после save/load.

Render FPS не влияет на ETA.

### 16E.4. Site capability

В первой версии сама physical construction site представляет базовый комплект строительного оборудования и имеет `baselineAssemblyRate`.

Будущее расширение:

```text
effectiveAssemblyRate =
    baselineRate
  × builderCapability
  × siteInfrastructure
  × conditionFactor
```

Реальный construction ship, orbital yard или upgraded builder смогут ускорять работу только через реальную capability, а не бесплатный UI bonus.

### 16E.5. Tech tiers / complexity

Когда появятся authoritative technology tiers:

```text
baseWorkTime =
    baseSetupSeconds
  + materialWork / effectiveAssemblyRate

finalBuildTime =
    baseWorkTime
  × techTierFactor
  × complexityFactor
```

При этом высокотехнологичная верфь может иметь более высокий `effectiveAssemblyRate`, поэтому higher tier не обязан механически всегда строиться дольше.

Уже начатый project хранит resolved `buildDurationTicks`. Баланс/tiers после save не переписывают прошлый контракт.

### Acceptance 16E

- без всех материалов BUILDING не начинается;
- после fulfillment BUILDING начинается детерминированно;
- progress основан на simulation ticks;
- save/load в середине BUILDING продолжает тот же ETA;
- удалённая система продолжает строительство;
- изменение balance policy не меняет persisted duration уже созданного project.

---

## 16F — Completion, player ownership и работа готовой станции

### 16F.1. Completion

Completion использует обычный lifecycle/content boundary:

```text
construction site удаляется
→ required materials записываются как resource sink construction
→ создаётся обычная station entity нужного archetype
→ resulting station EntityId записывается в ConstructionProjectState
→ PlayerState заменяет project ownership на OwnedStationRef
```

Никакой clone/player-only station entity.

### 16F.2. Station legal/faction identity

Player ownership отдельно от faction identity.

Baseline:

- если player project имеет legalFactionContentId, станция получает соответствующую legal/faction identity;
- если игрок независим и строит в допустимой neutral/unclaimed области, станция может оставаться без faction affiliation до Stage 17;
- ownership всё равно принадлежит игроку через PlayerState.

Stage 17 позже сможет перевести assets под созданную player faction без смены физической station identity.

### 16F.3. Деньги готовой станции

Станция имеет собственный реальный `WalletComponent`.

Player ownership **не означает автоматическое мгновенное перечисление прибыли в личный wallet**.

Минимальный `PlayerStationFinanceService` должен позволять:

```text
player wallet → station wallet  (deposit)
station wallet → player wallet  (withdraw)
```

с:

- реальной проверкой балансов;
- ledger MONEY_TRANSFER;
- атомарным rollback;
- невозможностью уйти в отрицательный баланс.

Это создаёт важный выбор: забрать деньги себе или оставить станции оборотный капитал для закупок.

### 16F.4. Экономическая работа станции

Готовая станция использует существующие компоненты своего archetype:

- MarketComponent;
- InventoryComponent;
- WalletComponent;
- Production/Consumption, если archetype их содержит;
- обычные trade routes;
- обычную destruction/persistence модель.

На Stage 16 не нужен отдельный passive-income расчёт.

### 16F.5. Что пока не требуется

Можно отложить:

- продажу станции другому владельцу;
- сложную модульную перестройку;
- автоматическую dividend/tax policy;
- employee simulation;
- полноценное station fitting;
- собственную faction treasury — это Stage 17.

### Acceptance 16F

- completion создаёт ровно одну обычную station entity;
- physical station существует по resulting EntityId;
- ownership появляется в PlayerState;
- faction/legal affiliation не подменяет ownership;
- deposit/withdraw сохраняет сумму денег;
- станция продолжает торговать/производить обычным world core;
- destruction удаляет реальный актив и ownership без replacement grant.

---

## 16G — Управление проектами и strategic/local UI

UI строится только после authoritative services.

### 16G.1. Project panel

Для каждого owned project показывать:

- `ConstructionProjectId`;
- archetype;
- system/location;
- статус;
- minimum funding / site wallet;
- delivered / required / missing materials;
- estimated material work;
- build duration;
- build progress;
- ETA;
- legal/territory status;
- возможную причину блокировки.

### 16G.2. Local construction mode

В active system игрок должен получить понятный placement preview:

- ghost/outline станции;
- valid/invalid placement;
- clearance zones;
- итоговую стоимость/материалы/ETA до подтверждения.

Preview не резервирует координаты и не изменяет мир.

### 16G.3. Global map

Stage-15 strategic map расширяется:

- owned construction sites;
- progress/status;
- missing materials;
- owned completed stations;
- маршруты supply fleets;
- известная territory/access информация.

Global map может отдавать ordinary commands, но не завершать стройку.

### 16G.4. Notifications

Минимальные события:

- проект создан;
- недостаточно funding;
- material requirement fulfilled;
- строительство началось;
- строительство завершено;
- site destroyed / project failed;
- supply order не может найти допустимый source/route.

---

## 16H — Cancellation, failure и hardening

### 16H.1. Cancellation до материалов

Сохраняем текущую простую семантику: site wallet возвращается owner, site удаляется, project → CANCELLED.

### 16H.2. Cancellation после частичной доставки

Текущий Stage-9 core запрещает это из-за отсутствия material-fate policy. Stage 16 должен сделать поведение явным.

Предпочтительный baseline:

- до BUILDING игрок может отменить project;
- неиспользованные деньги возвращаются игроку;
- доставленные материалы **не исчезают** — они остаются в физическом salvage/container/site-remnant inventory либо переводятся в явно созданный recoverable cargo entity;
- ledger не создаёт и не уничтожает материал без причины.

Фактический Stage-16 boundary выбран консервативнее: после physical delivery required materials voluntary cancellation отклоняется до появления recoverable-material policy. Это не позволяет скрыто потерять или вернуть ресурсы из воздуха.

### 16H.3. Cancellation во время BUILDING

Для первого Stage 16 можно запретить voluntary cancellation после начала BUILDING, пока нет корректной salvage-by-progress модели.

Позже:

```text
recoverable materials = f(progress, damage, archetype)
```

через ordinary salvage/destruction mechanics.

### 16H.4. Site destruction

Уже существующий `failDestroyedSite` остаётся authoritative:

```text
site destroyed
→ project FAILED
→ никакого automatic respawn/refund
→ возможный salvage только через destruction pipeline
```

### 16H.5. Concurrency/determinism

Несколько проектов игрока и NPC должны:

- иметь stable IDs;
- обновляться в deterministic order;
- конкурировать за одни рынки/материалы;
- не получать reserved supply;
- сохранять корректность при remote simulation.

---

# 5. Будущие технологические тиры для станций и кораблей

Это cross-cutting requirement, но не должно блокировать первый рабочий Stage-16 vertical slice.

## 5.1. Тир — не класс размера и не качество

Нужно различать:

```text
hull/station role & physical scale
!=
technology tier
```

Пример:

- маленький T3 courier может быть меньше большого T1 bulk freighter;
- T2 research station может быть физически меньше T1 industrial depot;
- высокий тир означает более сложную технологию/производство, а не автоматическое превосходство по всем параметрам.

## 5.2. Что должен определять tech tier станции

Будущий `StationArchetype.techTier` или stable equivalent:

- prerequisite technology/unlock;
- требуемый уровень строительной площадки/верфи;
- необходимые специализированные компоненты;
- integration/commissioning complexity;
- доступность station modules/functions;
- требования к ремонту/upgrade инфраструктуре;
- возможные лицензии/фракционные ограничения.

## 5.3. Что должен определять tech tier корабля

Будущий `ShipArchetype.techTier` или stable equivalent:

- требуемый класс shipyard;
- component/fitting prerequisites;
- сложность tooling/production;
- ремонт/refit capability;
- blueprint/unlock rules;
- косвенную цену через реальные компоненты, дефицит, время производства и продавца.

## 5.4. Tier capability

Производящая инфраструктура сама должна иметь capability:

```text
facilityCapabilityTier >= requiredTechTier
```

как один из условий. Но одной цифры недостаточно для специальных производств: позже возможны capability tags:

```text
CAP_HEAVY_HULL
CAP_PRECISION_ELECTRONICS
CAP_MILITARY_REACTOR
CAP_CAPITAL_ASSEMBLY
```

Тир и specialization должны дополнять друг друга.

## 5.5. Экономический эффект должен быть эмерджентным

Запрещён подход:

```text
T1 = 100 credits
T2 = 200 credits
T3 = 300 credits
```

Предпочтительно:

```text
higher tier
→ более редкие/сложные компоненты
→ более capable facility
→ больше/сложнее production work
→ ограниченное число производителей
→ более длинные supply chains
→ scarcity / market pressure
→ реальная высокая цена
```

## 5.6. Persistence

При введении tech tiers:

- добавить bounded validated content fields;
- задать migration/default для существующих archetypes;
- уже начатые проекты сохраняют resolved duration/material contract;
- уже существующие ships/stations не должны исчезать при изменении tier rules;
- player и AI используют одинаковые capability checks.

---

# 6. Предлагаемый порядок реализации Stage 16

```text
16A ownership/schema separation
→ 16B placement + project authoring
→ 16C player funding + site economy
→ 16D physical material logistics
→ 16E build execution/time/capability seam
→ 16F completion + station ownership/finance
→ 16G UI/global-map management
→ 16H failure/cancel/hardening + end-to-end acceptance
```

Практические PR-срезы, запланированные до начала реализации, сохранены как исторический design record. Фактические slices были реализованы в PR #56–#70 и перечислены в `docs/stage16_completion_record.md`.

---

# 7. Stage 16 Definition of Done

Stage 16 считается завершённым только когда автоматизированный deterministic acceptance доказывает полный сценарий:

```text
игрок имеет обычный personal wallet и owned fleet
→ выбирает допустимый station archetype
→ выбирает физически допустимую точку
→ создаётся project/site, но не готовая station
→ деньги реально переводятся из player wallet в site wallet
→ недостающие материалы реально существуют на рынках/в cargo
→ как минимум одна партия материалов физически доставляется owned fleet
→ остальные материалы могут быть доставлены обычной экономикой/логистикой
→ без полного material bill строительство не стартует
→ после fulfillment начинается BUILDING
→ игрок покидает систему, world продолжает жить
→ save/load в процессе сохраняет IDs, деньги, материалы и progress
→ после нужного числа simulation ticks проект завершается
→ создаётся обычная physical station entity
→ PlayerState получает ownership именно этого EntityId
→ station wallet/inventory/market/production работают обычными системами
→ player может физически корректно deposit/withdraw operating funds
→ повторный save/load сохраняет станцию и ownership
→ destruction готовой station удаляет актив без бесплатной замены
```

Обязательные отрицательные проверки:

- нельзя строить в undiscovered/invalid location;
- нельзя строить в запрещённой чужой территории;
- нельзя передавать материалы через всю систему;
- нельзя построить без реальных материалов;
- нельзя ускорить стройку простой доплатой;
- нельзя получить duplicate station/project через повтор команды;
- нельзя потерять/удвоить деньги при exception/rollback;
- нельзя после load получить другой buildDuration;
- UI не может изменить project state напрямую.

**Фактический итог: PASS.** Финальный aggregate acceptance и core CI gate подтверждены PR #70 / CI #1337.

---

# 8. Что сознательно остаётся после Stage 16

Stage 16 **не обязан** реализовывать:

- собственную полноценную player faction — Stage 17;
- territory conquest;
- налоги/субсидии player faction;
- автоматические дивиденды;
- глубокие station modules/fitting;
- полноценные production tech trees;
- кораблестроение игрока;
- advanced shipyard queues;
- workforce/population simulation;
- сложный salvage прогресса отменённой BUILDING станции;
- remote project placement без реального builder/capability;
- advanced tactical combat AI.

Stage 16 оставляет для них стабильные ownership, construction, technology-tier и finance seams.
