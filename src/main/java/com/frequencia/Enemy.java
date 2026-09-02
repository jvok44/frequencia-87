package com.frequencia;

import java.util.Random;

/**
 * A Estática: só avança fora do cone da lanterna.
 * Comportamentos extras conforme a fase:
 *  - fase 4+: teleporte curto na escuridão
 *  - fase 5+: investida (lunge) após ficar congelada na luz
 *  - fase 6+: persegue a última posição conhecida do jogador
 *  - fase 7+: teleporte mais agressivo + rush mais longo
 */
public class Enemy {

    public double x, y;
    public final double radius = 16;
    private double speed = 55;
    private final int phaseIndex;

    public boolean active = false;
    public boolean spottedLastFrame = false;

    public boolean rushing = false;
    private double rushX, rushY;
    private double rushTimer = 0;

    /** Investida curta e violenta. */
    public boolean lunging = false;
    private double lungeTimer = 0;

    /** Última posição vista do jogador (stalk). */
    private double lastKnownX, lastKnownY;
    private boolean hasLastKnown = false;

    /** Cooldown de teleporte. */
    private double teleportCd = 0;
    /** Tempo que ficou iluminada (para lunge ao sair da luz). */
    private double litAcc = 0;

    /** Echo visual: posição fantasma após teleporte. */
    public double echoX, echoY;
    public double echoTimer = 0;

    private final Random random = new Random();
    private double wanderAngle = 0;
    private double wanderTimer = 0;

    public Enemy(double x, double y) {
        this(x, y, 0);
    }

    public Enemy(double x, double y, int phaseIndex) {
        this.x = x;
        this.y = y;
        this.phaseIndex = phaseIndex;
        this.speed = 55 * (1.0 + phaseIndex * 0.12);
        this.wanderAngle = random.nextDouble() * Math.PI * 2;
        this.lastKnownX = x;
        this.lastKnownY = y;
    }

    public void triggerRush(double targetX, double targetY) {
        active = true;
        rushing = true;
        lunging = false;
        rushX = targetX;
        rushY = targetY;
        // fases altas: rush mais longo
        rushTimer = 4.5 + Math.min(3.0, phaseIndex * 0.35);
    }

    public void update(double dt, GameMap map, double playerX, double playerY,
                       boolean illuminated, double difficulty) {
        if (!active) return;

        if (echoTimer > 0) echoTimer -= dt;
        if (teleportCd > 0) teleportCd -= dt;

        if (illuminated) {
            litAcc += dt;
            spottedLastFrame = true;
            // congela sob a lanterna
            return;
        }

        // Saiu da luz depois de ficar exposta → lunge (fase 5+)
        if (spottedLastFrame && phaseIndex >= 5 && litAcc > 0.6) {
            lunging = true;
            lungeTimer = 0.85 + random.nextDouble() * 0.4;
            litAcc = 0;
        }
        spottedLastFrame = false;
        litAcc = Math.max(0, litAcc - dt * 0.5);

        // Atualiza última posição conhecida se tem linha de visão
        if (map.hasLineOfSight(x, y, playerX, playerY)) {
            lastKnownX = playerX;
            lastKnownY = playerY;
            hasLastKnown = true;
        }

        wanderTimer -= dt;

        double moveAngle;
        double effSpeed = speed * difficulty;

        if (lunging) {
            lungeTimer -= dt;
            if (lungeTimer <= 0) {
                lunging = false;
            } else {
                moveAngle = Math.atan2(playerY - y, playerX - x);
                effSpeed = speed * difficulty * 3.4;
                step(map, moveAngle, effSpeed, dt);
                return;
            }
        }

        if (rushing) {
            rushTimer -= dt;
            double dx = rushX - x;
            double dy = rushY - y;
            double distRush = Math.hypot(dx, dy);
            if (distRush < 28 || rushTimer <= 0) {
                rushing = false;
            } else {
                moveAngle = Math.atan2(dy, dx);
                effSpeed = speed * difficulty * (2.6 + phaseIndex * 0.08);
                step(map, moveAngle, effSpeed, dt);
                return;
            }
        }

        // Teleporte na escuridão (fase 4+)
        if (phaseIndex >= 4 && teleportCd <= 0) {
            double dist = Math.hypot(playerX - x, playerY - y);
            double chance = phaseIndex >= 7 ? 0.018 : 0.01;
            if (dist > 160 && dist < 520 && random.nextDouble() < chance) {
                tryTeleport(map, playerX, playerY);
            }
        }

        double dx = playerX - x;
        double dy = playerY - y;
        double distToPlayer = Math.hypot(dx, dy);

        boolean hasSight = map.hasLineOfSight(x, y, playerX, playerY);
        if (hasSight && distToPlayer < 480) {
            moveAngle = Math.atan2(dy, dx);
        } else if (phaseIndex >= 6 && hasLastKnown) {
            // Stalk: vai até a última posição vista
            double lx = lastKnownX - x;
            double ly = lastKnownY - y;
            if (Math.hypot(lx, ly) > 24) {
                moveAngle = Math.atan2(ly, lx);
            } else {
                hasLastKnown = false;
                if (wanderTimer <= 0) {
                    wanderAngle = random.nextDouble() * Math.PI * 2;
                    wanderTimer = 1.2 + random.nextDouble() * 1.8;
                }
                moveAngle = wanderAngle;
            }
        } else {
            if (wanderTimer <= 0) {
                wanderAngle = random.nextDouble() * Math.PI * 2;
                wanderTimer = 1.5 + random.nextDouble() * 2.0;
            }
            moveAngle = wanderAngle;
        }

        step(map, moveAngle, effSpeed, dt);
    }

    private void step(GameMap map, double moveAngle, double effSpeed, double dt) {
        double nx = x + Math.cos(moveAngle) * effSpeed * dt;
        double ny = y + Math.sin(moveAngle) * effSpeed * dt;
        if (!map.collides(nx, y, radius)) x = nx;
        if (!map.collides(x, ny, radius)) y = ny;
    }

    /** Teleporta para um ponto entre a posição atual e o jogador. */
    private void tryTeleport(GameMap map, double playerX, double playerY) {
        echoX = x;
        echoY = y;
        echoTimer = 1.2;

        double angle = Math.atan2(playerY - y, playerX - x);
        // avança 90–180 px na direção do jogador, com leve desvio
        double dist = 90 + random.nextDouble() * 100;
        angle += (random.nextDouble() - 0.5) * 0.8;
        double tx = x + Math.cos(angle) * dist;
        double ty = y + Math.sin(angle) * dist;

        // se colide, tenta pontos menores
        for (int i = 0; i < 6; i++) {
            if (!map.collides(tx, ty, radius + 2)) {
                x = tx;
                y = ty;
                teleportCd = phaseIndex >= 7 ? 3.5 : 5.5;
                return;
            }
            dist *= 0.7;
            tx = x + Math.cos(angle) * dist;
            ty = y + Math.sin(angle) * dist;
        }
        teleportCd = 2.0; // falhou, cooldown curto
    }

    public double distanceTo(double px, double py) {
        return Math.hypot(px - x, py - y);
    }

    public int getPhaseIndex() {
        return phaseIndex;
    }
}
