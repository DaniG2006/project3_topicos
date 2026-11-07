import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Ventana principal: una pestaña por ejercicio. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignorada) { }

            JFrame f = new JFrame("Proyecto de Hilos - Unidad 3");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("1. Sincronización", new Ejercicio1Panel());
            tabs.addTab("2. Cronómetro", new Ejercicio2Panel());
            tabs.addTab("3. Archivo + Ciclos", new Ejercicio3Panel());
            tabs.addTab("4. Juego", new Ejercicio4Juego());

            f.add(tabs);
            f.setSize(920, 680);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
