package ponyrun;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/** Dibujo de todos los personajes y objetos con bloques de píxeles. */
final class Sprites {

    private static final int U = 2;

    static final Color MELENA = new Color(120, 60, 170);
    static final Color CUERNO = new Color(250, 220, 90);
    static final Color PEZUNA = new Color(95, 45, 130);
    static final Color OJO = new Color(35, 15, 55);

    private static final String[] CORAZON = {
        ".XX.XX.",
        "XXXXXXX",
        "XXXXXXX",
        ".XXXXX.",
        "..XXX..",
        "...X..."
    };

    private Sprites() { }

    static Color mezcla(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static void c(Graphics2D g, int x, int y0, int cx, int cy, int w, int h, Color col) {
        g.setColor(col);
        g.fillRect(x + cx * U, y0 + cy * U, w * U, h * U);
    }

    // ------------------------------------------------------------------ pony

    static void pony(Graphics2D g, int x, int pies, int frame, boolean agachado, boolean aire, Color cuerpo) {
        if (agachado) {
            AffineTransform at = g.getTransform();
            g.translate(x, pies);
            g.scale(1.15, 0.6);
            ponyBase(g, 0, 0, frame, false, cuerpo);
            g.setTransform(at);
        } else {
            ponyBase(g, x, pies, frame, aire, cuerpo);
        }
    }

    private static void ponyBase(Graphics2D g, int x, int pies, int frame, boolean aire, Color cuerpo) {
        int y0 = pies - 18 * U;
        Color sombra = cuerpo.darker();

        // cola
        int ond = (frame == 0) ? 0 : 1;
        c(g, x, y0, 0, 8 + ond, 2, 6, MELENA);
        c(g, x, y0, 1, 7, 3, 5, MELENA);
        c(g, x, y0, 0, 12 + ond, 2, 3, MELENA);

        // patas
        int[] lx;
        int[] len;
        if (aire) {
            lx = new int[] {3, 6, 11, 14};
            len = new int[] {3, 3, 3, 3};
        } else {
            lx = new int[] {3, 6, 11, 14};
            len = (frame == 0) ? new int[] {4, 3, 3, 4} : new int[] {3, 4, 4, 3};
        }
        for (int k = 0; k < 4; k++) {
            Color col = (k % 2 == 0) ? sombra : cuerpo;
            c(g, x, y0, lx[k], 13, 3, len[k], col);
            c(g, x, y0, lx[k] - 1, 13 + len[k], 4, 1, PEZUNA);
        }

        // cuerpo, cuello y cabeza
        c(g, x, y0, 3, 8, 12, 6, cuerpo);
        c(g, x, y0, 4, 13, 10, 2, sombra);
        c(g, x, y0, 12, 5, 4, 5, cuerpo);
        c(g, x, y0, 14, 2, 7, 5, cuerpo);
        c(g, x, y0, 20, 5, 2, 2, sombra);

        // melena, oreja, cuerno y ojo
        c(g, x, y0, 11, 2, 3, 8, MELENA);
        c(g, x, y0, 13, 1, 2, 2, cuerpo);
        c(g, x, y0, 18, 0, 2, 3, CUERNO);
        c(g, x, y0, 17, 4, 2, 2, OJO);
        c(g, x, y0, 20, 8, 2, 1, new Color(165, 90, 180));
    }

    // ------------------------------------------------------------------ obstáculos

    static void poste(Graphics2D g, int x, int pies, int w, int h) {
        int y = pies - h;
        g.setColor(new Color(150, 85, 40));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(190, 120, 60));
        g.fillRect(x, y, 1, h);
        g.setColor(new Color(105, 55, 25));
        g.fillRect(x + w - 2, y, 2, h);
        g.setColor(new Color(175, 105, 52));
        g.fillRect(x, y, w, 2);
    }

