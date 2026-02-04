# Tarea: Crear Pruebas Realistas de Procesos para la GUI

## Objetivo
Crear un nuevo archivo de pruebas (`GUIRealisticTest.java`) que demuestre el funcionamiento del simulador RTOS conectando la GUI con el kernel real, usando el `ClockThread` como motor único.

## Fases de Implementación

### Fase 1: Preparación y Estructura Base
- [x] Revisar documentación (`GUI_SPECIFICATIONS.md`, `SPECIFICACION_PROYECTO.md`, `NOTAS.md`)
- [x] Revisar código existente (`MainWindow.java`, `GUITest.java`, `ClockThread.java`)
- [x] Revisar kernel (`OperatingSystem.java`, `PCB.java`, `MemoryManager.java`)
- [x] Revisar dispositivos (`IODeviceThread.java`, `InterruptGeneratorThread.java`)
- [x] Crear plan de implementación

### Fase 2: Actualizar MainWindow para usar snapshots
- [x] Cambiar `DefaultTableModel` por `AbstractTableModel` personalizado (`PCBTableModel.java`)
- [x] Agregar tablas para todos los estados (NEW, RUNNING, TERMINATED)
- [x] Agregar panel de log de eventos
- [x] Agregar indicador de modo CPU (USER/KERNEL)

### Fase 3: Crear GUIRealisticTest.java
- [x] Setup inicial con `OperatingSystem` y `ClockThread`
- [x] Generador de procesos aleatorios con PCB nuevo
- [x] Loop de actualización usando snapshots reales
- [x] Conexión de controles (Start/Pause/Step) - botones en GUI

### Fase 4: Escenarios de Prueba
- [x] Escenario 1: Procesos básicos sin I/O
- [x] Escenario 2: Procesos con I/O y bloqueo
- [x] Escenario 3: Memoria llena y swapping
- [x] Escenario 4: Interrupciones y modo KERNEL
- [x] Escenario 5: Cambio de algoritmo en ejecución

### Fase 5: Verificación
- [ ] Compilar y ejecutar sin errores
- [ ] Verificar visualización de todas las colas
- [ ] Verificar log de eventos
- [ ] Verificar métricas básicas
