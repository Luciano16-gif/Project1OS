package ve.edu.unimet.so.proyecto1.kernel;
import ve.edu.unimet.so.proyecto1.datastructures.Compare;
import ve.edu.unimet.so.proyecto1.datastructures.LinkedQueue;
import ve.edu.unimet.so.proyecto1.datastructures.OrderedList;
import ve.edu.unimet.so.proyecto1.datastructures.SimpleList;
import ve.edu.unimet.so.proyecto1.models.PCB;
import ve.edu.unimet.so.proyecto1.models.ProcessState;

public class MemoryManager {
    private int maxProcessesInMemory = 6;
    private final OperatingSystem os;

    private final OrderedList<PCB> readySuspended;
    private final OrderedList<PCB> blockedSuspended;

    private final Compare.Comparator<PCB> leastCriticalComparator = (left, right) -> {
      int comparison = Integer.compare(criticalityRank(left), criticalityRank(right));
      if (comparison != 0) return comparison;
      comparison = Long.compare(right.getVirtualDeadlineTick(), left.getVirtualDeadlineTick());
      if (comparison != 0) return comparison;
      comparison = Integer.compare(left.getEffectivePriority(), right.getEffectivePriority());
      if (comparison != 0) return comparison;
      comparison = Integer.compare(right.getRemainingInstructions(), left.getRemainingInstructions());
      if (comparison != 0) return comparison;
      comparison = Long.compare(right.getArrivalTick(), left.getArrivalTick());
      if (comparison != 0) return comparison;
      return Integer.compare(right.getPid(), left.getPid());
    };

    private final Compare.Comparator<PCB> mostCriticalComparator =
      (left, right) -> -leastCriticalComparator.compare(left, right);

    public MemoryManager(OperatingSystem os) {
      if (os == null) throw new IllegalArgumentException("os must not be null");
      this.os = os;
      this.readySuspended = new OrderedList<>(mostCriticalComparator);
      this.blockedSuspended = new OrderedList<>(mostCriticalComparator);
    }

    public void setMaxProcessesInMemory(int value) {
      if (value <= 0) {
        throw new IllegalArgumentException("maxProcessesInMemory must be > 0");
      }
      this.maxProcessesInMemory = value;
    }

    public int getMaxProcessesInMemory() {
      return this.maxProcessesInMemory;
    }

    public void admitFromNew() {
      int rotationsWithoutAdmission = 0;
      while (!os.getNewQueue().isEmpty()) {
        int queueSize = os.getNewQueue().size();
        if (rotationsWithoutAdmission >= queueSize) {
          // Full pass with no possible RAM admission in this tick.
          // Keep NEW processes in NEW (suspended states are for swapped-out residents).
          return;
        }

        PCB incoming = os.getNewQueue().peek();
        if (incoming == null) {
          return;
        }

        if (tryAdmitIncoming(incoming)) {
          os.getNewQueue().dequeue();
          os.enqueueReady(incoming);
          rotationsWithoutAdmission = 0;
        } else {
          os.getNewQueue().enqueue(os.getNewQueue().dequeue());
          rotationsWithoutAdmission++;
        }
      }
    }
    
    public void swapInIfSpace() {
      while (getResidentCount() < maxProcessesInMemory) {
        PCB p = readySuspended.pollFirst();
        if (p != null) {
          os.enqueueReady(p);
          os.logEvent("Proceso " + p.getPid() + " reanudado desde READY_SUSPENDED");
          continue;
        }
        p = blockedSuspended.pollFirst();
        if (p != null) {
          p.setState(ProcessState.BLOCKED);
          os.getBlockedList().add(p);
          os.logEvent("Proceso " + p.getPid() + " reanudado desde BLOCKED_SUSPENDED");
          continue;
        }
        break;
      }
    }

    public void onIoComplete(PCB process) {
      if (process == null) return;
      if (process.getState() == ProcessState.BLOCKED) {
        removeFromBlocked(process);
        os.enqueueReady(process);
        os.logEvent("I/O completada para " + process.getPid());
      } else if (process.getState() == ProcessState.BLOCKED_SUSPENDED) {
        blockedSuspended.removeFirst(process);
        process.setState(ProcessState.READY_SUSPENDED);
        process.markWaitingStateEntryTick(os.getGlobalTickInternal());
        readySuspended.add(process);
        os.logEvent("I/O completada para " + process.getPid() + " (suspendido)");
      }
    }

