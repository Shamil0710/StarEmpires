# 23B: масштаб точных свидетельств добычи

Статус: PARTIAL, реализация и проверки интеграции, 2026-10-11.
Масштабная добыча из реального сохранения и полный путь продажи ещё проверяются;
это не доказательство завершения 23B.

## Продолжение 2026-10-11: реальные receipts свыше прежнего row limit

`target/stage23b-actual-extraction-over100k-audit.log`: **AUDIT PASS** на heap 3 ГБ.
Проверен output `target/stage23b-ordinary-production-capital-series.s25`, tick 13326974:
47124 cargo rows, 187 компактных серий / **112983 новых физических receipts**,
максимальная серия 1796 receipts, freight payload **14637508 bytes**, allocator 181938.
Точные restore/capture и freight encode/decode равны исходному состоянию; read-only аудит
не изменил кампанию. В трюме 117257.30367615103 кг metallic_ore, кошелёк 84967000 milliCredits.
Это подтверждает масштаб реальной добычи более 100000 receipts в прежних bounds,
но не завершение цели 1 млн кг, продажи или всего обязательного интеграционного набора.

Отдельное продолжение `target/stage23b-production-capital-million-continuation.s25`
кооперативно сохранено на tick 13328459 штатным QA stop hook перед пересборкой.
Thread dump выявил ещё один полный freight capture каждого такта в
`GeneratedCampaignSession.observeFreightDeliveryDeadlines`. Метод теперь читает текущие
`freighterStates()`, сохраняя проверку срока каждого active order на каждом обычном такте;
полная валидация ledger остаётся на capture/save. Native framing и физические правила не меняются.

`target/stage23b-series-deadline-verify.log`: **17 tests, 0 failures/errors/skips,
BUILD SUCCESS** за 1:26, Java 17, строгий Javadoc и desktop packaging. Проверены
session tick/frame partition и restore, freight recovery, личная добыча, compact codec и FIFO.
JaCoCo использовал накопленный exec с предупреждениями о несовпадении некоторых изменённых
классов; этот целевой прогон не является новым полным подтверждением coverage всей ветки.

`target/stage23b-series-deadline-actual-resume.log`: exact resume с tick 13328459,
ещё **20000 обычных физических ticks**, штатный budget stop и native save на **13348459**.
`target/stage23b-series-deadline-actual-audit.log`: **AUDIT PASS** на heap 3 ГБ;
47160 cargo rows, 223 серии / **134468 новых receipts**, freight payload **14651008 bytes**,
allocator 203459, фактический трюм **133010.899447284 кг**, прежний кошелёк 84967000 milliCredits.
Следующий input — `target/stage23b-production-capital-million-continuation.s25`.
Исходный output tick 13326974 сохранён отдельно без перезаписи. QA-процессы завершены.
На этом checkpoint добыча 1 млн кг, обратный полёт и conserved sale ещё не были завершены.

## 2026-10-11: миллион кг, обычный возврат и конечные деньги покупателя

`target/stage23b-production-capital-million-run.log`: exact resume с tick 13348459;
обычная непрерывная добыча остановлена командой STOP_MINING на **14530873**.
Фактический output — **1000000.5289471328 кг metallic_ore**. Composed native encode/decode,
restore и сравнение полного state прошли (`JOURNEY CHECKPOINT PASS`). После **397544**
обычных физических ticks обратного полёта выполнен DOCK hub.14 на **14928417**.
Ни simulation time, ни позиции, источник, оборудование, топливо или деньги не подставлялись.

Полная продажа **отказала до первой сделки**: handling 200000 кг/такт, цена 18000 milli/kg,
кошелёк станции только **8000 milliCredits**. Read-only аудит подтвердил treasury faction.beta=0,
поэтому существующий working-capital review не может пополнить рынок. Кошелёк игрока
сохранил **84967000 milliCredits**; миллион кг не объявляется полученным денежным капиталом.
Финальный checkpoint прибытия — `target/stage23b-production-capital-million-continuation.s25`,
tick **14928418**, docked=true. Старый журнал назван `million-run`, но содержит явный FAILURE
продажи и не является PASS полной цепочки.

`target/stage23b-million-arrival-market-audit.log` и `target/stage23b-million-known-market-audit.log`:
**AUDIT PASS** на heap 3 ГБ, точный restore/capture и freight round trip. Freight:
49133 rows, 2188 компактных серий / **1316882 новых физических receipts**, largest series 1796,
payload **15390976 bytes**, allocator 1388501. Исходные point lots не сливались при restore.
Диагностические чужие кошельки/казна не записываются в personal knowledge и не открывают сделки.

