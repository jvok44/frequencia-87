package com.frequencia;

import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

public class GameEngine {

    private enum State { MENU, INTRO, PLAYING, PAUSED, PHASE_CLEAR, WIN_FINAL, LOSE_CAUGHT, LOSE_SANITY }

    private static final int HUD_HEIGHT = 70;
    private static final double INTERACT_RADIUS = 46;
    private static final double HALF_CONE_ANGLE = Math.toRadians(28);

    private final Canvas canvas;
    private final GraphicsContext gc;
    private final Image spritePlayer;
    private final Image spriteEnemy;
    private final Image spriteIntro;

    private GameMap map;
    private Player player;
    private Enemy enemy;

    private State state = State.MENU;
    private final Set<KeyCode> keysPressed = EnumSet.noneOf(KeyCode.class);
    private double mouseX, mouseY;

    private long lastNanoTime = -1;
    private double elapsedSeconds = 0;
    private String message = "";
    private double messageTimer = 0;

    private String loreText = "";
    private double loreTimer = 0;
    private int totalFusesCollected = 0;

    private int currentPhase = 0; // 0-based (0 = fase 1)

    /** Radar (R): só após fase 3; consome cargas. */
    private boolean radarActive = false;
    private double radarDrainAcc = 0;
    private double proximityWarnTimer = 0;
    private int detectorLevel = 0; // 0–4 (fusível)
    private double enemyProximity = 0; // 0–1

    private final SoundFX sfx = new SoundFX();
    private final Random random = new Random();

    // Atmosfera / polish
    private double flashlightFlicker = 1.0;   // multiplicador do cone (0.4–1.0)
    private double screenShake = 0;          // pixels residual
    private double powerFlashTimer = 0;      // flash ao restaurar energia
    private double ambientPowerBoost = 0;    // luz ambiente extra com energia
    private double staticNoiseTimer = 0;     // para overlay de estática visual
    private boolean wasNearEnemy = false;
    private double teleportSfxCd = 0;

    // Meta / progresso da run
    private double totalPlayTime = 0;
    private double lowestSanity = 100;
    private int echoesHeard = 0;
    private int maxEchoes = Lore.LOG_FRAGMENTS.length;

    // Objetivo rádio (fases 7–8)
    private double radioTuneProgress = 0; // 0–1
    private boolean radioTuned = false;
    private static final double RADIO_TUNE_SECONDS = 4.5;

    // Tutorial fase 1
    private boolean tipMoveShown = false;
    private boolean tipFuseShown = false;
    private boolean tipBreakerShown = false;
    private boolean tipFlashShown = false;
    private double tutorialTimer = 0;

    // Save
    private static final String SAVE_FILE = "frequencia87_save.txt";
    private int savedPhase = 0;

    public GameEngine() {
        this.canvas = new Canvas(GameMap.PIXEL_WIDTH, GameMap.PIXEL_HEIGHT + HUD_HEIGHT);
        this.gc = canvas.getGraphicsContext2D();
        this.spritePlayer = loadSprite("/sprites/player.png");
        this.spriteEnemy = loadSprite("/sprites/enemy.png");
        this.spriteIntro = loadSprite("/sprites/intro_story.png");
        loadSave();
        resetGame();
    }

    private static Image loadSprite(String path) {
        try {
            var url = GameEngine.class.getResource(path);
            if (url == null) {
                System.err.println("Sprite não encontrado: " + path);
                return null;
            }
            return new Image(url.toExternalForm(), false);
        } catch (Exception e) {
            System.err.println("Falha ao carregar sprite " + path + ": " + e.getMessage());
            return null;
        }
    }

    public Canvas getCanvas() {
        return canvas;
    }

    private void resetGame() {
        currentPhase = 0;
        totalPlayTime = 0;
        lowestSanity = 100;
        echoesHeard = 0;
        tipMoveShown = tipFuseShown = tipBreakerShown = tipFlashShown = false;
        tutorialTimer = 0;
        startPhase(currentPhase);
    }

    private void continueFromSave() {
        currentPhase = Math.max(0, Math.min(savedPhase, GameMap.TOTAL_PHASES - 1));
        totalPlayTime = 0;
        lowestSanity = 100;
        // ecos já "ouvidos" até a fase salva
        echoesHeard = fusesCollectedBeforePhase(currentPhase);
        totalFusesCollected = echoesHeard;
        tipMoveShown = tipFuseShown = tipBreakerShown = tipFlashShown = true;
        startPhase(currentPhase);
    }

    private void startPhase(int phaseIndex) {
        map = new GameMap(phaseIndex);
        int needed = GameMap.fusesNeededForPhase(phaseIndex);
        boolean radarOn = phaseIndex >= 3; // desbloqueia após passar da fase 3
        player = new Player(map.playerStartX, map.playerStartY, needed, radarOn);
        enemy = new Enemy(map.breakerX, map.breakerY - GameMap.TILE * 1.5, phaseIndex);
        radarActive = false;
        proximityWarnTimer = 0;
        elapsedSeconds = 0;
        message = "";
        messageTimer = 0;
        loreText = "";
        loreTimer = 0;
        flashlightFlicker = 1.0;
        screenShake = 0;
        powerFlashTimer = 0;
        ambientPowerBoost = 0;
        staticNoiseTimer = 0;
        wasNearEnemy = false;
        radioTuneProgress = 0;
        radioTuned = !map.requiresRadioTune; // se não precisa, já está "ok"
        // volta a contagem de fusíveis "totais" para o ponto onde essa fase começa,
        // assim os ecos de história sempre aparecem na ordem certa, mesmo reiniciando
        totalFusesCollected = fusesCollectedBeforePhase(phaseIndex);
    }

    /** Soma de fusíveis necessários em todas as fases anteriores a phaseIndex. */
    private int fusesCollectedBeforePhase(int phaseIndex) {
        int sum = 0;
        for (int i = 0; i < phaseIndex; i++) {
            sum += GameMap.fusesNeededForPhase(i);
        }
        return sum;
    }

    public void attachInput(Scene scene) {
        scene.setOnKeyPressed(e -> {
            KeyCode code = e.getCode();
            keysPressed.add(code);
            switch (code) {
                case ENTER -> {
                    if (state == State.MENU) {
                        resetGame();
                        state = State.INTRO;
                    } else if (state == State.INTRO) {
                        state = State.PLAYING;
                        showMessage("Torre em silêncio. Encontre os fusíveis.");
                    } else if (state == State.WIN_FINAL) {
                        resetGame();
                        state = State.INTRO;
                    } else if (state == State.LOSE_CAUGHT || state == State.LOSE_SANITY) {
                        startPhase(currentPhase);
                        state = State.PLAYING;
                        showMessage("Checkpoint — Fase " + (currentPhase + 1));
                    } else if (state == State.PHASE_CLEAR) {
                        currentPhase++;
                        if (currentPhase >= GameMap.TOTAL_PHASES) {
                            state = State.WIN_FINAL;
                        } else {
                            startPhase(currentPhase);
                            state = State.PLAYING;
                            showMessage("Fase " + (currentPhase + 1) + " — " + player.fusesNeeded + " fusíveis necessários");
                        }
                    }
                }
                case F -> {
                    if (state == State.PLAYING && player.battery > 0) {
                        player.flashlightOn = !player.flashlightOn;
                    }
                }
                case R -> {
                    if (state == State.PLAYING) tryToggleRadar();
                }
                case SPACE -> {
                    if (state == State.PLAYING) handleInteract();
                }
                case ESCAPE, P -> {
                    if (state == State.PLAYING) {
                        state = State.PAUSED;
                        showMessage("PAUSADO — ESC/P continuar · M mudo");
                    } else if (state == State.PAUSED) {
                        state = State.PLAYING;
                        lastNanoTime = -1; // evita salto de dt
                        showMessage("Retomando transmissão…");
                    }
                }
                case M -> {
                    if (state == State.PLAYING || state == State.PAUSED || state == State.MENU) {
                        boolean on = !sfx.isEnabled();
                        sfx.setEnabled(on);
                        showMessage(on ? "Áudio ligado" : "Áudio mudo");
                    }
                }
                case C -> {
                    if (state == State.MENU && savedPhase > 0) {
                        continueFromSave();
                        state = State.PLAYING;
                        showMessage("Continuando da fase " + (currentPhase + 1));
                    }
                }
                default -> { /* movimento tratado continuamente em update() */ }
            }
        });
        scene.setOnKeyReleased(e -> keysPressed.remove(e.getCode()));
        scene.setOnMouseMoved(e -> { mouseX = e.getX(); mouseY = e.getY(); });
        scene.setOnMouseDragged(e -> { mouseX = e.getX(); mouseY = e.getY(); });
    }

