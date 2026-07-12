package com.floydwarshall.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

import com.floydwarshall.model.Graph;

public class MatrixTableView {

    public interface CellEditListener {
        void onCellEdited(int i, int j, String newValue);
    }

    public interface CellSelectionListener {
        void onCellSelected(int i, int j);

        void onRowHeaderSelected(int i);

        void onColumnHeaderSelected(int j);

        void onSelectionCleared();
    }

    public static class Row {
        private final ObservableList<StringProperty> cells = FXCollections.observableArrayList();

        public Row(int n) {
            for (int k = 0; k < n; k++) {
                cells.add(new SimpleStringProperty(""));
            }
        }

        public ObservableList<StringProperty> getCells() {
            return cells;
        }
    }

    private static final StringConverter<String> IDENTITY = new StringConverter<>() {
        @Override
        public String toString(String s) {
            return s == null ? "" : s;
        }

        @Override
        public String fromString(String s) {
            return s;
        }
    };

    private final TableView<Row> table;
    private final boolean editable;
    private final List<Row> rows;

    private CellEditListener editListener;
    private CellSelectionListener selectionListener;

    // Подсветка алгоритма: (i,j), (i,k), (k,j)
    private int hlI = -1, hlJ = -1, hlK = -1;

    public MatrixTableView(boolean editable) {
        this.editable = editable;
        this.rows = new ArrayList<>();
        this.table = new TableView<>();
        this.table.setEditable(editable);
        this.table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        this.table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        this.table.setPrefHeight(220);
        this.table.setMinHeight(160);

        setupSelectionListener();
    }

    public void rebuild(Graph graph) {
        table.getColumns().clear();
        rows.clear();

        int n = graph.size();

        // Столбец с заголовками строк (номера вершин).
        TableColumn<Row, String> rowHeaderCol = new TableColumn<>("v \\ v");
        rowHeaderCol.setSortable(false);
        rowHeaderCol.setResizable(false);
        rowHeaderCol.setReorderable(false);
        rowHeaderCol.setPrefWidth(50);
        rowHeaderCol.setStyle("-fx-background-color: #eceff1; -fx-font-weight: bold;");
        rowHeaderCol.setCellValueFactory(cell -> {
            int idx = rows.indexOf(cell.getValue());
            return new SimpleStringProperty(idx >= 0 ? String.valueOf(idx) : "");
        });
        table.getColumns().add(rowHeaderCol);

        // Создаём строки данных.
        for (int i = 0; i < n; i++) {
            Row row = new Row(n);
            for (int j = 0; j < n; j++) {
                Integer v = graph.get(i, j);
                row.getCells().get(j).set((v == null) ? "inf" : String.valueOf(v));
            }
            rows.add(row);
        }

        // Столбцы вершин.
        for (int j = 0; j < n; j++) {
            final int colIndex = j;
            TableColumn<Row, String> col = new TableColumn<>(String.valueOf(j));
            col.setSortable(false);
            col.setResizable(false);
            col.setPrefWidth(56);
            col.setReorderable(false);
            col.setCellValueFactory(cell -> {
                Row r = cell.getValue();
                if (r == null || colIndex >= r.getCells().size()) {
                    return new SimpleStringProperty("");
                }
                return r.getCells().get(colIndex);
            });

            if (editable) {
                col.setCellFactory(tv -> makeEditableCell(colIndex));
                col.setOnEditCommit(e -> {
                    Row r = e.getRowValue();
                    int i = rows.indexOf(r);
                    if (i >= 0) {
                        r.getCells().get(colIndex).set(e.getNewValue());
                        if (editListener != null) {
                            editListener.onCellEdited(i, colIndex, e.getNewValue());
                        }
                    }
                });
            } else {
                col.setCellFactory(tv -> makeReadOnlyCell(colIndex));
            }
            table.getColumns().add(col);
        }

        ObservableList<Row> data = FXCollections.observableArrayList(rows);
        table.setItems(data);
        table.refresh();
    }

    private TableCell<Row, String> makeEditableCell(int j) {
        TextFieldTableCell<Row, String> cell = new TextFieldTableCell<>(IDENTITY) {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                int i = getIndex();
                applyHighlight(this, i, j);
            }
        };

        // Клик по ячейке → уведомление о выделении.
        cell.setOnMousePressed(e -> {
            int i = cell.getIndex();
            if (i >= 0 && i < rows.size() && selectionListener != null) {
                selectionListener.onCellSelected(i, j);
            }
        });

        // Диагональ не редактируется.
        cell.indexProperty().addListener((obs, oldI, newI) -> {
            int i = newI.intValue();
            cell.setEditable(editable && i != j);
        });

        return cell;
    }

    private TableCell<Row, String> makeReadOnlyCell(int j) {
        return new TableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                int i = getIndex();
                applyHighlight(this, i, j);
            }
        };
    }

    private void applyHighlight(TableCell<Row, String> cell, int i, int j) {
        if (i < 0 || j < 0)
            return;
        boolean isAlgCell = (i == hlI && j == hlJ) ||
                (i == hlI && j == hlK) ||
                (i == hlK && j == hlJ);
        boolean isDiag = (i == j);
        if (isAlgCell) {
            cell.setStyle("-fx-background-color: #fff176; -fx-font-weight: bold;");
        } else if (isDiag) {
            cell.setStyle("-fx-background-color: #eceff1; -fx-text-fill: #90a4ae;");
        } else {
            cell.setStyle("");
        }
    }

    public void setAlgorithmHighlight(int i, int j, int k) {
        this.hlI = i;
        this.hlJ = j;
        this.hlK = k;
        table.refresh();
    }

    public void clearAlgorithmHighlight() {
        this.hlI = -1;
        this.hlJ = -1;
        this.hlK = -1;
        table.refresh();
    }

    private void setupSelectionListener() {
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (selectionListener == null)
                return;
            if (sel == null) {
                selectionListener.onSelectionCleared();
                return;
            }
            int i = rows.indexOf(sel);
            int j = table.getFocusModel().getFocusedCell().getColumn() - 1;
            if (i >= 0) {
                if (j >= 0) {
                    selectionListener.onCellSelected(i, j);
                } else {
                    selectionListener.onRowHeaderSelected(i);
                }
            }
        });
    }

    public TableView<Row> getNode() {
        return table;
    }

    public void setEditListener(CellEditListener l) {
        this.editListener = l;
    }

    public void setSelectionListener(CellSelectionListener l) {
        this.selectionListener = l;
    }

    public void clearSelectionSync() {
        table.getSelectionModel().clearSelection();
    }

    public Integer[][] toMatrix() {
        int n = rows.size();
        Integer[][] m = new Integer[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                String s = rows.get(i).getCells().get(j).get().trim();
                if (s.isEmpty() || s.equalsIgnoreCase("inf")) {
                    m[i][j] = null;
                } else {
                    try {
                        m[i][j] = Integer.parseInt(s);
                    } catch (NumberFormatException ex) {
                        m[i][j] = null;
                    }
                }
            }
        }
        return m;
    }
}
