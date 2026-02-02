/*
 * ClockThread.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

public class ClockThread extends Thread {

    private static final int DEFAULT_CYCLE_MS = 100;
    private static final int DEFAULT_IRQ_MIN_TICKS = 5;
    private static final int DEFAULT_IRQ_MAX_TICKS = 15;

    private final OperatingSystem os;
    private final Object pauseLock = new Object();

    private volatile boolean running;
    private volatile boolean paused;
    private volatile int cycleDurationMs;
    private volatile boolean started;

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
        setName("ClockThread");
    }

    @Override
    public void run() {
        while (running) {
            waitIfPaused();
            if (!running) break;

            long start = System.currentTimeMillis();
            tickDevices();
            os.executeOneCycle();
            sleepRemaining(start);
        }
    }

    public void startClock() {
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
        running = false;
        paused = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    public void stepOnce() {
        if (!paused) return;
        tickDevices();
        os.executeOneCycle();
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
        if (ioDevice == null) {
            ioDevice = new IODeviceThread(os);
        }
        if (interruptGenerator == null) {
            interruptGenerator = new InterruptGeneratorThread(os, DEFAULT_IRQ_MIN_TICKS, DEFAULT_IRQ_MAX_TICKS);
        }
    }

    private void tickDevices() {
        if (ioDevice != null) {
            ioDevice.tickBlocked();
        }
        if (interruptGenerator != null) {
            interruptGenerator.tickMaybeGenerate(os.getGlobalTick());
        }
    }
}
