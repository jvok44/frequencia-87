package com.frequencia;

public class Player {

    public double x, y;
    public final double radius = 14;
    public double speed = 180; // pixels por segundo

    public double angle = 0; // direção da lanterna, em radianos
    public boolean flashlightOn = true;

    public double battery = 100;      // 0-100 lanterna
    public double sanity = 100;       // 0-100
    public int fuses = 0;
    public int fusesNeeded = 3;
    public boolean powerRestored = false;

    /** Cargas do radar (máx. 3). Começa com 1; +2 no mapa a partir da fase 4. */
    public int radarCharges = 1;
    public static final int RADAR_CHARGES_MAX = 3;
    public boolean radarUnlocked = false; // true a partir da fase 4 (após passar da 3)

    public Player(double startX, double startY, int fusesNeeded, boolean radarUnlocked) {
        this.x = startX;
        this.y = startY;
        this.fusesNeeded = fusesNeeded;
        this.radarUnlocked = radarUnlocked;
        this.radarCharges = radarUnlocked ? 1 : 0;
    }

    public Player(double startX, double startY, int fusesNeeded) {
        this(startX, startY, fusesNeeded, false);
    }

    public Player(double startX, double startY) {
        this(startX, startY, 3, false);
    }

    public double coneRangeLit() {
        double base = 230;
        return base * (0.35 + 0.65 * (battery / 100.0));
    }

    public double ambientRadius() {
        return 46;
    }
}
