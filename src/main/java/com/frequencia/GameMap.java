package com.frequencia;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa o layout da estação de rádio: paredes, portas e onde
 * ficam os itens colecionáveis (fusíveis, bateria), o quadro de energia
 * e a porta de saída. Suporta 8 mapas diferentes (fases 0–7).
 */
public class GameMap {

    public static final int TILE = 48;

    // Todos os layouts têm o mesmo tamanho: 25 colunas × 18 linhas
    private static final String[][] LAYOUTS = {
        // Fase 1 — layout original
        {
            "#########################",
            "#P......#...............#",
            "#.......#....F..........#",
            "#.......#######.........#",
            "#.......................#",
            "#..F....................#",
            "#..........#####........#",
            "#.......................#",
            "#....#####..............#",
            "#................F......#",
            "#.................B.....#",
            "#.......................#",
            "#....######.............#",
            "#.......................#",
            "#...............#####...#",
            "#....................E..#",
            "#....................K..#",
            "#########################",
        },
        // Fase 2 — corredores em cruz
        {
            "#########################",
            "#P..........#...........#",
            "#...........#.....F.....#",
            "#.....#######...........#",
            "#.......................#",
            "#..F....................#",
            "#######.....#####.......#",
            "#.......................#",
            "#...........#...........#",
            "#.....F.....#.....B.....#",
            "#...........#...........#",
            "#.......................#",
            "#.......#####.....#######",
            "#.......................#",
            "#...............#.......#",
            "#...............#..E....#",
            "#...............#..K....#",
            "#########################",
        },
        // Fase 3 — labirinto mais fechado + 4 fusíveis
        {
            "#########################",
            "#P..#.........#.........#",
            "#...#....F....#....F....#",
            "#...######....#.........#",
            "#........#..............#",
            "#..F.....#.....#####....#",
            "#........#..............#",
            "######...#........#.....#",
            "#........#........#.....#",
            "#....F...######...#..B..#",
            "#.................#.....#",
            "#.....#####.............#",
            "#...........#...........#",
            "#...........#...........#",
            "#.....#######.....###...#",
            "#....................E..#",
            "#....................K..#",
            "#########################",
        },
        // Fase 4 — salas interligadas
        {
            "#########################",
            "#P......#.......#.......#",
            "#.......#...F...#...F...#",
            "#.......#.......#.......#",
            "###.#####.......#####.###",
            "#.......................#",
            "#..F....................#",
            "#...........B...........#",
            "#.......................#",
            "###.#####.......#####.###",
            "#.......#.......#.......#",
            "#...F...#.......#...F...#",
            "#.......#.......#.......#",
            "#.......#####.#####.....#",
            "#.......................#",
            "#....................E..#",
            "#....................K..#",
            "#########################",
        },
        // Fase 5 — espiral / caminho longo
        {
            "#########################",
            "#P......................#",
            "#.#####################.#",
            "#.#...................#.#",
            "#.#.###############.#.#.#",
            "#.#.#.............F.#.#.#",
            "#.#.#.###########.#.#.#.#",
            "#.#.#.#.........F.#.#.#.#",
            "#.#.#.#.#####.###.#.#.#.#",
            "#.#.#.#.#...B.#.#.#.#.#.#",
            "#.#.#.#.#.F...#.#.#.#.#.#",
            "#.#.#.#.#####.###.#.#.#.#",
            "#.#.#.#....F....#.#.#.#.#",
            "#.#.#.###########.#.#.#.#",
            "#.#.#.............F.#.#.#",
            "#.#.#################E#.#",
            "#.#..................K..#",
            "#########################",
        },
        // Fase 6 — aberto no centro, paredes laterais + 5 fusíveis
        {
            "#########################",
            "#P..#...................#",
            "#...#....F..........F...#",
            "#...#...................#",
            "#.......#####.....#######",
            "#.......................#",
            "#..F....................#",
            "#...........B...........#",
            "#.......................#",
            "#....................F..#",
            "########...#########....#",
            "#.......................#",
            "#...F...................#",
            "#.......................#",
            "#...............#####...#",
            "#....................E..#",
            "#....................K..#",
            "#########################",
        },
        // Fase 7 — muitos obstáculos + 5 fusíveis
        {
            "#########################",
            "#P......#...#...#.......#",
            "#...F...#...#...#...F...#",
            "#.......#...#...#.......#",
            "###.###.###.###.###.#####",
            "#.......................#",
            "#..F..............F.....#",
            "#...........B...........#",
            "#.......................#",
            "#####.###.###.###.###.###",
            "#.......#...#...#.......#",
            "#...F...#...#...#...F...#",
            "#.......#...#...#.......#",
            "#.......................#",
            "#...............#####...#",
            "#....................E..#",
            "#....................K..#",
            "#########################",
        },
        // Fase 8 — final, labirinto denso + 6 fusíveis
        {
            "#########################",
            "#P..#.....#.....#.......#",
            "#...#..F..#..F..#...F...#",
            "#...#.....#.....#.......#",
            "###.#####.#####.#####.###",
            "#.......................#",
            "#..F..............F.....#",
            "#...........B...........#",
            "#.......................#",
            "###.#####.#####.#####.###",
            "#.......#.....#.........#",
            "#...F...#..F..#....F....#",
            "#.......#.....#.........#",
            "#.......................#",
            "#...............#####...#",
            "#....................E..#",
            "#....................K..#",
            "#########################",
        },
    };

