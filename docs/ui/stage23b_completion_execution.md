# Исполнение полного завершения 23B

## Продолжение интеграции палубы, 2026-10-13

Обычный `Stage228CampaignAuthority.advanceFrame` теперь обрабатывает существующие
launch/recovery cycles связанных carrier wings через реальные установленные отсеки.
`GeneratedCampaignCarrierBays` разделяет физическую проекцию между UI и world progression;
личные права видимости не останавливают NPC-палубу. Проекция вычисляется заново после
каждого world tick. Нулевое повреждённое состояние, отсутствующий отсек, прыжок или
несогласованный оператор не предоставляют работу. Не связанные с wings host IDs по-прежнему
требуют explicit physical provider до продвижения времени.

`GeneratedCampaignCarrierTickTest` проверяет паузу, ровно один обычный такт, замедление
при 50% integrity, остановку при нулевой integrity/снятом ангаре, точный codec/restore
незавершённой палубной работы и окончание в AWAITING_HANDOFF с прежним LAUNCHING occupancy.
Проверка использует явный carrier/craft fixture и отдельный sidecar; обычное получение,
физический handoff/миссии и composed native/content admission остаются открыты.
Первый целевой прогон: `target/stage23b-carrier-ordinary-tick-test.log`, 11 tests,
0 failures/errors/skips, BUILD SUCCESS. Итоговый лог после добавления проверки снятого
ангара: `target/stage23b-carrier-ordinary-tick-verify.log`, **11 tests, 0 failures/errors/skips,
BUILD SUCCESS**, строгий Javadoc и desktop packaging. Проверка целевая, JaCoCo отключён;
это не полный coverage/clean verify gate.
Статус этапа остаётся **23B ACTIVE/PARTIAL**.

## Активное полное завершение, 2026-10-11

Запрос «до полного завершения 23B» повторно подтверждён. Цель охватывает весь канонический
остаток и остаётся активной; промежуточные тесты/сохранения не являются её завершением.
Ручная приёмка пользователя после этапа 23 не заменяется и не блокирует текущую разработку.

- Добыт миллион кг, выполнены native round trip и настоящий возврат/DOCK hub.14.
  Продажа отвергнута из-за конечных денег: станция 8 кр., казна Союза 0; капитал не получен.
- Единственный действующий writer earning продолжения: `Stage23BOrdinaryMiningJourney`,
  output `target/stage23b-million-known-buyer-continuation.s25`, лог
  `target/stage23b-million-known-buyer-native23-resume.log`, immutable classpath
  `target/qa/stage23b-native23-flight-runtime.jar`. Session 60630; старт resume tick 23600000.
  Параметры: sellOnly=true, saleStation=hub.16, saleKg=1000, saleBatchKg=500,
  miningKg=1000000, maxTicks=10000000, checkpointInterval=100000. Все три hop 14→19→11→16 завершены;
  идёт настоящий локальный подход к лично известной hub.16. Не запускать второй writer
  к этому output. Следующие сборки не заменяют immutable jar работающего процесса.
  Предыдущий процесс с heap 3 ГБ получил OOM при checkpoint на tick 23600000;
  сохранение прочитано и точно восстановлено. В QA освобождён уже проверенный snapshot
  перед длительным полётом, новый процесс имеет heap 6 ГБ. Последний исходный файл отдельно
  сохранён в `target/stage23b-million-known-buyer-before-resume.s25`. Продажа пока не доказана.
- Native v23 сохраняет явный initial commissioning crew manifest. Старые v1–22 файлы и
  BASELINE получают неизвестный состав, загрузка не выдаёт экипаж. Приобретение/аффилиация
  не меняют roster; активный независимый пилот подтверждает только одного человека.
  `target/stage23b-crew-native23-verify.log`: BUILD SUCCESS, targeted verification,
  Javadoc/package, без покрытия. Контракт зафиксирован в release change control.
- Новая radar station reflection composition и coasting scan проверены: 10 tests,
  BUILD SUCCESS (`target/stage23b-static-radar-coasting-verify.log`). Реальный отдельный
  input прибытия сохранён; output `target/stage23b-ordinary-station-radar.s25`,
  `ORDINARY STATION RADAR PASS`, range 4900.026190682875, tick 14931824.
  Последующее shared-interval scan/thrust продолжение: **19 tests, BUILD SUCCESS**, Javadoc/package,
  `target/stage23b-radar-thrust-shared-interval-verify.log`. Реальный output
  `target/stage23b-ordinary-station-radar-with-thrust.s25` имеет range 4900.0279686494805,
  tick 14931825, thrust=true и **ORDINARY STATION RADAR PASS**, точный composed reload на 3 ГБ.
  Управление/топливо совпадают с обычным PlayerDirectControlSystem без второго такта.
- Формирование личных Stage-21D групп подключено к production UI/preview/confirm/journal/native;
  7 tests, BUILD SUCCESS (`target/stage23b-player-command-group-verify.log`). Отдельный
  real-input QA: `Stage23BActualCommandGroupJourney`, output
  `target/stage23b-actual-player-command-group.s25`, лог
  `target/stage23b-actual-player-command-group-memory-fixed.log`: **ACTUAL COMMAND GROUP PASS**,
  tick 14928418, wallet 84967000 milliCredits, fleet:26, home 14, groups=1.
  Первоначальный QA удерживал уже проверенные большие snapshots и previews до файловой записи
  и получил OOM на heap 3 ГБ; его log сохранён. После освобождения ненужных ссылок точная
  запись/reload прошли на **тех же 3 ГБ**, без изменения production codec или limits.
  Этот отдельный output не является writer earning ветки и не выдаёт crew/readiness или боевые возможности.
  `target/stage23b-actual-command-group-graphics.log`: **graphical probe passed**, RTX 4070,
  heap 4 ГБ, клавиатурные formation preview/confirm, неизменные player assets/money и F8/F9.
  Проверено добавление второго ранее не назначенного собственного корабля в новую группу.
  Снимки preview/complete сохранены в target. Панель формирования после визуального просмотра
  получает отдельное пояснение состава/регистрации/домашней системы вместо сведений автопилота.
  Обновлённая панель повторно прошла реальный graphical probe на native v23:
  `target/stage23b-native23-command-group-graphics.log`, keyboard preview/confirm и F8/F9.
  Снимки `target/stage23b-native23-command-group-preview.png` и `...-complete.png` сохранены;
  preview визуально проверен: фактическая регистрация и отсутствие исторического crew observation.
- Живая проекция FleetForceRegistry не копирует экономику/историю/прочие объекты мира при
  формировании группы. Результат идентичен persistent reconstruction до/после реального
  jump request; мир не изменяется. `target/stage23b-live-force-registry-verify.log`:
  9 tests, BUILD SUCCESS. Личный composed transition также удерживает timeSkip и crewManifest;
  `target/stage23b-crew-transition-verify.log`: 4 tests, BUILD SUCCESS.
- Общая регрессия `target/stage23b-current-full-verify.log` выявила устаревшее удаление
  current sidecars в тестовой historical framing fixture и пропуск adjacent owners в
  тестовом сравнении stock policy. Они исправлены с сохранением строгой валидации payload.
  Выявлен отдельно Stage-22 freeze drift только по child file format 4→5 и supported formats:
  текущий manifest намеренно versioned v4, исторический v3 сохранён неизменным. Оба требуют
  проверки обновлёнными тестами; этот первый full run не является успешной финальной проверкой.
  `target/stage23b-native23-regression-repair-verify.log`: **36 tests, BUILD SUCCESS**.
  Повторный полный `target/stage23b-current-full-recheck-verify.log`: **2640 tests,
  0 failures/errors, 1 configured skip, BUILD SUCCESS**, 15:42; все coverage thresholds,
  упаковка и строгий Javadoc прошли. Это подтверждает текущий код, не закрывает игровые gaps.
  Свежий `clean verify` запущен в отдельной копии
  `target/stage23b-clean-verify-workspace`; лог `target/stage23b-isolated-clean-verify.log`.
  Исходная папка target с реальными сохранениями и runtime работающего writer не очищается.
- Отдельный обычный radar approach QA: `target/stage23b-unknown-station-observed-approach.log`,
  output `target/stage23b-unknown-station-observed-approach.s25`, tick 14960012.
  Оплаченный импульс и 8 реальных подходов выбраны только по measured contact estimates;
  неизвестная станция не достигнута. Результат **PAUSED / NOT PROVEN**, не PASS.
  Поиск не подменяется координатами endpoint/source и не закрывает ordinary discovery seam.

Следующие обязательные seams: личные групповые приказы/операции с действительной crew/supply
authority, носители и малые аппараты на обычных world ticks и их native/content admission,
физическая количественная разведка ресурсов, обычное снабжение/промышленность/работающая верфь,
полная причинная NPC-поставка, новая станция, окончательные UI/графические и полные проверки.
Не маркировать 23B COMPLETE и не переходить к 23C до закрытия этих критериев.

### Решение по статическим радарным контактам

Существующие station archetypes не имеют fitted ship signature. Для активного отражения
используется явный transient контракт `stage23b.station-footprint-radar.v1`: RCS = 0.01 ×
authored footprintLengthM × footprintWidthM из существующего Stage20StationPhysicalGeometryProfile.
Это номинальная модель отражения, а не гарантированный нижний предел реального RCS.
Она не выводит сигнатуру из массы склада/установок и не создаёт источник тепла или радиоэнергии.
Импульс оплачивается тем же ShipEngineeringGrantService.IntervalBudget один раз для каждого
реального radar mode; mobile и static targets получают измерения одного импульса через тот
же ShipObservationService. При недостатке power/heat нет частичной траты или новых наблюдений.
Для simultaneous thrust scan используются actual TickResult powerSupplyW/powerDemandW,
потраченный discharge rate и actual recharge того же completed interval, дополнительно ограниченные
номинальным аппаратным margin. Новый beginAfterPropulsion не начисляет энергию и не повторяет
охлаждение/движение; pulse применяет лишь surviving interval headroom. Превью переносит transient
ввод в изолированную candidate authority, а confirmation сравнивает axis/braking до live tick,
так что смена ввода не может сделать подтверждённый бюджет устаревшим после mutation.
Только настоящий pulse создаёт анонимные SensorKnowledgeComponent measurements/tracks;
presentation и restore ничего не измеряют. Сенсорные поля уже сохраняются в world v5/native v22;
новый payload, объект станции, бюджет или изменение geometry/content fingerprints не вводятся.
Динамическая позиционная оценка не помещается в immutable static-location archive: там
по-прежнему нужны физическая стыковка либо действительно принятые advertised coordinates.

Запрос пользователя от 2026-10-05: продолжать до полного завершения 23B.
Канонический остаток — [stage23b_remaining_work.md](stage23b_remaining_work.md).
Ветка: `stage23b-production-ui-consolidation`. Существующие незакоммиченные изменения сохраняются.
Ручная приёмка назначена после всего этапа 23; автоматическая проверка её не заменяет.
Запрос 2026-10-10 «до полного завершения 23B» принят как активная цель всего остатка.
Ограниченный QA-отрезок и сохранение промежуточного полёта не являются концом этой цели.
Для полного прибытия localFuelSearch запущен отдельный continuation до 4 млн реальных
ticks с промежуточной записью каждые 100 тыс.; лог
`target/stage23b-actual-local-fuel-search-completion.log`. Не запускать второй writer к
`target/stage23b-actual-local-fuel-search.s25`, пока этот процесс работает.
Одновременно запущено отдельное обычное получение производственного капитала из
`target/stage23b-ordinary-yard-funding.s25`: 84 967 кр., собственный miner fleet:26,
система 14, известные source/hub координаты, 4 742 813,40 кг реактивной массы.
Output `target/stage23b-ordinary-production-capital.s25`, лог одноимённый `.log`.
Цель рейса — 1 млн кг существующей metallic_ore, настоящий полёт/добыча/продажа;
cruise 500 м/с, партии продажи ограничены фактической обработкой и ликвидностью.
До итогового PASS заработок не считать полученным. Это отдельная ветка настоящего
исторического прогресса; текущая приобретённая база и поисковый полёт не откатываются.
Оба процесса пишут разные файлы. Не запускать конкурирующие writers к их outputs.
Обновление: localFuelSearch **завершён**, actual DOCK hub.2 на tick 38299312,
exact restore audit PASS, кошелёк 14 767 кр.; hub.2 теперь лично известен.
Earning рейс физически прибыл на собственный известный участок и начал добычу.
Для пересборки производительности он кооперативно сохранён на tick 13203991 (27 081,624 кг
руды), без остановки посреди доменного такта/отката. Затем пройдены ещё 10 000 ticks
на исправленном resolver; output tick 13213991. Миллион кг и продажа ещё не завершены.
Полное продолжение требует устранить scale gap point cargo lots: каждый tick создаёт
отдельный receipt, codec ограничен 100 тыс. rows. Нельзя стирать исходные даты или
увеличивать предел вместо масштабируемого точного учёта. Следующий подшаг — lossless
series representation, partial FIFO handling и версия сохранения с исторической совместимостью.

