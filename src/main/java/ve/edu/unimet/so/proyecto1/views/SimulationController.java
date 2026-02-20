/*
 * SimulationController.java
 * Controla el ciclo de vida de la simulación: start, pause, step, velocidad
 */
package ve.edu.unimet.so.proyecto1.views;

import ve.edu.unimet.so.proyecto1.kernel.ClockThread;
import ve.edu.unimet.so.proyecto1.kernel.OperatingSystem;
import ve.edu.unimet.so.proyecto1.kernel.SchedulingPolicy;

/**
 * Controlador de simulación que maneja el estado y velocidad del reloj
 */
public class SimulationController {

    private final OperatingSystem os;
    private final ClockThread clock;
    private final MainWindow mainWindow;

    // Velocidad actual del ciclo en ms
    private int currentCycleDurationMs;

    // Límites de velocidad
    private static final int MIN_SPEED_MS = 10;
    private static final int MAX_SPEED_MS = 2000;

    public SimulationController(OperatingSystem os, ClockThread clock, MainWindow mainWindow, int initialSpeedMs) {
        this.os = os;
        this.clock = clock;
        this.mainWindow = mainWindow;
        this.currentCycleDurationMs = initialSpeedMs;
    }

    // --- Control de simulación ---

    public void startSimulation() {
        clock.startClock();
        System.out.println("Simulación iniciada - Ciclo: " + currentCycleDurationMs + "ms");
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

    public void stopSimulation() {
        clock.stopClock();
    }

    // --- Control de velocidad ---

    public void adjustSpeed(int deltaMs) {
        currentCycleDurationMs += deltaMs;
        applySpeedLimits();
        clock.setCycleDurationMs(currentCycleDurationMs);
        mainWindow.updateSpeedField(currentCycleDurationMs);
        System.out.println("⏱ Velocidad ajustada: " + currentCycleDurationMs + "ms por ciclo");
    }

    public void setSpeed(int speedMs) {
        currentCycleDurationMs = speedMs;
        applySpeedLimits();
        clock.setCycleDurationMs(currentCycleDurationMs);
        mainWindow.updateSpeedField(currentCycleDurationMs);
        System.out.println("⏱ Velocidad establecida: " + currentCycleDurationMs + "ms por ciclo");
    }

    private void applySpeedLimits() {
        if (currentCycleDurationMs < MIN_SPEED_MS) {
            currentCycleDurationMs = MIN_SPEED_MS;
        }
        if (currentCycleDurationMs > MAX_SPEED_MS) {
            currentCycleDurationMs = MAX_SPEED_MS;
        }
    }

    public int getCurrentSpeed() {
        return currentCycleDurationMs;
    }

    // --- Control de algoritmo ---

    public void setSchedulingAlgorithm(String name) {
        SchedulingPolicy policy = SchedulingPolicy.valueOf(name);
        os.setAlgorithm(policy);
        System.out.println("🔄 Algoritmo cambiado a: " + name);
    }
}
