package ponyrun;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.SourceDataLine;

/**
 * Sonido sintetizado por código (no necesita archivos de audio).
 *  - Hilo-Sonido: toma efectos de una cola y los reproduce (el juego nunca se detiene por el audio).
 *  - Hilo-Musica: toca una melodía de chiptune en bucle mientras se juega.
 */
public final class Sonido {

    public enum Efecto { SALTO, BONUS, POWERUP, GOLPE, MUERTE, HITO, RELOJ, GLITCH, VIDA, ROMPER }

    private static final float RATE = 22050f;
    private static final AudioFormat FORMATO = new AudioFormat(RATE, 16, 1, true, false);
    private static final Random RND = new Random();
    private static final Map<Efecto, byte[]> BANCO = new EnumMap<>(Efecto.class);
    private static final BlockingQueue<Efecto> COLA = new LinkedBlockingQueue<>();

    private static volatile boolean disponible = true;
    private static volatile boolean silencio = false;
    private static volatile boolean musicaActiva = false;
    private static boolean iniciado = false;

    private Sonido() { }

    public static synchronized void iniciar() {
        if (iniciado) return;
        iniciado = true;
        construirBanco();

        Thread fx = new Thread(Sonido::bucleEfectos, "Hilo-Sonido");
        fx.setDaemon(true);
        fx.start();

        Thread mus = new Thread(Sonido::bucleMusica, "Hilo-Musica");
        mus.setDaemon(true);
        mus.start();
    }

    public static void reproducir(Efecto e) {
        if (!silencio && disponible) COLA.offer(e);
    }

    public static void setSilencio(boolean valor) { silencio = valor; }

    public static boolean isSilencio() { return silencio; }

    public static void setMusica(boolean activa) { musicaActiva = activa; }

    // ------------------------------------------------------------------ síntesis

    /** forma: 0 = cuadrada, 1 = triangular, 2 = ruido. La frecuencia va de f0 a f1. */
    private static byte[] tono(double f0, double f1, double seg, double vol, int forma) {
        int n = (int) (RATE * seg);
        byte[] out = new byte[n * 2];
        double fase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double f = f0 + (f1 - f0) * t;
            fase += 2 * Math.PI * f / RATE;
            double v;
            switch (forma) {
                case 0:  v = Math.sin(fase) >= 0 ? 1 : -1; break;
                case 1:  v = 2 / Math.PI * Math.asin(Math.sin(fase)); break;
                default: v = RND.nextDouble() * 2 - 1;
            }
            double env = (1 - t) * Math.min(1.0, i / 80.0);
            short s = (short) (v * env * vol * 32767);
            out[2 * i] = (byte) (s & 0xFF);
            out[2 * i + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return out;
    }

    private static byte[] unir(byte[]... partes) {
        int total = 0;
        for (byte[] p : partes) total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : partes) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private static void construirBanco() {
        BANCO.put(Efecto.SALTO, tono(280, 620, 0.16, 0.25, 0));
        BANCO.put(Efecto.BONUS, unir(tono(660, 660, 0.07, 0.25, 0), tono(990, 990, 0.14, 0.25, 0)));
        BANCO.put(Efecto.POWERUP, unir(tono(523, 523, 0.07, 0.25, 0), tono(659, 659, 0.07, 0.25, 0),
                tono(784, 784, 0.07, 0.25, 0), tono(1047, 1047, 0.16, 0.25, 0)));
        BANCO.put(Efecto.GOLPE, unir(tono(0, 0, 0.12, 0.40, 2), tono(220, 60, 0.25, 0.35, 0)));
        BANCO.put(Efecto.MUERTE, unir(tono(440, 440, 0.18, 0.30, 0), tono(370, 370, 0.18, 0.30, 0),
                tono(311, 311, 0.18, 0.30, 0), tono(262, 180, 0.55, 0.30, 0)));
        BANCO.put(Efecto.HITO, unir(tono(880, 880, 0.05, 0.20, 0), tono(1175, 1175, 0.09, 0.20, 0)));
        BANCO.put(Efecto.RELOJ, tono(600, 200, 0.35, 0.30, 1));
        BANCO.put(Efecto.GLITCH, tono(0, 0, 0.25, 0.25, 2));
        BANCO.put(Efecto.VIDA, unir(tono(523, 523, 0.08, 0.25, 0), tono(784, 784, 0.08, 0.25, 0),
                tono(1047, 1047, 0.08, 0.25, 0), tono(1568, 1568, 0.20, 0.25, 0)));
        BANCO.put(Efecto.ROMPER, unir(tono(0, 0, 0.06, 0.30, 2), tono(400, 150, 0.10, 0.25, 0)));
    }

    // ------------------------------------------------------------------ hilos

    private static void bucleEfectos() {
        while (true) {
            try {
                Efecto e = COLA.take();
                if (!disponible) continue;
                byte[] datos = BANCO.get(e);
                Clip clip = AudioSystem.getClip();
                clip.open(FORMATO, datos, 0, datos.length);
                clip.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) clip.close();
                });
                clip.start();
            } catch (InterruptedException ex) {
                return;
            } catch (Exception | Error ex) {
                disponible = false; // equipo sin audio: el juego sigue sin sonido
            }
        }
    }

    private static double frecuencia(int midi) {
        return 440.0 * Math.pow(2, (midi - 69) / 12.0);
    }

    private static void bucleMusica() {
        int[] melodia = {
            57, 0, 60, 57, 64, 0, 60, 0,   57, 0, 60, 57, 65, 0, 64, 0,
            55, 0, 59, 55, 62, 0, 59, 0,   52, 0, 56, 59, 64, 0, 63, 0
        };
        SourceDataLine linea;
        try {
            linea = AudioSystem.getSourceDataLine(FORMATO);
            linea.open(FORMATO, 4096);
            linea.start();
        } catch (Exception | Error e) {
            disponible = false;
            return;
        }

        Map<Integer, byte[]> notas = new HashMap<>();
        byte[] silencioBuf = new byte[(int) (RATE * 0.14) * 2];
        int i = 0;
        while (true) {
            byte[] datos = silencioBuf;
            if (!silencio && musicaActiva) {
                int nota = melodia[i % melodia.length];
                i++;
                if (nota != 0) {
                    datos = notas.computeIfAbsent(nota, m -> tono(frecuencia(m), frecuencia(m), 0.14, 0.10, 0));
                }
            }
            linea.write(datos, 0, datos.length); // bloquea al ritmo del audio
        }
    }
}
