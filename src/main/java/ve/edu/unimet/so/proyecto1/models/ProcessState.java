/*
 * ProcessState.java
 */
package ve.edu.unimet.so.proyecto1.models;

/**
 * Define los estados posibles de un proceso en el simulador RTOS (Microsatélite).
 * 
 * El modelo implementa estrictamente los 7 estados requeridos:
 * - Básicos: NEW, READY, RUNNING, BLOCKED, TERMINATED.
 * - Transición/Suspensión (Memoria Virtual): READY_SUSPENDED, BLOCKED_SUSPENDED.
 * 
 * Estos estados permiten gestionar tanto la ejecución en CPU como el
 * intercambio (swapping) a memoria secundaria cuando la RAM se satura.
 */
public enum ProcessState {
    NEW,
    READY,
    RUNNING,
    BLOCKED,
    TERMINATED,
    READY_SUSPENDED,
    BLOCKED_SUSPENDED
}