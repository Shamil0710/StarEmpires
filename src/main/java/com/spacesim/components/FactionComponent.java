package com.spacesim.components;

import com.badlogic.ashley.core.Component;

public class FactionComponent implements Component {
    public int factionId;
    public FactionComponent(int id) { this.factionId = id; }
}