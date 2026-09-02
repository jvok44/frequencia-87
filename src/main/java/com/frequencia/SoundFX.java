package com.frequencia;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Efeitos sonoros sintéticos (sem arquivos externos):
 * - Detector de frequência / Geiger (fusíveis + Estática)
 * - Pings do radar
 */
public class SoundFX {

    private static final float SAMPLE_RATE = 22050f;
    private final Random random = new Random();
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sfx");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    private double geigerAcc = 0;
    private double radarPingAcc = 0;
    private double toneAcc = 0;
    private double ambientAcc = 0;
    private double hissAcc = 0;

    public void setEnabled(boolean on) {
        enabled.set(on);
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    // ------------------------------------------------------------------
    // API de alto nível (chamada a cada frame)
    // ------------------------------------------------------------------

    /**
     * @param fuseLevel 0–4 (0 = longe de fusível, 4 = em cima)
     * @param enemyProximity 0–1 (0 = longe/inativo, 1 = colando em você)
     * @param enemyActive se a Estática está caçando
     * @param dt delta time
     */
    public void updateDetector(int fuseLevel, double enemyProximity, boolean enemyActive, double dt) {
        if (!enabled.get()) return;

        // Ritmo base do Geiger sobe com fusível e com a Estática
        double ticksPerSec = 0.35 + fuseLevel * 1.1;
        if (enemyActive) {
            ticksPerSec += enemyProximity * 9.0; // bem agitado perto da Estática
        }
        geigerAcc += dt * ticksPerSec;
        while (geigerAcc >= 1.0) {
            geigerAcc -= 1.0;
            // Pitch: fusível = clicks médios; Estática = mais fino (agudo)
            double baseHz = 900 + fuseLevel * 180;
            if (enemyActive && enemyProximity > 0.05) {
                // quanto mais perto, mais fino e alto
                baseHz = 1400 + enemyProximity * 3200;
            }
            double vol = 0.12 + fuseLevel * 0.04;
            if (enemyActive) vol = Math.min(0.55, 0.14 + enemyProximity * 0.45);
            final double hz = baseHz;
            final double v = vol;
            final boolean harsh = enemyActive && enemyProximity > 0.35;
            pool.execute(() -> playClick(hz, v, harsh));
        }

        // Tom contínuo sutil só quando a Estática está bem perto (energia)
        if (enemyActive && enemyProximity > 0.55) {
            toneAcc += dt;
            if (toneAcc >= 0.12) {
                toneAcc = 0;
                double hz = 2200 + enemyProximity * 2800;
                pool.execute(() -> playTone(hz, 0.08, 0.09 * enemyProximity));
            }
        } else {
            toneAcc = 0;
        }
    }

    public void updateRadar(boolean active, double dt) {
        if (!enabled.get() || !active) {
            radarPingAcc = 0;
            return;
        }
        radarPingAcc += dt;
        if (radarPingAcc >= 1.15) {
            radarPingAcc = 0;
            pool.execute(this::playRadarSweep);
        }
    }

    public void playRadarOn() {
        if (!enabled.get()) return;
        pool.execute(() -> {
            playTone(520, 0.07, 0.2);
            sleep(60);
            playTone(780, 0.08, 0.22);
        });
    }

    public void playRadarOff() {
        if (!enabled.get()) return;
        pool.execute(() -> {
            playTone(700, 0.06, 0.18);
            sleep(50);
            playTone(420, 0.07, 0.16);
        });
    }

    public void playPickup() {
        if (!enabled.get()) return;
        pool.execute(() -> {
            playTone(660, 0.05, 0.15);
            sleep(40);
            playTone(990, 0.06, 0.15);
        });
    }

    public void playWarning() {
        if (!enabled.get()) return;
        pool.execute(() -> playTone(180, 0.12, 0.25));
    }

    public void playPowerRestore() {
        if (!enabled.get()) return;
        pool.execute(() -> {
            playTone(120, 0.08, 0.2);
            sleep(70);
            playTone(240, 0.1, 0.22);
            sleep(60);
            playTone(480, 0.14, 0.2);
            sleep(40);
            // relé / clique elétrico
            playClick(90, 0.3, true);
        });
    }

    public void playCaught() {
        if (!enabled.get()) return;
        pool.execute(() -> {
            playNoiseBurst(0.45, 0.35);
            sleep(30);
            playTone(80, 0.35, 0.28);
        });
    }

    public void playSanityLoss() {
        if (!enabled.get()) return;
        pool.execute(() -> {
            playTone(400, 0.2, 0.12);
            sleep(50);
            playNoiseBurst(0.6, 0.22);
            playTone(90, 0.4, 0.2);
        });
    }

    public void playPhaseClear() {
        if (!enabled.get()) return;
        pool.execute(() -> {
            playTone(520, 0.08, 0.18);
            sleep(50);
            playTone(780, 0.1, 0.2);
            sleep(50);
            playTone(1040, 0.12, 0.18);
        });
    }

    public void playWin() {
        if (!enabled.get()) return;
        pool.execute(() -> {
            playTone(330, 0.12, 0.2);
            sleep(80);
            playTone(440, 0.12, 0.2);
            sleep(80);
            playTone(550, 0.18, 0.22);
            sleep(100);
            playNoiseBurst(0.25, 0.08); // silêncio que sobra
        });
    }

    /** Eco de voz distorcida ao revelar fragmento de lore. */
    public void playLoreEcho() {
        if (!enabled.get()) return;
        pool.execute(() -> {
            // formantes grossas = "voz" artificial
            playTone(180, 0.08, 0.1);
            sleep(30);
            playTone(240, 0.12, 0.12);
            sleep(20);
            playNoiseBurst(0.12, 0.1);
            sleep(40);
            playTone(160, 0.15, 0.09);
        });
    }

    public void playRadioTune() {
        if (!enabled.get()) return;
        pool.execute(() -> {
            playTone(700, 0.05, 0.12);
            sleep(40);
            playTone(900, 0.05, 0.12);
            sleep(40);
            playTone(1100, 0.08, 0.14);
        });
    }

    public void playTeleport() {
        if (!enabled.get()) return;
        pool.execute(() -> playNoiseBurst(0.09, 0.2));
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Ambiente contínuo: chiado de rádio + tensão por sanidade/fase/inimigo.
     */
    public void updateAmbient(double sanityFrac, double enemyProx, boolean enemyActive, int phase, double dt) {
        if (!enabled.get()) return;

        // Hiss base sempre presente, mais forte em fases altas e sanidade baixa
        double hissRate = 0.35 + (1.0 - sanityFrac) * 0.9 + phase * 0.04;
        if (enemyActive) hissRate += enemyProx * 1.2;
        hissAcc += dt * hissRate;
        if (hissAcc >= 1.0) {
            hissAcc = 0;
            double vol = 0.03 + (1.0 - sanityFrac) * 0.07 + (enemyActive ? enemyProx * 0.06 : 0);
            final double v = Math.min(0.16, vol);
            pool.execute(() -> playNoiseBurst(0.05 + random.nextDouble() * 0.04, v));
        }

        // Drone grave quando sanidade crítica
        if (sanityFrac < 0.35) {
            ambientAcc += dt;
            if (ambientAcc >= 0.35) {
                ambientAcc = 0;
                double hz = 55 + (1.0 - sanityFrac) * 40;
                double vol = 0.04 + (0.35 - sanityFrac) * 0.12;
                pool.execute(() -> playTone(hz, 0.28, vol));
            }
        } else {
            ambientAcc = 0;
        }
    }

    private void playNoiseBurst(double seconds, double volume) {
        int n = (int) (SAMPLE_RATE * seconds);
        if (n < 8) n = 8;
        byte[] buf = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double env = Math.min(1.0, i / (SAMPLE_RATE * 0.008))
                    * Math.min(1.0, (n - i) / (SAMPLE_RATE * 0.02));
            double sample = (random.nextDouble() * 2 - 1) * env * volume;
            // leve filtragem: mistura com seno baixo pra não ser só white noise
            double t = i / SAMPLE_RATE;
            sample = sample * 0.85 + Math.sin(2 * Math.PI * 90 * t) * 0.15 * env * volume;
            short s = (short) (sample * Short.MAX_VALUE);
            buf[i * 2] = (byte) (s & 0xff);
            buf[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        write(buf);
    }

    // ------------------------------------------------------------------
    // Síntese
    // ------------------------------------------------------------------

    private void playClick(double hz, double volume, boolean harsh) {
        int n = (int) (SAMPLE_RATE * (harsh ? 0.035 : 0.022));
        byte[] buf = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / SAMPLE_RATE;
            double env = 1.0 - (double) i / n;
            env *= env;
            double sample;
            if (harsh) {
                // estática / chiado fino
                sample = Math.sin(2 * Math.PI * hz * t) * 0.55
                        + (random.nextDouble() * 2 - 1) * 0.45;
            } else {
                sample = Math.sin(2 * Math.PI * hz * t);
            }
            short s = (short) (sample * env * volume * Short.MAX_VALUE);
            buf[i * 2] = (byte) (s & 0xff);
            buf[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        write(buf);
    }

    private void playTone(double hz, double seconds, double volume) {
        int n = (int) (SAMPLE_RATE * seconds);
        byte[] buf = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / SAMPLE_RATE;
            double env = Math.min(1.0, i / (SAMPLE_RATE * 0.01))
                    * Math.min(1.0, (n - i) / (SAMPLE_RATE * 0.02));
            double sample = Math.sin(2 * Math.PI * hz * t) * env * volume;
            short s = (short) (sample * Short.MAX_VALUE);
            buf[i * 2] = (byte) (s & 0xff);
            buf[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        write(buf);
    }

    private void playRadarSweep() {
        int n = (int) (SAMPLE_RATE * 0.28);
        byte[] buf = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / SAMPLE_RATE;
            double hz = 400 + t * 1600; // varredura aguda
            double env = Math.sin(Math.PI * i / (double) n) * 0.18;
            double sample = Math.sin(2 * Math.PI * hz * t) * env;
            // leve ruído de “escopo”
            sample += (random.nextDouble() * 2 - 1) * 0.02 * env;
            short s = (short) (sample * Short.MAX_VALUE);
            buf[i * 2] = (byte) (s & 0xff);
            buf[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        write(buf);
    }

    private void write(byte[] buf) {
        try {
            AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
                line.open(fmt, buf.length);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
            }
        } catch (Exception ignored) {
            // ambiente sem placa de áudio — silencia sem quebrar o jogo
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
