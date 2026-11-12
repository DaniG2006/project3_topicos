package juegohilos;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Loading screen. Three threads run at the same time:
 *   Thread_I  -> reads record.txt (saved best score)
 *   Thread_II -> loop 1-10 that fills the progress bar
 *   Main      -> vowel loop (A E I O U animation)
 */
public class PantallaCarga extends JPanel {

    private static final String VOCALES = "AEIOU";

    private final JLabel lblTitulo = new JLabel("CATCH THE STARS", SwingConstants.CENTER);
    private final JLabel lblVocales = new JLabel("", SwingConstants.CENTER);
    private final JProgressBar barra = new JProgressBar(0, 100);
    private final JTextArea registro = new JTextArea();
    private final IntConsumer alTerminar;
    private volatile int mejor = 0;

    public PantallaCarga(IntConsumer alTerminar) {
        this.alTerminar = alTerminar;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        setPreferredSize(new Dimension(1080, 600));

        lblTitulo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        lblTitulo.setForeground(new Color(255, 215, 0));

        lblVocales.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 54));
        lblVocales.setAlignmentX(CENTER_ALIGNMENT);
        mostrarVocal(-1);

        barra.setStringPainted(true);
        barra.setAlignmentX(CENTER_ALIGNMENT);
        barra.setPreferredSize(new Dimension(600, 28));
        barra.setMaximumSize(new Dimension(600, 28));

        registro.setEditable(false);
        registro.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        registro.setBackground(new Color(20, 24, 32));
        registro.setForeground(new Color(150, 255, 170));
        JScrollPane sp = new JScrollPane(registro);
        sp.setAlignmentX(CENTER_ALIGNMENT);
        sp.setPreferredSize(new Dimension(600, 220));
        sp.setMaximumSize(new Dimension(600, 260));
        sp.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "Loading...", 0, 0, null, Color.WHITE));

        JPanel centro = new JPanel();
        centro.setOpaque(false);
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.add(Box.createVerticalStrut(20));
        centro.add(lblVocales);
        centro.add(Box.createVerticalStrut(20));
        centro.add(barra);
        centro.add(Box.createVerticalStrut(20));
        centro.add(sp);

        add(lblTitulo, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setPaint(new GradientPaint(0, 0, new Color(15, 20, 50), 0, getHeight(), new Color(45, 20, 80)));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }

    /** Starts the three loading threads and a coordinator that waits for them. */
    public void iniciar() {
        log("[Main] Loading resources...");

        Thread hiloI = new Thread(this::leerMejor, "Thread_I");
        Thread hiloII = new Thread(this::cicloProgreso, "Thread_II");
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
            log("[Main] Loading complete. Let's play!");
            Sonido.reproducir(Sonido.Efecto.READY);
            dormir(700);
            SwingUtilities.invokeLater(() -> alTerminar.accept(mejor));
        }, "Coordinator-Thread").start();
    }

    /** Thread_I: reads the best-score file (creates it if missing). */
    private void leerMejor() {
        Path ruta = Paths.get("record.txt");
        log("[Thread_I] Reading " + ruta.getFileName() + "...");
        dormir(500);
        try {
            if (!Files.exists(ruta)) {
                Files.write(ruta, Collections.singletonList("0"), StandardCharsets.UTF_8);
                log("[Thread_I] record.txt did not exist: created with 0");
            }
            try (BufferedReader br = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
                String linea = br.readLine();
                if (linea != null) {
                    try {
                        mejor = Math.max(0, Integer.parseInt(linea.trim()));
                    } catch (NumberFormatException e) {
                        log("[Thread_I] Invalid content, using 0");
                    }
                }
            }
            log("[Thread_I] Best score loaded: " + mejor);
        } catch (IOException e) {
            log("[Thread_I] Read error: " + e.getMessage());
        }
    }

    /** Thread_II: loop 1-10, each step fills the bar by 10%. */
    private void cicloProgreso() {
        for (int i = 1; i <= 10; i++) {
            final int paso = i;
            SwingUtilities.invokeLater(() -> barra.setValue(paso * 10));
            log("[Thread_II] Step " + paso + "/10");
            Sonido.reproducir(Sonido.Efecto.TICK);
            dormir(300);
        }
    }

    /** Main: vowel loop (2 rounds), highlights the current vowel. */
    private void cicloVocales() {
        for (int vuelta = 0; vuelta < 2; vuelta++) {
            for (int i = 0; i < VOCALES.length(); i++) {
                mostrarVocal(i);
                dormir(300);
            }
        }
        mostrarVocal(-1);
    }

    private void mostrarVocal(int actual) {
        StringBuilder sb = new StringBuilder("<html>");
        for (int i = 0; i < VOCALES.length(); i++) {
            String color = (i == actual) ? "#FFD700" : "#7A7FA0";
            sb.append("<span style='color:").append(color).append("'>")
              .append(VOCALES.charAt(i)).append("</span>&nbsp;&nbsp;");
        }
        sb.append("</html>");
        String html = sb.toString();
        SwingUtilities.invokeLater(() -> lblVocales.setText(html));
    }

    private void log(String texto) {
        SwingUtilities.invokeLater(() -> {
            registro.append(texto + "\n");
            registro.setCaretPosition(registro.getDocument().getLength());
        });
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
