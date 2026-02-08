/*
 * OperatingSystem.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

import java.util.concurrent.Semaphore;

import ve.edu.unimet.so.proyecto1.datastructures.Compare;
import ve.edu.unimet.so.proyecto1.datastructures.LinkedQueue;
import ve.edu.unimet.so.proyecto1.datastructures.OrderedList;
import ve.edu.unimet.so.proyecto1.datastructures.SimpleList;
import ve.edu.unimet.so.proyecto1.models.PCB;
import ve.edu.unimet.so.proyecto1.models.ProcessState;

public class OperatingSystem {
    private static final int RECOVERY_PRIORITY_MAX = 90;
    private static final long EDF_VIRTUAL_DEADLINE_ADVANCE_TICKS = 10;

    private volatile long globalTick;
    private int quantum;
    private SchedulingPolicy currentPolicy;
    private final MemoryManager memoryManager;
    private final PeriodicTaskManager periodicTaskManager;
    private final EventQueue eventQueue;
    private int isrTicksRemaining;
    private boolean kernelModeForGui;
    private long lastInterruptDetectedTick;
    private int lastIsrCostTicks;
    private String lastInterruptType;
    private final SimpleList<KernelEvent> pendingInterrupts;

    // Control de ejecución
    private PCB cpu;
    private int cpuQuantumTicks; // Contador de uso de quantum actual

    // Estructuras
    private final LinkedQueue<PCB> newQueue;
    private final LinkedQueue<PCB> readyQueueFIFO;
    private OrderedList<PCB> readyListSorted;
    private final SimpleList<PCB> blockedList;
    private final SimpleList<PCB> terminatedList;
    private final SimpleList<String> eventLog;
    private final Semaphore stateLock;
    private int maxLogEntries = 200;
    private long userBusyTicks;
    private long kernelBusyTicks;
    private long idleTicks;
    private long terminatedBeforeDeadlineCount;
    private long totalTerminatedWaitingTicks;
    private int nextKernelPid;

    public static final class GuiSnapshot {
        public final long globalTick;
        public final boolean kernelMode;
        public final Object[] runningRow;
        public final Object[][] newRows;
        public final Object[][] readyRows;
        public final Object[][] blockedRows;
        public final Object[][] terminatedRows;
        public final Object[][] readySuspendedRows;
        public final Object[][] blockedSuspendedRows;
        public final String[] eventLog;
        public final int residentProcessCount;
        public final int maxProcessesInMemory;
        public final double missionSuccessRate;
        public final double throughput;
        public final double averageWaitingTime;
        public final double cpuUtilizationTotal;

        private GuiSnapshot(
                long globalTick,
                boolean kernelMode,
                Object[] runningRow,
                Object[][] newRows,
                Object[][] readyRows,
                Object[][] blockedRows,
                Object[][] terminatedRows,
                Object[][] readySuspendedRows,
                Object[][] blockedSuspendedRows,
                String[] eventLog,
                int residentProcessCount,
                int maxProcessesInMemory,
                double missionSuccessRate,
                double throughput,
                double averageWaitingTime,
                double cpuUtilizationTotal) {
            this.globalTick = globalTick;
            this.kernelMode = kernelMode;
            this.runningRow = runningRow;
            this.newRows = newRows;
            this.readyRows = readyRows;
            this.blockedRows = blockedRows;
            this.terminatedRows = terminatedRows;
            this.readySuspendedRows = readySuspendedRows;
            this.blockedSuspendedRows = blockedSuspendedRows;
            this.eventLog = eventLog;
            this.residentProcessCount = residentProcessCount;
            this.maxProcessesInMemory = maxProcessesInMemory;
            this.missionSuccessRate = missionSuccessRate;
            this.throughput = throughput;
            this.averageWaitingTime = averageWaitingTime;
            this.cpuUtilizationTotal = cpuUtilizationTotal;
        }
    }

    private final Compare.Comparator<PCB> srtComparator = (p1, p2) -> {
        int comparison = Integer.compare(p1.getRemainingInstructions(), p2.getRemainingInstructions());
        if (comparison != 0)
            return comparison;
        comparison = Integer.compare(recoveryRank(p2), recoveryRank(p1));
        if (comparison != 0)
            return comparison;
        comparison = Long.compare(p1.getDeadlineTick(), p2.getDeadlineTick());
        if (comparison != 0)
            return comparison;
        comparison = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (comparison != 0)
            return comparison;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    private final Compare.Comparator<PCB> priorityComparator = (p1, p2) -> {
        int comparison = Integer.compare(p2.getEffectivePriority(), p1.getEffectivePriority());
        if (comparison != 0)
            return comparison;
        comparison = Integer.compare(recoveryRank(p2), recoveryRank(p1));
        if (comparison != 0)
            return comparison;
        comparison = Long.compare(p1.getDeadlineTick(), p2.getDeadlineTick());
        if (comparison != 0)
            return comparison;
        comparison = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (comparison != 0)
            return comparison;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    private final Compare.Comparator<PCB> edfComparator = (p1, p2) -> {
        int comparison = Long.compare(p1.getVirtualDeadlineTick(), p2.getVirtualDeadlineTick());
        if (comparison != 0)
            return comparison;
        comparison = Integer.compare(recoveryRank(p2), recoveryRank(p1));
        if (comparison != 0)
            return comparison;
        comparison = Integer.compare(p2.getEffectivePriority(), p1.getEffectivePriority());
        if (comparison != 0)
            return comparison;
        comparison = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (comparison != 0)
            return comparison;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    private final Compare.Comparator<PCB> fifoComparator = (p1, p2) -> {
        int comparison = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (comparison != 0)
            return comparison;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    public OperatingSystem(int initialQuantum) {
        if (initialQuantum <= 0) {
            throw new IllegalArgumentException("initialQuantum must be > 0");
        }
        this.globalTick = 0;
        this.quantum = initialQuantum;
        this.currentPolicy = SchedulingPolicy.FCFS;
        this.cpu = null;
        this.cpuQuantumTicks = 0;
        this.isrTicksRemaining = 0;
        this.kernelModeForGui = false;
        this.lastInterruptDetectedTick = -1;
        this.lastIsrCostTicks = 0;
        this.lastInterruptType = null;

        this.newQueue = new LinkedQueue<>();
        this.readyQueueFIFO = new LinkedQueue<>();
        this.readyListSorted = new OrderedList<>(srtComparator);
        this.blockedList = new SimpleList<>();
        this.terminatedList = new SimpleList<>();
        this.eventLog = new SimpleList<>();
        this.stateLock = new Semaphore(1, true);
        this.memoryManager = new MemoryManager(this);
        this.periodicTaskManager = new PeriodicTaskManager(this);
        this.eventQueue = new EventQueue();
        this.pendingInterrupts = new SimpleList<>();
        this.userBusyTicks = 0;
        this.kernelBusyTicks = 0;
        this.idleTicks = 0;
        this.terminatedBeforeDeadlineCount = 0;
        this.totalTerminatedWaitingTicks = 0;
        this.nextKernelPid = 1_000_000;
    }

    // --- Lógica Principal del Ciclo ---

    public void executeOneCycle() {
        lockState();
        boolean userExecuted = false;
        boolean kernelExecuted = false;
        boolean contextSwitchThisTick = false;
        try {
            globalTick++;
            periodicTaskManager.releaseDueTasks(globalTick);
            detectDeadlineMisses();

            processEvents();
            // Keep NEW admission moving even while ISR is in progress.
            // Otherwise NEW can starve for many ticks under interrupt bursts.
            memoryManager.admitFromNew();
            if (isrTicksRemaining > 0) {
                isrTicksRemaining--;
                kernelExecuted = true;
                if (isrTicksRemaining == 0) {
                    long latency = (lastInterruptDetectedTick >= 0)
                            ? (globalTick - lastInterruptDetectedTick)
                            : 0;
                    logEvent("ISR terminada; latencia " + latency + " ticks; servicio " + lastIsrCostTicks + " ticks");
                    if (!pendingInterrupts.isEmpty()) {
                        KernelEvent next = pendingInterrupts.removeAt(0);
                        publishEvent(next);
                    }
                }
                return;
            }
            if (cpu == null) {
                contextSwitchThisTick = scheduleNextProcess();
            } else if (isPreemptivePolicy()) {
                PCB bestReady = readyListSorted.peekFirst();
                if (bestReady != null && shouldPreempt(bestReady, cpu)) {
                    preemptCurrentProcess();
                    contextSwitchThisTick = true;
                    scheduleNextProcess();
                }
            }
            if (cpu != null) {
                cpu.executeCycle();
                userExecuted = true;
                cpuQuantumTicks++;

                if (cpu.hasFinished()) {
                    contextSwitchThisTick = true;
                    terminateProcess(cpu);
                    scheduleNextProcess();
                } else if (cpu.shouldTriggerIO()) {
                    contextSwitchThisTick = true;
                    publishEvent(new KernelEvent(KernelEvent.Type.IO_REQUEST, cpu));
                    processEvents();
                    scheduleNextProcess();
                }
                // Verificar Quantum (Solo RR)
                else if (currentPolicy == SchedulingPolicy.RR && cpuQuantumTicks >= quantum) {
                    contextSwitchThisTick = true;
                    preemptCurrentProcess();
                    scheduleNextProcess();
                }
            }
        } finally {
            kernelModeForGui = kernelExecuted || (contextSwitchThisTick && !userExecuted);
            incrementWaitingTimes();
            if (kernelExecuted) {
                kernelBusyTicks++;
            } else if (userExecuted) {
                userBusyTicks++;
            } else {
                idleTicks++;
            }
            unlockState();
        }
    }

    private boolean scheduleNextProcess() {
        PCB next = getNextProcess();
        if (next != null) {
            cpu = next;
            cpu.setState(ProcessState.RUNNING);
            cpu.setStartTick(globalTick);
            cpuQuantumTicks = 0; // Reset quantum
            return true;
        }
        return false;
    }

    private void preemptCurrentProcess() {
        if (cpu == null)
            return;

        // Cambio de contexto: Running -> Ready
        addProcess(cpu); // Devuelve a la cola correspondiente
        cpu = null;
        cpuQuantumTicks = 0;
    }

    // --- Gestión de Procesos ---

    /**
     * Agrega un proceso a la cola NEW (para uso externo/GUI)
     */
    public void submitNewProcess(PCB process) {
        lockState();
        try {
            enqueueNewInternal(process);
        } finally {
            unlockState();
        }
    }

    void enqueueNewInternal(PCB process) {
        if (process == null) {
            return;
        }
        newQueue.enqueue(process);
    }

    private void addProcess(PCB process) {
        ProcessState previousState = process.getState();
        process.setState(ProcessState.READY);
        if (previousState != ProcessState.READY && previousState != ProcessState.READY_SUSPENDED) {
            process.markWaitingStateEntryTick(globalTick);
        }

        if (isFifoAlgorithm()) {
            readyQueueFIFO.enqueue(process);
        } else {
            readyListSorted.add(process);
        }
    }

    private PCB getNextProcess() {
        if (isFifoAlgorithm()) {
            return readyQueueFIFO.dequeue();
        } else {
            return readyListSorted.pollFirst();
        }
    }

    private void terminateProcess(PCB process) {
        process.setState(ProcessState.TERMINATED);
        process.setFinishTick(globalTick);
        terminatedList.add(process);
        totalTerminatedWaitingTicks += process.getWaitingTime();
        if (process.getFinishTick() <= process.getDeadlineTick()) {
            terminatedBeforeDeadlineCount++;
        }
        if (cpu == process) {
            cpu = null;
            cpuQuantumTicks = 0;
        }
        memoryManager.swapInIfSpace();
    }

    public void setAlgorithm(SchedulingPolicy newPolicy) {
        lockState();
        try {
        if (newPolicy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        if (this.currentPolicy == newPolicy)
            return;

        this.currentPolicy = newPolicy;

        SimpleList<PCB> tempBuffer = new SimpleList<>();

        while (!readyQueueFIFO.isEmpty()) {
            tempBuffer.add(readyQueueFIFO.dequeue());
        }

        while (!readyListSorted.isEmpty()) {
            tempBuffer.add(readyListSorted.pollFirst());
        }

        if (newPolicy == SchedulingPolicy.FCFS || newPolicy == SchedulingPolicy.RR) {
            this.readyListSorted = new OrderedList<>(srtComparator);
            OrderedList<PCB> fifoOrdered = new OrderedList<>(fifoComparator);
            tempBuffer.forEach(fifoOrdered::add);
            while (!fifoOrdered.isEmpty()) {
                readyQueueFIFO.enqueue(fifoOrdered.pollFirst());
            }
        } else {
            Compare.Comparator<PCB> targetComparator = switch (newPolicy) {
                case PRIORITY -> priorityComparator;
                case EDF -> edfComparator;
                default -> srtComparator;
            };
            this.readyListSorted = new OrderedList<>(targetComparator);
            tempBuffer.forEach(p -> readyListSorted.add(p));
        }
        } finally {
            unlockState();
        }
    }

    private boolean isFifoAlgorithm() {
        return currentPolicy == SchedulingPolicy.FCFS || currentPolicy == SchedulingPolicy.RR;
    }

    private boolean isPreemptivePolicy() {
        return currentPolicy == SchedulingPolicy.SRT
                || currentPolicy == SchedulingPolicy.PRIORITY
                || currentPolicy == SchedulingPolicy.EDF;
    }

    private boolean shouldPreempt(PCB candidate, PCB running) {
        return switch (currentPolicy) {
            case SRT -> candidate.getRemainingInstructions() < running.getRemainingInstructions();
            case PRIORITY -> candidate.getEffectivePriority() > running.getEffectivePriority();
            case EDF -> candidate.getVirtualDeadlineTick() < running.getVirtualDeadlineTick();
            default -> false;
        };
    }

    // Package-private helpers for MemoryManager
    LinkedQueue<PCB> getNewQueue() {
        return newQueue;
    }

    SimpleList<PCB> getBlockedList() {
        return blockedList;
    }

    LinkedQueue<PCB> getReadyQueueFIFO() {
        return readyQueueFIFO;
    }

    OrderedList<PCB> getReadyListSorted() {
        return readyListSorted;
    }

    PCB getCpuInternal() {
        return cpu;
    }

    long getGlobalTickInternal() {
        return globalTick;
    }

    boolean isFifoAlgorithmInternal() {
        return isFifoAlgorithm();
    }

    void enqueueReady(PCB p) {
        addProcess(p);
    }

    int allocateKernelPid() {
        return nextKernelPid++;
    }

    boolean preemptRunningForAdmission() {
        if (cpu == null || cpu.getState() != ProcessState.RUNNING) {
            return false;
        }
        preemptCurrentProcess();
        return true;
    }

    void publishEvent(KernelEvent event) {
        eventQueue.enqueue(event);
    }

    /**
     * Permite generar una interrupción externa desde la GUI
     */
    public void submitInterrupt(String interruptType, int costTicks) {
        lockState();
        try {
        if (interruptType == null || interruptType.isBlank())
            return;
        if (costTicks <= 0)
            costTicks = 1;
        KernelEvent event = new KernelEvent(interruptType, globalTick, costTicks);
        eventQueue.enqueue(event);
        } finally {
            unlockState();
        }
    }

    void logEvent(String message) {
        if (message == null)
            return;
        if (eventLog.size() >= maxLogEntries) {
            eventLog.removeAt(0);
        }
        eventLog.add(message);
    }

    public String[] snapshotEventLog() {
        lockState();
        try {
            return eventLogToArrayInternal();
        } finally {
            unlockState();
        }
    }

    public void registerPeriodicTask(
            String baseName,
            int totalInstructions,
            int priority,
            int periodTicks,
            int relativeDeadlineTicks,
            int ioEveryTicks,
            int ioServiceTicks,
            long firstReleaseTick) {
        lockState();
        try {
            registerPeriodicTaskInternal(
                    baseName,
                    totalInstructions,
                    priority,
                    periodTicks,
                    relativeDeadlineTicks,
                    ioEveryTicks,
                    ioServiceTicks,
                    firstReleaseTick);
        } finally {
            unlockState();
        }
    }

    public void registerPeriodicTask(
            String baseName,
            int totalInstructions,
            int priority,
            int periodTicks,
            int relativeDeadlineTicks,
            int ioEveryTicks,
            int ioServiceTicks) {
        lockState();
        try {
            registerPeriodicTaskInternal(
                    baseName,
                    totalInstructions,
                    priority,
                    periodTicks,
                    relativeDeadlineTicks,
                    ioEveryTicks,
                    ioServiceTicks,
                    globalTick);
        } finally {
            unlockState();
        }
    }

    public void clearPeriodicTasks() {
        lockState();
        try {
            periodicTaskManager.clearDefinitions();
        } finally {
            unlockState();
        }
    }

    public int getPeriodicTaskCount() {
        lockState();
        try {
            return periodicTaskManager.getDefinitionCount();
        } finally {
            unlockState();
        }
    }

    private void registerPeriodicTaskInternal(
            String baseName,
            int totalInstructions,
            int priority,
            int periodTicks,
            int relativeDeadlineTicks,
            int ioEveryTicks,
            int ioServiceTicks,
            long firstReleaseTick) {
        PeriodicTaskDefinition definition = new PeriodicTaskDefinition(
                baseName,
                totalInstructions,
                priority,
                periodTicks,
                relativeDeadlineTicks,
                ioEveryTicks,
                ioServiceTicks,
                firstReleaseTick);
        periodicTaskManager.addDefinition(definition);
    }

    public GuiSnapshot snapshotForGui() {
        lockState();
        try {
            Object[] runningRow = (cpu == null) ? null : pcbToRowInternal(cpu);
            Object[][] newRows = toRowsInternal(snapshotQueue(newQueue));
            Object[][] readyRows = isFifoAlgorithm()
                    ? toRowsInternal(snapshotQueue(readyQueueFIFO))
                    : toRowsInternal(toPcbArray(readyListSorted.toArray()));
            Object[][] blockedRows = toRowsInternal(toPcbArray(blockedList.toArray()));
            Object[][] terminatedRows = toRowsInternal(toPcbArray(terminatedList.toArray()));
            Object[][] readySuspendedRows = toRowsInternal(memoryManager.snapshotReadySuspended());
            Object[][] blockedSuspendedRows = toRowsInternal(memoryManager.snapshotBlockedSuspended());
            String[] logSnapshot = eventLogToArrayInternal();

            int readyCount = isFifoAlgorithm() ? readyQueueFIFO.size() : readyListSorted.size();
            int blockedCount = blockedList.size();
            int runningCount = (cpu == null) ? 0 : 1;
            int residentCount = readyCount + blockedCount + runningCount;
            int maxMemory = memoryManager.getMaxProcessesInMemory();

            double successRate = (terminatedList.size() == 0)
                    ? 0.0
                    : (double) terminatedBeforeDeadlineCount / terminatedList.size();
            double throughputValue = (globalTick <= 0)
                    ? 0.0
                    : (double) terminatedList.size() / globalTick;
            double avgWaitValue = (terminatedList.size() == 0)
                    ? 0.0
                    : (double) totalTerminatedWaitingTicks / terminatedList.size();
            double cpuUtilValue = (globalTick <= 0)
                    ? 0.0
                    : (double) (userBusyTicks + kernelBusyTicks) / globalTick;

            return new GuiSnapshot(
                    globalTick,
                    kernelModeForGui,
                    runningRow,
                    newRows,
                    readyRows,
                    blockedRows,
                    terminatedRows,
                    readySuspendedRows,
                    blockedSuspendedRows,
                    logSnapshot,
                    residentCount,
                    maxMemory,
                    successRate,
                    throughputValue,
                    avgWaitValue,
                    cpuUtilValue);
        } finally {
            unlockState();
        }
    }

    public Object[][] snapshotNewRows() {
        lockState();
        try {
            return toRowsInternal(snapshotQueue(newQueue));
        } finally {
            unlockState();
        }
    }

    public Object[][] snapshotReadyRows() {
        lockState();
        try {
            if (isFifoAlgorithm()) {
                return toRowsInternal(snapshotQueue(readyQueueFIFO));
            }
            return toRowsInternal(toPcbArray(readyListSorted.toArray()));
        } finally {
            unlockState();
        }
    }

    public Object[][] snapshotBlockedRows() {
        lockState();
        try {
            return toRowsInternal(toPcbArray(blockedList.toArray()));
        } finally {
            unlockState();
        }
    }

    public Object[][] snapshotTerminatedRows() {
        lockState();
        try {
            return toRowsInternal(toPcbArray(terminatedList.toArray()));
        } finally {
            unlockState();
        }
    }

    public Object[][] snapshotReadySuspendedRows() {
        lockState();
        try {
            return toRowsInternal(memoryManager.snapshotReadySuspended());
        } finally {
            unlockState();
        }
    }

    public Object[][] snapshotBlockedSuspendedRows() {
        lockState();
        try {
            return toRowsInternal(memoryManager.snapshotBlockedSuspended());
        } finally {
            unlockState();
        }
    }

    public Object[] snapshotRunningRow() {
        lockState();
        try {
            if (cpu == null) {
                return null;
            }
            return pcbToRowInternal(cpu);
        } finally {
            unlockState();
        }
    }

    public PCB[] snapshotNew() {
        lockState();
        try {
            return snapshotQueue(newQueue);
        } finally {
            unlockState();
        }
    }

    public PCB[] snapshotReady() {
        lockState();
        try {
            if (isFifoAlgorithm()) {
                return snapshotQueue(readyQueueFIFO);
            }
            Object[] arr = readyListSorted.toArray();
            PCB[] out = new PCB[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = (PCB) arr[i];
            }
            return out;
        } finally {
            unlockState();
        }
    }

    public PCB[] snapshotRunning() {
        lockState();
        try {
            if (cpu == null)
                return new PCB[0];
            return new PCB[] { cpu };
        } finally {
            unlockState();
        }
    }

    public PCB[] snapshotBlocked() {
        lockState();
        try {
            Object[] arr = blockedList.toArray();
            PCB[] out = new PCB[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = (PCB) arr[i];
            }
            return out;
        } finally {
            unlockState();
        }
    }

    public PCB[] snapshotBlockedSuspended() {
        lockState();
        try {
            return memoryManager.snapshotBlockedSuspended();
        } finally {
            unlockState();
        }
    }

    public PCB[] snapshotReadySuspended() {
        lockState();
        try {
            return memoryManager.snapshotReadySuspended();
        } finally {
            unlockState();
        }
    }

    public PCB[] snapshotTerminated() {
        lockState();
        try {
            Object[] arr = terminatedList.toArray();
            PCB[] out = new PCB[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = (PCB) arr[i];
            }
            return out;
        } finally {
            unlockState();
        }
    }

    public Object[] pcbToRow(PCB p) {
        lockState();
        try {
            if (p == null)
                return new Object[0];
            return pcbToRowInternal(p);
        } finally {
            unlockState();
        }
    }

    public boolean isInKernelMode() {
        lockState();
        try {
            return kernelModeForGui;
        } finally {
            unlockState();
        }
    }

    private PCB[] snapshotQueue(LinkedQueue<PCB> queue) {
        int size = queue.size();
        PCB[] out = new PCB[size];
        for (int i = 0; i < size; i++) {
            PCB p = queue.dequeue();
            out[i] = p;
            queue.enqueue(p);
        }
        return out;
    }

    private void processEvents() {
        KernelEvent event;
        while ((event = eventQueue.poll()) != null) {
            switch (event.getType()) {
                case IO_REQUEST -> handleIoRequest(event.getPcb());
                case IO_COMPLETE -> memoryManager.onIoComplete(event.getPcb());
                case INTERRUPT -> handleInterrupt(event);
            }
        }
    }

    private void handleInterrupt(KernelEvent event) {
        if (event == null)
            return;
        if (isrTicksRemaining > 0) {
            pendingInterrupts.add(event);
            return;
        }
        lastInterruptType = event.getInterruptType();
        lastInterruptDetectedTick = event.getDetectedTick();
        lastIsrCostTicks = event.getIsrCostTicks();
        isrTicksRemaining = lastIsrCostTicks;
        logEvent("Interrupción detectada: " + lastInterruptType);
    }

    private void handleIoRequest(PCB process) {
        if (process == null)
            return;
        if (process.getState() != ProcessState.RUNNING)
            return;

        int ioServiceTicks = Math.max(1, process.getIoServiceTicks());
        process.setState(ProcessState.BLOCKED);
        process.setIoRemainingTicks(ioServiceTicks);
        blockedList.add(process);
        logEvent("Proceso " + process.getPid() + " bloqueado por I/O (" + ioServiceTicks + " ticks)");

        if (cpu == process) {
            cpu = null;
            cpuQuantumTicks = 0;
        }
    }

    void onIoDeviceTick() {
        lockState();
        try {
            tickIoForList(blockedList, ProcessState.BLOCKED);
            memoryManager.tickBlockedSuspendedIo();
        } finally {
            unlockState();
        }
    }

    // Getters / Setters
    public long getGlobalTick() {
        return globalTick;
    }

    public PCB getCpu() {
        lockState();
        try {
            return cpu;
        } finally {
            unlockState();
        }
    }

    private void detectDeadlineMisses() {
        detectDeadlineMissesInArray(snapshotQueue(newQueue));
        if (isFifoAlgorithm()) {
            detectDeadlineMissesInArray(snapshotQueue(readyQueueFIFO));
        } else {
            detectDeadlineMissesInArray(toPcbArray(readyListSorted.toArray()));
        }
        detectDeadlineMissesInArray(toPcbArray(blockedList.toArray()));
        detectDeadlineMissesInArray(memoryManager.snapshotReadySuspended());
        detectDeadlineMissesInArray(memoryManager.snapshotBlockedSuspended());
        detectDeadlineMiss(cpu);
    }

    private void detectDeadlineMissesInArray(PCB[] processes) {
        if (processes == null) {
            return;
        }
        for (PCB process : processes) {
            detectDeadlineMiss(process);
        }
    }

    private void detectDeadlineMiss(PCB process) {
        if (process == null) {
            return;
        }
        if (process.getState() == ProcessState.TERMINATED) {
            return;
        }
        if (process.isDeadlineMissed()) {
            return;
        }
        if (globalTick > process.getDeadlineTick()) {
            process.markDeadlineMissed();
            logEvent("Fallo de Deadline en Proceso " + process.getPid());
            applyDeadlineRecovery(process);
        }
    }

    private void applyDeadlineRecovery(PCB process) {
        if (process == null || process.isRecoveryBoostApplied()) {
            return;
        }
        if (process.isEmergency()) {
            process.applyDeadlineRecoveryBoost(process.getEffectivePriority(), 0);
            logEvent("Proceso de emergencia " + process.getPid() + " en estado CRITICAL_MISS");
            return;
        }
        process.applyDeadlineRecoveryBoost(RECOVERY_PRIORITY_MAX, EDF_VIRTUAL_DEADLINE_ADVANCE_TICKS);
        logEvent("Proceso " + process.getPid() + " promovido a recuperacion (prio " + process.getEffectivePriority()
                + ", vDeadline " + process.getVirtualDeadlineTick() + ")");

        refreshProcessOrdering(process);
    }

    private void refreshProcessOrdering(PCB process) {
        if (process == null) {
            return;
        }
        if (!isFifoAlgorithm() && process.getState() == ProcessState.READY) {
            readyListSorted.removeFirst(process);
            readyListSorted.add(process);
        }
        memoryManager.refreshSuspendedOrder(process);
    }

    private int recoveryRank(PCB process) {
        if (process == null) {
            return -1;
        }
        if (process.isEmergency()) {
            return 2;
        }
        if (process.isRecoveryBoostApplied()) {
            return 1;
        }
        return 0;
    }

    private PCB[] toPcbArray(Object[] arr) {
        PCB[] out = new PCB[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = (PCB) arr[i];
        }
        return out;
    }

    public int getQuantum() {
        lockState();
        try {
            return quantum;
        } finally {
            unlockState();
        }
    }

    public int getMaxProcessesInMemory() {
        lockState();
        try {
            return memoryManager.getMaxProcessesInMemory();
        } finally {
            unlockState();
        }
    }

    public void setMaxProcessesInMemory(int value) {
        lockState();
        try {
            memoryManager.setMaxProcessesInMemory(value);
            memoryManager.swapInIfSpace();
        } finally {
            unlockState();
        }
    }

    public int getResidentProcessCount() {
        lockState();
        try {
            int ready = isFifoAlgorithm() ? readyQueueFIFO.size() : readyListSorted.size();
            int blocked = blockedList.size();
            int runningCount = (cpu == null) ? 0 : 1;
            return ready + blocked + runningCount;
        } finally {
            unlockState();
        }
    }

    public void setQuantum(int quantum) {
        lockState();
        try {
            if (quantum <= 0) {
                throw new IllegalArgumentException("quantum must be > 0");
            }
            this.quantum = quantum;
        } finally {
            unlockState();
        }
    }

    public long getUserBusyTicks() {
        lockState();
        try {
            return userBusyTicks;
        } finally {
            unlockState();
        }
    }

    public long getKernelBusyTicks() {
        lockState();
        try {
            return kernelBusyTicks;
        } finally {
            unlockState();
        }
    }

    public long getIdleTicks() {
        lockState();
        try {
            return idleTicks;
        } finally {
            unlockState();
        }
    }

    public double getMissionSuccessRate() {
        lockState();
        try {
            if (terminatedList.size() == 0) {
                return 0.0;
            }
            return (double) terminatedBeforeDeadlineCount / terminatedList.size();
        } finally {
            unlockState();
        }
    }

    public double getThroughput() {
        lockState();
        try {
            if (globalTick <= 0) {
                return 0.0;
            }
            return (double) terminatedList.size() / globalTick;
        } finally {
            unlockState();
        }
    }

    public double getAverageWaitingTime() {
        lockState();
        try {
            if (terminatedList.size() == 0) {
                return 0.0;
            }
            return (double) totalTerminatedWaitingTicks / terminatedList.size();
        } finally {
            unlockState();
        }
    }

    public double getCpuUtilizationTotal() {
        lockState();
        try {
            if (globalTick <= 0) {
                return 0.0;
            }
            return (double) (userBusyTicks + kernelBusyTicks) / globalTick;
        } finally {
            unlockState();
        }
    }

    private Object[] pcbToRowInternal(PCB p) {
        long remainingDeadline = p.getDeadlineRemaining(globalTick);
        return new Object[] {
                p.getPid(),
                p.getName(),
                p.getState().name(),
                p.getProgramCounter(),
                p.getMar(),
                p.getPriority(),
                p.getRemainingInstructions(),
                remainingDeadline
        };
    }

    private String[] eventLogToArrayInternal() {
        Object[] arr = eventLog.toArray();
        String[] out = new String[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = (String) arr[i];
        }
        return out;
    }

    private Object[][] toRowsInternal(PCB[] processes) {
        if (processes == null || processes.length == 0) {
            return new Object[0][0];
        }
        Object[][] rows = new Object[processes.length][];
        for (int i = 0; i < processes.length; i++) {
            PCB process = processes[i];
            rows[i] = (process == null) ? new Object[0] : pcbToRowInternal(process);
        }
        return rows;
    }

    private void tickIoForList(SimpleList<PCB> processes, ProcessState expectedState) {
        for (int i = 0; i < processes.size(); i++) {
            PCB process = processes.get(i);
            if (process == null || process.getState() != expectedState) {
                continue;
            }
            int remaining = process.getIoRemainingTicks();
            if (remaining <= 0) {
                continue;
            }
            process.decrementIoRemainingTicks();
            if (process.getIoRemainingTicks() == 0) {
                publishEvent(new KernelEvent(KernelEvent.Type.IO_COMPLETE, process));
            }
        }
    }

    private void incrementWaitingTimes() {
        incrementWaitingTimesForReady();
        incrementWaitingTimesForReadySuspended();
    }

    private void incrementWaitingTimesForReady() {
        if (isFifoAlgorithm()) {
            int size = readyQueueFIFO.size();
            for (int i = 0; i < size; i++) {
                PCB process = readyQueueFIFO.dequeue();
                if (process != null) {
                    if (process.getWaitingStateEntryTick() < globalTick) {
                        process.incrementWaitingTime();
                    }
                    readyQueueFIFO.enqueue(process);
                }
            }
            return;
        }
        for (int i = 0; i < readyListSorted.size(); i++) {
            PCB process = readyListSorted.get(i);
            if (process != null && process.getWaitingStateEntryTick() < globalTick) {
                process.incrementWaitingTime();
            }
        }
    }

    private void incrementWaitingTimesForReadySuspended() {
        PCB[] suspended = memoryManager.snapshotReadySuspended();
        for (PCB process : suspended) {
            if (process != null && process.getWaitingStateEntryTick() < globalTick) {
                process.incrementWaitingTime();
            }
        }
    }

    private void lockState() {
        try {
            stateLock.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private void unlockState() {
        stateLock.release();
    }
}
