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

Eliminar rama local:
```bash
git branch -d nombre-rama
```

Eliminar rama remota:
```bash
git push origin --delete nombre-rama
```

### Commits
- Mensajes descriptivos: `feat: ...`, `fix: ...`, `docs: ...`
- Evitar commits gigantes; preferir pequeños y frecuentes.

## Preguntas abiertas / Dudas
- [x] **Rango de memoria:** Decidido por el equipo. Sugerencia inicial: `maxProcessesInMemory` configurable con default bajo (6–8) para forzar swapping.
- [x] **Carga JSON/CSV:** Ya no es requisito. El sistema debe iniciar con procesos generados automáticamente.
- [x] **Deadline Miss:** Definido como fail-soft con recuperacion (el proceso no se mata; se marca `deadlineMissed` y se aplica politica de recuperacion).

## Decisiones cerradas (deadline/recovery)
- La prioridad mostrada/operativa en runtime es la **prioridad efectiva** (`effectivePriority`).
- La prioridad original de configuracion se mantiene como **prioridad base** (`basePriority`).
- En recovery actual **no hay decaimiento** del boost (no se baja automaticamente despues).
- El deadline se sigue evaluando desde `arrivalTick` (incluye espera en `NEW`).

## Estado actual del repo (al 2026-02-02)
**Estructuras de Datos:**
- [x] Propias: `LinkedQueue`, `SimpleList`, `OrderedList`.
- [x] Tests básicos (`DataStructuresTest`) pasando.

**Modelos y Kernel:**
- [x] `PCB` actualizado (I/O por countdown: `ioEveryTicks`, `ioTriggerCountdown`, `ioServiceTicks`, `ioRemainingTicks`).
- [x] `ProcessState` (7 estados incluyendo suspendidos).
- [ ] `PeriodicTaskTemplate` (no existe en el código actual).
- [x] `OperatingSystem` con:
    - Preemptividad correcta (SRT/PRIORITY/EDF) + RR por quantum.
    - Reordenamiento READY con tie-breakers.
    - Event queue + logging básico.
- [x] `MemoryManager`:
    - Admisión NEW → READY/SUSPENDED.
    - Swap-out/in con criterios de criticidad.
- [x] I/O core:
    - IO_REQUEST / IO_COMPLETE.
    - IODevice tick (clock-driven).
- [x] Interrupciones core:
    - Eventos INT y ticks de ISR (clock-driven).
- [x] `ClockThread` creado (driver principal).

**Pendiente (Siguientes pasos):**
- [ ] Completar GUI (reemplazar placeholders y cumplir spec).
- [ ] Conectar `ClockThread` a controles GUI (Start/Pause/Step) y ciclo configurable.
- [ ] Conectar snapshots a tablas GUI (NEW/READY/RUNNING/BLOCKED/TERMINATED + suspendidos).
- [ ] Panel de log (usar `snapshotEventLog()`).
- [ ] Indicador GUI de modo CPU (USER/KERNEL) durante ISR.
- [ ] Validaciones de inputs GUI (cycleDurationMs, quantum, maxProcessesInMemory).

## Handoff GUI, especificaciones y correcciones
- Evitar `DefaultTableModel`; usar `AbstractTableModel` con snapshots (`PCB[]`).
- El kernel ya expone snapshots para colas y logs (`snapshotEventLog()`).
- `ClockThread` debe ser el motor único (no usar `Thread.sleep` en GUI).

## Bug fixes pendientes (rama `fix/general_bug_fixes`)
Ir borrando (o marcar `[x]`) a medida que se cierre cada punto.

### P1 (alta prioridad)
- [x] **Métrica de CPU subcontada cuando el clock va mas rapido que el refresh de GUI.**  
      Hoy el conteo de CPU busy depende del timer de GUI (100ms), no del tick real del kernel.  
      Referencias: `src/main/java/ve/edu/unimet/so/proyecto1/views/GUIRealisticTest.java`
- [x] **`waitingTime` nunca incrementa.**  
      `avgWait` sale incorrecto porque no se actualiza el acumulador de espera en READY/READY_SUSPENDED.  
      Referencias: `src/main/java/ve/edu/unimet/so/proyecto1/models/PCB.java`, `src/main/java/ve/edu/unimet/so/proyecto1/kernel/OperatingSystem.java`, `src/main/java/ve/edu/unimet/so/proyecto1/views/GUIRealisticTest.java`
- [x] **Mutacion de PCB desde `IODeviceThread` fuera del lock central del kernel.**  
      Riesgo de condiciones de carrera al decrementar `ioRemainingTicks` desde otro thread.  
      Referencias: `src/main/java/ve/edu/unimet/so/proyecto1/kernel/IODeviceThread.java`

### P2 (media prioridad)
- [x] **`ClockThread` no reinicia limpio despues de `stopClock()`.**  
      `started` queda en `true`; revisar semantica para permitir restart seguro o bloquearlo explicitamente.  
      Referencia: `src/main/java/ve/edu/unimet/so/proyecto1/kernel/ClockThread.java`
- [x] **Inconsistencia UX de `STEP` al inicio.**  
      El boton aparece habilitado pero no hace nada hasta pausar.  
      Referencias: `src/main/java/ve/edu/unimet/so/proyecto1/views/GUIRealisticTest.java`, `src/main/java/ve/edu/unimet/so/proyecto1/kernel/ClockThread.java`
- [x] **Faltan validaciones duras en configuracion de kernel.**  
      Validar rangos en `setQuantum(...)` y `setMaxProcessesInMemory(...)` para evitar valores invalidos.  
      Referencias: `src/main/java/ve/edu/unimet/so/proyecto1/kernel/OperatingSystem.java`, `src/main/java/ve/edu/unimet/so/proyecto1/kernel/MemoryManager.java`
- [x] **Snapshots exponen `PCB` mutable directamente a GUI.**  
      Riesgo de lecturas inconsistentes de datos cuando cambian durante render.  
      Referencias: `src/main/java/ve/edu/unimet/so/proyecto1/kernel/OperatingSystem.java`, `src/main/java/ve/edu/unimet/so/proyecto1/views/PCBTableModel.java`

## Nota de mantenimiento
Actualizar este archivo y ESPECIFICACION_PROYECTO.md al cerrar PRs importantes.

