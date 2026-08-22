# Star Empires — generated-world command UI

> Статус: Stage-21 entry foundation over the accepted Stage-20/20.5 runtime.

## Назначение

Интерфейс запускает тот же generated world, который прошёл Stage-20.5 final acceptance. Он не
создаёт отдельную UI-симуляцию и не заменяет физические координаты, груз, владельцев, фит,
маршруты или jump state.

Production bootstrap находится в `Stage20PlayableGeneratedWorldFactory`. Новый мир проходит
accepted generation/planning chain один раз. Загрузка сохранения использует только
`Stage20GeneratedWorldRuntimeBridge.restore` и не перегенерирует сохранённую вселенную.

## Вкладки

### Система

Показывает объекты текущей active simulation area:

- обычные ECS-сущности;
- persistent freight fleets;
- станции и верфи;
- добывающие аванпосты;
- конечные resource occurrences;
- jump/resource anchors;
- anomalies, derelicts и resource phenomena.

Каждый отображённый объект выбирается мышью. Inspector использует read-only runtime projection и
показывает только доступные authoritative данные: имя, ID, фракцию, координаты, физическое
хранилище, промышленное оборудование, груз, корпус, фит, фазу и маршрут.

Если несколько объектов имеют одинаковые физические координаты, их маркеры немного раздвигаются
на экране. Это presentation-only declutter: persistent `LocalPhysicalPosition` не изменяется.

### Галактика

Показывает generated `Galaxy → Sector → StarSystem` topology, реальные jump connections,
контролирующие фракции и active system. Выбор другой системы открывает её карточку. Команда
`ОТКРЫТЬ СИСТЕМУ` меняет только full-tick simulation area через `WorldSimulation.activateSystem`;
она не телепортирует корабли.

### Фракции

Показывает persistent faction identity, контролируемые системы, казну, налоги/тарифы,
территориальные претензии, стратегические цели, договоры, эмбарго и число физических транспортов.

### Логистика

Показывает каждый real `FleetId`, владельца, lifecycle phase, cargo mass/capacity, hull, fit,
товар, source/destination endpoints, ordered neighbor route, delivery deadline и задержки.

`GeneratedWorldFreightAutopilot` выполняет только обычные Stage-18/20.5 операции: finite
extraction, staging, loading, dispatch, neighbor jumps, unloading and return. Он не создаёт cargo,
fleet replacements или shortcut arrivals.

## Масштабирование и стиль

- UI scale вычисляется независимо от world zoom для viewport от 900×620 до 4K;
- TTF-шрифты генерируются под текущий pixel size и включают кириллицу;
- hit radius сохраняет минимальный удобный размер;
- панели имеют bounded width на ultrawide/4K;
- детали прокручиваются колесом мыши;
- палитра следует `Империя — визуальный код v0.1`: graphite/gunmetal, warm ivory, burgundy,
  restrained brass, muted cyan, service amber и emergency red;
- UI остаётся функциональным и прямоугольным, без decorative hologram/cyberpunk элементов.

## Windows launcher

```bat
run-generated-world.bat
```

Другой seed можно передать первым аргументом:

```bat
run-generated-world.bat 8
```

Seed обязан пройти текущий accepted remote-refining bootstrap. Канонический первый playable seed —
`1`.

Управление:

- `F1` / `F2` / `F3` / `F4` — вкладки;
- ЛКМ — выбор;
- колесо мыши — inspector scroll;
- `SPACE` — пауза;
- `1`–`4` — скорость ×1/×2/×4/×8;
- `F5` / `F9` — сохранение/загрузка;
- `ESC` — выход.

Сохранение: `saves/generated-world-runtime.s25`.

## Scope boundary

Это production-facing generated-world UI foundation и точка входа в Stage 21. Она не объявляет
готовыми NPC, missions, reputation или narrative event systems Stage 21 и не заменяет финальный
Stage-23 accessibility/onboarding/polish gate.
