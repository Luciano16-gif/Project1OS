package ve.edu.unimet.so.proyecto1.kernel;

import ve.edu.unimet.so.proyecto1.models.PCB;

/**
 * Immutable periodic task template plus release cursor state.
 *
 * Each definition generates jobs at fixed intervals:
 * releaseTick = firstReleaseTick + k * periodTicks
 */
final class PeriodicTaskDefinition {
    private final String baseName;
    private final int totalInstructions;
    private final int priority;
    private final int periodTicks;
    private final int relativeDeadlineTicks;
    private final int ioEveryTicks;
    private final int ioServiceTicks;

    private long nextReleaseTick;
    private int releasedJobs;

    PeriodicTaskDefinition(
            String baseName,
            int totalInstructions,
            int priority,
            int periodTicks,
            int relativeDeadlineTicks,
            int ioEveryTicks,
            int ioServiceTicks,
            long firstReleaseTick) {
        if (baseName == null || baseName.isBlank()) {
            throw new IllegalArgumentException("baseName must not be null/blank");
        }
        if (totalInstructions <= 0) {
            throw new IllegalArgumentException("totalInstructions must be > 0");
        }
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks must be > 0");
        }
        if (relativeDeadlineTicks <= 0) {
            throw new IllegalArgumentException("relativeDeadlineTicks must be > 0");
        }
        if (ioEveryTicks < 0) {
            throw new IllegalArgumentException("ioEveryTicks must be >= 0");
        }
        if (ioServiceTicks < 0) {
            throw new IllegalArgumentException("ioServiceTicks must be >= 0");
        }
        if (ioEveryTicks > 0 && ioServiceTicks <= 0) {
            throw new IllegalArgumentException("ioServiceTicks must be > 0 when ioEveryTicks > 0");
        }
        if (firstReleaseTick < 0) {
            throw new IllegalArgumentException("firstReleaseTick must be >= 0");
        }
        this.baseName = baseName;
        this.totalInstructions = totalInstructions;
        this.priority = priority;
        this.periodTicks = periodTicks;
        this.relativeDeadlineTicks = relativeDeadlineTicks;
        this.ioEveryTicks = ioEveryTicks;
        this.ioServiceTicks = ioServiceTicks;
        this.nextReleaseTick = firstReleaseTick;
        this.releasedJobs = 0;
    }

    String getBaseName() {
        return baseName;
    }

    long getNextReleaseTick() {
        return nextReleaseTick;
    }

    boolean isDue(long currentTick) {
        return currentTick >= nextReleaseTick;
    }

    long trimBacklogForTick(long currentTick, int maxDueReleasesToKeep) {
        if (maxDueReleasesToKeep <= 0) {
            throw new IllegalArgumentException("maxDueReleasesToKeep must be > 0");
        }
        if (!isDue(currentTick)) {
            return 0;
        }
        long dueCount = ((currentTick - nextReleaseTick) / periodTicks) + 1;
        if (dueCount <= maxDueReleasesToKeep) {
            return 0;
        }
        long dropped = dueCount - maxDueReleasesToKeep;
        nextReleaseTick += dropped * (long) periodTicks;
        return dropped;
    }

    PCB createNextJob(int pid, long releaseTick) {
        releasedJobs++;
        String jobName = baseName + "#" + releasedJobs;
        long absoluteDeadline = releaseTick + relativeDeadlineTicks;
        return new PCB(
                pid,
                jobName,
                totalInstructions,
                priority,
                releaseTick,
                absoluteDeadline,
                ioEveryTicks,
                ioServiceTicks);
    }

    void advanceReleaseCursor() {
        nextReleaseTick += periodTicks;
    }
}
