import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Ejercicio 1: cuatro hilos que entran al MISMO método sincronizado.
 * SubProceso1 y SubProceso2 hacen el ciclo 1-10; SubProceso3 y SubProceso4, a-j.
 * Gracias a "synchronized", un hilo termina por completo antes de que entre el siguiente.
 */
public class Ejercicio1Panel extends JPanel {

    private final JTextArea salida = new JTextArea();
    private final JButton btnIniciar = new JButton("Iniciar hilos");
    private final JButton btnLimpiar = new JButton("Limpiar");

    public Ejercicio1Panel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        salida.setEditable(false);
        salida.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT));
        barra.add(btnIniciar);
        barra.add(btnLimpiar);

        add(barra, BorderLayout.NORTH);
        add(new JScrollPane(salida), BorderLayout.CENTER);

        btnIniciar.addActionListener(e -> iniciar());
        btnLimpiar.addActionListener(e -> salida.setText(""));
    }

    private void iniciar() {
        btnIniciar.setEnabled(false);
        salida.setText("");

        Thread sub1 = new Thread(() -> ejecutar("SubProceso1", false), "SubProceso1");
        Thread sub2 = new Thread(() -> ejecutar("SubProceso2", false), "SubProceso2");
        Thread sub3 = new Thread(() -> ejecutar("SubProceso3", true), "SubProceso3");
        Thread sub4 = new Thread(() -> ejecutar("SubProceso4", true), "SubProceso4");

        // Hilo auxiliar: arranca los cuatro y espera a que terminen (no bloquea la interfaz)
        new Thread(() -> {
            Thread[] orden = {sub1, sub4, sub3, sub2}; // mismo orden que el ejemplo del profesor
            try {
                for (Thread t : orden) {
                    t.start();
                    Thread.sleep(50); // pequeña pausa para que el orden de entrada sea predecible
                }
                for (Thread t : orden) {
                    t.join();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            SwingUtilities.invokeLater(() -> btnIniciar.setEnabled(true));
        }).start();
    }

    /** Método común a los cuatro hilos. Sincronizado sobre este panel. */
    private synchronized void ejecutar(String nombre, boolean letras) {
        imprimir("Inició:\t\t" + nombre);
        for (int i = 0; i < 10; i++) {
            String valor = letras ? String.valueOf((char) ('a' + i)) : String.valueOf(i + 1);
            imprimir("Hilo " + valor);
            dormir(150);
        }
        imprimir("Terminó:\t\t" + nombre);
    }

    private void imprimir(String texto) {
        SwingUtilities.invokeLater(() -> salida.append(texto + "\n"));
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
