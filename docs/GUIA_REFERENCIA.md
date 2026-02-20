# Guía de Referencia — Simulador RTOS para Microsatélite

**Dónde está cada cosa y cómo funciona todo junto**

---

## 1. Mapa General del Proyecto

El proyecto tiene 25 archivos Java organizados en 5 paquetes:

```
src/main/java/ve/edu/unimet/so/proyecto1/
│
├── datastructures/        ← Estructuras de datos propias (sin Java Collections)
│   ├── LinkedQueue.java       Cola FIFO con nodos enlazados
│   ├── OrderedList.java       Lista ordenada por comparador
│   ├── SimpleList.java        Lista dinámica con arreglo
│   ├── Compare.java           Interfaz Comparator propia
│   └── DataStructuresTest.java  Pruebas de las estructuras
│
├── kernel/                ← Núcleo del sistema operativo
│   ├── OperatingSystem.java       Lógica central (1225 líneas)
│   ├── ClockThread.java           Hilo del reloj (sincronización)
│   ├── IODeviceThread.java        Hilo del dispositivo de I/O
│   ├── InterruptGeneratorThread.java  Hilo generador de interrupciones
│   ├── MemoryManager.java         Gestión de memoria y swap
│   ├── SchedulingPolicy.java      Enum de políticas (FCFS, RR, SRT, PRIORITY, EDF)
│   ├── EventQueue.java            Cola de eventos del kernel
│   ├── KernelEvent.java           Modelo de evento (IRQ, I/O)
│   ├── PeriodicTaskManager.java   Gestor de tareas periódicas
│   └── PeriodicTaskDefinition.java  Definición de tarea periódica
│
├── models/                ← Modelos de datos
│   ├── PCB.java                   Bloque de Control de Proceso
│   └── ProcessState.java          Enum de 7 estados
│
├── utils/                 ← Utilidades
│   └── ProcessGenerator.java     Generador de procesos realistas
│
├── views/                 ← Interfaz gráfica (GUI)
│   ├── GUIRealisticTest.java      Coordinador principal (punto de entrada)
│   ├── SimulationController.java  Control de simulación (start, pause, velocidad)
│   ├── GUIUpdater.java            Loop de actualización de la GUI
│   ├── MainWindow.java            Ventana principal (layout, componentes)
│   ├── PCBTableModel.java         Modelo de tabla (AbstractTableModel)
│   └── CPUGraphPanel.java         Gráfica de utilización de CPU
│
└── Project1OS.java        ← Main class del proyecto
```

---

## 2. Funciones Clave del Backend (Kernel)

### 2.1 OperatingSystem.java — El corazón del simulador

**Ubicación:** `kernel/OperatingSystem.java` (~1280 líneas)

Este archivo contiene TODA la lógica de simulación. Si te preguntan "dónde se ejecuta X", probablemente esté aquí.

#### Reset del sistema

| Método | Qué hace |
|--------|----------|
| `reset()` | **Reinicio completo.** Limpia todas las colas (new, ready, blocked, terminated), libera CPU, resetea tick a 0, limpia ISR y métricas, vacía event queue y log, limpia periódicas y memoria suspendida, vuelve a FCFS. Todo bajo `lockState()`. |

#### Ejecución del ciclo (líneas ~260–340)

| Método | Línea | Qué hace |
|--------|-------|----------|
| `executeOneCycle()` | 204 | **El método más importante.** Se ejecuta una vez por tick. Orquesta todo: eventos, admisión de procesos, ISR, ejecución en CPU, planificación, I/O y métricas. |
| `scheduleNextProcess()` | 288 | Toma el siguiente proceso de la cola READY y lo pone en CPU. Resetea el quantum. |
| `getNextProcess()` | 300 | Decide de dónde sacar el siguiente proceso según el algoritmo (de `readyQueueFIFO` si FCFS/RR, de `readyListSorted` si SRT/PRIORITY/EDF). |

#### Planificación (líneas 113–427)

