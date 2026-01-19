# Simulador RTOS (Microsatélite) — Especificación Inicial (v0.2.1) (revisada)

Documento interno para que estemos en la misma página, sujeto a cambios.  
Proyecto: `Project1OS` (NetBeans + Java 23 + Maven).
Hice una revision despues de poder hablar con alguien que me dio unos consejos

## 1) Objetivo

Construir un simulador de Sistema Operativo en Tiempo Real (RTOS) para un microsatélite que permita:

- Gestionar procesos periódicos y aperiódicos con prioridad y deadline.
- Simular 5 políticas de planificación con cambio dinámico en ejecución:
  - Generales: **FCFS**, **Round Robin (RR)**, **SRT**
  - Tiempo real: **Prioridad Estática Preemptiva**, **EDF**
- Manejar interrupciones externas asíncronas mediante Threads, preemptando la CPU.
- Modelar el ciclo de vida completo de procesos con estados suspendidos:
  - NEW, READY, RUNNING, BLOCKED, TERMINATED, READY_SUSPENDED, BLOCKED_SUSPENDED
- Mostrar todo en una GUI Swing (obligatoria): colas, PCB, log, métricas y gráficas.

Meta principal: ser demostrable, estable y evaluable en menos de 5 semanas, evitando sobreingeniería.

---

## 2) Restricciones obligatorias (rúbrica)

- Lenguaje: **Java 21+** (se usa Java 23).
- Proyecto: **Maven** y ejecutable en **NetBeans**.
- GUI obligatoria (Swing recomendado).
- Concurrencia obligatoria: **Threads + Semaphores**.
- Prohibido Java Collections Framework en el código del estudiante:
  - No usar: ArrayList, LinkedList, Queue, Stack, Vector, Map, Set, etc.
- Librerías externas permitidas solo para:
  - Gráficas (charts)
  - Carga JSON/CSV

### 2.1 Nota de cumplimiento (importante para evitar “puntos perdidos”)

Nuestro código **no** usará colecciones del framework (java.util.* tipo List/Map/Queue/etc.).

Se permite usar APIs de concurrencia del JDK necesarias para la rúbrica:

- java.util.concurrent.Semaphore (requerido por semáforos)
- Thread, Runnable, volatile, etc.

Swing/JDK puede usar estructuras internas (ej. Vector) dentro del framework, pero:

- nosotros no las instanciamos ni dependemos de ellas,
- y evitamos modelos como DefaultTableModel justamente para no “apoyarnos” en colecciones.

---

## 3) Supuestos del simulador (para reducir complejidad)

- El tiempo se modela en ticks discretos.
- 1 instrucción = 1 tick, todas las instrucciones cuestan lo mismo.
- PC y MAR incrementan +1 por instrucción ejecutada.
- La preempción “inmediata” se aplica en frontera de tick (antes de la siguiente instrucción).
- Deadline es absoluta en ticks (deadlineTick).
- “Memoria” se modela como máximo de procesos residentes (sin memoria real).

---

## 4) Reloj global y ciclo de simulación

- **globalTick (long)**: incrementa 1 por ciclo.
- **cycleDurationMs (volatile int)**: configurable en tiempo real desde GUI.

Orden del ciclo por tick (KernelClockThread):

1. Consumir eventos pendientes (interrupciones, I/O completada, acciones GUI).
2. Admisión desde NEW y swapping (mediano plazo / memoria).
3. Decidir planificación (puede causar cambio de contexto).
4. Ejecutar máximo 1 instrucción del proceso RUNNING (si no estamos en ISR).
5. Actualizar métricas, deadlines, y refrescar GUI con snapshots.

**Regla de preempción:** si un evento llega “durante un tick”, se refleja antes del siguiente tick (tick-boundary).

---

## 5) Modelo de proceso (PCB) y estados

### 5.1 Estados (enum ProcessState)

- NEW
- READY
- RUNNING
- BLOCKED
- TERMINATED
- READY_SUSPENDED
- BLOCKED_SUSPENDED

### 5.2 Campos mínimos del PCB

**Identidad / estado**

- int id
- String name
- ProcessState state

**Ejecución**

- int pc
- int mar
- int totalInstructions
- int remainingInstructions

**Tiempo real**

