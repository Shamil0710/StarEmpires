from pathlib import Path

path = Path("docs/development_roadmap.md")
text = path.read_text()
replacements = {
    "**NEXT — следующий Stage-17 implementation block после закрытия 17E.**":
        "**ACTIVE — 17F.1 COMPLETE в PR #109; 17F.2 fiscal policy — NEXT.**",
    "### 17F.1 — doctrine profile\n\nPersistent/data-driven doctrine задаёт веса общей decision model, а не performance bonus. Player faction получает editable baseline doctrine в допустимых пределах; authored AI factions получают характерные profiles.":
        "### 17F.1 — doctrine profile\n\n**COMPLETE — PR #109.** Persistent `FactionDoctrineState` хранит семь bounded `[0,100]` institutional axes и входит в versioned world persistence v6. v1-v5 saves мигрируют в neutral midpoint doctrine без выдумывания исторически отсутствовавшей faction personality. Authored AI factions получают разные data-driven profiles из content catalog; профиль входит в semantic content fingerprint и materialize-ится при создании нового мира. Dynamic player-created faction стартует neutral и редактируется через тот же `WorldSimulation.updateFactionDoctrine(...)` boundary.\n\nDoctrine задаёт веса общей decision model, а не performance bonus. Уже подключённый common treaty evaluator читает live persistent doctrine receiving faction; изменение профиля не переносит деньги/товары, не меняет production/combat/territory/legal access и не переписывает diplomatic history.",
    "### 17F.2 — fiscal policy\n\nFaction может задавать:":
        "### 17F.2 — fiscal policy\n\n**NEXT.** Faction может задавать:",
}
for old, new in replacements.items():
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one roadmap anchor, found {count}: {old[:70]}")
    text = text.replace(old, new, 1)
path.write_text(text)