    public void start() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanoTime < 0) lastNanoTime = now;
                double dt = Math.min((now - lastNanoTime) / 1_000_000_000.0, 0.05);
                lastNanoTime = now;

                if (state == State.PLAYING) {
                    update(dt);
                }
                render();
            }
        };
        timer.start();
    }

    // ------------------------------------------------------------------
    // UPDATE
    // ------------------------------------------------------------------

    private void update(double dt) {
        elapsedSeconds += dt;

        double dx = 0, dy = 0;
        if (keysPressed.contains(KeyCode.W) || keysPressed.contains(KeyCode.UP)) dy -= 1;
        if (keysPressed.contains(KeyCode.S) || keysPressed.contains(KeyCode.DOWN)) dy += 1;
        if (keysPressed.contains(KeyCode.A) || keysPressed.contains(KeyCode.LEFT)) dx -= 1;
        if (keysPressed.contains(KeyCode.D) || keysPressed.contains(KeyCode.RIGHT)) dx += 1;

        if (dx != 0 || dy != 0) {
            double len = Math.hypot(dx, dy);
            dx /= len;
            dy /= len;
            double nx = player.x + dx * player.speed * dt;
            if (!map.collides(nx, player.y, player.radius)) player.x = nx;
            double ny = player.y + dy * player.speed * dt;
            if (!map.collides(player.x, ny, player.radius)) player.y = ny;
        }

        player.angle = Math.atan2(mouseY - player.y, mouseX - player.x);

        if (player.flashlightOn) {
            player.battery -= 3.2 * dt;
            if (player.battery <= 0) {
                player.battery = 0;
                player.flashlightOn = false;
            }
            // Flicker quando a bateria está crítica
            if (player.battery < 28) {
                double severity = 1.0 - (player.battery / 28.0);
                if (random.nextDouble() < 0.08 + severity * 0.35) {
                    flashlightFlicker = 0.25 + random.nextDouble() * 0.35;
                } else {
                    flashlightFlicker = Math.min(1.0, flashlightFlicker + dt * 4.0);
                }
            } else {
                flashlightFlicker = 1.0;
            }
        } else {
            flashlightFlicker = 1.0;
        }

        // Decai do flash de energia e boost ambiente
        if (powerFlashTimer > 0) powerFlashTimer -= dt;
        if (player.powerRestored) {
            ambientPowerBoost = 0.18 + 0.04 * Math.sin(elapsedSeconds * 2.4);
        } else {
            ambientPowerBoost = Math.max(0, ambientPowerBoost - dt * 0.15);
        }

        // Screen shake perto da Estática / rush
        double distShake = enemy.active ? enemy.distanceTo(player.x, player.y) : 9999;
        if (enemy.active && (enemy.rushing || distShake < 140)) {
            double intensity = enemy.rushing ? 3.5 : Math.max(0, (140 - distShake) / 140.0 * 2.8);
            screenShake = intensity;
            if (!wasNearEnemy && distShake < 160) {
                sfx.playWarning();
                wasNearEnemy = true;
            }
        } else {
            screenShake = Math.max(0, screenShake - dt * 8);
            if (distShake > 220) wasNearEnemy = false;
        }

        staticNoiseTimer += dt;

        for (Pickup p : map.pickups) {
            if (!p.collected && Math.hypot(p.x - player.x, p.y - player.y) < 28) {
                p.collected = true;
                if (p.type == Pickup.Type.FUSE) {
                    player.fuses++;
                    if (totalFusesCollected < Lore.LOG_FRAGMENTS.length) {
                        showLore(Lore.LOG_FRAGMENTS[totalFusesCollected]);
                    }
                    totalFusesCollected++;
                    if (totalFusesCollected > echoesHeard) echoesHeard = totalFusesCollected;
                    // A Estática sente o eco e corre até o local do fusível
                    enemy.triggerRush(p.x, p.y);
                    sfx.playPickup();
                    showMessage("Fusível (" + player.fuses + "/" + player.fusesNeeded + ") — Estática em rush!");
                } else if (p.type == Pickup.Type.RADAR_CELL) {
                    if (player.radarCharges < Player.RADAR_CHARGES_MAX) {
                        player.radarCharges++;
                        sfx.playPickup();
                    showMessage("Célula de radar (" + player.radarCharges + "/" + Player.RADAR_CHARGES_MAX + ")");
                    } else {
                        showMessage("Radar já está com carga máxima.");
                    }
                } else {
                    player.battery = Math.min(100, player.battery + 45);
                    sfx.playPickup();
                    showMessage("Bateria recarregada.");
                }
            }
        }

        if (!enemy.active && player.fuses >= 1) {
            enemy.active = true;
            showMessage("A frequência muda. Você não está mais sozinho.");
        }

        // consome carga do radar enquanto ativo
        if (radarActive) {
            if (!player.radarUnlocked || player.radarCharges <= 0 || !enemy.active) {
                radarActive = false;
            } else {
                radarDrainAcc += dt;
                // ~8 segundos por carga
                if (radarDrainAcc >= 8.0) {
                    radarDrainAcc = 0;
                    player.radarCharges = Math.max(0, player.radarCharges - 1);
                    if (player.radarCharges <= 0) {
                        radarActive = false;
                        showMessage("Radar sem carga.");
                    }
                }
            }
        }

        // aviso sonoro/visual de proximidade
        double distEarly = enemy.active ? enemy.distanceTo(player.x, player.y) : 9999;
        if (enemy.active && distEarly < 200) {
            proximityWarnTimer = 0.35;
        }
        if (proximityWarnTimer > 0) proximityWarnTimer -= dt;

        // Detector de frequência (item inicial): 4 níveis por fusível + Estática
        updateDetectorSensors();
        sfx.updateDetector(detectorLevel, enemyProximity, enemy.active, dt);
        sfx.updateRadar(radarActive, dt);
        sfx.updateAmbient(player.sanity / 100.0, enemyProximity, enemy.active, currentPhase, dt);

        double enemyLight = lightAt(enemy.x, enemy.y);
        boolean illuminated = enemy.active && player.flashlightOn && enemyLight > 0.22
                && withinCone(enemy.x, enemy.y);
        // dificuldade sobe um pouco com o tempo e também com a fase
        double difficulty = Math.min(2.2, 1.0 + elapsedSeconds / 90.0 + currentPhase * 0.08);
        enemy.update(dt, map, player.x, player.y, illuminated, difficulty);

        double sanityDrain = 0.5;
        if (!player.flashlightOn) sanityDrain += 3.0;
        double distToEnemy = enemy.distanceTo(player.x, player.y);
        if (enemy.active && distToEnemy < 260 && !illuminated) {
            sanityDrain += (260 - distToEnemy) / 260.0 * 9.0;
        }
        // Zonas de drenagem no mapa
        sanityDrain *= map.sanityDrainMultiplier(player.x, player.y);
        player.sanity -= sanityDrain * dt;
        if (player.flashlightOn && (!enemy.active || distToEnemy > 300)) {
            player.sanity += 2.2 * dt;
        }
        player.sanity = clamp(player.sanity, 0, 100);
        if (player.sanity < lowestSanity) lowestSanity = player.sanity;

        totalPlayTime += dt;

        // Sintonizar rádio (segurar perto do console após energia)
        if (map.requiresRadioTune && player.powerRestored && !radioTuned) {
            double rd = Math.hypot(player.x - map.radioX, player.y - map.radioY);
            boolean holding = rd < INTERACT_RADIUS + 10
                    && (keysPressed.contains(KeyCode.SPACE));
            if (holding) {
                radioTuneProgress += dt / RADIO_TUNE_SECONDS;
                if (radioTuneProgress >= 1.0) {
                    radioTuneProgress = 1.0;
                    radioTuned = true;
                    sfx.playRadioTune();
                    showMessage("Frequência 87 sintonizada. A saída responde.");
                }
            } else if (radioTuneProgress > 0 && radioTuneProgress < 1) {
                radioTuneProgress = Math.max(0, radioTuneProgress - dt * 0.25);
            }
        }

        // Tutorial fase 1
        if (currentPhase == 0) {
            tutorialTimer += dt;
            if (!tipMoveShown && tutorialTimer > 1.2) {
                tipMoveShown = true;
                showMessage("WASD para se mover · Mouse mira a lanterna");
            }
            if (!tipFlashShown && tutorialTimer > 5.0) {
                tipFlashShown = true;
                showMessage("F liga/desliga a lanterna — a Estática só anda no escuro");
            }
            if (!tipFuseShown && player.fuses == 0 && tutorialTimer > 9.0) {
                tipFuseShown = true;
                showMessage("Procure fusíveis laranja · o detector no canto ajuda");
            }
            if (!tipBreakerShown && player.fuses >= player.fusesNeeded && !player.powerRestored) {
                tipBreakerShown = true;
                showMessage("Leve os fusíveis ao quadro de energia (dourado) e pressione ESPAÇO");
            }
        }

        // Teleporte da Estática — feedback sonoro no início do eco
        if (teleportSfxCd > 0) teleportSfxCd -= dt;
        if (enemy.echoTimer > 1.15 && teleportSfxCd <= 0) {
            sfx.playTeleport();
            teleportSfxCd = 1.0;
        }

        if (enemy.active && distToEnemy <= player.radius + enemy.radius - 2) {
            sfx.playCaught();
            state = State.LOSE_CAUGHT;
        }
        if (player.sanity <= 0) {
            sfx.playSanityLoss();
            state = State.LOSE_SANITY;
        }

        if (messageTimer > 0) {
            messageTimer -= dt;
        }
        if (loreTimer > 0) {
            loreTimer -= dt;
        }
    }

    private void handleInteract() {
        double distBreaker = Math.hypot(player.x - map.breakerX, player.y - map.breakerY);
        double distExit = Math.hypot(player.x - map.exitX, player.y - map.exitY);

        if (distBreaker < INTERACT_RADIUS) {
            if (player.fuses >= player.fusesNeeded && !player.powerRestored) {
                player.powerRestored = true;
                powerFlashTimer = 1.4;
                ambientPowerBoost = 0.22;
                sfx.playPowerRestore();
                showMessage("Energia restaurada! A saída foi destravada.");
            } else if (!player.powerRestored) {
                showMessage("Faltam fusíveis: " + player.fuses + "/" + player.fusesNeeded);
            }
        } else if (distExit < INTERACT_RADIUS) {
            if (player.powerRestored) {
                if (map.requiresRadioTune && !radioTuned) {
                    showMessage("Sintonize o rádio no console (segure ESPAÇO) antes de sair.");
                } else if (currentPhase >= GameMap.TOTAL_PHASES - 1) {
                    sfx.playWin();
                    saveProgress(0); // run completa
                    state = State.WIN_FINAL;
                } else {
                    sfx.playPhaseClear();
                    saveProgress(currentPhase + 1);
                    state = State.PHASE_CLEAR;
                }
            } else {
                showMessage("A porta está trancada. Sem energia.");
            }
        } else if (map.requiresRadioTune && player.powerRestored && !radioTuned
                && Math.hypot(player.x - map.radioX, player.y - map.radioY) < INTERACT_RADIUS + 10) {
            showMessage("Segure ESPAÇO para sintonizar a frequência 87…");
        }
    }

    private void showMessage(String text) {
        message = text;
        messageTimer = 3.5;
    }

    private void showLore(String text) {
        loreText = text;
        loreTimer = 11.0;
        sfx.playLoreEcho();
    }

    private void saveProgress(int phaseToSave) {
        savedPhase = phaseToSave;
        try {
            Path p = Path.of(SAVE_FILE);
            Files.writeString(p, Integer.toString(phaseToSave), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private void loadSave() {
        try {
            Path p = Path.of(SAVE_FILE);
            if (Files.isRegularFile(p)) {
                String s = Files.readString(p, StandardCharsets.UTF_8).trim();
                savedPhase = clampInt(Integer.parseInt(s), 0, GameMap.TOTAL_PHASES - 1);
            }
        } catch (Exception e) {
            savedPhase = 0;
        }
    }

    private int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }


    /** Calcula nível do detector (fusíveis) e proximidade da Estática (0–1). */
    private void updateDetectorSensors() {
        // Fusível mais próximo
        double nearestFuse = Double.MAX_VALUE;
        for (Pickup p : map.pickups) {
            if (p.collected || p.type != Pickup.Type.FUSE) continue;
            nearestFuse = Math.min(nearestFuse, Math.hypot(p.x - player.x, p.y - player.y));
        }
        if (nearestFuse == Double.MAX_VALUE) {
            detectorLevel = 0;
        } else if (nearestFuse < 40) {
            detectorLevel = 4;
        } else if (nearestFuse < 90) {
            detectorLevel = 3;
        } else if (nearestFuse < 160) {
            detectorLevel = 2;
        } else if (nearestFuse < 260) {
            detectorLevel = 1;
        } else {
            detectorLevel = 0;
        }

        // Estática: 0 longe, 1 colado — alimenta o tom fino/alto
        if (!enemy.active) {
            enemyProximity = 0;
        } else {
            double d = enemy.distanceTo(player.x, player.y);
            // começa a reagir cedo (~420px), máximo perto (~40px)
            enemyProximity = clamp(1.0 - (d - 40) / 380.0, 0, 1);
        }
    }

    private void tryToggleRadar() {
        if (!player.radarUnlocked) {
            showMessage("Radar bloqueado — complete a fase 3.");
            return;
        }
        if (player.radarCharges <= 0) {
            showMessage("Sem células de radar. Procure no mapa.");
            return;
        }
        if (!enemy.active) {
            showMessage("Nenhum sinal hostil no ar… ainda.");
            return;
        }
        radarActive = !radarActive;
        if (radarActive) {
            sfx.playRadarOn();
            showMessage("Radar ativo (R para desligar)");
        } else {
            sfx.playRadarOff();
            showMessage("Radar desligado");
        }
    }

    // ------------------------------------------------------------------
    // ILUMINAÇÃO
    // ------------------------------------------------------------------

    private double normalizeAngle(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    private boolean withinCone(double x, double y) {
        double dist = Math.hypot(x - player.x, y - player.y);
        if (dist > player.coneRangeLit() * flashlightFlicker) return false;
        double angleTo = Math.atan2(y - player.y, x - player.x);
        double diff = Math.abs(normalizeAngle(angleTo - player.angle));
        return diff <= HALF_CONE_ANGLE && map.hasLineOfSight(player.x, player.y, x, y);
    }

    /** Retorna quanto de luz (0 a 1) atinge o ponto (x,y): iluminação ambiente + cone da lanterna. */
    private double lightAt(double x, double y) {
        double distAmbient = Math.hypot(x - player.x, y - player.y);
        double ambientR = player.ambientRadius();
        double lAmbient = clamp(1 - distAmbient / ambientR, 0, 1);

        // Com energia restaurada, a torre ganha um pouco de luz residual (pisca levemente)
        if (ambientPowerBoost > 0) {
            lAmbient = Math.max(lAmbient, ambientPowerBoost * (0.85 + 0.15 * Math.sin(elapsedSeconds * 5 + x * 0.01)));
        }

        double lCone = 0;
        if (player.flashlightOn) {
            double coneRange = player.coneRangeLit() * flashlightFlicker;
            double dist = distAmbient;
            if (dist <= coneRange && map.hasLineOfSight(player.x, player.y, x, y)) {
                double angleTo = Math.atan2(y - player.y, x - player.x);
                double diff = Math.abs(normalizeAngle(angleTo - player.angle));
                if (diff <= HALF_CONE_ANGLE) {
                    double angFactor = 1 - (diff / HALF_CONE_ANGLE) * 0.55;
                    double distFactor = 1 - dist / coneRange;
                    lCone = clamp(angFactor * distFactor * flashlightFlicker, 0, 1);
                }
            }
        }
        double light = Math.max(lAmbient, lCone);
        // Flash branco breve ao religar o quadro
        if (powerFlashTimer > 0) {
            light = Math.min(1.0, light + powerFlashTimer * 0.55);
        }
        return light;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------------
    // RENDER
    // ------------------------------------------------------------------

    private void render() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        switch (state) {
            case MENU -> renderMenu();
            case INTRO -> renderIntro();
            case PLAYING -> {
                renderGame();
                renderAtmosphereOverlays();
                renderLoreOverlay();
                renderHud();
            }
            case PAUSED -> {
                renderGame();
                renderAtmosphereOverlays();
                renderHud();
                renderPauseOverlay();
            }
            case PHASE_CLEAR -> renderPhaseClear();
            case WIN_FINAL -> renderWinFinal();
            case LOSE_CAUGHT -> renderEndScreen("ESTÁTICA", Lore.LOSE_CAUGHT_LINES, Color.CRIMSON);
            case LOSE_SANITY -> renderEndScreen("SEM SINAL", Lore.LOSE_SANITY_LINES, Color.CRIMSON);
        }
    }

    /** Mostra, por alguns segundos, o eco de história revelado ao pegar um fusível. */
    private void renderLoreOverlay() {
        if (loreTimer <= 0) return;
        double alpha = clamp(loreTimer / 11.0, 0, 1) * 0.95 + 0.05;
        double cx = canvas.getWidth() / 2;
        double boxH = 52;

        gc.setFill(Color.color(0.02, 0.02, 0.04, 0.72 * alpha));
        gc.fillRoundRect(cx - 450, 10, 900, boxH, 6, 6);
        gc.setStroke(Color.color(0.55, 0.45, 0.2, 0.55 * alpha));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(cx - 450, 10, 900, boxH, 6, 6);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.color(0.95, 0.88, 0.55, alpha));
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        // quebra simples se texto longo
        if (loreText.length() > 95) {
            int mid = loreText.lastIndexOf(' ', 95);
            if (mid < 40) mid = 95;
            gc.fillText(loreText.substring(0, mid).trim(), cx, 30);
            gc.fillText(loreText.substring(mid).trim(), cx, 48);
        } else {
            gc.fillText(loreText, cx, 40);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** Overlay de pause. */
    private void renderPauseOverlay() {
        gc.setFill(Color.color(0, 0, 0, 0.62));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setTextAlign(TextAlignment.CENTER);
        double cx = canvas.getWidth() / 2;
        double cy = canvas.getHeight() / 2;
        gc.setFill(Color.ORANGE);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 36));
        gc.fillText("PAUSADO", cx, cy - 20);
        gc.setFill(Color.WHEAT);
        gc.setFont(Font.font("Consolas", 15));
        gc.fillText("ESC ou P — continuar   ·   F — lanterna   ·   R — radar", cx, cy + 18);
        gc.setFill(Color.color(0.7, 0.7, 0.65));
        gc.setFont(Font.font("Consolas", 12));
        gc.fillText("A Estática também espera. Não por muito tempo.", cx, cy + 48);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Efeitos de atmosfera: vinheta de sanidade, noise de estática, shake, flash de energia.
     */
    private void renderAtmosphereOverlays() {
        double w = GameMap.PIXEL_WIDTH;
        double h = GameMap.PIXEL_HEIGHT;

        // Screen shake (desloca conteúdo já desenhado via overlay displacement visual nas bordas)
        if (screenShake > 0.4) {
            double sx = (random.nextDouble() - 0.5) * screenShake * 2;
            double sy = (random.nextDouble() - 0.5) * screenShake * 2;
            gc.setStroke(Color.color(0.15, 0, 0, 0.35));
            gc.setLineWidth(2);
            gc.strokeRect(sx, sy, w, h);
        }

        // Vinheta / borda quando sanidade baixa
        double sanityFrac = player != null ? player.sanity / 100.0 : 1;
        if (sanityFrac < 0.55) {
            double strength = (0.55 - sanityFrac) / 0.55;
            // vinheta radial simulada com retângulos nas bordas
            double a = 0.15 + strength * 0.55;
            gc.setFill(Color.color(0.05, 0, 0.08, a));
            double band = 40 + strength * 70;
            gc.fillRect(0, 0, w, band);
            gc.fillRect(0, h - band, w, band);
            gc.fillRect(0, 0, band, h);
            gc.fillRect(w - band, 0, band, h);

            // ruído de "estática de TV" — pontos aleatórios
            int dots = (int) (40 + strength * 180);
            for (int i = 0; i < dots; i++) {
                double nx = random.nextDouble() * w;
                double ny = random.nextDouble() * h;
                double na = 0.08 + random.nextDouble() * 0.25 * strength;
                gc.setFill(Color.color(0.9, 0.9, 0.95, na));
                gc.fillRect(nx, ny, 2, 1 + random.nextInt(3));
            }

            // linhas horizontais de interferência
            if (strength > 0.4) {
                int lines = (int) (2 + strength * 6);
                for (int i = 0; i < lines; i++) {
                    double ly = random.nextDouble() * h;
                    gc.setStroke(Color.color(0.7, 0.75, 1, 0.06 + strength * 0.08));
                    gc.setLineWidth(1);
                    gc.strokeLine(0, ly, w, ly);
                }
            }
        }

        // Flash branco ao religar energia
        if (powerFlashTimer > 0) {
            gc.setFill(Color.color(1, 0.95, 0.8, clamp(powerFlashTimer * 0.45, 0, 0.55)));
            gc.fillRect(0, 0, w, h);
        }

        // Tint vermelho pulsante muito perto da Estática
        if (enemy != null && enemy.active && enemyProximity > 0.7) {
            double a = (enemyProximity - 0.7) / 0.3 * 0.22;
            gc.setFill(Color.color(0.6, 0.0, 0.0, a));
            gc.fillRect(0, 0, w, h);
        }
    }

    private void renderGame() {
        char[][] grid = map.getGrid();
        for (int row = 0; row < GameMap.ROWS; row++) {
            for (int col = 0; col < GameMap.COLS; col++) {
                double cx = col * GameMap.TILE + GameMap.TILE / 2.0;
                double cy = row * GameMap.TILE + GameMap.TILE / 2.0;
                double l = lightAt(cx, cy);
                boolean wall = grid[row][col] == '#';
                // ruído bem sutil (só pra não ficar chapado)
                int h = (col * 374761 + row * 668265) & 255;
                double noise = (h / 255.0) * 0.025;

                if (wall) {
                    // PAREDE: bem mais clara e azulada — contraste forte com o chão
                    double base = 0.22 + 0.42 * l + noise;
                    double r = clamp(base * 0.72, 0, 1);
                    double g = clamp(base * 0.78, 0, 1);
                    double b = clamp(base * 0.95, 0, 1);
                    gc.setFill(Color.color(r, g, b));
                    gc.fillRect(col * GameMap.TILE, row * GameMap.TILE, GameMap.TILE, GameMap.TILE);

                    // contorno escuro nas faces que tocam o chão (silhueta legível)
                    if (l > 0.05) {
                        gc.setStroke(Color.color(0.05, 0.06, 0.08, clamp(0.35 + l * 0.4, 0.35, 0.85)));
                        gc.setLineWidth(2);
                        // só desenha borda se o vizinho for chão
                        int T = GameMap.TILE;
                        if (col + 1 < GameMap.COLS && grid[row][col + 1] != '#')
                            gc.strokeLine((col + 1) * T - 1, row * T, (col + 1) * T - 1, (row + 1) * T);
                        if (col - 1 >= 0 && grid[row][col - 1] != '#')
                            gc.strokeLine(col * T + 1, row * T, col * T + 1, (row + 1) * T);
                        if (row + 1 < GameMap.ROWS && grid[row + 1][col] != '#')
                            gc.strokeLine(col * T, (row + 1) * T - 1, (col + 1) * T, (row + 1) * T - 1);
                        if (row - 1 >= 0 && grid[row - 1][col] != '#')
                            gc.strokeLine(col * T, row * T + 1, (col + 1) * T, row * T + 1);
                    }
                } else {
                    // CHÃO: bem mais escuro e quente — nunca compete com parede
                    double base = 0.025 + 0.10 * l + noise * 0.4;
                    double r = clamp(base * 1.15, 0, 1);
                    double g = clamp(base * 0.95, 0, 1);
                    double b = clamp(base * 0.75, 0, 1);
                    gc.setFill(Color.color(r, g, b));
                    gc.fillRect(col * GameMap.TILE, row * GameMap.TILE, GameMap.TILE, GameMap.TILE);
                }
            }
        }

        // Zonas de sanidade (só visíveis com luz)
        for (double[] z : map.sanityZones) {
            double l = lightAt(z[0], z[1]);
            if (l < 0.08) continue;
            double pulse = 0.35 + 0.25 * Math.sin(elapsedSeconds * 2.5 + z[0]);
            gc.setFill(Color.color(0.35, 0.05, 0.45, 0.12 * l * pulse));
            gc.fillOval(z[0] - 28, z[1] - 28, 56, 56);
            gc.setStroke(Color.color(0.55, 0.2, 0.7, 0.25 * l));
            gc.setLineWidth(1);
            gc.strokeOval(z[0] - 28, z[1] - 28, 56, 56);
        }

        // Cabos no chão (props)
        drawFloorCables();

        // Porta de saída — moldura + luz de emergência
        double exitPulse = 0.4 + 0.25 * Math.sin(elapsedSeconds * 3.0);
        double el = Math.max(0.15, lightAt(map.exitX, map.exitY));
        gc.setStroke(Color.color(0.6 * exitPulse, 0.08, 0.08, clamp(el + 0.3, 0.3, 1)));
        gc.setLineWidth(3);
        gc.strokeRect(map.exitX - 16, map.exitY - 22, 32, 44);
        gc.setFill(Color.color(0.5 * exitPulse, 0.05, 0.05, 0.7));
        gc.fillOval(map.exitX - 8, map.exitY - 8, 16, 16);
        if (player.powerRestored && (!map.requiresRadioTune || radioTuned)) {
            gc.setFill(Color.color(0.2, 0.9, 0.3, 0.5 + 0.3 * exitPulse));
            gc.fillOval(map.exitX - 5, map.exitY - 5, 10, 10);
        }
        gc.setFill(Color.color(0.9, 0.7, 0.7, clamp(el, 0.3, 0.9)));
        gc.setFont(Font.font("Consolas", 9));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("SAÍDA", map.exitX, map.exitY + 28);
        gc.setTextAlign(TextAlignment.LEFT);

        // computadores na parede do spawn
        drawSpawnComputers();

        // quadro de energia
        double breakerLight = lightAt(map.breakerX, map.breakerY);
        if (breakerLight > 0.08) {
            Color c = player.powerRestored ? Color.LIMEGREEN : Color.GOLD;
            double a = clamp(breakerLight, 0.25, 1);
            gc.setFill(Color.color(0.1, 0.1, 0.12, a));
            gc.fillRect(map.breakerX - 14, map.breakerY - 18, 28, 36);
            gc.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), a));
            gc.fillRect(map.breakerX - 10, map.breakerY - 12, 20, 24);
            gc.setStroke(Color.color(1, 1, 1, 0.25 * a));
            gc.strokeRect(map.breakerX - 14, map.breakerY - 18, 28, 36);
            gc.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), a * 0.9));
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 9));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(player.powerRestored ? "ON" : "PWR", map.breakerX, map.breakerY + 28);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        // Console de rádio (fases especiais)
        if (map.requiresRadioTune && map.radioX > 0) {
            double rl = Math.max(0.12, lightAt(map.radioX, map.radioY));
            gc.setFill(Color.color(0.08, 0.1, 0.12, clamp(rl, 0.3, 1)));
            gc.fillRoundRect(map.radioX - 18, map.radioY - 12, 36, 28, 4, 4);
            double glow = radioTuned ? 0.9 : (0.4 + 0.4 * radioTuneProgress);
            gc.setFill(Color.color(0.2, 0.85 * glow, 0.4, clamp(rl, 0.4, 1)));
            gc.fillRect(map.radioX - 12, map.radioY - 6, 24, 12);
            if (!radioTuned && player.powerRestored) {
                gc.setFill(Color.color(0.9, 0.85, 0.4, 0.7 + 0.3 * Math.sin(elapsedSeconds * 4)));
                gc.setFont(Font.font("Consolas", 10));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText("RÁDIO " + (int) (radioTuneProgress * 100) + "%", map.radioX, map.radioY + 28);
                gc.setTextAlign(TextAlignment.LEFT);
            } else if (radioTuned) {
                gc.setFill(Color.LIMEGREEN);
                gc.setFont(Font.font("Consolas", 9));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText("87.0 MHz", map.radioX, map.radioY + 28);
                gc.setTextAlign(TextAlignment.LEFT);
            }
        }

        for (Pickup p : map.pickups) {
            if (p.collected) continue;
            double l = lightAt(p.x, p.y);
            if (l < 0.12) continue;
            Color base = switch (p.type) {
                case FUSE -> Color.ORANGE;
                case RADAR_CELL -> Color.LIMEGREEN;
                default -> Color.SKYBLUE;
            };
            gc.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), clamp(l, 0.3, 1)));
            gc.fillOval(p.x - 9, p.y - 9, 18, 18);
        }

        // inimigo — sprite da Estática em pé (só com luz suficiente)
        double enemyLight = lightAt(enemy.x, enemy.y);
        if (enemy.active && enemyLight > 0.1) {
            double a = clamp(enemyLight + 0.15, 0.2, 1);
            gc.setGlobalAlpha(a);
            if (spriteEnemy != null) {
                double ew = 52;
                double eh = 70;
                double gx = (random.nextDouble() - 0.5) * 2;
                // em pé, ancorado pelos pés (sem rotação)
                gc.drawImage(spriteEnemy, enemy.x - ew / 2 + gx, enemy.y - eh + 8, ew, eh);
                if (enemy.rushing) {
                    gc.setGlobalAlpha(a * 0.4);
                    gc.drawImage(spriteEnemy, enemy.x - ew / 2 - gx * 2, enemy.y - eh + 8, ew, eh);
                }
            } else {
                gc.setFill(Color.color(0.35, 0.02, 0.02, 1));
                gc.fillOval(enemy.x - enemy.radius, enemy.y - enemy.radius * 1.4, enemy.radius * 2, enemy.radius * 2.8);
            }
            gc.setGlobalAlpha(1.0);
            gc.setStroke(Color.color(1, 1, 1, a * 0.35));
            for (int i = 0; i < 3; i++) {
                double ox = (random.nextDouble() - 0.5) * 20;
                double oy = enemy.y - 40 + random.nextDouble() * 50;
                gc.strokeLine(enemy.x - 18 + ox, oy, enemy.x + 18 + ox, oy);
            }
            if (enemy.lunging) {
                gc.setStroke(Color.color(1, 0.2, 0.1, a * 0.6));
                gc.setLineWidth(2);
                gc.strokeOval(enemy.x - 22, enemy.y - 50, 44, 60);
            }
        }

        // Eco residual após teleporte
        if (enemy.active && enemy.echoTimer > 0) {
            double ea = clamp(enemy.echoTimer / 1.2, 0, 1) * 0.45;
            gc.setGlobalAlpha(ea);
            if (spriteEnemy != null) {
                gc.drawImage(spriteEnemy, enemy.echoX - 26, enemy.echoY - 62, 52, 70);
            } else {
                gc.setFill(Color.color(0.4, 0.05, 0.05));
                gc.fillOval(enemy.echoX - 14, enemy.echoY - 20, 28, 40);
            }
            gc.setGlobalAlpha(1.0);
            gc.setStroke(Color.color(0.8, 0.2, 1, ea));
            gc.setLineWidth(1);
            for (int i = 0; i < 4; i++) {
                double oy = enemy.echoY - 35 + i * 12;
                gc.strokeLine(enemy.echoX - 16, oy, enemy.echoX + 16, oy);
            }
        }

        // jogador — sprite em pé (sem deitar com a mira); só a lanterna aponta
        if (spritePlayer != null) {
            double pw = 48;
            double ph = 48;
            gc.drawImage(spritePlayer, player.x - pw / 2, player.y - ph + 10, pw, ph);
        } else {
            gc.setFill(Color.WHEAT);
            gc.fillOval(player.x - player.radius, player.y - player.radius, player.radius * 2, player.radius * 2);
        }
        // direção da lanterna (linha na mira do mouse)
        if (player.flashlightOn) {
            gc.setStroke(Color.color(1, 0.95, 0.7, 0.9));
            gc.setLineWidth(2.5);
            gc.strokeLine(player.x, player.y - 6,
                    player.x + Math.cos(player.angle) * (player.radius + 18),
                    player.y - 6 + Math.sin(player.angle) * (player.radius + 18));
        }

        // vinheta de aviso quando a Estática está perto
        if (enemy.active && proximityWarnTimer > 0) {
            double a = clamp(proximityWarnTimer / 0.35, 0, 1) * 0.45;
            gc.setStroke(Color.color(0.9, 0.05, 0.05, a));
            gc.setLineWidth(18);
            gc.strokeRect(9, 9, GameMap.PIXEL_WIDTH - 18, GameMap.PIXEL_HEIGHT - 18);
            gc.setFill(Color.color(1, 0.15, 0.1, a * 0.9));
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 22));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("⚠ SINAL PRÓXIMO", GameMap.PIXEL_WIDTH / 2.0, 48);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        if (radarActive) {
            drawRadar();
        }

        drawDetectorWidget();
    }

    /** Medidor estilo Geiger no canto — 4 barras de frequência + intensidade da Estática. */
    private void drawDetectorWidget() {
        double x = 12;
        double y = GameMap.PIXEL_HEIGHT - 58;
        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillRoundRect(x, y, 168, 48, 8, 8);
        gc.setStroke(Color.color(0.4, 0.4, 0.35, 0.7));
        gc.strokeRoundRect(x, y, 168, 48, 8, 8);

        gc.setFill(Color.color(0.85, 0.8, 0.5));
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        gc.fillText("DETECTOR FREQ", x + 8, y + 14);

        for (int i = 0; i < 4; i++) {
            boolean on = detectorLevel > i;
            gc.setFill(on ? Color.ORANGE : Color.color(0.25, 0.25, 0.22));
            gc.fillRect(x + 8 + i * 28, y + 22, 24, 8);
        }

        // barra da Estática (energia / tom fino)
        gc.setFill(Color.GRAY);
        gc.setFont(Font.font("Consolas", 9));
        gc.fillText("EST", x + 8, y + 42);
        gc.setFill(Color.color(0.2, 0.05, 0.05));
        gc.fillRect(x + 32, y + 34, 120, 8);
        if (enemy.active && enemyProximity > 0) {
            gc.setFill(Color.color(1, 0.15 + 0.2 * enemyProximity, 0.1));
            gc.fillRect(x + 32, y + 34, 120 * enemyProximity, 8);
        }
    }

    /** Radar estilo aviônica: você no centro, Estática em vermelho. */
    private void drawRadar() {
        double cx = GameMap.PIXEL_WIDTH - 90;
        double cy = 90;
        double rr = 70;
        // alcance do radar em pixels do mundo
        double worldRange = 520;

        gc.setFill(Color.color(0.02, 0.08, 0.05, 0.75));
        gc.fillOval(cx - rr, cy - rr, rr * 2, rr * 2);
        gc.setStroke(Color.color(0.2, 0.9, 0.4, 0.7));
        gc.setLineWidth(2);
        gc.strokeOval(cx - rr, cy - rr, rr * 2, rr * 2);
        gc.setLineWidth(1);
        gc.strokeOval(cx - rr * 0.66, cy - rr * 0.66, rr * 1.33, rr * 1.33);
        gc.strokeOval(cx - rr * 0.33, cy - rr * 0.33, rr * 0.66, rr * 0.66);
        gc.strokeLine(cx - rr, cy, cx + rr, cy);
        gc.strokeLine(cx, cy - rr, cx, cy + rr);

        // você (centro)
        gc.setFill(Color.LIME);
        gc.fillOval(cx - 4, cy - 4, 8, 8);

        if (enemy.active) {
            double edx = enemy.x - player.x;
            double edy = enemy.y - player.y;
            double dist = Math.hypot(edx, edy);
            double scale = rr / worldRange;
            double bx = cx + edx * scale;
            double by = cy + edy * scale;
            // limita à borda do círculo
            double bdx = bx - cx, bdy = by - cy;
            double bd = Math.hypot(bdx, bdy);
            if (bd > rr - 6) {
                bdx = bdx / bd * (rr - 6);
                bdy = bdy / bd * (rr - 6);
                bx = cx + bdx;
                by = cy + bdy;
            }
            double pulse = 0.55 + 0.45 * Math.sin(elapsedSeconds * 8);
            gc.setFill(Color.color(1, 0.1, 0.1, pulse));
            gc.fillOval(bx - 5, by - 5, 10, 10);
            if (dist < 220) {
                gc.setStroke(Color.color(1, 0.3, 0.2, 0.6));
                gc.strokeOval(bx - 10, by - 10, 20, 20);
            }
        }

        gc.setFill(Color.color(0.5, 1, 0.6, 0.85));
        gc.setFont(Font.font("Consolas", 10));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("RADAR", cx, cy + rr + 14);
        gc.fillText(player.radarCharges + "/" + Player.RADAR_CHARGES_MAX, cx, cy + rr + 26);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void renderHud() {
        double y0 = GameMap.PIXEL_HEIGHT;
        gc.setFill(Color.color(0.05, 0.05, 0.06));
        gc.fillRect(0, y0, canvas.getWidth(), HUD_HEIGHT);

        drawBar(16, y0 + 14, 180, 14, player.battery / 100.0, Color.SKYBLUE, "LANTERNA");
        drawBar(16, y0 + 40, 180, 14, player.sanity / 100.0, Color.MEDIUMPURPLE, "SANIDADE");

        gc.setFill(Color.ORANGE);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("FUSÍVEIS " + player.fuses + "/" + player.fusesNeeded, 220, y0 + 26);

        // Fase atual no HUD
        gc.setFill(Color.CYAN);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        gc.fillText("FASE " + (currentPhase + 1) + "/" + GameMap.TOTAL_PHASES
                + " — " + Lore.PHASE_NAMES[currentPhase], 400, y0 + 26);

        gc.setFill(player.powerRestored ? Color.LIMEGREEN : Color.GRAY);
        gc.setFont(Font.font("Consolas", 13));
        String energyLine = player.powerRestored
                ? "Energia: RESTAURADA — vá até a saída (ESPAÇO)"
                : "Energia: desligada";
        gc.fillText(energyLine, 220, y0 + 48);

        // status do radar
        if (player.radarUnlocked) {
            gc.setFill(radarActive ? Color.LIMEGREEN : Color.DARKSEAGREEN);
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
            gc.fillText("RADAR " + player.radarCharges + "/" + Player.RADAR_CHARGES_MAX
                    + (radarActive ? " [ON — R]" : " [R]"), 520, y0 + 48);
        } else {
            gc.setFill(Color.GRAY);
            gc.setFont(Font.font("Consolas", 12));
            gc.fillText("RADAR bloqueado (após fase 3)", 520, y0 + 48);
        }

        if (map.requiresRadioTune && player.powerRestored && !radioTuned) {
            gc.setFill(Color.color(0.3, 0.9, 0.5));
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
            gc.fillText(String.format("RÁDIO %.0f%% — segure ESPAÇO no console", radioTuneProgress * 100), 400, y0 + 48);
        }

        if (messageTimer > 0) {
            gc.setFill(Color.color(1, 1, 1, clamp(messageTimer, 0, 1)));
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(message, canvas.getWidth() - 16, y0 + 24);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawBar(double x, double y, double w, double h, double frac, Color color, String label) {
        frac = clamp(frac, 0, 1);
        gc.setFill(Color.color(0.15, 0.15, 0.15));
        gc.fillRect(x, y, w, h);
        gc.setFill(color);
        gc.fillRect(x, y, w * frac, h);
        gc.setStroke(Color.color(0.4, 0.4, 0.4));
        gc.setLineWidth(1);
        gc.strokeRect(x, y, w, h);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Consolas", 9));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(label, x, y - 3);
    }


    private void drawFloorCables() {
        // cabos decorativos ligando breaker → saída (só trechos iluminados)
        double x0 = map.breakerX, y0 = map.breakerY;
        double x1 = map.exitX, y1 = map.exitY;
        int segs = 8;
        for (int i = 0; i < segs; i++) {
            double t0 = i / (double) segs;
            double t1 = (i + 1) / (double) segs;
            double ax = x0 + (x1 - x0) * t0;
            double ay = y0 + (y1 - y0) * t0 + Math.sin(t0 * 6) * 12;
            double bx = x0 + (x1 - x0) * t1;
            double by = y0 + (y1 - y0) * t1 + Math.sin(t1 * 6) * 12;
            double l = lightAt((ax + bx) / 2, (ay + by) / 2);
            if (l < 0.12) continue;
            gc.setStroke(Color.color(0.12, 0.14, 0.1, clamp(l * 0.28, 0.05, 0.22)));
            gc.setLineWidth(1.5);
            gc.strokeLine(ax, ay, bx, by);
        }
        // placa da torre perto do spawn
        double lx = map.playerStartX;
        double ly = map.playerStartY - 36;
        double ll = lightAt(lx, ly);
        if (ll > 0.1) {
            gc.setFill(Color.color(0.25, 0.22, 0.15, clamp(ll, 0.3, 0.8)));
            gc.fillRect(lx - 40, ly - 8, 80, 16);
            gc.setFill(Color.color(0.85, 0.75, 0.4, clamp(ll, 0.4, 1)));
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 10));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(Lore.PHASE_NAMES[currentPhase], lx, ly + 4);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    private void drawSpawnComputers() {
        if (map.computerPanels.isEmpty()) return;
        double t = elapsedSeconds;
        int i = 0;
        for (double[] panel : map.computerPanels) {
            double cx = panel[0];
            double cy = panel[1];
            double l = Math.max(lightAt(cx, cy), 0.15);
            // corpo do rack
            gc.setFill(Color.color(0.12 * l, 0.12 * l, 0.14 * l));
            gc.fillRect(cx - 18, cy - 14, 36, 28);
            // tela
            double pulse = 0.55 + 0.45 * Math.sin(t * 3.0 + i);
            double g = 0.15 + 0.55 * pulse * l;
            double r = (i % 3 == 2) ? 0.45 * pulse * l : 0.05 * l;
            double b = (i % 3 == 1) ? 0.35 * pulse * l : 0.12 * l;
            gc.setFill(Color.color(clamp(r, 0, 1), clamp(g, 0, 1), clamp(b, 0, 1)));
            gc.fillRect(cx - 14, cy - 10, 28, 16);
            // LEDs
            gc.setFill(Color.color(0.1, 0.8 * l, 0.2, 0.9));
            gc.fillOval(cx - 12, cy + 8, 4, 4);
            gc.setFill(Color.color(0.8 * l, 0.15, 0.1, 0.9));
            gc.fillOval(cx - 4, cy + 8, 4, 4);
            gc.setFill(Color.color(0.8 * l, 0.7 * l, 0.1, 0.9));
            gc.fillOval(cx + 4, cy + 8, 4, 4);
            // rótulo
            gc.setFill(Color.color(0.6, 0.6, 0.55, clamp(l, 0.3, 0.85)));
            gc.setFont(Font.font("Consolas", 8));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("RX", cx, cy - 16);
            i++;
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void renderIntro() {
        double cw = canvas.getWidth();
        double ch = canvas.getHeight();
        // imagem de fundo
        if (spriteIntro != null) {
            gc.drawImage(spriteIntro, 0, 0, cw, ch);
        } else {
            gc.setFill(Color.color(0.05, 0.05, 0.07));
            gc.fillRect(0, 0, cw, ch);
        }
        // véu escuro para legibilidade
        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillRect(0, 0, cw, ch);

        gc.setTextAlign(TextAlignment.CENTER);
        double cx = cw / 2;

        gc.setFill(Color.CRIMSON);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 28));
        gc.fillText("REGISTRO — TORRE DE RETRANSMISSÃO", cx, 70);

        gc.setFill(Color.color(0.75, 0.72, 0.6));
        gc.setFont(Font.font("Consolas", 14));
        String[] lines = {
            "Você é o operador noturno desta torre isolada.",
            "Sua mesa: rádios, monitores, o canal de emergência 87.0 MHz.",
            "",
            "O apagão matou os geradores. Os fusíveis queimaram.",
            "Sem energia no quadro, a porta de saída permanece trancada.",
            "",
            "Às 02:17 alguém — ou algo — respondeu na frequência.",
            "Não deveria haver ninguém do outro lado.",
            "",
            "Agora a Estática se move no escuro.",
            "Ela só avança quando sua lanterna não a encara.",
            "",
            "Encontre os fusíveis. Restaure a energia. Fuja.",
            "Não deixe o silêncio te engolir.",
        };
        double y = 120;
        for (String line : lines) {
            gc.fillText(line, cx, y);
            y += 22;
        }

        gc.setFill(Color.ORANGE);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        gc.fillText("Pressione ENTER para entrar na torre", cx, ch - 48);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void renderMenu() {
        gc.setTextAlign(TextAlignment.CENTER);
        double cx = canvas.getWidth() / 2;

        gc.setFill(Color.CRIMSON);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 42));
        gc.fillText("FREQUÊNCIA 87", cx, 88);

        gc.setFill(Color.color(0.55, 0.55, 0.58));
        gc.setFont(Font.font("Consolas", 13));
        gc.fillText("— registro de emergência · torre de retransmissão isolada —", cx, 118);

        // História
        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font("Consolas", 14));
        double y = 160;
        double lh = 20;
        String[] story = {
            "O apagão veio sem aviso. Geradores falharam um a um.",
            "Você é o único operador ainda na torre. O protocolo manda",
            "manter o canal de emergência aberto: 87.0 MHz.",
            "",
            "Às 02:17 alguém respondeu. Uma voz quebrada, cheia de ruído.",
            "Não havia ninguém do outro lado. Não deveria haver.",
            "",
            "Agora a luz se foi de vez. Os fusíveis queimaram. A saída",
            "está trancada sem energia no quadro. E no escuro, algo se",
            "move — uma presença de estática que só avança quando você",
            "não a observa com a lanterna.",
            "",
            "Oito torres. Oito chances. Restaure a energia. Fuja.",
            "Não deixe a frequência te engolir.",
        };
        for (String line : story) {
            gc.fillText(line, cx, y);
            y += lh;
        }

        gc.setFill(Color.DARKGRAY);
        gc.setFont(Font.font("Consolas", 12));
        gc.fillText("A Estática só se move fora do foco da lanterna. Bateria e sanidade são limitadas.", cx, y + 8);
        gc.fillText("Ao morrer, você reinicia na mesma fase (checkpoint).", cx, y + 26);
        gc.fillText("Cada fusível revela um eco do que aconteceu nesta rede antes de você.", cx, y + 44);

        gc.setFill(Color.WHEAT);
        gc.setFont(Font.font("Consolas", 13));
        gc.fillText("WASD mover · F lanterna · ESPAÇO interagir · R radar (fase 4+) · ESC/P pausar · detector sempre ativo", cx, y + 68);

        gc.setFill(Color.ORANGE);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        gc.fillText("Pressione ENTER para sintonizar", cx, y + 104);
        if (savedPhase > 0) {
            gc.setFill(Color.CYAN);
            gc.setFont(Font.font("Consolas", 14));
            gc.fillText("C — continuar da fase " + (savedPhase + 1), cx, y + 130);
        }
        gc.setFill(Color.color(0.6, 0.6, 0.55));
        gc.setFont(Font.font("Consolas", 12));
        gc.fillText("M — mudo  ·  ESC/P — pausar no jogo", cx, y + 154);

        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void renderPhaseClear() {
        gc.setTextAlign(TextAlignment.CENTER);
        double cx = canvas.getWidth() / 2;
        double cy = canvas.getHeight() / 2;

        gc.setFill(Color.LIGHTGREEN);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 36));
        gc.fillText(Lore.PHASE_NAMES[currentPhase] + " CONCLUÍDA", cx, cy - 40);

        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font("Consolas", 16));
        gc.fillText("A frequência se estabilizou... por enquanto.", cx, cy + 4);

        int nextIndex = currentPhase + 1;
        if (nextIndex < GameMap.TOTAL_PHASES) {
            gc.setFill(Color.CYAN);
            gc.fillText("Próxima: " + Lore.PHASE_NAMES[nextIndex], cx, cy + 34);

            gc.setFill(Color.color(0.7, 0.65, 0.5));
            gc.setFont(Font.font("Consolas", 13));
            gc.fillText(Lore.PHASE_HINT[nextIndex], cx, cy + 56);
        }

        gc.setFill(Color.WHEAT);
        gc.setFont(Font.font("Consolas", 14));
        gc.fillText("Pressione ENTER para a próxima torre", cx, cy + 92);

        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void renderWinFinal() {
        String[] lines;
        String subtitle;
        if (echoesHeard >= maxEchoes) {
            lines = Lore.WIN_LINES_ALL_ECHOS;
            subtitle = "TODOS OS ECOS RECUPERADOS";
        } else if (lowestSanity >= 40) {
            lines = Lore.WIN_LINES_HIGH_SANITY;
            subtitle = "SANIDADE ESTÁVEL";
        } else {
            lines = Lore.WIN_LINES_LOW_SANITY;
            subtitle = "SINAL DEGRADADO";
        }
        gc.setTextAlign(TextAlignment.CENTER);
        double cx = canvas.getWidth() / 2;
        double cy = canvas.getHeight() / 2 - 40;

        gc.setFill(Color.LIGHTGREEN);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 36));
        gc.fillText("TRANSMISSÃO COMPLETA", cx, cy - 50);

        gc.setFill(Color.ORANGE);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        gc.fillText(subtitle, cx, cy - 22);

        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font("Consolas", 15));
        double ly = cy + 10;
        for (String line : lines) {
            gc.fillText(line, cx, ly);
            ly += 24;
        }

        // Log de transmissão
        ly += 16;
        gc.setFill(Color.color(0.55, 0.75, 0.9));
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
        gc.fillText("— LOG DE TRANSMISSÃO —", cx, ly);
        ly += 22;
        gc.setFill(Color.color(0.75, 0.75, 0.7));
        gc.setFont(Font.font("Consolas", 13));
        int mins = (int) (totalPlayTime / 60);
        int secs = (int) (totalPlayTime % 60);
        gc.fillText(String.format("Tempo total: %02d:%02d", mins, secs), cx, ly);
        ly += 18;
        gc.fillText("Ecos ouvidos: " + echoesHeard + " / " + maxEchoes, cx, ly);
        ly += 18;
        gc.fillText(String.format("Menor sanidade: %.0f%%", lowestSanity), cx, ly);
        ly += 18;
        gc.fillText("Torres concluídas: " + GameMap.TOTAL_PHASES, cx, ly);

        ly += 36;
        gc.setFill(Color.WHEAT);
        gc.setFont(Font.font("Consolas", 14));
        gc.fillText("Pressione ENTER para recomeçar do início", cx, ly);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void renderEndScreen(String title, String[] lines, Color color) {
        gc.setTextAlign(TextAlignment.CENTER);
        double cx = canvas.getWidth() / 2;
        double cy = canvas.getHeight() / 2;

        gc.setFill(color);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 40));
        gc.fillText(title, cx, cy - 40);

        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(Font.font("Consolas", 15));
        double ly = cy;
        for (String line : lines) {
            gc.fillText(line, cx, ly);
            ly += 24;
        }

        gc.setFill(Color.WHEAT);
        gc.setFont(Font.font("Consolas", 14));
        if (state == State.WIN_FINAL) {
            gc.fillText("Pressione ENTER para recomeçar do início", cx, ly + 24);
        } else {
            gc.fillText("Pressione ENTER para voltar ao checkpoint (Fase " + (currentPhase + 1) + ")", cx, ly + 24);
        }

        gc.setTextAlign(TextAlignment.LEFT);
    }
}
