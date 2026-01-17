# Simulador RTOS (Microsatélite) — Especificación Inicial (v0.1)

Documento interno para que estemos en la misma página, sujeto a cambios.  
Proyecto: `Project1OS` (NetBeans + Java 23 + Maven).

## 1) Objetivo (qué estamos construyendo)
Simulador de un **Sistema Operativo de Tiempo Real (RTOS)** para un microsatélite, capaz de:
- Gestionar procesos periódicos y aperiódicos con **deadlines** y **prioridad**.
- Simular **planificación** (FCFS, RR, SRT, Prioridad Estática Preemptiva, EDF) con **cambio dinámico** de algoritmo.
- Simular **interrupciones/eventos** asíncronos mediante **Threads**, preemptando la CPU.
- Modelar **estados de proceso** completos (incluye suspendidos por memoria).
- Mostrar en **GUI** (obligatoria) colas + PCB + log + métricas + gráficas.

## 2) Restricciones obligatorias (del enunciado)
- **Lenguaje:** Java **21+** (nos quedamos en **Java 23**).
- **IDE:** NetBeans (la solución debe correr allí).
- **GUI obligatoria:** si es solo consola, 0 puntos.
- **Concurrencia obligatoria:** Threads + Semáforos (exclusión mutua).
- **Estructuras de datos:** prohibido usar colecciones de `java.util` (ArrayList/Queue/Stack/Vector y “cualquier colección del framework”).  
  => Implementar nuestras propias listas/colas.
- Librerías externas: solo para **gráficas** (ej. JFreeChart), **JSON/CSV**, **Threads/Semáforos**.

## 3) Decisiones de diseño acordadas (hasta ahora)
### 3.1 GUI
- Usar **Swing** (NetBeans GUI Builder/Matisse): `JFrame` + `JTable` + `JTextArea` + controles (`JComboBox`, botones, slider).
- Motivo: menos fricción de configuración que JavaFX y más probable que “corra en el profe” sin sorpresas.

### 3.2 Tiempo y deadlines
- Tener un reloj global `globalTick` (sube 1 por ciclo).
- **Deadline absoluto** por proceso: `deadlineTick` (tick exacto en el que debe estar terminado).
- La GUI muestra “cuenta regresiva” como: `deadlineTick - globalTick`.

### 3.3 Memoria (mediano plazo)
- La “memoria principal” se modela como: `maxProcesosEnMemoria` (configurable en GUI).
- Si el sistema supera ese máximo: el planificador de mediano plazo mueve procesos a:
  - `READY_SUSPENDED` o `BLOCKED_SUSPENDED`
- Regla base: **priorizar mantener en memoria los más críticos** (deadlines más cercanos).

## 4) Modelo de proceso (PCB) y estados
### 4.1 Estados (enum)
- `NEW`
- `READY`
- `RUNNING`
- `BLOCKED`
- `TERMINATED`
- `READY_SUSPENDED`
- `BLOCKED_SUSPENDED`

### 4.2 Campos mínimos sugeridos para `PCB`
Identidad/estado:
- `int id`
- `String name`
- `ProcessState state`

CPU/ejecución:
- `int pc` (incrementa 1 por ciclo)
- `int mar` (incrementa 1 por ciclo, mismo comportamiento que PC por simplicidad)
- `int totalInstructions`
- `int remainingInstructions`

Tiempo real:
- `int priority` (definir convención: por ejemplo, número más alto = más prioridad)
- `long arrivalTick`
- `long deadlineTick` (absoluto)

E/S (simplificado pero suficiente):
- `int ioEveryTicks` (cada cuántos ciclos dispara E/S; 0 = nunca)
- `int ioExceptionTicks` (cuánto tarda en “generar” la excepción)
- `int ioServiceTicks` (cuánto tarda en atenderse antes de volver a READY)
- `int ioRemainingTicks` (contador interno cuando está bloqueado)

