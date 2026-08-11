package com.spacesim.components;
import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class TradeAIComponent implements Component {
    public enum State { IDLE, TRAVEL_TO_BUY, BUYING, TRAVEL_TO_SELL, SELLING }

    public State state = State.IDLE;
    public Entity buyStation;
    public Entity sellStation;
    public Entity targetStation;
    public int targetItem = -1;
    public int targetAmount = 0;
    public int cargoSpace = 100;
    public int cargoAmount = 0;
    public float credits = 1000f;
    public float expectedProfit = 0f;

    public void resetRoute() {
        buyStation = null;
        sellStation = null;
        targetStation = null;
        targetItem = -1;
        targetAmount = 0;
        expectedProfit = 0f;
    }
}
