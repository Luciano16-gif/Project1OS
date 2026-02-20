# Informe Técnico — Simulador RTOS para Microsatélite

**Proyecto 1 — Sistemas Operativos (Trimestre 8)**  
**Universidad Metropolitana — Profesor Zabala**

---

## 1. Descripción de Métodos Más Importantes

### 1.1 `OperatingSystem.executeOneCycle()`

Método central del simulador. Se ejecuta una vez por tick del reloj y orquesta todas las operaciones del kernel:

```
executeOneCycle():
    1. lockState()                        ← Adquiere semáforo
    2. globalTick++
    3. periodicTaskManager.releaseDueTasks()  ← Libera tareas periódicas
    4. detectDeadlineMisses()             ← Marca procesos con deadline vencido
    5. processEvents()                    ← Procesa cola de eventos (IRQ, I/O)
    6. memoryManager.admitFromNew()       ← Admite procesos de NEW a READY
    7. Si ISR activa → decrementar isrTicksRemaining, retornar
    8. Si CPU vacía → scheduleNextProcess()
    9. Si política preemptiva → verificar si el mejor READY desplaza al RUNNING
   10. Si CPU ocupada → executeCycle() del proceso
       - Si terminó → terminateProcess() + scheduleNextProcess()
       - Si I/O → handleIoRequest() + scheduleNextProcess()
       - Si quantum expirado (RR) → preemptCurrentProcess()
   11. Actualizar contadores: userBusyTicks, kernelBusyTicks, idleTicks
   12. incrementWaitingTimes()            ← Incrementa waitingTime de procesos en READY
   13. unlockState()                      ← Libera semáforo
```

**Decisión clave:** La admisión desde NEW ocurre incluso durante ISRs para evitar inanición bajo ráfagas de interrupciones.

### 1.2 `OperatingSystem.scheduleNextProcess()`

Selecciona el siguiente proceso a ejecutar según la política activa:

- **FCFS / RR:** Desencola de `readyQueueFIFO` (FIFO puro).
- **SRT / PRIORITY / EDF:** Extrae el primer elemento de `readyListSorted` (lista ordenada por comparador).

Al seleccionar, establece el estado a `RUNNING`, registra `startTick` y resetea `cpuQuantumTicks`.

### 1.3 `OperatingSystem.setAlgorithm()`

Permite cambiar la política de planificación en tiempo de ejecución:

1. Extrae todos los procesos de ambas estructuras de READY (`readyQueueFIFO` y `readyListSorted`).
2. Si la nueva política es FIFO (FCFS/RR): reinserta en `readyQueueFIFO` ordenados por llegada.
3. Si es ordenada (SRT/PRIORITY/EDF): crea nueva `OrderedList` con el comparador correspondiente y reinserta.

### 1.4 `OperatingSystem.shouldPreempt()`

Determina si un proceso candidato de READY debe expulsar al proceso en ejecución:

| Política | Criterio de Preemption |
|----------|----------------------|
| **SRT** | `candidato.remainingInstructions < running.remainingInstructions` |
| **PRIORITY** | `candidato.effectivePriority > running.effectivePriority` |
| **EDF** | `candidato.virtualDeadlineTick < running.virtualDeadlineTick` |
| **FCFS / RR** | Nunca (no preemptivo por este mecanismo) |

### 1.5 `OperatingSystem.handleInterrupt()`

Procesa una interrupción externa:

1. Si ya hay una ISR en curso → la nueva interrupción se encola en `pendingInterrupts`.
2. Si no → registra tipo, tick de detección, y asigna `isrTicksRemaining` (costo en ticks).
3. Durante la ISR, el CPU no ejecuta procesos de usuario (modo KERNEL).

### 1.6 `OperatingSystem.handleIoRequest()`

Cuando un proceso solicita I/O:

1. Cambia su estado de `RUNNING` a `BLOCKED`.
2. Establece `ioRemainingTicks` según la duración del servicio.
3. Lo agrega a `blockedList`.
4. Libera la CPU (`cpu = null`).

### 1.7 `OperatingSystem.terminateProcess()`

Al finalizar un proceso:

