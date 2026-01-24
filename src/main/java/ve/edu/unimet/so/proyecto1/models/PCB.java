/*
 * PCB.java
 */
package ve.edu.unimet.so.proyecto1.models;

public class PCB {

    // --- Identificación ---
    private final int pid;
    private final String name;
    private ProcessState state;

    // --- Registros y Ejecución ---
    private int programCounter;
    private int mar;
    private final int totalInstructions;

    // --- Planificación (RTOS) ---
    private final int priority;
    private final long arrivalTick;
    private final long deadlineTick; // Deadline absoluto

    // --- Entrada/Salida (I/O) ---
    private final int ioEventCycle;      // Instrucción donde ocurre el bloqueo (-1 si no tiene)
    private final int ioServiceDuration; // Duración del bloqueo
    private int ioWaitedTicks;

    // --- Métricas ---
    private long startTick = -1;
    private long finishTick = -1;
    private long waitingTime = 0;

    public PCB(int pid, String name, int totalInstructions, int priority, long arrivalTick, long deadlineTick, int ioEventCycle, int ioServiceDuration) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null/blank");
        }
        if (totalInstructions <= 0) {
            throw new IllegalArgumentException("totalInstructions must be > 0");
        }
        if (deadlineTick < arrivalTick) {
            throw new IllegalArgumentException("deadlineTick must be >= arrivalTick");
        }
        if (ioEventCycle < -1 || ioEventCycle > totalInstructions || ioEventCycle == 0) {
            throw new IllegalArgumentException("ioEventCycle must be -1 or in [1, totalInstructions]");
        }
        if (ioServiceDuration < 0) {
            throw new IllegalArgumentException("ioServiceDuration must be >= 0");
        }

        this.pid = pid;
        this.name = name;
        this.totalInstructions = totalInstructions;
        this.priority = priority;
        this.arrivalTick = arrivalTick;
        this.deadlineTick = deadlineTick;
        this.ioEventCycle = ioEventCycle;
        this.ioServiceDuration = ioServiceDuration;

        this.state = ProcessState.NEW;
        this.programCounter = 0;
        this.mar = 0;
        this.ioWaitedTicks = 0;
    }

    // --- Getters y Setters Básicos ---
    
    public int getPid() { return pid; }
    public String getName() { return name; }
    public ProcessState getState() { return state; }
    public void setState(ProcessState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        this.state = state;
    }

    public int getProgramCounter() { return programCounter; }
    public int getMar() { return mar; }
    public int getTotalInstructions() { return totalInstructions; }
    
    public int getRemainingInstructions() {
        return Math.max(0, totalInstructions - programCounter);
    }

    public int getPriority() { return priority; }
    public long getArrivalTick() { return arrivalTick; }
    public long getDeadlineTick() { return deadlineTick; }

    public int getIoEventCycle() { return ioEventCycle; }
    public int getIoServiceDuration() { return ioServiceDuration; }
    public int getIoWaitedTicks() { return ioWaitedTicks; }

    public long getStartTick() { return startTick; }
    public long getFinishTick() { return finishTick; }
    public long getWaitingTime() { return waitingTime; }

    // --- Lógica de Simulación ---

    public void executeCycle() {
        if (programCounter < totalInstructions) {
            programCounter++;
            mar++;
        }
    }

    public void incrementIoWait() {
        this.ioWaitedTicks++;
    }
    
    public void resetIoWait() {
        this.ioWaitedTicks = 0;
    }

    public void incrementWaitingTime() {
        this.waitingTime++;
    }

    public void setStartTick(long tick) {
        if (this.startTick == -1) {
            this.startTick = tick;
        }
    }

    public void setFinishTick(long tick) {
        this.finishTick = tick;
    }

    public boolean hasFinished() {
        return programCounter >= totalInstructions;
    }

    public boolean shouldTriggerIO() {
        // Expected to be checked AFTER executeCycle() for the current tick.
        return ioEventCycle != -1 && programCounter == ioEventCycle;
    }
    
    public long getDeadlineRemaining(long currentTick) {
        return deadlineTick - currentTick;
    }

    @Override
    public String toString() {
        return String.format("PCB{ID=%d, Name='%s', State=%s, PC=%d/%d, Prio=%d}", 
                pid, name, state, programCounter, totalInstructions, priority);
    }
}