| Método | Línea | Qué hace |
|--------|-------|----------|
| `setAlgorithm()` | 368 | Cambia la política en tiempo de ejecución. Extrae todos los procesos de READY y los reinserta en la estructura correcta. |
| `shouldPreempt()` | 420 | Decide si un proceso en READY debe desplazar al que está en CPU. Usa criterio distinto por algoritmo. |
| `preemptCurrentProcess()` | 413 | Saca al proceso de CPU y lo devuelve a READY. |
| `srtComparator` | 113 | Ordena por instrucciones restantes (menor primero). |
| `priorityComparator` | 129 | Ordena por prioridad efectiva (mayor primero). |
| `edfComparator` | 145 | Ordena por deadline virtual (menor primero). |
| `fifoComparator` | 161 | Ordena por tick de llegada (menor primero). |

#### Procesos — Creación y finalización

| Método | Línea | Qué hace |
|--------|-------|----------|
| `submitNewProcess()` | 450 | Recibe un PCB desde la GUI y lo encola en `newQueue`. |
| `terminateProcess()` | 353 | Marca el proceso como TERMINATED, calcula métricas (waitingTime, deadline), libera CPU. |
| `submitInterrupt()` | 482 | Permite inyectar una interrupción externa (ej: botón de emergencia en la GUI). |

#### Interrupciones (líneas 843–963)

| Método | Línea | Qué hace |
|--------|-------|----------|
| `processEvents()` | 843 | Saca eventos de la `EventQueue` y los despacha (IRQ o I/O). |
| `handleInterrupt()` | 854 | Activa la ISR. Si ya hay una corriendo, encola la nueva. Registra tipo, tick y costo. |
| `handleIoRequest()` | 868 | Bloquea un proceso: RUNNING → BLOCKED, lo añade a `blockedList`. |
| `onIoDeviceTick()` | 886 | Llamado por `IODeviceThread`: decrementa contadores de I/O para procesos bloqueados. |
| `detectDeadlineMisses()` | 910 | Recorre TODAS las colas buscando procesos cuyo deadline ya pasó. |
| `applyDeadlineRecovery()` | 949 | Aplica el boost de recuperación (prioridad → 90, deadline virtual adelantado 10 ticks). |

#### Snapshots para la GUI (líneas 605–810)

| Método | Línea | Qué hace |
|--------|-------|----------|
| `snapshotForGui()` | 605 | **Punto de contacto GUI ↔ Backend.** Genera un `GuiSnapshot` con todos los datos necesarios para pintar la interfaz: tablas, métricas, log, memoria. Todo bajo `lockState()`. |
| `snapshotNewRows()` | 660 | Devuelve los procesos de newQueue como `Object[][]` para la tabla. |
| `snapshotReadyRows()` | 669 | Devuelve READY (de FIFO o de OrderedList según algoritmo). |
| `snapshotBlockedRows()` | 681 | Devuelve procesos bloqueados. |
| `snapshotTerminatedRows()` | 690 | Devuelve procesos terminados. |
| `pcbToRowInternal()` | 1124 | Convierte un PCB a `Object[]` con los campos que muestra la tabla. |

#### Métricas (líneas 1049–1122)

| Método | Línea | Qué hace |
|--------|-------|----------|
| `getUserBusyTicks()` | 1049 | Ticks donde la CPU ejecutó procesos de usuario. |
| `getKernelBusyTicks()` | 1058 | Ticks donde la CPU ejecutó ISRs. |
| `getIdleTicks()` | 1067 | Ticks donde no hubo actividad. |
| `getMissionSuccessRate()` | 1076 | Tasa de éxito = terminados antes de deadline / total terminados. |
| `getThroughput()` | 1088 | Procesos terminados por tick. |
| `getAverageWaitingTime()` | 1100 | Promedio del waitingTime de todos los terminados. |
| `getCpuUtilizationTotal()` | 1112 | (userBusy + kernelBusy) / globalTick. |

---

### 2.2 ClockThread.java — El motor del tiempo

**Ubicación:** `kernel/ClockThread.java` (210 líneas)

El `ClockThread` es el hilo que mueve toda la simulación. Cada "tick" simula un ciclo del reloj del procesador.

