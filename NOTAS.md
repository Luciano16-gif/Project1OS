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

## Nota de mantenimiento
Actualizar este archivo y ESPECIFICACION_PROYECTO.md al cerrar PRs importantes.

