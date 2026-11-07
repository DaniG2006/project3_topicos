package juegohilos;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Juego "Atrapa las estrellas" con TODOS los elementos del proyecto:
 *
 *  - Ej. 1 (sincronización): cada 5 estrellas seguidas se lanzan 4 hilos (SubProceso1-4)
 *          que entran al MISMO método synchronized (ZonaBonus.ejecutar) y dan puntos extra.
 *  - Ej. 2 (cronómetro): Hilo-Cronometro mide el tiempo jugado (se detiene en pausa).
 *  - Ej. 3 (archivo + ciclos): el récord se lee en la carga (Hilo_I) y se guarda al terminar;
 *          Hilo-Vocales hace el ciclo de vocales A-E-I-O-U (hay que atraparlas en orden).
 *  - Ej. 4 (juego): hilo del juego, generador y cuenta regresiva de 60 s.
 */
public class Juego extends JPanel {

    private static final int DURACION = 60;
    private static final int PUNTOS_BONUS_PASO = 1;
    private static final char[] VOCALES = {'A', 'E', 'I', 'O', 'U'};

    private enum Tipo { ESTRELLA, BOMBA, VOCAL }

    private static class Objeto {
        final double x;
        double y;
        final double vy;
        final Tipo tipo;
        final char letra;

        Objeto(double x, double vy, Tipo tipo, char letra) {
            this.x = x;
            this.y = -20;
            this.vy = vy;
            this.tipo = tipo;
            this.letra = letra;
        }
    }

    private final List<Objeto> objetos = new CopyOnWriteArrayList<>();
    private final Random rnd = new Random();
    private final Lienzo lienzo = new Lienzo();
    private final JTextArea registro = new JTextArea();
    private final JButton btnIniciar = new JButton("Iniciar / Reiniciar");
    private final JButton btnPausar = new JButton("Pausar");
    private final ZonaBonus zonaBonus = new ZonaBonus();

    private final AtomicInteger puntos = new AtomicInteger();      // lo modifican varios hilos
    private final AtomicBoolean bonusActivo = new AtomicBoolean(false);

    private volatile boolean jugando, finJuego, nuevoMejor, pausado, izq, der;
    private volatile int partida, vidas = 3, tiempo = DURACION, racha, vocalSiguiente, mejor;
    private volatile long cronoMs;
    private volatile double jugadorX = 400;

    public Juego() {
        setLayout(new BorderLayout());

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        btnIniciar.setFocusable(false); // el teclado siempre va al juego
        btnPausar.setFocusable(false);
        btnPausar.setEnabled(false);
        barra.add(btnIniciar);
        barra.add(btnPausar);
        barra.add(new JLabel("Move with arrows or A/D   |  Space: pause   |   Catch the vowels A-E-I-O-U in order for +100 and +1 life"));

        registro.setEditable(false);
        registro.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        registro.setBackground(new Color(20, 24, 32));
        registro.setForeground(new Color(150, 255, 170));
        JScrollPane sp = new JScrollPane(registro);
        sp.setPreferredSize(new Dimension(280, 520));
        sp.setBorder(BorderFactory.createTitledBorder("Registro de hilos"));

        add(barra, BorderLayout.NORTH);
        add(lienzo, BorderLayout.CENTER);
        add(sp, BorderLayout.EAST);

        btnIniciar.addActionListener(e -> iniciar());
        btnPausar.addActionListener(e -> alternarPausa());
    }

    public void setMejor(int valor) {
        mejor = valor;
        log("Récord actual: " + valor);
        lienzo.repaint();
    }

    public void enfocar() {
        lienzo.requestFocusInWindow();
    }

    // ------------------------------------------------------------------ control

    private synchronized void iniciar() {
        final int id = ++partida;
        objetos.clear();
        puntos.set(0);
        vidas = 3;
        tiempo = DURACION;
        racha = 0;
        vocalSiguiente = 0;
        cronoMs = 0;
        finJuego = false;
        nuevoMejor = false;
        pausado = false;
        jugadorX = Math.max(lienzo.getWidth(), 100) / 2.0;
        jugando = true;

        btnPausar.setText("Pausar");
        btnPausar.setEnabled(true);
        registro.setText("");
        log("=== Partida " + id + " ===");
        lienzo.requestFocusInWindow();

        crearHilo("Hilo-Juego", () -> bucleJuego(id));
        crearHilo("Hilo-Generador", () -> bucleGenerador(id));
        crearHilo("Hilo-Tiempo", () -> bucleTiempo(id));
        crearHilo("Hilo-Cronometro", () -> bucleCronometro(id));
        crearHilo("Hilo-Vocales", () -> bucleVocales(id));
    }