| Método | Qué hace |
|--------|----------|
| `run()` | Loop principal: `tickDevices()` + `os.executeOneCycle()` + `sleep()`. |
| `tickDevices()` | Protocolo tick-ack: señala a IODeviceThread y InterruptGeneratorThread, espera a que ambos terminen, y luego ejecuta el ciclo. |
| `startClock()` | Inicia el hilo (solo la primera vez). |
| `pauseClock()` | Pausa la simulación (el hilo queda vivo pero no avanza). |
| `resumeClock()` | Reanuda la simulación pausada. |
| `stepOnce()` | Ejecuta exactamente 1 tick (debug). |
| `setCycleDurationMs()` | Cambia la velocidad de la simulación. |

**Sincronización:** Usa 4 semáforos (`ioTickSignal`, `ioTickDone`, `irqTickSignal`, `irqTickDone`) para coordinar con los hilos auxiliares.

---

### 2.3 MemoryManager.java — Gestión de memoria y swap

**Ubicación:** `kernel/MemoryManager.java` (~390 líneas)

Controla cuántos procesos caben en "memoria" (por defecto 6). Cuando se llena, hace swap.

| Método | Qué hace |
|--------|----------|
| `admitFromNew()` | Intenta mover procesos de NEW a READY. Si no hay espacio, evalúa swap. |
| `swapOut()` | Expulsa un proceso a disco: READY → READY_SUSPENDED ó BLOCKED → BLOCKED_SUSPENDED. |
| `swapInIfSpace()` | Si hay espacio libre, trae un proceso de READY_SUSPENDED → READY. |
| `selectVictimFromSwapOut()` | Elige qué proceso sacar de memoria (el de menor criticidad). |
| `onIoComplete()` | Cuando un proceso suspendido termina I/O: BLOCKED_SUSPENDED → READY_SUSPENDED. |
| `getResidentCount()` | Cuenta cuántos procesos hay en memoria (ready + blocked + running). |
| `reset()` | Limpia `readySuspended` y `blockedSuspended` (usado por el botón RESET). |

---

### 2.4 IODeviceThread.java — Hilo de I/O

**Ubicación:** `kernel/IODeviceThread.java` (60 líneas)

1. Espera señal del reloj (`tickSignal.acquire()`).
2. Llama a `os.onIoDeviceTick()` que decrementa `ioRemainingTicks` de cada proceso en BLOCKED.
3. Cuando llega a 0: BLOCKED → READY.
4. Señala al reloj que terminó (`tickDone.release()`).

---

### 2.5 InterruptGeneratorThread.java — Hilo de interrupciones

**Ubicación:** `kernel/InterruptGeneratorThread.java` (88 líneas)

1. Espera señal del reloj.
2. Decrementa un contador `ticksUntilNext`.
3. Cuando llega a 0: genera una interrupción con tipo aleatorio (`MICROMETEORITO`, `RADIACION_SOLAR`, `COMANDO_TIERRA`) y costo aleatorio (1–5 ticks).
4. Publica el evento en la `EventQueue`.

---

### 2.6 PCB.java — Bloque de Control de Proceso

**Ubicación:** `models/PCB.java` (206 líneas)

Cada proceso es un objeto PCB que contiene:

| Campo | Descripción |
|-------|-------------|
| `pid` | ID único del proceso |
| `name` | Nombre temático (Telemetry_TX, GPS_Update, etc.) |
| `totalInstructions` | Total de instrucciones a ejecutar |
| `programCounter` | Instrucción actual (avanza con `executeCycle()`) |
| `mar` | Registro de dirección de memoria |
| `priority` / `effectivePriority` | Prioridad base y efectiva (con boost) |
| `arrivalTick` | Tick en que llegó al sistema |
| `deadlineTick` / `virtualDeadlineTick` | Deadline absoluto y virtual (ajustable por recovery) |
| `state` | Estado actual (ProcessState enum) |
| `waitingTime` | Ticks acumulados esperando en READY |
| `ioEveryNInstructions` | Cada cuántas instrucciones pide I/O |
| `ioServiceTicks` | Cuántos ticks dura la I/O |
| `ioRemainingTicks` | Ticks restantes de I/O actual |

