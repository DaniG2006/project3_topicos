package ponyrun;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** Shared drawing for the menu and loading screens (sunburst, hills, buttons, outlined text). */
final class UI {

    static final int W = 1000;
    static final int H = 580;

    static final Color MORADO = new Color(60, 20, 110);
    static final Color AMARILLO = new Color(250, 235, 60);
    static final Color BOTON = new Color(178, 139, 239);
    static final Color BOTON_SEL = new Color(211, 176, 255);
    static final Color SILUETA = new Color(122, 66, 190);

    private static final Font F_BOTON = new Font("Comic Sans MS", Font.BOLD, 28);

    private UI() { }

    /** Rotating purple / magenta sunburst. */
    static void rayos(Graphics2D g, double t) {
        g.setColor(new Color(148, 112, 204));
        g.fillRect(0, 0, W, H);
        double cx = W / 2.0;
        double cy = 300;
        double rot = t * 0.12;
        int n = 12;
        g.setColor(new Color(190, 98, 200));
        for (int i = 0; i < n; i++) {
            double a0 = rot + i * 2 * Math.PI / n;
            double a1 = a0 + Math.PI / n;
            Path2D.Double p = new Path2D.Double();
            p.moveTo(cx, cy);
            p.lineTo(cx + Math.cos(a0) * 1500, cy + Math.sin(a0) * 1500);
            p.lineTo(cx + Math.cos(a1) * 1500, cy + Math.sin(a1) * 1500);
            p.closePath();
            g.fill(p);
        }
    }

    static void sol(Graphics2D g, double t) {
        double r = 112 + Math.sin(t * 1.5) * 2;
        g.setColor(new Color(248, 230, 50));
        g.fill(new Ellipse2D.Double(W / 2.0 - r, 300 - r, 2 * r, 2 * r));
    }

    static void colinas(Graphics2D g) {
        g.setColor(new Color(176, 208, 10));
        g.fill(new Ellipse2D.Double(-300, 360, 1100, 520));
        g.setColor(new Color(140, 168, 0));
        g.fill(new Ellipse2D.Double(250, 405, 1200, 500));
    }

    /** Text with a thick outline, centered on cx, with the baseline at "base". */
    static void textoContorno(Graphics2D g, String texto, Font f, double cx, double base,
                              Color relleno, Color borde, float grosor) {
        FontRenderContext frc = g.getFontRenderContext();
        Shape s = new TextLayout(texto, f, frc).getOutline(null);
        Rectangle2D b = s.getBounds2D();
        AffineTransform at = AffineTransform.getTranslateInstance(cx - b.getWidth() / 2 - b.getX(), base);
        Shape pos = at.createTransformedShape(s);
        g.setStroke(new BasicStroke(grosor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(borde);
        g.draw(pos);
        g.setColor(relleno);
        g.fill(pos);
    }

    static void boton(Graphics2D g, Rectangle r, String texto, boolean sel) {
        int dy = sel ? -3 : 0;
        g.setColor(MORADO);
        g.fillRect(r.x + 4, r.y + 7 + dy, r.width, r.height);
        g.setColor(sel ? BOTON_SEL : BOTON);
        g.fillRect(r.x, r.y + dy, r.width, r.height);
        g.setColor(MORADO);
        g.setStroke(new BasicStroke(3f));
        g.drawRect(r.x, r.y + dy, r.width, r.height);
        textoContorno(g, texto, F_BOTON, r.getCenterX(), r.y + dy + r.height * 0.70,
                sel ? AMARILLO : Color.WHITE, MORADO, 4f);
    }

    static void botonX(Graphics2D g, Rectangle r, boolean hover) {
        g.setColor(hover ? BOTON_SEL : BOTON);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(MORADO);
        g.setStroke(new BasicStroke(4f));
        g.drawRect(r.x, r.y, r.width, r.height);
        g.setColor(AMARILLO);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(r.x + 10, r.y + 10, r.x + r.width - 10, r.y + r.height - 10);
        g.drawLine(r.x + r.width - 10, r.y + 10, r.x + 10, r.y + r.height - 10);
    }
}