- int priority (convención: número mayor = más prioridad)
- long arrivalTick
- long deadlineTick (absoluta)
- boolean deadlineMissed

**I/O simplificada**

- int ioEveryTicks (0 = nunca)
- int ioTriggerCountdown (cuenta hacia 0)
- int ioServiceTicks
- int ioRemainingTicks

**RR**

- int rrQuantumUsed

**Métricas por proceso**

- long readyWaitTicks
- long startTick
- long endTick

---

## 6) Estructuras de datos (sin java.util collections)

### 6.1 Estructuras mínimas requeridas (alineadas al repo)

- **LinkedQueue<T>** FIFO O(1) (ya implementada)
- **SimpleList<T>** (arreglo dinámico) para:
  - lista global de PCBs
  - templates periódicas
  - snapshots / terminated list
- **OrderedList<T>** (inserción ordenada O(n), “best en index 0”) para:
  - READY en EDF/SRT/PRIORITY

(Opcional) buffer circular con arreglo fijo para gráficas (si hace falta)

Nota: el nombre “DynamicArray” en el documento anterior se reemplaza por el real del repo: **SimpleList**.

### 6.2 Regla clave de seguridad

- Solo el kernel mueve PCBs entre colas.
- Los hilos externos publican eventos, no cambian colas directamente.

### 6.3 Snapshots para GUI

Para evitar iteración concurrente y depender de modelos Swing internos:

- Cada cola expone un snapshot por tick:
  - Object[] toArray() o PCB[] snapshotArray() (preferible para colas de PCB)
- La GUI nunca itera nodos internos ni usa iteradores de colecciones.

---

## 7) Políticas de planificación (5)

### 7.1 Selector dinámico

SchedulingPolicy = { FCFS, RR, SRT, PRIORITY, EDF }

Cambiar política en ejecución:

- Rebuild/reordenar READY con el comparador nuevo.
- Forzar replanificación al siguiente tick.

### 7.2 Reglas de preempción (en frontera de tick)

- **FCFS:** no preemptivo (solo cambia por bloqueo/fin/ISR).
- **RR:** preemptivo por quantum.
- **SRT:** preemptivo si existe otro con menor remainingInstructions.
- **PRIORITY:** preemptivo por mayor prioridad.
- **EDF:** preemptivo por deadline más próxima.

### 7.3 Empates (tie-breakers estables)

- **EDF:** deadlineTick asc, priority desc, arrivalTick asc, id asc
- **SRT:** remainingInstructions asc, deadlineTick asc, arrivalTick asc, id asc
- **PRIORITY:** priority desc, deadlineTick asc, arrivalTick asc, id asc
- **FCFS:** arrivalTick asc, id asc
- **RR:** FIFO (FCFS), pero aplica quantum

---

## 8) Procesos periódicos (modelo práctico y controlado)

MVP de periodicidad sin procesos infinitos:

- Lista de plantillas periódicas **PeriodicTaskTemplate** almacenadas en SimpleList<PeriodicTaskTemplate>.

Cada template tiene:

- nombre base
- instrucciones (WCET)
- periodTicks
- relativeDeadlineTicks
- prioridad
- patrón I/O opcional
- nextReleaseTick
- jobsReleased (contador)

Liberación de jobs:

- Si globalTick >= nextReleaseTick:
  - crear PCB job
  - arrivalTick = globalTick
  - deadlineTick = globalTick + relativeDeadlineTicks
  - nextReleaseTick += periodTicks
  - jobsReleased++

Control de crecimiento (obligatorio para no explotar):

- maxJobsPerTemplate y/o maxTotalJobsGlobal
- Si se alcanza el límite, deja de liberar y lo loguea.

---

## 9) Memoria y planificador de mediano plazo (suspendidos)

### 9.1 Modelo de memoria

- maxProcessesInMemory (configurable GUI)
- Residentes = procesos en READY, RUNNING, BLOCKED
- No cuentan: NEW, *_SUSPENDED, TERMINATED

### 9.2 Regla clara NEW vs SUSPENDED (para evitar confusión de rúbrica)

- NEW = proceso no admitido aún al sistema residente.
- *_SUSPENDED = proceso que ya estaba residente y fue swap-out por falta de memoria.

### 9.3 Admisión desde NEW

Cada tick:

