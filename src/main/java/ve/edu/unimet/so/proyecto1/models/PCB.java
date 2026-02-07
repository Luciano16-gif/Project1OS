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
    private final int ioEveryTicks;      // 0 = nunca, N = cada N instrucciones ejecutadas
    private int ioTriggerCountdown;      // cuenta hacia 0 mientras corre
    private final int ioServiceTicks;    // duración del bloqueo
    private int ioRemainingTicks;        // ticks restantes mientras está bloqueado

    // --- Métricas ---
    private long startTick = -1;
    private long finishTick = -1;
    private long waitingTime = 0;
    private boolean deadlineMissed = false;

    public PCB(int pid, String name, int totalInstructions, int priority, long arrivalTick, long deadlineTick, int ioEveryTicks, int ioServiceTicks) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null/blank");
        }
        if (totalInstructions <= 0) {
            throw new IllegalArgumentException("totalInstructions must be > 0");
        }
        if (deadlineTick < arrivalTick) {
            throw new IllegalArgumentException("deadlineTick must be >= arrivalTick");
        }
        if (ioEveryTicks < 0) {
            throw new IllegalArgumentException("ioEveryTicks must be >= 0");
        }
        if (ioServiceTicks < 0) {
            throw new IllegalArgumentException("ioServiceTicks must be >= 0");
        }

        this.pid = pid;
        this.name = name;
        this.totalInstructions = totalInstructions;
        this.priority = priority;
        this.arrivalTick = arrivalTick;
        this.deadlineTick = deadlineTick;
        this.ioEveryTicks = ioEveryTicks;
        this.ioServiceTicks = ioServiceTicks;

        this.state = ProcessState.NEW;
        this.programCounter = 0;
        this.mar = 0;
        this.ioTriggerCountdown = ioEveryTicks;
        this.ioRemainingTicks = 0;
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

    public int getIoEveryTicks() { return ioEveryTicks; }
    public int getIoTriggerCountdown() { return ioTriggerCountdown; }
    public int getIoServiceTicks() { return ioServiceTicks; }
    public int getIoRemainingTicks() { return ioRemainingTicks; }

    public long getStartTick() { return startTick; }
    public long getFinishTick() { return finishTick; }
    public long getWaitingTime() { return waitingTime; }
    public boolean isDeadlineMissed() { return deadlineMissed; }

    // --- Lógica de Simulación ---

    public void executeCycle() {
        if (programCounter < totalInstructions) {
            programCounter++;
            mar++;
        }
    }

    public void setIoRemainingTicks(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("ioRemainingTicks must be >= 0");
        }
        this.ioRemainingTicks = value;
    }

    public void decrementIoRemainingTicks() {
        if (ioRemainingTicks > 0) {
            ioRemainingTicks--;
        }
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

    public void markDeadlineMissed() {
        this.deadlineMissed = true;
    }

    public boolean hasFinished() {
        return programCounter >= totalInstructions;
    }

    public boolean shouldTriggerIO() {
        // Expected to be checked AFTER executeCycle() for the current tick.
        if (ioEveryTicks <= 0) return false;
        if (ioTriggerCountdown > 0) {
            ioTriggerCountdown--;
        }
        if (ioTriggerCountdown == 0) {
            ioTriggerCountdown = ioEveryTicks;
            return true;
        }
        return false;
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
