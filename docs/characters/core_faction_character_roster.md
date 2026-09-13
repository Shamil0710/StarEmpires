# Core faction character roster art mapping

Status: production-art mapping for the two Stage-23 core factions.

The generated-world client currently exposes provisional Stage-20 faction identities `faction.alpha` and `faction.beta`. The character presentation layer maps them explicitly as a compatibility bridge only:

- `faction.alpha` → Empire
- `faction.beta` → Industrial Union

The bridge changes presentation only; it does not rename or mutate simulation, persistence, economy, diplomacy, territory or AI authority. Canonical IDs `faction.empire` and `faction.industrial_union` are also accepted by the portrait renderer so the art survives the later identity migration without broad substring matching.

## Empire — `assets/characters/empire/character_roster.png`

Left to right, six 56×84 cells:

1. Admiral Viktor Arden — higher military command.
2. Elizaveta Kern — imperial diplomat / plenipotentiary.
3. Captain Marek Volin — combat ship / escort-group commander.
4. Anton Velsky — junior naval engineer.
5. Dr. Sofia Radan — military / ship medic.
6. Tomas Reichel — state logistics / procurement controller.

## Industrial Union — `assets/characters/industrial_union/character_roster.png`

Left to right, six 56×84 cells:

1. Commander Hana Markovic — fleet operational command.
2. Idris Kamal — large production-program coordinator.
3. Mira Chen — senior shipyard engineer.
4. Alexey Moreno — strategic cargo-convoy commander.
5. Lina Okafor — intersystem logistics dispatcher / coordinator.
6. David Stein — senior emergency-repair foreman.

## Runtime contract

- atlas dimensions: 336×84 pixels;
- six cells per faction, each 56×84;
- transparent background;
- renderer removes near-zero-alpha fringe before uploading the texture, preventing faint edge halos;
- art is presentation-only and can be absent without affecting simulation state;
- the Factions tab shows the corresponding six-person roster when one of the two core factions is selected.