Продолжение 2026-10-10: предложения существующих ресурсных карт показывают архивный вид
сырья без неоплаченной навигации. На реальном v21 checkpoint оплачены карта проводников
и существующая маршрутная карта (суммарно 200 кр., кошелёк 14 767 кр.). Личные пути:
металлическая руда — 2→7→9→13→16→11→19→14; проводники — 2→7→8→3.
У местного продавца отсутствует карта light_metal_minerals; три сырьевых пути не закрыты.
В UI добычи подключены выбор кг, Shift/Ctrl и количество в журнале. 10 целевых тестов,
Javadoc и packaging прошли; GPU-покупка карты и exact F8/F9 также прошли.
Попытка реального вылета отклонена штатным onboard fuel preflight: известный маршрут
с возможным снабжением не означает достаточное текущее топливо. Отдельный checkpoint
`target/stage23b-actual-conductor-supply.s25` сохранён после UNDOCK, без добычи/доставки.
Следующий зависимый шаг — обычное физическое снабжение корабля и продолжение доставки.

Дополнительное продолжение 2026-10-10: read-only fuel audit подтвердил 87 643,15 кг
reaction mass в miner, отсутствие воды в собственном трюме/industry.2 и отсутствие liquid
storage у этой станции. Projected journey предполагает загрузку 709 584,44 кг на hub.2,
который ещё неизвестен лично; диагностический удалённый stock не передан в personal knowledge.
По цене 2,5 кр./кг такая покупка превышает текущие 14 767 кр. Местный dispatcher не имеет
доступных station-map offers: purchases=0. На отдельном `target/stage23b-actual-fuel-access.s25`
выполнен только обычный повторный DOCK в собственной industry.2; exact native reload прошёл.
Для маршрута добавлена отдельная проверка onboard fuel первого прыжка, для SHIPS/supply —
крупные шаги кг. Заправка, финансирование и обнаружение местного жидкого дока остаются
игровыми зависимостями; никаких материалов/координат/денег при загрузке не выдано.
Начат обычный поиск по заранее выбранному локальному origin waypoint, не по скрытой
координате дока: 60 000 настоящих ticks, cruise 25 м/с, native checkpoint tick 34673641
в `target/stage23b-actual-local-fuel-search.s25`. Exact restore/read-only audit прошёл.
Дальнейшее прибытие, фактический контакт и заправка не заявлены. Итоговые 8 целевых
тестов, Javadoc/package и RTX 4070 fuel UI/F8/F9 прошли; детали в журнале реализации.

Актуальное продолжение 2026-10-09: компонентные партии и native v20 подключены;
добавлен собственный перенос сырья LOAD_COMMODITY / UNLOAD_COMMODITY без оплаты.
11 тестов, строгий Javadoc и packaging прошли. Реальный acquired checkpoint подтвердил
1 кг existing ore → трюм → exact native reload → следующий такт → свой склад с лимитом
3 ГБ; GPU-кнопки и F8/F9 прошли. Следующий зависимый шаг — обычное получение остальных
материалов и их доставка для полного bill секции, затем подходящая действующая верфь.
Детали и логи — в журнале реализации; завершение 23B не заявляется.
Полная цепочка one-section bill проверена отдельным физическим тестом: 8 refining batches,
3 component batches, сборка 1,5 млн кг, полная конечная работа и partial native reload каждого
этапа. Добавлен собственный агрегированный supply plan и исправлены дробные списания на
прежней точности 1e-9 кг. Итог: 23 теста, строгий Javadoc и packaging — BUILD SUCCESS.
Тестовый feedstock/line fixture не закрывает обычное получение всех ресурсов и мощностей:
реальный read-only аудит не нашёл действующей линии пяти недостающих рецептов в текущем файле.
Следующее продолжение: управление всеми исходными собственными установками и режим 50 %
штатных ресурсов подключены к owner/berth guards, UI, журналу и прежним native snapshots.
6 целевых тестов, строгий Javadoc и packaging прошли. На настоящей базе подтверждены
half/full и сохранение четырёх ресурсных сумм; GPU-кнопки и F8/F9 также прошли.
100 кг существующей руды реально переработаны за 100 вместо 50 тактов, без выдачи progress.
Продолжимый checkpoint — `target/stage23b-ordinary-half-rate-refining-restored.s25`:
759,7472054745352 кг руды, 126 кг металла, rated bulk refinery, кошелёк 14 967 кр.
Дальше требуется физическое решение получения недостающих установок/обработанных
материалов: они отсутствуют в текущем мире, а их строительство само требует таких входов.
Следующий срез разрывает строительный cycle отдельной batch-processing установкой:
25 млн кг трёх обычных bulk-сплавов, 4 млн рабочих секунд, отключённый physical completion,
явное включение из существующих station allocations. Default opening capital и прежние
bills сохранены. Native v21 добавляет два проверяемых runtime content fingerprints.
49 целевых/исторических тестов, Javadoc и packaging прошли; реальная база и GPU подтверждают
совместимую существующую assembly, чистый отказ на missing bill и точный v21 save/load.
Продолжимый файл — `target/stage23b-bootstrap-materials-v21.s25`, без нового сооружения.
Дальше: добыча/доставка metallic/light-metal/conductor feedstocks, выпуск трёх строительных
сплавов и обычное строительство/ввод периодической установки. 23B остаётся ACTIVE/PARTIAL.

## Порядок зависимостей

1. Физическая основа строительства и ввода мощностей: реальные килограммы, сохранение
   резерва и места, конечная общая работа, территориальные права, владение.
2. Обычное получение собственной станции и верфи, поставка материалов, player commands и UI.
3. Изготовление, доставка и установка нового оборудования, доступ и расчёт услуг чужих станций,
   боезапас; полный путь добычи и продажи без выдачи оборудования тестом.
4. Получение ограниченных ресурсных измерений через реальные службы наблюдения;
   карты/наложения и остальные закрытые показатели привязать к действующему игроку.
5. Собственные командные группы, операции и фактические территориальные переходы.
6. Игроковые carrier/small-craft команды и обычное получение физических активов.
7. Причинные NPC-предложения из реальных знаний/потребностей и существующего финансирования.
8. Совместная проверка сохранений/миграций, итоговый графический прогон и необходимый полный
   `clean verify`, согласование точного revision и документации.

## Текущий технический checkpoint

Согласование списка ресурсных наблюдений и инспектора выполнено в рабочей копии:
23 целевых теста, строгий Javadoc, `target/stage23b-resource-intelligence-final-validation.log`.
Новых измерений эта проекция не создаёт.

Добавлен `Stage18FacilityConstructionWorkQueue` поверх существующей Stage-18H construction
authority. Он использует точный авторский bill, атомарно резервирует доступное сырьё,
сохраняет его складскую вместимость и прогресс в существующих construction-order snapshots.
Общий бюджет расходуется на последовательных world ticks. Повторный tick не выполняет работу,
пропущенный интервал отклоняется. Завершённый заказ сохраняет evidence для materialization;
сырьё становится частью сооружения и освобождает складской резерв. Отмена разрешена только
до начала работы: последующая разборка требует уже принятой физической политики salvage.

`Stage18StationStorage` учитывает строительный резерв совместно с товарами, изготовлением,
ремонтом, переоснащением и индивидуальным оборудованием. Сам snapshot склада резерв не
дублирует: при восстановлении очередь обязана вызвать `bindReservations`.

Проверка `target/stage23b-facility-construction-final-validation.log`:
29 тестов без failures/errors/skips, строгий свежий Javadoc, BUILD SUCCESS, 34.468 с.
Дополнительные storage/logistics/production bridge регрессии: 14 тестов, BUILD SUCCESS,
`target/stage23b-facility-construction-storage-regression.log`.

Очередь включена в generated runtime capture/restore и проверку личного владельца активного заказа.
Native checkpoint сохраняет резерв и частичную работу. Завершение обновляет live industrial registry,
сохраняя прежний storage, состояние других установок и конструктивное evidence. Новая установка
отключена и не получает энергию, теплоотвод, работников или обслуживание автоматически.
Checkpoint с завершённым заказом без соответствующей установки отклоняется.

Игроковые START/CANCEL_FACILITY_CONSTRUCTION проходят тот же preview/submit и проверку
собственного физически пристыкованного корабля, что и изготовление. Production UI показывает
авторский состав, фактический запас, резерв, частичный прогресс и условия отмены. Прогресс идёт
на world ticks, а инженерная работа одной установленной линии расходуется совместно с
manufacturing: строительство получает только неистраченный остаток. Завершение записывается в журнал.

Ввод построенной установки подключён как `ALLOCATE_FACILITY_RESOURCES`: тот же собственник,
местный корабль и физическая стыковка. `Stage18FacilityResourceAllocator` перераспределяет только
уже существующие четыре распределения станции (энергия, теплоотвод, персонал, обслуживание),
не меняя массы, повреждений или содержимого. Прежние установки теряют отданную мощность;
недостаток любого ресурса отклоняет всю операцию. Все facility и yard capabilities пересчитываются.
Распределение сохраняется существующими installation snapshots, отражается в UI и журнале.
`target/stage23b-facility-allocation-validation.log`: 18 тестов без failures/errors/skips,
строгий Javadoc, BUILD SUCCESS, 1:06. Проверены сохранение всех четырёх сумм, недостаток,
повторное принятие строительства после включения, native round trip, ремонт и переоснащение.

Окончательная проверка `target/stage23b-construction-command-final-validation.log`:
13 тестов без failures/errors/skips, строгий свежий Javadoc, BUILD SUCCESS, 42.906 с.
После явного berth fixture проверены настоящие `DOCK`, preview/submit строительного заказа,
неизменность состояния при preview, одноразовый token, запись журнала и отмена до начала
работы с возвратом точного сырья. Исправлена проверка будущего industrial watermark, включая
завершённые managed orders и checkpoint без текущего игрока. Прежние 10 player persistence
сценариев прошли. Повторная привязка fuel authority была отклонена существующим guard;
лишняя привязка удалена: изменения facility allocations не меняют её endpoint storage/ownership.

Физический груз готовых изделий реализован в owning freight schema 7. `ProductCargoLotState`
содержит общий уникальный cargo ID, FleetId, product ID, целое количество, исходную станцию
и время погрузки. Лоты точно соответствуют реальным product counts трюма; масса готовых
изделий учитывается совместно с сырьём и индивидуальным оборудованием. Stage18 finite logistics
выполняет погрузку/выгрузку, частичная выгрузка сохраняет исходное evidence, уничтожение
корабля удаляет оставшиеся изделия без доставки. Старые schemas 1–6 читают свой формат
и не получают изделия; внешний native envelope пока остаётся v10. Все fingerprint/geometry
копирования сохраняют новые лоты. Native campaign требует личного владельца и отвергает
погрузку из будущего времени; точный полный save/load с fitted mass и выгрузкой проверен.

Добавлен seam резерва product counts для будущей сохраняемой обработки: резерв остаётся
в сыром snapshot источника и занимает то же место, но не доступен другим переносам/расходам.
Он пока не вызывается production commands и должен быть связан с отдельным checkpoint очереди.
Исправлен overflow product count: он отклоняется до изменения любого из двух складов.

`target/stage23b-finished-product-cargo-tests.log`: 14 тестов, BUILD SUCCESS.
`target/stage23b-finished-product-cargo-validation.log`: 17 тестов прошли, Javadoc обнаружил
18 недостающих @param в новом API; комментарии дополнены, ограничения Javadoc сохранены.
`target/stage23b-finished-product-cargo-final-validation.log`: 24 теста и свежий строгий Javadoc,
BUILD SUCCESS, 55.663 с. Проверены genuine v3/v4/v6 layouts, сохранение готового груза,
raw/extraction и used-equipment регрессии, storage reserve и изготовление/переоснащение.
Окончательная проверка future-loading guard: `target/stage23b-finished-product-cargo-watermark-validation.log`,
6 тестов и свежий строгий Javadoc, BUILD SUCCESS, 44.973 с.

