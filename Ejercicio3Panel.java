import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Ejercicio 3:
 *   Hilo_I  -> lee un archivo y muestra su contenido
 *   Hilo_II -> ciclo del 1 al 10 (muestra la iteración)
 *   Main    -> ciclo de vocales, ejecutándose al mismo tiempo que los hilos
 *
 * En una interfaz gráfica no se debe bloquear el hilo de eventos con ciclos,
 * por eso el "Main" de la tabla se ejecuta en un hilo llamado "Main".
 */
public class Ejercicio3Panel extends JPanel {

    private final JTextArea areaArchivo = crearArea();
    private final JTextArea areaCiclo = crearArea();
    private final JTextArea areaVocales = crearArea();
    private final JLabel lblArchivo = new JLabel("Ningún archivo seleccionado");
    private final JButton btnElegir = new JButton("Elegir archivo...");
    private final JButton btnEjecutar = new JButton("Ejecutar");
    private File archivo;

    public Ejercicio3Panel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        barra.add(btnElegir);
        barra.add(btnEjecutar);
        barra.add(lblArchivo);

        JPanel centro = new JPanel(new GridLayout(1, 3, 10, 10));
        centro.add(conTitulo(areaArchivo, "Hilo_I - Contenido del archivo"));
        centro.add(conTitulo(areaCiclo, "Hilo_II - Ciclo 1-10"));
        centro.add(conTitulo(areaVocales, "Main - Vocales"));

        add(barra, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);

        btnElegir.addActionListener(e -> elegirArchivo());
        btnEjecutar.addActionListener(e -> ejecutar());
    }

    private void elegirArchivo() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            archivo = fc.getSelectedFile();
            lblArchivo.setText(archivo.getName());
        }
    }

    private void ejecutar() {
        if (archivo == null) {
            JOptionPane.showMessageDialog(this, "Primero elige un archivo de texto.");
            return;
        }
        btnEjecutar.setEnabled(false);
        btnElegir.setEnabled(false);
        areaArchivo.setText("");
        areaCiclo.setText("");
        areaVocales.setText("");

        Thread hiloI = new Thread(this::leerArchivo, "Hilo_I");
        Thread hiloII = new Thread(this::cicloNumeros, "Hilo_II");
        Thread main = new Thread(this::cicloVocales, "Main");

        hiloI.start();
        hiloII.start();
        main.start();

        new Thread(() -> {
            try {
                hiloI.join();
                hiloII.join();
                main.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            SwingUtilities.invokeLater(() -> {
                btnEjecutar.setEnabled(true);
                btnElegir.setEnabled(true);
            });
        }).start();
    }

    private void leerArchivo() {
        try (BufferedReader br = Files.newBufferedReader(archivo.toPath(), StandardCharsets.UTF_8)) {
            String linea;
            while ((linea = br.readLine()) != null) {
                agregar(areaArchivo, linea);
                dormir(100);
            }
        } catch (IOException e) {
            agregar(areaArchivo, "Error al leer el archivo: " + e.getMessage());
        }
    }

    private void cicloNumeros() {
        for (int i = 1; i <= 10; i++) {
            agregar(areaCiclo, "Iteración " + i);
            dormir(300);
        }
    }

    private void cicloVocales() {
        char[] vocales = {'a', 'e', 'i', 'o', 'u'};
        for (int vuelta = 1; vuelta <= 2; vuelta++) {
            for (char v : vocales) {
                agregar(areaVocales, "Vocal: " + v);
                dormir(300);
            }
        }
    }

    private static void agregar(JTextArea area, String texto) {
        SwingUtilities.invokeLater(() -> area.append(texto + "\n"));
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static JTextArea crearArea() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        return a;
    }

    private static JScrollPane conTitulo(JTextArea area, String titulo) {
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createTitledBorder(titulo));
        return sp;
    }
}
