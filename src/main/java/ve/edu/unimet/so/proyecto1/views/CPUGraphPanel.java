package ve.edu.unimet.so.proyecto1.views;

import javax.swing.*;
import java.awt.*;

/**
 * CPUGraphPanel - Panel de gráfica de utilización de CPU
 * Usa arreglo circular para almacenar los últimos N puntos de datos
 * (cumple con la restricción de no usar Java Collections)
 */
public class CPUGraphPanel extends JPanel {

    // Arreglo circular para datos de utilización (0.0 - 1.0)
    private final double[] dataPoints;
    private final int maxPoints;
    private int headIndex = 0; // Posición del próximo dato a escribir
    private int count = 0; // Cantidad de datos actuales

    // Colores
    private static final Color COLOR_BG = new Color(20, 25, 40);
    private static final Color COLOR_GRID = new Color(50, 60, 80);
    private static final Color COLOR_LINE = new Color(0, 200, 100);
    private static final Color COLOR_FILL = new Color(0, 200, 100, 40);
    private static final Color COLOR_AXIS = new Color(100, 120, 150);

    public CPUGraphPanel(int maxPoints) {
        this.maxPoints = maxPoints;
        this.dataPoints = new double[maxPoints];
        setBackground(COLOR_BG);
        setPreferredSize(new Dimension(200, 80));
    }

    /**
     * Agrega un nuevo punto de utilización (0.0 - 1.0)
     */
    public void addDataPoint(double utilization) {
        // Clamp entre 0 y 1
        utilization = Math.max(0.0, Math.min(1.0, utilization));

        dataPoints[headIndex] = utilization;
        headIndex = (headIndex + 1) % maxPoints;
        if (count < maxPoints) {
            count++;
        }
        repaint();
    }

    /**
     * Limpia todos los datos
     */
    public void clear() {
        count = 0;
        headIndex = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int margin = 5;
        int graphW = w - 2 * margin;
        int graphH = h - 2 * margin;

        // Dibujar fondo y grid
        g2.setColor(COLOR_BG);
        g2.fillRect(0, 0, w, h);

        // Líneas horizontales del grid (25%, 50%, 75%, 100%)
        g2.setColor(COLOR_GRID);
        g2.setStroke(new BasicStroke(1));
        for (int i = 1; i <= 4; i++) {
            int y = margin + (int) (graphH * (1.0 - i * 0.25));
            g2.drawLine(margin, y, w - margin, y);
        }

        // Dibujar área bajo la curva y línea
        if (count > 1) {
            int[] xPoints = new int[count + 2];
            int[] yPoints = new int[count + 2];

            for (int i = 0; i < count; i++) {
                // Índice real en el arreglo circular
                int dataIdx = (headIndex - count + i + maxPoints) % maxPoints;
                double val = dataPoints[dataIdx];

                xPoints[i] = margin + (int) ((double) i / (maxPoints - 1) * graphW);
                yPoints[i] = margin + (int) (graphH * (1.0 - val));
            }

            // Cerrar el polígono para el fill
            xPoints[count] = xPoints[count - 1];
            yPoints[count] = margin + graphH;
            xPoints[count + 1] = xPoints[0];
            yPoints[count + 1] = margin + graphH;

            // Área fill
            g2.setColor(COLOR_FILL);
            g2.fillPolygon(xPoints, yPoints, count + 2);

            // Línea
            g2.setColor(COLOR_LINE);
            g2.setStroke(new BasicStroke(2));
            for (int i = 0; i < count - 1; i++) {
                g2.drawLine(xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
            }
        }

        // Ejes
        g2.setColor(COLOR_AXIS);
        g2.setStroke(new BasicStroke(1));
        g2.drawRect(margin, margin, graphW, graphH);

        // Etiqueta "100%" arriba
        g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g2.setColor(COLOR_AXIS);
        g2.drawString("100%", margin + 2, margin + 10);
        g2.drawString("0%", margin + 2, h - margin - 2);
    }
}
