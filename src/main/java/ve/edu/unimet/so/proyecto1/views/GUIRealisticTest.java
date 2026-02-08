/*
 * GUIRealisticTest.java
 * Coordinador principal de la aplicación GUI del simulador RTOS
 * Refactorizado: usa SimulationController y GUIUpdater
 */
package ve.edu.unimet.so.proyecto1.views;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import ve.edu.unimet.so.proyecto1.kernel.ClockThread;
import ve.edu.unimet.so.proyecto1.kernel.OperatingSystem;
import ve.edu.unimet.so.proyecto1.models.PCB;
import ve.edu.unimet.so.proyecto1.utils.ProcessGenerator;

/**
 * Coordinador principal que inicializa y conecta todos los componentes
 */
public class GUIRealisticTest {

    // Componentes del sistema
    private final OperatingSystem os;
    private final ClockThread clock;
    private final MainWindow mainWindow;

    // Subcomponentes refactorizados
    private final SimulationController simulationController;
    private final GUIUpdater guiUpdater;

    // Configuración
    private static final int QUANTUM = 4;
    private static final int INITIAL_CYCLE_DURATION_MS = 200;

    public GUIRealisticTest() {
        // 1. Inicializar el kernel
        this.os = new OperatingSystem(QUANTUM);

        // 2. Crear el ClockThread (motor de la simulación)
        this.clock = new ClockThread(os, INITIAL_CYCLE_DURATION_MS);

        // 3. Crear la ventana principal
        this.mainWindow = new MainWindow();

        // 4. Crear controladores
        this.simulationController = new SimulationController(os, clock, mainWindow, INITIAL_CYCLE_DURATION_MS);
        this.guiUpdater = new GUIUpdater(os, mainWindow);

        // 5. Generar procesos iniciales
        generateInitialProcesses();

        // 6. Registrar tareas periodicas base
        registerDefaultPeriodicTasks();

        // 7. Configurar botones de control
        setupControlButtons();

        // 8. Iniciar el loop de actualización de GUI
        guiUpdater.startRefreshLoop();

        // 9. Manejar cierre de ventana
        setupWindowClosing();
    }

    private void generateInitialProcesses() {
        ProcessGenerator.resetPidCounter();
        PCB[] batch = ProcessGenerator.generateMixedBatch(0);
        for (PCB p : batch) {
            os.submitNewProcess(p);
        }
        System.out.println("Generados " + batch.length + " procesos iniciales");
    }

    private void registerDefaultPeriodicTasks() {
        // Configuracion conservadora para evitar sobrecarga continua del sistema.
        os.registerPeriodicTask("P_Telemetry", 6, 65, 360, 360, 4, 2, 60);
        os.registerPeriodicTask("P_HealthCheck", 5, 70, 480, 480, 0, 0, 120);
        os.registerPeriodicTask("P_AttitudeCtrl", 7, 75, 320, 320, 0, 0, 180);
        os.registerPeriodicTask("P_SensorSweep", 4, 60, 440, 440, 5, 2, 240);
        System.out.println("Registradas " + os.getPeriodicTaskCount() + " tareas periodicas");
    }

    private void setupControlButtons() {
        // Botón START
        mainWindow.getStartButton().addActionListener(e -> {
            simulationController.startSimulation();
            mainWindow.getStartButton().setEnabled(false);
            mainWindow.getPauseButton().setEnabled(true);
            mainWindow.getStepButton().setEnabled(false);
        });

        // Botón PAUSE
        mainWindow.getPauseButton().addActionListener(e -> {
            simulationController.pauseSimulation();
            mainWindow.getStartButton().setEnabled(true);
            mainWindow.getPauseButton().setEnabled(false);
            mainWindow.getStepButton().setEnabled(true);
        });

        // Botón STEP
        mainWindow.getStepButton().addActionListener(e -> {
            simulationController.stepSimulation();
        });

        // Botón GENERATE 1
        mainWindow.getGenerateOneButton().addActionListener(e -> {
            long currentTick = os.getGlobalTick();
            PCB p = ProcessGenerator.createRandomProcess(currentTick);
            os.submitNewProcess(p);
            System.out.println("📦 Generado 1 proceso: " + p.getName());
        });

        // Botón GENERATE 5
        mainWindow.getGenerateFiveButton().addActionListener(e -> {
            long currentTick = os.getGlobalTick();
            PCB[] batch = ProcessGenerator.generateRandomBatch(5, currentTick);
            for (PCB p : batch) {
                os.submitNewProcess(p);
            }
            System.out.println("📦 Generados 5 procesos aleatorios");
        });

        // Botón GENERATE 20
        mainWindow.getGenerateTwentyButton().addActionListener(e -> {
            long currentTick = os.getGlobalTick();
            PCB[] batch = ProcessGenerator.generateRandomBatch(20, currentTick);
            for (PCB p : batch) {
                os.submitNewProcess(p);
            }
            System.out.println("📦 Generados 20 procesos (memoria llena → swap!)");
        });

        // Botón SPEED DOWN
        mainWindow.getSpeedDownButton().addActionListener(e -> {
            simulationController.adjustSpeed(100);
        });

        // Botón SPEED UP
        mainWindow.getSpeedUpButton().addActionListener(e -> {
            simulationController.adjustSpeed(-50);
        });

        // Campo de velocidad
        mainWindow.getSpeedField().addActionListener(e -> {
            try {
                int newSpeed = Integer.parseInt(mainWindow.getSpeedField().getText().trim());
                simulationController.setSpeed(newSpeed);
            } catch (NumberFormatException ex) {
                mainWindow.updateSpeedField(simulationController.getCurrentSpeed());
            }
        });

        // Botón de EMERGENCIA
        mainWindow.getEmergencyButton().addActionListener(e -> {
            long currentTick = os.getGlobalTick();
            os.submitInterrupt("MICROMETEORITO_MANUAL", 3);
            PCB emergency = ProcessGenerator.createEmergencyProcess(currentTick);
            os.submitNewProcess(emergency);
            System.out.println(
                    "🚨 EMERGENCIA: Interrupción + Proceso " + emergency.getName() + " en tick " + currentTick);
        });

        // ComboBox de ALGORITMO
        mainWindow.getAlgorithmComboBox().addActionListener(e -> {
            String selected = (String) mainWindow.getAlgorithmComboBox().getSelectedItem();
            simulationController.setSchedulingAlgorithm(selected);
        });

        // Estado inicial de botones
        mainWindow.getPauseButton().setEnabled(false);
        mainWindow.getStepButton().setEnabled(false);
    }

    private void setupWindowClosing() {
        mainWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                guiUpdater.stopRefreshLoop();
                simulationController.stopSimulation();
            }
        });
    }

    public void show() {
        mainWindow.setVisible(true);
    }
}