Следующий уже известный рынок — hub.16, личный маршрут **14→19→11→16**.
В audited checkpoint его кошелёк 10025000 milliCredits, treasury faction.alpha=25000000;
эти конечные средства недостаточны для продажи всего миллиона. QA теперь умеет продолжать
продажу выбранной части у лично известного другого рынка: `sellOnly=true`,
`saleStation=hub.16`, `saleKg=1000`, `saleBatchKg=500`, `miningKg=1000000`.
Он использует существующий PlayerFleetRoutePlanner, обычные UNDOCK/JUMP/fly/DOCK/SELL,
и проверяет уже полученные личные координаты; новые координаты и средства не выдаются.
Аудитор также сообщает tick/docked и при явном `markets` читает только уже известные станции.
Оба изменённых QA helper скомпилированы Java 17; production code этим продолжением не менялся.

`target/stage23b-million-known-buyer-continuation.log`: UNDOCK и JUMP 14→19 приняты
на tick **14928418**; штатный MOVING_TO_JUMP рассчитан до **16594571**.
Reviewed existing QA stop hook сохранил runner на обычной tick boundary **15000000**,
без редактирования world objects. Процесс завершён; input прибытия сохранён отдельно.
Следующий resume — `target/stage23b-million-known-buyer-continuation.s25` с параметрами выше.
`target/stage23b-million-known-buyer-resume-audit.log`: **AUDIT PASS**, tick 15000000,
docked=false, неизменные миллион кг и личный кошелёк; 49134 rows, freight 15391308 bytes.
Продажа части, дальнейшее финансирование/заправка и полный интеграционный FIFO/supply набор
остаются незавершёнными. Статус 23B **ACTIVE/PARTIAL**.

Первый реализованный компонент — `ExtractionReceiptSeries`: исходный allocated ordinal/tick,
неизменный fixed step и измеренная масса, текущий FIFO head и сохранившиеся original anchors.
Append допускает только битово равные step/mass и соседние реальные ordinals/ticks;
partial consumption сохраняет дату первого receipt, полное — сдвигает FIFO head.
После удаления префикса initial allocation history не исчезает. Время вычисляется тем же
целочисленным tick × original fixed step, не суммированием дат или усреднением.
`target/stage23b-receipt-series-verify.log`: 3 tests, BUILD SUCCESS, Javadoc/package;
проверены 200003 receipts с точными временными битами, partial FIFO и переполнения.
Компонент включён в CargoLotState, реальный mining runtime и FIFO SELL/LOAD/UNLOAD/consumable
consumption. Freight schema 8 хранит серии; payload без серий сохраняет подлинное framing schema 7.
Native v22 допускает новое представление; поддельный заголовок v21 с сериями отклоняется.
Исторические point lots при чтении остаются отдельными исходными receipts, без слияния или grants.
`target/stage23b-series-runtime-verify.log`: 28 tests, BUILD SUCCESS, Javadoc/package.
Проверены реальная добыча через authority, composed round trip, forged older header,
200003 виртуальных receipts, частичный FIFO, грузовые операции и исторические сохранения.

Профиль `target/stage23b-series-scale-threads.log` выявил дополнительный capture всей истории
грузов при synchronizeCompletedHops каждого такта. Этот путь теперь читает только текущие
freighter rows; полная проверка сохраняется на границах capture/save. Регрессия и сборка:
`target/stage23b-series-hop-synchronization-verify.log`, BUILD SUCCESS.
Первое реальное продолжение сохранено штатным QA tick hook на tick 13226969;
`target/stage23b-production-capital-series-optimized.log` содержит продолжение этого output
на исправленном коде. Пока не утверждается достижение 100000 receipts или цели 1 млн кг.

Read-only аудит `target/stage23b-actual-extraction-series-audit.log` подтвердил точный restore
и freight round trip: 46962 строки, 22 серии / 12978 новых receipts, freight payload 14576658 bytes,
allocator 81768; груз 43929.94295490357 кг, кошелёк 84967000 milliCredits.
Следующий прогон обнаружил неверную численную границу при сверке общего трюма:
сложение поступлений округляется на масштабе всего трюма, а не короткой отдельной серии.
Его последующая запись отказала; последним проверенным output остаётся tick 13226969.
Исправление учитывает число исходных физических сложений и ULP вместимости трюма;
регрессионный fixture разбивает 200003 реальных по формату receipts на короткие серии.
Ни количество receipts, ни физические запасы этим исправлением не увеличиваются.
Также autopilot теперь читает текущие freighter rows без capture всей истории на каждом такте.

`target/stage23b-series-hold-roundoff-final-verify.log`: 22 tests, 0 failures/errors,
BUILD SUCCESS, Java 17, Javadoc, packaging и coverage gates. Исправленный реальный прогон:
`target/stage23b-production-capital-series-roundoff-fixed.log`; checkpoint каждые 20000
мировых тактов. Обе выявленные горячие проверки устранены без пропуска физических тактов.
Требуется полноценная интеграция ниже, включая численную mass consistency при больших сериях,
доставочные receipts, исторические payloads и реальный миллион-кг рейс. Лимиты не подняты.

