# NOTAS

Notas internas del proyecto.

## Git workflow (lo que pide el profesor)
Lo que piden NO es complicado: es basicamente un flujo tipo "equipo real".

### Ramas
- `main`: siempre estable (lo que "se entrega").
- `develop`: integracion diaria.
- `feat/...`, `fix/...`, `docs/...`: ramas de trabajo para PRs.

### Como trabajamos (regla simple)
1) Cada tarea vive en una rama propia (ej: `feat/scheduler-edf`).
2) Se abre PR hacia `develop`.
3) Cuando `develop` esta estable, PR de `develop` -> `main`.

### Comandos tipicos
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

### Commits
- Mensajes descriptivos: `feat: ...`, `fix: ...`, `docs: ...`
- Evitar commits gigantes; preferir pequeños y frecuentes.

## Preguntas abiertas / Dudas
- [x] **Rango de memoria:** Decidido por el equipo. Sugerencia inicial: `maxProcessesInMemory` configurable con default bajo (6–8) para forzar swapping.
- [x] **Carga JSON/CSV:** Ya no es requisito. El sistema debe iniciar con procesos generados automáticamente.
- [ ] **Deadline Miss:** Pendiente confirmar con la preparadora. Mientras tanto seguimos fail-soft (no se mata; solo se marca `deadlineMissed`).

## Estado actual del repo (al 2026-01-19)
**Estructuras de Datos:**
- [x] Propias: `LinkedQueue`, `SimpleList`, `OrderedList`.
- [x] Tests básicos (`DataStructuresTest`) pasando.

**Modelos y Kernel:**
- [x] `PCB` base (identidad, PC/MAR, prioridad, arrival/deadline, I/O simple).
- [x] `ProcessState` (7 estados incluyendo suspendidos).
- [ ] `PeriodicTaskTemplate` (no existe en el código actual).
- [x] `OperatingSystem` base:
    - Cola FIFO para FCFS/RR y lista ordenada para SRT/EDF/Prioridad.
    - Cambio dinámico de algoritmo (reordena la cola READY).
    - Aún no maneja admisión NEW, swapping, I/O real ni preemptividad en SRT/EDF/Prioridad.

**Pendiente (Siguientes pasos):**
- [ ] Crear `ClockThread` (el motor que llama a `executeOneCycle`).
- [ ] Interfaz Gráfica (GUI) para ver esto funcionando.

## Plan de correcciones (scheduler y base)
Objetivo: arreglar el codigo actual para que sea correcto, mantenible y listo para seguir creciendo sin rework.

### Flujo de ramas recomendado
- Crear una rama dedicada para estos arreglos (ej: `fix/scheduler-preemption` o `refactor/scheduler-core`).
- Trabajar ahi y hacer PR hacia `feat/process-model`.
- Cuando este validado, PR de `feat/process-model` -> `develop`.
- Esto deja trazabilidad clara y evita mezclar arreglos con otras features.

### 1) Refactor de politicas: usar `enum` en vez de `int`
**Problema:** `currentAlgorithm` como `int` permite valores invalidos y rompe exhaustividad en `switch`.
**Solucion:** crear un `enum SchedulingPolicy` y usarlo en todo el kernel.

Snippet sugerido:
```java
public enum SchedulingPolicy { FCFS, RR, SRT, PRIORITY, EDF }
```

Cambios recomendados:
- Reemplazar `ALG_*` por `SchedulingPolicy`.
- `private SchedulingPolicy currentPolicy;`
- `setAlgorithm(SchedulingPolicy policy)` y actualizar el `switch`.
- En GUI, mapear el combo directamente a `SchedulingPolicy`.

### 2) Preemptividad real en SRT / PRIORITY / EDF
**Problema:** actualmente solo hay cambio de proceso cuando CPU queda libre o por quantum (RR).  
**Solucion:** en cada tick, antes de ejecutar instruccion, comparar CPU vs mejor READY.

Snippet sugerido (dentro de `executeOneCycle()`):
```java
if (cpu == null) {
    scheduleNextProcess();
} else if (isPreemptivePolicy()) {
    PCB bestReady = readyListSorted.peekFirst();
    if (bestReady != null && getComparator().compare(bestReady, cpu) < 0) {
        preemptCurrentProcess();
        scheduleNextProcess();
    }
}
```

Helpers recomendados:
```java
private boolean isPreemptivePolicy() {
    return currentPolicy == SchedulingPolicy.SRT
        || currentPolicy == SchedulingPolicy.PRIORITY
        || currentPolicy == SchedulingPolicy.EDF;
}

private Compare.Comparator<PCB> getComparator() {
    return switch (currentPolicy) {
        case PRIORITY -> priorityComparator;
        case EDF -> edfComparator;
        default -> srtComparator;
    };
}
```

### 3) Comparadores con desempates (tie-breakers) estables
**Problema:** comparadores actuales solo usan 1 campo; empates dan orden no determinista.  
**Solucion:** encadenar comparaciones segun la especificacion (deadline/priority/arrival/id).

Ejemplo EDF (deadline asc, priority desc, arrival asc, pid asc):
```java
private int compareEdf(PCB a, PCB b) {
    int c = Long.compare(a.getDeadlineTick(), b.getDeadlineTick());
    if (c != 0) return c;
    c = Integer.compare(b.getPriority(), a.getPriority());
    if (c != 0) return c;
    c = Long.compare(a.getArrivalTick(), b.getArrivalTick());
    if (c != 0) return c;
    return Integer.compare(a.getPid(), b.getPid());
}
```

### 4) Cambio dinamico de algoritmo respetando FIFO real
**Problema:** al cambiar de SRT/EDF/PRIORITY -> FCFS/RR, el orden FIFO queda sesgado.  
**Solucion:** al pasar a FCFS/RR, reconstruir FIFO usando `arrivalTick` y `pid`.

Idea sin usar Collections:
1) Drenar READY a `SimpleList`.
2) Insertar en un `OrderedList<PCB>` con comparador por arrival/pid.
3) Pasar del OrderedList a `readyQueueFIFO` en orden.

Comparador sugerido:
```java
private final Compare.Comparator<PCB> fifoComparator =
    (a, b) -> {
        int c = Long.compare(a.getArrivalTick(), b.getArrivalTick());
        return (c != 0) ? c : Integer.compare(a.getPid(), b.getPid());
    };
```

### 5) Checklist de validacion rapida (manual)
- SRT: un proceso corto nuevo debe preemptar al largo en el siguiente tick.
- PRIORITY: uno de mayor prioridad debe preemptar a uno menor.
- EDF: el de deadline mas cercano debe preemptar.
- Cambio de politica: al pasar a FCFS, la cola queda en orden de `arrivalTick`.

## Nota de mantenimiento
Actualizar este archivo y ESPECIFICACION_PROYECTO.md al cerrar PRs importantes.

