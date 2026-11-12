package ponyrun;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * PONY RUN: juego tipo "dinosaurio de Chrome" con ambientación de arcade antiguo.
 *
 * Hilos:
 *   Hilo-Juego       -> actualiza el mundo y dibuja cada cuadro (~60 FPS)
 *   Hilo-Obstaculos  -> genera postes y bichos voladores
 *   Hilo-Bonus       -> genera manzanas, estrellas, relojes y corazones
 *   Hilo-Cronometro  -> mide el tiempo jugado (se detiene en pausa)
 *   Hilo-Glitch      -> provoca fallos visuales aleatorios en la "pantalla"
 *   Hilo-Sonido / Hilo-Musica (clase Sonido) y Hilo-Guardar (récord en archivo)
 *
 * Todo el estado del mundo lo modifica solo Hilo-Juego; los demás hilos le envían
 * cosas mediante colas concurrentes, así no hay condiciones de carrera.
 */
public class PanelJuego extends JPanel {

    // ------------------------------------------------------------------ constantes
    private static final int W = 480, H = 270;          // resolución lógica (pixel art)
    private static final int ESC = 2;                   // ampliación a pantalla
    private static final int SCR_W = W * ESC, SCR_H = H * ESC;
    private static final int MARCO = 20;                // marco de la "tele"
    private static final int SUELO = 215;
    private static final int PX = 56;                   // posición X del pony

    private static final Color PONY_ROSA = new Color(214, 150, 238);
    private static final Color SOMBRA = new Color(20, 10, 40);
    private static final Font FUENTE = new Font("Comic Sans MS", Font.BOLD, 12);
    private static final Font FUENTE_P = new Font("Comic Sans MS", Font.BOLD, 10);
    private static final Font FUENTE_G = new Font("Comic Sans MS", Font.BOLD, 34);
    private static final Font FUENTE_M = new Font("Comic Sans MS", Font.BOLD, 16);
    private static final String[] MENSAJES = {
        "SIGUE CORRIENDO", "NO PUEDES SALIR", "TE ESTOY VIENDO", "ERROR 666", "NO HAY ESCAPE"
    };

    private enum Estado { TITULO, JUGANDO, GAMEOVER }
    private enum TObst { POSTES, BICHO }
    private enum TBonus { MANZANA, ESTRELLA, RELOJ, CORAZON }

    // ------------------------------------------------------------------ clases internas
    private static class Obst {
        double x;
        final TObst tipo;
        final int[] alturas;
        final int w;
        final int y;

        Obst(double x, int[] alturas) {             // grupo de postes
            this.x = x;
            this.tipo = TObst.POSTES;
            this.alturas = alturas;
            this.w = alturas.length * 6 + (alturas.length - 1) * 8;
            int max = 0;
            for (int a : alturas) max = Math.max(max, a);
            this.y = SUELO - max;
        }

        Obst(double x, int y) {                     // bicho volador
            this.x = x;
            this.tipo = TObst.BICHO;
            this.alturas = null;
            this.w = 18;
            this.y = y;
        }

        boolean choca(Rectangle r) {
            if (tipo == TObst.BICHO) return r.intersects(x + 3, y + 2, 12, 9);
            for (int i = 0; i < alturas.length; i++) {
                if (r.intersects(x + i * 14 + 1, SUELO - alturas[i], 4, alturas[i])) return true;
            }
            return false;
        }
    }

    private static class Bonus {
        double x, y, fase;
        final TBonus tipo;

        Bonus(double x, double y, TBonus tipo) {
            this.x = x;
            this.y = y;
            this.tipo = tipo;
        }
    }

    private static class Part {
        double x, y, vx, vy;
        int vida;
        final Color color;
        final int tam;