Срез многотактовой обработки готовых изделий подключён к native v11, игроковым командам,
журналу и production UI. Товар остаётся у источника в настоящем резерве до завершения;
общий конечный endpoint handling budget не дублируется. Работа и резерв продолжаются после
сохранения, отмена освобождает только товар и не возвращает обработку. Физический перенос
публикуется одноразовым разрешением, создающим исходные provenance и массу без нового бюджета.
Переход player/world сохраняет эту очередь. Исторический native v10 получает пустую очередь.
`target/stage23b-product-commands-validation.log`: 9 тестов, строгий Javadoc, BUILD SUCCESS,
52.451 с. Реальные LOAD/UNLOAD/CANCEL проходят все конечные такты без подмены прогресса;
проверены codec framing и соседняя обработка individual equipment. Начальные ownership,
docking и поставка товара остаются явными fixtures; полный обычный путь не доказан.
`target/stage23b-product-native11-validation.log`: 14 тестов, BUILD SUCCESS, 45.948 с.
Проверены настоящий native-v10 layout, миграция в пустую очередь v11 без выдачи ресурсов,
точное совместное сохранение, обычные команды обработки и production UI regression.
Все остальные пункты остатка сохранены.

`target/stage23b-construction-final-validation.log`: 21 тест без failures/errors/skips,
строгий свежий Javadoc, BUILD SUCCESS, 47.022 с. Проверены исходная/загруженная частичная работа,
совместное изготовление/строительство без двойного бюджета, сохранение завершённой установки,
повторное принятие результата, ограничения владельца и projection. Владение и поставка сырья
в этих проверках явно остаются fixtures — обычное приобретение станции ими не доказано.

### Следующая интеграция — ещё не выполнена

Платная покупка существующей гражданской станции теперь подключена к authority и production UI.
`GeneratedCampaignStationSalePolicy` v1 задаёт явную цену: летучий склад 50 тыс. кр., промышленная
станция 70 тыс. кр., переработка 180 тыс. кр., высокие технологии 350 тыс. кр., торговля 150 тыс. кр.,
пограничная станция 200 тыс. кр. Военные/добывающие типы не предлагаются. Цена не читается из
скрытых запасов покупателя; предложение показывается только для собственного наблюдения места.
Реальная стыковка и совпадение настоящего владельца с продавцом перепроверяются перед оплатой.
Personal ownership добавляется существующим OwnedStationRef, деньги поступают в казну продавца;
станция, её кошелёк, склад, установки/верфь, мощность, повреждения и правовая регистрация сохраняются.
Новые рынки привязываются к промышленному владельцу; фракция контроля территории может отличаться.
Старые файлы не меняют юридический статус и не получают владельца, деньги или предложение при restore.

`target/stage23b-station-acquisition-validation.log`: 9 тестов, строгий Javadoc, BUILD SUCCESS, 2:04.
Проверены реальная оплата/владение, exact native round trip и физический stock conservation,
начальное отклонение недоступной станции, покупки кораблей и многотактовый finished-product handling.
Геометрия положительного purchase-сценария явно задана: новая кампания имеет расстояние около
9.21 млн м до промышленной станции. Полный обычный путь прибытия ещё не доказан и не считается закрытым.
Владение, деньги, материалы и строительная работа в purchase-сценарии не выдаются fixtures.
Окончательная проверка `target/stage23b-station-acquisition-final-validation.log`: 11 тестов,
BUILD SUCCESS, 2:05. Проверены foreign/stale confirmation, неверный продавец, нулевая цена,
недостаток денег без перевода любой стороне, обычный initial profile, physical cargo и UI.

- Подключить физическую постройку верфи к yard registry и обеспечить её фактические опоры.
- Обеспечить обычное создание физической станции и её registration/materialization; старый
  Stage-16 integer-inventory construction нельзя напрямую объявить килограммовым Stage-18 объектом.
- Доказать путь без подмены ownership, сырья, прогресса или установленной верфи fixtures.

### Физический фундамент строительства верфей, 2026-10-05

`stage23-yard-construction-v1.json` задаёт отдельную физическую массу трёх существующих
проектов верфей: 100, 350 и 500 млн кг. Материальные доли, работа на килограмм и требования
опор берутся из общих профилей Stage-18H; integer inventory старого планировщика не
объявляется килограммами. Каталог ограничен и имеет собственный fingerprint.

`Stage23YardConstructionWorkQueue` резервирует точный bill, удерживает физическую
вместимость склада, расходует переданный общий WorkBudget и сохраняет завершённые
свидетельства. Отмена разрешена до начала работы. Schema-1 codec проверяет границы,
идентичность спецификации, время и количество строк. Установка в live registry допускает
только точный завершённый заказ этой очереди: новая верфь отключена, рабочие ресурсы нулевые.
Повторная установка не сбрасывает состояние; конфликтующий ID отклоняется.

`target/stage23b-yard-foundation-validation.log`: 11 тестов и строгий Javadoc, BUILD SUCCESS,
59.911 s. `target/stage23b-yard-storage-campaign-regression.log`: 4 теста совместимости
строительства установок, native готовых товаров и перевозки, BUILD SUCCESS, 52.774 s.
`target/stage23b-yard-adoption-validation.log`: 8 тестов и строгий Javadoc, BUILD SUCCESS,
26.610 s. Проверены отключённая установка завершённой верфи, сохранение промышленного
реестра, повторное применение, незавершённая работа и конфликт определения под тем же ID.
Очередь подключена к native v12, командам START_YARD_CONSTRUCTION / CANCEL_YARD_CONSTRUCTION,
тактовой работе, журналу и production UI. Общая линия расходует только один WorkBudget
после изготовления и строительства установок. Joint admission проверяет owner, фактическую
станцию/место, watermark, совокупный резерв установок и изготовления, отсутствие преждевременной
установки и точное сохранение завершённой верфи. Genuine native v11 мигрирует с пустой очередью.

`target/stage23b-yard-native12-final-validation.log`: 11 тестов и строгий Javadoc,
BUILD SUCCESS, 1:55. Проверены exact native round trip завершённой отключённой верфи,
пустая миграция genuine v11, отсутствие структуры при заявленном завершении, преждевременная
установка, future watermark и чистое отклонение команды без всего bill. Общие native codec
и физическая очередь прошли свои проверки. Это не положительный ordinary construction journey.
`target/stage23b-yard-native12-ui-regression.log`: 4 теста, BUILD SUCCESS, 1:05.
Проверены чистая production projection/save-load, многотактовая перевозка товаров и покупка
существующей станции на текущей native-оболочке. Полный suite не запускался.
Большие bills не помещаются в имеющиеся склады целиком. Следующий переход ниже добавляет
поэтапную доставку; отдельные структурные fixtures не подтверждают обычное получение верфи.

### Поэтапная поставка на стройплощадку верфи, 2026-10-05

Новые START_YARD_CONSTRUCTION создают пустой заказ, не выдавая материалов или структуры.
Общие реальные такты перевозят доступную часть каждого authored bill со склада собственной
станции в ограниченную custody этого заказа. Эта площадка принимает только требуемые
килограммы и не предоставляет общей складской вместимости. Весь bill можно доставлять
через склад меньшего размера; engineering work начинается только после полной поставки.

На одном endpoint доставка на площадку, обработка снятых модулей и готовых продуктов
используют один TransferBudget. Engineering work остаётся общим с изготовлением и
строительством установок. Повторная обработка того же такта не перемещает материал.
Фактическая поставка создаёт запись журнала; текущая обработка учитывается при новых
торговых командах. Отмена до работы возвращает только доставленную массу и отклоняется
атомарно, если исходный склад заполнен: резерв не исчезает.

Yard codec schema 2 сохраняет site/warehouse mode и bounded exact delivered material.
Schema 1 по-прежнему читается и кодируется для прежних warehouse-only заказов: полный
резерв остаётся на старом складе без превращения в дополнительный материал площадки.
Outer native остаётся v12. UI показывает доставленную долю и объясняет автоматическую
поставку со склада, отсутствие работы без всего состава и требования к отмене.
Обычный экономический путь приобретения всего bill и ввод работающей верфи ещё не доказаны.

Исправлена остановка полной поставки на double-residue: общая складская служба удаляет
остатки меньше 1e-9 кг, поэтому yard delivery завершает bill с той же точностью и сохраняет
канонические quantities спецификации. Fingerprint и authored masses не меняются.

`target/stage23b-yard-staged-validation.log`: 8 тестов и строгий Javadoc, BUILD SUCCESS,
1:31. Проверены полный 100-миллионный bill через склад с 1 млн кг на класс, продолжение
посреди поставки, завершение после конечной работы, реальный расход общего handling,
повторный такт, сохранение массы и отказ отмены при заполненном складе. Campaign command
создаёт пустой заказ, последующий действительный такт перевозит конечную массу и native
save/load сохраняет частичную поставку. Schema-2 codec отклоняет excess/unknown material,
future delivery, второй резерв и работу до всего bill. Перевозка готовых товаров остаётся рабочей.
Исходный полный запас в domain-сценарии явно является fixture; обычное его приобретение
и достаточные рабочие ресурсы этим прогоном не объявляются доказанными.

### Распределение существующих ресурсов верфей, 2026-10-05

ALLOCATE_YARD_RESOURCES подключён к собственному физическому berth gate, preview/submit,
production UI и журналу. Stage18YardResourceAllocator переносит четыре существующих
распределения между верфями одной станции: энергию, инженерную работу, целочисленный
персонал и автоматизацию. Все суммы сохраняются, состояние/повреждения доноров не сбрасываются,
нехватка отклоняет неизменяемую транзакцию целиком. Ресурсы facility не переименовываются
в yard staff/work/automation. Отдельно проверяется работающая проекция target с фактическими
опорными установками; отсутствие опор не публикует новое распределение.

`target/stage23b-yard-resources-validation.log`: 3 теста и строгий Javadoc, BUILD SUCCESS, 1:18.
Проверены перераспределение без выдачи, идемпотентность, malformed/absent target и нехватка,
а также campaign construction/native regression. Этот лог не доказывает обычное приобретение
материалов или весь путь получения работающей верфи.

`target/stage23b-yard-resources-registry-validation.log`: 7 тестов, BUILD SUCCESS, 37.927 s.
Завершённая новая верфь становится ACTIVE за счёт прежних распределений верфей фактической
станции. Подтверждены сохранение всех четырёх сумм, working registry save/load и отсутствие
сброса рабочих ресурсов при повторной установке construction evidence. Completed-work fixture
остаётся явным; ordinary acquisition/material delivery этим тестом не подменяется.

### Личные рынки по собственному знанию, 2026-10-05

ProductionUiProjector больше не перечисляет неизвестные endpoint как личные станции:
требуется собственная INFRASTRUCTURE запись с сохранённым положением. Расстояние берётся
до этого положения, а не endpoint truth. Товары и котировки доступны при actual docking;
чужой stock/free capacity/market wallet не раскрываются. Текущий кошелёк и собственный
трюм сохраняют обычное отображение. Проекция не изменяет authority и не копирует NPC knowledge.

`target/stage23b-personal-market-knowledge-validation.log`: 3 теста и строгий Javadoc,
BUILD SUCCESS, 1:55. New-game distant/unknown station не появляется в personal logistics
при просмотре другой фракции; station acquisition и composed UI save/load проходят.
Обычное получение observation/broadcast до сближения остаётся открытым; этот UI-срез
не даёт игроку новое знание, штатный автопилот или полную фильтрацию всех карт.

### Общий электрический бюджет перед сообщением станции, 2026-10-05

Проверен существующий ShipSensorRuntime: passive radio использует реальные emissions,
апертуру, inverse-square propagation, noise и SNR. Он не даёт фиксированного detection radius,
а NPC bootstrap knowledge не представляет полученное игроком сообщение.

Manufacturing IntervalBudget теперь позволяет reserveElectricalEnergyJ из того же остатка
энергии фактической установки. Это основание оплаты инфраструктурной электрической службы,
а не новая энергия: невалидный запрос отклоняется, нехватка возвращает false без эффектов,
work/maintenance не меняются, следующий потребитель получает только остаток joules.
`target/stage23b-shared-electrical-validation.log`: 7 тестов и строгий Javadoc,
BUILD SUCCESS, 1:13. Сообщение станции, параметры фактического передатчика, приём существующим
установленным сенсором и обычное появление личного station knowledge ещё не подключены.
Этот фундамент не считается реализованным обнаружением или обычным прибытием.

### Оплаченный пакет станции и физический радиоприём, 2026-10-05

Stage23StationBroadcastRuntime создаёт opaque Transmission только после оплаты всей
электрической энергии из того же facility interval и проверки доступного отвода waste heat.
Radiated power не может превышать electrical input. Payload закрыт до успешного приёма:
используются реальная passive-radio definition, sensor availability, относительная SI
геометрия, EW emitters в receiver frame и штатный ShipSensorRuntime SNR. Decoder требует
classification SNR, а не только detection. Bearing-only measurement не объявляется range:
положение берётся из декодированных объявленных координат actual source packet.

