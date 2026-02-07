/*
 * InterruptGeneratorThread.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

import java.util.Random;
import java.util.concurrent.Semaphore;

public class InterruptGeneratorThread extends Thread {

    private final OperatingSystem os;
    private final Semaphore tickSignal;
    private final Semaphore tickDone;
    private final Random rng = new Random();
    private final String[] types = {
        "MICROMETEORITO",
        "RADIACION_SOLAR",
        "COMANDO_TIERRA"
    };
    private final int minTicks;
    private final int maxTicks;
    private int ticksUntilNext;
    private volatile boolean running;

    public InterruptGeneratorThread(OperatingSystem os, int minTicks, int maxTicks, Semaphore tickSignal, Semaphore tickDone) {
        if (os == null) {
            throw new IllegalArgumentException("os must not be null");
        }
        if (minTicks < 1 || maxTicks < minTicks) {
            throw new IllegalArgumentException("invalid tick range");
        }
        if (tickSignal == null || tickDone == null) {
            throw new IllegalArgumentException("tick semaphores must not be null");
        }
        this.os = os;
        this.minTicks = minTicks;
        this.maxTicks = maxTicks;
        this.tickSignal = tickSignal;
        this.tickDone = tickDone;
        this.ticksUntilNext = nextInterval();
        this.running = true;
        setName("InterruptGeneratorThread");
    }

    @Override
    public void run() {
        while (running) {
            waitForTick();
            if (!running) {
                tickDone.release();
                break;
            }
            tickMaybeGenerate(os.getGlobalTick());
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

    private void tickMaybeGenerate(long currentTick) {
        if (ticksUntilNext > 0) {
            ticksUntilNext--;
        }
        if (ticksUntilNext > 0) return;
        int cost = 1 + rng.nextInt(5);
        String type = types[rng.nextInt(types.length)];
        os.publishEvent(new KernelEvent(type, currentTick, cost));
        ticksUntilNext = nextInterval();
    }

    private int nextInterval() {
        if (minTicks == maxTicks) return minTicks;
        return minTicks + rng.nextInt(maxTicks - minTicks + 1);
    }
}
