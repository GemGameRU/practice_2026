package com.floydwarshall.view;

import com.floydwarshall.model.Graph;
import static com.brunomnsilva.smartgraph.graphview.UtilitiesJavaFX.pick;
import com.brunomnsilva.smartgraph.graphview.*;
import com.brunomnsilva.smartgraph.graph.*;
import javafx.application.Platform;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.geometry.Pos;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

public class SmartGraphView extends BorderPane {

    public interface SelectionListener {
        void onSelection(SelectionType type, int a, int b);
    }

    public enum SelectionType {
        VERTEX, EDGE, NONE
    }

    public record EdgeWeight(int u, int v, int weight) {
        @Override
        public String toString() {
            return String.valueOf(weight);
        }
    }

    private Digraph<Integer, EdgeWeight> smartGraph;
    private SmartGraphPanel<Integer, EdgeWeight> graphView;
    private StackPane wrapper;
    private SelectionListener listener;
    private final boolean editableSource;
    private boolean verticesFixed = false;

    private int selectedVertex = -1;
    private int[] selectedEdge = null;
    private int selectedVertexEdges = -1;
    private boolean selectedOutgoing = true;

    private int highlightI = -1, highlightJ = -1, highlightK = -1;
    private boolean highlightWasUpdate = false;

    public SmartGraphView(double width, double height, String title, boolean editableSource) {
        this.editableSource = editableSource;
        this.smartGraph = new DigraphEdgeList<>();

        SmartCircularSortedPlacementStrategy placementStrategy = new SmartCircularSortedPlacementStrategy();
        ForceDirectedLayoutStrategy<Integer> automaticPlacement = new ForceDirectedSpringGravityLayoutStrategy<>(20.0,
                0.1, 0.1, 0.5, 0.01);

        SmartGraphProperties sgp = new SmartGraphProperties(getClass().getResourceAsStream("/smartgraph.properties"));
        java.net.URI css = null;
        try {
            css = getClass().getResource("/smartgraph.css").toURI();
        } catch (URISyntaxException e) {
            /* ignore */
        }

        this.graphView = new SmartGraphPanel<>(smartGraph, sgp, placementStrategy, css, automaticPlacement);
        this.graphView.setAutomaticLayout(true);
        this.graphView.setPrefSize(width, height);

        this.wrapper = new StackPane();
        this.wrapper.getChildren().add(this.graphView);

        this.setPrefSize(width, height);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5; -fx-font-size: 13px; -fx-text-fill: #37474f;");
        setTop(titleLabel);
        setCenter(wrapper);
        setAlignment(titleLabel, Pos.CENTER);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(this.widthProperty());
        clip.heightProperty().bind(this.heightProperty());
        this.setClip(clip);

        setupInteractions();
    }

    public void setVerticesFixed(boolean fixed) {
        this.verticesFixed = fixed;
        graphView.setAutomaticLayout(!fixed);
    }

    private void setupInteractions() {
        this.graphView.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1)
                return;

            Node target = (Node) pick(this, event.getSceneX(), event.getSceneY());