    private int getResidentCount() {
      int ready = os.isFifoAlgorithmInternal() ? 
        os.getReadyQueueFIFO().size() : 
        os.getReadyListSorted().size();
      int blocked = os.getBlockedList().size();
      return ready + blocked + (os.getCpuInternal() != null ? 1 : 0);
    }

    private PCB selectVictimFromSwapOut() {
      PCB victim = selectVictimFromReady();
      if (victim != null) return victim;
      return selectVictimFromBlocked();

    }

    private PCB selectVictimFromReady() {
      if (!os.isFifoAlgorithmInternal()) {
        return selectVictimFromReadySorted();
      }
      LinkedQueue<PCB> queue = os.getReadyQueueFIFO();
      int size = queue.size();
      PCB victim = null;
      for (int i = 0; i < size; i++) {
        PCB p = queue.dequeue();
        if (victim == null || leastCriticalComparator.compare(p, victim) < 0) {
          victim = p;
        }
        queue.enqueue(p);
      }
      return victim;
    }

    private PCB selectVictimFromReadySorted() {
      PCB victim = null;
      OrderedList<PCB> ready = os.getReadyListSorted();
      for (int i = 0; i < ready.size(); i++) {
        PCB p = ready.get(i);
        if (victim == null || leastCriticalComparator.compare(p, victim) < 0) {
          victim = p;
        }
      }
      return victim;
    }

    private PCB selectVictimFromBlocked() {
      SimpleList<PCB> blocked = os.getBlockedList();
      PCB victim = null;
      for (int i = 0; i < blocked.size(); i++) {
        PCB p = blocked.get(i);
        if (victim == null || leastCriticalComparator.compare(p, victim) < 0) {
          victim = p;
        }
      }
      return victim;
    }

    public void swapOut(PCB victim) {
      if (victim.getState() == ProcessState.READY) {
        removeFromReady(victim);
        victim.setState(ProcessState.READY_SUSPENDED);
        victim.markWaitingStateEntryTick(os.getGlobalTickInternal());
        readySuspended.add(victim);
        os.logEvent("Proceso " + victim.getPid() + " movido a READY_SUSPENDED");
      } else if (victim.getState() == ProcessState.BLOCKED) {
        removeFromBlocked(victim);
        victim.setState(ProcessState.BLOCKED_SUSPENDED);
        blockedSuspended.add(victim);
        os.logEvent("Proceso " + victim.getPid() + " movido a BLOCKED_SUSPENDED");
      }
    }

    private void removeFromReady(PCB target) {
      if (os.isFifoAlgorithmInternal()) {
        LinkedQueue<PCB> queue = os.getReadyQueueFIFO();
        int size = queue.size();
        for (int i = 0; i < size; i++) {
          PCB p = queue.dequeue();
          if (p != target) {
            queue.enqueue(p);
          }
        }
      } else {
        os.getReadyListSorted().removeFirst(target);
      }
    }

    private void removeFromBlocked(PCB target) {
      os.getBlockedList().removeFirst(target);
    }

    PCB[] snapshotBlockedSuspended() {
      Object[] arr = blockedSuspended.toArray();
      PCB[] out = new PCB[arr.length];
      for (int i = 0; i < arr.length; i++) {
        out[i] = (PCB) arr[i];
      }
      return out;
    }

    PCB[] snapshotReadySuspended() {
      Object[] arr = readySuspended.toArray();
      PCB[] out = new PCB[arr.length];
      for (int i = 0; i < arr.length; i++) {
        out[i] = (PCB) arr[i];
      }
      return out;
    }

    void refreshSuspendedOrder(PCB process) {
      if (process == null) {
        return;
      }
      if (process.getState() == ProcessState.READY_SUSPENDED) {
        if (readySuspended.removeFirst(process)) {
          readySuspended.add(process);
        }
      } else if (process.getState() == ProcessState.BLOCKED_SUSPENDED) {
        if (blockedSuspended.removeFirst(process)) {
          blockedSuspended.add(process);
        }
      }
    }

