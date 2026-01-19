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

    public static final int ALG_FCFS = 0;
    public static final int ALG_RR = 1;
    public static final int ALG_SRT = 2;
    public static final int ALG_PRIORITY = 3;
    public static final int ALG_EDF = 4;

    private int globalTick;
    private int quantum;
    private int currentAlgorithm;
    
    // Control de ejecución
    private PCB cpu;
    private int cpuQuantumTicks; // Contador de uso de quantum actual
    
    // Estructuras
    private final LinkedQueue<PCB> newQueue; // Por ahora no la usamos mucho, directo a Ready
    private final LinkedQueue<PCB> readyQueueFIFO;
    private OrderedList<PCB> readyListSorted; 
    private final SimpleList<PCB> blockedList;
    private final SimpleList<PCB> terminatedList;

    private final Compare.Comparator<PCB> srtComparator = (p1, p2) -> 
            Integer.compare(p1.getRemainingInstructions(), p2.getRemainingInstructions());

    private final Compare.Comparator<PCB> priorityComparator = (p1, p2) -> 
            Integer.compare(p2.getPriority(), p1.getPriority());

    private final Compare.Comparator<PCB> edfComparator = (p1, p2) -> 
            Long.compare(p1.getDeadlineTick(), p2.getDeadlineTick());

    public OperatingSystem(int initialQuantum) {
        this.globalTick = 0;
        this.quantum = initialQuantum;
        this.currentAlgorithm = ALG_FCFS;
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
            else if (currentAlgorithm == ALG_RR && cpuQuantumTicks >= quantum) {
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

    public void setAlgorithm(int newAlgorithm) {
        if (this.currentAlgorithm == newAlgorithm) return;

        int oldAlg = this.currentAlgorithm;
        this.currentAlgorithm = newAlgorithm;
        
        SimpleList<PCB> tempBuffer = new SimpleList<>();
        
        while (!readyQueueFIFO.isEmpty()) {
            tempBuffer.add(readyQueueFIFO.dequeue());
        }
        
        while (!readyListSorted.isEmpty()) {
            tempBuffer.add(readyListSorted.pollFirst());
        }

        Compare.Comparator<PCB> targetComparator = switch (newAlgorithm) {
            case ALG_PRIORITY -> priorityComparator;
            case ALG_EDF -> edfComparator;
            default -> srtComparator;
        };
        
        this.readyListSorted = new OrderedList<>(targetComparator);
        tempBuffer.forEach(p -> addProcess(p));
    }

    private boolean isFifoAlgorithm() {
        return currentAlgorithm == ALG_FCFS || currentAlgorithm == ALG_RR;
    }

    // Getters / Setters
    public int getGlobalTick() { return globalTick; }
    public PCB getCpu() { return cpu; }
    public int getQuantum() { return quantum; }
    public void setQuantum(int quantum) { this.quantum = quantum; }
}