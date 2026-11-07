package ponyrun;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Ventana principal de PONY RUN. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("PONY RUN");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            PanelJuego panel = new PanelJuego();
            f.add(panel);
            f.setResizable(false);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}
