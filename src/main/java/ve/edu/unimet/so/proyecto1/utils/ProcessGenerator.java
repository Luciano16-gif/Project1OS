/*
 * ProcessGenerator.java
 * Generador de procesos realistas para pruebas del simulador RTOS
 */
package ve.edu.unimet.so.proyecto1.utils;

import ve.edu.unimet.so.proyecto1.models.PCB;
import java.util.Random;

public class ProcessGenerator {

    private static int nextPid = 1;
    private static final Random rng = new Random();

    // Nombres de procesos temáticos de microsatélite
    private static final String[] TELEMETRY_NAMES = {
            "Telemetry_TX", "Sensor_Read", "Health_Check", "Status_Report"
    };
    private static final String[] CAMERA_NAMES = {
            "Cam_Capture", "Img_Compress", "Photo_Store", "Video_Stream"
    };
    private static final String[] NAV_NAMES = {
            "GPS_Update", "Orbit_Calc", "Attitude_Ctrl", "Star_Track"
    };
    private static final String[] EMERGENCY_NAMES = {
            "Collision_Avoid", "Solar_Shield", "Safe_Mode", "Emergency_TX"
    };
    private static final String[] LOW_PRIO_NAMES = {
            "Log_Write", "Cache_Clean", "Diag_Report", "Mem_Defrag"
    };

    /**
     * Resetea el contador de PIDs (útil para pruebas)
     */
    public static void resetPidCounter() {
        nextPid = 1;
    }

    /**
     * Proceso de telemetría: alta frecuencia, prioridad media, I/O frecuente
     */
    public static PCB createTelemetryProcess(long arrivalTick) {
        int instructions = 10 + rng.nextInt(11); // 10-20
        int priority = 50;
        long deadline = arrivalTick + 50;
        int ioEvery = 5;
        int ioService = 2;
        String name = TELEMETRY_NAMES[rng.nextInt(TELEMETRY_NAMES.length)];
        return new PCB(nextPid++, name, instructions, priority, arrivalTick, deadline, ioEvery, ioService);
    }

    /**
     * Proceso de cámara: largo, prioridad media-baja, I/O moderada
     */
    public static PCB createCameraProcess(long arrivalTick) {
        int instructions = 30 + rng.nextInt(21); // 30-50
        int priority = 40;
        long deadline = arrivalTick + 100;
        int ioEvery = 10;
        int ioService = 5;
        String name = CAMERA_NAMES[rng.nextInt(CAMERA_NAMES.length)];
        return new PCB(nextPid++, name, instructions, priority, arrivalTick, deadline, ioEvery, ioService);
    }

    /**
     * Proceso de navegación: medio, prioridad alta
     */
    public static PCB createNavigationProcess(long arrivalTick) {
        int instructions = 20 + rng.nextInt(11); // 20-30
        int priority = 60;
        long deadline = arrivalTick + 80;
        int ioEvery = 8;
        int ioService = 3;
        String name = NAV_NAMES[rng.nextInt(NAV_NAMES.length)];
        return new PCB(nextPid++, name, instructions, priority, arrivalTick, deadline, ioEvery, ioService);
    }

    /**
     * Proceso de emergencia: corto, máxima prioridad, sin I/O
     */
    public static PCB createEmergencyProcess(long arrivalTick) {
        int instructions = 5 + rng.nextInt(6); // 5-10
        int priority = 99;
        long deadline = arrivalTick + 20;
        int ioEvery = 0; // Sin I/O para mayor velocidad
        int ioService = 0;
        String name = EMERGENCY_NAMES[rng.nextInt(EMERGENCY_NAMES.length)];
        return new PCB(nextPid++, name, instructions, priority, arrivalTick, deadline, ioEvery, ioService);
    }

    /**
     * Proceso de baja prioridad: logs, diagnósticos, I/O frecuente
     */
    public static PCB createLowPriorityProcess(long arrivalTick) {
        int instructions = 15 + rng.nextInt(11); // 15-25
        int priority = 10;
        long deadline = arrivalTick + 200;
        int ioEvery = 3;
        int ioService = 4;
        String name = LOW_PRIO_NAMES[rng.nextInt(LOW_PRIO_NAMES.length)];
        return new PCB(nextPid++, name, instructions, priority, arrivalTick, deadline, ioEvery, ioService);
    }

    /**
     * Proceso completamente aleatorio
     */
    public static PCB createRandomProcess(long arrivalTick) {
        int type = rng.nextInt(5);
        return switch (type) {
            case 0 -> createTelemetryProcess(arrivalTick);
            case 1 -> createCameraProcess(arrivalTick);
            case 2 -> createNavigationProcess(arrivalTick);
            case 3 -> createLowPriorityProcess(arrivalTick);
            default -> createTelemetryProcess(arrivalTick); // Default a telemetría
        };
    }

    /**
     * Proceso simple sin I/O para pruebas básicas
     */
    public static PCB createSimpleProcess(long arrivalTick, int instructions, int priority) {
        long deadline = arrivalTick + instructions + 20;
        String name = "Process_" + nextPid;
        return new PCB(nextPid++, name, instructions, priority, arrivalTick, deadline, 0, 0);
    }

    /**
     * Genera un batch de procesos aleatorios
     */
    public static PCB[] generateRandomBatch(int count, long startTick) {
        PCB[] batch = new PCB[count];
        for (int i = 0; i < count; i++) {
            batch[i] = createRandomProcess(startTick);
        }
        return batch;
    }

    /**
     * Genera un batch mixto con todos los tipos
     */
    public static PCB[] generateMixedBatch(long startTick) {
        PCB[] batch = new PCB[5];
        batch[0] = createTelemetryProcess(startTick);
        batch[1] = createCameraProcess(startTick);
        batch[2] = createNavigationProcess(startTick);
        batch[3] = createEmergencyProcess(startTick);
        batch[4] = createLowPriorityProcess(startTick);
        return batch;
    }
}
