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

    private final Compare.Comparator<PCB> leastCriticalComparator = (a, b) -> {
      int c = Integer.compare(criticalityRank(a), criticalityRank(b));
      if (c != 0) return c;
      c = Long.compare(b.getVirtualDeadlineTick(), a.getVirtualDeadlineTick());
      if (c != 0) return c;
      c = Integer.compare(a.getEffectivePriority(), b.getEffectivePriority());
      if (c != 0) return c;
      c = Integer.compare(b.getRemainingInstructions(), a.getRemainingInstructions());
      if (c != 0) return c;
      c = Long.compare(b.getArrivalTick(), a.getArrivalTick());
      if (c != 0) return c;
      return Integer.compare(b.getPid(), a.getPid());
    };

    private final Compare.Comparator<PCB> mostCriticalComparator =
      (a, b) -> -leastCriticalComparator.compare(a, b);

    public MemoryManager(OperatingSystem os) {
      if (os == null) throw new IllegalArgumentException("os must not be null");
      this.os = os;
      this.readySuspended = new OrderedList<>(mostCriticalComparator);
      this.blockedSuspended = new OrderedList<>(mostCriticalComparator);
    }

    public void setMaxProcessesInMemory(int value) {
      this.maxProcessesInMemory = value;
    }

    public int getMaxProcessesInMemory() {
      return this.maxProcessesInMemory;
    }

    public void admitFromNew() {
      while (!os.getNewQueue().isEmpty()) {
        if (getResidentCount() < maxProcessesInMemory) {
          PCB p = os.getNewQueue().dequeue();
          p.setState(ProcessState.READY);
          os.enqueueReady(p);
        } else {
          PCB victim = selectVictimFromSwapOut();
          if (victim == null) return;
          swapOut(victim);
          PCB p = os.getNewQueue().dequeue();
          p.setState(ProcessState.READY);
          os.enqueueReady(p);
        }
      }
    }
    
    public void swapInIfSpace() {
      while (getResidentCount() < maxProcessesInMemory) {
        PCB p = readySuspended.pollFirst();
        if (p != null) {
          p.setState(ProcessState.READY);
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
        process.setState(ProcessState.READY);
        os.enqueueReady(process);
        os.logEvent("I/O completada para " + process.getPid());
      } else if (process.getState() == ProcessState.BLOCKED_SUSPENDED) {
        blockedSuspended.removeFirst(process);
        process.setState(ProcessState.READY_SUSPENDED);
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