    void tickBlockedSuspendedIo() {
      for (int i = 0; i < blockedSuspended.size(); i++) {
        PCB process = blockedSuspended.get(i);
        if (process == null || process.getState() != ProcessState.BLOCKED_SUSPENDED) {
          continue;
        }
        int remaining = process.getIoRemainingTicks();
        if (remaining <= 0) {
          continue;
        }
        process.decrementIoRemainingTicks();
        if (process.getIoRemainingTicks() == 0) {
          os.publishEvent(new KernelEvent(KernelEvent.Type.IO_COMPLETE, process));
        }
      }
    }

    private boolean shouldPreemptRunningForAdmission(PCB incoming, PCB running) {
      if (incoming == null || running == null) {
        return false;
      }
      int incomingRank = criticalityRank(incoming);
      int runningRank = criticalityRank(running);
      if (incomingRank != runningRank) {
        return incomingRank > runningRank;
      }

      int comparison = Long.compare(incoming.getVirtualDeadlineTick(), running.getVirtualDeadlineTick());
      if (comparison != 0) {
        return comparison < 0;
      }

      comparison = Integer.compare(incoming.getEffectivePriority(), running.getEffectivePriority());
      if (comparison != 0) {
        return comparison > 0;
      }

      comparison = Integer.compare(incoming.getRemainingInstructions(), running.getRemainingInstructions());
      if (comparison != 0) {
        return comparison < 0;
      }

      // Same urgency tier: allow admission to avoid NEW starvation when memory is tight.
      return true;
    }

    private boolean tryAdmitIncoming(PCB incoming) {
      if (incoming == null) {
        return false;
      }

      if (getResidentCount() < maxProcessesInMemory) {
        return true;
      }

      PCB victim = selectVictimFromSwapOut();
      if (victim != null) {
        if (shouldSwapOutForIncoming(incoming, victim)) {
          swapOut(victim);
          return true;
        }
        // If current READY/BLOCKED victim is not swappable, try to free RAM via
        // preemption first; this may expose RUNNING as the least-critical victim.
        return tryPreemptAndSwap(incoming);
      }

      return tryPreemptAndSwap(incoming);
    }

    private boolean tryPreemptAndSwap(PCB incoming) {
      PCB running = os.getCpuInternal();
      if (!shouldPreemptRunningForAdmission(incoming, running)) {
        return false;
      }
      if (!os.isFifoAlgorithmInternal() && !shouldSwapOutForIncoming(incoming, running)) {
        return false;
      }
      if (!os.preemptRunningForAdmission()) {
        return false;
      }
      PCB victim = running;
      if (victim == null || (victim.getState() != ProcessState.READY && victim.getState() != ProcessState.BLOCKED)) {
        victim = selectVictimFromSwapOut();
      }
      if (victim == null) {
        return false;
      }
      if (!shouldSwapOutForIncoming(incoming, victim)) {
        return false;
      }
      swapOut(victim);
      return true;
    }

    private boolean shouldSwapOutForIncoming(PCB incoming, PCB victim) {
      if (incoming == null || victim == null) {
        return false;
      }
      if (os.isFifoAlgorithmInternal()) {
        return true;
      }
      int rankComparison = Integer.compare(criticalityRank(incoming), criticalityRank(victim));
      if (rankComparison != 0) {
        return rankComparison > 0;
      }

      // Non-FIFO policies: use the policy primary metric only.
      return switch (os.getCurrentPolicyInternal()) {
        case SRT -> incoming.getRemainingInstructions() < victim.getRemainingInstructions();
        case PRIORITY -> incoming.getEffectivePriority() > victim.getEffectivePriority();
        case EDF -> incoming.getVirtualDeadlineTick() < victim.getVirtualDeadlineTick();
        default -> false;
      };
    }

    private int criticalityRank(PCB process) {
      if (process == null) {
        return -1;
      }
      if (process.isEmergency()) {
        return 2;
      }
      if (process.isRecoveryBoostApplied()) {
        return 1;
      }
      return 0;
    }

}
