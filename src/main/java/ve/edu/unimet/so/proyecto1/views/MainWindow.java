package ve.edu.unimet.so.proyecto1.views;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.geom.Point2D;
import ve.edu.unimet.so.proyecto1.models.PCB;

/**
 * MainWindow - Ventana principal del simulador RTOS
 * Refactorizada para usar PCBTableModel (AbstractTableModel) en lugar de
 * DefaultTableModel
 */
public class MainWindow extends JFrame {

    // --- COLORES ---
    private final Color COLOR_BG = new Color(20, 20, 40);
    private final Color COLOR_PANEL = new Color(30, 30, 60);
    private final Color COLOR_TEXT = new Color(200, 220, 255);
    private final Color COLOR_ACCENT = new Color(100, 149, 237);

    // --- COMPONENTES DINÁMICOS ---
    private JLabel clockLabel;
    private JLabel cpuLabel;
    private JLabel cpuDetailsLabel; // Atributos del proceso (Prio, PC, MAR, etc)
    private JLabel cpuModeLabel; // Indicador USER/KERNEL
    private JProgressBar instructionBar;
    private JProgressBar memoryBar;
    private JTextArea logArea; // Panel de log de eventos
    private EmergencyButton emergencyButton;

    // --- BOTONES DE CONTROL ---
    private JButton startButton;
    private JButton pauseButton;
    private JButton stepButton;
    private JButton generateOneButton;
    private JButton generateFiveButton; // GEN 5
    private JButton generateTwentyButton;
    private JComboBox<String> algorithmComboBox; // Dropdown de algoritmos
    private JButton speedUpButton;
    private JButton speedDownButton;
    private JTextField speedField; // Campo de velocidad del reloj

    // --- MODELOS DE TABLAS (AbstractTableModel) ---
    private PCBTableModel newModel;
    private PCBTableModel readyModel;
    private PCBTableModel runningModel;
    private PCBTableModel blockedModel;
    private PCBTableModel terminatedModel;
    private PCBTableModel readySuspendedModel;
    private PCBTableModel blockedSuspendedModel;

    // --- MÉTRICAS ---
    private JLabel successRateLabel;
    private JLabel throughputLabel;
    private JLabel avgWaitLabel;
    private JLabel cpuUtilLabel;
    private CPUGraphPanel cpuGraphPanel;

    public MainWindow() {
        setTitle("UNIMET-Sat RTOS Simulator - Mission Control");
        setSize(1400, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));
        getContentPane().setBackground(COLOR_BG);

        // Inicializar modelos
        initModels();

        // 1. Header
        add(createHeader(), BorderLayout.NORTH);

        // 2. Main Panel (3 columnas: colas izq, centro, colas der)
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBackground(COLOR_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // Panel izquierdo: NEW + READY
        JPanel leftPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        leftPanel.setBackground(COLOR_BG);
        leftPanel.add(createQueuePanel("NEW (Waiting Admission)", newModel));
        leftPanel.add(createQueuePanel("READY Queue", readyModel));

        // Panel central: CPU + Memory + Log
        JPanel centerPanel = createCentralPanel();

        // Panel derecho: BLOCKED + TERMINATED
        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        rightPanel.setBackground(COLOR_BG);
        rightPanel.add(createQueuePanel("BLOCKED (I/O Wait)", blockedModel));
        rightPanel.add(createQueuePanel("TERMINATED", terminatedModel));

        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        // Ajustar tamaños - tablas más anchas, centro más compacto
        leftPanel.setPreferredSize(new Dimension(660, 0));
        rightPanel.setPreferredSize(new Dimension(660, 0));
        centerPanel.setPreferredSize(new Dimension(300, 0));
        centerPanel.setMaximumSize(new Dimension(300, Integer.MAX_VALUE));

        add(mainPanel, BorderLayout.CENTER);

        // 3. Footer: Suspended queues
        add(createFooter(), BorderLayout.SOUTH);
    }

