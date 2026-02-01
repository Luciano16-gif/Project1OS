/*
 * InterruptGeneratorThread.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

import java.util.Random;

public class InterruptGeneratorThread extends Thread {

    private final OperatingSystem os;
    private final Random rng = new Random();
    private final String[] types = {
        "MICROMETEORITO",
        "RADIACION_SOLAR",
        "COMANDO_TIERRA"
    };
    private final int minSleepMs;
    private final int maxSleepMs;
    private volatile boolean running = true;

    public InterruptGeneratorThread(OperatingSystem os, int minSleepMs, int maxSleepMs) {
        if (os == null) {
            throw new IllegalArgumentException("os must not be null");
        }
        if (minSleepMs < 1 || maxSleepMs < minSleepMs) {
            throw new IllegalArgumentException("invalid sleep range");
        }
        this.os = os;
        this.minSleepMs = minSleepMs;
        this.maxSleepMs = maxSleepMs;
        setName("InterruptGeneratorThread");
        setDaemon(true);
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(nextSleep());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!running) break;
            int cost = 1 + rng.nextInt(5);
            String type = types[rng.nextInt(types.length)];
            long detectedTick = os.getGlobalTick();
            os.publishEvent(new KernelEvent(type, detectedTick, cost));
        }
    }

    private int nextSleep() {
        if (minSleepMs == maxSleepMs) return minSleepMs;
        return minSleepMs + rng.nextInt(maxSleepMs - minSleepMs + 1);
    }
}
