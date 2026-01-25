package ve.edu.unimet.so.proyecto1.views;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.geom.Point2D;

public class MainWindow extends JFrame {

    // --- COLORES ---
    private final Color COLOR_BG = new Color(20, 20, 40);
    private final Color COLOR_PANEL = new Color(30, 30, 60);
    private final Color COLOR_TEXT = new Color(200, 220, 255);
    private final Color COLOR_ACCENT = new Color(100, 149, 237);

    // --- COMPONENTES DINÁMICOS (Para poder actualizarlos) ---
    private JLabel clockLabel;
    private JLabel cpuLabel;
    private JProgressBar instructionBar;
    private JProgressBar memoryBar;
    
    // Modelos de tablas (Para agregar/quitar filas)
    private DefaultTableModel readyModel;
    private DefaultTableModel blockedModel;
    private DefaultTableModel readySuspendedModel;
    private DefaultTableModel blockedSuspendedModel;

    public MainWindow() {
        setTitle("UNIMET-Sat RTOS Simulator - Mission Control");
        setSize(1280, 768);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(COLOR_BG);

        // 1. Header
        add(createHeader(), BorderLayout.NORTH);

        // 2. Main Panel
        JPanel mainPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        mainPanel.setBackground(COLOR_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Inicializamos los modelos de las tablas
        readyModel = new DefaultTableModel(new String[]{"ID", "Process", "Prio"}, 0);
        blockedModel = new DefaultTableModel(new String[]{"ID", "Process", "Wait"}, 0);
        
        mainPanel.add(createQueuePanel("Ready Queue (RAM)", readyModel));
        mainPanel.add(createCentralPanel());
        mainPanel.add(createQueuePanel("Blocked Queue (I/O)", blockedModel));

        add(mainPanel, BorderLayout.CENTER);

        // 3. Footer
        add(createFooter(), BorderLayout.SOUTH);
    }

    // --- MÉTODOS PÚBLICOS PARA ACTUALIZAR LA INTERFAZ (API) ---

    public void updateClock(int cycle) {
        clockLabel.setText(String.format("MISSION CLOCK: Cycle %04d  ", cycle));
    }

    public void updateCPU(String processName, int progress, int maxInstructions) {
        if (processName == null || processName.isEmpty()) {
            cpuLabel.setText("IDLE");
            instructionBar.setValue(0);
            instructionBar.setString("");
        } else {
            cpuLabel.setText(processName);
            instructionBar.setMaximum(maxInstructions);
            instructionBar.setValue(progress);
            instructionBar.setString(progress + " / " + maxInstructions + " Instr");
        }
    }

    public void updateMemory(int percentage) {
        memoryBar.setValue(percentage);
        memoryBar.setString("Memory Usage: " + percentage + "%");
        
        // Cambiar color si está crítica
        if (percentage > 80) memoryBar.setForeground(Color.RED);
        else if (percentage > 50) memoryBar.setForeground(Color.ORANGE);
        else memoryBar.setForeground(new Color(0, 200, 0));
    }

    // ... otros métodos de update ...

    public void addRowToReadySuspended(Object[] row) { 
        readySuspendedModel.addRow(row); 
    }
    
    public void addRowToBlockedSuspended(Object[] row) { 
        blockedSuspendedModel.addRow(row); 
    }

    // Métodos para las tablas
    public void addRowToReady(Object[] row) { readyModel.addRow(row); }
    public void addRowToBlocked(Object[] row) { blockedModel.addRow(row); }
    public void clearReady() { readyModel.setRowCount(0); }
    public void clearBlocked() { blockedModel.setRowCount(0); }

    // --- CREACIÓN DE PANELES (Modificados para usar variables de clase) ---

    private JPanel createHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_PANEL);
        panel.setBorder(BorderFactory.createLineBorder(COLOR_ACCENT));

        JLabel title = new JLabel("  🛰️ MISSION CONTROL CENTER");
        title.setForeground(COLOR_TEXT);
        title.setFont(new Font("Consolas", Font.BOLD, 20));