Полученная StaticObservation имеет PERSISTENT_INFRASTRUCTURE_BROADCAST provenance и
не раскрывает запас ресурса. Нехватка энергии/охлаждения, далёкий сигнал, отключённый
сенсор, шумовой jammer и приём вне оплаченного интервала не создают knowledge.
`target/stage23b-station-radio-validation.log`: 9 тестов и строгий Javadoc, BUILD SUCCESS,
1:26. Transmitter/sensor inputs в domain-тесте являются явными fixtures; выбор настоящего
установленного передатчика, привязка actual source budget/cooling и fitted receiver,
такт кампании и фактическая запись личного знания ещё не подключены. Обычное обнаружение
этим доменным прогоном не считается закрытым.

### Архивы NPC и сведения продавца при реальной покупке, 2026-10-05

Production factory новой кампании теперь вызывает существующий Stage20GeneratedDiscoveryBootstrapPlan:
фракция получает свои опорные и собственные производственные станции из явной начальной authority.
Профиль stage23b.new-campaign-owned-station-archives.v1 задаёт время 0 и одни сутки актуальности
производственных сведений; resourceGrants пуст. Ранее factory создавала только пустого наблюдателя,
поэтому наличие доменного bootstrap plan не означало наличия архива NPC в обычной игре.
Restore не вызывает factory и не дополняет старые знания.

После conserved покупки существующего стартового корабля его продавец передаёт архивированные
координаты собственных гражданских станций. Отсутствующая запись, отсутствие положения или
несовпадение классификации не заполняются из мира. Сохраняются точные координаты источника,
FACTION_INTELLIGENCE provenance и отдельная квитанция SELLER_STATION_BRIEFING; ресурсы, запасы,
оборудование и владение не выдаются. Система каждого полученного объекта добавляется к личным
известным системам без соседних маршрутов. Preview работает на изолированном checkpoint.

UI отличает известную станцию от посещённой и показывает источник на русском. Архивное знание
не разрешает удалённую покупку или получение котировок; настоящий docking добавляет отдельное
свидетельство визита. Native v12 сохраняет это существующими knowledge/player/journal payloads.
target/stage23b-seller-briefing-final-validation.log: 9 тестов и строгий Javadoc,
BUILD SUCCESS, 1:58. Геометрия обычного путешествия, физический радиоприём в кампании и
полное получение работающей верфи этим срезом не доказаны.
target/stage23b-seller-briefing-regression.log: 12 тестов покупки, production UI и lifecycle,
BUILD SUCCESS, 1:51; сохранение clock fraction и воспроизводимость cadence не изменились.
target/stage23b-seller-briefing-visit-validation.log: 2 теста, BUILD SUCCESS, 1:06;
дополнительно подтверждено сохранение PHYSICAL_VISIT_OR_SURVEY после настоящего docking
у уже известной станции. Геометрия прибытия в этом тесте остаётся явно заданной fixture.

### Оплаченный ремонт чужой гражданской верфи, 2026-10-06

START_REPAIR допускает фактический собственный корабль у совместимой установленной работающей
гражданской верфи при реальной стыковке и совпадении юридического владельца станции с оператором.
Чужое переоснащение и доступ к его складу снятых модулей этим изменением не разрешаются.
Опубликованный первоначальный тариф: 50 кр. за килограмм реального ремонтного материала и
1 кр. за секунду требуемой инженерной работы; округление вверх выполняется один раз до милли-кредита.
Невалидный состав, сумма вне валютных границ и недостаточный личный баланс отклоняются до резерва.

Деньги вычитаются из личного кошелька и удерживаются конкретным RepairOrder вместе с исходным
повреждением, материалом, верфью и временем. Казна оператора ещё не получает эти деньги.
Отмена до фактической работы возвращает материал и весь денежный резерв; после начала работы
платный заказ нужно завершить. Собственная станция сохраняет прежний режим без денежной оплаты.
Только после реального завершения ремонта сохранённый резерв переводится штатным
WorldSimulation.transferToFactionTreasury в существующую казну оператора. Журнал содержит
резерв/возврат через фактическую дельту кошелька и однократное REPAIR_SERVICE_SETTLED.

Schema 2 ремонтной очереди сохраняет оператора и сумму. Schema 1 по-прежнему означает работу
собственника без договора; старые заказы не получают денег. Outer native v13 отделяет новое
финансовое обязательство: v1–v12 сохраняют прежние данные, а файл старой версии не может
заявить платный резерв. Composed validator проверяет личный корабль, фактический berth,
гражданскую специализацию, юридического владельца, казну и точную установленную верфь.
UI показывает стоимость, удержанную сумму и условия отмены, не раскрывая чужой свободный запас.

target/stage23b-paid-repair-final-validation.log: 15 тестов и строгий Javadoc,
BUILD SUCCESS, 2:04. Транзакционный campaign-тест использует явно заданные повреждение,
геометрию, установку верфи и поставку материала; обычное путешествие и получение работающей
станции этим тестом не доказаны. Переоснащение чужой верфи и оставшиеся игровые пути ещё нужны.
target/stage23b-native13-queue-regression.log: 14 тестов, BUILD SUCCESS, 1:28.
Подтверждены настоящее чтение v12 owner repair без начисления денег, запрет платного резерва
под старым заголовком и регрессия переоснащения, строительства установок/верфей и готового груза.

### Формат будущего оплаченного переоснащения, 2026-10-06

ShipyardRefitServicePayment удерживает оператора и положительную сумму по инженерной работе
и физической массе реальных новых продуктов. Отдельный bill новых продуктов может быть пустым
для снятия или установки собственного использованного оборудования: тогда оплачивается работа,
а использованный модуль не продаётся повторно как новый. Неизвестный продукт, невалидный count,
невалидная работа или выход суммы за валютные границы отклоняются; округление выполняется один раз.

Refit Order сохраняет optional servicePayment при withWork. Standalone schema 3 кодирует его
вместе с обеими компоновками, повреждением, прогрессом и входящим оборудованием. Schema 1/2
не получают вымышленных договоров; старые constructors и owner-only encode сохраняют прежний
формат. Изменение суммы, усечённый/trailing payload и попытка прочитать платное содержимое
под старым schema-2 заголовком отвергаются.

Native v13 и нынешний owner-only work executor явно отклоняют такое финансовое обязательство:
расчёт и чужая авторизация ещё не подключены, поэтому завершение не может молча потерять резерв.
Нужны дальнейшая интеграция оплаты/возврата и права на снятые индивидуальные модули у чужой
станции. Эти модули должны оставаться собственностью игрока при сохранении, обработке, перевозке
и последующей установке; владение местом хранения не должно подменять владение оборудованием.
target/stage23b-paid-refit-format-validation.log: 15 тестов и строгий Javadoc, BUILD SUCCESS,
1:45. Проверены новый формат и прежний действующий путь переоснащения; платная игровая услуга
и её обычное получение этим прогоном не доказаны. Outer native остаётся v13.

### Владелец оборудования отдельно от склада, 2026-10-06

StoredModule получил optional ownerActorId. Он сохраняется при relocate между физическими
хранилищами вместе с ID, sourceAssetId, removal tick, повреждением и возрастом; смена склада
не передаёт право собственности. Deposit может принять уже авторизованного владельца
реального completion; сам kernel не выводит владельца из исходного корабля или станции.
Это необходимо для сохранения прав на оборудование, снятое у чужого оплаченного оператора.

Standalone custody schema 2 хранит optional actor по строке. Schema 1 и старые constructors
оставляют ownerActorId отсутствующим без выдачи прав при загрузке; legacy-only encode сохраняет
прежние bytes/schema. Проверены смешанные данные, перемещение, точная сохранность состояния,
невалидный owner, неизвестная версия, усечённый и trailing payload. Native v13 пока явно
отклоняет новые owner tags, включая вложенные used-refit inputs: применение права в кампании
требует следующей совместной авторизации с финансовым заказом и услугой чужого склада.

target/stage23b-module-owner-validation.log: 18 тестов и строгий Javadoc, BUILD SUCCESS,
1:21. Старые физические очереди и storage/campaign persistence проходят. Сохранённое поле
не означает реализованной авторизации, оплаченного игрового переоснащения или полного 23B.
Далее нужны производство owner tag из фактического игрокового completion, проверка права
для использованных inputs и транспортных команд, сохранение/расчёт платы и production UI.

### Физический executor оплаченного переоснащения, 2026-10-06

Refit queue принимает действительный денежный резерв и сохраняет его при частичной работе.
Context добавляет авторизованного владельца оборудования, чистую проверку денежных условий
и финальный transfer callback. Прежний трёхпараметровый context не имеет финансового права:
платная работа остаётся на паузе без потери резерва. Явно чужой owner использованного input
также не проходит исполнение. Платная отмена после выполненной работы отвергается.

Перед финальным переводом полностью стадируются fitting, физическая вместимость корабля,
removed custody и склад. Storage preflight учитывает manufacturing, repair, refit, construction,
yard site, module custody и finished-product handling layers. Отказ расчёта не расходует последнюю
работу, не меняет корабль/склад и не теряет резерв. Callback должен выполнять только фактический
денежный перевод; после него публикуется заранее проверенный физический переход. Снятые модули
получают переданный авторизованным context owner, а не владельца принимающей станции.

target/stage23b-paid-refit-executor-validation.log: 19 тестов и строгий Javadoc,
BUILD SUCCESS, 1:01. Доменный оплаченный заказ проверен с явными оборудованием/верфью/денежными
fixtures: отказ финального расчёта, частичная работа, отказ отмены, save/resume, conserved wallet
transfer, сохранение повреждения/возраста/owner снятого модуля и отсутствие повторной выплаты.
Прежний owner campaign path проходит. Native v13 ещё не допускает платное переоснащение и явные
owner tags; далее требуется совместная интеграция реального campaign caller, чужой авторизации,
нативного checkpoint, транспортных прав и production UI. Этот executor не закрывает весь 23B.

### Native v14: услуги чужой верфи и права на личные модули, 2026-10-06

Campaign START_REFIT / START_REFIT_USED резервируют деньги при фактической собственной
стыковке у совместимой работающей гражданской верфи реального юридического оператора.
Цена новых изделий вычисляется из настоящего product bill; использованный личный модуль
не продаётся повторно как новый. UI раскрывает тариф, полную pristine quote и удержанную сумму.
Отмена до работы возвращает весь резерв. Финальное завершение переводит его существующей
казне через штатную WorldSimulation authority, публикует fitting/cargo/custody и записывает
однократное REFIT_SERVICE_SETTLED. Repair и refit сохраняют общий actual yard interval budget.

Снятая игроковая вещь получает actor.player из авторизованного completion. Физическое хранение
у чужой станции не передаёт ей собственность. Personal UI, used inputs и transport commands
проверяют владельца вещи; legacy право собственного склада/трюма можно явно зафиксировать
только в настоящем авторизованном handling. Чужой actor не подменяется личным владельцем.
Private foreign handling требует actual civilian operator, legal registration, stationary berth,
свободную собственную машину, настоящий endpoint mass budget и физическую вместимость.

Native schema/file/runtime v14 сохраняет paid-refit schema 3 и explicit-custody schema 2.
V1–v13 не могут заявить эти новые обязательства/права; их прежние данные мигрируют без выдачи.
Формат root payloads сохраняется, PlayerWorldTransition переносит те же реальные владельцы.
target/stage23b-native14-first-validation.log: 16 тестов, BUILD SUCCESS, 1:19.
target/stage23b-native14-paid-refit-validation.log: 18 тестов и строгий Javadoc,
BUILD SUCCESS, 1:04. Проверены pure preview, reserve/refund, частичная работа и resume,
платёж/права после завершения, запрет этих данных под v13, личный used-input request и отмена,
реальная многотактовая перевозка снятого модуля с чужой станции в трюм и composed save/load.
Геометрия, оборудование, деньги и предшествующая инженерная работа в campaign-сценарии
являются явно указанными fixtures. Полный объём finite refit work проверен отдельно domain
executor. Этот прогон не доказывает обычную закупку, путешествие, получение работающей верфи
или завершение всего 23B. Оставшиеся сценарии перечислены в общем списке.

Статус: **23B ACTIVE/PARTIAL**. Строительство установок подключено к кампании, но оно ещё
не закрывает обычное получение станции/верфи или всей стадии. Накопленный связанный срез
зафиксирован в `3bf2c46f` (2026-10-06); полная проверка выявила оставшиеся регрессии
исторических test fixtures, которые исправляются отдельным сегментом.

### Продолжение 2026-10-06: совместимость, физический манифест и контактные уведомления