- Mientras NEW no esté vacía y residentCount < maxProcessesInMemory:
  - NEW → READY
- Si NEW tiene procesos y memoria está llena:
  - intentar swap-out (nunca swap del RUNNING)
  - si hay víctima: mover a suspendido correspondiente y admitir 1 de NEW

### 9.4 Selección de víctima (menos crítico)

Orden “menos crítico primero”:

1. deadlineTick más lejana (mayor)
2. prioridad más baja
3. remainingInstructions mayor
4. arrivalTick más reciente
5. id mayor

Preferencia:

- primero víctima en READY
- si no hay, víctima en BLOCKED
- nunca mover RUNNING

### 9.5 Swap-in

Cuando hay espacio:

- traer primero desde READY_SUSPENDED el más crítico
- si no hay, desde BLOCKED_SUSPENDED (vuelve a BLOCKED y continúa I/O)

Logs obligatorios:

- “Proceso X movido a READY_SUSPENDED”
- “Proceso X movido a BLOCKED_SUSPENDED”
- “Proceso X reanudado desde SUSPENDED”

---

## 10) I/O asíncrona (bloqueo y retorno)

### 10.1 Disparo de I/O

En cada tick de ejecución de una instrucción:

- si ioEveryTicks > 0:
  - decrementar ioTriggerCountdown
  - si llega a 0 → publicar evento IO_REQUEST(pcbId) y resetear contador

### 10.2 Manejo en kernel

Al procesar IO_REQUEST:

- RUNNING → BLOCKED
- ioRemainingTicks = ioServiceTicks
- log: “Proceso X bloqueado por I/O (Y ticks)”

### 10.3 Hilo de dispositivo (IODeviceThread)

- Decrementa ioRemainingTicks de procesos BLOCKED y BLOCKED_SUSPENDED
- Cuando llega a 0 publica IO_COMPLETE(pcbId)

Kernel al procesar IO_COMPLETE:

- BLOCKED → READY
- BLOCKED_SUSPENDED → READY_SUSPENDED
- log: “I/O completada para X”

### 10.4 Nota de interpretación (para la frase “retornar al procesador”)

Interpretación práctica: “La excepción/I/O ocurre en un proceso (PCB) y ese mismo PCB retorna luego a competir por CPU y reanuda su ejecución”.

No significa que el “mismo hilo del dispositivo” ejecute CPU; el “retorno al procesador” se cumple porque el mismo PCB vuelve a READY y eventualmente RUNNING.

---

## 11) Interrupciones externas e ISR (asíncrono)

### 11.1 Generación

InterruptGeneratorThread crea eventos:

- tipo (micrometeorito, tormenta solar, comando tierra, etc.)
- detectedTick
- isrCostTicks (1–5 ticks)
- acción opcional: crear proceso emergencia, ajustar prioridades

### 11.2 Manejo ISR en kernel

Al recibir interrupción:

- log: “Interrupción detectada: TIPO”
- latencia:
  - latency = globalTick - detectedTick
- entrar en modo kernel por isrCostTicks ticks:
  - durante ISR no se ejecuta instrucción de usuario
- log: “ISR terminada; latencia L ticks; servicio S ticks”
- si crea proceso emergencia:
  - entra a NEW y se admite con reglas de memoria

GUI muestra:

- CPU Mode: USER / KERNEL

---

## 12) Concurrencia (Threads + Semaphores) — Modelo mínimo viable

### 12.1 Threads

- KernelClockThread: único escritor de colas/estados.
- SimProcessThread por PCB: ejecuta 1 instrucción por permiso.
- IODeviceThread: maneja I/O y publica eventos.
- InterruptGeneratorThread: publica interrupciones.

### 12.2 Semáforos

- kernelLock (Semaphore(1)): protege colas, RUNNING, contadores, métricas internas.
- eventLock (Semaphore(1)): protege la cola de eventos.

Por proceso:

- runPermit (Semaphore(0))
- stepDone (Semaphore(0))

Reglas anti-deadlock (obligatorias):

- Solo el kernel toma kernelLock para mover PCBs.
- Hilos externos solo toman eventLock para publicar eventos.
- El kernel no espera stepDone sosteniendo kernelLock.

Idealmente el kernel procesa eventos así:

