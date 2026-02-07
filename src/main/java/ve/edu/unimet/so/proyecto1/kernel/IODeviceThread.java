/*
 * IODeviceThread.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

import java.util.concurrent.Semaphore;
import ve.edu.unimet.so.proyecto1.models.PCB;
import ve.edu.unimet.so.proyecto1.models.ProcessState;

public class IODeviceThread extends Thread {

    private final OperatingSystem os;
    private final Semaphore tickSignal;
    private final Semaphore tickDone;
    private volatile boolean running;

    public IODeviceThread(OperatingSystem os, Semaphore tickSignal, Semaphore tickDone) {
        if (os == null) {
            throw new IllegalArgumentException("os must not be null");
        }
        if (tickSignal == null || tickDone == null) {
            throw new IllegalArgumentException("tick semaphores must not be null");
        }
        this.os = os;
        this.tickSignal = tickSignal;
        this.tickDone = tickDone;
        this.running = true;
        setName("IODeviceThread");
    }

    @Override
    public void run() {
        while (running) {
            waitForTick();
            if (!running) {
                tickDone.release();
                break;
            }
            tickBlocked();
            tickDone.release();
        }
    }

    public void requestStop() {
        running = false;
        tickSignal.release();
    }

    private void waitForTick() {
        try {
            tickSignal.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private void tickBlocked() {
        PCB[] blocked = os.snapshotBlocked();
        for (PCB p : blocked) {
            if (p == null) continue;
            if (p.getState() != ProcessState.BLOCKED) continue;
            int remaining = p.getIoRemainingTicks();
            if (remaining <= 0) continue;
            p.decrementIoRemainingTicks();
            if (p.getIoRemainingTicks() == 0) {
                os.publishEvent(new KernelEvent(KernelEvent.Type.IO_COMPLETE, p));
            }
        }
        PCB[] blockedSusp = os.snapshotBlockedSuspended();
        for (PCB p : blockedSusp) {
            if (p == null) continue;
            if (p.getState() != ProcessState.BLOCKED_SUSPENDED) continue;
            int remaining = p.getIoRemainingTicks();
            if (remaining <= 0) continue;
            p.decrementIoRemainingTicks();
            if (p.getIoRemainingTicks() == 0) {
                os.publishEvent(new KernelEvent(KernelEvent.Type.IO_COMPLETE, p));
            }
        }
    }
}