        Part(double x, double y, double vx, double vy, int vida, Color color, int tam) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.vida = vida; this.color = color; this.tam = tam;
        }
    }

    private static class Texto {
        final String s;
        final double x;
        double y;
        int vida = 45;
        final Color color;

        Texto(String s, double x, double y, Color color) {
            this.s = s; this.x = x; this.y = y; this.color = color;
        }
    }

    // ------------------------------------------------------------------ estado compartido (entre hilos)
    private volatile Estado estado = Estado.TITULO;
    private volatile boolean pausado = false;
    private volatile int pausaSeleccion = 0;
    private volatile boolean saltoPulsado, agachadoPulsado, saltoSolicitado, iniciarSolicitado;
    private volatile int partida = 0;
    private volatile long tiempoMs = 0;                 // lo escribe Hilo-Cronometro
    private volatile long glitchHasta = 0;
    private volatile String glitchMsg = "";
    private volatile long gameOverDesde = 0;
    private volatile int mejor = 0;
    private Runnable alMenuPrincipal = () -> { };

    private final ConcurrentLinkedQueue<Obst> pendientesObst = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Bonus> pendientesBonus = new ConcurrentLinkedQueue<>();
    private final Random rnd = new Random();

    // ------------------------------------------------------------------ estado del mundo (solo Hilo-Juego)
    private final List<Obst> obstaculos = new ArrayList<>();
    private final List<Bonus> bonuses = new ArrayList<>();
    private final List<Part> particulas = new ArrayList<>();
    private final List<Texto> textos = new ArrayList<>();

    private double ponyY = SUELO, vy = 0, velocidad = 2.5, dist = 0;
    private double sueloOff = 0, fondoOff = 0;
    private boolean enSuelo = true, agachado = false, nuevoRecord = false;
    private int vidas = 3, extra = 0, hitoAnterior = 0;
    private int invulnerable = 0, estrellaFrames = 0, relojFrames = 0;
    private int shake = 0, flashRojo = 0, hitstop = 0, destello = 0;
    private int animFrames = 0, reloj = 0;

    // ------------------------------------------------------------------ dibujo
    private final BufferedImage[] buffers = new BufferedImage[2];
    private int bufIdx = 0;
    private volatile BufferedImage listo;
    private BufferedImage scanlines, vineta;
    private final Random rndVisual = new Random();
    private final int[] estX = new int[40];
    private final int[] estY = new int[40];

    public PanelJuego() {
        setPreferredSize(new Dimension(SCR_W + 2 * MARCO, SCR_H + 2 * MARCO));
        setBackground(Color.BLACK);
        setFocusable(true);

        for (int i = 0; i < 2; i++) buffers[i] = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < estX.length; i++) {
            estX[i] = rnd.nextInt(W);
            estY[i] = rnd.nextInt(SUELO - 90);
        }

        construirOverlays();
        configurarTeclas();
        cargarMejor();
        dibujarFrame();
        Sonido.iniciar();

        Thread t = new Thread(this::bucleJuego, "Hilo-Juego");
        t.setDaemon(true);
        t.start();
    }

    // =================================================================== entrada

    private void configurarTeclas() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        for (String k : new String[] {"SPACE", "UP", "W"}) {
            im.put(KeyStroke.getKeyStroke("pressed " + k), "saltoOn");
            im.put(KeyStroke.getKeyStroke("released " + k), "saltoOff");
        }
        for (String k : new String[] {"DOWN", "S"}) {
            im.put(KeyStroke.getKeyStroke("pressed " + k), "agacharOn");
            im.put(KeyStroke.getKeyStroke("released " + k), "agacharOff");
        }
        im.put(KeyStroke.getKeyStroke("pressed P"), "pausa");
        im.put(KeyStroke.getKeyStroke("pressed ESCAPE"), "menuPausa");
        im.put(KeyStroke.getKeyStroke("pressed LEFT"), "pausaIzquierda");
        im.put(KeyStroke.getKeyStroke("pressed RIGHT"), "pausaDerecha");
        im.put(KeyStroke.getKeyStroke("pressed M"), "sonido");
        im.put(KeyStroke.getKeyStroke("pressed ENTER"), "enterJuego");

        am.put("saltoOn", accion(() -> teclaSalto(true)));
        am.put("saltoOff", accion(() -> teclaSalto(false)));
        am.put("agacharOn", accion(() -> agachadoPulsado = true));
        am.put("agacharOff", accion(() -> agachadoPulsado = false));
        am.put("pausa", accion(() -> { if (estado == Estado.JUGANDO) pausado = !pausado; }));
        am.put("menuPausa", accion(this::alternarMenuPausa));
        am.put("pausaIzquierda", accion(() -> seleccionarPausa(-1)));
        am.put("pausaDerecha", accion(() -> seleccionarPausa(1)));
        am.put("sonido", accion(() -> Sonido.setSilencio(!Sonido.isSilencio())));
        am.put("enterJuego", accion(() -> {
            if (pausado) activarPausa();
            else solicitarInicio();
        }));
        im.put(KeyStroke.getKeyStroke("pressed UP"), "pausaArriba");
        im.put(KeyStroke.getKeyStroke("pressed DOWN"), "pausaAbajo");
        am.put("pausaArriba", accion(() -> {
            if (pausado) seleccionarPausa(-1);
            else teclaSalto(true);
        }));
        am.put("pausaAbajo", accion(() -> {
            if (pausado) seleccionarPausa(1);
            else agachadoPulsado = true;
        }));
    }

    private static AbstractAction accion(Runnable r) {
        return new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
    }

    private void teclaSalto(boolean presionada) {
        saltoPulsado = presionada;
        if (!presionada) return;
        if (pausado) {
            activarPausa();
            return;
        }
        if (estado == Estado.JUGANDO) saltoSolicitado = true;
        else solicitarInicio();
    }

    private void solicitarInicio() {
        boolean puede = estado == Estado.TITULO
                || (estado == Estado.GAMEOVER && System.currentTimeMillis() - gameOverDesde > 700);
        if (puede) iniciarSolicitado = true;
    }

    /** Starts a game after the external menu and loading screen finish. */
    public void iniciarDesdeMenu() {
        solicitarInicio();
    }

    public void setAlMenuPrincipal(Runnable r) {
        alMenuPrincipal = r;
    }

    private void alternarMenuPausa() {
        if (estado != Estado.JUGANDO) return;
        pausado = !pausado;
        pausaSeleccion = 0;
        if (pausado) Sonido.setMusica(false);
        else Sonido.setMusica(true);
    }

    private void seleccionarPausa(int delta) {
        if (!pausado || estado != Estado.JUGANDO) return;
        pausaSeleccion = (pausaSeleccion + delta + 4) % 4;
        Sonido.reproducir(Sonido.Efecto.MOVE);
    }

    private void activarPausa() {
        if (!pausado || estado != Estado.JUGANDO) return;
        switch (pausaSeleccion) {
            case 0:
                pausado = false;
                Sonido.setMusica(true);
                break;
            case 1:
                pausado = false;
                iniciarSolicitado = true;
                estado = Estado.TITULO;
                Sonido.setMusica(false);
                break;
            case 2:
                Sonido.setSilencio(!Sonido.isSilencio());
                break;
            default:
                pausado = false;
                estado = Estado.TITULO;
                Sonido.setMusica(false);
                alMenuPrincipal.run();
        }
    }

    // =================================================================== récord (archivo)

    private void cargarMejor() {
        Path ruta = Paths.get("record_pony.txt");
        try {
            if (Files.exists(ruta)) {
                List<String> lineas = Files.readAllLines(ruta, StandardCharsets.UTF_8);
                if (!lineas.isEmpty()) mejor = Math.max(0, Integer.parseInt(lineas.get(0).trim()));
            }
        } catch (IOException | NumberFormatException e) {
            mejor = 0;
        }
    }

    private void guardarMejor(int valor) {
        new Thread(() -> {
            try {
                Files.write(Paths.get("record_pony.txt"),
                        Collections.singletonList(String.valueOf(valor)), StandardCharsets.UTF_8);
            } catch (IOException ignorada) { }
        }, "Hilo-Guardar").start();
    }

    // =================================================================== hilos auxiliares

    private void crearHilo(String nombre, Runnable r) {
        Thread t = new Thread(r, nombre);
        t.setDaemon(true);
        t.start();
    }

    private boolean activo(int id) {
        return estado == Estado.JUGANDO && id == partida;
    }

    /** Espera "ms" de juego: el tiempo en pausa no cuenta. */
    private void esperar(int id, long ms) {
        long restante = ms;
        while (restante > 0 && activo(id)) {
            dormir(50);
            if (!pausado) restante -= 50;
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void bucleObstaculos(int id) {
        esperar(id, 1200);
        while (activo(id)) {
            pendientesObst.add(crearObstaculo());
            esperar(id, 900 + rnd.nextInt(1300));
        }
    }

    private Obst crearObstaculo() {
        int seg = (int) (tiempoMs / 1000);
        int r = rnd.nextInt(100);
        if (seg >= 15 && r < 25) return new Obst(W + 10, SUELO - 30);   // bicho bajo: saltar o agacharse
        if (seg >= 15 && r < 45) return new Obst(W + 10, SUELO - 66);   // bicho alto: no saltar
        int n = 1 + rnd.nextInt(3);
        int[] alturas = new int[n];
        int[] opciones = {20, 28, 34};
        for (int i = 0; i < n; i++) alturas[i] = opciones[rnd.nextInt(opciones.length)];
        return new Obst(W + 10, alturas);
    }

    private void bucleBonus(int id) {
        esperar(id, 4000);
        while (activo(id)) {
            int r = rnd.nextInt(100);
            TBonus tipo = r < 50 ? TBonus.MANZANA : r < 70 ? TBonus.CORAZON : r < 85 ? TBonus.ESTRELLA : TBonus.RELOJ;
            double[] alturas = {SUELO - 18, SUELO - 62, SUELO - 95};
            pendientesBonus.add(new Bonus(W + 20, alturas[rnd.nextInt(alturas.length)], tipo));
            esperar(id, 6000 + rnd.nextInt(6000));
        }
    }

    /** Cronómetro: tiempo jugado, no avanza en pausa. */
    private void bucleCronometro(int id) {
        long local = 0;
        long ultimo = System.nanoTime();
        while (activo(id)) {
            dormir(10);
            long ahora = System.nanoTime();
            if (!pausado) local += (ahora - ultimo) / 1_000_000;
            ultimo = ahora;
            if (activo(id)) tiempoMs = local;
        }
    }

    private void bucleGlitch(int id) {
        while (activo(id)) {
            esperar(id, 12000 + rnd.nextInt(14000));
            if (!activo(id)) break;
            lanzarGlitch(350 + rnd.nextInt(300), MENSAJES[rnd.nextInt(MENSAJES.length)], true);
        }
    }

    private void lanzarGlitch(long ms, String mensaje, boolean sonido) {
        glitchMsg = mensaje == null ? "" : mensaje;
        glitchHasta = System.currentTimeMillis() + ms;
        if (sonido) Sonido.reproducir(Sonido.Efecto.GLITCH);
    }

    // =================================================================== hilo principal del juego

    private void bucleJuego() {
        long siguiente = System.nanoTime();
        while (true) {
            try {
                reloj++;
                if (iniciarSolicitado) {
                    iniciarSolicitado = false;
                    if (estado != Estado.JUGANDO) iniciarPartida();
                }
                if (estado == Estado.JUGANDO) {
                    if (!pausado) actualizar();
                } else if (estado == Estado.TITULO) {
                    sueloOff += 2.5;
                    fondoOff += 2.5;
                    if (rnd.nextInt(600) == 0) lanzarGlitch(300, MENSAJES[rnd.nextInt(MENSAJES.length)], false);
                }
                dibujarFrame();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            siguiente += 16_666_667L;
            long espera = (siguiente - System.nanoTime()) / 1_000_000;
            if (espera > 0) dormir(espera);
            else siguiente = System.nanoTime();
        }
    }

    private void iniciarPartida() {
        final int id = ++partida;
        obstaculos.clear();
        bonuses.clear();
        particulas.clear();
        textos.clear();
        pendientesObst.clear();
        pendientesBonus.clear();

        ponyY = SUELO; vy = 0; enSuelo = true; agachado = false;
        vidas = 3; extra = 0; dist = 0; hitoAnterior = 0;
        invulnerable = 0; estrellaFrames = 0; relojFrames = 0;
        shake = 0; flashRojo = 0; hitstop = 0; destello = 0; animFrames = 0;
        nuevoRecord = false;
        tiempoMs = 0;
        velocidad = 4.6;
        pausado = false;
        estado = Estado.JUGANDO;
        Sonido.setMusica(true);

        crearHilo("Hilo-Obstaculos", () -> bucleObstaculos(id));
        crearHilo("Hilo-Bonus", () -> bucleBonus(id));
        crearHilo("Hilo-Cronometro", () -> bucleCronometro(id));
        crearHilo("Hilo-Glitch", () -> bucleGlitch(id));
    }

    private void terminarPartida() {
        estado = Estado.GAMEOVER;
        gameOverDesde = System.currentTimeMillis();
        Sonido.setMusica(false);
        Sonido.reproducir(Sonido.Efecto.MUERTE);
        int total = total();
        if (total > mejor) {
            mejor = total;
            nuevoRecord = true;
            guardarMejor(total);
        }
        lanzarGlitch(900, "FIN", false);
    }

    private int total() {
        return (int) dist + extra;
    }

    // =================================================================== lógica

    private void actualizar() {
        // objetos creados por los otros hilos
        Obst po;
        while ((po = pendientesObst.poll()) != null) obstaculos.add(po);
        Bonus pb;
        while ((pb = pendientesBonus.poll()) != null) {
            boolean choca = true;
            while (choca) {
                choca = false;
                for (Obst o : obstaculos) {
                    if (Math.abs(o.x - pb.x) < 70) {
                        pb.x += 60;
                        choca = true;
                        break;
                    }
                }
            }
            bonuses.add(pb);
        }

        // congelado de impacto (hit-stop)
        if (hitstop > 0) {
            hitstop--;
            return;
        }

        animFrames++;
        double base = Math.min(10.0, 4.6 + tiempoMs / 1000.0 * 0.07);
        velocidad = base * (relojFrames > 0 ? 0.6 : 1.0);
        if (estrellaFrames > 0) estrellaFrames--;
        if (relojFrames > 0) relojFrames--;
        if (invulnerable > 0) invulnerable--;
        if (shake > 0) shake--;
        if (flashRojo > 0) flashRojo--;
        if (destello > 0) destello--;

        // control del pony
        boolean agacha = agachadoPulsado;
        if (enSuelo) {
            agachado = agacha;
            if (saltoSolicitado || saltoPulsado) {
                vy = -8.5;
                enSuelo = false;
                agachado = false;
                Sonido.reproducir(Sonido.Efecto.SALTO);
                explosion(PX + 14, SUELO, 6, new Color(150, 190, 100), new Color(110, 150, 70));
            }
        } else {
            double gr = 0.62;
            if (saltoPulsado && vy < 0) gr = 0.38;   // salto más alto si se mantiene la tecla
            if (agacha) gr = 1.5;                    // caída rápida
            vy += gr;
            ponyY += vy;
            if (ponyY >= SUELO) {
                ponyY = SUELO;
                vy = 0;
                enSuelo = true;
                explosion(PX + 14, SUELO, 5, new Color(150, 190, 100), new Color(110, 150, 70));
            }
        }
        saltoSolicitado = false;

        // desplazamiento
        sueloOff += velocidad;
        fondoOff += velocidad;
        for (Obst o : obstaculos) o.x -= velocidad;
        for (Bonus b : bonuses) {
            b.x -= velocidad;
            b.fase += 0.12;
        }
        obstaculos.removeIf(o -> o.x + o.w < -30);
        bonuses.removeIf(b -> b.x < -30);

        // puntuación e hitos cada 100 puntos
        dist += velocidad * 0.04;
        int total = total();
        if (total / 100 > hitoAnterior) {
            hitoAnterior = total / 100;
            destello = 30;
            Sonido.reproducir(Sonido.Efecto.HITO);
        }

        // colisiones con obstáculos
        Rectangle pr = rectPony();
        Iterator<Obst> it = obstaculos.iterator();
        while (it.hasNext()) {
            Obst o = it.next();
            if (!o.choca(pr)) continue;
            if (estrellaFrames > 0) {
                romper(o);
                it.remove();
            } else if (invulnerable <= 0) {
                golpe();
                break;
            }
        }

        // colisiones con bonus
        Iterator<Bonus> ib = bonuses.iterator();
        while (ib.hasNext()) {
            Bonus b = ib.next();
            if (pr.intersects(b.x - 8, b.y - 8, 16, 16)) {
                recoger(b);
                ib.remove();
            }
        }

        // polvo y estela arcoíris
        if (enSuelo && animFrames % 6 == 0) {
            particulas.add(new Part(PX + 4, SUELO - 1, -velocidad * 0.3, -0.4, 14, new Color(150, 190, 100), 2));
        }
        if (estrellaFrames > 0) {
            Color arco = Color.getHSBColor((reloj % 60) / 60f, 0.7f, 1f);
            particulas.add(new Part(PX + 4, ponyY - 8 - rnd.nextInt(12), -velocidad * 0.6, 0, 18, arco, 3));
        }

        for (Part p : particulas) {
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.18;
            p.vida--;
        }
        particulas.removeIf(p -> p.vida <= 0);
        for (Texto t : textos) {
            t.y -= 0.6;
            t.vida--;
        }
        textos.removeIf(t -> t.vida <= 0);
    }

    private Rectangle rectPony() {
        int alto = agachado ? 12 : 18;
        return new Rectangle(PX + 4, (int) ponyY - alto, 16, alto);
    }

    private void golpe() {
        vidas--;
        invulnerable = 100;
        shake = 14;
        flashRojo = 14;
        hitstop = 6;
        explosion(PX + 16, ponyY - 14, 30, new Color(255, 80, 90), new Color(214, 150, 238), Color.WHITE);
        Sonido.reproducir(Sonido.Efecto.GOLPE);
        lanzarGlitch(380, null, false);
        if (vidas <= 0) terminarPartida();
    }

    private void romper(Obst o) {
        extra += 25;
        shake = 5;
        explosion(o.x + o.w / 2.0, o.y + 10, 16, new Color(190, 120, 60), new Color(255, 225, 70));
        textos.add(new Texto("+25", o.x, o.y - 4, new Color(255, 225, 70)));
        Sonido.reproducir(Sonido.Efecto.ROMPER);
    }

    private void recoger(Bonus b) {
        switch (b.tipo) {
            case MANZANA:
                extra += 50;
                textos.add(new Texto("+50", b.x, b.y - 6, new Color(255, 120, 120)));
                explosion(b.x, b.y, 12, new Color(220, 40, 50), new Color(90, 190, 70));
                Sonido.reproducir(Sonido.Efecto.BONUS);
                break;
            case ESTRELLA:
                estrellaFrames = 6 * 60;
                textos.add(new Texto("INVENCIBLE!", b.x - 20, b.y - 6, new Color(255, 225, 70)));
                explosion(b.x, b.y, 20, new Color(255, 225, 70), Color.WHITE);
                Sonido.reproducir(Sonido.Efecto.POWERUP);
                break;
            case RELOJ:
                relojFrames = 5 * 60;
                textos.add(new Texto("LENTO", b.x - 6, b.y - 6, new Color(140, 190, 255)));
                explosion(b.x, b.y, 14, new Color(140, 190, 255), Color.WHITE);
                Sonido.reproducir(Sonido.Efecto.RELOJ);
                break;
            default:
                vidas = Math.min(5, vidas + 1);
                textos.add(new Texto("+1 VIDA", b.x - 10, b.y - 6, new Color(255, 100, 140)));
                explosion(b.x, b.y, 14, new Color(255, 100, 140), Color.WHITE);
                Sonido.reproducir(Sonido.Efecto.VIDA);
        }
    }

    private void explosion(double x, double y, int n, Color... cols) {
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * 2 * Math.PI;
            double v = 1 + rnd.nextDouble() * 4;
            particulas.add(new Part(x, y, Math.cos(a) * v, Math.sin(a) * v - 1,
                    25 + rnd.nextInt(20), cols[rnd.nextInt(cols.length)], 2 + rnd.nextInt(2)));
        }
    }

    // =================================================================== dibujo del mundo (en Hilo-Juego)

    private void dibujarFrame() {
        BufferedImage img = buffers[bufIdx];
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        int sx = 0, sy = 0;
        if (shake > 0) {
            int m = shake > 8 ? 4 : 2;
            sx = rnd.nextInt(2 * m + 1) - m;
            sy = rnd.nextInt(2 * m + 1) - m;
        }
        g.translate(sx, sy);

        double noche = 0;
        if (estado != Estado.TITULO) {
            double fase = (tiempoMs % 90000) / 90000.0;
            noche = 0.5 - 0.5 * Math.cos(2 * Math.PI * fase);
        }

        dibujarFondo(g, noche);
        dibujarSuelo(g, noche);

        for (Bonus b : bonuses) {
            int bx = (int) b.x;
            int by = (int) (b.y + Math.sin(b.fase) * 3);
            switch (b.tipo) {
                case MANZANA:  Sprites.manzana(g, bx, by); break;
                case ESTRELLA: Sprites.estrella(g, bx, by, b.fase * 0.5); break;
                case RELOJ:    Sprites.reloj(g, bx, by); break;
                default:       Sprites.corazon(g, bx - 4, by - 3, new Color(255, 80, 120));
            }
        }

        for (Obst o : obstaculos) {
            if (o.tipo == TObst.BICHO) {
                int bob = (int) (Math.sin(reloj * 0.15 + o.x * 0.02) * 2);
                Sprites.bicho(g, (int) o.x, o.y + bob, (reloj / 6) % 2);
            } else {
                for (int i = 0; i < o.alturas.length; i++) {
                    Sprites.poste(g, (int) o.x + i * 14, SUELO, 6, o.alturas[i]);
                }
            }
        }

        dibujarPony(g);

        for (Part p : particulas) {
            int a = Math.min(255, p.vida * 14);
            g.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
            g.fillRect((int) p.x, (int) p.y, p.tam, p.tam);
        }
        g.setFont(FUENTE);
        for (Texto t : textos) texto(g, t.s, (int) t.x, (int) t.y, t.color);

        if (flashRojo > 0) {
            g.setColor(new Color(255, 0, 40, Math.min(150, flashRojo * 10)));
            g.fillRect(-8, -8, W + 16, H + 16);
        }

        g.translate(-sx, -sy);
        dibujarHUD(g);
        dibujarPantallas(g);

        g.dispose();
        listo = img;
        bufIdx ^= 1;
        repaint();
    }

    private void dibujarPony(Graphics2D g) {
        Color cuerpo = PONY_ROSA;
        if (estrellaFrames > 0) cuerpo = Color.getHSBColor((reloj % 60) / 60f, 0.45f, 1f);
        if (estado == Estado.GAMEOVER) cuerpo = new Color(150, 150, 165);
        boolean visible = invulnerable <= 0 || (invulnerable / 4) % 2 == 0;
        if (!visible) return;
        int frame = (estado == Estado.GAMEOVER) ? 0 : (estado == Estado.TITULO ? (reloj / 6) % 2 : (animFrames / 5) % 2);
        AffineTransform ponyTransform = g.getTransform();
        g.translate(PX, ponyY);
        g.scale(0.68, 0.68);
        Sprites.pony(g, 0, 0, frame, agachado, !enSuelo, cuerpo);
        g.setTransform(ponyTransform);
    }

    private void dibujarFondo(Graphics2D g, double noche) {
        Color cieloAlto = Sprites.mezcla(new Color(85, 135, 205), new Color(14, 10, 40), noche);
        Color cieloBajo = Sprites.mezcla(new Color(215, 200, 230), new Color(75, 40, 105), noche);
        g.setPaint(new GradientPaint(0, 0, cieloAlto, 0, SUELO, cieloBajo));
        g.fillRect(-8, -8, W + 16, SUELO + 8);

        if (noche > 0.35) {
            int a = (int) (Math.min(1, (noche - 0.35) / 0.4) * 255);
            for (int i = 0; i < estX.length; i++) {
                if (((reloj + i * 7) / 20) % 5 == 0) continue;
                g.setColor(new Color(255, 255, 255, a));
                g.fillRect(estX[i], estY[i], (i % 3 == 0) ? 2 : 1, (i % 3 == 0) ? 2 : 1);
            }
        }

        int solY = (int) (88 + noche * 140);
        g.setColor(Sprites.mezcla(new Color(248, 220, 95), new Color(200, 90, 60), noche));
        g.fillOval(300 - 38, solY - 38, 76, 76);

        if (noche > 0.4) {
            int a = (int) (Math.min(1, (noche - 0.4) / 0.4) * 255);
            g.setColor(new Color(235, 235, 215, a));
            g.fillOval(110 - 16, 70 - 16, 32, 32);
            g.setColor(new Color(cieloAlto.getRed(), cieloAlto.getGreen(), cieloAlto.getBlue(), a));
            g.fillOval(118 - 16, 66 - 16, 28, 28);
        }

        colinas(g, fondoOff * 0.12, 150, 24, 10,
                Sprites.mezcla(new Color(160, 200, 95), new Color(45, 50, 95), noche));
        colinas(g, fondoOff * 0.30, 178, 16, 8,
                Sprites.mezcla(new Color(120, 170, 60), new Color(30, 60, 60), noche));

        double toff = fondoOff * 0.55;
        int sep = 190;
        long k0 = (long) Math.floor(toff / sep);
        for (int i = -1; i <= (W / sep) + 2; i++) {
            long k = k0 + i;
            if (Math.floorMod(k, 3L) == 1) continue;
            int x = (int) (i * sep - (toff - k0 * sep)) + (int) Math.floorMod(k * 37, 50L);
            Sprites.arbol(g, x, SUELO + 1, noche, (int) Math.floorMod(k * 13, 3L));
        }
    }

    private void colinas(Graphics2D g, double off, int base, double amp1, double amp2, Color col) {
        g.setColor(col);
        for (int x = -8; x < W + 8; x += 4) {
            double hh = base + amp1 * Math.sin((x + off) / 90.0) + amp2 * Math.sin((x + off) / 37.0);
            int top = ((int) hh / 4) * 4;
            g.fillRect(x, top, 4, SUELO - top);
        }
    }

    private void dibujarSuelo(Graphics2D g, double noche) {
        g.setColor(Sprites.mezcla(new Color(70, 120, 45), new Color(20, 50, 40), noche));
        g.fillRect(-8, SUELO, W + 16, H - SUELO + 8);
        Color clara = Sprites.mezcla(new Color(120, 175, 80), new Color(45, 95, 70), noche);
        g.setColor(clara);
        g.fillRect(-8, SUELO, W + 16, 2);
        int off = (int) (sueloOff % 16);
        for (int fila = 0; fila < 3; fila++) {
            int y = SUELO + 8 + fila * 16;
            for (int x = -16; x < W + 16; x += 16) {
                int xx = x - off + (fila % 2) * 8;
                g.drawLine(xx, y, xx + 4, y + 4);
                g.drawLine(xx + 4, y + 4, xx + 8, y);
            }
        }
    }

    // =================================================================== HUD y pantallas

    private static String formato(long ms) {
        long m = ms / 60000;
        long s = (ms / 1000) % 60;
        long d = (ms / 100) % 10;
        return String.format("%02d:%02d.%d", m, s, d);
    }

    private void texto(Graphics2D g, String s, int x, int y, Color c) {
        g.setColor(new Color(38, 12, 72));
        g.drawString(s, x + 2, y + 2);
        g.drawString(s, x + 1, y + 1);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    private void textoCentrado(Graphics2D g, String s, int cx, int y, Color c) {
        FontMetrics fm = g.getFontMetrics();
        texto(g, s, cx - fm.stringWidth(s) / 2, y, c);
    }

    private void dibujarHUD(Graphics2D g) {
        g.setFont(FUENTE);
        if (estado != Estado.TITULO) {
            for (int i = 0; i < vidas; i++) {
                AffineTransform vida = g.getTransform();
                g.translate(8 + i * 20, 2);
                g.scale(1.7, 1.7);
                Sprites.corazon(g, 0, 0, new Color(255, 65, 95));
                g.setTransform(vida);
            }
            texto(g, "TIEMPO " + formato(tiempoMs), 8, 36, UI.AMARILLO);

            int by = 40;
            if (estrellaFrames > 0) {
                Sprites.estrella(g, 14, by + 6, 0);
                texto(g, (estrellaFrames / 60 + 1) + " s", 26, by + 10, UI.AMARILLO);
                by += 16;
            }
            if (relojFrames > 0) {
                Sprites.reloj(g, 14, by + 6);
                texto(g, (relojFrames / 60 + 1) + " s", 26, by + 10, new Color(190, 220, 255));
            }
        }

        String hi = String.format("MEJOR %05d", Math.max(mejor, total()));
        String sc = String.format("%05d", total());
        boolean ocultar = destello > 0 && (destello / 4) % 2 == 0;
        texto(g, hi, W - 150, 20, UI.AMARILLO);
        if (estado != Estado.TITULO && !ocultar) texto(g, sc, W - 62, 20, Color.WHITE);

        g.setFont(FUENTE_P);
        texto(g, Sonido.isSilencio() ? "SONIDO APAGADO (M)" : "SONIDO ACTIVO (M)",
                W - 135, 34, Color.WHITE);

        if (estado != Estado.TITULO) {
            int bx = 70, bw = W - 140, by = H - 16;
            g.setColor(new Color(70, 40, 150));
            g.fillRect(bx, by, bw, 3);
            int mx = bx + (int) ((total() % 100) / 100.0 * bw);
            g.setColor(new Color(110, 75, 210));
            g.fillPolygon(new int[] {mx - 4, mx + 4, mx}, new int[] {by + 11, by + 11, by + 5}, 3);
        }
    }

    private void dibujarPantallas(Graphics2D g) {
        boolean parpadeo = (reloj / 30) % 2 == 0;
        if (estado == Estado.TITULO) {
            g.setColor(new Color(10, 5, 30, 130));
            g.fillRect(70, 40, W - 140, 150);
            g.setFont(FUENTE_G);
            textoCentrado(g, "PONY ISLAND", W / 2, 88, new Color(255, 215, 90));
            g.setFont(FUENTE_P);
            textoCentrado(g, "una carrera contra la oscuridad", W / 2, 106, new Color(210, 190, 240));
            g.setFont(FUENTE_M);
            if (parpadeo) textoCentrado(g, "PRESIONA ESPACIO", W / 2, 140, Color.WHITE);
            g.setFont(FUENTE_P);
            textoCentrado(g, "ESPACIO/ARRIBA: saltar   ABAJO: agacharse", W / 2, 162, new Color(220, 220, 235));
            textoCentrado(g, "P: pausa   M: sonido", W / 2, 176, new Color(220, 220, 235));
        } else if (estado == Estado.GAMEOVER) {
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(0, 0, W, H);
            g.setFont(FUENTE_G);
            textoCentrado(g, "FIN DEL JUEGO", W / 2, 100, new Color(255, 70, 90));
            g.setFont(FUENTE_M);
            textoCentrado(g, "PUNTOS " + total() + "   TIEMPO " + formato(tiempoMs), W / 2, 130, Color.WHITE);
            textoCentrado(g, nuevoRecord ? "NUEVO RECORD!" : "MEJOR " + mejor, W / 2, 154,
                    nuevoRecord ? new Color(255, 225, 70) : new Color(200, 200, 215));
            if (parpadeo) textoCentrado(g, "ESPACIO PARA REINTENTAR", W / 2, 190, Color.WHITE);
        } else if (pausado) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(22, 18, 55, 235));
            g.fillRoundRect(70, 12, W - 140, 246, 10, 10);
            g.setColor(new Color(255, 220, 90));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(70, 12, W - 140, 246, 10, 10);
            g.setFont(new Font("Comic Sans MS", Font.BOLD, 30));
            textoCentrado(g, "MENÚ", W / 2, 55, Color.WHITE);
            g.setFont(new Font("Comic Sans MS", Font.BOLD, 16));
            String[] opciones = {
                "CONTINUAR", "NUEVA PARTIDA",
                "SONIDO: " + (Sonido.isSilencio() ? "APAGADO" : "ENCENDIDO"),
                "SALIR AL MENÚ"
            };
            for (int i = 0; i < opciones.length; i++) {
                Color color = i == pausaSeleccion ? new Color(255, 220, 0) : Color.WHITE;
                String texto = i == pausaSeleccion ? "> " + opciones[i] + " <" : opciones[i];
                textoCentrado(g, texto, W / 2, 91 + i * 32, color);
            }
            g.setFont(new Font("Comic Sans MS", Font.PLAIN, 10));
            textoCentrado(g, "ARRIBA/ABAJO: mover   ENTER/ESPACIO: elegir   ESC: cerrar",
                    W / 2, 242, new Color(210, 205, 225));
        }
    }

    // =================================================================== efecto "tele antigua" (en el hilo de Swing)

    private void construirOverlays() {
        scanlines = new BufferedImage(SCR_W, SCR_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D s = scanlines.createGraphics();
        s.setColor(new Color(0, 0, 0, 70));
        for (int y = 0; y < SCR_H; y += 3) s.fillRect(0, y, SCR_W, 1);
        s.dispose();

        vineta = new BufferedImage(SCR_W, SCR_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D v = vineta.createGraphics();
        v.translate(SCR_W / 2.0, SCR_H / 2.0);
        v.scale(1, SCR_H / (double) SCR_W);
        v.setPaint(new RadialGradientPaint(new Point2D.Float(0, 0), SCR_W * 0.72f,
                new float[] {0.55f, 1f},
                new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 215)}));
        v.fillRect(-SCR_W, -SCR_W, 2 * SCR_W, 2 * SCR_W);
        v.dispose();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();

        g.setPaint(new GradientPaint(0, 0, new Color(48, 44, 40), 0, getHeight(), new Color(22, 20, 18)));
        g.fillRect(0, 0, getWidth(), getHeight());

        int ox = (getWidth() - SCR_W) / 2;
        int oy = (getHeight() - SCR_H) / 2;
        Shape pantalla = new RoundRectangle2D.Double(ox, oy, SCR_W, SCR_H, 70, 70);
        Shape clipPrevio = g.getClip();
        g.setClip(pantalla);

        g.setColor(Color.BLACK);
        g.fillRect(ox, oy, SCR_W, SCR_H);

        BufferedImage img = listo;
        if (img != null) dibujarImagen(g, img, ox, oy);

        g.drawImage(scanlines, ox, oy, null);
        g.drawImage(vineta, ox, oy, null);

        // parpadeo leve de la pantalla
        g.setColor(new Color(0, 0, 0, rndVisual.nextInt(14)));
        g.fillRect(ox, oy, SCR_W, SCR_H);

        // ruido de estática
        if (estado == Estado.GAMEOVER || System.currentTimeMillis() < glitchHasta) {
            dibujarRuido(g, ox, oy);
        }

        g.setClip(clipPrevio);
        g.setColor(new Color(8, 8, 8));
        g.setStroke(new BasicStroke(8f));
        g.draw(pantalla);
        g.dispose();
    }

    private void dibujarImagen(Graphics2D g, BufferedImage img, int ox, int oy) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        boolean glitch = System.currentTimeMillis() < glitchHasta;
        if (!glitch) {
            g.drawImage(img, ox, oy, SCR_W, SCR_H, null);
            return;
        }

        int franja = 12;
        for (int sy = 0; sy < H; sy += franja) {
            int sh = Math.min(franja, H - sy);
            int desp = rndVisual.nextInt(100) < 35 ? (rndVisual.nextInt(31) - 15) * ESC : 0;
            BufferedImage sub = img.getSubimage(0, sy, W, sh);
            g.drawImage(sub, ox + desp, oy + sy * ESC, W * ESC, sh * ESC, null);
        }

        Composite previo = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
        g.drawImage(img, ox + 8, oy, SCR_W, SCR_H, null);
        g.setComposite(previo);
        g.setColor(new Color(255, 0, 80, 35));
        g.fillRect(ox, oy, SCR_W, SCR_H);

        String m = glitchMsg;
        if (m != null && !m.isEmpty()) {
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 36));
            FontMetrics fm = g.getFontMetrics();
            int x = ox + (SCR_W - fm.stringWidth(m)) / 2 + rndVisual.nextInt(9) - 4;
            int y = oy + SCR_H / 2 + rndVisual.nextInt(9) - 4;
            g.setColor(new Color(60, 0, 0));
            g.drawString(m, x + 3, y + 3);
            g.setColor(new Color(255, 30, 60));
            g.drawString(m, x, y);
        }
    }

    private void dibujarRuido(Graphics2D g, int ox, int oy) {
        int rw = SCR_W / 8, rh = SCR_H / 8;
        BufferedImage ruido = new BufferedImage(rw, rh, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                int v = rndVisual.nextInt(256);
                ruido.setRGB(x, y, (40 << 24) | (v << 16) | (v << 8) | v);
            }
        }
        g.drawImage(ruido, ox, oy, SCR_W, SCR_H, null);
    }

    private void telarana(Graphics2D g, int x, int y, int dir) {
        g.setColor(new Color(225, 225, 225, 85));
        int largo = 120;
        int rayos = 5;
        for (int i = 0; i < rayos; i++) {
            double a = Math.toRadians(i * 90.0 / (rayos - 1));
            g.drawLine(x, y, x + (int) (dir * Math.cos(a) * largo), y + (int) (Math.sin(a) * largo));
        }
        for (int r = 25; r <= largo; r += 25) {
            for (int i = 0; i < rayos - 1; i++) {
                double a1 = Math.toRadians(i * 90.0 / (rayos - 1));
                double a2 = Math.toRadians((i + 1) * 90.0 / (rayos - 1));
                double am = (a1 + a2) / 2;
                double sag = r * 0.88;
                g.drawLine(x + (int) (dir * Math.cos(a1) * r), y + (int) (Math.sin(a1) * r),
                           x + (int) (dir * Math.cos(am) * sag), y + (int) (Math.sin(am) * sag));
                g.drawLine(x + (int) (dir * Math.cos(am) * sag), y + (int) (Math.sin(am) * sag),
                           x + (int) (dir * Math.cos(a2) * r), y + (int) (Math.sin(a2) * r));
            }
        }
    }
}
