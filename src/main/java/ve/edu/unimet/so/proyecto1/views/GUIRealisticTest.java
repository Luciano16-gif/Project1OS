/*
 * GUIRealisticTest.java
 * Prueba realista de la GUI conectada al kernel del simulador RTOS
 * Actualizado para usar los nuevos métodos de snapshot de MainWindow
 */
package ve.edu.unimet.so.proyecto1.views;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import ve.edu.unimet.so.proyecto1.kernel.ClockThread;
import ve.edu.unimet.so.proyecto1.kernel.OperatingSystem;
import ve.edu.unimet.so.proyecto1.models.PCB;
import ve.edu.unimet.so.proyecto1.utils.ProcessGenerator;

public class GUIRealisticTest {

    // Componentes principales
    private final OperatingSystem os;
    private final ClockThread clock;
    private final MainWindow mainWindow;
    private Timer refreshTimer;

    // Configuración
    private static final int QUANTUM = 4;
    private static final int CYCLE_DURATION_MS = 200; // ms por ciclo (ajustable)

    public GUIRealisticTest() {
        // 1. Inicializar el kernel
        this.os = new OperatingSystem(QUANTUM);

        // 2. Crear el ClockThread (motor único de la simulación)
        this.clock = new ClockThread(os, CYCLE_DURATION_MS);

        // 3. Crear la ventana
        this.mainWindow = new MainWindow();

        // 4. Generar procesos iniciales
        generateInitialProcesses();

        // 5. Configurar botones de control y emergencia
        setupControlButtons();

        // 6. Iniciar el loop de actualización de GUI
        startGUIRefreshLoop();

        // Asegura liberar hilos al cerrar la ventana.
        this.mainWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (refreshTimer != null) {
                    refreshTimer.stop();
                }
                clock.stopClock();
            }
        });
    }

    private void generateInitialProcesses() {
        ProcessGenerator.resetPidCounter();

        // Generar un batch mixto de procesos
        PCB[] batch = ProcessGenerator.generateMixedBatch(0);
        for (PCB p : batch) {
            os.submitNewProcess(p);
        }

        System.out.println("Generados " + batch.length + " procesos iniciales");
    }

    private void setupControlButtons() {
        // Botón START
        mainWindow.getStartButton().addActionListener(e -> {
            startSimulation();
            mainWindow.getStartButton().setEnabled(false);
            mainWindow.getPauseButton().setEnabled(true);
            mainWindow.getStepButton().setEnabled(false);
        });

        // Botón PAUSE
        mainWindow.getPauseButton().addActionListener(e -> {
            pauseSimulation();
            mainWindow.getStartButton().setEnabled(true);
            mainWindow.getPauseButton().setEnabled(false);
            mainWindow.getStepButton().setEnabled(true);
        });

        // Botón STEP (solo cuando está pausado)
        mainWindow.getStepButton().addActionListener(e -> {
            stepSimulation();
        });

        // Botón GENERATE 1 - genera 1 proceso aleatorio
        mainWindow.getGenerateOneButton().addActionListener(e -> {
            long currentTick = os.getGlobalTick();
            PCB p = ProcessGenerator.createRandomProcess(currentTick);
            os.submitNewProcess(p);
            System.out.println("📦 Generado 1 proceso: " + p.getName());
        });

        // Botón GENERATE 5 - genera 5 procesos aleatorios
        mainWindow.getGenerateFiveButton().addActionListener(e -> {
            long currentTick = os.getGlobalTick();
            PCB[] batch = ProcessGenerator.generateRandomBatch(5, currentTick);
            for (PCB p : batch) {
                os.submitNewProcess(p);
            }
            System.out.println("📦 Generados 5 procesos aleatorios");
        });

        // Botón GENERATE 20 - genera 20 procesos (fuerza swap)
        mainWindow.getGenerateTwentyButton().addActionListener(e -> {
            long currentTick = os.getGlobalTick();
            PCB[] batch = ProcessGenerator.generateRandomBatch(20, currentTick);
            for (PCB p : batch) {
                os.submitNewProcess(p);
            }
            System.out.println("📦 Generados 20 procesos (memoria llena → swap!)");
        });

        // Botón SPEED DOWN - más lento
        mainWindow.getSpeedDownButton().addActionListener(e -> {
            adjustSpeed(100); // +100ms
        });

        // Botón SPEED UP - más rápido
        mainWindow.getSpeedUpButton().addActionListener(e -> {
            adjustSpeed(-50); // -50ms
        });

        // Campo de velocidad - entrada manual
        mainWindow.getSpeedField().addActionListener(e -> {
            try {
                int newSpeed = Integer.parseInt(mainWindow.getSpeedField().getText().trim());
                setSpeed(newSpeed);
            } catch (NumberFormatException ex) {
                mainWindow.updateSpeedField(currentCycleDurationMs); // Restaurar valor válido
            }
        });

        // Botón de EMERGENCIA - genera interrupción + proceso
        mainWindow.getEmergencyButton().addActionListener(e -> {
            long currentTick = os.getGlobalTick();

            // 1. Generar interrupción (activa modo KERNEL)
            os.submitInterrupt("MICROMETEORITO_MANUAL", 3);

            // 2. Crear proceso de emergencia de alta prioridad
            PCB emergency = ProcessGenerator.createEmergencyProcess(currentTick);
            os.submitNewProcess(emergency);

            System.out.println(
                    "🚨 EMERGENCIA: Interrupción + Proceso " + emergency.getName() + " en tick " + currentTick);
        });

        // ComboBox de ALGORITMO - selección directa
        mainWindow.getAlgorithmComboBox().addActionListener(e -> {
            String selected = (String) mainWindow.getAlgorithmComboBox().getSelectedItem();
            setSchedulingAlgorithm(selected);
        });

        // Estado inicial de botones
        mainWindow.getPauseButton().setEnabled(false);
        mainWindow.getStepButton().setEnabled(false); // STEP solo cuando está pausado
    }

    private void setSchedulingAlgorithm(String name) {
        var policy = ve.edu.unimet.so.proyecto1.kernel.SchedulingPolicy.valueOf(name);
        os.setAlgorithm(policy);
        System.out.println("🔄 Algoritmo cambiado a: " + name);
    }

    private void startGUIRefreshLoop() {
        // Timer de Swing para actualizar la GUI cada 100ms
        refreshTimer = new Timer(100, e -> refreshGUI());
        refreshTimer.start();
    }

    private void refreshGUI() {
        SwingUtilities.invokeLater(() -> {
            long globalTick = os.getGlobalTick();

            // Actualizar reloj
            mainWindow.updateClock((int) globalTick);

            // Actualizar modo CPU (USER/KERNEL)
            mainWindow.updateCpuMode(os.isInKernelMode());

            // Actualizar CPU (proceso en ejecución) usando snapshot inmutable por fila
            Object[] runningRow = os.snapshotRunningRow();
            if (runningRow != null && runningRow.length >= 8) {
                String processName = String.valueOf(runningRow[1]);
                int programCounter = ((Number) runningRow[3]).intValue();
                int remaining = ((Number) runningRow[6]).intValue();
                int totalInstructions = Math.max(1, programCounter + remaining);
                mainWindow.updateCPU(processName, programCounter, totalInstructions);
            } else {
                mainWindow.updateCPU(null, 0, 0);
            }

            // Actualizar detalles del proceso en ejecución
            mainWindow.updateRunningDetailsRow(runningRow);

            // Actualizar tablas con snapshots por filas (sin exponer PCB mutable)
            mainWindow.updateNewTableRows(os.snapshotNewRows());
            mainWindow.updateReadyTableRows(os.snapshotReadyRows());
            mainWindow.updateBlockedTableRows(os.snapshotBlockedRows());
            mainWindow.updateTerminatedTableRows(os.snapshotTerminatedRows());
            mainWindow.updateReadySuspendedTableRows(os.snapshotReadySuspendedRows());
            mainWindow.updateBlockedSuspendedTableRows(os.snapshotBlockedSuspendedRows());

            // Actualizar log de eventos
            mainWindow.updateLog(os.snapshotEventLog());

            // Actualizar memoria (porcentaje de uso)
            updateMemoryBar();

            // Actualizar métricas
            updateMetrics(globalTick);
        });
    }

    private void updateMetrics(long globalTick) {
        double successRate = os.getMissionSuccessRate();
        double throughput = os.getThroughput();
        double avgWait = os.getAverageWaitingTime();
        double cpuUtilTotal = os.getCpuUtilizationTotal();

        mainWindow.updateMetrics(successRate, throughput, avgWait, cpuUtilTotal);

        // Agregar punto a gráfica cada 5 ticks
        if (globalTick % 5 == 0) {
            mainWindow.addCpuUtilDataPoint(cpuUtilTotal);
        }
    }

    private void updateMemoryBar() {
        int total = os.getResidentProcessCount();
        int maxMemory = os.getMaxProcessesInMemory();
        if (maxMemory <= 0) {
            maxMemory = 1;
        }
        int percentage = (total * 100) / maxMemory;
        mainWindow.updateMemory(Math.min(percentage, 100));
    }

    public void show() {
        mainWindow.setVisible(true);
    }

    public void startSimulation() {
        clock.startClock();
        System.out.println("Simulación iniciada - Quantum: " + QUANTUM + ", Ciclo: " + CYCLE_DURATION_MS + "ms");
    }

    public void pauseSimulation() {
        clock.pauseClock();
        System.out.println("Simulación pausada");
    }

    public void resumeSimulation() {
        clock.resumeClock();
        System.out.println("Simulación reanudada");
    }

    public void stepSimulation() {
        clock.stepOnce();
    }

    // Velocidad actual del ciclo en ms
    private int currentCycleDurationMs = CYCLE_DURATION_MS;

    private void adjustSpeed(int deltaMs) {
        currentCycleDurationMs += deltaMs;
        // Limitar entre 10ms (muy rápido) y 2000ms (muy lento)
        if (currentCycleDurationMs < 10)
            currentCycleDurationMs = 10;
        if (currentCycleDurationMs > 2000)
            currentCycleDurationMs = 2000;

        clock.setCycleDurationMs(currentCycleDurationMs);
        mainWindow.updateSpeedField(currentCycleDurationMs);
        System.out.println("⏱ Velocidad ajustada: " + currentCycleDurationMs + "ms por ciclo");
    }

    private void setSpeed(int speedMs) {
        // Limitar entre 10ms (muy rápido) y 2000ms (muy lento)
        if (speedMs < 10)
            speedMs = 10;
        if (speedMs > 2000)
            speedMs = 2000;

        currentCycleDurationMs = speedMs;
        clock.setCycleDurationMs(currentCycleDurationMs);
        mainWindow.updateSpeedField(currentCycleDurationMs);
        System.out.println("⏱ Velocidad establecida: " + currentCycleDurationMs + "ms por ciclo");
    }

    // --- MAIN ---
    public static void main(String[] args) {
        System.out.println("=== GUIRealisticTest - Prueba de GUI con Kernel Real ===");
        System.out.println("Use los botones START/PAUSE/STEP para controlar la simulación\n");

        SwingUtilities.invokeLater(() -> {
            GUIRealisticTest test = new GUIRealisticTest();
            test.show();
            // La simulación ahora se controla con los botones en la GUI
        });
    }
}