**Métodos clave:**
- `executeCycle()` — Avanza el PC y MAR (simula ejecutar una instrucción).
- `shouldTriggerIO()` — Verifica si toca hacer I/O (basado en `ioTriggerCountdown`).
- `hasFinished()` — `programCounter >= totalInstructions`.
- `getRemainingInstructions()` — `totalInstructions - programCounter`.

---

### 2.7 ProcessGenerator.java — Generador de procesos

**Ubicación:** `utils/ProcessGenerator.java` (151 líneas)

Genera procesos con nombres y parámetros realistas de microsatélite:

| Método | Tipo de proceso | Prioridad | Instrucciones | I/O |
|--------|-------|-----------|---------------|-----|
| `createTelemetryProcess()` | Telemetría | 50 | 10–20 | Cada 5 instrucciones |
| `createCameraProcess()` | Cámara | 40 | 30–50 | Cada 10 instrucciones |
| `createNavigationProcess()` | Navegación | 60 | 20–30 | Cada 8 instrucciones |
| `createEmergencyProcess()` | Emergencia | 99 | 5–10 | Sin I/O |
| `createLowPriorityProcess()` | Baja prioridad | 10 | 15–25 | Cada 3 instrucciones |
| `createRandomProcess()` | Aleatorio | Variable | Variable | Variable |
| `generateMixedBatch()` | Uno de cada tipo | — | — | — |
| `generateRandomBatch()` | N aleatorios | — | — | — |

---

## 3. La Interfaz Gráfica (GUI)

### 3.1 Arquitectura — Quién hace qué

La GUI se divide en 4 clases con responsabilidades claras:

```
┌──────────────────────────────────────────────────────────────┐
│                    GUIRealisticTest.java                      │
│            (Coordinador - conecta todo)                       │
│                                                              │
│  1. Crea OperatingSystem + ClockThread                       │
│  2. Crea MainWindow                                          │
│  3. Crea SimulationController + GUIUpdater                   │
│  4. Genera procesos iniciales                                │
│  5. Conecta botones con acciones                             │
│  6. Inicia el loop de refresh                                │
└───────────┬─────────────────────┬────────────────────────────┘
            │                     │
            ▼                     ▼
┌───────────────────┐   ┌──────────────────────────┐
│ SimulationController│   │      GUIUpdater           │
│                   │   │                          │
│ start/pause/step  │   │ Timer cada 100ms:        │
│ velocidad (±)     │   │  1. os.snapshotForGui()  │
│ cambio algoritmo  │   │  2. Actualiza tablas     │
│                   │   │  3. Actualiza métricas   │
│  ↕ ClockThread    │   │  4. Actualiza memoria    │
│  ↕ OperatingSystem│   │  5. Actualiza gráfica    │
└───────────────────┘   └──────────────────────────┘
            │                     │
            └──────────┬──────────┘
                       ▼
            ┌──────────────────┐
            │   MainWindow     │
            │                  │
            │ Layout completo: │
            │  - Header        │
            │  - 7 tablas      │
            │  - Panel CPU     │
            │  - Barra memoria │
            │  - Panel métricas│
            │  - Gráfica CPU   │
            │  - Log eventos   │
            └──────────────────┘
```

---

### 3.2 GUIRealisticTest.java — El punto de entrada

**Ubicación:** `views/GUIRealisticTest.java` (~220 líneas)

Este archivo es el `main()` del programa. Su constructor hace todo en orden:

```java
public GUIRealisticTest() {
    // 1. Crea el kernel
    this.os = new OperatingSystem(QUANTUM);        // quantum = 4

    // 2. Crea el reloj
    this.clock = new ClockThread(os, 200);         // 200ms por tick

    // 3. Crea la ventana
    this.mainWindow = new MainWindow();

    // 4. Crea los controladores
    this.simulationController = new SimulationController(os, clock, mainWindow, 200);
    this.guiUpdater = new GUIUpdater(os, mainWindow);

    // 5. Genera 5 procesos iniciales (mixtos)
    generateInitialProcesses();

    // 6. Registra 4 tareas periódicas
    registerDefaultPeriodicTasks();

    // 7. Conecta cada botón con su acción
    setupControlButtons();

    // 8. Arranca el timer de refresh GUI
    guiUpdater.startRefreshLoop();
}
```

