package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

public class FactionComponent implements Component {
    public int factionId;

    public FactionComponent(int id) {
        this.factionId = id;
    }

    public String getFactionName() {
        if (factionId < 0 || factionId >= Constants.FACTION_NAMES.length) {
            return "Неизвестная фракция";
        }
        return Constants.FACTION_NAMES[factionId];
    }
}