    private void alternarPausa() {
        if (!jugando) return;
        pausado = !pausado;
        btnPausar.setText(pausado ? "Reanudar" : "Pausar");
        log(pausado ? "-- Juego en pausa --" : "-- Juego reanudado --");
        lienzo.requestFocusInWindow();
    }

    private synchronized void terminar(int id) {
        if (id != partida || !jugando) return;
        jugando = false;
        finJuego = true;
        pausado = false;
        int p = puntos.get();
        log("=== Fin: " + p + " puntos ===");
        if (p > mejor) {
            mejor = p;
            nuevoMejor = true;
            guardarMejor(p);
        }
        SwingUtilities.invokeLater(() -> {
            btnPausar.setEnabled(false);
            btnPausar.setText("Pausar");
            lienzo.repaint();
        });
    }

    /** Guarda el récord en record.txt usando un hilo propio. */
    private void guardarMejor(int valor) {
        new Thread(() -> {
            try {
                Files.write(Paths.get("record.txt"),
                        Collections.singletonList(String.valueOf(valor)), StandardCharsets.UTF_8);
                log("[Hilo-Guardar] Récord guardado: " + valor);
            } catch (IOException e) {
                log("[Hilo-Guardar] No se pudo guardar: " + e.getMessage());
            }
        }, "Hilo-Guardar").start();
    }

    // ------------------------------------------------------------------ hilos

    private void crearHilo(String nombre, Runnable r) {
        Thread t = new Thread(r, nombre);
        t.setDaemon(true);
        t.start();
    }

    /** Un hilo sigue vivo solo mientras su partida esté activa. */
    private boolean activo(int id) {
        return jugando && id == partida;
    }

    /** Espera "ms" milisegundos de juego (el tiempo en pausa no cuenta). */
    private void esperar(int id, long ms) {
        long restante = ms;
        while (restante > 0 && activo(id)) {
            dormir(50);
            if (!pausado) restante -= 50;
        }
    }

    private void bucleJuego(int id) {
        while (activo(id)) {
            if (!pausado) actualizar(id);
            lienzo.repaint();
            dormir(16);
        }
        lienzo.repaint();
    }

    private void actualizar(int id) {
        int w = lienzo.getWidth();
        int h = lienzo.getHeight();
        int dir = (der ? 1 : 0) - (izq ? 1 : 0);
        jugadorX = Math.max(40, Math.min(w - 40, jugadorX + dir * 9));

        Rectangle2D.Double jugador = new Rectangle2D.Double(jugadorX - 40, h - 40, 80, 18);
        for (Objeto o : objetos) {
            o.y += o.vy;
            if (jugador.intersects(o.x - 14, o.y - 14, 28, 28)) {
                objetos.remove(o);
                recoger(o, id);
            } else if (o.y > h + 20) {
                objetos.remove(o);
            }
        }
    }

    private void recoger(Objeto o, int id) {
        switch (o.tipo) {
            case ESTRELLA:
                puntos.addAndGet(10);
                racha++;
                if (racha % 5 == 0) lanzarBonus(id);
                break;
            case BOMBA:
                vidas--;
                racha = 0;
                log("¡Bomba! Vidas: " + Math.max(vidas, 0));
                if (vidas <= 0) terminar(id);
                break;
            case VOCAL:
                puntos.addAndGet(20);
                vocalSiguiente++;
                log("Vocal " + o.letra + " atrapada (+20)");
                if (vocalSiguiente >= VOCALES.length) {
                    vocalSiguiente = 0;
                    puntos.addAndGet(100);
                    vidas = Math.min(5, vidas + 1);
                    log("¡A-E-I-O-U completo! +100 y +1 vida");
                }
                break;
        }
    }

    private void bucleGenerador(int id) {
        while (activo(id)) {
            esperar(id, Math.max(280, 800 - puntos.get() * 3L / 2));
            if (!activo(id)) break;
            int w = Math.max(lienzo.getWidth(), 100);
            double x = 20 + rnd.nextInt(w - 40);
            double vy = 3 + rnd.nextDouble() * 3 + puntos.get() / 150.0;
            Tipo tipo = rnd.nextInt(100) < 28 ? Tipo.BOMBA : Tipo.ESTRELLA;
            objetos.add(new Objeto(x, vy, tipo, ' '));
        }
    }

    /** Cuenta regresiva de 60 s. */
    private void bucleTiempo(int id) {
        int restante = DURACION;
        while (activo(id) && restante > 0) {
            esperar(id, 1000);
            if (!activo(id)) return;
            restante--;
            tiempo = restante;
        }
        if (activo(id)) terminar(id);
    }

