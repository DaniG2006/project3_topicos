package ponyrun;

import java.awt.CardLayout;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Ventana principal de PONY RUN. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("PONY RUN");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            CardLayout tarjetas = new CardLayout();
            JPanel pantallas = new JPanel(tarjetas);
            PantallaMenu menu = new PantallaMenu();
            PantallaCarga carga = new PantallaCarga();
            PanelJuego juego = new PanelJuego();

            pantallas.add(menu, "menu");
            pantallas.add(carga, "carga");
            pantallas.add(juego, "juego");
            juego.setAlMenuPrincipal(() -> {
                tarjetas.show(pantallas, "menu");
                Sonido.setMusica(true);
                menu.activar();
                menu.requestFocusInWindow();
            });
            menu.setAlNuevoJuego(() -> {
                tarjetas.show(pantallas, "carga");
                carga.iniciar(() -> {
                    tarjetas.show(pantallas, "juego");
                    juego.iniciarDesdeMenu();
                    juego.requestFocusInWindow();
                });
                carga.requestFocusInWindow();
            });
            menu.setAlSalir(f::dispose);

            f.add(pantallas);
            f.setResizable(false);
            f.setPreferredSize(new Dimension(UI.W, UI.H));
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            menu.activar();
        });
    }
}