Periódicos (si aplica):
- `boolean periodic`
- `int periodTicks`
- `long nextReleaseTick` (si modelamos “jobs” por periodo)

Métricas por proceso:
- `long readyWaitTicks` (acumulado en READY)
- `long startTick`, `long endTick` (para turnaround/throughput)
- `boolean deadlineMissed`

## 5) Políticas de planificación (qué decide el CPU)
Regla general: el planificador elige 1 proceso de `READY` para pasar a `RUNNING`.

### 5.1 FCFS (First Come First Served)
- Selección: el primero que llegó (FIFO).
- Normalmente no-preemptivo (en la práctica del simulador, podemos mantenerlo simple).

### 5.2 RR (Round Robin)
- Selección: FIFO, pero con **quantum** (ej: 3 ticks).
- Preemptivo: si el quantum se agota, el proceso vuelve al final de `READY`.

### 5.3 SRT (Shortest Remaining Time)
- Selección: menor `remainingInstructions`.
- Preemptivo: si aparece uno con menos restante que el actual, se preempta.

### 5.4 Prioridad Estática Preemptiva
- Selección: mayor prioridad (según convención).
- Preemptivo: si llega uno de mayor prioridad, preempta.

### 5.5 EDF (Earliest Deadline First)
- Selección: menor `deadlineTick` (deadline más cercano).
- Preemptivo: si entra uno con deadline menor al que corre, preempta.

### 5.6 Empates (para que la GUI no “parezca random”)
Definir un desempate estable; propuesta:
- EDF: `deadlineTick`, luego `priority`, luego `arrivalTick`, luego `id`.
- SRT: `remainingInstructions`, luego `deadlineTick`, luego `arrivalTick`, luego `id`.
- Prioridad: `priority`, luego `deadlineTick`, luego `arrivalTick`, luego `id`.

## 6) Estructuras de datos (sin java.util collections)
### 6.1 Ya implementado
- `ve.edu.unimet.so.proyecto1.datastructures.LinkedQueue<T>` (FIFO O(1) enqueue/dequeue).

### 6.2 Por implementar (mínimo viable)
- `SortedLinkedList<T>` (o `OrderedQueue<T>`): inserción ordenada O(n) para mantener `READY` ordenado en EDF/SRT/Prioridad.
  - Ventaja: simple y suficiente para 20–200 procesos.
  - `poll()` del “mejor” en O(1) (sacando head).

### 6.3 Necesidad para GUI
Las colas/listas deben permitir “ver su contenido” sin exponer nodos:
- `toArray()` (devuelve `Object[]`) o
- `forEach(Visitor<T>)`

## 7) Concurrencia (Threads + Semáforos)
Objetivo: cumplir requisito sin volverlo inmanejable.

### 7.1 Threads propuestos
- `KernelClockThread`: controla el `globalTick`, planificación, cambios de estado, métricas.
- `ProcessThread` por proceso: simula ejecución “1 instrucción por permiso”.
- `InterruptGeneratorThread`: genera eventos asíncronos (micro-meteorito, ráfaga solar, comando tierra).
- `IOServiceThread` (por evento de E/S o por proceso): maneja excepción/servicio y devuelve el proceso a `READY`.

### 7.2 Semáforos (exclusión + coordinación)
- Semáforo/mutex por estructura compartida (colas de estados, lista de terminados, log).
- Semáforos para “turnos” de CPU:
  - Kernel libera 1 “tick” al proceso seleccionado.
  - Proceso ejecuta 1 instrucción y devuelve control al kernel.

## 8) Interrupciones / ISR (simulación)
Comportamiento esperado:
- Un evento externo llega (Thread independiente).
- Se registra “Interrupción detectada”.
- Se suspende el proceso en CPU (preemption) y se ejecuta la rutina de servicio (modo kernel visible en GUI).
- Posible resultado: creación de un proceso de emergencia con deadline corto, o cambio de prioridad, etc.

## 9) Memoria y suspendidos (mediano plazo)
Regla general:
- Si `procesosEnMemoria >= maxProcesosEnMemoria`, no podemos admitir más sin suspender.