Начальный полный прогон: 2547 тестов, 5 failures, 17 errors, 1 skip,
`target/stage23b-segment-verify.log`. Исторические fixtures теперь явно выбирают BASELINE:
новый NPC-манифест не маскируется старым заголовком. Гражданские архивы учитываются при
выборе действительно неизвестной цели разведки; отдельный тестовый NPC состав создаётся
изолированным service, без замены живых диспетчеров. Старое station custody задаётся без
появившихся позже actor tags. Проверка применения политики сохраняет всю игровую authority
и ожидает одну фактическую запись журнала. Исторические guards writer не ослаблены.

`GeneratedCampaignInitialMiningReserveTest` проверяет одну изменённую существующую NPC-машину,
действующее установленное оборудование, отсутствие выдачи личного актива, сохранность
остальных машин/заказов/складов/казны, exact native round-trip, отказ манифеста под v15
и восстановление исторического манифеста без превращения корабля в шахтёра.
Согласованы текущие `campaign=16` и release-notes identity. У архивных overlay исправлено
название системы вместо `StarSystemId[...]`; реальные контакты проверяются по сохранённому
составу и фракции наблюдателя. Этот сегмент — `1de86846`.

Личные уведомления о предлагаемых поставках создаются после настоящего подтверждённого
DOCK у диспетчера и при выдаче новой заявки во время фактического контакта. Обе версии
advanceFrame подключены к одному правилу. Событие сохраняет actual tick, контракт, получателя
и физические килограммы, с нулевым изменением личного кошелька. Удалённые сведения NPC,
preview, последующие такты и restore не создают повторов. Повторный визит является новым
контактом; уведомление не выплачивает эскроу. Используются существующие journal kind/action
и writer v16, без новых полей или grant при миграции.

Финальная целевая проверка разведки, начального манифеста и NPC-уведомлений:
5 тестов, 0 failures/errors/skips, BUILD SUCCESS,
`target/stage23b-continuation-final-targeted.log` (1:44).
Остальные 62 сценария расширенной группы прошли предшествующий целевой прогон.
Геометрия прибытия в contact tests остаётся явным fixture; полного обычного путешествия,
приобретения оборудования, добычи и выполнения заявки эта проверка не доказывает.
Статус остаётся **23B ACTIVE/PARTIAL**; общий окончательный gate фиксируется отдельно.

### Общая проверка продолжения 23B

Для функционального revision `7d967af61af8d93f50c9e6dd16c84b57ca5e62b0` выполнен Java-17
`mvn -B -ntp -Ptest-parallel clean verify`: **2548 тестов, 0 failures, 0 errors, 1 skip**.
Сборка дошла до упаковки и остановилась на 15 Javadoc warnings у четырёх новых перегрузок
композиции initial reserve policy. Лог: `target/stage23b-continuation-clean-verify.log` (20:00).

После этого изменены только Javadoc comments: добавлены описания всех параметров и результата.
Оставшаяся lifecycle-проверка выполнена с `-Dmaven.main.skip=true -Dmaven.test.skip=true`,
чтобы сохранить ровно проверенный байткод и его полный JaCoCo execution data. Строгий Javadoc,
desktop/shaded packaging и обе coverage thresholds прошли; **BUILD SUCCESS**, 23.031 с,
`target/stage23b-continuation-post-test-verify.log`. Полный тестовый набор повторно не запускался,
так как исполняемый код после него не менялся. Старые логи перед clean сохранены в системном
временном каталоге `StarEmpires-stage23b-20261006-continuation`.

Эта проверка закрывает регрессии текущего связанного среза. Она не закрывает весь 23B,
не является exact-head CI, окончательным графическим прогоном или ручной приёмкой.
Следующий игровой пункт — обычное приобретение шахтёра, добыча и выполнение сырьевой заявки;
остальные незавершённые направления остаются в `stage23b_remaining_work.md`.

### Обычный старт: непосредственный доступ к доку

Устранена циклическая зависимость: личная логистика раньше скрывала неизвестный док,
хотя только стыковка могла дать архивную запись. `canDockAtPersonalStation` проверяет
действующий собственный корабль, отсутствие прыжка, настоящую станцию и прежний berth
не далее 1 км / не быстрее 1 м/с. Подтверждённый DOCK использует то же правило.
Без архивных координат UI показывает только «Местный док» и возможность непосредственной
стыковки; навигационной координаты, названия, котировок и внутренних запасов не выдаёт.
Посещение и точное положение записывает только фактический DOCK.

`GeneratedCampaignOrdinaryDockContactTest` проходит от настоящего нового старта seed 1:
без изменения положения/скорости, выдачи груза или денег тест видит ближайший хаб,
выполняет DOCK, продолжает один штатный такт, покупает 1 кг настоящего запаса и проверяет
расход кошелька/склада, груз и exact save/load. Remote/rejected contacts и чистая проекция
не создают знания. Целевой gate: 11 тестов, 0 failures/errors/skips, строгий Javadoc,
desktop packaging, BUILD SUCCESS (2:43), `target/stage23b-ordinary-dock-validation.log`;
coverage в этом целевом прогоне отключено. Writer остаётся v16, status ACTIVE/PARTIAL.

### Личные карты и предложения местного продавца, 2026-10-06

Production-клиент привязан к человеку, включая экран до создания пилота и повторную загрузку.
Он больше не выбирает произвольную фракцию для получения её архива, истории, групп или балансов.
Личные карты показывают известные системы и связи между ними, собственные корабли и сохранённые
координаты личных статических наблюдений. Чужие запасы и установленные мощности не раскрываются;
значок/подпись станции не сообщают наличие чужой верфи. Наблюдение источника не раскрывает
добывающий аванпост, его склад или оборудование. Панели повторно ограничивают даже переданный
диагностический world snapshot. Проекции не создают наблюдений и воспроизводятся после native load.

Резервные корпуса предлагает только оператор настоящего местного дока продавца. UI и PURCHASE
используют одно правило, которое повторно проверяет стыковку, геометрию, оператора, локальность,
свободный пустой корпус и отсутствие прыжка. Предложение сообщает назначение оборудования,
но не становится обнаружением координат корабля.

Целевые проверки: `target/stage23b-personal-access-final-validation.log` — **28 тестов**, 0
failures/errors/skips, строгий Javadoc, desktop packaging, BUILD SUCCESS (4:41).
Последнее уточнение значка станции: `target/stage23b-personal-access-final-recheck.log` — **6 тестов**,
0 failures/errors/skips, строгий Javadoc, desktop packaging, BUILD SUCCESS (2:37).
Coverage в этих прогонах отключено; полного итогового gate этапа они не заменяют.

`Stage23BOrdinaryMiningJourney` продолжает инженерный путь настоящими командами и физическими
тактами, с промежуточным native checkpoint каждые 20 тыс. тактов. Он не выдаёт деньги, оборудование,
руду или новое положение корабля. Выбор маршрута и координат в этом QA-инструменте пока использует
физический реестр: это не доказательство полного пользовательского пути обнаружения и навигации.
Длинный прогон выявил остановку всей симуляции из-за запроса следующего прыжка NPC во время
FTL cooldown. В обычной политике добавлено ожидание cooldown, восстановления накопителя и
теплоотвода; восстановление принадлежит прежним физическим тактам, а не политике автопилота.

Прежний ручной маршрут на 3 км/с прошёл три прыжка до системы шахтёра, но исчерпал
реактивную массу при торможении перед `hub.14` после более 2 млн обычных тактов.
Покупки шахтёра, добычи и продажи этот прогон не достиг; checkpoint и логи сохранены.
QA-инструмент теперь использует штатный экономичный FTL approach и заранее проверяет
полный разгон/торможение местного перелёта. Для отдельного нового прогона можно задать
`stage23b.journey.checkpoint` и `stage23b.journey.cruiseMps`; существующий неудачный
checkpoint не заменяется выдачей топлива или исправлением координат.

Отдельная проверка `target/stage23b-freight-recovery-save-resume.log`: 1 тест, BUILD SUCCESS,
52.026 с. Ожидание cooldown не изменяет инженерное состояние или геометрию, а последующие
обычные такты и save/resume приводят к одному и тому же настоящему следующему прыжку.
Графический прогон обнаружил зависимость собственных дипломатических форм от списка
чужих закрытых экономических сводок; контрагенты переведены на публичные идентичности
каталога и мира, без чтения/публикации их балансов.

Итоговая проверка публичных контрагентов, личных карт и ожидания FTL:
`target/stage23b-personal-counterparty-validation.log` — **9 тестов**, 0 failures/errors/skips,
строгий Javadoc, desktop packaging, BUILD SUCCESS (3:09). Coverage отключено.
Графический прогон `target/stage23b-personal-access-graphics-utf8.log` на NVIDIA RTX 4070:
**Graphical probe passed**. Проверены создание пилота, физическая тяга, клавиатура/мышь,
сохранение/загрузка, резервные корпуса, реальные личные приказы, регистрация активов,
собственные политики/дипломатия, посещённые маршруты, продажа с реальным денежным изменением,
снабжение из собственного груза, журнал, хранение снятых модулей и отмена ремонта.
Геометрия, владение станцией и оборудование в соответствующих сервисных частях остаются
явными fixtures; полный обычный путь и ручную приёмку этот графический результат не заменяет.
QA-скрипты скомпилированы с явной UTF-8; source launcher на Windows также требует
`-Dfile.encoding=UTF-8`, иначе проверки русских текстов используют неверную кодировку.

### Оплаченные карты по фактической добыче NPC, 2026-10-07

Обычная автономия использует общий `extractForFreighter`: существующий назначенный
перевозчик, его настоящий местный источник/товар и работа установленного аванпоста.
Положительный физический результат создаёт в архиве оператора отчёт о координате участка
и наличии образца. Нулевая/отклонённая добыча отчёта не создаёт, повторная добыча не
переиздаёт его, чужие личные архивы не получают этих данных. Это сообщение оператора
производственного участка, а не утверждение, что грузовик лично обследовал месторождение.
Точный запас и содержание не передаются.

У действующего диспетчера добавлен `BUY_RESOURCE_MAP`. Требуются его настоящее место
контакта, доступность, существующая запись его фракции с координатой и ещё неизвестный
игроку участок. За одну карту из личного кошелька в существующую казну оператора переходят
100 кредитов; недостаток средств, неизвестный участок, отсутствие контакта и stale/foreign
confirmation не изменяют знания или финансы. Копируется существующее наблюдение с его
временем и сроком актуальности. Отчёт может относиться к удалённой системе; в PlayerState
добавляется только указанная система. Native writer остаётся v16, новых полей или фактов
при миграции нет. Предпросмотр не раскрывает координату и не создаёт личного открытия.

Production UI показывает цену и условия передачи карты, использует обычные клавиатурные
preview/confirm и сохраняет оплату в личном журнале. Исправлен переход из строки личного
ресурсного открытия в соответствующий `resource:` marker: прежде открывалась система,
но выбранный `personal-resource:` идентификатор не находился на локальной карте.

Дополнительная оплаченная покупка существующего корабля теперь вызывает тот же ограниченный
station briefing, что начальная продажа. Передаются только новые, уже архивированные
координаты собственных гражданских станций продавца; ресурсы и чужие данные не выдаются.
Уже известные положения не освежаются и не порождают повторного briefing.

`target/stage23b-resource-map-final-validation.log`: **20 тестов**, 0 failures/errors/skips,
строгий Javadoc, desktop packaging, BUILD SUCCESS (3:57). Проверены обычные world ticks и
автономная добыча с настоящим уменьшением источника и грузом перевозчика, закрытость личного
архива, оплата/казна, отсутствие повторной покупки, original observation time, native round trip,
stale/foreign confirmation и недостаток денег после обычной капитализации собственной казны.
Геометрия прибытия к диспетчеру в этом контактном тесте остаётся явным fixture; сырьё,
оборудование, финансирование и наблюдения им не предоставляются.

`target/stage23b-resource-map-ui-validation.log`: **9 тестов**, 0 failures/errors/skips,
строгий Javadoc, desktop packaging, BUILD SUCCESS (1:47). Coverage в целевых прогонах отключено.
`target/stage23b-resource-map-graphics.log`: **Resource map graphical probe passed** на NVIDIA
RTX 4070. Настоящий клиент проходит клавиатурную проверку/оплату карты, точный F8/F9 и
переход к оплаченному ресурсу на карте. Для контакта загружен отмеченный выше геометрический
fixture; карта происходит из штатной добычи NPC, а деньги и сведения не выдаются.

Полный обычный путь получения шахтёра, добычи и продажи и количественные измерения ресурса
этим срезом не объявляются завершёнными. Остальные пункты 23B сохраняются в каноническом остатке.