1. Establece estado `TERMINATED` y registra `finishTick`.
2. Acumula `waitingTime` en `totalTerminatedWaitingTicks`.
3. Si terminó antes del deadline → incrementa `terminatedBeforeDeadlineCount`.
4. Libera la CPU y llama a `memoryManager.swapInIfSpace()` para admitir procesos suspendidos.

### 1.8 `MemoryManager.admitFromNew()`

Controla la admisión de procesos desde la cola NEW:

1. Si hay espacio en memoria → admite directamente (`NEW → READY`).
2. Si la memoria está llena → ejecuta `tryPreemptAndSwap()` para intercambiar con un proceso de menor criticidad.
3. Evalúa si debe expulsar al proceso RUNNING actual si el entrante tiene mayor prioridad.

### 1.9 `MemoryManager.swapOut()`

Expulsa un proceso de memoria a disco:

- Si estaba en `READY` → pasa a `READY_SUSPENDED`.
- Si estaba en `BLOCKED` → pasa a `BLOCKED_SUSPENDED`.
- Selecciona víctima por menor prioridad y mayor tiempo restante de I/O.

### 1.10 `PCB.executeCycle()`

Simula la ejecución de una instrucción:

```java
public void executeCycle() {
    if (programCounter < totalInstructions) {
        programCounter++;  // Avanza PC
        mar++;             // Incrementa registro de dirección de memoria
    }
}
```

### 1.11 `PCB.shouldTriggerIO()`

Determina si el proceso debe solicitar I/O después de ejecutar un ciclo. Usa un contador `ioTriggerCountdown` que decrementa cada instrucción y dispara I/O al llegar a 0.

---

## 2. Implementación de Hilos para el Manejo de Interrupciones

### 2.1 Arquitectura de Hilos

El simulador utiliza 3 hilos principales sincronizados mediante semáforos:

```
┌────────────────────────────────────────────────────────┐
│                     ClockThread                        │
│  (hilo principal - orquesta cada tick)                 │
│                                                        │
│  Cada tick:                                            │
│    1. Señala a IODeviceThread (ioTickSignal.release)    │
│    2. Señala a InterruptGeneratorThread (irqTickSignal) │
│    3. Espera que ambos terminen (ioTickDone, irqTickDone)│
│    4. Ejecuta os.executeOneCycle()                     │
│    5. Duerme el tiempo restante del ciclo              │
└────────────────────────────────────────────────────────┘
         │                              │
    ioTickSignal                   irqTickSignal
         ▼                              ▼
┌──────────────────┐         ┌──────────────────────────┐
│  IODeviceThread   │         │  InterruptGeneratorThread │
│                   │         │                          │
│  Cada tick:       │         │  Cada tick:              │
│  - Decrementa     │         │  - Decrementa contador   │
│    ioRemainingTicks│        │  - Si llega a 0:         │
│  - Si llega a 0:  │         │    genera interrupción   │
│    BLOCKED→READY  │         │    (MICROMETEORITO,      │
│  - Señala done    │         │     RADIACION_SOLAR,     │
│                   │         │     COMANDO_TIERRA)      │
└──────────────────┘         └──────────────────────────┘
```

### 2.2 Protocolo de Sincronización (Tick-Ack)

Se usa un protocolo estricto de señalización basado en `java.util.concurrent.Semaphore`:

```java
// ClockThread.tickDevices():
ioTickDone.drainPermits();      // Limpiar permisos previos
irqTickDone.drainPermits();
ioTickSignal.release();         // Señalar a IODevice: "procesa este tick"
irqTickSignal.release();        // Señalar a IRQ: "procesa este tick"
ioTickDone.acquire();           // Esperar confirmación de IODevice
irqTickDone.acquire();          // Esperar confirmación de IRQ
```

**Garantía:** El `ClockThread` no avanza al siguiente tick hasta que ambos hilos auxiliares confirmen que procesaron el tick actual. Esto evita condiciones de carrera.

### 2.3 IODeviceThread — Manejo de I/O

Simula un dispositivo de I/O que reduce el tiempo restante de cada proceso bloqueado:

