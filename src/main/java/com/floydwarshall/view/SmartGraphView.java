package com.floydwarshall.view;

import com.brunomnsilva.smartgraph.graphview.*;
import com.brunomnsilva.smartgraph.graph.*;
import com.floydwarshall.model.Graph;

import static com.brunomnsilva.smartgraph.graphview.UtilitiesJavaFX.pick;

import javafx.application.Platform;
import javafx.scene.layout.BorderPane;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
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

    // Integer uniquity check workaround (for lib)
    public record EdgeWeight(int u, int v, int weight) {
        @Override
        public String toString() {
            return String.valueOf(weight);
        }
    }

    private Digraph<Integer, EdgeWeight> smartGraph;
    private SmartGraphPanel<Integer, EdgeWeight> graphView;
    private SelectionListener listener;
    private final boolean editableSource;
    private int selectedVertex = -1;
    private int[] selectedEdge = null;
    private int highlightI = -1, highlightJ = -1, highlightK = -1;

    public SmartGraphView(double width, double height, String title, boolean editableSource) {
        this.editableSource = editableSource;
        this.smartGraph = new DigraphEdgeList<>();
        SmartCircularSortedPlacementStrategy placementStrategy = new SmartCircularSortedPlacementStrategy();
        ForceDirectedLayoutStrategy<Integer> automaticPlacement = new ForceDirectedSpringGravityLayoutStrategy<>(
                20.0,
                0.1,
                0.1,
                0.5,
                0.01);
        SmartGraphProperties sgp = new SmartGraphProperties(
                getClass().getResourceAsStream("/smartgraph.properties"));

        java.net.URI css = null;
        try {
            css = getClass().getResource("/smartgraph.css").toURI();
        } catch (URISyntaxException e) {

        }
        this.graphView = new SmartGraphPanel<>(smartGraph, sgp, placementStrategy, css, automaticPlacement);
        this.graphView.setAutomaticLayout(true);

        this.setPrefSize(width, height);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5; -fx-font-size: 13px; -fx-text-fill: #37474f;");
        setTop(titleLabel);
        setCenter(graphView);
        setAlignment(titleLabel, Pos.CENTER);
        setupInteractions();
    }

    private void setupInteractions() {
        this.graphView.setOnMouseClicked(event -> {
            // Only handle primary button single clicks
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1) {
                return;
            }

            Node target = (Node) pick(this, event.getSceneX(), event.getSceneY());

            if (target instanceof SmartGraphVertex) {
                // Vertex clicked
                @SuppressWarnings("unchecked")
                SmartGraphVertex<Integer> v = (SmartGraphVertex<Integer>) target;
                selectedVertex = v.getUnderlyingVertex().element();
                selectedEdge = null;
                applyStylesAsync();
                if (listener != null) {
                    listener.onSelection(SelectionType.VERTEX, selectedVertex, -1);
                }
            } else if (target instanceof SmartGraphEdge) {
                // Edge clicked
                @SuppressWarnings("unchecked")
                SmartGraphEdge<EdgeWeight, Integer> e = (SmartGraphEdge<EdgeWeight, Integer>) target;
                Edge<EdgeWeight, Integer> edge = e.getUnderlyingEdge();
                Vertex<Integer>[] verts = edge.vertices();
                int u = verts[0].element();
                int v = verts[1].element();
                selectedVertex = -1;
                selectedEdge = new int[] { u, v };
                applyStylesAsync();
                if (listener != null) {
                    listener.onSelection(SelectionType.EDGE, u, v);
                }
            } else {
                // Background clicked
                selectedVertex = -1;
                selectedEdge = null;
                applyStylesAsync();
                if (listener != null) {
                    listener.onSelection(SelectionType.NONE, -1, -1);
                }
            }
        });
    }

    public void init() {
        graphView.init();
    }

    public void setSelectionListener(SelectionListener listener) {
        this.listener = listener;
    }

    /**
     * Incremental sync method. Instead of wiping the graph clean (which resets
     * layout coordinates to 0,0),
     * it diffs the current UI graph against the new matrix and only adds/removes
     * what changed.
     */
    public void setGraph(Graph matrixGraph) {
        int n = matrixGraph.size();

        // 1. Remove vertices that are out of bounds (e.g., if graph size decreased)
        List<Vertex<Integer>> verticesToRemove = new ArrayList<>();
        for (Vertex<Integer> v : smartGraph.vertices()) {
            if (v.element() >= n) {
                verticesToRemove.add(v);
            }
        }
        for (Vertex<Integer> v : verticesToRemove) {
            smartGraph.removeVertex(v);
        }

        // 2. Add new vertices if graph size increased
        Set<Integer> existingVertices = new HashSet<>();
        for (Vertex<Integer> v : smartGraph.vertices()) {
            existingVertices.add(v.element());
        }
        for (int i = 0; i < n; i++) {
            if (!existingVertices.contains(i)) {
                smartGraph.insertVertex(i);
            }
        }

        // 3. Sync Edges (Add, Remove, or Update weights)
        Map<String, Edge<EdgeWeight, Integer>> currentEdges = new HashMap<>();
        for (Edge<EdgeWeight, Integer> e : smartGraph.edges()) {
            Vertex<Integer>[] inc = e.vertices();
            String key = inc[0].element() + "-" + inc[1].element();
            currentEdges.put(key, e);
        }

        Set<String> newEdgeKeys = new HashSet<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Integer w = matrixGraph.get(i, j);
                if (w != null && i != j) {
                    String key = i + "-" + j;
                    newEdgeKeys.add(key);

                    if (!currentEdges.containsKey(key)) {
                        // New edge
                        smartGraph.insertEdge(i, j, new EdgeWeight(i, j, w));
                    } else {
                        // Existing edge - check if weight changed
                        Edge<EdgeWeight, Integer> existing = currentEdges.get(key);
                        if (existing.element().weight() != w) {
                            smartGraph.removeEdge(existing);
                            smartGraph.insertEdge(i, j, new EdgeWeight(i, j, w));
                        }
                    }
                }
            }
        }

        // Remove edges that no longer exist in the matrix
        for (Map.Entry<String, Edge<EdgeWeight, Integer>> entry : currentEdges.entrySet()) {
            if (!newEdgeKeys.contains(entry.getKey())) {
                smartGraph.removeEdge(entry.getValue());
            }
        }

        // Defer update until the JavaFX Scene is ready
        Platform.runLater(() -> {
            graphView.update();
            applyStylesAsync();
        });
    }

    public void setAlgorithmHighlight(int i, int j, int k) {
        this.highlightI = i;
        this.highlightJ = j;
        this.highlightK = k;
        applyStylesAsync();
    }

    public void clearAlgorithmHighlight() {
        this.highlightI = -1;
        this.highlightJ = -1;
        this.highlightK = -1;
        applyStylesAsync();
    }

    public void selectVertex(int v) {
        this.selectedVertex = v;
        this.selectedEdge = null;
        applyStylesAsync();
    }

    public void selectEdge(int from, int to) {
        this.selectedVertex = -1;
        this.selectedEdge = new int[] { from, to };
        applyStylesAsync();
    }

    public void clearSelection() {
        this.selectedVertex = -1;
        this.selectedEdge = null;
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
            if (vertex == highlightK) {
                style = "vertex-k";
            } else if (vertex == highlightI || vertex == highlightJ) {
                style = "vertex-ij";
            } else if (vertex == selectedVertex) {
                style = "vertex-selected";
            }
            SmartStylableNode node = graphView.getStylableVertex(vertex);
            if (node != null) {
                node.setStyleClass(style);
            }
        }

        for (Edge<EdgeWeight, Integer> e : smartGraph.edges()) {
            Vertex<Integer>[] incident = e.vertices();
            int u = incident[0].element();
            int v = incident[1].element();
            String style = "edge";
            boolean isAlgEdge = (u == highlightI && v == highlightJ) ||
                    (u == highlightI && v == highlightK) ||
                    (u == highlightK && v == highlightJ);
            boolean isSel = (selectedEdge != null && selectedEdge[0] == u && selectedEdge[1] == v);

            if (isAlgEdge) {
                style = "edge-highlight";
            } else if (isSel) {
                style = "edge-selected";
            }

            SmartStylableNode node = graphView.getStylableEdge(e);
            if (node != null) {
                node.setStyleClass(style);
            }
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