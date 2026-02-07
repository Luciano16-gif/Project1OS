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

        // Botón GENERATE 1 - genera 1 proceso aleatorio
        window.getGen1Button().addActionListener(e -> {
            long currentTick = os.getGlobalTick();
            PCB p = ProcessGenerator.createRandomProcess(currentTick);
            os.submitNewProcess(p);
            System.out.println("📦 Generado 1 proceso: " + p.getName());
        });

        // Botón GENERATE 5 - genera 5 procesos aleatorios
        window.getGenerateButton().addActionListener(e -> {
            long currentTick = os.getGlobalTick();
            PCB[] batch = ProcessGenerator.generateRandomBatch(5, currentTick);
            for (PCB p : batch) {
                os.submitNewProcess(p);
            }
            System.out.println("📦 Generados 5 procesos aleatorios");
        });

        // Botón GENERATE 20 - genera 20 procesos (fuerza swap)
        window.getGen20Button().addActionListener(e -> {
            long currentTick = os.getGlobalTick();
            PCB[] batch = ProcessGenerator.generateRandomBatch(20, currentTick);
            for (PCB p : batch) {
                os.submitNewProcess(p);
            }
            System.out.println("📦 Generados 20 procesos (memoria llena → swap!)");
        });

        // Botón SPEED DOWN - más lento
        window.getSpeedDownButton().addActionListener(e -> {
            adjustSpeed(100); // +100ms
        });

        // Botón SPEED UP - más rápido
        window.getSpeedUpButton().addActionListener(e -> {
            adjustSpeed(-50); // -50ms
        });

        // Campo de velocidad - entrada manual
        window.getSpeedField().addActionListener(e -> {
            try {
                int newSpeed = Integer.parseInt(window.getSpeedField().getText().trim());
                setSpeed(newSpeed);
            } catch (NumberFormatException ex) {
                window.updateSpeedField(currentCycleDurationMs); // Restaurar valor válido
            }
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

        // ComboBox de ALGORITMO - selección directa
        window.getAlgoCombo().addActionListener(e -> {
            String selected = (String) window.getAlgoCombo().getSelectedItem();
            setSchedulingAlgorithm(selected);
        });

        // Estado inicial de botones
        window.getPauseButton().setEnabled(false);
        window.getStepButton().setEnabled(true); // Permitir step antes de iniciar
    }

    private void setSchedulingAlgorithm(String name) {
        var policy = ve.edu.unimet.so.proyecto1.kernel.SchedulingPolicy.valueOf(name);
        os.setAlgorithm(policy);
        System.out.println("🔄 Algoritmo cambiado a: " + name);
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

            // Actualizar detalles del proceso en ejecución
            PCB runningProcess = (running.length > 0) ? running[0] : null;
            window.updateRunningDetails(runningProcess, globalTick);

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

            // Actualizar métricas
            updateMetrics(globalTick, running);
        });
    }

    // --- Contadores para métricas ---
    private long lastProcessedTick = -1;
    private long cpuBusyTicks = 0; // Acumulativo total

    // Ventana deslizante para utilización reciente (últimos 50 ticks)
    private static final int WINDOW_SIZE = 50;
    private final boolean[] cpuWindow = new boolean[WINDOW_SIZE];
    private int windowIndex = 0;
    private int windowBusyCount = 0;

    private void updateMetrics(long globalTick, PCB[] running) {
        boolean isBusy = running.length > 0 && running[0] != null;

        // Solo contar una vez por tick (evitar doble conteo por refresh de GUI)
        if (globalTick > lastProcessedTick) {
            // Acumulativo total
            if (isBusy) {
                cpuBusyTicks++;
            }

            // Ventana deslizante: restar el valor antiguo, sumar el nuevo
            if (cpuWindow[windowIndex]) {
                windowBusyCount--;
            }
            cpuWindow[windowIndex] = isBusy;
            if (isBusy) {
                windowBusyCount++;
            }
            windowIndex = (windowIndex + 1) % WINDOW_SIZE;

            lastProcessedTick = globalTick;
        }

        // Calcular métricas desde TERMINATED
        PCB[] terminated = os.snapshotTerminated();
        int terminatedCount = terminated.length;
        int successCount = 0;
        long totalWaitTicks = 0;

        for (int i = 0; i < terminatedCount; i++) {
            PCB p = terminated[i];
            if (p != null) {
                // Éxito = terminó antes o en el deadline
                if (p.getFinishTick() <= p.getDeadlineTick()) {
                    successCount++;
                }
                totalWaitTicks += p.getWaitingTime();
            }
        }

        // Calcular valores
        double successRate = (terminatedCount > 0) ? (double) successCount / terminatedCount : 0.0;
        double throughput = (globalTick > 0) ? (double) terminatedCount / globalTick : 0.0;
        double avgWait = (terminatedCount > 0) ? (double) totalWaitTicks / terminatedCount : 0.0;

        // CPU: usar ventana deslizante para valor reciente
        int windowFilled = (int) Math.min(globalTick, WINDOW_SIZE);
        double cpuUtilRecent = (windowFilled > 0) ? (double) windowBusyCount / windowFilled : 0.0;

        // CPU acumulativo para el label
        double cpuUtilTotal = (globalTick > 0) ? (double) cpuBusyTicks / globalTick : 0.0;

        // Actualizar panel de métricas (mostrar ambos: reciente y total)
        window.updateMetrics(successRate, throughput, avgWait, cpuUtilRecent);

        // Agregar punto a gráfica cada 5 ticks (usar valor reciente)
        if (globalTick % 5 == 0) {
            window.addCpuUtilDataPoint(cpuUtilRecent);
        }
    }

    private void updateMemoryBar() {
        // Calcular uso de memoria basado en procesos residentes
        int ready = os.snapshotReady().length;
        int running = os.snapshotRunning().length;
        int blocked = os.snapshotBlocked().length;
        int total = ready + running + blocked;

        int maxMemory = os.getMaxProcessesInMemory();
        if (maxMemory <= 0) {
            maxMemory = 1;
        }
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
        window.updateSpeedField(currentCycleDurationMs);
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
        window.updateSpeedField(currentCycleDurationMs);
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
