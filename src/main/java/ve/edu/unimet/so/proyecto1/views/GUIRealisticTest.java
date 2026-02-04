/*
 * GUIRealisticTest.java
 * Prueba realista de la GUI conectada al kernel del simulador RTOS
 * Actualizado para usar los nuevos métodos de snapshot de MainWindow
 */
package ve.edu.unimet.so.proyecto1.views;

import javax.swing.*;
import ve.edu.unimet.so.proyecto1.kernel.ClockThread;
import ve.edu.unimet.so.proyecto1.kernel.OperatingSystem;
import ve.edu.unimet.so.proyecto1.models.PCB;
import ve.edu.unimet.so.proyecto1.utils.ProcessGenerator;

public class GUIRealisticTest {

    // Componentes principales
    private final OperatingSystem os;
    private final ClockThread clock;
    private final MainWindow window;

    // Configuración
    private static final int QUANTUM = 4;
    private static final int CYCLE_DURATION_MS = 200; // ms por ciclo (ajustable)

    public GUIRealisticTest() {
        // 1. Inicializar el kernel
        this.os = new OperatingSystem(QUANTUM);

        // 2. Crear el ClockThread (motor único de la simulación)
        this.clock = new ClockThread(os, CYCLE_DURATION_MS);

        // 3. Crear la ventana
        this.window = new MainWindow();

        // 4. Generar procesos iniciales
        generateInitialProcesses();

        // 5. Configurar botones de control y emergencia
        setupControlButtons();

        // 6. Iniciar el loop de actualización de GUI
        startGUIRefreshLoop();
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
        window.getStartButton().addActionListener(e -> {
            startSimulation();
            window.getStartButton().setEnabled(false);
            window.getPauseButton().setEnabled(true);
            window.getStepButton().setEnabled(false);
        });

        // Botón PAUSE
        window.getPauseButton().addActionListener(e -> {
            pauseSimulation();
            window.getStartButton().setEnabled(true);
            window.getPauseButton().setEnabled(false);
            window.getStepButton().setEnabled(true);
        });

        // Botón STEP (solo cuando está pausado)
        window.getStepButton().addActionListener(e -> {
            stepSimulation();
        });

        // Botón GENERATE 5 - genera 5 procesos aleatorios
        window.getGenerateButton().addActionListener(e -> {
            long currentTick = os.getGlobalTick();
            PCB[] batch = ProcessGenerator.generateRandomBatch(5, currentTick);
            for (PCB p : batch) {
                os.submitNewProcess(p);
            }
            System.out.println("📦 Generados 5 procesos aleatorios en tick " + currentTick);
        });

        // Botón de EMERGENCIA - genera interrupción + proceso
        window.getEmergencyButton().addActionListener(e -> {
            long currentTick = os.getGlobalTick();

            // 1. Generar interrupción (activa modo KERNEL)
            os.submitInterrupt("MICROMETEORITO_MANUAL", 3);

            // 2. Crear proceso de emergencia de alta prioridad
            PCB emergency = ProcessGenerator.createEmergencyProcess(currentTick);
            os.submitNewProcess(emergency);

            System.out.println(
                    "🚨 EMERGENCIA: Interrupción + Proceso " + emergency.getName() + " en tick " + currentTick);
        });

        // Botón de CAMBIO DE ALGORITMO - cicla entre todos
        window.getAlgoButton().addActionListener(e -> {
            cycleSchedulingAlgorithm();
        });

        // Estado inicial de botones
        window.getPauseButton().setEnabled(false);
        window.getStepButton().setEnabled(true); // Permitir step antes de iniciar
        updateAlgoButtonLabel(); // Mostrar algoritmo actual
    }

    // Índice actual del algoritmo
    private int currentAlgoIndex = 0;
    private static final ve.edu.unimet.so.proyecto1.kernel.SchedulingPolicy[] ALGORITHMS = {
            ve.edu.unimet.so.proyecto1.kernel.SchedulingPolicy.FCFS,
            ve.edu.unimet.so.proyecto1.kernel.SchedulingPolicy.RR,
            ve.edu.unimet.so.proyecto1.kernel.SchedulingPolicy.SRT,
            ve.edu.unimet.so.proyecto1.kernel.SchedulingPolicy.PRIORITY,
            ve.edu.unimet.so.proyecto1.kernel.SchedulingPolicy.EDF
    };

    private void cycleSchedulingAlgorithm() {
        currentAlgoIndex = (currentAlgoIndex + 1) % ALGORITHMS.length;
        var newAlgo = ALGORITHMS[currentAlgoIndex];
        os.setAlgorithm(newAlgo);
        updateAlgoButtonLabel();
        System.out.println("🔄 Algoritmo cambiado a: " + newAlgo.name());
    }

    private void updateAlgoButtonLabel() {
        var algo = ALGORITHMS[currentAlgoIndex];
        window.getAlgoButton().setText("⚙ " + algo.name());
    }

    private void startGUIRefreshLoop() {
        // Timer de Swing para actualizar la GUI cada 100ms
        Timer refreshTimer = new Timer(100, e -> refreshGUI());
        refreshTimer.start();
    }

    private void refreshGUI() {
        SwingUtilities.invokeLater(() -> {
            long globalTick = os.getGlobalTick();

            // Actualizar reloj
            window.updateClock((int) globalTick);

            // Actualizar modo CPU (USER/KERNEL)
            window.updateCpuMode(os.isInKernelMode());

            // Actualizar CPU (proceso en ejecución)
            PCB[] running = os.snapshotRunning();
            if (running.length > 0 && running[0] != null) {
                PCB p = running[0];
                window.updateCPU(
                        p.getName(),
                        p.getProgramCounter(),
                        p.getTotalInstructions());
            } else {
                window.updateCPU(null, 0, 0);
            }

            // Actualizar todas las tablas usando snapshots directos
            window.updateNewTable(os.snapshotNew(), globalTick);
            window.updateReadyTable(os.snapshotReady(), globalTick);
            window.updateBlockedTable(os.snapshotBlocked(), globalTick);
            window.updateTerminatedTable(os.snapshotTerminated(), globalTick);
            window.updateReadySuspendedTable(os.snapshotReadySuspended(), globalTick);
            window.updateBlockedSuspendedTable(os.snapshotBlockedSuspended(), globalTick);

            // Actualizar log de eventos
            window.updateLog(os.snapshotEventLog());

            // Actualizar memoria (porcentaje de uso)
            updateMemoryBar();
        });
    }

    private void updateMemoryBar() {
        // Calcular uso de memoria basado en procesos residentes
        int ready = os.snapshotReady().length;
        int running = os.snapshotRunning().length;
        int blocked = os.snapshotBlocked().length;
        int total = ready + running + blocked;

        // Asumimos max 6 procesos en memoria (default de MemoryManager)
        int maxMemory = 6;
        int percentage = (total * 100) / maxMemory;
        window.updateMemory(Math.min(percentage, 100));
    }

    public void show() {
        window.setVisible(true);
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
