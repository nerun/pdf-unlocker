/*
 * PDF Unlocker — table model backing the file list shown in the main window.
 * Copyright 2026 Daniel Dias Rodrigues <danieldiasr@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pdfunlocker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import org.pdfunlocker.i18n.I18n;

final class PdfTableModel extends AbstractTableModel {

    static final int COL_NAME = 0;
    static final int COL_SIZE = 1;
    static final int COL_PASSWORD = 2;
    static final int COL_STATUS = 3;

    private static final String[] COLUMNS = {
        I18n.t("column.file"), I18n.t("column.size"), I18n.t("column.password"),
        I18n.t("column.status")
    };

    private final List<PdfEntry> entries = new ArrayList<>();

    PdfEntry get(int row) {
        return entries.get(row);
    }

    List<PdfEntry> entries() {
        return List.copyOf(entries);
    }

    /** @return false if the file is already in the list */
    boolean add(Path path, long size) {
        for (PdfEntry e : entries) {
            if (e.path.equals(path)) {
                return false;
            }
        }
        entries.add(new PdfEntry(path, size));
        int row = entries.size() - 1;
        fireTableRowsInserted(row, row);
        return true;
    }

    void remove(int[] rows) {
        int[] sorted = rows.clone();
        java.util.Arrays.sort(sorted);
        for (int i = sorted.length - 1; i >= 0; i--) {
            entries.remove(sorted[i]);
            fireTableRowsDeleted(sorted[i], sorted[i]);
        }
    }

    void clear() {
        int n = entries.size();
        if (n > 0) {
            entries.clear();
            fireTableRowsDeleted(0, n - 1);
        }
    }

    void entryChanged(PdfEntry entry) {
        int row = entries.indexOf(entry);
        if (row >= 0) {
            fireTableRowsUpdated(row, row);
        }
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column == COL_PASSWORD;
    }

    @Override
    public Object getValueAt(int row, int column) {
        PdfEntry e = entries.get(row);
        return switch (column) {
            case COL_NAME -> e.fileName();
            case COL_SIZE -> formatSize(e.size);
            case COL_PASSWORD -> e.password;
            case COL_STATUS -> e.state.label();
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column == COL_PASSWORD) {
            PdfEntry e = entries.get(row);
            e.password = value == null ? "" : value.toString();
            if (e.state == PdfEntry.State.NEEDS_PASSWORD || e.state == PdfEntry.State.ERROR) {
                e.state = PdfEntry.State.PENDING;
                e.message = "";
            }
            fireTableRowsUpdated(row, row);
        }
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.0f KB", kb);
        }
        return String.format("%.1f MB", kb / 1024.0);
    }
}
