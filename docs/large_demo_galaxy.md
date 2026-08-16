# Star Empires — Large Demo Galaxy

> Статус: **pre-Stage-17.5 manual scale gate**  
> Цель: дать разработке достаточно большой живой мир для ручной проверки уже реализованных систем до начала Combat Depth / Ship Fitting.

## 1. Зачем нужен отдельный large demo

Исторический `DemoGalaxyFactory.createState(...)` намеренно остаётся компактным трёхсистемным fixture. Он удобен для быстрых unit/integration/acceptance тестов, но слишком мал для ручного наблюдения за:

- многосистемной экономикой;
- удалённой симуляцией;
- длинными логистическими цепочками;
- распределением нескольких фракций;
- территорией и дипломатией;
- стратегическим ростом;
- строительством и supply pressure;
- сохранением/загрузкой большого world snapshot.

Large demo решает только эту задачу. Это **не Stage 20 world generation** и не финальная модель физического устройства галактики.

## 2. Scale contract

`LargeDemoGalaxyFactory` создаёт:

- **100 StarSystem**;
- **12 Sector**;
- одну полностью связную jump topology;
- первые три legacy-системы без изменения stable IDs:
  - `1 — Anchor`;
  - `2 — Corona`;
  - `3 — Frontier`;
- ещё 97 deterministic систем в десяти региональных секторах;
- deterministic планеты и asteroid-field landmarks;
- обычную `SimulationSession` для каждой системы;
- **8 faction actors**;
- **8 startup economic/system profiles**.

Каждая из 100 систем после bootstrap является обычной частью `WorldSimulation`.

## 3. Сектора

Legacy:

1. `Core Sector`;
2. `Outer Rim`.

Large-demo regions:

1. `Aquila Reach`;
2. `Borealis March`;
3. `Cygnus Verge`;
4. `Draconis Belt`;
5. `Erebus Expanse`;
6. `Fornax Corridor`;
7. `Gemini Frontier`;
8. `Helios Spur`;
9. `Icarus Drift`;
10. `Janus Rim`.

Региональные цепочки соединены между собой gateway edges; внутри регионов добавлены дополнительные связи, поэтому галактика не является единственной линейной дорогой.

## 4. Фракции

Сохраняются три authored faction из content catalog:

- `faction.neutral` — Нейтралы;
- `faction.trade_league` — Торговая лига;
- `faction.miners` — Шахтёры.

Large demo добавляет пять persistent world-bootstrap actors через общий Stage-17 faction identity contract:

- `faction.imperial_directorate` — Имперский директорат;
- `faction.frontier_union` — Союз пограничных миров;
- `faction.industrial_combine` — Промышленный комбинат;
- `faction.free_ports` — Лига свободных портов;
- `faction.research_consortium` — Исследовательский консорциум.

Они занимают runtime faction slots `3..7`; authored actors сохраняют `0..2`.

Каждая новая faction имеет:

- stable identity;
- display name;
- doctrine;
- ordinary faction economy/treasury;
- ordinary strategic state;
- controlled systems;
- directed relations;
- физические станции/корабли с обычным `FactionComponent`.

`WORLD_BOOTSTRAP` является только provenance identity. Он не даёт бонусов, ресурсов или особых правил после создания мира.

## 5. Разнообразие систем

Large demo использует восемь **startup profiles**:

- `CAPITAL`;
- `TRADE_HUB`;
- `MINING`;
- `ENERGY`;
- `AGRICULTURAL`;
- `INDUSTRIAL`;
- `ARSENAL`;
- `FRONTIER`.

Профиль задаёт только исходные физические условия существующих компонентов:

```text
initial inventory stock
+ configured/effective market target
+ strategic planets / asteroid-field layout
```

Например:

- mining system начинает с большим запасом ore и повышенным спросом на energy;
- industrial system имеет больше steel, но повышенный спрос на ore/energy;
- arsenal имеет больше weapons и повышенный спрос на steel/energy;
- frontier начинает с низкими запасами и более высоким demand target;
- trade hub имеет широкие стартовые запасы.

После bootstrap профиль больше не применяется.

Это принципиально важно:

> **никаких `MINING +20% production`, `CAPITAL +50% money`, hidden refill или faction-class bonuses нет.**

