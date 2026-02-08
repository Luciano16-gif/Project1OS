/*
 * GUIUpdater.java
 * Actualiza la GUI con datos del kernel (refresh, métricas, memoria)
 */
package ve.edu.unimet.so.proyecto1.views;

import javax.swing.Timer;
import ve.edu.unimet.so.proyecto1.kernel.OperatingSystem;

/**
 * Actualizador de GUI que refresca la interfaz con datos del kernel
 */
public class GUIUpdater {

    private final OperatingSystem os;
    private final MainWindow mainWindow;
    private Timer refreshTimer;

    // Intervalo de refresh en ms
    private static final int REFRESH_INTERVAL_MS = 100;

    public GUIUpdater(OperatingSystem os, MainWindow mainWindow) {
        this.os = os;
        this.mainWindow = mainWindow;
    }

    /**
     * Inicia el loop de actualización de GUI
     */
    public void startRefreshLoop() {
        refreshTimer = new Timer(REFRESH_INTERVAL_MS, e -> refreshGUI());
        refreshTimer.start();
    }

    /**
     * Detiene el loop de actualización
     */
    public void stopRefreshLoop() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }

    /**
     * Refresca todos los componentes de la GUI con datos actuales del kernel
     */
    private void refreshGUI() {
        OperatingSystem.GuiSnapshot snapshot = os.snapshotForGui();
        long globalTick = snapshot.globalTick;

        // Actualizar reloj
        mainWindow.updateClock((int) globalTick);

        // Actualizar modo CPU (USER/KERNEL)
        mainWindow.updateCpuMode(snapshot.kernelMode);

        // Actualizar CPU (proceso en ejecución) usando snapshot inmutable por fila
        Object[] runningRow = snapshot.runningRow;
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

        // Actualizar tablas con un snapshot atómico por tick
        mainWindow.updateNewTableRows(snapshot.newRows);
        mainWindow.updateReadyTableRows(snapshot.readyRows);
        mainWindow.updateBlockedTableRows(snapshot.blockedRows);
        mainWindow.updateTerminatedTableRows(snapshot.terminatedRows);
        mainWindow.updateReadySuspendedTableRows(snapshot.readySuspendedRows);
        mainWindow.updateBlockedSuspendedTableRows(snapshot.blockedSuspendedRows);

        // Actualizar log de eventos
        mainWindow.updateLog(snapshot.eventLog);

        // Actualizar memoria (porcentaje de uso)
        updateMemoryBar(snapshot.residentProcessCount, snapshot.maxProcessesInMemory);

        // Actualizar métricas
        updateMetrics(globalTick, snapshot.missionSuccessRate, snapshot.throughput,
                snapshot.averageWaitingTime, snapshot.cpuUtilizationTotal);
    }

    private void updateMetrics(long globalTick, double successRate, double throughput,
            double avgWait, double cpuUtilTotal) {
        mainWindow.updateMetrics(successRate, throughput, avgWait, cpuUtilTotal);

        // Agregar punto a gráfica cada 5 ticks
        if (globalTick % 5 == 0) {
            mainWindow.addCpuUtilDataPoint(cpuUtilTotal);
        }
    }

    private void updateMemoryBar(int residentProcessCount, int maxMemory) {
        if (maxMemory <= 0) {
            maxMemory = 1;
        }
        int percentage = (residentProcessCount * 100) / maxMemory;
        mainWindow.updateMemory(Math.min(percentage, 100));
    }
}