Статус остаётся **23B ACTIVE/PARTIAL**. Обычная добыча/продажа и остальные пункты канонического
остатка не объявляются закрытыми по одному инженерному или графическому прогону.

Финальная проверка 2026-10-07: `target/stage23b-resource-map-clean-verify.log`,
`mvn -B -ntp -Ptest-parallel clean verify` под Java 17: **2554 теста, 0 failures,
0 errors, 1 штатный skip** (`Stage19JLongSoakTest`), BUILD SUCCESS за 24:29.
Строгий Javadoc, desktop packaging и все пороги JaCoCo пройдены.
Полный прогон выявил и позволил исправить запрет briefing при покупке второго корпуса:
активный корабль сохраняется, оба корпуса должны принадлежать игроку и быть в одной системе.
Проверено отсутствие освежения уже известных станционных наблюдений.
Проверка строк станции уточнена для фактического непосредственного контакта с ещё не
записанным доком: он может иметь строку доступа, но не сохранённое положение или map focus.
Отдельная перепроверка этих сценариев и ресурсных карт: **13 тестов**, BUILD SUCCESS за 2:55,
`target/stage23b-briefing-regression.log`. Старый журнал неудачного полного прогона и QA
сохранения оставлены для диагностики; финальный полный прогон выполнен после исправлений.

### Обычная физическая добыча, продажа и большие сохранения, 2026-10-07

`Stage23BOrdinaryMiningJourney` теперь продолжает точный native checkpoint: незавершённый
прыжок, уже приобретённый корпус, начатую добычу или обратный полёт. Лимит тактов сохраняет
кампанию и явно сообщает `JOURNEY PAUSED`, а не успех. Участки полёта проверяют настоящий
конечный delta-v с учётом текущего вектора скорости; торможение использует консервативную
возможность, полученную из реального инженерного расчёта. Каждый игровой такт проходит
через обычный `advanceFrame`; часы, позиции, скорости, оборудование и запасы не подставляются.

Seed 1 прошёл физические переходы 16 → 11 → 19 → 14, подход и настоящую стыковку с `hub.14`.
На такте **10605610** куплен существующий `fleet:26` с шахтёрской компоновкой за **25000 кредитов**.
Проверены перевод из личного кошелька в существующую казну продавца, прежнее число корпусов
и неизменность фактического приобретённого freight asset. После обычных UNDOCK/SWITCH
шахтёр физически достиг участка: production UI предложил доступную команду добычи.
START_MINING на такте **11017764**, STOP_MINING на **11017768**: конечный источник уменьшился,
в собственном трюме появились **1.1731790950991603 кг металлической руды**.

После реального обратного полёта и стыковки на **11425791** продан **1 кг** на такте
**11425792**, доход личного кошелька **18 кредитов**. Проверены соответствующее уменьшение
груза, увеличение настоящего склада, точное изменение денег и native round trip после
добычи и продажи. Итог: `ORDINARY MINING SALE PASS` в
`target/stage23b-ordinary-mining-sale-after-codec-fix.log`.
Сохранение: `target/stage23b-ordinary-mining-budgeted.s25.sale.s25`.
Старый неудачный checkpoint отдельного прежнего маршрута сохранён.

Это автоматизированный **обычный физический путь**, без установки оборудования или
геометрии fixtures. QA выбирает точки маршрута из runtime для управляющих команд.
Получение игроком этих навигационных сведений через исследование/контакты/оплаченные карты
и ручная приёмка этим проходом не доказываются. Количественные измерения, работающие верфи,
прочие услуги, носители, операции и полная сырьевая заявка остаются в каноническом остатке 23B.

Длинный проход обнаружил реальный сбой больших сохранений: checkpoint **149002601 байт**
после добычи вызвал `OutOfMemoryError` при round trip на heap 3 ГБ.
Stage-21A–I и native campaign теперь читают вложенные runtime payload через ограниченные
потоки без полной копии на каждый уровень. Проверки длины, усечения, trailing bytes,
версий и межсистемных ссылок сохранены; закрытие дочернего потока не закрывает родителя.
Запись и native v16 не менялись, миграции не добавляют активов, денег или знаний.
Тот же checkpoint и оба round trip после исправления прошли на **прежних 3 ГБ**.

`target/stage23b-bounded-save-validation.log`: **42 теста**, 0 failures/errors/skips,
BUILD SUCCESS за 3:13, строгий Javadoc и desktop packaging.
Проверены границы/закрытие вложенного потока, все Stage-21A–I persistence acceptance,
native campaign codec, личная добыча и карты.
`target/stage23b-resource-map-journal-validation.log`: **5 тестов**, BUILD SUCCESS;
покупка карты получила собственное понятное название в личном журнале.

`target/stage23b-ordinary-mining-large-graphics.log`: **Ordinary mining checkpoint graphical
probe passed** на RTX 4070, heap 4 ГБ. Настоящий клиент F9 точно загрузил полученное после
продажи сохранение и клавиатурой открыл собственный ресурсный маркер. Деньги, груз, журнал,
личные знания и мировой такт сохранены; выбор системы меняет только предусмотренное
состояние навигации/планировщика. Инспектор показывает реальное личное наблюдение и отсутствие
количественных измерений, не точный физический запас/содержание. Геометрических или
оборудовательных fixtures в этом графическом проходе нет.

Статус: **23B ACTIVE/PARTIAL**. Физическая цепочка покупки шахтёра, добычи и продажи доказана;
это не завершение остальных пунктов 23B и не ручная приёмка этапа 23.

Финальный `mvn -B -ntp -Ptest-parallel clean verify` после исправления декодеров:
`target/stage23b-bounded-save-clean-verify.log`, **2557 тестов, 0 failures, 0 errors,
1 штатный skip** (`Stage19JLongSoakTest`), BUILD SUCCESS за **14:08**.
Строгий Javadoc, desktop packaging и все пороги JaCoCo пройдены.
QA-сохранения, журналы и графический снимок сохранены отдельно перед clean и восстановлены
в `target`; снимок фактического исследованного участка —
`target/stage23b-ordinary-mining-known-marker.png`.

### Продолжение 2026-10-14: измеренная навигация и новая станция

Закрыт переход из личной сенсорной строки на анонимную измеренную точку локальной карты,
включая keyboard C focus, погрешность/возраст и отсутствие true target metadata.
Bearing-only, потерянное received range evidence и другая система не дают координату.
Пересекающиеся подписи больше не накладываются на выбранную. 13 целевых тестов,
Javadoc/package прошли; настоящий measured checkpoint и RTX 4070 F8/F9 — PASS.

Отдельный обычный новый старт seed 1 с оплаченной покупкой fleet:7 затем прошёл
63766 общих физических ticks: реальные radar pulses, guidance только по полученным
range/bearing estimates, подход и DOCK ранее неизвестного hub.16. Проверено отсутствие
цели в исходном личном архиве/discoveredObjects, точный composed native round trip и
GPU keyboard focus/F8/F9. Исторические сохранения не изменялись. После стыковки нет
доступных industrial map offers; кошелёк 75000 кр., 0 покупок, отдельный PARTIAL output.

Подробные журналы, snapshots и различие промежуточной полной регрессии (2648 tests,
JaCoCo/Javadoc/package) и последующей целевой проверки — в
[журнале реализации](stage23b_production_ui_consolidation.md).
Этап остаётся **ACTIVE/PARTIAL**; полный актуальный перечень —
[канонический остаток](stage23b_remaining_work.md).

### Продолжение 2026-10-14: физическое состояние малых аппаратов (PARTIAL)

Native v25 сохраняет отдельные точные координаты и скорости уже выпущенных аппаратов.
Чтение v1–24 оставляет этот раздел пустым: позиции, аппараты и завершённые вылеты
не выводятся из старых миссий. Общий неизменяемый инженерный каталог объединяет
принятые определения грузовых кораблей и аппаратов без изменения физических параметров.
Движение аппарата использует существующий PlayerDirectControlSystem на общем world tick.
Завершённый deck cycle освобождает отсек только при явной актуальной физической проекции
устья запуска; стандартный пустой provider сохраняет ожидание handoff. Ретаск сохраняет
происхождение вылета; подтверждённая потеря сразу удаляет физический маркер и однократно
пишет личное событие. Личная карта и список кораблей показывают только доказанно свои
аппараты и не раскрывают opaque цели миссий.

Целевая проверка: target/stage23b-native25-local-flight-verify.log — 37 tests,
0 failures/errors/skips, Javadoc/package PASS. Проверка GPU RTX 4070:
target/stage23b-native25-local-flight-graphics.log — клавиатурный выбор аппарата,
фокус C, точные F8/F9 на паузе и отдельный реальный такт движения, PASS.
Сохранение target/stage23b-native25-local-flight-graphics-paused-fixture.s25 и снимок
target/stage23b-native25-local-flight-focus.png используют ЯВНО заданные produced/deployed
craft и геометрию; обычное приобретение или запуск этим не подтверждены.
Полная регрессия этой версии ещё выполняется; результат будет записан отдельно.

Открыты обычное приобретение носителя/аппаратов, личные команды, авторская физическая
геометрия запуска/приёма, маневрирование и последствия миссий, снабжение/замена и
самостоятельное личное владение аппаратом после утраты/продажи носителя.
Этап 23B остаётся ACTIVE/PARTIAL; этот срез не закрывает пункт 5 целиком.

Продолжение этого среза: физический deck recovery теперь согласован с local-flight owner.
Пока фактический приём стоит в очереди и отсек недоступен, аппарат остаётся снаружи,
движется и сохраняется с RECOVERY_PENDING. Когда существующий deck owner принимает
его в RECOVERING, отдельная позиция удаляется без второго такта движения; окончание
конечной обработки переводит миссию в COMPLETE, оставляя аппарат SERVICING.
Топливо, боезапас, деньги и новая identity не выдаются. Личный журнал фиксирует приём
в ангар один раз. Новый runtime-тест покрывает ожидание, reload, приём и завершение;
составной native-тест — точное восстановление очереди и движение при недоступном отсеке.
Геометрия recovery gate в этих тестах задана явно; обычный вход в приёмный шлюз ещё открыт.

Первая полная регрессия v25: 2659 tests, 7 failures, 0 errors, 1 штатный skip.
Все семь failures относятся к общему helper старых файлов: он ожидал EOF сразу после
crew payload и не учитывал новый local-flight payload. Helper теперь проверяет и удаляет
только пустой раздел; действующую физическую позицию он отбрасывать запрещает.
Повторная историческая проверка target/stage23b-native25-historical-fixture-verify.log:
28 tests, 0 failures/errors/skips, Javadoc/package BUILD SUCCESS.
Настоящее старое сохранение неизвестной станции прошло GPU keyboard focus/F8/F9
на v25: target/stage23b-native25-historical-graphics.log, без геометрических fixture.

Последующее согласование физического приёма проверено составной цепочкой и native restore
внутри RECOVERING/SERVICING; событие приёма однократно, COMPLETE наступает на общем такте.
Актуальные целевые проверки: 25 и 19 tests, Javadoc/package PASS; GPU повторно PASS.
Первая полная recovery-регрессия выявила один повторный ручной COMPLETE в старом тесте;
после исправления автоматическое завершение и прежние физические проверки сохраняются.
Полная повторная регрессия: target/stage23b-native25-final-full-verify.log (выполняется).
На обычной стыковке нового пилота у hub.16 диспетчер доступен, предложенных контрактов 0.
Отдельное продолжение реальной добычи продало ещё 947 кг за 17046 кр., затем конечный
бюджет покупателя остановил продажу; кошелёк 119988 кр., точный reload отдельного файла PASS.
Подробные свидетельства и границы — в журнале реализации; 23B по-прежнему ACTIVE/PARTIAL.

### Итог текущей регрессии 2026-10-14

Актуальная полная проверка target/stage23b-native25-final-full-verify.log:
**2661 tests, 0 failures, 0 errors, 1 штатный skip**, BUILD SUCCESS за **11:07**
(17:22:14 +03:00). JaCoCo: все coverage checks выполнены; Javadoc/package PASS.
Включены исправления исторических fixture, автоматический recovery COMPLETE и составное
native восстановление во время приёма, однократный журнал и неизменные конечные consumables.
Это полная регрессия текущей реализации, не финальный clean verify всего законченного 23B.
Статус этапа остаётся **ACTIVE/PARTIAL**, восемь обязательных блоков остатка не закрыты целиком.

2026-10-14: игроковая отмена queued запуска подключена к SHIPS, клавиатуре,
pure single-use preview, общему mission/deck validator и однократному журналу.
После физического вылета отмена запрещена; текущий аппарат/расходники сохраняются.
Целевые 10 tests и GPU keyboard preview/confirm/F8/F9 прошли на явно заданной очереди.
Обычное приобретение носителя/аппарата и остальные миссии по-прежнему открыты.
Карточка аппарата дополнительно очищается от технических design/bay IDs и enum-статусов.
Подробности и границы доказательства — в журнале реализации.