Дальше систему изменяют только обычные economy/logistics/production/construction/diplomacy rules.

## 6. Desktop integration и навигация

Обычный `DesktopLauncher` включает JVM property:

```text
spacesim.demo.large=true
```

для двух manual modes:

- default playable application;
- `--spectator` economy view.

`PlayableTestWorldFactory` продолжает пользоваться обычным `DemoGalaxyFactory.create(...)`; поэтому desktop получает large world без отдельной player-only simulation path.

Graphics validation modes large world не включают.

### Live navigation

Playable HUD больше не трактует галактику как линейный `Anchor ↔ Corona` test route.

Он показывает:

- текущий `StarSystemId` и имя;
- текущего strategic controller;
- все direct neighbors из `GalaxyTopology.neighbors(current)`;
- controller каждого соседнего узла;
- текущий выбранный immediate jump destination.

Управление:

```text
K
→ циклически выбрать следующий direct neighbor

J
→ запросить ordinary authoritative jump в выбранного neighbor
```

UI не меняет положение флота и не строит отдельную travel path. Он только передаёт выбранный direct neighbor в `PlayerRuntime.requestJump(...)`, после чего существующий Stage-10 jump FSM повторно проверяет topology и выполняет переход.

Канонический cross-stage contract: `docs/inter_system_navigation_contract.md`.

## 7. Compact-test isolation

`DemoGalaxyFactory.createState(...)` всегда остаётся трёхсистемным deterministic fixture независимо от JVM property.

Это защищает CI от умножения стоимости каждого исторического acceptance на 100 `SimulationSession`.

Large-scale проверки находятся только в специализированном `LargeDemoGalaxyFactoryTest` и navigation-specific tests.

## 8. Curated playable concessions

Существующий `PlayableTestWorldFactory` для ручного test-world выдаёт стартовой faction явные construction concessions в чужих контролируемых системах.

Это не скрытый bypass territorial law: создаются обычные persistent `TerritorialConstructionRightState`, а authorization проходит через общий resolver.

Для текущего large-demo это сознательная manual-testing convenience: игрок может проверить строительство в разных регионах без предварительного полного политического прохождения.

Будущий production campaign bootstrap не обязан выдавать такие права.

## 9. Acceptance gate

Перед merge large demo обязан доказать:

```text
exactly 100 systems
→ all systems have SimulationState
→ connected jump graph from Anchor
→ legacy 1/2/3 topology preserved
→ multiple planet-count and asteroid-field shapes
→ materially different physical market fingerprints
→ exactly 8 faction economies + strategies
→ 5 WORLD_BOOTSTRAP identities
→ runtime faction IDs 0..7 occur on physical ECS entities
→ every system has exactly one strategic controller
→ every selectable immediate jump destination is a direct topology neighbor
→ non-neighbor player/world jump requests are rejected
→ current WorldStateCodec round-trip exact
→ deterministic re-encode exact
→ compact createState remains exactly 3 systems
```

## 10. Что проверять вручную

На large demo перед Stage 17.5 полезно проверять в живом desktop build:

1. читаемость 100-system global map;
2. свободный выбор между direct neighbors через `K` и `J`;
3. много-hop маршруты как последовательность реальных соседних переходов;
4. remote-system lag и bounded strategic update behavior;
5. divergence цен между mining/industrial/frontier/trade systems;
6. реальные trade routes и shortages;
7. supply pressure и faction economic decisions;
8. faction treasuries и fiscal policy;
9. diplomacy/treaty/embargo effects;
10. claims/control/construction concessions;
11. player construction и owned stations в разных регионах;
12. save/load большого world state;
13. повторное продолжение симуляции после load без duplication/reset.

## 11. Граница с будущими этапами

Large demo не должен заранее решать задачи Stage 18 или Stage 20.

Он переиспользует текущие пять commodity items и существующие station/ship archetypes. Набор ресурсов, extraction processes, facilities, shipyards и production graph будет расширяться на Stage 18; физически калиброванный universe placement — на Stage 20.

При этом neighbor-only topology уже является долгоживущим cross-stage invariant: Stage 20 сможет заменить layout/edge generation, но не право ordinary jump пропускать промежуточные системы.

Поэтому large demo — это **масштабный стенд текущих систем**, а не обещание финального распределения ресурсов, звёзд, планет или политической карты.
