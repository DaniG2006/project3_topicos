package juegohilos;

import java.awt.CardLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Starts the loading screen and, when it finishes, switches to the game. */
public class Main {
    public static void main(String[] args) {
        Sonido.iniciar();
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignorada) { }

            JFrame f = new JFrame("Catch the Stars - Threads Project");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            CardLayout cartas = new CardLayout();
            JPanel raiz = new JPanel(cartas);

            Juego juego = new Juego();
            PantallaCarga carga = new PantallaCarga(mejor -> {
                juego.setMejor(mejor);
                cartas.show(raiz, "juego");
                juego.enfocar();
            });

            raiz.add(carga, "carga");
            raiz.add(juego, "juego");

            f.add(raiz);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);

            carga.iniciar();
        });
    }
}
