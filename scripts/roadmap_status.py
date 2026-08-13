from pathlib import Path
p=Path('docs/development_roadmap.md')
t=p.read_text()
changes=[
('Текущий implementation focus: **Stage 10C — Galactic route planner**.','Текущий implementation focus: **Stage 10D — Cross-system market discovery**.'),
('### Stage 10C — Galactic route planner\n\n**Статус:** ACTIVE','### Stage 10C — Galactic route planner\n\n**Статус:** COMPLETE candidate — `docs/stage10c_galactic_route_planner.md`'),
('### Stage 10D — Cross-system market discovery\n\n- [ ] bounded market discovery across reachable topology;','### Stage 10D — Cross-system market discovery\n\n**Статус:** ACTIVE\n\n- [ ] bounded market discovery across reachable topology;')]
for a,b in changes:
    if t.count(a)!=1: raise SystemExit('marker mismatch')
    t=t.replace(a,b,1)
p.write_text(t)
