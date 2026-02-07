/*
 * ClockThread.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

import java.util.concurrent.Semaphore;

public class ClockThread extends Thread {

    private static final int DEFAULT_CYCLE_MS = 100;
    private static final int DEFAULT_IRQ_MIN_TICKS = 5;
    private static final int DEFAULT_IRQ_MAX_TICKS = 15;

    private final OperatingSystem os;
    private final Object pauseLock = new Object();
    private final Object tickLock = new Object();

    private volatile boolean running;
    private volatile boolean paused;
    private volatile int cycleDurationMs;
    private volatile boolean started;
    private volatile boolean terminated;

    private final Semaphore ioTickSignal = new Semaphore(0);
    private final Semaphore irqTickSignal = new Semaphore(0);
    private final Semaphore ioTickDone = new Semaphore(0);
    private final Semaphore irqTickDone = new Semaphore(0);

    private IODeviceThread ioDevice;
    private InterruptGeneratorThread interruptGenerator;

    public ClockThread(OperatingSystem os) {
        this(os, DEFAULT_CYCLE_MS);
    }

    public ClockThread(OperatingSystem os, int cycleDurationMs) {
        if (os == null) {
            throw new IllegalArgumentException("os must not be null");
        }
        if (cycleDurationMs <= 0) {
            throw new IllegalArgumentException("cycleDurationMs must be > 0");
        }
        this.os = os;
        this.cycleDurationMs = cycleDurationMs;
        this.terminated = false;
        setName("ClockThread");
    }

    @Override
    public void run() {
        while (running) {
            waitIfPaused();
            if (!running) break;

            long start = System.currentTimeMillis();
            runOneTick();
            sleepRemaining(start);
        }
    }

    public void startClock() {
        if (terminated) {
            throw new IllegalStateException("ClockThread was stopped and cannot be restarted. Create a new instance.");
        }
        running = true;
        paused = false;
        startAuxThreads();
        if (!started) {
            started = true;
            start();
        } else {
            resumeClock();
        }
    }

    public void pauseClock() {
        paused = true;
    }

    public void resumeClock() {
        paused = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    public void stopClock() {
        if (!started) {
            return;
        }
        running = false;
        paused = false;
        if (ioDevice != null) {
            ioDevice.requestStop();
        }
        if (interruptGenerator != null) {
            interruptGenerator.requestStop();
        }
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        waitAuxThreadsStop();
        terminated = true;
    }

    public void stepOnce() {
        if (!paused) return;
        runOneTick();
    }

    public void setCycleDurationMs(int ms) {
        if (ms <= 0) {
            throw new IllegalArgumentException("cycleDurationMs must be > 0");
        }
        this.cycleDurationMs = ms;
    }

    private void waitIfPaused() {
        synchronized (pauseLock) {
            while (paused && running) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void sleepRemaining(long startMs) {
        long elapsed = System.currentTimeMillis() - startMs;
        long remaining = cycleDurationMs - elapsed;
        if (remaining > 0) {
            try {
                Thread.sleep(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void startAuxThreads() {
        if (ioDevice == null || !ioDevice.isAlive()) {
            ioDevice = new IODeviceThread(os, ioTickSignal, ioTickDone);
            ioDevice.start();
        }
        if (interruptGenerator == null || !interruptGenerator.isAlive()) {
            interruptGenerator = new InterruptGeneratorThread(
                    os,
                    DEFAULT_IRQ_MIN_TICKS,
                    DEFAULT_IRQ_MAX_TICKS,
                    irqTickSignal,
                    irqTickDone);
            interruptGenerator.start();
        }
    }

    private void tickDevices() {
        ioTickDone.drainPermits();
        irqTickDone.drainPermits();
        if (ioDevice != null) {
            ioTickSignal.release();
        }
        if (interruptGenerator != null) {
            irqTickSignal.release();
        }
        if (ioDevice != null) {
            waitTickDone(ioTickDone);
        }
        if (interruptGenerator != null) {
            waitTickDone(irqTickDone);
        }
    }

    private void runOneTick() {
        // Prevent concurrent tick handshakes between run loop and manual step.
        synchronized (tickLock) {
            tickDevices();
            os.executeOneCycle();
        }
    }

    private void waitTickDone(Semaphore doneSemaphore) {
        try {
            doneSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitAuxThreadsStop() {
        waitThreadStop(ioDevice);
        waitThreadStop(interruptGenerator);
        ioDevice = null;
        interruptGenerator = null;
    }

    private void waitThreadStop(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
