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

    private long globalTick;
    private int quantum;
    private SchedulingPolicy currentPolicy;
    
    // Control de ejecución
    private PCB cpu;
    private int cpuQuantumTicks; // Contador de uso de quantum actual
    
    // Estructuras
    private final LinkedQueue<PCB> newQueue; //Por ahora no la usamos mucho, directo a Ready
    private final LinkedQueue<PCB> readyQueueFIFO;
    private OrderedList<PCB> readyListSorted; 
    private final SimpleList<PCB> blockedList;
    private final SimpleList<PCB> terminatedList;

    private final Compare.Comparator<PCB> srtComparator = (p1, p2) -> {
        int c = Integer.compare(p1.getRemainingInstructions(), p2.getRemainingInstructions());
        if (c != 0) return c;
        c = Long.compare(p1.getDeadlineTick(), p2.getDeadlineTick());
        if (c != 0) return c;
        c = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (c != 0) return c;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    private final Compare.Comparator<PCB> priorityComparator = (p1, p2) -> {
        int c = Integer.compare(p2.getPriority(), p1.getPriority());
        if (c != 0) return c;
        c = Long.compare(p1.getDeadlineTick(), p2.getDeadlineTick());
        if (c != 0) return c;
        c = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (c != 0) return c;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    private final Compare.Comparator<PCB> edfComparator = (p1, p2) -> {
        int c = Long.compare(p1.getDeadlineTick(), p2.getDeadlineTick());
        if (c != 0) return c;
        c = Integer.compare(p2.getPriority(), p1.getPriority());
        if (c != 0) return c;
        c = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (c != 0) return c;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    private final Compare.Comparator<PCB> fifoComparator = (p1, p2) -> {
        int c = Long.compare(p1.getArrivalTick(), p2.getArrivalTick());
        if (c != 0) return c;
        return Integer.compare(p1.getPid(), p2.getPid());
    };

    public OperatingSystem(int initialQuantum) {
        this.globalTick = 0;
        this.quantum = initialQuantum;
        this.currentPolicy = SchedulingPolicy.FCFS;
        this.cpu = null;
        this.cpuQuantumTicks = 0;

        this.newQueue = new LinkedQueue<>();
        this.readyQueueFIFO = new LinkedQueue<>();
        this.readyListSorted = new OrderedList<>(srtComparator);
        this.blockedList = new SimpleList<>();
        this.terminatedList = new SimpleList<>();
    }

    // --- Lógica Principal del Ciclo ---
    
    public void executeOneCycle() {
        globalTick++;
        
        // 1. Intentar cargar proceso si CPU está libre
        if (cpu == null) {
            scheduleNextProcess();
        } else if (isPreemptivePolicy()) {
            PCB bestReady = readyListSorted.peekFirst();
            if (bestReady != null && shouldPreempt(bestReady, cpu)) {
                preemptCurrentProcess();
                scheduleNextProcess();
            }
        }

        // 2. Ejecutar instrucción
        if (cpu != null) {
            cpu.executeCycle();
            cpuQuantumTicks++;

            // 3. Verificar terminación
            if (cpu.hasFinished()) {
                terminateProcess(cpu);
                scheduleNextProcess(); // Intentar cargar otro inmediatamente
            } 
            // 4. Verificar Quantum (Solo RR)
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
        if (cpu == null) return;
        
        // Cambio de contexto: Running -> Ready
        cpu.setState(ProcessState.READY);
        addProcess(cpu); // Devuelve a la cola correspondiente
        cpu = null;
        cpuQuantumTicks = 0;
    }

    // --- Gestión de Procesos ---

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
    }

    public void setAlgorithm(SchedulingPolicy newPolicy) {
        if (newPolicy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        if (this.currentPolicy == newPolicy) return;

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

    private Compare.Comparator<PCB> getComparator() {
        return switch (currentPolicy) {
            case PRIORITY -> priorityComparator;
            case EDF -> edfComparator;
            default -> srtComparator;
        };
    }

    private boolean shouldPreempt(PCB candidate, PCB running) {
        return switch (currentPolicy) {
            case SRT -> candidate.getRemainingInstructions() < running.getRemainingInstructions();
            case PRIORITY -> candidate.getPriority() > running.getPriority();
            case EDF -> candidate.getDeadlineTick() < running.getDeadlineTick();
            default -> false;
        };
    }

    // Getters / Setters
    public long getGlobalTick() { return globalTick; }
    public PCB getCpu() { return cpu; }
    public int getQuantum() { return quantum; }
    public void setQuantum(int quantum) { this.quantum = quantum; }
}
