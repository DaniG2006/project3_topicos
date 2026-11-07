import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Ejercicio 2: cronómetro con un hilo que actualiza la pantalla cada 10 ms. */
public class Ejercicio2Panel extends JPanel {

    private final JLabel lblTiempo = new JLabel("00:00:00.00", SwingConstants.CENTER);
    private final JButton btnIniciar = new JButton("Iniciar");
    private final JButton btnPausar = new JButton("Pausar");
    private final JButton btnReiniciar = new JButton("Reiniciar");

    // Estado compartido entre la interfaz y el hilo (protegido con "lock")
    private final Object lock = new Object();
    private boolean corriendo = false;
    private long acumuladoMs = 0;
    private long inicioMs = 0;
    private int generacion = 0; // evita que dos hilos del cronómetro vivan a la vez

    public Ejercicio2Panel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(40, 20, 40, 20));

        lblTiempo.setFont(new Font(Font.MONOSPACED, Font.BOLD, 72));

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        botones.add(btnIniciar);
        botones.add(btnPausar);
        botones.add(btnReiniciar);

        add(lblTiempo, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);

        btnIniciar.addActionListener(e -> iniciar());
        btnPausar.addActionListener(e -> pausar());
        btnReiniciar.addActionListener(e -> reiniciar());
    }

    private void iniciar() {
        final int miGeneracion;
        synchronized (lock) {
            if (corriendo) return;
            corriendo = true;
            inicioMs = System.currentTimeMillis();
            miGeneracion = ++generacion;
        }
        Thread hilo = new Thread(() -> {
            while (true) {
                long t;
                boolean sigue;
                synchronized (lock) {
                    if (miGeneracion != generacion) return; // llegó un hilo más nuevo
                    sigue = corriendo;
                    t = tiempoActual();
                }
                mostrar(t);
                if (!sigue) return; // se pausó o reinició: ya mostró el valor final
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "Hilo-Cronometro");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void pausar() {
        synchronized (lock) {
            if (!corriendo) return;
            acumuladoMs += System.currentTimeMillis() - inicioMs;
            corriendo = false;
        }
        // el hilo muestra el valor final y termina solo
    }

    private void reiniciar() {
        synchronized (lock) {
            corriendo = false;
            acumuladoMs = 0;
        }
        lblTiempo.setText(formato(0));
    }

    /** Debe llamarse con "lock" tomado. */
    private long tiempoActual() {
        return corriendo ? acumuladoMs + (System.currentTimeMillis() - inicioMs) : acumuladoMs;
    }

    private void mostrar(long ms) {
        SwingUtilities.invokeLater(() -> lblTiempo.setText(formato(ms)));
    }

    private static String formato(long ms) {
        long cs = (ms / 10) % 100;
        long s = (ms / 1000) % 60;
        long m = (ms / 60000) % 60;
        long h = ms / 3600000;
        return String.format("%02d:%02d:%02d.%02d", h, m, s, cs);
    }
}