1. toma eventLock, drena eventos a estructura local/simple, suelta eventLock
2. toma kernelLock, aplica transiciones, suelta kernelLock

### 12.3 Handshake kernel ↔ proceso

Kernel por tick:

- elige RUNNING
- runPermit.release() (exactamente 1 tick)
- espera stepDone.acquire()

Proceso:

- runPermit.acquire()
- executeOneTick():
  - pc++, mar++, remainingInstructions--
  - si corresponde, publica IO_REQUEST
- stepDone.release()

---

## 13) GUI Swing obligatoria

### 13.1 Visualización mínima

Un JTable por cola:

- NEW, READY, RUNNING, BLOCKED, READY_SUSPENDED, BLOCKED_SUSPENDED, TERMINATED

Columnas:

- ID, Nombre, Estado, PC, MAR, Prioridad, RemainingInstr, RemainingDeadline
- RemainingDeadline = deadlineTick - globalTick

### 13.2 Modelo de tabla (sin DefaultTableModel)

No usar DefaultTableModel. Usar:

- AbstractTableModel personalizado
- backing data: PCB[] snapshot

### 13.3 Controles obligatorios

- Start / Pause / Step (opcional pero recomendado para demo)
- Selector algoritmo
- cycleDurationMs, quantumTicks, maxProcessesInMemory
- Botones: generar 20, agregar emergencia, load JSON, load CSV

### 13.4 Seguridad de hilo (Swing)

- Actualizar GUI solo en EDT con SwingUtilities.invokeLater(...).
- La GUI trabaja con snapshots del kernel.

---

## 14) Logging de eventos (requerido)

JTextArea con scroll con eventos clave:

- Interrupciones detectadas + latencia + ISR service
- Swaps a suspendidos + swap-in
- Deadline miss
- Policy switch + reorder READY
- Context switch
- I/O request + completion

Recomendado: buffer circular de strings para limitar crecimiento.

---

## 15) Deadlines y fail-soft

Cada tick:

- si globalTick > deadlineTick y el proceso no terminó:
  - deadlineMissed = true
  - log 1 vez: “Deadline miss…”

El proceso continúa, pero cuenta como fallo de misión.

---

## 16) Métricas y gráficas (evitar debates)

Contadores:

- userBusyTicks: ticks ejecutando instrucción de usuario
- osBusyTicks: ticks en ISR (y opcionalmente context-switch si lo modelan)
- idleTicks: sin RUNNING y sin ISR
- totalTicks = globalTick

Mostrar dos métricas (recomendado):

- Utilización total CPU: (userBusyTicks + osBusyTicks) / totalTicks
- Utilización user-only: userBusyTicks / totalTicks

Mission Success Rate:

- successCount: TERMINATED con endTick <= deadlineTick
- terminatedCount
- successRate = successCount / terminatedCount (si terminatedCount=0, mostrar 0 o “N/A”)

Throughput:

- terminatedCount / totalTicks (o por ventana)

Waiting time:

- promedio de readyWaitTicks de procesos terminados

### 16.2 Gráfica

- “CPU Utilization vs time”
- muestreo cada N ticks, guardar últimos M puntos (arreglo circular)

---

## 17) Carga desde archivos (JSON/CSV)

- CSV: parsing manual con BufferedReader (sin colecciones).
- JSON: librería que deserialice a arreglos (ProcessSpec[]), no List.
- Procesos cargados → NEW.

---

## 18) Eventos (tipos mínimos)

Para mantenerlo implementable:

- INTERRUPT(type, detectedTick, isrCostTicks, optionalAction)
- IO_REQUEST(pcbId)
- IO_COMPLETE(pcbId)
- CREATE_PROCESS(spec) (desde GUI / generador / periodic release)
- POLICY_CHANGE(newPolicy) (acción GUI)

---

## 19) Definición de “hecho”

El simulador es “completo” si:

- GUI muestra colas y PCBs en vivo por tick.
- “Generate 20 random” + “Add emergency” funcionan.
- Cambio de algoritmo sin reiniciar.
- Se observan suspendidos por memoria.
- Interrupciones preemptan y consumen ticks en ISR (modo USER/KERNEL visible).
- I/O bloquea y retorna (incluyendo suspendidos).
- Métricas y al menos 1 gráfica.
- Sin Java Collections Framework en código del estudiante.
