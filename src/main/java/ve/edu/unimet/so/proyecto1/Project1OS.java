/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package ve.edu.unimet.so.proyecto1;

import ve.edu.unimet.so.proyecto1.datastructures.LinkedQueue;
/**
 *
 * @author chano
 */
public class Project1OS {

    public static void main(String[] args) {
        LinkedQueue<Integer> queue = new LinkedQueue<>();
        queue.enqueue(1);
        queue.enqueue(2);
        queue.enqueue(3);
        System.out.println(queue.peek());
        System.out.println(queue.dequeue());
        System.out.println(queue.dequeue());
        queue.clear();
        System.out.println(queue.isEmpty());
    }
}
