/*
 * GUIRealisticTest.java
 * Prueba realista de la GUI conectada al kernel del simulador RTOS
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
    private static final int INITIAL_PROCESSES = 5;

    public GUIRealisticTest() {
        // 1. Inicializar el kernel
        this.os = new OperatingSystem(QUANTUM);

        // 2. Crear el ClockThread (motor único de la simulación)
        this.clock = new ClockThread(os, CYCLE_DURATION_MS);

        // 3. Crear la ventana
        this.window = new MainWindow();

        // 4. Generar procesos iniciales
        generateInitialProcesses();

        // 5. Iniciar el loop de actualización de GUI
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

    private void startGUIRefreshLoop() {
        // Timer de Swing para actualizar la GUI cada 100ms
        Timer refreshTimer = new Timer(100, e -> refreshGUI());
        refreshTimer.start();
    }

    private void refreshGUI() {
        SwingUtilities.invokeLater(() -> {
            // Actualizar reloj
            window.updateClock((int) os.getGlobalTick());

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

            // Actualizar tablas de colas
            refreshReadyTable();
            refreshBlockedTable();
            refreshSuspendedTables();

            // Actualizar memoria (porcentaje de uso)
            updateMemoryBar();
        });
    }

    private void refreshReadyTable() {
        window.clearReady();
        PCB[] ready = os.snapshotReady();
        for (PCB p : ready) {
            if (p != null) {
                window.addRowToReady(new Object[] {
                        String.valueOf(p.getPid()),
                        p.getName(),
                        String.valueOf(p.getPriority())
                });
            }
        }
    }

    private void refreshBlockedTable() {
        window.clearBlocked();
        PCB[] blocked = os.snapshotBlocked();
        for (PCB p : blocked) {
            if (p != null) {
                window.addRowToBlocked(new Object[] {
                        String.valueOf(p.getPid()),
                        p.getName(),
                        p.getIoRemainingTicks() + " ticks"
                });
            }
        }
    }

    private void refreshSuspendedTables() {
        // Ready-Suspended
        window.clearReadySuspended();
        PCB[] readySusp = os.snapshotReadySuspended();
        for (PCB p : readySusp) {
            if (p != null) {
                window.addRowToReadySuspended(new Object[] {
                        String.valueOf(p.getPid()),
                        p.getName(),
                        String.valueOf(p.getPriority())
                });
            }
        }

        // Blocked-Suspended
        window.clearBlockedSuspended();
        PCB[] blockedSusp = os.snapshotBlockedSuspended();
        for (PCB p : blockedSusp) {
            if (p != null) {
                window.addRowToBlockedSuspended(new Object[] {
                        String.valueOf(p.getPid()),
                        p.getName(),
                        p.getIoRemainingTicks() + " ticks"
                });
            }
        }
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
        System.out.println("Iniciando...\n");

        SwingUtilities.invokeLater(() -> {
            GUIRealisticTest test = new GUIRealisticTest();
            test.show();

            // Auto-iniciar la simulación después de 1 segundo
            Timer autoStart = new Timer(1000, e -> {
                test.startSimulation();
            });
            autoStart.setRepeats(false);
            autoStart.start();
        });
    }
}
