package com.floydwarshall.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseEvent;
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
        private final int index;
        private final ObservableList<StringProperty> cells = FXCollections.observableArrayList();

        public Row(int index, int n) {
            this.index = index;
            for (int k = 0; k < n; k++) {
                cells.add(new SimpleStringProperty(""));
            }
        }

        public int getIndex() {
            return index;
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

    // Алгоритмическая подсветка
    private int hlI = -1, hlJ = -1, hlK = -1;
    private boolean hlWasUpdate = false;

    // Пользовательское выделение
    private int selectedVertex = -1;
    private int selectedEdgeFrom = -1, selectedEdgeTo = -1;
    private int selectedRow = -1;
    private int selectedCol = -1;

    private final List<Label> colHeaderLabels = new ArrayList<>();
    private boolean isRebuilding = false;

    public MatrixTableView(boolean editable) {
        this.editable = editable;
        this.rows = new ArrayList<>();
        this.table = new TableView<>();
        this.table.setEditable(editable);
        this.table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        this.table.getSelectionModel().setCellSelectionEnabled(true);
        this.table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        this.table.setPrefHeight(220);
        this.table.setMinHeight(160);
        setupSelectionListener();
        setupEmptyAreaClickHandler();
    }

    private void setupEmptyAreaClickHandler() {
        table.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (isRebuilding || selectionListener == null)
                return;
            Node target = (Node) e.getTarget();
            boolean onInteractive = false;
            Node cur = target;
            while (cur != null && cur != table) {
                if (cur instanceof TableCell) {
                    onInteractive = true;
                    break;
                }
                cur = cur.getParent();
            }
            if (!onInteractive) {
                cur = target;
                while (cur != null && cur != table) {
                    if (cur instanceof Label) {
                        onInteractive = true;
                        break;
                    }
                    cur = cur.getParent();
                }
            }
            if (!onInteractive) {
                selectionListener.onSelectionCleared();
            }
        });
    }

    public void rebuild(Graph graph) {
        isRebuilding = true;
        table.getColumns().clear();
        rows.clear();
        colHeaderLabels.clear();
        int n = graph.size();

        TableColumn<Row, String> rowHeaderCol = new TableColumn<>("v \\ v");
        rowHeaderCol.setSortable(false);
        rowHeaderCol.setResizable(false);
        rowHeaderCol.setReorderable(false);
        rowHeaderCol.setPrefWidth(50);
        rowHeaderCol.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getIndex())));
        rowHeaderCol.setCellFactory(tv -> {
            TableCell<Row, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setText(null);
                        setStyle("");
                        return;
                    }
                    setText(item);
                    int i = getIndex();
                    if (selectedVertex == i || selectedRow == i) {
                        setStyle("-fx-background-color: #fff176; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            };
            cell.setOnMousePressed(e -> {
                int i = cell.getIndex();
                if (i >= 0 && selectionListener != null)
                    selectionListener.onRowHeaderSelected(i);
            });
            return cell;
        });
        table.getColumns().add(rowHeaderCol);

        for (int i = 0; i < n; i++) {
            Row row = new Row(i, n);
            for (int j = 0; j < n; j++) {
                Integer v = graph.get(i, j);
                row.getCells().get(j).set((v == null) ? "inf" : String.valueOf(v));
            }
            rows.add(row);
        }

        for (int j = 0; j < n; j++) {
            final int colIndex = j;
            TableColumn<Row, String> col = new TableColumn<>();
            Label headerLabel = new Label(String.valueOf(j));
            headerLabel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            headerLabel.setAlignment(Pos.CENTER);
            headerLabel.setOnMouseClicked(e -> {
                if (selectionListener != null)
                    selectionListener.onColumnHeaderSelected(colIndex);
                table.getSelectionModel().clearSelection();
            });
            col.setGraphic(headerLabel);
            colHeaderLabels.add(headerLabel);
            col.setSortable(false);
            col.setResizable(false);
            col.setPrefWidth(56);
            col.setReorderable(false);
            col.setCellValueFactory(cell -> {
                Row r = cell.getValue();
                if (r == null || colIndex >= r.getCells().size())
                    return new SimpleStringProperty("");
                return r.getCells().get(colIndex);
            });
            if (editable) {
                col.setCellFactory(tv -> makeEditableCell(colIndex));
                col.setOnEditCommit(e -> {
                    Row r = e.getRowValue();
                    int i = r.getIndex();
                    if (editListener != null)
                        editListener.onCellEdited(i, colIndex, e.getNewValue());
                });
            } else {
                col.setCellFactory(tv -> makeReadOnlyCell(colIndex));
            }
            table.getColumns().add(col);
        }

        table.setItems(FXCollections.observableArrayList(rows));
        table.getSelectionModel().clearSelection();
        table.refresh();
        isRebuilding = false;
    }

    public void setEditingLocked(boolean locked) {
        if (this.editable)
            table.setEditable(!locked);
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
                setEditable(editable && i != j);
                applyCellStyle(this, i, j);
            }
        };
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
                applyCellStyle(this, i, j);
            }
        };
    }

    private void applyCellStyle(TableCell<Row, String> cell, int i, int j) {
        if (i < 0 || j < 0)
            return;

        boolean isDiag = (i == j);

        // 1. Алгоритмическая подсветка (высший приоритет)
        boolean isAlgIJ = (i == hlI && j == hlJ);
        boolean isAlgIK = (i == hlI && j == hlK) && !isAlgIJ;
        boolean isAlgKJ = (i == hlK && j == hlJ) && !isAlgIJ && !isAlgIK;

        if (isAlgIJ) {
            if (hlWasUpdate) {
                cell.setStyle("-fx-background-color: #66bb6a; -fx-font-weight: bold;"); // зелёный
            } else {
                cell.setStyle("-fx-background-color: #fff176; -fx-font-weight: bold;"); // жёлтый
            }
            return;
        }
        if (isAlgIK) {
            cell.setStyle("-fx-background-color: #ffb74d; -fx-font-weight: bold;"); // оранжевый (i)
            return;
        }
        if (isAlgKJ) {
            cell.setStyle("-fx-background-color: #ce93d8; -fx-font-weight: bold;"); // фиолетовый (j)
            return;
        }

        // 2. Пользовательское выделение
        boolean isUserSelected = false;
        if (selectedVertex >= 0 && (i == selectedVertex || j == selectedVertex)) {
            isUserSelected = true;
        } else if (selectedEdgeFrom >= 0 && i == selectedEdgeFrom && j == selectedEdgeTo) {
            isUserSelected = true;
        } else if (selectedRow >= 0 && i == selectedRow) {
            isUserSelected = true;
        } else if (selectedCol >= 0 && j == selectedCol) {
            isUserSelected = true;
        }

        if (isUserSelected) {
            cell.setStyle("-fx-background-color: #fff176; -fx-font-weight: bold;");
        } else if (isDiag) {
            cell.setStyle("-fx-background-color: #eceff1; -fx-text-fill: #90a4ae;");
        } else {
            cell.setStyle("");
        }
    }

    public void setAlgorithmHighlight(int i, int j, int k, boolean wasUpdate) {
        this.hlI = i;
        this.hlJ = j;
        this.hlK = k;
        this.hlWasUpdate = wasUpdate;
        table.refresh();
    }

    public void clearAlgorithmHighlight() {
        this.hlI = -1;
        this.hlJ = -1;
        this.hlK = -1;
        this.hlWasUpdate = false;
        table.refresh();
    }

    public void selectVertex(int v) {
        this.selectedVertex = v;
        this.selectedEdgeFrom = -1;
        this.selectedEdgeTo = -1;
        this.selectedRow = -1;
        this.selectedCol = -1;
        updateHeaderStyles();
        table.refresh();
    }

    public void selectEdge(int from, int to) {
        this.selectedVertex = -1;
        this.selectedEdgeFrom = from;
        this.selectedEdgeTo = to;
        this.selectedRow = -1;
        this.selectedCol = -1;
        updateHeaderStyles();
        table.refresh();
    }

    public void selectRow(int i) {
        this.selectedVertex = -1;
        this.selectedEdgeFrom = -1;
        this.selectedEdgeTo = -1;
        this.selectedRow = i;
        this.selectedCol = -1;
        updateHeaderStyles();
        table.refresh();
    }

    public void selectCol(int j) {
        this.selectedVertex = -1;
        this.selectedEdgeFrom = -1;
        this.selectedEdgeTo = -1;
        this.selectedRow = -1;
        this.selectedCol = j;
        updateHeaderStyles();
        table.refresh();
    }

    public void clearSelectionSync() {
        this.selectedVertex = -1;
        this.selectedEdgeFrom = -1;
        this.selectedEdgeTo = -1;
        this.selectedRow = -1;
        this.selectedCol = -1;
        updateHeaderStyles();
        table.getSelectionModel().clearSelection();
        table.refresh();
    }

    private void updateHeaderStyles() {
        for (int c = 0; c < colHeaderLabels.size(); c++) {
            if (selectedVertex == c || selectedCol == c) {
                colHeaderLabels.get(c).setStyle("-fx-background-color: #fff176; -fx-font-weight: bold;");
            } else {
                colHeaderLabels.get(c).setStyle("");
            }
        }
    }

    private void setupSelectionListener() {
        Runnable updateSelection = () -> {
            if (isRebuilding || selectionListener == null)
                return;
            @SuppressWarnings("unchecked")
            TablePosition<Row, ?> pos = table.getFocusModel().getFocusedCell();
            if (pos == null || (pos.getRow() < 0 && pos.getColumn() <= 0)) {
                selectionListener.onSelectionCleared();
                return;
            }
            if (pos.getRow() < 0) {
                selectionListener.onColumnHeaderSelected(pos.getColumn() - 1);
                return;
            }
            int i = pos.getRow();
            int j = pos.getColumn() - 1;
            if (j >= 0) {
                selectionListener.onCellSelected(i, j);
            } else {
                selectionListener.onRowHeaderSelected(i);
            }
        };
        table.getFocusModel().focusedCellProperty().addListener((obs, oldPos, newPos) -> updateSelection.run());
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateSelection.run());
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