Heurística inicial propuesta (simple):
1) Nunca suspender `RUNNING`.
2) Preferir suspender de `READY` (si existe), si no, de `BLOCKED`.
3) Elegir candidato “menos crítico”: deadline más lejano; y si empata, menor prioridad.
4) Log: “Proceso X movido a Suspendido”.

## 10) GUI (pantalla mínima)
### 10.1 Visualización
- Un `JTable` por cola: NEW, READY, RUNNING (1 fila), BLOCKED, READY_SUSPENDED, BLOCKED_SUSPENDED, TERMINATED.
- Columnas recomendadas: `ID | Nombre | Estado | PC | MAR | Prioridad | InstrRestantes | DeadlineRestante`.
- `JTextArea` para log de eventos (con scroll).

### 10.2 Controles
- `Start / Pause / Step` (opcional Step para depurar).
- Selector de algoritmo: `FCFS | RR | SRT | PRIORITY | EDF`.
- Parámetros: `cycleDurationMs`, `quantumTicks`, `maxProcesosEnMemoria`.
- Botones:
  - “Generar 20 procesos aleatorios”
  - “Agregar proceso aleatorio (emergencia)”
  - “Cargar desde JSON/CSV”

## 11) Métricas
En tiempo real:
- Utilización CPU = `ticksCPUOcupado / ticksTotales`.
- Tasa de Éxito de Misión = `% procesos terminados antes del deadline`.
- Throughput = procesos terminados por ventana de tiempo o total/ticks.
- Tiempo de espera promedio = promedio de `readyWaitTicks`.

Gráficas:
- CPU utilization vs tiempo (JFreeChart u otra).

## 12) Git workflow (lo que pide el profesor)
Lo que piden NO es complicado: es básicamente un flujo tipo “equipo real”, esperemos que el github como tal no
tenga problemas como en sistemas de info.

### 12.1 Ramas
- `main`: siempre estable (lo que “se entrega”).
- `develop`: integración diaria.
- `feat/...`, `fix/...`: ramas de trabajo para PRs.

### 12.2 Cómo trabajamos (regla simple)
1) Cada tarea vive en una rama propia (ej: `feat/scheduler-edf`).
2) Se abre PR hacia `develop`.
3) Cuando `develop` está estable, PR de `develop` -> `main`.

### 12.3 Comandos típicos
Crear `develop` (una vez):
```bash
git checkout -b develop
git push -u origin develop
```

Crear rama por feature:
```bash
git checkout develop
git pull
git checkout -b feat/gui-queues
git push -u origin feat/gui-queues
```

esto es mas que nada para tenerlo a la mano pq siempre se me olvida

### 12.4 Commits
- Mensajes descriptivos: `feat: ...`, `fix: ...`, `docs: ...`
- Evitar commits gigantes; preferir pequeños y frecuentes.

## 13) Preguntas abiertas (para confirmar con profesores o preparadoras)
- ¿Qué rango esperan para `maxProcesosEnMemoria`? (si no lo dan, lo dejamos configurable)
- ¿Qué librería prefieren/permiten para JSON/CSV específicamente?
- ¿Qué hacer con un proceso que pierde deadline? (marcar “fallido y terminar”, o “fail-soft” y dejarlo correr)

## 14) Estado actual del repo (al 2026-01-17)
- Existe `LinkedQueue<T>` en `src/main/java/ve/edu/unimet/so/proyecto1/datastructures/LinkedQueue.java`.
- `main()` actual es solo prueba de cola; luego se reemplaza por arranque de GUI.

Lo mejor sera que estemos actualizando esto a medida que hacemos prs, para que tengamos un acceso facil a estar al dia,
por lo que antes de subir cambios al github hay que asegurarnos de actualizar este archivo con lo necesario, la version
la puse por ser fancy realmente pero puede ser util para llevar un control mas como de lo que estamos haciendo