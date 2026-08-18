package com.spacesim.ui;

/** Stable presentation/CLI identity for Stage-19J tactical validation scenarios. */
public enum TacticalScenarioId {
    /** Stage-17.5/19 1v1 regression reference. */ LEGACY_DUEL("duel"),
    /** Shared actor-bounded balanced 4v4. */ BALANCED_4V4("4v4"),
    /** Mixed-role 8v8 with finite guided specialist stores. */ MIXED_8V8("8v8"),
    /** Mixed 8v8 with accepted physical pre-damage and reaction-mass depletion. */ DAMAGED_DEPLETED_8V8("8v8-damaged"),
    /** Mixed 16v16 exact-local fleet engagement. */ MIXED_16V16("16v16"),
    /** Dense 16v16 kinetic/guided/interceptor/decoy saturation case. */ SATURATION_16V16("saturation");

    private final String cliKey;

    TacticalScenarioId(String cliKey) {
        this.cliKey = cliKey;
    }

    /** @return stable lowercase command-line key */
    public String cliKey() {
        return cliKey;
    }
}