## Наблюдаемая проблема

Обычный рейс `target/stage23b-ordinary-production-capital.s25` реально прибыл к лично
наблюдённой metallic_ore в системе 14. На tick 13213991 в собственном трюме
34 413,99387795392 кг; кошелёк 84 967 кр. Добыча/сохранение проверены без выдачи
оборудования, источника, координат, геометрии, денег или материалов.

`Stage20FreightRuntime.extractPersonalCommodity` создаёт отдельный CargoLotState на каждый
успешный такт. `Stage20FreightPersistenceCodec.MAX_ROWS=100000`, payload bounded 64 МБ.
Цель рейса — 1 млн кг; её точные point receipts превышают этот предел задолго до
полного трюма. Полный bill строительства ещё больше. Простое повышение лимита не
устраняет стоимость live snapshots и многомиллионных collections.

Слияние с усреднённой/первой датой потеряет требуемые точные FIFO-доли, source endpoint,
provenance и время загрузки. Такое слияние не является допустимым завершением задачи.

## Обязательные свойства решения

- Компактная серия только для действительно последовательных успешных личных extraction
  receipts с одинаковыми source/order/commodity/provenance и битово равной массой результата.
- Исходные виртуальные lot ordinals, ticks и времена каждого receipt сохраняются.
  Время восстанавливается тем же `tick * fixedStepSeconds`, без усреднения или нового наблюдения.
- Непоследовательные ordinals/ticks, иная масса, иной источник и изменённые параметры
  разрывают серию. Загруженные старые point lots не получают прошлой добычи при restore.
- Оставшиеся точные receipt portions после частичной обработки сохраняют исходную
  личность/дату. Полное снятие первого receipt сдвигает FIFO head к следующему;
  частичное снятие сохраняет исходную дату именно этого receipt.
- Количество receipts и allocator watermark не заменяются количеством сериализованных
  серий. Overflow, чужой order/source, будущие ticks, неверный шаг, нестыкующийся mass
  ledger и пересекающиеся виртуальные IDs отклоняются до live mutation.
- Физические source/hold/handling/work/power и money authorities остаются прежними.
  Численная проверка должна учитывать существующую SI-точность и double-суммирование,
  не превращать погрешность в новый запас и не менять полученные физические результаты.
- SELL, собственные LOAD/UNLOAD, consumable loading, loss и supply-contract receipts
  должны расходовать одни и те же точные FIFO portions; масса и происхождение не теряются.
- Нужны явная freight schema и native admission нового представления (предварительно
  schema 8 / native v22), genuine historical v1–21 payloads, отсутствие grants при restore,
  отказ forged older header и полный composed save/resume. Не менять старые каталожные
  массы, recipes, fingerprints или капитал ради этого исправления.

## Доказательства, необходимые для закрытия

1. Физическая непрерывная добыча с более чем 100000 реальными receipts и сохранение
   compact state в прежних разумных byte/row bounds, без явного увеличения DOS limits.
2. Сравнение с некомпактными receipts: точные IDs/loaded times/source/provenance,
   частичный FIFO, последующие загрузки, источник/трюм/работа/энергия и money boundary.
3. Прерывание серии сменой source/rate/другим allocator receipt; partial reload и
   продолжение на следующем настоящем такте без дублирования работы.
4. Обработка потери, over-capacity, replay/stale preview, отказ невалидных intervals;
   настоящий SELL receipt и accepted supply predicate с сохранением причинности.
5. Точная миграция реального output tick 13213991 и продолжение цели 1 млн кг,
   возврат и conserved sale. До этого production capital не считать полученным.

## Уже выполненное соседнее исправление

Профиль `target/stage23b-production-capital-threads.log` выявил повторную полную
projection/fingerprint загрузку каталога в конструкторе resolver каждого mining tick.
`ProductionEngineeringRuntimeResolver` теперь разделяет только immutable default catalogs;
calculators, ship state и budgets остаются отдельными. Повторный extraction sample
не захватывает весь freight state после уже имеющегося receipt. `target/stage23b-mining-runtime-catalog-verify.log`:
23 tests, BUILD SUCCESS, Javadoc/package. Это не исправляет cardinality point lots.

QA кооперативно сохранён в конце обычного tick через reviewed runner redefinition,
его стандартные catch/finally и atomic native writer; world objects не менялись агентом.
Exact audit до/после optimized resume прошёл. Процесс закончен; можно продолжать
из существующего output после реализации масштабируемого ledger. Input earning,
acquired station и завершённый localFuelSearch сохранены отдельно.
