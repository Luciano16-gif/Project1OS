/*
 * IODeviceThread.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

import java.util.concurrent.Semaphore;

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
        os.onIoDeviceTick();
    }
}
