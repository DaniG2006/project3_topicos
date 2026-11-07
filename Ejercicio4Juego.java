import java.awt.AlphaComposite;
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
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Ejercicio 4 (iniciativa propia): "Atrapa las estrellas".
 * Hilos del juego:
 *   Hilo-Juego      -> mueve objetos, detecta colisiones y repinta (~60 FPS)
 *   Hilo-Generador  -> crea estrellas y bombas que caen (cada vez más rápido)
 *   Hilo-Tiempo     -> cuenta regresiva de 60 segundos
 */
public class Ejercicio4Juego extends JPanel {

    private static class Objeto {
        final double x;
        double y;
        final double vy;
        final boolean bomba;

        Objeto(double x, double vy, boolean bomba) {
            this.x = x;
            this.y = -20;
            this.vy = vy;
            this.bomba = bomba;
        }
    }

    private final List<Objeto> objetos = new CopyOnWriteArrayList<>();
    private final Random rnd = new Random();
    private final Lienzo lienzo = new Lienzo();
    private final JButton btnIniciar = new JButton("Iniciar / Reiniciar");

    private volatile boolean jugando = false;
    private volatile boolean finJuego = false;
    private volatile boolean izq = false;
    private volatile boolean der = false;
    private volatile int partida = 0;
    private volatile int puntos = 0;
    private volatile int vidas = 3;
    private volatile int tiempo = 60;
    private volatile double jugadorX = 400;

    public Ejercicio4Juego() {
        setLayout(new BorderLayout());

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        btnIniciar.setFocusable(false); // para que el teclado siempre vaya al juego
        barra.add(btnIniciar);
        barra.add(new JLabel("Mover: flechas o A/D  |  Estrella = +10 puntos  |  Bomba = -1 vida"));

        add(barra, BorderLayout.NORTH);
        add(lienzo, BorderLayout.CENTER);

        btnIniciar.addActionListener(e -> iniciar());
    }

    private synchronized void iniciar() {
        final int id = ++partida;
        objetos.clear();
        puntos = 0;
        vidas = 3;
        tiempo = 60;
        finJuego = false;
        jugadorX = Math.max(lienzo.getWidth(), 100) / 2.0;
        jugando = true;
        lienzo.requestFocusInWindow();

        crearHilo("Hilo-Juego", () -> bucleJuego(id));
        crearHilo("Hilo-Generador", () -> bucleGenerador(id));
        crearHilo("Hilo-Tiempo", () -> bucleTiempo(id));
    }

    private void crearHilo(String nombre, Runnable r) {
        Thread t = new Thread(r, nombre);
        t.setDaemon(true);
        t.start();
    }

    /** Un hilo sigue vivo solo mientras la partida a la que pertenece esté activa. */
    private boolean activo(int id) {
        return jugando && id == partida;
    }

    private synchronized void terminar(int id) {
        if (id != partida || !jugando) return;
        jugando = false;
        finJuego = true;
        SwingUtilities.invokeLater(lienzo::repaint);
    }

    private void bucleJuego(int id) {
        while (activo(id)) {
            int w = lienzo.getWidth();
            int h = lienzo.getHeight();
            int dir = (der ? 1 : 0) - (izq ? 1 : 0);
            jugadorX = Math.max(40, Math.min(w - 40, jugadorX + dir * 9));

            Rectangle2D.Double jugador = new Rectangle2D.Double(jugadorX - 40, h - 40, 80, 18);
            for (Objeto o : objetos) {
                o.y += o.vy;
                if (jugador.intersects(o.x - 14, o.y - 14, 28, 28)) {
                    objetos.remove(o);
                    if (o.bomba) {
                        vidas--;
                        if (vidas <= 0) terminar(id);
                    } else {
                        puntos += 10;
                    }
                } else if (o.y > h + 20) {
                    objetos.remove(o);
                }
            }
            lienzo.repaint();
            dormir(16);
        }
        lienzo.repaint();
    }

    private void bucleGenerador(int id) {
        while (activo(id)) {
            dormir(Math.max(250, 800 - puntos * 2L));
            if (!activo(id)) break;
            int w = Math.max(lienzo.getWidth(), 100);
            double x = 20 + rnd.nextInt(w - 40);
            double vy = 3 + rnd.nextDouble() * 3 + puntos / 150.0;
            objetos.add(new Objeto(x, vy, rnd.nextInt(100) < 28));
        }
    }

    private void bucleTiempo(int id) {
        while (activo(id) && tiempo > 0) {
            dormir(1000);
            if (activo(id)) tiempo--;
        }
        if (activo(id)) terminar(id);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    /** Zona de dibujo del juego. */
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

            for (Objeto o : objetos) {
                if (o.bomba) {
                    g.setColor(new Color(35, 35, 35));
                    g.fillOval((int) o.x - 14, (int) o.y - 14, 28, 28);
                    g.setColor(Color.RED);
                    g.setStroke(new BasicStroke(3));
                    g.drawOval((int) o.x - 14, (int) o.y - 14, 28, 28);
                } else {
                    g.setColor(new Color(255, 215, 0));
                    g.fill(estrella(o.x, o.y, 16));
                }
            }

            g.setColor(new Color(0, 200, 255));
            g.fillRoundRect((int) jugadorX - 40, h - 40, 80, 18, 10, 10);

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.setColor(Color.WHITE);
            g.drawString("Puntos: " + puntos, 15, 28);
            g.drawString("Tiempo: " + tiempo + " s", w / 2 - 45, 28);
            g.drawString("Vidas:", w - 150, 28);
            g.setColor(Color.RED);
            for (int i = 0; i < Math.max(vidas, 0); i++) {
                g.fillOval(w - 80 + i * 22, 13, 16, 16);
            }

            if (!jugando) {
                g.setColor(new Color(0, 0, 0, 160));
                g.fillRect(0, 0, w, h);
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
                centrado(g, finJuego ? "¡Fin del juego!" : "Atrapa las estrellas", w, h / 2 - 10);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
                centrado(g, finJuego ? "Puntos: " + puntos : "Presiona \"Iniciar\" para jugar", w, h / 2 + 30);
            }
            g.dispose();
        }

        private void centrado(Graphics2D g, String texto, int ancho, int y) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(texto, (ancho - fm.stringWidth(texto)) / 2, y);
        }
    }
}
