package com.floydwarshall;

import com.floydwarshall.controller.Controller;
import com.floydwarshall.logging.Logger;
import com.floydwarshall.model.Graph;
import com.floydwarshall.view.ControlPanel;
import com.floydwarshall.view.GraphCanvas;
import com.floydwarshall.view.LogPanel;
import com.floydwarshall.view.MatrixTableView;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class App extends Application {
    private static final double WINDOW_W = 1280;
    private static final double WINDOW_H = 860;

    private Logger logger;
    private Graph inputGraph;
    private GraphCanvas canvas1;
    private GraphCanvas canvas2;
    private MatrixTableView table1;
    private MatrixTableView table2;
    private ControlPanel controlPanel;
    private TextArea stepDescriptionField;
    private Controller controller;

    @Override
    public void start(Stage primaryStage) {
        logger = new Logger();
        logger.log(Logger.Type.INFO, "Программа запущена");

        inputGraph = new Graph(4);
        inputGraph.set(0, 1, 5);
        inputGraph.set(0, 3, 2);
        inputGraph.set(1, 2, 3);
        inputGraph.set(2, 3, 1);
        inputGraph.set(3, 1, 1);

        canvas1 = new GraphCanvas(560, 320, "Холст 1 — вводимый граф", true);
        canvas2 = new GraphCanvas(560, 320, "Холст 2 — граф кратчайших путей", false);
        canvas1.setGraph(inputGraph);
        canvas2.setGraph(new Graph(inputGraph));

        table1 = new MatrixTableView(true);
        table2 = new MatrixTableView(false);
        table1.rebuild(inputGraph);
        table2.rebuild(inputGraph);

        controlPanel = new ControlPanel();

        stepDescriptionField = new TextArea("Алгоритм не запущен");
        stepDescriptionField.setEditable(false);
        stepDescriptionField.setWrapText(true);
        stepDescriptionField.setPrefWidth(420);
        stepDescriptionField.setPrefRowCount(5);
        stepDescriptionField.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 12px;");

        LogPanel logPanel = new LogPanel(logger);

        controller = new Controller(
                inputGraph, canvas1, canvas2, table1, table2, controlPanel, logger);
        controller.setStepDescriptionUpdater(text -> stepDescriptionField.setText(text));

        HBox canvasesBox = new HBox(8, canvas1, canvas2);
        canvasesBox.setPadding(new Insets(6));
        HBox.setHgrow(canvas1, Priority.ALWAYS);
        HBox.setHgrow(canvas2, Priority.ALWAYS);

        VBox table1Box = titledBox("Таблица 1 — вводимая матрица смежности", table1.getNode());
        VBox table2Box = titledBox("Таблица 2 — текущая матрица алгоритма", table2.getNode());
        HBox tablesBox = new HBox(8, table1Box, table2Box);
        tablesBox.setPadding(new Insets(0, 6, 6, 6));
        HBox.setHgrow(table1Box, Priority.ALWAYS);
        HBox.setHgrow(table2Box, Priority.ALWAYS);

        VBox stepBox = titledBox("Поле описания текущего шага", stepDescriptionField);
        HBox controlsBox = new HBox(8, controlPanel.getRoot(), stepBox);
        controlsBox.setPadding(new Insets(0, 6, 6, 6));
        controlsBox.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(controlPanel.getRoot(), Priority.ALWAYS);
        HBox.setHgrow(stepBox, Priority.ALWAYS);

        VBox logBox = titledBox("Панель логов", logPanel.getRoot());
        logBox.setPadding(new Insets(0, 6, 6, 6));

        VBox center = new VBox(6, canvasesBox, tablesBox, controlsBox, logBox);
        VBox.setVgrow(logBox, Priority.ALWAYS);
        BorderPane root = new BorderPane();
        root.setCenter(center);
        Scene scene = new Scene(root, WINDOW_W, WINDOW_H);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                canvas1.clearSelection();
                canvas2.clearSelection();
                table1.clearSelectionSync();
                table2.clearSelectionSync();
            } else if (e.getCode() == KeyCode.RIGHT) {
                // Обработка нажатия стрелки вправо для шага вперед
                controller.stepForward();
            }
        });

        primaryStage.setTitle("Флойд-Уоршалл — Версия 1");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(680);
        primaryStage.show();

        canvas1.widthProperty().addListener(o -> canvas1.draw());
        canvas1.heightProperty().addListener(o -> canvas1.draw());
        canvas2.widthProperty().addListener(o -> canvas2.draw());
        canvas2.heightProperty().addListener(o -> canvas2.draw());
    }

    private VBox titledBox(String title, javafx.scene.Node content) {
        Label lbl = new Label(title);
        lbl.setFont(Font.font("SansSerif", 12));
        lbl.setStyle("-fx-background-color: #eceff1; -fx-padding: 3 6 3 6; -fx-font-weight: bold;");
        VBox box = new VBox(lbl, content);
        box.setStyle("-fx-border-color: #cfd8dc; -fx-border-width: 1; -fx-background-color: #ffffff;");
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    public static void main(String[] args) {
        launch(args);
    }
}