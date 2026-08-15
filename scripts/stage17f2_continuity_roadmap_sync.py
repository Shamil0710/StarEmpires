from pathlib import Path

path = Path("docs/development_roadmap.md")
text = path.read_text()

replacements = [
    (
        "> Последняя синхронизация: **2026-08-15 после закрытия Stage 17C и подробной фиксации политико-экономической архитектуры Stage 17D–17F / Stage 18; `Ship Mathematics v1.0 Design Baseline` остаётся accepted foundation для 17.5 / 19 / 21. Фактический runtime-статус — Stage 17 ACTIVE, следующий implementation slice — 17D.**",
        "> Последняя синхронизация: **2026-08-15 после закрытия Stage 17F.1 и Stage 17F.2; continuity-hardening 17F.2 проходит PR #113, а Stage 17F.3 fiscal trade-offs уже ACTIVE в PR #112. `Ship Mathematics v1.0 Design Baseline` остаётся accepted foundation для 17.5 / 19 / 21.**",
    ),
    (
        "**ACTIVE — 17F.1 COMPLETE в PR #109; 17F.2 fiscal policy — NEXT.**",
        "**ACTIVE — 17F.1 COMPLETE в PR #109; 17F.2 COMPLETE в PR #110 с continuity-hardening в PR #113; 17F.3 fiscal trade-offs — ACTIVE в PR #112.**",
    ),
    (
        "### 17F.2 — fiscal policy\n\n**NEXT.** Faction может задавать:",
        "### 17F.2 — fiscal policy\n\n**COMPLETE — PR #110; continuity-hardening PR #113.** Общий player/AI fiscal-policy boundary управляет уже существующими conserved economic flows и treasury spending authorizations. PR #113 закрывает legacy reconstruction paths: pure treasury capitalization/withdrawal сохраняют reserve/construction policy и Stage-17E diplomacy, а founding dynamic player faction сохраняет существующую diplomacy и создаёт только собственный neutral diplomacy + explicit zero fiscal baseline.\n\nFaction может задавать:",
    ),
    (
        "### 17F.3 — fiscal trade-offs\n\nPolicy должна иметь реальные последствия:",
        "### 17F.3 — fiscal trade-offs\n\n**ACTIVE — PR #112.** Первый fiscal-only causal slice вводит read-only diagnostics реальной treasury/station/construction позиции и acceptance, где tax, subsidy и reserve/construction trade-offs возникают только через conserved wallet flows. Protectionism/open-trade supplier/route trade-offs остаются в связке с 17F.5 resilience policy, чтобы реализовываться через access, suppliers, logistics и route cost, а не flat multipliers.\n\nPolicy должна иметь реальные последствия:",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one roadmap anchor, found {count}: {old[:80]}")
    text = text.replace(old, new, 1)

path.write_text(text)
