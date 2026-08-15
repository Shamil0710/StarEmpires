from pathlib import Path

path = Path("docs/development_roadmap.md")
text = path.read_text()

old_sync = "> Последняя синхронизация: **2026-08-15 после закрытия Stage 17C и подробной фиксации политико-экономической архитектуры Stage 17D–17F / Stage 18; `Ship Mathematics v1.0 Design Baseline` остаётся accepted foundation для 17.5 / 19 / 21. Фактический runtime-статус — Stage 17 ACTIVE, следующий implementation slice — 17D.**"
new_sync = "> Последняя синхронизация: **2026-08-15 после закрытия Stage 17F.1 и реализации Stage 17F.2; `Ship Mathematics v1.0 Design Baseline` остаётся accepted foundation для 17.5 / 19 / 21. Фактический runtime-статус — Stage 17 ACTIVE, следующий implementation slice — 17F.3 fiscal trade-offs.**"
if old_sync not in text:
    raise SystemExit("roadmap synchronization header anchor not found")
text = text.replace(old_sync, new_sync, 1)

old_f2 = """### 17F.2 — fiscal policy

**NEXT.** Faction может задавать:

- own-station tax rate;
- territorial foreign-station levy;
- treasury reserve floor;
- station liquidity-support policy;
- construction/investment budget priorities;
- после Stage 17.5/18 — military ammunition/repair/replacement reserve priorities.

Все выплаты и сборы являются real wallet transfers. «Budget» — authorization/priority над treasury, а не второй магический источник денег; отдельный sub-account допускается только как conserved persistent account.

### 17F.3 — fiscal trade-offs

Policy должна иметь реальные последствия:
"""
new_f2 = """### 17F.2 — fiscal policy

**COMPLETE — PR #111.** Общий player/AI `FactionFiscalPolicyState` и `WorldSimulation.updateFactionFiscalPolicy(...)` управляют уже существующими fiscal/economic seams, не создавая отдельную казну или abstract modifiers. Faction может задавать:

- own-station tax rate;
- territorial foreign-station levy;
- transaction/customs tariff rate через Stage-17E tariff law;
- treasury reserve floor;
- station liquidity reserve и max liquidity-support per decision;
- max construction/investment authorization per decision;
- после Stage 17.5/18 — military ammunition/repair/replacement reserve priorities.

Reserve floor ограничивает discretionary treasury outflow для subsidy и faction-funded construction; Stage-9D autonomous investment использует тот же доступный treasury budget. Tax/territorial levy продолжают исполняться как ordinary station→treasury `MONEY_TRANSFER`, customs — через уже существующий Stage-17E transaction settlement. Policy edit сам по себе не двигает деньги, товары, fleets, territory или diplomacy history.

World file format v7 добавляет только новые reserve/construction authorization fields; v1-v6 мигрируют с legacy behavior (`reserve floor = 0`, без дополнительного construction ceiling). Newly founded dynamic player faction стартует с нулевыми fiscal authorizations и использует тот же command boundary, что authored AI. Stage-17C capitalization/withdrawal сохраняют fiscal policy и diplomacy при balance-only transition.

Все выплаты и сборы являются real wallet transfers. «Budget» — authorization/priority над treasury, а не второй магический источник денег; отдельный sub-account допускается только как conserved persistent account.

### 17F.3 — fiscal trade-offs

**NEXT.**

Policy должна иметь реальные последствия:
"""
if old_f2 not in text:
    raise SystemExit("Stage 17F.2 roadmap anchor not found")
text = text.replace(old_f2, new_f2, 1)
path.write_text(text)
