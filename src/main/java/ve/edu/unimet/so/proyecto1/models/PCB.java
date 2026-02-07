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
    private final int basePriority;
    private int effectivePriority;
    private final long arrivalTick;
    private final long deadlineTick; // Deadline absoluto
    private long virtualDeadlineTick;
    private final boolean emergency;
    private boolean recoveryBoostApplied;

    // --- Entrada/Salida (I/O) ---
    private final int ioEveryTicks;      // 0 = nunca, N = cada N instrucciones ejecutadas
    private int ioTriggerCountdown;      // cuenta hacia 0 mientras corre
    private final int ioServiceTicks;    // duración del bloqueo
    private int ioRemainingTicks;        // ticks restantes mientras está bloqueado

    // --- Métricas ---
    private long startTick = -1;
    private long finishTick = -1;
    private long waitingTime = 0;
    private long waitingStateEntryTick = -1;
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
        this.basePriority = priority;
        this.effectivePriority = priority;
        this.arrivalTick = arrivalTick;
        this.deadlineTick = deadlineTick;
        this.virtualDeadlineTick = deadlineTick;
        this.emergency = priority == 99;
        this.recoveryBoostApplied = false;
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

    public int getPriority() { return effectivePriority; }
    public int getBasePriority() { return basePriority; }
    public int getEffectivePriority() { return effectivePriority; }
    public long getArrivalTick() { return arrivalTick; }
    public long getDeadlineTick() { return deadlineTick; }
    public long getVirtualDeadlineTick() { return virtualDeadlineTick; }
    public boolean isEmergency() { return emergency; }
    public boolean isRecoveryBoostApplied() { return recoveryBoostApplied; }

    public int getIoEveryTicks() { return ioEveryTicks; }
    public int getIoTriggerCountdown() { return ioTriggerCountdown; }
    public int getIoServiceTicks() { return ioServiceTicks; }
    public int getIoRemainingTicks() { return ioRemainingTicks; }

    public long getStartTick() { return startTick; }
    public long getFinishTick() { return finishTick; }
    public long getWaitingTime() { return waitingTime; }
    public long getWaitingStateEntryTick() { return waitingStateEntryTick; }
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

    public void markWaitingStateEntryTick(long tick) {
        this.waitingStateEntryTick = tick;
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

    public void applyDeadlineRecoveryBoost(int maxRecoveryPriority, long virtualDeadlineAdvanceTicks) {
        if (recoveryBoostApplied) {
            return;
        }
        recoveryBoostApplied = true;
        if (!emergency && effectivePriority < maxRecoveryPriority) {
            effectivePriority = maxRecoveryPriority;
        }
        if (virtualDeadlineAdvanceTicks > 0) {
            long advanced = virtualDeadlineTick - virtualDeadlineAdvanceTicks;
            virtualDeadlineTick = Math.max(arrivalTick, advanced);
        }
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
        return String.format("PCB{ID=%d, Name='%s', State=%s, PC=%d/%d, Prio=%d, BasePrio=%d, Rec=%s}",
                pid, name, state, programCounter, totalInstructions, effectivePriority, basePriority,
                recoveryBoostApplied ? "Y" : "N");
    }
}