        // Variable de clase
        clockLabel = new JLabel("MISSION CLOCK: Cycle 0000  ");
        clockLabel.setForeground(Color.GREEN);
        clockLabel.setFont(new Font("Monospaced", Font.BOLD, 20));

        panel.add(title, BorderLayout.WEST);
        panel.add(clockLabel, BorderLayout.EAST);
        panel.setPreferredSize(new Dimension(0, 60));
        return panel;
    }

    private JPanel createQueuePanel(String title, DefaultTableModel model) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_PANEL);
        
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(COLOR_ACCENT), title);
        border.setTitleColor(COLOR_TEXT);
        border.setTitleFont(new Font("Arial", Font.BOLD, 14));
        panel.setBorder(border);

        JTable table = new JTable(model);
        table.setBackground(new Color(40, 40, 70));
        table.setForeground(Color.WHITE);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setBackground(new Color(20, 20, 40));
        table.getTableHeader().setForeground(COLOR_TEXT);
        
        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(new Color(40, 40, 70));
        
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCentralPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 10, 10));
        panel.setBackground(COLOR_BG);

        // CPU
        JPanel cpuPanel = new JPanel(new BorderLayout());
        cpuPanel.setBackground(COLOR_PANEL);
        TitledBorder cpuBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.CYAN), "RUNNING PROCESS (CPU)");
        cpuBorder.setTitleColor(Color.CYAN);
        cpuPanel.setBorder(cpuBorder);

        // Variable de clase
        cpuLabel = new JLabel("IDLE", SwingConstants.CENTER);
        cpuLabel.setFont(new Font("Consolas", Font.BOLD, 28));
        cpuLabel.setForeground(Color.WHITE);
        
        // Variable de clase
        instructionBar = new JProgressBar();
        instructionBar.setValue(0);
        instructionBar.setStringPainted(true);

        cpuPanel.add(cpuLabel, BorderLayout.CENTER);
        cpuPanel.add(instructionBar, BorderLayout.SOUTH);

        // Memoria
        JPanel memPanel = new JPanel(new BorderLayout());
        memPanel.setBackground(COLOR_PANEL);
        TitledBorder memBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.ORANGE), "MAIN MEMORY");
        memBorder.setTitleColor(Color.ORANGE);
        memPanel.setBorder(memBorder);

        // Variable de clase
        memoryBar = new JProgressBar();
        memoryBar.setValue(0);
        memoryBar.setString("Memory Usage: 0%");
        memoryBar.setStringPainted(true);
        memoryBar.setForeground(new Color(0, 200, 0));
        
        EmergencyButton emergencyBtn = new EmergencyButton();
        emergencyBtn.setPreferredSize(new Dimension(0, 140));
        JPanel buttonContainer = new JPanel(new BorderLayout());
        buttonContainer.setBackground(COLOR_PANEL);
        buttonContainer.setBorder(BorderFactory.createEmptyBorder(15, 40, 15, 40));
        buttonContainer.add(emergencyBtn, BorderLayout.CENTER);

        memPanel.add(memoryBar, BorderLayout.NORTH);
        memPanel.add(buttonContainer, BorderLayout.CENTER);

        panel.add(cpuPanel);
        panel.add(memPanel);
        return panel;
    }

    private JPanel createFooter() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 10));
        panel.setBackground(COLOR_BG);
        panel.setPreferredSize(new Dimension(0, 150));

        readySuspendedModel = new DefaultTableModel(new String[]{"ID", "Process", "Prio"}, 0);
        blockedSuspendedModel = new DefaultTableModel(new String[]{"ID", "Process", "Wait"}, 0);

        panel.add(createQueuePanel("Ready-Suspended (Disk)", readySuspendedModel));
        panel.add(createQueuePanel("Blocked-Suspended (Disk)", blockedSuspendedModel));
        return panel;
    }

    // El Main original se queda solo para pruebas simples de visualización
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}

// --- CLASE PERSONALIZADA CORREGIDA (Texto fijo + Cambio de color) ---
class EmergencyButton extends JButton {