#### Conexión de botones (setupControlButtons, línea 81):

| Botón de la GUI | Qué hace al hacer clic |
|-----------------|----------------------|
| **▶ START** | `simulationController.startSimulation()` → `clock.startClock()` |
| **⏸ PAUSE** | `simulationController.pauseSimulation()` → `clock.pauseClock()` |
| **⏭ STEP** | `simulationController.stepSimulation()` → `clock.stepOnce()` |
| **🔄 RESET** | `resetAll()` → detiene clock, resetea OS, crea nuevo ClockThread, regenera procesos |
| **+1** | `ProcessGenerator.createRandomProcess()` → `os.submitNewProcess()` |
| **+5** | `ProcessGenerator.generateRandomBatch(5)` → `os.submitNewProcess()` x5 |
| **+20** | `ProcessGenerator.generateRandomBatch(20)` → `os.submitNewProcess()` x20 |
| **▲ (Speed Up)** | `simulationController.adjustSpeed(-50)` → `clock.setCycleDurationMs()` |
| **▼ (Speed Down)** | `simulationController.adjustSpeed(+100)` → `clock.setCycleDurationMs()` |
| **Speed field** | `simulationController.setSpeed(valor)` |
| **🚨 EMERGENCIA** | `os.submitInterrupt("MICROMETEORITO_MANUAL", 3)` + `createEmergencyProcess()` |
| **ComboBox Algo** | `simulationController.setSchedulingAlgorithm(nombre)` → `os.setAlgorithm()` |

#### Método resetAll() — Reinicio completo:

```java
private void resetAll() {
    clock.stopClock();                              // 1. Detener simulación
    os.reset();                                     // 2. Limpiar todo el kernel
    this.clock = new ClockThread(os, speed);         // 3. Nuevo hilo (Java no reinicia threads)
    simulationController.setClock(this.clock);       // 4. Inyectar en controlador
    generateInitialProcesses();                      // 5. Regenerar procesos
    registerDefaultPeriodicTasks();                  // 6. Re-registrar periódicas
    guiUpdater.resetGraphState();                    // 7. Limpiar gráfica
    mainWindow.clearCpuGraph();
    mainWindow.getAlgorithmComboBox().setSelectedIndex(0);  // 8. Volver a FCFS
}
```

---

### 3.3 SimulationController.java — Control de simulación

**Ubicación:** `views/SimulationController.java` (~105 líneas)

Encapsula el control del `ClockThread` y la velocidad:

- **`startSimulation()`** → Llama a `clock.startClock()`.
- **`pauseSimulation()`** → Llama a `clock.pauseClock()`.
- **`stepSimulation()`** → Llama a `clock.stepOnce()` (avanza un tick).
- **`adjustSpeed(deltaMs)`** → Modifica la velocidad sumando/restando milisegundos (límites: 10ms–2000ms).
- **`setSpeed(speedMs)`** → Establece velocidad por valor exacto.
- **`setSchedulingAlgorithm(name)`** → Convierte String a `SchedulingPolicy` enum y llama a `os.setAlgorithm()`.
- **`setClock(ClockThread)`** → Reemplaza el clock actual (usado tras reset, porque Java no permite reiniciar un `Thread` terminado).

---

### 3.4 GUIUpdater.java — El puente entre kernel y GUI

**Ubicación:** `views/GUIUpdater.java` (~115 líneas)

Este es **el archivo clave** que conecta backend con frontend. Tiene un `Timer` de Swing que cada 100ms hace:

