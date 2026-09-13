# Core faction character roster art mapping

Status: production-art mapping for the two Stage-23 core factions.

The character presentation layer follows the governed Stage-22 systemic faction profiles instead of introducing presentation-only faction aliases. Runtime selection is resolved by authoritative stable faction ID through `Stage22FactionProfileCatalog.findProfileForFaction(...)`, then the portrait atlas is selected from the profile package key:

- `core.empire` → Empire portrait roster;
- `core.industrial_union` → Industrial Union portrait roster.

The current Stage-22 profile catalog binds those packages to the existing authoritative runtime/save faction identities. The portrait renderer does not rename, alias or mutate simulation identities and does not affect persistence, economy, diplomacy, territory, AI or any other simulation authority.

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
- faction-to-roster selection is derived from the governed Stage-22 systemic profile package key;
- the Factions tab shows the corresponding six-person roster when one of the two governed core factions is selected.
