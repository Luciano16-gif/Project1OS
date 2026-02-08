/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package ve.edu.unimet.so.proyecto1;

import javax.swing.SwingUtilities;
import ve.edu.unimet.so.proyecto1.views.GUIRealisticTest;


/**
 *
 * @author
 */
public class Project1OS {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GUIRealisticTest app = new GUIRealisticTest();
            app.show();
        });
    }
}
