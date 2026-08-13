package com.spacesim.world;

/** Категория world-level местоположения persistent fleet. */
public enum FleetLocationKind {
    /** Fleet материализован ровно в одной local StarSystem simulation session. */
    IN_SYSTEM,
    /** Fleet временно отсутствует во всех local sessions и хранится в transit layer. */
    IN_TRANSIT
}