    private void initModels() {
        newModel = new PCBTableModel();
        readyModel = new PCBTableModel();
        runningModel = new PCBTableModel();
        blockedModel = new PCBTableModel();
        terminatedModel = new PCBTableModel();
        readySuspendedModel = new PCBTableModel();
        blockedSuspendedModel = new PCBTableModel();
    }

    // =================== MÉTODOS PÚBLICOS DE ACTUALIZACIÓN ===================

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

    public void updateCpuMode(boolean isKernelMode) {
        if (isKernelMode) {
            cpuModeLabel.setText("MODE: KERNEL");
            cpuModeLabel.setForeground(Color.RED);
        } else {
            cpuModeLabel.setText("MODE: USER");
            cpuModeLabel.setForeground(Color.GREEN);
        }
    }

    public void updateMemory(int percentage) {
        memoryBar.setValue(percentage);
        memoryBar.setString("Memory Usage: " + percentage + "%");

        if (percentage > 80)
            memoryBar.setForeground(Color.RED);
        else if (percentage > 50)
            memoryBar.setForeground(Color.ORANGE);
        else
            memoryBar.setForeground(new Color(0, 200, 0));
    }

    // --- Métodos de actualización usando snapshots (recomendado) ---

    public void updateNewTable(PCB[] snapshot, long globalTick) {
        newModel.updateFromSnapshot(snapshot, globalTick);
    }

    public void updateNewTableRows(Object[][] rows) {
        newModel.updateFromRows(rows);
    }

    public void updateReadyTable(PCB[] snapshot, long globalTick) {
        readyModel.updateFromSnapshot(snapshot, globalTick);
    }

    public void updateReadyTableRows(Object[][] rows) {
        readyModel.updateFromRows(rows);
    }

    public void updateRunningTable(PCB[] snapshot, long globalTick) {
        runningModel.updateFromSnapshot(snapshot, globalTick);
    }

    public void updateRunningTableRows(Object[][] rows) {
        runningModel.updateFromRows(rows);
    }

    /** Actualiza los detalles del proceso en ejecución (Prio, PC, MAR, Deadline) */
    public void updateRunningDetails(PCB running, long globalTick) {
        if (running == null) {
            cpuDetailsLabel.setText(" ");
        } else {
            long deadline = running.getDeadlineTick();
            long remaining = deadline - globalTick;
            String details = String.format("Prio: %d  |  PC: %d  |  MAR: %d  |  Deadline: %d (%+d)",
                    running.getPriority(),
                    running.getProgramCounter(),
                    running.getMar(),
                    deadline,
                    remaining);
            cpuDetailsLabel.setText(details);
        }
    }

    public void updateRunningDetailsRow(Object[] runningRow) {
        if (runningRow == null || runningRow.length < 8) {
            cpuDetailsLabel.setText(" ");
            return;
        }
        String details = String.format("Prio: %s  |  PC: %s  |  MAR: %s  |  Deadline restante: %s",
                runningRow[5],
                runningRow[3],
                runningRow[4],
                runningRow[7]);
        cpuDetailsLabel.setText(details);
    }

    public void updateBlockedTable(PCB[] snapshot, long globalTick) {
        blockedModel.updateFromSnapshot(snapshot, globalTick);
    }

    public void updateBlockedTableRows(Object[][] rows) {
        blockedModel.updateFromRows(rows);
    }

    public void updateTerminatedTable(PCB[] snapshot, long globalTick) {
        terminatedModel.updateFromSnapshot(snapshot, globalTick);
    }

    public void updateTerminatedTableRows(Object[][] rows) {
        terminatedModel.updateFromRows(rows);
    }

    public void updateReadySuspendedTable(PCB[] snapshot, long globalTick) {
        readySuspendedModel.updateFromSnapshot(snapshot, globalTick);
    }

    public void updateReadySuspendedTableRows(Object[][] rows) {
        readySuspendedModel.updateFromRows(rows);
    }

    public void updateBlockedSuspendedTable(PCB[] snapshot, long globalTick) {
        blockedSuspendedModel.updateFromSnapshot(snapshot, globalTick);
    }

    public void updateBlockedSuspendedTableRows(Object[][] rows) {
        blockedSuspendedModel.updateFromRows(rows);
    }