1. Espera señal del reloj (`tickSignal.acquire()`).
2. Llama a `os.onIoDeviceTick()` que ejecuta `tickIoForList()`.
3. Para cada proceso en `blockedList`: decrementa `ioRemainingTicks`.
4. Si `ioRemainingTicks` llega a 0 → transición `BLOCKED → READY`.
5. También procesa `blockedSuspended`:
   - Si I/O termina estando suspendido → `BLOCKED_SUSPENDED → READY_SUSPENDED`.
6. Señala `tickDone.release()`.

### 2.4 InterruptGeneratorThread — Generación de Interrupciones

Genera interrupciones de hardware de forma pseudoaleatoria:

1. Espera señal del reloj.
2. Decrementa `ticksUntilNext`.
3. Cuando llega a 0:
   - Selecciona tipo aleatorio: `MICROMETEORITO`, `RADIACION_SOLAR`, `COMANDO_TIERRA`.
   - Genera costo aleatorio: `1 + random(5)` ticks.
   - Publica evento en la `EventQueue` del OS.
   - Calcula nuevo intervalo: `random(minTicks, maxTicks)`.

### 2.5 Manejo de Interrupciones en el Kernel

Cuando se detecta una interrupción:

1. `processEvents()` extrae eventos de la `EventQueue`.
2. `handleInterrupt()` activa la ISR:
   - Si ya hay una ISR activa → la nueva se encola en `pendingInterrupts`.
   - Si no → establece `isrTicksRemaining = costTicks`.
3. Durante la ISR (modo KERNEL):
   - La CPU no ejecuta procesos de usuario.
   - Se sigue admitiendo procesos desde NEW (para evitar inanición).
4. Al finalizar la ISR:
   - Registra latencia en el log.
   - Si hay interrupciones pendientes → procesa la siguiente.

### 2.6 Protección de Estado Compartido

Todo acceso al estado del sistema operativo se protege con un `Semaphore(1, true)` (fair):

```java
private void lockState() {
    stateLock.acquireUninterruptibly();
}
private void unlockState() {
    stateLock.release();
}
```

Esto garantiza exclusión mutua entre:
- `ClockThread` ejecutando `executeOneCycle()`
- `IODeviceThread` ejecutando `onIoDeviceTick()`
- GUI leyendo snapshots con `snapshotForGui()`
- Usuario enviando procesos con `submitNewProcess()`

---

## 3. Lógica de los Algoritmos de Planificación

### 3.1 FCFS (First-Come, First-Served)

**Tipo:** No preemptivo  
**Estructura:** `LinkedQueue<PCB>` (cola FIFO)  
**Criterio:** Orden de llegada (`arrivalTick`)

```
Proceso llega → enqueue(readyQueueFIFO)
CPU libre → dequeue(readyQueueFIFO) → RUNNING
```

- No expulsa al proceso en ejecución.
- Desventaja: procesos cortos esperan detrás de procesos largos (efecto convoy).

### 3.2 RR (Round Robin)

**Tipo:** Preemptivo por quantum  
**Estructura:** `LinkedQueue<PCB>` (cola FIFO)  
**Quantum:** Configurable (por defecto 4 ticks)  
**Criterio:** Tiempo equitativo

```
Cada tick:
  cpuQuantumTicks++
  Si cpuQuantumTicks >= quantum:
    preemptCurrentProcess() → mover RUNNING a final de readyQueueFIFO
    scheduleNextProcess()   → siguiente en la cola
```

- Garantiza equidad entre procesos.
- Apropiado para sistemas interactivos.
- No preempta por prioridad, solo por quantum.

### 3.3 SRT (Shortest Remaining Time)

**Tipo:** Preemptivo  
**Estructura:** `OrderedList<PCB>` con `srtComparator`  
**Criterio:** Menor `remainingInstructions` primero

```
Comparador SRT:
  1. remainingInstructions (menor primero)
  2. recoveryRank (procesos con boost tienen prioridad)
  3. deadlineTick (menor primero como desempate)
  4. arrivalTick (más antiguo primero)
  5. pid (último desempate)
```

- Cada tick verifica si un proceso en READY tiene menos instrucciones restantes que el RUNNING.
- Si es así → preempt inmediato.

### 3.4 PRIORITY (Prioridad)

**Tipo:** Preemptivo  
**Estructura:** `OrderedList<PCB>` con `priorityComparator`  
**Criterio:** Mayor `effectivePriority` primero

