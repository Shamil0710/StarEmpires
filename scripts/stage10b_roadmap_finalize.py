from pathlib import Path

path = Path("docs/development_roadmap.md")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "Текущий implementation focus: **Stage 10A — Fleet identity на world level**.",
    "Текущий implementation focus: **Stage 10C — Galactic route planner**.",
    "implementation focus",
)

replace_once(
    "### Stage 10A — Fleet identity на world level\n\n"
    "- [ ] определить persistent identity fleet/ship при переходе между local `SimulationSession`;\n"
    "- [ ] разделить «entity находится в system» и «entity находится in transit»;\n"
    "- [ ] transit state входит в `WorldState`;\n"
    "- [ ] переход не дублирует корабль одновременно в двух systems;\n"
    "- [ ] сохранение в середине transit безопасно.",
    "### Stage 10A — Fleet identity на world level\n\n"
    "**Статус:** COMPLETE — PR #19\n\n"
    "- [x] определить persistent identity fleet/ship при переходе между local `SimulationSession`;\n"
    "- [x] разделить «entity находится в system» и «entity находится in transit»;\n"
    "- [x] transit state входит в `WorldState`;\n"
    "- [x] переход не дублирует корабль одновременно в двух systems;\n"
    "- [x] сохранение в середине transit безопасно.",
    "Stage 10A status",
)

replace_once(
    "### Stage 10B — Jump transit\n\nМинимальная FSM:",
    "### Stage 10B — Jump transit\n\n"
    "**Статус:** COMPLETE candidate — `docs/stage10b_jump_transit.md`\n\n"
    "Минимальная FSM:",
    "Stage 10B status",
)

for old, new in [
    ("- [ ] deterministic jump duration;", "- [x] deterministic jump duration;"),
    ("- [ ] jump connections используются как navigation edges;", "- [x] jump connections используются как navigation edges;"),
    ("- [ ] path невозможен без topology connection;", "- [x] path невозможен без topology connection;"),
    ("- [ ] transit продолжается в remote simulation;", "- [x] transit продолжается в remote simulation;"),
    ("- [ ] save/load continuation сохраняет arrival state;", "- [x] save/load continuation сохраняет arrival state;"),
    ("- [ ] active system может меняться независимо от transit других флотов.", "- [x] active system может меняться независимо от transit других флотов."),
]:
    replace_once(old, new, old)

replace_once(
    "### Stage 10C — Galactic route planner\n\nРасширить planning от локального:",
    "### Stage 10C — Galactic route planner\n\n"
    "**Статус:** ACTIVE\n\n"
    "Расширить planning от локального:",
    "Stage 10C status",
)

path.write_text(text)