    /** Cronómetro (Ejercicio 2): tiempo jugado, no avanza en pausa. */
    private void bucleCronometro(int id) {
        long local = 0;
        long ultimo = System.nanoTime();
        while (activo(id)) {
            dormir(10);
            long ahora = System.nanoTime();
            if (!pausado) local += (ahora - ultimo) / 1_000_000;
            ultimo = ahora;
            if (activo(id)) cronoMs = local;
        }
    }

    /** Ciclo de vocales (Ejercicio 3): suelta la siguiente vocal A-E-I-O-U cada ~5 s. */
    private void bucleVocales(int id) {
        while (activo(id)) {
            esperar(id, 5000);
            if (!activo(id)) break;
            boolean hayVocal = false;
            for (Objeto o : objetos) {
                if (o.tipo == Tipo.VOCAL) {
                    hayVocal = true;
                    break;
                }
            }
            if (!hayVocal) {
                int w = Math.max(lienzo.getWidth(), 100);
                char letra = VOCALES[vocalSiguiente];
                objetos.add(new Objeto(20 + rnd.nextInt(w - 40), 3.2, Tipo.VOCAL, letra));
                log("[Hilo-Vocales] Cae la vocal " + letra);
            }
        }
    }

    // ------------------------------------------------------------------ bonus sincronizado (Ejercicio 1)

    private void lanzarBonus(int id) {
        if (!bonusActivo.compareAndSet(false, true)) return;
        log(">>> ¡COMBO! 4 hilos al mismo metodo");

        Thread[] hilos = {
            new Thread(() -> zonaBonus.ejecutar(id, "SubProceso1", false), "SubProceso1"),
            new Thread(() -> zonaBonus.ejecutar(id, "SubProceso2", false), "SubProceso2"),
            new Thread(() -> zonaBonus.ejecutar(id, "SubProceso3", true), "SubProceso3"),
            new Thread(() -> zonaBonus.ejecutar(id, "SubProceso4", true), "SubProceso4")
        };

        Thread coordinador = new Thread(() -> {
            try {
                for (Thread t : hilos) {
                    t.setDaemon(true);
                    t.start();
                    Thread.sleep(30); // orden de entrada predecible
                }
                for (Thread t : hilos) t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            bonusActivo.set(false);
        }, "Hilo-CoordinadorBonus");
        coordinador.setDaemon(true);
        coordinador.start();
    }

    /** Método común sincronizado: un SubProceso termina por completo antes de que entre otro. */
    private class ZonaBonus {
        synchronized void ejecutar(int id, String nombre, boolean letras) {
            if (!activo(id)) return;
            log("Inició:  " + nombre);
            for (int i = 0; i < 10 && activo(id); i++) {
                String valor = letras ? String.valueOf((char) ('a' + i)) : String.valueOf(i + 1);
                log("  Hilo " + valor);
                puntos.addAndGet(PUNTOS_BONUS_PASO);
                esperar(id, 120);
            }
            log("Terminó: " + nombre);
        }
    }

    // ------------------------------------------------------------------ utilidades

    private void log(String texto) {
        SwingUtilities.invokeLater(() -> {
            registro.append(texto + "\n");
            registro.setCaretPosition(registro.getDocument().getLength());
        });
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String formato(long ms) {
        long cs = (ms / 10) % 100;
        long s = (ms / 1000) % 60;
        long m = ms / 60000;
        return String.format("%02d:%02d.%02d", m, s, cs);
    }

    private static Shape estrella(double cx, double cy, double r) {
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI / 5 * i - Math.PI / 2;
            double rad = (i % 2 == 0) ? r : r / 2.3;
            double px = cx + Math.cos(ang) * rad;
            double py = cy + Math.sin(ang) * rad;
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.closePath();
        return p;
    }

    // ------------------------------------------------------------------ dibujo

    private class Lienzo extends JPanel {

        Lienzo() {
            setPreferredSize(new Dimension(800, 520));
            setFocusable(true);
            setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            configurarTeclas();
        }

        private void configurarTeclas() {
            InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
            ActionMap am = getActionMap();
            asignar(im, am, "LEFT", true);
            asignar(im, am, "A", true);
            asignar(im, am, "RIGHT", false);
            asignar(im, am, "D", false);

            im.put(KeyStroke.getKeyStroke("pressed space"), "pausa");
            am.put("pausa", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    alternarPausa();
                }
            });
        }

        private void asignar(InputMap im, ActionMap am, String tecla, boolean esIzquierda) {
            String on = "on" + tecla;
            String off = "off" + tecla;
            im.put(KeyStroke.getKeyStroke("pressed " + tecla), on);
            im.put(KeyStroke.getKeyStroke("released " + tecla), off);
            am.put(on, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (esIzquierda) izq = true; else der = true;
                }
            });
            am.put(off, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (esIzquierda) izq = false; else der = false;
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            g.setPaint(new GradientPaint(0, 0, new Color(15, 20, 50), 0, h, new Color(45, 20, 80)));
            g.fillRect(0, 0, w, h);

            // objetos
            for (Objeto o : objetos) {
                int ox = (int) o.x;
                int oy = (int) o.y;
                switch (o.tipo) {
                    case BOMBA:
                        g.setColor(new Color(35, 35, 35));
                        g.fillOval(ox - 14, oy - 14, 28, 28);
                        g.setColor(Color.RED);
                        g.setStroke(new BasicStroke(3));
                        g.drawOval(ox - 14, oy - 14, 28, 28);
                        break;
                    case VOCAL:
                        g.setColor(new Color(40, 180, 90));
                        g.fillOval(ox - 16, oy - 16, 32, 32);
                        g.setColor(Color.WHITE);
                        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
                        String s = String.valueOf(o.letra);
                        FontMetrics fm = g.getFontMetrics();
                        g.drawString(s, ox - fm.stringWidth(s) / 2, oy + 6);
                        break;
                    default:
                        g.setColor(new Color(255, 215, 0));
                        g.fill(estrella(o.x, o.y, 16));
                }
            }

            // jugador
            g.setColor(new Color(0, 200, 255));
            g.fillRoundRect((int) jugadorX - 40, h - 40, 80, 18, 10, 10);

            dibujarHUD(g, w);

            if (!jugando) {
                g.setColor(new Color(0, 0, 0, 170));
                g.fillRect(0, 0, w, h);
                g.setColor(Color.WHITE);
                if (finJuego) {
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
                    centrado(g, "Finish", w, h / 2 - 40);
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
                    centrado(g, "Points: " + puntos.get() + "   |   Time played: " + formato(cronoMs), w, h / 2);
                    centrado(g, nuevoMejor ? "New record!" : "RRecord: " + mejor, w, h / 2 + 36);
                } else {
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
                    centrado(g, "Catch those stars", w, h / 2 - 90);
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
                    centrado(g, "Move: arrows or A/D   |   Space: pause", w, h / 2 - 50);
                    centrado(g, "Star: +10 points   |   Bomb: -1 life", w, h / 2 - 22);
                    centrado(g, "Catch the vowels A-E-I-O-U in order: +100 and +1 life", w, h / 2 + 6);
                    centrado(g, "5 stars in a row = combo (synchronized threads)", w, h / 2 + 34);
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
                    centrado(g, "Press \"Start\" to play", w, h / 2 + 80);
                }
            } else if (pausado) {
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRect(0, 0, w, h);
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
                centrado(g, "PAUSED", w, h / 2);
            }
            g.dispose();
        }

