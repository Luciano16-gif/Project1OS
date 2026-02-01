/*
 * IODeviceThread.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

import ve.edu.unimet.so.proyecto1.models.PCB;
import ve.edu.unimet.so.proyecto1.models.ProcessState;

public class IODeviceThread extends Thread {

    private final OperatingSystem os;
    private volatile boolean running = true;

    public IODeviceThread(OperatingSystem os) {
        if (os == null) {
            throw new IllegalArgumentException("os must not be null");
        }
        this.os = os;
        setName("IODeviceThread");
        setDaemon(true);
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        while (running) {
            tickBlocked();
            try {
                // TODO: sync with ClockThread tick duration instead of fixed sleep.
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
