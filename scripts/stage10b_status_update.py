from pathlib import Path
p=Path("docs/development_roadmap.md")
t=p.read_text()
a="**Статус:** COMPLETE candidate — `docs/stage10b_jump_transit.md`"
b="**Статус:** COMPLETE — PR #20; `docs/stage10b_jump_transit.md`"
if t.count(a)!=1: raise SystemExit("marker mismatch")
p.write_text(t.replace(a,b,1))
