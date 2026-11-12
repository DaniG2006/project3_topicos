package ponyrun;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Loading screen shown after pressing NEW GAME.
 *   Loader-Thread            -> goes through the loading steps and fills the bar
 *   Loading-Animation-Thread -> smooth bar, rotating sunburst, running pony (~60 FPS)
 *   Dots-Thread              -> animates the "..." after the message
 */
public class PantallaCarga extends JPanel {

    private static final String[] PASOS = {
        "Despertando al pony", "Puliendo el cuerno", "Plantando manzanos",
        "Afinando los sonidos", "Escondiendo al diablo"
    };
    private static final Font F_TITULO = new Font("Comic Sans MS", Font.BOLD, 84);
    private static final Font F_TEXTO = new Font("Comic Sans MS", Font.BOLD, 24);
    private static final Font F_PCT = new Font("Comic Sans MS", Font.BOLD, 22);

    private volatile double objetivo = 0;
    private volatile double mostrado = 0;
    private volatile double t = 0;
    private volatile int paso = 0;
    private volatile int puntos = 0;
    private volatile boolean corriendo = false;

    public PantallaCarga() {
        setPreferredSize(new Dimension(UI.W, UI.H));
        setFocusable(true);
    }

    /** Starts the loading threads. "alTerminar" runs on the Swing thread when the bar is full. */
    public void iniciar(Runnable alTerminar) {
        objetivo = 0;
        mostrado = 0;
        paso = 0;
        puntos = 0;
        corriendo = true;

        Thread carga = new Thread(() -> bucleCarga(alTerminar), "Loader-Thread");
        Thread anim = new Thread(this::bucleAnimacion, "Loading-Animation-Thread");
        Thread dots = new Thread(this::bucleTexto, "Dots-Thread");
        anim.setDaemon(true);
        dots.setDaemon(true);
        carga.setDaemon(true);
        anim.start();
        dots.start();
        carga.start();
    }

    private void bucleCarga(Runnable alTerminar) {
        for (int i = 0; i < PASOS.length; i++) {
            paso = i;
            Sonido.reproducir(Sonido.Efecto.HITO);
            int sub = 20;
            for (int k = 1; k <= sub; k++) {
                objetivo = (i + k / (double) sub) / PASOS.length * 100;
                dormir(28);
            }
        }
        objetivo = 100;
        Sonido.reproducir(Sonido.Efecto.POWERUP);
        dormir(700);
        corriendo = false;
        SwingUtilities.invokeLater(alTerminar);
    }

    private void bucleAnimacion() {
        while (corriendo) {
            t += 0.016;
            mostrado += (objetivo - mostrado) * 0.12;
            repaint();
            dormir(16);
        }
    }

    private void bucleTexto() {
        while (corriendo) {
            puntos = (puntos + 1) % 4;
            dormir(350);
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double tt = t;
        double prog = Math.max(0, Math.min(100, mostrado));

        UI.rayos(g, tt);
        UI.sol(g, tt);
        UI.colinas(g);

        UI.textoContorno(g, "CARGANDO", F_TITULO, UI.W / 2.0, 150, UI.AMARILLO, UI.MORADO, 14f);

        // progress bar
        int bx = 250;
        int by = 340;
        int bw = 500;
        int bh = 40;
        g.setColor(UI.MORADO);
        g.fillRect(bx + 5, by + 8, bw, bh);
        g.setColor(new Color(90, 45, 150));
        g.fillRect(bx, by, bw, bh);
        int lleno = (int) ((bw - 8) * prog / 100.0);
        g.setPaint(new GradientPaint(bx, by, new Color(255, 245, 90), bx, by + bh, new Color(255, 170, 30)));
        g.fillRect(bx + 4, by + 4, lleno, bh - 8);
        g.setColor(UI.MORADO);
        g.setStroke(new java.awt.BasicStroke(5f));
        g.drawRect(bx, by, bw, bh);
        UI.textoContorno(g, (int) prog + "%", F_PCT, UI.W / 2.0, by + 28, Color.WHITE, UI.MORADO, 5f);

        // pony running along the bar
        double px = bx + 4 + lleno - 32 * 0.55;
        AffineTransform at = g.getTransform();
        g.translate(px, by - 4);
        Sprites.pony(g, 0, 0, (int) (tt * 14), false, false, UI.SILUETA);
        g.setTransform(at);

        // current step
        StringBuilder sb = new StringBuilder(PASOS[Math.min(paso, PASOS.length - 1)]);
        for (int i = 0; i < puntos; i++) sb.append('.');
        UI.textoContorno(g, sb.toString(), F_TEXTO, UI.W / 2.0, by + 95, Color.WHITE, UI.MORADO, 6f);
        g.dispose();
    }
}
