# Stage 18 — deferred industrial chain expansion

> Статус: **DEFERRED / OPTIONAL CONTENT EXPANSION**  
> Зафиксировано: **2026-08-17**  
> Основание: Stage 18A–18I формирует завершённый минимальный physical-industrial baseline; дальнейшее углубление цепочек не является блокером для Stage 19–21.

## 1. Решение

Текущая Stage-18 промышленная модель сохраняется без дополнительного дробления до игровых, экономических и long-run simulation тестов.

Baseline на момент фиксации:

- **9** natural resource occurrence families;
- **23** physical commodity types;
- **11** refining/material-production recipes;
- **3** industrial component families;
- **13** reusable finished-product manufacturing profiles;
- **29** bound module/ammunition product identities.

Эти числа описывают текущий baseline, а не окончательный предел контента.

## 2. Что может быть расширено позднее

После тестирования допускается отдельный content/economy expansion, который может добавить промежуточные стадии там, где они создают meaningful gameplay consequences.

Кандидаты:

```text
ore / raw feed
→ concentrate / beneficiated feed
→ refined base material
→ specialized alloy / ceramic / chemical / electronic material
→ semi-finished stock
→ specialized mechanical / electrical / electronic assemblies
→ finished module / ammunition / infrastructure
```

Возможные направления:

- concentrates и mine-side beneficiation для дорогих haul chains;
- более специализированные structural/light/refractory alloys;
- отдельные polymers, propellant/energetic и industrial chemical families;
- semiconductor / optical / dielectric intermediate materials;
- motors, actuators, pumps, bearings и другие mechanical subassemblies;
- power electronics, control electronics, sensors и guidance subassemblies;
- специализированные ammunition/warhead/propulsion subchains;
- maintenance spares и service consumables, если тесты покажут отдельную логистическую ценность.

Этот список является направлением исследования, а не утверждённым набором новых SKU.

## 3. Критерий добавления нового commodity/process/facility

Новый элемент не вводится только ради реализма или большего количества товаров.

Он должен создавать хотя бы одно самостоятельное игровое следствие:

- новый природный или географический bottleneck;
- отдельное технологическое требование;
- meaningful logistics/storage/hazard constraint;
- самостоятельную trade specialization;
- отдельную инфраструктурную инвестицию;
- заметную возможность блокады или нарушения supply chain;
- meaningful substitution/recycling choice;
- отдельный maintenance/warfare consequence;
- существенно различимый баланс между регионами, фракциями или технологиями.

Если новая стадия всегда производится и потребляется внутри одного facility и не создаёт самостоятельного решения, она должна оставаться внутренней детализацией recipe, а не отдельным commodity.

## 4. Когда возвращаться к расширению

Решение о дроблении цепочек принимается только после данных из реальной симуляции, в частности:

- long-run economic soak;
- scarcity/price/stock volatility;
- inter-system logistics load;
- station specialization;
- военных потерь supply nodes;
- AI production planning;
- player readability и workload;
- выявленных «слишком коротких» цепочек, где крупный сектор экономики не создаёт собственного bottleneck.

Предпочтительный владелец основной content-expansion — **Stage 22 (Content & Balance Alpha)**.

Если расширение потребует **новых natural occurrence/resource families**, Stage 20 world-generation contract должен пройти отдельный compatibility review, чтобы новые ресурсы имели физически правдоподобную spatial distribution и не появлялись как произвольные loot classes.

## 5. Compatibility rule

Расширение не должно ломать физические инварианты Stage 18:

```text
finite source
→ physical extraction
→ physical storage/logistics
→ mass-closed processing
→ finite power/work/maintenance
→ physical products
→ physical operation/repair
→ bounded destruction/salvage/recycling
```

Любое изменение authoritative commodity/recipe/product semantics должно обновлять Stage-18 industrial semantic fingerprint и иметь явную save/content migration policy. Старые kg quantities нельзя молча переинтерпретировать как новый commodity или новую единицу.

## 6. Итог

Текущая промышленная схема считается **достаточной и завершённой для baseline**.

Дальнейшее расширение цепочек намеренно оставлено возможным, но будет выполняться только на основании результатов тестирования и только там, где дополнительная глубина улучшает экономическую специализацию, логистику, стратегические bottlenecks или решения игрока/AI.
