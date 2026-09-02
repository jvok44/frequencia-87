package com.frequencia;

public class Pickup {
    public enum Type { FUSE, BATTERY, RADAR_CELL }

    public final Type type;
    public final double x, y;
    public boolean collected = false;

    public Pickup(Type type, double x, double y) {
        this.type = type;
        this.x = x;
        this.y = y;
    }
}