    // --- Métodos de métricas ---

    /**
     * Actualiza las métricas mostradas en el panel
     * 
     * @param successRate Tasa de éxito (0.0 - 1.0)
     * @param throughput  Throughput (procesos/tick)
     * @param avgWait Tiempo de espera promedio (ticks)
     * @param cpuUtil     Utilización de CPU (0.0 - 1.0)
     */
    public void updateMetrics(double successRate, double throughput, double avgWait, double cpuUtil) {
        successRateLabel.setText(String.format("✅ Success: %.1f%%", successRate * 100));
        throughputLabel.setText(String.format("📈 Thru: %.4f/t", throughput));
        avgWaitLabel.setText(String.format("⏱ Wait: %.1f t", avgWait));
        cpuUtilLabel.setText(String.format("🖥 CPU: %.1f%%", cpuUtil * 100));
    }

    /**
     * Agrega un punto de datos a la gráfica de utilización de CPU
     * 
     * @param utilization Valor de utilización (0.0 - 1.0)
     */
    public void addCpuUtilDataPoint(double utilization) {
        cpuGraphPanel.addDataPoint(utilization);
    }

    // --- Métodos de log ---

    public void updateLog(String[] logEntries) {
        if (logEntries == null || logEntries.length == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        // Mostrar últimas 50 entradas (más recientes primero)
        int start = Math.max(0, logEntries.length - 50);
        for (int i = logEntries.length - 1; i >= start; i--) {
            sb.append(logEntries[i]).append("\n");
        }
        logArea.setText(sb.toString());
        logArea.setCaretPosition(0); // Scroll al inicio
    }

    public void appendLog(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // --- Métodos legacy (retrocompatibilidad con GUIRealisticTest actual) ---

    public void addRowToReady(Object[] row) {
        readyModel.addRow(row);
    }

    public void addRowToBlocked(Object[] row) {
        blockedModel.addRow(row);
    }

    public void addRowToReadySuspended(Object[] row) {
        readySuspendedModel.addRow(row);
    }

    public void addRowToBlockedSuspended(Object[] row) {
        blockedSuspendedModel.addRow(row);
    }

    public void clearReady() {
        readyModel.clear();
    }

    public void clearBlocked() {
        blockedModel.clear();
    }

    public void clearReadySuspended() {
        readySuspendedModel.clear();
    }

    public void clearBlockedSuspended() {
        blockedSuspendedModel.clear();
    }

    // --- Getters para botones de control ---
    public JButton getEmergencyButton() {
        return emergencyButton;
    }

    public JButton getStartButton() {
        return startButton;
    }

    public JButton getPauseButton() {
        return pauseButton;
    }

    public JButton getStepButton() {
        return stepButton;
    }

    public JButton getGenerateOneButton() {
        return generateOneButton;
    }

    public JButton getGenerateFiveButton() {
        return generateFiveButton;
    }

    public JButton getGenerateTwentyButton() {
        return generateTwentyButton;
    }

    public JComboBox<String> getAlgorithmComboBox() {
        return algorithmComboBox;
    }

    // Backward-compatible aliases for existing callers.
    public JButton getGen1Button() {
        return getGenerateOneButton();
    }

    public JButton getGenerateButton() {
        return getGenerateFiveButton();
    }

    public JButton getGen20Button() {
        return getGenerateTwentyButton();
    }

    public JComboBox<String> getAlgoCombo() {
        return getAlgorithmComboBox();
    }

    public JButton getSpeedUpButton() {
        return speedUpButton;
    }

    public JButton getSpeedDownButton() {
        return speedDownButton;
    }

    public JTextField getSpeedField() {
        return speedField;
    }

    public void updateSpeedField(int speedMs) {
        speedField.setText(String.valueOf(speedMs));
    }

    // =================== CREACIÓN DE PANELES ===================

    private JPanel createHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_PANEL);
        panel.setBorder(BorderFactory.createLineBorder(COLOR_ACCENT));

        JLabel title = new JLabel("  🛰️ MISSION CONTROL CENTER");
        title.setForeground(COLOR_TEXT);
        title.setFont(new Font("Consolas", Font.BOLD, 20));

        // Panel central con botones de control
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        controlPanel.setBackground(COLOR_PANEL);

        startButton = createControlButton("▶ START", new Color(0, 150, 0));
        pauseButton = createControlButton("⏸ PAUSE", new Color(200, 150, 0));
        stepButton = createControlButton("⏭ STEP", new Color(100, 149, 237));
        generateOneButton = createControlButton("+1", new Color(100, 100, 180));
        generateFiveButton = createControlButton("+5", new Color(150, 100, 200));
        generateTwentyButton = createControlButton("+20", new Color(180, 80, 150));

        // Dropdown de algoritmos
        String[] algorithms = { "FCFS", "RR", "SRT", "PRIORITY", "EDF" };
        algorithmComboBox = new JComboBox<>(algorithms);
        algorithmComboBox.setFont(new Font("Monospaced", Font.BOLD, 11));
        algorithmComboBox.setBackground(new Color(50, 50, 80));
        algorithmComboBox.setForeground(Color.ORANGE);
        algorithmComboBox.setToolTipText("Algoritmo de planificación");
        speedDownButton = createControlButton("⏪", new Color(80, 80, 120));

        // Campo de velocidad editable
        speedField = new JTextField("200", 4);
        speedField.setHorizontalAlignment(JTextField.CENTER);
        speedField.setFont(new Font("Monospaced", Font.BOLD, 12));
        speedField.setBackground(new Color(30, 30, 50));
        speedField.setForeground(Color.CYAN);
        speedField.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 120)));
        speedField.setToolTipText("Velocidad en ms (10-2000)");

        speedUpButton = createControlButton("⏩", new Color(80, 80, 120));

        controlPanel.add(startButton);
        controlPanel.add(pauseButton);
        controlPanel.add(stepButton);
        controlPanel.add(generateOneButton);
        controlPanel.add(generateFiveButton);
        controlPanel.add(generateTwentyButton);
        controlPanel.add(algorithmComboBox);
        controlPanel.add(speedDownButton);
        controlPanel.add(speedField);
        controlPanel.add(speedUpButton);

        // Panel derecho con clock y modo CPU
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 5));
        rightPanel.setBackground(COLOR_PANEL);

        cpuModeLabel = new JLabel("MODE: USER");
        cpuModeLabel.setForeground(Color.GREEN);
        cpuModeLabel.setFont(new Font("Monospaced", Font.BOLD, 14));

        clockLabel = new JLabel("CYCLE: 0000  ");
        clockLabel.setForeground(Color.GREEN);
        clockLabel.setFont(new Font("Monospaced", Font.BOLD, 18));

        rightPanel.add(cpuModeLabel);
        rightPanel.add(clockLabel);

        panel.add(title, BorderLayout.WEST);
        panel.add(controlPanel, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.EAST);
        panel.setPreferredSize(new Dimension(0, 55));
        return panel;
    }

    private JButton createControlButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setPreferredSize(new Dimension(90, 30));
        btn.setBorder(BorderFactory.createRaisedBevelBorder());
        return btn;
    }

    private JPanel createQueuePanel(String title, TableModel model) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_PANEL);

        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_ACCENT), title);
        border.setTitleColor(COLOR_TEXT);
        border.setTitleFont(new Font("Arial", Font.BOLD, 12));
        panel.setBorder(border);

        JTable table = new JTable(model);
        table.setBackground(new Color(40, 40, 70));
        table.setForeground(Color.WHITE);
        table.setFillsViewportHeight(true);
        table.setRowHeight(20);
        table.getTableHeader().setBackground(new Color(20, 20, 40));
        table.getTableHeader().setForeground(COLOR_TEXT);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(new Color(40, 40, 70));

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCentralPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(COLOR_BG);

        // Panel superior: CPU + Running
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBackground(COLOR_BG);

        // CPU Panel
        JPanel cpuPanel = new JPanel(new BorderLayout());
        cpuPanel.setBackground(COLOR_PANEL);
        TitledBorder cpuBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.CYAN), "RUNNING PROCESS (CPU)");
        cpuBorder.setTitleColor(Color.CYAN);
        cpuPanel.setBorder(cpuBorder);

        cpuLabel = new JLabel("IDLE", SwingConstants.CENTER);
        cpuLabel.setFont(new Font("Consolas", Font.BOLD, 24));
        cpuLabel.setForeground(Color.WHITE);

        instructionBar = new JProgressBar();
        instructionBar.setValue(0);
        instructionBar.setStringPainted(true);

        // Label para atributos del proceso (más pequeño y menos prominente)
        cpuDetailsLabel = new JLabel(" ", SwingConstants.CENTER);
        cpuDetailsLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        cpuDetailsLabel.setForeground(new Color(150, 170, 200));

        // Panel inferior para barra e info
        JPanel cpuBottomPanel = new JPanel(new BorderLayout(0, 2));
        cpuBottomPanel.setBackground(COLOR_PANEL);
        cpuBottomPanel.add(instructionBar, BorderLayout.NORTH);
        cpuBottomPanel.add(cpuDetailsLabel, BorderLayout.SOUTH);

        cpuPanel.add(cpuLabel, BorderLayout.CENTER);
        cpuPanel.add(cpuBottomPanel, BorderLayout.SOUTH);

        // Memory + Emergency Button Panel - altura fija
        JPanel memPanel = new JPanel(new BorderLayout(5, 5));
        memPanel.setBackground(COLOR_PANEL);
        TitledBorder memBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.ORANGE), "MAIN MEMORY");
        memBorder.setTitleColor(Color.ORANGE);
        memPanel.setBorder(memBorder);
        memPanel.setPreferredSize(new Dimension(0, 140)); // Altura para incluir título

        memoryBar = new JProgressBar();
        memoryBar.setValue(0);
        memoryBar.setString("Memory Usage: 0%");
        memoryBar.setStringPainted(true);
        memoryBar.setForeground(new Color(0, 200, 0));

        emergencyButton = new EmergencyButton();
        emergencyButton.setPreferredSize(new Dimension(250, 85)); // Tamaño decente
        JPanel btnContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 5));
        btnContainer.setBackground(COLOR_PANEL);
        btnContainer.add(emergencyButton);

        memPanel.add(memoryBar, BorderLayout.NORTH);
        memPanel.add(btnContainer, BorderLayout.CENTER);

        topPanel.add(cpuPanel, BorderLayout.NORTH);
        topPanel.add(memPanel, BorderLayout.SOUTH); // Ahora está al sur con tamaño fijo

        // Panel central: Log de eventos - ocupa el espacio restante
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBackground(COLOR_PANEL);
        TitledBorder logBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.YELLOW), "EVENT LOG");
        logBorder.setTitleColor(Color.YELLOW);
        logPanel.setBorder(logBorder);

        logArea = new JTextArea();
        logArea.setBackground(new Color(10, 10, 30));
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane logScroll = new JScrollPane(logArea);
        logPanel.add(logScroll, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH); // CPU y memoria arriba
        panel.add(logPanel, BorderLayout.CENTER); // Log ocupa el centro (espacio restante)

        return panel;
    }

    private JPanel createFooter() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 10, 10));
        panel.setBackground(COLOR_BG);
        panel.setPreferredSize(new Dimension(0, 150));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        panel.add(createQueuePanel("READY-SUSPENDED (Disk)", readySuspendedModel));
        panel.add(createQueuePanel("BLOCKED-SUSPENDED (Disk)", blockedSuspendedModel));
        panel.add(createMetricsPanel());
        return panel;
    }

    private JPanel createMetricsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(COLOR_PANEL);
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0, 200, 150)), "MISSION METRICS");
        border.setTitleColor(new Color(0, 200, 150));
        panel.setBorder(border);

        // Panel de labels (arriba)
        JPanel labelsPanel = new JPanel(new GridLayout(2, 2, 5, 2));
        labelsPanel.setBackground(COLOR_PANEL);

        successRateLabel = createMetricLabel("✅ Success: --", new Color(100, 255, 100));
        throughputLabel = createMetricLabel("📈 Thru: --", new Color(100, 200, 255));
        avgWaitLabel = createMetricLabel("⏱ Wait: --", new Color(255, 200, 100));
        cpuUtilLabel = createMetricLabel("🖥 CPU: --", new Color(200, 150, 255));

        labelsPanel.add(successRateLabel);
        labelsPanel.add(throughputLabel);
        labelsPanel.add(avgWaitLabel);
        labelsPanel.add(cpuUtilLabel);

        // Gráfica de CPU (abajo)
        cpuGraphPanel = new CPUGraphPanel(100);

        panel.add(labelsPanel, BorderLayout.NORTH);
        panel.add(cpuGraphPanel, BorderLayout.CENTER);

        return panel;
    }

    private JLabel createMetricLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Consolas", Font.BOLD, 11));
        label.setForeground(color);
        return label;
    }

}

