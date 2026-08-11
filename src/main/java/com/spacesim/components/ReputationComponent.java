package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

public class ReputationComponent implements Component {
    private final float[] reputation = new float[Constants.MAX_FACTIONS];

    public float getReputation(int factionId) {
        if (!isValidFaction(factionId)) {
            return 0f;
        }
        return reputation[factionId];
    }

    public void addReputation(int factionId, float amount) {
        if (!isValidFaction(factionId)) {
            return;
        }
        if (!Float.isFinite(amount)) {
            throw new IllegalArgumentException("Изменение репутации должно быть конечным числом");
        }
        reputation[factionId] = Math.max(Constants.MIN_REPUTATION,
                Math.min(Constants.MAX_REPUTATION, reputation[factionId] + amount));
    }

    private boolean isValidFaction(int factionId) {
        return factionId >= 0 && factionId < Constants.MAX_FACTIONS;
    }
}