Итог текущего продолжения: полная регрессия команды аппарата — 2663 tests,
0 failures/errors, 1 штатный skip; покрытие/Javadoc/package PASS (13:13).
Последующие понятные подписи карточки проверены отдельно: 21 tests и повторный GPU
keyboard preview/confirm/F8/F9 PASS. Полный этап этим результатом не заявляется закрытым.
Обычное ожидание у hub.16 продвинуло мир ровно на 30000 тактов (63766 → 93766),
кошелёк сохранил 75000 кр.; доступный диспетчер по-прежнему не предлагает контрактов
или промышленных карт. Все выходы отдельные, точное восстановление подтверждено.
Для остальных carrier-миссий остаются физические link context/план манёвра и авторское
устье запуска; текущий принятый datalink задаёт каналы, но намеренно не задаёт дальность.
Подробные журналы и границы доказательства — в журнале реализации. Статус ACTIVE/PARTIAL.

### 2026-10-14: реальные остатки добытого образца в личной разведке

Карточки ресурсных открытий в INTELLIGENCE дополнены материалом, оставшейся массой
в собственных трюмах и временем самого раннего/позднего сохранившегося поступления.
Источник — существующие CargoLotState и ExtractionReceiptSeries; проекция не читает
точные запасы или состав месторождения. Учитываются только собственные корабли,
точный личный extraction order и provenance. Купленный, чужой, проданный и выгруженный
груз исключён. Погашенные квитанции не возвращаются в расчёт массы; FIFO использует
время оставшейся головной квитанции. Серии не разворачиваются в отдельные поступления.

Дополнение доступно только существующему личному наблюдению RESOURCE_OCCURRENCE
с RESOURCE_INDICATION или выше. Груз не создаёт новое открытие или ResourceEstimate.
После расходования образца сохраняется архивное свидетельство с пояснением отсутствия
груза на собственных кораблях. Исходное время наблюдения не обновляется.
Каталог материалов кэшируется в проекторе; формат сохранения не меняется.

target/stage23b-extraction-sample-ui-tests.log: **15 tests, 0 failures/errors/skips,
BUILD SUCCESS**, Java 17, 32.122 с. Проверены личная собственность, provenance,
несколько кораблей, FIFO с миллионной серией, отсутствие знания/NONE, расходование
образца, неизменность checkpoint при проекции и точный native restore игровой добычи.
Независимый reviewer проверил этот срез; замечания по каталогу и покрытию устранены.
target/stage23b-extraction-sample-ui-javadoc.log: строгий Javadoc **BUILD SUCCESS**, 15.956 с.
Полная регрессия и GPU-проверка этого дополнения не выполнялись. Количественное
исследование месторождения остаётся открытым: авторская модель измерителя состава,
запаса и погрешности отсутствует. Статус **23B ACTIVE/PARTIAL**.

### 2026-10-14: игроковая загрузка боезапаса собственной станции

LOAD_AMMUNITION использует действующую физическую стыковку лично принадлежащего idle
корабля у собственной станции. Целые патроны выбранного установленного кинетического
орудия списываются из конечного product stock через Stage19WarfareSupplyService и
поступают в прежний ConsumableState. Привязанный WeaponLoadoutState, damage, maintenance,
энергия и тепло сохраняются; деньги, боеприпасы или готовность не выдаются.
Непривязанные, несовместимые и уничтоженные орудия не получают пополнения; отказ
одного магазина не закрывает другой корректный магазин. Полный существующий adapter
по-прежнему проверяет все боевые орудия; servicing использует проверку выбранного mount.

Владение и реальная геометрия проверяются до раскрытия склада. Служба проверяет
существующие authored массу/геометрию патрона, fitted feed capacity и предел массы
корпуса. Масса партии ограничена общим endpoint mass rate; отдельный патрон — max unit
mass. Ремонт, переоснащение и перенос оборудования/изделий исключают одновременную
загрузку. journal фиксирует фактическое исполнение в штуках и расход handling interval;
повторная загрузка/commodity handling того же такта запрещены и после native restore.

Runtime product vocabulary дополнен существующими core/small-craft ammunition IDs
через withAmmunitionCatalog(STAGE22_AUTHORED). Это регистрация ранее авторских изделий,
без изменения тел боеприпасов, recipes, starting stock или формата ConsumableState.
Во вкладке SHIPS добавлены own-berth rows, кнопки -/+ ШТ., pure single-use preview и
confirm. На паузе применяется только доступная обработка уже завершённого интервала.

Целевые проверки до последнего max-unit-mass guard:
target/stage23b-personal-ammunition-final-tests.log — **17 tests, 0 failures/errors/skips,
BUILD SUCCESS**, 35.780 с. Проверены консервация stock/count/mass и идентичности,
empty/foreign/remote/unbound/incompatible отказы, переполнение магазина при достаточном
stock, предел корпуса, уничтоженное оружие, сохранение использованного handling, обратный порядок
commodity→ammo, native reload, одно событие и расход патрона общим AmmunitionRuntime.
Независимый reviewer подтвердил устранение случая смешанных магазинов и отсутствие
оставшихся блокирующих замечаний этого среза; итог зависит от полной регрессии.

target/stage23b-personal-ammunition-graphics.log — **RTX 4070 PASS**: клавиатурные
количество/preview/confirm, одно событие, неизменность денег и точные F8/F9.
target/stage23b-ammunition-graphics-fixture.s25 использует явно заданные weapon fit,
own-station, четыре патрона и berth; обычное приобретение не заявляется.
Снимки target/stage23b-ammunition-preview.png и target/stage23b-ammunition-loaded.png
визуально проверены: 0→1 патрон в магазине, 4→3 на складе, поля и кнопки читаемы.
Полная регрессия последнего исходного состояния:
target/stage23b-personal-ammunition-full-verify.log — **2673 tests, 0 failures/errors,
1 штатный skip**, **BUILD SUCCESS**, Java 17, -Ptest-parallel verify, **15:04**
(23:15:35 +03:00). JaCoCo: все coverage checks выполнены; строгий Javadoc и package PASS.
Включены последний max-unit-mass guard, предыдущий срез отображения образцов и
унаследованное рабочее состояние 23B. Это полная регрессия текущего дерева, не итоговый
clean verify завершённого 23B. Статус **23B ACTIVE/PARTIAL**.

### 2026-10-15: партии боеприпасов в личном производстве

В INDUSTRY добавлены Union 140 кг и Empire 150 кг патроны с существующим общим
manufacturing.profile.kinetic_ammunition. Runtime-композиция получила только готовый
Empire product binding; профиль, fractions/energy/work/maintenance и Stage18 default
fingerprint не изменены. Новых рецептов, запасов или производственных возможностей нет.
Small-craft ammo не предлагаются без подтверждённых manufacturing bindings.

Выбор партии использует отдельный счётчик целых патронов, кнопки -/+ ШТ. (Shift — 100,
Ctrl — 10 000), точную массу и полный bill материалов. Новое количество сбрасывает
прошлый preview. START_MANUFACTURING передаёт выбранный count в прежнюю очередь;
другие секции по-прежнему заказываются по одной. Работающий заказ показывает собственный
requestedUnits, резерв и completedFraction, независимо от нового выбора количества.
Готовая партия поступает целиком; отмена возвращает материальный резерв при наличии места,
не возвращая работу. Отсутствующая/выключенная линия не подменяется номинальной мощностью.

Транзакционные тесты создают отдельный явный fixture: существующая собственная
физическая стыковка и оружие; конечный donor для bill конструкции ordnance; явный
construction-work budget, реальный следующий world tick, adoption выключенной установки
и распределение существующих station resources. Материалы патронов также заданы конечным
донором. Это проверка общей цепочки исполнения, не обычное получение этих активов.
Обе партии по три патрона проходят pure preview/confirm, полный reserve, partial native
save/load и одинаковое продолжение до выпуска. Изготовленный Empire патрон затем
загружается в прежний магазин через LOAD_AMMUNITION. Два параллельных ammo orders
расходуют один energy budget; отмена после partial reload возвращает reserve ровно один раз.

target/stage23b-ammunition-manufacturing-verify.log: **24 tests, 0 failures/errors/skips,
BUILD SUCCESS**, Java 17, **56.935 с**, строгий Javadoc и package PASS. Это целевой verify
с -Djacoco.skip=true; глобальное покрытие и полная регрессия дерева здесь не заявляются.
Предыдущая полная регрессия 2673 tests относится к состоянию до этого производственного среза.

target/stage23b-ammunition-manufacturing-graphics.log: **RTX 4070 PASS, exit 0** — keyboard
count/preview/confirm, сброс stale preview при смене партии, резерв ровно трёх патронов,
настоящий partial tick, exact F8/F9 и отмена без готового выпуска. Снимки
target/stage23b-ammunition-manufacturing-preview.png и
target/stage23b-ammunition-manufacturing-partial.png визуально проверены: 3 шт. / 450 кг,
исходные materials и reserve, 0.56% выполненной работы, читаемые кнопки и счётчик.
Независимый reviewer не нашёл блокирующих замечаний; уточнение текста отмены применено.
Обычный путь строительства/ввода ordnance и получения сырья остаётся открытым.
Статус **23B ACTIVE/PARTIAL**.

## Продолжение 2026-10-15: отдельное личное владение аппаратами

Native v26 хранит отдельный ограниченный реестр personalCraftOwnership. Связь крыла,
юридический оператор и владение носителем больше не создают право собственности на аппарат.
Смена/потеря носителя сохраняет личный аппарат на карте и в списке; командная доступность
по-прежнему проверяет существующий законный носитель и физическое состояние.
Утрата самого аппарата сохраняет историческое право без создания замены. Передача права
отзывает доступ и инвалидирует старое подтверждение. Журнал launch/recovery/loss проверяет
право на аппарат и допускает отсутствие собственного носителя. Player/world composition
сохраняет реестр. Не выделенные allocator ID и дубликаты отклоняются; v1–25 получают пустой
реестр, без вывода приобретений из чужих/старых wings. Trusted composition boundary принимает
явный результат приобретения/передачи; обычный игровой путь приобретения ещё требуется.

Проверка: `target/stage23b-personal-craft-property-verify.log`, **21 tests, 0 failures/errors/skips,
BUILD SUCCESS**, строгий Javadoc/package, целевой прогон без JaCoCo. Независимое ревью
выявило и исправлено стирание реестра при world transition и допуск будущих craft ID.
RTX 4070: `target/stage23b-personal-property-local-flight-graphics-v26.log`, **PASS**:
keyboard focus собственного аппарата, F8/F9 до/после общего физического такта. Аппарат,
вылет и координата в этой проверке явно заданы fixture; обычное получение/вылет не заявлены.

Обычная навигация: предыдущий вывод об отсутствии промышленной цели у нового пилота
исправлен. В личном seller briefing уже присутствует `industry.2` (system 2), цена 70 000 кр.
при кошельке 75 000 кр. QA ошибочно искал только текущую систему 16. Исправленная проверка
использует существующий личный архив независимо от системы: 0 покупок карт, точный native
reload, `target/stage23b-ordinary-new-pilot-industrial-map-audit-v26.log`, **PASS**. Полный
личный маршрут пока отсутствует: известны системы [2,16]. Нулевые station map offers здесь
корректно подавляют повторную продажу уже известных координат. Полёт/покупка этим логом
не доказываются. Новый journey исследует только опубликованные production UI выходы,
затем использует планировщик по лично открытым системам; скрытый topology itinerary не передаётся.

23B остаётся ACTIVE/PARTIAL: обычные supply/bootstrap, новые промышленные станции,
quantitative surveys, реальные carrier missions, strategic operations и финальная регрессия
должны быть завершены отдельно. Ручная приёмка остаётся после всего этапа 23.
### Уточнение обычного bootstrap, 2026-10-15

На настоящей новой ветке завершены опубликованные UI-переходы 16→11 (tick 1 730 523)
и 11→18; затем начат 18→13. `target/stage23b-ordinary-industrial-explored-navigation-v26.log`
и `...-next.log` подтверждают реальную физическую работу и точный reload bounded результатов;
это не полный маршрут/покупка. FTL после прибытия требует 299 настоящих тактов готовности,
которые QA теперь ожидает, без сброса cooldown. Продолжение читает сохранённый незавершённый
прыжок; промежуточные файлы записываются каждые 100 000 тактов.

