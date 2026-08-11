package com.spacesim.components;
import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class TradeAIComponent implements Component {
    public enum State { IDLE, TRAVEL_TO_BUY, TRAVEL_TO_SELL, TRADING }

    public State state = State.IDLE;
    public Entity targetStation;
    public int targetItem = -1;
    public int cargoSpace = 100;
}
