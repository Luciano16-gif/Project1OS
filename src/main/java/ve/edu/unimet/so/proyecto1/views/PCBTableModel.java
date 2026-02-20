/*
 * PCBTableModel.java
 * Modelo de tabla personalizado basado en AbstractTableModel
 * Cumple con la especificación de NO usar DefaultTableModel
 */
package ve.edu.unimet.so.proyecto1.views;

import javax.swing.table.AbstractTableModel;
import ve.edu.unimet.so.proyecto1.models.PCB;

public class PCBTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {
            "ID", "Name", "State", "PC", "MAR", "Prio", "Remaining", "Deadline"
    };

    // Backing data: arreglo simple de filas (Object[])
    private Object[][] data;
    private int rowCount;
    private static final int INITIAL_CAPACITY = 50;

    public PCBTableModel() {
        this.data = new Object[INITIAL_CAPACITY][COLUMN_NAMES.length];
        this.rowCount = 0;
    }

    @Override
    public int getRowCount() {
        return rowCount;
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= rowCount)
            return null;
        if (columnIndex < 0 || columnIndex >= COLUMN_NAMES.length)
            return null;
        return data[rowIndex][columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false; // Las celdas no son editables
    }

    /**
     * Actualiza el modelo con un array de PCBs (desde snapshot)
     */
    public void updateFromSnapshot(PCB[] snapshot, long globalTick) {
        if (snapshot == null) {
            clear();
            return;
        }

        // Asegurar capacidad
        if (snapshot.length > data.length) {
            data = new Object[snapshot.length + 10][COLUMN_NAMES.length];
        }

        // Llenar datos
        for (int i = 0; i < snapshot.length; i++) {
            PCB p = snapshot[i];
            if (p != null) {
                data[i][0] = p.getPid();
                data[i][1] = p.getName();
                data[i][2] = p.getState().name();
                data[i][3] = p.getProgramCounter();
                data[i][4] = p.getMar();
                data[i][5] = p.getPriority();
                data[i][6] = p.getRemainingInstructions();
                data[i][7] = p.getDeadlineRemaining(globalTick);
            }
        }

        rowCount = snapshot.length;
        fireTableDataChanged();
    }

    /**
     * Actualiza el modelo con un array de filas ya formateadas
     */
    public void updateFromRows(Object[][] rows) {
        if (rows == null || rows.length == 0) {
            clear();
            return;
        }

        // Asegurar capacidad
        if (rows.length > data.length) {
            data = new Object[rows.length + 10][COLUMN_NAMES.length];
        }

        // Copiar datos
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] != null) {
                int cols = Math.min(rows[i].length, COLUMN_NAMES.length);
                for (int j = 0; j < cols; j++) {
                    data[i][j] = rows[i][j];
                }
            }
        }

        rowCount = rows.length;
        fireTableDataChanged();
    }

    /**
     * Limpia el modelo
     */
    public void clear() {
        rowCount = 0;
        fireTableDataChanged();
    }

    /**
     * Agrega una fila al modelo
     */
    public void addRow(Object[] row) {
        // Asegurar capacidad
        if (rowCount >= data.length) {
            Object[][] newData = new Object[data.length * 2][COLUMN_NAMES.length];
            for (int i = 0; i < data.length; i++) {
                newData[i] = data[i];
            }
            data = newData;
        }

        if (row != null) {
            int cols = Math.min(row.length, COLUMN_NAMES.length);
            for (int j = 0; j < cols; j++) {
                data[rowCount][j] = row[j];
            }
        }
        rowCount++;
        fireTableRowsInserted(rowCount - 1, rowCount - 1);
    }
}
