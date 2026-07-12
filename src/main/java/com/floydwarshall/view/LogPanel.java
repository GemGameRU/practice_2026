package com.floydwarshall.view;

import com.floydwarshall.logging.Logger;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class LogPanel {

    private final VBox root;
    private final ListView<String> listView;

    public LogPanel(Logger logger) {
        listView = new ListView<>(logger.getEntries());
        listView.setPrefHeight(140);
        listView.setMinHeight(90);
        listView.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 11px;");

        // Подсветка ERROR-записей.
        listView.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    return;
                }
                setText(item);
                if (item.contains("[ERROR]")) {
                    setTextFill(Color.web("#c62828"));
                    setStyle("-fx-font-weight: bold;");
                } else if (item.contains("[STATE]")) {
                    setTextFill(Color.web("#1565c0"));
                } else if (item.contains("[ACTION]")) {
                    setTextFill(Color.web("#2e7d32"));
                } else {
                    setTextFill(Color.web("#212121"));
                }
            }
        });

        // Автопрокрутка к последней записи.
        logger.getEntries().addListener((ListChangeListener<String>) c -> {
            while (c.next()) {
                if (c.wasAdded() && !c.getAddedSubList().isEmpty()) {
                    int last = listView.getItems().size() - 1;
                    Platform.runLater(() -> listView.scrollTo(last));
                }
            }
        });

        root = new VBox(listView);
        root.setPrefHeight(160);
        VBox.setVgrow(listView, javafx.scene.layout.Priority.ALWAYS);
    }

    public VBox getRoot() {
        return root;
    }
}