            if (target instanceof SmartGraphVertex) {
                @SuppressWarnings("unchecked")
                SmartGraphVertex<Integer> v = (SmartGraphVertex<Integer>) target;
                selectedVertex = v.getUnderlyingVertex().element();
                selectedEdge = null;
                selectedVertexEdges = -1;
                applyStylesAsync();
                if (listener != null)
                    listener.onSelection(SelectionType.VERTEX, selectedVertex, -1);
            } else if (target instanceof SmartGraphEdge) {
                @SuppressWarnings("unchecked")
                SmartGraphEdge<EdgeWeight, Integer> e = (SmartGraphEdge<EdgeWeight, Integer>) target;
                Edge<EdgeWeight, Integer> edge = e.getUnderlyingEdge();
                Vertex<Integer>[] verts = edge.vertices();
                int u = verts[0].element();
                int v = verts[1].element();

                selectedVertex = -1;
                selectedEdge = new int[] { u, v };
                selectedVertexEdges = -1;
                applyStylesAsync();
                if (listener != null)
                    listener.onSelection(SelectionType.EDGE, u, v);
            } else {
                selectedVertex = -1;
                selectedEdge = null;
                selectedVertexEdges = -1;
                applyStylesAsync();
                if (listener != null)
                    listener.onSelection(SelectionType.NONE, -1, -1);
            }
        });

        this.addEventFilter(ScrollEvent.SCROLL, event -> {
            double deltaY = event.getDeltaY();
            double deltaX = event.getDeltaX();
            if (event.isShiftDown()) {
                double delta = (deltaX != 0) ? deltaX : deltaY;
                wrapper.setTranslateX(wrapper.getTranslateX() + delta);
            } else {
                wrapper.setTranslateY(wrapper.getTranslateY() + deltaY);
            }
            event.consume();
        });

        this.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (verticesFixed) {
                event.consume();
            }
        });
    }

    public void init() {
        graphView.init();
    }

    public void setSelectionListener(SelectionListener listener) {
        this.listener = listener;
    }

    public void setGraph(Graph matrixGraph) {
        int n = matrixGraph.size();

        List<Vertex<Integer>> verticesToRemove = new ArrayList<>();
        for (Vertex<Integer> v : smartGraph.vertices())
            if (v.element() >= n)
                verticesToRemove.add(v);
        for (Vertex<Integer> v : verticesToRemove)
            smartGraph.removeVertex(v);

        Set<Integer> existingVertices = new HashSet<>();
        for (Vertex<Integer> v : smartGraph.vertices())
            existingVertices.add(v.element());

        for (int i = 0; i < n; i++)
            if (!existingVertices.contains(i))
                smartGraph.insertVertex(i);

        Map<String, Edge<EdgeWeight, Integer>> currentEdges = new HashMap<>();
        for (Edge<EdgeWeight, Integer> e : smartGraph.edges()) {
            Vertex<Integer>[] inc = e.vertices();
            currentEdges.put(inc[0].element() + "-" + inc[1].element(), e);
        }

        Set<String> newEdgeKeys = new HashSet<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Integer w = matrixGraph.get(i, j);
                if (w != null && i != j) {
                    String key = i + "-" + j;
                    newEdgeKeys.add(key);
                    if (!currentEdges.containsKey(key)) {
                        smartGraph.insertEdge(i, j, new EdgeWeight(i, j, w));
                    } else {
                        Edge<EdgeWeight, Integer> existing = currentEdges.get(key);
                        if (existing.element().weight() != w) {
                            smartGraph.removeEdge(existing);
                            smartGraph.insertEdge(i, j, new EdgeWeight(i, j, w));
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, Edge<EdgeWeight, Integer>> entry : currentEdges.entrySet()) {
            if (!newEdgeKeys.contains(entry.getKey()))
                smartGraph.removeEdge(entry.getValue());
        }

        Platform.runLater(() -> {
            graphView.update();
            applyStylesAsync();
        });
    }

    public void setAlgorithmHighlight(int i, int j, int k, boolean wasUpdate) {
        this.highlightI = i;
        this.highlightJ = j;
        this.highlightK = k;
        this.highlightWasUpdate = wasUpdate;
        applyStylesAsync();
    }

    public void clearAlgorithmHighlight() {
        this.highlightI = -1;
        this.highlightJ = -1;
        this.highlightK = -1;
        this.highlightWasUpdate = false;
        applyStylesAsync();
    }

    public void selectVertex(int v) {
        this.selectedVertex = v;
        this.selectedEdge = null;
        this.selectedVertexEdges = -1;
        applyStylesAsync();
    }

    public void selectEdge(int from, int to) {
        this.selectedVertex = -1;
        this.selectedEdge = new int[] { from, to };
        this.selectedVertexEdges = -1;
        applyStylesAsync();
    }

    public void selectVertexOutgoing(int v) {
        this.selectedVertex = v;
        this.selectedEdge = null;
        this.selectedVertexEdges = v;
        this.selectedOutgoing = true;
        applyStylesAsync();
    }

    public void selectVertexIncoming(int v) {
        this.selectedVertex = v;
        this.selectedEdge = null;
        this.selectedVertexEdges = v;
        this.selectedOutgoing = false;
        applyStylesAsync();
    }

    public void clearSelection() {
        this.selectedVertex = -1;
        this.selectedEdge = null;
        this.selectedVertexEdges = -1;
        applyStylesAsync();
    }

    private void applyStylesAsync() {
        Platform.runLater(this::applyAllStyles);
    }

    private void applyAllStyles() {
        if (smartGraph == null)
            return;

        for (Vertex<Integer> v : smartGraph.vertices()) {
            int vertex = v.element();
            String style = "vertex";

            // Особое выделение вершин k, i, j
            if (vertex == highlightK)
                style = "vertex-k"; // Для k используем отдельный класс (в CSS можно задать scale: 1.3 и яркий цвет)
            else if (vertex == highlightI)
                style = "vertex-i";
            else if (vertex == highlightJ)
                style = "vertex-j";
            else if (vertex == selectedVertex)
                style = "vertex-selected";

            SmartStylableNode node = graphView.getStylableVertex(vertex);
            if (node != null)
                node.setStyleClass(style);
        }

        for (Edge<EdgeWeight, Integer> e : smartGraph.edges()) {
            Vertex<Integer>[] incident = e.vertices();
            int u = incident[0].element();
            int v = incident[1].element();

            String style = "edge";
            boolean isAlgIJ = (u == highlightI && v == highlightJ);
            boolean isAlgIK = (u == highlightI && v == highlightK);
            boolean isAlgKJ = (u == highlightK && v == highlightJ);

            if (isAlgIJ) {
                // Если было обновление, красим ребро в зеленый
                style = highlightWasUpdate ? "edge-ij-updated" : "edge-ij";
            } else if (isAlgIK) {
                style = "edge-ik";
            } else if (isAlgKJ) {
                style = "edge-kj";
            } else {
                boolean isSel = false;
                if (selectedEdge != null && selectedEdge[0] == u && selectedEdge[1] == v)
                    isSel = true;
                else if (selectedVertexEdges >= 0) {
                    if (selectedOutgoing && u == selectedVertexEdges)
                        isSel = true;
                    if (!selectedOutgoing && v == selectedVertexEdges)
                        isSel = true;
                }
                if (isSel)
                    style = "edge-selected";
            }

            SmartStylableNode node = graphView.getStylableEdge(e);
            if (node != null)
                node.setStyleClass(style);
        }
    }

    public boolean isEditableSource() {
        return editableSource;
    }

    public int getSelectedVertex() {
        return selectedVertex;
    }

    public int[] getSelectedEdge() {
        return selectedEdge;
    }

    public void draw() {
        Platform.runLater(() -> {
            graphView.update();
            applyStylesAsync();
        });
    }
}