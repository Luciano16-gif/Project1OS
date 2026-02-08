package ve.edu.unimet.so.proyecto1.kernel;

import ve.edu.unimet.so.proyecto1.datastructures.SimpleList;
import ve.edu.unimet.so.proyecto1.models.PCB;

/**
 * Releases periodic task jobs and forwards them to NEW admission.
 */
final class PeriodicTaskManager {
    private static final int MAX_RELEASES_PER_TASK_PER_TICK = 1;
    private static final int MAX_RELEASES_GLOBAL_PER_TICK = 10;
    private static final int MAX_NEW_QUEUE_SIZE_FOR_PERIODIC_RELEASE = 200;

    private final OperatingSystem os;
    private final SimpleList<PeriodicTaskDefinition> taskDefinitions;
    private int nextReleaseStartIndex;

    PeriodicTaskManager(OperatingSystem os) {
        if (os == null) {
            throw new IllegalArgumentException("os must not be null");
        }
        this.os = os;
        this.taskDefinitions = new SimpleList<>();
        this.nextReleaseStartIndex = 0;
    }

    void addDefinition(PeriodicTaskDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        taskDefinitions.add(definition);
    }

    void clearDefinitions() {
        taskDefinitions.clear();
        nextReleaseStartIndex = 0;
    }

    int getDefinitionCount() {
        return taskDefinitions.size();
    }

    void releaseDueTasks(long currentTick) {
        int definitionCount = taskDefinitions.size();
        if (definitionCount == 0) {
            return;
        }
        if (nextReleaseStartIndex >= definitionCount) {
            nextReleaseStartIndex = 0;
        }

        int releasedGlobal = 0;
        boolean queueLimitReached = false;
        boolean globalCapReached = false;
        int visited = 0;
        int index = nextReleaseStartIndex;
        while (visited < definitionCount) {
            if (releasedGlobal >= MAX_RELEASES_GLOBAL_PER_TICK) {
                globalCapReached = true;
                break;
            }
            if (os.getNewQueue().size() >= MAX_NEW_QUEUE_SIZE_FOR_PERIODIC_RELEASE) {
                queueLimitReached = true;
                break;
            }

            PeriodicTaskDefinition definition = taskDefinitions.get(index);
            long dropped = definition.trimBacklogForTick(currentTick, MAX_RELEASES_PER_TASK_PER_TICK);
            if (dropped > 0) {
                os.logEvent("Liberacion periodica acotada: " + definition.getBaseName()
                        + " omitio " + dropped + " instancias atrasadas");
            }

            int releasedForTask = 0;
            while (definition.isDue(currentTick)
                    && releasedForTask < MAX_RELEASES_PER_TASK_PER_TICK
                    && releasedGlobal < MAX_RELEASES_GLOBAL_PER_TICK) {
                if (os.getNewQueue().size() >= MAX_NEW_QUEUE_SIZE_FOR_PERIODIC_RELEASE) {
                    queueLimitReached = true;
                    break;
                }

                long scheduledReleaseTick = definition.getNextReleaseTick();
                // Preserve original release time so EDF/deadline accounting stays correct
                // when jobs are emitted late under overload.
                PCB job = definition.createNextJob(os.allocateKernelPid(), scheduledReleaseTick);
                os.enqueueNewInternal(job);
                os.logEvent("Liberacion periodica: " + definition.getBaseName() + " -> " + job.getName());
                definition.advanceReleaseCursor();
                releasedForTask++;
                releasedGlobal++;
            }

            if (queueLimitReached) {
                break;
            }

            if (releasedGlobal >= MAX_RELEASES_GLOBAL_PER_TICK) {
                globalCapReached = true;
                index = (index + 1) % definitionCount;
                break;
            }

            index = (index + 1) % definitionCount;
            visited++;
        }

        if (globalCapReached) {
            os.logEvent("Liberacion periodica limitada: maximo global por tick alcanzado");
        } else if (queueLimitReached) {
            os.logEvent("Liberacion periodica limitada: NEW en umbral de seguridad");
        }

        // Continue next tick from where the iteration stopped to avoid starvation.
        nextReleaseStartIndex = index;
    }
}