// --- CLASE EmergencyButton (sin cambios) ---
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

        // A. CARCASA METÁLICA
        g2.setColor(new Color(60, 60, 60));
        g2.fillRoundRect(0, 0, w, h, 20, 20);

        GradientPaint metalPaint = new GradientPaint(0, 0, new Color(200, 200, 200), 0, h, new Color(100, 100, 100));
        g2.setPaint(metalPaint);
        g2.fillRoundRect(3, 3, w - 6, h - 6, 18, 18);

        // B. PLACA BASE ROJA
        g2.setColor(new Color(130, 0, 0));
        g2.fillRoundRect(10, 10, w - 20, h - 20, 10, 10);
        g2.setColor(new Color(0, 0, 0, 60));
        g2.drawRoundRect(10, 10, w - 20, h - 20, 10, 10);

        // CÁLCULOS
        int buttonDiameter = Math.min(w, h) - 50;
        if (buttonDiameter < 10)
            buttonDiameter = 10;

        int buttonX = (w - buttonDiameter) / 2;
        int staticButtonY = 12;

        // TEXTO
        int textY = staticButtonY + buttonDiameter + 16;
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 9));
        FontMetrics fm = g2.getFontMetrics();

        String line1 = "EMERGENCY INT";
        g2.drawString(line1, (w - fm.stringWidth(line1)) / 2, textY);

        // BOTÓN
        boolean isPressed = getModel().isArmed();
        int offsetY = isPressed ? 3 : 0;
        int currentButtonY = staticButtonY + offsetY;

        g2.setPaint(new GradientPaint(0, staticButtonY, Color.GRAY, 0, staticButtonY + buttonDiameter, Color.WHITE));
        g2.fillOval(buttonX - 2, staticButtonY - 2, buttonDiameter + 4, buttonDiameter + 4);

        Point2D center = new Point2D.Float(buttonX + buttonDiameter / 2.0f, currentButtonY + buttonDiameter / 2.0f);
        float radius = buttonDiameter / 2.0f;
        float[] dist = { 0.0f, 0.85f, 1.0f };

        Color[] colors = isPressed
                ? new Color[] { new Color(200, 50, 50), new Color(150, 0, 0), new Color(50, 0, 0) }
                : new Color[] { new Color(255, 80, 80), new Color(200, 0, 0), new Color(100, 0, 0) };

        RadialGradientPaint spherePaint = new RadialGradientPaint(center, radius, dist, colors);
        g2.setPaint(spherePaint);
        g2.fillOval(buttonX, currentButtonY, buttonDiameter, buttonDiameter);

        // BRILLO
        g2.setPaint(new LinearGradientPaint(
                0, currentButtonY, 0, currentButtonY + buttonDiameter / 2,
                new float[] { 0f, 1f },
                new Color[] { new Color(255, 255, 255, 140), new Color(255, 255, 255, 0) }));
        g2.fillOval(buttonX + (buttonDiameter / 4), currentButtonY + 4, buttonDiameter / 2, buttonDiameter / 3);

        g2.dispose();
    }
}
