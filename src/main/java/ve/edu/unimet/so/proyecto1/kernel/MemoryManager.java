package ve.edu.unimet.so.proyecto1.kernel;
import ve.edu.unimet.so.proyecto1.datastructures.Compare;
import ve.edu.unimet.so.proyecto1.datastructures.OrderedList;
import ve.edu.unimet.so.proyecto1.models.PCB;

public class MemoryManager {
    private int maxProcessesInMemory = 6;
    private final OperatingSystem os;

    private final OrderedList<PCB> readySuspended;
    private final OrderedList<PCB> blockedSuspended;

    private final Compare.Comparator<PCB> leastCriticalComparator = (a, b) -> {
      int c = Long.compare(b.getDeadlineTick(), a.getDeadlineTick());
      if (c != 0) return c;
      c = Integer.compare(b.getPriority(), a.getPriority());
      if (c != 0) return c;
      c = Integer.compare(b.getRemainingInstructions(), a.getRemainingInstructions());
      if (c != 0) return c;
      c = Long.compare(b.getArrivalTick(), a.getArrivalTick());
      if (c != 0) return c;
      return Integer.compare(b.getPid(), a.getPid());
    };

    private final Compare.Comparator<PCB> mostCriticalComparator = (a , b) -> {
      return leastCriticalComparator.compare(a, b);
    };

    public MemoryManager(OperatingSystem os){
      if (os == null) throw new IllegalArgumentException("os must not be null");
      this.os = os;
      this.readySuspended = new OrderedList<>(mostCriticalComparator);
      this.blockedSuspended = new OrderedList<>(mostCriticalComparator);
    };
}
