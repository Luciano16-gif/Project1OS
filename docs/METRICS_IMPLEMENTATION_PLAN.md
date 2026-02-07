# Plan de Implementación: Panel de Métricas para GUI

## Objetivo
Agregar un panel de métricas obligatorias a la GUI existente sin perder funcionalidad actual.

## Métricas Requeridas (según especificación)

| Métrica | Fórmula | Descripción |
|---------|---------|-------------|
| **Tasa de éxito** | `successCount / terminatedCount` | % procesos terminados ANTES de deadline |
| **Throughput** | `terminatedCount / totalTicks` | Procesos completados por tick |
| **Tiempo espera promedio** | `Σ readyWaitTicks / terminatedCount` | Promedio de espera en READY |
| **Utilización CPU** | `(userBusyTicks + osBusyTicks) / totalTicks` | Uso efectivo del procesador |

---

## Diseño Propuesto

### Opción Elegida: Panel de Métricas en el Footer

Reorganizar el footer para incluir métricas + gráfica:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  HEADER: Controles + Clock                                                  │
├────────────────┬───────────────────┬────────────────────────────────────────┤
│  NEW + READY   │  CPU + Mem + Log  │   BLOCKED + TERMINATED                 │
│    (660px)     │     (300px)       │        (660px)                         │
├────────────────┴───────────────────┴────────────────────────────────────────┤
│  FOOTER:                                                                    │
│  ┌────────────────────────┬────────────────────────┬────────────────────────┐
│  │  READY_SUSPENDED       │   BLOCKED_SUSPENDED    │     METRICS PANEL      │
│  │      (tabla)           │      (tabla)           │  ┌──────────────────┐  │
│  │                        │                        │  │ Tasa: 85.5%      │  │
│  │                        │                        │  │ Thru: 0.042/tick │  │
│  │                        │                        │  │ Wait: 12.3 ticks │  │
│  │                        │                        │  │ CPU: 78.2%       │  │
│  │                        │                        │  │                  │  │
│  │                        │                        │  │ ┌──────────────┐ │  │
│  │                        │                        │  │ │  CPU Graph   │ │  │
│  │                        │                        │  │ └──────────────┘ │  │
│  └────────────────────────┴────────────────────────┴──────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Cambios en MainWindow.java

### 1. Nuevos Componentes

```java
// --- MÉTRICAS ---
private JLabel successRateLabel;    // "Success: 85.5%"
private JLabel throughputLabel;     // "Thru: 0.042/tick"
private JLabel avgWaitLabel;        // "Wait: 12.3 ticks"
private JLabel cpuUtilLabel;        // "CPU: 78.2%"
private CPUGraphPanel cpuGraphPanel; // Gráfica de utilización
```

### 2. Clase CPUGraphPanel (nuevo archivo)

Panel de gráfica que:
- Almacena últimos 100 puntos de utilización (arreglo circular de `double[]`)
- Dibuja línea de utilización vs tiempo
- Se actualiza cada N ticks (configurable)

### 3. Métodos de Actualización

```java
public void updateMetrics(double successRate, double throughput, 
                          double avgWait, double cpuUtil);
public void addCpuUtilDataPoint(double utilization);
```

---

## Cambios en GUIRealisticTest.java

### 1. Cálculo de Métricas

Agregar método `calculateMetrics()` que:
- Obtiene snapshot de TERMINATED
- Cuenta `successCount` (endTick <= deadlineTick)
- Suma `readyWaitTicks` de terminados
- Obtiene contadores del kernel (userBusyTicks, osBusyTicks, idleTicks)

### 2. Actualización Periódica

En `refreshGUI()`:
```java
// Cada tick, actualizar métricas
updateMetrics();
// Cada N ticks, agregar punto a gráfica
if (globalTick % 10 == 0) {
    window.addCpuUtilDataPoint(cpuUtil);
}
```

---

## Nuevos Archivos

| Archivo | Descripción |
|---------|-------------|
| `CPUGraphPanel.java` | Panel con gráfica de utilización usando arreglo circular |

---

## Orden de Implementación

1. [x] Diseñar estructura (este documento)
2. [x] Crear `CPUGraphPanel.java` con arreglo circular
3. [x] Agregar componentes de métricas a `MainWindow.java`
4. [x] Reorganizar `createFooter()` para incluir panel de métricas
5. [x] Agregar métodos `updateMetrics()` y `addCpuUtilDataPoint()`  
6. [x] Conectar cálculo de métricas en `GUIRealisticTest.java`
7. [x] Compilar y verificar

---

## Consideraciones

- **Sin librerías externas de gráficas**: Usamos `Graphics2D` nativo de Swing
- **Arreglo circular**: Cumple con restricción de no usar Java Collections
- **Buffer de 100 puntos**: Suficiente para ~10 segundos a 200ms/tick