    public EmergencyButton() {
        super();
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // --- 1. ELEMENTOS ESTÁTICOS (No se mueven al clicar) ---
        
        // A. CARCASA METÁLICA
        g2.setColor(new Color(60, 60, 60));
        g2.fillRoundRect(0, 0, w, h, 20, 20);
        
        GradientPaint metalPaint = new GradientPaint(0, 0, new Color(200, 200, 200), 0, h, new Color(100, 100, 100));
        g2.setPaint(metalPaint);
        g2.fillRoundRect(3, 3, w - 6, h - 6, 18, 18);

        // B. PLACA BASE ROJA
        g2.setColor(new Color(130, 0, 0));
        g2.fillRoundRect(10, 10, w - 20, h - 20, 10, 10);
        g2.setColor(new Color(0, 0, 0, 60)); // Sombra
        g2.drawRoundRect(10, 10, w - 20, h - 20, 10, 10);

        // --- CÁLCULOS DE POSICIÓN ---
        
        // Definimos el tamaño y posición "base" (sin presionar)
        int buttonDiameter = Math.min(w, h) - 65;
        if (buttonDiameter < 10) buttonDiameter = 10;
        
        int buttonX = (w - buttonDiameter) / 2;
        int staticButtonY = 15; // Posición Y original fija

        // --- 2. EL TEXTO (Ahora es estático) ---
        // Usamos staticButtonY para que el texto NO se mueva cuando el botón baje
        int textY = staticButtonY + buttonDiameter + 22; 
        
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();

        String line1 = "EMERGENCY INTERRUPTION";
        String line2 = "(MICRO-METEORITE)";

        g2.drawString(line1, (w - fm.stringWidth(line1)) / 2, textY);
        g2.drawString(line2, (w - fm.stringWidth(line2)) / 2, textY + 14);

        // --- 3. ELEMENTOS DINÁMICOS (El botón que se mueve) ---

        // Calculamos el desplazamiento
        boolean isPressed = getModel().isArmed();
        int offsetY = isPressed ? 4 : 0; // Baja 4 pixeles si se presiona
        int currentButtonY = staticButtonY + offsetY;

        // C. ANILLO METÁLICO (Base)
        // El anillo se queda quieto o se mueve muy poco, aquí lo dejamos quieto para dar efecto de que el botón entra en él
        g2.setPaint(new GradientPaint(0, staticButtonY, Color.GRAY, 0, staticButtonY + buttonDiameter, Color.WHITE));
        g2.fillOval(buttonX - 3, staticButtonY - 3, buttonDiameter + 6, buttonDiameter + 6);

        // D. EL BOTÓN (ESFERA) CON CAMBIO DE COLOR
        Point2D center = new Point2D.Float(buttonX + buttonDiameter / 2.0f, currentButtonY + buttonDiameter / 2.0f);
        float radius = buttonDiameter / 2.0f;
        float[] dist = {0.0f, 0.85f, 1.0f};
        
        Color[] colors;
        if (isPressed) {
            // COLORES AL PRESIONAR (Más oscuros/densos para simular presión y sombra)
            colors = new Color[] {
                new Color(200, 50, 50), // Centro menos brillante
                new Color(150, 0, 0),   // Cuerpo más oscuro
                new Color(50, 0, 0)     // Borde muy oscuro
            };
        } else {
            // COLORES NORMALES (Brillantes)
            colors = new Color[] {
                new Color(255, 80, 80), // Centro brillante
                new Color(200, 0, 0),   // Rojo estándar
                new Color(100, 0, 0)    // Borde oscuro
            };
        }

        RadialGradientPaint spherePaint = new RadialGradientPaint(center, radius, dist, colors);
        g2.setPaint(spherePaint);
        g2.fillOval(buttonX, currentButtonY, buttonDiameter, buttonDiameter);

        // E. BRILLO SUPERIOR (Glossy)
        // El brillo se mueve con el botón
        g2.setPaint(new LinearGradientPaint(
                0, currentButtonY, 0, currentButtonY + buttonDiameter / 2,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 140), new Color(255, 255, 255, 0)}
        ));
        g2.fillOval(buttonX + (buttonDiameter/4), currentButtonY + 5, buttonDiameter/2, buttonDiameter/3);

        g2.dispose();
    }
}