    public static final int TOTAL_PHASES = LAYOUTS.length;
    public static final int COLS = LAYOUTS[0][0].length();
    public static final int ROWS = LAYOUTS[0].length;
    public static final int PIXEL_WIDTH = COLS * TILE;
    public static final int PIXEL_HEIGHT = ROWS * TILE;

    private final char[][] grid = new char[ROWS][COLS];

    public double playerStartX, playerStartY;
    public double breakerX, breakerY;
    public double exitX, exitY;
    public final List<Pickup> pickups = new ArrayList<>();
    /** Painéis de computador na parede do spawn (só visual). */
    public final List<double[]> computerPanels = new ArrayList<>();

    /** Zonas que drenam sanidade extra (centros em pixels). */
    public final List<double[]> sanityZones = new ArrayList<>();

    /** Console de rádio (objetivo especial). */
    public double radioX = -1, radioY = -1;
    /** Após restaurar energia, precisa sintonizar o rádio antes de sair. */
    public boolean requiresRadioTune = false;

    public final int phaseIndex;

    /** Cria o mapa da fase indicada (0 = primeira fase, 7 = última). */
    public GameMap(int phaseIndex) {
        if (phaseIndex < 0 || phaseIndex >= TOTAL_PHASES) {
            phaseIndex = 0;
        }
        this.phaseIndex = phaseIndex;
        String[] layout = LAYOUTS[phaseIndex];
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                char c = layout[y].charAt(x);
                grid[y][x] = c;
                double centerX = x * TILE + TILE / 2.0;
                double centerY = y * TILE + TILE / 2.0;
                switch (c) {
                    case 'P' -> {
                        playerStartX = centerX;
                        playerStartY = centerY;
                        grid[y][x] = '.';
                    }
                    case 'K' -> {
                        breakerX = centerX;
                        breakerY = centerY;
                        grid[y][x] = '.';
                    }
                    case 'E' -> {
                        exitX = centerX;
                        exitY = centerY;
                        grid[y][x] = '.';
                    }
                    case 'F' -> {
                        pickups.add(new Pickup(Pickup.Type.FUSE, centerX, centerY));
                        grid[y][x] = '.';
                    }
                    case 'B' -> {
                        pickups.add(new Pickup(Pickup.Type.BATTERY, centerX, centerY));
                        grid[y][x] = '.';
                    }
                    case 'R' -> {
                        pickups.add(new Pickup(Pickup.Type.RADAR_CELL, centerX, centerY));
                        grid[y][x] = '.';
                    }
                    default -> {
                        // '#' ou '.' permanecem como estão
                    }
                }
            }
        }
        placeSpawnComputers();
        // A partir da fase 4 (índice >= 3): coloca 2 células de radar em tiles livres
        if (phaseIndex >= 3) {
            spawnRadarCells(2);
        }
        buildSanityZones();
        setupRadioObjective();
    }

    /** Zonas de drenagem de sanidade em fases intermediárias/altas. */
    private void buildSanityZones() {
        sanityZones.clear();
        if (phaseIndex < 2) return;
        int target = 2 + phaseIndex / 2;
        int placed = 0;
        // espalha em tiles abertos longe do spawn e do breaker
        for (int y = 2; y < ROWS - 2 && placed < target; y++) {
            for (int x = 2; x < COLS - 2 && placed < target; x++) {
                if (grid[y][x] != '.') continue;
                double cx = x * TILE + TILE / 2.0;
                double cy = y * TILE + TILE / 2.0;
                if (Math.hypot(cx - playerStartX, cy - playerStartY) < TILE * 4) continue;
                if (Math.hypot(cx - breakerX, cy - breakerY) < TILE * 2.5) continue;
                // pseudo-aleatório estável por fase
                int h = (x * 31 + y * 17 + phaseIndex * 13) & 0xff;
                if (h % 11 != 0) continue;
                sanityZones.add(new double[] { cx, cy });
                placed++;
            }
        }
    }

    /**
     * Fase 7 (índice 6) e 8 (índice 7): após religar a energia, sintonizar o rádio
     * no console antes de poder escapar.
     */
    private void setupRadioObjective() {
        requiresRadioTune = phaseIndex >= 6;
        if (!requiresRadioTune) {
            radioX = -1;
            radioY = -1;
            return;
        }
        // coloca o rádio perto dos painéis de computador, ou ao lado do spawn
        if (!computerPanels.isEmpty()) {
            double[] p = computerPanels.get(0);
            radioX = p[0];
            radioY = p[1] + TILE * 0.9;
        } else {
            radioX = playerStartX + TILE;
            radioY = playerStartY;
        }
    }

    /** Multiplicador de drenagem de sanidade na posição (1 = normal). */
    public double sanityDrainMultiplier(double px, double py) {
        double mult = 1.0;
        for (double[] z : sanityZones) {
            double d = Math.hypot(px - z[0], py - z[1]);
            if (d < TILE * 1.35) {
                mult = Math.max(mult, 2.4);
            } else if (d < TILE * 2.2) {
                mult = Math.max(mult, 1.55);
            }
        }
        return mult;
    }

    /** Coloca 3 monitores na parede do topo, perto do ponto de spawn do jogador. */
    private void placeSpawnComputers() {
        int pc = (int) Math.floor(playerStartX / TILE);
        int pr = (int) Math.floor(playerStartY / TILE);
        // procura parede horizontal acima/ao lado do spawn
        int[] cols = { pc - 1, pc, pc + 1, pc + 2 };
        for (int col : cols) {
            if (col < 1 || col >= COLS - 1) continue;
            // parede na linha acima do jogador, ou a própria se for parede vizinha
            for (int row = Math.max(0, pr - 2); row <= pr; row++) {
                if (grid[row][col] == '#') {
                    double cx = col * TILE + TILE / 2.0;
                    double cy = row * TILE + TILE / 2.0;
                    computerPanels.add(new double[] { cx, cy });
                    break;
                }
            }
            if (computerPanels.size() >= 4) break;
        }
        // fallback: força na parede superior próxima ao P
        if (computerPanels.isEmpty()) {
            for (int dx = -1; dx <= 2; dx++) {
                int col = pc + dx;
                if (col < 1 || col >= COLS - 1) continue;
                computerPanels.add(new double[] { col * TILE + TILE / 2.0, TILE / 2.0 });
            }
        }
    }

    private void spawnRadarCells(int count) {
        int placed = 0;
        // varre o mapa em ordem determinística para achar chãos livres longe do start
        for (int y = 2; y < ROWS - 2 && placed < count; y++) {
            for (int x = 2; x < COLS - 2 && placed < count; x++) {
                if (grid[y][x] != '.') continue;
                double cx = x * TILE + TILE / 2.0;
                double cy = y * TILE + TILE / 2.0;
                // não sobrepor itens existentes
                boolean occupied = false;
                for (Pickup p : pickups) {
                    if (Math.hypot(p.x - cx, p.y - cy) < TILE * 0.8) {
                        occupied = true;
                        break;
                    }
                }
                if (occupied) continue;
                if (Math.hypot(cx - playerStartX, cy - playerStartY) < TILE * 3) continue;
                // espalha: um no "norte", outro no "sul" do mapa
                if (placed == 0 && y > ROWS / 2) continue;
                if (placed == 1 && y < ROWS / 2) continue;
                pickups.add(new Pickup(Pickup.Type.RADAR_CELL, cx, cy));
                placed++;
            }
        }
        // fallback se não achou o bastante
        for (int y = 1; y < ROWS - 1 && placed < count; y++) {
            for (int x = 1; x < COLS - 1 && placed < count; x++) {
                if (grid[y][x] != '.') continue;
                double cx = x * TILE + TILE / 2.0;
                double cy = y * TILE + TILE / 2.0;
                boolean occupied = false;
                for (Pickup p : pickups) {
                    if (Math.hypot(p.x - cx, p.y - cy) < TILE * 0.6) {
                        occupied = true;
                        break;
                    }
                }
                if (occupied) continue;
                pickups.add(new Pickup(Pickup.Type.RADAR_CELL, cx, cy));
                placed++;
            }
        }
    }

    /** Construtor sem argumento mantém compatibilidade (fase 1). */
    public GameMap() {
        this(0);
    }

    public boolean isWallAtPixel(double px, double py) {
        int col = (int) Math.floor(px / TILE);
        int row = (int) Math.floor(py / TILE);
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return true;
        return grid[row][col] == '#';
    }

    /** Verifica colisão considerando o raio (tamanho) da entidade em vários pontos da borda. */
    public boolean collides(double centerX, double centerY, double radius) {
        return isWallAtPixel(centerX - radius, centerY - radius)
                || isWallAtPixel(centerX + radius, centerY - radius)
                || isWallAtPixel(centerX - radius, centerY + radius)
                || isWallAtPixel(centerX + radius, centerY + radius);
    }

    /** Checagem simples de linha de visão (para saber se a lanterna/entidade enxerga através de paredes). */
    public boolean hasLineOfSight(double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double dist = Math.hypot(dx, dy);
        int steps = (int) (dist / (TILE / 4.0)) + 1;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = x0 + dx * t;
            double y = y0 + dy * t;
            if (isWallAtPixel(x, y)) return false;
        }
        return true;
    }

    public char[][] getGrid() {
        return grid;
    }

    /** Quantidade de fusíveis necessários na fase (aumenta com o nível). */
    public static int fusesNeededForPhase(int phaseIndex) {
        // Fase 1: 3, depois sobe até 6 na última
        return Math.min(6, 3 + phaseIndex / 2);
    }
}
