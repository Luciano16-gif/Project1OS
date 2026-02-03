# Plan de Implementación: Pruebas Realistas de Procesos para GUI

Este documento detalla el plan para crear un archivo de pruebas (`GUIRealisticTest.java`) que conecte la GUI actual con el kernel real del simulador RTOS.

## Contexto del Problema

El archivo `GUITest.java` existente simula datos de manera manual usando `Thread.sleep` y actualizaciones hardcodeadas. Necesitamos:

1. Usar el `ClockThread` como motor único (regla #1 de `GUI_SPECIFICATIONS.md`)
2. Obtener datos reales usando los métodos `snapshot*()` del `OperatingSystem`
3. Crear procesos con el constructor actualizado de `PCB`
4. Demostrar escenarios realistas: I/O, interrupciones, swapping, cambio de algoritmo

## Decisión Pendiente

> **IMPORTANTE:** La `MainWindow` actual usa `DefaultTableModel` que está **prohibido** por la especificación.
>
> **Opciones:**
> 1. Crear primero `GUIRealisticTest.java` con la GUI actual para validar la conexión con el kernel
> 2. Refactorizar `MainWindow` primero para usar `AbstractTableModel`
>
> **Recomendación:** Opción 1 (crear pruebas primero, refactorizar después)

---

## Cambios Propuestos

### 1. Nuevo: `GUIRealisticTest.java`

**Ubicación:** `src/main/java/ve/edu/unimet/so/proyecto1/views/GUIRealisticTest.java`

**Estructura:**
```java
public class GUIRealisticTest {
    private OperatingSystem os;
    private ClockThread clock;
    private MainWindow window;
    
    // - main(): punto de entrada
    // - setupKernel(): inicializa OS con quantum
    // - generateRandomProcesses(int count): crea PCBs realistas
    // - refreshGUI(): lee snapshots y actualiza tablas
}
```

### 2. Nuevo: `ProcessGenerator.java`

**Ubicación:** `src/main/java/ve/edu/unimet/so/proyecto1/utils/ProcessGenerator.java`

**Perfiles de proceso:**

| Tipo | Instrucciones | Prioridad | Deadline | I/O Every | I/O Service |
|------|---------------|-----------|----------|-----------|-------------|
| Telemetry | 10-20 | 50 | +50 ticks | 5 | 2 |
| Camera | 30-50 | 40 | +100 ticks | 10 | 5 |
| Navigation | 20-30 | 60 | +80 ticks | 8 | 3 |
| Emergency | 5-10 | 99 | +20 ticks | 0 | 0 |
| Log/Low-Prio | 15-25 | 10 | +200 ticks | 3 | 4 |

---

## Plan de Verificación

### Compilar y Ejecutar
```bash
cd Project1OS
mvn compile
mvn exec:java -Dexec.mainClass="ve.edu.unimet.so.proyecto1.views.GUIRealisticTest"
```

### Escenarios a Verificar

1. **Flujo básico:** 3 procesos sin I/O terminan en orden FCFS
2. **I/O y bloqueo:** Procesos se mueven a Blocked y regresan a Ready
3. **Presión de memoria:** Procesos van a Ready-Suspended con `maxProcessesInMemory=3`
4. **Interrupciones:** Logs de "Interrupción detectada" aparecen

---

## Referencia Técnica

### Constructor de PCB
```java
new PCB(pid, name, totalInstructions, priority, arrivalTick, deadlineTick, ioEveryTicks, ioServiceTicks);
```

### Métodos de Snapshot
- `os.snapshotNew()`, `snapshotReady()`, `snapshotRunning()`, `snapshotBlocked()`
- `os.snapshotReadySuspended()`, `snapshotBlockedSuspended()`, `snapshotTerminated()`
- `os.snapshotEventLog()` → `String[]`
- `os.pcbToRow(PCB)` → `Object[]`
- `os.isInKernelMode()` → `boolean`

### Controles del Clock
- `clock.startClock()`, `pauseClock()`, `resumeClock()`, `stepOnce()`
- `clock.setCycleDurationMs(int)`
