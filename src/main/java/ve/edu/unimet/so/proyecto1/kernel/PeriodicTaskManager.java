package ve.edu.unimet.so.proyecto1.kernel;

import ve.edu.unimet.so.proyecto1.datastructures.SimpleList;
import ve.edu.unimet.so.proyecto1.models.PCB;

/**
 * Releases periodic task jobs and forwards them to NEW admission.
 */
final class PeriodicTaskManager {
    private final OperatingSystem os;
    private final SimpleList<PeriodicTaskDefinition> taskDefinitions;

    PeriodicTaskManager(OperatingSystem os) {
        if (os == null) {
            throw new IllegalArgumentException("os must not be null");
        }
        this.os = os;
        this.taskDefinitions = new SimpleList<>();
    }

    void addDefinition(PeriodicTaskDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        taskDefinitions.add(definition);
    }

    void clearDefinitions() {
        taskDefinitions.clear();
    }

    int getDefinitionCount() {
        return taskDefinitions.size();
    }

    void releaseDueTasks(long currentTick) {
        for (int i = 0; i < taskDefinitions.size(); i++) {
            PeriodicTaskDefinition definition = taskDefinitions.get(i);
            while (definition.isDue(currentTick)) {
                long releaseTick = definition.getNextReleaseTick();
                PCB job = definition.createNextJob(os.allocateKernelPid(), releaseTick);
                os.enqueueNewInternal(job);
                os.logEvent("Liberacion periodica: " + definition.getBaseName() + " -> " + job.getName());
                definition.advanceReleaseCursor();
            }
        }
    }
}

