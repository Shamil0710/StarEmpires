# Global Strategic Map / Faction UI

> Status: **PRE-STAGE-17.5 PLAYABLE UI SLICE**  
> Scope: large manual test world presentation only.

## Purpose

The playable desktop client now exposes the existing Stage-10/17 strategic world state instead of forcing manual testers to infer galaxy structure from the current-system HUD.

Press **G** to open the full-screen strategic overlay; **G** or **Esc** closes it.

## Authoritative data contract

The overlay is presentation-only. It reads:

- `GalaxyTopology.systems()` for all strategic systems and their authored galaxy coordinates;
- `GalaxyTopology.connections()` for the explicit neighbor-only jump graph;
- `WorldSimulation.controllingFaction(...)` for current territorial controller;
- Stage-17 `FactionStrategicState` for controlled systems, fiscal rates, claims and goals;
- `FactionEconomicState` for real treasury balances;
- `FactionDiplomacyState` for customs tariff and treaty/embargo records;
- authored plus `WorldFactionIdentityState` names for all live factions.

It does **not** mutate fleet placement, territory, diplomacy, wallets, cargo, prices or jump state.

## Map presentation

The left strategic panel contains every system in the current galaxy and every explicit jump edge. System node color corresponds to controlling faction. Unclaimed systems use a neutral marker. The current system is highlighted in yellow; the direct neighbor currently selected by the ordinary K/J navigation UI is highlighted in cyan.

Each node is labelled by stable system number. Current-system details show its name, sector, controller and direct-link summary.

The overlay validates that a selected jump marker, when present, is an actual `GalaxyTopology.neighbors(current)` entry. It cannot display an invented non-neighbor jump target.

## Faction block

The right panel lists the live factions and reads current authoritative Stage-17 values:

- display name;
- number of controlled systems;
- treasury balance;
- own-station tax;
- foreign-territory/transit levy;
- customs tariff;
- territorial claim count;
- strategic goal count;
- treaty and embargo record counts.

These values are diagnostics, not bonuses. No faction receives a UI-only economy or strategic modifier.

## Input isolation

Opening the strategic overlay clears held movement input. While it is open, ordinary gameplay commands are consumed instead of passing through to the flight/combat/trade controls. The simulation itself continues to advance according to the current pause/time-scale state.

## Acceptance

Automated model tests require the large-demo snapshot to contain exactly the same system/edge counts as authoritative topology, preserve each system's controller and neighbor count, expose all eight current demo factions, and account for all 100 controlled systems. A selected jump marker outside the direct-neighbor graph must be rejected.