```
refreshGUI():
    1. snapshot = os.snapshotForGui()         ← Obtiene TODOS los datos del kernel
    2. mainWindow.updateClock(tick)           ← Actualiza el reloj
    3. mainWindow.updateCpuMode(mode)         ← Muestra USER/KERNEL/IDLE
    4. mainWindow.updateCPU(name, progress)   ← Panel del proceso en ejecución
    5. mainWindow.updateRunningDetailsRow()   ← Detalles (Prio, PC, MAR, Deadline)
    6. mainWindow.updateNewTableRows()        ← Tabla de NEW
    7. mainWindow.updateReadyTableRows()      ← Tabla de READY
    8. mainWindow.updateBlockedTableRows()    ← Tabla de BLOCKED
    9. mainWindow.updateTerminatedTableRows() ← Tabla de TERMINATED
   10. mainWindow.updateReadySuspendedTableRows()   ← Tabla READY_SUSPENDED
   11. mainWindow.updateBlockedSuspendedTableRows() ← Tabla BLOCKED_SUSPENDED
   12. mainWindow.updateLog(eventLog)         ← Log de eventos
   13. updateMemoryBar(resident, max)         ← Barra de memoria
   14. updateMetrics(success, throughput, avgWait, cpuUtil) ← Panel de métricas
   15. Si tick % 5 == 0 → mainWindow.addCpuUtilDataPoint() ← Punto en gráfica
```

**Método adicional:** `resetGraphState()` — Reinicia el tracking del último tick de la gráfica (`lastCpuGraphTick = -1`). Se llama durante el reset de la simulación.

**Flujo de datos:** `OperatingSystem` → `GuiSnapshot` → `GUIUpdater` → `MainWindow`

El snapshot `GuiSnapshot` es una clase interna de `OperatingSystem` que contiene TODOS los datos ya procesados: filas de tablas (`Object[][]`), métricas, log, etc. Esto evita que la GUI acceda directamente a las colas del kernel.

---

### 3.5 MainWindow.java — La ventana principal

**Ubicación:** `views/MainWindow.java` (~785 líneas)

Construye toda la interfaz visual usando Swing puro (sin bibliotecas externas).

#### Layout de la ventana:

```
┌───────────────────────────────────────────────────────────┐
│                      HEADER (createHeader)                 │
│  [UNIMET-Sat RTOS Simulator]                              │
│  [▶ START][⏸ PAUSE][⏭ STEP][🔄 RESET] [+1][+5][+20]     │
│  [FCFS ▼]  [▲][___ms]                                    │
│            [▼]          ← flechas velocidad verticales    │
├──────────┬──────────────────────┬─────────────────────────┤
│  LEFT    │      CENTER          │         RIGHT           │
│          │                      │                         │
│ ┌──────┐ │ ┌──────────────────┐ │ ┌───────────────────┐   │
│ │ NEW  │ │ │   CPU PANEL      │ │ │    BLOCKED        │   │
│ │ table│ │ │ [Process name]   │ │ │    table          │   │
│ └──────┘ │ │ [Progress bar]   │ │ └───────────────────┘   │
│          │ │ [Mode: USER]     │ │                         │
│ ┌──────┐ │ │ [Prio/PC/MAR]   │ │ ┌───────────────────┐   │
│ │READY │ │ └──────────────────┘ │ │   TERMINATED      │   │
│ │ table│ │ ┌──────────────────┐ │ │    table          │   │
│ └──────┘ │ │   MEMORY BAR     │ │ └───────────────────┘   │
│          │ └──────────────────┘ │                         │
│          │ ┌──────────────────┐ │                         │
│          │ │   🚨 EMERGENCY   │ │                         │
│          │ └──────────────────┘ │                         │
│          │ ┌──────────────────┐ │                         │
│          │ │   EVENT LOG      │ │                         │
│          │ │   (text area)    │ │                         │
│          │ └──────────────────┘ │                         │
├──────────┴──────────────────────┴─────────────────────────┤
│                       FOOTER (createFooter)                │
│  ┌──────────────────┐ ┌──────────────────────────────────┐│
│  │ READY_SUSPENDED  │ │            METRICS PANEL         ││
│  │     table        │ │  Success Rate: 85.0%             ││
│  ├──────────────────┤ │  Throughput: 0.12 proc/tick      ││
│  │BLOCKED_SUSPENDED │ │  Avg Wait: 5.3 ticks             ││
│  │     table        │ │  CPU Util: 78.5%                 ││
│  └──────────────────┘ │  ┌─────────────────────────────┐ ││
│                       │  │     CPU Graph (CPUGraphPanel)│ ││
│                       │  └─────────────────────────────┘ ││
│                       └──────────────────────────────────┘│
└───────────────────────────────────────────────────────────┘
```

