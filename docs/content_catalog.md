# Data-driven Content Catalog

## Назначение

Stage 4 отделяет игровые данные от hot-path simulation-кода. Production catalog находится в `src/main/resources/data/content/catalog-v1.json` и загружается через `ContentCatalogLoader` без OpenGL/backend-зависимостей.

Simulation по-прежнему использует плотные `int` runtime ID и primitive arrays. Строковые content ID используются на границах загрузки, archetype-ссылок и авторинга данных, поэтому data-driven слой не превращает каждый simulation tick в lookup по строкам.

## Versioning

- JSON `schemaVersion`: `1`.
- Поддерживаемая версия определяется `ContentCatalogLoader.CURRENT_SCHEMA_VERSION`.
- Неизвестная версия отклоняется fail-fast.
- Persistent save schema и content schema — разные версии. Изменение структуры JSON не должно молча менять GameState schema.
- Semantic fingerprint каталога — lowercase SHA-256. Он вычисляется из нормализованных игровых параметров, а не из исходных пробелов/форматирования JSON.

## Stable IDs и runtime IDs

Persistent content IDs имеют namespaced lower-case форму, например:

- `item.ore`
- `recipe.steel_smelting`
- `faction.trade_league`
- `ship.energy_tanker`
- `station.foundry`

Для товаров и фракций loader назначает уже указанные в данных плотные runtime IDs. Они должны образовывать диапазон `0..N-1` без дыр.

`Constants.MAX_ITEMS = 64` означает **slot capacity**, а не число существующих товаров. Фактическое количество активных товаров задаёт catalog. Благодаря этому `InventoryComponent`, `MarketComponent`, `Recipe` и другие hot-path структуры остаются primitive arrays.

Пять исторических `ItemType` сохранены только как compatibility facade для core ID `0..4`. Data-only товар не обязан иметь Java enum-константу: `ItemType.fromId(id)` для такого товара возвращает `null`, а рынок/TradeAI используют metadata из `ContentCatalog`.

## Items

Обязательные поля:

```json
{
  "id": "item.ore",
  "runtimeId": 0,
  "codeName": "Ore",
  "displayName": "Руда",
  "category": "MATERIAL",
  "basePrice": 10.0,
  "mineable": true
}
```

Validation:

- unique stable ID;
- unique dense runtime ID;
- `0 <= runtimeId < MAX_ITEMS`;
- `basePrice > 0` и finite;
- mineable item должен иметь `MATERIAL` category.

`MarketSystem` берёт base price и список активных item IDs только из catalog.

## Recipes

Recipe хранит persistent item IDs в JSON, а loader преобразует их в dense runtime indices при materialization.

```json
{
  "id": "recipe.steel_smelting",
  "displayName": "Выплавка стали",
  "durationSeconds": 4.0,
  "inputs": {"item.ore": 2, "item.energy": 1},
  "outputs": {"item.steel": 2}
}
```

Validation запрещает неизвестные item IDs, неположительные amounts, неположительную duration и recipe без output.

## Factions

Faction metadata хранится в catalog:

```json
{"id":"faction.miners","runtimeId":2,"displayName":"Шахтёры"}
```

Runtime ID остаётся плотным индексом существующего массива репутации. `Constants.FACTION_*` остаются compatibility/runtime constants, но production display metadata и archetype-ссылки принадлежат catalog.

## Ship archetypes vs ShipType

`ShipType` больше не означает конкретную модель корабля. Это небольшой runtime role enum, который кодирует поведение систем и cargo policy:

- `FINISHED_GOODS_CARRIER`
- `MATERIAL_CARRIER`
- `GAS_LIQUID_CARRIER`
- `MINING_SHIP`
- `COMBAT_SHIP`

Конкретная модель — data-driven `ShipArchetypeDefinition`, например `ship.energy_tanker`. Archetype задаёт cargo capacity, movement speed, initial credits и role-specific mining/combat параметры.

Generic `ArchetypeEntityFactory` materialize-ит торговый, mining или combat ECS Entity из archetype без нового Java subclass/enum.

## Station archetypes

Station archetype задаёт:

- stable archetype ID;
- inventory capacity;
- starting credits;
- faction content ID;
- optional recipe ID;
- market rules: initial stock, target stock, consumption.

Loader проверяет все ссылки `station -> faction / recipe / item`, отсутствие повторов market item и суммарный стартовый stock относительно capacity.

`DemoWorldFactory` после Stage 4 хранит только сценарные instance-данные: имя, координаты, выбранный archetype и торговую специализацию. Экономические параметры станции/корабля в нём не дублируются.

## Archetype identity в save/load

Созданные data-driven станции и корабли получают `ArchetypeComponent(contentId)`.

`EntityState` schema v2 сохраняет этот stable archetype ID. Stage-3 save schema v1 не имела такого поля; миграция оставляет `archetype = null`, не пытаясь угадывать тип по имени. Все фактические runtime-компоненты старого save при этом сохраняются и продолжают симуляцию.

## Backward-compatible item slot migration

Stage-3 schema v1 имела пять item slots. Schema v2 использует текущую capacity `64`.

`GameStateMigration`:

- сохраняет первые пять значений точно;
- integer/float/double slots дополняет нулями;
- tradable flags — `false`;
- price history — пустыми рядами;
- unknown legacy archetype — `null`.

`GameStateCodec` читает обе logical schemas. В schema v2 archetype field добавлен schema-aware, поэтому decoder v1 не пытается читать байт, которого в старом файле нет.

## Content-bound save envelope

`GameState` остаётся чистым authoritative state. Совместимость внешнего data catalog хранится в отдельном `ContentBoundSaveCodec` envelope:

```text
STEC magic
save-envelope version
64-byte semantic catalog fingerprint
GameState payload length
GameStateCodec payload
```

`SimulationPersistence.save(...)` записывает fingerprint текущего session catalog.

`SimulationPersistence.load(...)` сначала сравнивает fingerprint файла и выбранного catalog и только затем вызывает `SimulationSession.restore(...)`. Несовместимый content pack поэтому отклоняется до продолжения simulation.

Raw `STEM` Stage-3 saves по-прежнему принимаются и считаются связанными со встроенным legacy-compatible production catalog.

## Как добавить новый товар без изменения Java simulation-кода

1. Добавить item в JSON со следующим плотным `runtimeId` меньше `MAX_ITEMS`.
2. При необходимости добавить recipe, ссылающийся на его stable item ID.
3. Добавить market rule в нужный station archetype либо новый station archetype.
4. Выбрать совместимую cargo category — TradeAI использует category/mineable metadata catalog, а не `ItemType`.
5. Запустить `./mvnw clean verify`.

Acceptance test `DataOnlyItemIntegrationTest` уже создаёт шестой `item.water` с `runtimeId=5`, которого нет в `ItemType`, и доказывает корректную market price/cargo policy.

## Как добавить новый тип станции или корабля

Добавляется новая запись `station.*` или `ship.*` в JSON. `ArchetypeEntityFactoryTest` доказывает создание новых `station.coolant_refinery` и `ship.fast_tanker` generic factory без нового Java типа.

Новая runtime **роль поведения** может потребовать Java-код; новая модель существующей роли — нет.

## Инварианты Stage 4

Data-driven переход не отменяет предыдущие гарантии:

- fixed-step determinism;
- seeded/stateful RNG;
- money/resource conservation;
- stable EntityId;
- exact GameState continuation;
- fail-fast corrupted save parsing.

Любое изменение content/persistence слоя считается готовым только после полного `clean verify`, включая continuation, migration, Javadoc и JaCoCo gates.
