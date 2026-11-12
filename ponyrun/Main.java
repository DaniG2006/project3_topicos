package ponyrun;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Ventana principal de PONY RUN. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("PONY RUN");
            f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            Runnable confirmarSalida = () -> {
                int respuesta = JOptionPane.showConfirmDialog(
                        f,
                        "¿Deseas salir del juego?",
                        "Confirmar salida",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (respuesta == JOptionPane.YES_OPTION) {
                    f.dispose();
                }
            };
            f.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) {
                    confirmarSalida.run();
                }
            });
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
            menu.setAlSalir(confirmarSalida);

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
