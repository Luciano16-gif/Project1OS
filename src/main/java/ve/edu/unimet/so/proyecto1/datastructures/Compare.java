/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ve.edu.unimet.so.proyecto1.datastructures;

/**
 *
 * @author chano
 */
public class Compare {

    /**
     * Minimal comparator
     * Returns negative if a < b, zero if equal, positive if a > b.
     */
    public interface Comparator<T> {
        int compare(T a, T b);
    }

}