```
Comparador PRIORITY:
  1. effectivePriority (mayor primero)
  2. recoveryRank (boost de recuperación)
  3. deadlineTick (menor primero como desempate)
  4. arrivalTick (más antiguo primero)
  5. pid (último desempate)
```

- Prioridades: 1 (baja) a 99 (emergencia).
- Los procesos con deadline perdido reciben un *recovery boost* que incrementa su `effectivePriority` a 90.
- Preempta cuando un proceso de mayor prioridad llega a READY.

### 3.5 EDF (Earliest Deadline First)

**Tipo:** Preemptivo  
**Estructura:** `OrderedList<PCB>` con `edfComparator`  
**Criterio:** Menor `virtualDeadlineTick` primero

```
Comparador EDF:
  1. virtualDeadlineTick (menor primero)
  2. recoveryRank (boost de recuperación)
  3. effectivePriority (mayor primero como desempate)
  4. arrivalTick (más antiguo primero)
  5. pid (último desempate)
```

- Óptimo para sistemas en tiempo real: minimiza deadline misses.
- El `virtualDeadlineTick` puede adelantarse 10 ticks al aplicar recovery boost, dando más urgencia a procesos comprometidos.

---

## 4. Transición de Estados

### 4.1 Modelo de 7 Estados

```
                   admitFromNew()
      ┌─────────┐ ───────────────── ┌─────────┐
      │   NEW   │                   │  READY  │ ◄───── I/O completado
      └─────────┘                   └────┬────┘        (BLOCKED → READY)
                                         │
                              scheduleNextProcess()
                                         │
                                         ▼
                                    ┌─────────┐
                                    │ RUNNING │
                                    └────┬────┘
                                   ┌─────┤─────────────┐
                          hasFinished()  │      shouldTriggerIO()
                                   │   quantum       │
                                   ▼  expirado       ▼
                             ┌──────────┐    ┌──────────┐
                             │TERMINATED│    │ BLOCKED  │
                             └──────────┘    └──────────┘
                                                  │
                                          memoria llena
                                                  │
              ┌─────────────────┐           ┌─────▼───────────┐
              │ READY_SUSPENDED │           │BLOCKED_SUSPENDED│
              │    (en disco)   │ ◄──────── │   (en disco)    │
              └─────────────────┘  I/O done └─────────────────┘
                      │
               swapInIfSpace()
                      │
                      ▼
                ┌─────────┐
                │  READY  │
                └─────────┘
```

### 4.2 Tabla de Transiciones

| Transición | Evento / Condición | Método |
|-----------|-------------------|--------|
| `NEW → READY` | Hay espacio en memoria | `admitFromNew()` |
| `READY → RUNNING` | CPU libre o preemption | `scheduleNextProcess()` |
| `RUNNING → READY` | Quantum expirado (RR) o preemption | `preemptCurrentProcess()` |
| `RUNNING → BLOCKED` | Solicitud de I/O | `handleIoRequest()` |
| `RUNNING → TERMINATED` | Proceso completado | `terminateProcess()` |
| `BLOCKED → READY` | I/O completado (en memoria) | `tickIoForList()` |
| `BLOCKED → BLOCKED_SUSPENDED` | Memoria llena, swap out | `swapOut()` |
| `READY → READY_SUSPENDED` | Memoria llena, swap out | `swapOut()` |
| `BLOCKED_SUSPENDED → READY_SUSPENDED` | I/O completado en disco | `tickBlockedSuspendedIo()` |
| `READY_SUSPENDED → READY` | Espacio libre en memoria | `swapInIfSpace()` |

---

## 5. Análisis Comparativo de Algoritmos

### 5.1 Escenario de Estrés (50+ procesos, memoria limitada a 6)

| Métrica | FCFS | RR | SRT | PRIORITY | EDF |
|---------|------|----|-----|----------|-----|
| **Throughput** | Bajo | Medio | Alto | Alto | Alto |
| **Avg Wait Time** | Alto | Bajo-Medio | Bajo | Variable | Bajo |
| **Deadline Misses** | Muchos | Moderados | Pocos | Pocos | Mínimos |
| **Equidad** | Baja | Alta | Baja | Baja | Media |
| **Context Switches** | Mínimos | Muchos | Moderados | Moderados | Moderados |