    static void bicho(Graphics2D g, int x, int y, int frame) {
        Color ala = new Color(245, 195, 60);
        Color alaBorde = new Color(200, 140, 30);
        if (frame == 0) {
            g.setColor(ala);
            g.fillRect(x, y + 1, 6, 5);
            g.fillRect(x + 12, y + 1, 6, 5);
            g.setColor(alaBorde);
            g.fillRect(x, y + 1, 6, 1);
            g.fillRect(x + 12, y + 1, 6, 1);
        } else {
            g.setColor(ala);
            g.fillRect(x + 1, y + 6, 5, 4);
            g.fillRect(x + 12, y + 6, 5, 4);
            g.setColor(alaBorde);
            g.fillRect(x + 1, y + 9, 5, 1);
            g.fillRect(x + 12, y + 9, 5, 1);
        }
        g.setColor(new Color(160, 90, 210));
        g.fillRect(x + 6, y + 3, 6, 8);
        g.setColor(new Color(110, 50, 160));
        g.fillRect(x + 6, y + 9, 6, 2);
        g.setColor(Color.WHITE);
        g.fillRect(x + 7, y + 5, 1, 1);
        g.fillRect(x + 10, y + 5, 1, 1);
        g.setColor(new Color(90, 40, 130));
        g.fillRect(x + 7, y, 1, 3);
        g.fillRect(x + 10, y, 1, 3);
    }

    // ------------------------------------------------------------------ bonus

    static void manzana(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(220, 40, 50));
        g.fillOval(cx - 5, cy - 5, 10, 10);
        g.setColor(new Color(255, 150, 150));
        g.fillRect(cx - 3, cy - 3, 2, 2);
        g.setColor(new Color(110, 65, 30));
        g.fillRect(cx, cy - 8, 1, 3);
        g.setColor(new Color(90, 190, 70));
        g.fillRect(cx + 1, cy - 8, 3, 2);
    }

    static void estrella(Graphics2D g, int cx, int cy, double rot) {
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI / 5 * i - Math.PI / 2 + rot;
            double rad = (i % 2 == 0) ? 8 : 3.5;
            double px = cx + Math.cos(ang) * rad;
            double py = cy + Math.sin(ang) * rad;
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.closePath();
        g.setColor(new Color(255, 225, 70));
        g.fill(p);
        g.setColor(new Color(210, 150, 0));
        g.draw(p);
    }

    static void reloj(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(70, 110, 200));
        g.fillOval(cx - 7, cy - 7, 14, 14);
        g.setColor(Color.WHITE);
        g.fillOval(cx - 5, cy - 5, 10, 10);
        g.setColor(new Color(40, 40, 60));
        g.drawLine(cx, cy, cx, cy - 4);
        g.drawLine(cx, cy, cx + 3, cy);
    }

    static void corazon(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        for (int r = 0; r < CORAZON.length; r++) {
            for (int k = 0; k < CORAZON[r].length(); k++) {
                if (CORAZON[r].charAt(k) == 'X') g.fillRect(x + k, y + r, 1, 1);
            }
        }
    }

    // ------------------------------------------------------------------ decoración

    static void arbol(Graphics2D g, int x, int pies, double noche, int v) {
        Color tronco = mezcla(new Color(125, 75, 40), new Color(45, 30, 35), noche);
        Color hoja = mezcla(new Color(105, 170, 70), new Color(32, 72, 60), noche);
        Color hoja2 = mezcla(new Color(85, 145, 55), new Color(24, 58, 50), noche);
        int a = v * 4;

        g.setColor(tronco);
        g.fillRect(x + 15, pies - 34 - a, 5, 34 + a);

        g.setColor(hoja2);
        g.fillOval(x, pies - 62 - a, 24 + a, 24 + a);
        g.fillOval(x + 20, pies - 58 - a, 22 + a, 22 + a);
        g.setColor(hoja);
        g.fillOval(x + 8, pies - 74 - a, 28 + a, 28 + a);

        g.setColor(mezcla(new Color(215, 45, 50), new Color(90, 30, 50), noche));
        int[][] manzanas = {{8, -52}, {26, -66}, {34, -48}, {16, -62}};
        for (int[] m : manzanas) g.fillRect(x + m[0], pies + m[1] - a, 3, 3);
    }
}
