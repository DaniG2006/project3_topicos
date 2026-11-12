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
 * "Catch the Stars" - game with all the project elements:
 *
 *  - Ex. 1 (synchronization): every 5 stars in a row, 4 threads (SubProcess1-4) enter the SAME
 *          synchronized method (ZonaBonus.ejecutar) and give extra points.
 *  - Ex. 2 (stopwatch): Stopwatch-Thread measures play time (it stops while paused).
 *  - Ex. 3 (file + loops): the best score is read on the loading screen and saved when the game ends;
 *          Vowel-Thread runs the vowel cycle A-E-I-O-U (catch them in order).
 *  - Ex. 4 (game): game loop, spawner and 60-second countdown, each one in its own thread.
 *
 * Controls: SPACE = start / restart, ESC = menu, P = pause, M = sound on/off.
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
    private final JButton btnIniciar = new JButton("Start / Restart");
    private final JButton btnPausar = new JButton("Pause");
    private final ZonaBonus zonaBonus = new ZonaBonus();

    private final AtomicInteger puntos = new AtomicInteger();      // several threads add points
    private final AtomicBoolean bonusActivo = new AtomicBoolean(false);

    private volatile boolean jugando, finJuego, nuevoMejor, pausado, izq, der;
    private volatile boolean menuAbierto, pausaPorMenu;
    private volatile int menuSel = 0;
    private volatile int partida, vidas = 3, tiempo = DURACION, racha, vocalSiguiente, mejor;
    private volatile long cronoMs, finDesde;
    private volatile double jugadorX = 400;

    public Juego() {
        setLayout(new BorderLayout());

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        btnIniciar.setFocusable(false); // keyboard always goes to the game
        btnPausar.setFocusable(false);
        btnPausar.setEnabled(false);
        barra.add(btnIniciar);
        barra.add(btnPausar);
        barra.add(new JLabel("Move: arrows or A/D   |   SPACE: start   |   P: pause   |   ESC: menu   |   M: sound"));

        registro.setEditable(false);
        registro.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        registro.setBackground(new Color(20, 24, 32));
        registro.setForeground(new Color(150, 255, 170));
        JScrollPane sp = new JScrollPane(registro);
        sp.setPreferredSize(new Dimension(280, 520));
        sp.setBorder(BorderFactory.createTitledBorder("Thread log"));

        add(barra, BorderLayout.NORTH);
        add(lienzo, BorderLayout.CENTER);
        add(sp, BorderLayout.EAST);

        btnIniciar.addActionListener(e -> iniciar());
        btnPausar.addActionListener(e -> alternarPausa());
    }

    public void setMejor(int valor) {
        mejor = valor;
        log("Current best score: " + valor);
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
        menuAbierto = false;
        pausaPorMenu = false;
        jugadorX = Math.max(lienzo.getWidth(), 100) / 2.0;
        jugando = true;

        btnPausar.setText("Pause");
        btnPausar.setEnabled(true);
        registro.setText("");
        log("=== Match " + id + " ===");
        Sonido.reproducir(Sonido.Efecto.START);
        lienzo.requestFocusInWindow();

        crearHilo("Game-Thread", () -> bucleJuego(id));
        crearHilo("Spawner-Thread", () -> bucleGenerador(id));
        crearHilo("Timer-Thread", () -> bucleTiempo(id));
        crearHilo("Stopwatch-Thread", () -> bucleCronometro(id));
        crearHilo("Vowel-Thread", () -> bucleVocales(id));
    }

    private void alternarPausa() {
        if (!jugando || menuAbierto) return;
        pausado = !pausado;
        btnPausar.setText(pausado ? "Resume" : "Pause");
        log(pausado ? "-- Game paused --" : "-- Game resumed --");
        lienzo.requestFocusInWindow();
        lienzo.repaint();
    }

    private synchronized void terminar(int id) {
        if (id != partida || !jugando) return;
        jugando = false;
        finJuego = true;
        pausado = false;
        finDesde = System.currentTimeMillis();
        int p = puntos.get();
        log("=== End: " + p + " points ===");
        if (p > mejor) {
            mejor = p;
            nuevoMejor = true;
            guardarMejor(p);
            Sonido.reproducir(Sonido.Efecto.RECORD);
        } else {
            Sonido.reproducir(Sonido.Efecto.GAMEOVER);
        }
        SwingUtilities.invokeLater(() -> {
            btnPausar.setEnabled(false);
            btnPausar.setText("Pause");
            lienzo.repaint();
        });
    }

    /** Saves the best score in record.txt using its own thread. */
    private void guardarMejor(int valor) {
        new Thread(() -> {
            try {
                Files.write(Paths.get("record.txt"),
                        Collections.singletonList(String.valueOf(valor)), StandardCharsets.UTF_8);
                log("[Save-Thread] Best score saved: " + valor);
            } catch (IOException e) {
                log("[Save-Thread] Could not save: " + e.getMessage());
            }
        }, "Save-Thread").start();
    }

    // ------------------------------------------------------------------ menu (ESC)

    private void alternarMenu() {
        if (menuAbierto) cerrarMenu(); else abrirMenu();
    }

    private void abrirMenu() {
        menuAbierto = true;
        menuSel = 0;
        if (jugando && !pausado) {      // the menu pauses all threads
            pausado = true;
            pausaPorMenu = true;
        }
        btnPausar.setText(pausado ? "Resume" : "Pause");
        Sonido.reproducir(Sonido.Efecto.MENU_OPEN);
        log("-- Menu opened --");
        lienzo.repaint();
    }

    private void cerrarMenu() {
        menuAbierto = false;
        if (pausaPorMenu) {
            pausado = false;
            pausaPorMenu = false;
        }
        btnPausar.setText(pausado ? "Resume" : "Pause");
        Sonido.reproducir(Sonido.Efecto.MENU_CLOSE);
        log("-- Menu closed --");
        lienzo.repaint();
    }

    private void moverMenu(int delta) {
        if (!menuAbierto) return;
        menuSel = (menuSel + delta + 4) % 4;
        Sonido.reproducir(Sonido.Efecto.MOVE);
        lienzo.repaint();
    }

    private void elegirMenu() {
        if (!menuAbierto) return;
        Sonido.reproducir(Sonido.Efecto.SELECT);
        switch (menuSel) {
            case 0:
                cerrarMenu();
                break;
            case 1:
                cerrarMenu();
                iniciar();
                break;
            case 2:
                Sonido.setSilencio(!Sonido.isSilencio());
                lienzo.repaint();
                break;
            default:
                System.exit(0);
        }
    }

    /** SPACE: select in the menu; otherwise start / restart the game. */
    private void teclaEspacio() {
        if (menuAbierto) {
            elegirMenu();
        } else if (!jugando) {
            if (!finJuego || System.currentTimeMillis() - finDesde > 600) iniciar();
        }
    }

    // ------------------------------------------------------------------ threads

    private void crearHilo(String nombre, Runnable r) {
        Thread t = new Thread(r, nombre);
        t.setDaemon(true);
        t.start();
    }

    /** A thread stays alive only while its own match is active. */
    private boolean activo(int id) {
        return jugando && id == partida;
    }

    /** Waits "ms" of game time (time spent paused does not count). */
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
                Sonido.reproducir(Sonido.Efecto.STAR);
                if (racha % 5 == 0) lanzarBonus(id);
                break;
            case BOMBA:
                vidas--;
                racha = 0;
                Sonido.reproducir(Sonido.Efecto.BOMB);
                log("Bomb! Lives: " + Math.max(vidas, 0));
                if (vidas <= 0) terminar(id);
                break;
            case VOCAL:
                puntos.addAndGet(20);
                vocalSiguiente++;
                log("Vowel " + o.letra + " caught (+20)");
                if (vocalSiguiente >= VOCALES.length) {
                    vocalSiguiente = 0;
                    puntos.addAndGet(100);
                    vidas = Math.min(5, vidas + 1);
                    Sonido.reproducir(Sonido.Efecto.COMPLETE);
                    log("A-E-I-O-U complete! +100 and +1 life");
                } else {
                    Sonido.reproducir(Sonido.Efecto.VOWEL);
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

    /** 60-second countdown (beeps during the last 5 seconds). */
    private void bucleTiempo(int id) {
        int restante = DURACION;
        while (activo(id) && restante > 0) {
            esperar(id, 1000);
            if (!activo(id)) return;
            restante--;
            tiempo = restante;
            if (restante > 0 && restante <= 5) Sonido.reproducir(Sonido.Efecto.WARNING);
        }
        if (activo(id)) terminar(id);
    }

    /** Stopwatch (Exercise 2): play time, does not advance while paused. */
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

    /** Vowel cycle (Exercise 3): drops the next vowel A-E-I-O-U about every 5 seconds. */
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
                log("[Vowel-Thread] Vowel " + letra + " is falling");
            }
        }
    }

    // ------------------------------------------------------------------ synchronized bonus (Exercise 1)

    private void lanzarBonus(int id) {
        if (!bonusActivo.compareAndSet(false, true)) return;
        log(">>> COMBO! 4 threads, same method");
        Sonido.reproducir(Sonido.Efecto.POWERUP);

        Thread[] hilos = {
            new Thread(() -> zonaBonus.ejecutar(id, "SubProcess1", false), "SubProcess1"),
            new Thread(() -> zonaBonus.ejecutar(id, "SubProcess2", false), "SubProcess2"),
            new Thread(() -> zonaBonus.ejecutar(id, "SubProcess3", true), "SubProcess3"),
            new Thread(() -> zonaBonus.ejecutar(id, "SubProcess4", true), "SubProcess4")
        };

        Thread coordinador = new Thread(() -> {
            try {
                for (Thread t : hilos) {
                    t.setDaemon(true);
                    t.start();
                    Thread.sleep(30); // predictable entry order
                }
                for (Thread t : hilos) t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            bonusActivo.set(false);
        }, "Bonus-Coordinator");
        coordinador.setDaemon(true);
        coordinador.start();
    }

    /** Shared synchronized method: one SubProcess finishes completely before the next one enters. */
    private class ZonaBonus {
        synchronized void ejecutar(int id, String nombre, boolean letras) {
            if (!activo(id)) return;
            log("Started:  " + nombre);
            for (int i = 0; i < 10 && activo(id); i++) {
                String valor = letras ? String.valueOf((char) ('a' + i)) : String.valueOf(i + 1);
                log("  Thread " + valor);
                puntos.addAndGet(PUNTOS_BONUS_PASO);
                Sonido.reproducir(Sonido.Efecto.TICK);
                esperar(id, 120);
            }
            log("Finished: " + nombre);
        }
    }

    // ------------------------------------------------------------------ utilities

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

    // ------------------------------------------------------------------ drawing and keyboard

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

            tecla(im, am, "pressed SPACE", "espacio", Juego.this::teclaEspacio);
            tecla(im, am, "pressed ENTER", "enter", Juego.this::elegirMenu);
            tecla(im, am, "pressed ESCAPE", "menu", Juego.this::alternarMenu);
            tecla(im, am, "pressed P", "pausa", Juego.this::alternarPausa);
            tecla(im, am, "pressed UP", "arriba", () -> moverMenu(-1));
            tecla(im, am, "pressed W", "arriba2", () -> moverMenu(-1));
            tecla(im, am, "pressed DOWN", "abajo", () -> moverMenu(1));
            tecla(im, am, "pressed S", "abajo2", () -> moverMenu(1));
            tecla(im, am, "pressed M", "sonido", () -> {
                Sonido.setSilencio(!Sonido.isSilencio());
                repaint();
            });
        }

        private void tecla(InputMap im, ActionMap am, String ks, String nombre, Runnable r) {
            im.put(KeyStroke.getKeyStroke(ks), nombre);
            am.put(nombre, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    r.run();
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

            // falling objects
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

            // player
            g.setColor(new Color(0, 200, 255));
            g.fillRoundRect((int) jugadorX - 40, h - 40, 80, 18, 10, 10);

            dibujarHUD(g, w, h);

            if (!jugando) {
                g.setColor(new Color(0, 0, 0, 170));
                g.fillRect(0, 0, w, h);
                g.setColor(Color.WHITE);
                if (finJuego) {
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
                    centrado(g, "GAME OVER", w, h / 2 - 40);
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
                    centrado(g, "Score: " + puntos.get() + "   |   Time played: " + formato(cronoMs), w, h / 2);
                    centrado(g, nuevoMejor ? "New best score!" : "Best: " + mejor, w, h / 2 + 36);
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
                    centrado(g, "Press SPACE to play again", w, h / 2 + 90);
                } else {
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
                    centrado(g, "Catch the Stars", w, h / 2 - 90);
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
                    centrado(g, "Move: arrows or A/D   |   P: pause   |   ESC: menu", w, h / 2 - 50);
                    centrado(g, "Star: +10 points   |   Bomb: -1 life", w, h / 2 - 22);
                    centrado(g, "Catch the vowels A-E-I-O-U in order: +100 and +1 life", w, h / 2 + 6);
                    centrado(g, "5 stars in a row = combo (synchronized threads)", w, h / 2 + 34);
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
                    centrado(g, "Press SPACE to start", w, h / 2 + 85);
                }
            } else if (pausado && !menuAbierto) {
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRect(0, 0, w, h);
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
                centrado(g, "PAUSED", w, h / 2);
            }

            if (menuAbierto) dibujarMenu(g, w, h);
            g.dispose();
        }

        private void dibujarHUD(Graphics2D g, int w, int h) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            g.setColor(Color.WHITE);

            // line 1
            g.drawString("Score: " + puntos.get(), 15, 26);
            g.drawString("Best: " + Math.max(mejor, puntos.get()), 165, 26);
            g.drawString("Time: " + tiempo + " s", w / 2 + 20, 26);
            g.drawString("Lives:", w - 170, 26);
            g.setColor(Color.RED);
            for (int i = 0; i < Math.min(Math.max(vidas, 0), 5); i++) {
                g.fillOval(w - 105 + i * 18, 12, 14, 14);
            }

            // line 2
            g.setColor(Color.WHITE);
            g.drawString("Stopwatch: " + formato(cronoMs), 15, 54);
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
                centrado(g, "BONUS: synchronized threads running", w, 84);
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.setColor(new Color(200, 200, 220));
            g.drawString("ESC: menu   |   Sound: " + (Sonido.isSilencio() ? "OFF" : "ON") + " (M)", 10, h - 8);
        }

        private void dibujarMenu(Graphics2D g, int w, int h) {
            g.setColor(new Color(0, 0, 0, 185));
            g.fillRect(0, 0, w, h);

            int bw = 420;
            int bh = 270;
            int bx = (w - bw) / 2;
            int by = (h - bh) / 2;
            g.setColor(new Color(25, 20, 55));
            g.fillRoundRect(bx, by, bw, bh, 24, 24);
            g.setColor(new Color(255, 215, 0));
            g.setStroke(new BasicStroke(3));
            g.drawRoundRect(bx, by, bw, bh, 24, 24);

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
            g.setColor(Color.WHITE);
            centrado(g, "MENU", w, by + 52);

            String[] items = {"RESUME", "NEW GAME", "SOUND: " + (Sonido.isSilencio() ? "OFF" : "ON"), "QUIT"};
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            for (int i = 0; i < items.length; i++) {
                int y = by + 105 + i * 40;
                if (i == menuSel) {
                    g.setColor(new Color(255, 215, 0));
                    centrado(g, "> " + items[i] + " <", w, y);
                } else {
                    g.setColor(Color.WHITE);
                    centrado(g, items[i], w, y);
                }
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.setColor(Color.LIGHT_GRAY);
            centrado(g, "UP/DOWN: move   ENTER/SPACE: select   ESC: close", w, by + bh - 16);
        }

        private void centrado(Graphics2D g, String texto, int ancho, int y) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(texto, (ancho - fm.stringWidth(texto)) / 2, y);
        }
    }
}