Промышленный аудит выявил дополнительный обязательный остаток, а не завершение bootstrap:
у `industry.2` нет `storage.liquid_tank`. Установка bootstrap предоставляет chemical capability,
но не физическую ёмкость. `refining.industrial_chemicals` требует 55% carbonaceous и 45%
volatile feedstock; второй вход нельзя разгрузить на эту базу. Наличие interface у facility
не создаёт станционные танки. Для bootstrap25M базовая bill требует 15M structural alloy,
6.25M light alloy и 3.75M conductor metal; при нулевом запасе это не менее 22 058 824 кг
metallic ore, 11 574 075 кг light-metal minerals и 5 952 381 кг conductor ore.

Существующие `refinery_complex` имеют жидкий склад 10M кг, bulk/chemical структуры и цену
180 000 кр.; `frontier_multipurpose` — жидкий склад 20M кг и цену 200 000 кр. Но большинство
таких generated endpoints сейчас представлены только storage/handling: промышленный реестр
содержит единственную специализированную `industry.2`. Проверка цены обращается к отсутствующему
industrial node. Поэтому нужны согласованные покупка/промышленный admission существующего
endpoint и сохранение единственного владельца его склада. Регистрация шаблонных структур
должна оставлять их выключенными без operating allocations; источники энергии/персонала/
обслуживания остаются отдельной обязательной задачей. Изменять frozen generation rows,
исторические bootstrap fingerprints или выдавать запасы для обхода этого разрыва нельзя.
Указанные типы/экземпляры — диагностические данные, не личные знания нового пилота.
Полная регрессия после v26 и исправления historical fixtures завершена:
`target/stage23b-personal-property-full-verify-fixed.log` — **2680 tests, 0 failures/errors,
1 штатный skip, BUILD SUCCESS**, JaCoCo thresholds, строгий Javadoc и desktop package PASS
(2026-10-15 13:52:38 +03:00). Первый диагностический полный прогон выявил 9 падений framing/
release-doc checks; они исправлены, повторный прогон полностью зелёный. Основной код после
этого прогона не изменён. Проверена текущая рабочая копия с незакоммиченными изменениями;
это не exact-head CI и не финальный isolated clean verify всего завершённого 23B.
### Продолжение 2026-10-16: pending handling при admission

Исправлен composed validator частичной обработки личного модуля: вместо поиска оператора
только в industrial specialization module-only path проверяет настоящий ECS-владелец,
существующую экономику, canonical civilian placement и точный berth. Repair/refit/product
default остаётся строгим. На existing frontier с реальными oversized storage/handling
проверены явно заданная historical custody, partial work, обычная покупка, exact reload,
completion/cancel без дублирования массы/денег и отсутствие faction/docking/дистанция 5000 м.
23 целевых теста PASS, независимое ревью без блокирующих замечаний. Полная регрессия:
`target/stage23b-pending-admission-full-verify.log` (выполняется).
Это закрывает отдельную проверку admission/pending module handling; обычный старт обработки
на ещё не зарегистрированной станции и весь промышленный bootstrap не заявляются закрытыми.
Подробности — [описание admission](stage23b_existing_station_industrial_admission.md).

Обычная навигация продолжена runtime v27 из точного migrated v26 checkpoint с heap 6 GiB
и бюджетом 10 млн настоящих тактов: `target/stage23b-ordinary-industrial-navigation-v27-continuation.log/.s25`.
Исходный файл сохраняется; промежуточные контрольные записи выполняются каждые 100000 тактов.
Результат незавершённого прогона не считается прибытием или покупкой станции.
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
### 2026-10-16: initial station legal capital — full gate

Отдельная политика `stage23b.initial-npc-station-legal-capital.v1` реализована для новых
кампаний: ограниченные существующие refinery/frontier получают настоящих NPC-операторов,
только owner-local архив и местного диспетчера. Старый explicit baseline и restore сохраняют
отсутствие владельца; деньги/stock/geometry/operating allocations не создаются. Один план
используется в preview и commit; условия старта показывают только число назначений.
Назначенный экономический владелец не меняется вслед за поздним territorial fallback.

`target/stage23b-initial-station-capital-full-verify.log`: **2690 tests, 0 failures/errors,
1 штатный skip, BUILD SUCCESS**, 14:52, 2026-10-16 09:27:48 +03:00; JaCoCo thresholds,
строгий Javadoc и desktop package PASS. Независимое read-only ревью без блокирующих находок.
Реальный acquired checkpoint `industry.2` проверен после этой policy: exact restore и один
общий такт без изменения кошелька/флота, `target/stage23b-real-acquired-station-current-policy-integrity.log`.
Этот gate относится к initial legal capital до последующих storage v28/FIFO изменений. Рабочая копия содержит inherited uncommitted changes,
не exact-head CI и не финальный isolated clean gate всего 23B.

Подробности: `docs/ui/stage23b_initial_station_legal_capital.md`. Следующий промышленный
разрыв — оплачиваемое расширение liquid storage работающей станции; контракт зафиксирован
в `docs/ui/stage23b_paid_liquid_storage_expansion.md`, **реализация ещё отсутствует**.
Обычный ресурсный маршрут с low-fuel шахтёром остаётся PARTIAL, безопасные выходы не найдены;
QA не выдаёт топливо/координаты. Все восемь широких блоков остатка 23B остаются открытыми.
### 2026-10-16: bounded small-craft production vocabulary

`Stage23SmallCraftProductionCatalogs` adds exactly seven existing M22.8J module IDs,
their authoritative engineering masses, seven bindings to existing manufacturing family
profiles, and the two compact hull / seven module service profiles. Historical Stage22
loaders remain unchanged. `withEngineeringModules` selects explicit IDs from a larger
engineering universe and rejects unknown, repeated and already registered identities
without changing the original registry. No inventory, funds, operating resources or work
are created. The campaign runtime is not switched to this catalog yet: persistent paid
BUILD_SMALL_CRAFT escrow, shared yard work, physical delivery and ownership integration
remain necessary before claiming the playable acquisition path.

`target/stage23b-small-craft-catalog-manufacturing-tests.log`: 10 tests PASS, including
all seven full material bills, refusal without work and refusal to reuse spent work/material.
The test supplies explicit materials and capabilities; this is not ordinary acquisition.
Independent read-only review found no blockers. Full gate:
`target/stage23b-small-craft-catalog-full-verify.log` — PASS; final result below.
### Ordinary paid resource navigation: PASS, 2026-10-16

`target/stage23b-ordinary-new-pilot-resource-navigation-resumed-v28.log` reached
system 3 on tick 40537628, using only personally known routes and published UI exits.
The target is the previously purchased report
`source.stage20e.s3.resource.3.0.occurrence.conductors.commodity.feedstock.conductor_ore`.
The ordinary pilot checkpoint reloaded exactly; no assets or money were granted.
This closes the system-navigation leg for this paid report, not mining or quantitative survey.
The separate physical approach now runs from that preserved arrival checkpoint:
`target/stage23b-ordinary-paid-resource-physical-visit-v28.log/.s25`.
Its guidance reads only the saved personal waypoint; no approach completion is claimed yet.
Ordinary physical approach completed: `target/stage23b-ordinary-paid-resource-physical-visit-v28.log`
— **PASS**, tick 41500311, distance 249.99868551598124 m after 962683 actual flight ticks.
Guidance used the purchased personal waypoint, with ordinary thrust and braking. Exact native
reload passed. The wallet, owned fleets, resource knowledge and original report evidence were
preserved; no extraction or deposit survey was fabricated. The paid map → published-exit
navigation → local physical approach chain is now demonstrated. Cargo-only equipment does
not gain mining capability from visiting the site.
Ordinary system-3 local exploration also reached a real station without using its hidden
coordinate: `target/stage23b-ordinary-resource3-origin-search-complete-v28.log` — PASS.
After the predetermined origin search and actual braking, DOCK hub.3 committed at tick
43523559. Original resource-visit and intermediate search checkpoints remain preserved.
The station has actual water stock; no refueling transaction has been claimed.
`target/stage23b-ordinary-hub3-npc-contract-audit-v28.log` is a pure local audit: wallet
4900 credits, empty personal cargo, zero accessible NPC contacts/contracts in this historical
campaign. No new dispatcher or offer was injected to turn this visit into contract acceptance.
Full gate completed: `target/stage23b-small-craft-catalog-full-verify.log` —
**2701 tests, 0 failures, 0 errors, 1 skip; BUILD SUCCESS**, 25:12,
2026-10-16 14:53:00 +03:00. Coverage thresholds, strict Javadoc and package passed.
This result supersedes the running gate status above. Independent catalog review found
no blockers. No production source changed after this gate started; later changes were
QA journey code and documentation. Stage23B remains ACTIVE/PARTIAL: this catalog slice
does not complete persistent small-craft construction, physical delivery or the other
remaining acceptance blocks. Native format remains v28.

## 2026-10-16: сохранённое строительство малого аппарата

Реализована paid build queue с полным материальным резервом, реальными общими тактами
верфи, отменой нетронутого заказа и отдельной приёмкой готового пустого аппарата.
Native v29 сохраняет очередь и точно мигрирует настоящий checkpoint v28 без выдач.
Прошли целевые проверки завершения, совокупного складского резерва, подделок и повторного
использования номера отменённого заказа; строгий Javadoc прошёл. Полный verify выполняется:
target/stage23b-small-craft-build-native29-full-verify.log. Обычное получение полного bill,
физическая доставка и прочий канонический остаток 23B остаются открытыми.

## Current native29 production gate — 2026-10-16 20:13:13 +03:00

Full mvn -Ptest-parallel verify completed: target/stage23b-small-craft-build-native29-full-verify.log,
2714 tests, 0 failures, 0 errors, 1 expected skip, BUILD SUCCESS, 18:11.
Both coverage files were merged, JaCoCo thresholds passed, strict Javadoc and desktop package passed.
This supersedes preceding in-progress entries and includes the central typed/file legacy guard,
module-card names, cancelled-order cache reuse fix and conserved available-yard allocation.
Independent reviews found no remaining blockers for these slices. Builder affiliation stays locked
until CLAIM or valid cancellation; ordinary affiliation preview refuses safely without live mutation.
No production source changed after the successful gate began. Subsequent changes are QA/test
continuation and documentation. This dirty working-copy gate is not exact-head CI or whole23B RC
acceptance. Stage23B remains ACTIVE/PARTIAL; ordinary material chains, delivery and other broad
acceptance blocks remain open.

## Generated NPC supply composition — 2026-10-16 20:23:47 +03:00

GeneratedCampaignNpcSupplyOffersTest now verifies the previously missing successful composed
cycle: real freight loss and actual shortage observation fund a generated proposal; player
ACCEPT takes1kg; PURCHASE buys the existing idle NPC mining vessel; START_MINING produces
external cargo from an existing finite FREE_BODY source over completed ticks; ordinary SELL
creates/consumes the delivery receipt and completes the contract. Source reserve decreases,
cargo loses1kg, recipient stock gains1kg, sale income and held bonus are paid exactly, issuer
treasury is not charged again, and reputation/journal completion occur once. Journal money sums
match the actual wallet delta. Exact native reload and the next tick do not replay the bonus.

Targeted gate: target/stage23b-npc-generated-offer-ordinary-sale-composition.log —1test PASS,
0 failures/errors,38.477s. This expands an existing test after the2714-test production gate;
production sources remain unchanged. Independent read-only review found no blockers.
This is composed integration, not ordinary journey evidence: destruction/review invocation and
travel/source geometry are explicit fixtures. NPC, proposal, cargo and escrow are not injected
in the new successful-cycle helper. Historical zero-roster campaigns receive no new NPCs.
Full ordinary NPC continuation without those fixtures remains open.

Физический путь шахтёра к industry.2 продолжен из сохранённого движения через систему8. Файл `target/stage23b-actual-miner-system8-to-industry2-native29.s25` сохраняет ещё 2000000 реальных тактов, результат PARTIAL/TICK_BUDGET, прибытие не заявляется. Следующий участок пишет отдельный `target/stage23b-actual-miner-system8-transit2-to-industry2-native29.s25` на неизменном проверенном runtime `target/qa/stage23b-native29-2714-flight-runtime.jar`. Деньги, топливо и исходные сохранения не подменяются.

2026-10-17: локальная охрана прошла полный `mvn -Ptest-parallel verify`:2722tests/0failures/0errors/1skip, JaCoCo/Javadoc/package PASS,18:40; `target/stage23b-local-guard-full-verify.log`. Целевые12PASS; reviewer blockers отсутствуют. Базовый полный2714PASS заменён новым2722PASS для текущего production-кода. Обычный маршрут кindustry.2 всё ещё PARTIAL: tick49929023,система3,339739.807kgтоплива,физическая местная дозаправка ещё требуется.
