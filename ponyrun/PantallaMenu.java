package ponyrun;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Title screen ("login"): NEW GAME / OPTIONS / CREDITS.
 * Keyboard: UP/DOWN, ENTER or SPACE, ESC to go back. Mouse: hover and click.
 * Menu-Animation-Thread rotates the sunburst and makes the pony jump.
 */
public class PantallaMenu extends JPanel {

    private enum Vista { PRINCIPAL, OPCIONES, CREDITOS }

    private static final Font F_TITULO = new Font("Comic Sans MS", Font.BOLD, 76);
    private static final Font F_SUBTITULO = new Font("Comic Sans MS", Font.BOLD, 58);
    private static final Font F_TEXTO = new Font("Comic Sans MS", Font.BOLD, 22);
    private static final Font F_PEQ = new Font("Comic Sans MS", Font.BOLD, 15);

    private final Rectangle botonX = new Rectangle(UI.W - 54, 14, 38, 38);
    private Vista vista = Vista.PRINCIPAL;
    private int sel = 0;
    private boolean hoverX = false;
    private volatile double t = 0;

    private Runnable alNuevoJuego = () -> { };
    private Runnable alSalir = () -> System.exit(0);

    public PantallaMenu() {
        setPreferredSize(new Dimension(UI.W, UI.H));
        setFocusable(true);
        Sonido.iniciar();
        Sonido.setMusica(true);
        configurarTeclas();
        configurarRaton();

        Thread anim = new Thread(this::bucleAnimacion, "Menu-Animation-Thread");
        anim.setDaemon(true);
        anim.start();
    }

    public void setAlNuevoJuego(Runnable r) { alNuevoJuego = r; }

    public void setAlSalir(Runnable r) { alSalir = r; }

    /** Called when this screen is shown again. */
    public void activar() {
        vista = Vista.PRINCIPAL;
        sel = 0;
        requestFocusInWindow();
    }