### 5.2 Conclusiones por Algoritmo

#### FCFS
- **Fortaleza:** Implementación simple, mínimos cambios de contexto.
- **Debilidad:** Efecto convoy; procesos con deadlines cortos mueren esperando detrás de procesos largos.
- **Veredicto:** Inadecuado para RTOS con deadlines estrictos.

#### RR (Round Robin)
- **Fortaleza:** Equidad excelente; todos los procesos reciben CPU regularmente.
- **Debilidad:** No considera prioridades ni deadlines. Un quantum muy pequeño genera overhead por cambios de contexto; uno muy grande degenera en FCFS.
- **Veredicto:** Apropiado para tiempo compartido general, no óptimo para sistemas en tiempo real.

#### SRT (Shortest Remaining Time)
- **Fortaleza:** Minimiza tiempo de espera promedio; procesos cortos terminan rápido.
- **Debilidad:** Inanición de procesos largos (necesita mecanismo de recovery para mitigar). No considera deadlines directamente.
- **Veredicto:** Buen rendimiento general; el recovery boost mitiga la inanición.

#### PRIORITY
- **Fortaleza:** Procesos de emergencia (prioridad 99) se ejecutan inmediatamente. El recovery boost (prioridad → 90) rescata procesos comprometidos.
- **Debilidad:** Inanición de procesos de baja prioridad sin mecanismo de envejecimiento.
- **Veredicto:** Efectivo en escenarios con emergencias; el boost de recuperación es clave.

#### EDF (Earliest Deadline First)
- **Fortaleza:** Óptimo para minimizar deadline misses. El `virtualDeadlineTick` adelantado da urgencia a procesos que perdieron su deadline.
- **Debilidad:** Mayor overhead computacional por reordenamiento constante.
- **Veredicto:** El algoritmo más adecuado para el simulador RTOS del microsatélite.

### 5.3 Cumplimiento de Deadlines

En escenarios de estrés con muchos procesos compitiendo por 6 espacios en memoria:

- **EDF** logra la mayor tasa de éxito (procesos terminados antes de su deadline).
- **PRIORITY** con recovery boost es el segundo más efectivo.
- **FCFS** tiene la peor tasa de éxito al no considerar urgencia temporal.

El mecanismo de **recovery boost** (implementado en `applyDeadlineRecoveryBoost()`) afecta tanto a PRIORITY como a EDF:
- En PRIORITY: eleva la prioridad efectiva a 90.
- En EDF: adelanta el `virtualDeadlineTick` 10 ticks, dándole urgencia adicional.

---

## 6. Estructuras de Datos Utilizadas

### No se utilizan colecciones estándar de Java

Todas las estructuras son implementaciones propias:

| Estructura | Uso | Descripción |
|-----------|-----|-------------|
| `LinkedQueue<T>` | Cola NEW, cola READY (FCFS/RR) | Cola FIFO con nodos enlazados |
| `OrderedList<T>` | Cola READY (SRT/PRIORITY/EDF), suspendidos | Lista ordenada con inserción por comparador |
| `SimpleList<T>` | Lista de bloqueados, terminados, log de eventos | Lista dinámica con arreglo redimensionable |

La `OrderedList` mantiene los elementos ordenados durante la inserción usando búsqueda lineal (`findInsertIndex()`), lo que garantiza que `pollFirst()` siempre retorna el proceso más prioritario en O(1).

---

## 7. Métricas del Sistema

### Métricas Implementadas

| Métrica | Fórmula | Origen |
|---------|---------|--------|
| **Tasa de Éxito** | `terminatedBeforeDeadlineCount / terminatedList.size()` | `snapshotForGui()` |
| **Throughput** | `terminatedList.size() / globalTick` | `snapshotForGui()` |
| **Tiempo de Espera Promedio** | `totalTerminatedWaitingTicks / terminatedList.size()` | `snapshotForGui()` |
| **Utilización de CPU** | `(userBusyTicks + kernelBusyTicks) / globalTick` | `snapshotForGui()` |

La gráfica de utilización de CPU muestra los últimos 100 puntos usando un arreglo circular (`CPUGraphPanel`), actualizándose cada 5 ticks.