        private void dibujarHUD(Graphics2D g, int w) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            g.setColor(Color.WHITE);

            // línea 1
            g.drawString("Points: " + puntos.get(), 15, 26);
            g.drawString("Record: " + Math.max(mejor, puntos.get()), 165, 26);
            g.drawString("Time: " + tiempo + " s", w / 2 + 20, 26);
            g.drawString("Lives:", w - 170, 26);
            g.setColor(Color.RED);
            for (int i = 0; i < Math.min(Math.max(vidas, 0), 5); i++) {
                g.fillOval(w - 105 + i * 18, 12, 14, 14);
            }

            // línea 2
            g.setColor(Color.WHITE);
            g.drawString("Chronometer: " + formato(cronoMs), 15, 54);
            for (int i = 0; i < VOCALES.length; i++) {
                int x = w / 2 + 20 + i * 28;
                if (i < vocalSiguiente) g.setColor(new Color(255, 215, 0));
                else g.setColor(new Color(150, 150, 175));
                g.drawString(String.valueOf(VOCALES[i]), x, 54);
                if (i == vocalSiguiente && jugando) {
                    g.setColor(Color.WHITE);
                    g.drawRect(x - 5, 39, 21, 20);
                }
            }
            g.setColor(Color.WHITE);
            g.drawString("Combo: " + (racha % 5) + "/5", w - 170, 54);

            if (jugando && bonusActivo.get()) {
                g.setColor(new Color(0, 255, 140));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                centrado(g, "BONUS: synchronized threads in action", w, 84);
            }
        }

        private void centrado(Graphics2D g, String texto, int ancho, int y) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(texto, (ancho - fm.stringWidth(texto)) / 2, y);
        }
    }
}