    private void bucleAnimacion() {
        while (true) {
            t += 0.016;
            if (isShowing()) repaint();
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------ menu structure

    private String[] items() {
        switch (vista) {
            case OPCIONES:
                return new String[] {
                    "SONIDOS: " + (Sonido.isEfectos() ? "ON" : "OFF"),
                    "MUSICA: " + (Sonido.isMusicaOn() ? "ON" : "OFF"),
                    "EFECTO CRT: " + (Ajustes.crt ? "ACTIVADO" : "DESACTIVADO"),
                    "ATRAS"
                };
            case CREDITOS:
                return new String[] {"ATRAS"};
            default:
                return new String[] {"NUEVO JUEGO", "OPCIONES", "CREDITOS"};
        }
    }

    private Rectangle rect(int i) {
        if (vista == Vista.PRINCIPAL) return new Rectangle(UI.W / 2 - 125, 320 + i * 58, 250, 46);
        if (vista == Vista.OPCIONES) return new Rectangle(UI.W / 2 - 165, 175 + i * 60, 330, 48);
        return new Rectangle(UI.W / 2 - 165, 480, 330, 48);
    }

    private void activar(int i) {
        Sonido.reproducir(Sonido.Efecto.SELECT);
        switch (vista) {
            case PRINCIPAL:
                if (i == 0) {
                    alNuevoJuego.run();
                } else if (i == 1) {
                    vista = Vista.OPCIONES;
                    sel = 0;
                } else {
                    vista = Vista.CREDITOS;
                    sel = 0;
                }
                break;
            case OPCIONES:
                if (i == 0) Sonido.setEfectos(!Sonido.isEfectos());
                else if (i == 1) Sonido.setMusicaOn(!Sonido.isMusicaOn());
                else if (i == 2) Ajustes.crt = !Ajustes.crt;
                else {
                    vista = Vista.PRINCIPAL;
                    sel = 1;
                }
                break;
            default:
                vista = Vista.PRINCIPAL;
                sel = 2;
        }
        repaint();
    }

    private void atras() {
        if (vista == Vista.PRINCIPAL) return;
        Sonido.reproducir(Sonido.Efecto.BACK);
        sel = (vista == Vista.OPCIONES) ? 1 : 2;
        vista = Vista.PRINCIPAL;
        repaint();
    }

    private void mover(int delta) {
        int n = items().length;
        sel = (sel + delta + n) % n;
        Sonido.reproducir(Sonido.Efecto.MOVE);
        repaint();
    }

    // ------------------------------------------------------------------ input

    private void configurarTeclas() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        tecla(im, am, "pressed UP", "up", () -> mover(-1));
        tecla(im, am, "pressed W", "w", () -> mover(-1));
        tecla(im, am, "pressed DOWN", "down", () -> mover(1));
        tecla(im, am, "pressed S", "s", () -> mover(1));
        tecla(im, am, "pressed ENTER", "enter", () -> activar(sel));
        tecla(im, am, "pressed SPACE", "space", () -> activar(sel));
        tecla(im, am, "pressed ESCAPE", "esc", this::atras);
    }

    private void tecla(InputMap im, ActionMap am, String ks, String nombre, Runnable r) {
        im.put(KeyStroke.getKeyStroke(ks), nombre);
        am.put(nombre, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    private void configurarRaton() {
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                Point p = e.getPoint();
                hoverX = botonX.contains(p);
                String[] it = items();
                for (int i = 0; i < it.length; i++) {
                    if (rect(i).contains(p) && sel != i) {
                        sel = i;
                        Sonido.reproducir(Sonido.Efecto.MOVE);
                    }
                }
                repaint();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Point p = e.getPoint();
                if (botonX.contains(p)) {
                    alSalir.run();
                    return;
                }
                String[] it = items();
                for (int i = 0; i < it.length; i++) {
                    if (rect(i).contains(p)) {
                        sel = i;
                        activar(i);
                        return;
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double tt = t;

        UI.rayos(g, tt);
        UI.sol(g, tt);

        // Pony follows a downward-opening quadratic trajectory around the sun.
        double faseRuta = (tt * 0.22) % 2.0;
        double recorrido = faseRuta <= 1.0
                ? faseRuta * 2.0 - 1.0
                : 3.0 - faseRuta * 2.0;
        double ponyX = 500 + recorrido * 118;
        double ponyY = 350 - 58 * (1.0 - recorrido * recorrido);
        AffineTransform at = g.getTransform();
        g.translate(ponyX, ponyY);
        g.scale(0.9, 0.9);
        Sprites.pony(g, 0, 0, (int) (tt * 8), false, true, UI.SILUETA);
        g.setTransform(at);

        UI.colinas(g);

        // title
        double pulso = 1 + Math.sin(tt * 2) * 0.015;
        AffineTransform at2 = g.getTransform();
        g.translate(UI.W / 2.0, 110);
        g.scale(pulso, pulso);
        g.translate(-UI.W / 2.0, -110);
        if (vista == Vista.PRINCIPAL) {
            UI.textoContorno(g, "PONY RUN", F_TITULO, UI.W / 2.0, 112, UI.AMARILLO, UI.MORADO, 10f);
        } else {
            UI.textoContorno(g, vista == Vista.OPCIONES ? "OPCIONES" : "CREDITOS", F_SUBTITULO,
                    UI.W / 2.0, 112, UI.AMARILLO, UI.MORADO, 11f);
        }
        g.setTransform(at2);

        if (vista == Vista.CREDITOS) {
            String[] lineas = {
                "PONY RUN",
                "Proyecto de Hilos de Unidad 3",
                "Topicos Avanzados de Programacion 2026",
                "Para Ing. Anibal Roberto Saucedo Rosales",
                "Autor: DaniG"
            };
            for (int i = 0; i < lineas.length; i++) {
                UI.textoContorno(g, lineas[i], F_TEXTO, UI.W / 2.0, 200 + i * 52,
                        Color.WHITE, UI.MORADO, 6f);
            }
        }

        String[] it = items();
        for (int i = 0; i < it.length; i++) UI.boton(g, rect(i), it[i], i == sel);
        UI.botonX(g, botonX, hoverX);

        UI.textoContorno(g, "v1.0", F_TEXTO, UI.W - 55, UI.H - 20, UI.AMARILLO, UI.MORADO, 5f);
        UI.textoContorno(g, "ARRIBA/ABAJO: mover   ENTER: elegir   ESC: volver", F_PEQ, 210, UI.H - 16,
                UI.AMARILLO, UI.MORADO, 4f);
        g.dispose();
    }
}
