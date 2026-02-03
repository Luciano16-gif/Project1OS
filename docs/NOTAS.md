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
- [ ] **Deadline Miss:** Pendiente confirmar con la preparadora. Mientras tanto seguimos fail-soft (no se mata; solo se marca `deadlineMissed`).

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

## Nota de mantenimiento
Actualizar este archivo y ESPECIFICACION_PROYECTO.md al cerrar PRs importantes.

