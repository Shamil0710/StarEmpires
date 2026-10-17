# 23B: промышленная регистрация существующей станции

Статус: **IMPLEMENTED / ORDINARY ACQUISITION OPEN**, 2026-10-15.

Регистрация и native restore реализованы в v27. Первый focused-прогон после исправления
передачи admission в session restore: **14 tests, 0 failures/errors, BUILD SUCCESS**,
`target/stage23b-existing-station-admission-focused.log` (19:07:27 +03:00).
Он включает покупку/точный reload, исторический v26, local flight, crew и личные аппараты.
Промышленная работа и обычная покупка refinery этим не подтверждены.

Расширенная регрессия: **33 tests, 0 failures/errors, BUILD SUCCESS**,
`target/stage23b-existing-station-admission-regression.log` (19:08:44 +03:00).
Включены повреждённый codec, неизвестная станция/оператор, покупки оборудования,
ремонт/refit, supply contract, time skip и release governance.
Целевой `verify`: **7 tests PASS**, строгий Javadoc и desktop package PASS,
`target/stage23b-existing-station-admission-package-verify.log` (19:17:53 +03:00).
JaCoCo в целевом verify отключён; это не полная регрессия и не isolated clean verify.

Полная регрессия v27: `mvn -B -ntp -Ptest-parallel verify` — **2687 tests,
0 failures/errors, 1 штатный skip, BUILD SUCCESS**, 19:32:53 +03:00, 14:45 min.
Лог: `target/stage23b-existing-station-admission-full-verify.log`; JaCoCo thresholds,
строгий Javadoc и desktop package PASS. Проверена незакоммиченная рабочая копия;
это не exact-head CI и не финальная isolated clean проверка законченного 23B.
Реальный v26 checkpoint нового пилота прочитан runtime v27 с точным capture:
`target/stage23b-native27-real-v26-migration-audit.log` — PASS, кошелёк 75 000 кр.,
личных станций нет, known systems [2,16], физический reaction mass сохранён.

## Проверенная проблема

`industry.2` не имеет `storage.liquid_tank`. Bootstrap-установка добавляет
chemical-processing capability, но не физическую ёмкость. Рецепт industrial chemicals
требует volatile feedstock; его нельзя доставить в такой склад.

Существующие определения `refinery_complex` имеют 10 млн кг жидкой вместимости
и bulk/chemical структуры; цена в `GeneratedCampaignStationSalePolicy` — 180 000 кр.
`frontier_multipurpose` имеет 20 млн кг жидкой вместимости и цену 200 000 кр.
Эти определения уже авторские: новые числовые параметры для их регистрации не нужны.

До исправления неспециализированные generated endpoints сохраняли только storage/handling.
Созданный по шаблону промышленный node отбрасывался. Проверка цены вызывала
`industrial().station(stationId)` для объекта, которого в промышленном реестре нет.
В проверенном seed 1 реестр содержит только специализированную `industry.2`.
Диагностические instance IDs не являются личными навигационными знаниями игрока.

## Сохраняемая authority

Добавлен отдельный bounded реестр допуска existing endpoints. Это регистрация уже
существующего физического объекта, а не строительство новой станции. Запись содержит
station ID и юридического оператора. Archetype/system/position выводятся из неизменённого
canonical infrastructure placement; дублирующие координаты и такт допуска не сохраняются.
Оператор дополнительно сверяется с реальным ECS-владельцем при parent restore.

Реестр передаётся до восстановления промышленного runtime. Нельзя сначала
восстановить урезанный runtime и затем пытаться восстановить потерянные allocations.
Новое сохранение v27 содержит отдельный versioned admission; исторический v26 получает
пустой реестр. Frozen generation rows и прежние bootstrap fingerprints сохраняются.
Просто наличие storage snapshot или OwnedStationRef не доказывает admission.

## Точки реализации

| Владелец | Необходимое изменение |
|---|---|
| `Stage20IndustrialEntityMaterializer` | Factory для проверенного canonical infrastructure placement; restore принимает specialization IDs плюс явно допущенные IDs. |
| `MaterializedIndustrialRegistry` | Immutable admission существующей станции с проверкой конфликтов идентичности. |
| `Stage20GeneratedIndustrialRuntimeBridge` | `industrialStationView`/restore учитывают допущенные IDs без изменения generation provenance. |
| `Stage20GeneratedWorldRuntimeBridge.LiveRuntime` | Атомарно обновляет industry и infrastructure endpoint registry. |
| `InfrastructureRegistry` | Переключает единственного владельца склада на industrial node, сохраняя ID, геометрию, handling и тот же storage object. |
| `Stage228CampaignAuthority` | Цена из существующего endpoint archetype; юридический продавец из реального ECS; покупка и admission проходят общий isolated preview и single-use commit. |
| Native checkpoint | Явно сохраняет admission и передаёт его до создания дочерних runtime authorities. |

Factory использует `Stage18StationIndustrialNode.instantiate`. Имеющиеся шаблонные
структуры регистрируются с нулевыми power/heat/labor/maintenance и `enabled=false`.
Склад не инициализируется заново: существующие commodities, products и pending custody
должны иметь одного владельца до и после регистрации. Не создаются новые yards.

`OwnedStationRef` остаётся личным имущественным правом. Юридический оператор станции
не превращается автоматически в личную фракцию; территория и гарнизон не меняются.

## Отдельный обязательный источник работы