#### Métodos de creación del layout:

| Método | Línea | Qué construye |
|--------|-------|--------------|
| `createHeader()` | 416 | Título, botones de control, generación de procesos, velocidad, selector de algoritmo |
| `createCentralPanel()` | 526 | Panel de CPU (nombre, barra de progreso, modo, detalles), barra de memoria, botón emergencia, log de eventos |
| `createQueuePanel()` | 500 | Panel genérico para cualquier tabla de cola (NEW, READY, etc.) |
| `createFooter()` | 616 | Tablas de READY_SUSPENDED y BLOCKED_SUSPENDED + panel de métricas |
| `createMetricsPanel()` | 628 | Labels de métricas + CPUGraphPanel |
| `createControlButton()` | 489 | Crea un botón estilizado (colores, fuente, etc.) |
| `initModels()` | 112 | Crea los 7 `PCBTableModel` (uno por tabla) |

#### Métodos de actualización (llamados por GUIUpdater):

| Método | Qué actualiza |
|--------|-------------|
| `updateClock(cycle)` | Label del reloj: "⏱ Tick: 42" |
| `updateCPU(name, progress, max)` | Nombre del proceso y barra de progreso |
| `updateCpuMode(mode)` | Indicador de modo: USER (verde), KERNEL (rojo), IDLE (gris) |
| `updateRunningDetailsRow(row)` | Detalles: Prio, PC, MAR, Deadline |
| `updateMemory(percentage)` | Barra de memoria con color (verde → amarillo → rojo) |
| `updateNewTableRows(rows)` | Tabla NEW |
| `updateReadyTableRows(rows)` | Tabla READY |
| `updateBlockedTableRows(rows)` | Tabla BLOCKED |
| `updateTerminatedTableRows(rows)` | Tabla TERMINATED |
| `updateReadySuspendedTableRows(rows)` | Tabla READY_SUSPENDED |
| `updateBlockedSuspendedTableRows(rows)` | Tabla BLOCKED_SUSPENDED |
| `updateMetrics(success, throughput, avgWait, cpuUtil)` | 4 labels de métricas |
| `addCpuUtilDataPoint(util)` | Agrega punto a la gráfica |
| `updateLog(entries)` | Área de texto con eventos recientes |
| `clearCpuGraph()` | Limpia la gráfica de CPU (usado por RESET) |

---

### 3.6 PCBTableModel.java — Modelo de tablas

**Ubicación:** `views/PCBTableModel.java` (148 líneas)

Extiende `AbstractTableModel` (NO usa `DefaultTableModel` — restricción del proyecto). Cada tabla del sistema tiene una instancia de este modelo.

**Columnas:** ID | Name | State | PC | MAR | Prio | Remaining | Deadline

**Cómo se actualizan las tablas:**
1. `GUIUpdater.refreshGUI()` obtiene un `GuiSnapshot` del kernel.
2. El snapshot contiene `Object[][]` para cada cola (ya convertidos por `pcbToRowInternal()`).
3. GUIUpdater llama a `mainWindow.updateXxxTableRows(rows)`.
4. MainWindow llama a `pcbTableModel.updateFromRows(rows)`.
5. El modelo llama a `fireTableDataChanged()` que notifica a Swing para repintar.

---

### 3.7 CPUGraphPanel.java — Gráfica de utilización

**Ubicación:** `views/CPUGraphPanel.java` (125 líneas)

Panel Swing personalizado que dibuja una gráfica de línea de la utilización de CPU:

- Usa un **arreglo circular** (`double[]`) de 100 puntos (sin Java Collections).
- Se actualiza cada 5 ticks via `addDataPoint(utilization)`.
- Dibuja con `Graphics2D`: grid de fondo, área sombreada, línea de datos, ejes.
- Colores: fondo oscuro, línea verde, fill semitransparente.

