/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ve.edu.unimet.so.proyecto1.datastructures;

/**
 *
 * @author chano
 */
public class DataStructuresTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    private static final class Box {
        final int id;
        Box(int id) { this.id = id; }
    }

    public static void runAll() {
        testsRun = 0;
        testsPassed = 0;
        testSimpleList();
        testOrderedList();
        System.out.println("OK: " + testsPassed + "/" + testsRun + " tests passed.");
    }

    private static void testSimpleList() {
        SimpleList<Box> list = new SimpleList<>(2);
        expect(list.size() == 0, "SimpleList starts empty");
        expect(list.isEmpty(), "SimpleList isEmpty true");

        Box a = new Box(1);
        Box b = new Box(2);
        Box c = new Box(3);

        list.add(a);
        list.add(b);
        expect(list.size() == 2, "SimpleList size after add");
        expect(list.get(0) == a, "SimpleList get(0) returns a");
        expect(list.get(1) == b, "SimpleList get(1) returns b");

        list.set(1, c);
        expect(list.get(1) == c, "SimpleList set(1) updates to c");

        Box removed = list.removeAt(0);
        expect(removed == a, "SimpleList removeAt returns a");
        expect(list.size() == 1, "SimpleList size after removeAt");
        expect(list.get(0) == c, "SimpleList shifted after removeAt");

        Box d = new Box(4);
        list.add(d);
        expect(list.size() == 2, "SimpleList size after add d");

        boolean removedFirst = list.removeFirst(c);
        expect(removedFirst, "SimpleList removeFirst returns true");
        expect(list.size() == 1, "SimpleList size after removeFirst");
        expect(list.get(0) == d, "SimpleList remaining is d");

        Object[] arr = list.toArray();
        expect(arr.length == 1, "SimpleList toArray length");
        expect(arr[0] == d, "SimpleList toArray element");

        final int[] count = new int[] {0};
        list.forEach(item -> count[0]++);
        expect(count[0] == 1, "SimpleList forEach count");

        list.clear();
        expect(list.isEmpty(), "SimpleList clear");
    }

    private static void testOrderedList() {
        OrderedList<Box> list = new OrderedList<>(
                (a, b) -> Integer.compare(a.id, b.id),
                2
        );

        Box a = new Box(5);
        Box b = new Box(1);
        Box c = new Box(3);
        Box d = new Box(3);

        list.add(a);
        list.add(b);
        list.add(c);
        list.add(d);

        expect(list.size() == 4, "OrderedList size after add");
        expect(list.get(0) == b, "OrderedList sorted position 0");
        expect(list.get(1) == c, "OrderedList sorted position 1");
        expect(list.get(2) == d, "OrderedList stable insertion for equals");
        expect(list.get(3) == a, "OrderedList sorted position 3");

        Box first = list.peekFirst();
        expect(first == b, "OrderedList peekFirst");

        Box polled = list.pollFirst();
        expect(polled == b, "OrderedList pollFirst");
        expect(list.size() == 3, "OrderedList size after pollFirst");
        expect(list.get(0) == c, "OrderedList new first after pollFirst");

        boolean removedFirst = list.removeFirst(a);
        expect(removedFirst, "OrderedList removeFirst returns true");
        expect(list.size() == 2, "OrderedList size after removeFirst");

        Object[] arr = list.toArray();
        expect(arr.length == 2, "OrderedList toArray length");

        final int[] count = new int[] {0};
        list.forEach(item -> count[0]++);
        expect(count[0] == 2, "OrderedList forEach count");

        list.clear();
        expect(list.isEmpty(), "OrderedList clear");
    }

    private static void expect(boolean condition, String message) {
        testsRun++;
        if (!condition) {
            throw new RuntimeException("FAIL: " + message);
        }
        testsPassed++;
    }
}
