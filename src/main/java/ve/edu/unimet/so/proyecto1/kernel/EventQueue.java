/*
 * EventQueue.java
 */
package ve.edu.unimet.so.proyecto1.kernel;

import java.util.concurrent.Semaphore;
import ve.edu.unimet.so.proyecto1.datastructures.LinkedQueue;

public class EventQueue {
    private final LinkedQueue<KernelEvent> queue = new LinkedQueue<>();
    private final Semaphore lock = new Semaphore(1);

    public void enqueue(KernelEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        acquire();
        try {
            queue.enqueue(event);
        } finally {
            lock.release();
        }
    }

    public KernelEvent poll() {
        acquire();
        try {
            return queue.dequeue();
        } finally {
            lock.release();
        }
    }

    private void acquire() {
        try {
            lock.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
