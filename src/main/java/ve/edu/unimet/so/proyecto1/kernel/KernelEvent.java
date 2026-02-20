/*
 * KernelEvent.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

import ve.edu.unimet.so.proyecto1.models.PCB;

public class KernelEvent {

    public enum Type {
        IO_REQUEST,
        IO_COMPLETE,
        INTERRUPT
    }

    private final Type type;
    private final PCB pcb;
    private final String interruptType;
    private final long detectedTick;
    private final int isrCostTicks;

    public KernelEvent(Type type, PCB pcb) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (type == Type.INTERRUPT) {
            throw new IllegalArgumentException("use interrupt constructor for INTERRUPT events");
        }
        if (pcb == null) {
            throw new IllegalArgumentException("pcb must not be null");
        }
        this.type = type;
        this.pcb = pcb;
        this.interruptType = null;
        this.detectedTick = -1;
        this.isrCostTicks = 0;
    }

    public KernelEvent(String interruptType, long detectedTick, int isrCostTicks) {
        if (interruptType == null || interruptType.isBlank()) {
            throw new IllegalArgumentException("interruptType must not be null/blank");
        }
        if (isrCostTicks <= 0) {
            throw new IllegalArgumentException("isrCostTicks must be > 0");
        }
        this.type = Type.INTERRUPT;
        this.pcb = null;
        this.interruptType = interruptType;
        this.detectedTick = detectedTick;
        this.isrCostTicks = isrCostTicks;
    }

    public Type getType() { return type; }
    public PCB getPcb() { return pcb; }
    public String getInterruptType() { return interruptType; }
    public long getDetectedTick() { return detectedTick; }
    public int getIsrCostTicks() { return isrCostTicks; }
}
