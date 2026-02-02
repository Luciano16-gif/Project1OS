/*
 * InterruptGeneratorThread.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

import java.util.Random;

public class InterruptGeneratorThread {

    private final OperatingSystem os;
    private final Random rng = new Random();
    private final String[] types = {
        "MICROMETEORITO",
        "RADIACION_SOLAR",
        "COMANDO_TIERRA"
    };
    private final int minTicks;
    private final int maxTicks;
    private int ticksUntilNext;

    public InterruptGeneratorThread(OperatingSystem os, int minTicks, int maxTicks) {
        if (os == null) {
            throw new IllegalArgumentException("os must not be null");
        }
        if (minTicks < 1 || maxTicks < minTicks) {
            throw new IllegalArgumentException("invalid tick range");
        }
        this.os = os;
        this.minTicks = minTicks;
        this.maxTicks = maxTicks;
        this.ticksUntilNext = nextInterval();
    }

    public void tickMaybeGenerate(long currentTick) {
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
