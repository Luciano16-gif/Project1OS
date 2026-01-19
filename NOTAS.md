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
- [ ] **Rango de memoria:** Que rango esperan para `maxProcesosEnMemoria`? (Pendiente preguntar. Por defecto usamos 5).
- [ ] **Libreria JSON:** ¿Cuál prefieren? (Probablemente usaremos una simple como `org.json` o parseo manual si se ponen estrictos).
- [x] **Deadline Miss:** ¿Matar o seguir? -> **Resuelto:** La Spec v0.2.1 define "Fail-soft". El proceso sigue, solo se marca el flag `deadlineMissed`.

## Estado actual del repo (al 2026-01-18 - v0.2.1)
**Estructuras de Datos:**
- [x] Propias: `LinkedQueue`, `SimpleList`, `OrderedList`.
- [x] Tests básicos (`DataStructuresTest`) pasando.

**Modelos y Kernel:**
- [x] `PCB` actualizado (campos de I/O, métricas, deadline flags).
- [x] `ProcessState` (7 estados incluyendo suspendidos).
- [x] `PeriodicTaskTemplate` para generar trabajos repetitivos.
- [x] `OperatingSystem` (Kernel v0.2.1):
    - Admisión de procesos (New -> Ready o New -> Suspended).
    - Lógica de Scheduler (FCFS, RR, SRT, EDF, Prioridad).
    - Cambio dinámico de algoritmos (reordenamiento de colas).
    - Detección de bloqueos por I/O.

**Pendiente (Siguientes pasos):**
- [ ] Crear `ClockThread` (el motor que llama a `executeOneCycle`).
- [ ] Interfaz Gráfica (GUI) para ver esto funcionando.

## Nota de mantenimiento
Actualizar este archivo y ESPECIFICACION_PROYECTO.md al cerrar PRs importantes.
```