Admission сам по себе не запускает refinery. `Stage18FacilityResourceAllocator`
перераспределяет уже имеющиеся ресурсы только внутри той же станции. У зарегистрированных
нулевых структур нет донора. Нельзя вывести существующий reactor, power grid, crew или
maintenance из rated profile либо перенести allocations другой станции числовой записью.

Нужен самостоятельный физический игровой путь обеспечения этих ресурсов либо другой
проверенный источник готовых chemicals. В старом диагностическом checkpoint ни один
endpoint не имел stock industrial chemicals/volatile feedstock. Этот отрицательный
результат не следует обобщать на все будущие обычные состояния без проверки.

Дополнительный проверенный пробел: исходные refinery seed 1 не имеют юридического
`FactionComponent` продавца. Production не назначает им владельца ради успешной покупки:
цена и preview покупки закрыты. Положительный интеграционный тест явно регистрирует
существующего NPC-оператора и задаёт конечный бюджет/berth в fixture; отдельный negative
test проверяет исходный объект без продавца. Это не ordinary-play доказательство.

Независимое read-only ревью не нашло блокирующих ошибок после исправления restore chain.
Сценарий pending individual-module handling при promotion/reload проверен 2026-10-16:
частичная обработка, покупка через production preview/submit, точный reload, отдельные
ветки отмены и конечного завершения. Использована существующая `frontier_multipurpose`:
`refinery_complex` не имеет `storage.oversized` и не может хранить индивидуальные модули.
Начальная custody/работа заданы явно как historical fixture; обычный старт `LOAD_MODULE`
до industrial admission этим не заявляется. Запрет покупки станции с pending product
purchase сохранён. Подробности следующего исправления приведены ниже.

## Критерии проверки

- Точный v26 restore не создаёт новых промышленных станций и не меняет их запасы.
- Покупка требует личных координат, фактического berth, денег и существующего продавца.
- Деньги, stock, position и handling сохраняются при admission; повторный допуск отвергается.
- Конфликт с canonical specialization или другим endpoint отвергается.
- Новый node имеет реальные выключенные структуры, нулевые allocations и прежний склад.
- Composed capture содержит один snapshot этого склада; pending transfers не дублируются.
- Частичная обработка, отмена и native reload сохраняют прежнюю материальную custody.
- Распределение без существующего донора отклоняется.
- Реальный ввод и переработка проверяются отдельно после реализации источника ресурсов.

Полная игровая цепочка и итоговый clean verify остаются в
[каноническом остатке](stage23b_remaining_work.md).

## 2026-10-16: сохранение частичной обработки при регистрации

Выявлен и исправлен валидаторный разрыв: `ShipyardModuleCustodyCheckpointValidator`
искал оператора только в `INDUSTRIAL_SPECIALIZATION`, хотя отдельный существующий
civilian endpoint имеет настоящего юридического ECS-владельца без specialization.
Новый module-only handling path в `ShipyardRepairCheckpointValidator` выводит оператора
из физического faction component, persisted/authored faction identity и существующей
экономики; проверяет canonical civilian placement и точный личный berth.
Прежний вход repair/refit/product validation по умолчанию всё ещё требует specialization.
Мощности, ремонтные услуги, деньги и промышленные структуры этим не выдаются.

В `GeneratedCampaignExistingStationAdmissionTest` существующая frontier с её штатной
oversized-вместимостью и ценой 200000 кр. получает явно заданные historical custody и
частичную обработку одного совместимого существующего модуля. Обработка использует
реальный конечный бюджет, меньший либо равный fixed interval. После обычной покупки
проверяются тот же storage instance и занятая ёмкость, неизменные condition/age/owner,
точный native reload и две независимые ветки: отмена оставляет модуль у source; завершение
однократно перемещает его в реальный hold без pristine product/денежного дубликата.
Отсутствие faction, отсутствие docking и физическое удаление на 5000 м отвергаются.
Добавлен конфликт admission с другой существующей economic faction.

`target/stage23b-admission-physical-handling-regression.log`: 23 tests, 0 failures/errors,
BUILD SUCCESS (00:11:15 +03:00). Независимое read-only ревью блокирующих ошибок не нашло.
Полная регрессия актуального кода и новых negative assertions выполняется отдельно:
`target/stage23b-pending-admission-full-verify.log`. Обычное приобретение и ввод refinery
в работу остаются открытыми; это не завершение 23B и не финальный isolated clean verify.
### Подтверждённые результаты 2026-10-16, 00:25 +03:00

Полная регрессия handling/admission завершена: `target/stage23b-pending-admission-full-verify.log`,
**2688 tests, 0 failures/errors, 1 штатный skip, BUILD SUCCESS** (13:22), coverage,
строгий Javadoc и package PASS. Это текущая рабочая копия до следующей политики initial
station legal capital, а не финальный isolated clean verify всего 23B.

Реальный ordinary пилот из сохранения v26 продолжил путь в runtime v27: опубликованные
UI exits и лично известные маршруты привели в system 2 на tick 26357162; exact native reload
PASS (`target/stage23b-ordinary-industrial-navigation-v27-continuation.log`). Затем физическое
сближение по уже полученным личным координатам, dock и обычный `PURCHASE_STATION` завершились
на tick 27200080: `industry.2`, цена 70000 кр., остаток 5000 кр., conserved seller treasury,
неизменная физическая industry/fleet, exact reload PASS
(`target/stage23b-ordinary-industry2-acquisition-v27.log`). Денег, координат и berth fixture
в этом продолжении нет. Обычный ввод химической цепочки и строительство yard ещё открыты.
