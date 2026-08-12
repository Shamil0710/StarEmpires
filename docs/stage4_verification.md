# Stage 4 — Data-driven content verification

> Этот документ фиксирует implementation evidence. `docs/development_roadmap.md` переводится в `COMPLETE` только после exact final-head push CI + pull_request CI и merge PR #5 в `main`.

## Base

Stage 4 ветка `feat/data-driven-content` создана от Stage 3 main merge commit:

`10559976dacced9d07392df9125f504a851d9b76`

## Реализовано

- [x] versioned JSON content catalog (`schemaVersion: 1`);
- [x] stable string IDs для items / recipes / factions / ships / stations;
- [x] dense runtime IDs для hot-path item/faction arrays;
- [x] `MAX_ITEMS` превращён из «числа enum-товаров» в capacity `64`;
- [x] `ItemType` оставлен только compatibility facade первых пяти core IDs;
- [x] `MarketSystem` получает base prices / active items из `ContentCatalog`;
- [x] `TradeAISystem` получает item category/mineable metadata из catalog;
- [x] `ShipType` отделён от конкретной модели и используется как runtime role/cargo policy;
- [x] concrete `ShipArchetypeDefinition` вынесен в data;
- [x] `StationArchetypeDefinition` вынесен в data;
- [x] faction display metadata и archetype refs вынесены в data;
- [x] generic `ArchetypeEntityFactory` создаёт station/trader/miner/combat entities из данных;
- [x] `DemoWorldFactory` оставляет только scenario instance placement / names / specialization;
- [x] stable archetype ID сохраняется в `ArchetypeComponent` и `EntityState` schema v2;
- [x] Stage-3 save schema v1 мигрирует 5 item slots → current slot capacity;
- [x] legacy v1 не получает выдуманный archetype ID;
- [x] current v2 codec schema-aware читает/пишет archetype field;
- [x] semantic SHA-256 catalog fingerprint реализован;
- [x] content-bound `STEC` save-envelope хранит fingerprint отдельно от authoritative `GameState`;
- [x] raw `STEM` save поддерживается как legacy input;
- [x] `SimulationPersistence` выполняет fingerprint compatibility check до restore;
- [x] authoring/versioning contract задокументирован в `docs/content_catalog.md`.

## Acceptance tests

### Data-only item

`DataOnlyItemIntegrationTest` создаёт шестой `item.water`, `runtimeId=5`, отсутствующий в Java `ItemType`.

Проверяется:

- `ItemType.fromId(5) == null`;
- metadata загружается из JSON;
- GAS/LIQUID cargo policy принимает товар;
- `MarketSystem` рассчитывает цену по data-driven `basePrice`.

### Data-only station/ship archetypes

`ArchetypeEntityFactoryTest` создаёт из тестового JSON:

- `station.coolant_refinery`;
- `ship.fast_tanker`.

Ни один новый Java enum/subclass для этих моделей не добавляется.

### Persistent migration

`GameStateMigrationTest` строит бинарный Stage-3 v1 layout и проверяет:

- первые 5 item slots сохраняются точно;
- новые slots получают neutral defaults;
- recipes/markets/history расширяются согласованно;
- legacy archetype остаётся `null`.

### Archetype round-trip

`EntityStateMapperTest` проверяет stable archetype ID через:

`Entity -> EntityState -> new Entity -> EntityState`.

### Content-bound save/load

`SimulationPersistenceTest` проверяет:

- custom catalog → save → load тем же catalog;
- exact state equality после load;
- deterministic continuation после load;
- отказ при попытке загрузить save с другим semantic catalog fingerprint;
- чтение raw GameStateCodec save как legacy format.

### Validation

`ContentArchetypeValidationTest` проверяет fail-fast для:

- неизвестной station faction;
- неизвестного recipe;
- неизвестного market item;
- неполных mining/combat role-specific параметров.

## Последние подтверждённые промежуточные gates

До добавления content-bound envelope функциональный archetype/persistence HEAD прошёл:

- 218/218 tests;
- file continuation;
- v1→v2 migration;
- archetype round-trip;
- JAR build.

Единственный обнаруженный на том HEAD блокер был Javadoc для lookup methods `ContentCatalog`; он исправлен отдельным commit до открытия PR #5.

## Merge gate — ещё не закрыт

Перед переводом Stage 4 в `COMPLETE` обязательно:

- [ ] exact final HEAD `./mvnw --batch-mode --no-transfer-progress clean verify` — SUCCESS;
- [ ] все JUnit tests — SUCCESS;
- [ ] Javadoc `failOnWarnings=true` — SUCCESS;
- [ ] JaCoCo line/branch thresholds — SUCCESS;
- [ ] push CI exact HEAD — SUCCESS;
- [ ] independent pull_request CI exact HEAD — SUCCESS;
- [ ] обновить `docs/development_roadmap.md` фактическим Stage 2/3/4 статусом;
- [ ] перевести PR #5 из draft;
- [ ] merge только с expected exact head SHA;
- [ ] после merge создать Stage 5 branch только от нового `main`.
