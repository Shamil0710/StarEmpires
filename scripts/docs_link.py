from pathlib import Path
p=Path('docs/development_roadmap.md')
t=p.read_text()
a='**Статус:** COMPLETE candidate — `docs/stage10c_galactic_route_planner.md`'
b='**Статус:** COMPLETE — PR #21; `docs/stage10c_galactic_route_planner.md`'
if t.count(a)!=1: raise SystemExit('roadmap marker')
p.write_text(t.replace(a,b,1))

p=Path('docs/stage10c_galactic_route_planner.md')
t=p.read_text()
a='Status: COMPLETE candidate pending PR merge. Stage 10D is the next active implementation focus in the roadmap.'
b='Status: COMPLETE — PR #21 pending merge. Stage 10D is the next active implementation focus in the roadmap.'
if t.count(a)!=1: raise SystemExit('doc marker')
p.write_text(t.replace(a,b,1))