---

## 4. Flujo Completo: Del Clic al Tick

### 4.1 "Presiono START"

```
1. Usuario clic → [▶ START]
2. GUIRealisticTest.setupControlButtons() → listener del botón
3. simulationController.startSimulation()
4. clock.startClock()
5. ClockThread.run() comienza el loop:
   a. tickDevices() → señala IODevice + IRQGenerator
   b. IODevice: decrementa I/O, señala done
   c. IRQGenerator: puede generar interrupción, señala done
   d. os.executeOneCycle() → ejecuta un tick completo
   e. Thread.sleep(cycleDurationMs)
6. GUIUpdater (cada 100ms) → os.snapshotForGui() → actualiza MainWindow
```

### 4.2 "Presiono GEN 20"

```
1. Usuario clic → [GEN 20]
2. ProcessGenerator.generateRandomBatch(20, currentTick)
3. Para cada PCB: os.submitNewProcess(p) → lo encola en newQueue
4. En el siguiente tick:
   a. memoryManager.admitFromNew() intenta admitir
   b. Si memoria llena (6 procesos max) → selectVictimFromSwapOut()
   c. Victim: READY → READY_SUSPENDED ó BLOCKED → BLOCKED_SUSPENDED
   d. El nuevo proceso entra a READY
5. GUIUpdater refresca → tablas muestran los cambios
```

### 4.3 "Presiono 🚨 EMERGENCIA"

```
1. Usuario clic → [🚨 EMERGENCY]
2. os.submitInterrupt("MICROMETEORITO_MANUAL", 3)
   → Crea KernelEvent y lo publica en EventQueue
3. ProcessGenerator.createEmergencyProcess(currentTick)
   → PCB con prioridad 99, deadline corto, sin I/O
4. os.submitNewProcess(emergency) → entra a newQueue
5. En el siguiente tick:
   a. processEvents() saca el evento → handleInterrupt()
   b. ISR activa: isrTicksRemaining = 3 (3 ticks en KERNEL mode)
   c. admitFromNew() admite el proceso de emergencia
6. Después de 3 ticks de ISR:
   a. Si algoritmo PRIORITY → el proceso de emergencia (prio 99) preempta al running
   b. Se ejecuta primero por tener máxima prioridad
7. GUI muestra: modo KERNEL durante ISR, luego USER ejecutando el proceso de emergencia
```

### 4.4 "Cambio de algoritmo a EDF"

```
1. Usuario selecciona "EDF" en JComboBox
2. simulationController.setSchedulingAlgorithm("EDF")
3. os.setAlgorithm(SchedulingPolicy.EDF):
   a. Extrae todos los procesos de readyQueueFIFO y readyListSorted
   b. Crea nueva OrderedList con edfComparator
   c. Reinserta todos los procesos ordenados por virtualDeadlineTick
4. A partir de ahora:
   - scheduleNextProcess() toma el de menor deadline
   - shouldPreempt() compara deadlines para preemption
```

### 4.5 "Presiono 🔄 RESET"

```
1. Usuario clic → [🔄 RESET]
2. GUIRealisticTest.resetAll():
   a. clock.stopClock() → detiene el hilo actual + hilos auxiliares (IO, IRQ)
   b. os.reset() → limpia TODO: colas, CPU, ISR, métricas, log, periódicas, memoria
   c. new ClockThread(os, speed) → crea nuevo hilo (Java no reinicia threads)
   d. simulationController.setClock(newClock) → inyecta el nuevo clock
   e. generateInitialProcesses() → crea 5 procesos mixtos desde PID 1
   f. registerDefaultPeriodicTasks() → 4 tareas periódicas base
   g. guiUpdater.resetGraphState() → reinicia tracking de la gráfica
   h. mainWindow.clearCpuGraph() → borra puntos de la gráfica
   i. algorithmComboBox → vuelve a FCFS
   j. Botones → START habilitado, PAUSE/STEP deshabilitados
3. GUIUpdater.refreshGUI() → siguiente refresh muestra el estado limpio
4. La simulación queda en pausa, lista para presionar START de nuevo
```
