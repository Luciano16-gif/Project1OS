# GUI especificaciones / Guía de integración

Este archivo explica cómo conectar la GUI con el kernel actual.

## 1) Regla numero 1: ClockThread es el único motor
- **No** usar `Thread.sleep` en la GUI para avanzar la simulación.
- Inicia un `ClockThread` y deja que llame a `OperatingSystem.executeOneCycle()`.
- Para "Step", usar `ClockThread.stepOnce()`.

## 2) Fuentes de datos para las tablas (snapshots)
Usar los métodos de `OperatingSystem`:

- `snapshotNew()`
- `snapshotReady()`
- `snapshotRunning()`
- `snapshotBlocked()`
- `snapshotReadySuspended()`
- `snapshotBlockedSuspended()`
- `snapshotTerminated()`
- `snapshotEventLog()`

Cada snapshot devuelve `PCB[]` (o `String[]` para logs), listo para convertir a filas.

### Helper para filas de tabla
Usar:

```
os.pcbToRow(pcb)
```

Devuelve:
```
ID | Name | State | PC | MAR | Priority | RemainingInstr | RemainingDeadline
```

## 3) Tablas GUI (sin DefaultTableModel)
La spec prohíbe `DefaultTableModel`.
Usar `AbstractTableModel` con backing `PCB[]` (snapshot).

Columnas sugeridas:
```
ID, Nombre, Estado, PC, MAR, Prioridad, RemainingInstr, RemainingDeadline
```

## 4) Indicador de modo CPU
Usar:
```
os.isInKernelMode()
```

Si es true → mostrar KERNEL (ISR activa). Si es false → USER.

## 5) Panel de log
Usar:
```
os.snapshotEventLog()
```

Retorna las últimas entradas (máx 200).

## 6) Wiring Start/Pause/Step
Recomendado:
- Start -> `clock.startClock()`
- Pause -> `clock.pauseClock()`
- Resume -> `clock.resumeClock()`
- Step -> `clock.stepOnce()` (solo si está pausado)
- Stop -> `clock.stopClock()`

Duración del ciclo:
```
clock.setCycleDurationMs(valor)
```

## 7) Notas importantes del kernel
- **I/O es por countdown**: `ioEveryTicks`, `ioServiceTicks`.
- **I/O + Interrupts** son controlados por el ClockThread (no por loops independientes).
- `ClockThread` llama ambos ticks en cada ciclo.

## 8) Constructor de PCB cambió
Ahora es:
```
PCB(pid, name, totalInstructions, priority, arrivalTick, deadlineTick, ioEveryTicks, ioServiceTicks)
```

Actualizar cualquier generador/GUI que use el constructor antiguo.

## 9) Pendiente en GUI
- Reemplazar placeholders en `views/` con snapshots reales.
- Añadir tablas para **NEW**, **READY**, **RUNNING**, **BLOCKED**, **TERMINATED** y suspendidos.
- Panel de log e indicador CPU.
- Validaciones para `cycleDurationMs`, `quantum`, `maxProcessesInMemory`.

