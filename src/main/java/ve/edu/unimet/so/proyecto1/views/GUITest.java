package ve.edu.unimet.so.proyecto1.views;

import javax.swing.SwingUtilities;

public class GUITest {

    public static void main(String[] args) {
        // 1. Crear la ventana
        MainWindow window = new MainWindow();
        window.setVisible(true);

        // 2. Hilo de simulación
        new Thread(() -> {
            try {
                System.out.println("Iniciando simulación de prueba...");
                
                // Vamos a correr 100 ciclos
                for (int tick = 1; tick <= 100; tick++) {
                    final int currentTick = tick;

                    SwingUtilities.invokeLater(() -> {
                        
                        // Actualizar reloj siempre
                        window.updateClock(currentTick);

                        // --- FASE 1: INICIO (Llenando RAM) ---
                        if (currentTick == 2) {
                            window.addRowToReady(new Object[]{"1", "System_Boot", "99"});
                            window.updateMemory(10);
                        }
                        if (currentTick == 10) {
                            window.addRowToReady(new Object[]{"2", "Telemetry_Svc", "50"});
                            window.addRowToReady(new Object[]{"3", "Cam_Capture", "40"});
                            window.updateMemory(30);
                        }

                        // --- FASE 2: EJECUCIÓN CPU ---
                        if (currentTick >= 15 && currentTick <= 40) {
                            // Simulamos que el proceso 1 está corriendo
                            int progress = currentTick - 15;
                            window.updateCPU("System_Boot", progress, 25);
                        }

                        // --- FASE 3: I/O Y MEMORIA CRÍTICA ---
                        if (currentTick == 41) {
                            window.updateCPU(null, 0, 0); // CPU libre
                            window.addRowToBlocked(new Object[]{"1", "System_Boot", "Waiting I/O"});
                            window.updateMemory(85); // ¡Alerta roja!
                        }

                        // --- FASE 4: SWAPPING (Usando la parte de abajo) ---
                        // Simulamos que llegan procesos nuevos pero no caben en RAM
                        
                        if (currentTick == 50) {
                            // Proceso pesado va directo a Disco (Ready-Suspended)
                            window.addRowToReadySuspended(new Object[]{"4", "Heavy_Analysis", "10"});
                            window.updateMemory(95);
                        }
                        
                        if (currentTick == 60) {
                            // Otro proceso va al disco
                            window.addRowToReadySuspended(new Object[]{"5", "Img_Processing", "20"});
                            window.updateMemory(100); // Memoria llena
                        }

                        if (currentTick == 70) {
                            // Un proceso que estaba bloqueado en RAM, lo mandamos a Disco (Blocked-Suspended)
                            // para liberar espacio
                            window.addRowToBlockedSuspended(new Object[]{"6", "Low_Prio_Logs", "Disk Write"});
                            // Al moverlo bajamos un poco la RAM
                            window.updateMemory(90); 
                        }
                    });

                    // Velocidad de la simulación (más rápido para no aburrirte)
                    Thread.sleep(150);
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}