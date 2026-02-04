/*
 * OperatingSystem.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

import ve.edu.unimet.so.proyecto1.datastructures.Compare;
import ve.edu.unimet.so.proyecto1.datastructures.LinkedQueue;
import ve.edu.unimet.so.proyecto1.datastructures.OrderedList;
import ve.edu.unimet.so.proyecto1.datastructures.SimpleList;
import ve.edu.unimet.so.proyecto1.models.PCB;
import ve.edu.unimet.so.proyecto1.models.ProcessState;

public class OperatingSystem {

    private volatile long globalTick;
    private int quantum;
    private SchedulingPolicy currentPolicy;
    private final MemoryManager memoryManager;
    private final EventQueue eventQueue;
    private int isrTicksRemaining;
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
    private int maxLogEntries = 200;

    private final Compare.Comparator<PCB> srtComparator = (p1, p2) -> {
        int c = Integer.compare(p1.getRemainingInstructions(), p2.getRemainingInstructions());
        if (c != 0)
            return c;
        c = Long.compare(p1.getDeadlineTick(), p2.getDeadlineTick());
        if (c != 0)
            return c;
        c = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (c != 0)
            return c;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    private final Compare.Comparator<PCB> priorityComparator = (p1, p2) -> {
        int c = Integer.compare(p2.getPriority(), p1.getPriority());
        if (c != 0)
            return c;
        c = Long.compare(p1.getDeadlineTick(), p2.getDeadlineTick());
        if (c != 0)
            return c;
        c = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (c != 0)
            return c;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    private final Compare.Comparator<PCB> edfComparator = (p1, p2) -> {
        int c = Long.compare(p1.getDeadlineTick(), p2.getDeadlineTick());
        if (c != 0)
            return c;
        c = Integer.compare(p2.getPriority(), p1.getPriority());
        if (c != 0)
            return c;
        c = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (c != 0)
            return c;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    private final Compare.Comparator<PCB> fifoComparator = (p1, p2) -> {
        int c = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (c != 0)
            return c;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    public OperatingSystem(int initialQuantum) {
        this.globalTick = 0;
        this.quantum = initialQuantum;
        this.currentPolicy = SchedulingPolicy.FCFS;
        this.cpu = null;
        this.cpuQuantumTicks = 0;
        this.isrTicksRemaining = 0;
        this.lastInterruptDetectedTick = -1;
        this.lastIsrCostTicks = 0;
        this.lastInterruptType = null;

        this.newQueue = new LinkedQueue<>();
        this.readyQueueFIFO = new LinkedQueue<>();
        this.readyListSorted = new OrderedList<>(srtComparator);
        this.blockedList = new SimpleList<>();
        this.terminatedList = new SimpleList<>();
        this.eventLog = new SimpleList<>();
        this.memoryManager = new MemoryManager(this);
        this.eventQueue = new EventQueue();
        this.pendingInterrupts = new SimpleList<>();
    }

    // --- Lógica Principal del Ciclo ---

    public void executeOneCycle() {
        globalTick++;

        processEvents();
        if (isrTicksRemaining > 0) {
            isrTicksRemaining--;
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
        memoryManager.admitFromNew();
        if (cpu == null) {
            scheduleNextProcess();
        } else if (isPreemptivePolicy()) {
            PCB bestReady = readyListSorted.peekFirst();
            if (bestReady != null && shouldPreempt(bestReady, cpu)) {
                preemptCurrentProcess();
                scheduleNextProcess();
            }
        }
        if (cpu != null) {
            cpu.executeCycle();
            cpuQuantumTicks++;

            if (cpu.hasFinished()) {
                terminateProcess(cpu);
                scheduleNextProcess();
            } else if (cpu.shouldTriggerIO()) {
                publishEvent(new KernelEvent(KernelEvent.Type.IO_REQUEST, cpu));
                processEvents();
                scheduleNextProcess();
            }
            // Verificar Quantum (Solo RR)
            else if (currentPolicy == SchedulingPolicy.RR && cpuQuantumTicks >= quantum) {
                preemptCurrentProcess();
                scheduleNextProcess();
            }
        }
    }

    private void scheduleNextProcess() {
        PCB next = getNextProcess();
        if (next != null) {
            cpu = next;
            cpu.setState(ProcessState.RUNNING);
            cpu.setStartTick(globalTick);
            cpuQuantumTicks = 0; // Reset quantum
        }
    }

    private void preemptCurrentProcess() {
        if (cpu == null)
            return;

        // Cambio de contexto: Running -> Ready
        cpu.setState(ProcessState.READY);
        addProcess(cpu); // Devuelve a la cola correspondiente
        cpu = null;
        cpuQuantumTicks = 0;
    }

    // --- Gestión de Procesos ---

    /**
     * Agrega un proceso a la cola NEW (para uso externo/GUI)
     */
    public void submitNewProcess(PCB process) {
        if (process == null)
            return;
        newQueue.enqueue(process);
    }

    public void addProcess(PCB process) {
        process.setState(ProcessState.READY);

        if (isFifoAlgorithm()) {
            readyQueueFIFO.enqueue(process);
        } else {
            readyListSorted.add(process);
        }
    }

    public PCB getNextProcess() {
        if (isFifoAlgorithm()) {
            return readyQueueFIFO.dequeue();
        } else {
            return readyListSorted.pollFirst();
        }
    }

    public void terminateProcess(PCB process) {
        process.setState(ProcessState.TERMINATED);
        process.setFinishTick(globalTick);
        terminatedList.add(process);
        if (cpu == process) {
            cpu = null;
            cpuQuantumTicks = 0;
        }
        memoryManager.swapInIfSpace();
    }

    public void setAlgorithm(SchedulingPolicy newPolicy) {
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
            tempBuffer.forEach(p -> addProcess(p));
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
            case PRIORITY -> candidate.getPriority() > running.getPriority();
            case EDF -> candidate.getDeadlineTick() < running.getDeadlineTick();
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

    boolean isFifoAlgorithmInternal() {
        return isFifoAlgorithm();
    }

    void enqueueReady(PCB p) {
        addProcess(p);
    }

    void publishEvent(KernelEvent event) {
        eventQueue.enqueue(event);
    }

    /**
     * Permite generar una interrupción externa desde la GUI
     */
    public void submitInterrupt(String interruptType, int costTicks) {
        if (interruptType == null || interruptType.isBlank())
            return;
        if (costTicks <= 0)
            costTicks = 1;
        KernelEvent event = new KernelEvent(interruptType, globalTick, costTicks);
        eventQueue.enqueue(event);
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
        // TODO: expose to GUI log panel when UI wiring is ready.
        Object[] arr = eventLog.toArray();
        String[] out = new String[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = (String) arr[i];
        }
        return out;
    }

    public PCB[] snapshotNew() {
        return snapshotQueue(newQueue);
    }

    public PCB[] snapshotReady() {
        if (isFifoAlgorithm()) {
            return snapshotQueue(readyQueueFIFO);
        }
        Object[] arr = readyListSorted.toArray();
        PCB[] out = new PCB[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = (PCB) arr[i];
        }
        return out;
    }

    public PCB[] snapshotRunning() {
        if (cpu == null)
            return new PCB[0];
        return new PCB[] { cpu };
    }

    public PCB[] snapshotBlocked() {
        Object[] arr = blockedList.toArray();
        PCB[] out = new PCB[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = (PCB) arr[i];
        }
        return out;
    }

    public PCB[] snapshotBlockedSuspended() {
        return memoryManager.snapshotBlockedSuspended();
    }

    public PCB[] snapshotReadySuspended() {
        return memoryManager.snapshotReadySuspended();
    }

    public PCB[] snapshotTerminated() {
        Object[] arr = terminatedList.toArray();
        PCB[] out = new PCB[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = (PCB) arr[i];
        }
        return out;
    }

    public Object[] pcbToRow(PCB p) {
        if (p == null)
            return new Object[0];
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

    public boolean isInKernelMode() {
        return isrTicksRemaining > 0;
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

        process.setState(ProcessState.BLOCKED);
        process.setIoRemainingTicks(process.getIoServiceTicks());
        blockedList.add(process);
        logEvent("Proceso " + process.getPid() + " bloqueado por I/O (" + process.getIoServiceTicks() + " ticks)");

        if (cpu == process) {
            cpu = null;
            cpuQuantumTicks = 0;
        }
    }

    // Getters / Setters
    public long getGlobalTick() {
        return globalTick;
    }

    public PCB getCpu() {
        return cpu;
    }

    public int getQuantum() {
        return quantum;
    }

    public void setQuantum(int quantum) {
        this.quantum = quantum;
    }
}
